# TDA Web-App — Funktions-Spezifikation (ohne Design/Stil)

Lokale Web-Spiegelung des Android-Clients ([tda/webapp/index.html](../webapp/index.html)),
eine einzelne, eigenständige HTML-Datei. Läuft ohne Build/Server über `file://`.
Diese Spezifikation beschreibt **nur Verhalten, Logik, Daten und Protokoll** — keine
Farben, Themes, Layouts oder Typografie.

## Zweck & Umfang
- Den Frühwarn-Kern testbar machen: Alarm mit S-Wellen-Countdown, Intensität,
  Schutzstatus, Nachbeben-Sequenz, Ereignisbericht, Mehrsprachigkeit.
- Zwei Alarmquellen, unabhängig voneinander:
  1. **Lokaler Testszenario-Injektor** (kein Netz).
  2. **Server-Verbindungsmodus** (WebSocket), jeder Alarm Ed25519-verifiziert.
- Kein Persistieren, kein Upload, kein Tracking. Zustand lebt nur im Speicher der Seite.

## Datenmodell — Alarm-Payload
String-Map, Feldnamen 1:1 wie Backend `build_payload`:
`v, id, ver, state, tier, test, origin_ts, lat, lon, depth_km, mag, mag_hi, src, issued_ts`.
- `origin_ts`, `issued_ts`: Epoch in **Millisekunden**.
- `tier` ∈ {`P0`, `P1`, `P2`}; `ver` = Revisionszähler (Erst-Alarm = 1).

## Fachlogik (portiert aus `Eew`, identisch zum Backend)
- **Haversine-Distanz**, Erdradius R = 6371 km, `dist_km` = Distanz(Nutzerort, Epizentrum).
- **S-Wellen-Countdown:** `sek = max(0, dist_km / 3.5 − verstrichene_zeit)`, vS = 3.5 km/s.
- **P-Welle (nur Info):** `sek = max(0, dist_km / 6.0 − verstrichene_zeit)`, vP = 6.0 km/s.
- **Intensität am Ort (grobe Schätzung, klar so gekennzeichnet):**
  `MMI ≈ 1.5·mag − 3.0·log10(max(dist_km,1)) + 3.0`, geklemmt auf [1..12];
  Ausgabe als römische Stufe I–XII + Kurztext.
- **Schutzstatus** aus MMI: ≥ 5 → „Ducken · Schützen · Halten"; 3–4 → „Erschütterung
  möglich – in Deckung bereit"; < 3 → „schwach, wahrscheinlich sicher".

## Zustände (Status)
`READY` · `ATTENTION` · `ALARM_P0` · `CONFIRMED_P2` · `DISTURBANCE_DISCARDED`.
Ein Status-Element zeigt den aktuellen Zustand; jeder Zustand hat Symbol **und** Text
(nie Bedeutung allein über Farbe).

## Städte (Nutzerort)
Adana 37.00,35.32 · Gaziantep 37.06,37.38 · Kahramanmaraş 37.58,36.93 ·
İstanbul 41.01,28.98 · İzmir 38.42,27.14 · Ankara 39.93,32.86.
Der gewählte Ort steuert Distanz, Countdown und Intensität.

## Screens & Verhalten
1. **Startscreen**
   - Status-Anzeige, Ortswahl, Sprachwahl, Theme-/Barrierefreiheits-Umschalter (Funktion vorhanden),
     Live-Seismogramm, Testszenario-Buttons, Sequenzliste, Server-Verbindungsbereich.
2. **Alarm-Overlay** (Vollbild-fähig)
   - Tier-Kennzeichnung P0/P1/P2 über Symbol + Text.
   - Großer **Countdown**, tickt jede Sekunde: „<n> s bis S-Welle · <Ort>"; bei 0 „S-Welle da".
   - P-Wellen-Hinweis (Info) solange > 0.
   - Kennzahlen: Magnitude (Zusatz „Proxy-Schätzung" bei P0, „Pd, instrumentell" bei P2),
     Intensität (I–XII + Text), Ursprungsentfernung (km), Warnzeit (s).
   - Schutzstatus prominent.
   - **Live-Eskalation P0 → P2** für dasselbe Ereignis, ohne Neu-Öffnen.
   - Aktionen: „Ereignisbericht öffnen", „Schließen".
