# Car HUD Raspberry Pi (Renderer + API)

This repo contains the Raspberry Pi side of a car windshield HUD system:
- Fullscreen HUD renderer (pygame)
- HTTP API server (Flask) for live updates
- systemd service to auto-start on boot
- USB tether networking notes (Android -> Pi via usb0)

## Runtime behavior
- The Pi runs `hud.py` which:
  - Starts a Flask server on port 5000
  - Renders a fullscreen HUD via pygame
  - Updates HUD fields when phone sends JSON

## API
Base URL (USB tether static IP):
- `http://192.168.254.2:5000`

Endpoints:
- GET `/state` -> returns current HUD state JSON
- POST `/update` -> update any subset of fields

POST JSON fields (all strings):
- speed
- mpg
- range
- turn
- distance

Example:
```json
{ "speed": "65", "turn": "Turn Right", "distance": "1.5 miles" }
