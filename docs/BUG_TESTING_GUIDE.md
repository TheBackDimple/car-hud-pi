# Bug Testing Guide — A (TLS/WebSocket) & C (Cross-checks)

Detailed step-by-step instructions for validating Bug 1 (TLS/WebSocket Connect) and regression cross-checks.

---

## Prerequisites

| Item | Notes |
|------|------|
| **Raspberry Pi** | Running the HUD stack (backend + frontend). Default Pi IP: `192.168.254.2` |
| **Android device** | Physical device or emulator. Emulator must be on same network as Pi (or use `10.0.2.2` for localhost if Pi runs on host). |
| **Network** | Pi and Android on same LAN (Wi‑Fi). |
| **Certs** | `scripts/start.sh` auto-generates self-signed certs in `certs/` if missing. |

**Emulator vs physical device:**  
- **Emulator:** Use Pi’s LAN IP (e.g. `192.168.254.2`). If Pi runs on your dev machine, you may need port forwarding or a real Pi.  
- **Physical device:** Same Wi‑Fi as Pi; use Pi’s IP. Debug builds trust self-signed certs; release builds need a trusted cert or user-installed CA.

---

## A) TLS / WebSocket Connect (Bug 1)

### A1. No CLEARTEXT Error on Connect

**Goal:** Confirm the app connects over `wss://` without the cleartext policy error.

**Steps:**

1. **Start the Pi stack**
   ```bash
   cd /path/to/car-hud-pi
   ./scripts/start.sh
   ```
   - Backend should start with TLS (look for `uvicorn ... --ssl-keyfile ... --ssl-certfile ...`).
   - Chromium should open `https://localhost:8000`.
   - If certs are missing, `start.sh` generates them in `certs/`.

2. **Launch the Android app** (debug build).

3. **Open the Connect flow**
   - From the Home screen, tap **Connect** (or **Start Trip** if Connect is disabled).
   - Grant location permission if prompted.

4. **Check for errors**
   - **Pass:** Status card shows **"Connected"** (green).
   - **Fail:** Status card shows **"Error: CLEARTEXT communication to 192.168.254.2 not permitted..."** or similar.

**Troubleshooting:**
- If you see CLEARTEXT: app is still using `ws://`. Verify `HudConnectionService.kt` uses `wss://$piHost:8000/ws?role=android`.
- If you see SSL/certificate errors: debug build should trust self-signed. Ensure `ApplicationInfo.FLAG_DEBUGGABLE` is set (debug builds only).
- If "Connecting…" never changes: check Pi IP in Settings (default `192.168.254.2`), firewall, and that backend is listening on `0.0.0.0:8000`.

---

### A2. Foreground Service Stays Connected (20–30s)

**Goal:** Ensure the foreground service keeps the connection alive and doesn’t drop.

**Steps:**

1. Connect as in A1.
2. Leave the Home screen open (do not background the app).
3. Wait **20–30 seconds**.
4. Observe the status card.

**Pass:** Status remains **"Connected"** (green) for the full duration.  
**Fail:** Status changes to **"Disconnected"** or **"Error"** without user action.

**Troubleshooting:**
- OkHttp `pingInterval(20, TimeUnit.SECONDS)` keeps the WebSocket alive.
- If it drops: check Pi logs for WebSocket errors; verify no firewall/proxy closing idle connections.

---

### A3. Disconnect Stops Service

**Goal:** Confirm Disconnect cleanly stops the service and updates state.

**Steps:**

1. Connect as in A1.
2. Tap **Disconnect**.
3. Check:
   - Status card shows **"Disconnected"** (gray).
   - Foreground notification disappears (if shown).
   - Connect button becomes enabled again.

**Pass:** All of the above.  
**Fail:** Status still shows "Connected", notification remains, or Connect stays disabled.

---

### A4. Reconnection After Network Interruption

**Goal:** Verify the app reconnects when the Pi’s network comes back.

**Steps:**

1. Connect as in A1.
2. **Simulate network loss:** On the Pi, disable Wi‑Fi (e.g. `sudo rfkill block wifi` or disable in GUI).
3. Wait 5–10 seconds. Status should change to **"Disconnected"** or **"Error"**.
4. **Restore network:** Re-enable Pi Wi‑Fi.
5. Wait up to **~10 seconds** (reconnect delay is 5s in `HudConnectionService`).

**Pass:** Status returns to **"Connected"** without tapping Connect again.  
**Fail:** Status stays disconnected; user must tap Connect manually.

**Note:** `scheduleReconnect()` only runs when state is `Disconnected` or `Error`. If the failure path doesn’t update state correctly, reconnect may not trigger.

---

### A5. Pi Side — HTTPS and Backend Startup

