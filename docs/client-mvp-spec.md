# TDA Android-Client MVP — Bau-Spezifikation

Nativer Android-Client (Kotlin) für Alert2IQ. Ziel: eine **echte, installierbare Debug-APK**, die den Frühwarn-Kern für den Nutzer erlebbar macht — Alarm mit S-Wellen-Countdown, Schutzstatus, echtem Handy-Seismogramm, Nachbeben-Sequenz, Mehrsprachigkeit und Themes. Testbar **ohne Backend** über einen lokalen Testszenario-Injektor.

## Bewiesene Toolchain (exakt so verwenden — STABILER Ort, nicht Scratchpad)
- JDK: `C:/Users/HP/.tda-toolchain/jdk-17.0.20.1+1` → `JAVA_HOME` (auch als `org.gradle.java.home` in `android/gradle.properties` gesetzt)
- Android-SDK: `C:/Users/HP/AppData/Local/Android/Sdk` → `ANDROID_HOME` (build-tools 36.0.0, platform android-36 vorhanden; **keine** weiteren SDK-Komponenten nachinstallieren — cmdline-tools fehlen)
- Gradle: `C:/Users/HP/.tda-toolchain/gradle-8.11.1/bin/gradle` ODER der bereits generierte Wrapper `tda/android/gradlew`.
- **Das Projektgerüst `tda/android/` existiert bereits** (settings.gradle, build.gradle, app/build.gradle, gradle.properties inkl. `org.gradle.java.home`, local.properties, Gradle-Wrapper, .gitignore) und baut. NUR den App-Code (Manifest, Kotlin, res) ergänzen.
- AGP **8.9.1**, Kotlin **2.0.21**, compileSdk/targetSdk **36**, minSdk **26**
- `gradle.properties`: `org.gradle.jvmargs=-Xmx2560m`, `android.useAndroidX=true`, `android.suppressUnsupportedCompileSdk=36`
- `local.properties`: `sdk.dir=C:/Users/HP/AppData/Local/Android/Sdk`
- Kotlin-DSL-Falle: `kotlinOptions{ jvmTarget = '17' }` (Zuweisung, nicht Methodenaufruf).

## Projekt
- Verzeichnis: `tda/android/` (Git-Wurzel `tda/`). Package `app.tda`.
- Abhängigkeiten (alle aus `google()`/`mavenCentral()`): `androidx.core:core-ktx`, `androidx.appcompat:appcompat`, `com.google.android.material:material:1.12.0`, `androidx.constraintlayout:constraintlayout`, `org.jetbrains.kotlinx:kotlinx-coroutines-android`. Kein Compose (Views + eine Custom-View fürs Seismogramm), um den Build robust zu halten.
- **Definition of Done (hartes Gate):** `gradle --no-daemon assembleDebug` erzeugt `app/build/outputs/apk/debug/app-debug.apk` ohne Fehler. Jede DoD-Behauptung mit echter Build-Ausgabe belegen.

## Fachlogik (aus dem verifizierten Backend gespiegelt — `object Eew`)
- Alarm-Payload-Felder (String-Map, wie Backend `build_payload`): `v,id,ver,state,tier,test,origin_ts,lat,lon,depth_km,mag,mag_hi,src,issued_ts`.
- Tier: `P0` (nur Telefon-Cluster), `P1` (eine Katalogquelle), `P2` (bestätigt). Farbe/Form je Tier.
- **S-Wellen-Countdown:** `sekunden = max(0, dist_km/3.5 − verstrichene_zeit)`, `dist_km` = Haversine(Nutzerort, Epizentrum). vS = 3,5 km/s. P-Welle (Info) vP = 6,0 km/s.
- **Intensität am Ort (grob, ehrlich als Schätzung):** einfache Abnahme `MMI ≈ 1.5·mag − 3.0·log10(max(dist_km,1)) + 3.0`, geklemmt [1..12]; Label I–XII + Kurztext. Klar als grobe Schätzung kennzeichnen.
- **Schutzstatus** aus MMI: ≥ V → „Ducken · Schützen · Halten"; III–IV → „Erschütterung möglich – in Deckung bereit"; < III → „schwach, wahrscheinlich sicher".
- Haversine identisch zum Backend (R=6371).

