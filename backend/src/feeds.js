// Feed fetching + normalization. Structured as an array of async source
// functions so more feeds can be added later without touching callers.

const USGS_URL =
  "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_hour.geojson";

/**
 * Normalize a single USGS GeoJSON feature into our internal event shape.
 * Returns null for malformed features (missing geometry/coordinates/id) instead
 * of throwing — USGS occasionally emits features with geometry:null, and one bad
 * feature must never discard the whole poll batch (would cause missed alarms).
 */
export function normalizeUsgsFeature(feature) {
  if (!feature || !feature.id) return null;
  const coords = feature.geometry && feature.geometry.coordinates;
  if (!Array.isArray(coords) || coords.length < 2) return null;
  const [lon, lat, depthKm] = coords;
  if (typeof lat !== "number" || typeof lon !== "number") return null;
  const props = feature.properties || {};
  return {
    id: feature.id,
    lat,
    lon,
    depthKm: typeof depthKm === "number" ? depthKm : 10,
    mag: props.mag,
    originTs: props.time,
    place: props.place,
    src: "usgs",
  };
}

/**
 * Parse a full USGS GeoJSON FeatureCollection into normalized events.
 * Each feature is normalized defensively; malformed features are skipped, never
 * fatal to the batch.
 */
export function parseUsgsGeoJson(geojson) {
  if (!geojson || !Array.isArray(geojson.features)) return [];
  const out = [];
  for (const f of geojson.features) {
    let ev = null;
    try { ev = normalizeUsgsFeature(f); } catch { ev = null; }
    if (ev) out.push(ev);
  }
  return out;
}

/** Fetch + normalize the USGS feed. Never throws — returns [] on failure. */
export async function fetchUsgs() {
  try {
    const res = await fetch(USGS_URL);
    if (!res.ok) return [];
    const geojson = await res.json();
    return parseUsgsGeoJson(geojson);
  } catch {
    return [];
  }
}

// Add further sources here as additional async () => Event[] functions.
export const SOURCES = [fetchUsgs];

/** Poll all sources, tolerating individual failures, and return the merged list. */
export async function pollAllSources() {
  const results = await Promise.allSettled(SOURCES.map((fn) => fn()));
  const events = [];
  for (const r of results) {
    if (r.status === "fulfilled" && Array.isArray(r.value)) {
      events.push(...r.value);
    }
  }
  return events;
}

/**
 * In-memory dedup set for event ids, capped at maxSize entries (FIFO
 * eviction) so it can't grow unbounded across polls/sources.
 */
export function createDedup(maxSize = 5000) {
  const seen = new Set();
  return {
    /** Returns true and records the id if it's new; false if already seen. */
    isNew(id) {
      if (seen.has(id)) return false;
      seen.add(id);
      if (seen.size > maxSize) {
        const oldest = seen.values().next().value;
        seen.delete(oldest);
      }
      return true;
    },
    size() {
      return seen.size;
    },
  };
}

/** Filter events down to those not yet seen by the given dedup tracker. */
export function dedupEvents(events, dedup) {
  return events.filter((e) => dedup.isNew(e.id));
}
