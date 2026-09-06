"""
Bio-Seismology Pet Anomaly Cluster Engine for Alert2IQ.
Aggregates user-reported pet agitation signals in spatio-temporal cells (20km radius, 30-min window).
Triggers elevated regional attention status when cluster thresholds are exceeded.
"""

import time
import math
from typing import Dict, List, Optional
from dataclasses import dataclass, field

EARTH_RADIUS_KM = 6371.0

def haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    r = math.pi / 180.0
    la1, la2 = lat1 * r, lat2 * r
    dlat = (lat2 - lat1) * r
    dlon = (lon2 - lon1) * r
    a = math.sin(dlat / 2.0) ** 2 + math.cos(la1) * math.cos(la2) * math.sin(dlon / 2.0) ** 2
    a = min(1.0, max(0.0, a))
    return EARTH_RADIUS_KM * 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))

@dataclass
class PetReport:
    report_id: str
    user_id: str
    latitude: float
    longitude: float
    animal_type: str = "DOG" # "DOG", "CAT", "BIRD", "LIVESTOCK"
    timestamp_ts: float = field(default_factory=time.time)

class BioAnomalyClusterEngine:

    def __init__(self, time_window_sec: int = 1800, radius_km: float = 20.0, min_reports_threshold: int = 5):
        self.time_window_sec = time_window_sec
        self.radius_km = radius_km
        self.min_reports_threshold = min_reports_threshold
        self.reports: List[PetReport] = []

    def add_report(self, report: PetReport) -> None:
        self.reports.append(report)
        self.cleanup_old_reports()

    def cleanup_old_reports(self, now_ts: Optional[float] = None) -> None:
        now = now_ts or time.time()
        cutoff = now - self.time_window_sec
        self.reports = [r for r in self.reports if r.timestamp_ts >= cutoff]

    def evaluate_clusters(self, target_lat: float, target_lon: float, now_ts: Optional[float] = None) -> Dict:
        self.cleanup_old_reports(now_ts)
        nearby_reports = [
            r for r in self.reports
            if haversine_km(target_lat, target_lon, r.latitude, r.longitude) <= self.radius_km
        ]
        
        unique_users = {r.user_id for r in nearby_reports}
        cluster_size = len(unique_users)
        is_elevated = cluster_size >= self.min_reports_threshold

        return {
            "target_lat": target_lat,
            "target_lon": target_lon,
            "radius_km": self.radius_km,
            "reports_count": len(nearby_reports),
            "unique_user_count": cluster_size,
            "threshold": self.min_reports_threshold,
            "is_elevated_attention": is_elevated,
            "status": "ATTENTION_ELEVATED" if is_elevated else "NORMAL"
        }