package sukun.minimalist.app.launcher.com.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs

/**
 * Vertical container for the settings section cards.
 *
 * Cards take a full row while expanded, pinned, marked full width, or while [editMode] is on.
 * Collapsed neighbors can share a row. While [editMode] is on, cards can be dragged to reorder.
 */
class SettingsSectionsLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewGroup(context, attrs) {

    /** Ids of cards that always occupy a whole row and cannot be dragged. */
    private val pinnedIds = mutableSetOf<Int>()

    /** Ids of cards currently rendered at full width. */
    private val fullWidthIds = mutableSetOf<Int>()

    /** Ids of cards clipped down to their header row. */
    private val collapsedIds = mutableSetOf<Int>()

    /** Card ids in the order they should be rendered. Ids missing here keep their XML order. */
    private var order: List<Int> = emptyList()

    var editMode = false
        set(value) {
            if (field == value) return
            field = value
            if (!value) stopDrag(animate = false)
            requestLayout()
        }

    /** Invoked with the new card id order once a drag finishes. */
    var onOrderChanged: ((List<Int>) -> Unit)? = null

    /** True while a card is being dragged. */
    var onDragStateChanged: ((Boolean) -> Unit)? = null

    /** Invoked when the container needs the scroll parent to move while dragging. */
    var onDragScrollBy: ((Int) -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val gutter = (GUTTER_DP * resources.displayMetrics.density).toInt()
    private var draggingView: View? = null
    private var downX = 0f
    private var downY = 0f
    private var grabOffsetY = 0f
    private var dragY = 0f
    private var lastSwapAt = 0L

    init {
        clipChildren = false
        clipToPadding = false
    }

    fun setPinned(vararg ids: Int) {
        pinnedIds.clear()
        pinnedIds.addAll(ids.toList())
    }

    fun setOrder(ids: List<Int>) {
        if (order == ids) return
        order = ids
        requestLayout()
    }

    fun currentOrder(): List<Int> = orderedChildren().map { it.id }

    fun setFullWidth(id: Int, full: Boolean) {
        val changed = if (full) fullWidthIds.add(id) else fullWidthIds.remove(id)
        if (changed) requestLayout()
    }

    fun setCollapsed(id: Int, collapsed: Boolean) {
        val changed = if (collapsed) collapsedIds.add(id) else collapsedIds.remove(id)
        if (changed) requestLayout()
    }

    /** Expanded cards always take a whole row, as do pinned, widened, and edit-mode layers. */
    private fun isFullWidthChild(child: View): Boolean =
        editMode || child.id in pinnedIds || child.id in fullWidthIds || child.id !in collapsedIds

    private fun orderedChildren(): List<View> {
        val visible = (0 until childCount)
            .map { getChildAt(it) }
            .filter { it.visibility != View.GONE }
        if (order.isEmpty()) return visible
        val rank = order.withIndex().associate { (index, id) -> id to index }
        return visible.sortedWith(
            compareBy(
                { if (it.id in pinnedIds) 0 else 1 },
                { rank[it.id] ?: Int.MAX_VALUE },
            )
        )
    }

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams =
        MarginLayoutParams(context, attrs)

    override fun generateDefaultLayoutParams(): LayoutParams =
        MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

    override fun generateLayoutParams(p: LayoutParams?): LayoutParams = MarginLayoutParams(p)

    override fun checkLayoutParams(p: LayoutParams?): Boolean = p is MarginLayoutParams

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val available = width - paddingLeft - paddingRight
        val children = orderedChildren()
        val g = gutter
        val rows = mutableListOf<MeasureRow>()

        var index = 0
        while (index < children.size) {
            val child = children[index]
            val partner = partnerFor(children, index)
            if (partner != null) {
                val cellWidth = (available - g) / 2
                measureCard(child, cellWidth)
                measureCard(partner, cellWidth)
                val height = maxOf(child.measuredHeight, partner.measuredHeight)
                val stretch = child.id in collapsedIds && partner.id in collapsedIds
                rows += MeasureRow(listOf(child, partner), cellWidth, height, stretch)
                index += 2
            } else {
                measureCard(child, available)
                rows += MeasureRow(
                    listOf(child),
                    available,
                    child.measuredHeight,
                    stretch = child.id in collapsedIds,
                )
                index += 1
            }
        }

        val natural = paddingTop + paddingBottom +
            rows.sumOf { it.height } +
            g * (rows.size - 1).coerceAtLeast(0)
        val viewport = viewportHeight(heightMeasureSpec)
        // Overview layers share leftover height. A single drilled-in page keeps wrap content.
        val stretchRows = if (children.size <= 1) emptyList() else rows.filter { it.stretch }
        if (viewport > natural && stretchRows.isNotEmpty()) {
            val fixed = rows.filter { !it.stretch }.sumOf { it.height }
            val guttersTotal = g * (rows.size - 1).coerceAtLeast(0)
            val budget = viewport - paddingTop - paddingBottom - fixed - guttersTotal
            val each = budget / stretchRows.size
            var remainder = budget % stretchRows.size
            stretchRows.forEach { row ->
                val target = each + if (remainder > 0) 1 else 0
                if (remainder > 0) remainder -= 1
                row.height = maxOf(row.height, target)
            }
        }

        rows.forEach { row ->
            row.cards.forEach { card -> remeasureToHeight(card, row.cellWidth, row.height) }
        }

        val total = paddingTop + paddingBottom +
            rows.sumOf { it.height } +
            g * (rows.size - 1).coerceAtLeast(0)
        setMeasuredDimension(
            width,
            resolveSize(total.coerceAtLeast(viewport), heightMeasureSpec),
        )
    }

