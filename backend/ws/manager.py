"""WebSocket connection manager.

Tracks Android client and HUD frontend connections.
Routes messages: Android → Server → HUD Frontend.
Implements heartbeat ping/pong every 5 seconds.
"""
import asyncio
import json
import logging
from enum import Enum
from typing import Any

from fastapi import WebSocket

from backend.models.messages import ConnectionStatusPayload, FullStatePayload
from backend.state.store import store

logger = logging.getLogger(__name__)


class ConnectionRole(str, Enum):
    """Identifies the type of WebSocket client."""

    ANDROID = "android"
    HUD = "hud"
    OBD = "obd"


class ConnectionManager:
    """Manages WebSocket connections and message routing."""

    def __init__(self) -> None:
        self._android: WebSocket | None = None
        self._hud: WebSocket | None = None
        self._obd: WebSocket | None = None
        self._heartbeat_task: asyncio.Task[None] | None = None

    def is_android(self, websocket: WebSocket) -> bool:
        """True if this websocket is the Android client."""
        return self._android is websocket

    def is_obd(self, websocket: WebSocket) -> bool:
        """True if this websocket is the OBD reader (Pi-side)."""
        return self._obd is websocket

    async def connect(
        self, websocket: WebSocket, role: ConnectionRole
    ) -> None:
        """Accept connection and register by role."""
        await websocket.accept()
        if role == ConnectionRole.ANDROID:
            if self._android:
                await self._android.close()
                logger.info("Replaced existing Android connection")
            self._android = websocket
            logger.info("Android client connected")
        elif role == ConnectionRole.OBD:
            if self._obd:
                await self._obd.close()
                logger.info("Replaced existing OBD reader connection")
            self._obd = websocket
            logger.info("OBD reader connected")
        else:
            if self._hud:
                await self._hud.close()
                logger.info("Replaced existing HUD connection")
            self._hud = websocket
            logger.info("HUD frontend connected")
            # Start heartbeat when HUD connects (keeps connection alive)
            self._start_heartbeat()

        if role == ConnectionRole.ANDROID:
            await self.send_android_status(True)

    def disconnect(self, websocket: WebSocket) -> None:
        """Unregister connection."""
        if self._android == websocket:
            self._android = None
            logger.info("Android client disconnected")
        if self._obd == websocket:
            self._obd = None
            logger.info("OBD reader disconnected")
        if self._hud == websocket:
            self._hud = None
            self._stop_heartbeat()
            logger.info("HUD frontend disconnected")

    def _start_heartbeat(self) -> None:
        """Start background heartbeat task."""
        self._stop_heartbeat()
        self._heartbeat_task = asyncio.create_task(self._heartbeat_loop())

    def _stop_heartbeat(self) -> None:
        """Stop heartbeat task."""
        if self._heartbeat_task:
            self._heartbeat_task.cancel()
            self._heartbeat_task = None

    async def _heartbeat_loop(self) -> None:
        """Send connection_status every 5 seconds to connected clients."""
        try:
            while True:
                await asyncio.sleep(5)
                msg = {
                    "type": "connection_status",
                    "payload": ConnectionStatusPayload().model_dump(),
                    "timestamp": None,
                }
                await self._broadcast(msg)
        except asyncio.CancelledError:
            pass

    async def _broadcast(self, message: dict[str, Any]) -> None:
        """Send message to all connected clients."""
        payload = json.dumps(message)
        for ws in (self._android, self._hud):
            if ws:
                try:
                    await ws.send_text(payload)
                except Exception as e:
                    logger.warning("Broadcast failed: %s", e)

    async def send_to_hud(self, message: dict[str, Any]) -> None:
        """Forward message to HUD frontend only."""
        if not self._hud:
            return
        try:
            await self._hud.send_json(message)
        except Exception as e:
            logger.warning("Send to HUD failed: %s", e)

    async def send_android_status(self, connected: bool) -> None:
        """Notify HUD whether the Android data client is connected."""
        await self.send_to_hud(
            {
                "type": "android_status",
                "payload": {"connected": connected},
                "timestamp": None,
            }
        )

    async def send_to_android(self, message: dict[str, Any]) -> None:
        """Send message to Android client only."""
        if not self._android:
            return
        try:
            await self._android.send_json(message)
        except Exception as e:
            logger.warning("Send to Android failed: %s", e)

    async def send_full_state_to_hud(self) -> None:
        """Send last known state to HUD (on reconnect)."""
        state = store.get_full_state()
        payload = {**state, "android_connected": self.has_android()}
        msg = {
            "type": "full_state",
            "payload": FullStatePayload(**payload).model_dump(),
            "timestamp": None,
        }
        await self.send_to_hud(msg)

    def has_android(self) -> bool:
        return self._android is not None

    def has_hud(self) -> bool:
        return self._hud is not None


# Singleton instance
manager = ConnectionManager()
