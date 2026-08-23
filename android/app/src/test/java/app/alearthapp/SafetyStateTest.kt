package app.alearthapp

import org.junit.Assert.assertEquals
import org.junit.Test

class SafetyStateTest {
    private fun cfg(enabled: Boolean = true, threshold: Int = 7) =
        SafetyConfig(beaconEnabled = enabled, mmiThreshold = threshold, graceMillis = 300_000, deadmanMillis = 60_000)

    @Test fun belowThreshold_staysIdle() {
        val s = SafetyState(cfg())
        assertEquals(SafetyPhase.IDLE, s.onConfirmedQuake(6.9))
    }

    @Test fun disabled_staysIdle() {
        val s = SafetyState(cfg(enabled = false))
        assertEquals(SafetyPhase.IDLE, s.onConfirmedQuake(9.0))
    }

    @Test fun qualifyingQuake_arms() {
        val s = SafetyState(cfg())
        assertEquals(SafetyPhase.ARMED, s.onConfirmedQuake(7.0))
    }

    @Test fun graceThenCountdown_leadsToBeacon() {
        val s = SafetyState(cfg())
        s.onConfirmedQuake(7.5)
        assertEquals(SafetyPhase.ASKING, s.onGraceElapsed())
        assertEquals(SafetyPhase.BEACON, s.onCountdownElapsed())
    }

    @Test fun userSafeDuringAsking_goesToWatch() {
        val s = SafetyState(cfg())
        s.onConfirmedQuake(7.5); s.onGraceElapsed()
        assertEquals(SafetyPhase.WATCH, s.onUserSafe())
    }

    @Test fun userHelpDuringAsking_goesToBeacon() {
        val s = SafetyState(cfg())
        s.onConfirmedQuake(7.5); s.onGraceElapsed()
        assertEquals(SafetyPhase.BEACON, s.onUserHelp())
    }

    @Test fun userSafeStopsActiveBeacon() {
        val s = SafetyState(cfg())
        s.onConfirmedQuake(7.5); s.onGraceElapsed(); s.onCountdownElapsed()
        assertEquals(SafetyPhase.WATCH, s.onUserSafe())
    }

    @Test fun graceIgnoredWhenNotArmed() {
        val s = SafetyState(cfg())
        assertEquals(SafetyPhase.IDLE, s.onGraceElapsed())
    }

    @Test fun resetReturnsToIdle() {
        val s = SafetyState(cfg())
        s.onConfirmedQuake(7.5)
        s.reset()
        assertEquals(SafetyPhase.IDLE, s.phase)
    }
}