    private fun viewportHeight(heightMeasureSpec: Int): Int {
        val specSize = MeasureSpec.getSize(heightMeasureSpec)
        when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY, MeasureSpec.AT_MOST -> if (specSize > 0) return specSize
        }
        val parentView = parent as? View ?: return 0
        val parentH = parentView.measuredHeight.takeIf { it > 0 } ?: parentView.height
        if (parentH <= 0) return 0
        return (parentH - parentView.paddingTop - parentView.paddingBottom).coerceAtLeast(0)
    }

    private fun measureCard(child: View, cellWidth: Int) {
        val widthSpec = MeasureSpec.makeMeasureSpec(cellWidth.coerceAtLeast(0), MeasureSpec.EXACTLY)
        child.measure(
            widthSpec,
            getChildMeasureSpec(
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                0,
                LayoutParams.WRAP_CONTENT,
            ),
        )
        if (child.id !in collapsedIds) return
        val header = (child as? ViewGroup)?.getChildAt(0) ?: return
        val collapsedHeight = (
            child.paddingTop + child.paddingBottom + header.measuredHeight
            ).coerceAtLeast(child.minimumHeight)
        child.measure(
            widthSpec,
            MeasureSpec.makeMeasureSpec(collapsedHeight, MeasureSpec.EXACTLY),
        )
    }

    private fun remeasureToHeight(child: View, cellWidth: Int, rowHeight: Int) {
        if (child.measuredHeight == rowHeight) return
        child.measure(
            MeasureSpec.makeMeasureSpec(cellWidth.coerceAtLeast(0), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(rowHeight.coerceAtLeast(0), MeasureSpec.EXACTLY),
        )
    }

    /** The card that shares a row with `children[index]`, or null when it needs a full row. */
    private fun partnerFor(children: List<View>, index: Int): View? {
        val child = children[index]
        if (isFullWidthChild(child)) return null
        return children.getOrNull(index + 1)?.takeIf { !isFullWidthChild(it) }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val children = orderedChildren()
        val g = gutter
        val animateSiblings = draggingView != null

        var y = paddingTop
        var index = 0
        while (index < children.size) {
            val child = children[index]
            val partner = partnerFor(children, index)

            if (partner != null) {
                val rowHeight = maxOf(child.measuredHeight, partner.measuredHeight)
                layoutCard(child, paddingLeft, y, animateSiblings)
                layoutCard(partner, paddingLeft + child.measuredWidth + g, y, animateSiblings)
                y += rowHeight + g
                index += 2
            } else {
                layoutCard(child, paddingLeft, y, animateSiblings)
                y += child.measuredHeight + g
                index += 1
            }
        }
    }

    private fun layoutCard(child: View, left: Int, top: Int, animate: Boolean) {
        val oldLeft = child.left
        val oldTop = child.top
        val wasLaidOut = child.isLaidOut
        child.layout(left, top, left + child.measuredWidth, top + child.measuredHeight)

        if (child === draggingView) {
            child.animate().cancel()
            child.translationX = 0f
            child.translationY = dragY - top - grabOffsetY
            return
        }

        if (animate && wasLaidOut && (oldLeft != left || oldTop != top)) {
            child.animate().cancel()
            child.translationX = (oldLeft - left).toFloat()
            child.translationY = (oldTop - top).toFloat()
            child.animate()
                .translationX(0f)
                .translationY(0f)
                .setDuration(SLOT_ANIM_MS)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else if (draggingView == null) {
            child.translationX = 0f
            child.translationY = 0f
        }
    }

    // region drag to reorder

    private fun findCardUnder(x: Float, y: Float): View? {
        return orderedChildren().lastOrNull { child ->
            child.id !in pinnedIds &&
                x >= child.left && x <= child.right &&
                y >= child.top && y <= child.bottom
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!editMode) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                dragY = ev.y
                if (findCardUnder(ev.x, ev.y) != null) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (draggingView != null) return true
                if (abs(ev.y - downY) > touchSlop || abs(ev.x - downX) > touchSlop) {
                    val card = findCardUnder(downX, downY) ?: return false
                    startDrag(card)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                stopDrag(animate = draggingView != null)
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!editMode) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragY = event.y
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dragged = draggingView ?: run {
                    if (abs(event.y - downY) <= touchSlop && abs(event.x - downX) <= touchSlop) {
                        return true
                    }
                    val card = findCardUnder(downX, downY) ?: return true
                    startDrag(card)
                    draggingView
                } ?: return true

                dragY = event.y
                dragged.translationX = 0f
                dragged.translationY = dragY - dragged.top - grabOffsetY
                autoScrollIfNeeded(event.y)
                maybeSwap(dragged, event.y)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasDragging = draggingView != null
                stopDrag(animate = wasDragging)
                parent?.requestDisallowInterceptTouchEvent(false)
                if (wasDragging) onOrderChanged?.invoke(currentOrder())
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun startDrag(card: View) {
        card.animate().cancel()
        draggingView = card
        grabOffsetY = downY - card.top
        dragY = downY
        card.translationZ = DRAG_Z
        card.scaleX = DRAG_SCALE
        card.scaleY = DRAG_SCALE
        parent?.requestDisallowInterceptTouchEvent(true)
        onDragStateChanged?.invoke(true)
    }

    private fun stopDrag(animate: Boolean) {
        val card = draggingView ?: return
        draggingView = null
        val endAction = Runnable {
            card.translationZ = 0f
            onDragStateChanged?.invoke(false)
        }
        if (animate) {
            card.animate()
                .translationX(0f)
                .translationY(0f)
                .translationZ(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(SLOT_ANIM_MS)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction(endAction)
                .start()
        } else {
            card.animate().cancel()
            card.translationX = 0f
            card.translationY = 0f
            card.scaleX = 1f
            card.scaleY = 1f
            endAction.run()
        }
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    private fun autoScrollIfNeeded(y: Float) {
        val scroller = onDragScrollBy ?: return
        val scrollerView = parent as? View ?: return
        val visibleTop = scrollerView.scrollY.toFloat()
        val visibleBottom = visibleTop + scrollerView.height
        val edge = AUTO_SCROLL_EDGE_DP * resources.displayMetrics.density
        when {
            y < visibleTop + edge -> scroller(-AUTO_SCROLL_STEP)
            y > visibleBottom - edge -> scroller(AUTO_SCROLL_STEP)
        }
    }

    /** Moves the dragged layer up or down when the finger crosses a neighbour's midpoint. */
    private fun maybeSwap(dragged: View, y: Float) {
        val now = System.currentTimeMillis()
        if (now - lastSwapAt < SWAP_THROTTLE_MS) return

        val children = orderedChildren().filter { it.id !in pinnedIds }
        if (children.size < 2) return
        val from = children.indexOf(dragged)
        if (from < 0) return

        val prev = children.getOrNull(from - 1)
        val next = children.getOrNull(from + 1)
        val targetIndex = when {
            next != null && y > next.top + next.measuredHeight / 2f -> from + 1
            prev != null && y < prev.top + prev.measuredHeight / 2f -> from - 1
            else -> return
        }

        val reordered = children.map { it.id }.toMutableList()
        reordered.removeAt(from)
        reordered.add(targetIndex, dragged.id)
        val nextOrder = pinnedOrder() + reordered
        if (nextOrder == order) return
        order = nextOrder
        lastSwapAt = now
        requestLayout()
    }

    private fun pinnedOrder(): List<Int> =
        (0 until childCount)
            .map { getChildAt(it) }
            .filter { it.id in pinnedIds }
            .map { it.id }

    // endregion

    private companion object {
        const val GUTTER_DP = 8f
        const val DRAG_Z = 16f
        const val DRAG_SCALE = 1.02f
        const val AUTO_SCROLL_EDGE_DP = 72f
        const val AUTO_SCROLL_STEP = 18
        const val SLOT_ANIM_MS = 180L
        const val SWAP_THROTTLE_MS = 140L
    }

    private class MeasureRow(
        val cards: List<View>,
        val cellWidth: Int,
        var height: Int,
        val stretch: Boolean,
    )
}