## Screens & Verhalten
1. **Startscreen (`MainActivity`)**
   - Kopf: App-Name + Status-Chip („System bereit" / „Aufmerksamkeit" / „ALARM P0" / „BESTÄTIGT P2" / „Störung verworfen").
   - Ortswahl: Dropdown mit Städten (Adana 37.00,35.32 · Gaziantep 37.06,37.38 · Kahramanmaraş 37.58,36.93 · İstanbul 41.01,28.98 · İzmir 38.42,27.14 · Ankara 39.93,32.86). Gewählter Ort steuert Countdown/Intensität.
   - Sprache-Dropdown (TR/EN/KU/AR) — wechselt Locale zur Laufzeit (AppCompat `setApplicationLocales`).
   - Theme-Umschalter (System/Hell/Dunkel) + Schalter „farbfehlsichtigkeitssicher".
   - **Live-Seismogramm** (Custom-View): echter Beschleunigungssensor (`SensorManager`, `TYPE_ACCELEROMETER`), rollende Kurve der Magnitude |a|−g. Zeigt reale Erschütterung, wenn man das Handy bewegt.
   - Test-Buttons: „Testbeben (P0→P2)", „Nachbeben-Sequenz", „Störung: Feuerwerk", „Zurücksetzen". (Lokaler Injektor, kein Netz.)
2. **Alarmscreen (`AlertActivity`, Full-Screen-fähig)**
   - Tier-Streifen P0/P2 (Farbe **und** Symbol **und** Text — nie Farbe allein, Spec §10).
   - Großer **Countdown** (mono, tabellarische Ziffern) „… s bis S-Welle · <Ort>", tickt jede Sekunde runter; bei 0 „S-Welle da".
   - Kennzahlen: Magnitude (Proxy bei P0, „Pd" bei P2), Intensität (I–XII + Text), Ursprungsentfernung, Warnzeit.
   - **Schutzstatus** groß, mit Symbol.
   - Alarmton + Vibration beim Auslösen (`Ringtone`/`VibrationEffect`); respektiert Stumm-Einstellung nicht bei P2 (lebensrettend), P0 gedämpfter.
   - „P0 → P2"-Eskalation sichtbar (bei Nachbeben-/Bestätigungs-Szenario).
3. **Nachbeben-Sequenz (`SequenceView`/Bereich):** eine lebende Liste statt vieler Einzelalarme — Hauptbeben + nachfolgende Beben mit Zeit/Magnitude/Intensität, live aktualisiert (Spec „Sequenz-Modus").
4. **Ereignisbericht (`ReportActivity`):** Zeitachse des letzten Ereignisses (Bruchbeginn → Detektion → Alarm → S-Welle am Ort), exportierbar als Text/Teilen-Intent.

## Aufmerksamkeits-Modus
Vor dem scharfen Alarm ein kurzer „Aufmerksamkeit"-Zustand (Status-Chip amber, dezenter Ton, Screen wärmt vor) — im Testszenario 1–2 s vor dem P0-Alarm. Bestätigt sich das Signal → Alarm; sonst still zurück.

## Testszenario-Injektor (`TestScenarios`, kein Netz)
Coroutine-getrieben, treibt die echte UI:
- **Testbeben:** Aufmerksamkeit (t0) → P0-Alarm (t0+1,5 s, Countdown aus gewähltem Ort, z. B. Adana↔Kahramanmaraş ≈ 57 s) → P2-Bestätigung (t0+6 s, Magnitude instrumentell verfeinert).
- **Nachbeben-Sequenz:** Hauptbeben M7,8 → nach kurzer Pause M7,5 → mehrere kleinere; Sequenzliste füllt sich, Push nur oberhalb Schwelle.
- **Feuerwerk:** „Störung erkannt – kein Alarm" mit ehrlicher Begründung (stadtweit-simultan, kein konsistenter Wellenfront) — spiegelt den Backend-Filter.

## i18n & Barrierefreiheit
- `res/values/strings.xml` (EN default), `values-tr`, `values-ku`, `values-ar` (RTL: `android:supportsRtl="true"`). Lebensrettende Kernstrings (Countdown-Label, Schutzstatus, Tier, Alarmtitel) in allen vier Sprachen; sonstige Strings mind. TR/EN. Kommentar: muttersprachliche Prüfung vor Produktiv nötig.
- Zustände nie über Farbe allein: immer Symbol + Text + Form. Farbfehlsichtigkeitssicheres Alternativ-Palette (blau/orange statt grün/rot) über Theme-Attribut.
- Content-Descriptions für Statuselemente; große Schrift für Countdown/Schutzstatus.

## Grenzen (bewusst, im Bericht als verschobene Lücken nennen)
- **Kein** live-FCM/Backend: Alarme kommen aus dem lokalen Injektor. Payload-Signaturprüfung (Ed25519/BouncyCastle) ist im MVP nicht verdrahtet (Public-Key-Verteilung offen) — als Kommentar markieren.
- Voller Permission-Gauntlet (§5), Pushy, BLE-Mesh-SOS, echter Sensor-Sammeldienst/Upload sind Stufe 2 — nicht hier.
- Der Sensor wird nur zur **Anzeige** (Seismogramm) gelesen, nicht hochgeladen.
