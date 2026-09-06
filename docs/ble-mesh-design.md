# Ernstfall-BLE-Mesh — Designdokument (Stufe 2)

Optionales, Ernstfall-getriggertes, signiertes BLE-Broadcast-Relay für den Android-Client von „Alert2IQ". Trägt im Katastrophenfall (Mobilfunk/Internet tot nach Starkbeben) eine winzige **signierte Alarm-** (und optional **SOS-**) Nachricht von Handy zu Handy weiter — maximal akkusparend. **Dies ist ein Entwurf, kein Code.**

Grundlage: die belegte Recherche (BitChat als Blueprint, Serval als Erdbeben-Präzedenz, Bridgefy/FireChat als Sicherheits-Gegenbeispiele). Kernprinzip: **kein Eigenkrypto — wir nutzen die bereits vorhandene Ed25519-Signatur wieder.**

---

## 1. Ziel & Nicht-Ziele

**Ziel:**
- Wenn die Infrastruktur zusammenbricht, soll eine bestätigte Warnung weiter fließen: Geräte, die den Alarm noch online empfingen, geben ihn per BLE an Geräte in Reichweite weiter, die keine Verbindung mehr haben (Store-and-Forward-Flooding, hop-begrenzt).
- Optional derselbe Kanal für **SOS** (Hilferuf mit grober Position) an Kontakte/nahe Helfer.
- **Maximal akkusparend:** im Normalbetrieb praktisch null Verbrauch (Modul standardmäßig AUS).

**Nicht-Ziele (bewusst):**
- Kein Chat, keine Dateien, kein Medientransfer.
- Kein garantierter iOS↔Android-Mesh (ungelöstes Branchenproblem — separat betrachtet, § 8).
- Kein Ersatz für die Online-Zustellung (FCM/WebSocket) — reiner **Notfall-Fallback**.
- Keine Standortverfolgung; BLE-Scan läuft mit `neverForLocation`.

---

## 2. Architektur

**Reines BLE-Advertising-Broadcast ohne Verbindungsaufbau + selbstgebautes Epidemic-Flooding.** Kein SIG-BLE-Mesh-Profil (Android-Telefone sind darin nur Proxy-Client, kein Relay). Nearby Connections / Wi-Fi Aware nur als spätere Fallback-Option (§ 8).

