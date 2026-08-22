# TP-3 · Nachbeben-/Notsignal-Kern — Design

**Datum:** 2026-08-22
**Teilprojekt:** TP-3 (nativer Android-Client `tda/android`)
**Ziel (ein Satz):** Nach einem starken Beben führt der Client eine ehrliche Überlebens-Kette aus — kritischer Alarm, der Stumm/DND durchbrechen kann, eine „Bist du sicher?"-Rückfrage mit Totmann-Automatik, ein lokales Notsignal (Pfeife + Bildschirm- + Taschenlampen-Strobo) für den Verschütteten-Fall und ein „Ich bin in Sicherheit"-Teilen.

---

## Kontext

Baut auf dem bestehenden Client auf (`tda/android`, Kotlin, Gradle 8.11 / JDK 17). Vorhanden:
- `EventBus` — zentraler Kanal (`alarm`/`status`/`sequence`/`pushNotice`/`disturbance`), gespeist von `TestScenarios` **und** `ServerLink` (Ed25519-verifiziert).
- `MainActivity` — beobachtet `EventBus`, startet `AlertActivity` bei `alarm.ver==1`; Test-Knöpfe (`runEarthquakeDrill`, `runAftershockSequence`, `runFireworksDisturbance`, `reset`); Server-Verbindung.
- `AlertActivity` — Vollbild-Alarm, P0→P2-Eskalation live, Alarmton (best-effort `Ringtone`/`USAGE_ALARM`) + Vibration. Enthält den `TODO(Stufe 2): true DND-bypass needs the full permission gauntlet (spec §5)`.
- `Eew` — reine Fachlogik (Distanz, S-Wellen-ETA, `mmi`, `protectionLevel`, `SequenceEntry`).
- `Prefs` — SharedPreferences (`tda_prefs`): Sprache, Theme, Farbenblind, Stadt.
- **Nachbeben-Sequenzliste existiert bereits** (`Eew.SequenceEntry`, `EventBus.sequence`, `runAftershockSequence`, Omori-Schwelle §3.6) — TP-3 baut darauf auf, ersetzt sie nicht.
- Manifest hat nur `VIBRATE`/`INTERNET`/`ACCESS_NETWORK_STATE`; **kein** Notification-/Foreground-Service-/Full-Screen-/Kamera-Recht, **kein** Service.

## Gestaltungsgrundsatz (bindend)

**Rettet Leben, nervt nicht.** Jede Automatik hat einen Ausschalter und einen Ein-Tipp-Abbruch; Voreinstellungen sind konservativ (lieber selten scharf als Fehlalarm-Sturm); jede Zustell-/Rechte-Lücke wird sichtbar statt still. TEST ist unverwechselbar von Ernst (Cry-Wolf-Schutz).

## Umfang

**Drin (TP-3):**
1. **Kritischer Alarm trotz Stumm/DND** — Full-Screen-Intent-Kette + Alarm-Audiokanal im Vordergrund-Dienst; DND **best-effort + Opt-in**.
2. **„Bist du sicher?"-Modus** — Wächter/Beacon-Gabelung nach starkem Beben, mit 5-Min-Schonfrist und Totmann-Automatik; Vordergrund-Dienst als Träger.
3. **Lokales Notsignal** — Pfeife (synthetisiert) + Bildschirm-Strobo + Taschenlampen-Strobo, parametrisiert und einzeln abschaltbar.
4. **„Ich bin in Sicherheit"-Teilen** — Android-Teilen-Sheet, vorformuliert, kein Tracking.

**Nicht in TP-3 (in TP-3b, eigene Spec):** die zwei nativen Onboardings („System benutzen" / „Einstellungen bedienen", beide wegklickbar, Einstellungs-Onboarding beim 5. Start erneut) **und** eine ausgebaute Einstellungsseite. TP-3 persistiert alle neuen Parameter in `Prefs` mit sinnvollen Defaults und exponiert nur die **minimal nötigen** Auslöser (Rechte-Anfrage bei Bedarf, Test-Knöpfe); die schöne Erklär-/Bedien-Oberfläche liefert TP-3b.

**Nicht in TP-3 (Stufe 2 / TP-4):** BLE-Mesh-Funkruf (Nachbar verschüttet, Gerät-zu-Gerät, §12.2); echte Hintergrund-Zustellung per FCM. TP-3 hält den Empfangskanal (`ServerLink`) über den Vordergrund-Dienst am Leben, ersetzt aber FCM nicht.

---

## Parameter (Defaults, alle persistiert in `Prefs`, alle im Ernstfall/Settings änderbar)

