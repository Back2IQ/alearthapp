"""
Fault Zone Database & Micro-Swarm Detector for Alert2IQ.
Tracks major tectonic fault segments (North Anatolian, East Anatolian, Aegean Arc, San Andreas)
and flags unusual foreshock micro-swarms (M1.5 - M2.8).
"""

import time
from typing import Dict, List, Optional
from dataclasses import dataclass, field

@dataclass
class FaultSegment:
    segment_id: str
    name: str
    region: str
    last_major_rupture_year: int
    slip_rate_mm_year: float
    locked_status: bool = True
    slip_deficit_meters: float = 0.0

@dataclass
class MicroQuake:
    quake_id: str
    magnitude: float
    latitude: float
    longitude: float
    timestamp_ts: float = field(default_factory=time.time)

KNOWN_FAULTS = [
    FaultSegment("naf_marmara", "North Anatolian Fault — Marmara Segment", "Turkey / Istanbul", 1766, 24.0, True, 6.2),
    FaultSegment("eaf_pütürge", "East Anatolian Fault — Pütürge Segment", "Turkey / Malatya", 2020, 10.0, False, 0.4),
    FaultSegment("aegean_arc", "Hellenic & Aegean Volcanic Arc", "Greece / Turkey", 1956, 35.0, True, 2.4),
    FaultSegment("san_andreas_south", "San Andreas Fault — Southern Segment", "USA / California", 1690, 30.0, True, 10.0)
]

class FaultStressMonitor:

    def __init__(self, swarm_window_sec: int = 10800, min_swarm_count: int = 5):
        self.swarm_window_sec = swarm_window_sec
        self.min_swarm_count = min_swarm_count
        self.micro_quakes: List[MicroQuake] = []
        self.faults = {f.segment_id: f for f in KNOWN_FAULTS}

    def add_micro_quake(self, quake: MicroQuake) -> None:
        self.micro_quakes.append(quake)
        self.cleanup_old_quakes()

    def cleanup_old_quakes(self, now_ts: Optional[float] = None) -> None:
        now = now_ts or time.time()
        cutoff = now - self.swarm_window_sec
        self.micro_quakes = [q for q in self.micro_quakes if q.timestamp_ts >= cutoff]

    def evaluate_swarm_alert(self, now_ts: Optional[float] = None) -> Dict:
        self.cleanup_old_quakes(now_ts)
        quake_count = len(self.micro_quakes)
        is_swarm_active = quake_count >= self.min_swarm_count
        
        return {
            "micro_quakes_in_window": quake_count,
            "window_hours": self.swarm_window_sec / 3600.0,
            "is_swarm_active": is_swarm_active,
            "attention_level": "ATTENTION_SWARM" if is_swarm_active else "NORMAL",
            "monitored_faults_count": len(self.faults)
        }