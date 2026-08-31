package sukun.minimalist.app.launcher.com

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import sukun.minimalist.app.launcher.com.data.Constants
import sukun.minimalist.app.launcher.com.data.OnboardingAction
import sukun.minimalist.app.launcher.com.data.OnboardingManager
import sukun.minimalist.app.launcher.com.data.Prefs
import sukun.minimalist.app.launcher.com.databinding.ActivityMainBinding
import sukun.minimalist.app.launcher.com.helper.AmbientThemeController
import sukun.minimalist.app.launcher.com.helper.FakeHomeActivity
import sukun.minimalist.app.launcher.com.helper.applyLauncherBrightnessForTheme
import sukun.minimalist.app.launcher.com.helper.clearLauncherBrightnessOverride
import sukun.minimalist.app.launcher.com.helper.getColorFromAttr
import sukun.minimalist.app.launcher.com.helper.hasBeenDays
import sukun.minimalist.app.launcher.com.helper.hasBeenHours
import sukun.minimalist.app.launcher.com.helper.hasBeenMinutes
import sukun.minimalist.app.launcher.com.helper.isDarkThemeOn
import sukun.minimalist.app.launcher.com.helper.isDaySince
import sukun.minimalist.app.launcher.com.helper.isDefaultLauncher
import sukun.minimalist.app.launcher.com.helper.isEinkDisplay
import sukun.minimalist.app.launcher.com.helper.isNetworkAvailable
import sukun.minimalist.app.launcher.com.helper.isSukunDefault
import sukun.minimalist.app.launcher.com.helper.LocaleHelper
import sukun.minimalist.app.launcher.com.helper.isTablet
import sukun.minimalist.app.launcher.com.helper.openUrl
import sukun.minimalist.app.launcher.com.helper.rateApp
import sukun.minimalist.app.launcher.com.helper.resetLauncherViaFakeActivity
import sukun.minimalist.app.launcher.com.helper.setPlainWallpaper
import sukun.minimalist.app.launcher.com.helper.shareApp
import sukun.minimalist.app.launcher.com.helper.createHomeRoleRequestIntent
import sukun.minimalist.app.launcher.com.helper.PremiumAccess
import sukun.minimalist.app.launcher.com.helper.PremiumBillingManager
import sukun.minimalist.app.launcher.com.ui.SettingsFragment
import sukun.minimalist.app.launcher.com.helper.showToast
import sukun.minimalist.app.launcher.com.helper.turnOffSukunLauncher
import sukun.minimalist.app.launcher.com.helper.dpToPx
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private val homeRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { _ ->
        handleHomeRoleRequestResult()
    }

    private lateinit var prefs: Prefs
    private lateinit var navController: NavController
    private lateinit var viewModel: MainViewModel
    private lateinit var binding: ActivityMainBinding
    private var timerJob: Job? = null
    private var isResumed = false
    private var profileReceiver: BroadcastReceiver? = null
    private var ambientThemeController: AmbientThemeController? = null
    private var premiumBillingManager: PremiumBillingManager? = null
    private var driveRestoreDialogShowing = false
    private var driveRestorePromptedUpdatedAt = 0L
    private var driveRestoreChoiceMade = false

    override fun attachBaseContext(context: Context) {
        val config = Configuration(context.resources.configuration)
        config.fontScale = Prefs(context).textSizeScale
        applyOverrideConfiguration(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        AppCompatDelegate.setDefaultNightMode(prefs.resolveLaunchNightMode())
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ensureFakeHomeDisabled()

        navController = this.findNavController(R.id.nav_host_fragment)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (navController.currentDestination?.id != R.id.mainFragment) {
                    if (!navController.popBackStack()) {
                    }
                } else {
                    binding.messageLayout.visibility = View.GONE
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        if (prefs.firstOpen) {
            viewModel.firstOpen(true)
            prefs.firstOpen = false
            prefs.firstOpenTime = System.currentTimeMillis()
            showToast(R.string.premium_trial_welcome)
            viewModel.setDefaultClockApp()
        }
        enforcePremiumExpiry()

        sukun.minimalist.app.launcher.com.helper.sync.AnalyticsRollupManager.ensureCurrent(this)
        sukun.minimalist.app.launcher.com.helper.sync.ScreenTimeSnapshotWorker.schedule(this)
        prefs.registerSyncDirtyListener {
            if (prefs.isSignedIn) {
                sukun.minimalist.app.launcher.com.helper.sync.AccountSyncManager
                    .markLocalDirty(applicationContext)
            }
        }

        showFirstRunFlowIfNeeded()

        initClickListeners()
        initOnboardingPanel()
        initObservers(viewModel)
        restoreOnboardingTourUi()
        viewModel.getAppList()
        setupOrientation()
        initPremiumBilling()

        window.addFlags(FLAG_LAYOUT_NO_LIMITS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            profileReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    viewModel.isPrivateSpaceToggling = false
                    viewModel.getPrivateSpaceAppList()
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PROFILE_AVAILABLE)
                addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(profileReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    registerReceiver(profileReceiver, filter)
                }
            } catch (_: SecurityException) {
                profileReceiver = null
            }
        }
        setupAmbientThemeController()
    }

    override fun onStart() {
        super.onStart()
        checkTheme()
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        ambientThemeController?.start()
        applyLauncherBrightnessForTheme()
        viewModel.isPrivateSpaceToggling = false
        viewModel.onReturnedToLauncher()
        if (viewModel.appList.value.isNullOrEmpty()) {
            viewModel.getAppList()
        }
        showFirstRunFlowIfNeeded()
        viewModel.isSukunDefault()
        checkOnboardingLauncherStep()
        if (viewModel.isOnboardingActive()) {
            restoreOnboardingTourUi()
        }
        viewModel.syncWallpaperIfPending()
        viewModel.syncAzanIfNeeded()
        if (prefs.isSignedIn) {
            lifecycleScope.launch(Dispatchers.IO) {
                val outcome = sukun.minimalist.app.launcher.com.helper.sync.AccountSyncManager
                    .evaluateDrivePull(applicationContext, this@MainActivity)
                if (outcome is sukun.minimalist.app.launcher.com.helper.sync.AccountSyncManager.DrivePullOutcome.NeedsConfirmation) {
                    withContext(Dispatchers.Main) {
                        promptDriveRestoreIfNeeded(outcome.remote)
                    }
                } else if (outcome is sukun.minimalist.app.launcher.com.helper.sync.AccountSyncManager.DrivePullOutcome.Applied) {
                    withContext(Dispatchers.Main) {
                        showToast(R.string.backup_drive_restored)
                        safeRecreate()
                    }
                }
            }
        }
    }

    fun promptDriveRestoreIfNeeded(
        remote: sukun.minimalist.app.launcher.com.helper.sync.GoogleDriveBackupHelper.RemoteBackup,
        onKeepLocal: (() -> Unit)? = null,
        onRestored: (() -> Unit)? = null,
    ) {
        if (isFinishing || isDestroyed) return
        if (driveRestoreDialogShowing) return
        if (remote.updatedAt == driveRestorePromptedUpdatedAt) return
        if (remote.updatedAt <= prefs.syncDeclinedRemoteUpdatedAt) return
        driveRestorePromptedUpdatedAt = remote.updatedAt
        driveRestoreDialogShowing = true
        driveRestoreChoiceMade = false
        AlertDialog.Builder(this)
            .setTitle(R.string.backup_drive_restore_title)
            .setMessage(R.string.backup_drive_restore_message)
            .setNegativeButton(R.string.backup_drive_restore_keep) { _, _ ->
                driveRestoreChoiceMade = true
                driveRestoreDialogShowing = false
                if (onKeepLocal != null) {
                    onKeepLocal.invoke()
                } else {
                    lifecycleScope.launch(Dispatchers.IO) {
                        sukun.minimalist.app.launcher.com.helper.sync.AccountSyncManager
                            .keepLocalAndPushToDrive(applicationContext, remote.updatedAt, this@MainActivity)
                    }
                }
            }
            .setPositiveButton(R.string.backup_drive_restore_confirm) { _, _ ->
                driveRestoreChoiceMade = true
                driveRestoreDialogShowing = false
                lifecycleScope.launch(Dispatchers.IO) {
                    val ok = sukun.minimalist.app.launcher.com.helper.sync.AccountSyncManager
                        .applyRemoteBackup(applicationContext, remote)
                    withContext(Dispatchers.Main) {
                        if (ok) {
                            showToast(R.string.backup_drive_restored)
                            onRestored?.invoke()
                            safeRecreate()
                        }
                    }
                }
            }
            .setOnDismissListener {
                driveRestoreDialogShowing = false
                if (!driveRestoreChoiceMade) {
                    sukun.minimalist.app.launcher.com.helper.sync.AccountSyncManager
                        .recordDeclinedRemoteRestore(applicationContext, remote.updatedAt)
                }
                driveRestoreChoiceMade = false
            }
            .show()
    }

    override fun onPause() {
        ambientThemeController?.stop()
        clearLauncherBrightnessOverride()
        super.onPause()
    }

    override fun onStop() {
        isResumed = false
        if (viewModel.isAuthFlowActive) {
            android.util.Log.w(
                sukun.minimalist.app.launcher.com.helper.GoogleAuthHelper.TAG,
                "MainActivity.onStop during Google sign-in recreating=$isRecreating " +
                    "configChange=$isChangingConfigurations finishing=$isFinishing"
            )
        }
        if (!isRecreating && !isChangingConfigurations) {
            backToHomeScreen()
        }
        if (prefs.isSignedIn && !isFinishing && !isRecreating) {
            lifecycleScope.launch(Dispatchers.IO) {
                sukun.minimalist.app.launcher.com.helper.sync.AccountSyncManager.syncNow(
                    applicationContext,
                    activity = this@MainActivity,
                )
            }
        }
        super.onStop()
    }

    override fun onUserLeaveHint() {
        if (viewModel.isAuthFlowActive) {
            android.util.Log.w(
                sukun.minimalist.app.launcher.com.helper.GoogleAuthHelper.TAG,
                "MainActivity.onUserLeaveHint during Google sign-in"
            )
        }
        backToHomeScreen()
        super.onUserLeaveHint()
    }

    override fun onNewIntent(intent: Intent?) {
        val alreadyHome = navController.currentDestination?.id == R.id.mainFragment
        if (viewModel.isOnboardingActive()) {
            restoreOnboardingTourUi()
            viewModel.refreshHome.postValue(false)
            viewModel.isSukunDefault()
            checkOnboardingLauncherStep()
        } else {
            backToHomeScreen()
        }
        if (alreadyHome && isResumed && prefs.homeButtonShowRecents)
            viewModel.showRecentApps.call()
        super.onNewIntent(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppCompatDelegate.setDefaultNightMode(prefs.resolveLaunchNightMode())
    }

    private fun initClickListeners() {
        binding.ivClose.setOnClickListener {
            binding.messageLayout.visibility = View.GONE
        }
    }

    private fun initObservers(viewModel: MainViewModel) {
        viewModel.launcherResetFailed.observe(this) {
            openLauncherChooser(it)
        }
        viewModel.resetLauncherLiveData.observe(this) {
            if (isDefaultLauncher()) {
                turnOffSukunLauncher()
            } else {
                requestHomeRole()
            }
        }
        viewModel.checkForMessages.observe(this) {
            checkForMessages()
        }
        viewModel.showDialog.observe(this) {
            when (it) {
                Constants.Dialog.ABOUT -> {
                    showMessageDialog(R.string.app_name, R.string.welcome_to_sukun_settings, R.string.okay) {
                        binding.messageLayout.visibility = View.GONE
                    }
                }

                Constants.Dialog.WALLPAPER -> {
                    prefs.wallpaperMsgShown = true
                    prefs.userState = Constants.UserState.REVIEW
                    showMessageDialog(R.string.did_you_know, R.string.wallpaper_message, R.string.enable) {
                        prefs.dailyWallpaper = true
                        viewModel.setWallpaperWorker()
                        val message = if (isNetworkAvailable()) {
                            R.string.your_wallpaper_will_update_shortly
                        } else {
                            R.string.wallpaper_will_update_when_online
                        }
                        showToast(getString(message))
                    }
                }

                Constants.Dialog.REVIEW -> {
                    prefs.userState = Constants.UserState.RATE
                    showMessageDialog(R.string.hey, R.string.review_message, R.string.leave_a_review) {
                        prefs.rateClicked = true
                        showToast("😇❤️")
                        rateApp()
                    }
                }

                Constants.Dialog.RATE -> {
                    prefs.userState = Constants.UserState.SHARE
                    showMessageDialog(R.string.app_name, R.string.rate_us_message, R.string.rate_now) {
                        prefs.rateClicked = true
                        showToast("🤩❤️")
                        rateApp()
                    }
                }

                Constants.Dialog.SHARE -> {
                    prefs.shareShownTime = System.currentTimeMillis()
                    showMessageDialog(R.string.hey, R.string.share_message, R.string.share_now) {
                        showToast("😊❤️")
                        shareApp()
                    }
                }

                Constants.Dialog.HIDDEN -> {
                    showMessageDialog(R.string.hidden_apps, R.string.hidden_apps_message, R.string.okay) {
                    }
                }

                Constants.Dialog.KEYBOARD -> {
                    showMessageDialog(R.string.app_name, R.string.keyboard_message, R.string.okay) {
                    }
                }

                Constants.Dialog.DIGITAL_WELLBEING -> {
                    showMessageDialog(R.string.screen_time, R.string.app_usage_message, R.string.permission) {
                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                }

            }
        }
        viewModel.onboardingActive.observe(this) { active ->
            binding.onboardingLayout.visibility = if (active == true) View.VISIBLE else View.GONE
            if (active == true) {
                updateOnboardingPanel(viewModel.onboardingStepIndex.value ?: 0)
            }
        }
        viewModel.onboardingStepIndex.observe(this) { stepIndex ->
            if (!viewModel.isOnboardingActive()) return@observe
            val index = stepIndex ?: 0
            updateOnboardingPanel(index)
            syncOnboardingNavigation(index)
            if (OnboardingManager.stepAt(index).requiredAction == OnboardingAction.SET_DEFAULT_LAUNCHER) {
                viewModel.isSukunDefault()
                checkOnboardingLauncherStep()
            }
        }
        viewModel.isSukunDefault.observe(this) { /* no-op */ }
    }

    private fun initOnboardingPanel() {
        binding.btnOnboardingBack.setOnClickListener {
            if (viewModel.onboardingGoBack()) {
                handleOnboardingBackNavigation(viewModel.onboardingStepIndex.value ?: 0)
            }
        }
        binding.btnOnboardingSkip.setOnClickListener {
            viewModel.skipOnboarding()
        }
        binding.btnOnboardingPrimary.setOnClickListener {
            val step = viewModel.currentOnboardingStep()
            when (step.requiredAction) {
                OnboardingAction.TAP_START ->
                    viewModel.reportOnboardingAction(OnboardingAction.TAP_START)
                OnboardingAction.SET_DEFAULT_LAUNCHER ->
                    requestDefaultLauncherForOnboarding()
                OnboardingAction.TAP_FINISH ->
                    viewModel.reportOnboardingAction(OnboardingAction.TAP_FINISH)
                OnboardingAction.TAP_HOME_APP_SLOT,
                OnboardingAction.SEARCH_APPS,
                OnboardingAction.OPEN_APP_DRAWER ->
                    viewModel.reportOnboardingAction(step.requiredAction)
                OnboardingAction.TAP_PRAYER_SETTINGS,
                OnboardingAction.TAP_FOCUS_MODE,
                OnboardingAction.TAP_MINDFUL_MORNING,
                OnboardingAction.TAP_SCREEN_TIME,
                OnboardingAction.TAP_LANGUAGE,
                OnboardingAction.TAP_APPEARANCE,
                -> advanceOnboardingDiscoveryStep(step.requiredAction)
                else -> Unit
            }
        }
    }

    private fun requestDefaultLauncherForOnboarding() {
        try {
            if (isSukunDefault(this)) {
                viewModel.reportOnboardingAction(OnboardingAction.SET_DEFAULT_LAUNCHER)
                viewModel.refreshHome.postValue(false)
            } else {
                requestHomeRole()
            }
        } catch (e: Exception) {
            android.util.Log.w("Sukun", "Failed to request default launcher", e)
            showToast(R.string.unable_to_open_app)
        }
    }

    private fun requestHomeRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val intent = createHomeRoleRequestIntent()
            if (intent != null) {
                try {
                    homeRoleLauncher.launch(intent)
                    return
                } catch (e: Exception) {
                    android.util.Log.w("Sukun", "Failed to launch home role request", e)
                }
            }
        }
        try {
            resetLauncherViaFakeActivity()
        } catch (e: Exception) {
            android.util.Log.w("Sukun", "Failed to show launcher chooser", e)
            showToast(R.string.unable_to_open_app)
        }
    }

    private fun handleHomeRoleRequestResult() {
        lifecycleScope.launch {
            delay(500)
            viewModel.isSukunDefault()
            viewModel.refreshHome.postValue(false)
            if (viewModel.isOnboardingActive()
                && viewModel.currentOnboardingStep().requiredAction == OnboardingAction.SET_DEFAULT_LAUNCHER
                && isSukunDefault(this@MainActivity)
            ) {
                binding.root.post {
                    updateOnboardingPanel(viewModel.onboardingStepIndex.value ?: 0)
                    checkOnboardingLauncherStep()
                }
            }
        }
    }

    private fun ensureFakeHomeDisabled() {
        try {
            packageManager.setComponentEnabledSetting(
                ComponentName(this, FakeHomeActivity::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        } catch (_: Exception) {
        }
    }

    private fun updateOnboardingPanel(stepIndex: Int) {
        val step = OnboardingManager.stepAt(stepIndex)
        val total = OnboardingManager.steps.size
        val current = stepIndex + 1

        // Step transition animation
        binding.onboardingLayout.alpha = 0f
        binding.onboardingLayout.animate().alpha(1f).setDuration(250).start()

        // Icon
        step.iconRes?.let { binding.onboardingIcon.setImageResource(it) }

        // Progress + counter
        binding.onboardingProgress?.progress = (current * 100) / total
        binding.onboardingStepCounter?.text = "$current / $total"

        // Title and Body
        binding.onboardingTitle.setText(step.titleRes)
        binding.onboardingBody.setText(step.bodyRes)
        binding.onboardingActionHint.setText(step.actionHintRes)
        
        // Dot indicators
        updateOnboardingDots(stepIndex, total)

        binding.btnOnboardingBack.isEnabled = stepIndex > 0
        binding.btnOnboardingBack.alpha = if (stepIndex > 0) 1f else 0.35f
        binding.btnOnboardingSkip.visibility = when {
            step.isDone -> View.GONE
            step.requiredAction == OnboardingAction.SET_DEFAULT_LAUNCHER -> View.GONE
            else -> View.VISIBLE
        }
        
        val isLauncherStep = step.requiredAction == OnboardingAction.SET_DEFAULT_LAUNCHER
        val isSettingsStep = isSettingsTargetStep(step.requiredAction)
        val isHomeAppsStep = step.requiredAction == OnboardingAction.TAP_HOME_APP_SLOT
        val isDrawerStep = step.requiredAction == OnboardingAction.SEARCH_APPS || step.requiredAction == OnboardingAction.OPEN_APP_DRAWER
        
        binding.onboardingBody.visibility = View.VISIBLE
        val showPrimary = step.isWelcome || step.isDone || isLauncherStep || isSettingsStep || isHomeAppsStep || isDrawerStep
        binding.btnOnboardingPrimary.visibility = if (showPrimary) View.VISIBLE else View.GONE
        binding.btnOnboardingPrimary.setText(
            when {
                step.isWelcome -> R.string.onboarding_start_tour
                step.isDone -> R.string.onboarding_finish
                isLauncherStep -> R.string.onboarding_launcher_set
                else -> R.string.onboarding_next
            },
        )
        
        if (isLauncherStep && isSukunDefault(this)) {
            binding.onboardingBody.setText(R.string.onboarding_launcher_done)
            binding.onboardingActionHint.visibility = View.GONE
            // Allow manual "Next" if auto-advance is slow
            binding.btnOnboardingPrimary.visibility = View.VISIBLE
            binding.btnOnboardingPrimary.setText(R.string.onboarding_next)
        } else {
            binding.onboardingActionHint.visibility =
                if (showPrimary && !isLauncherStep && !isSettingsStep && !isHomeAppsStep && !isDrawerStep) View.GONE else View.VISIBLE
        }
    }

    private fun updateOnboardingDots(selectedIndex: Int, total: Int) {
        val container = binding.onboardingDots
        container.removeAllViews()
        val dotSize = 6.dpToPx()
        val margin = 4.dpToPx()
        
        for (i in 0 until total) {
            val dot = View(this)
            val params = LinearLayout.LayoutParams(dotSize, dotSize)
            params.setMargins(margin, 0, margin, 0)
            dot.layoutParams = params
            dot.setBackgroundResource(
                if (i == selectedIndex) R.drawable.bg_onboarding_dot_selected 
                else R.drawable.bg_onboarding_dot_unselected
            )
            container.addView(dot)
        }
    }

    private fun isSettingsTargetStep(action: OnboardingAction) = when (action) {
        OnboardingAction.TAP_PRAYER_SETTINGS,
        OnboardingAction.TAP_FOCUS_MODE,
        OnboardingAction.TAP_MINDFUL_MORNING,
        OnboardingAction.TAP_SCREEN_TIME,
        OnboardingAction.TAP_LANGUAGE,
        OnboardingAction.TAP_APPEARANCE,
        -> true
        else -> false
    }

    private fun advanceOnboardingDiscoveryStep(action: OnboardingAction) {
        viewModel.reportOnboardingAction(action)
    }

    private fun restoreOnboardingTourUi() {
        if (!viewModel.isOnboardingActive()) return
        try {
            binding.onboardingLayout.visibility = View.VISIBLE
            updateOnboardingPanel(viewModel.onboardingStepIndex.value ?: 0)
            syncOnboardingNavigation(viewModel.onboardingStepIndex.value ?: 0)
            if (navController.currentDestination?.id == R.id.mainFragment) {
                viewModel.refreshHome.postValue(false)
            }
        } catch (e: Exception) {
            android.util.Log.w("Sukun", "Failed to restore onboarding UI", e)
        }
    }

    private fun syncOnboardingNavigation(stepIndex: Int) {
        binding.root.post {
            try {
                when (OnboardingManager.stepAt(stepIndex).requiredAction) {
                    OnboardingAction.TAP_PRAYER_SETTINGS,
                    OnboardingAction.TAP_FOCUS_MODE,
                    OnboardingAction.TAP_MINDFUL_MORNING,
                    OnboardingAction.TAP_SCREEN_TIME,
                    OnboardingAction.TAP_LANGUAGE,
                    OnboardingAction.TAP_APPEARANCE,
                    -> openSettingsForOnboarding()
                    OnboardingAction.SEARCH_APPS,
                    OnboardingAction.OPEN_APP_DRAWER -> openAppDrawerForOnboarding()
                    OnboardingAction.TAP_HOME_APP_SLOT,
                    OnboardingAction.SET_DEFAULT_LAUNCHER,
                    OnboardingAction.OPEN_SETTINGS,
                    OnboardingAction.TAP_FINISH -> navController.popBackStack(R.id.mainFragment, false)
                    else -> Unit
                }
            } catch (e: Exception) {
                android.util.Log.w("Sukun", "Onboarding navigation sync failed", e)
            }
        }
    }

    private fun handleOnboardingBackNavigation(stepIndex: Int) {
        val action = OnboardingManager.stepAt(stepIndex).requiredAction
        when (action) {
            OnboardingAction.TAP_START,
            OnboardingAction.TAP_HOME_APP_SLOT,
            OnboardingAction.SET_DEFAULT_LAUNCHER,
            OnboardingAction.OPEN_SETTINGS,
            OnboardingAction.TAP_FINISH -> navController.popBackStack(R.id.mainFragment, false)
            OnboardingAction.SEARCH_APPS,
            OnboardingAction.OPEN_APP_DRAWER -> openAppDrawerForOnboarding()
            OnboardingAction.TAP_PRAYER_SETTINGS,
            OnboardingAction.TAP_FOCUS_MODE,
            OnboardingAction.TAP_MINDFUL_MORNING,
            OnboardingAction.TAP_SCREEN_TIME,
            OnboardingAction.TAP_LANGUAGE,
            OnboardingAction.TAP_APPEARANCE -> openSettingsForOnboarding()
        }
    }

    private fun openAppDrawerForOnboarding() {
        if (navController.currentDestination?.id == R.id.appListFragment) return
        navController.popBackStack(R.id.mainFragment, false)
        if (navController.currentDestination?.id == R.id.mainFragment) {
            navController.navigate(R.id.action_mainFragment_to_appListFragment)
        }
    }

    private fun openSettingsForOnboarding() {
        val destinationId = navController.currentDestination?.id
        if (destinationId == R.id.settingsFragment || destinationId == R.id.languageFragment) return
        navController.popBackStack(R.id.mainFragment, false)
        if (navController.currentDestination?.id == R.id.mainFragment) {
            navController.navigate(R.id.action_mainFragment_to_settingsFragment)
        }
    }

    private fun checkOnboardingLauncherStep() {
        try {
            if (viewModel.isOnboardingActive()
                && viewModel.currentOnboardingStep().requiredAction == OnboardingAction.SET_DEFAULT_LAUNCHER
                && isSukunDefault(this)
            ) {
                navController.popBackStack(R.id.mainFragment, false)
                viewModel.refreshHome.postValue(false)
                viewModel.reportOnboardingAction(OnboardingAction.SET_DEFAULT_LAUNCHER)
            }
        } catch (e: Exception) {
            android.util.Log.w("Sukun", "Onboarding launcher step check failed", e)
        }
    }

    private fun showMessageDialog(title: Int, message: Int, action: Int, clickListener: () -> Unit) {
        binding.tvTitle.text = getString(title)
        binding.tvMessage.text = getString(message)
        binding.tvAction.text = getString(action)
        binding.tvAction.setOnClickListener {
            clickListener()
            binding.messageLayout.visibility = View.GONE
        }
        binding.messageLayout.visibility = View.VISIBLE
    }

    private fun checkForMessages() {
        if (prefs.firstOpenTime == 0L)
            prefs.firstOpenTime = System.currentTimeMillis()

        val calendar = Calendar.getInstance()
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        if (dayOfYear == 1 && dayOfYear != prefs.shownOnDayOfYear) {
            prefs.shownOnDayOfYear = dayOfYear
            showMessageDialog(R.string.hey, R.string.new_year_wish, R.string.cheers) {}
            return
        } else if (dayOfYear == 32 && dayOfYear != prefs.shownOnDayOfYear) {
            prefs.shownOnDayOfYear = dayOfYear
            showMessageDialog(R.string.hey, R.string.new_year_wish_1, R.string.cheers) {}
            return
        }

        when (prefs.userState) {
            Constants.UserState.START -> {
                if (prefs.firstOpenTime.hasBeenMinutes(10))
                    prefs.userState = Constants.UserState.WALLPAPER
            }

            Constants.UserState.WALLPAPER -> {
                if (prefs.wallpaperMsgShown || prefs.dailyWallpaper)
                    prefs.userState = Constants.UserState.REVIEW
                else if (isSukunDefault(this))
                    viewModel.showDialog.postValue(Constants.Dialog.WALLPAPER)
            }

            Constants.UserState.REVIEW -> {
                if (prefs.rateClicked)
                    prefs.userState = Constants.UserState.SHARE
                else if (isSukunDefault(this) && prefs.firstOpenTime.hasBeenHours(1))
                    viewModel.showDialog.postValue(Constants.Dialog.REVIEW)
            }

            Constants.UserState.RATE -> {
                if (prefs.rateClicked)
                    prefs.userState = Constants.UserState.SHARE
                else if (isSukunDefault(this)
                    && prefs.firstOpenTime.isDaySince() >= 7
                    && calendar.get(Calendar.HOUR_OF_DAY) >= 16
                ) viewModel.showDialog.postValue(Constants.Dialog.RATE)
            }

            Constants.UserState.SHARE -> {
                if (isSukunDefault(this) && prefs.firstOpenTime.hasBeenDays(14)
                    && prefs.shareShownTime.isDaySince() >= 70
                    && calendar.get(Calendar.HOUR_OF_DAY) >= 16
                ) viewModel.showDialog.postValue(Constants.Dialog.SHARE)
            }
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun setupOrientation() {
        if (isTablet(this) || Build.VERSION.SDK_INT == Build.VERSION_CODES.O)
            return
        // In Android 8.0, windowIsTranslucent cannot be used with screenOrientation=portrait
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    private fun backToHomeScreen() {
        if (viewModel.isPrivateSpaceToggling) return
        if (viewModel.isAuthFlowActive) {
            android.util.Log.i(
                sukun.minimalist.app.launcher.com.helper.GoogleAuthHelper.TAG,
                "backToHomeScreen skipped: Google sign-in is active dest=${navController.currentDestination?.label}"
            )
            return
        }
        binding.messageLayout.visibility = View.GONE
        if (viewModel.isOnboardingActive()) return
        val destinationId = navController.currentDestination?.id ?: return
        if (destinationId == R.id.mainFragment) return
        navController.popBackStack(R.id.mainFragment, false)
    }

    private fun showFirstRunFlowIfNeeded() {
        if (!prefs.signInPromptShown) {
            showSignInIfNeeded()
            return
        }
        if (!viewModel.shouldOfferOnboarding()) return
        if (viewModel.isOnboardingActive()) return
        viewModel.startOnboarding()
    }

    private fun showSignInIfNeeded() {
        if (navController.currentDestination?.id == R.id.signInFragment) return
        try {
            navController.navigate(R.id.action_mainFragment_to_signInFragment)
        } catch (e: Exception) {
            android.util.Log.w("Sukun", "Failed to open sign-in screen", e)
        }
    }

    private fun setPlainWallpaper() {
        setPlainWallpaper(this, android.R.color.black)
    }

    private fun openLauncherChooser(resetFailed: Boolean) {
        if (resetFailed) {
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            startActivity(intent)
        }
    }

    private var isRecreating = false

    private fun checkTheme() {
        if (isRecreating) return
        timerJob?.cancel()
        timerJob = lifecycleScope.launch {
            delay(500)
            if (!isResumed) return@launch
            
            val isDark = isDarkThemeOn()
            val themeMode = prefs.appTheme
            
            val mismatch = when (themeMode) {
                AppCompatDelegate.MODE_NIGHT_YES -> !isDark
                AppCompatDelegate.MODE_NIGHT_NO -> isDark
                Constants.THEME_MODE_AMBIENT_LIGHT -> false
                else -> false
            }

            if (mismatch) {
                isRecreating = true
                AppCompatDelegate.setDefaultNightMode(themeMode)
            } else {
                // Check if attributes are correctly applied
                val primaryColor = try { getColorFromAttr(R.attr.primaryColor) } catch (_: Exception) { 0 }
                val expected = if (isDark) getColor(R.color.white) else getColor(R.color.black)
                
                if (primaryColor != 0 && primaryColor != expected) {
                    isRecreating = true
                    safeRecreate()
                }
            }
        }
    }

    private fun setupAmbientThemeController() {
        ambientThemeController?.stop()
        ambientThemeController = null
        if (!prefs.isAmbientLightTheme() || !PremiumAccess.hasPremiumAccess(prefs)) return

        ambientThemeController = AmbientThemeController(
            context = this,
            initialDark = prefs.ambientThemeDark,
        ) { dark ->
            val nightMode = AmbientThemeController.nightModeForDark(dark)
            if (prefs.ambientThemeDark == dark &&
                AppCompatDelegate.getDefaultNightMode() == nightMode
            ) {
                return@AmbientThemeController
            }
            prefs.ambientThemeDark = dark
            AppCompatDelegate.setDefaultNightMode(nightMode)
            applyLauncherBrightnessForTheme()
            if (isResumed && !isRecreating) {
                isRecreating = true
                safeRecreate()
            }
        }.also { it.start() }
    }

    fun showUpgradeDialog() {
        if (prefs.isProUser) {
            showToast(R.string.premium_already_active)
            return
        }
        val message = if (PremiumAccess.trialExpired(prefs)) {
            getString(R.string.premium_trial_ended) + "\n\n" + getString(R.string.premium_feature_requires_upgrade)
        } else {
            getString(R.string.premium_feature_requires_upgrade)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.go_premium)
            .setMessage(message)
            .setPositiveButton(R.string.upgrade_to_premium) { _, _ ->
                premiumBillingManager?.launchPremiumPurchase()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Dismiss sheets/dialogs and recreate on the next frame to avoid WindowManager DeadObjectException on MIUI. */
    fun safeRecreate() {
        if (isFinishing || isDestroyed) return
        isRecreating = true
        dismissOverlayDialogs()
        window?.decorView?.post {
            if (!isFinishing && !isDestroyed) {
                recreate()
            }
        } ?: recreate()
    }

    /** Reload UI after backup import without killing the process abruptly. */
    fun restartAfterSettingsImport() {
        if (isFinishing || isDestroyed) return
        dismissOverlayDialogs()
        viewModelStore.clear()
        safeRecreate()
    }

    private fun dismissOverlayDialogs() {
        driveRestoreDialogShowing = false
        supportFragmentManager.fragments.forEach { fragment ->
            dismissDialogFragments(fragment)
        }
    }

    private fun dismissDialogFragments(fragment: Fragment) {
        if (fragment is DialogFragment) {
            fragment.dismissAllowingStateLoss()
        }
        fragment.childFragmentManager.fragments.forEach { dismissDialogFragments(it) }
    }

    private fun enforcePremiumExpiry() {
        if (PremiumAccess.hasPremiumAccess(prefs)) return
        if (prefs.dailyWallpaper) {
            prefs.dailyWallpaper = false
            viewModel.cancelWallpaperWorker()
        }
    }

    private fun initPremiumBilling() {
        premiumBillingManager = PremiumBillingManager(this, prefs).also { manager ->
            manager.onPremiumStatusChanged = {
                setupAmbientThemeController()
                supportFragmentManager.fragments
                    .filterIsInstance<SettingsFragment>()
                    .forEach { it.onPremiumStatusChanged() }
            }
            manager.start()
        }
    }

    override fun onDestroy() {
        premiumBillingManager?.endConnection()
        premiumBillingManager = null
        ambientThemeController?.stop()
        ambientThemeController = null
        profileReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            Constants.REQUEST_CODE_ENABLE_ADMIN -> {
                if (resultCode == Activity.RESULT_OK) {
                    prefs.lockModeOn = true
                    prefs.doubleTapAction = sukun.minimalist.app.launcher.com.data.Constants.DoubleTapAction.LOCK
                }
            }
            sukun.minimalist.app.launcher.com.helper.sync.GoogleDriveAuthHelper.REQUEST_CODE_DRIVE_AUTH -> {
                sukun.minimalist.app.launcher.com.helper.sync.GoogleDriveAuthHelper
                    .handleActivityResult(this, requestCode, resultCode, data)
            }
        }
    }
}
