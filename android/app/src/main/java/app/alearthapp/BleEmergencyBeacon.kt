package app.alearthapp

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.BatteryManager
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

object BleEmergencyBeacon {

    private const val TAG = "BleEmergencyBeacon"
    val SERVICE_UUID: UUID = UUID.fromString("0000AE01-0000-1000-8000-00805F9B34FB")
    const val MANUFACTURER_ID: Int = 0x02E5

    private var advertiser: BluetoothLeAdvertiser? = null
    private var callback: AdvertiseCallback? = null
    var isBroadcasting: Boolean = false
        private set

    @SuppressLint("MissingPermission")
    fun start(
        context: Context,
        status: BleSosStatus = BleSosStatus.TRAPPED,
        lat: Double = 0.0,
        lon: Double = 0.0
    ) {
        if (isBroadcasting) return

        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
        val adapter = bm.adapter
        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "Bluetooth not available or disabled")
            return
        }

        advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.w(TAG, "BLE Advertising not supported on this device")
            return
        }

        val batteryMgr = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryPct = batteryMgr?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 50

        val msg = BleSosMessage(
            status = status,
            batteryPercent = batteryPct,
            timestampSec = System.currentTimeMillis() / 1000L,
            coarseLat = lat.toFloat(),
            coarseLon = lon.toFloat()
        )
        val payload = BleSosMessage.encode(msg)

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0) // kontinuierlich bis stop()
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
                Log.i(TAG, "BLE Emergency Beacon broadcasting started successfully")
            }

            override fun onStartFailure(errorCode: Int) {
                isBroadcasting = false
                Log.e(TAG, "BLE Emergency Beacon failed to start: $errorCode")
            }
        }

        try {
            advertiser?.startAdvertising(settings, data, callback)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE advertising", e)
            isBroadcasting = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stop(context: Context) {
        if (!isBroadcasting && advertiser == null) return
        try {
            callback?.let { advertiser?.stopAdvertising(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping BLE advertising", e)
        } finally {
            isBroadcasting = false
            advertiser = null
            callback = null
        }
    }
}
