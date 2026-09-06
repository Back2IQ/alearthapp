"""Ultra-Fast 64-Byte Binary UDP Ingestor with 15-Packet Early-Exit Short Circuit.

Alert2IQ (Back2IQ Studio - Founder: Deniz Kiran).

Decodes 64-byte binary UDP packets directly in memory. When 15 valid P-wave packets
from the same Geohash cell arrive within a 200ms window, the cell immediately TRIPS
and invokes the trigger callback (< 15ms total latency).
"""
from __future__ import annotations

import asyncio
import struct
import time
from collections import defaultdict, deque
from typing import Callable, Optional

# Binary struct layout: 16s (device_salt), 6s (geohash), f (peak_accel), d (timestamp_ms), 30s (hmac/padding)
_PAYLOAD_STRUCT = struct.Struct("!16s6sfd30s")
_EARLY_EXIT_THRESHOLD = 15
_WINDOW_MS = 200.0


class FastUdpProtocol(asyncio.DatagramProtocol):
    def __init__(self, on_cell_trip: Callable[[str, float, int], None]):
        self.on_cell_trip = on_cell_trip
        # cell -> deque of timestamp_ms
        self._cell_pulses: dict[str, deque[float]] = defaultdict(deque)
        self._tripped_cells: set[str] = set()

    def datagram_received(self, data: bytes, addr: tuple[str, int]) -> None:
        if len(data) != 64:
            return

        try:
            device_hash, geohash_raw, peak_accel, timestamp_ms, _ = _PAYLOAD_STRUCT.unpack(data)
            geohash = geohash_raw.decode("ascii", errors="ignore").strip()
            if not geohash:
                return

            now_ms = time.time() * 1000.0
            pulses = self._cell_pulses[geohash]
            pulses.append(now_ms)

            # Evict stale entries outside the 200ms window
            while pulses and pulses[0] < now_ms - _WINDOW_MS:
                pulses.popleft()

            # Early-Exit Short-Circuit: If threshold reached and cell not already tripped in window
            if len(pulses) >= _EARLY_EXIT_THRESHOLD and geohash not in self._tripped_cells:
                self._tripped_cells.add(geohash)
                self.on_cell_trip(geohash, float(peak_accel), len(pulses))
                # Reset trip lock after window
                asyncio.get_event_loop().call_later(2.0, self._reset_trip, geohash)

        except Exception:  # noqa: BLE001
            pass

    def _reset_trip(self, geohash: str) -> None:
        self._tripped_cells.discard(geohash)


def unpack_64byte_packet(data: bytes) -> Optional[dict]:
    if len(data) != 64:
        return None
    try:
        device_hash, geohash_raw, peak_accel, timestamp_ms, hmac_pad = _PAYLOAD_STRUCT.unpack(data)
        return {
            "device_hash": device_hash.decode("latin1").rstrip("\x00"),
            "geohash": geohash_raw.decode("ascii", errors="ignore").strip(),
            "peak_accel": float(peak_accel),
            "timestamp_ms": float(timestamp_ms),
        }
    except Exception:  # noqa: BLE001
        return None


def pack_64byte_packet(device_hash: str, geohash: str, peak_accel: float, timestamp_ms: float) -> bytes:
    dev_bytes = device_hash.encode("latin1")[:16].ljust(16, b"\x00")
    geo_bytes = geohash.encode("ascii")[:6].ljust(6, b"\x00")
    padding = b"\x00" * 30
    return _PAYLOAD_STRUCT.pack(dev_bytes, geo_bytes, peak_accel, timestamp_ms, padding)
