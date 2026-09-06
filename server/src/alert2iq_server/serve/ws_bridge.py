from __future__ import annotations

import asyncio
import json
import random
import time
from collections import defaultdict, deque

import websockets
from websockets.exceptions import ConnectionClosed

from alert2iq_server.p0b.signals import coarsen_cell, detect_cell_center
from alert2iq_server.serve.alerts import scenario_alerts

_CLIENTS: set = set()

# --- Detection network live state (global by construction: coarsen_cell works
# anywhere on Earth, so the network is not Turkey-bound - only the app's list
# filter is). This is the visible participatory layer; the full statistical P0b
# pipeline (background/density/cluster/wavefront/reputation) lives in
# alert2iq_server.p0b.* and is the production detection path. Here we keep a light,
# honest live aggregation for the map: online devices per cell + a rolling
# trigger window + a simple distinct-device cluster indicator. ---
_ONLINE: dict = {}            # ws -> {"cell", "lat", "lon", "since"}
_TRIGGERS: deque = deque()    # (received_ms, cell, device_id, lat, lon)

_TRIGGER_WINDOW_MS = 90_000   # how long a trigger stays on the live map
_DETECT_WINDOW_MS = 60_000    # window for the cluster indicator
_DETECT_MIN_DEVICES = 3       # distinct devices in one cell -> "detecting"


def _now_ms() -> int:
    return int(time.time() * 1000)


def _expire(now_ms: int) -> None:
    while _TRIGGERS and _TRIGGERS[0][0] <= now_ms - _TRIGGER_WINDOW_MS:
        _TRIGGERS.popleft()


def _add_trigger(cell: str, device_id: str, lat: float, lon: float, now_ms: int) -> None:
    _TRIGGERS.append((now_ms, cell, device_id, lat, lon))


def _inject_synthetic_cluster(now_ms: int, lat: float = 37.58, lon: float = 36.93,
                              devices: int = 8) -> None:
    """A 'quake' simulation seeds a tight burst of synthetic triggers around the
    epicentre so the live network map visibly lights up a detecting cell. Marked
    synthetic by their device ids; never leaves the demo path."""
    for i in range(devices):
        dlat = lat + random.uniform(-0.05, 0.05)
        dlon = lon + random.uniform(-0.05, 0.05)
        _add_trigger(coarsen_cell(dlat, dlon), f"sim-{i}-{now_ms}", dlat, dlon, now_ms)


def _network_state() -> dict:
    now = _now_ms()
    _expire(now)
    online_by_cell: dict[str, int] = defaultdict(int)
    for info in _ONLINE.values():
        online_by_cell[info["cell"]] += 1
    trig_by_cell: dict[str, dict] = defaultdict(lambda: {"triggers": 0, "devices": set()})
    for (ts, cell, device, lat, lon) in _TRIGGERS:
        d = trig_by_cell[cell]
        d["triggers"] += 1
        if ts > now - _DETECT_WINDOW_MS:
            d["devices"].add(device)

    cells = []
    for cell in set(online_by_cell) | set(trig_by_cell):
        clat, clon = detect_cell_center(cell)
        cells.append({
            "cell": cell, "lat": round(clat, 3), "lon": round(clon, 3),
            "online": online_by_cell.get(cell, 0),
            "triggers": trig_by_cell.get(cell, {}).get("triggers", 0),
        })
    detections = []
    for cell, d in trig_by_cell.items():
        if len(d["devices"]) >= _DETECT_MIN_DEVICES:
            clat, clon = detect_cell_center(cell)
            detections.append({
                "cell": cell, "lat": round(clat, 3), "lon": round(clon, 3),
                "devices": len(d["devices"]), "triggers": d["triggers"],
            })
    return {
        "type": "network", "ts": now,
        "online_total": len(_ONLINE),
        "cells": cells, "detections": detections,
    }


async def _send(ws, obj: dict) -> None:
    try:
        await ws.send(json.dumps(obj))
    except ConnectionClosed:
        pass


async def _broadcast(obj: dict) -> None:
    out = json.dumps(obj)
    for client in list(_CLIENTS):
        try:
            await client.send(out)
        except ConnectionClosed:
            pass


async def _broadcast_network() -> None:
    await _broadcast(_network_state())


async def _handler(ws, priv_key_b64: str, pub_key_b64: str) -> None:
    _CLIENTS.add(ws)
    try:
        await _send(ws, {"type": "hello", "pub_key": pub_key_b64})
        await _send(ws, _network_state())  # newcomer gets current network at once
        async for raw in ws:
            try:
                msg = json.loads(raw)
            except (TypeError, ValueError):
                continue
            cmd = msg.get("cmd")
            now_ms = _now_ms()

            if cmd == "simulate":
                kind = msg.get("kind")
                if kind == "quake":
                    _inject_synthetic_cluster(now_ms)
                    await _broadcast_network()
                try:
                    payloads = scenario_alerts(kind, priv_key_b64, now_ms=now_ms)
                except ValueError:
                    payloads = []
                for payload in payloads:
                    await _broadcast({"type": "alert", "payload": payload})

            elif cmd == "join":
                try:
                    lat, lon = float(msg["lat"]), float(msg["lon"])
                except (KeyError, TypeError, ValueError):
                    continue
                _ONLINE[ws] = {"cell": coarsen_cell(lat, lon), "lat": lat, "lon": lon, "since": now_ms}
                await _broadcast_network()

            elif cmd == "trigger":
                try:
                    lat, lon = float(msg["lat"]), float(msg["lon"])
                except (KeyError, TypeError, ValueError):
                    continue
                _add_trigger(coarsen_cell(lat, lon), f"dev-{id(ws)}", lat, lon, now_ms)
                await _broadcast_network()

            elif cmd == "leave":
                _ONLINE.pop(ws, None)
                await _broadcast_network()

    except ConnectionClosed:
        pass
    finally:
        _CLIENTS.discard(ws)
        _ONLINE.pop(ws, None)


async def _expiry_loop() -> None:
    """Age out stale triggers and keep the online count fresh on the map."""
    while True:
        await asyncio.sleep(5)
        try:
            await _broadcast_network()
        except Exception:  # noqa: BLE001 - housekeeping must never die
            pass


def start_server(host: str, port: int, *, priv_key_b64: str, pub_key_b64: str):
    async def handler(ws):
        await _handler(ws, priv_key_b64, pub_key_b64)

    return websockets.serve(handler, host, port)


async def serve(host: str = "0.0.0.0", port: int = 8000, *, priv_key_b64: str,
                pub_key_b64: str) -> None:
    async with start_server(host, port, priv_key_b64=priv_key_b64,
                            pub_key_b64=pub_key_b64) as server:
        asyncio.create_task(_expiry_loop())
        await server.wait_closed()
