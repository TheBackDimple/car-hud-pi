#!/bin/bash
# Full Pi setup script (Phase 9)
# Run once on first-time Pi setup
# Run as: ./setup.sh  (or sudo ./setup.sh for apt installs)
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
HUD_USER="${SUDO_USER:-$USER}"
[ -z "$HUD_USER" ] && HUD_USER=pi
HUD_HOME="/home/$HUD_USER"

echo "Car HUD Pi - Setup"
echo "=================="

# Install Python dependencies (includes obd for OBD reader)
echo "Installing Python dependencies..."
cd "$PROJECT_DIR/backend"
pip install -r requirements.txt

# Bluetooth for OBD (if not already installed)
if ! command -v bluetoothctl &>/dev/null; then
    echo "Installing Bluetooth tools for OBD..."
    sudo apt-get install -y bluetooth bluez-utils
fi

# Build React frontend (requires Node.js)
if ! command -v npm &>/dev/null; then
    echo "Node.js/npm not found. Install with: curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash - && sudo apt-get install -y nodejs"
    echo "Or: sudo apt-get install -y nodejs npm"
    exit 1
fi
echo "Building frontend..."
"$SCRIPT_DIR/build-frontend.sh"

# Install Chromium if not present (package name varies: chromium-browser or chromium)
if ! command -v chromium-browser &>/dev/null && ! command -v chromium &>/dev/null; then
    echo "Installing Chromium..."
    sudo apt-get update
    sudo apt-get install -y chromium-browser 2>/dev/null || sudo apt-get install -y chromium
fi

# Configure dhcpcd for USB tethering (usb0 → 192.168.254.2)
DHCPCD_INCLUDE="include $PROJECT_DIR/networking/dhcpcd-usb0.conf"
if ! grep -qF "dhcpcd-usb0.conf" /etc/dhcpcd.conf 2>/dev/null; then
    echo "Configuring USB tether (dhcpcd)..."
    echo "" | sudo tee -a /etc/dhcpcd.conf
    echo "# Car HUD USB tether" | sudo tee -a /etc/dhcpcd.conf
    echo "$DHCPCD_INCLUDE" | sudo tee -a /etc/dhcpcd.conf
    echo "  Added $DHCPCD_INCLUDE to /etc/dhcpcd.conf"
else
    echo "USB tether config already in /etc/dhcpcd.conf"
fi

# Disable screen blanking (persistent across reboots)
AUTOSTART_DIR="$HUD_HOME/.config/lxsession/LXDE-pi"
AUTOSTART_FILE="$AUTOSTART_DIR/autostart"
if [ "$(id -u)" = 0 ]; then
    mkdir -p "$AUTOSTART_DIR"
    chown -R "$HUD_USER:$HUD_USER" "$AUTOSTART_DIR" 2>/dev/null || true
else
    mkdir -p "$AUTOSTART_DIR" 2>/dev/null || true
fi
if [ -d "$AUTOSTART_DIR" ]; then
    if ! grep -q "xset s off" "$AUTOSTART_FILE" 2>/dev/null; then
        {
            echo ""
            echo "# Car HUD - disable screen blanking"
            echo "@xset s off"
            echo "@xset -dpms"
            echo "@xset s noblank"
        } >> "$AUTOSTART_FILE"
        [ "$(id -u)" = 0 ] && chown "$HUD_USER:$HUD_USER" "$AUTOSTART_FILE" 2>/dev/null || true
        echo "  Added screen blanking disable to $AUTOSTART_FILE"
    else
        echo "  Screen blanking already disabled in $AUTOSTART_FILE"
    fi
else
    echo "  Note: Could not create $AUTOSTART_DIR (run as $HUD_USER or with sudo)"
fi

# Enable systemd service (update paths for actual install location)
echo "Enabling hud.service..."
SERVICE_FILE="$PROJECT_DIR/systemd/hud.service"
sed -e "s|/home/pi/car-hud-pi|$PROJECT_DIR|g" -e "s|User=pi|User=$HUD_USER|g" -e "s|/home/pi|$HUD_HOME|g" "$SERVICE_FILE" | sudo tee /etc/systemd/system/hud.service > /dev/null
sudo systemctl daemon-reload
sudo systemctl enable hud.service

echo ""
echo "Setup complete."
echo "  Project: $PROJECT_DIR"
echo "  User: $HUD_USER"
echo "  Service: sudo systemctl start hud.service  (or reboot)"
echo "  Edit /etc/systemd/system/hud.service if paths differ from $HUD_HOME/car-hud-pi"
