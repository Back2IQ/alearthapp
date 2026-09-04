package app.alearthapp

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log

data class DiscoveredBeacon(
    val deviceAddress: String,
    val message: BleSosMessage,
    val rssi: Int,
    val estimatedDistanceMeters: Double,
    val lastSeenMs: Long = System.currentTimeMillis()
)

/**
 * Signal-Resonator gegen Beton- & Trümmer-Fading.
 * Filtert extreme Mehrwege-Sprünge (±25 dBm) via Moving-Median (5 Samples)
 * und glättet den Signalverlauf via Exponential Moving Average (EMA, alpha = 0.35).
 */
class RssiSmoother(
    private val windowSize: Int = 5,
    private val alpha: Double = 0.35
) {
    private val samples = ArrayDeque<Int>()
    private var smoothedRssi: Double? = null

    fun addSample(rssi: Int): Double {
        if (samples.size >= windowSize) {
            samples.removeFirst()
        }
        samples.addLast(rssi)

        val sorted = samples.sorted()
        val median = sorted[sorted.size / 2].toDouble()

        val prev = smoothedRssi
        val smooth = if (prev == null) {
            median
        } else {
            alpha * median + (1.0 - alpha) * prev
        }
        smoothedRssi = smooth
        return smooth
    }

    fun clear() {
        samples.clear()
        smoothedRssi = null
    }
}

object BleRescueScanner {

    private const val TAG = "BleRescueScanner"
    const val SCAN_TIMEOUT_MS = 10 * 60 * 1000L // 10 Minuten Auto-Timeout gegen Retter-Akku-Entleerung
    const val STALE_BEACON_THRESHOLD_MS = 30 * 1000L // 30s Veraltungs-Schwelle

    private var scanner: BluetoothLeScanner? = null
    private var callback: ScanCallback? = null
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    var isScanning: Boolean = false
        private set

    private val discoveredMap = mutableMapOf<String, DiscoveredBeacon>()
    private val smoothersMap = mutableMapOf<String, RssiSmoother>()

    @SuppressLint("MissingPermission")
    fun startScan(
        context: Context,
        onUpdate: (List<DiscoveredBeacon>) -> Unit
    ) {
        if (isScanning) return

        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
        val adapter = bm.adapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "Bluetooth not enabled for rescue scan")
            return
        }

        scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            Log.w(TAG, "BLE Scanner not available")
            return
        }

        synchronized(discoveredMap) {
            discoveredMap.clear()
            smoothersMap.clear()
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(BleEmergencyBeacon.SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()

        callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result ?: return
                val record = result.scanRecord ?: return
                val serviceData = record.getServiceData(ParcelUuid(BleEmergencyBeacon.SERVICE_UUID))
                val msg = BleSosMessage.decode(serviceData) ?: return

                // Invariante 4: Anti-Spoofing & Replay-Schutz
                if (!msg.isValidTimestamp()) {
                    Log.w(TAG, "Rejected expired/spoofed beacon from ${result.device?.address}")
                    return
                }

                val devAddr = result.device?.address ?: "Unknown"

                val snapshot: List<DiscoveredBeacon>
                synchronized(discoveredMap) {
                    // Invariante 2: Signal-Resonator Glättung
                    val smoother = smoothersMap.getOrPut(devAddr) { RssiSmoother() }
                    val smoothedRssi = smoother.addSample(result.rssi)
                    val dist = BleSosMessage.estimateDistanceMeters(smoothedRssi.toInt())

                    val beacon = DiscoveredBeacon(
                        deviceAddress = devAddr,
                        message = msg,
                        rssi = smoothedRssi.toInt(),
                        estimatedDistanceMeters = dist,
                        lastSeenMs = System.currentTimeMillis()
                    )
                    discoveredMap[devAddr] = beacon

                    // Veraltete Beacons entfernen (> 30s)
                    val now = System.currentTimeMillis()
                    discoveredMap.entries.removeIf { now - it.value.lastSeenMs > STALE_BEACON_THRESHOLD_MS }

                    snapshot = discoveredMap.values.sortedBy { it.estimatedDistanceMeters }
                }
                // Deliver outside the lock — prevents holding the monitor during cross-thread marshaling
                onUpdate(snapshot)
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE Rescue Scan failed: $errorCode")
                stopScan()
            }
        }

        try {
            scanner?.startScan(listOf(filter), settings, callback)
            isScanning = true
            Log.i(TAG, "BLE Rescue Scanner started with 10-min safety timeout")

            // Invariante 3: 10-Minuten Timeout-Sicherung
            timeoutRunnable?.let { timeoutHandler.removeCallbacks(it) }
            val tr = Runnable {
                Log.i(TAG, "BLE Rescue Scanner safety timeout reached (10 min) - stopping scan")
                stopScan()
            }
            timeoutRunnable = tr
            timeoutHandler.postDelayed(tr, SCAN_TIMEOUT_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE rescue scan", e)
            isScanning = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        timeoutRunnable?.let {
            timeoutHandler.removeCallbacks(it)
            timeoutRunnable = null
        }
        if (!isScanning && scanner == null) return
        try {
            callback?.let { scanner?.stopScan(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping BLE rescue scan", e)
        } finally {
            isScanning = false
            scanner = null
            callback = null
        }
    }
}
