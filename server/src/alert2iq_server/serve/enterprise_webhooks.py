"""
Enterprise & Industrial Automation Webhook Dispatcher for Alert2IQ.
Sends sub-millisecond P-wave event payloads to registered factory/building automation systems
to automatically shut gas valves, freeze elevators, and trip electrical breakers.
"""

import hmac
import hashlib
import json
import time
from typing import Dict, Any, List

class EnterpriseWebhookDispatcher:

    def __init__(self):
        self.endpoints: List[Dict[str, str]] = []

    def register_endpoint(self, client_id: str, url: str, secret_key: str) -> None:
        self.endpoints.append({
            "client_id": client_id,
            "url": url,
            "secret_key": secret_key
        })

    def sign_payload(self, payload_str: str, secret_key: str) -> str:
        return hmac.new(
            secret_key.encode("utf-8"),
            payload_str.encode("utf-8"),
            hashlib.sha256
        ).hexdigest()

    def prepare_dispatch_payloads(self, alert_data: Dict[str, Any]) -> List[Dict[str, Any]]:
        dispatches = []
        now_ts = time.time()

        webhook_body = {
            "event": "p_wave_alert",
            "timestamp": now_ts,
            "alert": alert_data,
            "actions_recommended": [
                "CLOSE_GAS_VALVES",
                "PARK_ELEVATORS_NEAREST_FLOOR",
                "SAFE_STOP_INDUSTRIAL_ROBOTS"
            ]
        }
        body_str = json.dumps(webhook_body, sort_keys=True)

        for ep in self.endpoints:
            signature = self.sign_payload(body_str, ep["secret_key"])
            dispatches.append({
                "client_id": ep["client_id"],
                "url": ep["url"],
                "signature": f"sha256={signature}",
                "body": body_str
            })

        return dispatches