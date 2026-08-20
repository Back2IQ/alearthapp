# Plan-Landkarte — Turkey Disaster Alert (MVP)

**Zweck:** Reihenfolge, Abhängigkeiten und Zuschnitt aller Implementierungspläne, damit nicht der kleinste Teil fertig wird, während der USP (P0a) unadressiert bleibt. Spec: `../../../../UMSETZUNGSPLAN.md` (Vollrevision 4). Grundlage: Spec §8 (MVP-Umfang) + §11 (offene Entscheidungen mit Reihenfolge).

**Leitprinzip der Reihenfolge:** Erst das Fundament (Strom/Fusion/Publish/Client-Empfang), dann der differenzierende Kern (P0a), dann die Breite (Bürgermeldungen, Offline, Betrieb). Jeder Plan endet mit einem eigenständig testbaren Ergebnis.

---

## Blockierende Vorbedingungen (aus §11 — VOR den jeweiligen Plänen)

| Vorbedingung | Blockiert | Status |
|---|---|---|
| **Stations-Abdeckungsanalyse** | Plan B (P0a) | **Recherche-Zwischenstand:** frei nur wenige GEOFON-Stationen (dünn); KOERI-SeedLink-Port sofort testen; volle TU/KO-Abdeckung braucht AFAD/KOERI-Kooperation. Raspberry-Shake NICHT frei echtzeitfähig. → P0a ab Tag 1 nur punktuell. |
| **VM-Kapazitätsrechnung** (Strom+PG+Redis+Tiles+Monitoring auf einer Free-VM) | Deployment / Plan F | offen |
| **KVKK-Auslandsübermittlung + Hosting-Festlegung** | jeder Umgang mit personenbez. Daten (Plan D, Konten) | offen |
| **Play-Store-Full-Screen-Intent-Prüfung** | Plan C (Alarm-Layer) | offen |
| **Sammelplatz-Datenzugang** (AFAD Toplanma Alanları) | Plan E (Offline-Paket, Sammelplatz-Ebene) | offen |

---

## Pläne und Abhängigkeiten

```
Plan A (Durchstich P1/P2)  ──┬──► Plan B (P0a Stations-EEW)  ◄─ Abdeckungsanalyse
   Fundament: Strom,        │
   Fusion, Publisher,       ├──► Plan C (nativer Client + Alarm-Layer)
   Signierung, Min-Client   │
                            ├──► Plan D (Bürgermeldungen + Reputation)
                            │
                            └──► Plan F (Betrieb: Monitoring/Kill-Switch/Keys)  [querschnittlich]

Plan C ──► Plan E (Offline-Sicherheitspaket + Orte/Schwellen gerätelokal)
Plan B + Plan A ──► Plan G (Tsunami T0/T1)  ◄─ KOERI-Zugang
```

| Plan | Inhalt (Spec-Bezug) | Hängt ab von | Modell-Routing (Implementer) |
|---|---|---|---|
| **A — Durchstich** ✅ existiert | P1/P2-Katalog → kanonisches Ereignis → signierter FCM-Publish → Minimal-Client (§8) | — | haiku/sonnet je Task |
| **B — P0a Stations-EEW** (USP, stationsabhängig) | SeedLink-Ingest, Picker (SeisBench/PhaseNet), PyOcto-Associator, Pd-Magnitude (Wu 2007/Kuyuk&Allen 2013), Beobachtungspfad PLUM, EPIC-artige Alarmkriterien; ARM-Benchmark als erster Task; regionale Nachkalibrierung als eigener Schritt (§2.2, §3) | Plan A (Fusion/Strom/Publisher), Abdeckungsanalyse/Stationszugang | **opus** (Architektur+Wissenschaft), sonnet für Mechanik |
| **B2 — P0b Crowdsourcing-Detektion** (USP, nutzerabhängig, hängt NICHT am Fremdzugang) | Server-Detektor nach Finazzi (Poisson-Hintergrund, Score-Detektor, Pareto-Tail auf 1 Fehlalarm/Jahr), räumliches Clustering (Finazzi 2022), Anbindung an bestehenden P0b-Sammelbetrieb | Plan A (Strom/Fusion), P0b-Sammel-Client, Geräte-Attestierung | **opus** (Statistik/Anti-Abuse), sonnet Mechanik |
| **C — Nativer Client + Alarm-Layer** | Full-Screen-Alarm, Vordergrund-Dienst, Countdown, Schutzstatus, Android-Permission-Gauntlet (§5), i18n/RTL, Farbthemen, Barrierefreiheit | Plan A (Payload/Verify, Zell-Topics) | sonnet, opus für Permission-/FGS-Architektur |
| **D — Bürgermeldungen + Reputation** | Melde-Signalklasse, Reputationsscore, C0/C1, Anti-Sybil, Kill-Switch, Moderation (§2.5) | Plan A (Strom/Event-Modell), Geräte-Attestierung | sonnet, opus für Reputations-/Anti-Abuse-Design |
| **E — Offline-Paket + Orte** | Gerätelokale Orte/Schwellen, Offline-Sicherheitspaket (Sammelplätze/112/Treffpunkt), Geräte-Export QR (§5, §8) | Plan C, Sammelplatz-Datenzugang | sonnet |
| **F — Betrieb** (querschnittlich) | Monitoring, Kanarien (Latenz + wöchentliche Erkennungs-Replays), Totmann-Schaltung, Kill-Switch (Zwei-Personen), Schlüsselmanagement/KMS-Trennung + Rotation, Statusseite, Ressourcen-Isolation (§6) | Plan A, VM-Kapazitätsrechnung | opus für Sicherheits-/Schlüsselteil, sonnet Rest |
| **G — Tsunami T0/T1** | NEAMTWS-orientierte Ableitung, KOERI-Bulletins, Küstenort-Attribut (§2.3) | Plan A/B, KOERI-Zugang | sonnet |

