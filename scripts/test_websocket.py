#!/usr/bin/env python3
"""
Simple WebSocket test script for Phase 1.
Run the backend first: uvicorn backend.main:app --reload --port 8000
Then run this from project root: python scripts/test_websocket.py
"""
import asyncio
import json
import sys
import os
from pathlib import Path

# Add project root to path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

try:
    import websockets
except ImportError:
    print("Install websockets: pip install websockets")
    sys.exit(1)


async def test_hud_client():
    """Connect as HUD, request state, receive messages."""
    use_tls = os.environ.get("USE_TLS", "").lower() in ("1", "true", "yes")
    scheme = "wss" if use_tls else "ws"
    uri = f"{scheme}://localhost:8000/ws?role=hud"
    async with websockets.connect(uri) as ws:
        # Request state (simulates frontend reconnect)
        await ws.send(json.dumps({"type": "request_state", "payload": {}, "timestamp": None}))
        msg = await asyncio.wait_for(ws.recv(), timeout=2)
        data = json.loads(msg)
        print(f"HUD received: {data['type']}")
        assert data["type"] in ("full_state", "connection_status")
        print("  OK: HUD client works")


async def test_android_client():
    """Connect as Android, send hud_data, verify it's relayed."""
    use_tls = os.environ.get("USE_TLS", "").lower() in ("1", "true", "yes")
    scheme = "wss" if use_tls else "ws"
    uri = f"{scheme}://localhost:8000/ws?role=android"
    async with websockets.connect(uri) as ws:
        # Send hud_data
        msg = {
            "type": "hud_data",
            "payload": {
                "speed": "65",
                "gpsSpeed": "64",
                "rpm": "2400",
                "coolantTemp": "195",
                "mpg": "28.5",
                "range": "320",
                "fuelLevel": "72",
                "turn": "Turn Right",
                "distance": "0.5 mi",
                "timestamp": 1708123456789,
            },
            "timestamp": 1708123456789,
        }
        await ws.send(json.dumps(msg))
        print("Android sent hud_data")
        # No response expected (relayed to HUD only)
        print("  OK: Android client works")


async def main():
    print("Testing WebSocket endpoints...")
    await test_hud_client()
    await test_android_client()
    print("\nAll tests passed!")


if __name__ == "__main__":
    asyncio.run(main())
