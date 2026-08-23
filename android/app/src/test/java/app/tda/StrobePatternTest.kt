package app.tda

import org.junit.Assert.assertTrue
import org.junit.Test

class StrobePatternTest {
    @Test fun torchIsSparserThanScreen() {
        assertTrue(StrobePattern.onFraction(StrobePattern.torch) < StrobePattern.onFraction(StrobePattern.screen))
    }

    @Test fun fractionsAreInUnitRange() {
        for (p in listOf(StrobePattern.screen, StrobePattern.torch)) {
            val f = StrobePattern.onFraction(p)
            assertTrue(f > 0.0 && f < 1.0)
        }
    }

    @Test fun pulsesArePositive() {
        for (p in listOf(StrobePattern.screen, StrobePattern.torch)) {
            assertTrue(p.onMillis > 0 && p.offMillis > 0)
        }
    }
}
