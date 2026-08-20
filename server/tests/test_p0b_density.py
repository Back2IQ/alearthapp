from tda_server.p0b.density import DensityTracker
from tda_server.p0b.signals import ActivePing


def ping(dev: str, cell: str, ms: int) -> ActivePing:
    return ActivePing(dev, cell, ms, ms + 50)


def test_counts_distinct_active_devices():
    dt = DensityTracker(active_ttl_ms=1000)
    dt.observe(ping("a", "d1_1", 0))
    dt.observe(ping("b", "d1_1", 100))
    dt.observe(ping("a", "d1_1", 200))     # same device re-ping, still 2 distinct
    assert dt.nu("d1_1", 300) == 2

def test_expired_devices_drop_out():
    dt = DensityTracker(active_ttl_ms=1000)
    dt.observe(ping("a", "d1_1", 0))
    assert dt.nu("d1_1", 900) == 1
    assert dt.nu("d1_1", 1100) == 0        # ttl passed

def test_cells_are_independent():
    dt = DensityTracker(active_ttl_ms=1000)
    dt.observe(ping("a", "d1_1", 0))
    dt.observe(ping("b", "d2_2", 0))
    assert dt.nu("d1_1", 100) == 1 and dt.nu("d2_2", 100) == 1

def test_prune_frees_expired():
    dt = DensityTracker(active_ttl_ms=1000)
    dt.observe(ping("a", "d1_1", 0))
    dt.prune(2000)
    assert dt.nu("d1_1", 2000) == 0
