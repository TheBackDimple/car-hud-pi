"""Pydantic models for HUD vehicle data."""

from pydantic import BaseModel, Field


class HudDataPayload(BaseModel):
    """Payload for hud_data message (OBD + GPS + nav)."""

    speed: str = ""
    gpsSpeed: str = ""
    rpm: str = ""
    coolantTemp: str = ""
    mpg: str = ""
    range: str = ""
    fuelLevel: str = ""
    turn: str = ""
    distance: str = ""
    timestamp: int | None = Field(default=None, description="Unix timestamp ms")
