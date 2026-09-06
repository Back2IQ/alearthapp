package app.alert2iq

import android.content.Context
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

/**
 * Meldet FCM-Token + beobachtete Orte/Schwellen an das Push-Backend (POST /register),
 * damit der Server dieses Gerät bei passenden Beben per FCM wecken kann. No-op, solange
 * kein Backend ([Prefs.backendUrl]) oder kein Token/keine Abos vorliegen — der nächste
 * Sync (Token-Refresh oder Web-Änderung) versucht es erneut.
 */
object PushRegistrar {
    private val client by lazy { OkHttpClient() }
    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun register(ctx: Context) {
        Prefs.init(ctx)
        val base = Prefs.backendUrl.trimEnd('/')
        val token = Prefs.fcmToken
        val subs = Prefs.pushSubscriptions
        if (base.isEmpty() || token.isEmpty() || subs.isEmpty()) return
        val body = runCatching {
            JSONObject(subs).apply {
                put("token", token)
                put("platform", "android")
            }.toString()
        }.getOrNull() ?: return
        val req = Request.Builder()
            .url("$base/register")
            .post(body.toRequestBody(JSON))
            .build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { /* Best-effort. */ }
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }
}
