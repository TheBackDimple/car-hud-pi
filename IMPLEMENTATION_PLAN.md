# Car HUD — Full Implementation Plan

> **Goal:** A Raspberry Pi 4 runs a local React-based HUD displayed on a reflective windshield screen via Chromium kiosk mode. An Android phone connects over USB tethering and acts as the sole controller and data source — sending vehicle data (OBD-II + GPS), Google Maps imagery, and UI configuration to the Pi in real time over WebSockets.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Phase 0 — Project Structure & Migration](#2-phase-0--project-structure--migration)
3. [Phase 1 — FastAPI + WebSocket Server](#3-phase-1--fastapi--websocket-server)
4. [Phase 2 — React HUD Frontend](#4-phase-2--react-hud-frontend)
5. [Phase 3 — Chromium Kiosk & Reflective Display](#5-phase-3--chromium-kiosk--reflective-display)
6. [Phase 4 — Android App Foundation](#6-phase-4--android-app-foundation)
7. [Phase 5 — OBD-II & GPS Data Pipeline](#7-phase-5--obd-ii--gps-data-pipeline)
8. [Phase 6 — Google Maps Streaming](#8-phase-6--google-maps-streaming)
9. [Phase 7 — Preset & Layout System](#9-phase-7--preset--layout-system)
10. [Phase 8 — Feature Toggle System](#10-phase-8--feature-toggle-system)
11. [Phase 9 — System Integration & Boot](#11-phase-9--system-integration--boot)
12. [Phase 10 — Testing & Hardening](#12-phase-10--testing--hardening)
13. [WebSocket Message Schema Reference](#13-websocket-message-schema-reference)
14. [Known Limitations & Risks](#14-known-limitations--risks)
15. [Hardware & Software Requirements](#15-hardware--software-requirements)

---

## 1. Architecture Overview

```
┌──────────────────────┐  USB Tether (usb0)  ┌─────────────────────────────┐
│     Android Phone     │◄──────────────────►│       Raspberry Pi 4         │
│                       │   192.168.254.1     │       192.168.254.2          │
│  ┌─────────────────┐  │     WebSocket       │  ┌───────────────────────┐  │
│  │  Kotlin App      │  │◄─────────────────►│  │  FastAPI (port 8000)   │  │
│  │  - OBD-II BT     │  │                    │  │  - WebSocket endpoint  │  │
│  │  - GPS provider   │  │                    │  │  - Static file serve   │  │
│  │  - Maps snapshot  │  │                    │  └───────────┬───────────┘  │
│  │  - Preset editor  │  │                    │              │              │
│  │  - Feature toggle │  │                    │  ┌───────────▼───────────┐  │
│  └─────────────────┘  │                    │  │  React Frontend        │  │
│                       │                    │  │  (Chromium Kiosk)      │  │
│  ┌─────────────────┐  │                    │  │  - HUD renderer        │  │
│  │  Google Maps SDK │  │                    │  │  - Dynamic layout      │  │
│  │  (snapshot mode) │  │                    │  │  - CSS mirrored        │  │
│  └─────────────────┘  │                    │  │  - 1280x720            │  │
│                       │                    │  └───────────────────────┘  │
└──────────────────────┘                    └─────────────────────────────┘
                                                        │
                                                        ▼
                                              ┌───────────────────┐
                                              │  Reflective HUD    │
                                              │  (Windshield)      │
                                              └───────────────────┘
```

**Data flow is one-directional for most things:** Android phone pushes data **to** the Pi. The Pi's only job is to **receive and render**.

| Component | Tech | Role |
|-----------|------|------|
| Pi Backend | FastAPI (Python) | WebSocket server, serves React build |
| Pi Frontend | React + Vite + TypeScript | HUD rendering in Chromium |
| Pi Display | Chromium kiosk mode | Fullscreen browser, CSS-mirrored |
| Android App | Kotlin + Jetpack Compose | Controller, data source, preset editor |
| Vehicle Data | OBD-II (Bluetooth) + Phone GPS | Speed, RPM, fuel, temps, GPS speed |
| Maps | Google Maps SDK on Android | Snapshot capture → stream to Pi |
| Communication | WebSocket over USB tether | Real-time bidirectional messaging |

---

## 2. Phase 0 — Project Structure & Migration

**Goal:** Replace the current single-file pygame + Flask setup with a proper project structure.

### 2.1 New Repo Structure

```
car-hud-pi/
├── backend/
│   ├── main.py                  # FastAPI app entry point
│   ├── ws/
│   │   ├── __init__.py
│   │   ├── manager.py           # WebSocket connection manager
│   │   └── handlers.py          # Message type handlers
│   ├── models/
│   │   ├── __init__.py
│   │   ├── hud_data.py          # Pydantic models for HUD data
│   │   ├── layout.py            # Pydantic models for layout/presets
│   │   └── messages.py          # WebSocket message envelope model
│   ├── state/
│   │   ├── __init__.py
│   │   └── store.py             # In-memory state store
│   └── requirements.txt
├── frontend/
│   ├── index.html
│   ├── package.json
│   ├── tsconfig.json
│   ├── vite.config.ts
│   ├── src/
│   │   ├── main.tsx
│   │   ├── App.tsx
│   │   ├── hooks/
│   │   │   ├── useWebSocket.ts  # WebSocket connection hook
│   │   │   └── useHudStore.ts   # Zustand store hook
│   │   ├── components/
│   │   │   ├── HudCanvas.tsx    # Main HUD container
│   │   │   ├── SpeedGauge.tsx
│   │   │   ├── NavigationPanel.tsx
│   │   │   ├── MapTile.tsx      # Google Maps image display
│   │   │   ├── OBDPanel.tsx     # OBD-II data readouts
│   │   │   ├── FuelRange.tsx
│   │   │   └── GPSSpeed.tsx
│   │   ├── layouts/
│   │   │   └── DynamicGrid.tsx  # Dynamic grid layout renderer
│   │   ├── styles/
│   │   │   ├── hud.css          # HUD-specific styles (green-on-black, glow)
│   │   │   └── mirror.css       # CSS transform for reflective HUD
│   │   └── types/
│   │       ├── hudData.ts
│   │       └── layout.ts
│   └── public/
│       └── fonts/               # Custom HUD fonts if desired
├── systemd/
│   └── hud.service              # Updated systemd unit
├── networking/
│   └── dhcpcd-usb0.conf         # Existing USB tether config
├── scripts/
│   ├── setup.sh                 # Full Pi setup script
│   ├── start.sh                 # Start backend + Chromium
│   └── build-frontend.sh        # Build React for production
├── IMPLEMENTATION_PLAN.md
└── README.md
```

### 2.2 Steps

- [ ] Create the folder structure above
- [ ] Initialize the React + Vite + TypeScript project in `frontend/`
  ```bash
  cd frontend
  npm create vite@latest . -- --template react-ts
  npm install zustand
  ```
- [ ] Set up FastAPI in `backend/`
  ```bash
  cd backend
  pip install fastapi uvicorn[standard] websockets pydantic
  ```
  Create `requirements.txt`:
  ```
  fastapi>=0.110.0
  uvicorn[standard]>=0.29.0
  websockets>=12.0
  pydantic>=2.0
  ```
- [ ] Archive or remove `hud.py` (the old pygame + Flask app)
- [ ] Update `README.md` to reflect the new architecture

---

## 3. Phase 1 — FastAPI + WebSocket Server

**Goal:** Stand up the backend that serves the React build and handles WebSocket communication.

### 3.1 FastAPI App (`backend/main.py`)

```python
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse

app = FastAPI()

# Serve the built React frontend
app.mount("/assets", StaticFiles(directory="../frontend/dist/assets"), name="assets")

@app.get("/")
async def serve_frontend():
    return FileResponse("../frontend/dist/index.html")

@app.websocket("/ws")
async def websocket_endpoint(ws: WebSocket):
    await ws.accept()
    try:
        while True:
            data = await ws.receive_json()
            # Route to handlers based on message type
            await handle_message(ws, data)
    except WebSocketDisconnect:
        pass
```

### 3.2 WebSocket Connection Manager

Build a connection manager class that:
- [ ] Tracks the active Android client connection (only 1 expected)
- [ ] Tracks the HUD frontend connection (Chromium connects as a WS client)
- [ ] Routes messages: Android → Server → HUD Frontend
- [ ] Handles reconnection gracefully (Android disconnects/reconnects)
- [ ] Implements a heartbeat ping/pong (every 5 seconds)

### 3.3 Message Routing Logic

The server is primarily a **relay**:
1. Android sends a message (e.g., speed update, map frame, layout config)
2. Server validates the message envelope
3. Server forwards to the HUD frontend WebSocket

The server also maintains an **in-memory state store** so if the frontend reconnects (e.g., Chromium restarts), it can immediately receive the last known state.

### 3.4 Steps

- [ ] Implement `ConnectionManager` in `ws/manager.py`
- [ ] Define message handler dispatcher in `ws/handlers.py`
- [ ] Implement Pydantic models for all message types in `models/`
- [ ] Implement in-memory state store in `state/store.py`
- [ ] Add a health check endpoint: `GET /health`
- [ ] Test with a simple WebSocket client (e.g., `websocat` or Python script)

---

## 4. Phase 2 — React HUD Frontend

**Goal:** Build the HUD UI that connects to the backend via WebSocket and renders vehicle data.

### 4.1 WebSocket Hook

Create `useWebSocket.ts`:
- [ ] Connect to `ws://192.168.254.2:8000/ws` (or `ws://localhost:8000/ws` in dev)
- [ ] Auto-reconnect with exponential backoff (1s, 2s, 4s, max 10s)
- [ ] Parse incoming messages and dispatch to Zustand store
- [ ] Handle connection state (connected / disconnected / reconnecting)

### 4.2 Zustand State Store

Create `useHudStore.ts`:
```typescript
interface HudState {
  // Vehicle data
  speed: string;
  gpsSpeed: string;
  mpg: string;
  range: string;
  rpm: string;
  coolantTemp: string;
  // Navigation
  turn: string;
  distance: string;
  // Map
  mapFrame: string | null;  // base64 JPEG
  // Layout
  activePreset: LayoutPreset | null;
  // Connection
  isConnected: boolean;
}
```

### 4.3 HUD Components

Each component reads from the Zustand store and renders its piece of the HUD:

| Component | Data | Description |
|-----------|------|-------------|
| `SpeedGauge` | speed, gpsSpeed | Large speed display (OBD + GPS) |
| `NavigationPanel` | turn, distance | Turn-by-turn instruction |
| `MapTile` | mapFrame | Renders the latest map snapshot as an `<img>` |
| `OBDPanel` | rpm, coolantTemp | Engine data readouts |
| `FuelRange` | mpg, range | Fuel economy and range |
| `GPSSpeed` | gpsSpeed | Dedicated GPS speed (backup/comparison) |

### 4.4 Styling

- [ ] Dark background (`#000000`) — crucial for reflective HUD (black = transparent on film)
- [ ] Green/cyan/white text — high contrast on HUD film
- [ ] CSS glow effects (`text-shadow`) for readability
- [ ] No background colors on components — only text/icons that "float" on the windshield
- [ ] Large, bold fonts — must be readable at a glance while driving
- [ ] All sizing in `vw`/`vh` units to fill the 1280x720 viewport

### 4.5 Steps

- [ ] Install dependencies: `zustand`
- [ ] Implement WebSocket hook with auto-reconnect
- [ ] Implement Zustand store
- [ ] Build each HUD component
- [ ] Create the `HudCanvas` container that renders active components based on layout
- [ ] Style with HUD theme (green-on-black, glow, large fonts)
- [ ] Test with mock data

---

## 5. Phase 3 — Chromium Kiosk & Reflective Display

**Goal:** Launch Chromium fullscreen on the Pi, displaying the React app mirrored for windshield reflection.

### 5.1 CSS Mirror Transform

Since this is a **reflective HUD**, the entire display must be horizontally flipped so it reads correctly when reflected off the windshield.

In `mirror.css`:
```css
body {
  transform: scaleX(-1);
  overflow: hidden;
  margin: 0;
  padding: 0;
  width: 100vw;
  height: 100vh;
  background: #000;
}
```

> **Important:** Text, icons, and the map image will all render mirrored in the DOM but appear correctly on the windshield.

### 5.2 Chromium Kiosk Launch

Add to `scripts/start.sh`:
```bash
#!/bin/bash

# Start FastAPI backend
cd /home/pi/car-hud-pi/backend
uvicorn main:app --host 0.0.0.0 --port 8000 &
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
kill $BACKEND_PID
```

### 5.3 Disable Screen Blanking

The Pi must never turn off the screen:
```bash
# In /etc/xdg/lxsession/LXDE-pi/autostart or via xset
xset s off
xset -dpms
xset s noblank
```

### 5.4 Steps

- [ ] Add `mirror.css` with `scaleX(-1)` transform
- [ ] Create `start.sh` script
- [ ] Configure Pi to disable screen blanking
- [ ] Test fullscreen rendering at 1280x720
- [ ] Verify mirrored text is readable in reflection (use a mirror/phone camera to check)

---

## 6. Phase 4 — Android App Foundation

**Goal:** Set up the Kotlin Android app with USB tether communication and WebSocket client.

> **This will live in a separate folder/repo** (e.g., `car-hud-android/`) and be developed in Android Studio.

### 6.1 Project Setup

- [ ] Create new Android Studio project: **Kotlin + Jetpack Compose**
- [ ] Minimum SDK: API 26 (Android 8.0) — covers most devices
- [ ] Add dependencies:
  ```kotlin
  // build.gradle.kts (app)
  dependencies {
      // Compose (included with template)
      // WebSocket client
      implementation("com.squareup.okhttp3:okhttp:4.12.0")
      // Google Maps SDK
      implementation("com.google.android.gms:play-services-maps:18.2.0")
      // Location services
      implementation("com.google.android.gms:play-services-location:21.1.0")
      // Coroutines
      implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
      // JSON serialization
      implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
      // DataStore for saving presets
      implementation("androidx.datastore:datastore-preferences:1.0.0")
  }
  ```

### 6.2 Required Android Permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

### 6.3 USB Tether Detection

The Android app should:
- [ ] Detect when USB tethering is active
- [ ] Attempt WebSocket connection to `ws://192.168.254.2:8000/ws`
- [ ] Show connection status in the UI (connected / disconnected / error)
- [ ] Auto-reconnect when connection drops
- [ ] Optionally: allow the user to manually enter the Pi's IP (settings screen fallback)

### 6.4 WebSocket Client Service

Create a **foreground service** so the connection stays alive when the app is backgrounded:

```kotlin
class HudConnectionService : Service() {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null

    fun connect() {
        val request = Request.Builder()
            .url("ws://192.168.254.2:8000/ws")
            .build()
        webSocket = client.newWebSocket(request, HudWebSocketListener())
    }

    fun send(message: HudMessage) {
        webSocket?.send(Json.encodeToString(message))
    }
}
```

### 6.5 App Navigation Structure

```
App
├── Home Screen
│   ├── Connection status indicator
│   ├── Active preset display
│   ├── Quick actions (start/stop HUD)
│   └── Navigation to other screens
├── Preset Editor Screen
│   ├── Drag-and-drop layout canvas
│   ├── Component palette (available widgets)
│   ├── Save / Load / Delete presets (max 3)
│   └── Feature toggle switches
├── Map Screen
│   ├── Google Maps (full interaction)
│   ├── Destination input
│   └── Navigation start/stop
├── OBD-II Settings Screen
│   ├── Bluetooth device scanner
│   ├── OBD adapter pairing
│   └── Available PID selection
└── Settings Screen
    ├── Pi IP address (manual override)
    ├── Map streaming quality (low/med/high)
    ├── Data update frequency
    └── About / Debug info
```

### 6.6 Steps

- [ ] Create Android Studio project with Compose template
- [ ] Add all dependencies
- [ ] Declare permissions in `AndroidManifest.xml`
- [ ] Implement `HudConnectionService` (foreground service)
- [ ] Implement WebSocket client with OkHttp
- [ ] Build basic navigation scaffold (Home, Presets, Map, OBD, Settings)
- [ ] Test WebSocket connection to Pi over USB tether

---

## 7. Phase 5 — OBD-II & GPS Data Pipeline

**Goal:** Android app reads vehicle data from a Bluetooth OBD-II adapter and phone GPS, then streams it to the Pi.

### 7.1 Bluetooth OBD-II

**How it works:**
1. User pairs their ELM327-compatible OBD-II Bluetooth adapter in Android settings
2. App connects to the adapter via Bluetooth RFCOMM
3. App sends OBD PID requests and parses responses
4. Parsed data is packaged and sent over WebSocket

**Recommended library:** Use raw Bluetooth RFCOMM with ELM327 AT commands, or a library like [OBD-II Java API](https://github.com/pires/obd-java-api) adapted for Kotlin.

**Key OBD PIDs to support:**

| PID | Description | HUD Field |
|-----|-------------|-----------|
| `0x0D` | Vehicle Speed | `speed` |
| `0x0C` | Engine RPM | `rpm` |
| `0x05` | Coolant Temperature | `coolantTemp` |
| `0x5E` | Engine Fuel Rate | Used to compute `mpg` |
| `0x2F` | Fuel Tank Level | `fuelLevel` |
| `0x46` | Ambient Air Temp | `ambientTemp` (optional) |

**Update frequency:** Poll OBD every **500ms** (2 Hz). OBD-II Bluetooth adapters are slow (~100ms per PID), so batch requests carefully.

### 7.2 Phone GPS

Use Android's `FusedLocationProviderClient`:
- [ ] Request location updates every **1 second**
- [ ] Extract: latitude, longitude, speed, bearing
- [ ] GPS speed serves as a secondary/backup speed reading
- [ ] Bearing can be used for compass heading on HUD (optional)

### 7.3 Data Packaging

The Android app combines OBD + GPS data into a single message sent every **500ms**:

```json
{
  "type": "hud_data",
  "payload": {
    "speed": "65",
    "gpsSpeed": "64",
    "rpm": "2400",
    "coolantTemp": "195",
    "mpg": "28.5",
    "range": "320",
    "fuelLevel": "72",
    "turn": "Turn Right",
    "distance": "0.5 mi",
    "timestamp": 1708123456789
  }
}
```

### 7.4 Steps

- [ ] Implement Bluetooth OBD-II scanner/connector in Android app
- [ ] Implement ELM327 command protocol (AT commands + PID queries)
- [ ] Implement GPS location provider
- [ ] Create data packaging layer that merges OBD + GPS
- [ ] Send combined `hud_data` messages over WebSocket at 2 Hz
- [ ] Handle OBD disconnection gracefully (show "No OBD" on HUD)
- [ ] Handle GPS unavailability (indoor/tunnel)

---

## 8. Phase 6 — Google Maps Streaming

**Goal:** The Android app renders Google Maps, captures snapshots, and streams them to the HUD as image frames.

### 8.1 Approach: MapView Snapshot Streaming

Since we do **not** use the Google Maps API on the Pi (no internet on Pi), the Android app:
1. Renders a `MapView` using the Google Maps SDK
2. Periodically captures the map as a bitmap using `GoogleMap.snapshot()`
3. Compresses the bitmap to JPEG
4. Encodes as base64 and sends over WebSocket

### 8.2 Android Implementation

```kotlin
class MapStreamManager(private val mapView: MapView) {
    private var streaming = false
    private val captureInterval = 500L // ms (2 FPS)

    fun startStreaming(googleMap: GoogleMap, sendFrame: (String) -> Unit) {
        streaming = true
        CoroutineScope(Dispatchers.Main).launch {
            while (streaming) {
                googleMap.snapshot { bitmap ->
                    bitmap?.let {
                        val stream = ByteArrayOutputStream()
                        it.compress(Bitmap.CompressFormat.JPEG, 60, stream)
                        val base64 = Base64.encodeToString(
                            stream.toByteArray(), Base64.NO_WRAP
                        )
                        sendFrame(base64)
                    }
                }
                delay(captureInterval)
            }
        }
    }
}
```

### 8.3 WebSocket Message

```json
{
  "type": "map_frame",
  "payload": {
    "image": "<base64 JPEG string>",
    "width": 400,
    "height": 300,
    "timestamp": 1708123456789
  }
}
```

### 8.4 Frontend Rendering

In `MapTile.tsx`:
```tsx
const MapTile = () => {
  const mapFrame = useHudStore((s) => s.mapFrame);

  if (!mapFrame) return <div className="map-placeholder">No Map</div>;

  return (
    <img
      src={`data:image/jpeg;base64,${mapFrame}`}
      alt="Map"
      className="map-tile"
    />
  );
};
```

### 8.5 Performance Considerations

| Factor | Recommendation |
|--------|----------------|
| Frame rate | 2–3 FPS is sufficient for navigation; saves bandwidth |
| JPEG quality | 50–70% (balance clarity vs size) |
| Map tile resolution | 400×300 px for a small tile; 640×480 for larger display |
| Bandwidth | ~30–80 KB per frame at JPEG 60% → 60–240 KB/s at 2–3 FPS |
| USB 2.0 tether throughput | ~10–40 Mbps — more than enough |
| Latency | Expect ~200–400ms end-to-end (capture + compress + transmit + decode) |

### 8.6 Navigation Data

Separately from the map image, send **turn-by-turn text** as part of `hud_data`:
- The Android app can extract navigation info from Google Maps notifications using `NotificationListenerService`
- Or the user manually inputs destination, and the app uses the Google Directions API (requires internet — phone has cellular) to get route steps
- Turn instruction + distance to next turn are sent as structured text data

### 8.7 Steps

- [ ] Add Google Maps SDK to Android app (requires API key in `AndroidManifest.xml`)
- [ ] Implement `MapView` in a Compose-compatible way (use `AndroidView` wrapper)
- [ ] Implement snapshot capture loop
- [ ] Implement JPEG compression + base64 encoding
- [ ] Send `map_frame` messages over WebSocket
- [ ] Build `MapTile` React component to display frames
- [ ] Add quality/FPS setting in Android app settings
- [ ] Test latency and adjust quality/framerate
- [ ] Implement navigation data extraction (Directions API or Notification listener)

### 8.8 Important Notes

- **Google Maps API Key:** You need a Google Maps SDK API key for the Android app. This is free up to generous usage limits for mobile SDKs.
- **Google Maps ToS:** `GoogleMap.snapshot()` is an official SDK method intended for this kind of use. Personal/non-commercial use should be fine — but do not redistribute or cache map imagery.
- **Phone must have cellular data** for Maps to load tiles. The Pi itself needs no internet.

---

## 9. Phase 7 — Preset & Layout System

**Goal:** Users create up to 3 HUD layout presets on the Android app with drag-and-drop, and the active preset is sent to the Pi to control what and where components render.

### 9.1 Layout Data Model

```kotlin
// Android side
@Serializable
data class LayoutPreset(
    val id: Int,                    // 1, 2, or 3
    val name: String,               // "Highway", "City", "Minimal"
    val components: List<HudComponent>
)

@Serializable
data class HudComponent(
    val type: String,               // "speed", "map", "nav", "obd", "fuel", "gpsSpeed"
    val enabled: Boolean,           // Feature toggle
    val x: Float,                   // Grid position X (0.0 - 1.0, percentage)
    val y: Float,                   // Grid position Y (0.0 - 1.0, percentage)
    val width: Float,               // Width (0.0 - 1.0)
    val height: Float               // Height (0.0 - 1.0)
)
```

### 9.2 Android Preset Editor

The Preset Editor screen in the Android app:

1. **Canvas area** (phone screen) representing the 1280×720 HUD
   - Shows a preview of the HUD layout (simplified)
   - Components appear as draggable/resizable cards
2. **Component palette** (bottom sheet or side panel)
   - List of available HUD widgets: Speed, Map, Navigation, OBD, Fuel/Range, GPS Speed
   - Drag from palette onto canvas to add
   - Tap a placed component to toggle enabled/disabled or remove
3. **Preset slots** (top bar)
   - 3 tabs/buttons: Preset 1, Preset 2, Preset 3
   - Save/Load/Rename per slot
4. **Apply button** — sends the preset to the Pi immediately

**Jetpack Compose Drag & Drop:**
- Use `Modifier.pointerInput` with `detectDragGestures` for dragging
- Use `Modifier.onSizeChanged` + gesture offsets for positioning
- Store positions as percentages (0.0–1.0) for resolution independence

### 9.3 Sending Presets to Pi

When the user taps "Apply":
```json
{
  "type": "layout_config",
  "payload": {
    "presetId": 1,
    "name": "Highway",
    "components": [
      { "type": "speed", "enabled": true, "x": 0.05, "y": 0.1, "width": 0.25, "height": 0.35 },
      { "type": "map", "enabled": true, "x": 0.35, "y": 0.05, "width": 0.35, "height": 0.55 },
      { "type": "nav", "enabled": true, "x": 0.75, "y": 0.1, "width": 0.2, "height": 0.35 },
      { "type": "obd", "enabled": false, "x": 0, "y": 0, "width": 0, "height": 0 },
      { "type": "fuel", "enabled": true, "x": 0.05, "y": 0.55, "width": 0.25, "height": 0.35 },
      { "type": "gpsSpeed", "enabled": false, "x": 0, "y": 0, "width": 0, "height": 0 }
    ]
  }
}
```

### 9.4 React Dynamic Layout

In `DynamicGrid.tsx`:
```tsx
const DynamicGrid = () => {
  const preset = useHudStore((s) => s.activePreset);

  if (!preset) return <div className="awaiting-config">Awaiting config...</div>;

  return (
    <div className="hud-canvas">
      {preset.components
        .filter((c) => c.enabled)
        .map((c) => (
          <div
            key={c.type}
            className="hud-widget"
            style={{
              position: 'absolute',
              left: `${c.x * 100}%`,
              top: `${c.y * 100}%`,
              width: `${c.width * 100}%`,
              height: `${c.height * 100}%`,
            }}
          >
            <WidgetRenderer type={c.type} />
          </div>
        ))}
    </div>
  );
};
```

**No page refresh needed.** When a `layout_config` message arrives via WebSocket, the Zustand store updates and React re-renders the layout instantly. This is a live, reactive update — not an `npm run dev` restart.

### 9.5 Preset Persistence

- **On Android:** Save presets to `DataStore` (SharedPreferences successor). Presets survive app restarts.
- **On Pi:** The server holds the last-received preset in memory. If Chromium restarts, the server replays the last preset to the reconnecting frontend. Presets are NOT persisted on the Pi (the phone is the source of truth).

### 9.6 Steps

- [ ] Define `LayoutPreset` and `HudComponent` data classes (Android + TypeScript types)
- [ ] Build Preset Editor screen in Compose with drag-and-drop
- [ ] Implement preset save/load with DataStore
- [ ] Send `layout_config` message on "Apply"
- [ ] Build `DynamicGrid.tsx` that renders components based on preset
- [ ] Build `WidgetRenderer` that maps component type to React component
- [ ] Handle "no preset received yet" state on frontend
- [ ] Test with all 3 presets

---

## 10. Phase 8 — Feature Toggle System

**Goal:** Within each preset, users can enable/disable individual HUD features.

### 10.1 How It Works

This is already built into the preset system (the `enabled` boolean on each `HudComponent`). The Feature Toggle screen is a simpler alternative to the full drag-and-drop editor:

- List all HUD features with toggle switches
- Toggling a feature sets `enabled: true/false` in the active preset
- Changes are sent immediately to the Pi

### 10.2 Android UI

```
┌─────────────────────────────────┐
│  Feature Toggles — Preset 1     │
│─────────────────────────────────│
│  ● Speed Display        [ON ]   │
│  ● GPS Speed            [OFF]   │
│  ● Navigation           [ON ]   │
│  ● Google Maps          [ON ]   │
│  ● OBD-II Data          [OFF]   │
│  ● Fuel / Range         [ON ]   │
│─────────────────────────────────│
│  [ Apply to HUD ]              │
└─────────────────────────────────┘
```

### 10.3 Steps

- [ ] Add toggle switches in the Preset Editor screen (or a sub-screen)
- [ ] Wire toggles to the `enabled` field in the preset model
- [ ] Send updated `layout_config` on change
- [ ] Frontend already handles this via `filter(c => c.enabled)` in `DynamicGrid`

---

## 11. Phase 9 — System Integration & Boot

**Goal:** Make everything start automatically when the Pi boots.

### 11.1 Updated systemd Service

```ini
[Unit]
Description=Car HUD System
After=graphical.target network.target
Wants=network.target

[Service]
Type=simple
User=pi
Environment=DISPLAY=:0
ExecStartPre=/bin/sleep 5
ExecStart=/home/pi/car-hud-pi/scripts/start.sh
Restart=always
RestartSec=5

[Install]
WantedBy=graphical.target
```

### 11.2 Boot Sequence

```
Power On
  → Raspberry Pi OS boots
    → systemd starts hud.service
      → start.sh runs:
        1. Start uvicorn (FastAPI) on port 8000
        2. Wait 3 seconds for server ready
        3. Launch Chromium kiosk → http://localhost:8000
      → Frontend shows "Awaiting connection..." screen
        → User connects phone via USB
          → USB tether activates (usb0 → 192.168.254.x)
            → Android app connects WebSocket
              → HUD data starts flowing
              → User selects/applies preset
              → HUD renders live
```

### 11.3 Steps

- [ ] Update `hud.service` systemd unit
- [ ] Create `scripts/setup.sh` for first-time Pi setup:
  - Install Python deps
  - Build React frontend (`npm run build`)
  - Install Chromium if not present
  - Enable systemd service
  - Configure `dhcpcd.conf` for USB tethering
  - Disable screen blanking
- [ ] Test full boot-to-HUD sequence
- [ ] Test USB disconnect/reconnect recovery

---

## 12. Phase 10 — Testing & Hardening

### 12.1 Test Checklist

- [ ] **WebSocket:** Connect, disconnect, reconnect (Android app)
- [ ] **WebSocket:** Connect, disconnect, reconnect (Chromium frontend)
- [ ] **HUD Data:** OBD-II values render correctly
- [ ] **HUD Data:** GPS speed renders correctly
- [ ] **Map Streaming:** Map frames display with acceptable latency
- [ ] **Map Streaming:** Map stops gracefully when navigation ends
- [ ] **Presets:** All 3 presets save and load correctly
- [ ] **Presets:** Drag-and-drop positions are accurate
- [ ] **Feature Toggles:** Components appear/disappear correctly
- [ ] **Mirror:** Text reads correctly in windshield reflection
- [ ] **Boot:** Pi starts HUD automatically on power on
- [ ] **Recovery:** HUD recovers from phone disconnect
- [ ] **Recovery:** HUD recovers from OBD adapter disconnect
- [ ] **Performance:** No visible lag or memory leaks over 1+ hour runtime
- [ ] **Night/Day:** HUD is readable in daylight and at night

### 12.2 Error Handling

- [ ] No-connection state on frontend ("Awaiting phone connection...")
- [ ] OBD-II disconnected state ("No vehicle data")
- [ ] GPS unavailable state (tunnel, parking garage)
- [ ] Map unavailable state ("No map data")
- [ ] WebSocket reconnection with state replay

### 12.3 Performance Optimization

- [ ] React: Use `React.memo()` on HUD components to prevent unnecessary re-renders
- [ ] Map: Throttle frame updates to 2–3 FPS max
- [ ] Data: Batch OBD + GPS into single messages (already planned at 2 Hz)
- [ ] Images: Use appropriate JPEG quality (50–70%)
- [ ] Frontend: Avoid any DOM animations that cause layout thrashing

---

## 13. WebSocket Message Schema Reference

All messages follow this envelope:

```json
{
  "type": "<message_type>",
  "payload": { ... },
  "timestamp": 1708123456789
}
```

### Message Types

| Type | Direction | Description |
|------|-----------|-------------|
| `hud_data` | Android → Pi | Vehicle data (OBD + GPS + nav) |
| `map_frame` | Android → Pi | Base64 JPEG map snapshot |
| `layout_config` | Android → Pi | Active preset / layout definition |
| `connection_status` | Pi → Android | Acknowledgement / heartbeat |
| `request_state` | Pi Frontend → Server | Frontend requests last known state |
| `full_state` | Server → Pi Frontend | Server sends full state snapshot |

### `hud_data` Payload

```json
{
  "speed": "65",
  "gpsSpeed": "64",
  "rpm": "2400",
  "coolantTemp": "195",
  "mpg": "28.5",
  "range": "320",
  "fuelLevel": "72",
  "turn": "Turn Right on Main St",
  "distance": "0.5 mi",
  "timestamp": 1708123456789
}
```

### `map_frame` Payload

```json
{
  "image": "<base64 JPEG>",
  "width": 400,
  "height": 300,
  "timestamp": 1708123456789
}
```

### `layout_config` Payload

```json
{
  "presetId": 1,
  "name": "Highway",
  "components": [
    {
      "type": "speed",
      "enabled": true,
      "x": 0.05,
      "y": 0.1,
      "width": 0.25,
      "height": 0.35
    }
  ]
}
```

---

## 14. Known Limitations & Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| **Google Maps snapshot latency** | 200–400ms delay on map display | Acceptable for nav; turn-by-turn text is real-time |
| **Google Maps ToS** | Snapshot streaming is a gray area for redistribution | Personal use only; `snapshot()` is an official API method |
| **OBD-II Bluetooth speed** | ELM327 is slow (~100ms/PID); limits to 5–8 PIDs/sec | Prioritize critical PIDs (speed, RPM); poll less-critical ones less often |
| **USB tethering reliability** | Android may drop tether on sleep/lock | Use `WAKE_LOCK` + foreground service to keep connection alive |
| **Phone battery drain** | GPS + Bluetooth + Maps + USB tether = heavy power usage | USB tether charges the phone while connected (usually); warn user if battery-only |
| **Pi GPU/CPU load** | Chromium + WebSocket + map images | Pi 4 should handle this fine; avoid heavy CSS animations |
| **Reflective HUD brightness** | Hard to read in direct sunlight | Use max screen brightness; high-contrast colors; test with actual HUD film |
| **Chromium memory leaks** | Long-running browser sessions can leak | Add a periodic auto-refresh (every 6–12 hours) as a safety net |

---

## 15. Hardware & Software Requirements

### Hardware

| Item | Purpose |
|------|---------|
| Raspberry Pi 4 (2GB+ RAM) | HUD host |
| MicroSD card (16GB+) | Pi OS + application |
| HDMI display or HUD projector | 1280x720 output |
| HUD reflective film/screen | Windshield mount |
| USB-A to USB-C/Micro cable | Pi ↔ Phone tether |
| ELM327 Bluetooth OBD-II adapter | Vehicle data |
| Android phone | Controller + data source |
| Power supply (car 12V → 5V 3A) | Pi power in car |

### Software — Raspberry Pi

| Software | Version | Purpose |
|----------|---------|---------|
| Raspberry Pi OS (with desktop) | Bookworm+ | Base OS |
| Python | 3.11+ | FastAPI backend |
| Node.js | 20 LTS | Build React frontend |
| Chromium | Latest | Kiosk display |
| FastAPI | 0.110+ | WebSocket API server |
| Uvicorn | 0.29+ | ASGI server |

### Software — Android

| Software | Version | Purpose |
|----------|---------|---------|
| Android Studio | Latest | Development IDE |
| Kotlin | 1.9+ | App language |
| Jetpack Compose | Latest stable | UI framework |
| OkHttp | 4.12+ | WebSocket client |
| Google Maps SDK | 18.2+ | Map rendering |
| Min Android SDK | API 26 (8.0) | Compatibility floor |

---

## Implementation Order (Recommended)

```
Phase 0  ██████░░░░░░░░░░  Project setup & structure
Phase 1  ██████░░░░░░░░░░  FastAPI + WebSocket server
Phase 2  ████████░░░░░░░░  React HUD frontend (basic)
Phase 3  ██░░░░░░░░░░░░░░  Chromium kiosk + mirror CSS
Phase 4  ████████░░░░░░░░  Android app foundation + WebSocket
Phase 5  ██████████░░░░░░  OBD-II + GPS data pipeline
Phase 6  ████████████░░░░  Google Maps streaming
Phase 7  ██████████░░░░░░  Preset & layout system
Phase 8  ████░░░░░░░░░░░░  Feature toggles
Phase 9  ██████░░░░░░░░░░  Boot integration & systemd
Phase 10 ████████░░░░░░░░  Testing & hardening
```

> **Start with Phases 0–3** to get a working Pi-side HUD rendering mock data.
> Then **Phase 4** to establish the Android → Pi connection.
> Then **Phases 5–8** for features.
> Finally **Phases 9–10** for production readiness.
