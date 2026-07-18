package com.anubhav.app.ui.metrics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * A semicircular reference-range gauge (like a lab "metric" dial).
 *
 * The arc is split into three colour zones — low (amber), normal (green) and
 * high (red) — derived from a reference range. When a value is set, a marker is
 * placed on the arc so the patient can see where their result falls.
 */
class HealthGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private var rangeMin = 0.0
    private var rangeMax = 100.0
    private var low = 30.0
    private var high = 70.0
    private var value: Double? = null
    private var unit: String = ""

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0xFFE3E9EC.toInt()
    }
    private val zonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val markerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val markerRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xFF17272F.toInt()
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xFF5C6E78.toInt()
    }
    private val endLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8A9AA3.toInt()
    }

    private val arcRect = RectF()

    /** Configure the gauge. low/high are the normal-range bounds. */
    fun setRange(minV: Double, maxV: Double, lowV: Double, highV: Double, unitLabel: String) {
        var lo = min(lowV, highV)
        var hi = max(lowV, highV)
        var mn = minV
        var mx = maxV
        // Guard against degenerate bounds so the arc always renders.
        if (mx <= mn) { mn = min(0.0, lo); mx = hi * 1.6 + 1.0 }
        if (lo < mn) mn = lo
        if (hi > mx) mx = hi
        if (mx <= mn) mx = mn + 1.0
        rangeMin = mn; rangeMax = mx; low = lo; high = hi; unit = unitLabel
        invalidate()
    }

    fun setValue(v: Double?) { value = v; invalidate() }

    fun zoneColorFor(v: Double): Int = when {
        v < low -> COLOR_LOW
        v > high -> COLOR_HIGH
        else -> COLOR_NORMAL
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = View.MeasureSpec.getSize(widthMeasureSpec)
        val desiredH = (w * 0.52f).toInt() + dp(52f).toInt()
        setMeasuredDimension(w, View.resolveSize(desiredH, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val stroke = dp(16f)
        zonePaint.strokeWidth = stroke
        trackPaint.strokeWidth = stroke
        markerRing.strokeWidth = dp(4f)
        valuePaint.textSize = dp(26f)
        unitPaint.textSize = dp(13f)
        endLabelPaint.textSize = dp(11f)

        val side = dp(20f)
        val r = (width - side * 2 - stroke) / 2f
        if (r <= 0) return
        val cx = width / 2f
        val cy = dp(10f) + stroke / 2f + r
        arcRect.set(cx - r, cy - r, cx + r, cy + r)

        // Full track.
        canvas.drawArc(arcRect, 180f, 180f, false, trackPaint)

        val span = (rangeMax - rangeMin).toFloat().coerceAtLeast(0.0001f)
        val lowFrac = (((low - rangeMin) / span).toFloat()).coerceIn(0f, 1f)
        val highFrac = (((high - rangeMin) / span).toFloat()).coerceIn(0f, 1f)

        drawZone(canvas, 180f, lowFrac * 180f, COLOR_LOW)
        drawZone(canvas, 180f + lowFrac * 180f, (highFrac - lowFrac) * 180f, COLOR_NORMAL)
        drawZone(canvas, 180f + highFrac * 180f, (1f - highFrac) * 180f, COLOR_HIGH)

        // End labels.
        canvas.drawText(fmt(rangeMin), cx - r, cy + dp(18f), endLabelPaint.also { it.textAlign = Paint.Align.CENTER })
        canvas.drawText(fmt(rangeMax), cx + r, cy + dp(18f), endLabelPaint.also { it.textAlign = Paint.Align.CENTER })

        val v = value
        if (v != null) {
            val f = (((v - rangeMin) / span).toFloat()).coerceIn(0f, 1f)
            val angle = Math.toRadians((180f + f * 180f).toDouble())
            val mx = cx + r * cos(angle).toFloat()
            val my = cy + r * sin(angle).toFloat()
            markerRing.color = zoneColorFor(v)
            canvas.drawCircle(mx, my, dp(9f), markerFill)
            canvas.drawCircle(mx, my, dp(9f), markerRing)
            // Centre readout.
            canvas.drawText(fmt(v), cx, cy - dp(4f), valuePaint)
            if (unit.isNotBlank()) canvas.drawText(unit, cx, cy + dp(14f), unitPaint)
        } else {
            valuePaint.textSize = dp(15f)
            unitPaint.textSize = dp(12f)
            canvas.drawText("— —", cx, cy - dp(2f), valuePaint)
            if (unit.isNotBlank()) canvas.drawText(unit, cx, cy + dp(14f), unitPaint)
        }
    }

    private fun drawZone(canvas: Canvas, start: Float, sweep: Float, color: Int) {
        if (sweep <= 0.2f) return
        zonePaint.color = color
        canvas.drawArc(arcRect, start, sweep, false, zonePaint)
    }

    private fun fmt(d: Double): String {
        if (d.isNaN() || d.isInfinite()) return "-"
        return if (d == d.toLong().toDouble()) d.toLong().toString()
        else {
            val s = "%.2f".format(d)
            s.trimEnd('0').trimEnd('.')
        }
    }

    companion object {
        const val COLOR_LOW = 0xFFE0A100.toInt()
        const val COLOR_NORMAL = 0xFF2E9E86.toInt()
        const val COLOR_HIGH = 0xFFC0392B.toInt()
    }
}
