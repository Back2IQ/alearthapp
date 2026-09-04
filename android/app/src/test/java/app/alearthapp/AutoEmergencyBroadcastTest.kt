package app.alearthapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoEmergencyBroadcastTest {

    @Test
    fun testThrottleIntervalEnforced() {
        assertEquals(30 * 60 * 1000L, AutoEmergencyBroadcast.INITIAL_DELAY_MS)
        assertEquals(5 * 60 * 60 * 1000L, AutoEmergencyBroadcast.REPEAT_INTERVAL_MS)
    }

    @Test
    fun testBleDutyCycleTiming() {
        assertEquals(30 * 60 * 1000L, BleEmergencyBeacon.INITIAL_DELAY_MS)
        assertEquals(30 * 60 * 1000L, BleEmergencyBeacon.CYCLE_INTERVAL_MS)
        assertEquals(60 * 1000L, BleEmergencyBeacon.BURST_DURATION_MS)
    }
}
