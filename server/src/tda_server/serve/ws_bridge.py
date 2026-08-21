from __future__ import annotations

import json
import time

import websockets
from websockets.exceptions import ConnectionClosed

from tda_server.serve.alerts import scenario_alerts

_CLIENTS: set = set()


async def _handler(ws, priv_key_b64: str, pub_key_b64: str) -> None:
    _CLIENTS.add(ws)
    try:
        await ws.send(json.dumps({"type": "hello", "pub_key": pub_key_b64}))
        async for raw in ws:
            try:
                msg = json.loads(raw)
            except (TypeError, ValueError):
                continue
            if msg.get("cmd") != "simulate":
                continue
            kind = msg.get("kind")
            now_ms = int(time.time() * 1000)
            try:
                payloads = scenario_alerts(kind, priv_key_b64, now_ms=now_ms)
            except ValueError:
                continue
            for payload in payloads:
                out = json.dumps({"type": "alert", "payload": payload})
                for client in list(_CLIENTS):
                    try:
                        await client.send(out)
                    except ConnectionClosed:
                        pass
    except ConnectionClosed:
        pass
    finally:
        _CLIENTS.discard(ws)


def start_server(host: str, port: int, *, priv_key_b64: str, pub_key_b64: str):
    async def handler(ws):
        await _handler(ws, priv_key_b64, pub_key_b64)

    return websockets.serve(handler, host, port)


async def serve(host: str = "0.0.0.0", port: int = 8000, *, priv_key_b64: str,
                pub_key_b64: str) -> None:
    async with start_server(host, port, priv_key_b64=priv_key_b64,
                            pub_key_b64=pub_key_b64) as server:
        await server.wait_closed()
