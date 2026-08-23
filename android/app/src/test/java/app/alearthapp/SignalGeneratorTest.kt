package app.alearthapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalGeneratorTest {
    @Test fun bufferLengthMatchesDuration() {
        val buf = SignalGenerator.sweepPcm(800.0, 1600.0, 1000)
        assertEquals(SignalGenerator.SAMPLE_RATE, buf.size)
    }

    @Test fun bufferIsNotSilent() {
        val buf = SignalGenerator.sweepPcm(800.0, 1600.0, 200)
        assertTrue(buf.any { it.toInt() != 0 })
    }

    @Test fun amplitudeStaysWithinRange() {
        val buf = SignalGenerator.sweepPcm(600.0, 2400.0, 200, amplitude = 1.0)
        val max = buf.maxOf { kotlin.math.abs(it.toInt()) }
        assertTrue(max <= Short.MAX_VALUE.toInt())
        assertTrue(max > Short.MAX_VALUE.toInt() / 2) // wirklich laut
    }

    @Test fun shortDurationYieldsShortBuffer() {
        val buf = SignalGenerator.sweepPcm(800.0, 1600.0, 100)
        assertEquals(SignalGenerator.SAMPLE_RATE / 10, buf.size)
    }
}
