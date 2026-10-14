package sukun.minimalist.app.launcher.com.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.core.content.ContextCompat
import sukun.minimalist.app.launcher.com.R

/**
 * Dims the screen and leaves a rounded opening, with a bright ring, around the tour target.
 * The opening is recomputed before each draw so it follows scroll and layout changes.
 */
class OnboardingSpotlightView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        alpha = 128
    }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        color = ContextCompat.getColor(context, R.color.cream)
    }
    private val hole = RectF()
    private var hasHole = false
    private val cornerRadius = 14f * resources.displayMetrics.density
    private val holePadding = 6f * resources.displayMetrics.density

    private var target: View? = null
    private var boundsOnScreen: ((View) -> RectF)? = null
    private var preDrawAttached = false

    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        updateHole()
        true
    }

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun track(view: View, bounds: ((View) -> RectF)? = null) {
        if (target !== view) {
            detachTarget()
            target = view
        }
        boundsOnScreen = bounds
        updateHole()
        setSpotlightShown(true)
        ensurePreDraw()
    }

    fun refresh() {
        if (target != null) updateHole()
    }

    fun clear() {
        detachTarget()
        removePreDraw()
        if (hasHole) {
            hasHole = false
            invalidate()
        }
        setSpotlightShown(false)
    }

    fun clearIfInside(root: View) {
        val tracked = target ?: return
        if (tracked === root || tracked.isDescendantOf(root)) clear()
    }

    private fun detachTarget() {
        target = null
        boundsOnScreen = null
    }

    private fun ensurePreDraw() {
        if (preDrawAttached) return
        val observer = viewTreeObserver
        if (!observer.isAlive) return
        observer.addOnPreDrawListener(preDrawListener)
        preDrawAttached = true
    }

    private fun removePreDraw() {
        if (!preDrawAttached) return
        val observer = viewTreeObserver
        if (observer.isAlive) observer.removeOnPreDrawListener(preDrawListener)
        preDrawAttached = false
    }

    private fun updateHole() {
        val view = target
        val next = if (view == null || !view.isAttachedToWindow || view.width == 0 || view.height == 0) {
            null
        } else {
            val screen = boundsOnScreen?.invoke(view) ?: defaultBounds(view)
            if (screen.width() < 1f || screen.height() < 1f) {
                null
            } else {
                val origin = IntArray(2)
                getLocationOnScreen(origin)
                RectF(
                    screen.left - origin[0],
                    screen.top - origin[1],
                    screen.right - origin[0],
                    screen.bottom - origin[1],
                )
            }
        }
        if (next == null) {
            if (hasHole) {
                hasHole = false
                invalidate()
            }
            return
        }
        if (hasHole && hole == next) return
        hole.set(next)
        hasHole = true
        invalidate()
    }

    private fun defaultBounds(view: View): RectF {
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        return RectF(
            loc[0] - holePadding,
            loc[1] - holePadding,
            loc[0] + view.width + holePadding,
            loc[1] + view.height + holePadding,
        )
    }

    private fun setSpotlightShown(shown: Boolean) {
        val next = if (shown) VISIBLE else INVISIBLE
        if (visibility == next) return
        val parentGroup = parent as? ViewGroup
        val transition = parentGroup?.layoutTransition
        parentGroup?.layoutTransition = null
        visibility = next
        parentGroup?.layoutTransition = transition
    }

    override fun onDraw(canvas: Canvas) {
        val checkpoint = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        if (hasHole) {
            canvas.drawRoundRect(hole, cornerRadius, cornerRadius, clearPaint)
        }
        canvas.restoreToCount(checkpoint)
        if (hasHole) {
            canvas.drawRoundRect(hole, cornerRadius, cornerRadius, ringPaint)
        }
    }

    override fun onDetachedFromWindow() {
        removePreDraw()
        super.onDetachedFromWindow()
    }
}

private fun View.isDescendantOf(root: View): Boolean {
    var parentView = parent
    while (parentView is View) {
        if (parentView === root) return true
        parentView = parentView.parent
    }
    return false
}
