# Android App — Bug Review & Implementation Plan

> **Purpose:** Shareable plan for fixing Android app bugs and achieving Phase 7 preset editor parity. Based on review against [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md).

---

## Summary of Bugs Found

### Bug 1: WebSocket Connect Fails — CLEARTEXT Not Permitted

**Symptom:** After tapping Connect and allowing permissions, the app shows:

```
Error: CLEARTEXT communication to 192.168.254.2 not permitted by network security policy
```

**Root cause:** The Android app uses `ws://$piHost:8000/ws` (unencrypted HTTP/WebSocket). Android 9+ blocks cleartext traffic by default unless explicitly opted in. The app has no network security config allowing cleartext.

**Files involved:**
- [hud-android/CarHud/app/src/main/java/com/example/carhud/service/HudConnectionService.kt](hud-android/CarHud/app/src/main/java/com/example/carhud/service/HudConnectionService.kt) — WebSocket URL and OkHttp client
- [hud-android/CarHud/app/src/main/AndroidManifest.xml](hud-android/CarHud/app/src/main/AndroidManifest.xml) — no `networkSecurityConfig` or `usesCleartextTraffic`

---

### Bug 2: Preset Editor — Cannot Drag Components Across Sides

**Symptom:** Dragging HUD components (e.g., fuel card) from left to right does not work on the emulator. Components do not move as expected.

**Root causes (identified in code):**

1. **Unstable gesture key:** `pointerInput(component)` uses the whole mutable `HudComponent` object as the key. When state updates during a drag, the composable recomposes and the key changes, tearing down the gesture handler mid-drag.

2. **Wrong drag normalization:** The drag delta is divided by the **Card’s** size (`size.width`, `size.height` from the pointer scope) instead of the **canvas** size. Positions are stored as 0.0–1.0 (percentage of 1280×720 canvas), so the math is incorrect — small cards would require huge drag distances to move a small percentage.

**File involved:**
- [hud-android/CarHud/app/src/main/java/com/example/carhud/ui/screens/PresetEditorScreen.kt](hud-android/CarHud/app/src/main/java/com/example/carhud/ui/screens/PresetEditorScreen.kt) — `DraggableComponent` composable

---

### Bug 3: Phase 7 Preset Editor Parity Gaps

**Plan expectation (Phase 7):** Preset Editor should have:
- Draggable **and** resizable cards
- Component palette (drag/tap to add)
- Tap a placed component to toggle enabled/disabled or remove

**Current state:**
- Dragging exists but is broken (see Bug 2)
- No resizing — cards have fixed width/height
- No component palette — all 6 components are always present; only toggles in a separate list
- No tap-to-toggle/remove on placed cards — must use Feature Toggles screen

---

## Implementation Plan (Staged / Branch-Level)

Work is split into milestones so teammates can divide and conquer.

---

### Milestone A: Secure WebSocket Connect (Fix Screenshot Error)

**Goal:** Use `wss://` (TLS) instead of `ws://` so Android no longer hits the cleartext policy. Use self-signed cert for local Pi/dev (no paid cloud).

**Tasks:**

1. **Pi backend — serve TLS**
   - Update [scripts/start.sh](scripts/start.sh) to:
     - Generate self-signed certs in `certs/` if missing (e.g. `openssl req -x509 ...`)
     - Run uvicorn with `--ssl-keyfile` and `--ssl-certfile`
   - Chromium in start.sh should load `https://localhost:8000` with `--ignore-certificate-errors` for local self-signed

2. **Android — use wss and trust self-signed in debug**
   - In [HudConnectionService.kt](hud-android/CarHud/app/src/main/java/com/example/carhud/service/HudConnectionService.kt):
     - Change URL from `ws://$piHost:8000/ws?role=android` to `wss://$piHost:8000/ws?role=android`
     - For `BuildConfig.DEBUG`: configure OkHttp to trust all certs (custom `X509TrustManager` + `SSLContext`) so emulator/dev can connect to self-signed Pi
     - Release builds will require a properly trusted cert (or user-installed CA)

