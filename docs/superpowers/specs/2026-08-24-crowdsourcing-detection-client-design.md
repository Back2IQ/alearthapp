# Crowdsourcing Detection Client + HTTP Trigger-Ingest — Design

**Datum:** 2026-08-24
**Status:** Design freigegeben (Nutzer), Spec zur Review
**Branch:** `feat/crowdsourcing-client`

## Ziel (ein Satz)

Ein Handy, das lädt und ruhig liegt, erkennt Boden-Erschütterungen selbst und meldet
sie anonym an den Python-Server, damit dessen bereits gebauter Crowdsourcing-Detektor
(p0b) aus vielen gleichzeitigen Meldungen ein Beben ausrufen und Sekunden-Vorwarnungen
verschicken kann.

## Einordnung: was schon existiert, was diese Arbeit ergänzt

Der Python-Server (`tda/server/`) enthält **fertig und getestet**: den p0b-Detektor
(Finazzi-Methode: Dichte-Tracker, Poisson-Hintergrund, GPD-Schwelle 1 Fehlalarm/Jahr,
Wellenfront-Check, Cluster, Reputation, Pipeline — 56 Tests grün), das Trigger-Gateway
(`p0b/gateway.py`: Attestierung, Rate-Limit, Uhr-Gate), den Draht-Vertrag
(`p0b/signals.py`) und den FCM-Versand (`alert/publisher.py::FcmTransport`).

**Es fehlt genau zweierlei**, und nur das baut dieser Spec:

1. **Ein nativer Melde-Client** in der Android-App. Heute kommen Trigger ausschließlich
   aus Simulationen (WS-Befehl `trigger`, Szenario-JSON) — es gibt keinerlei echte
   Sensor-Erkennung.
2. **Ein Produktions-HTTP-Eingang** am Server. Die Ingest-*Logik* (`gateway.py`) ist da,
   aber an keine HTTP-Route verdrahtet, die ein Handy anrufen kann.

Nicht in diesem Spec (bewusst, benannt): der Detektor selbst (existiert), der
Alarm-Empfang auf dem Client (läuft über die bestehende FCM/`PushService`-Kette), das
Deployment auf Oracle (wartet auf Account).

## Bestehender Draht-Vertrag (verbindlich, aus `p0b/signals.py`)

Der Client MUSS exakt dieses Format erzeugen — der Server ist darauf getestet:

```
PhoneTrigger: device_hash, cell, trigger_ms, clock_unc_ms, received_ms, attest_ok
ActivePing:   device_hash, cell, ping_ms, received_ms

coarsen_cell(lat, lon) -> "d{floor(lat/0.1)}_{floor(lon/0.1)}"   # 0,1°-Raster (~11 km)
```

Serialisierung ist `dict[str, str]` (alle Werte Strings) — passt 1:1 auf einen
JSON-POST-Body. **`received_ms` und `attest_ok` setzt der Server**, nicht der Client.

## Grundentscheidungen (im Brainstorming bestätigt)

- **Signal:** Beschleunigungssensor (echtes Sekunden-EEW, wie Earthquake Network).
- **Messfenster:** nur wenn das Handy **lädt UND ruhig liegt** (minimaler Akku, kaum
  Fehlalarme).
- **Standort:** nur die **gerundete 0,1°-Zelle** verlässt das Gerät, nie Rohkoordinaten.
- **Mithelfen:** **Opt-in**, Standard aus, im Onboarding freundlich aktiv angeboten.
- **Fehlalarm-Aufteilung:** Der Client meldet großzügig; das **Aussortieren macht der
  Server** (glaubt nur bei vielen gleichzeitigen, räumlich gestreuten Meldungen).
- **Anonyme Kennung:** rotierende Zufalls-ID (siehe unten) — erfüllt den `device_hash`
  des Vertrags, ohne dauerhafte Verfolgbarkeit.

---

## Teil A — Client (nativ Android, `app.alearthapp`)

Neue Kotlin-Units, orientiert an bestehenden Mustern (`AlarmService` als
Foreground-Service, `PushRegistrar` für okhttp-POST an `Prefs.backendUrl`).

### A1. `QuakeSensorService` (Foreground-Service)
- `foregroundServiceType="specialUse"` wie `AlarmService`; nutzt die vorhandenen
  Permissions `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE`.
- **Lebenszyklus an das Ladekabel gekoppelt:** ein manifest-registrierter
  `PowerConnectionReceiver` auf `ACTION_POWER_CONNECTED` / `ACTION_POWER_DISCONNECTED`
  (beide sind vom Implicit-Broadcast-Verbot ausgenommen) startet/stoppt den Dienst.
  Läuft nur beim Laden → vernachlässigbarer Akku, Foreground-Notification vertretbar.
