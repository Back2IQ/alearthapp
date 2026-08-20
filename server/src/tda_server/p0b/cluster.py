from __future__ import annotations

from tda_server.geo.cells import haversine_km
from tda_server.p0b.signals import detect_cell_center
from tda_server.p0b.wavefront import CellHit


def form_cluster(hits: list[CellHit], *, link_km: float = 40.0) -> list[CellHit]:
    """Single-linkage spatial clustering; return the largest connected group."""
    if not hits:
        return []
    centers = [detect_cell_center(h.cell) for h in hits]
    n = len(hits)
    parent = list(range(n))

    def find(i: int) -> int:
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    for i in range(n):
        for j in range(i + 1, n):
            if haversine_km(*centers[i], *centers[j]) <= link_km:
                parent[find(i)] = find(j)

    groups: dict[int, list[CellHit]] = {}
    for i in range(n):
        groups.setdefault(find(i), []).append(hits[i])
    return max(groups.values(), key=len)


def cluster_origin(cluster: list[CellHit]) -> tuple[float, float, int]:
    earliest = min(cluster, key=lambda h: h.first_ms)
    lat, lon = detect_cell_center(earliest.cell)
    return lat, lon, earliest.first_ms
