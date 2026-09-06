from alert2iq_server.geo.cells import cell_id
from alert2iq_server.p0a.plum import IntensityObs, plum_cells, plum_triggered


def test_strong_obs_projects_to_local_cells():
    obs = [IntensityObs(39.0, 40.0, mmi=6.0)]
    cells = plum_cells(obs, radius_km=30.0, min_mmi=2.5)
    assert cell_id(39.0, 40.0) in cells
    assert cells[cell_id(39.0, 40.0)] == 6.0        # unattenuated projection

def test_weak_obs_below_threshold_ignored():
    obs = [IntensityObs(39.0, 40.0, mmi=2.0)]
    assert plum_cells(obs, min_mmi=2.5) == {}
    assert plum_triggered(obs, min_mmi=2.5) is False

def test_cell_takes_max_over_overlapping_obs():
    obs = [IntensityObs(39.0, 40.0, 4.0), IntensityObs(39.05, 40.05, 6.0)]
    cells = plum_cells(obs, radius_km=30.0)
    assert cells[cell_id(39.0, 40.0)] == 6.0

def test_site_factor_amplifies():
    obs = [IntensityObs(39.0, 40.0, 4.0)]
    cells = plum_cells(obs, site_factor=1.0)
    assert cells[cell_id(39.0, 40.0)] == 5.0
