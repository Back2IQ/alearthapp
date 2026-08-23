# Konkurrenzanalyse: Earthquake Network Pro (Android APK)

**Analysierte Datei:** `Earthquake Network Pro v25.7.6 (Paid).apk` (10,3 MB)
**Methode:** Statische Analyse (read-only) — APK als ZIP entpackt, `aapt2 dump badging/permissions/xmltree`,
String-Extraktion aus `classes.dex`/`classes2.dex`/`classes3.dex`/`resources.arsc`. Kein Reverse-Engineering
des vollständigen Codes (kein jadx/apktool-Decompile), keine Netzwerk-Calls an das Backend. Alle Aussagen sind
Belege aus der APK selbst, sofern nicht ausdrücklich als "Vermutung" markiert.

---

## 1. Identität

| Feld | Wert |
|---|---|
| Package name | `com.finazzi.distquakenoads` (Namensbestandteil "noads" deutet auf eine parallele Werbe-Variante `com.finazzi.distquake` hin — nur diese "noads"/Paid-Variante wurde analysiert) |
| App-Label | "Earthquake Network Pro" |
| versionName | 25.7.6 |
| versionCode | 6049579 |
| minSdkVersion | 23 (Android 6.0) |
| targetSdkVersion / compileSdk | 35 (Android 15) |
| Hauptactivity | `com.finazzi.distquakenoads.MainActivity` |
| Größe | 10,28 MB (2 DEX-Dateien + `classes3.dex`, Multidex aktiv: `androidx.multidex.MultiDexApplication` als Application-Klasse) |
| Native Libs | nur `libandroidx.graphics.path.so` und `libdatastore_shared_counter.so` (arm64-v8a/armeabi-v7a) — **keine eigene native (C/C++/NDK) Bibliothek für Signalverarbeitung**. Die Erkennungslogik läuft vollständig in Kotlin/Java. |

## 2. Berechtigungen (Auszug, relevant)