| Parameter | Default | Bereich | Bedeutung |
|---|---|---|---|
| `beaconEnabled` | `true` | an/aus | Hauptschalter der gesamten Notsignal-Automatik |
| `beaconMmiThreshold` | `7` | 5–9 | lokale Intensität (MMI am Ort), ab der die Kette scharf ist |
| `graceMinutes` | `5` | 0–30 | Schonfrist: solange bleibt die App ruhig (nur leise Bereitschafts-Notification), damit man erst ungestört handeln/telefonieren kann |
| `deadmanCountdownSec` | `60` | 15–300 | sichtbarer Countdown auf der „Bist du sicher?"-Anzeige; unbeantwortet → Notsignal |
| `signalWhistle` | `true` | an/aus | Pfeif-/Sirenenton |
| `signalScreenStrobe` | `true` | an/aus | Vollbild weiß/schwarz-Blinken |
| `signalTorchStrobe` | `true` | an/aus | Taschenlampen-Strobo, sparsam gepulst (Akku/Hitze) |
| `dndBypassOptIn` | `false` | an/aus | ob der Nutzer der App „Nicht stören"-Zugriff gewährt hat |

**Schwelle = lokale Intensität, nicht Magnitude:** Für das Verschüttungs-Risiko zählt, wie stark es *am Nutzerort* rüttelt (`Eew.mmi(mag, distKm)`), nicht die Herd-Magnitude. Die UI erklärt das als „wie stark spürbar".

## Ablauf (der Kern-Zustandsfluss)

Fall: bestätigtes Beben (P2) mit `Eew.mmi(mag, distKm) ≥ beaconMmiThreshold` und `beaconEnabled`.

1. **Alarm** (Teil 1) läuft wie bisher, jetzt DND-fähig.
2. **Schonfrist** (`graceMinutes`): App bleibt ruhig; es läuft nur eine **leise Bereitschafts-Notification** mit zwei Aktionen: „Ich bin in Sicherheit" (→ Schritt 5) und „Notsignal jetzt" (→ Schritt 4). Kein Vollbild, kein Ton.
3. **Anzeige**: nach Ablauf der Schonfrist Vollbild **„Bist du sicher?"** mit sichtbarem `deadmanCountdownSec`-Countdown und zwei großen Knöpfen: **„Mir geht's gut"** (→ Schritt 5, Wächter) / **„Ich brauche Hilfe"** (→ Schritt 4, Beacon).
4. **Beacon-Modus**: Notsignal startet (Teil 3), Vordergrund-Dienst schaltet auf Strom sparen (Screen darf schlafen), Warnungen bleiben an. Jederzeit mit einem Tipp stoppbar. Läuft der Countdown aus Schritt 3 unbeantwortet ab → automatisch hierher (Totmann).
5. **Wächter-Modus / in Sicherheit**: keine Notsignale; Vordergrund-Dienst wacht weiter über Nachbeben (hält `ServerLink` aktiv); bietet **„Ich bin in Sicherheit" teilen** (Teil 4) an. Warnungen bleiben an.

**In beiden Modi gilt:** neue P0/P2-Warnungen (auch starke Nachbeben, Durchbruchsregel §3.6) werden nie unterdrückt.

---

## Architektur

### Teil 1 · Kritischer Alarm trotz Stumm/DND

- **`NotificationChannels`** (neu) — legt beim Start die Kanäle an: `alarm_critical` (`IMPORTANCE_HIGH`, `USAGE_ALARM`-Sound, `setBypassDnd(true)` **nur** wenn `dndBypassOptIn` und Policy-Zugriff gewährt), `beacon_ready` (leise, für die Bereitschafts-Notification), `service` (Vordergrund-Dienst-Notification). TEST-Alarme nutzen einen sichtbar anderen Kanal/Ton (Cry-Wolf).
- **Full-Screen-Intent-Kette** — echter Alarm wird als hochprioritäre Notification mit `USE_FULL_SCREEN_INTENT` auf `AlertActivity` gepostet, statt nur bei offener `MainActivity` `startActivity` zu rufen → durchbricht Sperrbildschirm auch im Hintergrund.
- **Alarmton im Vordergrund-Dienst** — dauerhafter, DND-fähiger `USAGE_ALARM`-Ton statt kurzlebigem `Ringtone` in der Activity.
- **DND best-effort + Opt-in** — Standard ohne Sonderrecht (hoher Kanal + Alarm-Usage durchbricht Vibration/viele Stumm-Profile). `DndAccess`-Helfer prüft `NotificationManager.isNotificationPolicyAccessGranted()`; Opt-in leitet nach `ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`. **Ehrliche Beschriftung:** „ohne diese Freigabe kann volles ‚Nicht stören' den Alarm schlucken".
- **Rechte:** `POST_NOTIFICATIONS` (API 33+), `USE_FULL_SCREEN_INTENT` (API 34+ ggf. Nutzer-Freigabe via `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`), `ACCESS_NOTIFICATION_POLICY` (nur bei Opt-in).

