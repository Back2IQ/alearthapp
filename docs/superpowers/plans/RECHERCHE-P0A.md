# Recherche-Protokoll — P0a/P0b Fundierung (für Plan B)

**Stand:** 20. August 2026. Drei parallele Recherchen (Web + Primärquellen). Zweck: der EEW-Kern steht auf validierten offenen Bausteinen und belegter Fachliteratur, nicht auf aus Konkurrenz-APKs geratenem Wissen. Alle Kernaussagen mit Quelle; Einschätzungen sind markiert.

---

## 1. Empfohlener Stack (Picking + Assoziation)

**SeisBench (PhaseNet) + PyOcto-Associator + eigener schlanker Ringpuffer/Scheduler.** GaMMA (reines Python) als ARM-Fallback.

- **Picker:** `pip install seisbench` (v0.12.x), `sbm.PhaseNet.from_pretrained("geofon")`. Anwendung auf ObsPy-Streams via `model.annotate(stream)`. PhaseNet ist ein leichtes CNN — x86-Referenz > 1.000 samples/s pro Kern, damit für wenige Stationen auf CPU echtzeittauglich. EQTransformer ist größer/riskanter auf schwacher ARM-CPU.
- **Associator:** **PyOcto** (Münchmeyer, MIT, C++-Kern) — schnellster, aktiv gepflegt, liefert Hypozentrum + Herdzeit. Alternativ GaMMA (MIT, reines Python, langsamer).
- **Referenzarchitektur:** QuakeFlow (SeedLink → Picks → Associator → Katalog) als Muster, ohne den Kubernetes/Spark-Overhead. SeisComP/Earthworm nur als Architektur-Vorbild studieren (eigene Lizenzen, klassische STA/LTA-Picker).

**Risiken/Unbekannte (vor Plan B klären):**
1. **Keine ARM-Benchmarks** für SeisBench/PhaseNet publiziert → auf der Ziel-VM selbst messen, bevor Stationszahl/Fenstergröße fixiert werden.
2. **PyOcto hat keine Linux-ARM-Wheels** → Source-Build (C++/CMake/pybind11) auf der VM nötig; GaMMA als Fallback.
3. **SeisBench = GPL-3.0** (Copyleft) — für eine offene Gratis-App unkritisch, aber falls je proprietär: Blocker. Passt zur Nullkosten-/Offen-Ausrichtung, sollte aber bewusst entschieden werden.
4. **Doppel-Picks an Fenstergrenzen** — SeisBench löst das nicht automatisch; eigene Dedupe-/Merge-Logik mit überlappenden Fenstern nötig.
5. EPIC/ShakeAlert-Code ist **nicht offen** — nur Methodik-Referenz.

