package app.alearthapp

import kotlin.math.floor

/**
 * Rundet eine Rohposition auf die anonyme Detektionszelle (0,1°-Raster).
 * MUSS byte-identische Strings zu `p0b/signals.py::coarsen_cell` liefern —
 * nur diese Zell-ID verlässt das Gerät, nie die Rohkoordinate.
 */
object GeoCell {
    const val DETECT_CELL_DEG = 0.1

    /** Publish-Raster (0,5°) — MUSS `geo/cells.py::cell_id` byte-identisch spiegeln.
     * Der Server sendet FCM an das Topic `cell_<publishCell>`; das Gerät abonniert es. */
    const val PUBLISH_CELL_DEG = 0.5

    fun coarsenCell(lat: Double, lon: Double): String {
        val la = floor(lat / DETECT_CELL_DEG).toInt()
        val lo = floor(lon / DETECT_CELL_DEG).toInt()
        return "d${la}_$lo"
    }

    /** Publish-Zelle wie serverseitig `cell_id(lat,lon)` → z.B. "c72_72". */
    fun publishCell(lat: Double, lon: Double): String {
        val la = floor(lat / PUBLISH_CELL_DEG).toInt()
        val lo = floor(lon / PUBLISH_CELL_DEG).toInt()
        return "c${la}_$lo"
    }

    /** FCM-Topic der Publish-Zelle, exakt wie Server `f"cell_{cell}"`. */
    fun topicFor(lat: Double, lon: Double): String = "cell_${publishCell(lat, lon)}"

    /** Publish-Zelle + 8 Nachbarn als Topics — deckt Rand-/Eckfälle ab, wenn der
     * beobachtete Punkt nahe der Zellgrenze liegt (Server weitet um das Epizentrum,
     * die Nachbarn geben Sicherheitsmarge). */
    fun topicsAround(lat: Double, lon: Double): Set<String> {
        val cLa = floor(lat / PUBLISH_CELL_DEG).toInt()
        val cLo = floor(lon / PUBLISH_CELL_DEG).toInt()
        val out = HashSet<String>(9)
        for (dLa in -1..1) for (dLo in -1..1) out.add("cell_c${cLa + dLa}_${cLo + dLo}")
        return out
    }
}