### Teil 2 · „Bist du sicher?"-Modus + Vordergrund-Dienst

- **`AlarmService`** (neu, Foreground Service) — FGS-Typ `specialUse` mit ehrlicher `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`-Begründung (Erdbeben-Nachbeben-Wache). Trägt: Alarmton (Teil 1), die Schonfrist-/Countdown-Timer, den Wächter-Watch (hält `ServerLink`), das Beacon-Signal (Teil 3). Persistente Dienst-Notification; bittet einmalig um Akku-Ausnahme (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, honett erklärt).
- **`SafetyState`** (neu, **reine** testbare Logik) — Zustandsautomat:
  - Zustände: `IDLE → ARMED (Schonfrist läuft) → ASKING (Countdown läuft) → WATCH | BEACON`.
  - Eingänge: `onConfirmedQuake(mmi)`, `onGraceElapsed()`, `onCountdownElapsed()`, `onUserSafe()`, `onUserHelp()`, `onDisabled()`.
  - Übergänge kapseln die ganze Politik (Schwellenvergleich, Totmann); die Activity/der Service rufen nur Eingänge und rendern den Zustand. Kein Android-Import.
- **`BeaconActivity`** (neu) — Vollbild „Bist du sicher?" + Beacon-Anzeige (Strobo + „Stopp"-Knopf + „Ich bin in Sicherheit"). Wird per Full-Screen-Intent aus `AlarmService` gestartet.

### Teil 3 · Lokales Notsignal (Pfeife/Strobo)

- **`SignalGenerator`** (neu) — lauter Pfeif-/Sirenenton per `AudioTrack`, Puffer **im Code erzeugt** (Sinus-Sweep). Keine Audiodatei → Nullkosten, keine APK-Größe. `USAGE_ALARM`. Der Puffer-erzeugende Teil (Frequenz-Sweep → PCM-Samples) ist reine, testbare Funktion; die `AudioTrack`-Wiedergabe ist der dünne Android-Rand.
- **`Strobe`** (neu) — Bildschirm-Strobo (Vollbild-View, weiß/schwarz, max. Helligkeit über `WindowManager.LayoutParams.screenBrightness`) + Taschenlampen-Strobo (`CameraManager.setTorchMode`, kein Kamera-Recht nötig).
- **`StrobePattern`** (neu, **reine** testbare Logik) — liefert die An/Aus-Sequenz je Kanal (Bildschirm dichter, Taschenlampe sparsam gepulst zum Akku-/Hitzeschutz), damit die Timings getestet werden können, ohne Hardware.
- **Akku-schonend & abschaltbar:** jeder Kanal (`signalWhistle`/`signalScreenStrobe`/`signalTorchStrobe`) einzeln; Signal pulst statt Dauerlast; „Stopp" beendet sofort.

### Teil 4 · „Ich bin in Sicherheit"-Teilen

- Beim Melden „in Sicherheit" (Schritt 5) ein `ACTION_SEND`-Teilen-Sheet mit vorformuliertem Text (z. B. „Mir geht es gut. — via TDA") + optional grobe Zeit/Ort; **kein** eigener Server, **kein** Tracking, Nutzer wählt Ziel-App selbst. Rein optional, wegklickbar.

### Testbare reine Logik (JVM-Unit-Tests, erstmals im Client)

Führt den Test-Sourceset `app/src/test/java/app/tda/` + JUnit (`testImplementation`) ein.
- **`SafetyStateTest`** — Schwellen-Gating, Schonfrist→Anzeige→Totmann, Wächter/Beacon-Gabelung, `beaconEnabled=false` unterdrückt alles, Warnungen nie unterdrückt.
- **`CriticalAlarmPolicyTest`** — `CriticalAlarmPolicy` (rein): aus {Tier, `dndBypassOptIn`+Policy-Zugriff, `sound`-Pref, `test`-Flag} → AudioUsage/Kanal/Bypass; TEST ≠ Ernst.
- **`StrobePatternTest`** — An/Aus-Sequenzen je Kanal, Taschenlampe sparsamer als Bildschirm.
- **`SignalGeneratorTest`** — Sinus-Sweep-Puffer: Länge, Amplitude im gültigen PCM-Bereich, Frequenz steigt/fällt wie erwartet.