**Goal:** Confirm the Pi stack starts correctly with TLS and serves the HUD.

**Steps:**

1. Run `./scripts/start.sh` from project root.
2. **Check backend startup**
   - No immediate TLS/SSL errors in the terminal.
   - uvicorn binds to `0.0.0.0:8000` with `--ssl-keyfile` and `--ssl-certfile`.
3. **Check Chromium**
   - Opens `https://localhost:8000`.
   - HUD page loads (no certificate warning in kiosk mode due to `--ignore-certificate-errors`).
4. **Check cert generation**
   - If `certs/localhost-key.pem` and `certs/localhost-cert.pem` were missing, they are created.
   - SAN should include `DNS:localhost`, `IP:127.0.0.1`, `IP:192.168.254.2` if OpenSSL supports `-addext`.

**Pass:** Backend starts cleanly; Chromium shows HUD.  
**Fail:** SSL errors on startup; Chromium fails to load; or cert generation fails (e.g. no `openssl`).

---

### A6. Android ↔ Pi WebSocket (role=android)

**Goal:** Confirm Android messages are received and relayed to the HUD.

**Steps:**

1. Connect Android to Pi (A1).
2. **Trigger data flow**
   - Tap **Start Trip** and open the Map screen (starts GPS streaming).
   - Or go to **Preset Editor** → edit a preset → tap **Apply to HUD**.
3. **Verify on HUD (Chromium on Pi)**
   - HUD shows updated data (e.g. speed, GPS, or layout changes).
4. **Optional: script test**
   ```bash
   USE_TLS=1 python scripts/test_websocket.py
   ```
   - Connects as HUD and Android; sends `hud_data`; checks `request_state` response.

**Pass:** HUD updates when Android sends data; test script passes.  
**Fail:** HUD does not update; test script fails; or WebSocket closes unexpectedly.

---

## C) Cross-checks (Regression)

### C1. Connect Flow + HUD Update

**Goal:** Ensure HUD rendering still works after TLS changes (no regression).

**Steps:**

1. Start Pi with `./scripts/start.sh`.
2. Connect Android (A1).
3. Tap **Start Trip** → Map screen (starts GPS).
4. On the HUD (Chromium):
   - Speed/GPS values update (if GPS is available).
   - Map tile or placeholder is visible.
5. Toggle a feature (e.g. OBD) and confirm HUD reflects it.

**Pass:** HUD receives and displays data from Android over `wss://`.  
**Fail:** HUD stays blank or shows stale data; WebSocket never connects.

---

### C2. Preset Apply Flow

**Goal:** Verify `layout_config` applies correctly after TLS change.

**Steps:**

1. Connect Android to Pi.
2. Open **Preset Editor**.
3. **Edit layout**
   - Drag a block (e.g. fuel) to a new position.
   - Tap **Save** (optional).
   - Tap **Apply to HUD**.
4. **Check HUD (Chromium)**
   - Layout matches the editor (blocks in new positions).
   - No overlap; map in center.

**Pass:** HUD layout updates to match the preset.  
**Fail:** HUD layout unchanged; wrong positions; or `layout_config` not received.

---

### C3. Feature Toggles

**Goal:** Confirm toggles still work in editor and on HUD.

**Steps:**

1. Connect Android.
2. Open **Preset Editor** (or **Feature Toggles** screen).
3. **Toggle a component** (e.g. disable "OBD-II").
4. **Check editor**
   - OBD block disappears from the canvas.
5. **Check HUD**
   - OBD widget disappears (or is hidden) on the HUD.
6. **Re-enable** the component.
7. **Check again**
   - Block reappears in editor and on HUD.

**Pass:** Toggles update both editor and HUD.  
**Fail:** Toggle has no effect; editor and HUD out of sync.

---

## Quick Reference

| Test | Pass Criteria |
|------|---------------|
| A1 | No CLEARTEXT error; status = Connected |
| A2 | Stays Connected 20–30s |
| A3 | Disconnect → Disconnected, notification gone |
| A4 | Reconnects after Pi Wi‑Fi off/on |
| A5 | Pi starts with TLS; HUD loads in Chromium |
| A6 | Android messages reach HUD |
| C1 | HUD renders data after Connect |
| C2 | layout_config applies to HUD |
| C3 | Feature toggles update editor + HUD |

---

## Environment Notes

**Emulator only:**  
- Pi IP: use your Pi’s LAN IP or host machine IP if Pi runs there.  
- Certificate: debug build trusts self-signed.  
- `adb logcat` helps debug connection/SSL errors.

**Physical device:**  
- Same Wi‑Fi as Pi.  
- Debug build: trusts self-signed.  
- Release build: needs a trusted cert or user-installed CA for `wss://`.
