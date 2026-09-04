package app.alearthapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoEmergencyBroadcastTest {

    @Test
    fun testThrottleIntervalEnforced() {
        assertEquals(5 * 60 * 1000L, AutoEmergencyBroadcast.MIN_BROADCAST_INTERVAL_MS)
    }
}
