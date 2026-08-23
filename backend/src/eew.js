// EEW core formulas — identical to tda/webapp/index.html's Eew object.
export const EARTH_RADIUS_KM = 6371.0;
export const V_S_KM_S = 3.5;
export const V_P_KM_S = 6.0;

export function haversineKm(lat1, lon1, lat2, lon2) {
  const r = Math.PI / 180;
  const la1 = lat1 * r;
  const la2 = lat2 * r;
  const dLat = (lat2 - lat1) * r;
  const dLon = (lon2 - lon1) * r;
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(la1) * Math.cos(la2) * Math.sin(dLon / 2) ** 2;
  return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

export function mmi(mag, d) {
  return Math.min(12, Math.max(1, 1.5 * mag - 3 * Math.log10(Math.max(d, 1)) + 3));
}

export function sWaveEtaSeconds(d, e) {
  return Math.max(0, d / V_S_KM_S - e);
}

export function pWaveEtaSeconds(d, e) {
  return Math.max(0, d / V_P_KM_S - e);
}
