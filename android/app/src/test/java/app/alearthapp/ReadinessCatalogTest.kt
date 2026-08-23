package app.alearthapp

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadinessCatalogTest {
    @Test fun baseAndQuakeAlwaysApplicable() {
        val out = ReadinessCatalog.applicable(hasPet = false, hasKids = false, hasCar = false)
        assertTrue(out.any { it.group == Group.BASE })
        assertTrue(out.any { it.group == Group.QUAKE })
        assertTrue(out.none { it.group == Group.PET })
        assertTrue(out.none { it.group == Group.KIDS })
        assertTrue(out.none { it.group == Group.CAR })
    }

    @Test fun togglesAddTheirGroups() {
        val out = ReadinessCatalog.applicable(hasPet = true, hasKids = true, hasCar = true)
        assertTrue(out.any { it.group == Group.PET })
        assertTrue(out.any { it.group == Group.KIDS })
        assertTrue(out.any { it.group == Group.CAR })
    }

    @Test fun keysAreUnique() {
        val keys = ReadinessCatalog.ITEMS.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }
}
