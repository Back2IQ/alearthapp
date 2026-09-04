package app.alearthapp

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Akustisches Nahbereichs-Peilsignal für Ersthelfer und Verschütteten-Ortung.
 * Moduliert Ping-Frequenz (Intervall) und Tonhöhe je nach Distanz und Triage-Status
 * des am nächsten gelegenen Notfall-Beacons (Geiger-Müller-Peilprinzip).
 */
class RescueProximityAudio {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var pingJob: Job? = null

    @Volatile
    var isMuted: Boolean = false

    @Volatile
    private var currentBeacons: List<DiscoveredBeacon> = emptyList()

    companion object {
        const val SAMPLE_RATE = 44100
        const val PING_DURATION_MS = 45

        /**
         * Berechnet das Ping-Intervall in Millisekunden basierend auf der Distanz in Metern.
         * < 2m: Dauer-Ping (100ms)
         * 2m - 5m: 200ms
         * 5m - 15m: 450ms
         * 15m - 30m: 1000ms
         * > 30m: 2000ms
         */
        fun calculatePingIntervalMs(distanceMeters: Double): Long {
            return when {
                distanceMeters <= 0.0 -> 2000L
                distanceMeters < 2.0 -> 100L
                distanceMeters < 5.0 -> 200L
                distanceMeters < 15.0 -> 450L
                distanceMeters < 30.0 -> 1000L
                else -> 2000L
            }
        }

        /**
         * Ermittelt das priorisierte Ziel-Beacon (TRAPPED > INJURED > OK, bei gleichem Status das Nächste).
         */
        fun selectPriorityBeacon(beacons: List<DiscoveredBeacon>): DiscoveredBeacon? {
            if (beacons.isEmpty()) return null
            val trapped = beacons.filter { it.message.status == BleSosStatus.TRAPPED }
            if (trapped.isNotEmpty()) {
                return trapped.minByOrNull { it.estimatedDistanceMeters }
            }
            val injured = beacons.filter { it.message.status == BleSosStatus.INJURED }
            if (injured.isNotEmpty()) {
                return injured.minByOrNull { it.estimatedDistanceMeters }
            }
            return beacons.minByOrNull { it.estimatedDistanceMeters }
        }

        /**
         * Frequenz in Hz je nach Status.
         * TRAPPED: 1200 Hz (akuter Gefahrenton)
         * INJURED: 880 Hz (A4)
         * OK: 587 Hz (D5)
         */
        fun getFrequencyHz(status: BleSosStatus): Double {
            return when (status) {
                BleSosStatus.TRAPPED -> 1200.0
                BleSosStatus.INJURED -> 880.0
                BleSosStatus.OK -> 587.0
            }
        }

        /**
         * Erzeugt synthetische Sinuswellen-PCM-Audiodaten (16-bit Mono).
         */
        fun generatePingPcm(frequencyHz: Double, durationMs: Int = PING_DURATION_MS, sampleRate: Int = SAMPLE_RATE): ShortArray {
            val numSamples = (sampleRate * durationMs) / 1000
            val pcm = ShortArray(numSamples)
            val attackSamples = (sampleRate * 0.005).toInt().coerceAtLeast(1)
            val releaseSamples = (sampleRate * 0.010).toInt().coerceAtLeast(1)

            for (i in 0 until numSamples) {
                val angle = 2.0 * PI * i * (frequencyHz / sampleRate)
                var amplitude = sin(angle)

                // Hüllkurve (Envelope) gegen Knacken
                if (i < attackSamples) {
                    amplitude *= (i.toDouble() / attackSamples)
                } else if (i > numSamples - releaseSamples) {
                    amplitude *= ((numSamples - i).toDouble() / releaseSamples)
                }

                pcm[i] = (amplitude * Short.MAX_VALUE * 0.8).toInt().toShort()
            }
            return pcm
        }
    }

    fun updateBeacons(beacons: List<DiscoveredBeacon>) {
        currentBeacons = beacons
    }

    fun start() {
        if (pingJob?.isActive == true) return

        pingJob = scope.launch {
            var audioTrack: AudioTrack? = null
            try {
                val bufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(SAMPLE_RATE * PING_DURATION_MS / 1000 * 2)

                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack.play()

                while (isActive) {
                    val target = selectPriorityBeacon(currentBeacons)
                    if (target != null && !isMuted) {
                        val freq = getFrequencyHz(target.message.status)
                        val pcm = generatePingPcm(freq)
                        audioTrack.write(pcm, 0, pcm.size)

                        val interval = calculatePingIntervalMs(target.estimatedDistanceMeters)
                        val sleepTime = (interval - PING_DURATION_MS).coerceAtLeast(20L)
                        delay(sleepTime)
                    } else {
                        delay(250L)
                    }
                }
            } catch (e: Exception) {
                // Fallback / AudioTrack-Fehler abfangen
            } finally {
                try {
                    audioTrack?.stop()
                    audioTrack?.release()
                } catch (_: Exception) {}
            }
        }
    }

    fun stop() {
        pingJob?.cancel()
        pingJob = null
    }

    fun release() {
        stop()
        scope.cancel()
    }
}
