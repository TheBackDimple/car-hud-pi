# Car HUD — Raspberry Pi

Raspberry Pi side of a car windshield HUD system. A Pi 4 runs a local React-based HUD displayed on a reflective windshield screen via Chromium kiosk mode. An Android phone connects over USB tethering and acts as the controller and data source — sending vehicle data (OBD-II + GPS), Google Maps imagery, and UI configuration to the Pi in real time over WebSockets.

## Architecture

| Component      | Tech                         | Role                          |
|----------------|------------------------------|-------------------------------|
| Pi Backend     | FastAPI (Python)             | WebSocket server, serves React build |
| Pi Frontend    | React + Vite + TypeScript    | HUD rendering in Chromium     |
| Pi Display     | Chromium kiosk mode         | Fullscreen browser, CSS-mirrored |
| Android App    | Kotlin + Jetpack Compose     | Controller, data source       |
| Communication  | WebSocket over USB tether    | Real-time bidirectional messaging |

Data flow is one-directional for most things: the Android phone pushes data **to** the Pi. The Pi receives and renders.

## Project Structure

```
car-hud-pi/
├── backend/           # FastAPI + WebSocket server
│   ├── main.py
│   ├── ws/             # WebSocket manager & handlers
│   ├── models/         # Pydantic models
│   ├── state/          # In-memory state store
│   └── requirements.txt
├── frontend/           # React + Vite HUD UI
│   ├── src/
│   │   ├── hooks/      # useWebSocket, useHudStore
│   │   ├── components/ # HUD widgets
│   │   ├── layouts/    # Dynamic grid
│   │   ├── styles/     # HUD theme, mirror CSS
│   │   └── types/
│   └── public/
├── systemd/            # hud.service unit
├── networking/         # USB tether config (dhcpcd-usb0.conf)
├── scripts/
│   ├── setup.sh        # Full Pi setup
│   ├── start.sh        # Start backend + Chromium
│   └── build-frontend.sh
├── archive/legacy/     # Old pygame + Flask app (archived)
└── IMPLEMENTATION_PLAN.md
```

## Quick Start (Development)

**Backend** (from project root):
```bash
pip install -r backend/requirements.txt
uvicorn backend.main:app --reload --host 0.0.0.0 --port 8000
```

**Frontend:**
```bash
cd frontend
npm install
npm run dev
```

**Production build:**
```bash
./scripts/build-frontend.sh
./scripts/start.sh
```

## USB Tether Networking

- Pi IP: `192.168.254.2`
- Phone IP: `192.168.254.1`
- WebSocket: `ws://192.168.254.2:8000/ws`

See `networking/dhcpcd-usb0.conf` for dhcpcd configuration.

## Chromium Kiosk & Reflective Display (Phase 3)

The HUD uses a horizontal mirror transform (`scaleX(-1)`) so text reads correctly when reflected off the windshield. `scripts/start.sh` launches Chromium in kiosk mode at 1280×720 and disables screen blanking. Verify mirrored text with a mirror or phone camera.

## Pi Boot Integration (Phase 9)

To start the HUD automatically on boot:

```bash
./scripts/setup.sh   # Run once (as pi or with sudo)
sudo reboot          # Or: sudo systemctl start hud.service
```

The setup script installs Python deps, builds the frontend, configures USB tether (`dhcpcd`), disables screen blanking, and enables `hud.service`. Edit `/etc/systemd/system/hud.service` if your project path differs from `/home/pi/car-hud-pi`.

## Implementation Status

See `IMPLEMENTATION_PLAN.md` for the full phased implementation plan. Phases 0–9 (through boot integration) are implemented.
