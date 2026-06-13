package sukun.minimalist.app.launcher.com.ui

import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Filter
import android.widget.Filterable
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import sukun.minimalist.app.launcher.com.R
import sukun.minimalist.app.launcher.com.data.AppModel
import sukun.minimalist.app.launcher.com.data.Constants
import sukun.minimalist.app.launcher.com.databinding.AdapterAppDrawerBinding
import sukun.minimalist.app.launcher.com.databinding.AdapterAppSectionHeaderBinding
import sukun.minimalist.app.launcher.com.databinding.AdapterPrivateSpaceHeaderBinding
import sukun.minimalist.app.launcher.com.helper.PremiumAccess
import sukun.minimalist.app.launcher.com.helper.dpToPx
import sukun.minimalist.app.launcher.com.helper.getColorFromAttr
import sukun.minimalist.app.launcher.com.helper.hideKeyboard
import sukun.minimalist.app.launcher.com.helper.isSystemApp
import sukun.minimalist.app.launcher.com.helper.showKeyboard
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import java.text.Normalizer

class AppDrawerAdapter(
    private var flag: Int,
    private val appLabelGravity: Int,
    private val showAppIcons: Boolean,
    private val onListChanged: (List<AppModel>) -> Unit = {},
    private val appClickListener: (AppModel) -> Unit,
    private val appInfoListener: (AppModel) -> Unit,
    private val appDeleteListener: (AppModel) -> Unit,
    private val appHideListener: (AppModel, Int) -> Unit,
    private val appRenameListener: (AppModel, String) -> Unit,
    private val privateSpaceToggleListener: () -> Unit = {},
    private val privateSpaceSettingsListener: () -> Unit = {},
    private val appCooldownLimitListener: (AppModel) -> Unit = {},
) : ListAdapter<AppModel, RecyclerView.ViewHolder>(DIFF_CALLBACK), Filterable {

    var premiumLocked: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (itemCount > 0) notifyItemRangeChanged(0, itemCount)
        }

    companion object {
        const val VIEW_TYPE_APP = 0
        const val VIEW_TYPE_PRIVATE_HEADER = 1
        const val VIEW_TYPE_SECTION_HEADER = 2

        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AppModel>() {
            override fun areItemsTheSame(oldItem: AppModel, newItem: AppModel): Boolean = when {
                oldItem is AppModel.App && newItem is AppModel.App ->
                    oldItem.appPackage == newItem.appPackage && oldItem.user == newItem.user

                oldItem is AppModel.PinnedShortcut && newItem is AppModel.PinnedShortcut ->
                    oldItem.shortcutId == newItem.shortcutId && oldItem.user == newItem.user

                oldItem is AppModel.PrivateSpaceHeader && newItem is AppModel.PrivateSpaceHeader -> true
                oldItem is AppModel.SectionHeader && newItem is AppModel.SectionHeader ->
                    oldItem.sectionKey == newItem.sectionKey

                else -> false
            }

            override fun areContentsTheSame(oldItem: AppModel, newItem: AppModel): Boolean =
                oldItem == newItem
        }
    }

    private var autoLaunch = true
    private var isBangSearch = false
    private val appFilter = createAppFilter()
    private val myUserHandle = android.os.Process.myUserHandle()
    private val sectionPositions = linkedMapOf<String, Int>()
    var cooledOffPackages: Set<String> = emptySet()
        private set

    var appsList: MutableList<AppModel> = mutableListOf()
    var appFilteredList: MutableList<AppModel> = mutableListOf()

    override fun getItemViewType(position: Int): Int {
        return when (appFilteredList.getOrNull(position)) {
            is AppModel.PrivateSpaceHeader -> VIEW_TYPE_PRIVATE_HEADER
            is AppModel.SectionHeader -> VIEW_TYPE_SECTION_HEADER
            else -> VIEW_TYPE_APP
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_PRIVATE_HEADER -> PrivateSpaceHeaderViewHolder(
                AdapterPrivateSpaceHeaderBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
            VIEW_TYPE_SECTION_HEADER -> SectionHeaderViewHolder(
                AdapterAppSectionHeaderBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            else -> ViewHolder(
                AdapterAppDrawerBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        try {
            if (appFilteredList.isEmpty() || position == RecyclerView.NO_POSITION) return
            val appModel = appFilteredList[holder.bindingAdapterPosition]
            when (holder) {
                is PrivateSpaceHeaderViewHolder -> {
                    holder.bind(
                        appLabelGravity,
                        premiumLocked,
                        privateSpaceToggleListener,
                        privateSpaceSettingsListener,
                    )
                }
                is SectionHeaderViewHolder -> holder.bind(appModel.appLabel, appLabelGravity)

                is ViewHolder -> holder.bind(
                    flag,
                    appLabelGravity,
                    showAppIcons,
                    myUserHandle,
                    appModel,
                    appModel.appPackage in cooledOffPackages,
                    appClickListener,
                    appDeleteListener,
                    appInfoListener,
                    appHideListener,
                    appRenameListener,
                    appCooldownLimitListener,
                    premiumLocked,
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getFilter(): Filter = this.appFilter

    private fun createAppFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(charSearch: CharSequence?): FilterResults {
                isBangSearch = charSearch?.startsWith("!") ?: false
                autoLaunch = charSearch?.startsWith(" ")?.not() ?: true

                val appFilteredList = if (charSearch.isNullOrBlank()) {
                    appsList
                } else {
                    appsList.filter { app ->
                        app !is AppModel.PrivateSpaceHeader &&
                                app !is AppModel.SectionHeader &&
                                appLabelMatches(app.appLabel, charSearch)
                    }.distinctBy { app ->
                        if (app is AppModel.App) app.appPackage to app.user else app
                    }.toMutableList()
                }

                val filterResults = FilterResults()
                filterResults.values = appFilteredList
                return filterResults
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                results?.values?.let {
                    val items = (it as MutableList<AppModel>).toMutableList()
                    appFilteredList = items
                    rebuildSectionPositions(items)
                    submitList(items.toList()) {
                        onListChanged(appFilteredList.toList())
                        autoLaunch()
                    }
                }
            }
        }
    }

    private fun autoLaunch() {
        try {
            if (itemCount == 1
                && autoLaunch
                && isBangSearch.not()
                && flag == Constants.FLAG_LAUNCH_APP
                && appFilteredList.isNotEmpty()
                && appFilteredList[0] !is AppModel.PrivateSpaceHeader
                && appFilteredList[0] !is AppModel.SectionHeader
            ) appClickListener(appFilteredList[0])
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun appLabelMatches(appLabel: String, charSearch: CharSequence): Boolean {
        return (appLabel.contains(charSearch.trim(), true) or
                Normalizer.normalize(appLabel, Normalizer.Form.NFD)
                    .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
                    .replace(Regex("[-_+,. ]"), "")
                    .contains(charSearch, true))
    }

    fun setAppList(appsList: MutableList<AppModel>) {
        // Add empty app for bottom padding in recyclerview and assign to list
        val updatedApps = appsList.toMutableList().apply {
            add(
            AppModel.App(
                appLabel = "",
                key = null,
                appPackage = "",
                activityClassName = "",
                isNew = false,
                user = android.os.Process.myUserHandle()
            )
            )
        }
        this.appsList = updatedApps
        this.appFilteredList = updatedApps.toMutableList()
        rebuildSectionPositions(this.appFilteredList)
        submitList(this.appFilteredList.toList()) {
            onListChanged(appFilteredList.toList())
        }
    }

    fun launchFirstInList() {
        val first = appFilteredList.firstOrNull {
            it !is AppModel.PrivateSpaceHeader && it !is AppModel.SectionHeader
        }
        if (first != null) appClickListener(first)
    }

    fun updateCooledOff(packages: Set<String>) {
        cooledOffPackages = packages
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount)
    }

    fun getSections(): List<String> = sectionPositions.keys.toList()

    fun getPositionForSection(section: String): Int? = sectionPositions[section]

    private fun rebuildSectionPositions(items: List<AppModel>) {
        sectionPositions.clear()
        items.forEachIndexed { index, item ->
            val section = getSectionLabel(item) ?: return@forEachIndexed
            if (sectionPositions.containsKey(section).not()) {
                sectionPositions[section] = index
            }
        }
    }

    private fun getSectionLabel(item: AppModel): String? {
        if (item is AppModel.PrivateSpaceHeader || item is AppModel.SectionHeader || item.appLabel.isBlank()) return null
        val normalized = Normalizer.normalize(item.appLabel.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        val leadingChar = normalized.firstOrNull { char -> char.isLetterOrDigit() } ?: return "#"
        return if (leadingChar.isLetter()) leadingChar.uppercaseChar().toString() else "#"
    }

    class SectionHeaderViewHolder(private val binding: AdapterAppSectionHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(title: String, appLabelGravity: Int) = with(binding) {
            sectionTitle.text = title
            sectionTitle.gravity = appLabelGravity
        }
    }

    class PrivateSpaceHeaderViewHolder(private val binding: AdapterPrivateSpaceHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(
            appLabelGravity: Int,
            premiumLocked: Boolean,
            toggleListener: () -> Unit,
            settingsListener: () -> Unit,
        ) = with(binding) {
            privateSpaceTitle.gravity = appLabelGravity
            privateSpaceTitle.alpha = if (premiumLocked) PremiumAccess.LOCKED_ALPHA else 1f
            privateSpaceTitle.setOnClickListener { toggleListener() }
            privateSpaceTitle.setOnLongClickListener {
                settingsListener()
                true
            }
        }
    }

    class ViewHolder(private val binding: AdapterAppDrawerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(
            flag: Int,
            appLabelGravity: Int,
            showAppIcons: Boolean,
            myUserHandle: UserHandle,
            appModel: AppModel,
            isCoolingOff: Boolean,
            clickListener: (AppModel) -> Unit,
            appDeleteListener: (AppModel) -> Unit,
            appInfoListener: (AppModel) -> Unit,
            appHideListener: (AppModel, Int) -> Unit,
            appRenameListener: (AppModel, String) -> Unit,
            cooldownLimitListener: (AppModel) -> Unit,
            premiumLocked: Boolean,
        ) = with(binding) {
            val lockedAlpha = if (premiumLocked) PremiumAccess.LOCKED_ALPHA else 1f
            appHide.alpha = lockedAlpha
            appRename.alpha = lockedAlpha
            appCooldownLimit.alpha = lockedAlpha
            appHideLayout.visibility = View.GONE
            renameLayout.visibility = View.GONE
            appTitle.visibility = View.VISIBLE

            // Show indicators in title based on app type and state
            val baseLabel = buildString {
                append(appModel.appLabel)
                if (appModel.isNew) append(" ✦")
            }
            if (isCoolingOff && appModel.appPackage.isNotBlank()) {
                val indicator = "  ⏸"
                val full = baseLabel + indicator
                val span = SpannableString(full)
                val start = full.length - indicator.length
                span.setSpan(RelativeSizeSpan(0.85f), start, full.length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
                span.setSpan(
                    ForegroundColorSpan(appTitle.context.getColorFromAttr(R.attr.primaryColorTrans80)),
                    start, full.length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                appTitle.text = span
            } else {
                appTitle.text = baseLabel
            }
            appTitle.gravity = appLabelGravity
            setAppIcon(showAppIcons, appModel)
            otherProfileIndicator.isVisible = appModel.user != myUserHandle

            appTitle.setOnClickListener { clickListener(appModel) }
            appCooldownLimit.setOnClickListener {
                appHideLayout.visibility = View.GONE
                appTitle.visibility = View.VISIBLE
                cooldownLimitListener(appModel)
            }

            appTitle.setOnLongClickListener {
                if (appModel.appPackage.isNotEmpty()) {
                    appDelete.alpha = when (
                        appModel is AppModel.PinnedShortcut || !root.context.isSystemApp(appModel.appPackage, appModel.user)
                    ) {
                        true -> 1.0f
                        false -> 0.5f
                    }
                    appHide.text = if (flag == Constants.FLAG_HIDDEN_APPS)
                        root.context.getString(R.string.adapter_show)
                    else
                        root.context.getString(R.string.adapter_hide)
                    appTitle.visibility = View.INVISIBLE
                    appHide.alpha = when (appModel is AppModel.PinnedShortcut) {
                        true -> 0.5f
                        false -> 1.0f
                    }
                    appHideLayout.visibility = View.VISIBLE
                    // Only allow renaming non hidden apps
                    appRename.isVisible = flag != Constants.FLAG_HIDDEN_APPS
                }
                true
            }

            // Configure rename behavior
            appRename.setOnClickListener {
                if (appModel.appPackage.isNotEmpty()) {
                    etAppRename.hint = getAppName(etAppRename.context, appModel.appPackage, appModel.user)
                    etAppRename.setText(appModel.appLabel)
                    etAppRename.setSelectAllOnFocus(true)
                    renameLayout.visibility = View.VISIBLE
                    appHideLayout.visibility = View.GONE
                    etAppRename.showKeyboard()
                    etAppRename.imeOptions = EditorInfo.IME_ACTION_DONE
                }
            }
            etAppRename.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                appTitle.visibility = if (hasFocus) View.INVISIBLE else View.VISIBLE
            }
            etAppRename.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    etAppRename.hint = getAppName(etAppRename.context, appModel.appPackage, appModel.user)
                }

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    etAppRename.hint = ""
                }
            })
            etAppRename.setOnEditorActionListener { _, actionCode, _ ->
                if (actionCode == EditorInfo.IME_ACTION_DONE) {
                    val renameLabel = etAppRename.text.toString().trim()
                    if (renameLabel.isNotBlank() && appModel.appPackage.isNotBlank()) {
                        appRenameListener(appModel, renameLabel)
                        renameLayout.visibility = View.GONE
                    }
                    true
                }
                false
            }
            tvSaveRename.setOnClickListener {
                etAppRename.hideKeyboard()
                val renameLabel = etAppRename.text.toString().trim()
                if (renameLabel.isNotBlank() && appModel.appPackage.isNotBlank()) {
                    appRenameListener(appModel, renameLabel)
                    renameLayout.visibility = View.GONE
                } else {
                    appRenameListener(
                        appModel,
                        getAppName(etAppRename.context, appModel.appPackage, appModel.user)
                    )
                    renameLayout.visibility = View.GONE
                }
            }
            appInfo.setOnClickListener { appInfoListener(appModel) }
            appDelete.setOnClickListener { appDeleteListener(appModel) }
            appMenuClose.setOnClickListener {
                appHideLayout.visibility = View.GONE
                appTitle.visibility = View.VISIBLE
            }
            appRenameClose.setOnClickListener {
                renameLayout.visibility = View.GONE
                appTitle.visibility = View.VISIBLE
            }
            appHide.setOnClickListener { appHideListener(appModel, bindingAdapterPosition) }
        }

        private fun setAppIcon(showAppIcons: Boolean, appModel: AppModel) {
            val appTitle = binding.appTitle
            if (!showAppIcons || appModel.appPackage.isBlank()) {
                appTitle.setCompoundDrawablesRelative(null, null, null, null)
                appTitle.compoundDrawablePadding = 0
                return
            }
            val launcherApps = appTitle.context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val drawable = try {
                when (appModel) {
                    is AppModel.PinnedShortcut -> {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N_MR1) {
                            val query = LauncherApps.ShortcutQuery().apply {
                                setPackage(appModel.appPackage)
                                setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
                            }
                            launcherApps.getShortcuts(query, appModel.user)
                                ?.firstOrNull { it.id == appModel.shortcutId }
                                ?.let { launcherApps.getShortcutIconDrawable(it, appTitle.resources.displayMetrics.densityDpi) }
                        } else {
                            null
                        }
                    }

                    is AppModel.App -> launcherApps.getActivityList(appModel.appPackage, appModel.user)
                        .firstOrNull { it.componentName.className == appModel.activityClassName }
                        ?.getBadgedIcon(appTitle.resources.displayMetrics.densityDpi)
                        ?: launcherApps.getActivityList(appModel.appPackage, appModel.user)
                            .firstOrNull()
                            ?.getBadgedIcon(appTitle.resources.displayMetrics.densityDpi)

                    else -> null
                }
            } catch (_: Exception) {
                null
            }
            if (drawable == null) {
                appTitle.setCompoundDrawablesRelative(null, null, null, null)
                appTitle.compoundDrawablePadding = 0
                return
            }
            val iconSize = 24.dpToPx()
            drawable.setBounds(0, 0, iconSize, iconSize)
            appTitle.setCompoundDrawablesRelative(drawable, null, null, null)
            appTitle.compoundDrawablePadding = 12.dpToPx()
        }

        private fun getAppName(context: Context, appPackage: String, user: UserHandle): String {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            return try {
                val activityList = launcherApps.getActivityList(appPackage, user)
                if (activityList.isNotEmpty()) {
                    activityList.first().label.toString()
                } else {
                    val packageManager = context.packageManager
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(appPackage, 0)
                    ).toString()
                }
            } catch (_: Exception) {
                "" // As a fallback, display an empty string.
            }
        }
    }
}
