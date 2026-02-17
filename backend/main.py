"""
Car HUD — FastAPI backend
Serves React frontend and handles WebSocket communication.
"""
from pathlib import Path

from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

from backend.ws.handlers import handle_message
from backend.ws.manager import ConnectionManager, ConnectionRole, manager

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
    conn_role = ConnectionRole(role) if role in ("android", "hud") else ConnectionRole.HUD
    await manager.connect(websocket, conn_role)

    try:
        while True:
            data = await websocket.receive_json()
            await handle_message(websocket, data)
    except WebSocketDisconnect:
        pass
    finally:
        manager.disconnect(websocket)