- No-op, wenn `Prefs.crowdsourcingEnabled == false`.
- **Bekanntes Risiko (im Plan zu lösen):** Start eines Foreground-Service aus einem
  Hintergrund-Broadcast ist ab Android 12 eingeschränkt. Fallback, falls es greift:
  Start beim nächsten App-Öffnen + `WorkManager`-Prüfung des Ladezustands. Der Plan
  verifiziert das reale Verhalten auf dem Testgerät.

### A2. `StillnessDetector` (reine Funktion, testbar)
- Nimmt einen Strom von Sensor-Samples (Zeitstempel + x/y/z), führt die gleitende
  Varianz der Beschleunigungs-Magnitude.
- Zustand `SETTLED`, sobald die Varianz für ~60 s unter `stillVarThreshold` bleibt;
  jeder Ausschlag über `motionVarThreshold` → zurück zu `UNSETTLED` (Handy angefasst).
- Schwellen in einem `SensorConfig`-Objekt (feinjustierbar, Startwerte im Plan).

### A3. `ShakeDetector` (reine Funktion, testbar)
- Läuft nur im Zustand `SETTLED`.
- Erkennt einen **plötzlichen, kurz anhaltenden** Anstieg der Magnitude über der
  Ruhelage (Schwerkraft-Baseline) oberhalb `shakeThreshold` → gibt einen
  Erkennungs-Zeitstempel `trigger_ms` zurück.
- Bewusst großzügig kalibriert (Rückrufe > Präzision); der Server filtert. Nach einem
  Trigger eine kurze Sperrzeit (`refractoryMs`), um Dubletten desselben Ereignisses zu
  vermeiden.

### A4. `GeoCell` — `coarsenCell(lat, lon)` (reine Funktion, testbar)
- **Byte-identisch** zu `signals.coarsen_cell`: `"d${floor(lat/0.1)}_${floor(lon/0.1)}"`.
- Parität wird per Unit-Test gegen fest verdrahtete Referenzwerte aus dem Python-Test
  gesichert (analog zum bestehenden `Eew`-Parität-Prinzip).
- Eingabe: grober Standort (`ACCESS_COARSE_LOCATION`, Last-Known genügt bei 0,1°).

### A5. `AnonDeviceId` (rotierende anonyme Kennung)
- 16 Zufallsbytes, Base64 → `device_hash`. In `Prefs` gespeichert mit Rotationsdatum.
- **Rotiert täglich** (Tageswechsel) und bei Neuinstallation. Kein Konto, keine
  IMEI/Ad-ID. Ermöglicht Kurzzeit-Dedup/Reputation, keine Verfolgung über Tage.
- Rotationslogik als reine Funktion (Eingabe: gespeicherte ID + gespeichertes Datum +
  aktuelles Datum → ID + evtl. neues Datum) → testbar.

### A6. `TriggerReporter` + `ActivePinger` (okhttp, wie `PushRegistrar`)
- **Trigger:** bei Erkennung `POST {backendUrl}/trigger` mit JSON
  `{device_hash, cell, trigger_ms, clock_unc_ms}`. Best-effort, **kein Retry** (eine
  späte Meldung nützt nichts). No-op bei leerem `backendUrl`.
- **Ping:** solange `SETTLED`+lädt, alle ~30 min `POST {backendUrl}/ping` mit
  `{device_hash, cell, ping_ms}` (füttert den Dichte-Tracker ν; `active_ttl` = 45 min).
- **Uhr-Unsicherheit v1:** Systemzeit (OS-auto-synchronisiert) + fester konservativer
  `clock_unc_ms` (Default 1000, konfigurierbar; unter dem Gateway-Limit 2000). Echte
  SNTP-Messung ist eine spätere Verfeinerung.

### A7. Einstellungen + Onboarding
- `Prefs.crowdsourcingEnabled` (Default **false**).
- Einstellungs-Schalter „Mithelfen, andere zu warnen" mit Ein-Satz-Erklärung.
- Onboarding-Einladung (bestehende Onboarding-Struktur): *„Hilf mit, andere zu warnen —
  kostet fast nichts, nur wenn dein Handy lädt."* Beim Einschalten: Standort-Permission
  (coarse) anfragen.

### A8. Manifest
- `QuakeSensorService` (specialUse) + `PowerConnectionReceiver` registrieren.
- `ACCESS_COARSE_LOCATION`.

---

## Teil B — HTTP-Trigger-Eingang (Python, `tda/server`)

### B1. `serve/ingest.py` (keine neue Abhängigkeit)
- stdlib `ThreadingHTTPServer` + `BaseHTTPRequestHandler`, **exakt dem Muster von
  `serve/http_proxy.py` folgend** (läuft im Hintergrund-Thread neben WS-Bridge/Proxy).
