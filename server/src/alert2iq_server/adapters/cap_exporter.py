"""
OASIS Common Alerting Protocol (CAP v1.2) Exporter for Alert2IQ.
Enables interoperability with AFAD, EMSC, GDACS, Red Cross, and international disaster agencies.
"""

import json
from datetime import datetime, timezone
from typing import Dict, Any

class CapExporter:

    @staticmethod
    def alert_to_cap_dict(alert_payload: Dict[str, Any], sender: str = "alert2iq-engine@back2iq.com") -> Dict[str, Any]:
        """
        Converts an Alert2IQ hazard or earthquake alert payload to a CAP v1.2 compliant dictionary.
        """
        event_id = alert_payload.get("id", "alert_000")
        mag = alert_payload.get("mag", 0.0)
        tier = alert_payload.get("tier", "P0")
        lat = alert_payload.get("lat", 0.0)
        lon = alert_payload.get("lon", 0.0)
        city_name = alert_payload.get("cityName", alert_payload.get("place", "Unknown Location"))

        raw_ts = alert_payload.get("originTs") or alert_payload.get("time") or (datetime.now(timezone.utc).timestamp() * 1000)
        try:
            origin_ts = float(raw_ts)
        except (ValueError, TypeError):
            origin_ts = datetime.now(timezone.utc).timestamp() * 1000.0

        sent_iso = datetime.fromtimestamp(origin_ts / 1000.0, timezone.utc).isoformat()
        
        severity = "Severe" if mag >= 6.0 or tier == "P2" else ("Moderate" if mag >= 4.5 or tier == "P1" else "Minor")
        urgency = "Immediate" if tier in ["P0", "P2"] else "Expected"
        certainty = "Observed" if tier == "P2" else "Likely"

        return {
            "cap": {
                "version": "1.2",
                "identifier": f"urn:oid:2.49.0.0.792.0.alert2iq.{event_id}",
                "sender": sender,
                "sent": sent_iso,
                "status": "Actual" if not alert_payload.get("test", False) else "Test",
                "msgType": "Alert",
                "scope": "Public",
                "info": [
                    {
                        "category": ["Geo"],
                        "event": "Earthquake Early Warning",
                        "urgency": urgency,
                        "severity": severity,
                        "certainty": certainty,
                        "eventCode": [{"valueName": "SAME", "value": "EQW"}],
                        "expires": datetime.fromtimestamp((origin_ts + 300000) / 1000.0, timezone.utc).isoformat(),
                        "headline": f"Earthquake Warning: M{mag:.1f} near {city_name}",
                        "description": f"Alert2IQ P-wave detection network triggered a {severity} early warning for {city_name}.",
                        "instruction": "DROP, COVER & HOLD ON immediately. Stay away from windows and heavy furniture.",
                        "area": [
                            {
                                "areaDesc": city_name,
                                "circle": f"{lat:.4f},{lon:.4f},100.0"
                            }
                        ]
                    }
                ]
            }
        }

    @staticmethod
    def alert_to_cap_json(alert_payload: Dict[str, Any]) -> str:
        return json.dumps(CapExporter.alert_to_cap_dict(alert_payload), indent=2)