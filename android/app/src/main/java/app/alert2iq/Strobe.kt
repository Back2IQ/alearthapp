package app.alert2iq

import android.app.Activity
import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.view.WindowManager

/**
 * Bildschirm- und Taschenlampen-Strobo (spec TP-3 §"Teil 3"). Timings kommen aus dem
 * getesteten [StrobePattern]; hier nur der Hardware-Rand. setTorchMode braucht kein Kamera-Recht.
 */
class Strobe(private val activity: Activity) {
    private val handler = Handler(Looper.getMainLooper())
    private val cameraManager = activity.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private var torchId: String? = runCatching {
        cameraManager?.cameraIdList?.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()
    private var screenOn = false
    private var torchOn = false

    fun startScreen(pulse: Pulse) {
        val root = activity.findViewById<android.view.View>(R.id.beaconStrobe)
        val step = object : Runnable {
            var on = false
            override fun run() {
                on = !on
                root.setBackgroundColor(if (on) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
                setBrightness(if (on) 1f else 0.02f)
                handler.postDelayed(this, if (on) pulse.onMillis else pulse.offMillis)
            }
        }
        screenOn = true
        handler.post(step)
    }

    fun startTorch(pulse: Pulse) {
        val id = torchId ?: return
        val step = object : Runnable {
            var on = false
            override fun run() {
                on = !on
                runCatching { cameraManager?.setTorchMode(id, on) }
                handler.postDelayed(this, if (on) pulse.onMillis else pulse.offMillis)
            }
        }
        torchOn = true
        handler.post(step)
    }

    fun stopAll() {
        handler.removeCallbacksAndMessages(null)
        if (screenOn) setBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        if (torchOn) torchId?.let { runCatching { cameraManager?.setTorchMode(it, false) } }
        screenOn = false; torchOn = false
    }

    private fun setBrightness(value: Float) {
        val lp = activity.window.attributes
        lp.screenBrightness = value
        activity.window.attributes = lp
    }
}
