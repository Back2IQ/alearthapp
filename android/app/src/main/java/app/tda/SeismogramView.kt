package app.tda

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.AttributeSet
import android.view.View
import kotlin.math.sqrt

/**
 * Real live seismogram: reads the phone's accelerometer (TYPE_ACCELEROMETER) and
 * plots a rolling trace of |a| - g. This is genuine device motion, not simulated --
 * shake the phone and the trace reacts (spec "echtes Handy-Seismogramm").
 *
 * The sensor is read for on-screen display ONLY; nothing is uploaded or persisted
 * (spec "Grenzen": no sensor collection/upload service in the MVP).
 */
class SeismogramView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs), SensorEventListener {

    private val bufferSize = 220
    private val buffer = FloatArray(bufferSize)
    private var writeIndex = 0
    private var filled = false

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null

    private val tracePaint = Paint().apply {
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
        color = Color.parseColor("#FF00C853")
    }

    private val centerLinePaint = Paint().apply {
        strokeWidth = 1f
        color = Color.parseColor("#33808080")
    }

    /** Called by the host Activity once Prefs are ready, to pick the right trace color. */
    fun applyColorblindSafe(colorblindSafe: Boolean) {
        val resId = if (colorblindSafe) R.color.tda_seismogram_trace_cb else R.color.tda_seismogram_trace
        tracePaint.color = context.getColor(resId)
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInEditMode) {
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            accelerometer?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    override fun onDetachedFromWindow() {
        sensorManager?.unregisterListener(this)
        super.onDetachedFromWindow()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat() - SensorManager.GRAVITY_EARTH
        buffer[writeIndex] = magnitude
        writeIndex = (writeIndex + 1) % bufferSize
        if (writeIndex == 0) filled = true
        invalidate()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* not used */ }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val midY = h / 2f
        canvas.drawLine(0f, midY, w, midY, centerLinePaint)

        val count = if (filled) bufferSize else writeIndex
        if (count < 2) return

        // Scale: +-8 m/s^2 of deviation spans the full view height.
        val scale = (h / 2f) / 8f
        val dx = w / (bufferSize - 1)

        var prevX = 0f
        var prevY = midY
        var first = true
        for (i in 0 until count) {
            val idx = if (filled) (writeIndex + i) % bufferSize else i
            val value = buffer[idx]
            val x = i * dx
            val y = (midY - value * scale).coerceIn(0f, h)
            if (!first) {
                canvas.drawLine(prevX, prevY, x, y, tracePaint)
            }
            prevX = x
            prevY = y
            first = false
        }
    }
}
