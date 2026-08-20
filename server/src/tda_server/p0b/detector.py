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

    def earliest(self, cell: str, now_ms: int) -> int | None:
        """Earliest still-active trigger timestamp for cell, after evicting
        entries older than eps. None if no active trigger remains - callers
        must treat that as "cell currently inactive", not "use a stale value".
        """
        dq = self._ev.get(cell)
        if not dq:
            return None
        cutoff = now_ms - self.eps_ms
        while dq and dq[0][0] <= cutoff:
            dq.popleft()
        return dq[0][0] if dq else None

    def cells(self) -> list[str]:
        return list(self._ev)


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


from dataclasses import dataclass
from datetime import datetime, timezone

from tda_server.domain.events import SourceEvent
from tda_server.p0b.cluster import cluster_origin, form_cluster
from tda_server.p0b.reputation import ReputationStore, trigger_weight
from tda_server.p0b.signals import ActivePing, PhoneTrigger
from tda_server.p0b.wavefront import CellHit, wavefront_consistent


@dataclass(frozen=True)
class AttentionSignal:
    cell: str
    level: float
    at_ms: int


def estimate_magnitude(cluster: list[CellHit]) -> float:
    """Coarse lower-bound proxy from felt-area extent. NOT an instrumental value;
    Turkish calibration is a Plan B step. Never claim beyond point-source saturation."""
    from tda_server.geo.cells import haversine_km
    from tda_server.p0b.signals import detect_cell_center
    centers = [detect_cell_center(h.cell) for h in cluster]
    olat, olon = detect_cell_center(min(cluster, key=lambda h: h.first_ms).cell)
    radius_km = max((haversine_km(la, lo, olat, olon) for la, lo in centers), default=0.0)
    # felt radius -> rough magnitude floor; conservative, capped at saturation
    mag = 4.0 + 0.9 * (radius_km / 30.0)
    return round(min(mag, 7.0), 1)


class P0bDetector:
    def __init__(self, *, detector: "ScoreDetector", reputation: ReputationStore,
                 threshold_h: float, attn_h: float, eps_s: float = 20.0,
                 min_cells: int = 3) -> None:
        self.detector = detector
        self.reputation = reputation
        self.threshold_h = threshold_h
        self.attn_h = attn_h
        self.window = ScoreWindow(eps_s=eps_s)
        self.min_cells = min_cells

    def observe_trigger(self, t: PhoneTrigger) -> None:
        w = trigger_weight(self.reputation, t.device_hash, t.attest_ok)
        self.window.add(t.cell, t.trigger_ms, w)

    def evaluate(self, now_ms: int) -> tuple[SourceEvent | None, AttentionSignal | None]:
        hot: list[str] = []
        attn: AttentionSignal | None = None
        first_hit: dict[str, int] = {}
        # Only cells with a trigger still active inside the eps window count -
        # a cell whose triggers have all aged out contributes neither to a
        # score nor to a stale first_ms (FUND 1: first_hit must age with the
        # window, or a later event inherits an earlier one's frozen timing).
        for cell in self.window.cells():
            fm = self.window.earliest(cell, now_ms)
            if fm is None:
                continue
            first_hit[cell] = fm
            s = self.detector.score(cell, now_ms, self.window)
            if s >= self.attn_h and (attn is None or s > attn.level):
                attn = AttentionSignal(cell, s, now_ms)
            if s >= self.threshold_h:
                hot.append(cell)
        if len(hot) < self.min_cells:
            return None, attn
        hits = [CellHit(cell, first_hit[cell]) for cell in hot]
        cluster = form_cluster(hits)
        if len(cluster) < self.min_cells or not wavefront_consistent(
                cluster, min_cells=self.min_cells):
            return None, attn
        lat, lon, origin_ms = cluster_origin(cluster)
        ev = SourceEvent(
            source="p0b",
            source_event_id=f"p0b:{origin_ms}:{cluster[0].cell}",
            origin_time=datetime.fromtimestamp(origin_ms / 1000, tz=timezone.utc),
            lat=lat, lon=lon, depth_km=None,
            magnitude=estimate_magnitude(cluster),
            mag_type="p0b_proxy",
            received_at=datetime.fromtimestamp(now_ms / 1000, tz=timezone.utc),
        )
        return ev, attn

    @classmethod
    def production(cls, background: BackgroundModel, density: DensityTracker, *,
                   threshold_h: float, attn_h: float,
                   reputation: ReputationStore | None = None,
                   eps_s: float = 20.0, min_cells: int = 3) -> "P0bDetector":
        """Production constructor (FUND 6). The ScoreDetector reads nu from
        the exact same DensityTracker instance the caller passes to
        run_p0b_pipeline (which _consume_pings fills from real ActivePings),
        so Pings -> Density -> Score are guaranteed to share one tracker
        object. Wiring up two separate DensityTracker instances (or using
        the test-only build_p0b_detector/_SeededDensity path) leaves nu
        permanently 0 for the detector -> score always -1 -> never fires."""
        det = ScoreDetector(background, density, eps_s=eps_s)
        return cls(detector=det, reputation=reputation or ReputationStore(),
                   threshold_h=threshold_h, attn_h=attn_h, eps_s=eps_s,
                   min_cells=min_cells)


def build_p0b_detector(background: BackgroundModel, *, nu_per_cell: int,
                       threshold_h: float, attn_h: float, eps_s: float = 20.0,
                       min_cells: int = 3) -> P0bDetector:
    """Test/helper constructor: fixed synthetic density per cell."""
    density = DensityTracker(active_ttl_ms=10_000_000)
    # seed nu_per_cell active devices into every cell that later triggers
    class _SeededDensity(DensityTracker):
        def nu(self, cell: str, now_ms: int) -> int:
            return nu_per_cell
    det = ScoreDetector(background, _SeededDensity(), eps_s=eps_s)
    return P0bDetector(detector=det, reputation=ReputationStore(),
                       threshold_h=threshold_h, attn_h=attn_h, eps_s=eps_s,
                       min_cells=min_cells)
