package app.alert2iq

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

/**
 * BLE Notfall-Beacon mit asymmetrischem Duty-Cycling zur Maximierung der Überlebenszeit:
 * - Erste Übertragung nach 30 Minuten Ruhephase (Verhinderung von Fehlalarmen).
 * - Danach zyklische Bursts alle 30 Minuten (Burst-Dauer: 60s Dauerfeuer), um den Akku über Tage zu schonen.
 * - Direkter Start bei manuellem Notsignal ("Hilfe/Verschüttet").
 */
object BleEmergencyBeacon {

    private const val TAG = "BleEmergencyBeacon"
    val SERVICE_UUID: UUID = UUID.fromString("0000AE01-0000-1000-8000-00805F9B34FB")

    const val INITIAL_DELAY_MS = 30 * 60 * 1000L // 30 Min bis zur ersten Übertragung
    const val CYCLE_INTERVAL_MS = 30 * 60 * 1000L // Alle 30 Min Burst
    const val BURST_DURATION_MS = 60 * 1000L // 60s aktiver Sende-Burst

    private var advertiser: BluetoothLeAdvertiser? = null
    private var callback: AdvertiseCallback? = null
    private val handler = Handler(Looper.getMainLooper())
    private var cycleRunnable: Runnable? = null

    var isBroadcasting: Boolean = false
        private set

    @SuppressLint("MissingPermission")
    fun start(
        context: Context,
        status: BleSosStatus = BleSosStatus.TRAPPED,
        lat: Double = 0.0,
        lon: Double = 0.0,
        isUserResponsive: Boolean = false,
        immediateBurst: Boolean = false // true wenn Nutzer aktiv tippt, false bei Deadman/Auto
    ) {
        stop(context)

        val startDelay = if (immediateBurst) 0L else INITIAL_DELAY_MS
        Log.i(TAG, "BLE Emergency Beacon scheduled (first burst in ${startDelay / 1000}s, cycle 30m)")

        cycleRunnable = object : Runnable {
            override fun run() {
                executeBurst(context, status, lat, lon, isUserResponsive)
                handler.postDelayed(this, CYCLE_INTERVAL_MS)
            }
        }

        cycleRunnable?.let { handler.postDelayed(it, startDelay) }
    }

    @SuppressLint("MissingPermission")
    private fun executeBurst(
        context: Context,
        status: BleSosStatus,
        lat: Double,
        lon: Double,
        isUserResponsive: Boolean
    ) {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
        val adapter = bm.adapter
        if (adapter == null || !adapter.isEnabled) return

        advertiser = adapter.bluetoothLeAdvertiser ?: return

        val batteryMgr = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryPct = batteryMgr?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 50

        val profile = EmergencyVaultManager.loadProfile(context)
        val triage = if (profile.broadcastMedicalData) {
            val initials = if (profile.fullName.isNotBlank()) {
                val parts = profile.fullName.trim().split("\\s+".toRegex())
                if (parts.size >= 2) "${parts[0].first()}.${parts[1].first()}." else profile.fullName.take(4)
            } else ""

            BleTriageProfile(
                bloodType = BleBloodType.fromString(profile.bloodType),
                gender = BleGender.fromString(profile.gender),
                age = profile.age,
                isUserResponsive = isUserResponsive,
                hasInsulinDiabetes = profile.chronicDiseases.contains("insulin", ignoreCase = true) || profile.chronicDiseases.contains("diabetes", ignoreCase = true),
                hasHeartCondition = profile.chronicDiseases.contains("herz", ignoreCase = true) || profile.chronicDiseases.contains("heart", ignoreCase = true),
                hasRespiratoryRisk = profile.chronicDiseases.contains("asthma", ignoreCase = true) || profile.chronicDiseases.contains("lunge", ignoreCase = true),
                nameInitials = initials
            )
        } else {
            BleTriageProfile(isUserResponsive = isUserResponsive)
        }

        val msg = BleSosMessage(
            status = status,
            batteryPercent = batteryPct,
            timestampSec = System.currentTimeMillis() / 1000L,
            coarseLat = lat.toFloat(),
            coarseLon = lon.toFloat(),
            triage = triage
        )
        val payload = BleSosMessage.encode(msg)

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .addServiceData(ParcelUuid(SERVICE_UUID), payload)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                isBroadcasting = true
                Log.i(TAG, "BLE Emergency Beacon active burst started (60s)")
                // Nach 60 Sekunden Burst stoppen, um Akku bis zum nächsten 30-Minuten-Intervall zu schonen
                handler.postDelayed({ stopAdvertisingOnly() }, BURST_DURATION_MS)
            }

            override fun onStartFailure(errorCode: Int) {
                isBroadcasting = false
                Log.e(TAG, "BLE Emergency Beacon failed to start burst: $errorCode")
            }
        }

        try {
            advertiser?.startAdvertising(settings, data, callback)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE burst", e)
            isBroadcasting = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopAdvertisingOnly() {
        try {
            callback?.let { advertiser?.stopAdvertising(it) }
        } catch (_: Exception) {}
        isBroadcasting = false
        advertiser = null
        callback = null
    }

    @SuppressLint("MissingPermission")
    fun stop(context: Context) {
        cycleRunnable?.let { handler.removeCallbacks(it) }
        cycleRunnable = null
        stopAdvertisingOnly()
    }
}
