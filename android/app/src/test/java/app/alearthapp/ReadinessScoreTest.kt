package app.alearthapp

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadinessScoreTest {
    private val items = listOf(
        PrepItem("a", "qa", Group.BASE),
        PrepItem("b", "qb", Group.BASE),
        PrepItem("c", "qc", Group.QUAKE),
        PrepItem("d", "qd", Group.QUAKE),
    )

    @Test fun emptyOwnedIsZero() {
        val r = ReadinessScore.compute(items, emptySet())
        assertEquals(0, r.score); assertEquals(4, r.missingCount); assertEquals(4, r.total)
    }

    @Test fun allOwnedIsHundred() {
        val r = ReadinessScore.compute(items, setOf("a", "b", "c", "d"))
        assertEquals(100, r.score); assertEquals(0, r.missingCount)
    }

    @Test fun halfOwnedIsFifty() {
        val r = ReadinessScore.compute(items, setOf("a", "b"))
        assertEquals(50, r.score); assertEquals(2, r.missingCount)
    }

    @Test fun ownedKeysNotInListDoNotCount() {
        val r = ReadinessScore.compute(items, setOf("a", "zzz"))
        assertEquals(25, r.score); assertEquals(3, r.missingCount)
    }

    @Test fun emptyListIsZeroNotCrash() {
        val r = ReadinessScore.compute(emptyList(), setOf("a"))
        assertEquals(0, r.score); assertEquals(0, r.missingCount); assertEquals(0, r.total)
    }
}
