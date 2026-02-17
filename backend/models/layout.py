"""Pydantic models for layout presets and components."""

from pydantic import BaseModel, Field


class HudComponent(BaseModel):
    """A single HUD widget in the layout."""

    type: str = Field(..., description="speed, map, nav, obd, fuel, gpsSpeed")
    enabled: bool = True
    x: float = Field(0.0, ge=0.0, le=1.0)
    y: float = Field(0.0, ge=0.0, le=1.0)
    width: float = Field(0.0, ge=0.0, le=1.0)
    height: float = Field(0.0, ge=0.0, le=1.0)


class LayoutConfigPayload(BaseModel):
    """Payload for layout_config message."""

    presetId: int = 1
    name: str = ""
    components: list[HudComponent] = Field(default_factory=list)
