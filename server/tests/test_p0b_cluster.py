from tda_server.p0b.cluster import cluster_origin, form_cluster
from tda_server.p0b.wavefront import CellHit


def test_largest_connected_group_wins():
    # group A: 4 adjacent cells near (41,29); group B: 2 cells far away (36,36)
    a = [CellHit("d410_289", 100_000), CellHit("d411_289", 100_400),
         CellHit("d412_290", 100_800), CellHit("d413_290", 101_200)]
    b = [CellHit("d360_360", 100_100), CellHit("d361_360", 100_200)]
    cluster = form_cluster(a + b, link_km=40.0)
    assert set(h.cell for h in cluster) == set(h.cell for h in a)

def test_origin_is_earliest_cell_center():
    a = [CellHit("d411_289", 100_400), CellHit("d410_289", 100_000),
         CellHit("d412_290", 100_800)]
    lat, lon, origin_ms = cluster_origin(a)
    assert origin_ms == 100_000
    assert 41.0 <= lat < 41.1 and 28.9 <= lon < 29.0
