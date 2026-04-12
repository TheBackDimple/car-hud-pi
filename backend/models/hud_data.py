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
    maneuver: str = ""
    eta: str = ""
    speedLimit: str = ""
    timestamp: int | None = Field(default=None, description="Unix timestamp ms")


class ObdDataPayload(BaseModel):
    """Payload for obd_data message (from Pi OBD reader)."""

    speed: str = ""
    rpm: str = ""
    coolantTemp: str = ""
    mpg: str = ""
    range: str = ""
    fuelLevel: str = ""
    timestamp: int | None = Field(default=None, description="Unix timestamp ms")


class GpsDataPayload(BaseModel):
    """Payload for gps_data message (from Android)."""

    gpsSpeed: str = ""
    turn: str = ""
    distance: str = ""
    maneuver: str = ""
    eta: str = ""
    speedLimit: str = ""
    timestamp: int | None = Field(default=None, description="Unix timestamp ms")
