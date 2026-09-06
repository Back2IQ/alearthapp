from __future__ import annotations

from typing import NamedTuple

from alert2iq_server.domain.events import CanonicalEvent, EventState, SourceEvent
from alert2iq_server.geo.cells import haversine_km


class Transition(NamedTuple):
    event: CanonicalEvent
    kind: str  # "new" | "escalate" | "confirm"


class Correlator:
    def __init__(self, window_s: float = 180.0, radius_km: float = 120.0) -> None:
        self.window_s = window_s
        self.radius_km = radius_km
        self.events: list[CanonicalEvent] = []
        self._by_source_id: dict[tuple[str, str], CanonicalEvent] = {}

    def ingest(self, se: SourceEvent) -> Transition | None:
        ev = self._by_source_id.get((se.source, se.source_event_id))
        if ev is None:
            ev = self._match(se)
        if ev is None:
            ev = CanonicalEvent.from_source(se)
            self.events.append(ev)
            self._by_source_id[(se.source, se.source_event_id)] = ev
            return Transition(ev, "new")

        self._by_source_id[(se.source, se.source_event_id)] = ev
        known_sources = set(ev.sources)
        escalated = ev.merge(se)
        confirmed = (
            se.source not in known_sources
            and len(ev.sources) >= 2
            and ev.state is not EventState.CONFIRMED
        )
        if confirmed:
            ev.state = EventState.CONFIRMED
        if confirmed or escalated:
            ev.version += 1
            return Transition(ev, "escalate" if escalated else "confirm")
        return None

    def _match(self, se: SourceEvent) -> CanonicalEvent | None:
        for ev in self.events:
            dt = abs((se.origin_time - ev.origin_time).total_seconds())
            if dt > self.window_s:
                continue
            if haversine_km(se.lat, se.lon, ev.lat, ev.lon) <= self.radius_km:
                return ev
        return None
