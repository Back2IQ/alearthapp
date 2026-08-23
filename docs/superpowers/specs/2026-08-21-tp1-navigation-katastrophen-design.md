# TP-1 · Navigations-Schale + globaler Katastrophen-Tab — Design

**Datum:** 2026-08-21
**Teilprojekt:** TP-1 (von 4 — siehe Landkarte unten)
**Ziel (ein Satz):** Das endlose Scrollen durch eine feste Tab-Navigation ersetzen und einen eigenen Katastrophen-Tab bauen, der *alle* Naturgefahren global mit Filtern und wählbarem Zeitfenster (24 h / 48 h) zeigt.

---

## Kontext & Zerlegung

Die App besteht aus einem Server (Python: WebSocket-Bridge + HTTP-Proxy + Ed25519-Signatur) und zwei Clients: **Web-App** (`tda/webapp/index.html`, Prüf-Werkzeug + spätere mögliche Webseite) und **Android-APK** (nativer Kotlin-Client, voller Umfang). Gemeinsamer Vertrag: signierte Alarm-Payloads, Detektions-/Netzprotokoll, Katastrophen-Feeds.

Das Gesamtvorhaben ist in vier eigenständige Teilprojekte zerlegt; jedes ist für sich lauffähig und testbar:

| # | Teilprojekt | Web-App | Android-APK |
|---|---|---|---|
| **TP-1** | **Navigations-Schale + globaler Katastrophen-Tab** *(dieser Spec)* | ✅ voll | ✅ voll |
| TP-2 | Onboarding-Assistent (Einstellungen erklären, Profile, Probe-Alarm) | ✅ (Mesh nur erklärt) | ✅ voll |
| TP-3 | Nachbeben-Kern („Bist du sicher?"-Modus, kritischer Alarm, Pfeife/Strobo) | ✅ browsertauglich | ✅ voll |
| TP-4 | Offline-Rettung / Block A (Beacon, Verschütteten-Karte, Check-in, Offline-Karten, Medizinkarte, Ortung) | ⚠️ Vorschau + Netz-Check-in | ✅ voll (BLE-Mesh, Hintergrund) |

**Plattform-Grundsatz (Ehrlichkeit als Feature):** Was der Browser nicht kann (BLE-Mesh, Beacon, Hintergrund-Sensorik), wird in der Web-App **ausgeblendet oder als klar beschriftete Vorschau** gezeigt — nie als echt verkauft. TP-1 enthält davon nichts Kritisches; es ist vollständig browsertauglich.

---

## Umfang von TP-1

**Enthalten:**
1. Feste Tab-Navigation mit fünf Tabs, ersetzt das bisherige Lang-Scrollen.
2. Umzug bestehender Inhalte (Karte, Detektionsnetz, Seismogramm, Einstellungen) in ihre Tabs.
3. Neuer **Katastrophen-Tab**: global, alle Gefahrentypen, Filter, Zeitfenster 24/48 h.
4. **Entwickler-/Testmodus-Schalter** in den Einstellungen, der das „Live-Server"-Testpanel ein-/ausblendet.

**Nicht enthalten (spätere Teilprojekte):** SOS/Beacon/Rettung, „Bist du sicher?"-Nachbeben-Modus, kritischer Alarm, Onboarding-Assistent. Der Start-Tab bleibt in TP-1 bewusst schlicht (nur Status).

---

## Architektur

### A · Tab-Schale

Feste **Tab-Leiste unten** (mobil, Daumen-Reichweite); auf breitem Viewport (`min-width` Umschlag) sitzt sie **links** als vertikale Leiste. Genau ein View ist sichtbar; die übrigen bleiben im DOM verborgen (`hidden`/CSS), damit Wechsel sofort und ohne Neuaufbau erfolgen.

| Tab | Icon | Inhalt |
|---|---|---|
| **Start** | ⌂ | Ein-Blick-Status: „Alles ruhig in deiner Nähe" (grün) **oder** aktive Warnung groß; darunter das nächstgelegene jüngste Ereignis in einer Zeile. Sonst nichts. |
| **Karte** | ◎ | Bestehende Leaflet-Karte (Beben + Gefahren + Netz-Layer) inkl. Layer-Schalter. |
| **Katastrophen** | ⚠ | Neuer Kern (siehe B). |
| **Netzwerk** | ⚡ | Detektionsnetz + Seismogramm; **Live-Server-Testpanel nur bei aktivem Entwicklermodus** (siehe D). |
| **Einstellungen** | ⚙ | Alle Einstellungen, Sprache/Theme, Entwicklermodus-Schalter. |

**Zustand:** `State.activeTab` (String), persistiert in `tda_prefs`. Standard beim ersten Start: `start`. Tab-Wechsel setzt `activeTab`, blendet Views um, hebt das aktive Tab-Icon hervor (`aria-current="page"`).

### B · Katastrophen-Tab

**Vereinheitlichtes Modell** aus allen vorhandenen Quellen — Beben (USGS `all_day` + EMSC + AFAD via Proxy) **und** Gefahren (EONET + GDACS via Proxy):

```
Disaster = {
  id,            // stabile ID (Quelle+Quell-ID), für Dedup/Keys
  typ,           // "quake" | "tsunami" | "flood" | "storm" | "volcano" | "wildfire"
  schwere,       // Beben: Zahl (Magnitude); Gefahr: "green" | "orange" | "red"
  ort,           // Klartext-Ort
  zeit,          // epoch ms
  lat, lon,
  quelle,        // "USGS" | "EMSC" | "AFAD" | "EONET" | "GDACS"
  url            // Detail-Link (optional)
}
```

Die bestehenden Fetch-Funktionen (`fetchQuakes()`, `fetchHazards()`, `fetchServerHazards()`) werden auf dieses Modell abgebildet und in `unifiedDisasters[]` zusammengeführt (bestehende Dedup-Logik für Beben bleibt).

**Filter-Zeile** (Chips, klebt oben beim Scrollen der Liste):
- **Typ** (Mehrfachwahl): Alle · Beben · Tsunami · Flut · Sturm · Vulkan · Waldbrand
- **Region:** Global (Standard) · Türkei + Nachbarn (bestehende `inRegion`-Bounds) · In meiner Nähe (Radius aus `Settings.radiusKm`)
- **Zeitraum:** **24 h / 48 h** (Umschalter; Datenmodell erlaubt spätere 7-Tage-Erweiterung ohne Umbau)
- **Mindeststärke:** Regler — Beben nach Magnitude, Gefahren nach Alarmstufe (grün < orange < rot)
- **Sortierung:** Zeit (Standard) · Nähe · Schwere

**Listeneintrag:** Typ-Icon · Titel · Stärke-Badge („M 4.8" / „Orange") · Ort · Relativzeit („vor 12 min") · Entfernung (falls Standort bekannt) · Quellen-Badge. Tipp auf den Eintrag → Detailansicht/Popup; Aktion „Auf Karte zeigen" wechselt in den Karte-Tab und zentriert dort auf das Ereignis.

**Filter-Zustand** persistiert in `tda_prefs` (`Settings.disasterFilter = { typen:[…], region, fenster, minSchwere, sort }`), damit die Wahl einen Neustart überlebt.

### C · Datenfluss & Fehlerfälle

- **Fluss:** Quellen laden → auf `Disaster` mappen → in `unifiedDisasters[]` mischen → Zeitfenster (`zeit >= now - fenster`) + aktive Filter anwenden → sortieren → rendern. Auto-Refresh im bestehenden Intervall + manuelles Nachladen.
- **Quellen-Unabhängigkeit (Ehrlichkeit):** jede Quelle wird einzeln gefangen; fällt eine aus, zeigen die übrigen weiter, plus dezente Zeile „Quelle GDACS nicht erreichbar". Kein stiller Verlust.
- **Offline/Cache:** letzte erfolgreiche Liste aus `localStorage` mit „Stand vor X min".
- **Leer-Zustand:** „Keine Ereignisse in den letzten 24 h für diesen Filter" mit Hinweis, Filter zu lockern.

### D · Entwickler-/Testmodus-Schalter

- In **Einstellungen** ganz unten: Schalter **„Entwickler-/Testmodus"**, Standard **aus**, persistiert (`Settings.devMode`).
- **Aus** → das „Live-Server"-Testpanel (WebSocket-Feld, Verbinden/Trennen, „Beben simulieren", „Fehlalarm simulieren") ist im Netzwerk-Tab **nicht** vorhanden.
- **An** → das Panel erscheint im Netzwerk-Tab, klar beschriftet „Testwerkzeug — löst nur Übungs-/Demo-Alarme aus".
- Zweck: Prüf-Werkzeug bleibt jederzeit griffbereit, ein späterer Webseiten-Besucher sieht es nie.

---

## Android-APK

Der Kotlin-Client spiegelt dieselbe Tab-Struktur (Bottom-Navigation, fünf Tabs) und denselben Katastrophen-Tab mit identischer Filter-/Zeitfenster-Semantik. Datenquellen und Modell sind gleich; das Live-Server-Testpanel entfällt in der APK (dort gibt es andere Debug-Wege). Detailtiefe des Android-Teils folgt im Umsetzungsplan.

---

## Akzeptanzkriterien

- **Navigation:** Fünf Tabs sichtbar; Antippen wechselt den View ohne Scrollen; aktiver Tab hervorgehoben; `State.activeTab` überlebt Neuladen.
- **Kein Lang-Scrollen mehr:** bestehende Inhalte sind auf die Tabs verteilt; keine Seite ist mehr eine lange Scroll-Kolonne.
- **Katastrophen-Tab global:** zeigt standardmäßig weltweite Ereignisse aller Typen.
- **Filter wirken sichtbar:** Typ-/Region-/Stärke-Filter reduzieren die Liste nachvollziehbar; Umschalten **24 ↔ 48 h** ändert die Trefferzahl.
- **Quellen-Transparenz:** jede Zeile trägt ein Quellen-Badge; fällt eine Quelle aus, erscheint der Hinweis, die anderen bleiben.
- **Entwicklermodus:** Live-Server-Panel ist ohne den Schalter unsichtbar und mit Schalter sichtbar + korrekt beschriftet.
- **Syntax/Integrität:** `node --check` grün; keine doppelten Element-IDs; alle neuen i18n-Schlüssel in EN/TR/RU vorhanden (AR-Kern), mit EN-Fallback.

## Prüfung (Web)

`node --check` → SYNTAX_OK; App im Browser öffnen; Tabs durchklicken; im Katastrophen-Tab Filter setzen und 24↔48 h umschalten (Trefferzahl ändert sich); eine Quelle simuliert ausfallen lassen (Hinweis erscheint, Rest bleibt); Entwicklermodus an/aus (Panel erscheint/verschwindet).

## Grenzen

Nur die in „Umfang von TP-1" genannten Dateien/Bereiche werden angefasst. Keine Änderung am Signatur-/Alarm-Kern, an den Detektions-Protokollen des Servers oder an den Fetch-Adaptern über das Mapping hinaus. Keine SOS-/Rettungs-/Onboarding-Funktion (TP-2 bis TP-4).
