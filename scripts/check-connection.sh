#!/bin/bash
# Run on the Pi to diagnose why the Android app can't connect.
# Usage: ./scripts/check-connection.sh

echo "=== Car HUD Connection Diagnostic ==="
echo ""

echo "1. USB interface (usb0) - use this IP in the app:"
ip addr show usb0 2>/dev/null | grep -E "inet |state " || echo "   usb0 not found - enable USB tethering on phone"
echo ""

echo "2. Server listening on port 8000?"
ss -tlnp 2>/dev/null | grep 8000 || echo "   NOT listening - start hud.service or run start.sh"
echo ""

echo "3. Health check from Pi (localhost):"
curl -k -s -o /dev/null -w "%{http_code}" https://127.0.0.1:8000/health 2>/dev/null
if [ $? -eq 0 ]; then
  echo " - OK (server responds)"
else
  echo " - FAILED (server not running or not responding)"
fi
echo ""

echo "4. Firewall (ufw):"
if command -v ufw &>/dev/null; then
  ufw status 2>/dev/null | head -5 || sudo ufw status 2>/dev/null | head -5
  if sudo ufw status 2>/dev/null | grep -q "Status: active"; then
    echo "   If 8000 is not ALLOW, run: sudo ufw allow 8000/tcp && sudo ufw reload"
  fi
else
  echo "   ufw not installed"
fi
echo ""

echo "5. Health check via usb0 IP (if usb0 exists):"
USB_IP=$(ip -4 addr show usb0 2>/dev/null | grep -E 'inet ' | awk '{print $2}' | cut -d/ -f1)
if [ -n "$USB_IP" ]; then
  curl -k -s -o /dev/null -w "%{http_code}" "https://${USB_IP}:8000/health" 2>/dev/null
  echo " - from $USB_IP"
else
  echo "   (skip - no usb0)"
fi
echo ""
echo "=== On the phone: Wi-Fi OFF, USB tethering ON, use the usb0 IP above ==="
