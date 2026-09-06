from datetime import datetime, timezone
from alert2iq_server.alert.payload import derive_tier
from alert2iq_server.domain.events import EventState, SourceEvent
from alert2iq_server.fusion.correlator import Correlator
from alert2iq_server.geo.cells import haversine_km
from alert2iq_server.p0a.detector import P0aDetector
from alert2iq_server.p0a.discriminate import SourceZone
from alert2iq_server.p0a.epic import StationPick
from alert2iq_server.p0a.replay import replay_p0a

ZONES = [SourceZone(36.0, 40.0, 35.0, 44.0)]
NEAR = {"S0", "S1", "S2", "S3", "S4"}
ORIGIN = (37.17, 37.03)
T0 = 1_675_646_255_000  # 2023-02-06T01:17:35Z in ms


def picklist():
    coords = [(37.17, 37.03), (37.4, 37.1), (37.0, 37.2), (37.1, 36.8), (37.3, 36.9)]
    out = []
    for i, (la, lo) in enumerate(coords):
        d = haversine_km(la, lo, *ORIGIN)
        out.append({"station": f"S{i}", "phase": "P",
                    "time_ms": T0 + int(1000 * d / 6.0), "lat": la, "lon": lo,
                    "pd_cm": 1.5})
    return out


def test_replay_detects_within_latency_budget():
    det = P0aDetector(NEAR, ZONES, shadow=False)
    res = replay_p0a(picklist(), det, Correlator(), now_ms_fn=lambda: T0 + 8000)
    assert res["detected"] is True
    assert res["detect_latency_s"] is not None and res["detect_latency_s"] <= 10.0
    assert res["origin_error_km"] < 40.0


def test_p0b_then_p0a_cross_confirms_to_p2():
    # the executable proof of mutual reinforcement (B2 <-> B)
    corr = Correlator()
    t = datetime.fromtimestamp(T0 / 1000, tz=timezone.utc)
    p0b_ev = SourceEvent("p0b", f"p0b:{T0}", t, 37.17, 37.03, None, 5.5, "p0b_proxy", t)
    tr1 = corr.ingest(p0b_ev)
    assert derive_tier(tr1.event) == "P0"               # crowdsourcing first: P0
    p0a_ev = SourceEvent("p0a", f"p0a:{T0}", t, 37.2, 37.0, 8.0, 7.2, "pd", t)
    tr2 = corr.ingest(p0a_ev)
    assert tr2 is not None and tr2.event.state is EventState.CONFIRMED
    assert derive_tier(tr2.event) == "P2"               # stations confirm: P2
