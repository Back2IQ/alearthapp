from __future__ import annotations


class ReputationStore:
    """Per-device reputation, keyed by hardware attest id (not app instance id),
    so reinstalling does not reset to a fresh neutral score (spec 3)."""

    def __init__(self, base: float = 0.1, cap: float = 1.0, gain: float = 0.05) -> None:
        self.base = base
        self.cap = cap
        self.gain = gain
        self._rep: dict[str, float] = {}

    def _raw(self, attest_id: str) -> float:
        return self._rep.get(attest_id, self.base)

    def weight(self, attest_id: str, attest_ok: bool) -> float:
        rep = self._raw(attest_id)
        if not attest_ok:
            return min(rep, self.base)          # unattested devices cannot earn weight
        return max(0.0, min(rep, self.cap))

    def reward(self, attest_id: str) -> None:
        self._rep[attest_id] = min(self.cap, self._raw(attest_id) + self.gain)

    def penalize(self, attest_id: str, factor: float = 0.5) -> None:
        self._rep[attest_id] = max(0.0, self._raw(attest_id) * factor)


def trigger_weight(store: ReputationStore, attest_id: str, attest_ok: bool) -> float:
    return store.weight(attest_id, attest_ok)
