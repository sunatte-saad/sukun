package sukun.minimalist.app.launcher.com.ui

import android.os.Build
import android.text.Layout
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import sukun.minimalist.app.launcher.com.R
import sukun.minimalist.app.launcher.com.data.Prefs
import sukun.minimalist.app.launcher.com.widget.SettingsSectionsLayout

/**
 * Settings layers: all sections stay on one scrollable page. Pencil / edit mode
 * collapses them to titles with a drag handle so layers can be reordered.
 */
class SettingsSectionsController(
    private val container: SettingsSectionsLayout,
    private val prefs: Prefs,
) {

    private val cards = LinkedHashMap<String, ViewGroup>()
    private val titleTextSizesPx = mutableMapOf<TextView, Float>()
    private val titleOriginals = mutableMapOf<TextView, CharSequence>()
    private val cardPaddings = mutableMapOf<View, IntArray>()

    var editMode = false
        private set

    var onEditModeChanged: ((Boolean) -> Unit)? = null

    fun attach() {
        (container.findViewById<View>(R.id.sectionSukun) as? ViewGroup)?.let { card ->
            ensureBody(card)
            prepareCard(card)
            cards[SUKUN_KEY] = card
        }
        SECTION_IDS.forEach { (key, id) ->
            (container.findViewById<View>(id) as? ViewGroup)?.let { card ->
                ensureBody(card)
                prepareCard(card)
                cards[key] = card
            }
        }
        container.setPinned(R.id.sectionSukun)
        container.setFullWidth(R.id.sectionSukun, true)
        container.onOrderChanged = { ids -> persistOrder(ids) }
        applyOrder()
        applyWidths()
        applyPresentation()
        container.post { applyPresentation() }
    }

    fun setEditMode(enabled: Boolean) {
        if (editMode == enabled) return
        editMode = enabled
        container.editMode = enabled
        applyEditChrome()
        applyPresentation()
        onEditModeChanged?.invoke(enabled)
    }

    /** Kept for back-press; scrollable settings has nothing to collapse. */
    fun collapseAll(): Boolean = false

    /** Scrolls to the card that contains [target], used by onboarding. */
    fun expandContaining(target: View) {
        var current: View? = target
        while (current != null) {
            val key = cards.entries.firstOrNull { it.value === current }?.key
            if (key != null) {
                val card = cards[key] ?: return
                (container.parent as? ScrollView)?.post {
                    (container.parent as? ScrollView)?.smoothScrollTo(0, card.top)
                }
                return
            }
            current = current.parent as? View
        }
    }

    private fun applyWidths() {
        SECTION_IDS.values.forEach { id -> container.setFullWidth(id, true) }
    }

    private fun applyOrder() {
        val stored = prefs.settingsSectionOrder.filter { it in SECTION_IDS }
        val keys = stored + DEFAULT_ORDER.filterNot { it in stored }
        container.setOrder(listOf(R.id.sectionSukun) + keys.mapNotNull { SECTION_IDS[it] })
    }

    private fun applyPresentation() {
        prefs.settingsExpandedSection = ""
        cards.forEach { (key, card) ->
            val collapsed = editMode && key != SUKUN_KEY
            card.isVisible = true
            card.findViewById<View>(R.id.settingsSectionBody)?.isVisible = !collapsed
            container.setCollapsed(card.id, collapsed)
            applyCompactMetrics(key, card, collapsed)
            applyBackChevron(card, false)
            card.setOnClickListener(null)
            card.isClickable = false
            val header = card.getChildAt(0)
            header?.setOnClickListener(null)
            header?.isClickable = false
            card.findViewById<View>(R.id.sukunHiddenApps)?.isClickable = key == SUKUN_KEY
            card.setOnLongClickListener {
                if (!editMode) {
                    setEditMode(true)
                    true
                } else {
                    false
                }
            }
        }
        container.requestLayout()
    }

    private fun prepareCard(card: ViewGroup) {
        val lp = card.layoutParams
        if (lp is ViewGroup.MarginLayoutParams) {
            lp.setMargins(0, 0, 0, 0)
            card.layoutParams = lp
        }
        card.clipToOutline = true
        card.clipChildren = true
        card.clipToPadding = true
        if (card.id != R.id.sectionSukun) ensureDragHandle(card)
    }

    /** Puts a trailing drag handle over the title so the label stays centered. */
    private fun ensureDragHandle(card: ViewGroup) {
        if (card.findViewById<View>(R.id.settingsDragHandle) != null) return
        val header = card.getChildAt(0) ?: return
        val density = card.resources.displayMetrics.density
        val handle = ImageView(card.context).apply {
            id = R.id.settingsDragHandle
            setImageResource(R.drawable.ic_drag_handle)
            contentDescription = context.getString(R.string.settings_reorder_handle)
            val pad = (10 * density).toInt()
            setPadding(pad, pad, pad, pad)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            isVisible = false
            alpha = 0.7f
        }
        val handleLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            marginEnd = (2 * density).toInt()
        }
        if (header is FrameLayout) {
            header.addView(handle, handleLp)
            return
        }
        val savedTransition = card.layoutTransition
        card.layoutTransition = null
        val headerLp = header.layoutParams
        detachImmediately(header)
        val wrapper = FrameLayout(card.context).apply {
            layoutParams = headerLp
            clipChildren = false
            clipToPadding = false
        }
        wrapper.addView(
            header,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        wrapper.addView(handle, handleLp)
        if (wrapper.parent == null) {
            card.addView(wrapper, 0)
        }
        card.layoutTransition = savedTransition
    }

    private fun applyBackChevron(card: ViewGroup, @Suppress("UNUSED_PARAMETER") detail: Boolean) {
        card.findViewById<View>(R.id.settingsBackChevron)?.isVisible = false
    }

    private fun applyCompactMetrics(key: String, card: ViewGroup, collapsed: Boolean) {
        val header = card.getChildAt(0)
        val density = card.resources.displayMetrics.density
        card.minimumHeight = if (collapsed) collapsedRowMinHeight(density) else 0
        applyCollapsedPadding(card, collapsed)
        (card as? LinearLayout)?.gravity = if (collapsed) Gravity.CENTER else Gravity.TOP
        val headerLp = header?.layoutParams as? ViewGroup.MarginLayoutParams
        if (headerLp != null) {
            headerLp.width = ViewGroup.LayoutParams.MATCH_PARENT
            headerLp.height = if (collapsed) {
                ViewGroup.LayoutParams.MATCH_PARENT
            } else {
                ViewGroup.LayoutParams.WRAP_CONTENT
            }
            if (collapsed) {
                headerLp.setMargins(0, 0, 0, 0)
            } else {
                headerLp.setMargins(0, (8 * density).toInt(), 0, (4 * density).toInt())
            }
            header.layoutParams = headerLp
        }
        val titleView = headerTitle(card) ?: return
        configureTitle(titleView, key, collapsed, stacked = collapsed)
    }

    private fun headerTitle(card: ViewGroup): TextView? {
        val header = card.getChildAt(0) ?: return null
        if (header is TextView) return header
        if (header is ViewGroup) {
            header.findViewById<TextView>(R.id.sukunHiddenApps)?.let { return it }
            for (index in 0 until header.childCount) {
                (header.getChildAt(index) as? TextView)?.let { return it }
            }
        }
        return null
    }

    private fun configureTitle(title: TextView, key: String, collapsed: Boolean, stacked: Boolean) {
        titleTextSizesPx.getOrPut(title) { title.textSize }
        val original = titleOriginals.getOrPut(title) { title.text }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            title.breakStrategy = Layout.BREAK_STRATEGY_HIGH_QUALITY
            title.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        }
        title.includeFontPadding = false
        TextViewCompat.setAutoSizeTextTypeWithDefaults(
            title,
            TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE,
        )
        title.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
        if (!stacked || key == SUKUN_KEY) {
            title.text = original
            title.setTextSize(TypedValue.COMPLEX_UNIT_PX, titleTextSizesPx[title] ?: title.textSize)
            title.maxLines = Int.MAX_VALUE
            title.setLineSpacing(0f, 1f)
            title.gravity = if (key == SUKUN_KEY) Gravity.CENTER_VERTICAL else Gravity.START or Gravity.CENTER_VERTICAL
            title.textAlignment = View.TEXT_ALIGNMENT_GRAVITY
            return
        }
        title.text = original
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, titleTextSizesPx[title] ?: title.textSize)
        title.setLineSpacing(2f * title.resources.displayMetrics.density, 1f)
        title.gravity = Gravity.CENTER
        title.textAlignment = View.TEXT_ALIGNMENT_CENTER
        title.maxLines = 3
    }

    private fun applyEditChrome() {
        cards.forEach { (key, card) ->
            card.foreground = null
            card.scaleX = 1f
            card.scaleY = 1f
            card.rotation = 0f
            card.findViewById<View>(R.id.settingsDragHandle)?.isVisible =
                editMode && key != SUKUN_KEY
            card.findViewById<View>(R.id.settingsBackChevron)?.isVisible = false
        }
    }

    private fun applyCollapsedPadding(card: ViewGroup, collapsed: Boolean) {
        val original = cardPaddings.getOrPut(card) {
            intArrayOf(card.paddingLeft, card.paddingTop, card.paddingRight, card.paddingBottom)
        }
        if (!collapsed) {
            card.setPadding(original[0], original[1], original[2], original[3])
            return
        }
        val d = card.resources.displayMetrics.density
        val horizontal = (16f * d).toInt()
        val vertical = (14f * d).toInt()
        card.setPadding(horizontal, vertical, horizontal, vertical)
    }

    /** Equal share of the settings viewport so collapsed layers fill the screen. */
    private fun collapsedRowMinHeight(density: Float): Int {
        val count = cards.size.coerceAtLeast(1)
        val parent = container.parent as? View
        val viewport = parent?.let { p ->
            val h = p.height.takeIf { it > 0 } ?: p.measuredHeight
            if (h > 0) h - p.paddingTop - p.paddingBottom else 0
        } ?: 0
        val floor = (EDIT_ROW_MIN_HEIGHT_DP * density).toInt()
        if (viewport <= 0) return floor
        val gutters = (ROW_GUTTER_DP * density).toInt() * (count - 1)
        val budget = viewport - container.paddingTop - container.paddingBottom - gutters
        return (budget / count).coerceAtLeast(floor)
    }

    /**
     * Moves every row under the section title into a single body view so collapsing can hide
     * details with [View.GONE] instead of clipping overflow (which still painted over neighbors).
     */
    private fun ensureBody(card: ViewGroup): ViewGroup {
        val existing = card.findViewById<ViewGroup>(R.id.settingsSectionBody)
        if (existing != null) return existing
        val body = LinearLayout(card.context).apply {
            id = R.id.settingsSectionBody
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            clipChildren = false
            clipToPadding = false
        }
        // LayoutTransition (animateLayoutChanges) keeps a child attached until its
        // remove animation finishes, so the following addView() would throw
        // "The specified child already has a parent" and abort settings setup.
        val savedTransition = card.layoutTransition
        card.layoutTransition = null
        val toMove = (1 until card.childCount).map { card.getChildAt(it) }
        toMove.forEach { child ->
            val lp = child.layoutParams
            detachImmediately(child)
            if (child.parent == null) {
                body.addView(child, lp)
            }
        }
        if (body.parent == null) {
            card.addView(body)
        }
        card.layoutTransition = savedTransition
        return body
    }

    private fun detachImmediately(child: View) {
        val parent = child.parent as? ViewGroup ?: return
        val savedTransition = parent.layoutTransition
        parent.layoutTransition = null
        parent.endViewTransition(child)
        parent.removeView(child)
        parent.layoutTransition = savedTransition
    }

    private fun persistOrder(ids: List<Int>) {
        val byId = SECTION_IDS.entries.associate { (key, id) -> id to key }
        prefs.settingsSectionOrder = ids.mapNotNull { byId[it] }
    }

    private companion object {
        const val EDIT_ROW_MIN_HEIGHT_DP = 72f
        const val ROW_GUTTER_DP = 8f
        const val SUKUN_KEY = "sukun"

        val SECTION_IDS = linkedMapOf(
            "focus" to R.id.sectionFocus,
            "home" to R.id.sectionHomeScreen,
            "account" to R.id.sectionAccount,
            "appearance" to R.id.sectionAppearance,
            "gestures" to R.id.sectionGestures,
        )

        val DEFAULT_ORDER = SECTION_IDS.keys.toList()
    }
}
