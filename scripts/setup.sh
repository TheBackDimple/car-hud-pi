#!/bin/bash
# Full Pi setup script (Phase 9)
# Run once on first-time Pi setup
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "Car HUD Pi - Setup"
echo "=================="

# Install Python dependencies
echo "Installing Python dependencies..."
cd "$PROJECT_DIR/backend"
pip install -r requirements.txt

# Build React frontend
echo "Building frontend..."
"$SCRIPT_DIR/build-frontend.sh"

# Install Chromium if not present
if ! command -v chromium-browser &>/dev/null; then
    echo "Installing Chromium..."
    sudo apt-get update
    sudo apt-get install -y chromium-browser
fi

# Enable systemd service
echo "Enabling hud.service..."
sudo cp "$PROJECT_DIR/systemd/hud.service" /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable hud.service

# Screen blanking: start.sh runs xset at launch. For persistent config at login,
# add to ~/.config/lxsession/LXDE-pi/autostart:
#   @xset s off
#   @xset -dpms
#   @xset s noblank

echo ""
echo "Setup complete. Edit /etc/systemd/system/hud.service if paths differ."
echo "  Default ExecStart: /home/pi/car-hud-pi/scripts/start.sh"
echo "USB tether config: $PROJECT_DIR/networking/dhcpcd-usb0.conf"
echo "  Add to /etc/dhcpcd.conf: include $PROJECT_DIR/networking/dhcpcd-usb0.conf"
