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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
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
import sukun.minimalist.app.launcher.com.helper.isTablet
import sukun.minimalist.app.launcher.com.helper.openUrl
import sukun.minimalist.app.launcher.com.helper.rateApp
import sukun.minimalist.app.launcher.com.helper.resetLauncherViaFakeActivity
import sukun.minimalist.app.launcher.com.helper.setPlainWallpaper
import sukun.minimalist.app.launcher.com.helper.shareApp
import sukun.minimalist.app.launcher.com.helper.showLauncherSelector
import sukun.minimalist.app.launcher.com.helper.showToast
import sukun.minimalist.app.launcher.com.helper.turnOffSukunLauncher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var navController: NavController
    private lateinit var viewModel: MainViewModel
    private lateinit var binding: ActivityMainBinding
    private var timerJob: Job? = null
    private var isResumed = false
    private var profileReceiver: BroadcastReceiver? = null
    private var ambientThemeController: AmbientThemeController? = null

//    override fun onBackPressed() {
//        if (navController.currentDestination?.id != R.id.mainFragment)
//            super.onBackPressed()
//    }

    override fun attachBaseContext(context: Context) {
        val prefs = Prefs(context)
        val config = Configuration(context.resources.configuration)
        config.fontScale = prefs.textSizeScale.coerceIn(0.5f, 2.0f)
        super.attachBaseContext(context.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        if (isEinkDisplay()) prefs.appTheme = AppCompatDelegate.MODE_NIGHT_NO
        val nightMask = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        prefs.migrateLegacyAppTheme(nightMask == Configuration.UI_MODE_NIGHT_YES)
        AppCompatDelegate.setDefaultNightMode(prefs.resolveLaunchNightMode())
        super.onCreate(savedInstanceState)

        // Ensure any previously enabled FakeHomeActivity (used only temporarily to force the
        // home chooser during "set as default") is disabled. This prevents duplicate HOME
        // handlers that can leave the chooser in a broken state (unselectable Sukun entry,
        // unresponsive Remember/Cancel) on subsequent home presses or re-install flows.
        try {
            val fakeComponent = ComponentName(this, FakeHomeActivity::class.java)
            val current = packageManager.getComponentEnabledSetting(fakeComponent)
            if (current != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                packageManager.setComponentEnabledSetting(
                    fakeComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        } catch (_: Exception) {
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        navController = this.findNavController(R.id.nav_host_fragment)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.isOnboardingActive() && viewModel.onboardingGoBack()) {
                    handleOnboardingBackNavigation(viewModel.onboardingStepIndex.value ?: 0)
                    return
                }
                val destinationId = navController.currentDestination?.id
                if (destinationId != R.id.mainFragment) {
                    if (navController.popBackStack()) {
                        // Successfully popped back
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
            viewModel.setDefaultClockApp()
        }

        showFirstRunFlowIfNeeded()

        initClickListeners()
        initOnboardingPanel()
        initObservers(viewModel)
        restoreOnboardingTourUi()
        viewModel.getAppList()
        setupOrientation()

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
        // Query before the check so that a return-from-chooser or home press while on the
        // launcher step can see the fresh default status and advance the tour (and populate home).
        viewModel.isSukunDefault()
        checkOnboardingLauncherStep()
        if (viewModel.isOnboardingActive()) {
            restoreOnboardingTourUi()
        }
        viewModel.syncWallpaperIfPending()
        viewModel.syncAzanIfNeeded()
    }

    override fun onPause() {
        ambientThemeController?.stop()
        clearLauncherBrightnessOverride()
        super.onPause()
    }

    override fun onStop() {
        isResumed = false
        if (!isRecreating && !isChangingConfigurations) {
            backToHomeScreen()
        }
        super.onStop()
    }

    override fun onUserLeaveHint() {
        backToHomeScreen()
        super.onUserLeaveHint()
    }

    override fun onNewIntent(intent: Intent?) {
        val alreadyHome = navController.currentDestination?.id == R.id.mainFragment
        if (viewModel.isOnboardingActive()) {
            // A HOME intent arrived (e.g. after choosing Sukun in the system launcher picker
            // while on the home-apps step, or user pressed home during tour, or after the
            // default role/chooser grant completed). Re-assert the correct screen for the
            // current onboarding step so the tour UI + home content aren't stuck or blank.
            // Also query + check the launcher step here so that "return from chooser" can
            // advance the tour (and the advance will see a freshly populated home).
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
            when {
                isDefaultLauncher() -> turnOffSukunLauncher()
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                    showLauncherSelector(Constants.REQUEST_CODE_LAUNCHER_SELECTOR)
                else -> resetLauncherViaFakeActivity()
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
            // If we just arrived at the launcher step (e.g. via "Next" from home-apps, or back nav,
            // or physical home while on that step), query so the pill visibility and any panel
            // "done" state checks see the latest default status promptly.
            if (OnboardingManager.stepAt(index).requiredAction == OnboardingAction.SET_DEFAULT_LAUNCHER) {
                viewModel.isSukunDefault()
                // Opportunistically advance if default is already true (covers user who set
                // Sukun as default via the physical home button or the home pill while still
                // on the prior step, then tapped Next to reach here). We will show the "done"
                // state briefly (from updateOnboardingPanel above) then move the tour forward
                // without requiring an extra home press/return.
                checkOnboardingLauncherStep()
            }
        }
        // Note: we no longer auto-advance the launcher onboarding step from this observer.
        // Advancing on SET_DEFAULT_LAUNCHER is done only on "return" (onResume / onNewIntent)
        // via checkOnboardingLauncherStep + restore paths. This prevents advancing/navigating
        // while the system chooser/role UI is still in flight, and ensures HomeFragment has a
        // chance to populate (clock, home apps, etc.) before we potentially move to the next
        // tour step (settings). The LD is still observed by HomeFragment for the pill visibility.
        viewModel.isSukunDefault.observe(this) { /* no-op for onboarding advance; see checkOnboardingLauncherStep */ }
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
                OnboardingAction.TAP_SCREEN_TIME,
                OnboardingAction.TAP_LANGUAGE,
                OnboardingAction.TAP_APPEARANCE,
                -> advanceOnboardingDiscoveryStep(step.requiredAction)
                else -> Unit
            }
        }
    }

    private fun requestDefaultLauncherForOnboarding() {
        when {
            isSukunDefault(this) -> {
                viewModel.reportOnboardingAction(OnboardingAction.SET_DEFAULT_LAUNCHER)
                viewModel.refreshHome.postValue(false)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                showLauncherSelector(Constants.REQUEST_CODE_LAUNCHER_SELECTOR)
            else -> resetLauncherViaFakeActivity()
        }
    }

    private fun updateOnboardingPanel(stepIndex: Int) {
        val step = OnboardingManager.stepAt(stepIndex)
        val total = OnboardingManager.steps.size
        val current = stepIndex + 1

        // Minimalist progress + counter
        binding.onboardingProgress?.progress = (current * 100) / total
        binding.onboardingStepCounter?.text = "$current / $total"

        binding.onboardingTitle.setText(step.titleRes)
        binding.onboardingBody.setText(step.bodyRes)
        binding.onboardingActionHint.setText(step.actionHintRes)
        binding.btnOnboardingBack.isEnabled = stepIndex > 0
        binding.btnOnboardingBack.alpha = if (stepIndex > 0) 1f else 0.35f
        binding.btnOnboardingSkip.visibility = if (step.isDone) View.GONE else View.VISIBLE
        val isLauncherStep = step.requiredAction == OnboardingAction.SET_DEFAULT_LAUNCHER
        val isSettingsStep = isSettingsTargetStep(step.requiredAction)
        val isHomeAppsStep = step.requiredAction == OnboardingAction.TAP_HOME_APP_SLOT
        val isDrawerStep = step.requiredAction == OnboardingAction.SEARCH_APPS || step.requiredAction == OnboardingAction.OPEN_APP_DRAWER
        // Keep the explanation body visible for minimalist purpose-driven cards.
        // Only the launcher "done" state temporarily overrides the body text.
        binding.onboardingBody.visibility = View.VISIBLE
        val showPrimary = step.isWelcome || step.isDone || isLauncherStep || isSettingsStep || isHomeAppsStep || isDrawerStep
        binding.btnOnboardingPrimary.visibility = if (showPrimary) View.VISIBLE else View.GONE
        binding.btnOnboardingPrimary.setText(
            when {
                step.isWelcome -> R.string.onboarding_start_tour
                step.isDone -> R.string.onboarding_finish
                isLauncherStep -> R.string.onboarding_launcher_set
                isHomeAppsStep || isDrawerStep || isSettingsStep -> R.string.onboarding_next
                else -> R.string.onboarding_next
            },
        )
        if (isLauncherStep && isSukunDefault(this)) {
            binding.onboardingBody.setText(R.string.onboarding_launcher_done)
            binding.onboardingActionHint.visibility = View.GONE
            binding.btnOnboardingPrimary.visibility = View.GONE
        } else {
            binding.onboardingActionHint.visibility =
                if (showPrimary && !isLauncherStep && !isSettingsStep && !isHomeAppsStep && !isDrawerStep) View.GONE else View.VISIBLE
        }
    }

    private fun isSettingsTargetStep(action: OnboardingAction) = when (action) {
        OnboardingAction.TAP_PRAYER_SETTINGS,
        OnboardingAction.TAP_FOCUS_MODE,
        OnboardingAction.TAP_SCREEN_TIME,
        OnboardingAction.TAP_LANGUAGE,
        OnboardingAction.TAP_APPEARANCE,
        -> true
        else -> false
    }

    private fun advanceOnboardingDiscoveryStep(action: OnboardingAction) {
        // For discovery steps in Settings, just advance the tour without opening
        // heavy sheets, dialogs, permission prompts, or other fragments.
        viewModel.reportOnboardingAction(action)
    }

    private fun settingsStepPrimaryButtonRes(action: OnboardingAction) = R.string.onboarding_next

    private fun restoreOnboardingTourUi() {
        if (!viewModel.isOnboardingActive()) return
        binding.onboardingLayout.visibility = View.VISIBLE
        updateOnboardingPanel(viewModel.onboardingStepIndex.value ?: 0)
        syncOnboardingNavigation(viewModel.onboardingStepIndex.value ?: 0)
        // Always refresh home content when restoring tour UI (e.g. after home press or
        // return from chooser/settings during tour). This ensures clock, home apps slots,
        // weather/prayer etc. are populated even if a prior nav or chooser flow left us
        // on mainFragment with uninitialized HomeFragment state (the "blank" symptom).
        if (navController.currentDestination?.id == R.id.mainFragment) {
            viewModel.refreshHome.postValue(false)
        }
    }

    private fun syncOnboardingNavigation(stepIndex: Int) {
        when (OnboardingManager.stepAt(stepIndex).requiredAction) {
            OnboardingAction.TAP_PRAYER_SETTINGS,
            OnboardingAction.TAP_FOCUS_MODE,
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
        if (viewModel.isOnboardingActive()
            && viewModel.currentOnboardingStep().requiredAction == OnboardingAction.SET_DEFAULT_LAUNCHER
            && isSukunDefault(this)
        ) {
            // Ensure we are on the home screen and have asked HomeFragment to populate
            // (clock, date, home app slots, overlays, etc.). This prevents the "blank
            // wallpaper only" state after returning from the launcher chooser/role grant.
            // A tiny delay before the report gives the current destination a moment to
            // layout; the report will then advance the step (to settings) via the observer.
            navController.popBackStack(R.id.mainFragment, false)
            viewModel.refreshHome.postValue(false)
            lifecycleScope.launch {
                delay(60)
                if (viewModel.isOnboardingActive()
                    && viewModel.currentOnboardingStep().requiredAction == OnboardingAction.SET_DEFAULT_LAUNCHER
                    && isSukunDefault(this@MainActivity)
                ) {
                    viewModel.reportOnboardingAction(OnboardingAction.SET_DEFAULT_LAUNCHER)
                }
            }
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
        binding.messageLayout.visibility = View.GONE
        if (viewModel.isOnboardingActive()) return
        val destinationId = navController.currentDestination?.id ?: return
        if (destinationId == R.id.mainFragment) return
        navController.popBackStack(R.id.mainFragment, false)
    }

    private fun showFirstRunFlowIfNeeded() {
        if (!viewModel.shouldOfferOnboarding()) return
        if (viewModel.isOnboardingActive()) return
        viewModel.startOnboarding()
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
                    recreate()
                }
            }
        }
    }

    private fun setupAmbientThemeController() {
        ambientThemeController?.stop()
        ambientThemeController = null
        if (!prefs.isAmbientLightTheme() || !prefs.isProUser) return

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
                recreate()
            }
        }.also { it.start() }
    }

    override fun onDestroy() {
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

            Constants.REQUEST_CODE_LAUNCHER_SELECTOR -> {
                // Always query after the role/launcher chooser returns. Do NOT open the manage-defaults
                // settings here — the isSukunDefault() check right after RESULT_OK is racy (role may
                // not be reflected yet), and opening settings can leave the tour in a bad state or
                // make the chooser reappear in a broken/interactionless way on the next home press.
                viewModel.isSukunDefault()
                viewModel.refreshHome.postValue(false)

                // If we are still on the launcher onboarding step and the default is now held,
                // reflect the "done" state in the panel immediately (shows the "tour continues
                // when you return" message and hides the primary). We deliberately do NOT call
                // reportOnboardingAction here — the actual advance happens on return (onNewIntent
                // / onResume via checkOnboardingLauncherStep) so that the HomeFragment gets a
                // chance to populate its content (preventing the "blank home after set default").
                if (viewModel.isOnboardingActive()
                    && viewModel.currentOnboardingStep().requiredAction == OnboardingAction.SET_DEFAULT_LAUNCHER
                    && isSukunDefault(this)
                ) {
                    updateOnboardingPanel(viewModel.onboardingStepIndex.value ?: 0)
                }
            }
        }
    }
}
