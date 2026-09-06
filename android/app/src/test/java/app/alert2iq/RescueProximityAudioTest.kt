package app.alert2iq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RescueProximityAudioTest {

    @Test
    fun testPingIntervalProgression() {
        assertEquals(100L, RescueProximityAudio.calculatePingIntervalMs(1.2))
        assertEquals(200L, RescueProximityAudio.calculatePingIntervalMs(3.5))
        assertEquals(450L, RescueProximityAudio.calculatePingIntervalMs(10.0))
        assertEquals(1000L, RescueProximityAudio.calculatePingIntervalMs(22.0))
        assertEquals(2000L, RescueProximityAudio.calculatePingIntervalMs(45.0))
    }

    @Test
    fun testSelectPriorityBeaconPrioritisesTrapped() {
        val bOk = DiscoveredBeacon(
            deviceAddress = "ADDR_OK",
            message = BleSosMessage(BleSosStatus.OK, 80, 1000L, 36.0f, 37.0f),
            rssi = -60,
            estimatedDistanceMeters = 2.0
        )
        val bInjured = DiscoveredBeacon(
            deviceAddress = "ADDR_INJURED",
            message = BleSosMessage(BleSosStatus.INJURED, 75, 1000L, 36.0f, 37.0f),
            rssi = -70,
            estimatedDistanceMeters = 5.0
        )
        val bTrapped = DiscoveredBeacon(
            deviceAddress = "ADDR_TRAPPED",
            message = BleSosMessage(BleSosStatus.TRAPPED, 90, 1000L, 36.0f, 37.0f),
            rssi = -80,
            estimatedDistanceMeters = 12.0
        )

        val target = RescueProximityAudio.selectPriorityBeacon(listOf(bOk, bInjured, bTrapped))
        assertNotNull(target)
        assertEquals("ADDR_TRAPPED", target?.deviceAddress)
        assertEquals(BleSosStatus.TRAPPED, target?.message?.status)
    }

    @Test
    fun testSelectPriorityBeaconFallsBackToInjured() {
        val bOk = DiscoveredBeacon(
            deviceAddress = "ADDR_OK",
            message = BleSosMessage(BleSosStatus.OK, 80, 1000L, 36.0f, 37.0f),
            rssi = -60,
            estimatedDistanceMeters = 2.0
        )
        val bInjured = DiscoveredBeacon(
            deviceAddress = "ADDR_INJURED",
            message = BleSosMessage(BleSosStatus.INJURED, 75, 1000L, 36.0f, 37.0f),
            rssi = -70,
            estimatedDistanceMeters = 5.0
        )

        val target = RescueProximityAudio.selectPriorityBeacon(listOf(bOk, bInjured))
        assertNotNull(target)
        assertEquals("ADDR_INJURED", target?.deviceAddress)
    }

    @Test
    fun testSelectPriorityBeaconEmpty() {
        assertNull(RescueProximityAudio.selectPriorityBeacon(emptyList()))
    }

    @Test
    fun testPcmGenerationNonEmpty() {
        val pcm = RescueProximityAudio.generatePingPcm(1200.0, 45, 44100)
        assertTrue(pcm.isNotEmpty())
        assertEquals((44100 * 45) / 1000, pcm.size)
    }
}
