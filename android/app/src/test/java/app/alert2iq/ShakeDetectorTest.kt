package app.alert2iq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShakeDetectorTest {
    private val g = 9.81

    @Test fun quietGivesNoTrigger() {
        val det = ShakeDetector(SensorConfig())
        for (i in 0 until 100) assertNull(det.onSample(i * 20L, g + 0.01))
    }

    @Test fun sustainedShakeFiresOnceAtFirstSample() {
        val det = ShakeDetector(SensorConfig(shakeMinSamples = 3, refractoryMs = 30_000))
        assertNull(det.onSample(1000L, g + 2.0))   // sample 1 above delta
        assertNull(det.onSample(1020L, g + 2.0))   // sample 2
        val fired = det.onSample(1040L, g + 2.0)   // sample 3 -> trigger
        assertEquals(1000L, fired)                 // trigger_ms = first shaky sample
    }

    @Test fun refractorySuppressesImmediateSecondTrigger() {
        val det = ShakeDetector(SensorConfig(shakeMinSamples = 3, refractoryMs = 30_000))
        det.onSample(1000L, g + 2.0); det.onSample(1020L, g + 2.0); det.onSample(1040L, g + 2.0)
        // more shaking within refractory window -> no new trigger
        for (i in 0 until 5) assertNull(det.onSample(2000L + i * 20L, g + 2.0))
    }

    @Test fun singleSpikeDoesNotFire() {
        val det = ShakeDetector(SensorConfig(shakeMinSamples = 3))
        assertNull(det.onSample(1000L, g + 5.0))   // one tap
        assertNull(det.onSample(1020L, g + 0.01))  // back to quiet
        assertNull(det.onSample(1040L, g + 0.01))
    }
}
