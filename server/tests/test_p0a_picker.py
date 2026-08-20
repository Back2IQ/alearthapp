from pathlib import Path
import pytest

seisbench = pytest.importorskip("seisbench")
from obspy import read
from tda_server.p0a.picker import PhaseNetPicker

FIX = Path(__file__).parent / "fixtures" / "kahramanmaras_window.mseed"


@pytest.mark.skipif(not FIX.exists(), reason="miniSEED fixture not recorded")
def test_picker_finds_p_pick_on_real_event():
    stream = read(str(FIX))
    picker = PhaseNetPicker()
    picks = picker.pick(stream, station_lat=39.0, station_lon=40.0)
    assert any(p.phase == "P" for p in picks)
    for p in picks:
        assert p.time_ms > 0 and p.lat == 39.0
