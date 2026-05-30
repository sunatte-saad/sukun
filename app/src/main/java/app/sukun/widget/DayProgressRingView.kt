package app.sukun.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import app.sukun.R
import app.sukun.helper.getColorFromAttr
import kotlin.math.min

class DayProgressRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val ringBounds = RectF()
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private var elapsedFraction: Float = 0f

    fun setElapsedFraction(value: Float) {
        val boundedValue = value.coerceIn(0f, 1f)
        if (elapsedFraction == boundedValue) return
        elapsedFraction = boundedValue
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val ringColor = context.getColorFromAttr(R.attr.primaryColor)
        trackPaint.color = ColorUtils.setAlphaComponent(ringColor, 70)
        progressPaint.color = ringColor

        val strokeWidth = min(width, height) * 0.028f
        trackPaint.strokeWidth = strokeWidth
        progressPaint.strokeWidth = strokeWidth

        val inset = strokeWidth / 2f + min(width, height) * 0.08f
        ringBounds.set(inset, inset, width - inset, height - inset)

        val startAngle = 110f
        val totalSweep = 320f
        canvas.drawArc(ringBounds, startAngle, totalSweep, false, trackPaint)
        canvas.drawArc(ringBounds, startAngle, totalSweep * elapsedFraction, false, progressPaint)
    }
}
