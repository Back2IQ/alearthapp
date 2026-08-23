import { test } from "node:test";
import assert from "node:assert/strict";
import { createRequire } from "node:module";
const require = createRequire(import.meta.url);
const P = require("./prep.js");

test("buildAffiliateUrl encodes the query and appends the tag", () => {
  const u = P.buildAffiliateUrl("Erste-Hilfe-Set & Wasser", "TDA-PLACEHOLDER-21");
  assert.ok(u.startsWith("https://www.amazon.com.tr/s?k="));
  assert.ok(u.includes("tag=TDA-PLACEHOLDER-21"));
  assert.ok(u.includes("Erste-Hilfe-Set")); // encoded, not raw spaces/&
  assert.ok(!u.includes(" ")); // no raw spaces
  assert.ok(!/&(?!(k=|tag=))/.test(u.replace("?", "")) || u.split("&").length === 2); // only k & tag params
});

test("localSearchUrl builds a neutral web search", () => {
  const u = P.localSearchUrl("Trillerpfeife");
  assert.ok(u.startsWith("https://www.google.com/search?q="));
  assert.ok(u.includes("Trillerpfeife"));
});

test("profileToSettings maps the three profiles and defaults to ausgewogen", () => {
  assert.deepEqual(P.profileToSettings("vorsichtig"), { radiusKm: 500, minMag: 2.5 });
  assert.deepEqual(P.profileToSettings("ausgewogen"), { radiusKm: 300, minMag: 3.5 });
  assert.deepEqual(P.profileToSettings("starkbeben"), { radiusKm: 150, minMag: 5.0 });
  assert.deepEqual(P.profileToSettings("nonsense"), { radiusKm: 300, minMag: 3.5 });
});

test("PREP_CATALOG is well-formed", () => {
  assert.ok(Array.isArray(P.PREP_CATALOG) && P.PREP_CATALOG.length >= 4);
  for (const sec of P.PREP_CATALOG) {
    assert.ok(typeof sec.typ === "string");
    assert.ok(Array.isArray(sec.items) && sec.items.length >= 1);
    for (const it of sec.items) { assert.ok(it.key && it.query); }
  }
});
