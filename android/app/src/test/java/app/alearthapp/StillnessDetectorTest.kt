package app.alearthapp

import org.junit.Assert.assertEquals
import org.junit.Test

class StillnessDetectorTest {
    private val g = 9.81

    @Test fun becomesSettledAfterQuietWindow() {
        val det = StillnessDetector(SensorConfig(settleMs = 1000), windowSize = 10)
        var state = Stillness.UNSETTLED
        // 200 quiet samples at 50ms spacing = 10s of stillness, tiny noise
        for (i in 0 until 200) {
            val jitter = if (i % 2 == 0) 0.001 else -0.001
            state = det.onSample(i * 50L, g + jitter)
        }
        assertEquals(Stillness.SETTLED, state)
    }

    @Test fun motionResetsToUnsettled() {
        val det = StillnessDetector(SensorConfig(settleMs = 1000), windowSize = 10)
        for (i in 0 until 200) det.onSample(i * 50L, g + 0.001)
        // a big jolt: fill the window with high-variance samples
        var state = Stillness.SETTLED
        for (i in 200 until 220) {
            val spike = if (i % 2 == 0) g + 3.0 else g - 3.0
            state = det.onSample(i * 50L, spike)
        }
        assertEquals(Stillness.UNSETTLED, state)
    }
}
