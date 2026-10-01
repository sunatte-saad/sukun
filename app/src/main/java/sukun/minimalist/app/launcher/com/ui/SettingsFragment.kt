package sukun.minimalist.app.launcher.com.ui

import android.Manifest
import android.app.TimePickerDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import sukun.minimalist.app.launcher.com.helper.applyLauncherStatusBarVisibility
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import sukun.minimalist.app.launcher.com.MainActivity
import sukun.minimalist.app.launcher.com.MainViewModel
import sukun.minimalist.app.launcher.com.data.OnboardingAction
import sukun.minimalist.app.launcher.com.R
import sukun.minimalist.app.launcher.com.data.Constants
import sukun.minimalist.app.launcher.com.data.Prefs
import sukun.minimalist.app.launcher.com.data.formatReminderTime
import sukun.minimalist.app.launcher.com.databinding.FragmentSettingsBinding
import sukun.minimalist.app.launcher.com.helper.HourlyChimeScheduler
import sukun.minimalist.app.launcher.com.helper.HourlyChimeEffects
import sukun.minimalist.app.launcher.com.helper.LocaleHelper
import sukun.minimalist.app.launcher.com.helper.PrayerReminderScheduler
import sukun.minimalist.app.launcher.com.helper.ReminderScheduler
import sukun.minimalist.app.launcher.com.helper.animateAlpha
import sukun.minimalist.app.launcher.com.helper.appUsagePermissionGranted
import sukun.minimalist.app.launcher.com.helper.sync.AccountSyncManager
import sukun.minimalist.app.launcher.com.helper.GoogleAuthHelper
import sukun.minimalist.app.launcher.com.helper.getFocusModeStatus
import sukun.minimalist.app.launcher.com.helper.AmbientThemeController
import sukun.minimalist.app.launcher.com.helper.getColorFromAttr
import sukun.minimalist.app.launcher.com.helper.hasCameraPermission
import sukun.minimalist.app.launcher.com.helper.hasWeatherLocationPermission
import sukun.minimalist.app.launcher.com.helper.applyDeviceLocationDeniedFallbacks
import sukun.minimalist.app.launcher.com.helper.isLocationServicesEnabled
import sukun.minimalist.app.launcher.com.helper.missingSukunSetupPermissions
import sukun.minimalist.app.launcher.com.helper.showLocationServicesDisabledDialog
import sukun.minimalist.app.launcher.com.helper.hideKeyboard
import sukun.minimalist.app.launcher.com.helper.getCurrentDeviceLocationLabel
import sukun.minimalist.app.launcher.com.helper.getLocationSuggestions
import android.widget.ArrayAdapter
import android.widget.Filter
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sukun.minimalist.app.launcher.com.helper.PremiumAccess
import sukun.minimalist.app.launcher.com.helper.isAccessServiceEnabled
import sukun.minimalist.app.launcher.com.helper.showAccessibilityDisclosure
import sukun.minimalist.app.launcher.com.helper.isDarkThemeOn
import sukun.minimalist.app.launcher.com.helper.isEinkDisplay
import sukun.minimalist.app.launcher.com.helper.isNetworkAvailable
import sukun.minimalist.app.launcher.com.helper.isSukunDefault
import sukun.minimalist.app.launcher.com.helper.isTablet
import sukun.minimalist.app.launcher.com.helper.openUrl
import sukun.minimalist.app.launcher.com.helper.setPlainWallpaper
import sukun.minimalist.app.launcher.com.helper.showKeyboard
import sukun.minimalist.app.launcher.com.helper.showToast
import sukun.minimalist.app.launcher.com.helper.dpToPx
import sukun.minimalist.app.launcher.com.listener.DeviceAdmin
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

