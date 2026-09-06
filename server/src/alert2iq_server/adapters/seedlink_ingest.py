"""SeedLink Seismometer Stream Adapter (Alert2IQ - Back2IQ Studio).

Parses SeedLink MiniSEED station packets and ingests professional observatory ground-truth streams
(Kandilli Observatory, AFAD, EMSC) to establish 24/7 stationary seismic baseline signals.
"""
from __future__ import annotations

import struct
from dataclasses import dataclass
from typing import Optional


@dataclass(frozen=True)
class StationPacket:
    network: str
    station: str
    location: str
    channel: str
    timestamp_ms: float
    sample_rate: float
    sample_count: int
    peak_amplitude: float


def parse_mini_seed_header(data: bytes) -> Optional[StationPacket]:
    """Parses a SeedLink 512-byte MiniSEED packet header."""
    if len(data) < 48:
        return None

    try:
        # Check sequence number (6 ascii chars) + header quality indicator ('D', 'R', or 'M')
        seq_num = data[:6].decode("ascii", errors="ignore")
        quality = data[6:7].decode("ascii", errors="ignore")
        if quality not in ("D", "R", "M", "Q"):
            return None

        station = data[8:13].decode("ascii", errors="ignore").strip()
        location = data[13:15].decode("ascii", errors="ignore").strip()
        channel = data[15:18].decode("ascii", errors="ignore").strip()
        network = data[18:20].decode("ascii", errors="ignore").strip()

        year, day_of_year, hour, minute, second, _, _, num_samples, sample_rate_fact, sample_rate_mult = struct.unpack(
            ">HHBBBBHHHH", data[20:36]
        )


        sample_rate = float(sample_rate_fact) if sample_rate_fact > 0 else 1.0
        if sample_rate_mult > 1:
            sample_rate *= sample_rate_mult

        # Crude peak amplitude estimation from remaining bytes for test/fast verification
        peak_amp = 0.0
        if len(data) >= 52:
            raw_vals = struct.unpack_from(">4i", data, 36)
            peak_amp = float(max(abs(v) for v in raw_vals))


        return StationPacket(
            network=network,
            station=station,
            location=location,
            channel=channel,
            timestamp_ms=float(hour * 3600 + minute * 60 + second) * 1000.0,
            sample_rate=sample_rate,
            sample_count=num_samples,
            peak_amplitude=peak_amp
        )
    except Exception:  # noqa: BLE001
        return None
