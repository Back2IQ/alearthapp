from __future__ import annotations

import base64
import json
import socket

import websockets
from cryptography.hazmat.primitives import serialization as ser
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

from alert2iq_server.alert.payload import verify_payload
from alert2iq_server.serve.ws_bridge import start_server


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

            # The bridge also broadcasts "network" state (detection-network layer);
            # collect the two signed alert messages, ignoring interleaved network updates.
            alerts = []
            while len(alerts) < 2:
                msg = json.loads(await ws.recv())
                if msg["type"] == "network":
                    continue
                assert msg["type"] == "alert"
                assert verify_payload(msg["payload"], pub_b64) is True
                alerts.append(msg["payload"])

            assert alerts[0]["tier"] == "P0"
            assert alerts[-1]["tier"] == "P2"


async def _recv_network_until(ws, pred, limit: int = 40):
    for _ in range(limit):
        msg = json.loads(await ws.recv())
        if msg.get("type") == "network" and pred(msg):
            return msg
    raise AssertionError("no matching network message")


async def test_join_registers_device_and_quake_lights_up_a_detection():
    priv_b64, pub_b64 = _keypair()
    port = _free_port()

    async with start_server("127.0.0.1", port, priv_key_b64=priv_b64, pub_key_b64=pub_b64):
        async with websockets.connect(f"ws://127.0.0.1:{port}") as ws:
            assert json.loads(await ws.recv())["type"] == "hello"

            await ws.send(json.dumps({"cmd": "join", "lat": 37.0, "lon": 35.32}))
            net = await _recv_network_until(ws, lambda m: m["online_total"] >= 1)
            assert net["online_total"] >= 1
            assert any(c["online"] >= 1 for c in net["cells"])

            # a simulated quake seeds a synthetic trigger cluster near the epicentre
            await ws.send(json.dumps({"cmd": "simulate", "kind": "quake"}))
            det = await _recv_network_until(ws, lambda m: len(m["detections"]) >= 1)
            assert det["detections"][0]["devices"] >= 3
