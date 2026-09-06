from alert2iq_server.geo.cells import affected_cells, alert_radius_km, cell_id, haversine_km


def test_haversine_istanbul_ankara():
    d = haversine_km(41.01, 28.98, 39.93, 32.86)
    assert 340 < d < 360   # ~350 km


def test_cell_id_vectors():
    assert cell_id(41.0, 29.0) == "c82_58"
    assert cell_id(40.99, 28.99) == "c81_57"
    assert cell_id(-1.0, -1.0) == "c-2_-2"


def test_affected_cells_contains_center_and_scales():
    small = affected_cells(41.0, 29.0, 10)
    big = affected_cells(41.0, 29.0, 300)
    assert cell_id(41.0, 29.0) in small
    assert small < big


def test_alert_radius_table():
    assert alert_radius_km(3.9) == 0
    assert alert_radius_km(4.0) == 150
    assert alert_radius_km(5.5) == 300
    assert alert_radius_km(6.2) == 600
    assert alert_radius_km(7.8) == 1000
