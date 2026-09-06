package app.alert2iq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeismicPolarizationFilterTest {

    @Test
    fun `vertical tectonic P-wave vector triggers valid P-wave classification`() {
        // Near-vertical Z-axis acceleration + high STA/LTA ratio >= 4.5
        val res = SeismicPolarizationFilter.evaluate(
            ax = 0.5,
            ay = 0.5,
            az = 14.5,
            staLta = 5.2,
            gravityNorm = 9.81
        )
        assertTrue(res.isPWaveVector)
        assertTrue(res.confidenceScore > 0.0)
        assertTrue(res.dipAngleDeg < 45.0)
    }

    @Test
    fun `horizontal table bump is rejected`() {
        // Strong horizontal acceleration on X/Y axis with shallow dip angle
        val res = SeismicPolarizationFilter.evaluate(
            ax = 12.0,
            ay = 10.0,
            az = 9.81,
            staLta = 5.2,
            gravityNorm = 9.81
        )
        assertFalse(res.isPWaveVector)
        assertEquals(0.0, res.confidenceScore, 0.001)
    }

    @Test
    fun `low STA LTA ratio is rejected`() {
        // Low impulsive energy ratio
        val res = SeismicPolarizationFilter.evaluate(
            ax = 0.1,
            ay = 0.1,
            az = 10.2,
            staLta = 2.1,
            gravityNorm = 9.81
        )
        assertFalse(res.isPWaveVector)
    }
}