- `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, **`ACCESS_BACKGROUND_LOCATION`**
- `RECEIVE_BOOT_COMPLETED` (eigener `BootListener`-Receiver)
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `FOREGROUND_SERVICE_DATA_SYNC`
- `USE_FULL_SCREEN_INTENT`, `SYSTEM_ALERT_WINDOW`, `WAKE_LOCK`, `POST_NOTIFICATIONS`
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- `com.android.vending.BILLING` (Google Play Billing)
- `ACCESS_ADSERVICES_*` (Attribution/AdId/CustomAudience/Topics) — **Vermutung:** vermutlich nur durch die zusammen mit Firebase Analytics gebündelten Google-Play-Services-Bibliotheken ins Manifest gemergt, nicht notwendigerweise aktiv genutzt (siehe Abschnitt 6, keine AdMob-Ad-Klassen im Code gefunden).
- Keine Kamera-, Mikrofon-, SMS- oder Kontakte-Berechtigung.

Es gibt **keine** Berechtigung/API zum expliziten DND-Zugriff (`NotificationManager.isNotificationPolicyAccessGranted` / `ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`) — im gesamten String-Dump kein Treffer für "InterruptionFilter" oder "NotificationPolicy".

## 3. Detektions-/Crowdsourcing-Mechanik

Im Manifest ist bei `androidx.work.impl.foreground.SystemForegroundService` (Property `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE`) folgender Klartext-Hinweis hinterlegt (Pflichtangabe für Android 14+ Special-Use-Foreground-Services):

> "The service implements seismic monitoring using the device accelerometer and it sends data to the app backend for real-time earthquake detection. The service only starts if the device is idle and connected to a source of power. The service stops when the device exits the idle state or if it is unplugged from the source of power."

**Zentraler technischer Befund:** Die Sensor-Überwachung läuft nicht als klassischer eigener Dauer-Service, sondern als **WorkManager-Job** (`androidx.work.impl...SystemForegroundService`), dessen Constraints im Code als `requiresDeviceIdle=true` und `requiresCharging=true` gefunden wurden (Strings `setRequiresDeviceIdle`, `setRequiresCharging`, `requiresDeviceIdle=`, `requiresCharging=`). Das heißt:

- Die Beschleunigungssensor-Überwachung (`SensorManager`/`SensorEventListener`, `TYPE_ACCELEROMETER` referenziert) läuft **nur, wenn das Handy still liegt UND am Ladegerät hängt** — nicht während aktiver Nutzung, nicht in der Tasche, nicht am Akku unterwegs.
- Praktische Konsequenz: Das "Live-Sensornetz" besteht effektiv nur aus Telefonen, die gerade auf dem Nachttisch/Schreibtisch laden — ein signifikant kleinerer Ausschnitt der installierten Basis als "alle Telefone, die die App gerade offen/aktiv im Hintergrund haben".
- Kein eigener PlayerService für die Sensorik — `PlayerService` (foregroundServiceType Media Playback) ist ein separater Dienst, der zur Alarmton-Wiedergabe existiert.

Zusätzlich zur reinen Crowd-Erkennung integriert die App **offizielle Kataloge**: Strings wie `Earthquake detected by INGV`, `Earthquake detected by USGS`, `action_ingv`, `action_usgs`, `com.finazzi.distquakenoads.url_usgs` belegen, dass amtliche Netzwerke (v.a. INGV Italien, USGS) parallel als Alarmquelle eingebunden sind — es ist also ein Hybrid aus Crowd-Detektion + Katalog-Feed, nicht rein Crowd-only.

Ein Endpunkt `distquake_download_shakemap.php` deutet auf eine serverseitig generierte Shake-Map-Funktion hin.

## 4. Backend/Endpunkte

Eigene Domains (alle über HTTPS referenziert):

- `srv.earthquakenetwork.it` — vermutlich Haupt-API/Realtime-Server
- `cdn.earthquakenetwork.it` — statische Assets
- `htil.earthquakenetwork.it` — Kachel-Server für Karten (`tile_z%d_c%d_r%d.png`, vermutlich "heat tile")
- `%s.earthquakenetwork.it` — Subdomain-Template (Format-String, Zweck unklar — **Vermutung**: Load-Balancing/Region-Shards)

Backend-API-Stil: **klassische PHP-Endpunkte** (`distquake_upload_*.php`, `distquake_download_*.php`, u.a. `distquake_upload_gcm_latlon.php`, `distquake_download_friendship.php`, `distquake_upload_testalarm.php`, `distquake_update_subscription_and_pro_status.php`). Das ist ein technisch datiertes Muster (kein erkennbares REST/GraphQL-Schema) — **Vermutung**: deutet auf einen historisch gewachsenen, nicht modernisierten Backend-Stack hin; sagt für sich allein nichts über Zuverlässigkeit/Skalierbarkeit aus.

Realtime-Kanal: `OkHttp`-**WebSocket**-Implementierung ist im Code vorhanden (`Lokhttp3/internal/ws/RealWebSocket`, `WebSocketReader/Writer`, Sec-WebSocket-Handshake-Strings) — die Push-artige Live-Übertragung (Sensordaten hoch, Alarme runter) läuft vermutlich über WebSocket zum eigenen Server, nicht über MQTT (keine MQTT-Strings gefunden) und nicht primär über Firebase Realtime Database.

Eine zusätzliche Firebase-Realtime-DB-Instanz `hybrid-bastion-406.firebaseio.com` ist referenziert — **Vermutung:** Nebenkanal, evtl. für Chat/Präsenz-Features, nicht die primäre Alarm-Pipeline.

`usesCleartextTraffic="true"` ist global im Manifest gesetzt, ohne dass eine `res/xml`-`networkSecurityConfig`-Datei existiert, die das einschränkt — d.h. Klartext-HTTP ist auf OS-Ebene für beliebige Hosts erlaubt (keine erzwungene TLS-Pinning-Konfiguration sichtbar). Alle im Code gefundenen eigenen API-URLs sind zwar `https://`, aber die fehlende Einschränkung ist eine (kleine) Angriffsfläche. **Vermutung/Einordnung:** kein Beweis für tatsächliche Klartext-Nutzung, aber eine unsaubere Sicherheitskonfiguration.

