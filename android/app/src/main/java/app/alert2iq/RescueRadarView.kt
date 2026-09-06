package app.alert2iq

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Taktische 360°-Radaransicht zur Ortung verschütteter Personen im Katastrophenfall.
 * Rendert konzentrische Distanzringe, einen rotierenden Radar-Sweepstrahl und
 * farblich priorisierte Notfall-Beacons (🔴 TRAPPED, 🟡 INJURED, 🟢 OK) mit Distanz- & Akkustatus.
 */
class RescueRadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#3354E6CD")
        strokeWidth = 2f
    }

    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#1A54E6CD")
        strokeWidth = 1.5f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80A8E0D6")
        textSize = 26f
        textAlign = Paint.Align.CENTER
    }

    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#54E6CD")
    }

    private val blipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val blipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
        textAlign = Paint.Align.CENTER
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }

    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var sweepAngle = 0f
    private var pulseFraction = 0f
    private var sweepAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null

    private var beacons: List<DiscoveredBeacon> = emptyList()

    // Maximale Radar-Reichweite für Skalierung (50 Meter)
    private val maxDistanceMeters = 50.0

    init {
        startAnimators()
    }

    private fun startAnimators() {
        sweepAnimator?.cancel()
        sweepAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 3200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                sweepAngle = it.animatedValue as Float
                invalidate()
            }
            start()
        }

        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1400L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                pulseFraction = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun setBeacons(list: List<DiscoveredBeacon>) {
        beacons = list
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (sweepAnimator?.isRunning != true) {
            startAnimators()
        }
    }

    override fun onDetachedFromWindow() {
        sweepAnimator?.cancel()
        pulseAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = (min(width, height) / 2f) * 0.88f
        if (maxRadius <= 0) return

        // 1. Fadenkreuz zeichnen
        canvas.drawLine(cx - maxRadius, cy, cx + maxRadius, cy, crosshairPaint)
        canvas.drawLine(cx, cy - maxRadius, cx, cy + maxRadius, crosshairPaint)

        // 2. Konzentrische Distanzringe zeichnen (5m, 15m, 30m, 50m)
        val distances = listOf(5.0, 15.0, 30.0, 50.0)
        for (dist in distances) {
            val r = (dist / maxDistanceMeters * maxRadius).toFloat()
            canvas.drawCircle(cx, cy, r, ringPaint)
            // Ring-Label
            canvas.drawText("${dist.toInt()}m", cx, cy - r + 30f, textPaint)
        }

        // 3. Rotierender Sweep-Strahl mit Gradient
        canvas.save()
        canvas.rotate(sweepAngle, cx, cy)
        val colors = intArrayOf(
            Color.TRANSPARENT,
            Color.TRANSPARENT,
            Color.parseColor("#0854E6CD"),
            Color.parseColor("#3354E6CD"),
            Color.parseColor("#7054E6CD")
        )
        val positions = floatArrayOf(0f, 0.7f, 0.85f, 0.95f, 1f)
        sweepPaint.shader = SweepGradient(cx, cy, colors, positions)
        canvas.drawCircle(cx, cy, maxRadius, sweepPaint)
        canvas.restore()

        // 4. Eigener Standort (Zentrum)
        canvas.drawCircle(cx, cy, 10f, centerDotPaint)
        centerDotPaint.alpha = ((1f - pulseFraction) * 120).toInt()
        canvas.drawCircle(cx, cy, 10f + pulseFraction * 18f, centerDotPaint)
        centerDotPaint.alpha = 255

        // 5. Beacons als Blips rendern
        for (beacon in beacons) {
            val dist = beacon.estimatedDistanceMeters.coerceIn(0.5, maxDistanceMeters)
            val r = (dist / maxDistanceMeters * maxRadius).toFloat()

            // Stabiler Streuwinkel basierend auf Device Address Hash
            val hashAngleRad = Math.toRadians((Math.abs(beacon.deviceAddress.hashCode()) % 360).toDouble())
            val bx = cx + (r * cos(hashAngleRad)).toFloat()
            val by = cy + (r * sin(hashAngleRad)).toFloat()

            val statusColor = when (beacon.message.status) {
                BleSosStatus.TRAPPED -> Color.parseColor("#FF1744")  // Signalrot
                BleSosStatus.INJURED -> Color.parseColor("#FFD600")  // Warngelb
                BleSosStatus.OK -> Color.parseColor("#00E676")       // Smaragdgrün
            }

            // Pulsierende Gefahren-Aura für TRAPPED und INJURED
            if (beacon.message.status == BleSosStatus.TRAPPED) {
                auraPaint.color = statusColor
                auraPaint.alpha = ((1f - pulseFraction) * 200).toInt()
                canvas.drawCircle(bx, by, 16f + pulseFraction * 26f, auraPaint)
                // Zweiter innerer Puls
                val p2 = (pulseFraction + 0.5f) % 1f
                auraPaint.alpha = ((1f - p2) * 160).toInt()
                canvas.drawCircle(bx, by, 16f + p2 * 20f, auraPaint)
            } else if (beacon.message.status == BleSosStatus.INJURED) {
                auraPaint.color = statusColor
                auraPaint.alpha = ((1f - pulseFraction) * 180).toInt()
                canvas.drawCircle(bx, by, 14f + pulseFraction * 18f, auraPaint)
            }

            // Blip-Punkt
            blipPaint.color = statusColor
            canvas.drawCircle(bx, by, 12f, blipPaint)

            // Innerer weißer Kern
            blipPaint.color = Color.WHITE
            canvas.drawCircle(bx, by, 4f, blipPaint)

            // Distanz & Akku Label über/unter dem Blip
            val label = String.format(Locale.US, "%.1fm (%d%%)", beacon.estimatedDistanceMeters, beacon.message.batteryPercent)
            canvas.drawText(label, bx, by - 18f, blipTextPaint)
        }
    }
}
