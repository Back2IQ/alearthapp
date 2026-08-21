from __future__ import annotations

from tda_server.alert.payload import build_payload, sign_payload
from tda_server.fusion.correlator import Correlator
from tda_server.geo.cells import haversine_km
from tda_server.p0a.detector import P0aDetector
from tda_server.p0a.discriminate import SourceZone
from tda_server.p0a.epic import StationPick
from tda_server.p0b.background import BackgroundModel
from tda_server.p0b.detector import build_p0b_detector
from tda_server.p0b.replay import generate_scenario
from tda_server.p0b.signals import PhoneTrigger

_ZONES = [SourceZone(30.0, 44.0, 25.0, 45.0)]
_NEAR = {"S0", "S1", "S2", "S3", "S4"}


def _p0a_picks(origin: tuple[float, float], t0_ms: int, vp: float = 6.0,
              pd: float = 0.8) -> list[StationPick]:
    coords = [(37.2, 37.0), (37.4, 37.1), (37.0, 37.2), (37.1, 36.8), (37.3, 36.9)]
    out = []
    for i, (la, lo) in enumerate(coords):
        d = haversine_km(la, lo, *origin)
        out.append(StationPick(f"S{i}", "P", t0_ms + int(1000 * d / vp), la, lo, pd))
    return out


def scenario_alerts(kind: str, priv_key_b64: str, *, now_ms: int,
                    epicenter: tuple[float, float] = (37.17, 37.03)) -> list[dict[str, str]]:
    if kind == "firework":
        return []
    if kind != "quake":
        raise ValueError(f"unknown scenario kind: {kind!r}")

    t0_ms = now_ms
    scen = generate_scenario(epicenter=epicenter, mag=7.8, n_devices=400,
                             report_fraction=0.3, t0_ms=t0_ms, seed=7)
    det = build_p0b_detector(BackgroundModel(b0=-8.0, b1=0.005),
                             nu_per_cell=max(400 // 20, 5),
                             threshold_h=3.0, attn_h=1.0)

    corr = Correlator()
    payloads: list[dict[str, str]] = []

    se_p0b = None
    for tr in scen["triggers"]:
        det.observe_trigger(PhoneTrigger(tr["device_hash"], tr["cell"], tr["trigger_ms"],
                                         tr["clock_unc_ms"], tr["received_ms"], tr["attest_ok"]))
        ev, _attn = det.evaluate(tr["trigger_ms"])
        if ev is not None:
            se_p0b = ev
            break

    if se_p0b is None:
        return []

    tr_p0b = corr.ingest(se_p0b)
    if tr_p0b is not None:
        payloads.append(sign_payload(build_payload(tr_p0b, now_ms=now_ms), priv_key_b64))

    p0a_det = P0aDetector(_NEAR, _ZONES, shadow=False)
    dec = p0a_det.evaluate(_p0a_picks(epicenter, t0_ms), [], now_ms=now_ms)
    if dec.source_event is not None:
        tr_p0a = corr.ingest(dec.source_event)
        if tr_p0a is not None:
            payloads.append(sign_payload(build_payload(tr_p0a, now_ms=now_ms), priv_key_b64))

    return payloads