---

## Empfohlene Ausführungsreihenfolge

1. **Plan A** ausführen (beweist die Kette, legt Strom/Fusion/Publisher/Signierung + Minimal-Client) — mit den fünf Korrekturen aus dem Deep-Dive (siehe Plan-A-Kopf).
2. **Parallel** die Abdeckungsanalyse (Recherche läuft) + VM-Kapazitätsrechnung abschließen — beide blockieren B bzw. Deployment.
3. **Plan B (P0a)** — der eigentliche USP. Sobald die Abdeckungsanalyse zeigt, welche Quellzonen ab Tag 1 detektierbar sind. Gegründet auf offene Bausteine (SeisBench/PhaseNet, Associator, Pd/PLUM), kalibriert gegen historische türkische Beben, im Schattenbetrieb validiert — nie „scharf" ohne Replay-Nachweis.
4. **Plan C** (nativer Alarm-Client) — kann nach Plan A teils parallel zu B laufen (unabhängige Codebasis), braucht aber die Play-Store-Prüfung vorab.
5. **Plan D, E, F, G** nach Bedarf; F begleitet durchgehend.

**Wichtig:** Plan A ist bewusst der am wenigsten differenzierende Teil (Katalog-Weiterleitung, die AFAD/Kandilli-Apps auch leisten). Der Wert des Produkts entsteht in der Frühwarnung. Reihenfolge heißt nicht Priorität der Wichtigkeit — A ist nur das Gerüst.

**Strategische Neugewichtung aus der Recherche (P0b vor/neben P0a):** Weil der Stations-Echtzeitzugang für die Türkei ab Tag 1 gedeckelt ist (nur wenige GEOFON-Stationen ohne AFAD/KOERI-Kooperation), ist **das crowdsourced Phone-Netz (P0b) womöglich der schnellere Weg zu echter Sekunden-Abdeckung als Stationsdaten** — belegt durch Earthquake Network beim M7.8 2023 (12 s nach Bruch, bis 58 s Vorwarnung, rein crowdsourced; Finazzi et al. 2024). Die Methode ist mit exakten Formeln publiziert und nachbaubar (Poisson-Hintergrund + Score-Detektor + Pareto-Tail auf 1 Fehlalarm/Jahr). **Folge:** Ein eigener **Plan B2 (P0b-Detektion)** rückt in der Priorität neben Plan B (P0a) — beide sind der USP, und P0b hängt nicht am Fremdzugang, sondern nur an Nutzerzahl. Die Reihenfolge B vs. B2 hängt davon ab, ob der KOERI-SeedLink-Test/die AFAD-Kooperation schnell greift (dann P0a zuerst) oder nicht (dann P0b zuerst, um überhaupt Tag-1-EEW zu haben).

---

## Wissenschaftliche Fundierung (Recherche-Stand, fließt in Plan B)

- **Magnitude:** Pd (peak displacement) im 3-s-P-Fenster ist der stabilere Proxy als τc; Startkoeffizienten Wu/Kanamori/Allen/Hauksson 2007 + globale Kalibrierung Kuyuk & Allen 2013; Pd > 0,5 cm ≈ schadensrelevant. **Türkei-Nachkalibrierung** mit AFAD/KOERI-Daten ist ein eigener Plan-B-Schritt (kalifornische Defaults nur als Start).
- **Alarmkriterien (EPIC-Vorbild):** ≥ 4 assoziierte Stationen, ≥ 40 % der Nahstationen, feste Tiefe (8 km Startannahme), Grid-Search-Lokalisierung, gewichtetes Pd-Mittel, RMS-Grenze.
- **Beobachtungspfad:** PLUM (Kodera et al. 2018) — 30-km-Radius, unattenuierte Projektion; robust, aber kürzere Vorwarnzeit → als Ergänzung, nicht Ersatz des Modellpfads.
- **Große Brüche:** Punktquelle sättigt ~M7; Kahramanmaraş-artige Multi-Segment-/Supershear-Rupturen brauchen Finite-Fault (FinDer-Prinzip) — Stufe 2, da FinDer-Kern nicht voll offen (scfinder AGPL, Kern auf Anfrage).
- **Intensität am Nutzerort:** GMPE + GMICE + Vs30; die GMICE-Wahl allein verursacht > 1 MMI Unterschied und hunderte km Alarmradius-Differenz → Unsicherheit offen kommunizieren, regional kalibrieren.

- **Stack (belegt):** SeisBench/PhaseNet (Picker, CPU-tauglich, GPL-3.0) + PyOcto-Associator (MIT, schnellster; ARM-Source-Build) mit GaMMA als Python-Fallback; QuakeFlow als Architektur-Vorbild. Erster Plan-B-Task ist ein **ARM-Benchmark auf der Ziel-VM** (keine ARM-Referenzdaten publiziert).
- **Datenzugang (belegt, ernüchternd):** Raspberry-Shake ist NICHT frei echtzeitfähig; Tag-1-frei nur wenige GEOFON-Stationen; KOERI-SeedLink-Port in Minuten testbar; volle TU/KO-Abdeckung nur mit Kooperation. → Partnerschaftsschiene ist kritischer Pfad; P0b als möglicher Haupt-Weg.
- **P0b-Methode (belegt, nachbaubar):** Finazzi & Fassò 2017 (Poisson-Hintergrund `λ⁰=exp(β₀+β₁ν_t)`, Score `S=N/(ε·λ⁰)−1`, Pareto-Tail auf 1 Fehlalarm/Jahr) + räumliches Modell Finazzi et al. 2022.

Vollständiges, zitiertes Recherche-Protokoll: `RECHERCHE-P0A.md` (im selben Ordner).
