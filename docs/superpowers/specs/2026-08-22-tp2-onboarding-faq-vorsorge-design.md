# TP-2 · Onboarding + FAQ-Tab + Vorsorge-Tab — Design

**Datum:** 2026-08-22
**Teilprojekt:** TP-2 (Web-App zuerst; Android-Spiegel später)
**Ziel (ein Satz):** Ein überspringbarer Erst-Start-Assistent, der Einstellungen in Klartext erklärt und setzt, plus zwei neue Tabs — FAQ (mit „Onboarding wiederholen") und Vorsorge (gefahrenspezifische Ausrüstung mit gekennzeichneten Affiliate-Links).

---

## Kontext

Baut auf TP-1 auf (Web-App `tda/webapp/index.html`, self-contained, `file://`, kein Build). Vorhanden: 5-Tab-Schale (`switchTab`, `State.activeTab`), `Settings` (persistiert in `tda_prefs`), i18n `STR` (en/de/tr/ru/ar), Standort-Logik (GPS/Adresse/Preset-Städte), Demo-Alarm (`runEarthquakeDrill`), Web-Notification ist noch nicht angebunden. Prüf-Gate: `node check.mjs` (Syntax + doppelte IDs) + `node --test lib/*.test.mjs`.

## Gestaltungsgrundsatz (bindend)

**So einfach und unkompliziert wie möglich.** Jeder Schritt selbsterklärend, jede Fläche schlicht. Bewusst *weggelassen*: Häkchen-/Bereitschafts-Tracker, Bereitschafts-Score, FAQ-Deep-Links, jede Mehrfach-Auswahl oder zusätzliche Statusschicht. Im Zweifel weniger.

## Umfang

Drei Teile, gemeinsam ausgeliefert, aber als getrennte Bau-Tasks:
1. **Onboarding-Assistent** (Vollbild-Overlay, 6 Schritte, alles überspringbar).
2. **FAQ-Tab** (Akkordeon + „Onboarding erneut starten").
3. **Vorsorge-Tab** (Ausrüstung nach Gefahrentyp, Affiliate-Links mit Platzhalter-Tag).

Erweitert die Navigation von **5 auf 7 Tabs**: Start · Karte · Katastrophen · Netzwerk · **Vorsorge** · **FAQ** · Einstellungen.

---

## Architektur

### A · Navigation (7 Tabs)

Neue Tabs `vorsorge` (Icon 🎒) und `faq` (Icon ❔) werden in `TABS`, in die Tab-Leiste und als `<section class="tab-view">` aufgenommen. Reihenfolge: `["start","map","disasters","network","vorsorge","faq","settings"]`.

**Responsives Layout bei 7 Einträgen:** Auf der unteren Leiste (mobil, `<900px`) werden die **Text-Labels ausgeblendet** (`.tabbar button span:not(.ic){ display:none }`), nur Icons — so passen 7 Einträge. Auf der linken Leiste (`≥900px`) bleibt Icon + Text. Icons klein genug, Tap-Ziel ≥40px.

### B · Onboarding-Assistent

**Auslösung:** Beim Start zeigt `init()` das Overlay, wenn `Settings.onboarded !== true`. Manuell erneut über den FAQ-Tab-Knopf (`startOnboarding()`).

**Overlay:** Vollbild `#onboarding` (eigene `.overlay`-Variante), oben Fortschrittspunkte (6), unten **Zurück · Überspringen · Weiter** (letzter Schritt: **Fertig**). **Keine Pflichtfelder** — jeder Schritt ist mit „Weiter"/„Überspringen" verlassbar; sinnvolle Vorgaben greifen automatisch.

**Schritte:**
1. **Willkommen** — „TDA warnt Sekunden *vor* der Erschütterung und zeigt alle Naturgefahren." Kein Werbetext.
2. **Standort** — GPS-Knopf oder Adress-/Stadt-Eingabe (nutzt vorhandene `useGps`/Such-/City-Chip-Logik). Übersprungen → bestehender `State.loc` (zuletzt gewählt oder Preset Adana). Nie erzwungen.
3. **Schutzprofil** — drei Knöpfe, setzen `radiusKm` + `minMag`, mit Alltagsbeispiel:
   - **Vorsichtig** → `radiusKm 500`, `minMag 2.5`
   - **Ausgewogen** (Standard, vorausgewählt) → `radiusKm 300`, `minMag 3.5`
   - **Nur Starkbeben** → `radiusKm 150`, `minMag 5.0`
   Klartext: „Magnitude = Stärke des Bebens; Radius = wie weit weg dich ein Beben noch betrifft." Fein-Regler bleiben in den Einstellungen.
   **Live-Rückschau statt Theorie (Vereinfachung):** unter den Profilen eine ehrliche Zeile aus den bereits geladenen Bebendaten der letzten 24 Std. — z. B. *„Dieses Profil hätte dich bei 7 von 23 Beben in deiner Nähe gewarnt."* Zählt Beben mit `dist ≤ radiusKm` und `mag ≥ minMag`; aktualisiert beim Profilwechsel. Klar als „Rückschau, keine Vorhersage" gekennzeichnet. Wenn noch keine Daten geladen sind, wird die Zeile weggelassen (kein Platzhalter).
4. **Alarme** — Umschalter *Alarmton* (setzt `Settings.sound`) + *Benachrichtigungen*: fragt bei Aktivierung die **Web-Notification-Erlaubnis** (`Notification.requestPermission()`) an, ehrlich beschriftet „funktioniert, solange die Seite/App läuft; vollständige Push-Zustellung in der App". Übersprungen → unverändert.
5. **Offline-SOS / Bluetooth-Mesh** — **nur Erklärung**, klar gekennzeichnet: „Trägt Warnung + Notruf von Handy zu Handy, wenn das Netz tot ist — **vollständig nur in der App**, im Browser nicht möglich." Kein Schalter. (Vorbereitung TP-4.)
6. **Probe-Alarm** — Knopf „Probe-Alarm zeigen" löst einmal einen klar als Übung gekennzeichneten Demo-Alarm aus (nutzt bestehende Drill-Logik), damit man Ton/Optik wiedererkennt. Dann **Fertig** → `Settings.onboarded = true`, `savePrefs()`, Overlay zu.

**Persistenz:** `Settings.onboarded` (Bool, Standard `false`).

### C · FAQ-Tab

Statische Klartext-Antworten als Akkordeon (nutzt vorhandenes `<details>`-Muster, kein neues JS-Framework). Oben ein Knopf **„Onboarding erneut starten"** (`startOnboarding()`). Mindest-Fragen (alle i18n):
- Wie früh warnt TDA? (Sekunden, je nach Entfernung; ehrliche Grenze)
- Was bedeuten Magnitude, Tiefe, Radius?
- Warum kommt es zu einem Fehlalarm — und wie erkennt TDA ihn?
- Was passiert offline / wenn das Mobilnetz ausfällt? (Web vs. App ehrlich)
- Sind meine Standortdaten sicher? (Detektionsnetz teilt nur grobe Zelle + Zeit)
- Woher kommen die Daten? (USGS/EMSC/EONET direkt; GDACS/AFAD optional über Server)

### D · Vorsorge-Tab (Referral)

**Aufbau:** Kurzer Kopf + **Affiliate-Hinweis** (Pflicht, TR/EU): „Enthält Empfehlungs-Links; bei einem Kauf kann TDA eine kleine Provision erhalten — ohne Mehrkosten für dich." Dann Abschnitte **nach Gefahrentyp** (Alle · Beben · Flut · Sturm · Waldbrand), je 3–6 kuratierte Positionen.

**Position:** Name + kurzer Nutzen-Satz + Link „Bei Amazon suchen" + „Lokaler Händler". Kein Kauf-Zwang, keine Preise/Bewertungen vortäuschen.

**Link-Bau (robuste Suche statt toter Produkt-IDs):** reine Funktion
`buildAffiliateUrl(query, tag) -> "https://www.amazon.com.tr/s?k=<enc(query)>&tag=<tag>"`.
Platzhalter-Tag als leicht austauschbare Konstante `AFFILIATE_TAG = "TDA-PLACEHOLDER-21"`. Lokaler-Händler-Link = neutrale Websuche `https://www.google.com/search?q=<enc(query)>`.

**Beispiel-Katalog (Datenstruktur, kein toter Link):**
- Alle: Wasser (Vorrat), Erste-Hilfe-Set, Notgepäck, Dokumentenmappe (wasserdicht), Kurbelradio, Powerbank
- Beben: Trillerpfeife, Helm, Staubmaske FFP2, Rettungsdecke, Löschdecke
- Flut: wasserdichte Taschen, Gummistiefel, Schwimmweste
- Sturm: Taschenlampe, Fensterschutzfolie, Vorräte
- Waldbrand: FFP2-/Rauchmasken, Löschdecke, Schutzbrille

**Leitplanken (aus dem Gold-Eintrag):** ausschließlich in diesem Vorsorge-Tab, **nie** in Alarm-/SOS-/Warnflächen; jeder Link als Empfehlung gekennzeichnet; kein Standort-/Nutzerdatenabfluss (statische Such-Links, keine Tracker).

### E · Testbare reine Logik

Kleines Modul `lib/prep.js` (UMD, wie `disasters.js`) mit:
- `buildAffiliateUrl(query, tag)` — korrekte Enkodierung, Tag angehängt, kein Doppel-Encoding.
- `profileToSettings(profile)` — `"vorsichtig"|"ausgewogen"|"starkbeben"` → `{ radiusKm, minMag }` (die Werte oben); unbekannt → Ausgewogen.
Reale Node-Unit-Tests in `lib/prep.test.mjs`.

---

## i18n

Alle neuen Strings in **en/de/tr/ru** vollständig, **ar** Kern mit EN-Fallback. Neue Schlüssel u. a.: `tab_vorsorge`, `tab_faq`; Onboarding (`ob_*`: Titel/Fließtext je Schritt, Profil-Labels, Buttons); FAQ (`faq_q*`/`faq_a*`, `faq_restart`); Vorsorge (`prep_*`: Kopf, Affiliate-Hinweis, Typ-Abschnitte, Produktnamen/Nutzen, „Bei Amazon suchen"/„Lokaler Händler").

## Akzeptanzkriterien

- **Onboarding erscheint nur beim ersten Start** (`onboarded` false) und nach „erneut starten"; nie erzwungen; jeder Schritt überspringbar; „Fertig" setzt `onboarded=true` (überlebt Neuladen).
- **Keine Pflichtfelder:** man kann von Schritt 1 direkt bis Fertig durchklicken; Defaults (Profil Ausgewogen, Ton an, bestehender Standort) greifen.
- **Profile setzen die richtigen Werte** (500/2.5 · 300/3.5 · 150/5.0), sichtbar in den Einstellungen.
- **7 Tabs** vorhanden und umschaltbar; untere Leiste zeigt auf schmalem Screen nur Icons, ohne umzubrechen; linke Leiste zeigt Icon + Text.
- **FAQ-Tab** listet die Fragen als Akkordeon; „Onboarding erneut starten" öffnet das Overlay bei Schritt 1.
- **Vorsorge-Tab** zeigt Affiliate-Hinweis + Abschnitte je Gefahrentyp; Links öffnen eine Amazon-Suche mit dem Tag bzw. eine neutrale Websuche; nichts erscheint in Alarm-/SOS-Flächen.
- **Web-Notification:** Aktivieren in Schritt 4 fragt die Browser-Erlaubnis an; Ablehnung bricht nichts.
- **i18n:** kein `t()`-Schlüssel fehlt in `STR.en`; de/tr/ru vollständig; Platzhalter (%1) deckungsgleich.
- **Gate:** `node check.mjs` grün (keine doppelten IDs); `node --test lib/*.test.mjs` grün (inkl. neuer `prep`-Tests).

## Grenzen / nicht in TP-2

- **Echte Push-Zustellung** (FCM/Pushy) — braucht die native App + Firebase; hier nur der Web-Notification-Fallback.
- **Echte Affiliate-Einnahmen** — brauchen ein Amazon-Associates-Konto des Nutzers; Tag ist Platzhalter.
- **Android-Spiegel** der neuen Tabs/Onboarding — separates Folge-Teilprojekt.
- Kein Anfassen des Sicherheits-Kerns (`Eew`, `Signing`, `ServerLink`, Alarm/Countdown) außer dem Aufruf der bestehenden Drill-Logik für den Probe-Alarm.
