from __future__ import annotations

import asyncio
import logging
from typing import Awaitable, Callable

from alert2iq_server.fusion.correlator import Correlator, Transition
from alert2iq_server.p0b.density import DensityTracker
from alert2iq_server.p0b.detector import P0bDetector
from alert2iq_server.p0b.signals import deserialize_ping, deserialize_trigger
from alert2iq_server.stream.base import EventStream

log = logging.getLogger(__name__)


async def _consume_pings(ping_stream: EventStream, density: DensityTracker) -> None:
    async for _sid, rec in ping_stream.read():
        density.observe(deserialize_ping(rec))


async def _consume_triggers(trigger_stream: EventStream, detector: P0bDetector) -> None:
    async for _sid, rec in trigger_stream.read():
        detector.observe_trigger(deserialize_trigger(rec))


async def run_p0b_pipeline(
    trigger_stream: EventStream,
    ping_stream: EventStream,
    detector: P0bDetector,
    density: DensityTracker,
    correlator: Correlator,
    on_transition: Callable[[Transition], Awaitable[None]],
    *,
    tick_s: float = 1.0,
    clock: Callable[[], int],
) -> None:
    # `detector` must be built via P0bDetector.production(background, density, ...)
    # with THIS SAME `density` instance (FUND 6): that guarantees the
    # ScoreDetector inside `detector` reads nu from the tracker _consume_pings
    # fills below, rather than a disconnected/test-only density source.
    pings = asyncio.create_task(_consume_pings(ping_stream, density))
    trigs = asyncio.create_task(_consume_triggers(trigger_stream, detector))
    try:
        while True:
            now = clock()
            ev, _attn = detector.evaluate(now)
            if ev is not None:
                tr = correlator.ingest(ev)
                if tr is not None:
                    await on_transition(tr)
            await asyncio.sleep(tick_s)
    finally:
        pings.cancel()
        trigs.cancel()
