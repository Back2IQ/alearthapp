import { test } from "node:test";
import assert from "node:assert/strict";
import { matchEvents } from "../src/matcher.js";
import { haversineKm } from "../src/eew.js";

const IZMIR = { lat: 38.42, lon: 27.14 };
const ADANA = { lat: 37.0, lon: 35.32 };

function device(token, subscriptions) {
  return { token, lang: "tr", platform: "android", subscriptions };
}

test("hit within radius and above notifyMag", () => {
  const events = [
    { id: "e1", lat: IZMIR.lat, lon: IZMIR.lon, depthKm: 10, mag: 5.0, originTs: 1, place: "x", src: "usgs" },
  ];
  const devices = [
    device("tok1", [
      { lat: IZMIR.lat + 0.05, lon: IZMIR.lon, label: "home", notifyMag: 4.0, alarmMag: 6.0, radiusKm: 100 },
    ]),
  ];
  const pushes = matchEvents(events, devices);
  assert.equal(pushes.length, 1);
  assert.equal(pushes[0].token, "tok1");
  assert.equal(pushes[0].tier, "notify");
  assert.equal(pushes[0].matchedLabel, "home");
  assert.equal(pushes[0].data.id, "e1");
});

test("no hit outside radius", () => {
  const events = [
    { id: "e1", lat: ADANA.lat, lon: ADANA.lon, depthKm: 10, mag: 6.0, originTs: 1, place: "x", src: "usgs" },
  ];
  const devices = [
    device("tok1", [
      { lat: IZMIR.lat, lon: IZMIR.lon, label: "home", notifyMag: 3.0, alarmMag: 6.0, radiusKm: 50 },
    ]),
  ];
  // sanity: Izmir<->Adana is ~730km, way beyond 50km radius
  assert.ok(haversineKm(IZMIR.lat, IZMIR.lon, ADANA.lat, ADANA.lon) > 50);
  const pushes = matchEvents(events, devices);
  assert.equal(pushes.length, 0);
});

test("no hit below notifyMag", () => {
  const events = [
    { id: "e1", lat: IZMIR.lat, lon: IZMIR.lon, depthKm: 10, mag: 2.5, originTs: 1, place: "x", src: "usgs" },
  ];
  const devices = [
    device("tok1", [
      { lat: IZMIR.lat, lon: IZMIR.lon, label: "home", notifyMag: 4.0, alarmMag: 6.0, radiusKm: 100 },
    ]),
  ];
  const pushes = matchEvents(events, devices);
  assert.equal(pushes.length, 0);
});

test("tier is alarm once mag >= alarmMag", () => {
  const events = [
    { id: "e1", lat: IZMIR.lat, lon: IZMIR.lon, depthKm: 10, mag: 6.5, originTs: 1, place: "x", src: "usgs" },
  ];
  const devices = [
    device("tok1", [
      { lat: IZMIR.lat, lon: IZMIR.lon, label: "home", notifyMag: 4.0, alarmMag: 6.0, radiusKm: 100 },
    ]),
  ];
  const pushes = matchEvents(events, devices);
  assert.equal(pushes.length, 1);
  assert.equal(pushes[0].tier, "alarm");
});

test("exactly one push per device even with multiple matching subscriptions; strongest wins", () => {
  const events = [
    { id: "e1", lat: IZMIR.lat, lon: IZMIR.lon, depthKm: 10, mag: 6.5, originTs: 1, place: "x", src: "usgs" },
  ];
  const devices = [
    device("tok1", [
      // far-ish sub that only "notify"s (weaker alarmMag threshold not met, mag < alarmMag=7)
      { lat: IZMIR.lat + 0.5, lon: IZMIR.lon, label: "far-notify", notifyMag: 4.0, alarmMag: 7.0, radiusKm: 200 },
      // closer sub whose alarmMag is met -> should win as "alarm"
      { lat: IZMIR.lat + 0.01, lon: IZMIR.lon, label: "close-alarm", notifyMag: 4.0, alarmMag: 6.0, radiusKm: 200 },
    ]),
  ];
  const pushes = matchEvents(events, devices);
  assert.equal(pushes.length, 1);
  assert.equal(pushes[0].tier, "alarm");
  assert.equal(pushes[0].matchedLabel, "close-alarm");
});

test("exactly one push per device across multiple matching events; nearest/strongest wins", () => {
  const events = [
    { id: "e1", lat: IZMIR.lat, lon: IZMIR.lon, depthKm: 10, mag: 4.5, originTs: 1, place: "x", src: "usgs" },
    { id: "e2", lat: IZMIR.lat + 0.01, lon: IZMIR.lon, depthKm: 10, mag: 4.5, originTs: 2, place: "y", src: "usgs" },
  ];
  const devices = [
    device("tok1", [
      { lat: IZMIR.lat, lon: IZMIR.lon, label: "home", notifyMag: 4.0, alarmMag: 8.0, radiusKm: 200 },
    ]),
  ];
  const pushes = matchEvents(events, devices);
  assert.equal(pushes.length, 1);
  // both events same tier/mag; nearest (e1, distance 0) should win
  assert.equal(pushes[0].data.id, "e1");
});