Komponenten (Client-seitig, alle im Modul, standardmäßig inaktiv):
- **TriggerManager** — entscheidet, wann das Mesh „armed"/aktiv ist (§ 5). Einziger Weckpunkt.
- **MeshForegroundService** (Typ `connectedDevice`) — hält Advertising/Scan am Leben, solange aktiv; zeigt Pflicht-Notification („Notfall-Weitergabe aktiv").
- **Advertiser** — sendet die eigenen/weiterzureichenden Pakete als BLE-Advertisements (Extended Advertising, § 3).
- **Scanner** — empfängt Advertisements der Nachbarn, dedupliziert, verifiziert, leitet weiter.
- **RelayCache** — Store-and-Forward: gesehene Message-IDs (Dedupe), Hop-Zähler, Ablauf = Alarm-Gültigkeitsfenster.
- **Verifier** — prüft Ed25519-Signatur gegen den **eingebetteten Server-Public-Key** (identisch zu `Signing.kt`), bevor irgendetwas angezeigt oder weitergereicht wird.

Datenfluss: `Online-Alarm empfangen (FCM/WS) → in kompaktes BLE-Paket wandeln → bei Trigger broadcasten` **und** `fremdes Advertisement empfangen → verifizieren → dedupe → anzeigen falls neu → Hop−1 → weiter-advertisen bis TTL=0`.

---

## 3. Paketformat & Byte-Budget (die harte Entscheidung)

**Kernbefund: Die Ed25519-Signatur ist fix 64 Byte — allein das sprengt das Legacy-Advertising-Limit von 31 Byte. Daher ist Extended Advertising (Bluetooth 5, Android 8+) für die Einschritt-Broadcast-Weitergabe zwingend.**

Damit das Paket **selbsttragend signiert** ist (jedes Gerät kann offline verifizieren, ohne den Server), signiert der **Server** eine **kompakte Binärform** des Alarms mit demselben Ed25519-Schlüssel — zusätzlich zum vollständigen JSON-Payload für FCM/WS. Das BLE-Paket ist also nicht das JSON, sondern eine kompakte, separat signierte Binärstruktur. So bleibt es klein und offline-verifizierbar.

**Kompaktes Alarm-Paket (Zielgröße ~110–130 B, passt in eine Extended-Advertising-PDU ≤ 255 B):**

| Feld | Bytes | Inhalt |
|---|---|---|
| magic/version | 1 | Protokoll-Kennung + Version |
| msg_type | 1 | 0=Alarm, 1=SOS |
| hop_ttl | 1 | verbleibende Hops (Start 7) |
| flags | 1 | test-Bit, tier-Bits (P0/P1/P2) |
| msg_id | 8 | Dedupe-ID = Hash(id+ver), stabil pro Ereignisversion |
| origin_ms | 6 | Herdzeit (Unix ms) |
| lat | 4 | float32 |
| lon | 4 | float32 |
| depth | 1 | km |
| mag / mag_hi | 2 | Magnitude ×10 |
| radius_hint | 2 | Alarmradius km |
| **signature** | **64** | Ed25519 über die obigen Felder (Server-signiert) |
| **Summe** | **~99** | + Header-Overhead → ~110–130 B |

- **Signatur-Domäne:** Der Server signiert die kompakten Bytes (Felder magic…radius_hint) — die App verifiziert exakt diese. Das ist eine **eigene, kompakte Signatur-Domäne** neben der JSON-`canonical_bytes` (klar dokumentieren; der Server erzeugt beide Artefakte aus demselben kanonischen Ereignis).
- **`hop_ttl` und `flags` liegen AUSSERHALB der Signatur-Domäne** (sie ändern sich beim Relay) — sonst würde jeder Hop die Signatur brechen. Nur die unveränderlichen Ereignisfelder werden signiert; Hop/TTL sind ungeschützte Transport-Metadaten (Manipulation kostet nur einen Hop, nicht die Authentizität des Inhalts).
- **Legacy-only-Geräte (kein Extended Advertising, `isLeExtendedAdvertisingSupported()==false`):** können ein solches Paket nicht in einem Advertisement senden. Fallback: (a) sie empfangen weiterhin normal (Scannen funktioniert), (b) zum Senden ein kurzes Legacy-Beacon „Alarm vorhanden, ID x" + optionaler GATT-Read für die vollen Bytes — komplexer, deshalb Stufe-2b. MVP zielt auf Extended-Advertising-fähige Geräte (praktisch alle ab ~2017).

**SOS-Paket:** gleiche Hülle, `msg_type=1`, Nutzdaten = grobe Position + Zeit + Geräte-Attest-ID; signiert mit dem **Geräteschlüssel** des Absenders (nicht dem Server-Key). Empfänger können die Server-Signatur-Prüfung hier nicht anwenden → SOS wird als „ungeprüfter Absender" markiert, Vertrauen kommt aus Attest/Reputation/Rate-Limit (§ 7).

---

## 4. Verifikations- & Relay-Regeln

1. **Verify-before-anything:** Advertisement empfangen → Signatur gegen eingebetteten Server-Public-Key prüfen. Ungültig → sofort verwerfen (nicht anzeigen, nicht weiterreichen).
2. **Dedupe:** `msg_id` im RelayCache? → verwerfen (schon gesehen/weitergereicht).
3. **Ablauf:** `origin_ms` älter als das Alarm-Gültigkeitsfenster (z. B. 15 min für EEW) → verwerfen.
4. **Anzeigen:** neu + gültig → in die vorhandene Alarm-UI speisen (derselbe Weg wie FCM/WS-Alarme).
5. **Relay:** `hop_ttl > 0` → `hop_ttl−1`, `msg_id` in Cache, Paket ins eigene Advertising-Set aufnehmen (für ein begrenztes Fenster re-advertisen), dann aus dem aktiven Set entfernen.
6. **Flut-Schutz:** Hop-Limit 7 (BitChat-Vorbild), Cache-TTL = Gültigkeitsfenster, max. gleichzeitig re-advertiste Pakete begrenzt.

---

## 5. Trigger-Modell & Akku-Plan (Kern der Anforderung)

**Standard: AUS.** Das Modul verbraucht im Normalbetrieb nichts. Zustandsmaschine:

```
AUS ──(Nutzer-Opt-in aktiv)──► ARMED ──(Auslöser)──► AKTIV ──(Zeitfenster/Entwarnung)──► AUS
```

- **ARMED** (kostet ~nichts): nur passive Signale, die ohnehin anfallen — `ConnectivityManager`-Callback + gelegentlicher Accelerometer-Batch. Kein Dauer-Scan, kein Advertising.
- **Auslöser für AKTIV** (eines von beiden):
  - (a) **Bestätigter Alarm** kommt noch online herein (FCM/WS) → sofort ins Mesh spiegeln (die Nachbarn ohne Netz erreichen).
  - (b) **Internet-Verlust** (`NET_CAPABILITY_INTERNET` ohne `NET_CAPABILITY_VALIDATED`) **UND** Starkbeben am Accelerometer (Magnitudenschwelle) — beides zusammen, nie Konnektivitätsverlust allein (sonst Fehltrigger im Flugmodus/U-Bahn/Tunnel).
- **AKTIV:** Advertising + Scan an, zeitbegrenzt (24–72 h, angelehnt an die kritischen ersten Tage), dann Auto-Aus/Re-Check. Foreground-Service mit Pflicht-Notification.

**Akku im AKTIV-Modus (quantifiziert):**
- Advertising-Intervall im **Sekundenbereich (1–5 s)** statt Minimum: 100 ms → 1 s senkt den mittleren Strom um ~93 %; bei ~1 s liegt der Advertising-Anteil grob bei Bruchteilen von % Akku/h statt 15–20 % bei 20 ms.
- **Scan asymmetrisch** kürzer/seltener takten als Advertising (Scannen ist teurer); Batch-/PendingIntent-Scan statt Dauer-`ScanCallback` (moderne OEM-ROMs setzen nicht-batched Scan bei Screen-off ohnehin aus).
- **Kein eigener Wakelock;** auf Doze-Maintenance-Windows und Sensor-Batch-Flush aufsetzen (`maxReportLatencyUs` groß).
- Zeitbegrenzung + Auto-Aus verhindern Dauerlast nach dem Ereignis.

Merksatz: Der Akku-Kompromiss ist real und nicht wegzudesignen (Briar als Negativbeispiel: reiner Dauerscan ≈ 4× schnellerer Akkuverlust). Deshalb ist das **Trigger-Modell (Standard AUS)** die zentrale Architekturentscheidung, kein Nice-to-have.

---

## 6. Android-Constraints-Checkliste

- **Permissions (API 31+):** `BLUETOOTH_ADVERTISE`, `BLUETOOTH_SCAN` (mit `android:usesPermissionFlags="neverForLocation"`), `BLUETOOTH_CONNECT`. Für < API 31: `BLUETOOTH`/`BLUETOOTH_ADMIN` (`maxSdkVersion=30`) + `ACCESS_FINE_LOCATION`.
- **Foreground-Service (API 34+ Pflicht):** `foregroundServiceType="connectedDevice"` + Permission `FOREGROUND_SERVICE_CONNECTED_DEVICE`; `BLUETOOTH_CONNECT` zum Service-Start halten, sonst `SecurityException`. `POST_NOTIFICATIONS` (API 33+) für die Pflicht-Notification.
- **Doze/App-Standby:** Scan-Raten-Limits (Android 9: >5 Scans/30 s → 30 s Sperre); Foreground-Service umgeht Doze, ist aber „Privileg, kein Schlupfloch". Beim Aktivieren Nutzer um **Ausnahme von der Akku-Optimierung** bitten.
- **Extended-Advertising-Fähigkeit** zur Laufzeit prüfen (`isLeExtendedAdvertisingSupported()`, `getLeMaximumAdvertisingDataLength()`), nicht hartkodieren.
- **OEM-Aggressivität** (Xiaomi/Oppo/Samsung): Hintergrund-BLE-Scan wird bei Screen-off zunehmend unterdrückt/auf 5-min-Batches gedrosselt → **Geräte-Kompatibilitätshinweis** in der App, ehrlich kommunizieren.

**Nutzer-Flows:** Opt-in beim Einrichten (aus, aufklärend); beim ersten AKTIV-Werden: Berechtigungen + Akku-Ausnahme anfragen; sichtbare Notification, jederzeit abschaltbar.

---

## 7. Sicherheit & Missbrauch

- **Kein Eigenkrypto** — Ed25519-Signatur des bestehenden Systems wiederverwenden (genau das, woran FireChat/Bridgefy scheiterten). Alarm-Pakete sind server-signiert; verify-before-relay.
- **Hop-Limit + Dedupe + Ablauf** begrenzen Flutung/Endlosschleifen und Replay.
- **Transport-Metadaten ungeschützt, aber folgenlos:** Hop/TTL außerhalb der Signatur; Manipulation kostet höchstens einen Hop, nie die Inhalts-Authentizität.
- **SOS:** nicht anonym, ratenbegrenzt, an Geräte-Attest/Reputation gebunden; als „ungeprüfter Absender" gekennzeichnet, bis validierbar. Missbrauch (Falsch-SOS) bindet Helfer — deshalb Sperrbarkeit + Rate-Limit (wie in Spec § SOS).
- **Kein Standortleck:** Scan mit `neverForLocation`; SOS-Position nur grob + nur auf ausdrückliche Nutzeraktion.

---

## 8. Ehrliche Grenzen & Geräte-Fragmentierung

- **Kein natives Multi-Hop-Mesh-API** in Android — Store-and-Forward muss selbst gebaut werden (wie BitChat/CrowdLink).
- **Reichweite ~10–100 m/Hop, stark dichteabhängig.** Im verwüsteten, dünn besiedelten Gebiet (genau das Nach-Erdbeben-Szenario) reißt die Kette, wenn zu wenige Geräte aktiv sind. Ehrlich: Das Mesh hilft in Ballungsräumen, nicht im leeren Land.
- **OEM-Unterdrückung** kann „akkusparend" auf manchen Geräten faktisch zu „funktioniert nicht" machen — Kompatibilitätsliste/Warnung nötig.
- **iOS-Interop ungelöst** (Core-Bluetooth-Restriktionen; selbst BitChat experimentiert mit Wi-Fi Aware). Cross-Plattform-Mesh ist ein eigenes Forschungsproblem, nicht Teil des MVP.
- **Legacy-only-Geräte** brauchen den GATT-Fallback (Stufe 2b).
- Präzedenz, das real funktionierte: Serval (Haiti-Erdbeben), FireChat (HK, Skalierung), BitChat (technisch am nächsten). Was scheiterte: FireChat/Bridgefy-Frühversion an fehlender/eigener Krypto — unsere Ed25519-Wiederverwendung adressiert genau das.

---

## 9. Phasenplan

- **MVP (Stufe 2a):** Server erzeugt kompaktes signiertes Alarm-Paket; Client-Modul (Trigger AUS-Standard, Extended-Advertising-Broadcast, Scan, verify-before-relay, Dedupe/Hop/TTL) speist gültige Alarme in die vorhandene Alarm-UI. Nur Alarm, nur Extended-Advertising-Geräte, nur Android.
- **Stufe 2b:** SOS über denselben Kanal (Geräteschlüssel, Attest/Reputation); Legacy-Fallback (Beacon+GATT).
- **Stufe 2c:** iOS-Frage evaluieren (Wi-Fi Aware vs. BLE); optionale **LoRa-Companion**-Kopplung (Meshtastic) für Kilometer-Reichweite als Hardware-Ausbaustufe.

---

## 10. Offene Fragen / Risiken

- Genaues Alarm-Gültigkeitsfenster (Ablauf) je Tier — EEW kurz (Minuten), Nachbeben-/Katastrophenhinweise länger?
- Server-seitige Erzeugung des kompakten signierten BLE-Pakets: zusätzliches Artefakt neben JSON — Format einfrieren + versionieren.
- Feldtest der realen Reichweite/Weitergabe-Wahrscheinlichkeit bei verschiedenen Gerätedichten (nur empirisch beantwortbar).
- OEM-Geräte-Matrix: welche Modelle unterdrücken Hintergrund-BLE wie stark? (Kompatibilitätsliste pflegen.)
- Interferenz mit dem P0b-Sensor-Sammeldienst (beide nutzen Foreground-Service/Sensoren) — Ressourcen/Batterie koordinieren.
