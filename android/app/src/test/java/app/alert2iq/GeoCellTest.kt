package app.alert2iq

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

    @Test fun publishCellMatchesServerCellId() {
        // parity with server geo/cells.py::cell_id (0.5-degree publish grid)
        assertEquals("c72_72", GeoCell.publishCell(36.2, 36.15))
        assertEquals("c-11_240", GeoCell.publishCell(-5.05, 120.3))
        assertEquals("c0_0", GeoCell.publishCell(0.0, 0.0))
        assertEquals("c105_26", GeoCell.publishCell(52.52, 13.405))
    }

    @Test fun topicForMatchesServerTopic() {
        // server sends f"cell_{cell_id(lat,lon)}"
        assertEquals("cell_c72_72", GeoCell.topicFor(36.2, 36.15))
    }

    @Test fun topicsAroundIsCenterPlusEightNeighbours() {
        val t = GeoCell.topicsAround(36.2, 36.15)
        assertEquals(9, t.size)
        assertEquals(true, t.contains("cell_c72_72"))
        assertEquals(true, t.contains("cell_c71_71"))
        assertEquals(true, t.contains("cell_c73_73"))
    }
}
