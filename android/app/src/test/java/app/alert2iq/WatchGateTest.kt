package app.alert2iq

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchGateTest {

    @Test fun doesNotFeedShakeBeforeFirstSettle() {
        val gate = WatchGate()
        assertFalse(gate.update(Stillness.UNSETTLED))
    }

    @Test fun feedsShakeOnceSettled() {
        val gate = WatchGate()
        assertTrue(gate.update(Stillness.SETTLED))
    }

    @Test fun keepsFeedingShakeAfterSettleEvenWhenShakingUnsettlesIt() {
        // Dies ist genau der Bug: nach dem ersten SETTLED darf ein nachfolgendes
        // UNSETTLED (durch das Beben selbst ausgelöst) den ShakeDetector nicht
        // mehr abschneiden.
        val gate = WatchGate()
        assertTrue(gate.update(Stillness.SETTLED))
        assertTrue(gate.update(Stillness.UNSETTLED))
    }
}