- **`POST /trigger`**: JSON-Body `{device_hash, cell, trigger_ms, clock_unc_ms}` (+ optional
  `attest_token`). Server stempelt `received_ms = now`, reicht das Signal in die
  bestehende `gateway`-Logik (Attestierung → `attest_ok`, `TriggerGate`, `RateLimiter`);
  akzeptierte Trigger landen via `serialize_trigger` im `trigger_stream`.
- **`POST /ping`**: JSON-Body `{device_hash, cell, ping_ms}`. Server stempelt
  `received_ms`, leichte Rate-Begrenzung, hängt via `serialize_ping` an den `ping_stream`.
- **Antworten:** `202 Accepted` bei gültigem Schema (auch wenn das Gateway das Signal
  intern verwirft — Policy nicht nach außen leaken); `400 Bad Request` bei
  fehlerhaftem/unvollständigem JSON; `405` bei falscher Methode.
- **Thread→async-Brücke:** Der stdlib-Handler läuft im Thread, die Pipeline in asyncio.
  Der Handler übergibt akzeptierte Signale über `asyncio.run_coroutine_threadsafe` an
  die async `gateway`/Stream-Appends (Loop-Referenz beim Start injiziert). Die reine
  Validierungs-/Bau-Funktion (`raw dict → PhoneTrigger/ActivePing`) ist synchron und
  direkt unit-testbar, ohne Server-Socket.

### B2. Verdrahtung in `scripts/serve_local.py`
- `start_ingest(host, port, ...)` neben `start_http_proxy` und `serve` hochfahren; Port
  über Env (`TDA_INGEST_PORT`, Default z. B. 8002). Die Ingest-Signale fließen in
  dieselben Streams, die `run_p0b_pipeline` konsumiert.

---

## Datenfluss (Ende zu Ende)

```
[Handy lädt+ruhig] --Rütteln--> ShakeDetector -> coarsenCell -> TriggerReporter
   --POST /trigger--> serve/ingest.py --gateway(verify,gate,limit)--> trigger_stream
   --> run_p0b_pipeline -> P0bDetector.evaluate -> Correlator -> on_transition
   --> publisher(FcmTransport) --FCM--> [andere Handys im Umkreis: PushService -> Alarm]

[Handy lädt+ruhig] --alle 30 min--> ActivePinger --POST /ping--> ping_stream
   --> DensityTracker (ν_t je Zelle, Bezugsgröße für die Signifikanz)
```

## Testing

**Client (JVM-Unit-Tests, `android/app/src/test/java/app/alearthapp/`, JUnit 4):**
- `StillnessDetectorTest` — synthetische Sample-Reihen: ruhig → SETTLED nach Fenster;
  Ausschlag → UNSETTLED.
- `ShakeDetectorTest` — Ruhe → kein Trigger; simulierter Ruck → genau ein Trigger;
  Sperrzeit verhindert Dubletten.
- `GeoCellTest` — Parität gegen die Python-Referenzwerte (`coarsen_cell(41.02,28.97) ==
  "d410_289"` usw.).
- `AnonDeviceIdTest` — gleiche ID am selben Tag; neue ID am Folgetag.

**Server (`tda/server/tests/test_ingest.py`, pytest):**
- Gültiger `/trigger`-Body → PhoneTrigger korrekt gebaut, `received_ms` server-gestempelt,
  landet im Stream.
- Fehlerhafter Body → 400, nichts im Stream.
- Gültiger `/ping`-Body → ActivePing im ping_stream.
- Uhr-Gate/Rate-Limit greifen (über die bestehende gateway-Logik) → verworfen, aber 202.

## Fehlerbehandlung & Grenzen

- Kein Netz / leerer `backendUrl` → Meldung wird verworfen (kein Retry-Queue).
- Sensor nicht vorhanden → Feature inaktiv, kein Absturz.
- Server-Ingest ist der wahrscheinlichste Überlastpfad → Rate-Limit im Gateway
  (bestehend); der Handler bleibt zustandslos.
- Roh-GPS erreicht den Server nie (Client rundet lokal auf die Zelle).

## Aufgeschoben (benannt, nicht in diesem Spec)

- **Play-Integrity-Attestierung:** Server-Prüfer bleibt v1 großzügig (Stub-Verifier);
  echte Anbindung später. Der Detektor darf nie allein auf einem binären Attest-Verdikt
  beruhen.
- **Socket-Zweitkanal** (paralleles Wettrennen zu FCM) und **Pushy** (OEM-Resilienz).
- **Echte SNTP-Uhrmessung** (v1: Systemzeit + fester konservativer `clock_unc_ms`).
- **„Aufmerksamkeits-Modus"** (Server hebt Abtastrate naher Geräte an).
