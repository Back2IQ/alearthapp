from __future__ import annotations

import logging
from collections import defaultdict, deque
from typing import Awaitable, Callable

log = logging.getLogger(__name__)


class RingBuffer:
    def __init__(self, max_s: float = 120.0) -> None:
        self.max_s = max_s
        self._traces: dict[str, deque] = defaultdict(deque)

    def append(self, station: str, trace) -> None:
        dq = self._traces[station]
        dq.append(trace)
        # trim by count as a simple bound; real trimming by endtime on the VM
        while len(dq) > 256:
            dq.popleft()

    def window(self, station: str):
        return list(self._traces.get(station, []))


class SeedLinkIngest:
    """ObsPy EasySeedLinkClient wrapper. Integration path; wired on the VM once
    Task 1 confirms an open SeedLink endpoint. Skipped in CI without obspy."""

    def __init__(self, url: str, streams: list[str]) -> None:
        self.url = url
        self.streams = streams
        self.buffer = RingBuffer()

    async def run(self, on_stream: Callable[[str, object], Awaitable[None]]) -> None:
        from obspy.clients.seedlink.easyseedlink import EasySeedLinkClient  # noqa: F401
        raise NotImplementedError(
            "seedlink wiring pending open endpoint from Task 1 coverage decision")
