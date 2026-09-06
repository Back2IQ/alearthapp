from __future__ import annotations

from alert2iq_server.domain.hazards import HazardEvent, hazard_confidence


def build_hazard_payload(h: HazardEvent, *, now_ms: int) -> dict[str, str]:
    """Self-contained signed-hazard payload (all string values, <=1 KB). Signed
    with the same Ed25519 path as alert payloads (alert.payload.sign_payload).
    `conf` carries the graded delivery decision (push/opt_in/map)."""
    return {
        "v": "1",
        "type": "hazard",
        "hazard": h.hazard_type,
        "alert": h.alert_level,
        "conf": hazard_confidence(h.alert_level, h.hazard_type),
        "id": h.source_event_id,
        "lat": f"{h.lat:.4f}",
        "lon": f"{h.lon:.4f}",
        "title": h.title[:100],
        "country": h.country,
        "event_ts": str(h.event_ms),
        "issued_ts": str(now_ms),
        "url": h.url[:200],
    }
