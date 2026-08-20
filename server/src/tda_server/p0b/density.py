from __future__ import annotations

from tda_server.p0b.signals import ActivePing


class DensityTracker:
    """Live network size nu_t per detection cell: distinct devices whose last
    active ping is within active_ttl_ms. This is the ONLY admissible baseline
    for significance (spec 3: relative to live density, never absolute)."""

    def __init__(self, active_ttl_ms: int = 2_700_000) -> None:
        self.active_ttl_ms = active_ttl_ms
        # cell -> device_hash -> last_ping_ms
        self._last: dict[str, dict[str, int]] = {}

    def observe(self, ping: ActivePing) -> None:
        self._last.setdefault(ping.cell, {})[ping.device_hash] = ping.ping_ms

    def nu(self, cell: str, now_ms: int) -> int:
        devs = self._last.get(cell)
        if not devs:
            return 0
        cutoff = now_ms - self.active_ttl_ms
        return sum(1 for last in devs.values() if last > cutoff)

    def prune(self, now_ms: int) -> None:
        cutoff = now_ms - self.active_ttl_ms
        for cell in list(self._last):
            devs = self._last[cell]
            for dev in [d for d, last in devs.items() if last <= cutoff]:
                del devs[dev]
            if not devs:
                del self._last[cell]
