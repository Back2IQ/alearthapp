from __future__ import annotations

import logging
from typing import Protocol

import httpx

from tda_server.alert.payload import build_payload, sign_payload
from tda_server.fusion.correlator import Transition
from tda_server.geo.cells import affected_cells, alert_radius_km

log = logging.getLogger(__name__)


class Transport(Protocol):
    async def send(self, topic: str, data: dict[str, str]) -> None: ...


class FakeTransport:
    def __init__(self) -> None:
        self.sent: list[tuple[str, dict[str, str]]] = []

    async def send(self, topic: str, data: dict[str, str]) -> None:
        self.sent.append((topic, data))


class FcmTransport:
    def __init__(self, project_id: str, credentials_path: str) -> None:
        self.url = f"https://fcm.googleapis.com/v1/projects/{project_id}/messages:send"
        self.credentials_path = credentials_path
        self._client = httpx.AsyncClient(timeout=10)

    def _token(self) -> str:
        from google.auth.transport.requests import Request
        from google.oauth2 import service_account

        creds = service_account.Credentials.from_service_account_file(
            self.credentials_path,
            scopes=["https://www.googleapis.com/auth/firebase.messaging"],
        )
        creds.refresh(Request())
        return creds.token

    async def send(self, topic: str, data: dict[str, str]) -> None:
        body = {"message": {"topic": topic, "data": data,
                            "android": {"priority": "HIGH"}}}
        resp = await self._client.post(
            self.url, json=body,
            headers={"Authorization": f"Bearer {self._token()}"})
        resp.raise_for_status()


class Publisher:
    def __init__(self, transport: Transport, private_key_b64: str,
                 min_mag: float = 4.0) -> None:
        self.transport = transport
        self.private_key_b64 = private_key_b64
        self.min_mag = min_mag
        self._published: set[tuple[str, int]] = set()

    async def publish(self, tr: Transition, *, test: bool = False,
                      now_ms: int) -> int:
        ev = tr.event
        key = (ev.event_id, ev.version)
        if key in self._published:
            return 0
        radius = alert_radius_km(ev.magnitude)
        if ev.magnitude < self.min_mag or radius == 0:
            return 0
        payload = sign_payload(
            build_payload(tr, test=test, now_ms=now_ms), self.private_key_b64)
        cells = sorted(affected_cells(ev.lat, ev.lon, radius))
        for cell in cells:
            await self.transport.send(f"cell_{cell}", payload)
        self._published.add(key)
        log.info("published %s v%s to %d cells", ev.event_id, ev.version, len(cells))
        return len(cells)
