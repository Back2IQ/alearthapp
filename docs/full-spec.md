# Alert2IQ (TDA) — Vollspezifikation

**Stand:** 21. August 2026 · **Geltungsbereich:** die in diesem Repository vorhandene App (Server `tda/server/`, Android-Client `tda/android/`, Web-App `tda/webapp/`) samt geplanter Ausbaustufen.
**Abgrenzung:** Diese Spezifikation enthält **keine Design- und Stilvorgaben** (keine Farben, Themes, Layouts, Typografie). Funktionale Barrierefreiheits-Anforderungen (Redundanz von Bedeutungsträgern, Vibration, TTS) sind Verhalten, kein Stil, und bleiben enthalten.
**Methode:** Jeder Punkt der bestehenden Dokumente (UMSETZUNGSPLAN.md Rev. 4, client-mvp-spec.md, webapp-spec.md, ble-mesh-design.md) wurde übernommen **und verbessert**; das Änderungsregister in §15 macht jede Verbesserung nachvollziehbar.

**Leitprinzipien (unverändert hart):**
1. Ausschließlich echte, nachvollziehbare Datenquellen und eigene, physikalisch begründete Detektion. Keine Mock-Ereignisse in Produktion, keine erfundenen Messwerte, keine behauptete Aktualität ohne Messung.
2. Nullkosten-MVP: jede Komponente auf dauerhaft kostenloser Ebene; verbleibende Kosten offen ausgewiesen (25 USD Play-Konto einmalig; Domain ~10–15 USD/Jahr; Stufe 2: Apple 99 USD/Jahr, SMS-Fallback laufend).
3. Ehrlichkeit ist Feature: jede Warnung trägt Stufe, Quelle, Konfidenz und Entscheidungspfad; jede fehlende Fähigkeit ist sichtbar, nie kaschiert.

---

## 0. Ist-Stand der vorhandenen App

Was existiert und ist testgedeckt (Referenz für alle Soll-Punkte):

| Komponente | Stand |
|---|---|
| **Server** (`tda/server/`, Python, 36 Testdateien) | Adapter EMSC/USGS/AFAD/GDACS → append-only Stream (In-Memory oder Redis Streams) → Correlator (kanonisches Ereignis, Versionen) → Publisher (Ed25519-signierter Payload, FCM- oder Fake-Transport, `min_publish_mag` default 4.0). P0a-Module: Picker, Dedupe, Associator, EPIC-artige Ortung, Pd-Magnitude, PLUM, Diskriminierung, Rekalibrierung, Replay (Kahramanmaraş-Fixture). P0b-Module: Gateway, Signale, Schwellen, Hintergrundrate, Dichte, Cluster, Wellenfront-Check, Reputation, Tier, Replay. Geo-Zellen, WS-Bridge (`serve_local.py`), Hazard-Payload (GDACS). |
| **Android-Client** (`tda/android/`, Kotlin, Views) | Baubare Debug-APK. Startscreen (Status-Chip, Ortswahl 6 Städte, Sprachwahl TR/EN/KU/AR zur Laufzeit, Live-Seismogramm aus echtem Accelerometer), AlertActivity (Countdown, Kennzahlen, Schutzhinweis, Ton+Vibration, P0→P2-Eskalation), Sequenzliste, Ereignisbericht, lokaler Testszenario-Injektor. Kein Netz-Empfang verdrahtet. |
| **Web-App** (`tda/webapp/index.html`) | Funktionsspiegel des Clients als eine `file://`-fähige HTML-Datei; zusätzlich WebSocket-Servermodus mit **verpflichtender Ed25519-Verifikation** (Web Crypto) gegen eingebetteten Public Key; Injektor identisch. |
| **Payload v1 (implementiert)** | String-Map `v,id,ver,state,tier,test,origin_ts,lat,lon,depth_km,mag,mag_hi,src,issued_ts` + `sig`; kanonische Bytes = alphabetisch sortierte `key=value`-Zeilen, `\n`-verbunden, UTF-8; Ed25519 über diese Bytes. |

**Erkannte Schwächen des Ist-Stands, die diese Spec behebt:** kein Ablauf-/Anti-Replay-Feld im Payload (§5.2); keine Schlüssel-ID → Rotation erzwingt App-Update (§5.3); Countdown/Intensität nutzen Epizentral- statt Hypozentraldistanz — Herdtiefe wird ignoriert (§2.5); Client prüft `ver`-Monotonie nicht (Downgrade-Replay möglich) (§6.2); Intensitätsformel ist ad hoc statt publizierte IPE (§2.5).

---

## 1. Zielbild & Produktversprechen

Eine Warn-App für die Türkei (Android zuerst, iOS Stufe 2 nur Empfang), die Erdbeben **in Sekunden** meldet — im besten Fall vor Eintreffen der S-Wellen — und Seebeben-Tsunamis Minuten vor der Welle ankündigt. Weitere Naturgefahren folgen je erst nach formaler Quellenfreigabe; bis dahin decken reputationsgewichtete Bürgermeldungen (§2.6) und GDACS (§12.3) diese Lücke ehrlich gekennzeichnet ab.

**Verbessert — das Versprechen ist messbar formuliert:**
- **Warnzeit:** Systemanteil der Latenzkette (nach Physik) median < 5 s (§4); jede reale Warnzeit wird gemessen und veröffentlicht.
- **Ehrlichkeit:** 100 % der Warnungen tragen Stufe, Quelle, Konfidenz, Entscheidungspfad; 100 % der Quellausfälle und Ebenen-Abschaltungen erscheinen auf der Statusseite.
- **Blindzone quantifiziert statt nur benannt:** Im Umkreis von grob `r ≈ vS · (t_detekt + t_zustell)` um das Epizentrum (bei 10 km Herdtiefe und 5 s Systemzeit ≈ 15–25 km) kommt keine Warnung vor der S-Welle an — von keinem System der Welt. Die App zeigt pro Nutzerort an, ab welcher Herddistanz sie realistisch vorwarnen kann.
- **Kein Behördenersatz:** Verhaltens-/Evakuierungsanweisungen verweisen auf zuständige Stellen; kein Nachahmen von Cell Broadcast.

**Bewusster Nicht-Umfang (unverändert):** Chat/Social, Medien-Feeds, alles, was nicht warnt, rettet oder vorbereitet.

---

## 2. Detektionsmodell

### 2.1 Zwei unabhängige Entscheidungspfade

Warn-Entscheidung über zwei parallele, unabhängige Pfade — wer zuerst auslöst, gewinnt; Ergebnisse werden fusioniert, nie gegenseitig blockiert:

1. **Modellbasiert (quellenorientiert):** Aus den ersten P-Wellen-Sekunden Ort/Tiefe/Magnitude schätzen → Intensität + S-Ankunft pro Nutzerort. Am frühesten, aber modellabhängig.
2. **Beobachtungsbasiert (PLUM-Prinzip):** Wo Stationen (später Phone-Cluster) starke Erschütterung **messen**, wird der Ausbreitungsradius gewarnt — ohne Quellmodell. Robust gegen Magnituden-Sättigung und lange Brüche (Tōhoku 2011, Kahramanmaraş 2023).

**Verbessert:** Der PLUM-Pfad bekommt eine explizite Basisparametrierung: Auslösung ab gemessener Intensität ≥ MMI V an ≥ 2 Stationen innerhalb 30 km; Warnradius = Prognosekreis (Startwert 30 km, konfiguriert) mit distanzabhängiger Abklingfunktion und Vs30-Standortkorrektur der Zielzelle; Fortschreibung alle 1 s, solange neue Messungen eintreffen. Beide Pfade schreiben in dasselbe kanonische Ereignis (§3.4); der jeweils höhere Warn-Level gewinnt (asymmetrische Eskalation §2.5).

### 2.2 Erdbeben-Ebenen

| Stufe | Quelle | Latenz | Verfügbar ab |
|---|---|---|---|
| **P0a — Stations-Frühdetektion** | Eigener Echtzeit-Picker auf SeedLink-Streams. Tag-1-frei nur wenige GEOFON-Stationen; KOERI-Port sofort testbar; TU/KO-Vollabdeckung nur per Kooperation (§12.4). | ~5–20 s bei ausreichender Dichte, Tag 1 nur punktuell | Tag 1 punktuell |
| **P0b — Phone-Cluster** | Crowdsourced Accelerometer (nur idle + laden) | Sekunden (Beleg: 12 s nach Bruchbeginn, bis 58 s Vorwarnung beim M7.8 2023, Finazzi et al. 2024) | Stufe 2, pro Region nach Dichte-Freischaltung |
| **P1 — Schnellmeldung** | EMSC-WebSocket (inkl. Flashsourcing), USGS-Feed, AFAD-Polling | ~1–3 min | Tag 1 (implementiert) |
| **P2 — Bestätigung/Korrektur** | Konvergenz ≥ 2 unabhängiger Kataloge | Minuten | Tag 1 (implementiert) |

**P0a-Algorithmik, verbessert präzisiert (Module existieren):**
- **Picker:** zweistufig — energiebasierter Schnelltrigger (STA/LTA-Klasse, Latenzpfad) parallel zu ML-Picker (PhaseNet/SeisBench) als Qualitätspfad; ein Pick gilt erst mit SNR-Mindestwert und Stations-Gesundheitsstatus „ok". Dedupe pro Station/Zeitfenster (implementiert: `picker.py` + Dedupe-Test).
- **Assoziation:** Quorum ≥ 4 konsistente Picks (Laufzeit-Residuen unter Schwelle) für modellbasierten Alarm; PyOcto-Klasse als Referenz, Grid-Search-Ortung (EPIC-artig, `epic.py`) als implementierte Basis. Fernbeben (Ursprung außerhalb definierter Quellzonen) → nur Katalogabgleich, nie Lokalwarnung.
- **Schnell-Magnitude:** Pd/τc-Klasse aus den ersten 3 s P-Welle (Wu et al. 2007), fortgeschrieben jede Sekunde; Ausgabe immer als Spanne `[mag, mag_hi]`, nie Punktwert. **Verbessert:** Rekalibrierung (`recalibrate.py`) läuft als geschlossene Schleife gegen P2-Kataloge — Koeffizienten sind versionierte Konfiguration (§8.4), nicht Code.
- **Finite-Fault-Tracker (Stufe 2):** ab M7-Verdacht Bruch als wachsende Linie statt Punkt; im MVP kompensiert durch PLUM-Pfad + asymmetrische Eskalation.