3. **Other clients**
   - [scripts/obd_reader.py](scripts/obd_reader.py): default `HUD_WS_URL` or `--ws-url` to `wss://127.0.0.1:8000/ws?role=obd` when TLS is used
   - [frontend/src/hooks/useWebSocket.ts](frontend/src/hooks/useWebSocket.ts): use `wss://` when `window.location.protocol === 'https:'`

**Verification:** Connect flow works; no cleartext error.

---

### Milestone B: Fix Preset Dragging (Components Move Across Canvas)

**Goal:** Dragging a component (e.g. fuel card) from left to right works correctly.

**Tasks:**

1. **Stable gesture key**
   - In [PresetEditorScreen.kt](hud-android/CarHud/app/src/main/java/com/example/carhud/ui/screens/PresetEditorScreen.kt) `DraggableComponent`:
     - Change `pointerInput(component)` to `pointerInput(component.type)` so the key is stable (string) and does not change on recomposition

2. **Correct drag normalization**
   - Pass canvas dimensions into the drag handler (or use `LocalDensity` + `canvasWidth.toPx()` / `canvasHeight.toPx()`)
   - Normalize `dragAmount.x` and `dragAmount.y` by **canvas** pixel dimensions, not the Card’s `size`
   - `onPositionChange(dragAmount.x / canvasWidthPx, dragAmount.y / canvasHeightPx)`

**Verification:** Move fuel card from left to right; x/y persists correctly.

---

### Milestone C: Implement Resizing (Phase 7 Sub-Feature)

**Goal:** Users can resize placed components (width/height in 0.0–1.0).

**Tasks:**

1. Add resize handles (e.g. corner or edge) on each `DraggableComponent`
2. Use `detectDragGestures` (or similar) on the handle to update `component.width` and `component.height`
3. Clamp so the component stays within the canvas and maintains min size
4. Ensure drag still works after resizing

**Verification:** Resize a card; width/height update; boundaries respected.

---

### Milestone D: Add Palette + Tap-to-Toggle/Remove (Remaining Phase 7 Parity)

**Goal:** Component palette and tap actions on placed cards.

**Tasks:**

1. **Component palette**
   - Add a palette UI (bottom sheet or side panel) listing: Speed, Map, Nav, OBD, Fuel/Range, GPS Speed
   - Tapping a palette item enables/places the component on the canvas (or adds it if not present)
   - Align with current model: all 6 components exist; `enabled` controls visibility

2. **Tap on placed card**
   - Tap: toggle `enabled` (or show context menu: enable/disable, remove)
   - Remove: set `enabled = false` and optionally zero out x/y/width/height

3. **Apply to HUD**
   - Ensure `Apply to HUD` still sends `layout_config` with updated preset

**Verification:** Add/enable/disable/remove via palette and tap; apply to HUD; layout updates.

---

### Milestone E: Verification Checklist (Manual)

- [ ] **Connect:** Connect/disconnect works; no cleartext error
- [ ] **Drag:** Move fuel card from left to right; x/y persists
- [ ] **Resize:** Adjust width/height; boundaries respected
- [ ] **Palette / tap:** Add, toggle, remove; apply to HUD; layout reflects changes

---

## Feature Parity vs IMPLEMENTATION_PLAN.md

| Phase | Plan expectation | Current Android state |
|-------|------------------|------------------------|
| Phase 4 | WebSocket to Pi, foreground service | Implemented; connect fails due to cleartext |
| Phase 5 | GPS data from phone | Implemented (LocationDataProvider) |
| Phase 6 | Maps snapshot, map_frame | Implemented (MapStreamManager, MapScreen) |
| Phase 7 | Preset editor: drag, resize, palette, tap | Drag broken; no resize; no palette; no tap-on-card |
| Phase 8 | Feature toggles | Implemented (FeatureToggleScreen, toggles in PresetEditor) |

---

## Optional Follow-Ups (Not in Current Plan)

- Exponential backoff for WebSocket reconnect
- Navigation data extraction (turn/distance from Directions API or NotificationListenerService)
- WAKE_LOCK for connection when screen off (mentioned in plan’s Known Limitations)
