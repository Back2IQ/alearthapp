# AlearthApp — Glossar

Kurze, alltagstaugliche Erklärungen der Begriffe, die in der App und im Projekt vorkommen.
Ziel: verständlich für normale Nutzer:innen, nicht für Seismolog:innen.

---

## Erdbeben-Grundlagen

**Erdbeben-Frühwarnung (EEW, *Earthquake Early Warning*)**
Das Kernversprechen der App: dir **Sekunden Vorsprung** zu geben, bevor das starke Rütteln bei
dir ankommt. Möglich, weil Sensoren nahe am Bebenherd den Beginn zuerst „sehen" und die Warnung
der zerstörerischen Welle vorauseilt. Sie kann **nie vor dem Beben selbst** warnen — sie ist
schneller als die Erschütterung, nicht als die Zeit.

**P-Welle (Primärwelle)**
Die **schnellste** Welle (~6 km/s). Kommt **zuerst** an, ist aber meist schwach — oft nur ein
kurzer Ruck oder Grollen. Sensoren nutzen sie als Vorboten.

**S-Welle (Sekundärwelle / Scherwelle)**
Die **langsamere** Welle (~3,5 km/s), die aber das **heftige, gefährliche Rütteln** bringt, das
Gebäude beschädigt. Der Alarm „**S-Welle trifft ein**" meint genau diese Welle.

**Vorwarnzeit (*lead time*)**
Die Zeit zwischen „Beben erkannt" und „starke Welle erreicht deinen Standort". Je **weiter** du
vom Epizentrum bist, desto **mehr** Vorwarnung — direkt am Epizentrum sind es 0 Sekunden.

**Wellenfront**
Die sich kreisförmig ausbreitende Erschütterung. Ein echtes Beben hat eine **wandernde**
Wellenfront — daran erkennt die App, dass es kein Fehlalarm (z. B. Feuerwerk) ist.

---

## Stärke & Wirkung

**Magnitude (*Büyüklük*)**
Die **freigesetzte Energie** des Bebens — **eine** Zahl pro Beben (z. B. M 6,8), unabhängig vom
Ort. Jede Stufe bedeutet ~32× mehr Energie. **Nicht** mit Intensität verwechseln.

**Intensität (MMI, *Modified-Mercalli-Intensität*)**
Wie **stark es sich an einem bestimmten Ort anfühlt** — abhängig von Entfernung und Untergrund.
Skala **I–XII**: I = unmerklich, XII = nahezu totale Zerstörung. Dasselbe Beben hat also
**überall eine andere** Intensität, aber nur **eine** Magnitude.

**Epizentrum**
Der Punkt an der **Erdoberfläche** direkt über dem Bebenherd.

**Herdtiefe / Tiefe (*Derinlik*)**
Wie **tief** unter der Oberfläche das Beben ausgelöst wurde. Flache Beben richten näher an der
Oberfläche mehr Schaden an.

**Nachbeben (*aftershock*)**
Weitere, meist schwächere Beben **nach** dem Hauptbeben — können aber noch gefährlich sein
(vorgeschädigte Gebäude). Dafür gibt es die **Nachbeben-Wache** in der App.

**Vorbeben (*foreshock*)**
Kleinere Beben **vor** einem Hauptbeben — im Nachhinein erkennbar, selten vorhersagbar.

---

## Weitere Gefahren & Warnstufen

**Tsunami**
Von einem Seebeben ausgelöste Riesenwelle. Für Küstenstandorte gesondert einstellbar.

**Sturm / Überschwemmung (Hochwasser)**
Multi-Hazard-Warnungen über den GDACS-Feed (siehe unten). „Überschwemmung/Hochwasser" statt
„Flut", weil „Flut" auch Gezeiten meint.

**GDACS-Warnstufen (grün / orange / rot)**
Internationaler Schweregrad für Sturm/Flut/Tsunami:
- **grün** = geringe Auswirkung, **orange** = mittlere, **rot** = schwere.
In der App wählst du je Gefahr, **ab welcher Stufe** du benachrichtigt bzw. alarmiert wirst.

---

## Die zwei Warn-Stufen der App

**Benachrichtigung**
Die **leise** Stufe: ein dezenter Hinweis, wenn ein Beben für dich relevant ist. Kein Vollbild,
durchbricht **nicht** den Stumm-Modus.

**Alarm**
Die **laute** Stufe: DND-durchdringender **Vollbild-Alarm** mit Ton, der auch bei gesperrtem
Bildschirm das Display weckt. Für den Ernstfall.

**Warnradius (Umkreis)**
Nur Beben **innerhalb** dieser Entfernung von deinem Standort lösen etwas aus.

**Entwarnung**
Manuelles Beenden/Quittieren eines Alarms.

---

## Datenquellen

**USGS** — US-Erdbebendienst; weltweite Echtzeit-Bebendaten (offen, kostenlos).
**EMSC** — Europäisch-Mediterranes Seismologisches Zentrum; schnelle Meldungen für die Region.
**AFAD** — offizielle türkische Katastrophenbehörde.
**Kandilli** — türkisches Observatorium (Erdbebenforschung).
**GDACS** — globales Katastrophen-Frühwarnsystem (Schweregrade für Sturm/Flut/Tsunami).
**EONET** — NASA-Ereigniskatalog für Naturereignisse.

---

## App- & Technik-Begriffe

**Hybrid-App**
AlearthApp ist eine **native** Android-App, die die gestaltete Web-Oberfläche in sich lädt
(WebView) und mit einer lebensrettenden nativen Schicht verbindet — das Beste aus beidem.

**FCM (*Firebase Cloud Messaging*)**
Googles Push-Dienst. Der **Weckmechanismus**, mit dem der Server eine Warnung auf ein Handy
schiebt — auch wenn die App zu ist oder das Gerät im Standby liegt.

**Push-Token**
Die eindeutige „Postadresse" eines Geräts bei FCM. Die App meldet ihn ans Backend, damit der
Server gezielt **dieses** Gerät warnen kann.

**Backend / Poller / Matcher**
Der Server (Oracle Always Free): **pollt** die Feeds, **matcht** neue Beben gegen die Standorte
und Schwellen aller Geräte und schickt passende **Pushes** raus.

**Nachbeben-Wache / Beacon (Notsignal)**
Native Schutzfunktionen nach einem starken Beben: hält Wache für Nachbeben und kann ein
akustisches/optisches **Notsignal** senden (auch offline hilfreich zum Auffinden).

**„Nicht stören" durchbrechen (DND-Bypass)**
Erlaubnis, dass der Alarm auch bei aktivem „Nicht stören" hörbar ist — nur mit deiner
ausdrücklichen Freigabe.

**Autostart (MIUI/Xiaomi)**
Auf manchen Herstellern (z. B. Xiaomi) muss die App **Autostart** erlaubt bekommen, damit Pushes
auch nach „App weggewischt" zuverlässig ankommen.
