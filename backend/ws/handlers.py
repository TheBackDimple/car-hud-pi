"""Message type handlers — validate, update state, route to HUD."""
import logging
from typing import Any

from fastapi import WebSocket

from backend.models.hud_data import HudDataPayload
from backend.models.layout import LayoutConfigPayload
from backend.state.store import store
from backend.ws.manager import manager

logger = logging.getLogger(__name__)


async def handle_message(websocket: WebSocket, data: dict[str, Any]) -> None:
    """
    Route incoming message based on type.
    Android messages: validate, update store, forward to HUD.
    HUD messages: handle request_state.
    """
    msg_type = data.get("type", "")
    payload = data.get("payload", {})
    timestamp = data.get("timestamp")

    # Build envelope for forwarding (preserve original)
    envelope = {"type": msg_type, "payload": payload, "timestamp": timestamp}

    # HUD frontend requests state on reconnect
    if msg_type == "request_state":
        await manager.send_full_state_to_hud()
        return

    # Messages from Android — validate, store, forward to HUD
    if manager.is_android(websocket):
        if msg_type == "hud_data":
            try:
                validated = HudDataPayload.model_validate(payload)
                store.update_hud_data(validated.model_dump())
            except Exception as e:
                logger.warning("Invalid hud_data: %s", e)
                return
        elif msg_type == "map_frame":
            store.update_map_frame(payload)
        elif msg_type == "layout_config":
            try:
                validated = LayoutConfigPayload.model_validate(payload)
                store.update_layout_config(validated.model_dump())
            except Exception as e:
                logger.warning("Invalid layout_config: %s", e)
                return
        else:
            logger.debug("Unknown message type from Android: %s", msg_type)
            # Still forward unknown types in case we add new ones
        await manager.send_to_hud(envelope)
    else:
        # Message from HUD (or unregistered) — only request_state is handled above
        logger.debug("Ignoring message type %s from non-Android client", msg_type)
