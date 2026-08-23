package app.tda

/** Welcher Notification-Kanal/Ton für einen Alarm gilt (spec TP-3 §"Teil 1"). Reine Logik. */
enum class AlarmChannelKind { CRITICAL, TEST }

data class AlarmPlan(
    val channel: AlarmChannelKind,
    val playAlarmSound: Boolean,
    val vibrate: Boolean,
    val bypassDnd: Boolean,
    val fullScreen: Boolean
)

/**
 * Entscheidet, wie ein Alarm zugestellt wird. P2 (bestätigt, lebensrettend) darf laut sein
 * und — mit Opt-in + gewährtem Policy-Zugriff — volles DND durchbrechen; P0 bleibt gedämpft
 * (noch unbestätigt); TEST ist unverwechselbar (kein Alarmschleifen-Ton, nie DND-Durchbruch).
 */
object CriticalAlarmPolicy {
    fun plan(
        tier: Eew.Tier,
        isTest: Boolean,
        soundEnabled: Boolean,
        dndOptIn: Boolean,
        dndAccessGranted: Boolean
    ): AlarmPlan {
        if (isTest) {
            return AlarmPlan(AlarmChannelKind.TEST, playAlarmSound = false, vibrate = true, bypassDnd = false, fullScreen = true)
        }
        val confirmed = tier == Eew.Tier.P2
        val sound = confirmed && soundEnabled
        return AlarmPlan(
            channel = AlarmChannelKind.CRITICAL,
            playAlarmSound = sound,
            vibrate = true,
            bypassDnd = confirmed && dndOptIn && dndAccessGranted,
            fullScreen = true
        )
    }
}
