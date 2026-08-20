from __future__ import annotations

from collections import defaultdict, deque

from tda_server.p0b.background import BackgroundModel
from tda_server.p0b.density import DensityTracker


class ScoreWindow:
    """Sliding window of (timestamp, weight) trigger events per cell."""

    def __init__(self, eps_s: float) -> None:
        self.eps_ms = int(eps_s * 1000)
        self._ev: dict[str, deque[tuple[int, float]]] = defaultdict(deque)

    def add(self, cell: str, ts_ms: int, weight: float) -> None:
        self._ev[cell].append((ts_ms, weight))

    def count(self, cell: str, now_ms: int) -> float:
        dq = self._ev.get(cell)
        if not dq:
            return 0.0
        cutoff = now_ms - self.eps_ms
        while dq and dq[0][0] <= cutoff:
            dq.popleft()
        return sum(w for _ts, w in dq)

    def evict(self, now_ms: int) -> None:
        cutoff = now_ms - self.eps_ms
        for cell in list(self._ev):
            dq = self._ev[cell]
            while dq and dq[0][0] <= cutoff:
                dq.popleft()
            if not dq:
                del self._ev[cell]


class ScoreDetector:
    def __init__(self, background: BackgroundModel, density: DensityTracker,
                 eps_s: float = 20.0) -> None:
        self.bg = background
        self.density = density
        self.eps_s = eps_s

    def score(self, cell: str, now_ms: int, window: ScoreWindow) -> float:
        nu = self.density.nu(cell, now_ms)
        if nu <= 0:
            return -1.0
        n_eps = window.count(cell, now_ms)
        expected = self.bg.expected(nu, self.eps_s)
        if expected <= 0:
            return -1.0
        return n_eps / expected - 1.0
