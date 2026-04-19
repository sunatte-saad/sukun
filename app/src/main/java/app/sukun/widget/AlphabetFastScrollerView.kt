package app.sukun.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import app.sukun.R
import app.sukun.helper.getColorFromAttr

class AlphabetFastScrollerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private var sections: List<String> = emptyList()
    private var selectedIndex = -1
    private var sectionSelectedListener: ((String) -> Unit)? = null

    fun setSections(value: List<String>) {
        if (sections == value) return
        sections = value
        if (selectedIndex > sections.lastIndex) selectedIndex = -1
        invalidate()
    }

    fun setOnSectionSelectedListener(listener: (String) -> Unit) {
        sectionSelectedListener = listener
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (sections.isEmpty() || width == 0 || height == 0) return

        val slotHeight = height.toFloat() / sections.size
        val centerX = width / 2f
        val defaultTextSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            11f,
            resources.displayMetrics
        ).coerceAtMost(slotHeight * 0.58f)
        val selectedTextSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            13f,
            resources.displayMetrics
        ).coerceAtMost(slotHeight * 0.68f)

        sections.forEachIndexed { index, section ->
            val isSelected = index == selectedIndex
            textPaint.textSize = if (isSelected) selectedTextSize else defaultTextSize
            textPaint.color = context.getColorFromAttr(
                if (isSelected) R.attr.primaryColor else R.attr.primaryColorTrans50
            )
            val centerY = slotHeight * index + slotHeight / 2f
            val baseline = centerY - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(section, centerX, baseline, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (sections.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                updateSelection(event.y, dispatchSelection = true)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                selectedIndex = -1
                invalidate()
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateSelection(y: Float, dispatchSelection: Boolean) {
        val boundedIndex = ((y / height) * sections.size).toInt().coerceIn(0, sections.lastIndex)
        if (selectedIndex != boundedIndex) {
            selectedIndex = boundedIndex
            invalidate()
        }
        if (dispatchSelection) {
            sectionSelectedListener?.invoke(sections[boundedIndex])
        }
    }
}
