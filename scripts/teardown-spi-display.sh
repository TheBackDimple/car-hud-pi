#!/bin/bash
# Undo setup-spi-display.sh — switch back to HDMI output (Wayland/labwc).
#
# Run on the Pi as:  sudo bash scripts/teardown-spi-display.sh
# Reboot after:      sudo reboot
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
    echo "ERROR: Run with sudo" >&2
    exit 1
fi

CONFIG_TXT="/boot/firmware/config.txt"
XORG_CONF="/etc/X11/xorg.conf.d/99-spi-tft.conf"
TOUCH_CONF="/etc/X11/xorg.conf.d/99-touch-calibration.conf"
UDEV_RULE="/etc/udev/rules.d/99-fbdev.rules"
LIGHTDM_CONF="/etc/lightdm/lightdm.conf"

echo "=== SPI TFT Display Teardown ==="

# ---- 1. Remove X11 fbdev + touch configs ----
for f in "$XORG_CONF" "$TOUCH_CONF"; do
    if [ -f "$f" ]; then
        rm "$f"
        echo "[OK] Removed $f"
    fi
done

# ---- 2. Restore config.txt ----
BACKUP="$CONFIG_TXT.before-spi.bak"
if [ -f "$BACKUP" ]; then
    cp "$BACKUP" "$CONFIG_TXT"
    echo "[OK] Restored $CONFIG_TXT from backup"
else
    if [ -f "$CONFIG_TXT" ]; then
        sed -i \
            -e 's/^# \[spi-setup\] hdmi_force_hotplug=1/hdmi_force_hotplug=1/' \
            -e 's/^# \[spi-setup\] hdmi_group=2/hdmi_group=2/' \
            -e 's/^# \[spi-setup\] hdmi_mode=87/hdmi_mode=87/' \
            -e 's/^#  \[spi-setup\] hdmi_cvt=/hdmi_cvt=/' \
            "$CONFIG_TXT"
        echo "[OK] Uncommented HDMI lines in $CONFIG_TXT"
    fi
fi

# ---- 3. Restore LightDM config (back to Wayland/labwc) ----
LIGHTDM_BAK="$LIGHTDM_CONF.before-spi.bak"
if [ -f "$LIGHTDM_BAK" ]; then
    cp "$LIGHTDM_BAK" "$LIGHTDM_CONF"
    echo "[OK] Restored $LIGHTDM_CONF from backup (labwc/Wayland)"
else
    if [ -f "$LIGHTDM_CONF" ]; then
        sed -i \
            -e 's/^greeter-session=pi-greeter$/greeter-session=pi-greeter-labwc/' \
            -e 's/^user-session=rpd-x/user-session=rpd-labwc/' \
            -e 's/^autologin-session=rpd-x/autologin-session=rpd-labwc/' \
            -e '/^xserver-command=X -keeptty/d' \
            "$LIGHTDM_CONF"
        echo "[OK] Switched LightDM back to labwc (Wayland)"
    fi
fi

# ---- 4. Remove udev rule ----
if [ -f "$UDEV_RULE" ]; then
    rm "$UDEV_RULE"
    echo "[OK] Removed $UDEV_RULE"
fi

echo ""
echo "=== Done ==="
echo "Reboot now:  sudo reboot"
echo "Display will use HDMI via Wayland (labwc) — ready for the projector."
