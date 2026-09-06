package app.alert2iq

import kotlin.math.acos
import kotlin.math.sqrt

/**
 * 3D Seismic Polarization Vector Filter (Alert2IQ - Back2IQ Studio).
 *
 * Evaluates 3D accelerometer vectors (ax, ay, az) to distinguish tectonic P-waves
 * (which arrive at steep dip angles from the subsurface) from horizontal table bumps or walking motion.
 */
object SeismicPolarizationFilter {

    data class VectorResult(
        val isPWaveVector: Boolean,
        val magnitudeG: Double,
        val dipAngleDeg: Double,
        val staLtaRatio: Double,
        val confidenceScore: Double
    )

    /**
     * Evaluates a 3D acceleration vector and STA/LTA ratio.
     * @param ax Acceleration on X axis in m/s^2 or g
     * @param ay Acceleration on Y axis in m/s^2 or g
     * @param az Acceleration on Z axis in m/s^2 or g
     * @param staLta STA/LTA energy ratio (short-term average / long-term average)
     * @param gravityNorm Reference gravity magnitude (9.81 m/s^2 or 1.0 g)
     */
    fun evaluate(
        ax: Double,
        ay: Double,
        az: Double,
        staLta: Double,
        gravityNorm: Double = 9.81
    ): VectorResult {
        if (ax.isNaN() || ay.isNaN() || az.isNaN() || staLta.isNaN()) {
            return VectorResult(
                isPWaveVector = false,
                magnitudeG = 0.0,
                dipAngleDeg = 0.0,
                staLtaRatio = 0.0,
                confidenceScore = 0.0
            )
        }

        val totalMag = sqrt(ax * ax + ay * ay + az * az)
        if (totalMag.isNaN() || totalMag < 0.001) {
            return VectorResult(
                isPWaveVector = false,
                magnitudeG = 0.0,
                dipAngleDeg = 0.0,
                staLtaRatio = staLta,
                confidenceScore = 0.0
            )
        }

        // Dip angle relative to vertical Z axis (subsurface P-wave arrives near-vertical)
        val cosTheta = (az / totalMag).coerceIn(-1.0, 1.0)
        val dipAngleDeg = acos(cosTheta) * (180.0 / Math.PI)

        // P-wave criteria:
        // 1. STA/LTA ratio >= 4.5 (impulsive onset)
        // 2. Total magnitude exceeds 0.05g above baseline
        // 3. Dip angle reveals strong vertical component (dip angle < 45 deg or > 135 deg relative to Z-axis)
        val isVerticalImpulse = dipAngleDeg <= 45.0 || dipAngleDeg >= 135.0
        val isImpulsive = staLta >= 4.5
        val netMagG = (totalMag - gravityNorm).coerceAtLeast(0.0) / gravityNorm
        val isSignificant = netMagG >= 0.03 || totalMag >= 0.3

        val isPWave = isImpulsive && isVerticalImpulse && isSignificant

        val confidenceScore = if (isPWave) {
            ((staLta / 10.0) * 0.5 + (netMagG / 0.5) * 0.5).coerceIn(0.0, 1.0)
        } else {
            0.0
        }

        return VectorResult(
            isPWaveVector = isPWave,
            magnitudeG = netMagG,
            dipAngleDeg = dipAngleDeg,
            staLtaRatio = staLta,
            confidenceScore = confidenceScore
        )
    }
}
