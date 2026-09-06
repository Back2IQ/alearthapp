"""3D Seismic Polarization Vector Analysis (Alert2IQ - Back2IQ Studio).

Evaluates 3D acceleration vectors (ax, ay, az) and STA/LTA energy ratios
to distinguish vertical subsurface tectonic P-waves from horizontal noise.
"""
from __future__ import annotations

import math
from dataclasses import dataclass


@dataclass(frozen=True)
class PolarizationResult:
    is_p_wave: bool
    net_magnitude_g: float
    dip_angle_deg: float
    sta_lta_ratio: float
    confidence_score: float
    vector_evidence_index: float = 0.0
    signal_consistency_score: float = 0.0


def evaluate_polarization(
    ax: float,
    ay: float,
    az: float,
    sta_lta: float,
    gravity_norm: float = 9.81,
    reputation: float = 1.0,
    env_noise: float = 0.0,
    t_seconds: float = 1.0
) -> PolarizationResult:
    total_mag = math.sqrt(ax * ax + ay * ay + az * az)
    if total_mag < 1e-6:
        return PolarizationResult(
            is_p_wave=False,
            net_magnitude_g=0.0,
            dip_angle_deg=0.0,
            sta_lta_ratio=sta_lta,
            confidence_score=0.0,
            vector_evidence_index=0.0,
            signal_consistency_score=0.0
        )

    cos_theta = max(-1.0, min(1.0, az / total_mag))
    dip_angle_deg = math.degrees(math.acos(cos_theta))

    # Vertical-motion compatibility indicator d in {0, 1}
    # Dip angle relative to horizontal plane (theta >= 15 degrees)
    theta_rad = math.asin(min(1.0, abs(az) / total_mag))
    d_vertical = 1.0 if theta_rad >= (math.pi / 12.0) else 0.0

    # Degree of polarization p in [0, 1]
    p_polarization = min(1.0, max(0.0, abs(az) / total_mag))

    # Normalized STA/LTA ratio s_n in [0, 1]
    s_norm = min(1.0, max(0.0, (sta_lta - 1.5) / 8.5))

    # Vector Evidence Index S_vec = d * p * s_n in [0.0, 1.0]
    s_vec = d_vertical * p_polarization * s_norm

    # Signal Consistency Score S_cons = clamp((S_vec - 0.2) * R^2 / ((1 + N_env) * T), 0.0, 1.0)
    s_cons_raw = ((s_vec - 0.2) * (reputation ** 2)) / ((1.0 + max(0.0, env_noise)) * max(0.1, t_seconds))
    s_cons = min(1.0, max(0.0, s_cons_raw))

    is_vertical = (dip_angle_deg <= 45.0) or (dip_angle_deg >= 135.0)
    is_impulsive = sta_lta >= 4.5
    net_mag_g = max(0.0, total_mag - gravity_norm) / gravity_norm
    is_significant = net_mag_g >= 0.03 or total_mag >= 0.3

    is_p_wave = is_impulsive and is_vertical and is_significant

    if is_p_wave:
        confidence = min(1.0, (sta_lta / 10.0) * 0.5 + (net_mag_g / 0.5) * 0.5)
    else:
        confidence = 0.0

    return PolarizationResult(
        is_p_wave=is_p_wave,
        net_magnitude_g=round(net_mag_g, 4),
        dip_angle_deg=round(dip_angle_deg, 2),
        sta_lta_ratio=round(sta_lta, 2),
        confidence_score=round(confidence, 4),
        vector_evidence_index=round(s_vec, 4),
        signal_consistency_score=round(s_cons, 4)
    )
