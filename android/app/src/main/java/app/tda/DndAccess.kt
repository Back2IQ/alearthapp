package app.tda

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/** Kapselt den „Nicht stören"-Policy-Zugriff (spec TP-3 §"Teil 1", best-effort + Opt-in). */
object DndAccess {
    fun isGranted(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val nm = ctx.getSystemService(NotificationManager::class.java)
        return nm?.isNotificationPolicyAccessGranted == true
    }

    fun requestIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
}
