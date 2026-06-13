package sukun.minimalist.app.launcher.com.ui

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.ColorDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import sukun.minimalist.app.launcher.com.MainViewModel
import sukun.minimalist.app.launcher.com.data.OnboardingAction
import sukun.minimalist.app.launcher.com.R
import sukun.minimalist.app.launcher.com.data.AppModel
import sukun.minimalist.app.launcher.com.data.Constants
import sukun.minimalist.app.launcher.com.data.Prefs
import sukun.minimalist.app.launcher.com.data.PrayerState
import sukun.minimalist.app.launcher.com.data.WeatherData
import sukun.minimalist.app.launcher.com.data.TodoItem
import sukun.minimalist.app.launcher.com.data.toReminderList
import sukun.minimalist.app.launcher.com.data.toTodoJson
import sukun.minimalist.app.launcher.com.data.toTodoList
import sukun.minimalist.app.launcher.com.databinding.FragmentHomeBinding
import sukun.minimalist.app.launcher.com.helper.appUsagePermissionGranted
import sukun.minimalist.app.launcher.com.helper.canOpenNotificationsInFocusMode
import sukun.minimalist.app.launcher.com.helper.dpToPx
import sukun.minimalist.app.launcher.com.helper.applyLauncherStatusBarVisibility
import sukun.minimalist.app.launcher.com.helper.expandNotificationDrawer
import sukun.minimalist.app.launcher.com.helper.getFocusModeStatus
import sukun.minimalist.app.launcher.com.helper.getChangedAppTheme
import sukun.minimalist.app.launcher.com.helper.getColorFromAttr
import sukun.minimalist.app.launcher.com.helper.isDarkThemeOn
import sukun.minimalist.app.launcher.com.helper.getUserHandleFromString
import sukun.minimalist.app.launcher.com.MainActivity
import sukun.minimalist.app.launcher.com.helper.PremiumAccess
import sukun.minimalist.app.launcher.com.helper.isAccessServiceEnabled
import sukun.minimalist.app.launcher.com.helper.isPackageInstalled
import sukun.minimalist.app.launcher.com.helper.openAlarmApp
import sukun.minimalist.app.launcher.com.helper.openCalendar
import sukun.minimalist.app.launcher.com.helper.openCameraApp
import sukun.minimalist.app.launcher.com.helper.openDialerApp
import sukun.minimalist.app.launcher.com.helper.openSearch
import sukun.minimalist.app.launcher.com.helper.setPlainWallpaperByTheme
import sukun.minimalist.app.launcher.com.helper.showToast
import sukun.minimalist.app.launcher.com.helper.prayerKeyToMark
import sukun.minimalist.app.launcher.com.helper.toOverlayText
import sukun.minimalist.app.launcher.com.listener.OnSwipeTouchListener
import sukun.minimalist.app.launcher.com.helper.openGoogleWeather
import sukun.minimalist.app.launcher.com.listener.ViewSwipeTouchListener
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