Quellen: [SeisBench](https://github.com/seisbench/seisbench), [PyOcto](https://github.com/yetinam/pyocto) / [Seismica](https://seismica.library.mcgill.ca/article/view/1130), [GaMMA](https://github.com/AI4EPS/GaMMA), [QuakeFlow GJI 2022](https://academic.oup.com/gji/article/232/1/684/6694250).

---

## 2. Magnitude & Detektionslogik (Fachliteratur)

- **Schnelle Magnitude:** **Pd (peak displacement)** im 3-s-P-Fenster ist stabiler als τc. Start-Koeffizienten: Wu/Kanamori/Allen/Hauksson 2007 (`M = 4.218·log₁₀(τc) + 6.166`); Pd-Attenuation Wu & Zhao 2006 (`log₁₀(Pd) = −3.463 + 0.729·M − 1.374·log₁₀(R)`); globale Kalibrierung Kuyuk & Allen 2013. **Pd > 0,5 cm ≈ schadensrelevant.**
- **Alarmkriterien (EPIC-Vorbild):** ≥ 4 assoziierte Stationen, ≥ 40 % der Nahstationen getriggert, feste Tiefe (8 km Start), Grid-Search-Lokalisierung, gewichtetes Pd-Mittel über Stationen, RMS < 1,0.
- **Sättigung ~M7,0** (Trugman et al. 2019, physikalisch erklärt): Punktquelle unterschätzt große Brüche. ShakeAlert ergänzt ab M7 GNSS (GFAST-PGD) und FinDer.
- **Beobachtungspfad PLUM** (Kodera et al. 2018): 30-km-Radius, unattenuierte Projektion beobachteter Intensität + Site-Faktor; interner Flächen-Schwellwert Intensität ≈ 2,5. Robust gegen Sättigung/komplexe Brüche, aber **kürzere Vorwarnzeit** → Ergänzung, nicht Ersatz.
- **Finite-Fault (FinDer,** Böse et al. 2012): Template-Matching der PGA-Verteilung → Rupturlänge/-orientierung; nötig für Kahramanmaraş-artige lange/Supershear-Brüche. **scfinder** ist AGPL, der FinDer-Kern nur „auf Anfrage" → Stufe 2.
- **Intensität am Nutzerort:** GMPE + GMICE + Vs30. **Warnung:** die GMICE-Wahl allein verursacht > 1 MMI Unterschied und hunderte km Alarmradius-Differenz (Saunders et al. 2024) → Unsicherheit offen kommunizieren.
- **Türkei-Vorbehalt (gesichert):** Kalifornische Default-Koeffizienten sind ein vertretbarer Start, aber attenuations-/krustenabhängig. Türkei-Studien (Silivri 2025, MDPI; Kahramanmaraş-Kalibrierung) zeigen: **regionale Pd-Mw-Nachkalibrierung mit AFAD/KOERI-Daten ist ein eigener Plan-B-Schritt**, nicht optional.

Quellen: [ShakeAlert Algorithms](https://www.shakealert.org/system-information/shakealert-system-algorithms/), Wu et al. 2007 (DOI 10.1111/j.1365-246X.2007.03430.x), Kuyuk & Allen 2013 (DOI 10.1002/2013GL058580), Trugman et al. 2019 (DOI 10.1029/2018JB017093), Kodera et al. 2018 (BSSA 108(2):983), Böse et al. 2012 (DOI 10.1093/gji/ggs013), Saunders et al. 2024 (DOI 10.1785/0320240001).

---

## 3. Echtzeit-Datenzugang Türkei — der kritische Realitätscheck

**Ernüchternd: Ohne Vertrag ist die Türkei-Abdeckung ab Tag 1 dünn.**

| Quelle | Tag-1 ohne Vertrag | Türkei-Abdeckung |
|---|---|---|
| GEOFON SeedLink (`geofon.gfz.de:18000`) | ✅ sofort | **dünn** — nur einzelne GE-Kooperationsstationen (Malatya, Isparta, Arapgir) |
| IRIS/EarthScope (`rtserve.earthscope.org:18000`) | ✅ sofort | vermutlich **keine** türkischen Netze (zu verifizieren) |
| KOERI SeedLink (`eida.koeri.boun.edu.tr:18000`) | ⚠️ **unklar — in Minuten testbar** (`slinktool -Q`) | volle **KO**-Abdeckung, falls Port offen |
| AFAD/TDVM (**TU**-Netz) | ❌ nur Archiv-Webportale (TADAS/TDMS), kein Echtzeit-SeedLink | volle TU-Abdeckung nur mit Kooperation |
| Raspberry Shake zentral | ❌ **kostenpflichtig** (CAPS/Vertrieb) | — |
| Raspberry Shake FDSNWS | ✅ frei, aber **nicht Echtzeit (T−30 min)** | niedrige, unbekannte Zahl |

**Konsequenz für die Strategie (wichtig):**
- **P1-Kataloge** (EMSC/USGS/AFAD) funktionieren ab Tag 1 zuverlässig — aber das ist nur Schnellmeldung (Minuten), kein EEW.
- **P0a-Stations-EEW ab Tag 1 ist ohne AFAD/KOERI-Zugang faktisch auf wenige GEOFON-Stationen beschränkt** — für echtes Sekunden-EEW zu dünn. Der schnelle Testschritt: prüfen, ob KOERI einen offenen SeedLink-Port betreibt (Minuten). Der eigentliche Unlock ist die **Partnerschaftsschiene** (AFAD-TDVM/KOERI-Zugang) — sie rückt damit von „nice to have" auf den kritischen Pfad.
- **Deshalb ist P0b (Crowdsourcing) für die Türkei womöglich der schnellere Weg zu echter Abdeckung als Stationsdaten** — die Telefone kontrollieren wir, den Stationszugang nicht. Beleg: siehe unten.

---

## 4. P0b Crowdsourcing — die Methode ist belegt und nachbaubar

**Earthquake-Network-Methode (Finazzi), mit exakten Formeln (Finazzi & Fassò 2017, arXiv:1512.01026):**
- Phone wird „aktiv" bei laden + ruhig + kalibriert; sendet alle 30 min ein Active-Signal (→ Netzgröße `ν_t`) und bei Vibration ein Vibration-Signal mit Position. ~30 Fehlsignale/Gerät/Tag → Server-Aggregation nötig.
- **Hintergrund (kein Beben):** Poisson-Prozess `λ⁰(t) = exp(β₀ + β₁·ν_t)`, Parameter per ML auf bereinigten Historiendaten (EMSC-Katalog: Signale 5 min nach bekannten Beben entfernt).
- **Detektor (echtzeittauglich):** Score `S(ε,t) ≈ N_ε^t / (ε·λ⁰(t)) − 1` über gleitendes Fenster (ε ≈ 30 s); Alarm wenn `S > h`.
- **Schwellwert `h`:** empirisch aus dem Extremwert-Tail (verallgemeinerte Pareto-Verteilung) kalibriert auf **~1 Fehlalarm/Jahr** — deckt sich exakt mit unserem „1 Fehlalarm/Jahr"-Anspruch.
- **Detektionsverzögerung:** 1–2 s bei > 300 aktiven Phones, bis ~10 s bei < 50; Erkennung ~90 % ab Report-Fraktion φ > 0,25.
- **Räumliches Clustering:** volles Modell im 2022er SRL-Paper (Finazzi, Bondár, Bossu, Steed, DOI 10.1785/0220220213) — maßgeblich für echte Raum-Clusterung statt reiner Subnetz-Trennung.
- **Türkei-Realbeleg (Finazzi, Bossu, Cotton 2024, Sci Rep 14:4878):** Beim M7.8 am 6.2.2023 Alarm **12 s nach Bruchbeginn**, bis **58 s Vorwarnung** in Intensität-≥IX-Gebieten; bei Vollverteilung hätten ~2,7 Mio. Menschen 30–66 s bekommen.

Das ist der stärkste einzelne Beleg, dass unser Kern-USP für die Türkei funktioniert — und dass P0b nicht nur „Verstärker", sondern womöglich der Haupt-Frühwarnweg ist, solange der Stationszugang gedeckelt ist.

Quellen: Finazzi & Fassò 2017 ([arXiv:1512.01026](https://arxiv.org/abs/1512.01026), DOI 10.1007/s00477-016-1240-8), Finazzi 2020 (DOI 10.3389/feart.2020.00243), Finazzi et al. 2022 (DOI 10.1785/0220220213), Finazzi/Bossu/Cotton 2024 ([PMC10902327](https://pmc.ncbi.nlm.nih.gov/articles/PMC10902327/)).

---

## Wichtigste Konsequenzen für Spec & Pläne

1. **Spec-Korrektur (Faktentreue):** Raspberry Shake ist NICHT frei echtzeitfähig in der Türkei — die frühere Annahme „ab Tag 1 verfügbar" wird zurückgenommen.
2. **P0a-Erwartung ehrlich senken:** Tag-1-Stations-EEW ist ohne AFAD/KOERI-Zugang auf wenige GEOFON-Stationen beschränkt. Sofort-Test: KOERI-SeedLink-Port. Kritischer Pfad: Partnerschaftsschiene.
3. **P0b/Crowdsourcing aufwerten:** belegte, nachbaubare Methode + Türkei-Realerfolg → möglicher Haupt-Frühwarnweg, nicht nur Verstärker. Plan für P0b früher priorisieren als bisher gedacht.
4. **Plan B (P0a) gründet auf:** SeisBench/PhaseNet + PyOcto + Pd-Magnitude + PLUM, mit türkischer Nachkalibrierung als Pflichtschritt und ARM-Benchmark als erstem Task.
