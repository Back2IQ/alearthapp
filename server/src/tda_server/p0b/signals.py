from __future__ import annotations

import math
from dataclasses import dataclass

DETECT_CELL_DEG = 0.1


@dataclass(frozen=True)
class PhoneTrigger:
    device_hash: str
    cell: str
    trigger_ms: int
    clock_unc_ms: int
    received_ms: int
    attest_ok: bool


@dataclass(frozen=True)
class ActivePing:
    device_hash: str
    cell: str
    ping_ms: int
    received_ms: int


def coarsen_cell(lat: float, lon: float, deg: float = DETECT_CELL_DEG) -> str:
    return f"d{math.floor(lat / deg)}_{math.floor(lon / deg)}"


def detect_cell_center(cell: str, deg: float = DETECT_CELL_DEG) -> tuple[float, float]:
    body = cell[1:]  # strip leading 'd'
    lat_idx, lon_idx = body.split("_")
    lat = (int(lat_idx) + 0.5) * deg
    lon = (int(lon_idx) + 0.5) * deg
    return lat, lon


def serialize_trigger(t: PhoneTrigger) -> dict[str, str]:
    return {
        "device_hash": t.device_hash,
        "cell": t.cell,
        "trigger_ms": str(t.trigger_ms),
        "clock_unc_ms": str(t.clock_unc_ms),
        "received_ms": str(t.received_ms),
        "attest_ok": "1" if t.attest_ok else "0",
    }


def deserialize_trigger(d: dict[str, str]) -> PhoneTrigger:
    return PhoneTrigger(
        device_hash=d["device_hash"],
        cell=d["cell"],
        trigger_ms=int(d["trigger_ms"]),
        clock_unc_ms=int(d["clock_unc_ms"]),
        received_ms=int(d["received_ms"]),
        attest_ok=d["attest_ok"] == "1",
    )


def serialize_ping(p: ActivePing) -> dict[str, str]:
    return {
        "device_hash": p.device_hash,
        "cell": p.cell,
        "ping_ms": str(p.ping_ms),
        "received_ms": str(p.received_ms),
    }


def deserialize_ping(d: dict[str, str]) -> ActivePing:
    return ActivePing(
        device_hash=d["device_hash"],
        cell=d["cell"],
        ping_ms=int(d["ping_ms"]),
        received_ms=int(d["received_ms"]),
    )
