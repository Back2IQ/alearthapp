package app.alearthapp

import kotlin.math.floor

/**
 * Rundet eine Rohposition auf die anonyme Detektionszelle (0,1°-Raster).
 * MUSS byte-identische Strings zu `p0b/signals.py::coarsen_cell` liefern —
 * nur diese Zell-ID verlässt das Gerät, nie die Rohkoordinate.
 */
object GeoCell {
    const val DETECT_CELL_DEG = 0.1

    fun coarsenCell(lat: Double, lon: Double): String {
        val la = floor(lat / DETECT_CELL_DEG).toInt()
        val lo = floor(lon / DETECT_CELL_DEG).toInt()
        return "d${la}_$lo"
    }
}
