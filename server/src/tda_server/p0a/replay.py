from __future__ import annotations

from typing import Callable

from tda_server.fusion.correlator import Correlator
from tda_server.geo.cells import haversine_km
from tda_server.p0a.detector import P0aDetector
from tda_server.p0a.epic import StationPick


def replay_p0a(picklist: list[dict], detector: P0aDetector, correlator: Correlator, *,
               now_ms_fn: Callable[[], int]) -> dict:
    """Feed a recorded pick list through the P0a detector AND the shared correlator.
    Deterministic scientific gate; must pass before P0a goes live in a zone."""
    picks = [StationPick(p["station"], p["phase"], p["time_ms"], p["lat"], p["lon"],
                         p.get("pd_cm")) for p in picklist]
    origin_ms = min(p["time_ms"] for p in picklist)
    decision = detector.evaluate(picks, [], now_ms=now_ms_fn())
    if decision.source_event is None:
        return {"detected": False, "detect_latency_s": None,
                "origin_error_km": None, "reason": decision.reason}
    ev = decision.source_event
    tr = correlator.ingest(ev)
    detect_ms = int(ev.received_at.timestamp() * 1000)
    return {
        "detected": tr is not None,
        "detect_latency_s": (detect_ms - origin_ms) / 1000.0,
        "origin_error_km": haversine_km(ev.lat, ev.lon, picklist[0]["lat"],
                                        picklist[0]["lon"]),
        "path": decision.path,
    }
