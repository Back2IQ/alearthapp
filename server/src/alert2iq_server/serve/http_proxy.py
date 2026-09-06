"""Tiny CORS HTTP proxy for the browser web-app.

GDACS (multi-hazard: tsunami / cyclone / flood / volcano / wildfire / drought)
and AFAD (official Turkey earthquakes) do NOT send CORS headers, so a browser
served from file:// cannot fetch them directly. This proxy fetches them
server-side (reusing the already-tested adapters' parse logic) and re-serves
them as JSON with `Access-Control-Allow-Origin: *`, with a short in-process
cache so a page refresh never hammers the upstream.

Runs in a background thread next to the WebSocket bridge (see serve_local.py).
Never on the alarm-critical path - purely an enrichment feed for the map.
"""
from __future__ import annotations

import json
import logging
import threading
import time
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import httpx

from alert2iq_server.adapters.afad import AFAD_TZ, AFAD_URL, parse_afad_response
from alert2iq_server.adapters.gdacs import GDACS_URL, parse_gdacs_feed
from alert2iq_server.domain.hazards import hazard_confidence

log = logging.getLogger(__name__)

_CACHE_TTL_S = 300.0
_cache: dict[str, tuple[float, list]] = {}
_lock = threading.Lock()


def _cached(key: str, producer):
    """Return producer() output, cached per key for _CACHE_TTL_S seconds.
    On a producer error, serve a stale value if we have one, else re-raise."""
    now = time.time()
    with _lock:
        hit = _cache.get(key)
    if hit and now - hit[0] < _CACHE_TTL_S:
        return hit[1]
    try:
        data = producer()
    except Exception as exc:  # noqa: BLE001 - upstream best-effort
        log.warning("proxy %s upstream failed: %s", key, exc)
        if hit:
            return hit[1]
        raise
    with _lock:
        _cache[key] = (now, data)
    return data


def _fetch_hazards() -> list[dict]:
    with httpx.Client(timeout=30, follow_redirects=True) as client:
        resp = client.get(GDACS_URL)
        resp.raise_for_status()
        events = parse_gdacs_feed(resp.json(), datetime.now(timezone.utc))
    out = []
    for h in events:
        out.append({
            "source": "GDACS",
            "type": h.hazard_type,
            "alert": h.alert_level,
            "conf": hazard_confidence(h.alert_level, h.hazard_type),
            "lat": h.lat,
            "lon": h.lon,
            "title": h.title,
            "country": h.country,
            "event_ms": h.event_ms,
            "url": h.url,
        })
    return out


def _fetch_afad() -> list[dict]:
    now = datetime.now(timezone.utc)
    now_trt = now.astimezone(AFAD_TZ)
    params = {
        "start": (now_trt - timedelta(hours=24)).strftime("%Y-%m-%dT%H:%M:%S"),
        "end": now_trt.strftime("%Y-%m-%dT%H:%M:%S"),
        "minmag": "1",
    }
    with httpx.Client(timeout=20, follow_redirects=True) as client:
        resp = client.get(AFAD_URL, params=params)
        resp.raise_for_status()
        raw = resp.json()
    events = parse_afad_response(raw, now)
    # parse_afad_response drops the human location string; recover it by id.
    loc_by_id = {str(it.get("eventID")): it.get("location", "") for it in raw}
    out = []
    for se in events:
        out.append({
            "source": "AFAD",
            "mag": se.magnitude,
            "place": loc_by_id.get(se.source_event_id, ""),
            "time": int(se.origin_time.timestamp() * 1000),
            "lat": se.lat,
            "lon": se.lon,
            "depth": se.depth_km,
        })
    return out


class _Handler(BaseHTTPRequestHandler):
    def _send(self, code: int, body: bytes, ctype: str = "application/json") -> None:
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Cache-Control", "public, max-age=120")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self) -> None:  # noqa: N802 - stdlib naming
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, OPTIONS")
        self.end_headers()

    def do_GET(self) -> None:  # noqa: N802 - stdlib naming
        path = self.path.split("?", 1)[0].rstrip("/")
        try:
            if path == "/hazards":
                data = _cached("hazards", _fetch_hazards)
            elif path == "/afad":
                data = _cached("afad", _fetch_afad)
            elif path in ("", "/health"):
                self._send(200, b'{"ok":true}')
                return
            else:
                self._send(404, b'{"error":"not found"}')
                return
        except Exception:  # noqa: BLE001
            self._send(502, b'{"error":"upstream unavailable"}')
            return
        self._send(200, json.dumps(data).encode("utf-8"))

    def log_message(self, *args) -> None:  # keep the console quiet
        pass


def start_http_proxy(host: str = "0.0.0.0", port: int = 8001) -> ThreadingHTTPServer:
    """Start the proxy in a daemon thread and return the server object."""
    server = ThreadingHTTPServer((host, port), _Handler)
    threading.Thread(target=server.serve_forever, name="tda-http-proxy", daemon=True).start()
    log.info("TDA HTTP proxy on http://%s:%d (/hazards, /afad)", host, port)
    return server
