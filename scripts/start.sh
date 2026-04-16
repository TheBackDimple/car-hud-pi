#!/bin/bash
# Start FastAPI backend + OBD reader + Chromium kiosk (Phase 3+)
# Assumes frontend has been built (run build-frontend.sh first)
#
# Auto-detects display output at runtime:
#   - SPI TFT  (480x320 fbdev, when touchscreen + xorg config are present)
#   - HDMI     (projector / monitor — Wayland preferred, X11 fallback)
#
# This means you can swap between the SPI touchscreen and an HDMI projector
# without re-running any setup/teardown scripts — just reboot with the
# desired display connected.
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

HEADLESS_NO_KIOSK=false
[ "${HUD_SKIP_CHROMIUM:-}" = "1" ] && HEADLESS_NO_KIOSK=true

# ---- Runtime display detection ------------------------------------------------
# SPI mode activates only when BOTH the xorg config AND /dev/fb0 exist.
# If the touchscreen isn't plugged in, fb0 won't be created by the SPI overlay
# and we fall through to HDMI mode automatically.
SPI_MODE=false
STARTED_X=false
X_PID=""
WAYLAND_MODE=false

if ! $HEADLESS_NO_KIOSK; then
SPI_CONF="/etc/X11/xorg.conf.d/99-spi-tft.conf"
[ -f "$SPI_CONF" ] && [ -e /dev/fb0 ] && SPI_MODE=true

if $SPI_MODE; then
    # ---- SPI TFT: start bare X server on fb0 if nothing is running ----
    export DISPLAY=:0
    if ! pgrep -x Xorg >/dev/null 2>&1 && ! pgrep -x X >/dev/null 2>&1; then
        echo "SPI mode: starting X server on fb0..."
        X :0 -keeptty &
        X_PID=$!
        STARTED_X=true
        sleep 2
    fi
    echo "Display: SPI TFT (480x320 on /dev/fb0)"
else
    # ---- HDMI / projector — prefer running Wayland compositor ----
    export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}"

    if pgrep -x labwc >/dev/null 2>&1 || pgrep -x weston >/dev/null 2>&1; then
        for sock in "$XDG_RUNTIME_DIR"/wayland-*; do
            if [ -S "$sock" ]; then
                export WAYLAND_DISPLAY="$(basename "$sock")"
                WAYLAND_MODE=true
                break
            fi
        done
    fi

    if $WAYLAND_MODE; then
        echo "Display: HDMI via Wayland ($WAYLAND_DISPLAY)"
    else
        # X11 path (XWayland, standalone X, or LightDM X session)
        export DISPLAY="${DISPLAY:-:0}"
        if ! pgrep -x Xorg >/dev/null 2>&1 && ! pgrep -x X >/dev/null 2>&1; then
            echo "HDMI mode: starting X server..."
            X :0 &
            X_PID=$!
            STARTED_X=true
            sleep 2
        fi
        echo "Display: HDMI via X11 (:0)"
    fi
fi

# Disable screen blanking and hide cursor (X11 only)
if ! $WAYLAND_MODE; then
    xset s off 2>/dev/null || true
    xset -dpms 2>/dev/null || true
    xset s noblank 2>/dev/null || true
    unclutter -idle 0 -root &
fi
fi

if $HEADLESS_NO_KIOSK; then
    echo "Headless: no X11/Wayland/Chromium prep (HUD_SKIP_CHROMIUM=1)"
fi

# Start FastAPI backend (run from project root so backend package resolves)
cd "$PROJECT_DIR"

# Activate venv if present
if [ -f "$PROJECT_DIR/venv/bin/activate" ]; then
    . "$PROJECT_DIR/venv/bin/activate"
fi

# ---- TLS (self-signed for local dev) ----
CERT_DIR="$PROJECT_DIR/certs"
KEY_FILE="$CERT_DIR/localhost-key.pem"
CERT_FILE="$CERT_DIR/localhost-cert.pem"
mkdir -p "$CERT_DIR"

if [ ! -f "$KEY_FILE" ] || [ ! -f "$CERT_FILE" ]; then
  if ! command -v openssl &>/dev/null; then
    echo "ERROR: openssl not found; cannot generate self-signed certs." >&2
    exit 1
  fi

  # Try generating with SAN so TLS hostname verification is more likely to pass.
  if ! openssl req -x509 -newkey rsa:2048 -nodes \
    -keyout "$KEY_FILE" -out "$CERT_FILE" -days 365 \
    -subj "/CN=localhost" \
    -addext "subjectAltName=DNS:localhost,DNS:carhud.local,IP:127.0.0.1,IP:192.168.42.2,IP:192.168.171.140,IP:192.168.254.2,IP:192.168.1.251" &>/dev/null; then
    openssl req -x509 -newkey rsa:2048 -nodes \
      -keyout "$KEY_FILE" -out "$CERT_FILE" -days 365 \
      -subj "/CN=localhost" &>/dev/null
  fi
