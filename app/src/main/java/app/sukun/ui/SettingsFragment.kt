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
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import app.sukun.helper.applyLauncherStatusBarVisibility
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
import app.sukun.helper.LocaleHelper
import app.sukun.helper.PrayerReminderScheduler
import app.sukun.helper.ReminderScheduler
import app.sukun.helper.animateAlpha
import app.sukun.helper.appUsagePermissionGranted
import app.sukun.helper.getFocusModeStatus
import app.sukun.helper.AmbientThemeController
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
import app.sukun.helper.dpToPx
import app.sukun.listener.DeviceAdmin

class SettingsFragment : Fragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceManager: DevicePolicyManager
    private lateinit var componentName: ComponentName

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private var pendingScreenTimePermissionRequest = false

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
            requireContext().showToast(R.string.chime_sound_saved)
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
            if (!prefs.lockModeOn && prefs.doubleTapAction == Constants.DoubleTapAction.LOCK) {
                prefs.doubleTapAction = Constants.DoubleTapAction.OFF
            }

            binding.homeAppsNum.text = prefs.homeAppsNum.toString()
            migrateScreenTimePrefIfNeeded()
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
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                findNavController().popBackStack()
            } catch (_: Exception) {}
        }
    }

    override fun onClick(view: View) {
        binding.clockStyleSelectLayout?.visibility = View.GONE
        binding.appsNumSelectLayout.visibility = View.GONE
        binding.dateTimeSelectLayout.visibility = View.GONE
        binding.appThemeSelectLayout.visibility = View.GONE
        binding.swipeDownSelectLayout.visibility = View.GONE
        binding.focusModeSelectLayout?.visibility = View.GONE
        binding.doubleTapActionSelectLayout.visibility = View.GONE
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
        if (view.id != R.id.alignmentBottom)
            binding.alignmentSelectLayout.visibility = View.GONE

        when (view.id) {
            R.id.sukunHiddenApps -> showHiddenApps()
            R.id.screenTimeOnOff -> toggleScreenTime()
            R.id.appInfo -> openAppInfo(requireContext(), Process.myUserHandle(), BuildConfig.APPLICATION_ID)
            R.id.setLauncher -> {
                if (viewModel.isSukunDefault.value == true) {
                    confirmTurnOffSukunLauncher()
                } else {
                    viewModel.resetLauncherLiveData.call()
                }
            }
            R.id.homeAppsNum -> binding.appsNumSelectLayout.visibility = View.VISIBLE
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
            R.id.weatherSettings -> showWeatherSettingsSheet()
            R.id.prayerSettings -> showPrayerSettingsSheet()
            R.id.remindersSettingsHeader -> {
                val isVisible = binding.remindersSubMenuLayout?.visibility == View.VISIBLE
                binding.remindersSubMenuLayout?.visibility = if (isVisible) View.GONE else View.VISIBLE
                binding.remindersSettingsToggle?.text = getString(if (isVisible) R.string.manage else R.string.close)
            }
            R.id.remindersOnOff -> toggleReminders()
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
            R.id.focusModeHideStatusBar -> toggleFocusModeHideStatusBar()
            R.id.focusCustomClose -> {
                binding.focusCustomLayout.visibility = View.GONE
                binding.etFocusCustomMinutes.hideKeyboard()
            }
            R.id.remindersManage -> findNavController().navigate(R.id.action_settingsFragment_to_remindersFragment)
            R.id.hourlyChimeOnOff -> toggleHourlyChime()
            R.id.hourlyChimeStartHour -> showHourPicker(isStart = true)
            R.id.hourlyChimeEndHour -> showHourPicker(isStart = false)
            R.id.hourlyChimeSound -> binding.chimeSoundSelectLayout?.visibility = View.VISIBLE
            R.id.chimeSoundBundled -> updateChimeSound(Constants.ChimeSound.BUNDLED)
            R.id.chimeSoundDefault -> updateChimeSound(Constants.ChimeSound.DEFAULT)
            R.id.chimeSoundCustom -> customChimePickerLauncher.launch(arrayOf("audio/*"))
            R.id.statusBar -> toggleStatusBar()
            R.id.dateTime -> binding.dateTimeSelectLayout.visibility = View.VISIBLE
            R.id.dateTimeOn -> toggleDateTime(Constants.DateTime.ON)
            R.id.dateTimeOff -> toggleDateTime(Constants.DateTime.OFF)
            R.id.dateOnly -> toggleDateTime(Constants.DateTime.DATE_ONLY)
            R.id.clockStyle -> binding.clockStyleSelectLayout?.visibility = View.VISIBLE
            R.id.clockStyleStandard -> selectClockStyle(Constants.ClockStyle.STANDARD)
            R.id.clockStyleDayRing -> selectClockStyle(Constants.ClockStyle.DAY_RING)
            R.id.dayStartHour -> showDayHourEditor(isStartHour = true)
            R.id.dayEndHour -> showDayHourEditor(isStartHour = false)
            R.id.appThemeText -> binding.appThemeSelectLayout.visibility = View.VISIBLE
            R.id.themeLight -> updateTheme(AppCompatDelegate.MODE_NIGHT_NO)
            R.id.themeDark -> updateTheme(AppCompatDelegate.MODE_NIGHT_YES)
            R.id.themeSystem -> updateTheme(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            R.id.themeAmbient -> updateTheme(Constants.THEME_MODE_AMBIENT_LIGHT)
            R.id.appLanguageText -> findNavController().navigate(R.id.action_settingsFragment_to_languageFragment)
            R.id.textSizeValue -> binding.textSizesLayout.visibility = View.VISIBLE
            R.id.actionAccessibility -> openAccessibilityService()
            R.id.closeAccessibility -> toggleAccessibilityVisibility(false)
            R.id.notWorking -> {
                if (Constants.URL_DOUBLE_TAP.isBlank()) requireContext().showToast(R.string.not_set)
                else requireContext().openUrl(Constants.URL_DOUBLE_TAP)
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
            R.id.doubleTapAction -> binding.doubleTapActionSelectLayout.visibility = View.VISIBLE
            R.id.doubleTapOff -> selectDoubleTapMode(Constants.DoubleTapAction.OFF)
            R.id.doubleTapLock -> selectDoubleTapMode(Constants.DoubleTapAction.LOCK)
            R.id.doubleTapFocus -> selectDoubleTapMode(Constants.DoubleTapAction.FOCUS)

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
            }

            R.id.swipeLeftApp -> toggleSwipeLeft()
            R.id.swipeRightApp -> toggleSwipeRight()
            R.id.doubleTapAction -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        return true
    }

    private fun initClickListeners() {
        binding.sukunHiddenApps.setOnClickListener(this)
        binding.scrollLayout.setOnClickListener(this)
        binding.appInfo.setOnClickListener(this)
        binding.setLauncher.setOnClickListener(this)
        binding.homeAppsNum.setOnClickListener(this)
        binding.remindersSettingsHeader?.setOnClickListener(this)
        binding.remindersOnOff?.setOnClickListener(this)
        binding.remindersManage?.setOnClickListener(this)
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
        binding.prayerSettings?.setOnClickListener(this)
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
        binding.hourlyChimeStartHour?.setOnClickListener(this)
        binding.hourlyChimeEndHour?.setOnClickListener(this)
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
        binding.dayStartHour.setOnClickListener(this)
        binding.dayEndHour.setOnClickListener(this)
        binding.swipeLeftApp.setOnClickListener(this)
        binding.swipeRightApp.setOnClickListener(this)
        binding.swipeDownAction.setOnClickListener(this)
        binding.search.setOnClickListener(this)
        binding.notifications.setOnClickListener(this)
        binding.doubleTapAction.setOnClickListener(this)
        binding.doubleTapOff?.setOnClickListener(this)
        binding.doubleTapLock.setOnClickListener(this)
        binding.doubleTapFocus.setOnClickListener(this)
        binding.appThemeText.setOnClickListener(this)
        binding.themeLight.setOnClickListener(this)
        binding.themeDark.setOnClickListener(this)
        binding.themeSystem.setOnClickListener(this)
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

        binding.alignment.setOnLongClickListener(this)
        binding.appThemeText.setOnLongClickListener(this)
        binding.swipeLeftApp.setOnLongClickListener(this)
        binding.swipeRightApp.setOnLongClickListener(this)
        binding.doubleTapAction.setOnLongClickListener(this)
    }

    private fun initObservers() {
        val showWelcomeDialog = prefs.firstSettingsOpen
        if (showWelcomeDialog) {
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
        if (showWelcomeDialog) {
            view?.post {
                if (isAdded) {
                    viewModel.showDialog.postValue(Constants.Dialog.ABOUT)
                }
            }
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

    private fun toggleTodo() {
        prefs.showTodoOnHome = !prefs.showTodoOnHome
        populateTodoSettings()
        viewModel.refreshHome(false)
    }

    private fun populateTodoSettings() {
        binding.todoOnOff?.text = getString(if (prefs.showTodoOnHome) R.string.on else R.string.off)
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
        binding.remindersOnOff?.text = getString(if (prefs.showRemindersOnHome) R.string.on else R.string.off)
        binding.remindersOptionsLayout?.isVisible = prefs.showRemindersOnHome
    }

    private fun populatePremiumStatus() {
        binding.premiumRow.isVisible = !prefs.isProUser
        if (!prefs.isProUser) {
            binding.goPremium.text = getString(R.string.upgrade_to_premium)
            binding.goPremium.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
        }
    }

    private fun showUpgradeDialog() {
        if (prefs.isProUser) {
            requireContext().showToast(R.string.premium_already_active)
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.go_premium)
            .setMessage(R.string.premium_feature_requires_upgrade)
            .setPositiveButton(R.string.upgrade_to_premium) { _, _ ->
                prefs.unlockPremium()
                populatePremiumStatus()
                requireContext().showToast(R.string.premium_enabled)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun populateLanguage() {
        val selectedLanguage = LocaleHelper.getSelectedLanguage(requireContext())
        binding.appLanguageText?.text = selectedLanguage.listLabel()
    }

    private fun canUsePremiumFeature(): Boolean {
        if (prefs.isProUser) return true
        requireContext().showToast(R.string.premium_feature_requires_upgrade)
        return false
    }

    private fun populateFocusMode() {
        binding.focusMode.text = prefs.getFocusModeStatus(requireContext())
        if (prefs.isFocusModeActive()) {
            binding.focusModeSelectLayout?.visibility = View.GONE
            binding.focusCustomLayout.visibility = View.GONE
        }
        populateFocusModeHideStatusBar()
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
        binding.focusModeNotificationsLock.text = getString(
            if (prefs.focusModeLockNotifications) R.string.on else R.string.off
        )
    }

    private fun populateFocusModeHideStatusBar() {
        binding.focusModeHideStatusBar.text = getString(
            if (prefs.focusModeHideStatusBar) R.string.on else R.string.off
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

    private fun populateWeatherSettings() {
        binding.weatherSettingsSummary?.text = if (prefs.showWeatherOnHome) buildWeatherSummary() else getString(R.string.off)
    }

    private fun buildWeatherSummary(): String {
        val source = getString(
            when (prefs.weatherSourceMode) {
                Constants.WeatherSource.DEVICE -> R.string.device_location
                Constants.WeatherSource.GOOGLE -> R.string.google_weather
                else -> R.string.manual_location
            }
        )
        val units = getString(
            if (prefs.weatherUnits == Constants.WeatherUnit.FAHRENHEIT)
                R.string.fahrenheit_short
            else
                R.string.celsius_short
        )
        return "$source · $units"
    }

    private fun showWeatherSettingsSheet() {
        WeatherSettingsSheet.newInstance().also { sheet ->
            sheet.setListener(object : WeatherSettingsSheet.Listener {
                override fun onWeatherSettingsChanged() {
                    populateWeatherSettings()
                    populatePrayerSettings()
                    viewModel.refreshHome(false)
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

    private fun populatePrayerSettings() {
        binding.prayerSettingsSummary?.text =
            if (prefs.showPrayerOnHome) buildPrayerSummary() else getString(R.string.off)
    }

    private fun buildPrayerSummary(): String {
        val source = getString(
            if (prefs.prayerSourceMode == Constants.PrayerSource.DEVICE)
                R.string.device_location
            else
                R.string.manual_location
        )
        val azan = when (prefs.azanSound) {
            Constants.AzanSound.OFF -> null
            Constants.AzanSound.MARYLEBONE -> getString(R.string.azan_sound_marylebone)
            Constants.AzanSound.CUSTOM -> getString(R.string.custom)
            else -> getString(R.string.azan_sound_makkah)
        }
        return if (azan != null) "$source · $azan" else source
    }

    private fun showPrayerSettingsSheet() {
        PrayerSettingsSheet.newInstance().also { sheet ->
            sheet.setListener(object : PrayerSettingsSheet.Listener {
                override fun onPrayerSettingsChanged() {
                    populatePrayerSettings()
                    populateWeatherSettings()
                    viewModel.refreshHome(false)
                }
            })
            sheet.show(childFragmentManager, PrayerSettingsSheet.TAG)
        }
    }

    private fun populateStatusBar() {
        val activity = activity ?: return
        if (prefs.showStatusBar) {
            applyLauncherStatusBarVisibility(activity, show = true)
            binding.statusBar.text = getString(R.string.on)
        } else {
            applyLauncherStatusBarVisibility(activity, show = false)
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

    private fun selectClockStyle(selectedStyle: String) {
        binding.clockStyleSelectLayout?.visibility = View.GONE
        if (prefs.clockStyle == selectedStyle) return
        prefs.clockStyle = selectedStyle
        populateDateTime()
        viewModel.toggleDateTime()
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
        populateDoubleTapAction()
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
        if (!prefs.dailyWallpaper && !canUsePremiumFeature()) return
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
        if (!canUsePremiumFeature()) return
        prefs.dailyWallpaper = true
        populateWallpaperText()
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
        if (isAdded) {
            requireActivity().recreate()
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
            if (!canUsePremiumFeature()) {
                showUpgradeDialog()
                return
            }
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
            requireActivity().recreate()
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

    private fun populateAppThemeText(appTheme: Int = prefs.appTheme) {
        binding.themeAmbientLabel.text = getString(R.string.theme_mode_ambient)
        binding.themeAmbientStar.isVisible = true
        binding.appThemeText.text = when (appTheme) {
            AppCompatDelegate.MODE_NIGHT_YES -> getString(R.string.dark)
            AppCompatDelegate.MODE_NIGHT_NO -> getString(R.string.light)
            Constants.THEME_MODE_AMBIENT_LIGHT -> getString(R.string.theme_mode_ambient)
            else -> getString(R.string.system_default)
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
        prefs.showScreenTimeOnHome = requireContext().appUsagePermissionGranted()
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
            binding.screenTimeOnOff.text = getString(
                if (prefs.showScreenTimeOnHome) R.string.on else R.string.off
            )
        } else binding.screenTimeLayout.visibility = View.GONE
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

    private fun populateSwipeDownAction() {
        binding.swipeDownAction.text = when (prefs.swipeDownAction) {
            Constants.SwipeDownAction.NOTIFICATIONS -> getString(R.string.notifications)
            else -> getString(R.string.search)
        }
    }

    private fun populateDoubleTapAction() {
        binding.doubleTapAction.text = when (prefs.doubleTapAction) {
            Constants.DoubleTapAction.OFF -> getString(R.string.off)
            Constants.DoubleTapAction.FOCUS -> getString(R.string.focus)
            else -> getString(R.string.lock)
        }
    }

    private fun selectDoubleTapMode(mode: String) {
        if (prefs.doubleTapAction == mode) return
        when (mode) {
            Constants.DoubleTapAction.LOCK -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    if (!isAccessServiceEnabled(requireContext())) {
                        toggleAccessibilityVisibility(true)
                        return
                    }
                    prefs.lockModeOn = true
                } else {
                    val isAdmin = deviceManager.isAdminActive(componentName)
                    if (!isAdmin) {
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_permission_message))
                        requireActivity().startActivityForResult(intent, Constants.REQUEST_CODE_ENABLE_ADMIN)
                        return
                    }
                    prefs.lockModeOn = true
                }
                prefs.doubleTapAction = Constants.DoubleTapAction.LOCK
            }
            else -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) removeActiveAdmin()
                prefs.lockModeOn = false
                prefs.doubleTapAction = mode
            }
        }
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
        populateWeatherSettings()
        refreshWeatherIfConfigured()
        populatePrayerSettings()
        populateRemindersSettings()
        if (prefs.showPrayerOnHome) {
            viewModel.refreshPrayerData(forceLocationRefresh = prefs.prayerSourceMode == Constants.PrayerSource.DEVICE)
        }
    }

    private fun populateHourlyChime() {
        val enabled = prefs.hourlyChimeEnabled
        binding.hourlyChimeOnOff?.text = getString(if (enabled) R.string.on else R.string.off)
        binding.hourlyChimeTimeLayout?.isVisible = enabled
        binding.hourlyChimeSoundLayout?.isVisible = enabled
        binding.chimeSoundSelectLayout?.visibility = View.GONE
        binding.hourlyChimeStartHour?.text = formatHourLabel(prefs.hourlyChimeStartHour)
        binding.hourlyChimeEndHour?.text = formatHourLabel(prefs.hourlyChimeEndHour)
        binding.hourlyChimeSound?.text = getString(
            when (prefs.hourlyChimeSound) {
                Constants.ChimeSound.DEFAULT -> R.string.chime_sound_default
                Constants.ChimeSound.CUSTOM -> R.string.custom
                else -> R.string.chime_sound_bundled
            }
        )
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

    private fun updateChimeSound(sound: String) {
        prefs.hourlyChimeSound = sound
        binding.chimeSoundSelectLayout?.visibility = View.GONE
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
        if (::viewModel.isInitialized) {
            viewModel.checkForMessages.call()
        }
        super.onDestroy()
    }
}

