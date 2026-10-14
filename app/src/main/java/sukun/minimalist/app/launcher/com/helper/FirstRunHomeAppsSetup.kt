package sukun.minimalist.app.launcher.com.helper

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sukun.minimalist.app.launcher.com.MainViewModel
import sukun.minimalist.app.launcher.com.R
import sukun.minimalist.app.launcher.com.data.AppModel
import sukun.minimalist.app.launcher.com.data.Constants
import sukun.minimalist.app.launcher.com.data.Prefs
import sukun.minimalist.app.launcher.com.databinding.AdapterSetupHomeAppBinding
import sukun.minimalist.app.launcher.com.databinding.DialogSetupHomeAppsBinding
import java.util.concurrent.TimeUnit

object FirstRunHomeAppsSetup {

    private val USAGE_LOOKBACK_MS = TimeUnit.DAYS.toMillis(14)

    fun start(
        activity: AppCompatActivity,
        prefs: Prefs,
        viewModel: MainViewModel,
        onFinished: () -> Unit,
    ) {
        activity.lifecycleScope.launch {
            val apps = getAppsList(
                context = activity,
                prefs = prefs,
                includeRegularApps = true,
                includeHiddenApps = false,
            ).filterIsInstance<AppModel.App>()
            val ranked = withContext(Dispatchers.IO) { sortByUsage(activity, apps) }
            if (activity.isFinishing || activity.isDestroyed || ranked.isEmpty()) {
                if (!activity.isFinishing && !activity.isDestroyed && !prefs.hasAnyHomeApp()) {
                    prefs.homeAppsNum = 0
                    viewModel.refreshHome(true)
                }
                onFinished()
                return@launch
            }
            showPicker(activity, prefs, viewModel, ranked, onFinished)
        }
    }

    /** Most-used apps first when usage access is granted; otherwise keeps the alphabetical order. */
    private fun sortByUsage(context: Context, apps: List<AppModel.App>): List<AppModel.App> {
        if (!context.appUsagePermissionGranted()) return apps
        val usage = try {
            val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            manager.queryAndAggregateUsageStats(now - USAGE_LOOKBACK_MS, now)
                .mapValues { it.value.totalTimeInForeground }
        } catch (_: Exception) {
            return apps
        }
        return apps.sortedByDescending { usage[it.appPackage] ?: 0L }
    }

    private fun showPicker(
        activity: AppCompatActivity,
        prefs: Prefs,
        viewModel: MainViewModel,
        apps: List<AppModel.App>,
        onFinished: () -> Unit,
    ) {
        val maxApps = prefs.maxHomeAppsAllowed()
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_setup_home_apps, null)
        val binding = DialogSetupHomeAppsBinding.bind(dialogView)
        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        binding.setupHomeAppsMessage.text =
            activity.getString(R.string.setup_home_apps_message, maxApps)

        val selected = mutableListOf<AppModel.App>()

        fun updateContinue() {
            val enabled = selected.isNotEmpty()
            binding.setupHomeAppsContinue.text =
                activity.getString(R.string.setup_home_apps_continue, selected.size, maxApps)
            binding.setupHomeAppsContinue.isEnabled = enabled
            binding.setupHomeAppsContinue.alpha = if (enabled) 1f else 0.4f
        }

        val adapter = SetupHomeAppAdapter(selected) { app ->
            if (selected.remove(app)) return@SetupHomeAppAdapter true
            if (selected.size >= maxApps) {
                activity.showToast(
                    activity.getString(R.string.setup_home_apps_limit, maxApps),
                    Toast.LENGTH_SHORT,
                )
                return@SetupHomeAppAdapter false
            }
            selected.add(app)
            true
        }
        adapter.onSelectionChanged = { updateContinue() }
        adapter.submit(apps)

        binding.setupHomeAppsList.layoutManager = LinearLayoutManager(activity)
        binding.setupHomeAppsList.adapter = adapter

        binding.setupHomeAppsSearch.doAfterTextChanged { text ->
            val query = text?.toString()?.trim().orEmpty()
            adapter.submit(
                if (query.isEmpty()) apps
                else apps.filter { it.appLabel.contains(query, ignoreCase = true) },
            )
        }

        fun saveSelection() {
            binding.setupHomeAppsSearch.hideKeyboard()
            dialog.dismiss()
            if (selected.isEmpty()) {
                if (!prefs.hasAnyHomeApp()) prefs.homeAppsNum = 0
            } else {
                prefs.homeAppsNum = selected.size
                selected.forEachIndexed { index, app ->
                    viewModel.selectedApp(app, homeAppFlag(index + 1))
                }
            }
            viewModel.refreshHome(true)
            onFinished()
        }

        binding.setupHomeAppsContinue.setOnClickListener {
            if (selected.isNotEmpty()) saveSelection()
        }
        binding.setupHomeAppsSkip.setOnClickListener { saveSelection() }

        updateContinue()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun homeAppFlag(slot: Int): Int = when (slot) {
        1 -> Constants.FLAG_SET_HOME_APP_1
        2 -> Constants.FLAG_SET_HOME_APP_2
        3 -> Constants.FLAG_SET_HOME_APP_3
        4 -> Constants.FLAG_SET_HOME_APP_4
        5 -> Constants.FLAG_SET_HOME_APP_5
        6 -> Constants.FLAG_SET_HOME_APP_6
        7 -> Constants.FLAG_SET_HOME_APP_7
        else -> Constants.FLAG_SET_HOME_APP_8
    }

    /** [onToggle] returns false when the tap was rejected (limit reached). */
    private class SetupHomeAppAdapter(
        private val selected: List<AppModel.App>,
        private val onToggle: (AppModel.App) -> Boolean,
    ) : RecyclerView.Adapter<SetupHomeAppAdapter.Holder>() {

        var onSelectionChanged: (() -> Unit)? = null
        private var apps: List<AppModel.App> = emptyList()

        @android.annotation.SuppressLint("NotifyDataSetChanged")
        fun submit(list: List<AppModel.App>) {
            apps = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val binding = AdapterSetupHomeAppBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
            return Holder(binding)
        }

        @android.annotation.SuppressLint("NotifyDataSetChanged")
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val app = apps[position]
            holder.bind(app, selected.indexOf(app)) {
                if (onToggle(app)) {
                    // Slot numbers of other selected apps shift when one is removed.
                    notifyDataSetChanged()
                    onSelectionChanged?.invoke()
                }
            }
        }

        override fun getItemCount(): Int = apps.size

        class Holder(
            private val binding: AdapterSetupHomeAppBinding,
        ) : RecyclerView.ViewHolder(binding.root) {
            fun bind(app: AppModel.App, selectedIndex: Int, onClick: () -> Unit) {
                binding.setupHomeAppName.text = app.appLabel
                binding.setupHomeAppIcon.setImageDrawable(appIcon(binding.root.context, app))
                binding.setupHomeAppOrder.text =
                    if (selectedIndex >= 0) (selectedIndex + 1).toString() else ""
                binding.root.setOnClickListener { onClick() }
            }

            private fun appIcon(context: Context, app: AppModel.App) = try {
                val launcherApps =
                    context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                launcherApps.getActivityList(app.appPackage, app.user)
                    .firstOrNull { it.componentName.className == app.activityClassName }
                    ?.getBadgedIcon(binding.root.resources.displayMetrics.densityDpi)
                    ?: launcherApps.getActivityList(app.appPackage, app.user)
                        .firstOrNull()
                        ?.getBadgedIcon(binding.root.resources.displayMetrics.densityDpi)
            } catch (_: Exception) {
                null
            }
        }
    }
}
