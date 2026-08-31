package sukun.minimalist.app.launcher.com

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Build
import android.os.SystemClock
import android.os.UserHandle
import android.os.UserManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import sukun.minimalist.app.launcher.com.data.AppCooldownManager
import sukun.minimalist.app.launcher.com.data.MindfulMorningManager
import sukun.minimalist.app.launcher.com.data.AppModel
import sukun.minimalist.app.launcher.com.data.Constants
import sukun.minimalist.app.launcher.com.data.OnboardingAction
import sukun.minimalist.app.launcher.com.data.OnboardingManager
import sukun.minimalist.app.launcher.com.data.Prefs
import sukun.minimalist.app.launcher.com.data.PrayerState
import sukun.minimalist.app.launcher.com.data.WeatherData
import sukun.minimalist.app.launcher.com.helper.PrayerReminderScheduler
import sukun.minimalist.app.launcher.com.helper.SingleLiveEvent
import sukun.minimalist.app.launcher.com.helper.WeatherWorker
import sukun.minimalist.app.launcher.com.helper.WallpaperWorker
import sukun.minimalist.app.launcher.com.helper.downloadAzan
import sukun.minimalist.app.launcher.com.helper.isAzanCached
import sukun.minimalist.app.launcher.com.helper.isBundledAzanSound
import sukun.minimalist.app.launcher.com.helper.scheduleAzanDownloadWhenOnline
import sukun.minimalist.app.launcher.com.helper.formattedTimeSpent
import sukun.minimalist.app.launcher.com.helper.getAppsList
import sukun.minimalist.app.launcher.com.helper.getCachedPrayerState
import sukun.minimalist.app.launcher.com.helper.getCachedWeatherData
import sukun.minimalist.app.launcher.com.helper.getPrivateSpaceApps
import sukun.minimalist.app.launcher.com.helper.getPrivateSpaceUserHandle
import sukun.minimalist.app.launcher.com.helper.hasBeenMinutes
import sukun.minimalist.app.launcher.com.helper.isNetworkAvailable
import sukun.minimalist.app.launcher.com.helper.isSukunDefault
import sukun.minimalist.app.launcher.com.helper.isDarkThemeOn
import sukun.minimalist.app.launcher.com.helper.isPackageInstalled
import sukun.minimalist.app.launcher.com.helper.isPrivateSpaceLocked
import sukun.minimalist.app.launcher.com.helper.isExpired
import sukun.minimalist.app.launcher.com.helper.refreshPrayerState
import sukun.minimalist.app.launcher.com.helper.refreshWeather
import sukun.minimalist.app.launcher.com.helper.setPlainWallpaper
import sukun.minimalist.app.launcher.com.helper.showToast
import sukun.minimalist.app.launcher.com.helper.usageStats.EventLogWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit


class MainViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val APP_LIST_LOAD_MAX_RETRIES = 4
        private const val APP_LIST_LOAD_RETRY_DELAY_MS = 500L
        private const val APP_LIST_BOOT_RETRY_WINDOW_MS = 2 * 60 * 1000L
    }

    private fun shouldRetryEmptyAppList(): Boolean =
        SystemClock.elapsedRealtime() < APP_LIST_BOOT_RETRY_WINDOW_MS

    private var appListLoadGeneration = 0
    private var hiddenAppsLoadGeneration = 0
    private val appContext by lazy { application.applicationContext }
    private val prefs = Prefs(appContext)
    val cooldownManager by lazy { AppCooldownManager(prefs) }
    val mindfulMorningManager by lazy { MindfulMorningManager(appContext, prefs) }

    val firstOpen = MutableLiveData<Boolean>()
    val refreshHome = MutableLiveData<Boolean>()
    val toggleDateTime = MutableLiveData<Unit>()
    val updateSwipeApps = MutableLiveData<Any>()
    val appList = MutableLiveData<List<AppModel>?>()
    val hiddenApps = MutableLiveData<List<AppModel>?>()
    val isSukunDefault = MutableLiveData<Boolean>()
    val launcherResetFailed = MutableLiveData<Boolean>()
    val homeAppAlignment = MutableLiveData<Int>()
    val screenTimeValue = MutableLiveData<String>()
    val weatherData = MutableLiveData<WeatherData?>()
    val prayerData = MutableLiveData<PrayerState?>()
    val recentAppPackages = MutableLiveData<List<String>>(emptyList())

    val privateSpaceApps = MutableLiveData<List<AppModel>?>()
    val privateSpaceLocked = MutableLiveData<Boolean>()
    val privateSpaceAvailable = MutableLiveData<Boolean>()

    // Suppress backToHomeScreen during Private Space lock/unlock auth
    var isPrivateSpaceToggling = false

    // Suppress backToHomeScreen while Google Credential Manager sign-in UI is open
    var isAuthFlowActive = false

    val showDialog = SingleLiveEvent<String>()
    val checkForMessages = SingleLiveEvent<Unit?>()
    val resetLauncherLiveData = SingleLiveEvent<Unit?>()
    val showRecentApps = SingleLiveEvent<Unit?>()

    val onboardingActive = MutableLiveData(false)
    val onboardingStepIndex = MutableLiveData(0)
    val onboardingPerformSettingsAction = SingleLiveEvent<OnboardingAction>()

    init {
        restoreOnboardingTourState()
    }

    fun startOnboarding() {
        onboardingStepIndex.value = 0
        onboardingActive.value = true
        persistOnboardingTourState()
    }

    fun isOnboardingActive(): Boolean = onboardingActive.value == true

    fun currentOnboardingStep() = OnboardingManager.stepAt(onboardingStepIndex.value ?: 0)

    private fun restoreOnboardingTourState() {
        if (!prefs.onboardingTourActive) return
        onboardingStepIndex.value = prefs.onboardingTourStep
        onboardingActive.value = true
    }

    private fun persistOnboardingTourState() {
        val active = onboardingActive.value == true
        prefs.onboardingTourActive = active
        if (active) {
            prefs.onboardingTourStep = onboardingStepIndex.value ?: 0
        }
    }

    fun performCurrentOnboardingSettingsAction() {
        if (onboardingActive.value != true) return
        when (currentOnboardingStep().requiredAction) {
            OnboardingAction.TAP_PRAYER_SETTINGS,
            OnboardingAction.TAP_FOCUS_MODE,
            OnboardingAction.TAP_MINDFUL_MORNING,
            OnboardingAction.TAP_SCREEN_TIME,
            OnboardingAction.TAP_LANGUAGE,
            OnboardingAction.TAP_APPEARANCE,
            -> onboardingPerformSettingsAction.value = currentOnboardingStep().requiredAction
            else -> Unit
        }
    }

    fun reportOnboardingAction(action: OnboardingAction) {
        if (onboardingActive.value != true) return
        val step = currentOnboardingStep()
        if (step.requiredAction != action) return
        val next = (onboardingStepIndex.value ?: 0) + 1
        if (next >= OnboardingManager.steps.size) {
            completeOnboarding()
        } else {
            onboardingStepIndex.value = next
            persistOnboardingTourState()
        }
    }

    fun onboardingGoBack(): Boolean {
        if (onboardingActive.value != true) return false
        val current = onboardingStepIndex.value ?: 0
        if (current <= 0) return false
        onboardingStepIndex.value = current - 1
        persistOnboardingTourState()
        return true
    }

    fun skipOnboarding() {
        if (currentOnboardingStep().requiredAction == OnboardingAction.SET_DEFAULT_LAUNCHER) return
        completeOnboarding()
    }

    fun completeOnboarding() {
        prefs.onboardingComplete = true
        prefs.firstSettingsOpen = false
        prefs.onboardingTourActive = false
        onboardingActive.value = false
    }

    fun shouldOfferOnboarding(): Boolean {
        if (prefs.onboardingComplete) return false
        if (prefs.onboardingTourActive) return false
        return true
    }

    fun selectedApp(appModel: AppModel, flag: Int) {
        if (appModel is AppModel.PrivateSpaceHeader) return
        when (flag) {
            Constants.FLAG_LAUNCH_APP -> {
                when (appModel) {
                    is AppModel.PinnedShortcut -> launchShortcut(appModel)
                    is AppModel.App ->
                        launchApp(appModel.appPackage, appModel.activityClassName, appModel.user)

                    else -> {}
                }
            }

            Constants.FLAG_HIDDEN_APPS -> {
                if (appModel is AppModel.App) {
                    launchApp(appModel.appPackage, appModel.activityClassName, appModel.user)
                }
            }

            Constants.FLAG_SET_HOME_APP_1 -> saveHomeApp(appModel, 1)
            Constants.FLAG_SET_HOME_APP_2 -> saveHomeApp(appModel, 2)
            Constants.FLAG_SET_HOME_APP_3 -> saveHomeApp(appModel, 3)
            Constants.FLAG_SET_HOME_APP_4 -> saveHomeApp(appModel, 4)
            Constants.FLAG_SET_HOME_APP_5 -> saveHomeApp(appModel, 5)
            Constants.FLAG_SET_HOME_APP_6 -> saveHomeApp(appModel, 6)
            Constants.FLAG_SET_HOME_APP_7 -> saveHomeApp(appModel, 7)
            Constants.FLAG_SET_HOME_APP_8 -> saveHomeApp(appModel, 8)

            Constants.FLAG_SET_SWIPE_LEFT_APP -> saveSwipeApp(appModel, isLeft = true)
            Constants.FLAG_SET_SWIPE_RIGHT_APP -> saveSwipeApp(appModel, isLeft = false)
            Constants.FLAG_SET_CLOCK_APP -> saveClockApp(appModel)
            Constants.FLAG_SET_CALENDAR_APP -> saveCalendarApp(appModel)
            Constants.FLAG_SET_SCREEN_TIME_APP -> saveScreenTimeApp(appModel)
        }
    }

    private fun launchShortcut(appModel: AppModel.PinnedShortcut) {
        val launcher = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val query = LauncherApps.ShortcutQuery().apply {
            setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        }
        launcher.getShortcuts(query, appModel.user)?.find { it.id == appModel.shortcutId }
            ?.let { shortcut ->
                launcher.startShortcut(shortcut, null, null)
                prefs.pushRecentApp(appModel.appPackage)
            }
    }

    private fun saveHomeApp(appModel: AppModel, position: Int) {
        when (appModel) {
            is AppModel.PrivateSpaceHeader -> return
            is AppModel.SectionHeader -> return
            is AppModel.App -> {
                when (position) {
                    1 -> {
                        prefs.appName1 = appModel.appLabel
                        prefs.appPackage1 = appModel.appPackage
                        prefs.appUser1 = appModel.user.toString()
                        prefs.appActivityClassName1 = appModel.activityClassName
                        prefs.isShortcut1 = false
                        prefs.shortcutId1 = ""
                    }

                    2 -> {
                        prefs.appName2 = appModel.appLabel
                        prefs.appPackage2 = appModel.appPackage
                        prefs.appUser2 = appModel.user.toString()
                        prefs.appActivityClassName2 = appModel.activityClassName
                        prefs.isShortcut2 = false
                        prefs.shortcutId2 = ""
                    }

                    3 -> {
                        prefs.appName3 = appModel.appLabel
                        prefs.appPackage3 = appModel.appPackage
                        prefs.appUser3 = appModel.user.toString()
                        prefs.appActivityClassName3 = appModel.activityClassName
                        prefs.isShortcut3 = false
                        prefs.shortcutId3 = ""
                    }

                    4 -> {
                        prefs.appName4 = appModel.appLabel
                        prefs.appPackage4 = appModel.appPackage
                        prefs.appUser4 = appModel.user.toString()
                        prefs.appActivityClassName4 = appModel.activityClassName
                        prefs.isShortcut4 = false
                        prefs.shortcutId4 = ""
                    }

                    5 -> {
                        prefs.appName5 = appModel.appLabel
                        prefs.appPackage5 = appModel.appPackage
                        prefs.appUser5 = appModel.user.toString()
                        prefs.appActivityClassName5 = appModel.activityClassName
                        prefs.isShortcut5 = false
                        prefs.shortcutId5 = ""
                    }

                    6 -> {
                        prefs.appName6 = appModel.appLabel
                        prefs.appPackage6 = appModel.appPackage
                        prefs.appUser6 = appModel.user.toString()
                        prefs.appActivityClassName6 = appModel.activityClassName
                        prefs.isShortcut6 = false
                        prefs.shortcutId6 = ""
                    }

                    7 -> {
                        prefs.appName7 = appModel.appLabel
                        prefs.appPackage7 = appModel.appPackage
                        prefs.appUser7 = appModel.user.toString()
                        prefs.appActivityClassName7 = appModel.activityClassName
                        prefs.isShortcut7 = false
                        prefs.shortcutId7 = ""
                    }

                    8 -> {
                        prefs.appName8 = appModel.appLabel
                        prefs.appPackage8 = appModel.appPackage
                        prefs.appUser8 = appModel.user.toString()
                        prefs.appActivityClassName8 = appModel.activityClassName
                        prefs.isShortcut8 = false
                        prefs.shortcutId8 = ""
                    }
                }
            }

            is AppModel.PinnedShortcut -> {
                when (position) {
                    1 -> {
                        prefs.appName1 = appModel.appLabel
                        prefs.appPackage1 = appModel.appPackage
                        prefs.appUser1 = appModel.user.toString()
                        prefs.appActivityClassName1 = null
                        prefs.isShortcut1 = true
                        prefs.shortcutId1 = appModel.shortcutId
                    }

                    2 -> {
                        prefs.appName2 = appModel.appLabel
                        prefs.appPackage2 = appModel.appPackage
                        prefs.appUser2 = appModel.user.toString()
                        prefs.appActivityClassName2 = null
                        prefs.isShortcut2 = true
                        prefs.shortcutId2 = appModel.shortcutId
                    }

                    3 -> {
                        prefs.appName3 = appModel.appLabel
                        prefs.appPackage3 = appModel.appPackage
                        prefs.appUser3 = appModel.user.toString()
                        prefs.appActivityClassName3 = null
                        prefs.isShortcut3 = true
                        prefs.shortcutId3 = appModel.shortcutId
                    }

                    4 -> {
                        prefs.appName4 = appModel.appLabel
                        prefs.appPackage4 = appModel.appPackage
                        prefs.appUser4 = appModel.user.toString()
                        prefs.appActivityClassName4 = null
                        prefs.isShortcut4 = true
                        prefs.shortcutId4 = appModel.shortcutId
                    }

                    5 -> {
                        prefs.appName5 = appModel.appLabel
                        prefs.appPackage5 = appModel.appPackage
                        prefs.appUser5 = appModel.user.toString()
                        prefs.appActivityClassName5 = null
                        prefs.isShortcut5 = true
                        prefs.shortcutId5 = appModel.shortcutId
                    }

                    6 -> {
                        prefs.appName6 = appModel.appLabel
                        prefs.appPackage6 = appModel.appPackage
                        prefs.appUser6 = appModel.user.toString()
                        prefs.appActivityClassName6 = null
                        prefs.isShortcut6 = true
                        prefs.shortcutId6 = appModel.shortcutId
                    }

                    7 -> {
                        prefs.appName7 = appModel.appLabel
                        prefs.appPackage7 = appModel.appPackage
                        prefs.appUser7 = appModel.user.toString()
                        prefs.appActivityClassName7 = null
                        prefs.isShortcut7 = true
                        prefs.shortcutId7 = appModel.shortcutId
                    }

                    8 -> {
                        prefs.appName8 = appModel.appLabel
                        prefs.appPackage8 = appModel.appPackage
                        prefs.appUser8 = appModel.user.toString()
                        prefs.appActivityClassName8 = null
                        prefs.isShortcut8 = true
                        prefs.shortcutId8 = appModel.shortcutId
                    }
                }
            }
        }
        refreshHome(false)
    }

    private fun saveSwipeApp(appModel: AppModel, isLeft: Boolean) {
        when (appModel) {
            is AppModel.PrivateSpaceHeader -> return
            is AppModel.SectionHeader -> return
            is AppModel.App -> {
                if (isLeft) {
                    prefs.appNameSwipeLeft = appModel.appLabel
                    prefs.appPackageSwipeLeft = appModel.appPackage
                    prefs.appUserSwipeLeft = appModel.user.toString()
                    prefs.appActivityClassNameSwipeLeft = appModel.activityClassName
                    prefs.isShortcutSwipeLeft = false
                    prefs.shortcutIdSwipeLeft = ""
                } else {
                    prefs.appNameSwipeRight = appModel.appLabel
                    prefs.appPackageSwipeRight = appModel.appPackage
                    prefs.appUserSwipeRight = appModel.user.toString()
                    prefs.appActivityClassNameSwipeRight = appModel.activityClassName
                    prefs.isShortcutSwipeRight = false
                    prefs.shortcutIdSwipeRight = ""
                }
            }

            is AppModel.PinnedShortcut -> {
                if (isLeft) {
                    prefs.appNameSwipeLeft = appModel.appLabel
                    prefs.appPackageSwipeLeft = appModel.appPackage
                    prefs.appUserSwipeLeft = appModel.user.toString()
                    prefs.appActivityClassNameSwipeLeft = null
                    prefs.isShortcutSwipeLeft = true
                    prefs.shortcutIdSwipeLeft = appModel.shortcutId
                } else {
                    prefs.appNameSwipeRight = appModel.appLabel
                    prefs.appPackageSwipeRight = appModel.appPackage
                    prefs.appUserSwipeRight = appModel.user.toString()
                    prefs.appActivityClassNameSwipeRight = null
                    prefs.isShortcutSwipeRight = true
                    prefs.shortcutIdSwipeRight = appModel.shortcutId
                }
            }
        }
        updateSwipeApps()
    }

    private fun saveClockApp(appModel: AppModel) {
        if (appModel is AppModel.App) {
            prefs.clockAppPackage = appModel.appPackage
            prefs.clockAppUser = appModel.user.toString()
            prefs.clockAppClassName = appModel.activityClassName
        }
    }

    private fun saveCalendarApp(appModel: AppModel) {
        if (appModel is AppModel.App) {
            prefs.calendarAppPackage = appModel.appPackage
            prefs.calendarAppUser = appModel.user.toString()
            prefs.calendarAppClassName = appModel.activityClassName
        }
    }

    private fun saveScreenTimeApp(appModel: AppModel) {
        if (appModel is AppModel.App) {
            prefs.screenTimeAppPackage = appModel.appPackage
            prefs.screenTimeAppUser = appModel.user.toString()
            prefs.screenTimeAppClassName = appModel.activityClassName
        }
    }

    fun firstOpen(value: Boolean) {
        firstOpen.postValue(value)
    }

    fun refreshHome(appCountUpdated: Boolean) {
        refreshHome.value = appCountUpdated
    }

    fun toggleDateTime() {
        toggleDateTime.postValue(Unit)
    }

    private fun updateSwipeApps() {
        updateSwipeApps.postValue(Unit)
    }

    private fun launchApp(packageName: String, activityClassName: String?, userHandle: UserHandle) {
        val launcher = appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val activityInfo = launcher.getActivityList(packageName, userHandle)

        val isActivityValid = activityClassName.isNullOrBlank().not()
                && activityInfo.any { it.componentName.className == activityClassName }

        val component = if (isActivityValid)
            ComponentName(packageName, activityClassName)
        else {
            when (activityInfo.size) {
                0 -> {
                    appContext.showToast(appContext.getString(R.string.app_not_found))
                    return
                }

                1 -> ComponentName(packageName, activityInfo[0].name)
                else -> ComponentName(packageName, activityInfo[activityInfo.size - 1].name)
            }.also { prefs.updateAppActivityClassName(packageName, it.className) }
        }

        try {
            launcher.startMainActivity(component, userHandle, null, null)
            prefs.pushRecentApp(packageName)
            cooldownManager.recordLaunch(packageName)
            prefs.cooldownLastLaunchedPkg = packageName
        } catch (e: SecurityException) {
            try {
                launcher.startMainActivity(component, android.os.Process.myUserHandle(), null, null)
                prefs.pushRecentApp(packageName)
                cooldownManager.recordLaunch(packageName)
                prefs.cooldownLastLaunchedPkg = packageName
            } catch (e: Exception) {
                appContext.showToast(appContext.getString(R.string.unable_to_open_app))
            }
        } catch (e: Exception) {
            appContext.showToast(appContext.getString(R.string.unable_to_open_app))
        }
    }

    fun onReturnedToLauncher() {
        val pkg = prefs.cooldownLastLaunchedPkg
        if (pkg.isNotBlank()) {
            cooldownManager.recordReturnToLauncher(pkg)
            prefs.cooldownLastLaunchedPkg = ""
        }
    }

    fun getAppList(includeHiddenApps: Boolean = false) {
        val generation = ++appListLoadGeneration
        viewModelScope.launch {
            var apps = getAppsList(appContext, prefs, includeRegularApps = true, includeHiddenApps)
            var attempt = 0
            while (apps.isEmpty() && shouldRetryEmptyAppList() && attempt < APP_LIST_LOAD_MAX_RETRIES) {
                delay(APP_LIST_LOAD_RETRY_DELAY_MS * (attempt + 1))
                if (generation != appListLoadGeneration) return@launch
                apps = getAppsList(appContext, prefs, includeRegularApps = true, includeHiddenApps)
                attempt++
            }
            if (generation != appListLoadGeneration) return@launch
            appList.value = apps
            if (!includeHiddenApps) {
                recentAppPackages.value = getRecentAppPackages(apps)
            }
        }
        getPrivateSpaceAppList()
    }

    fun getHiddenApps() {
        val generation = ++hiddenAppsLoadGeneration
        viewModelScope.launch {
            var apps =
                getAppsList(appContext, prefs, includeRegularApps = false, includeHiddenApps = true)
            var attempt = 0
            while (apps.isEmpty() && shouldRetryEmptyAppList() && attempt < APP_LIST_LOAD_MAX_RETRIES) {
                delay(APP_LIST_LOAD_RETRY_DELAY_MS * (attempt + 1))
                if (generation != hiddenAppsLoadGeneration) return@launch
                apps =
                    getAppsList(appContext, prefs, includeRegularApps = false, includeHiddenApps = true)
                attempt++
            }
            if (generation != hiddenAppsLoadGeneration) return@launch
            hiddenApps.value = apps
        }
    }

    fun isSukunDefault() {
        isSukunDefault.value = isSukunDefault(appContext)
    }

    fun setWallpaperWorker(runImmediately: Boolean = true) {
        val uploadWorkRequest = PeriodicWorkRequestBuilder<WallpaperWorker>(24, TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.HOURS)
            .build()
        WorkManager
            .getInstance(appContext)
            .enqueueUniquePeriodicWork(
                Constants.WALLPAPER_WORKER_NAME,
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                uploadWorkRequest
            )
        if (runImmediately) {
            enqueueWallpaperRefresh(force = false)
        }
        if (prefs.wallpaperPendingSync) {
            WallpaperWorker.scheduleSyncWhenOnline(appContext)
        }
    }

    private fun enqueueWallpaperRefresh(force: Boolean) {
        val requestBuilder = OneTimeWorkRequestBuilder<WallpaperWorker>()
        if (force) {
            requestBuilder.setInputData(workDataOf(WallpaperWorker.KEY_FORCE_REFRESH to true))
        }
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            Constants.WALLPAPER_REFRESH_NOW_WORKER_NAME,
            ExistingWorkPolicy.REPLACE,
            requestBuilder.build()
        )
    }

    fun syncWallpaperIfPending() {
        if (prefs.dailyWallpaper && prefs.wallpaperPendingSync && appContext.isNetworkAvailable()) {
            WallpaperWorker.scheduleSyncWhenOnline(appContext)
        }
    }

    fun syncAzanIfNeeded() {
        if (!prefs.showPrayerOnHome || !prefs.azanEnabled) return
        val sound = prefs.azanSound
        if (!isBundledAzanSound(sound) || isAzanCached(appContext, sound)) return
        viewModelScope.launch {
            if (appContext.isNetworkAvailable()) {
                downloadAzan(appContext, sound)
            } else {
                scheduleAzanDownloadWhenOnline(appContext, sound)
            }
        }
    }

    fun refreshWallpaperNow() {
        viewModelScope.launch {
            prefs.dailyWallpaper = true
            enqueueWallpaperRefresh(force = true)
            setWallpaperWorker(runImmediately = false)
        }
    }

    fun cancelWallpaperWorker() {
        WorkManager.getInstance(appContext).cancelUniqueWork(Constants.WALLPAPER_WORKER_NAME)
        WorkManager.getInstance(appContext).cancelUniqueWork(Constants.WALLPAPER_REFRESH_NOW_WORKER_NAME)
        WorkManager.getInstance(appContext).cancelUniqueWork(Constants.WALLPAPER_SYNC_WORKER_NAME)
        prefs.dailyWallpaperUrl = ""
        prefs.lastWallpaperUpdateTime = 0L
        prefs.wallpaperPendingSync = false
        prefs.dailyWallpaper = false
    }

    fun setWeatherWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val workRequest =
            PeriodicWorkRequestBuilder<WeatherWorker>(Constants.WEATHER_REFRESH_HOURS, TimeUnit.HOURS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
        WorkManager
            .getInstance(appContext)
            .enqueueUniquePeriodicWork(
                Constants.WEATHER_WORKER_NAME,
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                workRequest
            )
    }

    fun cancelWeatherWorker(clearCachedWeather: Boolean = false) {
        WorkManager.getInstance(appContext).cancelUniqueWork(Constants.WEATHER_WORKER_NAME)
        if (clearCachedWeather) prefs.clearWeatherCache()
        weatherData.value = null
    }

    fun loadWeather(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (!prefs.showWeatherOnHome) {
                weatherData.value = null
                return@launch
            }
            if (prefs.weatherSourceMode == Constants.WeatherSource.GOOGLE) {
                weatherData.value = null
                return@launch
            }
            weatherData.value = getCachedWeatherData(prefs)
            val shouldRefresh = forceRefresh ||
                    weatherData.value == null ||
                    prefs.weatherLastUpdated.hasBeenMinutes(Constants.WEATHER_STALE_MINUTES)
            if (shouldRefresh) {
                refreshWeatherData()
            }
        }
    }

    fun refreshWeatherData() {
        viewModelScope.launch {
            val refreshedWeather = refreshWeather(appContext, prefs)
            weatherData.value = refreshedWeather ?: getCachedWeatherData(prefs)
        }
    }

    fun loadPrayerState(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            prayerData.value = getCachedPrayerState(prefs)
            if (!prefs.showPrayerOnHome) {
                prayerData.value = null
                PrayerReminderScheduler.cancelReminder(appContext)
                return@launch
            }

            val cachedPrayer = prayerData.value
            val shouldRefresh = forceRefresh ||
                    cachedPrayer == null ||
                    cachedPrayer.isExpired() ||
                    prefs.prayerLastUpdated.hasBeenMinutes(Constants.Prayer.DISPLAY_STALE_MINUTES)

            if (shouldRefresh) {
                refreshPrayerData(forceLocationRefresh = prefs.prayerSourceMode == Constants.PrayerSource.DEVICE)
            } else {
                PrayerReminderScheduler.scheduleNextReminder(appContext, prefs)
            }
        }
    }

    fun refreshPrayerData(forceLocationRefresh: Boolean = false) {
        viewModelScope.launch {
            val refreshedPrayer = refreshPrayerState(appContext, prefs, forceLocationRefresh)
            prayerData.value = refreshedPrayer ?: getCachedPrayerState(prefs)
            if (prefs.showPrayerOnHome && prayerData.value != null) {
                PrayerReminderScheduler.scheduleNextReminder(appContext, prefs)
            } else {
                PrayerReminderScheduler.cancelReminder(appContext)
            }
        }
    }

    fun cancelPrayerReminder(clearCachedPrayer: Boolean = false) {
        PrayerReminderScheduler.cancelReminder(appContext)
        if (clearCachedPrayer) prefs.clearPrayerCache()
        prayerData.value = null
    }

    fun updateHomeAlignment(gravity: Int) {
        prefs.homeAlignment = gravity
        homeAppAlignment.value = prefs.homeAlignment
    }

    fun getTodaysScreenTime() {
        // Skip throttle when there is no cached value yet (e.g. fresh ViewModel after process death),
        // so the view is never left visible-but-empty.
        val hasValue = screenTimeValue.value != null
        if (hasValue && prefs.screenTimeLastUpdated.hasBeenMinutes(1).not()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val eventLogWrapper = EventLogWrapper(appContext)
                // Start of today in millis
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startTime = calendar.timeInMillis
                val endTime = System.currentTimeMillis()

                val timeSpent = eventLogWrapper.aggregateSimpleUsageStats(
                    eventLogWrapper.aggregateForegroundStats(
                        eventLogWrapper.getForegroundStatsByTimestamps(startTime, endTime)
                    )
                )
                val viewTimeSpent = appContext.formattedTimeSpent(timeSpent)
                screenTimeValue.postValue(viewTimeSpent)
                prefs.screenTimeLastUpdated = endTime
            } catch (_: Exception) {
                screenTimeValue.postValue(appContext.formattedTimeSpent(0L))
            }
        }
    }

    fun getPrivateSpaceAppList() {
        viewModelScope.launch {
            val handle = getPrivateSpaceUserHandle(appContext)
            privateSpaceAvailable.value = handle != null
            if (handle != null) {
                privateSpaceLocked.value = isPrivateSpaceLocked(appContext, handle)
                privateSpaceApps.value = getPrivateSpaceApps(appContext, prefs)
            } else {
                privateSpaceLocked.value = true
                privateSpaceApps.value = emptyList()
            }
        }
    }

    fun openPrivateSpaceSettings() {
        try {
            val intent = Intent("android.settings.PRIVATE_SPACE_SETTINGS")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(intent)
            } catch (_: Exception) {
                appContext.showToast(appContext.getString(R.string.unable_to_open_app))
            }
        }
    }

    fun togglePrivateSpaceLock() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val handle = getPrivateSpaceUserHandle(appContext) ?: return
        try {
            isPrivateSpaceToggling = true
            val userManager = appContext.getSystemService(Context.USER_SERVICE) as UserManager
            val currentlyLocked = userManager.isQuietModeEnabled(handle)
            userManager.requestQuietModeEnabled(!currentlyLocked, handle)
        } catch (e: Exception) {
            isPrivateSpaceToggling = false
            e.printStackTrace()
        }
    }

    fun setDefaultClockApp() {
        viewModelScope.launch {
            try {
                Constants.CLOCK_APP_PACKAGES.firstOrNull { appContext.isPackageInstalled(it) }?.let { packageName ->
                    appContext.packageManager.getLaunchIntentForPackage(packageName)?.component?.className?.let {
                        prefs.clockAppPackage = packageName
                        prefs.clockAppClassName = it
                        prefs.clockAppUser = android.os.Process.myUserHandle().toString()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getRecentAppPackages(apps: List<AppModel>): List<String> {
        return try {
            val appPackages = apps
                .asSequence()
                .filterIsInstance<AppModel.App>()
                .map { it.appPackage }
                .filter { it.isNotBlank() }
                .toSet()
            if (appPackages.isEmpty()) return emptyList()

            val trackedRecent = prefs.recentApps
                .filter { it.isNotBlank() && appPackages.contains(it) && it != appContext.packageName }
                .distinct()
                .take(6)
            if (trackedRecent.isNotEmpty()) return trackedRecent

            val end = System.currentTimeMillis()
            val start = end - TimeUnit.DAYS.toMillis(7)
            val foregroundStats = EventLogWrapper(appContext).getForegroundStatsByTimestamps(start, end)
            if (foregroundStats.isEmpty()) return emptyList()

            foregroundStats
                .asSequence()
                .groupBy { it.packageName }
                .mapValues { (_, stats) -> stats.maxOfOrNull { it.endTime } ?: 0L }
                .filter { (pkg, timestamp) ->
                    pkg.isNotBlank() &&
                            pkg != appContext.packageName &&
                            timestamp > 0L &&
                            appPackages.contains(pkg)
                }
                .toList()
                .sortedByDescending { it.second }
                .map { it.first }
                .take(6)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
