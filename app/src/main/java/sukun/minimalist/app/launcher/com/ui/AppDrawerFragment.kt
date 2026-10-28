package sukun.minimalist.app.launcher.com.ui

import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Recycler
import sukun.minimalist.app.launcher.com.MainActivity
import sukun.minimalist.app.launcher.com.MainViewModel
import sukun.minimalist.app.launcher.com.R
import sukun.minimalist.app.launcher.com.data.AppCooldownConfig
import sukun.minimalist.app.launcher.com.data.AppModel
import sukun.minimalist.app.launcher.com.data.Constants
import sukun.minimalist.app.launcher.com.data.OnboardingAction
import sukun.minimalist.app.launcher.com.data.Prefs
import sukun.minimalist.app.launcher.com.databinding.FragmentAppDrawerBinding
import sukun.minimalist.app.launcher.com.helper.PremiumAccess
import sukun.minimalist.app.launcher.com.helper.deletePinnedShortcut
import sukun.minimalist.app.launcher.com.helper.dpToPx
import sukun.minimalist.app.launcher.com.helper.getColorFromAttr
import sukun.minimalist.app.launcher.com.helper.hideKeyboard
import sukun.minimalist.app.launcher.com.helper.isEinkDisplay
import sukun.minimalist.app.launcher.com.helper.isPrivateSpaceProfile
import sukun.minimalist.app.launcher.com.helper.isSystemApp
import sukun.minimalist.app.launcher.com.helper.openAppInfo
import sukun.minimalist.app.launcher.com.helper.openSearch
import sukun.minimalist.app.launcher.com.helper.openUrl
import sukun.minimalist.app.launcher.com.helper.showKeyboard
import sukun.minimalist.app.launcher.com.helper.showToast
import sukun.minimalist.app.launcher.com.helper.uninstall

class AppDrawerFragment : Fragment() {

    private lateinit var prefs: Prefs
    private lateinit var adapter: AppDrawerAdapter
    private lateinit var linearLayoutManager: LinearLayoutManager

