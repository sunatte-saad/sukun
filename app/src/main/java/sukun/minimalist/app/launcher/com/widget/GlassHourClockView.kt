package sukun.minimalist.app.launcher.com.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.graphics.Typeface
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.Surface
import android.view.View
import android.view.WindowManager
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import sukun.minimalist.app.launcher.com.R
import sukun.minimalist.app.launcher.com.helper.getColorFromAttr
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * Hours sit above minutes. One water line fills the lower digits first, then the upper digits.
 * The line stays level with the ground, so turning the phone makes the water run to the new bottom.
 */
class GlassHourClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val glyphMeasure = Rect()
    private val digitBounds = Array(4) { RectF() }
    private val glyphBox = RectF()
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var listeningForGravity = false

    /** Points toward the ground in view pixels. Down the screen until a sensor reading arrives. */
    private var gravityX = 0f
    private var gravityY = 1f

    private val gravityListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val (dx, dy) = downVector(event.values[0], event.values[1], displayRotation())
            val magnitude = hypot(dx, dy)
            if (magnitude < 1.2f) return
            val targetX = dx / magnitude
            val targetY = dy / magnitude
            gravityX += (targetX - gravityX) * WATER_FOLLOW
            gravityY += (targetY - gravityY) * WATER_FOLLOW
            if (abs(targetX - gravityX) > 0.004f || abs(targetY - gravityY) > 0.004f) {
                invalidate()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private var hourOfDay: Int = 0
    private var minute: Int = 0
    private var use24Hour: Boolean = true
    private var elapsedFraction: Float = 0f

    init {
        val clockFace = ResourcesCompat.getFont(context, R.font.rubik_medium)
            ?: Typeface.create("sans-serif-medium", Typeface.NORMAL)
        glassPaint.typeface = clockFace
        fillPaint.typeface = clockFace
        val now = Calendar.getInstance()
        hourOfDay = now.get(Calendar.HOUR_OF_DAY)
        minute = now.get(Calendar.MINUTE)
        use24Hour = DateFormat.is24HourFormat(context)
        updateContentDescription()
    }

    fun setTime(hourOfDay: Int, minute: Int, use24Hour: Boolean) {
        val nextHour = hourOfDay.coerceIn(0, 23)
        val nextMinute = minute.coerceIn(0, 59)
        if (this.hourOfDay == nextHour && this.minute == nextMinute && this.use24Hour == use24Hour) {
            return
        }
        this.hourOfDay = nextHour
        this.minute = nextMinute
        this.use24Hour = use24Hour
        updateContentDescription()
        invalidate()
    }

    fun setElapsedFraction(value: Float) {
        val boundedValue = value.coerceIn(0f, 1f)
        if (elapsedFraction == boundedValue) return
        elapsedFraction = boundedValue
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isShown) startGravity()
    }

    override fun onDetachedFromWindow() {
        stopGravity()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) startGravity() else stopGravity()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width < 2 || height < 2) return

        val digits = GlassHourTime.digits(hourOfDay, minute, use24Hour)
        if (digits.size != 4) return

        val density = resources.displayMetrics.density
        val gapX = 8f * density
        val gapY = 4f * density
        val colWidth = (width - gapX) / 2f
        val rowHeight = (height - gapY) / 2f
        if (colWidth < 1f || rowHeight < 1f) return

        val glassColor = context.getColorFromAttr(R.attr.primaryColor)
        glassPaint.color = ColorUtils.setAlphaComponent(glassColor, 88)
        fillPaint.color = glassColor

        val textSize = digitTextSize(colWidth, rowHeight)
        glassPaint.textSize = textSize
        fillPaint.textSize = textSize

        val downLen = hypot(gravityX, gravityY).coerceAtLeast(0.001f)
        val downX = gravityX / downLen
        val downY = gravityY / downLen
        var minProjection = Float.POSITIVE_INFINITY
        var maxProjection = Float.NEGATIVE_INFINITY
        fun includeProjection(box: RectF, downX: Float, downY: Float) {
            minProjection = minOf(
                minProjection,
                projection(box.left, box.top, downX, downY),
                projection(box.right, box.top, downX, downY),
                projection(box.left, box.bottom, downX, downY),
                projection(box.right, box.bottom, downX, downY),
            )
            maxProjection = maxOf(
                maxProjection,
                projection(box.left, box.top, downX, downY),
                projection(box.right, box.top, downX, downY),
                projection(box.left, box.bottom, downX, downY),
                projection(box.right, box.bottom, downX, downY),
            )
        }

        for (index in digits.indices) {
            val column = index % 2
            val row = index / 2
            val bounds = digitBounds[index]
            bounds.set(
                column * (colWidth + gapX),
                row * (rowHeight + gapY),
                column * (colWidth + gapX) + colWidth,
                row * (rowHeight + gapY) + rowHeight,
            )
            measureGlyph(bounds, digits[index])
            includeProjection(glyphBox, downX, downY)
            val baseline = bounds.centerY() - (glyphMeasure.top + glyphMeasure.bottom) / 2f
            canvas.drawText(digits[index].toString(), bounds.centerX(), baseline, glassPaint)
        }

        if (elapsedFraction <= 0f || maxProjection <= minProjection) return
        val surface = maxProjection - elapsedFraction * (maxProjection - minProjection)
        val surfaceX = downX * surface
        val surfaceY = downY * surface
        val angle = Math.toDegrees(atan2(downX.toDouble(), downY.toDouble())).toFloat()

        for (index in digits.indices) {
            val bounds = digitBounds[index]
            measureGlyph(bounds, digits[index])
            val baseline = bounds.centerY() - (glyphMeasure.top + glyphMeasure.bottom) / 2f
            canvas.save()
            canvas.rotate(angle, surfaceX, surfaceY)
            canvas.clipRect(-CLIP, surfaceY, CLIP, CLIP)
            canvas.rotate(-angle, surfaceX, surfaceY)
            canvas.drawText(digits[index].toString(), bounds.centerX(), baseline, fillPaint)
            canvas.restore()
        }
    }

    private fun measureGlyph(bounds: RectF, digit: Char) {
        val label = digit.toString()
        glassPaint.getTextBounds(label, 0, 1, glyphMeasure)
        val baseline = bounds.centerY() - (glyphMeasure.top + glyphMeasure.bottom) / 2f
        val centerX = bounds.centerX()
        glyphBox.set(
            centerX + glyphMeasure.left,
            baseline + glyphMeasure.top,
            centerX + glyphMeasure.right,
            baseline + glyphMeasure.bottom,
        )
    }

    private fun digitTextSize(colWidth: Float, rowHeight: Float): Float {
        glassPaint.textSize = 100f
        glassPaint.getTextBounds("8", 0, 1, glyphMeasure)
        val glyphWidth = glyphMeasure.width().coerceAtLeast(1)
        val glyphHeight = glyphMeasure.height().coerceAtLeast(1)
        val widthScale = (colWidth * 0.92f) / glyphWidth
        val heightScale = (rowHeight * 0.92f) / glyphHeight
        return 100f * min(widthScale, heightScale)
    }

    private fun startGravity() {
        if (listeningForGravity) return
        val sensor = gravitySensor ?: return
        listeningForGravity = sensorManager.registerListener(
            gravityListener,
            sensor,
            SensorManager.SENSOR_DELAY_UI,
        )
    }

    private fun stopGravity() {
        if (!listeningForGravity) return
        sensorManager.unregisterListener(gravityListener)
        listeningForGravity = false
    }

    private fun displayRotation(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        }
    }

    private fun updateContentDescription() {
        contentDescription = context.getString(
            R.string.glass_hour_clock_description,
            GlassHourTime.spokenLabel(hourOfDay, minute, use24Hour),
        )
    }

    companion object {
        private const val WATER_FOLLOW = 0.16f
        private const val CLIP = 100000f

        /** Sensor axes: +X right, +Y toward the top of the phone. Result points down the current screen. */
        internal fun downVector(sensorX: Float, sensorY: Float, rotation: Int): Pair<Float, Float> {
            return when (rotation) {
                Surface.ROTATION_90 -> sensorY to sensorX
                Surface.ROTATION_180 -> sensorX to -sensorY
                Surface.ROTATION_270 -> -sensorY to -sensorX
                else -> -sensorX to sensorY
            }
        }

        private fun projection(x: Float, y: Float, downX: Float, downY: Float): Float {
            return x * downX + y * downY
        }
    }
}