class SettingsFragment : Fragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceManager: DevicePolicyManager
    private lateinit var componentName: ComponentName

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private var pendingScreenTimePermissionRequest = false
    private var pendingFocusModeDuration: Long? = null
    // private var pendingDoubleTapLock = false
    private var onboardingHighlightView: View? = null
    private var onboardingHighlightOriginalBackground: android.graphics.drawable.Drawable? = null
    private var scrollLayoutBaseBottomPadding = 0
    private var sectionsController: SettingsSectionsController? = null

    private val googleAuthHelper by lazy { GoogleAuthHelper(requireContext().applicationContext) }

    private val locationSuggestions = mutableListOf<String>()
    private val locationAdapter by lazy {
        object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_dropdown_item_1line, locationSuggestions) {
            override fun getFilter() = object : Filter() {
                override fun performFiltering(constraint: CharSequence?) = FilterResults().apply {
                    values = locationSuggestions
                    count = locationSuggestions.size
                }
                override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                    notifyDataSetChanged()
                }
            }
        }
    }
    private var locationSuggestionJob: Job? = null
    private var cachedDeviceLocationLabel: String? = null
    private var pendingLocationAction: (() -> Unit)? = null

    private val customChimePickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            }
            prefs.hourlyChimeSound = Constants.ChimeSound.CUSTOM
            prefs.hourlyChimeCustomUri = uri.toString()
            populateHourlyChime()
            previewChimeSound(Constants.ChimeSound.CUSTOM)
            requireContext().showToast(R.string.chime_sound_saved)
        }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result[Manifest.permission.CAMERA] == true
                || requireContext().hasCameraPermission()
            if (granted) {
                prefs.hourlyChimeStyle = Constants.ChimeStyle.FLASH
                binding.chimeStyleSelectLayout?.visibility = View.GONE
                populateHourlyChime()
                previewChimeStyle(Constants.ChimeStyle.FLASH)
            } else {
                requireContext().showToast(R.string.chime_flash_camera_permission, Toast.LENGTH_LONG)
            }
        }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
                    || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            val action = pendingLocationAction
            pendingLocationAction = null
            if (granted) {
                action?.invoke() ?: setAppLocationDevice()
            } else {
                requireContext().applyDeviceLocationDeniedFallbacks(prefs)
                populateWeatherSettings()
                populatePrayerSettings()
                populateLocationSettings()
                viewModel.cancelWeatherWorker(clearCachedWeather = true)
                viewModel.loadWeather()
                viewModel.cancelPrayerReminder(clearCachedPrayer = true)
                viewModel.loadPrayerState()
                viewModel.refreshHome(false)
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    private val backupExportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@registerForActivityResult
            val ok = sukun.minimalist.app.launcher.com.helper.BackupHelper
                .exportToUri(requireContext(), uri)
            if (!ok) {
                requireContext().showToast(R.string.backup_export_failed)
                return@registerForActivityResult
            }
            if (prefs.isSignedIn) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val synced = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        AccountSyncManager.pushManualBackupToDrive(requireContext(), requireActivity())
                    }
                    requireContext().showToast(
                        if (synced) R.string.backup_exported_and_synced
                        else R.string.backup_exported_sync_failed
                    )
                }
            } else {
                requireContext().showToast(R.string.backup_exported)
            }
        }

    private val backupImportLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            if (!sukun.minimalist.app.launcher.com.helper.BackupHelper
                    .isValidBackup(requireContext(), uri)) {
                requireContext().showToast(R.string.backup_import_invalid)
                return@registerForActivityResult
            }
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.backup_import_confirm_title)
                .setMessage(R.string.backup_import_confirm_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.restore) { _, _ -> performImport(uri) }
                .show()
        }

    private fun performImport(uri: Uri) {
        val ok = sukun.minimalist.app.launcher.com.helper.BackupHelper
            .importFromUri(requireContext(), uri)
        if (!ok) {
            requireContext().showToast(R.string.backup_import_failed)
            return
        }
        sukun.minimalist.app.launcher.com.helper.ReminderScheduler.scheduleAll(requireContext())
        if (prefs.isSignedIn) {
            viewLifecycleOwner.lifecycleScope.launch {
                val synced = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    AccountSyncManager.pushManualBackupToDrive(requireContext(), requireActivity())
                }
                requireContext().showToast(
                    if (synced) R.string.backup_imported_and_synced else R.string.backup_imported,
                )
                (requireActivity() as? MainActivity)?.restartAfterSettingsImport()
            }
        } else {
            requireContext().showToast(R.string.backup_imported)
            (requireActivity() as? MainActivity)?.restartAfterSettingsImport()
        }
    }

    private fun launchBackupExport() {
        val ts = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
            .format(java.util.Date())
        backupExportLauncher.launch("${getString(R.string.backup_file_prefix)}-$ts.json")
    }

    private fun launchBackupImport() {
        backupImportLauncher.launch(arrayOf("application/json", "*/*"))
    }

    private fun showPremiumInfoDialog() {
        val message = buildString {
            append(getString(R.string.premium_trial_welcome))
            append("\n\n")
            when {
                PremiumAccess.isTrialActive(prefs) -> append(
                    getString(
                        R.string.premium_trial_days_left,
                        PremiumAccess.trialDaysRemaining(prefs),
                    ),
                )
                PremiumAccess.trialExpired(prefs) -> append(getString(R.string.premium_trial_ended))
            }
            append("\n\n")
            append(getString(R.string.premium_mindful_morning_hard_feature))
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.premium_info_title)
            .setMessage(message)
            .setPositiveButton(R.string.okay, null)
            .show()
    }

    private fun showBackupInfoDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.backup_info_title)
            .setMessage(R.string.backup_info_message)
            .setPositiveButton(R.string.okay, null)
            .show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            prefs = Prefs(requireContext())
            if (prefs.isFocusModeActive()) {
                requireContext().showToast(R.string.focus_mode_blocked)
                findNavController().popBackStack(R.id.mainFragment, false)
                return
            }
            viewModel = activity?.run {
                ViewModelProvider(this)[MainViewModel::class.java]
            } ?: throw Exception("Invalid Activity")
            viewModel.isSukunDefault()

            deviceManager = requireContext().getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            componentName = ComponentName(requireContext(), DeviceAdmin::class.java)
            checkAdminPermission()

            // Migrate: users who had azan disabled before the 4-way selector existed
            if (!prefs.azanEnabled && prefs.azanSound != Constants.AzanSound.OFF) {
                prefs.azanSound = Constants.AzanSound.OFF
            }
            // Migrate: users who had lockModeOn=false but doubleTapAction=lock (the old default)
            // if (!prefs.lockModeOn && prefs.doubleTapAction == Constants.DoubleTapAction.LOCK) {
            //     prefs.doubleTapAction = Constants.DoubleTapAction.OFF
            // }

            binding.homeAppsNum.text = prefs.homeAppsNum.toString()
            updateHomeAppsNumSelectorVisibility()
            migrateScreenTimePrefIfNeeded()
            migrateLegacyAppThemeIfNeeded()
            populateScreenTimeOnOff()
            populateWallpaperText()
            populateAppThemeText()
            populateLanguage()
            populateTextSize()
            populateAlignment()
            populateHomeAppIcons()
            populateFocusMode()
            populateFocusModeNotificationsLock()
            populateWeatherSettings()
            populateTodoSettings()
            populateRemindersSettings()
            populatePremiumStatus()
            applyPremiumVisuals()
            populateAccount()
            populatePrayerSettings()
            populateLocationSettings()
            setupLocationAutocomplete()
            populateHourlyChime()
            populateMindfulMorning()
            populateStatusBar()
            populateDateTime()
            populateSwipeApps()
            populateSwipeDownAction()
            // populateDoubleTapAction()
            populateActionHints()
            initClickListeners()
            initObservers()
            (binding.root as? ViewGroup)?.layoutTransition = null
            binding.scrollView.layoutTransition = null
            initSectionsController()
            requireActivity().onBackPressedDispatcher.addCallback(
                viewLifecycleOwner,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        if (sectionsController?.collapseAll() == true) return
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                },
            )
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                findNavController().popBackStack()
            } catch (_: Exception) {}
        }
    }

    override fun onClick(view: View) {
        binding.clockStyleSelectLayout?.visibility = View.GONE
        binding.timeFormatSelectLayout?.visibility = View.GONE
        binding.appsNumSelectLayout.visibility = View.GONE
        binding.dateTimeSelectLayout.visibility = View.GONE
        binding.appThemeSelectLayout.visibility = View.GONE
        binding.swipeDownSelectLayout.visibility = View.GONE
        binding.focusModeSelectLayout?.visibility = View.GONE
        binding.mindfulMorningDurationSelectLayout?.visibility = View.GONE
        binding.mindfulMorningSeveritySelectLayout?.visibility = View.GONE
        // binding.doubleTapActionSelectLayout.visibility = View.GONE
        if (view.id != R.id.textSizeSmall && view.id != R.id.textSizeMedium && view.id != R.id.textSizeLarge
            && view.id != R.id.textSizeXLarge
        ) {
            if (binding.textSizesLayout.isVisible) {
                binding.textSizesLayout.visibility = View.GONE
                applyTextSizeScale()
            }
        }
        if (view.id != R.id.focusCustom
            && view.id != R.id.focusCustomStart
            && view.id != R.id.focusCustomClose
        ) {
            binding.focusCustomLayout.visibility = View.GONE
            binding.etFocusCustomMinutes.hideKeyboard()
        }
        if (view.id != R.id.locationSettings
            && view.id != R.id.chipLocationDevice
            && view.id != R.id.chipLocationManual
            && view.id != R.id.btnSaveLocationSettings
            && view.id != R.id.btnCloseLocationSettings
            && view.id != R.id.etLocationSettings
        ) {
            binding.locationEditorLayout?.visibility = View.GONE
            binding.etLocationSettings?.hideKeyboard()
        }
        if (view.id != R.id.alignmentBottom)
            binding.alignmentSelectLayout.visibility = View.GONE

        when (view.id) {
            R.id.sukunHiddenApps -> showHiddenApps()
            R.id.screenTimeOnOff -> {
                val action = OnboardingAction.TAP_SCREEN_TIME
                viewModel.reportOnboardingAction(action)
                if (!isOnboardingDiscoveryStep(action)) {
                    toggleScreenTime()
                } else {
                    populateScreenTimeOnOff()
                }
            }
            R.id.turnOffSukun -> {
                if (viewModel.isSukunDefault.value == true) {
                    confirmTurnOffSukunLauncher()
                }
            }
            R.id.setLauncher -> onDefaultLauncherChecked()
            R.id.editSections -> toggleSectionsEditMode()
            R.id.startTour -> startOnboardingTour()
            R.id.homeAppsNum -> {
                updateHomeAppsNumSelectorVisibility()
                binding.appsNumSelectLayout.visibility = View.VISIBLE
            }
            R.id.dailyWallpaperUrl -> {
                if (prefs.dailyWallpaperUrl.isNotBlank()) {
                    requireContext().openUrl(prefs.dailyWallpaperUrl)
                } else {
                    toggleDailyWallpaperUpdate()
                }
            }
            R.id.dailyWallpaper -> toggleDailyWallpaperUpdate()
            R.id.changeWallpaperNow -> changeWallpaperNow()
            R.id.alignment -> binding.alignmentSelectLayout.visibility = View.VISIBLE
            R.id.alignmentLeft -> viewModel.updateHomeAlignment(Gravity.START)
            R.id.alignmentCenter -> viewModel.updateHomeAlignment(Gravity.CENTER)
            R.id.alignmentRight -> viewModel.updateHomeAlignment(Gravity.END)
            R.id.alignmentBottom -> updateHomeBottomAlignment()
            R.id.homeAppIcons -> toggleHomeAppIcons()
            R.id.todoOnOff -> toggleTodo()
            R.id.goPremium -> showUpgradeDialog()
            R.id.weatherSettings, R.id.weatherOnOff -> toggleInlineWeather()
            R.id.weatherManage, R.id.weatherManageRow -> showWeatherSettingsSheet()
            R.id.weatherSource -> binding.weatherSourceSelectLayout?.visibility = View.VISIBLE
            R.id.weatherSourceManual -> selectInlineWeatherSource(Constants.WeatherSource.MANUAL)
            R.id.weatherSourceDevice -> selectInlineWeatherSource(Constants.WeatherSource.DEVICE)
            R.id.weatherSourceGoogle -> selectInlineWeatherSource(Constants.WeatherSource.GOOGLE)
            R.id.weatherLocation -> binding.weatherLocationEditLayout?.visibility = View.VISIBLE
            R.id.weatherLocationSave -> saveInlineWeatherLocation()
            R.id.weatherLocationClose -> closeInlineWeatherLocationEditor()
            R.id.weatherUnits -> binding.weatherUnitsSelectLayout?.visibility = View.VISIBLE
            R.id.weatherUnitCelsius -> selectInlineWeatherUnits(Constants.WeatherUnit.CELSIUS)
            R.id.weatherUnitFahrenheit -> selectInlineWeatherUnits(Constants.WeatherUnit.FAHRENHEIT)
            R.id.prayerSettings, R.id.prayerOnOff -> togglePrayerOnOff()
            R.id.prayerManage, R.id.prayerManageRow -> showPrayerSettingsSheet()
            R.id.prayerSettingsToggle -> showPrayerSettingsSheet()
            R.id.prayerAnalyticsLink ->
                findNavController().navigate(R.id.action_settingsFragment_to_prayerAnalyticsFragment)
            R.id.screenTimeAnalyticsLink ->
                findNavController().navigate(R.id.action_settingsFragment_to_screenTimeAnalyticsFragment)
            R.id.locationSettings -> openLocationEditor()
            R.id.chipLocationDevice -> selectLocationDevice()
            R.id.chipLocationManual -> selectLocationManual()
            R.id.btnSaveLocationSettings -> saveLocationManual()
            R.id.btnCloseLocationSettings -> closeLocationEditor()
            R.id.remindersOnOff -> toggleReminders()
            R.id.focusMode -> {
                val action = OnboardingAction.TAP_FOCUS_MODE
                viewModel.reportOnboardingAction(action)
                if (!isOnboardingDiscoveryStep(action)) {
                    if (!PremiumAccess.hasPremiumAccess(prefs)) {
                        showUpgradeDialog()
                    } else if (prefs.isFocusModeActive()) {
                        requireContext().showToast(R.string.focus_mode_blocked)
                    } else {
                        binding.focusModeSelectLayout?.visibility = View.VISIBLE
                    }
                }
            }
            R.id.focus15m -> if (canUsePremiumFeature()) startFocusMode(Constants.FocusModeDuration.FIFTEEN_MIN)
            R.id.focus30m -> if (canUsePremiumFeature()) startFocusMode(Constants.FocusModeDuration.THIRTY_MIN)
            R.id.focus1h -> if (canUsePremiumFeature()) startFocusMode(Constants.FocusModeDuration.ONE_HOUR)
            R.id.focus2h -> if (canUsePremiumFeature()) startFocusMode(Constants.FocusModeDuration.TWO_HOURS)
            R.id.focusCustom -> if (canUsePremiumFeature()) showFocusCustomEditor()
            R.id.focusCustomStart -> startCustomFocusMode()
            R.id.focusOnOff -> toggleFocusOnOff()
            R.id.focusModeNotificationsLock -> toggleFocusModeNotificationsLock()
            R.id.focusModeHideStatusBar -> toggleFocusModeHideStatusBar()
            R.id.focusCustomClose -> {
                binding.focusCustomLayout.visibility = View.GONE
                binding.etFocusCustomMinutes.hideKeyboard()
            }
            R.id.mindfulMorningOnOff -> {
                val action = OnboardingAction.TAP_MINDFUL_MORNING
                viewModel.reportOnboardingAction(action)
                if (!isOnboardingDiscoveryStep(action)) {
                    toggleMindfulMorning()
                } else {
                    populateMindfulMorning()
                }
            }
            R.id.mindfulMorningDuration -> binding.mindfulMorningDurationSelectLayout?.visibility = View.VISIBLE
            R.id.mindfulMorning1h -> updateMindfulMorningDuration(1)
            R.id.mindfulMorning2h -> updateMindfulMorningDuration(2)
            R.id.mindfulMorning3h -> updateMindfulMorningDuration(3)
            R.id.mindfulMorningWakeTime -> showMindfulMorningWakePicker()
            R.id.mindfulMorningSeverity -> binding.mindfulMorningSeveritySelectLayout?.visibility = View.VISIBLE
            R.id.mindfulMorningSeverityNormal -> updateMindfulMorningHard(false)
            R.id.mindfulMorningSeverityHard -> updateMindfulMorningHard(true)
            R.id.hourlyChimeOnOff -> toggleHourlyChime()
            R.id.hourlyChimeInfo -> showHourlyChimeInfoDialog()
            R.id.hourlyChimeStartHour -> showHourPicker(isStart = true)
            R.id.hourlyChimeEndHour -> showHourPicker(isStart = false)
            R.id.hourlyChimeStyle -> {
                binding.chimeStyleSelectLayout?.visibility = View.VISIBLE
                binding.chimeSoundSelectLayout?.visibility = View.GONE
                requireContext().showToast(R.string.chime_preview_tap_style)
            }
            R.id.chimeStyleAuto -> updateChimeStyle(Constants.ChimeStyle.AUTO)
            R.id.chimeStyleSound -> updateChimeStyle(Constants.ChimeStyle.SOUND)
            R.id.chimeStyleVibrate -> updateChimeStyle(Constants.ChimeStyle.VIBRATE)
            R.id.chimeStyleSilent -> updateChimeStyle(Constants.ChimeStyle.SILENT_NOTIFICATION)
            R.id.chimeStyleFlash -> updateChimeStyle(Constants.ChimeStyle.FLASH)
            R.id.hourlyChimeSound -> {
                binding.chimeSoundSelectLayout?.visibility = View.VISIBLE
                requireContext().showToast(R.string.chime_preview_tap_sound)
            }
            R.id.chimeSoundBundled -> updateChimeSound(Constants.ChimeSound.BUNDLED)
            R.id.chimeSoundDefault -> updateChimeSound(Constants.ChimeSound.DEFAULT)
            R.id.chimeSoundCustom -> {
                if (canUsePremiumFeature()) customChimePickerLauncher.launch(arrayOf("audio/*"))
            }
            R.id.statusBar -> toggleStatusBar()
            R.id.dateTime -> binding.dateTimeSelectLayout.visibility = View.VISIBLE
            R.id.dateTimeOn -> toggleDateTime(Constants.DateTime.ON)
            R.id.dateTimeOff -> toggleDateTime(Constants.DateTime.OFF)
            R.id.dateOnly -> toggleDateTime(Constants.DateTime.DATE_ONLY)
            R.id.clockStyle -> binding.clockStyleSelectLayout?.visibility = View.VISIBLE
            R.id.clockStyleStandard -> selectClockStyle(Constants.ClockStyle.STANDARD)
            R.id.clockStyleDayRing -> selectClockStyle(Constants.ClockStyle.DAY_RING)
            R.id.timeFormat -> binding.timeFormatSelectLayout?.visibility = View.VISIBLE
            R.id.timeFormat12h -> selectTimeFormat(use24h = false)
            R.id.timeFormat24h -> selectTimeFormat(use24h = true)
            R.id.dayStartHour -> showDayHourPicker(isStartHour = true)
            R.id.dayEndHour -> showDayHourPicker(isStartHour = false)
            R.id.appThemeText -> {
                val action = OnboardingAction.TAP_APPEARANCE
                viewModel.reportOnboardingAction(action)
                if (!isOnboardingDiscoveryStep(action)) {
                    binding.appThemeSelectLayout.visibility = View.VISIBLE
                }
            }
            R.id.themeLight -> updateTheme(AppCompatDelegate.MODE_NIGHT_NO)
            R.id.themeDark -> updateTheme(AppCompatDelegate.MODE_NIGHT_YES)
            R.id.themeAmbient -> updateTheme(Constants.THEME_MODE_AMBIENT_LIGHT)
            R.id.appLanguageText -> {
                val isLanguageOnboardingStep = viewModel.isOnboardingActive() &&
                    viewModel.currentOnboardingStep().requiredAction == OnboardingAction.TAP_LANGUAGE
                if (isLanguageOnboardingStep) {
                    viewModel.reportOnboardingAction(OnboardingAction.TAP_LANGUAGE)
                } else {
                    findNavController().navigate(R.id.action_settingsFragment_to_languageFragment)
                }
            }
            R.id.textSizeValue -> binding.textSizesLayout.visibility = View.VISIBLE
            R.id.actionAccessibility -> openAccessibilityService()
            R.id.closeAccessibility -> {
                pendingFocusModeDuration = null
                toggleAccessibilityVisibility(false)
            }
            R.id.notWorking -> {
                requireContext().showToast(R.string.accessibility_not_working_help, Toast.LENGTH_LONG)
            }

            R.id.maxApps0 -> updateHomeAppsNum(0)
            R.id.maxApps1 -> updateHomeAppsNum(1)
            R.id.maxApps2 -> updateHomeAppsNum(2)
            R.id.maxApps3 -> updateHomeAppsNum(3)
            R.id.maxApps4 -> updateHomeAppsNum(4)
            R.id.maxApps5 -> updateHomeAppsNum(5)
            R.id.maxApps6 -> updateHomeAppsNum(6)
            R.id.maxApps7 -> updateHomeAppsNum(7)
            R.id.maxApps8 -> updateHomeAppsNum(8)

            R.id.textSizeSmall -> {
                pendingTextSizeScale = 0.9f
                applyTextSizeScale()
            }
            R.id.textSizeMedium -> {
                pendingTextSizeScale = 1.0f
                applyTextSizeScale()
            }
            R.id.textSizeLarge -> {
                pendingTextSizeScale = 1.1f
                applyTextSizeScale()
            }
            R.id.textSizeXLarge -> {
                pendingTextSizeScale = 1.25f
                applyTextSizeScale()
            }

            R.id.swipeLeftApp -> showAppListIfEnabled(Constants.FLAG_SET_SWIPE_LEFT_APP)
            R.id.swipeRightApp -> showAppListIfEnabled(Constants.FLAG_SET_SWIPE_RIGHT_APP)
            R.id.swipeDownAction -> binding.swipeDownSelectLayout.visibility = View.VISIBLE
            R.id.notifications -> updateSwipeDownAction(Constants.SwipeDownAction.NOTIFICATIONS)
            R.id.search -> updateSwipeDownAction(Constants.SwipeDownAction.SEARCH)
            // R.id.doubleTapAction -> binding.doubleTapActionSelectLayout.visibility = View.VISIBLE
            // R.id.doubleTapOff -> selectDoubleTapMode(Constants.DoubleTapAction.OFF)
            // R.id.doubleTapLock -> selectDoubleTapMode(Constants.DoubleTapAction.LOCK)
            // R.id.doubleTapFocus -> selectDoubleTapMode(Constants.DoubleTapAction.FOCUS)

        }
    }

    override fun onLongClick(view: View): Boolean {
        when (view.id) {
            R.id.alignment -> {
                prefs.appLabelAlignment = prefs.homeAlignment
                findNavController().navigate(R.id.action_settingsFragment_to_appListFragment)
                requireContext().showToast(getString(R.string.alignment_changed))
            }

            R.id.dailyWallpaper -> removeWallpaper()
            R.id.appThemeText -> {
                val action = OnboardingAction.TAP_APPEARANCE
                if (!isOnboardingDiscoveryStep(action)) {
                    binding.appThemeSelectLayout.visibility = View.VISIBLE
                }
            }

            R.id.swipeLeftApp -> toggleSwipeLeft()
            R.id.swipeRightApp -> toggleSwipeRight()
            // R.id.doubleTapAction -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        return true
    }

    private fun initClickListeners() {
        binding.accountAction?.setOnClickListener { onAccountActionClick() }
        binding.accountDeleteAction?.setOnClickListener { confirmDeleteAccount() }
        binding.mindfulMorningOnOff?.setOnClickListener(this)
        binding.mindfulMorningDuration?.setOnClickListener(this)
        binding.mindfulMorning1h?.setOnClickListener(this)
        binding.mindfulMorning2h?.setOnClickListener(this)
        binding.mindfulMorning3h?.setOnClickListener(this)
        binding.mindfulMorningWakeTime?.setOnClickListener(this)
        binding.mindfulMorningSeverity?.setOnClickListener(this)
        binding.mindfulMorningSeverityNormal?.setOnClickListener(this)
        binding.mindfulMorningSeverityHard?.setOnClickListener(this)
        binding.sukunHiddenApps.setOnClickListener(this)
        binding.scrollLayout.setOnClickListener(this)
        binding.turnOffSukun.setOnClickListener(this)
        binding.editSections?.setOnClickListener(this)
        binding.setLauncher.setOnClickListener(this)
        binding.startTour?.setOnClickListener(this)
        binding.homeAppsNum.setOnClickListener(this)
        binding.remindersOnOff?.setOnClickListener(this)
        binding.screenTimeOnOff.setOnClickListener(this)
        binding.dailyWallpaperUrl.setOnClickListener(this)
        binding.dailyWallpaper.setOnClickListener(this)
        binding.changeWallpaperNow.setOnClickListener(this)
        binding.alignment.setOnClickListener(this)
        binding.alignmentLeft.setOnClickListener(this)
        binding.alignmentCenter.setOnClickListener(this)
        binding.alignmentRight.setOnClickListener(this)
        binding.alignmentBottom?.setOnClickListener(this)
        binding.homeAppIcons?.setOnClickListener(this)
        binding.todoOnOff?.setOnClickListener(this)
        binding.goPremium.setOnClickListener(this)
        binding.weatherSettings?.setOnClickListener(this)
        binding.weatherOnOff?.setOnClickListener(this)
        binding.weatherManage?.setOnClickListener(this)
        binding.weatherManageRow?.setOnClickListener(this)
        binding.weatherSource?.setOnClickListener(this)
        binding.weatherSourceManual?.setOnClickListener(this)
        binding.weatherSourceDevice?.setOnClickListener(this)
        binding.weatherSourceGoogle?.setOnClickListener(this)
        binding.weatherLocation?.setOnClickListener(this)
        binding.weatherLocationSave?.setOnClickListener(this)
        binding.weatherLocationClose?.setOnClickListener(this)
        binding.weatherUnits?.setOnClickListener(this)
        binding.weatherUnitCelsius?.setOnClickListener(this)
        binding.weatherUnitFahrenheit?.setOnClickListener(this)
        binding.prayerSettings?.setOnClickListener(this)
        binding.prayerOnOff?.setOnClickListener(this)
        binding.prayerManage?.setOnClickListener(this)
        binding.prayerManageRow?.setOnClickListener(this)
        binding.prayerSettingsToggle?.setOnClickListener(this)
        binding.prayerAnalyticsLink?.setOnClickListener(this)
        binding.screenTimeAnalyticsLink?.setOnClickListener(this)
        binding.locationSettings?.setOnClickListener(this)
        binding.chipLocationDevice?.setOnClickListener(this)
        binding.chipLocationManual?.setOnClickListener(this)
        binding.btnSaveLocationSettings?.setOnClickListener(this)
        binding.btnCloseLocationSettings?.setOnClickListener(this)
        binding.focusOnOff.setOnClickListener(this)
        binding.focusMode.setOnClickListener(this)
        binding.focus15m.setOnClickListener(this)
        binding.focus30m.setOnClickListener(this)
        binding.focus1h.setOnClickListener(this)
        binding.focus2h.setOnClickListener(this)
        binding.focusCustom.setOnClickListener(this)
        binding.focusCustomStart.setOnClickListener(this)
        binding.focusModeNotificationsLock.setOnClickListener(this)
        binding.focusModeHideStatusBar.setOnClickListener(this)
        binding.focusCustomClose.setOnClickListener(this)
        binding.hourlyChimeOnOff?.setOnClickListener(this)
        binding.hourlyChimeInfo?.setOnClickListener(this)
        binding.hourlyChimeStartHour?.setOnClickListener(this)
        binding.hourlyChimeEndHour?.setOnClickListener(this)
        binding.hourlyChimeStyle?.setOnClickListener(this)
        binding.chimeStyleAuto?.setOnClickListener(this)
        binding.chimeStyleSound?.setOnClickListener(this)
        binding.chimeStyleVibrate?.setOnClickListener(this)
        binding.chimeStyleSilent?.setOnClickListener(this)
        binding.chimeStyleFlash?.setOnClickListener(this)
        binding.hourlyChimeSound?.setOnClickListener(this)
        binding.chimeSoundBundled?.setOnClickListener(this)
        binding.chimeSoundDefault?.setOnClickListener(this)
        binding.chimeSoundCustom?.setOnClickListener(this)
        binding.statusBar.setOnClickListener(this)
        binding.dateTime.setOnClickListener(this)
        binding.dateTimeOn.setOnClickListener(this)
        binding.dateTimeOff.setOnClickListener(this)
        binding.dateOnly.setOnClickListener(this)
        binding.clockStyle.setOnClickListener(this)
        binding.clockStyleStandard?.setOnClickListener(this)
        binding.clockStyleDayRing?.setOnClickListener(this)
        binding.timeFormat?.setOnClickListener(this)
        binding.timeFormat12h?.setOnClickListener(this)
        binding.timeFormat24h?.setOnClickListener(this)
        binding.dayStartHour.setOnClickListener(this)
        binding.dayEndHour.setOnClickListener(this)
        binding.swipeLeftApp.setOnClickListener(this)
        binding.swipeRightApp.setOnClickListener(this)
        binding.swipeDownAction.setOnClickListener(this)
        binding.search.setOnClickListener(this)
        binding.notifications.setOnClickListener(this)
        // binding.doubleTapAction.setOnClickListener(this)
        // binding.doubleTapOff?.setOnClickListener(this)
        // binding.doubleTapLock.setOnClickListener(this)
        // binding.doubleTapFocus.setOnClickListener(this)
        binding.appThemeText.setOnClickListener(this)
        binding.themeLight.setOnClickListener(this)
        binding.themeDark.setOnClickListener(this)
        binding.themeAmbient.setOnClickListener(this)
        binding.textSizeValue.setOnClickListener(this)
        binding.actionAccessibility.setOnClickListener(this)
        binding.closeAccessibility.setOnClickListener(this)
        binding.notWorking.setOnClickListener(this)

        binding.maxApps0.setOnClickListener(this)
        binding.maxApps1.setOnClickListener(this)
        binding.maxApps2.setOnClickListener(this)
        binding.maxApps3.setOnClickListener(this)
        binding.maxApps4.setOnClickListener(this)
        binding.maxApps5.setOnClickListener(this)
        binding.maxApps6.setOnClickListener(this)
        binding.maxApps7.setOnClickListener(this)
        binding.maxApps8.setOnClickListener(this)

        binding.textSizeSmall?.setOnClickListener(this)
        binding.textSizeMedium?.setOnClickListener(this)
        binding.textSizeLarge?.setOnClickListener(this)
        binding.textSizeXLarge?.setOnClickListener(this)
        binding.appLanguageText?.setOnClickListener(this)

        binding.dailyWallpaper.setOnLongClickListener(this)

        binding.backupExport?.setOnClickListener { if (canUsePremiumFeature()) launchBackupExport() }
        binding.backupImport?.setOnClickListener { if (canUsePremiumFeature()) launchBackupImport() }
        binding.backupInfo?.setOnClickListener { showBackupInfoDialog() }
        binding.premiumInfo?.setOnClickListener { showPremiumInfoDialog() }

        binding.alignment.setOnLongClickListener(this)
        binding.appThemeText.setOnLongClickListener(this)
        binding.swipeLeftApp.setOnLongClickListener(this)
        binding.swipeRightApp.setOnLongClickListener(this)
        // binding.doubleTapAction.setOnLongClickListener(this)
    }

    private fun initObservers() {
        val showWelcomeDialog = prefs.firstSettingsOpen && !viewModel.isOnboardingActive()
        if (showWelcomeDialog) {
            prefs.firstSettingsOpen = false
        }
        viewModel.isSukunDefault.observe(viewLifecycleOwner) {
            populateDefaultLauncher(it == true)
            if (it) {
                prefs.toShowHintCounter += 1
            }
        }
        viewModel.homeAppAlignment.observe(viewLifecycleOwner) {
            populateAlignment()
        }
        viewModel.updateSwipeApps.observe(viewLifecycleOwner) {
            populateSwipeApps()
        }
        if (showWelcomeDialog) {
            view?.post {
                if (isAdded) {
                    viewModel.showDialog.postValue(Constants.Dialog.ABOUT)
                }
            }
        }
        setupOnboardingObservers()
    }

    private fun setupOnboardingObservers() {
        viewModel.onboardingActive.observe(viewLifecycleOwner) { active ->
            binding.startTour?.visibility = if (active == true) View.GONE else View.VISIBLE
            updateOnboardingScrollPadding(active == true)
            if (active != true) {
                clearOnboardingHighlight()
            }
        }
        viewModel.onboardingStepIndex.observe(viewLifecycleOwner) {
            if (!viewModel.isOnboardingActive()) return@observe
            prepareOnboardingSettingsStep(viewModel.currentOnboardingStep().requiredAction)
        }
        viewModel.onboardingPerformSettingsAction.observe(viewLifecycleOwner) { action ->
            performOnboardingSettingsAction(action)
        }
    }

    private fun onboardingTargetView(action: OnboardingAction): View? = when (action) {
        OnboardingAction.TAP_PRAYER_SETTINGS -> binding.prayerSettings
        OnboardingAction.TAP_FOCUS_MODE -> binding.focusMode
        OnboardingAction.TAP_MINDFUL_MORNING -> binding.mindfulMorningOnOff
        OnboardingAction.TAP_SCREEN_TIME -> binding.screenTimeOnOff
        OnboardingAction.TAP_LANGUAGE -> binding.appLanguageText
        OnboardingAction.TAP_APPEARANCE -> binding.appThemeText
        else -> null
    }

    private fun isOnboardingDiscoveryStep(action: OnboardingAction): Boolean {
        return viewModel.isOnboardingActive() && viewModel.currentOnboardingStep().requiredAction == action
    }

    private fun prepareOnboardingSettingsStep(action: OnboardingAction) {
        val target = onboardingTargetView(action)
        if (target == null) {
            clearOnboardingHighlight()
            return
        }
        sectionsController?.expandContaining(target)
        updateOnboardingScrollPadding(true)
        binding.scrollView.post {
            scrollToShowOnboardingTarget(target)
            highlightOnboardingTarget(target)
        }
    }

    private fun performOnboardingSettingsAction(action: OnboardingAction) {
        onboardingTargetView(action)?.performClick()
    }

    private fun startOnboardingTour() {
        if (viewModel.isOnboardingActive()) return
        viewModel.startOnboarding()
        try {
            findNavController().popBackStack(R.id.mainFragment, false)
        } catch (_: Exception) {
        }
    }

    private fun initSectionsController() {
        val controller = SettingsSectionsController(binding.scrollLayout, prefs)
        controller.attach()
        controller.onEditModeChanged = {
            updateEditSectionsIcon()
            requireContext().showToast(
                if (it) R.string.settings_edit_layout_on else R.string.settings_edit_layout_off
            )
        }
        binding.scrollLayout.onDragScrollBy = { dy -> binding.scrollView.scrollBy(0, dy) }
        sectionsController = controller
        updateEditSectionsIcon()
        binding.sukunSettingsLogo?.clipToOutline = true
    }

    private fun toggleSectionsEditMode() {
        val controller = sectionsController ?: return
        controller.setEditMode(!controller.editMode)
    }

    private fun updateEditSectionsIcon() {
        val editing = sectionsController?.editMode == true
        binding.editSections?.apply {
            setImageResource(if (editing) R.drawable.ic_check else R.drawable.ic_rename)
            alpha = if (editing) 1f else 0.7f
            contentDescription = context.getString(
                if (editing) R.string.settings_edit_layout_done else R.string.settings_edit_layout,
            )
        }
    }

    private fun updateOnboardingScrollPadding(active: Boolean) {
        if (scrollLayoutBaseBottomPadding == 0) {
            scrollLayoutBaseBottomPadding = binding.scrollLayout.paddingBottom
        }
        val extra = if (active) (resources.displayMetrics.heightPixels * 0.38f).toInt() else 0
        binding.scrollLayout.setPadding(
            binding.scrollLayout.paddingLeft,
            binding.scrollLayout.paddingTop,
            binding.scrollLayout.paddingRight,
            scrollLayoutBaseBottomPadding + extra,
        )
    }

    private fun scrollToShowOnboardingTarget(target: View) {
        val scrollView = binding.scrollView
        val scrollContent = binding.scrollLayout
        var offsetTop = 0
        var view: View? = target
        while (view != null && view !== scrollContent) {
            offsetTop += view.top
            view = view.parent as? View
        }
        val reserveAbovePanel = (resources.displayMetrics.heightPixels * 0.40f).toInt()
        val targetScrollY = (offsetTop - reserveAbovePanel / 2).coerceAtLeast(0)
        scrollView.smoothScrollTo(0, targetScrollY)
    }

    private fun highlightOnboardingTarget(view: View) {
        clearOnboardingHighlight()
        onboardingHighlightView = view
        onboardingHighlightOriginalBackground = view.background
        view.setBackgroundResource(R.drawable.bg_onboarding_target_highlight)
        view.elevation = resources.getDimension(R.dimen.onboarding_target_elevation)
    }

    private fun clearOnboardingHighlight() {
        onboardingHighlightView?.let { view ->
            view.background = onboardingHighlightOriginalBackground
            view.elevation = 0f
        }
        onboardingHighlightView = null
        onboardingHighlightOriginalBackground = null
    }

    private fun toggleSwipeLeft() {
        prefs.swipeLeftEnabled = !prefs.swipeLeftEnabled
        if (prefs.swipeLeftEnabled) {
            binding.swipeLeftApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
            requireContext().showToast(getString(R.string.swipe_left_app_enabled))
        } else {
            binding.swipeLeftApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
            requireContext().showToast(getString(R.string.swipe_left_app_disabled))
        }
    }

    private fun toggleSwipeRight() {
        prefs.swipeRightEnabled = !prefs.swipeRightEnabled
        if (prefs.swipeRightEnabled) {
            binding.swipeRightApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
            requireContext().showToast(getString(R.string.swipe_right_app_enabled))
        } else {
            binding.swipeRightApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
            requireContext().showToast(getString(R.string.swipe_right_app_disabled))
        }
    }

    private fun toggleStatusBar() {
        prefs.showStatusBar = !prefs.showStatusBar
        populateStatusBar()
    }

    private fun toggleHomeAppIcons() {
        prefs.showHomeAppIcons = !prefs.showHomeAppIcons
        populateHomeAppIcons()
        viewModel.refreshHome(false)
    }

    private fun populateHomeAppIcons() {
        binding.homeAppIcons?.setOnOff(prefs.showHomeAppIcons)
    }

    private fun toggleTodo() {
        prefs.showTodoOnHome = !prefs.showTodoOnHome
        populateTodoSettings()
        viewModel.refreshHome(false)
    }

    private fun populateTodoSettings() {
        binding.todoOnOff?.setOnOff(prefs.showTodoOnHome)
    }

    private fun toggleReminders() {
        prefs.showRemindersOnHome = !prefs.showRemindersOnHome
        populateRemindersSettings()
        if (prefs.showRemindersOnHome) {
            ReminderScheduler.scheduleAll(requireContext())
        } else {
            ReminderScheduler.cancelAll(requireContext())
        }
        viewModel.refreshHome(false)
    }

    private fun populateRemindersSettings() {
        binding.remindersOnOff?.setOnOff(prefs.showRemindersOnHome)
    }

    private fun populateAccount() {
        val signedIn = prefs.isSignedIn

        binding.accountName?.isVisible = signedIn && prefs.accountName.isNotBlank()
        binding.accountName?.text = prefs.accountName
        binding.accountEmail?.isVisible = signedIn
        binding.accountEmail?.text = if (signedIn) {
            val base = prefs.accountEmail
            if (prefs.syncLastUploadAt > 0L) {
                "$base\n${getString(R.string.account_sync_last, formatSyncTime(prefs.syncLastUploadAt))}"
            } else if (signedIn) {
                "$base\n${getString(R.string.account_sync_pending)}"
            } else base
        } else ""
        binding.accountAction?.text = if (signedIn) getString(R.string.sign_out) else getString(R.string.sign_in_with_google)
        binding.accountAction?.setTextColor(
            requireContext().getColorFromAttr(
                if (signedIn) R.attr.primaryColorTrans50 else R.attr.primaryColor
            )
        )
        binding.accountDeleteAction?.isVisible = signedIn
        binding.accountDeleteAction?.setTextColor(
            requireContext().getColorFromAttr(R.attr.primaryColorTrans50)
        )
    }

    private fun formatSyncTime(whenMs: Long): String {
        val delta = System.currentTimeMillis() - whenMs
        return when {
            delta < 60_000L -> getString(R.string.account_sync_just_now)
            delta < 3_600_000L -> getString(R.string.account_sync_minutes_ago, (delta / 60_000L).toInt())
            delta < 86_400_000L -> getString(R.string.account_sync_hours_ago, (delta / 3_600_000L).toInt())
            else -> getString(R.string.account_sync_days_ago, (delta / 86_400_000L).toInt())
        }
    }

    private fun onAccountActionClick() {
        if (prefs.isSignedIn) confirmSignOut() else signIn()
    }

    private fun signIn() {
        android.util.Log.i(GoogleAuthHelper.TAG, "Sign-in tapped from Settings")
        if (!googleAuthHelper.isConfigured()) {
            showSignInFeedback(getString(R.string.sign_in_not_configured), isError = true)
            return
        }
        binding.accountAction?.isEnabled = false
        requireActivity().showToast(getString(R.string.sign_in_opening), Toast.LENGTH_SHORT)
        viewModel.isAuthFlowActive = true
        requireActivity().lifecycleScope.launch {
            try {
                val result = kotlinx.coroutines.withTimeout(180_000L) {
                    googleAuthHelper.signIn(requireActivity())
                }
                handleSignInResult(result)
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                android.util.Log.e(GoogleAuthHelper.TAG, "Settings sign-in timed out", e)
                showSignInFeedback(getString(R.string.sign_in_timed_out), isError = true)
            } catch (e: kotlinx.coroutines.CancellationException) {
                android.util.Log.e(
                    GoogleAuthHelper.TAG,
                    "Settings coroutine cancelled: ${e.javaClass.name} ${e.message}",
                    e
                )
                showSignInFeedback(getString(R.string.sign_in_cancelled), isError = false)
            } catch (e: Exception) {
                android.util.Log.e(
                    GoogleAuthHelper.TAG,
                    "Settings unexpected error: ${e.javaClass.name} ${e.message}",
                    e
                )
                showSignInFeedback(getString(R.string.sign_in_failed), isError = true)
            } finally {
                viewModel.isAuthFlowActive = false
                if (isAdded) {
                    binding.accountAction?.isEnabled = true
                }
            }
        }
    }

    private fun handleSignInResult(result: GoogleAuthHelper.SignInResult) {
        when (result) {
            is GoogleAuthHelper.SignInResult.Success -> {
                android.util.Log.i(GoogleAuthHelper.TAG, "Settings result=Success email=${result.account.email}")
                if (!isAdded) return
                val account = result.account
                viewLifecycleOwner.lifecycleScope.launch {
                    prefs.saveAccount(account.id, account.name, account.email, account.photoUrl)
                    val syncResult = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        AccountSyncManager.onSignInSuccess(requireContext(), requireActivity())
                    }
                    populateAccount()
                    when (syncResult) {
                        is AccountSyncManager.SyncResult.Success -> {
                            val msg = if (syncResult.restored) {
                                getString(R.string.signed_in_settings_restored, account.email)
                            } else {
                                getString(R.string.signed_in_backup_saved, account.email)
                            }
                            requireActivity().showToast(msg, Toast.LENGTH_LONG)
                        }
                        is AccountSyncManager.SyncResult.NeedsRestoreConfirm -> {
                            (requireActivity() as? MainActivity)?.promptDriveRestoreIfNeeded(
                                syncResult.remote,
                                onKeepLocal = {
                                    viewLifecycleOwner.lifecycleScope.launch {
                                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            AccountSyncManager.keepLocalAndPushToDrive(
                                                requireContext(),
                                                syncResult.remote.updatedAt,
                                                requireActivity(),
                                            )
                                        }
                                    }
                                },
                            )
                        }
                        is AccountSyncManager.SyncResult.Error -> {
                            requireActivity().showToast(
                                getString(R.string.signed_in_sync_failed, account.email),
                                Toast.LENGTH_LONG,
                            )
                        }
                    }
                }
            }
            is GoogleAuthHelper.SignInResult.Cancelled -> {
                android.util.Log.w(GoogleAuthHelper.TAG, "Settings result=Cancelled")
                showSignInFeedback(getString(R.string.sign_in_cancelled), isError = false)
            }
            is GoogleAuthHelper.SignInResult.Error -> {
                android.util.Log.e(GoogleAuthHelper.TAG, "Settings result=Error ${result.message}")
                showSignInFeedback(result.message, isError = true)
            }
        }
    }

    private fun showSignInFeedback(message: String, isError: Boolean) {
        if (!isAdded) return
        requireActivity().showToast(message, Toast.LENGTH_LONG)
        if (isError) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.sign_in_with_google)
                .setMessage(message)
                .setPositiveButton(R.string.okay, null)
                .show()
        }
    }

    private fun confirmSignOut() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.sign_out)
            .setMessage(R.string.sign_out_confirmation)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.sign_out) { _, _ -> signOut() }
            .show()
    }

    private fun signOut() {
        viewLifecycleOwner.lifecycleScope.launch {
            googleAuthHelper.signOut()
            AccountSyncManager.signOut()
            prefs.clearAccount()
            prefs.syncDeclinedRemoteUpdatedAt = 0L
            populateAccount()
            requireContext().showToast(R.string.signed_out)
        }
    }

    private fun confirmDeleteAccount() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_account_confirmation_title)
            .setMessage(R.string.delete_account_confirmation_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete_account) { _, _ -> deleteAccount() }
            .show()
    }

    private fun deleteAccount() {
        val progressDialog = AlertDialog.Builder(requireContext())
            .setMessage(R.string.delete_account_progress)
            .setCancelable(false)
            .show()

        viewLifecycleOwner.lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                AccountSyncManager.deleteCloudData(requireContext(), requireActivity())
            }
            googleAuthHelper.signOut()
            AccountSyncManager.signOut()
            prefs.clearAccount()
            prefs.syncPayloadUpdatedAt = 0L
            prefs.syncLastUploadAt = 0L
            progressDialog.dismiss()
            populateAccount()
            if (success) {
                requireContext().showToast(R.string.delete_account_success)
            } else {
                requireContext().showToast(R.string.delete_account_failed)
            }
        }
    }

    fun onPremiumStatusChanged() {
        if (_binding != null) {
            populatePremiumStatus()
            applyPremiumVisuals()
        }
    }

    private fun populatePremiumStatus() {
        binding.premiumRow.isVisible = !prefs.isProUser
        if (prefs.isProUser) return
        binding.goPremium.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
        binding.goPremium.text = getString(R.string.upgrade_to_premium)
    }

    private fun applyPremiumVisuals() {
        val alpha = PremiumAccess.lockedAlpha(prefs)
        binding.focusOnOff.alpha = alpha
        binding.focusMode.alpha = alpha
        binding.focusModeNotificationsLock.alpha = alpha
        binding.focusModeHideStatusBar.alpha = alpha
        binding.backupRow.alpha = alpha
        binding.dailyWallpaper.alpha = alpha
        binding.dailyWallpaperUrl.alpha = alpha
        binding.changeWallpaperNow.alpha = alpha
        binding.chimeSoundCustom?.alpha = alpha
        binding.mindfulMorningSeverityHard?.alpha = alpha
        binding.clockStyleDayRing?.alpha = alpha
    }

    private fun showUpgradeDialog() {
        (requireActivity() as? MainActivity)?.showUpgradeDialog()
    }

    private fun populateLanguage() {
        val selectedLanguage = LocaleHelper.getSelectedLanguage(requireContext())
        binding.appLanguageText?.text = selectedLanguage.listLabel()
    }

    private fun canUsePremiumFeature(): Boolean {
        if (PremiumAccess.hasPremiumAccess(prefs)) return true
        if (PremiumAccess.trialExpired(prefs)) {
            requireContext().showToast(R.string.premium_trial_ended)
        } else {
            requireContext().showToast(R.string.premium_feature_requires_upgrade)
        }
        showUpgradeDialog()
        return false
    }

    private fun populateFocusMode() {
        binding.focusMode.text = prefs.getFocusModeStatus(requireContext())
        if (prefs.isFocusModeActive()) {
            binding.focusModeSelectLayout?.visibility = View.GONE
            binding.focusCustomLayout.visibility = View.GONE
        }
        populateFocusOnOff()
        populateFocusModeHideStatusBar()
    }

    private fun toggleFocusOnOff() {
        prefs.showFocusOnHome = !prefs.showFocusOnHome
        if (!prefs.showFocusOnHome && prefs.isFocusModeActive()) {
            prefs.clearFocusMode()
            populateFocusMode()
        } else {
            populateFocusOnOff()
        }
        viewModel.refreshHome(false)
    }

    private fun populateFocusOnOff() {
        binding.focusOnOff.setOnOff(prefs.showFocusOnHome)
    }

    private fun toggleFocusModeNotificationsLock() {
        prefs.focusModeLockNotifications = !prefs.focusModeLockNotifications
        populateFocusModeNotificationsLock()
    }

    private fun toggleFocusModeHideStatusBar() {
        prefs.focusModeHideStatusBar = !prefs.focusModeHideStatusBar
        populateFocusModeHideStatusBar()
        viewModel.refreshHome(false)
    }

    private fun populateFocusModeNotificationsLock() {
        binding.focusModeNotificationsLock.setOnOff(prefs.focusModeLockNotifications)
    }

    private fun populateFocusModeHideStatusBar() {
        binding.focusModeHideStatusBar.setOnOff(prefs.focusModeHideStatusBar)
    }

    private fun startFocusMode(durationInMillis: Long) {
        if (!PremiumAccess.hasPremiumAccess(prefs)) {
            showUpgradeDialog()
            return
        }
        if (!isAccessServiceEnabled(requireContext())) {
            pendingFocusModeDuration = durationInMillis
            toggleAccessibilityVisibility(true)
            return
        }
        completeFocusModeStart(durationInMillis)
    }

    private fun completeFocusModeStart(durationInMillis: Long) {
        prefs.startFocusMode(durationInMillis)
        populateFocusMode()
        viewModel.refreshHome(false)
        findNavController().popBackStack(R.id.mainFragment, false)
    }

    private fun showFocusCustomEditor() {
        val lastDurationMinutes = (prefs.focusModeLastDuration / Constants.ONE_MINUTE_IN_MILLIS)
            .coerceIn(Constants.MIN_CUSTOM_FOCUS_MINUTES, Constants.MAX_CUSTOM_FOCUS_MINUTES)
        binding.focusCustomLayout.visibility = View.VISIBLE
        binding.etFocusCustomMinutes.setText(lastDurationMinutes.toString())
        binding.etFocusCustomMinutes.showKeyboard()
    }

    private fun startCustomFocusMode() {
        val customMinutes = binding.etFocusCustomMinutes.text?.toString()?.trim()?.toLongOrNull()
        if (customMinutes == null || customMinutes !in Constants.MIN_CUSTOM_FOCUS_MINUTES..Constants.MAX_CUSTOM_FOCUS_MINUTES) {
            requireContext().showToast(
                getString(
                    R.string.focus_custom_duration_error,
                    Constants.MIN_CUSTOM_FOCUS_MINUTES.toInt(),
                    Constants.MAX_CUSTOM_FOCUS_MINUTES.toInt()
                )
            )
            binding.etFocusCustomMinutes.showKeyboard()
            return
        }
        binding.focusCustomLayout.visibility = View.GONE
        binding.etFocusCustomMinutes.hideKeyboard()
        startFocusMode(customMinutes * Constants.ONE_MINUTE_IN_MILLIS)
    }

    private fun populateWeatherSettings() {
        populateInlineWeatherSettings()
    }

    private fun populateInlineWeatherSettings() {
        val isOn = prefs.showWeatherOnHome
        binding.weatherOnOff?.setOnOff(isOn)
        binding.weatherManageRow?.isVisible = isOn
        // Portrait uses Manage → sheet; keep legacy landscape inline options hidden.
        binding.weatherOptionsLayout?.isVisible = false
        binding.weatherLocationRow?.isVisible = false
        if (!isOn) return

        binding.weatherSource?.text = getString(
            when (prefs.weatherSourceMode) {
                Constants.WeatherSource.GOOGLE -> R.string.google_weather
                Constants.WeatherSource.MANUAL -> R.string.manual_location
                else -> R.string.device_location
            }
        )
        binding.weatherLocation?.text = when {
            prefs.weatherSourceMode == Constants.WeatherSource.GOOGLE ->
                getString(R.string.google_weather_short)
            prefs.weatherSourceMode == Constants.WeatherSource.DEVICE ->
                prefs.weatherLocationLabel.ifBlank { getString(R.string.device_location) }
            prefs.weatherLocationLabel.isNotBlank() -> prefs.weatherLocationLabel
            else -> getString(R.string.not_set)
        }
        binding.weatherUnits?.text = getString(
            if (prefs.weatherUnits == Constants.WeatherUnit.FAHRENHEIT)
                R.string.fahrenheit_short
            else
                R.string.celsius_short
        )
        updateInlineWeatherSourceChips()
        updateInlineWeatherUnitChips()
    }

    private fun updateInlineWeatherSourceChips() {
        setInlineChipState(
            binding.weatherSourceManual,
            prefs.weatherSourceMode == Constants.WeatherSource.MANUAL
        )
        setInlineChipState(
            binding.weatherSourceDevice,
            prefs.weatherSourceMode == Constants.WeatherSource.DEVICE
        )
        setInlineChipState(
            binding.weatherSourceGoogle,
            prefs.weatherSourceMode == Constants.WeatherSource.GOOGLE
        )
    }

    private fun updateInlineWeatherUnitChips() {
        setInlineChipState(
            binding.weatherUnitCelsius,
            prefs.weatherUnits == Constants.WeatherUnit.CELSIUS
        )
        setInlineChipState(
            binding.weatherUnitFahrenheit,
            prefs.weatherUnits == Constants.WeatherUnit.FAHRENHEIT
        )
    }

    private fun setInlineChipState(chip: TextView?, selected: Boolean) {
        chip ?: return
        chip.setTextColor(
            requireContext().getColorFromAttr(
                if (selected) R.attr.primaryColor else R.attr.primaryColorTrans50
            )
        )
        chip.paint.isFakeBoldText = selected
    }

    private fun toggleInlineWeather() {
        if (prefs.showWeatherOnHome) {
            prefs.showWeatherOnHome = false
            populateWeatherSettings()
            refreshWeatherIfConfigured()
            viewModel.refreshHome(false)
            return
        }
        val needsLocation = prefs.weatherSourceMode == Constants.WeatherSource.DEVICE
        if (needsLocation && !requireContext().hasWeatherLocationPermission()) {
            prefs.weatherSourceMode = Constants.WeatherSource.GOOGLE
            prefs.clearWeatherCache()
            prefs.showWeatherOnHome = true
            populateWeatherSettings()
            refreshWeatherIfConfigured()
            viewModel.refreshHome(false)
            return
        }
        prefs.showWeatherOnHome = true
        populateWeatherSettings()
        refreshWeatherIfConfigured()
        viewModel.refreshHome(false)
    }

    private fun selectInlineWeatherSource(source: String) {
        binding.weatherSourceSelectLayout?.visibility = View.GONE
        if (prefs.weatherSourceMode == source) return
        when (source) {
            Constants.WeatherSource.DEVICE -> requestWeatherLocationAccess {
                prefs.weatherSourceMode = Constants.WeatherSource.DEVICE
                finishInlineWeatherSourceChange()
            }
            Constants.WeatherSource.MANUAL -> {
                if (prefs.weatherLocationQuery.isBlank()) {
                    openLocationEditor()
                    selectLocationManual()
                    return
                }
                prefs.weatherSourceMode = source
                finishInlineWeatherSourceChange()
            }
            else -> {
                prefs.weatherSourceMode = source
                if (source == Constants.WeatherSource.GOOGLE) {
                    prefs.clearWeatherCache()
                }
                finishInlineWeatherSourceChange()
            }
        }
    }

    private fun finishInlineWeatherSourceChange() {
        populateWeatherSettings()
        populateLocationSettings()
        refreshWeatherIfConfigured()
        viewModel.refreshHome(false)
    }

    private fun requestWeatherLocationAccess(onGranted: () -> Unit) {
        val context = requireContext()
        if (!context.isLocationServicesEnabled()) {
            context.showLocationServicesDisabledDialog()
            return
        }
        if (context.hasWeatherLocationPermission()) {
            onGranted()
            return
        }
        pendingLocationAction = onGranted
        val needed = context.missingSukunSetupPermissions()
        if (needed.isEmpty()) {
            onGranted()
            return
        }
        locationPermissionLauncher.launch(needed)
    }

    private fun saveInlineWeatherLocation() {
        val query = binding.etWeatherLocation?.text?.toString()?.trim().orEmpty()
        if (query.isBlank()) {
            requireContext().showToast(R.string.weather_location_required)
            return
        }
        prefs.weatherSourceMode = Constants.WeatherSource.MANUAL
        prefs.weatherLocationQuery = query
        prefs.weatherLocationLabel = query
        prefs.weatherLatitude = ""
        prefs.weatherLongitude = ""
        prefs.clearWeatherCache()
        closeInlineWeatherLocationEditor()
        populateWeatherSettings()
        populateLocationSettings()
        refreshWeatherIfConfigured()
        viewModel.refreshHome(false)
    }

    private fun closeInlineWeatherLocationEditor() {
        binding.weatherLocationEditLayout?.visibility = View.GONE
        binding.etWeatherLocation?.hideKeyboard()
    }

    private fun selectInlineWeatherUnits(units: String) {
        binding.weatherUnitsSelectLayout?.visibility = View.GONE
        if (prefs.weatherUnits == units) return
        prefs.weatherUnits = units
        prefs.clearWeatherCache()
        populateWeatherSettings()
        refreshWeatherIfConfigured()
        viewModel.refreshHome(false)
    }

    private fun showWeatherSettingsSheet() {
        WeatherSettingsSheet.newInstance().also { sheet ->
            sheet.setListener(object : WeatherSettingsSheet.Listener {
                override fun onWeatherSettingsChanged() {
                    populateWeatherSettings()
                    populatePrayerSettings()
                    viewModel.refreshHome(false)
                }

                override fun onWeatherLocationNeeded() {
                    openLocationEditor()
                    selectLocationManual()
                }
            })
            sheet.show(childFragmentManager, WeatherSettingsSheet.TAG)
        }
    }

    private fun refreshWeatherIfConfigured() {
        if (!prefs.showWeatherOnHome) {
            viewModel.cancelWeatherWorker()
            viewModel.loadWeather()
            return
        }
        if (prefs.weatherSourceMode == Constants.WeatherSource.GOOGLE) {
            viewModel.cancelWeatherWorker(clearCachedWeather = true)
            viewModel.loadWeather()
            return
        }

        val canRefreshWeather = when (prefs.weatherSourceMode) {
            Constants.WeatherSource.DEVICE -> requireContext().hasWeatherLocationPermission()
            else -> prefs.weatherLocationQuery.isNotBlank()
        }

        if (canRefreshWeather) {
            viewModel.setWeatherWorker()
            viewModel.loadWeather(true)
        } else {
            viewModel.cancelWeatherWorker()
            viewModel.loadWeather()
        }
    }

    private fun populatePrayerSettings() {
        val isOn = prefs.showPrayerOnHome
        binding.prayerOnOff?.setOnOff(isOn)
        binding.prayerManageRow?.isVisible = isOn
        binding.prayerOptionsLayout?.isVisible = false
        binding.prayerLocationRow?.isVisible = false
        // Landscape: expose the on/off switch without expanding the old submenu.
        binding.prayerSubMenuLayout?.isVisible = true
        binding.prayerSettingsHeader?.isVisible = false
    }

    private fun togglePrayerOnOff() {
        val action = OnboardingAction.TAP_PRAYER_SETTINGS
        viewModel.reportOnboardingAction(action)
        if (isOnboardingDiscoveryStep(action)) {
            populatePrayerSettings()
            return
        }
        if (prefs.showPrayerOnHome) {
            prefs.showPrayerOnHome = false
            populatePrayerSettings()
            viewModel.cancelPrayerReminder(clearCachedPrayer = true)
            viewModel.refreshHome(false)
            return
        }
        if (!canUsePremiumFeature()) {
            populatePrayerSettings()
            return
        }
        if (prefs.prayerSourceMode == Constants.PrayerSource.DEVICE &&
            !requireContext().hasWeatherLocationPermission()
        ) {
            prefs.prayerSourceMode = Constants.PrayerSource.GOOGLE
            prefs.clearPrayerCache()
            prefs.showPrayerOnHome = true
            requestNotificationPermissionIfNeeded()
            populatePrayerSettings()
            viewModel.cancelPrayerReminder(clearCachedPrayer = true)
            viewModel.loadPrayerState()
            viewModel.refreshHome(false)
            return
        }
        prefs.showPrayerOnHome = true
        requestNotificationPermissionIfNeeded()
        populatePrayerSettings()
        viewModel.refreshPrayerData(
            forceLocationRefresh = prefs.prayerSourceMode == Constants.PrayerSource.DEVICE
        )
        viewModel.refreshHome(false)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) return
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun populateLocationSettings() {
        val summary = when {
            prefs.weatherSourceMode == Constants.WeatherSource.DEVICE
                    || prefs.prayerSourceMode == Constants.PrayerSource.DEVICE ->
                getString(R.string.device_location)
            prefs.weatherSourceMode == Constants.WeatherSource.GOOGLE
                    || prefs.prayerSourceMode == Constants.PrayerSource.GOOGLE ->
                getString(R.string.google_weather_short)
            prefs.weatherLocationLabel.isNotBlank() -> prefs.weatherLocationLabel
            prefs.prayerLocationLabel.isNotBlank() -> prefs.prayerLocationLabel
            else -> getString(R.string.not_set)
        }
        binding.locationSettingsSummary?.text = summary
    }

    private fun openLocationEditor() {
        val isDevice = requireContext().hasWeatherLocationPermission()
                && (prefs.weatherSourceMode == Constants.WeatherSource.DEVICE
                    || prefs.prayerSourceMode == Constants.PrayerSource.DEVICE)
        binding.locationEditorLayout?.visibility = View.VISIBLE
        updateLocationChips()
        val showInput = prefs.weatherSourceMode != Constants.WeatherSource.DEVICE
                && prefs.weatherSourceMode != Constants.WeatherSource.GOOGLE
                && prefs.prayerSourceMode != Constants.PrayerSource.DEVICE
                && prefs.prayerSourceMode != Constants.PrayerSource.GOOGLE
        binding.locationInputRow?.visibility = if (showInput) View.VISIBLE else View.GONE
        if (showInput) {
            val prefill = prefs.weatherLocationLabel.ifBlank { prefs.prayerLocationLabel }
            binding.etLocationSettings?.setText(prefill)
            binding.etLocationSettings?.setSelection(binding.etLocationSettings?.text?.length ?: 0)
            binding.etLocationSettings?.showKeyboard()
            prefetchDeviceLocation()
        }
    }

    private fun updateLocationChips() {
        val isDevice = prefs.weatherSourceMode == Constants.WeatherSource.DEVICE
                || prefs.prayerSourceMode == Constants.PrayerSource.DEVICE
        setLocationChipState(binding.chipLocationDevice, isDevice)
        setLocationChipState(binding.chipLocationManual, !isDevice)
    }

    private fun setLocationChipState(chip: TextView?, selected: Boolean) {
        chip ?: return
        chip.setTextColor(
            requireContext().getColorFromAttr(
                if (selected) R.attr.primaryColor else R.attr.primaryColorTrans50
            )
        )
        chip.paint.isFakeBoldText = selected
    }

    private fun selectLocationDevice() {
        requestWeatherLocationAccess { setAppLocationDevice() }
    }

    private fun setAppLocationDevice() {
        prefs.weatherSourceMode = Constants.WeatherSource.DEVICE
        prefs.prayerSourceMode = Constants.PrayerSource.DEVICE
        prefs.clearWeatherCache()
        prefs.clearPrayerCache()
        binding.locationInputRow?.visibility = View.GONE
        binding.etLocationSettings?.hideKeyboard()
        updateLocationChips()
        populateLocationSettings()
        populateWeatherSettings()
        populatePrayerSettings()
        refreshWeatherIfConfigured()
        viewModel.refreshPrayerData(forceLocationRefresh = true)
        viewModel.refreshHome(false)
    }

    private fun selectLocationManual() {
        binding.locationInputRow?.visibility = View.VISIBLE
        val prefill = prefs.weatherLocationLabel.ifBlank { prefs.prayerLocationLabel }
        binding.etLocationSettings?.setText(prefill)
        binding.etLocationSettings?.setSelection(binding.etLocationSettings?.text?.length ?: 0)
        binding.etLocationSettings?.showKeyboard()
        updateLocationChips()
        prefetchDeviceLocation()
    }

    private fun saveLocationManual() {
        val query = binding.etLocationSettings?.text?.toString()?.trim().orEmpty()
        if (query.isBlank()) {
            requireContext().showToast(R.string.weather_location_required)
            binding.etLocationSettings?.showKeyboard()
            return
        }
        prefs.weatherSourceMode = Constants.WeatherSource.MANUAL
        prefs.weatherLocationQuery = query
        prefs.weatherLocationLabel = query
        prefs.weatherLatitude = ""
        prefs.weatherLongitude = ""
        prefs.clearWeatherCache()
        prefs.prayerSourceMode = Constants.PrayerSource.MANUAL
        prefs.prayerLocationQuery = query
        prefs.prayerLocationLabel = query
        prefs.prayerLatitude = ""
        prefs.prayerLongitude = ""
        prefs.clearPrayerCache()
        closeLocationEditor()
        populateLocationSettings()
        populateWeatherSettings()
        populatePrayerSettings()
        refreshWeatherIfConfigured()
        if (prefs.showPrayerOnHome) viewModel.refreshPrayerData()
        viewModel.refreshHome(false)
        requireContext().showToast(R.string.location_saved)
    }

    private fun closeLocationEditor() {
        binding.locationEditorLayout?.visibility = View.GONE
        binding.etLocationSettings?.hideKeyboard()
    }

    private fun setupLocationAutocomplete() {
        val et = binding.etLocationSettings ?: return
        et.setAdapter(locationAdapter)
        et.threshold = 1
        et.setOnItemClickListener { _, _, position, _ ->
            val selected = locationAdapter.getItem(position).orEmpty()
            if (selected.isNotBlank()) {
                et.setText(selected, false)
                et.setSelection(selected.length)
            }
        }
        et.addTextChangedListener { text ->
            locationSuggestionJob?.cancel()
            val query = text?.toString().orEmpty()
            locationSuggestionJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(250)
                val deviceLabel = cachedDeviceLocationLabel
                val suggestions = buildList {
                    if (!deviceLabel.isNullOrBlank()) add(deviceLabel)
                    addAll(getLocationSuggestions(query))
                }.distinct()
                locationSuggestions.clear()
                locationSuggestions.addAll(suggestions)
                locationAdapter.notifyDataSetChanged()
                if (et.hasFocus() && suggestions.isNotEmpty()) {
                    et.showDropDown()
                }
            }
        }
    }

    private fun prefetchDeviceLocation() {
        if (cachedDeviceLocationLabel != null) return
        viewLifecycleOwner.lifecycleScope.launch {
            cachedDeviceLocationLabel = getCurrentDeviceLocationLabel(requireContext())
        }
    }

    private fun showPrayerSettingsSheet() {
        if (!prefs.showPrayerOnHome) return
        PrayerSettingsSheet.newInstance().also { sheet ->
            sheet.setListener(object : PrayerSettingsSheet.Listener {
                override fun onPrayerSettingsChanged() {
                    populatePrayerSettings()
                    populateWeatherSettings()
                    viewModel.refreshHome(false)
                }

                override fun onPrayerLocationNeeded() {
                    openLocationEditor()
                    selectLocationManual()
                }
            })
            sheet.show(childFragmentManager, PrayerSettingsSheet.TAG)
        }
    }

    private fun populateStatusBar() {
        val activity = activity ?: return
        applyLauncherStatusBarVisibility(activity, show = prefs.showStatusBar)
        binding.statusBar.setOnOff(prefs.showStatusBar)
    }

    private fun toggleDateTime(selected: Int) {
        prefs.dateTimeVisibility = selected
        populateDateTime()
        viewModel.toggleDateTime()
    }

    private fun populateDateTime() {
        binding.dateTime?.text = getString(
            when (prefs.dateTimeVisibility) {
                Constants.DateTime.DATE_ONLY -> R.string.date
                Constants.DateTime.ON -> R.string.on
                else -> R.string.off
            }
        )
        binding.dateTimeOptionsLayout.isVisible = prefs.dateTimeVisibility != Constants.DateTime.OFF
        val clockStyle = PremiumAccess.effectiveClockStyle(prefs)
        binding.clockStyle.text = getString(
            if (clockStyle == Constants.ClockStyle.DAY_RING) R.string.clock_style_day_ring
            else R.string.clock_style_standard
        )
        binding.timeFormat?.text = getString(
            if (prefs.timeFormat24h) R.string.reminder_time_format_24h
            else R.string.reminder_time_format_12h
        )
        binding.timeFormatSelectLayout?.visibility = View.GONE
        binding.dayStartHour.text = formatHourLabel(prefs.dayStartHour)
        binding.dayEndHour.text = formatHourLabel(prefs.dayEndHour)
        val showDayRingHours = prefs.dateTimeVisibility == Constants.DateTime.ON
                && clockStyle == Constants.ClockStyle.DAY_RING
        binding.dayHoursRow?.isVisible = showDayRingHours
    }

    private fun selectClockStyle(selectedStyle: String) {
        binding.clockStyleSelectLayout?.visibility = View.GONE
        if (selectedStyle == Constants.ClockStyle.DAY_RING && !canUsePremiumFeature()) return
        if (prefs.clockStyle == selectedStyle) return
        prefs.clockStyle = selectedStyle
        populateDateTime()
        viewModel.toggleDateTime()
    }

    private fun selectTimeFormat(use24h: Boolean) {
        binding.timeFormatSelectLayout?.visibility = View.GONE
        if (prefs.timeFormat24h == use24h) return
        prefs.timeFormat24h = use24h
        populateDateTime()
        populateHourlyChime()
        populateMindfulMorning()
        viewModel.toggleDateTime()
        if (prefs.showPrayerOnHome) viewModel.refreshPrayerData()
    }

    private fun showDayHourPicker(isStartHour: Boolean) {
        val current = if (isStartHour) prefs.dayStartHour else prefs.dayEndHour
        val hours = (0..23).map { formatHourLabel(it) }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(if (isStartHour) R.string.day_start_hour_title else R.string.day_end_hour_title)
            .setSingleChoiceItems(hours, current.coerceIn(0, 23)) { dialog, which ->
                if (isStartHour) {
                    prefs.dayStartHour = which
                    if (which >= prefs.dayEndHour) prefs.dayEndHour = (which + 1).coerceAtMost(23)
                } else {
                    prefs.dayEndHour = which
                    if (which <= prefs.dayStartHour) prefs.dayStartHour = (which - 1).coerceAtLeast(0)
                }
                populateDateTime()
                viewModel.toggleDateTime()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun formatHourLabel(hour: Int): String {
        return formatReminderTime(hour.coerceIn(0, 23), 0, prefs.timeFormat24h)
    }

    private fun showHiddenApps() {
        if (prefs.hiddenApps.isEmpty()) {
            requireContext().showToast(getString(R.string.no_hidden_apps))
            return
        }
        viewModel.getHiddenApps()
        findNavController().navigate(
            R.id.action_settingsFragment_to_appListFragment,
            bundleOf(Constants.Key.FLAG to Constants.FLAG_HIDDEN_APPS)
        )
    }

    private fun checkAdminPermission() {
        val isAdmin: Boolean = deviceManager.isAdminActive(componentName)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
            prefs.lockModeOn = isAdmin
    }

    private fun toggleAccessibilityVisibility(show: Boolean) {
        if (!show) {
            binding.accessibilityLayout.isVisible = false
            binding.scrollView.animateAlpha(1f)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            requireContext().showAccessibilityDisclosure()
            return
        }
        binding.notWorking.visibility = View.VISIBLE
        if (isAccessServiceEnabled(requireContext()))
            binding.actionAccessibility.text = getString(R.string.disable)
        binding.accessibilityLayout.isVisible = true
        binding.scrollView.animateAlpha(0.5f)
    }

    private fun openAccessibilityService() {
        toggleAccessibilityVisibility(false)
        // populateDoubleTapAction()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun removeActiveAdmin(toastMessage: String? = null) {
        try {
            deviceManager.removeActiveAdmin(componentName) // for backward compatibility
            requireContext().showToast(toastMessage)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun populateDefaultLauncher(isDefault: Boolean) {
        binding.setLauncher.setOnOff(isDefault)
        binding.turnOffSukun.alpha = if (isDefault) 1f else 0.4f
        binding.turnOffSukun.isEnabled = isDefault
    }

    private fun onDefaultLauncherChecked() {
        val wantDefault = binding.setLauncher.isChecked
        val isDefault = viewModel.isSukunDefault.value == true
        if (wantDefault && !isDefault) {
            viewModel.resetLauncherLiveData.call()
        } else if (!wantDefault && isDefault) {
            binding.setLauncher.isChecked = true
            confirmTurnOffSukunLauncher()
        } else {
            populateDefaultLauncher(isDefault)
        }
    }

    private fun confirmTurnOffSukunLauncher() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.turn_off_sukun_launcher)
            .setMessage(R.string.turn_off_sukun_confirmation)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.turn_off) { _, _ ->
                viewModel.resetLauncherLiveData.call()
            }
            .show()
    }

    private fun removeWallpaper() {
        if (requireContext().isEinkDisplay()) {
            prefs.appTheme = AppCompatDelegate.MODE_NIGHT_NO
            setPlainWallpaper(requireContext(), android.R.color.white)
        } else {
            prefs.appTheme = AppCompatDelegate.MODE_NIGHT_YES
            setPlainWallpaper(requireContext(), android.R.color.black)
        }
        if (!prefs.dailyWallpaper) return
        prefs.dailyWallpaper = false
        populateWallpaperText()
        viewModel.cancelWallpaperWorker()
    }

    private fun toggleDailyWallpaperUpdate() {
        if (!prefs.dailyWallpaper && !canUsePremiumFeature()) {
            populateWallpaperText()
            return
        }
        prefs.dailyWallpaper = !prefs.dailyWallpaper
        populateWallpaperText()
        if (prefs.dailyWallpaper) {
            viewModel.setWallpaperWorker()
            showWallpaperToasts()
        } else viewModel.cancelWallpaperWorker()
    }

    private fun showWallpaperToasts() {
        showWallpaperStatusToast()
    }

    private fun showWallpaperStatusToast() {
        val message = if (requireContext().isNetworkAvailable()) {
            R.string.your_wallpaper_will_update_shortly
        } else {
            R.string.wallpaper_will_update_when_online
        }
        val duration = if (requireContext().isNetworkAvailable()) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
        requireContext().showToast(getString(message), duration)
    }

    private fun changeWallpaperNow() {
        if (!canUsePremiumFeature()) return
        prefs.dailyWallpaper = true
        populateWallpaperText()
        viewModel.refreshWallpaperNow()
        showWallpaperStatusToast()
    }

    private fun updateHomeAppsNum(num: Int) {
        val clamped = num.coerceIn(0, prefs.maxHomeAppsAllowed())
        binding.homeAppsNum.text = clamped.toString()
        binding.appsNumSelectLayout.visibility = View.GONE
        prefs.homeAppsNum = clamped
        viewModel.refreshHome(true)
    }

    private fun updateHomeAppsNumSelectorVisibility() {
        val max = prefs.maxHomeAppsAllowed()
        binding.maxApps6.isVisible = max >= Constants.MAX_HOME_APPS_MEDIUM
        binding.maxApps7.isVisible = max >= Constants.MAX_HOME_APPS_SMALL
        binding.maxApps8.isVisible = false
    }

    private var pendingTextSizeScale: Float = -1f

    private fun adjustTextSizePreview(delta: Float) {
        val maxScale = if (isTablet(requireContext())) 2.0f else 1.25f
        val current = if (pendingTextSizeScale > 0) pendingTextSizeScale else prefs.textSizeScale
        val newScale = Math.round((current + delta) * 10f) / 10f
        val clamped = newScale.coerceIn(0.5f, maxScale)
        if (clamped == current) return
        pendingTextSizeScale = clamped
        binding.textSizeValue.text = getTextSizeLabelWithScale(clamped)
    }

    private fun applyTextSizeScale() {
        binding.textSizesLayout.visibility = View.GONE
        if (pendingTextSizeScale < 0 || prefs.textSizeScale == pendingTextSizeScale) {
            pendingTextSizeScale = -1f
            return
        }
        prefs.textSizeScale = pendingTextSizeScale
        pendingTextSizeScale = -1f
        prefs.homeAppsNum = prefs.homeAppsNum
        binding.homeAppsNum.text = prefs.homeAppsNum.toString()
        updateHomeAppsNumSelectorVisibility()
        if (isAdded) {
            (requireActivity() as? MainActivity)?.safeRecreate()
        }
    }

    private fun getTextSizeLabel(scale: Float): String {
        return when {
            scale <= 0.95f -> getString(R.string.small)
            scale <= 1.05f -> getString(R.string.medium)
            scale < 1.2f -> getString(R.string.large)
            else -> getString(R.string.xlarge)
        }
    }


    private fun updateTheme(appTheme: Int) {
        if (prefs.appTheme == appTheme) return
        if (appTheme == Constants.THEME_MODE_AMBIENT_LIGHT) {
            if (!AmbientThemeController.hasLightSensor(requireContext())) {
                requireContext().showToast(R.string.ambient_theme_no_sensor)
                return
            }
            if (!canUsePremiumFeature()) return
        }
        prefs.appTheme = appTheme
        populateAppThemeText(appTheme)
        if (prefs.dailyWallpaper) {
            setPlainWallpaper(appTheme)
            viewModel.setWallpaperWorker()
        }
        val nightMode = when (appTheme) {
            Constants.THEME_MODE_AMBIENT_LIGHT -> {
                val dark = requireContext().isDarkThemeOn()
                prefs.ambientThemeDark = dark
                AmbientThemeController.nightModeForDark(dark)
            }
            else -> appTheme
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
        if (isAdded) {
            (requireActivity() as? MainActivity)?.safeRecreate()
        }
    }

    private fun setAppTheme(theme: Int) {
        // This method is now redundant as logic moved to updateTheme
        AppCompatDelegate.setDefaultNightMode(theme)
    }

    private fun setPlainWallpaper(appTheme: Int) {
        when (appTheme) {
            AppCompatDelegate.MODE_NIGHT_YES -> setPlainWallpaper(requireContext(), android.R.color.black)
            AppCompatDelegate.MODE_NIGHT_NO -> setPlainWallpaper(requireContext(), android.R.color.white)
            Constants.THEME_MODE_AMBIENT_LIGHT -> {
                val color = if (prefs.ambientThemeDark) {
                    android.R.color.black
                } else {
                    android.R.color.white
                }
                setPlainWallpaper(requireContext(), color)
            }
            else -> {
                val color = if (requireContext().isDarkThemeOn()) {
                    android.R.color.black
                } else {
                    android.R.color.white
                }
                setPlainWallpaper(requireContext(), color)
            }
        }
    }

    private fun migrateLegacyAppThemeIfNeeded() {
        val nightMask = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isDeviceDark = nightMask == android.content.res.Configuration.UI_MODE_NIGHT_YES
        prefs.migrateLegacyAppTheme(isDeviceDark)
    }

    private fun populateAppThemeText(appTheme: Int = prefs.appTheme) {
        binding.themeAmbientLabel.text = getString(R.string.theme_mode_ambient)
        binding.themeAmbientStar.isVisible = true
        binding.appThemeText.text = when (appTheme) {
            AppCompatDelegate.MODE_NIGHT_YES -> getString(R.string.dark)
            AppCompatDelegate.MODE_NIGHT_NO -> getString(R.string.light)
            Constants.THEME_MODE_AMBIENT_LIGHT -> getString(R.string.theme_mode_ambient)
            else -> getString(R.string.dark)
        }
    }

    private fun populateTextSize() {
        binding.textSizeValue.text = getTextSizeLabel(prefs.textSizeScale)
        // Highlight selected option when the selector is visible
        val scale = prefs.textSizeScale
        val selectedColor = requireContext().getColorFromAttr(R.attr.primaryColor)
        val defaultColor = requireContext().getColorFromAttr(R.attr.primaryColorTrans50)
        binding.textSizeSmall?.setTextColor(if (scale <= 0.95f) selectedColor else defaultColor)
        binding.textSizeMedium?.setTextColor(if (scale > 0.95f && scale <= 1.05f) selectedColor else defaultColor)
        binding.textSizeLarge?.setTextColor(if (scale > 1.05f && scale < 1.2f) selectedColor else defaultColor)
        binding.textSizeXLarge?.setTextColor(if (scale >= 1.2f) selectedColor else defaultColor)
    }

    private fun getTextSizeLabelWithScale(scale: Float): String {
        val label = getTextSizeLabel(scale)
        val formattedScale = String.format("%.1fx", scale)
        return "$label ($formattedScale)"
    }

    private fun migrateScreenTimePrefIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (prefs.hasShowScreenTimeOnHomePref()) return
        prefs.showScreenTimeOnHome = true
    }

    private fun toggleScreenTime() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (prefs.showScreenTimeOnHome) {
            prefs.showScreenTimeOnHome = false
            populateScreenTimeOnOff()
            viewModel.refreshHome(false)
            return
        }
        if (requireContext().appUsagePermissionGranted()) {
            prefs.showScreenTimeOnHome = true
            populateScreenTimeOnOff()
            viewModel.refreshHome(false)
        } else {
            pendingScreenTimePermissionRequest = true
            viewModel.showDialog.postValue(Constants.Dialog.DIGITAL_WELLBEING)
        }
    }

    private fun populateScreenTimeOnOff() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            binding.screenTimeOnOff.setOnOff(prefs.showScreenTimeOnHome)
        } else binding.screenTimeLayout.visibility = View.GONE
    }


    private fun populateWallpaperText() {
        binding.dailyWallpaper.setOnOff(prefs.dailyWallpaper)
    }

    private fun updateHomeBottomAlignment() {
        if (viewModel.isSukunDefault.value != true) {
            requireContext().showToast(getString(R.string.please_set_sukun_as_default_first), Toast.LENGTH_LONG)
            return
        }
        prefs.homeBottomAlignment = !prefs.homeBottomAlignment
        populateAlignment()
        viewModel.updateHomeAlignment(prefs.homeAlignment)
    }

    private fun populateAlignment() {
        when (prefs.homeAlignment) {
            Gravity.START -> binding.alignment.text = getString(R.string.left)
            Gravity.CENTER -> binding.alignment.text = getString(R.string.center)
            Gravity.END -> binding.alignment.text = getString(R.string.right)
        }
        binding.alignmentBottom?.text = if (prefs.homeBottomAlignment)
            getString(R.string.bottom_on)
        else getString(R.string.bottom_off)
    }

    private fun populateSwipeDownAction() {
        binding.swipeDownAction.text = when (prefs.swipeDownAction) {
            Constants.SwipeDownAction.NOTIFICATIONS -> getString(R.string.notifications)
            else -> getString(R.string.search)
        }
    }

    // private fun populateDoubleTapAction() {
    //     binding.doubleTapAction.text = when (prefs.doubleTapAction) {
    //         Constants.DoubleTapAction.OFF -> getString(R.string.off)
    //         Constants.DoubleTapAction.FOCUS -> getString(R.string.focus)
    //         else -> getString(R.string.lock)
    //     }
    // }
    //
    // private fun selectDoubleTapMode(mode: String) {
    //     if (prefs.doubleTapAction == mode) return
    //     when (mode) {
    //         Constants.DoubleTapAction.LOCK -> {
    //             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    //                 if (!isAccessServiceEnabled(requireContext())) {
    //                     pendingDoubleTapLock = true
    //                     toggleAccessibilityVisibility(true)
    //                     return
    //                 }
    //                 applyDoubleTapLock()
    //                 return
    //             } else {
    //                 val isAdmin = deviceManager.isAdminActive(componentName)
    //                 if (!isAdmin) {
    //                     val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
    //                     intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
    //                     intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_permission_message))
    //                     requireActivity().startActivityForResult(intent, Constants.REQUEST_CODE_ENABLE_ADMIN)
    //                     return
    //                 }
    //                 applyDoubleTapLock()
    //                 return
    //             }
    //         }
    //         else -> {
    //             if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) removeActiveAdmin()
    //             prefs.lockModeOn = false
    //             prefs.doubleTapAction = mode
    //         }
    //     }
    //     populateDoubleTapAction()
    // }
    //
    // private fun applyDoubleTapLock() {
    //     prefs.lockModeOn = true
    //     prefs.doubleTapAction = Constants.DoubleTapAction.LOCK
    //     populateDoubleTapAction()
    //     requireContext().showToast(R.string.double_tap_lock_hint, Toast.LENGTH_LONG)
    // }

    private fun updateSwipeDownAction(swipeDownFor: Int) {
        if (prefs.swipeDownAction == swipeDownFor) return
        prefs.swipeDownAction = swipeDownFor
        populateSwipeDownAction()
    }

    private fun populateSwipeApps() {
        binding.swipeLeftApp.text = prefs.appNameSwipeLeft
        binding.swipeRightApp.text = prefs.appNameSwipeRight
        if (!prefs.swipeLeftEnabled)
            binding.swipeLeftApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
        if (!prefs.swipeRightEnabled)
            binding.swipeRightApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
    }

