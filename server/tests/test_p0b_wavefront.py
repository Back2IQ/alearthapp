from alert2iq_server.p0b.signals import detect_cell_center
from alert2iq_server.p0b.wavefront import CellHit, wavefront_consistent
from alert2iq_server.geo.cells import haversine_km


def hit_at(cell: str, origin_cell: str, v_kms: float, t0: int) -> CellHit:
    la, lo = detect_cell_center(cell)
    ola, olo = detect_cell_center(origin_cell)
    d = haversine_km(la, lo, ola, olo)
    return CellHit(cell, t0 + int(1000 * d / v_kms))


def test_earthquake_front_is_consistent():
    origin = "d410_289"
    cells = ["d410_289", "d411_289", "d412_290", "d413_291", "d414_292"]
    hits = [hit_at(c, origin, 3.5, 100_000) for c in cells]
    assert wavefront_consistent(hits) is True

def test_citywide_simultaneous_is_rejected():
    # fireworks/cheering: every cell triggers at the same instant, any distance
    cells = ["d410_289", "d411_289", "d412_290", "d413_291", "d414_292"]
    hits = [CellHit(c, 100_000) for c in cells]
    assert wavefront_consistent(hits) is False

def test_implausible_speed_rejected():
    origin = "d410_289"
    cells = ["d410_289", "d411_289", "d412_290", "d413_291"]
    hits = [hit_at(c, origin, 15.0, 100_000) for c in cells]   # 15 km/s: not S-wave
    assert wavefront_consistent(hits) is False

def test_too_few_cells_rejected():
    hits = [CellHit("d410_289", 100_000), CellHit("d411_289", 101_000)]
    assert wavefront_consistent(hits, min_cells=3) is False
