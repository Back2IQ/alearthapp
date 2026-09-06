package app.alert2iq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AnonDeviceIdTest {
    @Test fun sameDayKeepsId() {
        val stored = AnonId("AAAA", 20000L)
        val out = AnonDeviceId.rotate(stored, todayEpochDay = 20000L) { "BBBB" }
        assertEquals("AAAA", out.hash)
        assertEquals(20000L, out.dayEpoch)
    }

    @Test fun nextDayRotatesId() {
        val stored = AnonId("AAAA", 20000L)
        val out = AnonDeviceId.rotate(stored, todayEpochDay = 20001L) { "BBBB" }
        assertEquals("BBBB", out.hash)
        assertEquals(20001L, out.dayEpoch)
    }

    @Test fun noStoredIdCreatesOne() {
        val out = AnonDeviceId.rotate(null, todayEpochDay = 20001L) { "CCCC" }
        assertEquals("CCCC", out.hash)
        assertNotEquals("", out.hash)
    }
}
