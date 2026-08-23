import { test } from "node:test";
import assert from "node:assert/strict";
import { parseUsgsGeoJson, normalizeUsgsFeature, createDedup, dedupEvents } from "../src/feeds.js";

// Embedded USGS-shaped fixture — no network call in this test.
const fixture = {
  type: "FeatureCollection",
  features: [
    {
      id: "us7000abcd",
      properties: { mag: 4.5, place: "12km SE of Izmir, Turkey", time: 1700000000000 },
      geometry: { type: "Point", coordinates: [27.14, 38.42, 10.2] },
    },
    {
      id: "us7000abce",
      properties: { mag: 5.8, place: "20km NW of Adana, Turkey", time: 1700000060000 },
      geometry: { type: "Point", coordinates: [35.32, 37.0, 5.0] },
    },
    {
      id: "us7000abcf",
      properties: { mag: 2.1, place: "5km N of Van, Turkey", time: 1700000120000 },
      geometry: { type: "Point", coordinates: [43.4, 38.5, 15.7] },
    },
  ],
};

test("normalizeUsgsFeature maps a single feature correctly", () => {
  const ev = normalizeUsgsFeature(fixture.features[0]);
  assert.equal(ev.id, "us7000abcd");
  assert.equal(ev.lat, 38.42);
  assert.equal(ev.lon, 27.14);
  assert.equal(ev.depthKm, 10.2);
  assert.equal(ev.mag, 4.5);
  assert.equal(ev.originTs, 1700000000000);
  assert.equal(ev.place, "12km SE of Izmir, Turkey");
  assert.equal(ev.src, "usgs");
});

test("parseUsgsGeoJson normalizes all features in order", () => {
  const events = parseUsgsGeoJson(fixture);
  assert.equal(events.length, 3);
  assert.deepEqual(
    events.map((e) => e.id),
    ["us7000abcd", "us7000abce", "us7000abcf"]
  );
  assert.equal(events[1].mag, 5.8);
  assert.equal(events[1].lat, 37.0);
  assert.equal(events[1].lon, 35.32);
});

test("parseUsgsGeoJson handles missing/malformed input gracefully", () => {
  assert.deepEqual(parseUsgsGeoJson(null), []);
  assert.deepEqual(parseUsgsGeoJson({}), []);
});

test("dedup: same id across sources/polls is only new once, cap evicts oldest", () => {
  const dedup = createDedup(2);
  assert.equal(dedup.isNew("a"), true);
  assert.equal(dedup.isNew("a"), false); // duplicate
  assert.equal(dedup.isNew("b"), true);
  assert.equal(dedup.isNew("c"), true); // pushes size over cap -> evict oldest ("a")
  assert.ok(dedup.size() <= 2);
  assert.equal(dedup.isNew("a"), true); // "a" was evicted, so it's "new" again
});

test("dedupEvents filters out already-seen ids", () => {
  const dedup = createDedup(5000);
  const events = parseUsgsGeoJson(fixture);
  const firstPass = dedupEvents(events, dedup);
  assert.equal(firstPass.length, 3);
  const secondPass = dedupEvents(events, dedup);
  assert.equal(secondPass.length, 0);
});
