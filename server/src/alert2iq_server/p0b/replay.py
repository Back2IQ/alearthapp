from __future__ import annotations

import numpy as np

from alert2iq_server.geo.cells import haversine_km
from alert2iq_server.p0b.signals import ActivePing, PhoneTrigger, coarsen_cell, detect_cell_center


def generate_scenario(*, epicenter: tuple[float, float], mag: float, n_devices: int,
                      report_fraction: float, v_kms: float = 3.5, radius_km: float = 120.0,
                      t0_ms: int, seed: int) -> dict:
    rng = np.random.default_rng(seed)
    elat, elon = epicenter
    # scatter devices in a box around the epicenter (~radius_km)
    deg = radius_km / 111.0
    lats = elat + rng.uniform(-deg, deg, n_devices)
    lons = elon + rng.uniform(-deg, deg, n_devices)
    pings, triggers = [], []
    for i in range(n_devices):
        cell = coarsen_cell(lats[i], lons[i])
        pings.append({"device_hash": f"dev{i}", "cell": cell,
                      "ping_ms": t0_ms - 60_000, "received_ms": t0_ms - 60_000})
        d = haversine_km(lats[i], lons[i], elat, elon)
        # background false trigger (rare), any time in window
        if rng.random() < 0.01:
            bt = t0_ms + int(rng.uniform(-30_000, -5_000))
            triggers.append(_trig(f"dev{i}", cell, bt))
        # earthquake trigger for the report_fraction that feel it, within felt radius
        if d <= radius_km and mag >= 4.0 and rng.random() < report_fraction:
            arr = t0_ms + int(1000 * d / v_kms) + int(rng.normal(0, 300))
            triggers.append(_trig(f"dev{i}", cell, arr))
    triggers.sort(key=lambda t: t["trigger_ms"])
    return {"kind": "synthetic_scenario", "epicenter": [elat, elon], "mag": mag,
            "t0_ms": t0_ms, "n_devices": n_devices, "report_fraction": report_fraction,
            "pings": pings, "triggers": triggers}


def _trig(dev: str, cell: str, tms: int) -> dict:
    return {"device_hash": dev, "cell": cell, "trigger_ms": tms,
            "clock_unc_ms": 40, "received_ms": tms + 120, "attest_ok": True}


def replay_scenario(scenario: dict, detector_factory) -> dict:
    detector = detector_factory()
    t0 = scenario["t0_ms"]
    triggers = scenario["triggers"]
    detected_ms: int | None = None
    origin_err: float | None = None
    false_before = 0
    # feed triggers in time order, evaluating after each
    for tr in triggers:
        detector.observe_trigger(PhoneTrigger(
            tr["device_hash"], tr["cell"], tr["trigger_ms"], tr["clock_unc_ms"],
            tr["received_ms"], tr["attest_ok"]))
        ev, _attn = detector.evaluate(tr["trigger_ms"])
        if ev is not None and detected_ms is None:
            detected_ms = tr["trigger_ms"]
            elat, elon = scenario["epicenter"]
            origin_err = haversine_km(ev.lat, ev.lon, elat, elon)
            if detected_ms < t0:
                false_before += 1
    return {
        "detected": detected_ms is not None and detected_ms >= t0,
        "detect_latency_s": None if detected_ms is None else (detected_ms - t0) / 1000.0,
        "false_before_origin": false_before,
        "origin_error_km": origin_err,
    }
