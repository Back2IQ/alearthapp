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

    def calculate_hurst_exponent(self, time_series: List[float]) -> float:
        """Calculates Hurst Exponent H_bio = 1 - sum(log(R/S)_n) / (2 * log(n)).
        P0c Tertiary Research Indicator: H_bio < 0.3 indicates anomalous pet agitation cluster.
        Does NOT trigger siren gates (C0-C2), acts solely as Advanced Mode confidence booster.
        """
        n = len(time_series)
        if n < 4:
            return 0.5 # Random walk default
        
        mean_val = sum(time_series) / n
        deviations = [x - mean_val for x in time_series]
        
        # Cumulative deviations
        cum_dev = []
        current_sum = 0.0
        for dev in deviations:
            current_sum += dev
            cum_dev.append(current_sum)
            
        r_range = max(cum_dev) - min(cum_dev)
        variance = sum(d * d for d in deviations) / n
        s_std = math.sqrt(max(1e-6, variance))
        
        rs = r_range / s_std
        if rs <= 0 or n <= 1:
            return 0.5
            
        h_bio = 1.0 - (math.log(max(1.0001, rs)) / (2.0 * math.log(n)))
        return min(1.0, max(0.0, h_bio))

    def evaluate_clusters(self, target_lat: float, target_lon: float, now_ts: Optional[float] = None) -> Dict:
        self.cleanup_old_reports(now_ts)
        nearby_reports = [
            r for r in self.reports
            if haversine_km(target_lat, target_lon, r.latitude, r.longitude) <= self.radius_km
        ]
        
        unique_users = {r.user_id for r in nearby_reports}
        cluster_size = len(unique_users)
        is_elevated = cluster_size >= self.min_reports_threshold

        # Time series of report counts per 5-min bin
        binned_counts = [0] * 6
        if now_ts is None:
            now_ts = time.time()
        for r in nearby_reports:
            age_min = (now_ts - r.timestamp_ts) / 60.0
            bin_idx = min(5, max(0, int(age_min / 5.0)))
            binned_counts[bin_idx] += 1
            
        h_bio = self.calculate_hurst_exponent(binned_counts)
        is_anomalous = h_bio < 0.3 and is_elevated

        return {
            "target_lat": target_lat,
            "target_lon": target_lon,
            "radius_km": self.radius_km,
            "reports_count": len(nearby_reports),
            "unique_user_count": cluster_size,
            "threshold": self.min_reports_threshold,
            "is_elevated_attention": is_elevated,
            "h_bio_exponent": round(h_bio, 4),
            "is_p0c_research_anomaly": is_anomalous,
            "status": "ATTENTION_ELEVATED" if is_elevated else "NORMAL",
            "tier": "P0c_EXPERIMENTAL"
        }