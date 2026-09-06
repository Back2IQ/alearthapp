from alert2iq_server.geo.cells import haversine_km
from alert2iq_server.p0a.associator import associate, grid_search_origin
from alert2iq_server.p0a.epic import StationPick


def picks_around(origin=(39.0, 40.0), t0=1_000_000, vp=6.0):
    coords = [(39.0, 40.0), (39.2, 40.1), (38.9, 40.3), (39.1, 39.8), (38.8, 39.9)]
    out = []
    for i, (la, lo) in enumerate(coords):
        d = haversine_km(la, lo, *origin)
        out.append(StationPick(f"S{i}", "P", t0 + int(1000 * d / vp), la, lo, 0.7))
    return out


def test_grid_search_recovers_origin():
    o = grid_search_origin(picks_around(), vp_kms=6.0, fixed_depth_km=8.0)
    assert o is not None
    assert haversine_km(o.lat, o.lon, 39.0, 40.0) < 25.0    # within grid resolution
    assert o.rms_s < 1.0 and o.n_picks == 5

def test_associate_auto_falls_back_to_grid():
    o = associate(picks_around(), backend="grid")
    assert o is not None and o.depth_km == 8.0

def test_too_few_picks_returns_none():
    assert grid_search_origin(picks_around()[:2], vp_kms=6.0, fixed_depth_km=8.0) is None
