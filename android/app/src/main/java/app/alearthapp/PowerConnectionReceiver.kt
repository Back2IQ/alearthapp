package app.alearthapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Koppelt den [QuakeSensorService] ans Ladekabel: Strom rein → Service an,
 * Strom raus → Service aus. Nur wenn der Nutzer das Mithelfen aktiviert hat.
 */
class PowerConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Prefs.init(context)
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED ->
                if (Prefs.crowdsourcingEnabled) QuakeSensorService.start(context)
            Intent.ACTION_POWER_DISCONNECTED ->
                QuakeSensorService.stop(context)
        }
    }
}