## 5. Push-Kanäle

- **Nur Firebase Cloud Messaging (FCM)** wird verwendet: eigene `com.finazzi.distquakenoads.MyFirebaseMessagingService` (mit 9 inneren Klassen `$a`–`$i`, also recht umfangreiche Push-Verarbeitungslogik) plus Standard-FCM-Receiver.
- Kein Pushy, kein OneSignal, kein UrbanAirship gefunden — **kein Redundanz-/Fallback-Kanal** gegen OEM-Push-Drosselung (Xiaomi/Huawei/Oppo MIUI-Battery-Killer-Problematik). Das ist ein reales Risiko: Wird FCM auf einem aggressiv drosselnden OEM-Gerät verzögert/unterdrückt, gibt es keinen zweiten Weg, den Alarm zuzustellen.
- Ergänzend läuft die primäre Alarmzustellung vermutlich ohnehin über den eigenen WebSocket-Kanal (siehe Abschnitt 4) statt FCM, was das FCM-Ausfallrisiko für den Kern-Use-Case (App im Vordergrund/Foreground-Service aktiv) mindert — FCM dürfte primär für Weckung der App im Hintergrund/Chat-Benachrichtigungen dienen.

## 6. Werbung/Monetarisierung

- Das Paket heißt `distquakenoads` ("no ads") — vermutlich die kostenpflichtige Variante ohne Werbung, parallel zu einer gratis werbefinanzierten `distquake`-Variante (nicht analysiert).
- **Kein AdMob-Ad-Rendering-Code gefunden**: keine `ca-app-pub-`-Ad-Unit-IDs, keine `AdView`/`InterstitialAd`/`RewardedAd`/`MobileAds`-Klassenreferenzen im String-Dump. Die vorhandenen `admob_app_id`/`linked_admob_app_id`-Strings sind SQL-Spaltennamen aus dem Firebase-Analytics-internen Schema (Attribution-Linking), kein Beleg für aktives Ad-Serving. → **Befund: Die Paid-Version ist tatsächlich werbefrei**, die `ACCESS_ADSERVICES_*`-Berechtigungen sind wahrscheinlich reines Manifest-Merger-Rauschen aus Google-Play-Services-Bibliotheken.
- **In-App-Käufe/Abos vorhanden und zentral fürs Geschäftsmodell:** Play Billing Library eingebunden (`ProxyBillingActivity`/`V2`), eigene `InAppActivity`, Backend-Endpunkte `distquake_upload_subscription.php`, `distquake_upload_subscription_pro.php`, `distquake_update_subscription_and_pro_status.php`.
- **Besonders relevanter Fund — bezahlte Alarm-Priorität ("Pay-to-be-warned-first"):** Im String-Dump finden sich Texte wie:
  > "Be part of the priority lists of the first 10,000 or 100,000 people alerted in real time. The alert order is as follows: first all users with TOP 10K service, second all users with TOP 100K service, third all users with the PRO version and finally all other users. For the same service, the alert order is based on the distance from the epicenter."

  > "With this epicentre, you should receive the alert %s seconds in advance. However, %s people will be alerted before you. You will receive the alert %s seconds after the seismic waves. By subscribing now to the TOP 10K priority service, only %s people will be alerted before you..."

  Das bedeutet: Die **Reihenfolge der Alarm-Zustellung ist explizit nach Zahlungsstufe gestaffelt** (TOP 10K > TOP 100K > Pro > kostenlose Nutzer). Der zweite String impliziert sogar wörtlich, dass Nicht-Priority-Nutzer den Alarm **nach** Eintreffen der Erdbebenwellen ("after the seismic waves") erhalten können — bei einem Sicherheitsprodukt, dessen einziger Wert die Vorwarnzeit in Sekunden ist, ist das eine fundamentale Monetarisierung des Kernnutzens.

## 7. Alarm-Zustellung

