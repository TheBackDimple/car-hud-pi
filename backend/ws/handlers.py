"""Message type handlers — validate, update state, route to HUD."""
import logging
from typing import Any

from fastapi import WebSocket

from backend.models.hud_data import GpsDataPayload, HudDataPayload, ObdDataPayload
from backend.models.layout import LayoutConfigPayload
from backend.state.store import store
from backend.ws.manager import manager

logger = logging.getLogger(__name__)


async def _broadcast_hud_data() -> None:
    """Broadcast merged hud_data to HUD frontend."""
    hud = store.get_full_state()["hud_data"]
    if hud:
        msg = {"type": "hud_data", "payload": hud, "timestamp": hud.get("timestamp")}
        await manager.send_to_hud(msg)


async def handle_message(websocket: WebSocket, data: dict[str, Any]) -> None:
    """
    Route incoming message based on type.
    Android: gps_data, hud_data, map_frame, layout_config, hud_mirror, theme_config.
    OBD reader: obd_data.
    HUD: request_state.
    """
    msg_type = data.get("type", "")
    payload = data.get("payload", {})
    timestamp = data.get("timestamp")

    # HUD frontend requests state on reconnect
    if msg_type == "request_state":
        await manager.send_full_state_to_hud()
        return

    # Messages from OBD reader (Pi-side)
    if manager.is_obd(websocket):
        if msg_type == "obd_data":
            try:
                validated = ObdDataPayload.model_validate(payload)
                store.update_obd_data(validated.model_dump())
                await _broadcast_hud_data()
            except Exception as e:
                logger.warning("Invalid obd_data: %s", e)
        return

    # Messages from Android
    if manager.is_android(websocket):
        if msg_type == "gps_data":
            try:
                validated = GpsDataPayload.model_validate(payload)
                store.update_gps_data(validated.model_dump())
                await _broadcast_hud_data()
            except Exception as e:
                logger.warning("Invalid gps_data: %s", e)
        elif msg_type == "hud_data":
            # Full hud_data (legacy when no Pi OBD, or Android sends all)
            try:
                validated = HudDataPayload.model_validate(payload)
                store.update_hud_data(validated.model_dump())
                await _broadcast_hud_data()
            except Exception as e:
                logger.warning("Invalid hud_data: %s", e)
        elif msg_type == "map_frame":
            store.update_map_frame(payload)
            await manager.send_to_hud({"type": "map_frame", "payload": payload, "timestamp": timestamp})
        elif msg_type == "map_clear":
            store.clear_map_frame()
            await manager.send_to_hud(
                {"type": "map_frame", "payload": {"image": None}, "timestamp": timestamp}
            )
            if payload.get("trip_ended"):
                await manager.send_to_hud(
                    {
                        "type": "hud_notice",
                        "payload": {"message": "Trip Ended"},
                        "timestamp": timestamp,
                    }
                )
        elif msg_type == "layout_config":
            try:
                validated = LayoutConfigPayload.model_validate(payload)
                store.update_layout_config(validated.model_dump())
                await manager.send_to_hud({"type": "layout_config", "payload": validated.model_dump(), "timestamp": timestamp})
            except Exception as e:
                logger.warning("Invalid layout_config: %s", e)
        elif msg_type == "theme_config":
            await manager.send_to_hud({"type": "theme_config", "payload": payload, "timestamp": timestamp})
        elif msg_type == "hud_mirror":
            mirror = payload.get("mirror")
            if isinstance(mirror, bool):
                store.set_hud_mirror(mirror)
                await manager.send_to_hud(
                    {
                        "type": "hud_mirror",
                        "payload": {"mirror": mirror},
                        "timestamp": timestamp,
                    }
                )
        else:
            logger.debug("Unknown message type from Android: %s", msg_type)
        return

    # Message from HUD or unknown — ignore
    logger.debug("Ignoring message type %s from non-Android/OBD client", msg_type)
