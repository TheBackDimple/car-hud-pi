"""In-memory state store for HUD data.

When the frontend reconnects (e.g., Chromium restarts), the server
replays the last known state so the HUD displays immediately.
"""
from typing import Any


class HudStateStore:
    """Thread-safe in-memory store for last known HUD state."""

    def __init__(self) -> None:
        self._hud_data: dict[str, Any] | None = None
        self._map_frame: dict[str, Any] | None = None
        self._layout_config: dict[str, Any] | None = None

    def update_hud_data(self, data: dict[str, Any]) -> None:
        """Store latest vehicle data."""
        self._hud_data = data

    def update_map_frame(self, data: dict[str, Any]) -> None:
        """Store latest map frame."""
        self._map_frame = data

    def update_layout_config(self, data: dict[str, Any]) -> None:
        """Store latest layout preset."""
        self._layout_config = data

    def get_full_state(self) -> dict[str, Any]:
        """Return full state snapshot for frontend replay."""
        return {
            "hud_data": self._hud_data,
            "map_frame": self._map_frame,
            "layout_config": self._layout_config,
        }


# Singleton instance
store = HudStateStore()
