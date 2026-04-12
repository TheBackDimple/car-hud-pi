"""
Car HUD — FastAPI backend
Serves React frontend and handles WebSocket communication.
"""
from pathlib import Path

from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

from backend.state.store import store
from backend.ws.handlers import _broadcast_hud_data, handle_message
from backend.ws.manager import ConnectionRole, manager

app = FastAPI(title="Car HUD API")

# Resolve frontend dist path (relative to this file)
_BACKEND_DIR = Path(__file__).resolve().parent
_FRONTEND_DIST = _BACKEND_DIR.parent / "frontend" / "dist"


@app.get("/health")
async def health():
    """Health check endpoint."""
    return {"status": "ok"}


# Serve built React frontend (only if dist exists)
if _FRONTEND_DIST.exists():
    app.mount("/assets", StaticFiles(directory=_FRONTEND_DIST / "assets"), name="assets")

    @app.get("/")
    async def serve_frontend():
        return FileResponse(_FRONTEND_DIST / "index.html")
else:
    # Dev fallback when frontend not built
    @app.get("/")
    async def serve_frontend():
        return {"message": "Build frontend with: cd frontend && npm run build"}


@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket, role: str = "hud"):
    """
    WebSocket endpoint. Clients identify via query param:
    - ?role=android — Android phone (data source)
    - ?role=hud — HUD frontend (Chromium display)
    """
    role_map = {"android": ConnectionRole.ANDROID, "hud": ConnectionRole.HUD, "obd": ConnectionRole.OBD}
    conn_role = role_map.get(role, ConnectionRole.HUD)
    await manager.connect(websocket, conn_role)
    was_android = conn_role == ConnectionRole.ANDROID

    try:
        while True:
            data = await websocket.receive_json()
            await handle_message(websocket, data)
    except WebSocketDisconnect:
        pass
    finally:
        manager.disconnect(websocket)
        if was_android:
            store.clear_android_contribution()
            await manager.send_to_hud(
                {
                    "type": "map_frame",
                    "payload": {"image": None},
                    "timestamp": None,
                }
            )
            await _broadcast_hud_data()
            await manager.send_android_status(False)
