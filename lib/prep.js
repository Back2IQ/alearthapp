// Pure, DOM-free preparedness helpers. UMD: window.Prep (browser classic script,
// NO ES modules — blocked from file://) and module.exports (Node).
(function (root, factory) {
  const api = factory();
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  else root.Prep = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  function buildAffiliateUrl(query, tag) {
    return "https://www.amazon.com.tr/s?k=" + encodeURIComponent(query) + "&tag=" + encodeURIComponent(tag);
  }
  function localSearchUrl(query) {
    return "https://www.google.com/search?q=" + encodeURIComponent(query);
  }

  const PROFILES = {
    vorsichtig: { radiusKm: 500, minMag: 2.5 },
    ausgewogen: { radiusKm: 300, minMag: 3.5 },
    starkbeben: { radiusKm: 150, minMag: 5.0 },
  };
  function profileToSettings(profile) {
    return PROFILES[profile] ? { ...PROFILES[profile] } : { ...PROFILES.ausgewogen };
  }

  // key = i18n stem (prep_item_<key> for the name, prep_why_<key> for the one-liner);
  // query = the Amazon/web search term. Kept small and honest (curated, not upsell).
  const PREP_CATALOG = [
    { typ: "all", items: [
      { key: "water", query: "Trinkwasser Notvorrat" },
      { key: "firstaid", query: "Erste-Hilfe-Set" },
      { key: "gobag", query: "Notfallrucksack Fluchtrucksack" },
      { key: "docs", query: "wasserdichte Dokumententasche" },
      { key: "radio", query: "Kurbelradio Notfallradio" },
      { key: "powerbank", query: "Powerbank" },
    ] },
    { typ: "quake", items: [
      { key: "whistle", query: "Trillerpfeife Notsignal" },
      { key: "helmet", query: "Schutzhelm" },
      { key: "mask", query: "FFP2 Staubmaske" },
      { key: "blanket", query: "Rettungsdecke" },
    ] },
    { typ: "flood", items: [
      { key: "drybag", query: "wasserdichte Tasche Dry Bag" },
      { key: "boots", query: "Gummistiefel" },
      { key: "lifevest", query: "Schwimmweste" },
    ] },
    { typ: "storm", items: [
      { key: "flashlight", query: "Taschenlampe LED" },
      { key: "windowfilm", query: "Fensterschutzfolie" },
    ] },
    { typ: "wildfire", items: [
      { key: "smokemask", query: "FFP3 Rauchmaske" },
      { key: "fireblanket", query: "Löschdecke" },
      { key: "goggles", query: "Schutzbrille" },
    ] },
  ];

  return { buildAffiliateUrl, localSearchUrl, profileToSettings, PREP_CATALOG };
});