**P0b-Detektion (implementiert als Sammel-/Schattenpfad, warnt im MVP nicht):**
- Messung nur bei *idle + laden*; Selbstkalibrierung der Rauschschwelle pro Gerät (Perzentil-basiert über rollierendes Fenster — Waschmaschinen-Geräte schließen sich selbst aus).
- Trigger tragen NTP-Zeit ± Unsicherheit und **vergröberte Rasterzelle**, nie Präzisionsstandort.
- Cluster-Test: Triggerdichte pro Zelle gegen Grundrate der aktuell online befindlichen Geräte, als statistischer Signifikanztest relativ zur Live-Dichte (implementiert: `background.py`, `density.py`, `cluster.py`). **Verbessert:** Signifikanz als Poisson-Überraschungswert mit False-Discovery-Kontrolle über alle Zellen (viele Zellen = Multiple-Testing-Problem, sonst systematische Fehlalarme bei Netzwachstum).
- **Dichteunabhängige physikalische Untergrenze bleibt ausgewiesen:** MEMS-Rauschen ⇒ P0b sieht grob erst ab MMI ~IV — steht so in der Netzdichte-Anzeige.

### 2.3 Lokale Selbstdetektion (letzte Verteidigungslinie)

Erkennt ein Gerät selbst ein starkes P-Wellen-Muster, während der Aufmerksamkeits-Modus aktiv ist **oder** keine Verbindung besteht, schlägt es sofort lokal Alarm — null Netzlatenz, funktioniert bei totem Netz. Klar als „Geräte-Eigendetektion" gekennzeichnet.

**Verbessert — Fehlauslösungs-Härtung konkretisiert:**
- Scharf nur in zwei Zuständen: (a) Aufmerksamkeits-Modus vom Server gesetzt, (b) Konnektivitätsverlust bei zuvor gesunder Verbindung. Nie dauerhaft scharf im Normalbetrieb.
- Auslösebedingung: Gerät ruhig gelagert (Varianz-Vorfenster unter Schwelle) **und** P-Wellen-typischer Frequenz-/Amplitudenverlauf über konservativer Schwelle; ein bewegtes Gerät (Tasche, Fahrt) detektiert nicht.
- Bei Auslösung ohne Netz: Alarm + sofortiger Wechsel ins Offline-Sicherheitspaket (§6.5); mit Netz: zusätzlich Trigger-Upload höchster Priorität.
- `HIGH_SAMPLING_RATE_SENSORS` (Android 12+) wird zur Laufzeit geprüft; ohne Freigabe läuft die Detektion mit Standardrate und entsprechend konservativerer Schwelle — der Schutzstatus zeigt die reduzierte Empfindlichkeit an.

### 2.4 Tsunami (abgeleitete Ebene)

| Stufe | Signal | Latenz | Aussage |
|---|---|---|---|
| **T0 — Potenzial-Hinweis** | Eigene Ableitung aus P0/P1 nach der offiziellen NEAMTWS-Entscheidungsmatrix (Magnitude, Herdtiefe, Lage See/Land-Nähe) | Sekunden nach Detektion | „Seebeben — Tsunami möglich. Küstennähe meiden, auf amtliche Warnung achten." — als Heuristik gekennzeichnet |
| **T1 — Amtliche Warnung** | KOERI-Bulletins (regionaler NEAMTWS-Dienst) | Minuten | Amtliche Warnstufe, Behördenverweis |
| **T2 — Bestätigung/Entwarnung** | IOC-Pegelstationen + Bulletin-Updates | Minuten–Stunden | Welle gemessen / Entwarnung |

**Verbessert:**
- **T0-Matrix als versionierte Konfigurationstabelle** (Magnitude×Tiefe×Zone → Stufe), nicht als Code — identisch änderbar/auditierbar wie alle Schwellen (§8.4); T0 bewusst konservativ, da der Bruchmechanismus anfangs unbekannt ist (Blattverschiebung vs. Aufschiebung); T1 mit Momententensor bleibt maßgeblich, T0→T1 ist derselbe Entscheidungsweg, nur bestätigt.
- **Ebenen-Umgewichtung offshore explizit:** vor jeder Küsten-Quellzone (Marmara, Ägäis, Zypernbogen) wird die reale Detektionsfähigkeit (Stationen? Katalog-Latenz?) pro Zone ausgewiesen — offshore stehen keine Telefone.
- **Wellen-Countdown (Stufe 2):** ETA-Raster aus GEBCO-Bathymetrie offline vorberechnet (Quellzone → Küstenpunkt), Lookup „Welle frühestens in ~X min"; **verbessert:** Raster wird mit Publikationsdatum und Methodenversion ausgeliefert, damit ein Client nie mit veraltetem Raster stumm falsch rechnet.
- **Entwarnungslogik im MVP:** über T1-Bulletin-Updates; T2-Pegelbestätigung ab Stufe 2. Entwarnung ist immer lautlos (§6.3).

### 2.5 Systemweite Warnregeln

- **Intensität statt Magnitude:** Gewarnt wird nach erwarteter Erschütterung am Nutzerort.
  **Verbessert — Rechenweg präzise und physikalisch korrekt:**
  1. `dist_hypo = sqrt(dist_epi² + depth²)` — **Hypozentraldistanz; die Herdtiefe geht ein** (Ist-Stand nutzt nur Haversine-Epizentraldistanz: überschätzt Intensität und verkürzt Countdown bei tiefen Beben).
  2. Intensität über eine **publizierte IPE** (Intensity Prediction Equation, Allen-Wald-Worden-2012-Klasse) mit Hypozentraldistanz statt der bisherigen Ad-hoc-Formel; Koeffizienten als versionierte Konfiguration; Ausgabe geklemmt [I..XII] mit Unsicherheitsband.
  3. **Vs30-Standortterm** pro gespeichertem Ort (offene USGS-Vs30-Daten; Istanbuls Beckenlagen wichtigster Fall) — Klassenzuordnung beim Anlegen des Orts, offline nutzbar.
  4. Countdown: `t_S = dist_hypo / vS − (now − origin)` mit vS = 3,5 km/s im MVP; **Stufe 2: regionales 1-D-Geschwindigkeitsmodell** statt Konstante. P-Wellen-Info mit vP = 6,0 km/s analog. Anzeige immer `max(0, …)`, bei 0 „S-Welle da".
