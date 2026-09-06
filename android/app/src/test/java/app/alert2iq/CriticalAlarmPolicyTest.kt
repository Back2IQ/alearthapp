package app.alert2iq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CriticalAlarmPolicyTest {

    @Test fun test_isUnmistakablyDifferent_neverBypassesOrLoops() {
        val p = CriticalAlarmPolicy.plan(Eew.Tier.P2, isTest = true, soundEnabled = true, dndOptIn = true, dndAccessGranted = true)
        assertEquals(AlarmChannelKind.TEST, p.channel)
        assertFalse(p.playAlarmSound)
        assertFalse(p.bypassDnd)
    }

    @Test fun p2_confirmed_playsAlarmWhenSoundOn() {
        val p = CriticalAlarmPolicy.plan(Eew.Tier.P2, isTest = false, soundEnabled = true, dndOptIn = false, dndAccessGranted = false)
        assertEquals(AlarmChannelKind.CRITICAL, p.channel)
        assertTrue(p.playAlarmSound)
        assertTrue(p.vibrate)
        assertTrue(p.fullScreen)
    }

    @Test fun p2_soundOff_vibratesOnly() {
        val p = CriticalAlarmPolicy.plan(Eew.Tier.P2, isTest = false, soundEnabled = false, dndOptIn = true, dndAccessGranted = true)
        assertFalse(p.playAlarmSound)
        assertTrue(p.vibrate)
    }

    @Test fun p0_isMutedVibrationOnly() {
        val p = CriticalAlarmPolicy.plan(Eew.Tier.P0, isTest = false, soundEnabled = true, dndOptIn = true, dndAccessGranted = true)
        assertFalse(p.playAlarmSound) // P0 noch unbestätigt → gedämpft
        assertTrue(p.vibrate)
        assertFalse(p.bypassDnd)
    }

    @Test fun bypassDnd_onlyWithOptInAndAccess() {
        val base = { optIn: Boolean, access: Boolean ->
            CriticalAlarmPolicy.plan(Eew.Tier.P2, isTest = false, soundEnabled = true, dndOptIn = optIn, dndAccessGranted = access).bypassDnd
        }
        assertTrue(base(true, true))
        assertFalse(base(true, false))
        assertFalse(base(false, true))
        assertFalse(base(false, false))
    }
}
