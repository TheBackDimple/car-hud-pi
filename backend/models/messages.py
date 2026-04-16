"""WebSocket message envelope and payload models."""
from typing import Any

from pydantic import BaseModel, Field

from backend.models.hud_data import HudDataPayload
from backend.models.layout import LayoutConfigPayload


class MessageEnvelope(BaseModel):
    """All WebSocket messages follow this envelope."""

    type: str = Field(..., description="Message type for routing")
    payload: dict[str, Any] = Field(default_factory=dict)
    timestamp: int | None = Field(default=None, description="Unix timestamp ms")


# --- Outgoing message payloads (Server → Client) ---


class ConnectionStatusPayload(BaseModel):
    """Heartbeat / acknowledgement."""

    status: str = "ok"


class FullStatePayload(BaseModel):
    """Full state snapshot for frontend on reconnect."""

    hud_data: dict[str, Any] | None = None
    map_frame: dict[str, Any] | None = None
    layout_config: dict[str, Any] | None = None
    android_connected: bool = False
    hud_mirror: bool | None = None


# Re-export payload models for handler use
__all__ = [
    "MessageEnvelope",
    "HudDataPayload",
    "LayoutConfigPayload",
    "ConnectionStatusPayload",
    "FullStatePayload",
]