3. **Ereignisbericht-Overlay**
   - Zeitachse des letzten Ereignisses: Bruchbeginn (+0.0 s) → Detektion (P0) →
     Bestätigung (P2, oder „—") → S-Welle am Ort.
   - **Teilen**: `navigator.share`, sonst Zwischenablage, sonst Textausgabe.
4. **Störungs-Dialog**: „Störung erkannt – kein Alarm" mit ehrlicher Begründung.

## Testszenario-Injektor (kein Netz)
- **Testbeben:** `ATTENTION` (t0) → P0-Alarm (t0+1,5 s, Epizentrum Kahramanmaraş 37.58,36.93,
  M6.8, Tiefe 10 km, `src=p0b-phone-cluster`) → P2-Bestätigung (t0+6 s, M7.8, `mag_hi=7.9`,
  `src=p0a-instrumental`). `origin_ts` liegt 2 s vor P0, damit der Countdown realistisch läuft.
- **Nachbeben-Sequenz:** Hauptbeben M7.8 → P2 nach 4 s; danach demokomprimierte Kadenz
  (M7.5, 4.2, 5.1, 3.6, 4.8) mit Abständen 3/3/4/4/4 s. Sequenzliste füllt sich live;
  **Push-Hinweis nur ab M ≥ 5.0**.
- **Feuerwerk:** nach 0,8 s `DISTURBANCE_DISCARDED` + Störungs-Dialog (spiegelt Wellenfront-Filter).
- **Zurücksetzen:** bricht laufende Szenarien ab, Status → `READY`, Sequenz/Bericht/Alarm leer.

Ein Erst-Alarm (`ver == 1`) öffnet das Alarm-Overlay einmalig; spätere Revisionen desselben
`id` aktualisieren das offene Overlay (Eskalation).

## Server-Verbindungsmodus (WebSocket)
- Verbindungszustände: `DISCONNECTED` · `CONNECTING` · `CONNECTED` · `FAILED`.
- **Ausgehend:** `{"cmd":"simulate","kind":"quake"|"firework"}` (nur bei offener Verbindung).
- **Eingehend:** `{"type":"alert","payload":{…}}` (Server sendet außerdem ein `hello` mit
  angekündigtem `pub_key` — wird ignoriert, nie zur Prüfung verwendet).
- **Signaturprüfung (Pflicht vor Anzeige):**
  - Kanonische Bytes: alle Payload-Felder außer `sig`, Schlüssel alphabetisch sortiert,
    als `key=value` mit `\n` verbunden (kein Schluss-Newline), UTF-8 — byte-identisch zum
    Backend `canonical_bytes`.
  - Verifikation via Web-Crypto **Ed25519** gegen den **eingebetteten** Server-Public-Key
    `6kcriusWuSdd8wJ6IUXfYhasu7oN2LAIhW1HZGnaJhc=` (Base64, 32 Byte roh).
  - Ergebnis: `true` → Alarm durchreichen; `false` → verwerfen + Hinweis „Signatur ungültig";
    Crypto **nicht verfügbar** → verwerfen + ehrlicher Browser-Hinweis.
- Verifizierte P0/P1/P2-Alarme laufen durch dieselbe UI wie der lokale Injektor
  (Status, Bericht, Alarm-Overlay); P0-`issued_ts` wird je `id` gemerkt, damit ein späteres
  P2 die Detektionszeit korrekt berichtet.

## Live-Seismogramm
- Liest echte Gerätebewegung über `devicemotion` (`accelerationIncludingGravity`),
  rollende Kurve der Magnitude `|a| − 9.81`, Puffer 220 Samples.
- iOS: Erlaubnis-Button (`DeviceMotionEvent.requestPermission`). Kein Sensor vorhanden →
  ehrlicher Hinweis. Sensor wird **nur angezeigt**, nie gespeichert/hochgeladen.

## Mehrsprachigkeit
- Sprachen TR/EN/KU/AR, Umschaltung zur Laufzeit; Arabisch setzt `dir=rtl`.
- EN + TR vollständig; KU/AR tragen die lebensrettenden Kernstrings und fallen sonst
  auf EN zurück (wie im Android-Client).

## Grenzen (bewusst)
- Kein Persistieren, keine Push-Zustellung, kein Backend-Zwang: lokaler Modus arbeitet
  vollständig ohne Netz.
- Server-Modus setzt ein laufendes lokales Backend (`serve_local.py`) voraus und einen
  Browser mit Web-Crypto-Ed25519.
- Intensität/Countdown sind Anzeige-Schätzungen, ausdrücklich als solche gekennzeichnet.
