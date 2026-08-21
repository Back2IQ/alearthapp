from __future__ import annotations

import base64
import json
import socket

import websockets
from cryptography.hazmat.primitives import serialization as ser
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

from tda_server.alert.payload import verify_payload
from tda_server.serve.ws_bridge import start_server


def _keypair() -> tuple[str, str]:
    priv = Ed25519PrivateKey.generate()
    priv_raw = priv.private_bytes(ser.Encoding.Raw, ser.PrivateFormat.Raw, ser.NoEncryption())
    pub_raw = priv.public_key().public_bytes(ser.Encoding.Raw, ser.PublicFormat.Raw)
    return base64.b64encode(priv_raw).decode(), base64.b64encode(pub_raw).decode()


def _free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


async def test_simulate_quake_broadcasts_signed_p0_then_p2_alerts():
    priv_b64, pub_b64 = _keypair()
    port = _free_port()

    async with start_server("127.0.0.1", port, priv_key_b64=priv_b64, pub_key_b64=pub_b64):
        async with websockets.connect(f"ws://127.0.0.1:{port}") as ws:
            hello = json.loads(await ws.recv())
            assert hello["type"] == "hello"
            assert hello["pub_key"] == pub_b64

            await ws.send(json.dumps({"cmd": "simulate", "kind": "quake"}))

            alerts = []
            for _ in range(2):
                msg = json.loads(await ws.recv())
                assert msg["type"] == "alert"
                assert verify_payload(msg["payload"], pub_b64) is True
                alerts.append(msg["payload"])

            assert alerts[0]["tier"] == "P0"
            assert alerts[-1]["tier"] == "P2"