fi

python3 -m uvicorn backend.main:app --host 0.0.0.0 --port 8000 --ssl-keyfile "$KEY_FILE" --ssl-certfile "$CERT_FILE" &
BACKEND_PID=$!

# Start OBD reader if port exists (Bluetooth OBD paired with Pi)
# Pair with: bluetoothctl → pair <MAC> → trust <MAC>
# Bind with: sudo rfcomm bind 0 <MAC_ADDRESS>
OBD_PID=""
if [ -e /dev/rfcomm0 ]; then
    python3 "$SCRIPT_DIR/obd_reader.py" --port /dev/rfcomm0 --ws-url "wss://127.0.0.1:8000/ws?role=obd" &
    OBD_PID=$!
else
    echo "OBD: /dev/rfcomm0 not found (pair OBD adapter with Pi to enable)"
fi

# Wait for backend to be ready
sleep 3

# Headless / no HDMI: Chromium cannot open a display and exits immediately, which
# would run cleanup and kill the backend. Set HUD_SKIP_CHROMIUM=1 (e.g. systemctl edit hud.service → [Service] Environment=HUD_SKIP_CHROMIUM=1).
if $HEADLESS_NO_KIOSK; then echo "HUD_SKIP_CHROMIUM=1: FastAPI (+ OBD if enabled) only; Chromium skipped."
    wait $BACKEND_PID
    BACKEND_EXIT=$?
    [ -n "$OBD_PID" ] && kill $OBD_PID 2>/dev/null || true
    $STARTED_X && [ -n "$X_PID" ] && kill $X_PID 2>/dev/null || true
    exit $BACKEND_EXIT
fi

# Chromium URL (TLS — see uvicorn --ssl-* above).
# Windshield HUD: mirror horizontally so reflection reads correctly (see frontend main.tsx + mirror.css).
# Bench testing on a normal monitor: HUD_MIRROR=0 (e.g. systemctl edit hud.service).
HUD_MIRROR="${HUD_MIRROR:-1}"
CHROMIUM_URL="https://localhost:8000"
if [ "$HUD_MIRROR" = "1" ]; then
  CHROMIUM_URL="https://localhost:8000?mirror=true"
  echo "Chromium URL: mirrored for reflective display (HUD_MIRROR=1)"
else
  echo "Chromium URL: not mirrored (HUD_MIRROR=0)"
fi

# ---- Detect screen resolution ----
SCREEN_RES="1280,720"
if $WAYLAND_MODE; then
    DETECTED=$(wlr-randr 2>/dev/null | grep 'current' | grep -oE '[0-9]+x[0-9]+' | head -1)
else
    DETECTED=$(xrandr 2>/dev/null | grep '\*' | awk '{print $1}' | head -1)
fi
if [ -n "$DETECTED" ]; then
    SCREEN_RES="${DETECTED/x/,}"
    echo "Detected display: ${DETECTED}"
fi

# ---- Chromium flags per display mode ----
EXTRA_FLAGS=""
if $SPI_MODE; then
    EXTRA_FLAGS="--disable-gpu --disable-software-rasterizer --force-device-scale-factor=0.95"
    echo "SPI display mode: GPU disabled, scale=0.95"
elif $WAYLAND_MODE; then
    EXTRA_FLAGS="--ozone-platform=wayland --enable-features=UseOzonePlatform"
    echo "Wayland mode: using Ozone/Wayland backend"
fi

# Launch Chromium in kiosk mode (chromium-browser or chromium depending on distro)
CHROMIUM_CMD="chromium-browser"
command -v chromium-browser &>/dev/null || CHROMIUM_CMD="chromium"
$CHROMIUM_CMD \
  --kiosk \
  --noerrdialogs \
  --disable-infobars \
  --ignore-certificate-errors \
  --allow-insecure-localhost \
  --disable-session-crashed-bubble \
  --disable-translate \
  --password-store=basic \
  --no-first-run \
  --start-fullscreen \
  --window-size="$SCREEN_RES" \
  --autoplay-policy=no-user-gesture-required \
  $EXTRA_FLAGS \
  "$CHROMIUM_URL"

# Cleanup
[ -n "$OBD_PID" ] && kill $OBD_PID 2>/dev/null || true
kill $BACKEND_PID 2>/dev/null || true
$STARTED_X && [ -n "$X_PID" ] && kill $X_PID 2>/dev/null || true
