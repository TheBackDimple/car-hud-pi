#!/usr/bin/env python3
"""
OBD-II Reader — Runs on Raspberry Pi, reads vehicle data from Bluetooth OBD-II adapter.

The OBD-II adapter is paired with the Pi (not the phone). This script:
1. Connects to the ELM327 adapter via /dev/rfcomm0 (or configurable port)
2. Polls OBD PIDs every 500ms
3. Sends obd_data messages to the backend WebSocket

Requires: pip install obd websockets

Bluetooth setup on Pi:
  bluetoothctl → pair <MAC> → trust <MAC>
  sudo rfcomm bind 0 <MAC_ADDRESS>
"""
import argparse
import asyncio
import json
import logging
import os
import sys
import time
from pathlib import Path

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S",
)
logger = logging.getLogger("obd_reader")

# Default port for Bluetooth OBD (rfcomm bind creates /dev/rfcomm0)
DEFAULT_OBD_PORT = os.environ.get("OBD_PORT", "/dev/rfcomm0")
DEFAULT_WS_URL = os.environ.get("HUD_WS_URL", "wss://127.0.0.1:8000/ws?role=obd")
POLL_INTERVAL_SEC = 0.5  # 2 Hz


def _query_obd(connection, commands) -> dict | None:
    """Blocking OBD queries. Returns dict of str values or None on failure."""
    try:
        import obd
    except ImportError:
        logger.error("python-obd not installed. Run: pip install obd")
        return None

    result = {}
    for key, cmd in commands:
        try:
            r = connection.query(cmd)
            if r and r.value is not None:
                val = r.value
                if hasattr(val, "magnitude"):
                    result[key] = str(int(val.magnitude))
                else:
                    result[key] = str(val)
            else:
                result[key] = ""
        except Exception as e:
            logger.debug("OBD query %s failed: %s", key, e)
            result[key] = ""

    return result


def _connect_obd(port: str):
    """Blocking OBD connection. Returns connection or None."""
    try:
        import obd

        conn = obd.OBD(portstr=port, timeout=2.0)
        if conn.status() != obd.OBDStatus.CAR_CONNECTED:
            logger.warning("OBD status: %s (ignition may be off)", conn.status())
        return conn
    except Exception as e:
        logger.warning("OBD connection failed: %s", e)
        return None


def _process_obd_data(raw: dict) -> dict:
    """Compute mpg and range from raw OBD values."""
    data = dict(raw)

    # MPG from fuel rate (L/h) and speed (km/h)
    if data.get("mpg") and data.get("speed"):
        try:
            fuel_lph = float(data["mpg"])
            speed_kph = float(data["speed"])
            if fuel_lph > 0 and speed_kph > 0:
                mpg = (speed_kph * 0.621371) / (fuel_lph * 0.264172)
                data["mpg"] = f"{mpg:.1f}"
            else:
                data["mpg"] = ""
        except (ValueError, ZeroDivisionError):
            data["mpg"] = ""
    elif data.get("mpg"):
        data["mpg"] = ""

    # Range: fuel_level% * 12 gal * mpg (rough estimate)
    data["range"] = ""
    if data.get("fuelLevel") and data.get("mpg"):
        try:
            fl = float(data["fuelLevel"])
            mpg_val = float(data["mpg"])
            if mpg_val > 0:
                range_mi = 12 * (fl / 100) * mpg_val
                data["range"] = f"{int(range_mi)}"
        except (ValueError, ZeroDivisionError):
            pass

    return data


async def _run_obd_reader(port: str, ws_url: str) -> None:
    """Async main: connect OBD, connect WebSocket, poll and send."""
    try:
        import obd
        import websockets
    except ImportError as e:
        logger.error("Missing dependency: %s. Run: pip install obd websockets", e)
        sys.exit(1)

    commands = [
        ("speed", obd.commands.SPEED),
        ("rpm", obd.commands.RPM),
        ("coolantTemp", obd.commands.COOLANT_TEMP),
        ("fuelLevel", obd.commands.FUEL_LEVEL),
        ("mpg", obd.commands.FUEL_RATE),
    ]

    connection = None

    while True:
        # Connect to OBD
        if connection is None:
            connection = await asyncio.get_running_loop().run_in_executor(
                None, lambda: _connect_obd(port)
            )
            if connection is None:
                logger.warning("OBD unavailable. Retrying in 5s...")
                await asyncio.sleep(5)
                continue

        # Connect to backend WebSocket
        try:
            async with websockets.connect(
                ws_url, ping_interval=10, ping_timeout=5
            ) as ws:
                logger.info("Connected to backend at %s", ws_url)

                while True:
                    # Poll OBD (blocking)
                    raw = await asyncio.get_running_loop().run_in_executor(
                        None, lambda: _query_obd(connection, commands)
                    )

                    if raw is None:
                        connection = None
                        break

                    data = _process_obd_data(raw)
                    data["timestamp"] = int(time.time() * 1000)

                    msg = {
                        "type": "obd_data",
                        "payload": data,
                        "timestamp": data["timestamp"],
                    }
                    await ws.send(json.dumps(msg))
                    await asyncio.sleep(POLL_INTERVAL_SEC)

        except Exception as e:
            logger.warning("WebSocket error: %s. Reconnecting in 3s...", e)
            await asyncio.sleep(3)


def main():
    parser = argparse.ArgumentParser(description="OBD-II reader for Car HUD")
    parser.add_argument(
        "--port",
        default=DEFAULT_OBD_PORT,
        help=f"OBD serial port (default: {DEFAULT_OBD_PORT})",
    )
    parser.add_argument(
        "--ws-url",
        default=DEFAULT_WS_URL,
        help=f"Backend WebSocket URL (default: {DEFAULT_WS_URL})",
    )
    args = parser.parse_args()

    if not Path(args.port).exists():
        logger.warning(
            "Port %s does not exist. Pair OBD adapter and run: sudo rfcomm bind 0 <MAC>",
            args.port,
        )

    logger.info("Starting OBD reader (port=%s, ws=%s)", args.port, args.ws_url)
    asyncio.run(_run_obd_reader(args.port, args.ws_url))


if __name__ == "__main__":
    main()
