package app.alearthapp

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Status-chip state shown on the start screen (spec §"Startscreen"). */
enum class StatusState { READY, ATTENTION, ALARM_P0, CONFIRMED_P2, DISTURBANCE_DISCARDED }

/** Everything ReportActivity needs to render the "last event" timeline. */
data class ReportData(
    val eventId: String,
    val src: String,
    val mag: Double,
    val depthKm: Double,
    val originTs: Long,
    val p0IssuedTs: Long,
    val p2IssuedTs: Long?,
    val userDistKm: Double,
    val sWaveEtaAtP0Sec: Double
)

/**
 * In-process pub/sub connecting [TestScenarios] (the local injector) to MainActivity,
 * AlertActivity and ReportActivity. This stands in for what would otherwise be an
 * FCM push + local broadcast pipeline -- deliberately process-local, no network
 * (spec "Testszenario-Injektor (kein Netz)").
 */
object EventBus {
    private val _status = MutableStateFlow(StatusState.READY)
    val status: StateFlow<StatusState> = _status.asStateFlow()

    private val _alarm = MutableSharedFlow<Eew.AlarmPayload>(replay = 1, extraBufferCapacity = 8)
    val alarm: SharedFlow<Eew.AlarmPayload> = _alarm.asSharedFlow()

    private val _sequence = MutableStateFlow<List<Eew.SequenceEntry>>(emptyList())
    val sequence: StateFlow<List<Eew.SequenceEntry>> = _sequence.asStateFlow()

    private val _pushNotice = MutableSharedFlow<Eew.SequenceEntry>(extraBufferCapacity = 8)
    val pushNotice: SharedFlow<Eew.SequenceEntry> = _pushNotice.asSharedFlow()

    private val _disturbance = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val disturbance: SharedFlow<String> = _disturbance.asSharedFlow()

    private val _report = MutableStateFlow<ReportData?>(null)
    val report: StateFlow<ReportData?> = _report.asStateFlow()

    fun setStatus(s: StatusState) { _status.value = s }

    suspend fun emitAlarm(p: Eew.AlarmPayload) { _alarm.emit(p) }

    /** Non-suspending variant for callers without a coroutine scope (e.g. OkHttp's
     * WebSocket listener callbacks, which run on OkHttp's own dispatcher thread). */
    fun tryEmitAlarm(p: Eew.AlarmPayload) { _alarm.tryEmit(p) }

    fun appendSequence(e: Eew.SequenceEntry) { _sequence.value = _sequence.value + e }
    fun resetSequence() { _sequence.value = emptyList() }

    suspend fun emitPushNotice(e: Eew.SequenceEntry) { _pushNotice.emit(e) }

    suspend fun emitDisturbance(reasonKey: String) { _disturbance.emit(reasonKey) }

    fun setReport(r: ReportData?) { _report.value = r }

    /** Dedup guard so MainActivity only opens AlertActivity once per new alarm (ver==1),
     * even though the underlying SharedFlow replays its last value to late subscribers
     * (e.g. after a theme/locale-triggered recreate). Escalation updates (ver>1) for an
     * already-open alert are picked up by AlertActivity's own collector instead. */
    var lastLaunchedAlarmId: String? = null

    fun reset() {
        _status.value = StatusState.READY
        _sequence.value = emptyList()
        _report.value = null
        lastLaunchedAlarmId = null
    }
}
