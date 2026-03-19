#!/bin/bash
# Start FastAPI backend + OBD reader + Chromium kiosk (Phase 3+)
# Assumes frontend has been built (run build-frontend.sh first)
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Disable screen blanking (Pi must never turn off the display)
export DISPLAY="${DISPLAY:-:0}"
xset s off 2>/dev/null || true
xset -dpms 2>/dev/null || true
xset s noblank 2>/dev/null || true

# Start FastAPI backend (run from project root so backend package resolves)
cd "$PROJECT_DIR"

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
    -addext "subjectAltName=DNS:localhost,IP:127.0.0.1,IP:192.168.254.2" &>/dev/null; then
    openssl req -x509 -newkey rsa:2048 -nodes \
      -keyout "$KEY_FILE" -out "$CERT_FILE" -days 365 \
      -subj "/CN=localhost" &>/dev/null
  fi
fi

uvicorn backend.main:app --host 0.0.0.0 --port 8000 --ssl-keyfile "$KEY_FILE" --ssl-certfile "$CERT_FILE" &
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
  --no-first-run \
  --start-fullscreen \
  --window-size=1280,720 \
  --autoplay-policy=no-user-gesture-required \
  https://localhost:8000

# Cleanup
[ -n "$OBD_PID" ] && kill $OBD_PID 2>/dev/null || true
kill $BACKEND_PID 2>/dev/null || true
