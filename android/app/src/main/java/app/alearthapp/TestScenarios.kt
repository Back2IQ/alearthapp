package app.alearthapp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Local, network-free test-scenario injector (spec "Testszenario-Injektor (TestScenarios,
 * kein Netz)"). Drives the *real* UI end to end -- EventBus is the only channel, exactly
 * what a live FCM push would feed -- so the whole app is demoable and verifiable without
 * any backend or the (not-yet-wired) Ed25519 payload verification.
 *
 * Fixed demo epicenter: Kahramanmaraş (37.58, 36.93), matching the spec's worked example
 * ("Adana↔Kahramanmaraş ≈ 57 s").
 *
 * Aftershock timing is demo-compressed (seconds, not the real minutes/hours between
 * aftershocks) so the sequence scenario is watchable end to end in under a minute.
 */
object TestScenarios {

    private const val EPICENTER_LAT = 37.58
    private const val EPICENTER_LON = 36.93

    // Long-lived scope so a running scenario chain (aftershocks, P0->P2) survives a
    // Fragment/tab switch, which would otherwise cancel a caller-supplied view scope mid-chain.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var job: Job? = null
    private var counter = 0

    private fun nextId(prefix: String): String {
        counter += 1
        return "$prefix-${System.currentTimeMillis()}-$counter"
    }

    fun cancelAll() {
        job?.cancel()
        job = null
    }

    /** Testbeben: Aufmerksamkeit (t0) -> P0-Alarm (t0+1.5s) -> P2-Bestätigung (t0+6s). */
    fun runEarthquakeDrill(userCity: Eew.City) {
        cancelAll()
        EventBus.resetSequence()
        job = scope.launch {
            val distKm = Eew.haversineKm(userCity.lat, userCity.lon, EPICENTER_LAT, EPICENTER_LON)
            val id = nextId("eq")

            EventBus.setStatus(StatusState.ATTENTION)
            delay(1500)

            // Rupture "began" ~2s before P0 is issued (detection latency), so the
            // countdown on AlertActivity is already ticking down realistically.
            val originTs = System.currentTimeMillis() - 2000
            val p0IssuedTs = System.currentTimeMillis()
            val p0 = Eew.AlarmPayload(
                id = id, state = "issued", tier = Eew.Tier.P0, originTs = originTs,
                lat = EPICENTER_LAT, lon = EPICENTER_LON, depthKm = 10.0, mag = 6.8,
                src = "p0b-phone-cluster", issuedTs = p0IssuedTs
            )
            EventBus.setStatus(StatusState.ALARM_P0)
            EventBus.appendSequence(Eew.SequenceEntry("Mainshock", p0.mag, Eew.mmi(p0.mag, distKm), originTs, true))
            EventBus.setReport(
                ReportData(id, p0.src, p0.mag, p0.depthKm, originTs, p0IssuedTs, null, distKm,
                    Eew.sWaveEtaSeconds(distKm, (p0IssuedTs - originTs) / 1000.0))
            )
            EventBus.emitAlarm(p0)

            delay(4500) // total t0+6s

            val p2IssuedTs = System.currentTimeMillis()
            val p2 = p0.copy(
                state = "confirmed", tier = Eew.Tier.P2, ver = 2, mag = 7.8, magHi = 7.9,
                src = "p0a-instrumental", issuedTs = p2IssuedTs
            )
            EventBus.setStatus(StatusState.CONFIRMED_P2)
            EventBus.setReport(
                ReportData(id, p2.src, p2.mag, p2.depthKm, originTs, p0IssuedTs, p2IssuedTs, distKm,
                    Eew.sWaveEtaSeconds(distKm, (p0IssuedTs - originTs) / 1000.0))
            )
            EventBus.emitAlarm(p2)
        }
    }

    /** Nachbeben-Sequenz: Hauptbeben M7.8 -> M7.5 -> mehrere kleinere; Push nur oberhalb Schwelle. */
    fun runAftershockSequence(userCity: Eew.City) {
        cancelAll()
        EventBus.resetSequence()
        job = scope.launch {
            val distKm = Eew.haversineKm(userCity.lat, userCity.lon, EPICENTER_LAT, EPICENTER_LON)
            val mainId = nextId("seq")

            EventBus.setStatus(StatusState.ATTENTION)
            delay(1200)

            val originTs = System.currentTimeMillis() - 2000
            val p0IssuedTs = System.currentTimeMillis()
            val main = Eew.AlarmPayload(
                id = mainId, state = "issued", tier = Eew.Tier.P0, originTs = originTs,
                lat = EPICENTER_LAT, lon = EPICENTER_LON, depthKm = 12.0, mag = 7.8,
                src = "p0b-phone-cluster", issuedTs = p0IssuedTs
            )
            EventBus.setStatus(StatusState.ALARM_P0)
            EventBus.appendSequence(Eew.SequenceEntry("Mainshock", main.mag, Eew.mmi(main.mag, distKm), originTs, true))
            EventBus.setReport(
                ReportData(mainId, main.src, main.mag, main.depthKm, originTs, p0IssuedTs, null, distKm,
                    Eew.sWaveEtaSeconds(distKm, (p0IssuedTs - originTs) / 1000.0))
            )
            EventBus.emitAlarm(main)

            delay(4000)
            val p2IssuedTs = System.currentTimeMillis()
            val confirmed = main.copy(state = "confirmed", tier = Eew.Tier.P2, ver = 2, src = "p0a-instrumental", issuedTs = p2IssuedTs)
            EventBus.setStatus(StatusState.CONFIRMED_P2)
            EventBus.setReport(
                ReportData(mainId, confirmed.src, confirmed.mag, confirmed.depthKm, originTs, p0IssuedTs, p2IssuedTs, distKm,
                    Eew.sWaveEtaSeconds(distKm, (p0IssuedTs - originTs) / 1000.0))
            )
            EventBus.emitAlarm(confirmed)

            // Demo-compressed aftershock cadence: (magnitude, delay-since-previous seconds).
            val aftershocks = listOf(7.5 to 3.0, 4.2 to 3.0, 5.1 to 4.0, 3.6 to 4.0, 4.8 to 4.0)
            var t = originTs
            for ((mag, gapSec) in aftershocks) {
                delay((gapSec * 1000).toLong())
                t += (gapSec * 1000).toLong()
                val entry = Eew.SequenceEntry("Aftershock", mag, Eew.mmi(mag, distKm), t, false)
                EventBus.appendSequence(entry)
                if (mag >= 5.0) {
                    EventBus.emitPushNotice(entry)
                }
            }
        }
    }

    /** Feuerwerk: „Störung erkannt – kein Alarm" -- spiegelt den Backend-Wellenfront-Filter. */
    fun runFireworksDisturbance() {
        cancelAll()
        job = scope.launch {
            delay(800)
            EventBus.setStatus(StatusState.DISTURBANCE_DISCARDED)
            EventBus.emitDisturbance("firework")
        }
    }

    fun reset() {
        cancelAll()
        EventBus.reset()
    }
}
