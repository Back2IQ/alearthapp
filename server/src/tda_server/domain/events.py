from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum


class EventState(str, Enum):
    DETECTED = "detected"
    ALERTED = "alerted"
    CONFIRMED = "confirmed"
    RETRACTED = "retracted"


@dataclass(frozen=True)
class SourceEvent:
    source: str
    source_event_id: str
    origin_time: datetime
    lat: float
    lon: float
    depth_km: float | None
    magnitude: float
    mag_type: str
    received_at: datetime


@dataclass
class CanonicalEvent:
    event_id: str
    state: EventState
    origin_time: datetime
    lat: float
    lon: float
    depth_km: float | None
    magnitude: float
    mag_low: float
    mag_high: float
    version: int
    sources: dict[str, SourceEvent] = field(default_factory=dict)

    ESCALATION_DELTA = 0.2

    @classmethod
    def from_source(cls, se: SourceEvent) -> "CanonicalEvent":
        return cls(
            event_id=f"{se.source}:{se.source_event_id}",
            state=EventState.DETECTED,
            origin_time=se.origin_time,
            lat=se.lat,
            lon=se.lon,
            depth_km=se.depth_km,
            magnitude=se.magnitude,
            mag_low=se.magnitude,
            mag_high=se.magnitude,
            version=1,
            sources={se.source: se},
        )

    def merge(self, se: SourceEvent) -> bool:
        """Merge a source reading. Returns True if the alert-relevant estimate
        escalated (magnitude max rose by >= ESCALATION_DELTA). Early magnitudes
        are lower bounds: the max wins, de-escalation needs catalog consensus
        (handled at P2, not here) - among sources of the SAME precedence
        class. The p0b felt-area proxy ("p0b_proxy") is a coarse floor, not a
        measurement: once any instrumental reading exists (any mag_type other
        than "p0b_proxy"), the canonical magnitude is derived only from the
        instrumental readings (still escalate-fast max among those), so a
        more accurate instrumental value can never lose to a louder proxy."""
        self.sources[se.source] = se
        instrumental = [s for s in self.sources.values() if s.mag_type != "p0b_proxy"]
        old_max = self.mag_high
        if instrumental:
            mags = [s.magnitude for s in instrumental]
            self.mag_low = min(mags)
            self.mag_high = max(mags)
        else:
            self.mag_low = min(self.mag_low, se.magnitude)
            self.mag_high = max(self.mag_high, se.magnitude)
        # tolerance guards against binary float error on exact deltas
        # (e.g. 2.3 - 2.1 == 0.19999999999999973)
        escalated = (self.mag_high - old_max) >= self.ESCALATION_DELTA - 1e-9
        self.magnitude = self.mag_high
        return escalated
