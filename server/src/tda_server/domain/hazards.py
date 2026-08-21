from __future__ import annotations

from dataclasses import dataclass

# Non-earthquake hazard classes GDACS reports (plus EQ for cross-check/context).
HAZARD_TYPES = {"EQ", "TS", "FL", "TC", "VO", "WF", "DR"}


@dataclass(frozen=True)
class HazardEvent:
    """A multi-hazard catalogue signal (own class, distinct from the earthquake
    SourceEvent). Graded confidence like citizen reports - never a raw alarm."""

    source: str
    source_event_id: str
    hazard_type: str  # one of HAZARD_TYPES
    alert_level: str  # "green" | "orange" | "red"
    lat: float
    lon: float
    title: str
    country: str
    event_ms: int
    received_ms: int
    url: str


def hazard_confidence(alert_level: str, hazard_type: str) -> str:
    """Graded confidence -> delivery decision. Earthquakes are owned by the
    seismic pipeline (P0a/P0b + catalogs), so a GDACS EQ is context only, never
    a second alarm. Non-earthquake hazards carry the new alerting value:
    red -> push, orange -> opt-in + map, green -> map only."""
    if hazard_type == "EQ":
        return "map"
    return {"red": "push", "orange": "opt_in", "green": "map"}.get(alert_level, "map")


def serialize_hazard(h: HazardEvent) -> dict[str, str]:
    return {
        "source": h.source,
        "source_event_id": h.source_event_id,
        "hazard_type": h.hazard_type,
        "alert_level": h.alert_level,
        "lat": repr(h.lat),
        "lon": repr(h.lon),
        "title": h.title,
        "country": h.country,
        "event_ms": str(h.event_ms),
        "received_ms": str(h.received_ms),
        "url": h.url,
    }


def deserialize_hazard(d: dict[str, str]) -> HazardEvent:
    return HazardEvent(
        source=d["source"],
        source_event_id=d["source_event_id"],
        hazard_type=d["hazard_type"],
        alert_level=d["alert_level"],
        lat=float(d["lat"]),
        lon=float(d["lon"]),
        title=d["title"],
        country=d["country"],
        event_ms=int(d["event_ms"]),
        received_ms=int(d["received_ms"]),
        url=d["url"],
    )
