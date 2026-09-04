package app.alearthapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartReadinessTest {

    @Test
    fun testExpiryDetection() {
        val pastItem = ReadinessSupplyItem("1", "Old Water", "WATER", System.currentTimeMillis() - 10000)
        val futureItem = ReadinessSupplyItem("2", "Fresh Water", "WATER", System.currentTimeMillis() + 100000000)

        assertTrue(pastItem.isExpired())
        assertFalse(futureItem.isExpired())
    }

    @Test
    fun testExpiringSoonThreshold() {
        val now = System.currentTimeMillis()
        val item10Days = ReadinessSupplyItem("1", "Meds", "MEDICINE", now + (10L * 24 * 60 * 60 * 1000))
        val item40Days = ReadinessSupplyItem("2", "Rations", "FOOD", now + (40L * 24 * 60 * 60 * 1000))

        assertTrue(item10Days.isExpiringSoon(30, now))
        assertFalse(item40Days.isExpiringSoon(30, now))
    }
}