//    private fun populateDigitalWellbeing() {
//        binding.digitalWellbeing.isVisible = requireContext().isPackageInstalled(Constants.DIGITAL_WELLBEING_PACKAGE_NAME).not()
//                && requireContext().isPackageInstalled(Constants.DIGITAL_WELLBEING_SAMSUNG_PACKAGE_NAME).not()
//                && prefs.hideDigitalWellbeing.not()
//    }

    private fun showAppListIfEnabled(flag: Int) {
        if ((flag == Constants.FLAG_SET_SWIPE_LEFT_APP) and !prefs.swipeLeftEnabled) {
            requireContext().showToast(getString(R.string.long_press_to_enable))
            return
        }
        if ((flag == Constants.FLAG_SET_SWIPE_RIGHT_APP) and !prefs.swipeRightEnabled) {
            requireContext().showToast(getString(R.string.long_press_to_enable))
            return
        }
        viewModel.getAppList(true)
        findNavController().navigate(
            R.id.action_settingsFragment_to_appListFragment,
            bundleOf(Constants.Key.FLAG to flag)
        )
    }

    private fun populateActionHints() {
        if (viewModel.isSukunDefault.value != true) return
    }

    override fun onDestroyView() {
        clearOnboardingHighlight()
        sectionsController = null
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        if (prefs.isFocusModeActive()) {
            requireContext().showToast(R.string.focus_mode_blocked)
            try {
                findNavController().popBackStack(R.id.mainFragment, false)
            } catch (_: Exception) {
            }
            return
        }
        viewModel.isSukunDefault()
        if (pendingScreenTimePermissionRequest) {
            pendingScreenTimePermissionRequest = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && requireContext().appUsagePermissionGranted()
            ) {
                prefs.showScreenTimeOnHome = true
                viewModel.refreshHome(false)
            }
        }
        populateScreenTimeOnOff()
        populateFocusMode()
        populateHourlyChime()
        pendingFocusModeDuration?.let { duration ->
            if (isAccessServiceEnabled(requireContext())) {
                pendingFocusModeDuration = null
                toggleAccessibilityVisibility(false)
                completeFocusModeStart(duration)
            }
        }
        // if (pendingDoubleTapLock && isAccessServiceEnabled(requireContext())) {
        //     pendingDoubleTapLock = false
        //     toggleAccessibilityVisibility(false)
        //     applyDoubleTapLock()
        // }
        populateWeatherSettings()
        refreshWeatherIfConfigured()
        applyPremiumVisuals()
        populatePremiumStatus()
        populatePrayerSettings()
        populateRemindersSettings()
        if (prefs.showPrayerOnHome) {
            viewModel.refreshPrayerData(forceLocationRefresh = prefs.prayerSourceMode == Constants.PrayerSource.DEVICE)
        }
        if (viewModel.isOnboardingActive()) {
            prepareOnboardingSettingsStep(viewModel.currentOnboardingStep().requiredAction)
        }
    }

    private fun populateHourlyChime() {
        val enabled = prefs.hourlyChimeEnabled
        val storedStyle = prefs.hourlyChimeStyle
        val usesSound = storedStyle == Constants.ChimeStyle.SOUND ||
            storedStyle == Constants.ChimeStyle.AUTO
        binding.hourlyChimeOnOff?.setOnOff(enabled)
        binding.hourlyChimeTimeLayout?.isVisible = enabled
        binding.hourlyChimeStyleLayout?.isVisible = enabled
        binding.chimeStyleSelectLayout?.visibility = View.GONE
        binding.hourlyChimeSoundLayout?.isVisible = enabled && usesSound
        binding.chimeSoundSelectLayout?.visibility = View.GONE
        binding.hourlyChimeStartHour?.text = formatHourLabel(prefs.hourlyChimeStartHour)
        binding.hourlyChimeEndHour?.text = formatHourLabel(prefs.hourlyChimeEndHour)
        binding.hourlyChimeStyle?.text = chimeStyleLabel(storedStyle)
        binding.hourlyChimeSound?.text = getString(
            when (prefs.hourlyChimeSound) {
                Constants.ChimeSound.DEFAULT -> R.string.chime_sound_default
                Constants.ChimeSound.CUSTOM -> R.string.custom
                else -> R.string.chime_sound_bundled
            }
        )
    }

    private fun chimeStyleLabel(style: String): String {
        val resolved = HourlyChimeEffects.resolveStyle(requireContext(), style)
        val resolvedLabel = getString(
            when (resolved) {
                Constants.ChimeStyle.VIBRATE -> R.string.chime_style_vibrate
                Constants.ChimeStyle.SILENT_NOTIFICATION -> R.string.chime_style_silent
                Constants.ChimeStyle.FLASH -> R.string.chime_style_flash
                else -> R.string.chime_style_sound
            }
        )
        return if (style == Constants.ChimeStyle.AUTO) {
            getString(R.string.chime_style_auto_current, resolvedLabel)
        } else {
            resolvedLabel
        }
    }

    private fun populateMindfulMorning() {
        val enabled = prefs.mindfulMorningEnabled
        binding.mindfulMorningOnOff?.setOnOff(enabled)
        binding.mindfulMorningOptionsLayout?.isVisible = enabled
        binding.mindfulMorningDurationSelectLayout?.visibility = View.GONE
        binding.mindfulMorningSeveritySelectLayout?.visibility = View.GONE
        val durationText = when (prefs.mindfulMorningDurationHours) {
            1 -> getString(R.string.mindful_morning_1h)
            3 -> getString(R.string.mindful_morning_3h)
            else -> getString(R.string.mindful_morning_2h)
        }
        binding.mindfulMorningDuration?.text = durationText
        binding.mindfulMorningWakeTime?.text = formatMindfulMorningWakeTime()
        binding.mindfulMorningSeverity?.text = getString(
            if (prefs.mindfulMorningHard) R.string.mindful_morning_hard_premium
            else R.string.mindful_morning_normal
        )
    }

    private fun formatMindfulMorningWakeTime(): String {
        return formatReminderTime(
            prefs.mindfulMorningWakeHour,
            prefs.mindfulMorningWakeMinute,
            prefs.timeFormat24h,
        )
    }

    private fun toggleMindfulMorning() {
        prefs.mindfulMorningEnabled = !prefs.mindfulMorningEnabled
        populateMindfulMorning()
    }

    private fun updateMindfulMorningDuration(hours: Int) {
        prefs.mindfulMorningDurationHours = hours
        populateMindfulMorning()
    }

    private fun updateMindfulMorningHard(hard: Boolean) {
        if (hard && !canUsePremiumFeature()) return
        prefs.mindfulMorningHard = hard
        populateMindfulMorning()
    }

    private fun showMindfulMorningWakePicker() {
        TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                prefs.mindfulMorningWakeHour = hourOfDay
                prefs.mindfulMorningWakeMinute = minute
                populateMindfulMorning()
            },
            prefs.mindfulMorningWakeHour,
            prefs.mindfulMorningWakeMinute,
            prefs.timeFormat24h,
        ).show()
    }

    private fun toggleHourlyChime() {
        if (!prefs.hourlyChimeEnabled) {
            // About to enable: check for exact alarm permission on Android 12+
            if (!HourlyChimeScheduler.canScheduleExactChime(requireContext())) {
                requireContext().showToast(R.string.hourly_chime_exact_alarm_needed, Toast.LENGTH_LONG)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:${requireContext().packageName}")
                        )
                    )
                }
                populateHourlyChime()
                return
            }
        }

        prefs.hourlyChimeEnabled = !prefs.hourlyChimeEnabled
        if (prefs.hourlyChimeEnabled) {
            HourlyChimeScheduler.scheduleNext(requireContext())
            showHourlyChimeInfoDialog(offerTryCurrent = true)
        } else {
            HourlyChimeScheduler.cancel(requireContext())
        }
        populateHourlyChime()
    }

    private fun updateChimeStyle(style: String) {
        if (style == Constants.ChimeStyle.FLASH && !requireContext().hasCameraPermission()) {
            val needed = requireContext().missingSukunSetupPermissions()
            cameraPermissionLauncher.launch(
                needed.ifEmpty { arrayOf(Manifest.permission.CAMERA) }
            )
            return
        }
        prefs.hourlyChimeStyle = style
        binding.chimeStyleSelectLayout?.visibility = View.GONE
        populateHourlyChime()
        previewChimeStyle(style)
    }

    private fun updateChimeSound(sound: String) {
        prefs.hourlyChimeSound = sound
        binding.chimeSoundSelectLayout?.visibility = View.GONE
        populateHourlyChime()
        previewChimeSound(sound)
    }

    private fun previewChimeStyle(style: String) {
        if (!isAdded) return
        val resolved = HourlyChimeEffects.resolveStyle(requireContext(), style)
        val tipRes = when (resolved) {
            Constants.ChimeStyle.VIBRATE -> R.string.chime_preview_vibrate
            Constants.ChimeStyle.SILENT_NOTIFICATION -> R.string.chime_preview_silent
            Constants.ChimeStyle.FLASH -> R.string.chime_preview_flash
            else -> R.string.chime_preview_sound
        }
        requireContext().showToast(tipRes)
        HourlyChimeEffects.playStyle(requireContext(), style, prefs)
    }

    private fun previewChimeSound(sound: String) {
        if (!isAdded) return
        requireContext().showToast(R.string.chime_preview_sound)
        HourlyChimeEffects.playSound(requireContext(), sound, prefs.hourlyChimeCustomUri)
    }

    private fun showHourlyChimeInfoDialog(offerTryCurrent: Boolean = false) {
        val builder = AlertDialog.Builder(requireContext())
            .setTitle(R.string.hourly_chime_info_title)
            .setMessage(R.string.hourly_chime_info_message)
            .setPositiveButton(R.string.okay, null)
        if (offerTryCurrent && prefs.hourlyChimeEnabled) {
            val styleLabel = chimeStyleLabel(prefs.hourlyChimeStyle)
            builder.setNeutralButton(getString(R.string.chime_preview_try, styleLabel)) { _, _ ->
                previewChimeStyle(prefs.hourlyChimeStyle)
            }
        }
        builder.show()
    }

    private fun showHourPicker(isStart: Boolean) {
        val current = if (isStart) prefs.hourlyChimeStartHour else prefs.hourlyChimeEndHour
        val hours = (0..23).map { formatHourLabel(it) }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(if (isStart) R.string.hourly_chime_from else R.string.hourly_chime_to)
            .setSingleChoiceItems(hours, current) { dialog, which ->
                if (isStart) {
                    prefs.hourlyChimeStartHour = which
                    if (which >= prefs.hourlyChimeEndHour) prefs.hourlyChimeEndHour = (which + 1).coerceAtMost(23)
                } else {
                    prefs.hourlyChimeEndHour = which
                    if (which <= prefs.hourlyChimeStartHour) prefs.hourlyChimeStartHour = (which - 1).coerceAtLeast(0)
                }
                HourlyChimeScheduler.scheduleNext(requireContext())
                populateHourlyChime()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun CompoundButton.setOnOff(checked: Boolean) {
        if (isChecked != checked) isChecked = checked
    }

    override fun onDestroy() {
        if (::viewModel.isInitialized) {
            viewModel.checkForMessages.call()
        }
        super.onDestroy()
    }
}