class HomeFragment : Fragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceManager: DevicePolicyManager
    private var focusModeJob: Job? = null
    private var prayerJob: Job? = null
    private var dateTimeJob: Job? = null
    private var systemTimeReceiver: BroadcastReceiver? = null
    private var currentPrayerState: PrayerState? = null
    private var defaultHomeAppsPaddingTop = 0
    private var defaultHomeAppsPaddingBottom = 0
    private var topCornerStackRunnable: Runnable? = null
    private var overlayLayoutRunnable: Runnable? = null

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val safeBinding get() = _binding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

        deviceManager = context?.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        defaultHomeAppsPaddingTop = binding.homeAppsLayout.paddingTop
        defaultHomeAppsPaddingBottom = binding.homeAppsLayout.paddingBottom

        binding.mainLayout.layoutTransition = null
        initObservers()
        setHomeAlignment(prefs.homeAlignment)
        initSwipeTouchListener()
        initClickListeners()
        applyReadableHomeTextColors()
        bringOverlayViewsToFront()
        updateRemindersBellCount()
        updateTodoIconCount()
    }

    override fun onResume() {
        super.onResume()
        applyReadableHomeTextColors()
        syncFocusModeState()
        updateRemindersBellCount()
        updateTodoIconCount()
        populateHomeScreen(false)
        registerSystemTimeReceiver()
        viewModel.loadWeather()
        viewModel.loadPrayerState()
        viewModel.isSukunDefault()
    }

    override fun onPause() {
        stopFocusModeTicker()
        stopPrayerTicker()
        stopDateTimeTicker()
        unregisterSystemTimeReceiver()
        super.onPause()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.lock -> {}
            R.id.recents -> {}
            R.id.clock -> openClockApp()
            R.id.date -> openCalendarApp()
            R.id.ringClock -> openClockApp()
            R.id.ringDate -> openCalendarApp()
            R.id.prayerText -> { /* mark on long-press only */ }
            R.id.weatherText -> openGoogleWeather()
            R.id.remindersBellContainer -> openReminders()
            R.id.todoIconContainer -> openTodoList()
            R.id.setDefaultLauncher -> viewModel.resetLauncherLiveData.call()
            R.id.tvScreenTime -> openScreenTimeDigitalWellbeing()

            else -> {
                try { // Launch app
                    val appLocation = view.tag.toString().toInt()
                    homeAppClicked(appLocation)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun openClockApp() {
        if (prefs.clockAppPackage.isBlank())
            openAlarmApp(requireContext())
        else
            launchApp(
                "Clock",
                prefs.clockAppPackage,
                prefs.clockAppClassName,
                prefs.clockAppUser
            )
    }

    private fun openCalendarApp() {
        if (prefs.calendarAppPackage.isBlank())
            openCalendar(requireContext())
        else
            launchApp(
                "Calendar",
                prefs.calendarAppPackage,
                prefs.calendarAppClassName,
                prefs.calendarAppUser
            )
    }

    override fun onLongClick(view: View): Boolean {
        if (syncFocusModeState()) {
            requireContext().showToast(R.string.focus_mode_blocked)
            return true
        }
        when (view.id) {
            R.id.homeApp1 -> showAppList(Constants.FLAG_SET_HOME_APP_1, prefs.appName1.isNotEmpty(), true)
            R.id.homeApp2 -> showAppList(Constants.FLAG_SET_HOME_APP_2, prefs.appName2.isNotEmpty(), true)
            R.id.homeApp3 -> showAppList(Constants.FLAG_SET_HOME_APP_3, prefs.appName3.isNotEmpty(), true)
            R.id.homeApp4 -> showAppList(Constants.FLAG_SET_HOME_APP_4, prefs.appName4.isNotEmpty(), true)
            R.id.homeApp5 -> showAppList(Constants.FLAG_SET_HOME_APP_5, prefs.appName5.isNotEmpty(), true)
            R.id.homeApp6 -> showAppList(Constants.FLAG_SET_HOME_APP_6, prefs.appName6.isNotEmpty(), true)
            R.id.homeApp7 -> showAppList(Constants.FLAG_SET_HOME_APP_7, prefs.appName7.isNotEmpty(), true)
            R.id.homeApp8 -> showAppList(Constants.FLAG_SET_HOME_APP_8, prefs.appName8.isNotEmpty(), true)
            R.id.clock -> {
                showAppList(Constants.FLAG_SET_CLOCK_APP)
                prefs.clockAppPackage = ""
                prefs.clockAppClassName = ""
                prefs.clockAppUser = ""
            }

            R.id.ringClock -> {
                showAppList(Constants.FLAG_SET_CLOCK_APP)
                prefs.clockAppPackage = ""
                prefs.clockAppClassName = ""
                prefs.clockAppUser = ""
            }

            R.id.date -> {
                showAppList(Constants.FLAG_SET_CALENDAR_APP)
                prefs.calendarAppPackage = ""
                prefs.calendarAppClassName = ""
                prefs.calendarAppUser = ""
            }

            R.id.ringDate -> {
                showAppList(Constants.FLAG_SET_CALENDAR_APP)
                prefs.calendarAppPackage = ""
                prefs.calendarAppClassName = ""
                prefs.calendarAppUser = ""
            }

            R.id.prayerText -> promptMarkPrayerDone()

            R.id.tvScreenTime -> {
                showAppList(Constants.FLAG_SET_SCREEN_TIME_APP)
                prefs.screenTimeAppPackage = ""
                prefs.screenTimeAppClassName = ""
                prefs.screenTimeAppUser = ""
            }
        }
        return true
    }

    private fun initObservers() {
        if (prefs.firstSettingsOpen && !prefs.onboardingComplete && !viewModel.isOnboardingActive()) {
            binding.firstRunTips.visibility = View.VISIBLE
            binding.setDefaultLauncher.visibility = View.GONE
        } else binding.firstRunTips.visibility = View.GONE

        viewModel.refreshHome.observe(viewLifecycleOwner) {
            populateHomeScreen(it)
        }
        viewModel.isSukunDefault.observe(viewLifecycleOwner, Observer {
            if (it == true) {
                // Reset so the button reappears if the app later loses default status
                prefs.hideSetDefaultLauncher = false
            } else {
                if (prefs.dailyWallpaper && prefs.isEffectivelyDarkTheme()) {
                    prefs.dailyWallpaper = false
                    viewModel.cancelWallpaperWorker()
                }
                prefs.homeBottomAlignment = false
                setHomeAlignment()
            }
            if (binding.firstRunTips.isVisible) return@Observer
            binding.setDefaultLauncher.isVisible = it.not() && prefs.hideSetDefaultLauncher.not()
        })
        viewModel.homeAppAlignment.observe(viewLifecycleOwner) {
            setHomeAlignment(it)
        }
        viewModel.toggleDateTime.observe(viewLifecycleOwner) {
            populateDateTime()
            updateWeatherLayout()
        }
        viewModel.screenTimeValue.observe(viewLifecycleOwner) {
            val hasUsageAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && requireContext().appUsagePermissionGranted()
            if (it != null && prefs.showScreenTimeOnHome && hasUsageAccess) {
                binding.tvScreenTime?.text = it
                binding.tvScreenTime?.visibility = View.VISIBLE
                positionTopCornerStack()
            } else {
                binding.tvScreenTime?.visibility = View.GONE
                positionTopCornerStack()
            }
        }
        viewModel.weatherData.observe(viewLifecycleOwner) {
            populateWeather(it)
        }
        viewModel.prayerData.observe(viewLifecycleOwner) {
            populatePrayer(it)
        }
        viewModel.showRecentApps.observe(viewLifecycleOwner) {
            binding.recents.performClick()
        }
        syncFocusModeState()
    }

    private fun initSwipeTouchListener() {
        val context = requireContext()
        val swipeListener = getSwipeGestureListener(context)
        binding.mainLayout.setOnTouchListener(swipeListener)
        // homeAppsLayout is match_parent and sits above the wallpaper; empty-area long-presses land here.
        binding.homeAppsLayout.setOnTouchListener(swipeListener)
        binding.clock.setOnTouchListener(getViewSwipeTouchListener(context, binding.clock))
        binding.date.setOnTouchListener(getViewSwipeTouchListener(context, binding.date))
        binding.ringClock.setOnTouchListener(getViewSwipeTouchListener(context, binding.ringClock))
        binding.ringDate.setOnTouchListener(getViewSwipeTouchListener(context, binding.ringDate))
        binding.weatherText?.setOnTouchListener(getViewSwipeTouchListener(context, binding.weatherText!!))
        binding.prayerText?.let { prayerText ->
            prayerText.setOnTouchListener(getViewSwipeTouchListener(context, prayerText))
        }
        binding.remindersBellContainer.setOnTouchListener(
            getViewSwipeTouchListener(context, binding.remindersBellContainer)
        )
        binding.todoIconContainer.setOnTouchListener(getViewSwipeTouchListener(context, binding.todoIconContainer))
        binding.homeApp1.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp1))
        binding.homeApp2.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp2))
        binding.homeApp3.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp3))
        binding.homeApp4.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp4))
        binding.homeApp5.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp5))
        binding.homeApp6.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp6))
        binding.homeApp7.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp7))
        binding.homeApp8.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp8))
    }

    private fun initClickListeners() {
        binding.lock.setOnClickListener(this)
        binding.recents.setOnClickListener(this)
        binding.clock.setOnClickListener(this)
        binding.date.setOnClickListener(this)
        binding.ringClock.setOnClickListener(this)
        binding.ringDate.setOnClickListener(this)
        binding.clock.setOnLongClickListener(this)
        binding.date.setOnLongClickListener(this)
        binding.ringClock.setOnLongClickListener(this)
        binding.ringDate.setOnLongClickListener(this)
        binding.setDefaultLauncher.setOnClickListener { viewModel.resetLauncherLiveData.call() }
        binding.setDefaultLauncher.setOnLongClickListener {
            openHomeSettings()
            true
        }
        binding.weatherText?.setOnClickListener { openGoogleWeather() }
        binding.remindersBellContainer.setOnClickListener(this)
        binding.todoIconContainer.setOnClickListener(this)
        binding.tvScreenTime?.setOnClickListener(this)
        binding.tvScreenTime?.setOnLongClickListener(this)
    }

    private fun applyReadableHomeTextColors() {
        val context = requireContext()
        val isLight = !context.isDarkThemeOn()
        val primaryTextColor = context.getColorFromAttr(R.attr.primaryColor)
        val hintTextColor = context.getColorFromAttr(R.attr.primaryColorTrans80)
        val shadowColor = context.getColorFromAttr(R.attr.primaryTextShadowColor)
        val shadowRadius = if (isLight) 3f else 7f
        readableTextViews().forEach { textView ->
            textView.setTextColor(primaryTextColor)
            textView.setHintTextColor(hintTextColor)
            if (shadowColor == Color.TRANSPARENT) {
                textView.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            } else {
                textView.setShadowLayer(shadowRadius, 0f, 2f, shadowColor)
            }
        }
        binding.remindersBell.imageTintList = ColorStateList.valueOf(primaryTextColor)
        binding.todoIcon.imageTintList = ColorStateList.valueOf(primaryTextColor)
        binding.readabilityScrim.alpha = if (isLight) 1f else 0.55f
        binding.dayProgressRingView.invalidate()
    }

    private fun readableTextViews(): List<TextView> = listOfNotNull(
        binding.clock,
        binding.date,
        binding.ringClock,
        binding.ringDate,
        binding.weatherText,
        binding.prayerText,
        binding.focusModeStatus,
        binding.tvScreenTime,
        binding.homeApp1,
        binding.homeApp2,
        binding.homeApp3,
        binding.homeApp4,
        binding.homeApp5,
        binding.homeApp6,
        binding.homeApp7,
        binding.homeApp8,
        binding.firstRunTips,
        binding.setDefaultLauncher,
    )

    private fun setHomeAlignment(horizontalGravity: Int = prefs.homeAlignment) {
        val verticalGravity = if (prefs.homeBottomAlignment) Gravity.BOTTOM else Gravity.TOP
        binding.homeAppsLayout.gravity = frameHorizontalGravity(horizontalGravity) or verticalGravity
        applyDateTimeLayoutAlignment(horizontalGravity)
        val textGravity = textHorizontalGravity(horizontalGravity)
        binding.weatherText.gravity = textGravity
        binding.prayerText.gravity = textGravity
        binding.focusModeStatus.gravity = textGravity
        binding.homeApp1.gravity = textGravity
        binding.homeApp2.gravity = textGravity
        binding.homeApp3.gravity = textGravity
        binding.homeApp4.gravity = textGravity
        binding.homeApp5.gravity = textGravity
        binding.homeApp6.gravity = textGravity
        binding.homeApp7.gravity = textGravity
        binding.homeApp8.gravity = textGravity
        positionTopCornerStack()
        positionOverlayText(horizontalGravity)
        adjustHomeAppsLayoutForTextScale()
    }

    private fun applyDateTimeLayoutAlignment(horizontalGravity: Int) {
        binding.dateTimeLayout.gravity = frameHorizontalGravity(horizontalGravity)
        val params = binding.dateTimeLayout.layoutParams as? FrameLayout.LayoutParams ?: return
        params.gravity = Gravity.TOP or frameHorizontalGravity(horizontalGravity)
        params.topMargin = dateTimeLayoutTopMargin()
        binding.dateTimeLayout.layoutParams = params
    }

    private fun dateTimeLayoutTopMargin(): Int = 56.dpToPx()

    private fun populateDateTime() {
        binding.dateTimeLayout.isVisible = prefs.dateTimeVisibility != Constants.DateTime.OFF
        val showRingClock = prefs.dateTimeVisibility == Constants.DateTime.ON
                && prefs.clockStyle == Constants.ClockStyle.DAY_RING
        binding.dateTimeStandardLayout.isVisible = binding.dateTimeLayout.isVisible && !showRingClock
        binding.ringClockLayout.isVisible = binding.dateTimeLayout.isVisible && showRingClock
        binding.clock.isVisible = Constants.DateTime.isTimeVisible(prefs.dateTimeVisibility) && !showRingClock
        binding.date?.isVisible = Constants.DateTime.isDateVisible(prefs.dateTimeVisibility) && !showRingClock
        binding.ringClock.isVisible = showRingClock
        binding.ringDate.isVisible = showRingClock
        if (binding.clock.isVisible) resetTextClockToSystem(binding.clock)
        if (binding.ringClock.isVisible) resetTextClockToSystem(binding.ringClock)
        updateDateTimeDisplay()
        applyDateTimeLayoutAlignment(prefs.homeAlignment)
        if (binding.dateTimeLayout.isVisible) startDateTimeTicker()
        else stopDateTimeTicker()
        positionOverlayText()
        scheduleTopCornerStackLayout()
        binding.dateTimeLayout.doOnLayout {
            if (safeBinding == null) return@doOnLayout
            positionOverlayText()
            scheduleTopCornerStackLayout()
        }
    }

    private fun updateDateTimeDisplay() {
        if (!binding.dateTimeLayout.isVisible) return
        val defaultDateFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
        var defaultDateText = defaultDateFormat.format(Date())
        var ringDateText = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())

        if (!prefs.showStatusBar) {
            currentBatteryLevel()?.let { battery ->
                defaultDateText = getString(R.string.day_battery, defaultDateText, battery)
                ringDateText = getString(R.string.day_battery, ringDateText, battery)
            }
        }

        binding.date?.text = defaultDateText.replace(".,", ",")
        binding.ringDate.text = ringDateText.replace(".,", ",")
        updateDayProgressClock()
    }

    private fun updateDayProgressClock() {
        val calendar = Calendar.getInstance()
        val nowMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val startMinutes = prefs.dayStartHour * 60
        val endMinutes = prefs.dayEndHour * 60
        val elapsedFraction = when {
            nowMinutes <= startMinutes -> 0f
            nowMinutes >= endMinutes -> 1f
            else -> (nowMinutes - startMinutes).toFloat() / (endMinutes - startMinutes).toFloat()
        }
        binding.dayProgressRingView.setElapsedFraction(elapsedFraction)
    }

    private fun populateWeather(weather: WeatherData?) {
        if (!prefs.showWeatherOnHome) {
            binding.weatherText?.visibility = View.GONE
            positionOverlayText()
            return
        }
        binding.weatherText?.text = when {
            prefs.weatherSourceMode == Constants.WeatherSource.GOOGLE ->
                getString(R.string.google_weather_card)
            weather != null -> weather.displayText
            else -> getString(R.string.weather_unavailable)
        }
        binding.weatherText?.visibility = View.VISIBLE
        bringOverlayViewsToFront()
        positionOverlayText()
    }

    private fun openGoogleWeather() {
        when (prefs.weatherSourceMode) {
            Constants.WeatherSource.GOOGLE -> requireContext().openGoogleWeather(
                locationLabel = prefs.weatherLocationLabel,
                locationQuery = prefs.weatherLocationQuery,
            )
            else -> viewModel.loadWeather(true)
        }
    }

    private fun populatePrayer(prayerState: PrayerState?) {
        currentPrayerState = prayerState
        if (!prefs.showPrayerOnHome || prayerState == null) {
            binding.prayerText?.visibility = View.GONE
            stopPrayerTicker()
            positionOverlayText()
            return
        }
        binding.prayerText?.text = prayerState.toOverlayText(requireContext())
        binding.prayerText?.visibility = View.VISIBLE
        bringOverlayViewsToFront()
        positionOverlayText()
        startPrayerTicker()
    }

    private fun promptMarkPrayerDone() {
        val state = currentPrayerState ?: return
        val prayerKeyToMark = state.prayerKeyToMark()
        val prayerName = sukun.minimalist.app.launcher.com.helper.getPrayerName(requireContext(), prayerKeyToMark)
        AlertDialog.Builder(requireContext())
            .setTitle(prayerName)
            .setItems(
                arrayOf(
                    getString(R.string.prayer_mark_done),
                    getString(R.string.prayer_mark_missed)
                )
            ) { _, which ->
                if (which == 0) {
                    prefs.logPrayer(prayerKeyToMark)
                    requireContext().showToast(R.string.prayer_marked_prayed)
                } else {
                    prefs.unmarkPrayer(prayerKeyToMark)
                    requireContext().showToast(R.string.prayer_marked_missed)
                }
            }
            .show()
    }

    private fun updateTodoIconCount() {
        val show = prefs.showTodoOnHome
        binding.todoIconContainer.isVisible = show
        if (!show) return
        val pending = prefs.todoItemsJson.toTodoList().count { !it.completed }
        binding.todoIconCount.isVisible = pending > 0
        if (pending > 0) binding.todoIconCount.text = pending.toString()
        positionTopCornerStack()
    }

    private fun openTodoList() {
        if (!prefs.showTodoOnHome) return
        findNavController().navigate(R.id.action_mainFragment_to_todoFragment)
    }

    private fun updateWeatherLayout(horizontalGravity: Int = prefs.homeAlignment) {
        positionOverlayText(horizontalGravity)
    }

    private fun updatePrayerLayout(horizontalGravity: Int = prefs.homeAlignment) {
        positionOverlayText(horizontalGravity)
    }

    private fun syncFocusModeState(): Boolean {
        val isFocusModeActive = prefs.isFocusModeActive()
        binding.focusModeStatus?.isVisible = isFocusModeActive
        if (isFocusModeActive) {
            binding.focusModeStatus?.text = prefs.getFocusModeStatus(requireContext())
            positionOverlayText()
            startFocusModeTicker()
        } else {
            stopFocusModeTicker()
        }
        updateStatusBarVisibility(isFocusModeActive)
        return isFocusModeActive
    }

    private fun updateFocusModeLayout(horizontalGravity: Int = prefs.homeAlignment) {
        positionOverlayText(horizontalGravity)
    }

    private fun bringOverlayViewsToFront() {
        val binding = safeBinding ?: return
        binding.dateTimeLayout.bringToFront()
        binding.weatherText?.bringToFront()
        binding.prayerText?.bringToFront()
        binding.focusModeStatus?.bringToFront()
        binding.topCornerStack.bringToFront()
    }

    private fun positionOverlayText(horizontalGravity: Int = prefs.homeAlignment) {
        val layout = safeBinding?.mainLayout ?: return
        overlayLayoutRunnable?.let { layout.removeCallbacks(it) }
        val runnable = Runnable {
            overlayLayoutRunnable = null
            positionOverlayTextNow(horizontalGravity)
        }
        overlayLayoutRunnable = runnable
        layout.post(runnable)
    }

    private fun positionOverlayTextNow(horizontalGravity: Int = prefs.homeAlignment) {
        val binding = safeBinding ?: return
        try {
            applyTopCornerStackPositionsNow()
            val weatherTopMargin = overlayContentTopMargin(binding)
            val cornerReserve = overlayCornerReserveForAlignment(binding, horizontalGravity)

            updateOverlayLayout(binding.weatherText, horizontalGravity, weatherTopMargin, cornerReserve)

            val weather = binding.weatherText
            if (weather?.isVisible == true) {
                weather.doOnLayout {
                    if (safeBinding == null) return@doOnLayout
                    finishOverlayTextLayout(horizontalGravity)
                }
            } else {
                finishOverlayTextLayout(horizontalGravity)
            }
        } catch (_: Throwable) {
            // View was likely destroyed while this runnable executed; safely ignore
        }
    }

    private fun finishOverlayTextLayout(horizontalGravity: Int = prefs.homeAlignment) {
        val binding = safeBinding ?: return
        try {
            val spacing = 12.dpToPx()
            val weatherTopMargin = overlayContentTopMargin(binding)
            val cornerReserve = overlayCornerReserveForAlignment(binding, horizontalGravity)
            val availableWidth = overlayAvailableWidth(horizontalGravity, cornerReserve)

            val weatherHeight = visibleOverlayHeight(binding.weatherText, availableWidth)
            val prayerTopMargin = if (binding.weatherText?.isVisible == true) {
                weatherTopMargin + weatherHeight + spacing
            } else {
                weatherTopMargin
            }
            updateOverlayLayout(binding.prayerText, horizontalGravity, prayerTopMargin, cornerReserve)

            val prayerHeight = visibleOverlayHeight(binding.prayerText, availableWidth)
            val focusTopMargin = when {
                binding.prayerText.isVisible -> prayerTopMargin + prayerHeight + spacing
                binding.weatherText.isVisible -> weatherTopMargin + weatherHeight + spacing
                else -> weatherTopMargin
            }
            updateOverlayLayout(binding.focusModeStatus, horizontalGravity, focusTopMargin, cornerReserve)

            val focusHeight = visibleOverlayHeight(binding.focusModeStatus, availableWidth)
            val overlayBottom = when {
                binding.focusModeStatus.isVisible -> focusTopMargin + focusHeight
                binding.prayerText.isVisible -> prayerTopMargin + prayerHeight
                binding.weatherText.isVisible -> weatherTopMargin + weatherHeight
                else -> getDateTimeBottom() ?: 0
            }
            val topCornerBottom = binding.topCornerStack.takeIf { it.isVisible }
                ?.let { it.top + visibleOverlayHeight(it) } ?: 0
            val topOverlayBottom = if (overlaySharesIconColumn(horizontalGravity)) {
                max(overlayBottom, topCornerBottom)
            } else {
                overlayBottom
            }
            updateHomeAppsTopPadding(topOverlayBottom + spacing)
            bringOverlayViewsToFront()
        } catch (_: Throwable) {
            // View was likely destroyed while this runnable executed; safely ignore
        }
    }

    private fun overlayCornerReserveWidth(binding: FragmentHomeBinding): Int {
        if (!binding.topCornerStack.isVisible) return 0
        val stackWidth = when {
            binding.topCornerStack.width > 0 -> binding.topCornerStack.width
            binding.topCornerStack.measuredWidth > 0 -> binding.topCornerStack.measuredWidth
            else -> 0
        }
        if (stackWidth > 0) return stackWidth + 16.dpToPx()
        return 72.dpToPx()
    }

    private fun overlaySharesIconColumn(horizontalGravity: Int): Boolean {
        return frameHorizontalGravity(horizontalGravity) ==
                frameHorizontalGravity(topCornerHorizontalGravity())
    }

    private fun overlayCornerReserveForAlignment(
        binding: FragmentHomeBinding,
        horizontalGravity: Int,
    ): Int {
        if (!binding.topCornerStack.isVisible) return 0
        if (frameHorizontalGravity(horizontalGravity) == Gravity.END) {
            return overlayCornerReserveWidth(binding)
        }
        if (overlaySharesIconColumn(horizontalGravity)) {
            return overlayCornerReserveWidth(binding)
        }
        return 0
    }

    private fun overlayAvailableWidth(horizontalGravity: Int, cornerReserveWidth: Int): Int {
        val horizontalMargin = 24.dpToPx()
        if (frameHorizontalGravity(horizontalGravity) == Gravity.CENTER_HORIZONTAL) {
            return resources.displayMetrics.widthPixels - horizontalMargin * 2
        }
        val isRtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val iconSideInset = horizontalMargin + cornerReserveWidth
        val marginStart = if (isRtl) iconSideInset else horizontalMargin
        val marginEnd = if (isRtl) horizontalMargin else iconSideInset
        return (resources.displayMetrics.widthPixels - marginStart - marginEnd)
            .coerceAtLeast(horizontalMargin * 2)
    }

    private fun overlayContentTopMargin(binding: FragmentHomeBinding): Int {
        val spacing = 12.dpToPx()
        return getDateTimeBottom()?.plus(spacing) ?: 56.dpToPx()
    }

    private fun iconsBelowRing(binding: FragmentHomeBinding): Boolean {
        if (!binding.ringClockLayout.isVisible) return false
        return overlaySharesIconColumn(prefs.homeAlignment)
    }

    private fun visibleOverlayHeight(view: View?, maxMeasureWidth: Int? = null): Int {
        if (view == null || !view.isVisible) return 0
        if (view.height > 0) return view.height
        if (view.measuredHeight > 0 && view.width > 0) return view.measuredHeight
        val maxWidth = maxMeasureWidth ?: view.resources.displayMetrics.widthPixels
        val widthSpec = View.MeasureSpec.makeMeasureSpec(
            maxWidth,
            View.MeasureSpec.AT_MOST,
        )
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
        return view.measuredHeight
    }

    private fun getDateTimeBottom(): Int? {
        val binding = safeBinding ?: return null
        if (!binding.dateTimeLayout.isVisible) return null
        val layoutBottom = binding.dateTimeLayout.top + binding.dateTimeLayout.height
        val visibleClockBottom = when {
            binding.ringClockLayout.isVisible ->
                binding.dateTimeLayout.top + binding.ringClockLayout.top + binding.ringClockLayout.height
            binding.dateTimeStandardLayout.isVisible ->
                binding.dateTimeLayout.top + binding.dateTimeStandardLayout.top + binding.dateTimeStandardLayout.height
            else -> layoutBottom
        }
        return max(layoutBottom, visibleClockBottom)
    }

    private fun frameHorizontalGravity(alignment: Int): Int = when (alignment) {
        Gravity.CENTER, Gravity.CENTER_HORIZONTAL -> Gravity.CENTER_HORIZONTAL
        Gravity.END, Gravity.RIGHT -> Gravity.END
        else -> Gravity.START
    }

    private fun textHorizontalGravity(alignment: Int): Int = when (alignment) {
        Gravity.CENTER, Gravity.CENTER_HORIZONTAL -> Gravity.CENTER_HORIZONTAL
        Gravity.END, Gravity.RIGHT -> Gravity.END
        else -> Gravity.START
    }

    private fun updateOverlayLayout(
        view: TextView?,
        horizontalGravity: Int,
        topMargin: Int,
        cornerReserveWidth: Int = 0,
    ) {
        val overlayView = view ?: return
        val params = overlayView.layoutParams as? FrameLayout.LayoutParams ?: return
        val horizontalMargin = 24.dpToPx()
        val isRtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val frameGravity = frameHorizontalGravity(horizontalGravity)

        params.topMargin = topMargin
        params.gravity = Gravity.TOP or frameGravity

        if (frameGravity == Gravity.CENTER_HORIZONTAL) {
            params.width = FrameLayout.LayoutParams.MATCH_PARENT
            params.marginStart = horizontalMargin
            params.marginEnd = horizontalMargin
            overlayView.maxWidth = Int.MAX_VALUE
            overlayView.textAlignment = View.TEXT_ALIGNMENT_CENTER
            overlayView.gravity = Gravity.CENTER_HORIZONTAL
        } else {
            val iconSideInset = horizontalMargin + cornerReserveWidth
            params.width = FrameLayout.LayoutParams.WRAP_CONTENT
            params.marginStart = if (isRtl) iconSideInset else horizontalMargin
            params.marginEnd = if (isRtl) horizontalMargin else iconSideInset
            val availableWidth = resources.displayMetrics.widthPixels - params.marginStart - params.marginEnd
            overlayView.maxWidth = availableWidth.coerceAtLeast(horizontalMargin * 2)
            overlayView.textAlignment = when (frameGravity) {
                Gravity.END -> View.TEXT_ALIGNMENT_VIEW_END
                else -> View.TEXT_ALIGNMENT_VIEW_START
            }
            overlayView.gravity = textHorizontalGravity(horizontalGravity)
        }
        overlayView.layoutParams = params
    }

    private fun updateHomeAppsTopPadding(homeAppsMinTop: Int) {
        binding.homeAppsLayout.setPadding(
            binding.homeAppsLayout.paddingLeft,
            max(defaultHomeAppsPaddingTop, homeAppsMinTop),
            binding.homeAppsLayout.paddingRight,
            homeAppsBottomPadding(),
        )
        adjustHomeAppsLayoutForTextScale()
    }

    private fun homeAppTextViews(): List<TextView> = listOf(
        binding.homeApp1,
        binding.homeApp2,
        binding.homeApp3,
        binding.homeApp4,
        binding.homeApp5,
        binding.homeApp6,
        binding.homeApp7,
        binding.homeApp8,
    )

    private fun homeAppsBottomPadding(): Int = defaultHomeAppsPaddingBottom

    private fun adjustHomeAppsLayoutForTextScale() {
        if (_binding == null) return
        val scale = prefs.textSizeScale
        val defaultItemPadding = resources.getDimensionPixelSize(R.dimen.home_app_padding_vertical)
        val itemPadding = when {
            scale >= 1.2f -> (defaultItemPadding * 1.1f).toInt()
            scale >= 1.1f -> defaultItemPadding
            else -> defaultItemPadding
        }
        val horizontalGravity = prefs.homeAlignment
        homeAppTextViews().forEach { textView ->
            textView.maxLines = 1
            textView.ellipsize = TextUtils.TruncateAt.END
            textView.gravity = textHorizontalGravity(horizontalGravity)
            textView.setPadding(textView.paddingLeft, itemPadding, textView.paddingRight, itemPadding)
            val lp = textView.layoutParams as? LinearLayout.LayoutParams ?: return@forEach
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            lp.weight = 0f
            textView.layoutParams = lp
        }
        binding.homeAppsLayout.setPadding(
            binding.homeAppsLayout.paddingLeft,
            binding.homeAppsLayout.paddingTop,
            binding.homeAppsLayout.paddingRight,
            homeAppsBottomPadding(),
        )
    }

    private fun startFocusModeTicker() {
        if (focusModeJob?.isActive == true) return
        focusModeJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                val isFocusModeActive = prefs.isFocusModeActive()
                binding.focusModeStatus?.isVisible = isFocusModeActive
                if (!isFocusModeActive) {
                    updateStatusBarVisibility(false)
                    positionOverlayText()
                    break
                }
                binding.focusModeStatus?.text = prefs.getFocusModeStatus(requireContext())
                positionOverlayText()
                delay(1000)
            }
            focusModeJob = null
        }
    }

    private fun stopFocusModeTicker() {
        focusModeJob?.cancel()
        focusModeJob = null
    }

    private fun startDateTimeTicker() {
        if (dateTimeJob?.isActive == true) return
        dateTimeJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                if (!binding.dateTimeLayout.isVisible) break
                updateDateTimeDisplay()
                delay(if (prefs.showStatusBar) 60_000L else 15_000L)
            }
            dateTimeJob = null
        }
    }

    private fun resetTextClockToSystem(clock: TextClock) {
        clock.timeZone = java.util.TimeZone.getDefault().id
    }

    private fun currentBatteryLevel(): Int? {
        val batteryIntent = requireContext().registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        if (batteryIntent != null) {
            val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) {
                return ((level * 100f) / scale).toInt().coerceIn(0, 100)
            }
        }
        val capacity = (requireContext().getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return capacity.takeIf { it in 1..100 }
    }

    private fun registerSystemTimeReceiver() {
        unregisterSystemTimeReceiver()
        if (prefs.showStatusBar || !binding.dateTimeLayout.isVisible) return

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        systemTimeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                updateDateTimeDisplay()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(systemTimeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            requireContext().registerReceiver(systemTimeReceiver, filter)
        }
    }

    private fun unregisterSystemTimeReceiver() {
        systemTimeReceiver?.let { receiver ->
            try {
                requireContext().unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
            }
        }
        systemTimeReceiver = null
    }

    private fun stopDateTimeTicker() {
        dateTimeJob?.cancel()
        dateTimeJob = null
    }

    private fun startPrayerTicker() {
        if (prayerJob?.isActive == true) return
        prayerJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                val prayerState = currentPrayerState
                if (!prefs.showPrayerOnHome || prayerState == null) {
                    binding.prayerText?.isVisible = false
                    positionOverlayText()
                    break
                }
                if (prayerState.remainingMillis() <= 0L) {
                    viewModel.refreshPrayerData(forceLocationRefresh = prefs.prayerSourceMode == Constants.PrayerSource.DEVICE)
                    delay(1000)
                    continue
                }
                binding.prayerText?.text = prayerState.toOverlayText(requireContext())
                binding.prayerText?.isVisible = true
                bringOverlayViewsToFront()
                positionOverlayText()
                delay(1000)
            }
            prayerJob = null
        }
    }

    private fun stopPrayerTicker() {
        prayerJob?.cancel()
        prayerJob = null
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun populateScreenTime() {
        if (!prefs.showScreenTimeOnHome || requireContext().appUsagePermissionGranted().not()) {
            binding.tvScreenTime?.visibility = View.GONE
            positionTopCornerStack()
            return
        }

        viewModel.screenTimeValue.value?.let { cached ->
            binding.tvScreenTime?.text = cached
            binding.tvScreenTime?.visibility = View.VISIBLE
        }
        viewModel.getTodaysScreenTime()
        positionTopCornerStack()
    }

    private fun topCornerHorizontalGravity(): Int {
        return if (resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            Gravity.START
        } else {
            Gravity.END
        }
    }

    private fun topCornerHorizontalMargin(): Int = 24.dpToPx()

    private fun measuredOrDimen(view: View?, dimenRes: Int, scaleWithTextSize: Boolean = false): Int {
        if (view != null && view.height > 0) return view.height
        var size = resources.getDimensionPixelSize(dimenRes)
        if (scaleWithTextSize) size = (size * prefs.textSizeScale).toInt()
        return size
    }

    private fun estimatedDateTimeBottom(binding: FragmentHomeBinding): Int {
        val top = if (binding.dateTimeLayout.top > 0) binding.dateTimeLayout.top else 56.dpToPx()
        val contentHeight = when {
            binding.ringClockLayout.isVisible -> 220.dpToPx()
            else -> {
                var height = 0
                if (binding.clock.isVisible) {
                    height += measuredOrDimen(binding.clock, R.dimen.time_size, scaleWithTextSize = true)
                }
                if (binding.date?.isVisible == true) {
                    height += measuredOrDimen(binding.date, R.dimen.date_size, scaleWithTextSize = true)
                }
                height
            }
        }
        return top + contentHeight
    }

    private fun topCornerStackTopMargin(binding: FragmentHomeBinding): Int {
        val spacing = 6.dpToPx()
        if (!binding.dateTimeLayout.isVisible) {
            return screenTimeFallbackTopMargin()
        }
        if (iconsBelowRing(binding)) {
            val ringBottom = getDateTimeBottom() ?: estimatedDateTimeBottom(binding)
            return ringBottom + spacing
        }
        val dateTimeTop = binding.dateTimeLayout.top.takeIf { it > 0 } ?: 56.dpToPx()
        return dateTimeTop + spacing
    }

    private fun screenTimeFallbackTopMargin(): Int {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return if (isLandscape) {
            if (prefs.dateTimeVisibility == Constants.DateTime.DATE_ONLY) 36.dpToPx() else 56.dpToPx()
        } else {
            if (prefs.dateTimeVisibility == Constants.DateTime.DATE_ONLY) 45.dpToPx() else 72.dpToPx()
        }
    }

    private fun updateTopCornerStackVisibility(binding: FragmentHomeBinding) {
        binding.topCornerStack.isVisible =
            binding.remindersBellContainer.isVisible ||
            binding.todoIconContainer.isVisible ||
            binding.tvScreenTime?.isVisible == true
    }

    private fun positionTopCornerStack() {
        scheduleTopCornerStackLayout()
    }

    private fun scheduleTopCornerStackLayout() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val binding = safeBinding ?: return
        val layout = binding.mainLayout

        applyTopCornerStackPositionsNow()

        topCornerStackRunnable?.let { layout.removeCallbacks(it) }
        val runnable = Runnable {
            topCornerStackRunnable = null
            applyTopCornerStackPositions()
        }
        topCornerStackRunnable = runnable
        layout.post(runnable)
    }

    private fun applyTopCornerStackPositions() {
        val binding = safeBinding ?: return
        if (binding.dateTimeLayout.isVisible && binding.dateTimeLayout.height == 0) {
            binding.dateTimeLayout.doOnLayout {
                safeBinding?.mainLayout?.post { applyTopCornerStackPositionsNow() }
            }
            return
        }
        applyTopCornerStackPositionsNow()
    }

    private fun applyTopCornerStackPositionsNow() {
        val binding = safeBinding ?: return
        val horizontalMargin = topCornerHorizontalMargin()
        val iconsGravity = topCornerHorizontalGravity()

        try {
            updateTopCornerStackVisibility(binding)
            binding.topCornerStack.gravity = iconsGravity
            binding.tvScreenTime?.gravity = iconsGravity

            binding.topCornerStack.layoutParams =
                (binding.topCornerStack.layoutParams as? FrameLayout.LayoutParams)?.apply {
                    topMargin = topCornerStackTopMargin(binding)
                    marginStart = horizontalMargin
                    marginEnd = horizontalMargin
                    gravity = iconsGravity or Gravity.TOP
                } ?: FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = topCornerStackTopMargin(binding)
                    marginStart = horizontalMargin
                    marginEnd = horizontalMargin
                    gravity = iconsGravity or Gravity.TOP
                }
        } catch (_: Throwable) {
        }
    }

    private fun populateHomeScreen(appCountUpdated: Boolean) {
        if (appCountUpdated) hideHomeApps()
        populateDateTime()
        updateTodoIconCount()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            populateScreenTime()

        val homeAppsNum = prefs.homeAppsNum
        if (homeAppsNum == 0) {
            adjustHomeAppsLayoutForTextScale()
            return
        }

        binding.homeApp1.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp1, prefs.appName1, prefs.appPackage1, prefs.appActivityClassName1, prefs.appUser1, prefs.isShortcut1, prefs.shortcutId1)) {
            prefs.appName1 = ""
            prefs.appPackage1 = ""
        }
        if (homeAppsNum == 1) {
            adjustHomeAppsLayoutForTextScale()
            return
        }

        binding.homeApp2.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp2, prefs.appName2, prefs.appPackage2, prefs.appActivityClassName2, prefs.appUser2, prefs.isShortcut2, prefs.shortcutId2)) {
            prefs.appName2 = ""
            prefs.appPackage2 = ""
        }
        if (homeAppsNum == 2) {
            adjustHomeAppsLayoutForTextScale()
            return
        }

        binding.homeApp3.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp3, prefs.appName3, prefs.appPackage3, prefs.appActivityClassName3, prefs.appUser3, prefs.isShortcut3, prefs.shortcutId3)) {
            prefs.appName3 = ""
            prefs.appPackage3 = ""
        }
        if (homeAppsNum == 3) {
            adjustHomeAppsLayoutForTextScale()
            return
        }

        binding.homeApp4.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp4, prefs.appName4, prefs.appPackage4, prefs.appActivityClassName4, prefs.appUser4, prefs.isShortcut4, prefs.shortcutId4)) {
            prefs.appName4 = ""
            prefs.appPackage4 = ""
        }
        if (homeAppsNum == 4) {
            adjustHomeAppsLayoutForTextScale()
            return
        }

        binding.homeApp5.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp5, prefs.appName5, prefs.appPackage5, prefs.appActivityClassName5, prefs.appUser5, prefs.isShortcut5, prefs.shortcutId5)) {
            prefs.appName5 = ""
            prefs.appPackage5 = ""
        }
        if (homeAppsNum == 5) {
            adjustHomeAppsLayoutForTextScale()
            return
        }

        binding.homeApp6.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp6, prefs.appName6, prefs.appPackage6, prefs.appActivityClassName6, prefs.appUser6, prefs.isShortcut6, prefs.shortcutId6)) {
            prefs.appName6 = ""
            prefs.appPackage6 = ""
        }
        if (homeAppsNum == 6) return

        binding.homeApp7.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp7, prefs.appName7, prefs.appPackage7, prefs.appActivityClassName7, prefs.appUser7, prefs.isShortcut7, prefs.shortcutId7)) {
            prefs.appName7 = ""
            prefs.appPackage7 = ""
        }
        if (homeAppsNum == 7) return

        binding.homeApp8.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp8, prefs.appName8, prefs.appPackage8, prefs.appActivityClassName8, prefs.appUser8, prefs.isShortcut8, prefs.shortcutId8)) {
            prefs.appName8 = ""
            prefs.appPackage8 = ""
        }
        adjustHomeAppsLayoutForTextScale()
    }

    private fun setHomeAppText(
        textView: TextView,
        appName: String,
        packageName: String,
        activityClassName: String?,
        userString: String,
        isShortcut: Boolean,
        shortcutId: String?,
    ): Boolean {
        // Get user handle for the app/shortcut
        val userHandle = getUserHandleFromString(requireContext(), userString)

        // If it's a shortcut, verify it still exists
        if (isShortcut) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
                textView.text = ""
                setHomeAppIcon(textView, null)
                return false
            }
            val launcherApps = requireContext().getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

            // Query for the specific shortcut
            val query = LauncherApps.ShortcutQuery().apply {
                setPackage(packageName)
                setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            }

            try {
                val shortcuts = launcherApps.getShortcuts(query, userHandle)
                // Check if our shortcut still exists
                if (shortcuts?.any { it.id == shortcutId } == true) {
                    textView.text = appName
                    updateHomeAppIcon(
                        textView = textView,
                        packageName = packageName,
                        activityClassName = null,
                        userHandle = userHandle,
                        isShortcut = true,
                        shortcutId = shortcutId
                    )
                    return true
                }
                textView.text = ""
                setHomeAppIcon(textView, null)
                return false
            } catch (e: Exception) {
                e.printStackTrace()
                textView.text = ""
                setHomeAppIcon(textView, null)
                return false
            }
        }

        // Regular app check
        if (isPackageInstalled(requireContext(), packageName, userString)) {
            textView.text = appName
            updateHomeAppIcon(
                textView = textView,
                packageName = packageName,
                activityClassName = activityClassName,
                userHandle = userHandle,
                isShortcut = false,
                shortcutId = null
            )
            return true
        }
        textView.text = ""
        setHomeAppIcon(textView, null)
        return false
    }

    private fun updateHomeAppIcon(
        textView: TextView,
        packageName: String,
        activityClassName: String?,
        userHandle: android.os.UserHandle,
        isShortcut: Boolean,
        shortcutId: String?,
    ) {
        if (!prefs.showHomeAppIcons) {
            setHomeAppIcon(textView, null)
            return
        }
        val launcherApps = requireContext().getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val drawable = try {
            if (isShortcut && !shortcutId.isNullOrBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                val query = LauncherApps.ShortcutQuery().apply {
                    setPackage(packageName)
                    setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
                }
                launcherApps.getShortcuts(query, userHandle)
                    ?.firstOrNull { it.id == shortcutId }
                    ?.let { launcherApps.getShortcutIconDrawable(it, resources.displayMetrics.densityDpi) }
            } else {
                launcherApps.getActivityList(packageName, userHandle)
                    .firstOrNull { it.componentName.className == activityClassName }
                    ?.getBadgedIcon(resources.displayMetrics.densityDpi)
                    ?: launcherApps.getActivityList(packageName, userHandle)
                        .firstOrNull()
                        ?.getBadgedIcon(resources.displayMetrics.densityDpi)
            }
        } catch (_: Exception) {
            null
        }
        setHomeAppIcon(textView, drawable)
    }

    private fun setHomeAppIcon(textView: TextView, drawable: Drawable?) {
        if (drawable == null) {
            textView.setCompoundDrawablesRelative(null, null, null, null)
            textView.compoundDrawablePadding = 0
            return
        }
        val iconSize = if (prefs.textSizeScale >= 1.15f) 20.dpToPx() else 24.dpToPx()
        drawable.setBounds(0, 0, iconSize, iconSize)
        textView.setCompoundDrawablesRelative(drawable, null, null, null)
        textView.compoundDrawablePadding = if (prefs.textSizeScale >= 1.15f) 8.dpToPx() else 12.dpToPx()
    }

    private fun hideHomeApps() {
        binding.homeApp1.visibility = View.GONE
        binding.homeApp2.visibility = View.GONE
        binding.homeApp3.visibility = View.GONE
        binding.homeApp4.visibility = View.GONE
        binding.homeApp5.visibility = View.GONE
        binding.homeApp6.visibility = View.GONE
        binding.homeApp7.visibility = View.GONE
        binding.homeApp8.visibility = View.GONE
        setHomeAppIcon(binding.homeApp1, null)
        setHomeAppIcon(binding.homeApp2, null)
        setHomeAppIcon(binding.homeApp3, null)
        setHomeAppIcon(binding.homeApp4, null)
        setHomeAppIcon(binding.homeApp5, null)
        setHomeAppIcon(binding.homeApp6, null)
        setHomeAppIcon(binding.homeApp7, null)
        setHomeAppIcon(binding.homeApp8, null)
    }

    private fun launchAppOrShortcut(
        appName: String,
        packageName: String,
        activityClassName: String?,
        shortcutId: String?,
        isShortcut: Boolean,
        userString: String,
        fallback: (() -> Unit)? = null,
    ) {
        if (appName.isEmpty()) {
            showLongPressToast()
            return
        }
        if (isShortcut && !shortcutId.isNullOrEmpty()) {
            launchShortcut(
                packageName = packageName,
                shortcutId = shortcutId,
                shortcutLabel = appName,
                userString = userString
            )
        } else if (packageName.isNotEmpty()) {
            launchApp(
                appName = appName,
                packageName = packageName,
                activityClassName = activityClassName,
                userString = userString
            )
        } else {
            fallback?.invoke()
        }
    }

    private fun launchShortcut(shortcutId: String, packageName: String, shortcutLabel: String, userString: String) {
        viewModel.selectedApp(
            AppModel.PinnedShortcut(
                shortcutId = shortcutId,
                appLabel = shortcutLabel,
                user = getUserHandleFromString(requireContext(), userString),
                key = null,
                appPackage = packageName,
                isNew = false,
            ),
            Constants.FLAG_LAUNCH_APP
        )
    }

    private fun launchApp(appName: String, packageName: String, activityClassName: String?, userString: String) {
        val appModel = AppModel.App(
            appLabel = appName,
            key = null,
            appPackage = packageName,
            activityClassName = activityClassName,
            isNew = false,
            user = getUserHandleFromString(requireContext(), userString)
        )
        if (viewModel.cooldownManager.isInCooldown(packageName)) {
            showHomeCooldownWarning(packageName, appName) {
                viewModel.selectedApp(appModel, Constants.FLAG_LAUNCH_APP)
            }
            return
        }
        viewModel.selectedApp(appModel, Constants.FLAG_LAUNCH_APP)
    }

    private fun showHomeCooldownWarning(packageName: String, appName: String, onProceed: () -> Unit) {
        val cm = viewModel.cooldownManager
        val openCount = cm.getOpenCount(packageName)
        val durationMs = cm.getTotalDurationMs(packageName)
        val cooloffEndsAt = cm.getCooloffEndsAt(packageName)
        val remaining = ((cooloffEndsAt - System.currentTimeMillis()) / 60_000).coerceAtLeast(1)
        val durationText = run {
            val mins = durationMs / 60_000
            val h = mins / 60; val m = mins % 60
            if (h > 0) "${h}h ${m}m" else "${m}m"
        }

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
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialogView.findViewById<TextView>(R.id.cooldownOpenAnyway).setOnClickListener {
            dialog.dismiss(); onProceed()
        }
        dialogView.findViewById<TextView>(R.id.cooldownStayFocused).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun homeAppClicked(location: Int) {
        launchAppOrShortcut(
            appName = prefs.getAppName(location),
            packageName = prefs.getAppPackage(location),
            activityClassName = prefs.getAppActivityClassName(location),
            shortcutId = prefs.getShortcutId(location),
            isShortcut = prefs.getIsShortcut(location),
            userString = prefs.getAppUser(location)
        )
    }

    private fun openSwipeRightApp() {
        if (syncFocusModeState()) return
        if (!prefs.swipeRightEnabled) return
        launchAppOrShortcut(
            appName = prefs.appNameSwipeRight,
            packageName = prefs.appPackageSwipeRight,
            activityClassName = prefs.appActivityClassNameSwipeRight,
            shortcutId = prefs.shortcutIdSwipeRight,
            isShortcut = prefs.isShortcutSwipeRight,
            userString = prefs.appUserSwipeRight,
            fallback = { openDialerApp(requireContext()) }
        )
    }

    private fun openSwipeLeftApp() {
        if (syncFocusModeState()) return
        if (!prefs.swipeLeftEnabled) return
        launchAppOrShortcut(
            appName = prefs.appNameSwipeLeft,
            packageName = prefs.appPackageSwipeLeft,
            activityClassName = prefs.appActivityClassNameSwipeLeft,
            shortcutId = prefs.shortcutIdSwipeLeft,
            isShortcut = prefs.isShortcutSwipeLeft,
            userString = prefs.appUserSwipeLeft,
            fallback = { openCameraApp(requireContext()) }
        )
    }

    private fun showAppList(flag: Int, rename: Boolean = false, includeHiddenApps: Boolean = false) {
        if (syncFocusModeState()) {
            requireContext().showToast(R.string.focus_mode_blocked)
            return
        }

        val isHomeAppSlotStep = flag in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_HOME_APP_8 &&
            viewModel.isOnboardingActive() &&
            viewModel.currentOnboardingStep().requiredAction == OnboardingAction.TAP_HOME_APP_SLOT

        when {
            flag in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_HOME_APP_8 ->
                viewModel.reportOnboardingAction(OnboardingAction.TAP_HOME_APP_SLOT)
            flag == Constants.FLAG_LAUNCH_APP ->
                viewModel.reportOnboardingAction(OnboardingAction.OPEN_APP_DRAWER)
        }

        if (isHomeAppSlotStep) {
            // Do not open the app picker during the tour. The highlight + tap teaches the feature.
            requireContext().showToast(getString(R.string.onboarding_home_app_slot_chosen))
            return
        }

        viewModel.getAppList(includeHiddenApps)
        val navController = try {
            findNavController()
        } catch (e: Exception) {
            null
        } ?: return

        try {
            if (navController.currentDestination?.id == R.id.mainFragment) {
                navController.navigate(
                    R.id.action_mainFragment_to_appListFragment,
                    bundleOf(
                        Constants.Key.FLAG to flag,
                        Constants.Key.RENAME to rename
                    )
                )
            } else {
                navController.navigate(
                    R.id.appListFragment,
                    bundleOf(
                        Constants.Key.FLAG to flag,
                        Constants.Key.RENAME to rename
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun swipeDownAction() {
        when (prefs.swipeDownAction) {
            Constants.SwipeDownAction.SEARCH -> openSearch(requireContext())
            else -> expandNotificationDrawer(requireContext())
        }
    }

    private fun handleSwipeDownDuringFocusMode(): Boolean {
        val isFocusModeActive = syncFocusModeState()
        if (!isFocusModeActive) return false
        if (prefs.canOpenNotificationsInFocusMode()) {
            swipeDownAction()
        }
        return true
    }

    private fun lockPhone() {
        requireActivity().runOnUiThread {
            try {
                deviceManager.lockNow()
            } catch (e: SecurityException) {
                requireContext().showToast(getString(R.string.please_turn_on_double_tap_to_unlock), Toast.LENGTH_LONG)
                findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
            } catch (e: Exception) {
                requireContext().showToast(getString(R.string.launcher_failed_to_lock_device), Toast.LENGTH_LONG)
                prefs.lockModeOn = false
            }
        }
    }

    private fun updateStatusBarVisibility(isFocusModeActive: Boolean = prefs.isFocusModeActive()) {
        val activity = activity ?: return
        val hideForFocus = isFocusModeActive && prefs.focusModeHideStatusBar
        val show = !hideForFocus && prefs.showStatusBar
        applyLauncherStatusBarVisibility(activity, show)
        if (show) {
            unregisterSystemTimeReceiver()
        } else {
            registerSystemTimeReceiver()
            updateDateTimeDisplay()
        }
    }

    private fun changeAppTheme() {
        if (prefs.dailyWallpaper.not()) return
        val changedAppTheme = getChangedAppTheme(requireContext(), prefs.appTheme)
        prefs.appTheme = changedAppTheme
        if (prefs.dailyWallpaper) {
            setPlainWallpaperByTheme(requireContext(), changedAppTheme)
            viewModel.setWallpaperWorker()
        }
        AppCompatDelegate.setDefaultNightMode(prefs.resolveLaunchNightMode())
    }

    private fun openScreenTimeDigitalWellbeing() {
        if (prefs.screenTimeAppPackage.isNotBlank()) {
            launchApp(
                "Screen Time",
                prefs.screenTimeAppPackage,
                prefs.screenTimeAppClassName,
                prefs.screenTimeAppUser
            )
            return
        }
        val intent = Intent()
        try {
            intent.setClassName(
                Constants.DIGITAL_WELLBEING_PACKAGE_NAME,
                Constants.DIGITAL_WELLBEING_ACTIVITY
            )
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                intent.setClassName(
                    Constants.DIGITAL_WELLBEING_SAMSUNG_PACKAGE_NAME,
                    Constants.DIGITAL_WELLBEING_SAMSUNG_ACTIVITY
                )
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showLongPressToast() = requireContext().showToast(getString(R.string.long_press_to_select_app))

    private fun startFocusModeFromDoubleTap() {
        if (!PremiumAccess.hasPremiumAccess(prefs)) {
            (requireActivity() as? MainActivity)?.showUpgradeDialog()
            return
        }
        if (prefs.isFocusModeActive()) {
            requireContext().showToast(R.string.focus_mode_blocked)
            return
        }
        if (!isAccessServiceEnabled(requireContext())) {
            requireContext().showToast(R.string.focus_mode_enable_accessibility, Toast.LENGTH_LONG)
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        showFocusModeDurationPrompt()
    }

    private fun showFocusModeDurationPrompt() {
        val durationOptions = listOf(
            getString(R.string.focus_15m) to Constants.FocusModeDuration.FIFTEEN_MIN,
            getString(R.string.focus_30m) to Constants.FocusModeDuration.THIRTY_MIN,
            getString(R.string.focus_1h) to Constants.FocusModeDuration.ONE_HOUR,
            getString(R.string.focus_2h) to Constants.FocusModeDuration.TWO_HOURS,
        )
        val dialogView = layoutInflater.inflate(R.layout.dialog_focus_mode_duration, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogView.findViewById<TextView>(R.id.focusDuration15m).setOnClickListener {
            dialog.dismiss()
            activateFocusMode(durationOptions[0].second)
        }
        dialogView.findViewById<TextView>(R.id.focusDuration30m).setOnClickListener {
            dialog.dismiss()
            activateFocusMode(durationOptions[1].second)
        }
        dialogView.findViewById<TextView>(R.id.focusDuration1h).setOnClickListener {
            dialog.dismiss()
            activateFocusMode(durationOptions[2].second)
        }
        dialogView.findViewById<TextView>(R.id.focusDuration2h).setOnClickListener {
            dialog.dismiss()
            activateFocusMode(durationOptions[3].second)
        }
        dialogView.findViewById<TextView>(R.id.focusDurationCustom).setOnClickListener {
            dialog.dismiss()
            showCustomFocusModePrompt()
        }
        dialogView.findViewById<TextView>(R.id.focusDurationCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showCustomFocusModePrompt() {
        val lastDurationMinutes = (prefs.focusModeLastDuration / Constants.ONE_MINUTE_IN_MILLIS)
            .coerceIn(Constants.MIN_CUSTOM_FOCUS_MINUTES, Constants.MAX_CUSTOM_FOCUS_MINUTES)
        val dialogView = layoutInflater.inflate(R.layout.dialog_focus_mode_custom, null)
        val input = dialogView.findViewById<TextView>(R.id.focusCustomMinutesInput)
        input.text = lastDurationMinutes.toString()
        if (input is android.widget.EditText) {
            input.inputType = InputType.TYPE_CLASS_NUMBER
            input.setSelection(input.text?.length ?: 0)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setOnShowListener {
            dialogView.findViewById<TextView>(R.id.focusCustomCancel).setOnClickListener {
                dialog.dismiss()
            }
            dialogView.findViewById<TextView>(R.id.focusCustomStart).setOnClickListener {
                val customMinutes = input.text?.toString()?.trim()?.toLongOrNull()
                if (customMinutes == null || customMinutes !in Constants.MIN_CUSTOM_FOCUS_MINUTES..Constants.MAX_CUSTOM_FOCUS_MINUTES) {
                    requireContext().showToast(
                        getString(
                            R.string.focus_custom_duration_error,
                            Constants.MIN_CUSTOM_FOCUS_MINUTES.toInt(),
                            Constants.MAX_CUSTOM_FOCUS_MINUTES.toInt()
                        )
                    )
                    return@setOnClickListener
                }
                dialog.dismiss()
                activateFocusMode(customMinutes * Constants.ONE_MINUTE_IN_MILLIS)
            }
        }
        dialog.show()
    }

    private fun activateFocusMode(durationInMillis: Long) {
        if (!PremiumAccess.hasPremiumAccess(prefs)) {
            (requireActivity() as? MainActivity)?.showUpgradeDialog()
            return
        }
        prefs.startFocusMode(durationInMillis)
        syncFocusModeState()
        viewModel.refreshHome(false)
    }

    private fun updateRemindersBellCount() {
        val showReminders = prefs.showRemindersOnHome
        binding.remindersBellContainer.isVisible = showReminders
        if (!showReminders) {
            positionTopCornerStack()
            return
        }
        val count = prefs.remindersJson.toReminderList().count { it.enabled }
        binding.remindersBellCount.isVisible = count > 0
        if (count > 0) {
            binding.remindersBellCount.text = count.toString()
        }
        positionTopCornerStack()
    }

    private fun openReminders() {
        if (!isAdded) return
        if (syncFocusModeState()) {
            requireContext().showToast(R.string.focus_mode_blocked)
            return
        }
        try {
            findNavController().navigate(R.id.action_mainFragment_to_remindersFragment)
        } catch (_: Exception) {
        }
    }

    private fun openHomeSettings() {
        if (!isAdded) return
        if (syncFocusModeState()) {
            requireContext().showToast(R.string.focus_mode_blocked)
            return
        }
        val navController = try {
            findNavController()
        } catch (e: Exception) {
            null
        } ?: return

        if (navController.currentDestination?.id == R.id.settingsFragment) return

        val options = navOptions {
            launchSingleTop = true
        }
        try {
            when (navController.currentDestination?.id) {
                R.id.mainFragment ->
                    navController.navigate(R.id.action_mainFragment_to_settingsFragment, null, options)

                R.id.appListFragment ->
                    navController.navigate(R.id.action_appListFragment_to_settingsFragment2, null, options)

                else ->
                    navController.navigate(R.id.settingsFragment, null, options)
            }
            viewModel.reportOnboardingAction(OnboardingAction.OPEN_SETTINGS)
            viewModel.firstOpen(false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun textOnClick(view: View) = onClick(view)

    private fun textOnLongClick(view: View) = onLongClick(view)

    private fun getSwipeGestureListener(context: Context): View.OnTouchListener {
        return object : OnSwipeTouchListener(context) {
            override fun onSwipeLeft() {
                super.onSwipeLeft()
                if (syncFocusModeState()) return
                openSwipeLeftApp()
            }

            override fun onSwipeRight() {
                super.onSwipeRight()
                if (syncFocusModeState()) return
                openSwipeRightApp()
            }

            override fun onSwipeUp() {
                super.onSwipeUp()
                if (syncFocusModeState()) {
                    requireContext().showToast(R.string.focus_mode_blocked)
                    return
                }
                showAppList(Constants.FLAG_LAUNCH_APP)
            }

            override fun onSwipeDown() {
                super.onSwipeDown()
                if (handleSwipeDownDuringFocusMode()) return
                swipeDownAction()
            }

            override fun onLongClick() {
                super.onLongClick()
                openHomeSettings()
            }

            override fun onDoubleClick() {
                super.onDoubleClick()
                when (prefs.doubleTapAction) {
                    Constants.DoubleTapAction.FOCUS -> startFocusModeFromDoubleTap()
                    else -> {
                        if (!prefs.lockModeOn) return
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                            binding.lock.performClick()
                        else
                            lockPhone()
                    }
                }
            }

            override fun onClick() {
                super.onClick()
                viewModel.checkForMessages.call()
                syncFocusModeState()
            }
        }
    }

    private fun getViewSwipeTouchListener(context: Context, view: View): View.OnTouchListener {
        return object : ViewSwipeTouchListener(context, view) {
            override fun onSwipeLeft() {
                super.onSwipeLeft()
                if (syncFocusModeState()) return
                openSwipeLeftApp()
            }

            override fun onSwipeRight() {
                super.onSwipeRight()
                if (syncFocusModeState()) return
                openSwipeRightApp()
            }

            override fun onSwipeUp() {
                super.onSwipeUp()
                if (syncFocusModeState()) {
                    requireContext().showToast(R.string.focus_mode_blocked)
                    return
                }
                showAppList(Constants.FLAG_LAUNCH_APP)
            }

            override fun onSwipeDown() {
                super.onSwipeDown()
                if (handleSwipeDownDuringFocusMode()) return
                swipeDownAction()
            }

            override fun onLongClick(view: View) {
                super.onLongClick(view)
                textOnLongClick(view)
            }

            override fun onClick(view: View) {
                super.onClick(view)
                textOnClick(view)
            }
        }
    }

    override fun onDestroyView() {
        topCornerStackRunnable?.let { safeBinding?.mainLayout?.removeCallbacks(it) }
        overlayLayoutRunnable?.let { safeBinding?.mainLayout?.removeCallbacks(it) }
        topCornerStackRunnable = null
        overlayLayoutRunnable = null
        stopFocusModeTicker()
        unregisterSystemTimeReceiver()
        super.onDestroyView()
        _binding = null
    }
}
