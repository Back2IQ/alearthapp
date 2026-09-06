from __future__ import annotations

import math

CELL_DEG = 0.5
_EARTH_R_KM = 6371.0


def haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp = math.radians(lat2 - lat1)
    dl = math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * _EARTH_R_KM * math.asin(math.sqrt(a))


def cell_id(lat: float, lon: float) -> str:
    return f"c{math.floor(lat / CELL_DEG)}_{math.floor(lon / CELL_DEG)}"


def affected_cells(lat: float, lon: float, radius_km: float) -> set[str]:
    """All 0.5-degree cells whose center could lie within radius_km.
    Bounding-box scan with a half-cell margin; coarse on purpose - the client
    filters precisely against its own thresholds."""
    lat_margin = radius_km / 111.0 + CELL_DEG
    lon_scale = max(0.2, math.cos(math.radians(lat)))
    lon_margin = radius_km / (111.0 * lon_scale) + CELL_DEG
    cells: set[str] = set()
    la = lat - lat_margin
    while la <= lat + lat_margin:
        lo = lon - lon_margin
        while lo <= lon + lon_margin:
            cells.add(cell_id(la, lo))
            lo += CELL_DEG
        la += CELL_DEG
    return cells


def alert_radius_km(magnitude: float) -> float:
    if magnitude < 4.0:
        return 0.0
    if magnitude < 5.0:
        return 150.0
    if magnitude < 6.0:
        return 300.0
    if magnitude < 7.0:
        return 600.0
    return 1000.0
