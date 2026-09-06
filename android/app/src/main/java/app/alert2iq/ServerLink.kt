package app.alert2iq

import android.util.Log
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/** Chip states for the "Mit Server verbinden" section on the start screen. */
enum class ServerConnState { DISCONNECTED, CONNECTING, CONNECTED, FAILED }

/**
 * WebSocket bridge to the local TDA backend (spec "Server-Verbindungsmodus"). Owns the
 * OkHttp connection, sends `simulate` commands, and Ed25519-verifies ([Signing]) every
 * inbound `alert` payload BEFORE handing it to [EventBus] -- this is the only path by
 * which alerts from the network can reach the existing alarm UI. Payloads that fail
 * verification are dropped and only surface via [invalidSignature].
 *
 * The local [TestScenarios] injector is untouched and remains fully independent of this
 * object.
 */
object ServerLink {

    private const val TAG = "ServerLink"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    private var socket: WebSocket? = null

    private val _connState = MutableStateFlow(ServerConnState.DISCONNECTED)
    val connState: StateFlow<ServerConnState> = _connState.asStateFlow()

    /** pub_key announced by the server's "hello" message -- DISPLAY/COMPARISON ONLY.
     * Verification always uses the embedded [Signing.SERVER_PUBLIC_KEY_B64], never this. */
    private val _helloPubKey = MutableStateFlow<String?>(null)
    val helloPubKey: StateFlow<String?> = _helloPubKey.asStateFlow()

    /** Fires once per alert payload whose signature failed verification (dropped). */
    private val _invalidSignature = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val invalidSignature: SharedFlow<Unit> = _invalidSignature.asSharedFlow()

    /** Tracks the first P0 issued_ts per event id, so a later P2 for the same event can
     * still report an accurate detection timestamp (mirrors what [TestScenarios] does
     * locally with its own explicit state machine). Small and unbounded-but-harmless for
     * the lifetime of a debug/demo session. */
    private val p0IssuedTsByEvent = mutableMapOf<String, Long>()

    fun connect(url: String) {
        disconnect()
        _connState.value = ServerConnState.CONNECTING
        val request = try {
            Request.Builder().url(url).build()
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Invalid server URL: $url", e)
            _connState.value = ServerConnState.FAILED
            return
        }
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connState.value = ServerConnState.CONNECTED
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connState.value = ServerConnState.DISCONNECTED
                _helloPubKey.value = null
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket failure", t)
                _connState.value = ServerConnState.FAILED
                _helloPubKey.value = null
            }
        })
    }

    fun disconnect() {
        socket?.close(1000, "client disconnect")
        socket = null
        p0IssuedTsByEvent.clear()
        _helloPubKey.value = null
        _connState.value = ServerConnState.DISCONNECTED
    }

    /** Sends `{"cmd":"simulate","kind":kind}` (kind = "quake" or "firework"). No-op if
     * not currently connected. */
    fun sendSimulate(kind: String) {
        val msg = JSONObject().put("cmd", "simulate").put("kind", kind)
        socket?.send(msg.toString())
    }

    private fun handleMessage(text: String) {
        val msg = try {
            JSONObject(text)
        } catch (e: Exception) {
            Log.w(TAG, "Malformed WS message, ignored", e)
            return
        }
        when (msg.optString("type")) {
            "hello" -> _helloPubKey.value = if (msg.has("pub_key")) msg.optString("pub_key") else null
            "alert" -> {
                val payloadJson = msg.optJSONObject("payload") ?: return
                handleAlert(payloadJson)
            }
        }
    }

    private fun handleAlert(payloadJson: JSONObject) {
        val fields = mutableMapOf<String, String>()
        val keys = payloadJson.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            fields[k] = payloadJson.optString(k, "")
        }

        if (!Signing.verify(fields)) {
            Log.w(TAG, "Alert payload signature invalid -- discarded")
            _invalidSignature.tryEmit(Unit)
            return
        }

        val payload = toAlarmPayload(fields)
        if (payload == null) {
            Log.w(TAG, "Alert payload well-signed but malformed -- discarded")
            return
        }

        publishVerifiedAlarm(payload)
    }

    private fun publishVerifiedAlarm(payload: Eew.AlarmPayload) {
        val status = when (payload.tier) {
            Eew.Tier.P0 -> StatusState.ALARM_P0
            Eew.Tier.P1 -> StatusState.ATTENTION
            Eew.Tier.P2 -> StatusState.CONFIRMED_P2
        }
        EventBus.setStatus(status)

        val city = Prefs.selectedCity()
        val distKm = Eew.haversineKm(city.lat, city.lon, payload.lat, payload.lon)
        if (payload.tier == Eew.Tier.P0) {
            p0IssuedTsByEvent[payload.id] = payload.issuedTs
        }
        val p0IssuedTs = p0IssuedTsByEvent[payload.id] ?: payload.issuedTs
        val p2IssuedTs = if (payload.tier == Eew.Tier.P2) payload.issuedTs else null
        EventBus.setReport(
            ReportData(
                eventId = payload.id,
                src = payload.src,
                mag = payload.mag,
                depthKm = payload.depthKm,
                originTs = payload.originTs,
                p0IssuedTs = p0IssuedTs,
                p2IssuedTs = p2IssuedTs,
                userDistKm = distKm,
                sWaveEtaAtP0Sec = Eew.sWaveEtaSeconds(distKm, (p0IssuedTs - payload.originTs) / 1000.0)
            )
        )

        EventBus.tryEmitAlarm(payload)
    }

    /** Parses the server's string-map payload into [Eew.AlarmPayload]. Returns null on
     * any missing/unparseable required field rather than throwing. */
    private fun toAlarmPayload(f: Map<String, String>): Eew.AlarmPayload? = try {
        Eew.AlarmPayload(
            v = f["v"] ?: "1",
            id = f.getValue("id"),
            ver = f["ver"]?.toIntOrNull() ?: 1,
            state = f["state"] ?: "",
            tier = Eew.Tier.valueOf(f.getValue("tier")),
            test = f["test"] == "1" || f["test"]?.toBooleanStrictOrNull() == true,
            originTs = f.getValue("origin_ts").toLong(),
            lat = f.getValue("lat").toDouble(),
            lon = f.getValue("lon").toDouble(),
            depthKm = f["depth_km"]?.toDoubleOrNull() ?: 0.0,
            mag = f.getValue("mag").toDouble(),
            magHi = f["mag_hi"]?.toDoubleOrNull(),
            src = f["src"] ?: "",
            issuedTs = f["issued_ts"]?.toLongOrNull() ?: System.currentTimeMillis()
        )
    } catch (e: Exception) {
        Log.w(TAG, "Failed to parse alarm payload", e)
        null
    }
}