- `NotificationChannel`-API wird genutzt (`createNotificationChannel`, `getNotificationChannel`, `setDefaultNotificationChannelId`).
- Vollbild-Alarm vorhanden: `setFullScreenIntent`/`canUseFullScreenIntent`, Berechtigung `USE_FULL_SCREEN_INTENT` gesetzt.
- Audio-Attribut `USAGE_ALARM` (Android `AudioAttributes`) wird verwendet — das ist der technische Trick, mit dem viele Apps den lautlos/DND-Modus umgehen, ohne die spezielle DND-Policy-Berechtigung anzufragen (Alarm-Audiostream wird von den meisten OEMs nicht automatisch stummgeschaltet). Es gibt aber **keine** explizite Abfrage/Nutzung der offiziellen DND-Policy-API (`isNotificationPolicyAccessGranted`) — d.h. kein garantierter, von Google sanktionierter DND-Bypass, sondern ein Verhalten, das von OEM zu OEM variieren kann.
- Eigener `PlayerService` (Foreground-Service-Typ Media Playback) für Alarmton-Wiedergabe, plus Text-to-Speech-Integration (`TextToSpeech`, Strings `eqn_tts_eqn`, `eqn_tts_magnitude`, `eqn_tts_official`, `eqn_tts_manual` u.a. inkl. Lautstärke-Presets) — die App kann Alarme vorlesen (z. B. Magnitude ansagen).
- App-Widget vorhanden (`AppWidgetProviderActivity`, `res/xml/appwidget_info.xml`).

## 8. Datenschutz/Datensammlung

- Gesammelte Daten (aus Berechtigungen/Endpunkten ableitbar): Standort (auch Hintergrund), Beschleunigungssensordaten, Firebase-Analytics-Events, Crashlytics-Absturzberichte, Facebook-SDK-Events (App-Events, Login), Google-Sign-In/FirebaseUI-Auth-Identitäten (E-Mail/Telefon/Google/Facebook), Freundeslisten/Chat-Inhalte (`distquake_download_friendship.php`, `ChatPersonalActivity`).
- SDKs mit Tracking-/Analytics-Charakter: **Firebase Analytics**, **Firebase Crashlytics**, **Firebase Remote Config + In-App Messaging** (Endpunkte `firebaseremoteconfig.googleapis.com`, `firebaseremoteconfigrealtime.googleapis.com`, InAppMessaging-Proto-Dateien im Assets-Ordner), **Facebook SDK** (App-Events, `graph.facebook.com`-Zugriff via `graph.%s`-Template), Google Sign-In, reCAPTCHA (`recaptcha.net`), Google Play Integrity API (Anti-Abuse — sinnvoll gegen gefälschte Sensordaten in einem Crowdsourcing-Netz, **Vermutung**: dient der Erkennung manipulierter/emulierter Clients).
- Keine dedizierte `network_security_config` gefunden (siehe Abschnitt 4).
- Privacy-Policy-/Terms-Strings vorhanden (generische FirebaseUI-Auth-Consent-Texte: "I have read and I accept Privacy Policy and Terms and Conditions"), aber keine feste Klartext-URL zur Privacy Policy im String-Dump gefunden (wird vermutlich dynamisch aus Remote Config/Strings-Ressourcen geladen — **Vermutung**).
- Umfangreiches Social-Feature-Set (Profile, Freundschaftsanfragen, privater Chat, öffentlicher Chat, "Report User", "Friend Misconduct") bedeutet: deutlich größere Datenschutz-Angriffsfläche als eine reine Alarm-App (Nutzerprofile, Standortverlauf, Chatinhalte, Moderationsdaten).

## 9. Schwächen/Angriffspunkte für uns (Zusammenfassung)

