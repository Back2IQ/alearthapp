package app.alearthapp

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
 * Meldet anonyme Crowdsourcing-Signale an den Server: eine Erschütterung
 * (POST /trigger) und periodische „ich horche"-Pings (POST /ping). Best-effort,
 * kein Retry (eine späte Meldung nützt nichts). No-op ohne [Prefs.backendUrl].
 * Nur die Zell-ID wird gesendet, nie Rohkoordinaten (Aufrufer rundet vorher).
 */
object CrowdReport {
    private val client by lazy { OkHttpClient() }
    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun triggerJson(deviceHash: String, cell: String, triggerMs: Long, clockUncMs: Long): String =
        JSONObject()
            .put("device_hash", deviceHash)
            .put("cell", cell)
            .put("trigger_ms", triggerMs.toString())
            .put("clock_unc_ms", clockUncMs.toString())
            .toString()

    fun pingJson(deviceHash: String, cell: String, pingMs: Long): String =
        JSONObject()
            .put("device_hash", deviceHash)
            .put("cell", cell)
            .put("ping_ms", pingMs.toString())
            .toString()

    fun postTrigger(ctx: Context, deviceHash: String, cell: String,
                    triggerMs: Long, clockUncMs: Long) =
        post(ctx, "/trigger", triggerJson(deviceHash, cell, triggerMs, clockUncMs))

    fun postPing(ctx: Context, deviceHash: String, cell: String, pingMs: Long) =
        post(ctx, "/ping", pingJson(deviceHash, cell, pingMs))

    private fun post(ctx: Context, path: String, body: String) {
        Prefs.init(ctx)
        val base = Prefs.backendUrl.trimEnd('/')
        if (base.isEmpty()) return
        val req = Request.Builder().url("$base$path").post(body.toRequestBody(JSON)).build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { /* best-effort */ }
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }
}
