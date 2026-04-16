"""In-memory state store for HUD data.

When the frontend reconnects (e.g., Chromium restarts), the server
replays the last known state so the HUD displays immediately.

OBD data comes from the Pi's OBD reader; GPS/nav data comes from Android.
They are merged into a single hud_data for the frontend.
"""
from typing import Any

# Default empty hud_data structure
_DEFAULT_HUD = {
    "speed": "",
    "gpsSpeed": "",
    "rpm": "",
    "coolantTemp": "",
    "mpg": "",
    "range": "",
    "fuelLevel": "",
    "turn": "",
    "distance": "",
    "maneuver": "",
    "eta": "",
    "speedLimit": "",
    "timestamp": None,
}


class HudStateStore:
    """Thread-safe in-memory store for last known HUD state."""

    def __init__(self) -> None:
        self._hud_data: dict[str, Any] = dict(_DEFAULT_HUD)
        self._map_frame: dict[str, Any] | None = None
        self._layout_config: dict[str, Any] | None = None
        # None: only URL (?mirror=) applies until Android sends a preference.
        self._hud_mirror: bool | None = None

    def update_hud_data(self, data: dict[str, Any]) -> None:
        """Store full vehicle data (e.g. from Android when no Pi OBD)."""
        self._hud_data = {**_DEFAULT_HUD, **data}

    def update_obd_data(self, data: dict[str, Any]) -> None:
        """Merge OBD fields from Pi's OBD reader."""
        obd_fields = ("speed", "rpm", "coolantTemp", "mpg", "range", "fuelLevel")
        for k in obd_fields:
            if k in data and data[k] != "":
                self._hud_data[k] = str(data[k])
        if "timestamp" in data:
            self._hud_data["timestamp"] = data["timestamp"]

    def update_gps_data(self, data: dict[str, Any]) -> None:
        """Merge GPS/nav fields from Android."""
        gps_fields = ("gpsSpeed", "turn", "distance", "maneuver", "eta", "speedLimit")
        for k in gps_fields:
            if k in data:
                self._hud_data[k] = str(data[k]) if data[k] is not None else ""
        if "timestamp" in data:
            self._hud_data["timestamp"] = data["timestamp"]

    def update_map_frame(self, data: dict[str, Any]) -> None:
        """Store latest map frame."""
        self._map_frame = data

    def clear_map_frame(self) -> None:
        """Clear map snapshot (e.g. trip ended, streaming stopped)."""
        self._map_frame = None

    def update_layout_config(self, data: dict[str, Any]) -> None:
        """Store latest layout preset."""
        self._layout_config = data

    def set_hud_mirror(self, mirrored: bool) -> None:
        """Persisted display mirror preference from Android (forwarded to HUD)."""
        self._hud_mirror = mirrored

    def get_hud_mirror(self) -> bool | None:
        return self._hud_mirror

    def clear_android_contribution(self) -> None:
        """Clear map frame and GPS/nav fields when Android disconnects from the Pi."""
        self._map_frame = None
        for k in ("gpsSpeed", "turn", "distance", "maneuver", "eta", "speedLimit"):
            self._hud_data[k] = ""

    def get_full_state(self) -> dict[str, Any]:
        """Return full state snapshot for frontend replay."""
        return {
            "hud_data": dict(self._hud_data),
            "map_frame": self._map_frame,
            "layout_config": self._layout_config,
            "hud_mirror": self._hud_mirror,
        }


# Singleton instance
store = HudStateStore()
