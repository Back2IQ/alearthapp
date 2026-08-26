package app.alearthapp

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log

data class DiscoveredBeacon(
    val deviceAddress: String,
    val message: BleSosMessage,
    val rssi: Int,
    val estimatedDistanceMeters: Double,
    val lastSeenMs: Long = System.currentTimeMillis()
)

object BleRescueScanner {

    private const val TAG = "BleRescueScanner"

    private var scanner: BluetoothLeScanner? = null
    private var callback: ScanCallback? = null
    var isScanning: Boolean = false
        private set

    private val discoveredMap = mutableMapOf<String, DiscoveredBeacon>()

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

        discoveredMap.clear()

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(BleEmergencyBeacon.SERVICE_UUID))
            .build()

        // Bug fix: BALANCED statt LOW_LATENCY — Notfallmodus darf Akku nicht in Minuten entleeren
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()

        callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result ?: return
                val record = result.scanRecord ?: return
                val serviceData = record.getServiceData(ParcelUuid(BleEmergencyBeacon.SERVICE_UUID))
                val msg = BleSosMessage.decode(serviceData) ?: return

                val dist = BleSosMessage.estimateDistanceMeters(result.rssi)
                val beacon = DiscoveredBeacon(
                    deviceAddress = result.device?.address ?: "Unbekannt",
                    message = msg,
                    rssi = result.rssi,
                    estimatedDistanceMeters = dist,
                    lastSeenMs = System.currentTimeMillis()
                )

                val snapshot: List<DiscoveredBeacon>
                synchronized(discoveredMap) {
                    discoveredMap[beacon.deviceAddress] = beacon
                    snapshot = discoveredMap.values.sortedBy { it.estimatedDistanceMeters }
                }
                // Deliver outside the lock — prevents holding the monitor during cross-thread marshaling
                onUpdate(snapshot)
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "BLE Rescue Scan failed: $errorCode")
                isScanning = false
            }
        }

        try {
            scanner?.startScan(listOf(filter), settings, callback)
            isScanning = true
            Log.i(TAG, "BLE Rescue Scanner started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE rescue scan", e)
            isScanning = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
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
