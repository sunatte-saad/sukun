package app.sukun.ui

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import app.sukun.BuildConfig
import app.sukun.MainViewModel
import app.sukun.R
import app.sukun.data.Constants
import app.sukun.data.Prefs
import app.sukun.databinding.FragmentSettingsBinding
import app.sukun.helper.HourlyChimeScheduler
import app.sukun.helper.PrayerReminderScheduler
import app.sukun.helper.animateAlpha
import app.sukun.helper.appUsagePermissionGranted
import app.sukun.helper.getFocusModeStatus
import app.sukun.helper.getColorFromAttr
import app.sukun.helper.hasWeatherLocationPermission
import app.sukun.helper.isLocationServicesEnabled
import app.sukun.helper.hideKeyboard
import app.sukun.helper.isAccessServiceEnabled
import app.sukun.helper.isDarkThemeOn
import app.sukun.helper.isEinkDisplay
import app.sukun.helper.isSukunDefault
import app.sukun.helper.isTablet
import app.sukun.helper.openAppInfo
import app.sukun.helper.openUrl
import app.sukun.helper.setPlainWallpaper
import app.sukun.helper.showKeyboard
import app.sukun.helper.showToast
import app.sukun.listener.DeviceAdmin

class SettingsFragment : Fragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceManager: DevicePolicyManager
    private lateinit var componentName: ComponentName

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val weatherLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
                    || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                prefs.weatherSourceMode = Constants.WeatherSource.DEVICE
                prefs.clearWeatherCache()
                populateWeatherSettings()
                refreshWeatherIfConfigured()
            } else {
                requireContext().showToast(R.string.weather_permission_needed)
            }
        }
    private val prayerLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
                    || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                prefs.prayerSourceMode = Constants.PrayerSource.DEVICE
                prefs.clearPrayerCache()
                populatePrayerSettings()
                refreshPrayerIfConfigured(forceLocationRefresh = true)
            } else {
                requireContext().showToast(R.string.prayer_permission_needed)
            }
        }
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op: system handles denial */ }
    private val customAzanPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            }
            prefs.azanSound = Constants.AzanSound.CUSTOM
            prefs.azanCustomUri = uri.toString()
            populatePrayerSettings()
            refreshPrayerIfConfigured()
            requireContext().showToast(R.string.prayer_azan_saved)
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")
        viewModel.isSukunDefault()

        deviceManager = requireContext().getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        componentName = ComponentName(requireContext(), DeviceAdmin::class.java)
        checkAdminPermission()

        binding.homeAppsNum.text = prefs.homeAppsNum.toString()
        populateKeyboardText()
        populateAppDrawerFastScroller()
        populateScreenTimeOnOff()
        populateLockSettings()
        populateHomeButtonRecents()
        populateWallpaperText()
        populateAppThemeText()
        populateTextSize()
        populateAlignment()
        populateHomeAppIcons()
        populateFocusMode()
        populateFocusModeNotificationsLock()
        populateWeatherSettings()
        populateDailyNotesSettings()
        populatePrayerSettings()
        populateHourlyChime()
        populateStatusBar()
        populateDateTime()
        populateSwipeApps()
        populateSwipeDownAction()
        populateDoubleTapAction()
        populateActionHints()
        initClickListeners()
        initObservers()

    }

    override fun onClick(view: View) {
        binding.appsNumSelectLayout.visibility = View.GONE
        binding.dateTimeSelectLayout.visibility = View.GONE
        binding.appThemeSelectLayout.visibility = View.GONE
        binding.swipeDownSelectLayout.visibility = View.GONE
        binding.weatherUnitsSelectLayout.visibility = View.GONE
        binding.weatherSourceSelectLayout.visibility = View.GONE
        binding.prayerSourceSelectLayout?.visibility = View.GONE
        binding.azanSoundSelectLayout?.visibility = View.GONE
        binding.focusModeSelectLayout?.visibility = View.GONE
        binding.doubleTapActionSelectLayout.visibility = View.GONE
        if (view.id != R.id.textSizeMinus && view.id != R.id.textSizePlus) {
            if (binding.textSizesLayout.isVisible) {
                binding.textSizesLayout.visibility = View.GONE
                applyTextSizeScale()
            }
        }
        if (view.id != R.id.weatherLocation
            && view.id != R.id.weatherLocationSave
            && view.id != R.id.weatherLocationClose
        ) {
            binding.weatherLocationEditLayout?.visibility = View.GONE
            binding.etWeatherLocation?.hideKeyboard()
        }
        if (view.id != R.id.prayerLocation
            && view.id != R.id.prayerLocationSave
            && view.id != R.id.prayerLocationClose
        ) {
            binding.prayerLocationEditLayout?.visibility = View.GONE
            binding.etPrayerLocation?.hideKeyboard()
        }
        if (view.id != R.id.focusCustom
            && view.id != R.id.focusCustomStart
            && view.id != R.id.focusCustomClose
        ) {
            binding.focusCustomLayout.visibility = View.GONE
            binding.etFocusCustomMinutes.hideKeyboard()
        }
        if (view.id != R.id.alignmentBottom)
            binding.alignmentSelectLayout.visibility = View.GONE

        when (view.id) {
            R.id.sukunHiddenApps -> showHiddenApps()
            R.id.screenTimeOnOff -> viewModel.showDialog.postValue(Constants.Dialog.DIGITAL_WELLBEING)
            R.id.appInfo -> openAppInfo(requireContext(), Process.myUserHandle(), BuildConfig.APPLICATION_ID)
            R.id.setLauncher -> {
                if (viewModel.isSukunDefault.value == true) {
                    confirmTurnOffSukunLauncher()
                } else {
                    viewModel.resetLauncherLiveData.call()
                }
            }
            R.id.toggleLock -> toggleLockMode()
            R.id.homeButtonRecents -> toggleHomeButtonRecents()
            R.id.autoShowKeyboard -> toggleKeyboardText()
            R.id.appDrawerFastScroller -> toggleAppDrawerFastScroller()
            R.id.homeAppsNum -> binding.appsNumSelectLayout.visibility = View.VISIBLE
            R.id.dailyWallpaperUrl -> requireContext().openUrl(prefs.dailyWallpaperUrl)
            R.id.dailyWallpaper -> toggleDailyWallpaperUpdate()
            R.id.changeWallpaperNow -> changeWallpaperNow()
            R.id.alignment -> binding.alignmentSelectLayout.visibility = View.VISIBLE
            R.id.alignmentLeft -> viewModel.updateHomeAlignment(Gravity.START)
            R.id.alignmentCenter -> viewModel.updateHomeAlignment(Gravity.CENTER)
            R.id.alignmentRight -> viewModel.updateHomeAlignment(Gravity.END)
            R.id.alignmentBottom -> updateHomeBottomAlignment()
            R.id.homeAppIcons -> toggleHomeAppIcons()
            R.id.dailyNotesOnOff -> toggleDailyNotes()
            R.id.dailyNotesList -> showDailyNotesEditor()
            R.id.weatherOnOff -> toggleWeather()
            R.id.weatherSource -> binding.weatherSourceSelectLayout.visibility = View.VISIBLE
            R.id.weatherSourceManual -> updateWeatherSource(Constants.WeatherSource.MANUAL)
            R.id.weatherSourceDevice -> selectDeviceWeatherSource()
            R.id.weatherSourceGoogle -> updateWeatherSource(Constants.WeatherSource.GOOGLE)
            R.id.weatherLocation -> showWeatherLocationEditor()
            R.id.weatherLocationSave -> saveWeatherLocation()
            R.id.weatherLocationClose -> {
                binding.weatherLocationEditLayout?.visibility = View.GONE
                binding.etWeatherLocation?.hideKeyboard()
            }
            R.id.weatherUnits -> binding.weatherUnitsSelectLayout.visibility = View.VISIBLE
            R.id.weatherUnitCelsius -> updateWeatherUnits(Constants.WeatherUnit.CELSIUS)
            R.id.weatherUnitFahrenheit -> updateWeatherUnits(Constants.WeatherUnit.FAHRENHEIT)
            R.id.prayerOnOff -> togglePrayer()
            R.id.prayerSource -> binding.prayerSourceSelectLayout?.visibility = View.VISIBLE
            R.id.prayerSourceManual -> updatePrayerSource(Constants.PrayerSource.MANUAL)
            R.id.prayerSourceDevice -> selectDevicePrayerSource()
            R.id.prayerLocation -> showPrayerLocationEditor()
            R.id.prayerLocationSave -> savePrayerLocation()
            R.id.prayerLocationClose -> {
                binding.prayerLocationEditLayout?.visibility = View.GONE
                binding.etPrayerLocation?.hideKeyboard()
            }
            R.id.azanOnOff -> toggleAzan()
            R.id.azanSound -> binding.azanSoundSelectLayout?.visibility = View.VISIBLE
            R.id.azanSoundMakkah -> updateAzanSound(Constants.AzanSound.MAKKAH)
            R.id.azanSoundMarylebone -> updateAzanSound(Constants.AzanSound.MARYLEBONE)
            R.id.azanSoundCustom -> showCustomAzanPicker()
            R.id.customAzanFile -> showCustomAzanPicker()
            R.id.focusMode -> {
                if (prefs.isFocusModeActive()) requireContext().showToast(R.string.focus_mode_blocked)
                else binding.focusModeSelectLayout?.visibility = View.VISIBLE
            }
            R.id.focus15m -> startFocusMode(Constants.FocusModeDuration.FIFTEEN_MIN)
            R.id.focus30m -> startFocusMode(Constants.FocusModeDuration.THIRTY_MIN)
            R.id.focus1h -> startFocusMode(Constants.FocusModeDuration.ONE_HOUR)
            R.id.focus2h -> startFocusMode(Constants.FocusModeDuration.TWO_HOURS)
            R.id.focusCustom -> showFocusCustomEditor()
            R.id.focusCustomStart -> startCustomFocusMode()
            R.id.focusModeNotificationsLock -> toggleFocusModeNotificationsLock()
            R.id.focusCustomClose -> {
                binding.focusCustomLayout.visibility = View.GONE
                binding.etFocusCustomMinutes.hideKeyboard()
            }
            R.id.remindersManage -> findNavController().navigate(R.id.action_settingsFragment_to_remindersFragment)
            R.id.prayerAnalytics -> findNavController().navigate(R.id.action_settingsFragment_to_prayerAnalyticsFragment)
            R.id.hourlyChimeOnOff -> toggleHourlyChime()
            R.id.hourlyChimeStartHour -> showHourPicker(isStart = true)
            R.id.hourlyChimeEndHour -> showHourPicker(isStart = false)
            R.id.statusBar -> toggleStatusBar()
            R.id.dateTime -> binding.dateTimeSelectLayout.visibility = View.VISIBLE
            R.id.dateTimeOn -> toggleDateTime(Constants.DateTime.ON)
            R.id.dateTimeOff -> toggleDateTime(Constants.DateTime.OFF)
            R.id.dateOnly -> toggleDateTime(Constants.DateTime.DATE_ONLY)
            R.id.clockStyle -> showClockStylePicker()
            R.id.dayStartHour -> showDayHourEditor(isStartHour = true)
            R.id.dayEndHour -> showDayHourEditor(isStartHour = false)
            R.id.appThemeText -> binding.appThemeSelectLayout.visibility = View.VISIBLE
            R.id.themeLight -> updateTheme(AppCompatDelegate.MODE_NIGHT_NO)
            R.id.themeDark -> updateTheme(AppCompatDelegate.MODE_NIGHT_YES)
            R.id.themeSystem -> updateTheme(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            R.id.textSizeValue -> binding.textSizesLayout.visibility = View.VISIBLE
            R.id.actionAccessibility -> openAccessibilityService()
            R.id.closeAccessibility -> toggleAccessibilityVisibility(false)
            R.id.notWorking -> {
                if (Constants.URL_DOUBLE_TAP.isBlank()) requireContext().showToast(R.string.not_set)
                else requireContext().openUrl(Constants.URL_DOUBLE_TAP)
            }

            R.id.tvGestures -> binding.flSwipeDown.visibility = View.VISIBLE

            R.id.maxApps0 -> updateHomeAppsNum(0)
            R.id.maxApps1 -> updateHomeAppsNum(1)
            R.id.maxApps2 -> updateHomeAppsNum(2)
            R.id.maxApps3 -> updateHomeAppsNum(3)
            R.id.maxApps4 -> updateHomeAppsNum(4)
            R.id.maxApps5 -> updateHomeAppsNum(5)
            R.id.maxApps6 -> updateHomeAppsNum(6)
            R.id.maxApps7 -> updateHomeAppsNum(7)
            R.id.maxApps8 -> updateHomeAppsNum(8)

            R.id.textSizeMinus -> adjustTextSizePreview(-0.1f)
            R.id.textSizePlus -> adjustTextSizePreview(0.1f)

            R.id.swipeLeftApp -> showAppListIfEnabled(Constants.FLAG_SET_SWIPE_LEFT_APP)
            R.id.swipeRightApp -> showAppListIfEnabled(Constants.FLAG_SET_SWIPE_RIGHT_APP)
            R.id.swipeDownAction -> binding.swipeDownSelectLayout.visibility = View.VISIBLE
            R.id.notifications -> updateSwipeDownAction(Constants.SwipeDownAction.NOTIFICATIONS)
            R.id.search -> updateSwipeDownAction(Constants.SwipeDownAction.SEARCH)
            R.id.doubleTapAction -> binding.doubleTapActionSelectLayout.visibility = View.VISIBLE
            R.id.doubleTapLock -> updateDoubleTapAction(Constants.DoubleTapAction.LOCK)
            R.id.doubleTapFocus -> updateDoubleTapAction(Constants.DoubleTapAction.FOCUS)

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
                binding.appThemeSelectLayout.visibility = View.VISIBLE
                binding.themeSystem.visibility = View.VISIBLE
            }

            R.id.swipeLeftApp -> toggleSwipeLeft()
            R.id.swipeRightApp -> toggleSwipeRight()
            R.id.toggleLock -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        return true
    }

    private fun initClickListeners() {
        binding.sukunHiddenApps.setOnClickListener(this)
        binding.scrollLayout.setOnClickListener(this)
        binding.appInfo.setOnClickListener(this)
        binding.setLauncher.setOnClickListener(this)
        binding.autoShowKeyboard.setOnClickListener(this)
        binding.appDrawerFastScroller.setOnClickListener(this)
        binding.toggleLock.setOnClickListener(this)
        binding.homeButtonRecents.setOnClickListener(this)
        binding.homeAppsNum.setOnClickListener(this)
        binding.remindersManage?.setOnClickListener(this)
        binding.prayerAnalytics?.setOnClickListener(this)
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
        binding.dailyNotesOnOff.setOnClickListener(this)
        binding.dailyNotesList.setOnClickListener(this)
        binding.weatherOnOff.setOnClickListener(this)
        binding.weatherSource.setOnClickListener(this)
        binding.weatherSourceManual.setOnClickListener(this)
        binding.weatherSourceDevice.setOnClickListener(this)
        binding.weatherSourceGoogle.setOnClickListener(this)
        binding.weatherLocation.setOnClickListener(this)
        binding.weatherLocationSave.setOnClickListener(this)
        binding.weatherLocationClose.setOnClickListener(this)
        binding.weatherUnits.setOnClickListener(this)
        binding.weatherUnitCelsius.setOnClickListener(this)
        binding.weatherUnitFahrenheit.setOnClickListener(this)
        binding.prayerOnOff?.setOnClickListener(this)
        binding.prayerSource?.setOnClickListener(this)
        binding.prayerSourceManual?.setOnClickListener(this)
        binding.prayerSourceDevice?.setOnClickListener(this)
        binding.prayerLocation?.setOnClickListener(this)
        binding.prayerLocationSave?.setOnClickListener(this)
        binding.prayerLocationClose?.setOnClickListener(this)
        binding.azanOnOff?.setOnClickListener(this)
        binding.azanSound?.setOnClickListener(this)
        binding.azanSoundMakkah?.setOnClickListener(this)
        binding.azanSoundMarylebone?.setOnClickListener(this)
        binding.azanSoundCustom?.setOnClickListener(this)
        binding.customAzanFile?.setOnClickListener(this)
        binding.focusMode.setOnClickListener(this)
        binding.focus15m.setOnClickListener(this)
        binding.focus30m.setOnClickListener(this)
        binding.focus1h.setOnClickListener(this)
        binding.focus2h.setOnClickListener(this)
        binding.focusCustom.setOnClickListener(this)
        binding.focusCustomStart.setOnClickListener(this)
        binding.focusModeNotificationsLock.setOnClickListener(this)
        binding.focusCustomClose.setOnClickListener(this)
        binding.hourlyChimeOnOff?.setOnClickListener(this)
        binding.hourlyChimeStartHour?.setOnClickListener(this)
        binding.hourlyChimeEndHour?.setOnClickListener(this)
        binding.statusBar.setOnClickListener(this)
        binding.dateTime.setOnClickListener(this)
        binding.dateTimeOn.setOnClickListener(this)
        binding.dateTimeOff.setOnClickListener(this)
        binding.dateOnly.setOnClickListener(this)
        binding.clockStyle.setOnClickListener(this)
        binding.dayStartHour.setOnClickListener(this)
        binding.dayEndHour.setOnClickListener(this)
        binding.swipeLeftApp.setOnClickListener(this)
        binding.swipeRightApp.setOnClickListener(this)
        binding.swipeDownAction.setOnClickListener(this)
        binding.search.setOnClickListener(this)
        binding.notifications.setOnClickListener(this)
        binding.doubleTapAction.setOnClickListener(this)
        binding.doubleTapLock.setOnClickListener(this)
        binding.doubleTapFocus.setOnClickListener(this)
        binding.appThemeText.setOnClickListener(this)
        binding.themeLight.setOnClickListener(this)
        binding.themeDark.setOnClickListener(this)
        binding.themeSystem.setOnClickListener(this)
        binding.textSizeValue.setOnClickListener(this)
        binding.actionAccessibility.setOnClickListener(this)
        binding.closeAccessibility.setOnClickListener(this)
        binding.notWorking.setOnClickListener(this)
        binding.tvGestures.setOnClickListener(this)

        binding.maxApps0.setOnClickListener(this)
        binding.maxApps1.setOnClickListener(this)
        binding.maxApps2.setOnClickListener(this)
        binding.maxApps3.setOnClickListener(this)
        binding.maxApps4.setOnClickListener(this)
        binding.maxApps5.setOnClickListener(this)
        binding.maxApps6.setOnClickListener(this)
        binding.maxApps7.setOnClickListener(this)
        binding.maxApps8.setOnClickListener(this)

        binding.textSizeMinus.setOnClickListener(this)
        binding.textSizePlus.setOnClickListener(this)

        binding.dailyWallpaper.setOnLongClickListener(this)
        binding.alignment.setOnLongClickListener(this)
        binding.appThemeText.setOnLongClickListener(this)
        binding.swipeLeftApp.setOnLongClickListener(this)
        binding.swipeRightApp.setOnLongClickListener(this)
        binding.toggleLock.setOnLongClickListener(this)
    }

    private fun initObservers() {
        if (prefs.firstSettingsOpen) {
            viewModel.showDialog.postValue(Constants.Dialog.ABOUT)
            prefs.firstSettingsOpen = false
        }
        viewModel.isSukunDefault.observe(viewLifecycleOwner) {
            binding.setLauncher.text = getString(
                if (it) R.string.turn_off_sukun_launcher
                else R.string.set_as_default_launcher
            )
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
        binding.homeAppIcons?.text = getString(
            if (prefs.showHomeAppIcons) R.string.on else R.string.off
        )
    }

    private fun toggleDailyNotes() {
        prefs.showDailyNotesOnHome = !prefs.showDailyNotesOnHome
        populateDailyNotesSettings()
        viewModel.refreshHome(false)
    }

    private fun populateDailyNotesSettings() {
        binding.dailyNotesOnOff.text = getString(if (prefs.showDailyNotesOnHome) R.string.on else R.string.off)
        binding.dailyNotesOptionsLayout.isVisible = prefs.showDailyNotesOnHome
        binding.dailyNotesList.text = getDailyNotesSummary(prefs.dailyNotesList)
    }

    private fun showDailyNotesEditor() {
        if (!prefs.showDailyNotesOnHome) return
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4
            gravity = Gravity.TOP or Gravity.START
            setText(prefs.dailyNotesList)
            setSelection(text?.length ?: 0)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.daily_notes_list)
            .setMessage(R.string.daily_notes_hint)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                prefs.dailyNotesList = input.text?.toString()?.trim().orEmpty()
                populateDailyNotesSettings()
                viewModel.refreshHome(false)
                requireContext().showToast(R.string.daily_notes_saved)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun getDailyNotesSummary(rawNotes: String): String {
        val notes = rawNotes.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (notes.isEmpty()) return getString(R.string.not_set)
        return notes.first()
    }

    private fun populateFocusMode() {
        binding.focusMode.text = prefs.getFocusModeStatus(requireContext())
        if (prefs.isFocusModeActive()) {
            binding.focusModeSelectLayout?.visibility = View.GONE
            binding.focusCustomLayout.visibility = View.GONE
        }
    }

    private fun toggleFocusModeNotificationsLock() {
        prefs.focusModeLockNotifications = !prefs.focusModeLockNotifications
        populateFocusModeNotificationsLock()
    }

    private fun populateFocusModeNotificationsLock() {
        binding.focusModeNotificationsLock.text = getString(
            if (prefs.focusModeLockNotifications) R.string.on else R.string.off
        )
    }

    private fun startFocusMode(durationInMillis: Long) {
        if (!isAccessServiceEnabled(requireContext())) {
            requireContext().showToast(R.string.focus_mode_enable_accessibility, Toast.LENGTH_LONG)
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
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

    private fun toggleWeather() {
        prefs.showWeatherOnHome = !prefs.showWeatherOnHome
        populateWeatherSettings()
        if (prefs.showWeatherOnHome) {
            when (prefs.weatherSourceMode) {
                Constants.WeatherSource.DEVICE -> {
                    if (requireContext().hasWeatherLocationPermission()) {
                        refreshWeatherIfConfigured()
                    } else {
                        selectDeviceWeatherSource()
                    }
                }

                else -> {
                    if (prefs.weatherLocationQuery.isBlank()) {
                        requireContext().showToast(R.string.weather_location_required)
                    } else {
                        refreshWeatherIfConfigured()
                    }
                }
            }
        } else {
            refreshWeatherIfConfigured()
        }
        viewModel.refreshHome(false)
    }

    private fun populateWeatherSettings() {
        binding.weatherOnOff.text = getString(if (prefs.showWeatherOnHome) R.string.on else R.string.off)
        binding.weatherOptionsLayout.isVisible = prefs.showWeatherOnHome
        binding.weatherSource.text = getString(
            when (prefs.weatherSourceMode) {
                Constants.WeatherSource.DEVICE -> R.string.device_location
                Constants.WeatherSource.GOOGLE -> R.string.google_weather
                else -> R.string.manual_location
            }
        )
        binding.weatherUnits.text = getString(
            if (prefs.weatherUnits == Constants.WeatherUnit.FAHRENHEIT)
                R.string.fahrenheit_short
            else
                R.string.celsius_short
        )
        binding.weatherLocationRow.isVisible = prefs.showWeatherOnHome &&
                prefs.weatherSourceMode != Constants.WeatherSource.GOOGLE
        if (prefs.weatherSourceMode != Constants.WeatherSource.MANUAL || !prefs.showWeatherOnHome) {
            binding.weatherLocationEditLayout?.visibility = View.GONE
        }
        binding.weatherLocation.text = when {
            prefs.weatherSourceMode == Constants.WeatherSource.DEVICE ->
                prefs.weatherLocationLabel.ifBlank { getString(R.string.current_location) }
            prefs.weatherLocationQuery.isBlank() -> getString(R.string.not_set)
            prefs.weatherLocationLabel.isNotBlank() -> prefs.weatherLocationLabel
            else -> prefs.weatherLocationQuery
        }
    }

    private fun updateWeatherUnits(units: String) {
        if (prefs.weatherUnits == units) return
        prefs.weatherUnits = units
        prefs.clearWeatherCache()
        populateWeatherSettings()
        refreshWeatherIfConfigured()
    }

    private fun updateWeatherSource(sourceMode: String) {
        if (prefs.weatherSourceMode == sourceMode) return
        prefs.weatherSourceMode = sourceMode
        prefs.clearWeatherCache()
        populateWeatherSettings()
        refreshWeatherIfConfigured()
    }

    private fun selectDeviceWeatherSource() {
        if (requireContext().hasWeatherLocationPermission()) {
            updateWeatherSource(Constants.WeatherSource.DEVICE)
            return
        }
        weatherLocationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun showWeatherLocationEditor() {
        when (prefs.weatherSourceMode) {
            Constants.WeatherSource.DEVICE -> {
                if (!requireContext().hasWeatherLocationPermission()) {
                    weatherLocationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                } else if (!requireContext().isLocationServicesEnabled()) {
                    requireContext().showToast(R.string.location_services_disabled)
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            }
            Constants.WeatherSource.MANUAL -> {
                binding.weatherLocationEditLayout?.visibility = View.VISIBLE
                binding.etWeatherLocation?.setText(prefs.weatherLocationQuery)
                binding.etWeatherLocation?.showKeyboard()
            }
            else -> return
        }
    }

    private fun saveWeatherLocation() {
        val weatherLocationQuery = binding.etWeatherLocation?.text?.toString()?.trim().orEmpty()
        if (weatherLocationQuery.isBlank()) {
            requireContext().showToast(R.string.weather_location_required)
            binding.etWeatherLocation?.showKeyboard()
            return
        }
        prefs.weatherLocationQuery = weatherLocationQuery
        prefs.weatherLocationLabel = weatherLocationQuery
        prefs.weatherLatitude = ""
        prefs.weatherLongitude = ""
        prefs.clearWeatherCache()
        binding.weatherLocationEditLayout?.visibility = View.GONE
        binding.etWeatherLocation?.hideKeyboard()
        populateWeatherSettings()
        refreshWeatherIfConfigured()
        requireContext().showToast(R.string.weather_saved)
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

        if (prefs.weatherSourceMode == Constants.WeatherSource.MANUAL && prefs.weatherLocationQuery.isBlank()) {
            prefs.weatherSourceMode = Constants.WeatherSource.DEVICE
            populateWeatherSettings()
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

    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun togglePrayer() {
        prefs.showPrayerOnHome = !prefs.showPrayerOnHome
        populatePrayerSettings()
        if (prefs.showPrayerOnHome) {
            requestNotificationPermissionIfNeeded()
            when (prefs.prayerSourceMode) {
                Constants.PrayerSource.DEVICE -> {
                    if (requireContext().hasWeatherLocationPermission()) {
                        refreshPrayerIfConfigured(forceLocationRefresh = true, promptForAlarmPermission = true)
                    } else {
                        selectDevicePrayerSource()
                    }
                }

                else -> {
                    if (prefs.prayerLocationQuery.isBlank()) {
                        requireContext().showToast(R.string.prayer_location_required)
                    } else {
                        refreshPrayerIfConfigured(promptForAlarmPermission = true)
                    }
                }
            }
        } else {
            refreshPrayerIfConfigured()
        }
        viewModel.refreshHome(false)
    }

    private fun populatePrayerSettings() {
        binding.prayerOnOff?.text = getString(if (prefs.showPrayerOnHome) R.string.on else R.string.off)
        binding.prayerOptionsLayout?.isVisible = prefs.showPrayerOnHome
        binding.prayerSource?.text = getString(
            if (prefs.prayerSourceMode == Constants.PrayerSource.DEVICE) {
                R.string.device_location
            } else {
                R.string.manual_location
            }
        )
        binding.prayerLocationRow?.isVisible = prefs.showPrayerOnHome
        if (prefs.prayerSourceMode != Constants.PrayerSource.MANUAL || !prefs.showPrayerOnHome) {
            binding.prayerLocationEditLayout?.visibility = View.GONE
        }
        binding.prayerLocation?.text = when {
            prefs.prayerSourceMode == Constants.PrayerSource.DEVICE ->
                prefs.prayerLocationLabel.ifBlank { getString(R.string.current_location) }
            prefs.prayerLocationQuery.isBlank() -> getString(R.string.not_set)
            prefs.prayerLocationLabel.isNotBlank() -> prefs.prayerLocationLabel
            else -> prefs.prayerLocationQuery
        }
        binding.azanOnOff?.text = getString(if (prefs.azanEnabled) R.string.on else R.string.off)
        binding.azanSound?.text = getString(
            when (prefs.azanSound) {
                Constants.AzanSound.MARYLEBONE -> R.string.azan_sound_marylebone
                Constants.AzanSound.CUSTOM -> R.string.custom
                else -> R.string.azan_sound_makkah
            }
        )
        binding.customAzanRow?.isVisible = prefs.showPrayerOnHome && prefs.azanSound == Constants.AzanSound.CUSTOM
        binding.customAzanFile?.text = if (prefs.azanCustomUri.isBlank()) {
            getString(R.string.not_set)
        } else {
            getString(R.string.change)
        }
    }

    private fun updatePrayerSource(sourceMode: String) {
        if (prefs.prayerSourceMode == sourceMode) return
        prefs.prayerSourceMode = sourceMode
        prefs.clearPrayerCache()
        populatePrayerSettings()
        refreshPrayerIfConfigured(forceLocationRefresh = sourceMode == Constants.PrayerSource.DEVICE)
    }

    private fun selectDevicePrayerSource() {
        if (requireContext().hasWeatherLocationPermission()) {
            updatePrayerSource(Constants.PrayerSource.DEVICE)
            return
        }
        prayerLocationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun showPrayerLocationEditor() {
        if (prefs.prayerSourceMode != Constants.PrayerSource.MANUAL) return
        binding.prayerLocationEditLayout?.visibility = View.VISIBLE
        binding.etPrayerLocation?.setText(prefs.prayerLocationQuery)
        binding.etPrayerLocation?.showKeyboard()
    }

    private fun savePrayerLocation() {
        val prayerLocationQuery = binding.etPrayerLocation?.text?.toString()?.trim().orEmpty()
        if (prayerLocationQuery.isBlank()) {
            requireContext().showToast(R.string.prayer_location_required)
            binding.etPrayerLocation?.showKeyboard()
            return
        }
        prefs.prayerLocationQuery = prayerLocationQuery
        prefs.prayerLocationLabel = prayerLocationQuery
        prefs.prayerLatitude = ""
        prefs.prayerLongitude = ""
        prefs.clearPrayerCache()
        binding.prayerLocationEditLayout?.visibility = View.GONE
        binding.etPrayerLocation?.hideKeyboard()
        populatePrayerSettings()
        refreshPrayerIfConfigured()
        requireContext().showToast(R.string.prayer_saved)
    }

    private fun toggleAzan() {
        prefs.azanEnabled = !prefs.azanEnabled
        populatePrayerSettings()
        if (prefs.azanEnabled) {
            ensureExactAlarmPermissionIfNeeded()
        }
        refreshPrayerIfConfigured(promptForAlarmPermission = true)
    }

    private fun updateAzanSound(sound: String) {
        if (prefs.azanSound == sound && sound != Constants.AzanSound.CUSTOM) return
        prefs.azanSound = sound
        populatePrayerSettings()
        refreshPrayerIfConfigured()
    }

    private fun showCustomAzanPicker() {
        if (!prefs.showPrayerOnHome) return
        customAzanPickerLauncher.launch(arrayOf("audio/*"))
    }

    private fun refreshPrayerIfConfigured(
        forceLocationRefresh: Boolean = false,
        promptForAlarmPermission: Boolean = false,
    ) {
        if (!prefs.showPrayerOnHome) {
            viewModel.cancelPrayerReminder(clearCachedPrayer = true)
            return
        }

        val canRefreshPrayer = when (prefs.prayerSourceMode) {
            Constants.PrayerSource.DEVICE -> requireContext().hasWeatherLocationPermission()
            else -> prefs.prayerLocationQuery.isNotBlank()
        }

        if (canRefreshPrayer) {
            if (promptForAlarmPermission) {
                ensureExactAlarmPermissionIfNeeded()
            }
            viewModel.refreshPrayerData(forceLocationRefresh = forceLocationRefresh)
        } else {
            viewModel.cancelPrayerReminder(clearCachedPrayer = true)
        }
    }

    private fun ensureExactAlarmPermissionIfNeeded(): Boolean {
        if (PrayerReminderScheduler.canScheduleExactPrayerReminders(requireContext())) {
            return true
        }
        requireContext().showToast(R.string.prayer_exact_alarm_permission_needed, Toast.LENGTH_LONG)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${requireContext().packageName}")
                )
            )
        }
        return false
    }

    private fun populateStatusBar() {
        if (prefs.showStatusBar) {
            showStatusBar()
            binding.statusBar.text = getString(R.string.on)
        } else {
            hideStatusBar()
            binding.statusBar.text = getString(R.string.off)
        }
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
        binding.clockStyle.text = getString(
            if (prefs.clockStyle == Constants.ClockStyle.DAY_RING) R.string.clock_style_day_ring
            else R.string.clock_style_standard
        )
        binding.dayStartHour.text = formatHourLabel(prefs.dayStartHour)
        binding.dayEndHour.text = formatHourLabel(prefs.dayEndHour)
        val showDayRingHours = prefs.dateTimeVisibility == Constants.DateTime.ON
                && prefs.clockStyle == Constants.ClockStyle.DAY_RING
        binding.dayStartHourRow.isVisible = showDayRingHours
        binding.dayEndHourRow.isVisible = showDayRingHours
    }

    private fun showClockStylePicker() {
        val items = arrayOf(
            getString(R.string.clock_style_standard),
            getString(R.string.clock_style_day_ring)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.clock_style)
            .setItems(items) { _, which ->
                val selectedStyle = if (which == 1) Constants.ClockStyle.DAY_RING else Constants.ClockStyle.STANDARD
                if (prefs.clockStyle == selectedStyle) return@setItems
                prefs.clockStyle = selectedStyle
                populateDateTime()
                viewModel.toggleDateTime()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDayHourEditor(isStartHour: Boolean) {
        val currentValue = if (isStartHour) prefs.dayStartHour else prefs.dayEndHour
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.clock_hour_hint)
            setText(currentValue.toString())
            setSelection(text?.length ?: 0)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(if (isStartHour) R.string.day_start_hour_title else R.string.day_end_hour_title)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val enteredHour = input.text?.toString()?.trim()?.toIntOrNull()
                if (!isValidDayHour(isStartHour, enteredHour)) {
                    requireContext().showToast(
                        if (isStartHour) R.string.day_start_hour_error else R.string.day_end_hour_error
                    )
                    return@setPositiveButton
                }
                if (isStartHour) prefs.dayStartHour = enteredHour!!
                else prefs.dayEndHour = enteredHour!!
                populateDateTime()
                viewModel.toggleDateTime()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun isValidDayHour(isStartHour: Boolean, value: Int?): Boolean {
        if (value == null) return false
        return if (isStartHour) {
            value in 0..23 && value < prefs.dayEndHour
        } else {
            value in 1..24 && value > prefs.dayStartHour
        }
    }

    private fun formatHourLabel(hour: Int): String {
        return getString(R.string.clock_hour_format, hour)
    }

    private fun showStatusBar() {
        val decorView = activity?.window?.decorView ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            decorView.windowInsetsController?.show(WindowInsets.Type.statusBars())
        else
            @Suppress("DEPRECATION", "InlinedApi")
            decorView.apply {
                systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            }
    }

    private fun hideStatusBar() {
        val decorView = activity?.window?.decorView ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            decorView.windowInsetsController?.hide(WindowInsets.Type.statusBars())
        else {
            @Suppress("DEPRECATION")
            decorView.apply {
                systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_FULLSCREEN
            }
        }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            binding.notWorking.visibility = View.VISIBLE
        if (isAccessServiceEnabled(requireContext()))
            binding.actionAccessibility.text = getString(R.string.disable)
        binding.accessibilityLayout.isVisible = show
        binding.scrollView.animateAlpha(if (show) 0.5f else 1f)
    }

    private fun openAccessibilityService() {
        toggleAccessibilityVisibility(false)
        // prefs.lockModeOn = true
        populateLockSettings()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun toggleLockMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (!prefs.lockModeOn && !isAccessServiceEnabled(requireContext())) {
                toggleAccessibilityVisibility(true)
                return
            }
            prefs.lockModeOn = !prefs.lockModeOn
        } else {
            val isAdmin: Boolean = deviceManager.isAdminActive(componentName)
            if (isAdmin) {
                removeActiveAdmin("Admin permission removed.")
                prefs.lockModeOn = false
            } else {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                intent.putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    getString(R.string.admin_permission_message)
                )
                requireActivity().startActivityForResult(intent, Constants.REQUEST_CODE_ENABLE_ADMIN)
            }
        }
        populateLockSettings()
    }

    private fun removeActiveAdmin(toastMessage: String? = null) {
        try {
            deviceManager.removeActiveAdmin(componentName) // for backward compatibility
            requireContext().showToast(toastMessage)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun confirmTurnOffSukunLauncher() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.turn_off_sukun_launcher)
            .setMessage(R.string.turn_off_sukun_confirmation)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.turn_off) { _, _ ->
                requireContext().showToast(R.string.turn_off_sukun_hint, Toast.LENGTH_LONG)
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
        if (prefs.dailyWallpaper.not() && prefs.appTheme == AppCompatDelegate.MODE_NIGHT_YES && viewModel.isSukunDefault.value == false) {
            requireContext().showToast(R.string.set_as_default_launcher_first)
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
        if (isSukunDefault(requireContext()))
            requireContext().showToast(getString(R.string.your_wallpaper_will_update_shortly))
        else
            requireContext().showToast(getString(R.string.sukun_is_not_default_launcher), Toast.LENGTH_LONG)
    }

    private fun changeWallpaperNow() {
        prefs.dailyWallpaper = true
        populateWallpaperText()
        setPlainWallpaper(requireContext(), android.R.color.black)
        viewModel.refreshWallpaperNow()
        requireContext().showToast(R.string.your_wallpaper_will_update_shortly)
    }

    private fun updateHomeAppsNum(num: Int) {
        binding.homeAppsNum.text = num.toString()
        binding.appsNumSelectLayout.visibility = View.GONE
        prefs.homeAppsNum = num
        viewModel.refreshHome(true)
    }

    private var pendingTextSizeScale: Float = -1f

    private fun adjustTextSizePreview(delta: Float) {
        val maxScale = if (isTablet(requireContext())) 2.0f else 1.5f
        val current = if (pendingTextSizeScale > 0) pendingTextSizeScale else prefs.textSizeScale
        val newScale = Math.round((current + delta) * 10f) / 10f
        val clamped = newScale.coerceIn(0.5f, maxScale)
        if (clamped == current) return
        pendingTextSizeScale = clamped
        val formatted = String.format("%.1f", clamped)
        binding.textSizeValue.text = formatted
        binding.textSizeCurrent.text = formatted
    }

    private fun applyTextSizeScale() {
        if (pendingTextSizeScale < 0 || prefs.textSizeScale == pendingTextSizeScale) {
            pendingTextSizeScale = -1f
            return
        }
        prefs.textSizeScale = pendingTextSizeScale
        pendingTextSizeScale = -1f
        requireActivity().recreate()
    }

    private fun toggleKeyboardText() {
        if (prefs.autoShowKeyboard && prefs.keyboardMessageShown.not()) {
            viewModel.showDialog.postValue(Constants.Dialog.KEYBOARD)
            prefs.keyboardMessageShown = true
        } else {
            prefs.autoShowKeyboard = !prefs.autoShowKeyboard
            populateKeyboardText()
        }
    }

    private fun updateTheme(appTheme: Int) {
        if (AppCompatDelegate.getDefaultNightMode() == appTheme) return
        prefs.appTheme = appTheme
        populateAppThemeText(appTheme)
        setAppTheme(appTheme)
    }

    private fun setAppTheme(theme: Int) {
        if (AppCompatDelegate.getDefaultNightMode() == theme) return
        if (prefs.dailyWallpaper) {
            setPlainWallpaper(theme)
            viewModel.setWallpaperWorker()
        }
        requireActivity().recreate()
    }

    private fun setPlainWallpaper(appTheme: Int) {
        when (appTheme) {
            AppCompatDelegate.MODE_NIGHT_YES -> setPlainWallpaper(requireContext(), android.R.color.black)
            AppCompatDelegate.MODE_NIGHT_NO -> setPlainWallpaper(requireContext(), android.R.color.black)
            else -> {
                setPlainWallpaper(requireContext(), android.R.color.black)
            }
        }
    }

    private fun populateAppThemeText(appTheme: Int = prefs.appTheme) {
        when (appTheme) {
            AppCompatDelegate.MODE_NIGHT_YES -> binding.appThemeText.text = getString(R.string.dark)
            AppCompatDelegate.MODE_NIGHT_NO -> binding.appThemeText.text = getString(R.string.light)
            else -> binding.appThemeText.text = getString(R.string.system_default)
        }
    }

    private fun populateTextSize() {
        val formatted = String.format("%.1f", prefs.textSizeScale)
        binding.textSizeValue.text = formatted
        binding.textSizeCurrent.text = formatted
    }

    private fun populateScreenTimeOnOff() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (requireContext().appUsagePermissionGranted()) binding.screenTimeOnOff.text = getString(R.string.on)
            else binding.screenTimeOnOff.text = getString(R.string.off)
        } else binding.screenTimeLayout.visibility = View.GONE
    }

    private fun populateKeyboardText() {
        if (prefs.autoShowKeyboard) binding.autoShowKeyboard.text = getString(R.string.on)
        else binding.autoShowKeyboard.text = getString(R.string.off)
    }

    private fun toggleAppDrawerFastScroller() {
        prefs.appDrawerFastScroller = !prefs.appDrawerFastScroller
        populateAppDrawerFastScroller()
    }

    private fun populateAppDrawerFastScroller() {
        binding.appDrawerFastScroller.text = getString(
            if (prefs.appDrawerFastScroller) R.string.on else R.string.off
        )
    }

    private fun populateWallpaperText() {
        if (prefs.dailyWallpaper) binding.dailyWallpaper.text = getString(R.string.on)
        else binding.dailyWallpaper.text = getString(R.string.off)
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

    private fun toggleHomeButtonRecents() {
        if (!prefs.homeButtonShowRecents && !isAccessServiceEnabled(requireContext())) {
            toggleAccessibilityVisibility(true)
            return
        }
        prefs.homeButtonShowRecents = !prefs.homeButtonShowRecents
        populateHomeButtonRecents()
    }

    private fun populateHomeButtonRecents() {
        binding.homeButtonRecents.text = getString(
            if (prefs.homeButtonShowRecents && isAccessServiceEnabled(requireContext())) R.string.on
            else R.string.off
        )
    }

    private fun populateLockSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            binding.toggleLock.text = getString(
                if (prefs.lockModeOn && isAccessServiceEnabled(requireContext())) R.string.on
                else R.string.off
            )
        } else {
            binding.toggleLock.text = getString(
                if (prefs.lockModeOn) R.string.on
                else R.string.off
            )
        }
    }

    private fun populateSwipeDownAction() {
        binding.swipeDownAction.text = when (prefs.swipeDownAction) {
            Constants.SwipeDownAction.NOTIFICATIONS -> getString(R.string.notifications)
            else -> getString(R.string.search)
        }
    }

    private fun populateDoubleTapAction() {
        binding.doubleTapAction.text = when (prefs.doubleTapAction) {
            Constants.DoubleTapAction.FOCUS -> getString(R.string.focus)
            else -> getString(R.string.lock)
        }
    }

    private fun updateDoubleTapAction(action: String) {
        if (prefs.doubleTapAction == action) return
        prefs.doubleTapAction = action
        populateDoubleTapAction()
    }

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
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        viewModel.isSukunDefault()
        if (prefs.isFocusModeActive()) {
            populateFocusMode()
            if (findNavController().currentDestination?.id == R.id.settingsFragment) {
                findNavController().popBackStack(R.id.mainFragment, false)
                requireContext().showToast(R.string.focus_mode_blocked)
            }
            return
        }
        populateFocusMode()
        populateWeatherSettings()
        refreshWeatherIfConfigured()
        populatePrayerSettings()
        refreshPrayerIfConfigured(forceLocationRefresh = prefs.prayerSourceMode == Constants.PrayerSource.DEVICE)
    }

    private fun populateHourlyChime() {
        val enabled = prefs.hourlyChimeEnabled
        binding.hourlyChimeOnOff?.text = getString(if (enabled) R.string.on else R.string.off)
        binding.hourlyChimeTimeLayout?.isVisible = enabled
        binding.hourlyChimeStartHour?.text = formatHourLabel(prefs.hourlyChimeStartHour)
        binding.hourlyChimeEndHour?.text = formatHourLabel(prefs.hourlyChimeEndHour)
    }

    private fun toggleHourlyChime() {
        prefs.hourlyChimeEnabled = !prefs.hourlyChimeEnabled
        if (prefs.hourlyChimeEnabled) {
            HourlyChimeScheduler.scheduleNext(requireContext())
        } else {
            HourlyChimeScheduler.cancel(requireContext())
        }
        populateHourlyChime()
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

    override fun onDestroy() {
        viewModel.checkForMessages.call()
        super.onDestroy()
    }
}