    private var flag = Constants.FLAG_LAUNCH_APP
    private var canRename = false
    private var currentAppList: List<AppModel>? = null
    private var currentPrivateSpaceApps: List<AppModel>? = null
    private var currentPrivateSpaceLocked: Boolean = true
    private var currentPrivateSpaceAvailable: Boolean = false
    private var combineListRunnable: Runnable? = null
    private var listBottomPadding = 0
    private var systemBottomInset = 0
    private var imeBottomInset = 0
    private var searchExpanded = false
    private var openedSearchForTour = false
    private var hideSpotlightAttempts = 0
    private val drawerLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        updateKeyboardOverlapPadding()
        alignFastScrollerToAllApps()
    }

    private val viewModel: MainViewModel by activityViewModels()
    private var _binding: FragmentAppDrawerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAppDrawerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        if (prefs.isFocusModeActive()) {
            requireContext().showToast(R.string.focus_mode_blocked)
            findNavController().popBackStack(R.id.mainFragment, false)
            return
        }
        arguments?.let {
            flag = it.getInt(Constants.Key.FLAG, Constants.FLAG_LAUNCH_APP)
            canRename = it.getBoolean(Constants.Key.RENAME, false)
        }

        initViews()
        setupDrawerPositioning()
        initAdapter()
        initSearch()
        initObservers()
        setupDrawerOnboardingSpotlight()
        initClickListeners()
        currentAppList = viewModel.appList.value
        if (currentAppList.isNullOrEmpty()) {
            viewModel.getAppList()
        } else {
            scheduleUpdateCombinedAppList()
        }
    }

    private fun initViews() {
        binding.appRename.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
        binding.appRename.setShadowLayer(4f, 0f, 2f, requireContext().getColorFromAttr(R.attr.primaryTextShadowColor))
        if (flag == Constants.FLAG_HIDDEN_APPS)
            binding.search.queryHint = getString(R.string.hidden_apps)
        else if (flag in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_CALENDAR_APP)
            binding.search.queryHint = "Please select an app"
        else
            binding.search.queryHint = getString(R.string.search_apps_hint)
        try {
            binding.search.findViewById<View>(androidx.appcompat.R.id.search_mag_icon)?.apply {
                layoutParams = layoutParams.apply {
                    width = 0
                    height = 0
                }
                isVisible = false
            }
            val searchTextView = binding.search.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
            if (searchTextView != null) {
                searchTextView.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                searchTextView.setTextSize(
                    android.util.TypedValue.COMPLEX_UNIT_PX,
                    resources.getDimension(R.dimen.text_small),
                )
                searchTextView.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
                searchTextView.setHintTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
                searchTextView.setShadowLayer(4f, 0f, 2f, requireContext().getColorFromAttr(R.attr.primaryTextShadowColor))
                searchTextView.isCursorVisible = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    searchTextView.setTextCursorDrawable(R.drawable.search_cursor)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupDrawerPositioning() {
        listBottomPadding = binding.recyclerView.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            systemBottomInset = sys.bottom
            imeBottomInset = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            view.setPadding(view.paddingLeft, sys.top, view.paddingRight, view.paddingBottom)
            updateKeyboardOverlapPadding()
            insets
        }
        binding.root.viewTreeObserver.addOnGlobalLayoutListener(drawerLayoutListener)
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun updateKeyboardOverlapPadding() {
        val drawer = _binding ?: return
        val overlap = keyboardOverlapPx(drawer.root)
        val bottom = listBottomPadding + overlap
        if (drawer.recyclerView.paddingBottom != bottom) {
            drawer.recyclerView.setPadding(
                drawer.recyclerView.paddingLeft,
                drawer.recyclerView.paddingTop,
                drawer.recyclerView.paddingRight,
                bottom,
            )
            drawer.fastScroller.setPadding(
                drawer.fastScroller.paddingLeft,
                drawer.fastScroller.paddingTop,
                drawer.fastScroller.paddingRight,
                overlap,
            )
            alignFastScrollerToAllApps()
            scrollSearchResultsToTop()
        }
        val fabBottom = maxOf(systemBottomInset, overlap) + 16.dpToPx()
        val lp = drawer.drawerActions.layoutParams as? android.view.ViewGroup.MarginLayoutParams
        if (lp != null && lp.bottomMargin != fabBottom) {
            lp.bottomMargin = fabBottom
            drawer.drawerActions.layoutParams = lp
        }
    }

    /**
     * Pixels of this drawer that sit behind the keyboard.
     * Returns 0 when the keyboard is closed, or when the window has already
     * resized so the drawer ends above the keyboard.
     */
    private fun keyboardOverlapPx(root: View): Int {
        if (root.height <= 0) return 0
        val screenHeight = root.rootView.height
        if (screenHeight <= 0) return 0

        val location = IntArray(2)
        root.getLocationOnScreen(location)
        val rootBottom = location[1] + root.height

        var keyboardTop = screenHeight
        if (imeBottomInset > 0) {
            keyboardTop = minOf(keyboardTop, screenHeight - imeBottomInset)
        }
        val visible = Rect()
        root.getWindowVisibleDisplayFrame(visible)
        if (visible.bottom in 1 until screenHeight) {
            keyboardTop = minOf(keyboardTop, visible.bottom)
        }
        return (rootBottom - keyboardTop).coerceAtLeast(0)
    }

    private fun scrollSearchResultsToTop() {
        val drawer = _binding ?: return
        if (drawer.search.query.isNullOrBlank()) return
        if (!::linearLayoutManager.isInitialized) return
        linearLayoutManager.scrollToPositionWithOffset(0, 0)
    }

    private fun initSearch() {
        binding.search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                val drawer = _binding ?: return true
                if (!isAdded) return true
                if (query?.startsWith("!") == true)
                    requireContext().openUrl(Constants.URL_DUCK_SEARCH + query.replace(" ", "%20"))
                else if (::adapter.isInitialized && adapter.itemCount == 0)
                    requireContext().openSearch(query?.trim())
                drawer.search.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                val drawer = _binding ?: return true
                if (!::adapter.isInitialized) return true
                try {
                    if (newText.isNotBlank()) {
                        viewModel.reportOnboardingAction(OnboardingAction.SEARCH_APPS)
                        drawer.recyclerView.layoutAnimation = null
                    }
                    adapter.applyQuery(newText)
                    updateFastScroller()
                    drawer.appRename.visibility =
                        if (canRename && newText.isNotBlank()) View.VISIBLE else View.GONE
                    scrollSearchResultsToTop()
                    return true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return false
            }
        })
        binding.searchIcon.setOnClickListener { expandSearch() }
        binding.searchClose.setOnClickListener { collapseSearch() }
        if (prefs.autoShowKeyboard || flag != Constants.FLAG_LAUNCH_APP) {
            expandSearch(showKeyboard = prefs.autoShowKeyboard || flag != Constants.FLAG_LAUNCH_APP)
        } else {
            collapseSearch(clearQuery = false)
        }
    }

    private fun expandSearch(showKeyboard: Boolean = true) {
        val drawer = _binding ?: return
        searchExpanded = true
        drawer.searchIcon.isVisible = false
        drawer.drawerSettings.isVisible = true
        drawer.searchContainer.isVisible = true
        if (showKeyboard) drawer.search.showKeyboard()
    }

    private fun collapseSearch(clearQuery: Boolean = true) {
        val drawer = _binding ?: return
        searchExpanded = false
        if (clearQuery && !drawer.search.query.isNullOrBlank()) {
            drawer.search.setQuery("", false)
        }
        drawer.search.hideKeyboard()
        drawer.searchContainer.isVisible = false
        drawer.searchIcon.isVisible = true
        drawer.drawerSettings.isVisible = true
        drawer.appRename.isVisible = false
        updateFastScroller()
    }

    private fun initAdapter() {
        adapter = AppDrawerAdapter(
            flag,
            prefs.appLabelAlignment,
            prefs.showHomeAppIcons,
            onListChanged = {
                updateFastScroller()
                scrollSearchResultsToTop()
            },
            appClickListener = { appModel ->
                if (!isAdded || appModel.appPackage.isBlank()) return@AppDrawerAdapter
                if (flag == Constants.FLAG_LAUNCH_APP && appModel.appPackage.isNotBlank() &&
                    viewModel.mindfulMorningManager.isLaunchBlocked(appModel.appPackage)
                ) {
                    showMindfulMorningHardBlock()
                    return@AppDrawerAdapter
                }
                if (flag == Constants.FLAG_LAUNCH_APP && appModel is AppModel.App) {
                    if (viewModel.cooldownManager.isInCooldown(appModel.appPackage)) {
                        showCooldownWarningDialog(appModel) {
                            viewModel.selectedApp(appModel, flag)
                            findNavController().popBackStack(R.id.mainFragment, false)
                        }
                        return@AppDrawerAdapter
                    }
                }
                viewModel.selectedApp(appModel, flag)
                if (flag == Constants.FLAG_LAUNCH_APP || flag == Constants.FLAG_HIDDEN_APPS)
                    findNavController().popBackStack(R.id.mainFragment, false)
                else
                    findNavController().popBackStack()
            },
            appInfoListener = {
                if (!isAdded || it.appPackage.isBlank()) return@AppDrawerAdapter
                openAppInfo(
                    requireContext(),
                    it.user,
                    it.appPackage
                )
                findNavController().popBackStack(R.id.mainFragment, false)
            },
            appDeleteListener = { appModel ->
                if (!isAdded || appModel.appPackage.isBlank()) return@AppDrawerAdapter
                when (appModel) {
                    is AppModel.PrivateSpaceHeader -> {}
                    is AppModel.SectionHeader -> {}
                    is AppModel.PinnedShortcut ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                            requireContext().deletePinnedShortcut(
                                packageName = appModel.appPackage,
                                shortcutIdToDelete = appModel.shortcutId,
                                user = appModel.user,
                            )
                        }

                    is AppModel.App -> {
                        if (isPrivateSpaceProfile(requireContext(), appModel.user)) {
                            openAppInfo(requireContext(), appModel.user, appModel.appPackage)
                        } else if (requireContext().isSystemApp(appModel.appPackage, appModel.user)) {
                            requireContext().showToast(getString(R.string.system_app_cannot_delete))
                            openAppInfo(requireContext(), appModel.user, appModel.appPackage)
                        } else {
                            requireContext().uninstall(appModel.appPackage)
                        }
                    }
                }
                viewModel.getAppList()
            },
            appHideListener = { appModel, _ ->
                if (!isAdded || appModel.appPackage.isBlank()) return@AppDrawerAdapter
                if (!PremiumAccess.hasPremiumAccess(prefs)) {
                    (requireActivity() as? MainActivity)?.showUpgradeDialog()
                    return@AppDrawerAdapter
                }
                if (appModel is AppModel.PinnedShortcut) {
                    requireContext().showToast("Hiding pinned shortcuts is not supported")
                    return@AppDrawerAdapter
                }
                val newSet = mutableSetOf<String>()
                newSet.addAll(prefs.hiddenApps)
                if (flag == Constants.FLAG_HIDDEN_APPS) {
                    newSet.remove(appModel.appPackage) // for backward compatibility
                    newSet.remove(appModel.appPackage + "|" + appModel.user.toString())
                } else
                    newSet.add(appModel.appPackage + "|" + appModel.user.toString())

                prefs.hiddenApps = newSet
                if (newSet.isEmpty())
                    findNavController().popBackStack()
                if (prefs.firstHide) {
                    _binding?.search?.hideKeyboard()
                    prefs.firstHide = false
                    val hideTourShowing = viewModel.isOnboardingActive() &&
                        viewModel.currentOnboardingStep().requiredAction == OnboardingAction.OPEN_APP_DRAWER
                    if (!hideTourShowing) {
                        viewModel.showDialog.postValue(Constants.Dialog.HIDDEN)
                    }
                    findNavController().navigate(R.id.action_appListFragment_to_settingsFragment2)
                }
                viewModel.getAppList()
                viewModel.getHiddenApps()
            },
            appRenameListener = { appModel, renameLabel ->
                if (!isAdded) return@AppDrawerAdapter
                if (appModel.appPackage.isBlank() && appModel !is AppModel.PinnedShortcut) {
                    return@AppDrawerAdapter
                }
                if (!PremiumAccess.hasPremiumAccess(prefs)) {
                    (requireActivity() as? MainActivity)?.showUpgradeDialog()
                    return@AppDrawerAdapter
                }
                val identifier = when (appModel) {
                    is AppModel.PinnedShortcut -> appModel.shortcutId
                    is AppModel.App -> appModel.appPackage
                    else -> return@AppDrawerAdapter
                }
                prefs.setAppRenameLabel(identifier, renameLabel)
                viewModel.getAppList()
            },
            privateSpaceToggleListener = {
                if (PremiumAccess.hasPremiumAccess(prefs)) {
                    viewModel.togglePrivateSpaceLock()
                } else {
                    (requireActivity() as? MainActivity)?.showUpgradeDialog()
                }
            },
            privateSpaceSettingsListener = {
                if (PremiumAccess.hasPremiumAccess(prefs)) {
                    viewModel.openPrivateSpaceSettings()
                    findNavController().popBackStack(R.id.mainFragment, false)
                } else {
                    (requireActivity() as? MainActivity)?.showUpgradeDialog()
                }
            },
            appCooldownLimitListener = { appModel ->
                if (PremiumAccess.hasPremiumAccess(prefs) && appModel is AppModel.App) {
                    showCooldownConfigDialog(appModel)
                } else if (!PremiumAccess.hasPremiumAccess(prefs)) {
                    (requireActivity() as? MainActivity)?.showUpgradeDialog()
                }
            }
        )
        adapter.premiumLocked = !PremiumAccess.hasPremiumAccess(prefs)

        linearLayoutManager = object : LinearLayoutManager(requireContext()) {
            override fun scrollVerticallyBy(
                dx: Int,
                recycler: Recycler,
                state: RecyclerView.State,
            ): Int {
                val scrollRange = super.scrollVerticallyBy(dx, recycler, state)
                val overScroll = dx - scrollRange
                val list = _binding?.recyclerView
                if (overScroll < -10 && list?.scrollState == RecyclerView.SCROLL_STATE_DRAGGING)
                    checkMessageAndExit()
                return scrollRange
            }
        }

        binding.recyclerView.layoutManager = linearLayoutManager
        binding.recyclerView.adapter = adapter
        binding.recyclerView.addOnScrollListener(getRecyclerViewOnScrollListener())
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                alignFastScrollerToAllApps()
            }
        })
        binding.recyclerView.itemAnimator = null
        binding.fastScroller.setOnSectionSelectedListener { section ->
            adapter.getPositionForSection(section)?.let { position ->
                linearLayoutManager.scrollToPositionWithOffset(position, 0)
            }
        }
        if (requireContext().isEinkDisplay().not() && !prefs.autoShowKeyboard) {
            binding.recyclerView.layoutAnimation =
                AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_anim_from_bottom)
        }
        updateFastScroller()
    }

    private fun initObservers() {
        viewModel.firstOpen.observe(viewLifecycleOwner) {
        }
        if (flag == Constants.FLAG_HIDDEN_APPS) {
            viewModel.hiddenApps.observe(viewLifecycleOwner) {
                it?.let {
                    adapter.setAppList(it.toMutableList())
                }
            }
        } else {
            viewModel.appList.observe(viewLifecycleOwner) {
                currentAppList = it
                scheduleUpdateCombinedAppList()
            }
            if (flag == Constants.FLAG_LAUNCH_APP) {
                viewModel.privateSpaceAvailable.observe(viewLifecycleOwner) {
                    currentPrivateSpaceAvailable = it
                    scheduleUpdateCombinedAppList()
                }
                viewModel.privateSpaceLocked.observe(viewLifecycleOwner) {
                    currentPrivateSpaceLocked = it
                    scheduleUpdateCombinedAppList()
                }
                viewModel.privateSpaceApps.observe(viewLifecycleOwner) {
                    currentPrivateSpaceApps = it
                    scheduleUpdateCombinedAppList()
                }
            }
        }
    }

    private fun scheduleUpdateCombinedAppList() {
        val root = _binding?.root ?: return
        combineListRunnable?.let { root.removeCallbacks(it) }
        val runnable = Runnable {
            combineListRunnable = null
            updateCombinedAppList()
        }
        combineListRunnable = runnable
        root.post(runnable)
    }

    private fun updateCombinedAppList() {
        if (_binding == null) return
        val apps = currentAppList
            ?.filter { it !is AppModel.SectionHeader }
            ?: return
        val combined = mutableListOf<AppModel>()
        val query = _binding?.search?.query?.toString().orEmpty().trim()

        val uniqueApps = apps.distinctBy {
            when (it) {
                is AppModel.App -> it.appPackage + "\u0000" + it.user.toString()
                is AppModel.PinnedShortcut -> it.shortcutId + "\u0000" + it.user.toString()
                else -> it.appLabel
            }
        }
        combined.addAll(withLetterHeaders(uniqueApps))

        if (flag == Constants.FLAG_LAUNCH_APP && currentPrivateSpaceAvailable) {
            combined.add(AppModel.PrivateSpaceHeader(isLocked = currentPrivateSpaceLocked))
            if (!currentPrivateSpaceLocked) {
                currentPrivateSpaceApps?.let { combined.addAll(it) }
            }
        }

        adapter.updateCooledOff(viewModel.cooldownManager.getCooledOffPackages())
        adapter.updateMasked(maskedPackagesFrom(combined))
        adapter.setAppList(combined, query)
    }

    private fun withLetterHeaders(apps: List<AppModel>): List<AppModel> {
        val out = ArrayList<AppModel>(apps.size)
        var lastLetter: String? = null
        apps.forEach { app ->
            if (app is AppModel.PrivateSpaceHeader) {
                out.add(app)
                return@forEach
            }
            val letter = adapter.sectionLetter(app)
            if (letter != null && letter != lastLetter) {
                out.add(AppModel.SectionHeader(letter, "letter_$letter"))
                lastLetter = letter
            }
            out.add(app)
        }
        return out
    }

    private fun initClickListeners() {
        binding.drawerSettings.setOnClickListener { openSukunSettings() }
        binding.appRename.setOnClickListener {
            val name = binding.search.query.toString().trim()
            if (name.isEmpty()) {
                requireContext().showToast(getString(R.string.type_a_new_app_name_first))
                binding.search.showKeyboard()
                return@setOnClickListener
            }

            when (flag) {
                Constants.FLAG_SET_HOME_APP_1 -> prefs.appName1 = name
                Constants.FLAG_SET_HOME_APP_2 -> prefs.appName2 = name
                Constants.FLAG_SET_HOME_APP_3 -> prefs.appName3 = name
                Constants.FLAG_SET_HOME_APP_4 -> prefs.appName4 = name
                Constants.FLAG_SET_HOME_APP_5 -> prefs.appName5 = name
                Constants.FLAG_SET_HOME_APP_6 -> prefs.appName6 = name
                Constants.FLAG_SET_HOME_APP_7 -> prefs.appName7 = name
                Constants.FLAG_SET_HOME_APP_8 -> prefs.appName8 = name
            }
            findNavController().popBackStack()
        }
    }

    private fun openSukunSettings() {
        if (!isAdded) return
        binding.search.hideKeyboard()
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.appListFragment) return
        navController.navigate(R.id.action_appListFragment_to_settingsFragment2)
        viewModel.reportOnboardingAction(OnboardingAction.OPEN_SETTINGS)
        viewModel.firstOpen(false)
    }

    private fun updateFastScroller() {
        if (!::adapter.isInitialized) return
        val drawer = _binding ?: return
        val shouldShowScroller = flag == Constants.FLAG_LAUNCH_APP &&
                prefs.appDrawerFastScroller &&
                drawer.search.query.isNullOrBlank()
        val sections = adapter.getSections()
        val show = shouldShowScroller && sections.size > 1
        drawer.fastScroller.setSections(if (show) sections else emptyList())
        val endReserve = if (show) 24.dpToPx() else 0
        val list = drawer.recyclerView
        if (list.paddingEnd != endReserve) {
            list.setPaddingRelative(
                list.paddingStart,
                list.paddingTop,
                endReserve,
                list.paddingBottom,
            )
        }
        alignFastScrollerToAllApps()
    }

    private fun alignFastScrollerToAllApps() {
        val drawer = _binding ?: return
        if (!::adapter.isInitialized) return
        val scroller = drawer.fastScroller
        val shouldShow = flag == Constants.FLAG_LAUNCH_APP &&
                prefs.appDrawerFastScroller &&
                drawer.search.query.isNullOrBlank() &&
                adapter.getSections().size > 1
        if (!shouldShow) {
            scroller.isVisible = false
            scroller.translationY = 0f
            return
        }
        if (!::linearLayoutManager.isInitialized) return
        val recycler = drawer.recyclerView
        if (recycler.height == 0) return
        val headerPos = adapter.allAppsHeaderPosition()
        val top = if (headerPos < 0) {
            0
        } else {
            val headerView = linearLayoutManager.findViewByPosition(headerPos)
            val firstVisible = linearLayoutManager.findFirstVisibleItemPosition()
            when {
                headerView != null -> headerView.bottom.coerceAtLeast(0)
                headerPos < firstVisible -> 0
                else -> recycler.height
            }
        }.coerceIn(0, recycler.height)
        val height = recycler.height - top
        val params = scroller.layoutParams
        if (params.height != height) {
            params.height = height
            scroller.layoutParams = params
        }
        if (scroller.translationY != top.toFloat()) {
            scroller.translationY = top.toFloat()
        }
        scroller.isVisible = height > 48.dpToPx()
    }

    private fun getRecyclerViewOnScrollListener(): RecyclerView.OnScrollListener {
        return object : RecyclerView.OnScrollListener() {

            var onTop = false

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                when (newState) {

                    RecyclerView.SCROLL_STATE_DRAGGING -> {
                        onTop = !recyclerView.canScrollVertically(-1)
                        if (onTop)
                            _binding?.search?.hideKeyboard()
                    }

                    RecyclerView.SCROLL_STATE_IDLE -> {
                        val search = _binding?.search ?: return
                        if (!::prefs.isInitialized) return
                        if (!recyclerView.canScrollVertically(1))
                            search.hideKeyboard()
                        else if (!recyclerView.canScrollVertically(-1))
                            if (!onTop && isRemoving.not() && searchExpanded)
                                search.showKeyboard(prefs.autoShowKeyboard)
                    }
                }
            }
        }
    }

    private fun maskedPackagesFrom(apps: List<AppModel>): Set<String> {
        return apps.mapNotNull { model ->
            val packageName = when (model) {
                is AppModel.App -> model.appPackage
                is AppModel.PinnedShortcut -> model.appPackage
                else -> null
            }
            packageName?.takeIf { it.isNotBlank() && viewModel.mindfulMorningManager.isBlocked(it) }
        }.toSet()
    }

    private fun showMindfulMorningHardBlock() {
        val untilMillis = viewModel.mindfulMorningManager.getBlockedUntilTime()
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = untilMillis }
        val timeLabel = sukun.minimalist.app.launcher.com.data.formatReminderTime(
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE),
            prefs.timeFormat24h,
        )
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.mindful_morning)
            .setMessage(getString(R.string.mindful_morning_hard_blocked, timeLabel))
            .setPositiveButton(R.string.mindful_morning_stay_focused, null)
            .show()
    }

    private fun showCooldownWarningDialog(appModel: AppModel.App, onProceed: () -> Unit) {
        val cm = viewModel.cooldownManager
        val openCount = cm.getOpenCount(appModel.appPackage)
        val durationMs = cm.getTotalDurationMs(appModel.appPackage)
        val cooloffEndsAt = cm.getCooloffEndsAt(appModel.appPackage)
        val remaining = ((cooloffEndsAt - System.currentTimeMillis()) / 60_000).coerceAtLeast(1)

        val durationText = formatDuration(durationMs)
        val appName = appModel.appLabel

        val dialogView = layoutInflater.inflate(R.layout.dialog_cooldown_warning, null)
        dialogView.findViewById<TextView>(R.id.cooldownWarningTitle).text =
            getString(R.string.cooldown_warning_title, appName)
        dialogView.findViewById<TextView>(R.id.cooldownWarningStats).text =
            getString(R.string.cooldown_warning_stats, appName, openCount, durationText)
        dialogView.findViewById<TextView>(R.id.cooldownWarningRemaining).text =
            getString(R.string.cooldown_warning_remaining, remaining)
        dialogView.findViewById<TextView>(R.id.cooldownWarningAck).text =
            getString(R.string.cooldown_warning_ack, appName)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))

        dialogView.findViewById<TextView>(R.id.cooldownOpenAnyway).setOnClickListener {
            dialog.dismiss()
            onProceed()
        }
        dialogView.findViewById<TextView>(R.id.cooldownStayFocused).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showCooldownConfigDialog(appModel: AppModel.App) {
        val cm = viewModel.cooldownManager
        val existing = cm.getConfig(appModel.appPackage)

        val dialogView = layoutInflater.inflate(R.layout.dialog_cooldown_config, null)
        val etOpens = dialogView.findViewById<EditText>(R.id.etCooldownOpens)
        val etDuration = dialogView.findViewById<EditText>(R.id.etCooldownDuration)
        val etCooloff = dialogView.findViewById<EditText>(R.id.etCooldownCooloff)

        if (existing != null) {
            if (existing.maxOpens > 0) etOpens.setText(existing.maxOpens.toString())
            if (existing.maxDurationMinutes > 0) etDuration.setText(existing.maxDurationMinutes.toString())
            etCooloff.setText(existing.cooloffMinutes.toString())
        } else {
            etCooloff.setText("30")
        }

        dialogView.findViewById<TextView>(R.id.cooldownConfigTitle).text =
            getString(R.string.cooldown_config_title, appModel.appLabel)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))

        dialogView.findViewById<TextView>(R.id.cooldownConfigSave).setOnClickListener {
            val maxOpens = etOpens.text?.toString()?.trim()?.toIntOrNull() ?: 0
            val maxDurationMins = etDuration.text?.toString()?.trim()?.toIntOrNull() ?: 0
            val cooloffMins = etCooloff.text?.toString()?.trim()?.toIntOrNull() ?: 30

            if (maxOpens == 0 && maxDurationMins == 0) {
                requireContext().showToast(getString(R.string.cooldown_no_limits_set))
                return@setOnClickListener
            }

            cm.setConfig(AppCooldownConfig(
                packageName = appModel.appPackage,
                maxOpens = maxOpens,
                maxDurationMinutes = maxDurationMins,
                cooloffMinutes = cooloffMins.coerceAtLeast(1)
            ))
            adapter.updateCooledOff(cm.getCooledOffPackages())
            adapter.updateMasked(maskedPackagesFrom(adapter.appsList))
            dialog.dismiss()
        }

        dialogView.findViewById<TextView>(R.id.cooldownConfigRemove).setOnClickListener {
            cm.removeConfig(appModel.appPackage)
            adapter.updateCooledOff(cm.getCooledOffPackages())
            adapter.updateMasked(maskedPackagesFrom(adapter.appsList))
            dialog.dismiss()
        }

        dialogView.findViewById<TextView>(R.id.cooldownConfigCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun formatDuration(ms: Long): String {
        val totalMinutes = ms / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }

    private fun checkMessageAndExit() {
        if (!isAdded || _binding == null) return
        findNavController().popBackStack()
        if (flag == Constants.FLAG_LAUNCH_APP)
            viewModel.checkForMessages.call()
    }

    override fun onStart() {
        super.onStart()
        val drawer = _binding ?: return
        if (searchExpanded && ::prefs.isInitialized) drawer.search.showKeyboard(prefs.autoShowKeyboard)
    }

    override fun onResume() {
        super.onResume()
        if (_binding == null || !::prefs.isInitialized) return
        if (::adapter.isInitialized) {
            adapter.premiumLocked = !PremiumAccess.hasPremiumAccess(prefs)
        }
        if (flag == Constants.FLAG_HIDDEN_APPS) {
            viewModel.getHiddenApps()
        } else if (currentAppList.isNullOrEmpty()) {
            viewModel.getAppList()
        } else {
            scheduleUpdateCombinedAppList()
        }
    }

    override fun onStop() {
        _binding?.search?.hideKeyboard()
        super.onStop()
    }

    override fun onDestroyView() {
        combineListRunnable?.let { _binding?.root?.removeCallbacks(it) }
        combineListRunnable = null
        _binding?.search?.setOnQueryTextListener(null)
        _binding?.recyclerView?.removeCallbacks(hideSpotlightRunnable)
        _binding?.recyclerView?.clearOnScrollListeners()
        _binding?.root?.let { (activity as? MainActivity)?.clearOnboardingSpotlightFrom(it) }
        _binding?.root?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(drawerLayoutListener)
        super.onDestroyView()
        _binding = null
    }

    private fun setupDrawerOnboardingSpotlight() {
        viewModel.onboardingActive.observe(viewLifecycleOwner) { syncDrawerOnboardingSpotlight() }
        viewModel.onboardingStepIndex.observe(viewLifecycleOwner) { syncDrawerOnboardingSpotlight() }
    }

    private fun syncDrawerOnboardingSpotlight() {
        if (_binding == null) return
        if (!viewModel.isOnboardingActive()) {
            releaseDrawerTourSearch()
            (activity as? MainActivity)?.clearOnboardingSpotlightFrom(binding.root)
            return
        }
        when (viewModel.currentOnboardingStep().requiredAction) {
            OnboardingAction.SEARCH_APPS -> showSearchSpotlight()
            OnboardingAction.OPEN_APP_DRAWER -> {
                releaseDrawerTourSearch()
                showHideSpotlight()
            }
            else -> {
                releaseDrawerTourSearch()
                (activity as? MainActivity)?.clearOnboardingSpotlightFrom(binding.root)
            }
        }
    }

    private fun showSearchSpotlight() {
        val drawer = _binding ?: return
        if (!drawer.searchContainer.isVisible) {
            expandSearch(showKeyboard = false)
            openedSearchForTour = true
        }
        drawer.searchContainer.post {
            if (_binding == null || !isDrawerTourStep(OnboardingAction.SEARCH_APPS)) return@post
            (activity as? MainActivity)?.showOnboardingSpotlight(drawer.searchContainer)
        }
    }

    private fun showHideSpotlight() {
        hideSpotlightAttempts = 0
        val drawer = _binding ?: return
        drawer.recyclerView.removeCallbacks(hideSpotlightRunnable)
        drawer.recyclerView.post(hideSpotlightRunnable)
    }

    private val hideSpotlightRunnable = Runnable { showHideSpotlightNow() }

    private fun showHideSpotlightNow() {
        val drawer = _binding ?: return
        if (!isDrawerTourStep(OnboardingAction.OPEN_APP_DRAWER)) return
        val row = firstAppRow(drawer.recyclerView)
        if (row == null) {
            if (hideSpotlightAttempts < 20) {
                hideSpotlightAttempts++
                drawer.recyclerView.postDelayed(hideSpotlightRunnable, 100)
            }
            return
        }
        hideSpotlightAttempts = 0
        scrollDrawerRowAboveCard(row)
        (activity as? MainActivity)?.showOnboardingSpotlight(row)
    }

    private fun firstAppRow(recycler: RecyclerView): View? {
        for (i in 0 until recycler.childCount) {
            val row = recycler.getChildAt(i).findViewById<View>(R.id.appRow) ?: continue
            if (row.visibility == View.VISIBLE && row.width > 0 && row.height > 0) return row
        }
        return null
    }

    private fun scrollDrawerRowAboveCard(row: View) {
        val cardHeight = (activity as? MainActivity)?.onboardingCardHeight()?.takeIf { it > 0 } ?: return
        val loc = IntArray(2)
        row.getLocationOnScreen(loc)
        val bottom = loc[1] + row.height
        val allowed = resources.displayMetrics.heightPixels - cardHeight - 12.dpToPx()
        if (bottom > allowed) _binding?.recyclerView?.scrollBy(0, bottom - allowed)
    }

    private fun isDrawerTourStep(action: OnboardingAction): Boolean {
        return viewModel.isOnboardingActive() &&
            viewModel.currentOnboardingStep().requiredAction == action
    }

    private fun releaseDrawerTourSearch() {
        if (!openedSearchForTour) return
        openedSearchForTour = false
        if (_binding != null) collapseSearch()
    }
}
