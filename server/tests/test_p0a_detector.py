from tda_server.geo.cells import haversine_km
from tda_server.p0a.detector import P0aDetector
from tda_server.p0a.discriminate import SourceZone
from tda_server.p0a.epic import StationPick
from tda_server.p0a.plum import IntensityObs

ZONES = [SourceZone(36.0, 40.0, 35.0, 44.0)]
NEAR = {"S0", "S1", "S2", "S3", "S4"}


def picks(origin=(37.2, 37.0), t0=1_000_000, vp=6.0, pd=0.8):
    coords = [(37.2, 37.0), (37.4, 37.1), (37.0, 37.2), (37.1, 36.8), (37.3, 36.9)]
    out = []
    for i, (la, lo) in enumerate(coords):
        d = haversine_km(la, lo, *origin)
        out.append(StationPick(f"S{i}", "P", t0 + int(1000 * d / vp), la, lo, pd))
    return out


def test_model_path_emits_source_event_in_zone():
    det = P0aDetector(NEAR, ZONES, shadow=False)
    dec = det.evaluate(picks(), [], now_ms=1_000_500)
    assert dec.path == "model" and dec.source_event is not None
    assert dec.source_event.source == "p0a" and dec.source_event.mag_type == "pd"

def test_teleseism_rejected():
    det = P0aDetector(NEAR, ZONES, shadow=False)
    far = picks(origin=(10.0, 100.0))
    for i, p in enumerate(far):
        far[i] = StationPick(p.station, p.phase, p.time_ms, 10.0 + i * 0.1, 100.0, p.pd_cm)
    dec = det.evaluate(far, [], now_ms=1_000_500)
    assert dec.path == "none" and "teleseism" in dec.reason.lower()

def test_plum_path_triggers_when_model_below_threshold():
    det = P0aDetector(NEAR, ZONES, shadow=False)
    obs = [IntensityObs(37.2, 37.0, mmi=6.0)]
    dec = det.evaluate(picks()[:2], obs, now_ms=1_000_500)  # too few picks for model
    assert dec.path == "plum" and dec.source_event is not None

def test_shadow_mode_marks_event():
    det = P0aDetector(NEAR, ZONES, shadow=True)
    dec = det.evaluate(picks(), [], now_ms=1_000_500)
    assert dec.source_event.source_event_id.startswith("p0a-shadow:")
