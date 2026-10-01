package sukun.minimalist.app.launcher.com.helper

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import sukun.minimalist.app.launcher.com.MainViewModel
import sukun.minimalist.app.launcher.com.R
import sukun.minimalist.app.launcher.com.data.AppModel
import sukun.minimalist.app.launcher.com.data.Constants
import sukun.minimalist.app.launcher.com.data.HomeAppCategories
import sukun.minimalist.app.launcher.com.data.HomeAppCategory
import sukun.minimalist.app.launcher.com.data.Prefs
import sukun.minimalist.app.launcher.com.databinding.AdapterSetupHomeAppBinding
import sukun.minimalist.app.launcher.com.databinding.DialogSetupHomeAppsBinding

object FirstRunHomeAppsSetup {

    private data class Step(
        val category: HomeAppCategory,
        val slot: Int,
        val titleRes: Int,
        val messageRes: Int,
        val canSkip: Boolean,
    )

    fun start(
        activity: AppCompatActivity,
        prefs: Prefs,
        viewModel: MainViewModel,
        onFinished: () -> Unit,
    ) {
        activity.lifecycleScope.launch {
            prefs.homeAppsNum = 4
            val apps = getAppsList(
                context = activity,
                prefs = prefs,
                includeRegularApps = true,
                includeHiddenApps = false,
            ).filterIsInstance<AppModel.App>()
            showStep(
                activity = activity,
                prefs = prefs,
                viewModel = viewModel,
                apps = apps,
                selectedPackages = emptySet(),
                stepIndex = 0,
                onFinished = onFinished,
            )
        }
    }

    private fun steps(): List<Step> = listOf(
        Step(
            HomeAppCategory.SOCIAL,
            slot = 1,
            titleRes = R.string.setup_home_apps_social_title,
            messageRes = R.string.setup_home_apps_social_message,
            canSkip = false,
        ),
        Step(
            HomeAppCategory.FINANCE,
            slot = 2,
            titleRes = R.string.setup_home_apps_finance_title,
            messageRes = R.string.setup_home_apps_finance_message,
            canSkip = false,
        ),
        Step(
            HomeAppCategory.COMMUNICATION,
            slot = 3,
            titleRes = R.string.setup_home_apps_communication_title,
            messageRes = R.string.setup_home_apps_communication_message,
            canSkip = false,
        ),
        Step(
            HomeAppCategory.GAMES,
            slot = 4,
            titleRes = R.string.setup_home_apps_games_title,
            messageRes = R.string.setup_home_apps_games_message,
            canSkip = true,
        ),
    )

    private fun productivityStep(): Step = Step(
        HomeAppCategory.PRODUCTIVITY,
        slot = 4,
        titleRes = R.string.setup_home_apps_productivity_title,
        messageRes = R.string.setup_home_apps_productivity_message,
        canSkip = false,
    )

    private fun showStep(
        activity: AppCompatActivity,
        prefs: Prefs,
        viewModel: MainViewModel,
        apps: List<AppModel.App>,
        selectedPackages: Set<String>,
        stepIndex: Int,
        forceProductivity: Boolean = false,
        onFinished: () -> Unit,
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onFinished()
            return
        }
        val stepList = steps()
        if (!forceProductivity && stepIndex >= stepList.size) {
            onFinished()
            return
        }
        val step = if (forceProductivity) productivityStep() else stepList[stepIndex]
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_setup_home_apps, null)
        val binding = DialogSetupHomeAppsBinding.bind(dialogView)
        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        binding.setupHomeAppsTitle.text = activity.getString(
            R.string.setup_home_apps_step_title,
            activity.getString(step.titleRes),
            step.slot,
            4,
        )
        binding.setupHomeAppsMessage.setText(step.messageRes)

        val categorized = apps.filter {
            it.appPackage !in selectedPackages &&
                HomeAppCategories.matches(activity, it.appPackage, step.category)
        }
        val remaining = apps.filter { it.appPackage !in selectedPackages }
        val usingFallback = categorized.isEmpty()
        val choices = when {
            categorized.isNotEmpty() -> categorized
            remaining.isNotEmpty() -> remaining
            else -> apps
        }
        binding.setupHomeAppsFallback.isVisible = usingFallback && choices.isNotEmpty()
        binding.setupHomeAppsSkip.isVisible = step.canSkip && choices.isNotEmpty()

        if (choices.isEmpty()) {
            dialog.dismiss()
            advance(
                activity, prefs, viewModel, apps, selectedPackages,
                stepIndex, forceProductivity, onFinished,
            )
            return
        }

        binding.setupHomeAppsList.layoutManager = LinearLayoutManager(activity)
        binding.setupHomeAppsList.adapter = SetupHomeAppAdapter(choices) { app ->
            dialog.dismiss()
            viewModel.selectedApp(app, homeAppFlag(step.slot))
            val nextSelected = selectedPackages + app.appPackage
            advance(
                activity, prefs, viewModel, apps, nextSelected,
                stepIndex, forceProductivity, onFinished,
            )
        }

        binding.setupHomeAppsSkip.setOnClickListener {
            dialog.dismiss()
            showStep(
                activity = activity,
                prefs = prefs,
                viewModel = viewModel,
                apps = apps,
                selectedPackages = selectedPackages,
                stepIndex = stepIndex,
                forceProductivity = true,
                onFinished = onFinished,
            )
        }

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun advance(
        activity: AppCompatActivity,
        prefs: Prefs,
        viewModel: MainViewModel,
        apps: List<AppModel.App>,
        selectedPackages: Set<String>,
        stepIndex: Int,
        forceProductivity: Boolean,
        onFinished: () -> Unit,
    ) {
        if (forceProductivity) {
            onFinished()
            return
        }
        showStep(
            activity = activity,
            prefs = prefs,
            viewModel = viewModel,
            apps = apps,
            selectedPackages = selectedPackages,
            stepIndex = stepIndex + 1,
            onFinished = onFinished,
        )
    }

    private fun homeAppFlag(slot: Int): Int = when (slot) {
        1 -> Constants.FLAG_SET_HOME_APP_1
        2 -> Constants.FLAG_SET_HOME_APP_2
        3 -> Constants.FLAG_SET_HOME_APP_3
        else -> Constants.FLAG_SET_HOME_APP_4
    }

    private class SetupHomeAppAdapter(
        private val apps: List<AppModel.App>,
        private val onAppClick: (AppModel.App) -> Unit,
    ) : RecyclerView.Adapter<SetupHomeAppAdapter.Holder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val binding = AdapterSetupHomeAppBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
            return Holder(binding)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(apps[position], onAppClick)
        }

        override fun getItemCount(): Int = apps.size

        class Holder(
            private val binding: AdapterSetupHomeAppBinding,
        ) : RecyclerView.ViewHolder(binding.root) {
            fun bind(app: AppModel.App, onAppClick: (AppModel.App) -> Unit) {
                binding.setupHomeAppName.text = app.appLabel
                binding.setupHomeAppIcon.setImageDrawable(appIcon(binding.root.context, app))
                binding.root.setOnClickListener { onAppClick(app) }
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