---

## Berührte Dateien

**Neu:** `AlarmService.kt`, `NotificationChannels.kt`, `DndAccess.kt`, `SafetyState.kt`, `CriticalAlarmPolicy.kt`, `SignalGenerator.kt`, `Strobe.kt`, `StrobePattern.kt`, `BeaconActivity.kt` (+ Layout `activity_beacon.xml`), Tests unter `app/src/test/java/app/tda/`.
**Geändert:** `AndroidManifest.xml` (Rechte + `<service>` + FSI-fähige Activities), `AlertActivity.kt` (FSI-Start-Pfad, Übergabe an `AlarmService`/`SafetyState` nach Erschütterung), `MainActivity.kt` (minimale Rechte-Anfrage bei Bedarf + Test-Knopf „Notsignal-Kette (TEST)"), `Prefs.kt` (die acht Parameter oben), `TestScenarios.kt` (optional: TEST-Szenario, das die ganze Kette mit verkürzten Zeiten durchspielt), `build.gradle` (`testImplementation junit`), `strings.xml` **+ values-tr/ku/ar**.
**Nicht anfassen:** `Signing.kt`, `ServerLink.kt`-Verifikationskern, `Eew.kt`-Fachformeln (nur ggf. lesende Nutzung).

## i18n

Alle neuen Strings in **TR/EN/KU/AR** (der Client führt `values/`, `values-tr/`, `values-ku/`, `values-ar/`). Neue Schlüssel u. a.: Bereitschafts-Notification, „Bist du sicher?"/„Mir geht's gut"/„Ich brauche Hilfe"/„Stopp", Countdown-Text, DND-Opt-in-Erklärung, Akku-Ausnahme-Erklärung, „Ich bin in Sicherheit"-Teilentext, TEST-Kennzeichnung.

## Akzeptanzkriterien

- **Schwelle greift:** unterhalb `beaconMmiThreshold` oder bei `beaconEnabled=false` startet **keine** Kette; oberhalb schon (`SafetyStateTest` beweist die Logik).
- **Schonfrist:** nach qualifizierendem Beben bleibt die App `graceMinutes` lang ruhig (nur leise Notification); erst danach Vollbild-Anzeige.
- **Totmann:** unbeantworteter `deadmanCountdownSec`-Countdown → Notsignal startet automatisch; „Mir geht's gut" bricht ab und schaltet auf Wächter.
- **Notsignal:** aktive Kanäle (`signalWhistle`/`signalScreenStrobe`/`signalTorchStrobe`) laufen gemäß `StrobePattern`; „Stopp" beendet sofort; jeder Kanal einzeln abschaltbar.
- **Kritischer Alarm:** echter Alarm erscheint als Full-Screen-Intent auch bei gesperrtem Bildschirm; mit `dndBypassOptIn` + gewährtem Policy-Zugriff durchbricht der Kanal volles DND; ohne Opt-in ehrliche Anzeige der Grenze.
- **Teilen:** „Ich bin in Sicherheit" öffnet das Android-Teilen-Sheet mit vorformuliertem Text; ohne eigenen Server/Tracking; wegklickbar.
- **Cry-Wolf:** TEST-Kette klingt/sieht unverwechselbar anders als Ernst (`CriticalAlarmPolicyTest`).
- **Nullkosten:** kein bezahlter Dienst, keine gebundelte Audiodatei (Ton synthetisiert), keine neue Netz-Abhängigkeit.
- **Gate:** `./gradlew testDebugUnitTest` grün (neue reine-Logik-Tests) **und** `./gradlew assembleDebug` grün (kompiliert) — ausführbar, sobald JDK 17 steht.

## Grenzen / nicht in TP-3

- **Zwei native Onboardings + ausgebaute Einstellungsseite** → **TP-3b** (eigene Spec). TP-3 liefert nur Persistenz + minimale Auslöser.
- **BLE-Mesh-Funkruf / echte Hintergrund-FCM-Zustellung** → TP-4 / Stufe 2.
- **Kein Anfassen** des Signatur-/Verifikations-Kerns (`Signing`, `ServerLink`) und der `Eew`-Formeln außer lesender Nutzung.
- **Verifizierung real** erst mit installiertem JDK 17 (aktuell nur Java-8-JRE auf der Maschine).