1. **Bezahlte Alarm-Priorität** — der gravierendste Punkt: Geschwindigkeit der Lebensrettung ist explizit gestaffelt nach Abo-Stufe.
2. **Idle+Charging-Constraint** limitiert die tatsächliche Größe des "Live"-Sensornetzes drastisch (nur ladende, ruhende Handys erkennen aktiv mit).
3. **Kein Backup-Push-Kanal** (nur FCM) — Risiko bei OEM-Battery-Killern (Xiaomi/Huawei/Oppo etc.).
4. **Kein Multi-Hazard** — im gesamten String-Dump keine Hinweise auf Tsunami-, Sturm-, Flut- oder andere Gefahrenwarnungen; reiner Erdbeben-Fokus.
5. **Kein Türkei-spezifischer Katalog-Anschluss** erkennbar (nur INGV/USGS referenziert, kein AFAD/Kandilli-String gefunden) — Marktlücke für einen Türkei-fokussierten Anbieter.
6. **UI-Komplexität/Feature-Bloat**: vollwertiges Social-Network (Freunde, privater/öffentlicher Chat, Profile, Melde-/Moderationssystem) plus 3D-Globus-Ansicht plus Wellenform-Chart — hohe kognitive Last für eine App, deren Kernversprechen "schnelle Warnung" ist.
7. **Veraltetes Backend-Muster** (PHP-Skript-Endpunkte) und global aktiviertes Cleartext-Flag ohne einschränkende Security-Config — Vermutung, aber ein Signal für technische Modernisierungslücken.
8. **Registrierungs-/Login-Reibung**: FirebaseUI-Auth + Facebook-Login + reCAPTCHA deuten auf Kontozwang zumindest für Social-Features hin — potenzielle Onboarding-Hürde.
9. **Honesty-Layer nur teilweise vorhanden**: Die App zeigt zwar Unsicherheit bei Crowd-basierten Magnitudenschätzungen an ("uncertainty can be high…") und lokale Netzdichte, aber keine erkennbare systematische Fehlalarm-Statistik/Kalibrierung pro Region — Raum für eine echte, konsequente Ehrlichkeitsschicht.

---

## Differenzierungs-Chancen für AlearthApp (priorisiert)

1. **"Kein Pay-to-be-warned-first"** — als zentrales, hart kommunizierbares Ethik-Versprechen: Alarme gehen bei uns gleichzeitig an alle im Gefahrenradius, unabhängig von Abo-Stufe. Direkter, faktenbasierter Kontrast zum dokumentierten TOP-10K/TOP-100K-Prioritätssystem von EQN.
2. **Größeres/durchgängigeres Sensornetz**: Wenn AlearthApp Crowd-Sensing nicht auf "idle + laden" beschränkt (bzw. das transparent und nutzerfreundlicher konfigurierbar macht), ist das ein direkter technischer Vorteil in der Netzdichte — ehrlich kommunizieren, inkl. eigener Akku-Trade-offs.
3. **Echte Multi-Hazard-Abdeckung + Türkei-Fokus (AFAD/Kandilli)**: EQN hat weder das eine noch (erkennbar) das andere — klare Marktlücke, besonders für den türkischen Markt.
4. **Redundante Alarmzustellung** (mehrere Push-Kanäle / eigener Wake-Mechanismus) als Widerstandsfähigkeit gegen OEM-Battery-Killer — explizit gegen EQNs Single-Channel-FCM-Abhängigkeit positionieren.
5. **Schlanke, fokussierte UX** ohne eingebautes Social-Network — "eine App, ein Zweck" gegen EQNs Chat/Freunde/Profile-Bloat.
6. **Konsequente Ehrlichkeitsschicht**: über EQNs punktuelle Unsicherheitshinweise hinaus – systematische, region-basierte Fehlalarm-/Trefferquote sichtbar machen.
7. **Gratis + werbefrei ohne Kompromiss**: EQNs Paid-Version ist zwar auch werbefrei, verlangt dafür aber zusätzlich Abo-Zahlungen für Alarmgeschwindigkeit selbst — AlearthApp kann "gratis UND schnell für alle gleich" als Alleinstellungsmerkmal beanspruchen.

---

*Hinweis: Diese Analyse basiert auf statischer String-/Manifest-Extraktion, nicht auf vollständigem Decompile oder Laufzeitbeobachtung. Alle als "Vermutung" markierten Punkte sind plausible Ableitungen, keine verifizierten Fakten.*
