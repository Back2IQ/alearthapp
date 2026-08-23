# Native-Parität: Web-App-Funktionsumfang in die Android-App bringen

**Ausgangslage (23.08.2026):** Die native App (`tda/android`) war ein 3-Tab-Gerüst
(Start=Seismograph, Bereitschaft, Einstellungen). Der volle Funktionsumfang existiert
nur in der Web-App (`tda/webapp/index.html`, 7 Tabs + Onboarding + Karte + Recent
Disasters). Ziel: alles **nativ** nachbauen, in Etappen, jede Etappe = installierbare,
auf Gerät getestete APK.

**Native Life-Safety-Schicht (TP-3) ist fertig & getestet und bleibt unangetastet:**
Kritischer Vollbild-Alarm, Vordergrund-Dienst (Nachbeben-Wache), „Bist du sicher?"→Beacon,
Pfeife/Bildschirm-Blitz/Fackel-Blitz, Notification-Kanäle. (TP-4 BLE-SOS = offen.)

**Navigation:** Navigation Drawer (Burger) als Hauptnavigation — 7+ Bereiche sind zu viele
für eine Bottom-Tab-Leiste. Top-AppBar mit Burger-Icon.

**Drawer-Ziele (Endausbau):** Start · Karte · Beben (Recent Disasters) · Netzwerk
(Seismograph) · Vorsorge · Sicherheit/Notsignal · FAQ · Einstellungen.

---

## Etappe 1 — Grundgerüst richtigstellen  (diese Runde)
- Prefs.onboarded / alertRadiusKm / minMagnitude (erledigt).
- **Onboarding** (Activity, 6 Schritte, Inhalte aus Web-App): Willkommen · Standort ·
  Schutzstufe · Alerts/Benachrichtigungen · Offline-Rettung · Probealarm. Beim ersten
  Start vor MainActivity. Später über FAQ neu startbar. (Retro-Vorschau „hätte X von Y
  Beben gewarnt" kommt mit Etappe 2, wenn Feeds da sind — vorerst generischer Text.)
- **Echte Startseite** (`StartFragment`): Status-Hero (READY/…); Standort-Badge;
  Platzhalter-Panel „nächstes Beben in der Nähe" (wird in Etappe 3 verlinkt).
- **Seismograph** wandert von „Start" in eigenen **Netzwerk**-Tab (`NetworkFragment`,
  übernimmt bisheriges fragment_home: Seismogramm + Sequenz + Alarm-zurücksetzen).
- **Sicherheits-/Notsignal-Screen** (`SafetySettingsFragment`): macht die TP-3-Schalter
  sichtbar & testbar (Pfeife/Bildschirm-Blitz/Fackel/Beacon an-aus, MMI-Schwelle,
  Grace-Minuten, Totmann-Sekunden, DND-Opt-in; Test-Buttons).
- **FAQ** (`FaqFragment`): 6 statische Q/A aus der Web-App.
- **Drawer-Navigation** + Top-AppBar; Alarm-Plumbing bleibt in MainActivity (tab-unabhängig).
- Neue Strings als getrennte `values/strings_*.xml` (+ `values-de/…`), damit keine
  Merge-Kollision; tr/ku/ar/ru folgen als eigener i18n-Durchgang.

## Etappe 2 — Recent Disasters (nativ)
- `DisastersRepository` (OkHttp): USGS `all_day.geojson`, EMSC FDSN, NASA EONET; Dedup
  EMSC↔USGS; Portierung der Logik aus `webapp/lib/disasters.js` (toUnified/filter/sort).
- `DisastersFragment`: Typ/Region/Zeit/Sortierung/Min-Mag-Filter, Liste, ehrliche
  Fehleranzeige bei ausgefallener Quelle.
- Startseite „nächstes Beben in der Nähe" an die echten Daten hängen.

## Etappe 3 — Karte (nativ)
- osmdroid (OSM-Tiles), Marker aus denselben Feeds, Magnituden-Farbcodes + Legende,
  Ansichten Nearby/Felt/Global, Klick Startseite/Beben → Karte zentrieren.
- Kleine Karte im Alert-Screen.

## Etappe 4 (optional) — Rest & Politur
- Restliche Sprachen (RU + Übersetzung neuer Strings nach tr/ku/ar), Distanzeinheit km/mi,
  Push-Kategorien, Detection-Network-Beitritt, Colorblind-Feinschliff.
