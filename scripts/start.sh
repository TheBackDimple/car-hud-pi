#!/bin/bash
# Start FastAPI backend + OBD reader + Chromium kiosk (Phase 3+)
# Assumes frontend has been built (run build-frontend.sh first)
#
# Works on both:
#   - SPI TFT (480x320 fbdev, after setup-spi-display.sh)
#   - HDMI projector (1280x720+ via KMS/Wayland)
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
SPI_MODE=false
[ -f /etc/X11/xorg.conf.d/99-spi-tft.conf ] && SPI_MODE=true

export DISPLAY="${DISPLAY:-:0}"

# ---- SPI mode: start bare X server if no display server is running ----
STARTED_X=false
if $SPI_MODE; then
    if ! pgrep -x Xorg >/dev/null 2>&1 && ! pgrep -x X >/dev/null 2>&1; then
        echo "SPI mode: starting X server on fb0..."
        X :0 -keeptty &
        X_PID=$!
        STARTED_X=true
        sleep 2
    fi
fi

# Disable screen blanking
xset s off 2>/dev/null || true
xset -dpms 2>/dev/null || true
xset s noblank 2>/dev/null || true

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

# Chromium URL (TLS — see uvicorn --ssl-* above):
# For windshield reflection (production): add ?mirror=true
#   https://localhost:8000?mirror=true
# For normal display (demo/testing, default below):
#   https://localhost:8000

# Detect screen resolution via xrandr (works for both SPI fbdev and HDMI)
SCREEN_RES="1280,720"
DETECTED=$(xrandr 2>/dev/null | grep '\*' | awk '{print $1}' | head -1)
if [ -n "$DETECTED" ]; then
    SCREEN_RES="${DETECTED/x/,}"
    echo "Detected display: ${DETECTED}"
fi

EXTRA_FLAGS=""
if $SPI_MODE; then
    EXTRA_FLAGS="--disable-gpu --disable-software-rasterizer --force-device-scale-factor=0.95"
    echo "SPI display mode: GPU disabled, scale=0.95"
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
  https://localhost:8000

# Cleanup
[ -n "$OBD_PID" ] && kill $OBD_PID 2>/dev/null || true
kill $BACKEND_PID 2>/dev/null || true
$STARTED_X && [ -n "$X_PID" ] && kill $X_PID 2>/dev/null || true
