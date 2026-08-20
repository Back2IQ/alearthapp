import asyncio
from tda_server.fusion.correlator import Correlator, Transition
from tda_server.p0b.background import BackgroundModel
from tda_server.p0b.detector import P0bDetector, build_p0b_detector
from tda_server.p0b.density import DensityTracker
from tda_server.p0b.pipeline import run_p0b_pipeline
from tda_server.p0b.signals import ActivePing, PhoneTrigger, serialize_ping, serialize_trigger, coarsen_cell
from tda_server.stream.base import InMemoryStream
from tda_server.geo.cells import haversine_km
from tda_server.p0b.signals import detect_cell_center


async def test_pipeline_emits_transition_from_trigger_burst():
    trigger_stream = InMemoryStream()
    ping_stream = InMemoryStream()
    origin = (41.0, 29.0)
    ocell = coarsen_cell(*origin)
    ola, olo = detect_cell_center(ocell)
    t0 = 100_000
    last = t0
    for k in range(5):
        cell = coarsen_cell(origin[0] + 0.1 * k, origin[1])
        la, lo = detect_cell_center(cell)
        d = haversine_km(la, lo, ola, olo)
        tms = t0 + int(1000 * d / 3.5)
        last = max(last, tms)
        for j in range(8):
            await trigger_stream.append(serialize_trigger(
                PhoneTrigger(f"dev{k}_{j}", cell, tms + j, 40, tms + 100, True)))

    detector = build_p0b_detector(BackgroundModel(b0=-8.0, b1=0.005),
                                  nu_per_cell=100, threshold_h=3.0, attn_h=1.0)
    correlator = Correlator()
    got: list[Transition] = []

    clock_val = {"t": last + 1000}
    def clock() -> int:
        return clock_val["t"]

    async def on_transition(tr: Transition) -> None:
        got.append(tr)

    task = asyncio.create_task(run_p0b_pipeline(
        trigger_stream, ping_stream, detector, DensityTracker(),
        correlator, on_transition, tick_s=0.01, clock=clock))
    await asyncio.sleep(0.1)
    task.cancel()
    # CanonicalEvent (domain/events.py, Plan A) only exposes `.sources`
    # (dict keyed by source name), not a singular `.source` attribute.
    assert any("p0b" in tr.event.sources for tr in got)


async def test_pipeline_detects_via_shared_production_density_no_seeded_density():
    # FUND 6 regression: in production, run_p0b_pipeline's DensityTracker
    # (filled by real ActivePings from the ping stream) must be the SAME
    # object the ScoreDetector reads nu from. Wiring them up as two separate
    # DensityTracker instances (as build_p0b_detector's test-only
    # _SeededDensity implicitly encourages) leaves nu permanently 0 for the
    # detector's own tracker -> score always -1 -> no SourceEvent, ever.
    trigger_stream = InMemoryStream()
    ping_stream = InMemoryStream()
    origin = (41.0, 29.0)
    ocell = coarsen_cell(*origin)
    ola, olo = detect_cell_center(ocell)
    t0 = 100_000
    last = t0
    cells_used: set[str] = set()
    for k in range(5):
        cell = coarsen_cell(origin[0] + 0.1 * k, origin[1])
        cells_used.add(cell)
        la, lo = detect_cell_center(cell)
        d = haversine_km(la, lo, ola, olo)
        tms = t0 + int(1000 * d / 3.5)
        last = max(last, tms)
        for j in range(8):
            await trigger_stream.append(serialize_trigger(
                PhoneTrigger(f"dev{k}_{j}", cell, tms + j, 40, tms + 100, True)))

    # Real ActivePings, raising nu in each cell that will later trigger -
    # NOT a _SeededDensity stub.
    for cell in cells_used:
        for i in range(100):
            await ping_stream.append(serialize_ping(
                ActivePing(f"pingdev{cell}_{i}", cell, t0 - 60_000, t0 - 60_000)))

    density = DensityTracker()
    detector = P0bDetector.production(
        BackgroundModel(b0=-8.0, b1=0.005), density,
        threshold_h=3.0, attn_h=1.0)
    correlator = Correlator()
    got: list[Transition] = []

    clock_val = {"t": last + 1000}
    def clock() -> int:
        return clock_val["t"]

    async def on_transition(tr: Transition) -> None:
        got.append(tr)

    task = asyncio.create_task(run_p0b_pipeline(
        trigger_stream, ping_stream, detector, density,
        correlator, on_transition, tick_s=0.01, clock=clock))
    await asyncio.sleep(0.1)
    task.cancel()
    assert any("p0b" in tr.event.sources for tr in got)
