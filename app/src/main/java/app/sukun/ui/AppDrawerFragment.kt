package app.sukun.ui

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Recycler
import app.sukun.MainViewModel
import app.sukun.R
import app.sukun.data.AppCooldownConfig
import app.sukun.data.AppModel
import app.sukun.data.Constants
import app.sukun.data.Prefs
import app.sukun.databinding.FragmentAppDrawerBinding
import app.sukun.helper.deletePinnedShortcut
import app.sukun.helper.getColorFromAttr
import app.sukun.helper.hideKeyboard
import app.sukun.helper.isEinkDisplay
import app.sukun.helper.isPrivateSpaceProfile
import app.sukun.helper.isSystemApp
import app.sukun.helper.openAppInfo
import app.sukun.helper.openSearch
import app.sukun.helper.openUrl
import app.sukun.helper.showKeyboard
import app.sukun.helper.showToast
import app.sukun.helper.uninstall

class AppDrawerFragment : Fragment() {

    private lateinit var prefs: Prefs
    private lateinit var adapter: AppDrawerAdapter
    private lateinit var linearLayoutManager: LinearLayoutManager

    private var flag = Constants.FLAG_LAUNCH_APP
    private var canRename = false
    private var currentAppList: List<AppModel>? = null
    private var currentRecentPackages: List<String> = emptyList()
    private var currentPrivateSpaceApps: List<AppModel>? = null
    private var currentPrivateSpaceLocked: Boolean = true
    private var currentPrivateSpaceAvailable: Boolean = false

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
        initSearch()
        initAdapter()
        initObservers()
        initClickListeners()
    }

    private fun initViews() {
        binding.appRename.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
        binding.appRename.setShadowLayer(4f, 0f, 2f, requireContext().getColorFromAttr(R.attr.primaryTextShadowColor))
        if (flag == Constants.FLAG_HIDDEN_APPS)
            binding.search.queryHint = getString(R.string.hidden_apps)
        else if (flag in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_CALENDAR_APP)
            binding.search.queryHint = "Please select an app"
        try {
            val searchTextView = binding.search.findViewById<TextView>(R.id.search_src_text)
            if (searchTextView != null) {
                searchTextView.gravity = prefs.appLabelAlignment
                searchTextView.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
                searchTextView.setHintTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
                searchTextView.setShadowLayer(4f, 0f, 2f, requireContext().getColorFromAttr(R.attr.primaryTextShadowColor))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initSearch() {
        binding.search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (query?.startsWith("!") == true)
                    requireContext().openUrl(Constants.URL_DUCK_SEARCH + query.replace(" ", "%20"))
                else if (adapter.itemCount == 0)
                    requireContext().openSearch(query?.trim())
                else
                    adapter.launchFirstInList()
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                try {
                    adapter.filter.filter(newText)
                    updateFastScroller()
                    binding.appRename.visibility =
                        if (canRename && newText.isNotBlank()) View.VISIBLE else View.GONE
                    return true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return false
            }
        })
    }

    private fun initAdapter() {
        adapter = AppDrawerAdapter(
            flag,
            prefs.appLabelAlignment,
            prefs.showHomeAppIcons,
            onListChanged = {
                updateFastScroller()
            },
            appClickListener = { appModel ->
                if (flag == Constants.FLAG_LAUNCH_APP && appModel is AppModel.App
                    && viewModel.cooldownManager.isInCooldown(appModel.appPackage)
                ) {
                    showCooldownWarningDialog(appModel) {
                        viewModel.selectedApp(appModel, flag)
                        findNavController().popBackStack(R.id.mainFragment, false)
                    }
                    return@AppDrawerAdapter
                }
                viewModel.selectedApp(appModel, flag)
                if (flag == Constants.FLAG_LAUNCH_APP || flag == Constants.FLAG_HIDDEN_APPS)
                    findNavController().popBackStack(R.id.mainFragment, false)
                else
                    findNavController().popBackStack()
            },
            appInfoListener = {
                openAppInfo(
                    requireContext(),
                    it.user,
                    it.appPackage
                )
                findNavController().popBackStack(R.id.mainFragment, false)
            },
            appDeleteListener = { appModel ->
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
            appHideListener = { appModel, position ->
                if (appModel is AppModel.PinnedShortcut) {
                    requireContext().showToast("Hiding pinned shortcuts is not supported")
                    return@AppDrawerAdapter
                }
                adapter.appFilteredList.removeAt(position)
                adapter.notifyItemRemoved(position)
                adapter.appsList.remove(appModel)

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
                    binding.search.hideKeyboard()
                    prefs.firstHide = false
                    viewModel.showDialog.postValue(Constants.Dialog.HIDDEN)
                    findNavController().navigate(R.id.action_appListFragment_to_settingsFragment2)
                }
                viewModel.getAppList()
                viewModel.getHiddenApps()
            },
            appRenameListener = { appModel, renameLabel ->
                val identifier = when (appModel) {
                    is AppModel.PinnedShortcut -> appModel.shortcutId
                    is AppModel.App -> appModel.appPackage
                    else -> return@AppDrawerAdapter
                }
                prefs.setAppRenameLabel(identifier, renameLabel)
                viewModel.getAppList()
            },
            privateSpaceToggleListener = {
                viewModel.togglePrivateSpaceLock()
            },
            privateSpaceSettingsListener = {
                viewModel.openPrivateSpaceSettings()
                findNavController().popBackStack(R.id.mainFragment, false)
            },
            appCooldownLimitListener = { appModel ->
                if (appModel is AppModel.App) showCooldownConfigDialog(appModel)
            }
        )

        linearLayoutManager = object : LinearLayoutManager(requireContext()) {
            override fun scrollVerticallyBy(
                dx: Int,
                recycler: Recycler,
                state: RecyclerView.State,
            ): Int {
                val scrollRange = super.scrollVerticallyBy(dx, recycler, state)
                val overScroll = dx - scrollRange
                if (overScroll < -10 && binding.recyclerView.scrollState == RecyclerView.SCROLL_STATE_DRAGGING)
                    checkMessageAndExit()
                return scrollRange
            }
        }

        binding.recyclerView.layoutManager = linearLayoutManager
        binding.recyclerView.adapter = adapter
        binding.recyclerView.addOnScrollListener(getRecyclerViewOnScrollListener())
        binding.recyclerView.itemAnimator = null
        binding.fastScroller.setOnSectionSelectedListener { section ->
            adapter.getPositionForSection(section)?.let { position ->
                linearLayoutManager.scrollToPositionWithOffset(position, 0)
            }
        }
        if (requireContext().isEinkDisplay().not())
            binding.recyclerView.layoutAnimation =
                AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_anim_from_bottom)
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
                updateCombinedAppList()
            }
            viewModel.recentAppPackages.observe(viewLifecycleOwner) {
                currentRecentPackages = it ?: emptyList()
                updateCombinedAppList()
            }
            if (flag == Constants.FLAG_LAUNCH_APP) {
                viewModel.privateSpaceAvailable.observe(viewLifecycleOwner) {
                    currentPrivateSpaceAvailable = it
                    updateCombinedAppList()
                }
                viewModel.privateSpaceLocked.observe(viewLifecycleOwner) {
                    currentPrivateSpaceLocked = it
                    updateCombinedAppList()
                }
                viewModel.privateSpaceApps.observe(viewLifecycleOwner) {
                    currentPrivateSpaceApps = it
                    updateCombinedAppList()
                }
            }
        }
    }

    private fun updateCombinedAppList() {
        val apps = currentAppList ?: return
        val combined = mutableListOf<AppModel>()

        if (flag == Constants.FLAG_LAUNCH_APP) {
            val firstByPackage = apps
                .filterIsInstance<AppModel.App>()
                .groupBy { it.appPackage }
                .mapValues { (_, items) -> items.first() }

            val recentApps = currentRecentPackages.mapNotNull { firstByPackage[it] }
            if (recentApps.isNotEmpty()) {
                combined.add(AppModel.SectionHeader(getString(R.string.recently_used)))
                combined.addAll(recentApps)
            }
            combined.add(AppModel.SectionHeader(getString(R.string.all_apps)))
        }
        combined.addAll(apps)

        if (flag == Constants.FLAG_LAUNCH_APP && currentPrivateSpaceAvailable) {
            combined.add(AppModel.PrivateSpaceHeader(isLocked = currentPrivateSpaceLocked))
            if (!currentPrivateSpaceLocked) {
                currentPrivateSpaceApps?.let { combined.addAll(it) }
            }
        }

        adapter.updateCooledOff(viewModel.cooldownManager.getCooledOffPackages())
        adapter.setAppList(combined)
        adapter.filter.filter(binding.search.query)
    }

    private fun initClickListeners() {
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

    private fun updateFastScroller() {
        val shouldShowScroller = flag == Constants.FLAG_LAUNCH_APP &&
                prefs.appDrawerFastScroller &&
                binding.search.query.isNullOrBlank()
        val sections = adapter.getSections()
        binding.fastScroller.setSections(if (shouldShowScroller) sections else emptyList())
        binding.fastScroller.isVisible = shouldShowScroller && sections.size > 1
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
                            binding.search.hideKeyboard()
                    }

                    RecyclerView.SCROLL_STATE_IDLE -> {
                        if (!recyclerView.canScrollVertically(1))
                            binding.search.hideKeyboard()
                        else if (!recyclerView.canScrollVertically(-1))
                            if (!onTop && isRemoving.not())
                                binding.search.showKeyboard(prefs.autoShowKeyboard)
                    }
                }
            }
        }
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
            dialog.dismiss()
        }

        dialogView.findViewById<TextView>(R.id.cooldownConfigRemove).setOnClickListener {
            cm.removeConfig(appModel.appPackage)
            adapter.updateCooledOff(cm.getCooledOffPackages())
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
        findNavController().popBackStack()
        if (flag == Constants.FLAG_LAUNCH_APP)
            viewModel.checkForMessages.call()
    }

    override fun onStart() {
        super.onStart()
        binding.search.showKeyboard(prefs.autoShowKeyboard)
    }

    override fun onResume() {
        super.onResume()
        if (flag == Constants.FLAG_HIDDEN_APPS) {
            viewModel.getHiddenApps()
        } else {
            viewModel.getAppList()
        }
    }

    override fun onStop() {
        binding.search.hideKeyboard()
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
