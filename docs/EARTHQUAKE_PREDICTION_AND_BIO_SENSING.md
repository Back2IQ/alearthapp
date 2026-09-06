# Alert2IQ — Seismische Vorhersage-Mechanismen & Bio-Sensorik (Konzeptpapier)

> **Status:** Konzept & Diskussionsgrundlage für spätere Roadmap-Phasen  
> **Ecosystem:** Back2IQ – Ahead by Design (`https://back2iq.com`)  
> **Autor:** Deniz Kiran & Antigravity Core Team  
> **Datum:** 06. September 2026  

---

## 1. Executive Summary & Leitphilosophie

Die klassische, deterministische Vorhersage von Erdbeben („Tag X um Uhrzeit Y mit Stärke Z“) ist physikalisch und seismologisch Stand heute unmöglich. 

**Alert2IQ setzt daher auf probabilistische, multi-sensorische Früherkennungs- und Bereitschaftsindikatoren**:
Anstatt unseriöse Panik zu schüren, kombinieren wir **geophysikalische Spannungsdaten**, **seismische Mikroschwärme** und **biologische Frühindikatoren (Tierverhalten)** zu einem wissenschaftlich fundierten, regionalen Vorwarn- und Achtsamkeitsmodell.

---

## 2. Geophysikalische & Seismologische Indikatoren

### A. Mikrobeben-Cluster & b-Wert-Anomalien (Gutenberg-Richter)
* **Wissenschaftlicher Hintergrund:** Vor vielen großen Rupturen ändert sich das logarithmische Verhältnis zwischen kleinen und mittleren Beben (der sogenannte b-Wert). Ein rapider Abfall des b-Wertes an einem blockierten Verwerfungssegment signalisiert extremen Spannungsaufbau.
* **Alert2IQ-Anwendung:** Automatische Erkennung von Mikro-Schwärmen (z. B. 5+ Beben M 1.5 - M 2.8 innerhalb von 3 Stunden im selben 10-km-Radius).

### B. GNSS & InSAR Krustendehnungs-Messung (Tectonic Strain)
* **Wissenschaftlicher Hintergrund:** Satellitenbasierte Radarinterferometrie (InSAR) und permanente GNSS-Bodenstationen messen zentimeter- und millimetergenaue Kontinentalverschiebungen.
* **Alert2IQ-Anwendung („Fault-Stress-Radar“):** Visualisierung bekannter Bruchzonen (z. B. Nordanatolische Verwerfung, Marmara-Segment, Hellenischer Bogen) mit Farbindikation nach aufgestauter Energie seit dem letzten historischen Bruch.

### C. Piezoelektrische Ionisation & Total Electron Content (TEC)
* **Wissenschaftlicher Hintergrund:** Unter extremen Druckspannungen brechen mikroskopische Quarzstrukturen im Gestein und erzeugen piezoelektrische Spannungen. Diese setzen positive Ionen an die Erdoberfläche frei und verändern messbar die Elektronendichte in der Ionosphäre (24–72h vor Megabeben M >= 7.0).

### D. Probabilistische Nachbeben- & Folgemodelle (ETAS & Omori-Gesetz)
* **Wissenschaftlicher Hintergrund:** Nach einem Hauptbeben gehorcht die Frequenz der Nachbeben dem modifizierten Omori-Utsu-Gesetz.
* **Alert2IQ-Anwendung:** Dynamische 7-Tage-Nachbeben-Wahrscheinlichkeitskurven für Betroffene zur Beruhigung und realistischen Risikoeinschätzung.

---

## 3. Bio-Seismologie & Tierische Vorahnung (Empirische Basis)

### A. Wissenschaftliche Beweislage (Max-Planck-Institut für Verhaltensbiologie)
Unter Leitung von Prof. Dr. Martin Wikelski wies das Max-Planck-Institut in Mittelitalien nach, dass Tiere (Rinder, Schafe, Hunde, Vögel) **1 bis 20 Stunden vor Erdbeben (M 3.8 - M 6.6) statistisch hochsignifikante Hyperaktivität und Fluchtreflexe** zeigen.
* **Kollektiver Verstärkungseffekt:** Während einzelne Haustiere auch auf zufällige Reize reagieren, erreicht die **synchrone Unruhe einer Tiergruppe** eine Trefferwahrscheinlichkeit von über 80 %.
* **Distanz-Korrelation:** Je näher die Tiere am Epizentrum waren, desto früher setzte die Verhaltensänderung ein.

### B. Welche physikalischen Reize nehmen Tiere wahr?
1. **Positive Luft-Ionen:** Die tektonische Gesteinsbelastung erzeugt Ionen, die bei Säugetieren zu akutem Serotonin-Anstieg, Unruhe und Fluchtdrang führen.
2. **Infraschall (< 20 Hz):** Gesteinsmikrorisse erzeugen tieffrequente Töne, die Hunde, Elefanten und Vögel lange vor dem Hauptbeben hören.
3. **P-Wellen-Mikrovibrationen:** Über empfindliche Pfotenballen und Hufe spüren Tiere hochfrequente Kompressionswellen Sekunden vor Menschen.
4. **Ausgasungen (Radon, Methan):** Schlangen und Nagetiere fliehen aus Erdbauten, sobald Gase durch Mikroklüfte entweichen.

---

## 4. Alert2IQ Architektur-Modell für zukünftige Phasen

### Stufe 1: „Crowd-Pet Anomaly Watch“ (Nutzer-Schwarmmeldung)
* **Funktion:** Ein schneller 1-Klick-Button in der Alert2IQ-App:  
  *„Ungewöhnliches Tierverhalten melden“* (z. B. unbegründetes Dauerbellen, panisches Verstecken, Vogelschwarm-Unruhe).
* **Anti-Rauschen-Filter:** Erst wenn innerhalb von 30 Minuten in einer 20-km-Zelle **mehrere verifizierte, unabhängige Nutzer** melden, wird ein interner Anomaly-Score erhöht.

### Stufe 2: IoT-Integration für Smart-Pet Tracker (Visionär)
* **Schnittstelle:** Anbindung an etablierte Haustier-Tracker (z. B. Tractive, Garmin, Fressnapf).
* **Automatisierter Indikator:** Wenn hunderte Tracker in einer seismischen Risikozone synchron mitten in der Nacht unnatürliche Bewegungsausbrüche messen, fließt dies in den Vorwarn-Algorithmus ein.

---

## 5. Ethische Leitplanken & Anti-Panik-Garantie

1. **Keine Falschalarme:** Vorläufer-Indikatoren lösen **niemals** den lauten Vollbild-Alarm (Sirene / DND-Bypass) aus.
2. **Achtsamkeits-Hinweise statt Panik:** Status wechselt maximal auf Gelb (*„Erhöhte seismische / biologische Schwarm-Aktivität in Region X – prüfe deine Notfallvorräte“*).
3. **100% DSGVO-Datenschutz:** Ortsangaben von Haustiermeldungen werden nur als grobe 10-km-Rasterzellen anonym verarbeitet.

---

*Dokument gespeichert unter:* `tda/docs/EARTHQUAKE_PREDICTION_AND_BIO_SENSING.md`