package app.alearthapp

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoCellTest {
    @Test fun matchesPythonReferenceValues() {
        // parity with server coarsen_cell (p0b/signals.py test values)
        assertEquals("d410_289", GeoCell.coarsenCell(41.02, 28.97))
        assertEquals("d410_289", GeoCell.coarsenCell(41.08, 28.93))
    }

    @Test fun handlesNegativeCoordinates() {
        // floor rounds toward negative infinity, like Python math.floor
        assertEquals("d-1_-1", GeoCell.coarsenCell(-0.05, -0.05))
    }
}
