#!/bin/bash
# Start FastAPI backend + Chromium kiosk (Phase 3+)
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
uvicorn backend.main:app --host 0.0.0.0 --port 8000 &
BACKEND_PID=$!

# Wait for backend to be ready
sleep 3

# Launch Chromium in kiosk mode
chromium-browser \
  --kiosk \
  --noerrdialogs \
  --disable-infobars \
  --disable-session-crashed-bubble \
  --disable-translate \
  --no-first-run \
  --start-fullscreen \
  --window-size=1280,720 \
  --autoplay-policy=no-user-gesture-required \
  http://localhost:8000

# Cleanup
kill $BACKEND_PID 2>/dev/null || true