- **Unsicherheit sichtbar und lebendig:** Magnitude immer als Spanne („M6,1–6,7, wird präzisiert"); Countdown und Intensität aktualisieren live bei jeder Ereignisversion.
- **Asymmetrische Eskalation:** Warnstufen steigen sofort (frühe Magnituden sind systematische Untergrenzen); Herabstufung/Entwarnung nur nach P2/T2-Bestätigung. **Verbessert — mit expliziter Hysterese:** Hochstufung latenzfrei bei jeder Versionserhöhung; Herabstufung frühestens nach konfigurierter Mindesthaltezeit **und** bestätigender Quelle; Entwarnung ist eigener Ereigniszustand, nie stilles Verschwinden.
- **Netzdichte offen:** P0b-Freischaltstatus pro Region sichtbar, inkl. Tag/Nacht-Verlauf (idle+laden ⇒ nachts beste Abdeckung — genau dann, wenn Warnung am wertvollsten ist).

### 2.6 Bürgermeldungen & Reputationsmodell (eigene Signalklasse, ab MVP)

Nutzer melden aktiv („Ich hab's gespürt"; „Überschwemmung/Sturm/Feuer hier"). Wichtigster Hebel für Nicht-Erdbeben-Gefahren: dort ist eine vertrauenswürdige Bürgermeldung oft das früheste, teils einzige Signal — ohne MGM-/Behörden-API.

**Reputationsmodell, verbessert von Score zu belastbarer Statistik:**
- Vertrauensscore als **Beta-Verteilung** (Bestätigt-/Widerlegt-Zähler) statt roher Zahl: das Gewicht eines Melders ist der Erwartungswert, die Unsicherheit (wenig Historie = breite Verteilung) fließt explizit in die Clusterbewertung ein. Neulinge starten praktisch bei null Gewicht; eine einzelne Erstmelder-Meldung propagiert nie (< C0).
- Score steigt durch Bestätigung (Instrument, unabhängige Melder, amtliche Quelle), sinkt bei Widerlegung; **verbessert: zeitlicher Verfall** — lange inaktive Reputation altert Richtung Prior, damit gekaperte Alt-Accounts kein Dauerkapital sind.
- Gestufte Sanktionen: Herabgewichtung → Rate-Limit → Shadow-Ban (eigene Meldung sichtbar, propagiert nicht) → Ausschluss. Alles auditiert (§8.5).
- Community-Gegenprüfung („Ich auch" / „Kein Ereignis hier") speist Reputation und Entdoppelung.

**Anti-Sybil („unabhängig" ist definiert):** Ein C1-Cluster zählt nur Melder mit Diversität über mehrere Achsen — Hardware-Attest, IP/ASN, Account-Alter-Streuung, plausible Standorthistorie — plus Mindest-Beobachtungshistorie. **Kaltstart:** Seed-Melder aus Partnerorganisationen/NGOs statt Selbst-Bootstrapping; bis dahin ist C1-Push je Region deaktiviert und sichtbar deaktiviert.

**Gestufte Konfidenz (unverändert), Zustellung präzisiert:**

| Konfidenz | Auslöser | Zustellung |
|---|---|---|
| **C0** | eine einzelne vertrauenswürdige Meldung | Karte + Opt-in-Push |
| **C1** | Raum-Zeit-Cluster mehrerer unabhängiger vertrauenswürdiger Melder | regulärer Push, gekennzeichnet „Bürgermeldung, unbestätigt" |
| **amtlich/instrumentell** | Instrument/amtliche Quelle | überschreibt, hebt auf reguläre Ereignisstufe |

**Harte Grenzen:** Erdbeben — Bürgermeldung nie alleiniger Auslöser (nur Bestätigung/Intensitäts-Wahrheit). Flut/Sturm/Feuer/Erdrutsch — C1 darf eigenständig pushen, ehrlich etikettiert; amtlich überschreibt immer. Eigener Kill-Switch pro Gefahr/Region (§8.5).

---

## 3. Detektions-Pipeline & Anti-Fehlalarm-Mechanik

### 3.1 Datenfluss

```
Phone-Trigger ────────┐                                ┌→ modellbasierter Pfad ─┐
Stations-Picks ───────┤→ append-only Strom → Fusion ──┤                        ├→ Publisher → Zustellung
Katalog-Feeds ────────┤                                └→ Beobachtungspfad ────┘
Bürgermeldungen (§2.6)┘   (reputationsgewichtet; ein kanonisches Ereignis, Zustandsmaschine)
```

Implementiert: Stream (In-Memory/Redis) → Correlator → Publisher; P0a-/P0b-Stränge als Module mit Replay-Tests.

### 3.2 Aufmerksamkeits-Modus

Ein provisorisches Signal (kleiner Cluster, einzelner starker Pick) löst keinen Alarm aus, sondern weckt das Umfeld: höhere Abtast-/Melderate im Umkreis, Push-Infrastruktur vorwärmen, lokale Selbstdetektion scharfschalten. Bestätigung ⇒ Kette ist warm (1–3 s Gewinn); sonst hat kein Nutzer etwas bemerkt. **Verbessert:** Der Modus hat ein hartes Zeitfenster (Auto-Reset nach konfigurierten Sekunden ohne Bestätigung) und wird pro Region gezählt/veröffentlicht (KPI: Aufmerksamkeits-Präzision), damit ein zu nervöser Vorfilter messbar wird.

### 3.3 Fehlalarm-Filter

- **Wellenfront-Konsistenz, wellenart-spezifisch:** Stations-Picks gegen P-Geschwindigkeit (~5,5–8 km/s), Phone-Trigger gegen S-/Oberflächenwellen (~3–4,5 km/s) — je eigener Konsistenz-Check statt einer pauschalen Spanne (implementiert: `wavefront.py`). Punktereignisse (Explosion) bleiben lokal; stadtweit Simultanes (Torjubel) hat keine laufende Front.
- **P0a-Gegenprobe:** Schlagen Profi-Stationen nicht an, obwohl sie müssten, wird der Phone-Cluster herabgestuft.
- **Nicht-tektonische Diskriminierung:** Sprengungen über bekannte Orte/Zeitmuster, Herdtiefe ≈ 0, P/S-Amplitudenverhältnis (implementiert: `discriminate.py`).
- **Verbessert — Filterentscheidungen sind Ereignisse:** Jede Verwerfung wird als `DISCARDED`-Übergang mit Begründung im kanonischen Ereignis protokolliert und ist im Replay sichtbar — Fehlalarm-Forensik braucht die Verwerfungen, nicht nur die Alarme.

### 3.4 Fusion & Ereignis-Zustandsmaschine

Alle Signale korrelieren in **ein** kanonisches Ereignis mit Versionshistorie: `detektiert → gewarnt(P0) → bestätigt(P1) → final/zurückgezogen(P2)`; zusätzlich `verworfen(Störung)`.
- **Mehrereignis-fähig:** parallele Ereignisse (Nachbeben in der Coda), Assoziation mit Zeitfenstern pro Quellzone.
- **Idempotenz:** Schlüssel = `(event_id, version)`; verhindert Doppel-Push (implementiert im Publisher-Pfad; Abnahme §11.2).
- **Verbessert — Tier-Ableitung als Verband statt Sonderfall:** Ist-Stand: `sources == {p0b} → P0`, `CONFIRMED → P2`, sonst `P1`. Soll: explizite Tier-Halbordnung P0 < P1 < P2 mit Regel „Tier ist monoton nichtfallend pro Ereignis"; neue Quellen (P0a, Bürger-Bestätigung) fügen Zweige hinzu, ohne bestehende Logik anzufassen; ein Client darf ein niedrigeres Tier für dieselbe `id` nie als Downgrade anzeigen (§6.2).

### 3.5 Zeitsynchronisation (kritische Abhängigkeit)

NTP-Sync mit gemessener Abweichung pro Gerät; Trigger tragen Uhr-Unsicherheit; unplausible Uhren werden bei der Assoziation heruntergewichtet/ausgeschlossen; serverseitige Ankunftszeit als Gegenprüfung. **Verbessert — konkrete Klientenregel:** Der Client zeigt den Sekunden-Countdown nur bei Uhr-Unsicherheit ≤ 300 ms (Konfigurationswert); darüber „Erschütterung möglich" ohne Sekundenzahl. Der NTP-Offset wird beim Verbindungsaufbau und periodisch gemessen, gecacht und im Ereignisbericht mit ausgewiesen.

### 3.6 Sequenz-Modus (Nachbebensturm)

Nach einem Starkbeben schaltet die Region in den Sequenz-Modus: Nachbeben werden zur Sequenz gruppiert (lebende Ansicht statt hundert Einzelwarnungen), Push nur oberhalb automatisch angehobener Schwellen, Rest lautlos in die Sequenzansicht. **Verbessert:** Schwellenanhebung folgt einer definierten Abklingkurve (Omori-Klasse: anfangs hoch, über Tage sinkend) statt eines statischen Sprungs; ein Nachbeben, dessen erwartete Intensität am Nutzerort die Vollalarm-Schwelle reißt, durchbricht die Anhebung immer (M7,5 neun Stunden nach M7,8 — Kahramanmaraş — ist ein eigener Vollalarm, kein Sequenzeintrag).

### 3.7 Angriffs-Schutz (vergiftete Trigger, gefälschte Stationen)

- **Geräte-Attestierung zweipfadig:** Play-Integrity **plus** hardware-gebundener Geräteschlüssel (Keystore/StrongBox) + Verhaltenssignale — GMS-lose/Custom-ROM-Geräte werden nicht fälschlich abgewertet, Stock-Billiggeräte-Farmen nicht fälschlich geadelt. **Reinstall-Härtung:** Reputation bindet ans Hardware-Attest; Neuinstallation gibt keinen frischen Score, sondern kostet Zeit/Rate-Limit.
- Signierte Uploads, Rate-Limits, Reputationsgewichtung; Wellenfront-Check als physikalische Ebene (gescriptete Trigger erzeugen keine konsistente Ausbreitung).
- **Stations-Authentizität:** SeedLink über TLS wo möglich; Consumer-Sensoren (Raspberry Shake) lösen nie allein aus; plötzliche Korrelation sonst unkorrelierter Stationen gilt als Angriffs-Warnsignal, nicht als Bestätigung; eigene Stufe-3-Stationen mit Manipulationserkennung.
- Alarm-Payloads serverseitig signiert, clientseitig verifiziert (implementiert; Härtung §5).

### 3.8 Überleben des Ernstfalls

Hosting geografisch entkoppelt (außerhalb der Türkei); winzige Payloads; aggressive Wiederholung bei teilkollabiertem Netz; nach Ereignis-Bestätigung drosselt der Server Trigger-Uploads im Gebiet — das Netz gehört der Zustellung. **Degradations-Modus (verbessert, aus Risikotabelle in die Pipeline gezogen):** Fällt eine Stationszone plötzlich aus (Nahfeld-Strom/Netz tot), wird der Ausfall selbst als Ereignis-Signal gewertet und die Zone automatisch stärker auf P0b/Beobachtungspfad umgewichtet — Stille ist nie „keine Beben". Gratis-MVP = eine Always-Free-VM = offen kommunizierter Single Point of Failure (Totmann + Statusseite); aktiv-aktiv Nach-MVP.

### 3.9 Lernschleife

Jeder P0-Alarm wird automatisch gegen die P2-Wahrheit benotet (echt/falsch/verpasst); Quoten justieren Schwellen. Nach jedem echten Ereignis: öffentlicher Ereignisbericht (Zeitachse Bruchbeginn → Pick → Publish → mediane Zustellung, Schätzgüte, Fehlerursachen); Fehlalarme bekommen ein öffentliches Post-Mortem. **Verbessert:** Der wöchentliche Schatten-Replay gegen den historischen Korpus (§8.6) speist dieselbe Benotung — Erkennungs-Drift wird auch in bebenarmen Phasen bemerkt.

---

## 4. Latenzbudget (Sekunden-Kette mit Eigentümern)

| Kettenglied | Budget | Maßnahme |
|---|---|---|
| Bruchbeginn → P-Welle an Stationen | Physik (2–10 s, dichteabhängig) | Abdeckungsanalyse (§14); Community-Stationen in Lücken (Stufe 3) |
| Station → Server (SeedLink) | Profi median < 1,5 s; Consumer mehrere Sekunden | Record-Länge als hartes Gewichtungskriterium; langsame Stationen zählen weniger |
| Pick + Assoziation + Entscheidung | < 1,5 s | Heißer Pfad ohne DB-Zugriff; Aufmerksamkeits-Modus wärmt vor |
| Publish → FCM/Socket-Edge | < 0,5 s | Kleine Geo-Zellen (geringer Fanout pro Topic), vorgewärmte Verbindungen |
| Zustellung ans Gerät | median < 1,5 s, Worst-Case 3 s | Dualkanal-Wettrennen, gemessen pro Gerät/Kanal |
| Anzeige + Ton | < 0,3 s | Autarker Payload, AlertActivity vorgeladen |
| **Systemanteil gesamt** | **median < 5 s; Worst-Case-Summe ~6,8 s** | Ende-zu-Ende-Kanarien; Histogramme als Betriebs-KPI |

**Verbessert:**
- Jedes Glied wird als **p50/p95/p99** gemessen und veröffentlicht, nicht nur als Median — die Warnzeit des langsamsten Fünftels entscheidet über Leben, nicht der Durchschnitt.
- **FCM-Fanout als Latenzrisiko explizit:** Zellen so dimensionieren, dass die Abonnentenzahl pro Topic einen im Lasttest belegten Grenzwert nicht übersteigt; Lasttest mit realistischer Abonnentenzahl ist Abnahmekriterium (§11.1); Socket-Kanal läuft immer als paralleles Wettrennen.
- **Sendereihenfolge nach Restzeit:** Socket-Zustellung sortiert nach erwarteter S-Ankunft — wer am wenigsten Zeit hat, zuerst (Publisher-Verantwortung).

---

## 5. Alarm-Payload & Kryptographie

### 5.1 Payload v1 (implementiert, bleibt gültig)

String-Map: `v, id, ver, state, tier, test, origin_ts, lat, lon, depth_km, mag, mag_hi, src, issued_ts` + `sig`.
Kanonische Bytes: alle Felder außer `sig`, Schlüssel alphabetisch, `key=value` mit `\n` verbunden (kein Schluss-Newline), UTF-8. Signatur: Ed25519 über diese Bytes, Base64. Zeitstempel in Epoch-Millisekunden. `ver` = Revisionszähler (Erst-Alarm = 1). Größe < 1 KB, autark (kein Server-Roundtrip zur Anzeige).

### 5.2 Payload v2 (verbessert — abwärtskompatible Erweiterung)

Neue Pflichtfelder in `v=2`; v1-Clients ignorieren unbekannte Schlüssel nicht stillschweigend, sondern werden per Mindestversions-Mechanik (§6.8) migriert:

| Feld | Zweck |
|---|---|
| `exp` | **Ablauf (Epoch ms): Anti-Replay.** Ein Payload nach `exp` wird nie mehr als Alarm angezeigt (nur noch als Historie). Schließt die Ist-Lücke, dass ein einmal signierter Alarm zeitlich unbegrenzt wiedereinspielbar ist. Startwerte: P0/T0 15 min, P1/P2 60 min ab `issued_ts`; Werte pro Tier konfiguriert. |
| `kid` | **Schlüssel-ID** des Signierschlüssels — Voraussetzung für Rotation ohne App-Update (§5.3). |
| `radius_km` | Server-Hinweis zum betroffenen Radius (PLUM-/Modellpfad); Client bleibt letzte Instanz der Ortsentscheidung. |
| `path` | Entscheidungspfad (`model`, `plum`, `catalog`, `citizen`) — macht das Produktversprechen „Entscheidungspfad sichtbar" maschinenlesbar. |
| `lang_key` | Schlüssel der Handlungszeile (Client rendert lokalisiert; kein Text im Payload nötig). |

Regeln: Felder bleiben Strings; kanonische Bytes und Signaturverfahren unverändert (nur mehr Felder). `test=1`-Payloads tragen zusätzlich verpflichtend eine eigene, unverwechselbare Kennzeichnung im Client (§6.6).

### 5.3 Schlüsselhierarchie & Rotation (verbessert — vorher nur Betriebsprosa)

- **Zweistufig:** Ein **Offline-Root-Schlüssel** (nie auf der VM) signiert ein **Schlüsselmanifest** (Liste gültiger `kid` → Public Key, mit Gültigkeitsfenstern und Revocation-Liste). Der **Online-Alarmschlüssel** (im getrennten Secret-Store/KMS, eigenes Zugriffskonto — nie als Datei neben der App) signiert Alarme.
- Clients betten den Root-Public-Key ein und holen/cachen das Manifest über einen vom Alarmkanal getrennten Weg (Statusseite/CDN). Kompromittierter Alarmschlüssel ⇒ Manifest-Update, kein App-Store-Rollout. Rotation wird im Game-Day real geübt (§11.9).
- Web-App/Android verifizieren **vor jeder Anzeige**; fehlende Crypto-Fähigkeit des Browsers ⇒ verwerfen + ehrlicher Hinweis (implementiert in der Web-App; Regel gilt für alle Clients).

---

## 6. Zustellung & Client-Verhalten (Android-first)

### 6.1 Kanäle

- **P0-Broadcast statt Einzelzustellung:** ein Publish an regionale FCM-Topics (Geo-Rasterzellen; Geräte abonnieren die Zellen ihrer Orte). Serverseitige Regel-Engine bleibt für P1/P2, Inbox, Audit; P0 wird nachträglich protokolliert.
- **Duale Kanäle mit Wettrennen:** FCM-High-Priority **und** persistente Socket-Verbindung des Foreground-Service; Client nimmt das Erste, dedupliziert über `(id, ver)`, meldet Empfangszeiten beider Kanäle zurück (speist §4).
- **Dritter Kanal Pushy (GMS-unabhängig, Gratis-Ebene):** wird aktiviert, sobald die OEM-Testmatrix (§11.5) Zustellprobleme zeigt — Antwort auf Xiaomi/Oppo/Huawei-Drosselung; von VolcanoDiscovery produktiv vorgeführt.

### 6.2 Client-Regelauswertung (verbessert — Validierungsreihenfolge normativ)

Der Client entscheidet lokal, ob seine Schwellen gerissen sind. Verpflichtende Prüfreihenfolge vor jeder Anzeige:
1. **Signatur** gegen Manifest-Schlüssel (`kid`) — ungültig ⇒ verwerfen + stiller Zähler im Schutzstatus.
2. **Frische:** `now ≤ exp` (v2) bzw. konfiguriertes Fenster ab `issued_ts` (v1-Übergang) — abgelaufen ⇒ nur Historie.
3. **Versions-Monotonie pro `id`:** `ver` ≤ zuletzt gesehene Version ⇒ verwerfen (schließt Downgrade-Replay; im Ist-Stand ungeprüft).
4. **Tier-Monotonie pro `id`:** niedrigeres Tier ersetzt nie ein höheres in der Anzeige.
5. **Ortsauswertung:** pro gespeichertem Ort Hypozentraldistanz, IPE-Intensität (mit Vs30-Klasse), Countdown (§2.5); Schwellenvergleich (Intensität/Magnitude/Distanz je Ort).
6. **Dedupe/Eskalation:** Erst-Alarm (`ver==1` oder erste gesehene Version) öffnet die Alarmansicht; spätere Versionen aktualisieren sie in place (implementierte Web-App-Regel wird für alle Clients normativ).

### 6.3 Alarm-Erlebnis (funktional)

- `USE_FULL_SCREEN_INTENT` + eigene AlertActivity durchbricht den Lockscreen; Ton über eigenen Vordergrund-Dienst, funktioniert bei Lautlos, sofern DND-Bypass freigegeben.
- **Signaturen pro Stufe:** durchdringender Ton + Vollbild nur für P0/T0 über der Nutzerschwelle; Bestätigungen dezent; **Entwarnung lautlos**; Testalarme mit unverwechselbarer TEST-Kennzeichnung (eigener Ton/Wortlaut), nie identisch zur Ernstkette.
- Anzeige: Countdown, erwartete Intensität ± Spanne am Ort, eine Handlungszeile („Ducken · Schützen · Halten"), Konfidenz/Stufe/Quelle/Entscheidungspfad; Kennzahlen Magnitude (mit Herkunft „Proxy" vs. „instrumentell"), Distanz, Warnzeit.
- **Barrierefreiheit ab MVP (funktional):** markante Vibrationsmuster + Blitzlicht-Signal (Gehörlose), Sprachausgabe (TTS) in gewählter Sprache, Drop-Cover-Hold-On-Piktogramm neben der Handlungszeile (Lese-/Sprachbarriere), Bedeutungen nie über einen einzigen Sinneskanal codiert, Content-Descriptions auf Statuselementen. *(Konkrete Farb-/Theme-Festlegungen: ausgegliedert, keine Stilvorgaben in dieser Spec.)*
- **Verbessert:** Beim Alarm hält der Client CPU-Wachzustand bis Countdown-Ende (partial wakelock mit Obergrenze), damit der Sekundenticker unter Doze nicht einfriert; nach Countdown-Ende automatischer Wechsel ins Offline-Sicherheitspaket (§6.5).

### 6.4 Mehrsprachigkeit

Volle i18n-Architektur ab Tag 1: externalisierte Strings, automatische Geräte-Sprach-Erkennung mit manueller Übersteuerung, Sprache unabhängig vom Ort, RTL-Unterstützung. Sprachen sind Daten, nicht Code.
- **MVP:** Türkisch (Standard), Englisch, Kurdisch (Kurmancî), Arabisch — die real anwesenden Sprachgruppen der Erdbebenregionen.
- **Stufe 2:** Deutsch, Russisch; danach Persisch, Ukrainisch. Priorisierung nach realer Präsenz in den Erdbebenzonen, gegen aktuelle Aufenthalts-/Tourismusdaten geprüft.
- Lebensrettende Texte (Alarm, Handlungszeilen) muttersprachlich geprüft, nie nur maschinell. **Verbessert:** Der Prüfstatus jeder Sprache (maschinell / muttersprachlich geprüft / veraltet gegen String-Änderung) wird pro Release maschinell mitgeführt; eine ungeprüfte Änderung an einem lebensrettenden String blockiert den Release der betroffenen Sprache (Fallback auf letzte geprüfte Fassung + EN).
- **TTS-Fallback:** `isLanguageAvailable()` zur Laufzeit; fehlt die Engine (häufig bei Kurmancî), spielt die App muttersprachlich vorproduzierte Audio-Clips der Handlungszeilen statt stumm zu bleiben.

### 6.5 Offline-Sicherheitspaket (nach dem Beben)

Pro Nutzerort bei Einrichtung geladen, vollständig offline nutzbar: nächstgelegene offizielle Sammelplätze (nur nach bestätigtem Datenzugang — sonst startet das Paket ohne Sammelplatz-Ebene, keine inoffiziellen Daten), Notruf 112, Nach-dem-Beben-Hinweise („Gas prüfen, Nachbeben erwarten"), Familien-Treffpunkt-Notiz. Nach der Alarmanzeige wechselt die App automatisch hierhin. **Verbessert:** Das Paket trägt Versionsstand + Erstellungsdatum sichtbar; ein über Schwellwert veraltetes Paket markiert sich selbst als „prüfen" beim nächsten Online-Kontakt (nie stilles Veralten von Sammelplatzdaten).

### 6.6 Schutzstatus & Probealarm

Checkliste aller Zustellvoraussetzungen (Benachrichtigungen, Vollbild-Freigabe, Akku-Ausnahme, DND-Bypass, Boot-Neustart, Sensorrechte) mit Ein-Tipp-Fix; Zustand über Symbol **und** Text (nie ein Kanal allein). **Probealarm-Knopf:** Ende-zu-Ende bis zum Ton, mit unverwechselbarer TEST-Signatur — nie identisch zur P0-Kette (Cry-Wolf-Schutz). **Hersteller-Härtung:** aggressive Battery-Killer der Türkei-OEMs werden erkannt, gerätespezifische Anleitung (Autostart-Freigabe). Grundsatz: Ein Gerät, das keine Alarme empfangen kann, weiß das und zeigt es. **Verbessert:** Der Schutzstatus zählt auch stille Fehler (verworfene Signaturen, abgelaufene Payloads, Watchdog-Neustarts) und zeigt sie als Diagnosewert — Zustellprobleme werden sichtbar, bevor der Ernstfall sie beweist.

### 6.7 Android-Plattform-Realität (wird aktiv behandelt, nicht vorausgesetzt)

- **`USE_FULL_SCREEN_INTENT` (Android 14+):** `canUseFullScreenIntent()` zur Laufzeit prüfen, Ein-Tipp-Deeplink zur Freigabe; Fallback High-Priority-Notification + eigener Alarmton. Play-Store-Richtlinie/Kategorie vor Implementierung geklärt (§14).
- **Permission-Auto-Revoke (Android 11+):** periodischer WorkManager-Selbstcheck erkennt Entzug bei ruhender App und holt den Nutzer aktiv zurück; „App nicht pausieren" ist Teil der Schutzstatus-Fixes.
- **`POST_NOTIFICATIONS` (Android 13+):** einmalige harte Anfrage nur im geführten First-Run-Flow (§6.8).
- **Foreground-Service-Typen (Android 14+):** deklarierte Typen für Socket-/Sensor-Dienst (Festlegung §14), sonst schlägt der Start fehl.
- **`HIGH_SAMPLING_RATE_SENSORS` (Android 12+):** bewusst entschieden und im Schutzstatus mitgeprüft (§2.3).

### 6.8 Onboarding, Watchdog, Update, Verpasst

- **First-Run-Onboarding:** definierte Rechte-Reihenfolge mit Begründungsscreen vor jedem Systemdialog, in gewählter Sprache: Benachrichtigungen → Vollbild → Akku-Ausnahme → Standort (optional) → Sensoren → DND.
- **Geräte-Watchdog:** WorkManager-/AlarmManager-Heartbeat startet einen toten Vordergrund-Dienst neu (`START_STICKY` reicht nicht); Abstürze erscheinen im Schutzstatus.
- **Erzwungenes Update:** Server erkennt veraltete Clients beim Verbindungsaufbau (Protokoll-/Schlüssel-/Payloadversion) → blockierender „Update erforderlich"-Hinweis; alte Formate befristet abwärtskompatibel — kein stiller Fehl-Parse, besonders bei Sideload.
- **Verpasst ist sichtbar:** Fallen beide Kanäle aus, zeigt der nächste Start versäumte Ereignisse als „verpasst — Gerät war nicht erreichbar"; Lücken werden nie still aufgefüllt.
- **Geräte-Export (kontofrei):** Ortsliste + Schwellen als QR-Code/Datei exportier-/importierbar — gegen Totalverlust bei Gerätewechsel/-verlust.

---

## 7. Web-App (Funktionsspiegel, implementiert)

Eine eigenständige `file://`-fähige HTML-Datei; kein Persistieren sensibler Daten, kein Upload, kein Tracking.
- **Zwei Alarmquellen:** lokaler Testszenario-Injektor (ohne Netz) und WebSocket-Servermodus. Im Servermodus ist die Ed25519-Verifikation **Pflicht vor Anzeige** (kanonische Bytes byte-identisch zum Server; angesagte `pub_key` aus dem `hello` wird ignoriert, nie zur Prüfung verwendet); Crypto nicht verfügbar ⇒ verwerfen + ehrlicher Hinweis.
- Fachlogik identisch zum Client/Backend: Haversine (R = 6371), Countdown vS 3,5 / vP 6,0, Intensitätsschätzung + Schutzhinweis, Zustände `READY · ATTENTION · ALARM_P0 · CONFIRMED_P2 · DISTURBANCE_DISCARDED`; Erst-Alarm öffnet das Overlay, Folge-Versionen aktualisieren es (Eskalation P0→P2 ohne Neu-Öffnen); Sequenzliste; Ereignisbericht mit Zeitachse und Teilen-Funktion; Live-Seismogramm über `devicemotion` (iOS-Erlaubnis-Flow; Sensor nur Anzeige, nie Upload).
- **Verbessert:** (a) Die Web-App übernimmt die normative Validierungsreihenfolge §6.2 vollständig — zusätzlich zur Signatur also Frische-/`exp`-Prüfung und `ver`-Monotonie pro `id`; (b) Fachlogik-Update auf Hypozentraldistanz + IPE (§2.5), damit Web, Android und Backend dieselben Zahlen zeigen; (c) die sechs Städte bleiben, ergänzt um frei eingebbare Koordinaten (die Kernfunktion hängt nicht an einer Auswahlliste).

---

## 8. Backend-Architektur & Betrieb

### 8.1 Dienste-Schnitt (Isolationsregel)

**Alarm-Pfad** (latenzkritisch, darf nie degradieren): Trigger-Gateway (Attestierung, Signatur, Rate-Limit; zustandslos) · Stations-Ingest (SeedLink + Picker + Stationsgesundheit) · Katalog-Ingest (ein Adapter je Quelle; implementiert: EMSC/USGS/AFAD/GDACS) · Fusion/Event-Manager (beide Pfade, Zustandsmaschine, Sequenz-Modus) · Publisher (signiert, Topics + Socket, Restzeit-Sortierung).
**Browse-Pfad** (darf degradieren): API-Dienst (Orte, Profile, Inbox, Karte, Historie), Admin-Dienst; Lagekarte im Großereignis als statischer CDN-Snapshot.
**Ressourcen-Isolation auf der einen Gratis-VM:** cgroup-/Container-Limits mit reservierter CPU/IO für Fusion/Publisher; getrennte DB-Connection-Pools mit garantiertem Alarm-Kontingent — eine Trigger-/Meldeflut darf den Lebensrettungspfad nicht aushungern.

### 8.2 Verlässlichkeitsmechanik

- **Genau ein Entscheider pro Region:** Fusion im MVP als Einzelinstanz, aber idempotent und leader-election-fähig ausgelegt — Heißstandby später zuschaltbar ohne Umbau.
- **Replay als Grundeigenschaft:** alle Eingänge zuerst in den append-only Strom; Replay-Harness = derselbe Fusion-Code über historischem Strom (implementiert für P0a/P0b mit Fixtures). **Verbessert:** Replays sind deterministisch bis auf Hash — jeder Abnahmelauf protokolliert einen Ergebnis-Hash; ein abweichender Hash bei gleichem Input ist ein Regressionsbefund.
- **Rohdaten-TTL** (z. B. 90 Tage; ereignisbezogene Daten unbegrenzt); Trigger-Positionen nur als Raster.
- **SLO-Ehrlichkeit:** ≥ 99,95 % gilt erst ab Heißstandby; der Gratis-MVP fährt Best-Effort mit offen ausgewiesener Verfügbarkeit und misst das Fehlerbudget bereits, um den Standby-Sprung zu begründen.
- **Backups off-instance:** periodische komprimierte DB-/Strom-Dumps auf Gratis-Objektspeicher (OCI Object Storage/Cloudflare R2) — VM-Verlust ist nie Totalverlust. **Verbessert:** Restore wird periodisch real geprobt (ein ungetestetes Backup ist keins); Proben-Ergebnis auf der Statusseite.

### 8.3 Speicher & Stream

PostgreSQL/PostGIS als Wahrheit, Redis für Live-Zähler, Redis Streams als replayfähiger Ereignisstrom auf derselben VM (implementiert: `redis_stream.py`; In-Memory für Tests). Kafka-Klasse erst bei Skalierung. **Verbessert:** Der Strom wird zusätzlich periodisch als komprimiertes NDJSON-Archiv in den Objektspeicher ausgelagert — Replay-Korpus und Backup in einem, entkoppelt die 90-Tage-TTL von der VM-Platte.

### 8.4 Konfiguration mit Leitplanken

Alle Schwellen (Cluster-Signifikanz, Intensitätsgrenzen, PLUM-Grenzwerte, NEAMTWS-Matrix, IPE-/Magnituden-Koeffizienten, Ablauffenster, Hysteresen) sind **versionierte Konfiguration mit Audit** und gestuftem Rollout (Schattenbetrieb → Region → global), automatischem Rollback bei kippenden Kanarien. Jeder Fehlalarm ist auf eine benennbare Konfigurationsversion zurückführbar. **Verbessert:** Konfigurationsartefakte sind signiert (gleiche Schlüsselhierarchie §5.3) — eine kompromittierte VM kann nicht unbemerkt Schwellen verstellen.

### 8.5 Monitoring, Totmann, Kill-Switch, Moderation

- Gemessen pro Quelle (Frische/Latenz/Ausfall), pro Station (Rauschen/Picks/Record-Latenz), pro Region (Online-Dichte → P0b-Status), pro Latenz-Kettenglied, pro Zustellkanal; KPIs: Fehlalarm-/Verpasst-Quote, gewonnene Warnzeit, Aufmerksamkeits-Präzision (§3.2).
- **Totmann-Schaltung:** ausbleibender Fusion-Herzschlag alarmiert sofort (im Solo-Betrieb: Push/Anruf aufs Betreiber-Handy) — Stille wird nie als „keine Beben" gedeutet.
- **Statusseite** auf getrennter Infrastruktur (statisch, CDN, anderer Anbieter).
- **Kill-Switch pro Ebene und Region** (inkl. eigenständig abschaltbarer Bürgermeldungs-Ebene pro Gefahr/Region); jeder Eingriff auditiert; Break-Glass unabhängig vom Adminportal; aktive Abschaltungen öffentlich. **Zwei-Personen-Freigabe** bzw. zweiter Faktor im Solo-Betrieb + sofortige Alarmierung eines Zweitkanals — ein einzelner kompromittierter Zugang kann die P0-Ebene nicht unauffällig stummschalten.
- **Moderation:** Admin sperrt einzelne Melder; Sperren/Herabstufungen auditiert; Skalierung über Vertrauensstufen (hohe Reputation = Mit-Moderationsrechte) statt Admin-Einzelperson.

### 8.6 Erkennungs-Kanarien & Game Days

Zusätzlich zu Latenz-Kanarien läuft die Produktionsversion regelmäßig (z. B. wöchentlich) im Schatten gegen den historischen Wellenform-/Ereigniskorpus — Konfigurations-/Code-Drift der Erkennungsgüte fällt auch in bebenarmen Phasen auf. Game Days (synthetisches Großereignis, Quellausfall, Kill-Switch, Schlüsselrotation) so oft wie mit realer Kapazität machbar — ehrlich als wachsend-mit-Ressourcen benannt.

---

## 9. Datenmodell (Kernentitäten)

`Device` (Attestierungs-/Reputationsstatus, Uhr-Unsicherheit) · `Place` (Koordinaten, Küsten-Attribut, **Vs30-Klasse**, Sicherheitspaket-Version) · `AlertProfile` (Schwellen je Ort) · `Trigger` · `StationPick` · `CitizenReport` (Melder-Ref, Gefahrentyp, Rasterposition, Zeit, C0/C1, Status) · `Reporter` (Beta-Reputation: Bestätigt/Widerlegt-Zähler, Verfallsdatum, Sperrstatus) · `SourceEvent` (roh, je Quelle) · `CanonicalEvent` (Versionshistorie + Auslöse-Begründung + Entscheidungspfad + Verwerfungen) · `Sequence` (Hauptbeben-Referenz, Schwellenkurve) · `Delivery` (Kanal, Zeitstempel, Ack) · `SourceHealth` · `LatencyProbe` · `AuditLog` · `ConfigVersion` (signiert, §8.4) · `KeyManifest` (§5.3).
Stufe 2 ergänzt: `User` (Konten/Sync), `SosSignal`, `EmergencyContact`, `HelperOptIn`.
Invarianten: Rohmeldungen und kanonische Ereignisse strikt getrennt; kein Personenbezug in Sensordaten; Positionen von Triggern/Meldungen nur als Raster.

---

## 10. Datenschutz, Recht, Rollen

- **MVP ohne Konten:** Orte/Schwellen gerätelokal; Server kennt nur anonyme Geräte-ID + Atteststatus. Konten + Multi-Device-Sync ab Stufe 2 (Login-Entscheidung vor Stufe 2; Migrationspfad gerätelokal → Konto inkl. Reputation mitgedacht).
- **Rechtsrahmen:** KVKK (Türkei) + DSGVO (EU-Nutzer). **KVKK Art. 9** (grenzüberschreitende Übermittlung): Hosting außerhalb der Türkei löst die Pflicht aus — Einwilligungstext/zulässiger Mechanismus **vor Launch**. Zulässigkeit einer privaten Warn-App + Abgrenzung von amtlichen Warnungen (kein Cell-Broadcast-Nachahmen) als eigener Prüfpunkt.
- **Haftung:** AGB mit sichtbarer Best-Effort-Klausel (kein garantierter Alarm) in App und Store-Listing; haftungsbegrenzende Rechtsform vor Live-Betrieb (Einzelbetreiber trüge unbegrenztes Personenschadensrisiko).
- **Datenminimierung:** präziser Standort optional (manuelle Orte erfüllen die Kernfunktion); Trigger-/Meldepositionen vergröbert; getrennte Schutzbereiche für Push-Tokens, Geräte- und Ortsdaten; Export und Löschung. **Verbessert:** ein maschinenlesbares Datenverzeichnis (Feld → Zweck → Rechtsgrundlage → TTL) wird mit jeder Schemaänderung fortgeschrieben — Datenschutz-Drift wird damit review-pflichtig statt schleichend.
- **Ausnahme präziser Standort — nur SOS (Stufe 2):** ausschließlich bei expliziter SOS-Aktion, Einwilligung im Moment, definierte Empfänger, kein Bewegungsprofil.
- **Rollen:** Nutzer (eigene Orte/Regeln/Historie) · Support (nur pseudonymisierte Diagnostik) · Admin (Quellen, Ereignisprüfung, Konfiguration, Kill-Switch — auditiert).

---

## 11. MVP-Definition & Abnahmekriterien

**Produktversprechen des MVP:** Ein Android-Nutzer in der Türkei erhält bei einem relevanten Erdbeben die schnellstmögliche, ehrlich gestufte Warnung mit Countdown — ab Tag 1, ohne dass das Produkt eine Nutzerbasis voraussetzt — und hat nach dem Beben die wichtigsten Informationen offline zur Hand.

**Drin:** P0a-Kern (SeedLink, Picker, Assoziation, Pd/τc-Magnitude, PLUM-Basis) · P1/P2-Kataloge (implementiert) · P0b nur Sammeln/Schatten · lokale Selbstdetektion · Tsunami T0/T1 (Entwarnung über T1-Updates) · Bürgermeldungen + Reputation (Erdbeben nur Bestätigung; Flut/Sturm/Feuer C0/C1) · Zustellkette komplett (FCM-Topics + Socket, signierte Payloads v2, Full-Screen, Live-Countdown, Stufen-Signaturen, Entwarnung) · Offline-Sicherheitspaket · Sequenz-Modus Basis · Orte/Schwellen gerätelokal · Schutzstatus + Probealarm (TEST-Signatur) · Barrierefreiheits-Basis (funktional) · Geräte-Export · Inbox/Karte/Systemstatus (getrennte Notification-Kanäle je Stufe; C0/C1 nachts stummschaltbar ohne P0/T0 zu gefährden) · Betriebskern (Pfad-Isolation, Replay, Kanarien, Totmann, Kill-Switch, Statusseite, SLO-Messung) · i18n TR/EN/KU/AR.

**Ausdrücklich nicht drin:** iOS · Konten/Sync · P0b-Alarmierung · Finite-Fault-Tracker · Tsunami-ETA-Raster · amtliche Unwetter-/Flut-/Waldbrandquellen (nur Bürgermeldungen + GDACS-gekennzeichnet) · SOS (Stufe 2) · BLE-Mesh (Stufe 2) · eigene Stationen · EEW-Partnerlizenz (nur evaluieren) · Chat/Social · Sprachwelle 2 · Adminportal-Komfort.

**Messbare Abnahmekriterien (jedes mit Nachweisverfahren):**
1. **Latenzbudget belegt:** jedes Kettenglied im Budget (p50/p95 gemessen); Systemanteil median < 5 s; Katalog → Push median < 5 s nach Quellenpublikation. Nachweis: Kanarien-Histogramme über 30 Tage.
2. **Replay-Pflichttests deterministisch:** Kahramanmaraş 2023 (Sequenz-Modus greift; Mehrereignis-Assoziation trennt M7,8/M7,5; keine Doppelzustellung pro `(id, ver)`; PLUM löst trotz Magnituden-Unterschätzung aus) und Samos 2020 (T0 für Küstenorte) laufen durch den Produktions-Fusionscode; Silvester-Feuerwerk als Standard-Fehlalarmtest. **Verbessert:** identischer Ergebnis-Hash über 3 Läufe (§8.2).
3. **Fehlalarm-Disziplin:** im 30-Tage-Pilot kein unbestätigter Vollalarm ohne fristgerechte automatische Hoch-/Herabstufung; jede Warnung nennt Stufe/Quelle/Konfidenz/Pfad.
4. **Ehrlichkeit sichtbar:** Quellausfall ⇒ nachweislich Statusanzeige statt stiller Lücke; Abschaltungen öffentlich.
5. **Härtetest Zustellung:** Alarm erreicht Testgeräte bei Lautlos + DND + Doze (mit Freigaben) und nach Neustart ohne App-Öffnung — Testmatrix der fünf meistverbreiteten Android-OEMs der Türkei.
6. **Offline-Nachweis:** Sicherheitspaket vollständig im Flugmodus nutzbar.
7. **Bürgermeldung/Reputation belegt:** Erstmelder propagiert nicht; vertrauenswürdige Einzelmeldung ⇒ nur C0; nachweislich diverser Cluster ⇒ C1-Push „unbestätigt"; Erdbeben-Meldung löst nie allein aus; simulierter Sybil-/Reinstall-Angriff wird herabgewichtet/limitiert/ausgeschlossen; Kill-Switch + Admin-Sperre greifen.
8. **Produkt-/Vertrauens-KPIs:** Deinstallationsrate nach Alarm unter Schwelle; aktive Melder pro Pilotregion über Zielwert; dokumentierte Partnerschafts-/Seeding-Fortschritte.
9. **Sicherheits-Nachweise:** Signierschlüssel getrennt vom App-Host, Rotation im Game-Day real durchgeführt (Manifest-Weg, ohne App-Update); Break-Glass verlangt zweiten Faktor/zweite Person; gefälschter Stations-Pick löst ohne Korroboration keinen P0a-Alarm aus. **Verbessert zusätzlich:** abgelaufener Payload (`exp`) und `ver`-Downgrade werden vom Client nachweislich verworfen.
10. **Akku-/Ressourcenbudget:** Energieverbrauch (%/Tag) auf Low-End-Referenzgerät unter Schwelle bei aktivem Dienst; Offline-Paketgröße im Budget; Full-Screen-Fallback nachweislich funktionsfähig; Auto-Revoke wird vom Selbstcheck erkannt.
11. **Verbessert — Zahlenkonsistenz der Clients:** Android, Web-App und Backend liefern für dieselben Payloads identische Countdown-/Intensitätswerte (gemeinsamer Testvektor-Satz als Fixture) — verhindert Drift der dreifach implementierten Fachlogik.

---

## 12. Ausbaustufen

### 12.1 Stufe 2

SOS/Hilferuf (§12.2) · P0b-Alarmierung pro Region nach Dichte-Freischaltung · Finite-Fault-Tracker · Konten + Sync · Familien-Check-in („Mir geht's gut" mit einem Tipp) · Tsunami-ETA-Raster (GEBCO) · iOS (Empfang zuerst; Critical-Alert-Entitlement beantragen) · Sprachwelle DE/RU, dann FA/UK · Nachbeben-Hinweiskarte · Übungsmodus (markierter Drill) · Wear-OS-Relay (haptischer Alarm) · Offline-Regionalkarte (Evakuierung ohne Netz) · regionales S-Geschwindigkeitsmodell · Vorbereitungs-/Aufklärungsinhalte zwischen Ereignissen · BLE-Mesh Stufe 2a (§12.5).

### 12.2 SOS/Hilferuf (Stufe 2, Kern gratis)

Explizite Aktion sendet präzisen Standort + optionale Kurznachricht an definierte Empfänger: hinterlegte **Notfallkontakte** (Push + SMS-Fallback) und opt-in **Helfer-Nutzer in der Nähe**. **Behörden sind ausdrücklich nicht Empfänger** ohne formale Integration — wird nie suggeriert. Mehrkanal-Resilienz: Daten → SMS → BLE-Mesh. Missbrauchsschutz: identitätsgebunden, attestiert, ratenlimitiert, reputationsgebunden, sperrbar. **Helfer-Rolle gehärtet (Stalking-/Luring-Vektor):** höhere Verifikationsstufe als Basis-Attest; Sender steuert granular, wer benachrichtigt wird (kein Auto-Vollzugriff für Umkreis-Helfer), mit Ausschlussmöglichkeit; wiederholte SOS neuer Accounts vor Helfer-Zustellung ratenlimitiert (verhindert das Locken von Helfern an einen Angreifer-Ort). BLE-SOS Ende-zu-Ende verschlüsselt: Relay-Knoten sehen nie Klartext-Notstandort. Monetarisierung: SOS-Kern gratis; „Pro" nur Komfort (unbegrenzte Kontakte, Live-Standort-Teilen während eines Ereignisses, Priorität).

### 12.3 GDACS-Multihazard (Adapter implementiert)

GDACS (frei, ohne Key, GeoJSON) liefert Tsunami/Flut/Vulkan/Sturm/Waldbrand als **gekennzeichnete internationale Quelle** — macht die App vom Erdbeben-Melder zum Disaster-Alert, ohne die Quellenfreigabe-Regel zu verletzen. Zustellung als eigene, klar etikettierte Ebene mit eigenem Kill-Switch; amtliche türkische Quellen (MGM/EFFIS/GloFAS) je erst nach formaler Freigabe (Stufe 3).

### 12.4 Partnerschaftsschiene (kritischer Pfad)

Ohne AFAD/KOERI-Echtzeitzugang bleibt P0a auf wenige GEOFON-Stationen beschränkt. Sofort: KOERI-SeedLink-Port technisch testen (`slinktool -Q eida.koeri.boun.edu.tr:18000`, Minutenaufwand); **verbessert zusätzlich:** ORFEUS/EIDA- und GEOFON-SeedLink-Kataloge systematisch auf frei zugängliche türkische Echtzeitstationen prüfen (Nebenfund der Recherche — potenziell teilweiser Abdeckungsgewinn ohne formales Abkommen). Parallel AFAD/Universitäten mit echtem Tauschangebot: unsere Phone-Trigger-/Dichtedaten für die Wissenschaft gegen TU/KO-Echtzeitzugang. Wissenschaftlicher Beirat + offengelegte Methodik als Legitimitätskapital. Raspberry-Shake-Echtzeit als Zusatzquelle ab Tag 1 prüfen (nie allein auslösend, §3.7).

### 12.5 Ernstfall-BLE-Mesh (Stufe 2a/b/c — Design steht, kein Code)

Optionales, ernstfall-getriggertes, signiertes BLE-Broadcast-Relay: Geräte, die den Alarm noch online empfingen, fluten ihn per Extended Advertising (Bluetooth 5) an netzlose Nachbarn weiter. Kernpunkte:
- **Architektur:** Advertising-Broadcast ohne Verbindungsaufbau + Store-and-Forward-Flooding (kein SIG-Mesh-Profil); Komponenten TriggerManager, MeshForegroundService (`connectedDevice`), Advertiser, Scanner, RelayCache, Verifier.
- **Paket:** kompakte Binärform (~110–130 B): magic/version, msg_type, hop_ttl, flags, msg_id (Hash(id+ver)), origin_ms, lat/lon float32, depth, mag/mag_hi, radius_hint, **Ed25519-Signatur 64 B** — vom **Server** separat über die kompakten Bytes signiert (eigene Signatur-Domäne neben JSON-`canonical_bytes`, dokumentiert und versioniert). `hop_ttl`/`flags` außerhalb der Signatur (ändern sich beim Relay; Manipulation kostet einen Hop, nie Authentizität).
- **Relay-Regeln:** verify-before-anything → Dedupe (msg_id) → Ablauffenster (EEW ~15 min; **verbessert:** Fenster = `exp` aus §5.2, eine Wahrheit statt zweier Konstanten) → anzeigen falls neu → hop−1 → befristet re-advertisen. Hop-Limit 7.
- **Verbessert — Flutungs-Effizienz:** Re-Advertising mit zufälligem Jitter und Trickle-artiger Unterdrückung (wer dasselbe Paket bereits mehrfach von Nachbarn hört, sendet seltener) — senkt Kollisionen und Akku in dichten Menschenmengen, wo das Mesh am meisten leistet.
- **Trigger-Modell (zentrale Architekturentscheidung):** Standard **AUS** → ARMED (nur passive Signale) → AKTIV nur bei (a) bestätigtem Online-Alarm oder (b) Internet-Verlust **und** Starkbeben-Accelerometer zugleich; zeitbegrenzt 24–72 h, Auto-Aus. Advertising im Sekundenintervall (~−93 % Strom vs. 100 ms), Scan asymmetrisch/gebatcht, kein eigener Wakelock.
- **Android-Constraints:** Permissions API-31+-Satz mit `neverForLocation`; FGS-Typ + `FOREGROUND_SERVICE_CONNECTED_DEVICE`; Extended-Advertising-Fähigkeit zur Laufzeit prüfen; OEM-Unterdrückung ehrlich als Kompatibilitätshinweis.
- **SOS über Mesh (2b):** Geräteschlüssel-signiert, als „ungeprüfter Absender" markiert, Vertrauen aus Attest/Reputation/Rate-Limit; Legacy-Geräte-Fallback (Beacon + GATT) ebenfalls 2b; iOS-Interop/LoRa-Companion 2c.
- **Ehrliche Grenzen:** Reichweite 10–100 m/Hop, dichteabhängig — hilft in Ballungsräumen, nicht im leeren Land; OEM-Scan-Drosselung kann einzelne Geräte faktisch ausschließen; Feldtest der realen Weitergabe-Wahrscheinlichkeit ist offen.

### 12.6 Stufe 3

Eigene Community-Stationen (Raspberry-Shake-Klasse) an Partner-Standorten, platziert nach Abdeckungsanalyse, gleiche P0a-Pipeline · amtliche Unwetterquellen nach MGM-Freigabe (ohne Vereinbarung bleibt die Kategorie deaktiviert — kein Scraping als Produktionsstrategie) · Hochwasser/Waldbrand je mit eigener Quellenfreigabe (GloFAS/EFFIS als gekennzeichnete Quellen).

### 12.7 Wachstum & Vertrauens-Kaltstart

P0b und C1 brauchen kritische Masse. Hebel: **Netzwerk-Flywheel als ehrliche Story** („Jede Installation macht die Warnung für alle besser" — belegbar: 1–2 s P0b-Latenz ab ~300 aktiven Geräten pro Region) · virale Einladung, die zugleich Notfallkontakte anlegt · öffentliche Live-Abdeckungskarte (Netzdichte + erwartete Warnzeit pro Region, Tag/Nacht — zeigt ehrlich, wo die App trägt) · automatische öffentliche Ereignisberichte (§3.9) · Seeding-Partnerschaften mit NGOs/Communities · Positionierung „Sekunden-Frühwarnung statt Minuten-Meldung" (belegter Abstand zu allen vier Referenz-Katalog-Apps) · Bindungsinhalte zwischen Ereignissen.

---

## 13. Wesentliche Risiken und Gegenmaßnahmen

| Risiko | Gegenmaßnahme | Restrisiko (ehrlich) |
|---|---|---|
| Fehlalarm durch Rauschen/Störereignisse | Selbstkalibrierung, Signifikanz relativ zur Live-Dichte + FDR-Kontrolle, wellenartspezifischer Wellenfront-Check, P0a-Gegenprobe, Aufmerksamkeits-Modus, Lernschleife | seltene neuartige Störmuster; Post-Mortem-Pflicht |
| Fehlalarm durch Angriff | Zweipfad-Attestierung, signierte Uploads, Rate-Limits, Reputationsgewichtung, physikalischer Wellenfront-Check, signierte Payloads | staatlich-skalige Angreifer außerhalb des Modells |
| Gefälschte Stationsdaten | TLS, Consumer nie allein auslösend, plötzliche Fremd-Korrelation = Warnsignal, Manipulationserkennung eigener Stationen | kompromittiertes Profi-Netz |
| VM-Kompromittierung (Fälschung + Stummschaltung) | Schlüssel getrennt (KMS), Offline-Root + Manifest-Rotation (§5.3), signierte Konfiguration (§8.4), Zwei-Faktor-Break-Glass, Runbook | Zeitfenster bis Erkennung |
| Nahfeld-Telemetrieausfall durch das Ereignis | Degradations-Modus: Zonenausfall = Signal, Umgewichtung auf P0b/PLUM | Blindzone bleibt Physik |
| Trojanisierte Look-alike-APK nach Katastrophe | reproduzierbare Builds, veröffentlichte Signatur-Fingerprints, F-Droid-Reproducible, Sideload-Hinweis | Nutzer außerhalb offizieller Kanäle |
| Verpasstes Beben | zwei unabhängige Pfade, Mehrquellen-Ebenen, Totmann, Verpasst-KPI, Replay jedes Falls | Tag-1-Stationslücken (offen ausgewiesen) |
| Magnitude unterschätzt / Bruch länger als Punktquelle | asymmetrische Eskalation, PLUM-Pfad, Finite-Fault (Stufe 2) | frühe Untergrenzen-Anzeige bleibt |
| Nachbebensturm → Push-Sturm | Sequenz-Modus mit Abklingkurve, Mehrereignis-Assoziation, Durchbruchsregel für starke Nachbeben | — |
| Großereignis überlastet System | Pfad-Isolation (cgroups), CDN-Lagekarte, Trigger-Drosselung nach Bestätigung, Lasttests, Game Days | Single-VM-MVP offen kommuniziert |
| Infrastruktur selbst betroffen | Hosting außerhalb der Türkei, Statusseite auf Drittinfrastruktur, lokale Selbstdetektion + Offline-Paket als netzfreie letzte Linie | bis Heißstandby: SPOF |
| Oracle-Free-Tier-Risiken (Idle-Reclaim, Kontosperre, ARM-Kapazität) | Dauerlast dokumentiert prüfen; kalter Fallback-Anbieter vorab eingerichtet mit Umzugs-Runbook | Migrations-Downtime |
| OEM killt Hintergrunddienste | Schutzstatus-Härtung, Top-5-OEM-Testmatrix, FCM-High-Priority, Pushy-Kanal | einzelne ROM-Versionen |
| Quellenausfall/-verzögerung | Frische-Monitoring, offene Statusanzeige, Ebenen-Redundanz, keine Scheindaten | — |
| Nutzer deaktiviert Warnkette unbemerkt | Schutzstatus, Probealarm, Ein-Tipp-Fix, Auto-Revoke-Selbstcheck | bewusste Nutzerentscheidung |
| Vertrauensverlust nach Fehlalarm | gestufte Konfidenz, öffentliche Berichte/Post-Mortems, Warnzeit-Metrik, Deinstallations-KPI | — |
| Verwechslung mit amtlichen Warnungen | Quell-/Stufenkennzeichnung, kein Cell-Broadcast-Nachahmen, Behördenverweis, Rechtsprüfung vor Launch | — |
| Missbrauch von Standortdaten | manuelle Orte als Standard, Raster-Vergröberung, TTLs, Export/Löschung, Datenverzeichnis (§10) | — |
| Fake-Bürgermeldung / koordinierter Angriff | Beta-Reputation mit Verfall, Attest-Bindung, Diversitäts-Cluster, gestufte Konfidenz, Instrument-Gegencheck, Shadow-Ban, Kill-Switch | — |
| Falsch-SOS bindet Helfer (Stufe 2) | nicht anonym, attestiert, ratenlimitiert, sperrbar; Helfer-Härtung gegen Luring; Behörden nie Empfänger ohne Integration | — |

---

## 14. Offene Entscheidungen vor Implementierungsbeginn (geordnet, mit Blockierwirkung)

**Blockierend, zuerst:**
1. **Stations-Abdeckungsanalyse P0a** — blockiert jeden P0a-Ausbauplan. KOERI-Port-Test (Minuten); je Quellzone theoretische Warnzeit pro Großstadt (Zeit bis 4. Pick); steuert spätere Stationsplatzierung. (Zwischenstand: `server/docs/p0a-coverage.md`.)
2. **KVKK-Art.-9-Mechanismus + Hosting-Festlegung** — blockiert jeden Umgang mit personenbezogenen Daten. Oracle-EU-VM, Latenz nach Istanbul/Ankara messen.
3. **Play-Store-Richtlinie Full-Screen-Intent + App-Kategorie** — blockiert den Alarm-Erlebnis-Plan; Fallback definiert (§6.7).
4. **VM-Kapazitätsrechnung** (Strom-TTL + PostGIS + Redis + Kacheln + Monitoring auf einer VM) — blockiert das Deployment; klärt, ob cgroup-Isolation Spielraum hat.

**Danach:**
5. Karten: MapLibre + selbstgehostete OSM-Vektorkacheln (offizieller OSM-Tile-Server verbietet App-Produktivlast); Türkei-Extract auf Gratis-Hosting.
6. Backups + Restore-Probe (Objektspeicher, §8.2).
7. Oracle-Fallback-Anbieter kalt eingerichtet (Runbook).
8. Foreground-Service-Typen + Manifest-Permissions festlegen.
9. Governance & Finanzierung: Rechtsform, Nachfolge, Datenverantwortung, Finanzmodell (Pro-Komfort/Spenden/Partnerschaft) vor Live-Betrieb.
10. Push-Kanäle: FCM + Socket im MVP; Pushy nach Testmatrix-Befund.
11. Schwellen-Startwerte: aus Literatur + Replay kalibrieren, im Schattenbetrieb validieren (inkl. IPE-Koeffizienten, `exp`-Fenster, PLUM-Radien).
12. Sammelplatz-Datenzugang (AFAD/e-Devlet/Kommunen); ohne Freigabe ohne Sammelplatz-Ebene starten.
13. EEW-Partnerlizenz parallel evaluieren (keine MVP-Abhängigkeit).
14. Login-Strategie Stufe 2 (inkl. Migrationspfad gerätelokal → Konto).
15. Mehrnutzer-/Kindergeräte: Stufe 2 mit Konten/Familienfunktionen, kein MVP-Scope.

Jede Entscheidung trägt Owner + „blockiert welchen Plan"; die Sequenz gehört in ein kurzes Vorbereitungsphase-Dokument mit Plan-Landkarte.

---

## 15. Änderungsregister — Verbesserung je Punkt gegenüber Bestand

| Bereich (Bestand) | Verbesserung in dieser Spec |
|---|---|
| Zielbild | Produktversprechen messbar quantifiziert; Blindzone als Formel/Anzeige statt Prosa (§1) |
| PLUM-Pfad | explizite Basisparametrierung (2-Stationen-Korroboration, 30-km-Radius, Vs30-Zielkorrektur, 1-s-Fortschreibung) (§2.1) |
| P0a-Algorithmik | zweistufiger Picker (Energie + ML), Pick-Quorum ≥ 4, Rekalibrierung als geschlossene, versionierte Schleife (§2.2) |
| P0b-Signifikanz | Poisson-Überraschung + False-Discovery-Kontrolle über Zellen (Multiple-Testing-Fehlalarmquelle geschlossen) (§2.2) |
| Lokale Selbstdetektion | konkrete Scharfschalt-/Auslösebedingungen inkl. Ruhelage-Vorfenster und Sensorraten-Degradation (§2.3) |
| Tsunami T0 | Entscheidungsmatrix als versionierte, auditierbare Konfigurationstabelle; ETA-Raster mit Versions-/Datumspflicht (§2.4) |
| Intensität/Countdown | **Hypozentral- statt Epizentraldistanz (Ist-Fehler behoben)**; publizierte IPE statt Ad-hoc-Formel; Vs30-Term pro Ort (§2.5) |
| Eskalation | explizite Hysterese (Mindesthaltezeit + Bestätigungspflicht für Herabstufung; Entwarnung als eigener Zustand) (§2.5) |
| Reputation | Beta-Verteilung statt Rohscore (Unsicherheit fließt ein); zeitlicher Reputationsverfall gegen gekaperte Alt-Accounts (§2.6) |
| Aufmerksamkeits-Modus | hartes Auto-Reset-Fenster + öffentliche Präzisions-KPI (§3.2) |
| Fehlalarm-Filter | Verwerfungen als protokollierte Ereigniszustände (Forensik-Fähigkeit) (§3.3) |
| Tier-Ableitung | Tier-Halbordnung + Monotonieregel statt Quellen-Sonderfall (§3.4) |
| Zeitsync | konkrete Client-Regel (Countdown nur bei ≤ 300 ms Uhr-Unsicherheit), Offset-Messung/-Cache (§3.5) |
| Sequenz-Modus | Omori-artige Abklingkurve + Durchbruchsregel für starke Nachbeben (§3.6) |
| Degradations-Modus | aus Risikotabelle in die Pipeline gezogen: Zonenausfall = Signal (§3.8) |
| Latenzbudget | p50/p95/p99 statt nur Median; Fanout-Grenzwert als Lasttest-Abnahme (§4) |
| **Payload** | **v2: `exp` (Anti-Replay — Ist-Lücke), `kid` (Rotation), `radius_km`, `path`, `lang_key`** (§5.2) |
| **Schlüssel** | **zweistufige Hierarchie: Offline-Root signiert Schlüsselmanifest; Rotation ohne App-Update** (§5.3) |
| Client-Validierung | normative Reihenfolge inkl. **`ver`-Monotonie (Downgrade-Replay geschlossen)** und Tier-Monotonie (§6.2) |
| Alarm-Erlebnis | Wakelock-Regel für Countdown unter Doze; Auto-Wechsel ins Offline-Paket (§6.3) |
| i18n | maschinell geführter Prüfstatus je Sprache; ungeprüfte lebensrettende Strings blockieren den Sprach-Release (§6.4) |
| Offline-Paket | Versions-/Alterspflicht, Selbstmarkierung „prüfen" (§6.5) |
| Schutzstatus | zählt stille Fehler (verworfene Signaturen, Watchdog-Restarts) als Diagnosewert (§6.6) |
| Web-App | volle Validierungsreihenfolge, Fachlogik-Gleichzug (Hypozentral/IPE), freie Koordinateneingabe (§7) |
| Replay | Determinismus bis auf Ergebnis-Hash als Regressionskriterium (§8.2) |
| Backups | verpflichtende Restore-Proben mit veröffentlichtem Ergebnis (§8.2) |
| Stream | NDJSON-Archiv in Objektspeicher: Replay-Korpus + Backup entkoppelt von VM-Platte (§8.3) |
| Konfiguration | signierte Konfigurationsartefakte (kompromittierte VM kann Schwellen nicht still verstellen) (§8.4) |
| Datenschutz | maschinenlesbares Datenverzeichnis, fortgeschrieben je Schemaänderung (§10) |
| Abnahme | Ergebnis-Hash-Determinismus; Anti-Replay-/Downgrade-Nachweis; **Zahlenkonsistenz aller drei Fachlogik-Implementierungen über gemeinsame Testvektoren** (§11) |
| BLE-Mesh | Ablauffenster = Payload-`exp` (eine Wahrheit); Trickle-artige Relay-Unterdrückung + Jitter gegen Kollisionen (§12.5) |
| Partnerschiene | ORFEUS/EIDA/GEOFON-Katalogprüfung als konkreter Sofortschritt ergänzt (§12.4) |
| Risiken | Spalte „Restrisiko" — jede Gegenmaßnahme benennt ehrlich, was sie nicht abdeckt (§13) |
| Offene Entscheidungen | als geordnete Blockierkette mit Ownern statt flacher Liste (§14) |

---

## Quellen

Identisch mit UMSETZUNGSPLAN.md [1]–[21] (MGM, TU/FDSN, EFFIS, GloFAS, GEOFON, SeedLink, EMSC, USGS, AFAD, SeisBench/PhaseNet, NEAMTWS, KOERI, IOC Sea Level, GEBCO, Raspberry Shake, USGS Vs30, JMA/PLUM, ShakeAlert, AFAD TDVMS, Finazzi et al. 2024, PyOcto); zusätzlich: Allen, Wald & Worden 2012 (Intensity Prediction Equations), Wu et al. 2007 (Pd-Magnitude), RFC 6206 (Trickle-Algorithmus, Vorbild für §12.5). Jede Verfügbarkeits-/Latenzangabe bleibt Planungsannahme, bis die formale Zugangsprüfung sie bestätigt.
