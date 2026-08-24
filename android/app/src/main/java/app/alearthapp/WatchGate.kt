package app.alearthapp

/**
 * Latch-Wächter: entscheidet, ob dem [ShakeDetector] das aktuelle Sample gefüttert
 * werden darf. Sobald das Handy einmal SETTLED war, bleibt der Wächter offen —
 * ein nachfolgendes UNSETTLED (z. B. weil ein Beben selbst die Ruhe-Varianz
 * sprengt) darf den ShakeDetector NICHT mehr abschneiden, sonst kann er seine
 * nötigen aufeinanderfolgenden Samples nie sammeln und `/trigger` feuert nie.
 * Reine Logik, kein Android-Bezug.
 */
class WatchGate {
    private var watching = false

    /** Liefert true, wenn dieses Sample an den ShakeDetector weitergereicht werden soll. */
    fun update(state: Stillness): Boolean {
        if (state == Stillness.SETTLED) watching = true
        return watching
    }
}
