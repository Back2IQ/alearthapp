// Pure, DOM-free disaster-list logic. UMD: window.Disasters (browser, classic
// script — NO ES modules, they are blocked from file://) and module.exports (Node).
(function (root, factory) {
  const api = factory();
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  else root.Disasters = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  const EARTH_R_KM = 6371.0;
  function haversineKm(lat1, lon1, lat2, lon2) {
    const r = Math.PI / 180, la1 = lat1 * r, la2 = lat2 * r, dLat = (lat2 - lat1) * r, dLon = (lon2 - lon1) * r;
    const a = Math.sin(dLat / 2) ** 2 + Math.cos(la1) * Math.cos(la2) * Math.sin(dLon / 2) ** 2;
    return EARTH_R_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }

  // Türkiye + immediate neighbours bounding box (same as index.html TR_REGION).
  const TR = { minlat: 33.0, maxlat: 43.5, minlon: 24.0, maxlon: 47.0 };
  const EONET_TYPES = {
    "Wildfires": "wildfire", "Severe Storms": "storm", "Volcanoes": "volcano",
    "Floods": "flood", "Sea and Lake Ice": "ice", "Drought": "drought",
    "Landslides": "landslide", "Temperature Extremes": "temp",
  };
  const GDACS_TYPES = { TS: "tsunami", TC: "storm", FL: "flood", VO: "volcano", WF: "wildfire", DR: "drought" };
  const ALERT_RANK = { green: 1, orange: 1.5, red: 2 };

  function toUnified(quakes, eonet, gdacs) {
    const out = [];
    (quakes || []).forEach(q => {
      if (q.mag == null || q.lat == null || q.lon == null) return;
      out.push({ id: q.id, typ: "quake", schwere: q.mag, ort: q.place || "", zeit: q.time == null ? null : q.time,
        lat: q.lat, lon: q.lon, quelle: q.source || "USGS", url: q.url || "" });
    });
    (eonet || []).forEach(h => {
      const typ = EONET_TYPES[h.cat]; if (!typ) return;
      if (h.lat == null || h.lon == null) return;
      out.push({ id: h.id, typ, schwere: null, ort: h.title || "", zeit: h.date == null ? null : h.date,
        lat: h.lat, lon: h.lon, quelle: "EONET", url: h.url || "" });
    });
    (gdacs || []).forEach(h => {
      const typ = GDACS_TYPES[h.type]; if (!typ) return;
      if (h.lat == null || h.lon == null) return;
      out.push({ id: "gdacs:" + h.type + ":" + h.lat + "," + h.lon + ":" + (h.date ?? ""), typ,
        schwere: h.alert || null, ort: h.title || h.country || "", zeit: h.date == null ? null : h.date,
        lat: h.lat, lon: h.lon, quelle: "GDACS", url: h.url || "" });
    });
    return out;
  }

  function inTR(d) { return d.lat >= TR.minlat && d.lat <= TR.maxlat && d.lon >= TR.minlon && d.lon <= TR.maxlon; }

  function filterDisasters(list, filter, origin, nowMs) {
    const typen = filter.typen || [];
    const winMs = (filter.fensterH || 48) * 3600 * 1000;
    const minMag = filter.minMag || 0;
    const minAlertRank = (filter.minAlert && filter.minAlert !== "all") ? (ALERT_RANK[filter.minAlert] || 0) : 0;
    return (list || []).filter(d => {
      if (typen.length && !typen.includes(d.typ)) return false;
      if (d.zeit != null && nowMs - d.zeit > winMs) return false;
      // earthquakes filtered by Richter magnitude; hazards by GDACS alert level.
      if (d.typ === "quake") { if (typeof d.schwere === "number" && d.schwere < minMag) return false; }
      else if (minAlertRank > 0 && (ALERT_RANK[d.schwere] || 0) < minAlertRank) return false;
      if (filter.region === "tr" && !inTR(d)) return false;
      if (filter.region === "near" && origin && haversineKm(origin.lat, origin.lon, d.lat, d.lon) > origin.radiusKm) return false;
      return true;
    });
  }

  // Mixed-type severity so quakes and alert-level hazards interleave sensibly.
  function severityScore(d) {
    if (d.typ === "quake" && typeof d.schwere === "number") return d.schwere;
    if (typeof d.schwere === "string") return 3.5 + (ALERT_RANK[d.schwere] || 0); // green 4.5 / orange 5.0 / red 5.5
    return 0; // unranked hazards (EONET) sort last
  }

  function sortDisasters(list, sort, origin) {
    const arr = (list || []).slice();
    if (sort === "naehe" && origin) {
      arr.sort((a, b) => haversineKm(origin.lat, origin.lon, a.lat, a.lon) - haversineKm(origin.lat, origin.lon, b.lat, b.lon));
    } else if (sort === "schwere") {
      arr.sort((a, b) => severityScore(b) - severityScore(a));
    } else { // "zeit"
      arr.sort((a, b) => (b.zeit ?? -1) - (a.zeit ?? -1));
    }
    return arr;
  }

  return { toUnified, filterDisasters, sortDisasters, severityScore, haversineKm };
});
