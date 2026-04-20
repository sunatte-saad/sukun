package app.sukun.ui

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.ColorDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import app.sukun.MainViewModel
import app.sukun.R
import app.sukun.data.AppModel
import app.sukun.data.Constants
import app.sukun.data.Prefs
import app.sukun.data.PrayerState
import app.sukun.data.WeatherData
import app.sukun.databinding.FragmentHomeBinding
import app.sukun.helper.appUsagePermissionGranted
import app.sukun.helper.canOpenNotificationsInFocusMode
import app.sukun.helper.dpToPx
import app.sukun.helper.expandNotificationDrawer
import app.sukun.helper.getFocusModeStatus
import app.sukun.helper.getChangedAppTheme
import app.sukun.helper.getUserHandleFromString
import app.sukun.helper.isAccessServiceEnabled
import app.sukun.helper.isPackageInstalled
import app.sukun.helper.openAlarmApp
import app.sukun.helper.openCalendar
import app.sukun.helper.openCameraApp
import app.sukun.helper.openDialerApp
import app.sukun.helper.openSearch
import app.sukun.helper.setPlainWallpaperByTheme
import app.sukun.helper.showToast
import app.sukun.helper.toOverlayText
import app.sukun.listener.OnSwipeTouchListener
import app.sukun.listener.ViewSwipeTouchListener
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
    private var currentPrayerState: PrayerState? = null
    private var defaultHomeAppsPaddingTop = 0
    private var defaultHomeAppsPaddingBottom = 0

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

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

        initObservers()
        setHomeAlignment(prefs.homeAlignment)
        initSwipeTouchListener()
        initClickListeners()
    }

    override fun onResume() {
        super.onResume()
        syncFocusModeState()
        populateHomeScreen(false)
        viewModel.loadWeather()
        viewModel.loadPrayerState()
        viewModel.isSukunDefault()
    }

    override fun onPause() {
        stopFocusModeTicker()
        stopPrayerTicker()
        stopDateTimeTicker()
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
            R.id.setDefaultLauncher -> viewModel.resetLauncherLiveData.call()
            R.id.tvScreenTime -> openScreenTimeDigitalWellbeing()
            R.id.dailyNotesCard -> showDailyNotesEditor()

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

            R.id.tvScreenTime -> {
                showAppList(Constants.FLAG_SET_SCREEN_TIME_APP)
                prefs.screenTimeAppPackage = ""
                prefs.screenTimeAppClassName = ""
                prefs.screenTimeAppUser = ""
            }

            R.id.setDefaultLauncher -> {
                prefs.hideSetDefaultLauncher = true
                binding.setDefaultLauncher.visibility = View.GONE
                if (viewModel.isSukunDefault.value != true) {
                    requireContext().showToast(R.string.set_as_default_launcher)
                    findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
                }
            }
        }
        return true
    }

    private fun initObservers() {
        if (prefs.firstSettingsOpen) {
            binding.firstRunTips.visibility = View.VISIBLE
            binding.setDefaultLauncher.visibility = View.GONE
        } else binding.firstRunTips.visibility = View.GONE

        viewModel.refreshHome.observe(viewLifecycleOwner) {
            populateHomeScreen(it)
        }
        viewModel.isSukunDefault.observe(viewLifecycleOwner, Observer {
            if (it != true) {
                if (prefs.dailyWallpaper && prefs.appTheme == AppCompatDelegate.MODE_NIGHT_YES) {
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
            it?.let { binding.tvScreenTime?.text = it }
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
        binding.mainLayout.setOnTouchListener(getSwipeGestureListener(context))
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
        binding.setDefaultLauncher.setOnClickListener(this)
        binding.setDefaultLauncher.setOnLongClickListener(this)
        binding.tvScreenTime?.setOnClickListener(this)
        binding.tvScreenTime?.setOnLongClickListener(this)
        binding.dailyNotesCard.setOnClickListener(this)
    }

    private fun setHomeAlignment(horizontalGravity: Int = prefs.homeAlignment) {
        val verticalGravity = if (prefs.homeBottomAlignment) Gravity.BOTTOM else Gravity.CENTER_VERTICAL
        binding.homeAppsLayout.gravity = horizontalGravity or verticalGravity
        binding.dateTimeLayout.gravity = horizontalGravity
        binding.weatherText.gravity = horizontalGravity
        binding.prayerText.gravity = horizontalGravity
        binding.focusModeStatus.gravity = horizontalGravity
        binding.homeApp1.gravity = horizontalGravity
        binding.homeApp2.gravity = horizontalGravity
        binding.homeApp3.gravity = horizontalGravity
        binding.homeApp4.gravity = horizontalGravity
        binding.homeApp5.gravity = horizontalGravity
        binding.homeApp6.gravity = horizontalGravity
        binding.homeApp7.gravity = horizontalGravity
        binding.homeApp8.gravity = horizontalGravity
        positionOverlayText(horizontalGravity)
    }

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
        updateDateTimeDisplay()
        if (binding.dateTimeLayout.isVisible) startDateTimeTicker()
        else stopDateTimeTicker()
        positionOverlayText()
    }

    private fun updateDateTimeDisplay() {
        if (!binding.dateTimeLayout.isVisible) return
        val defaultDateFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
        var defaultDateText = defaultDateFormat.format(Date())

        if (!prefs.showStatusBar && prefs.clockStyle != Constants.ClockStyle.DAY_RING) {
            val battery = (requireContext().getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (battery > 0) {
                defaultDateText = getString(R.string.day_battery, defaultDateText, battery)
            }
        }

        binding.date?.text = defaultDateText.replace(".,", ",")
        binding.ringDate.text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())
            .replace(".,", ",")
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
        if (!prefs.showWeatherOnHome || weather == null) {
            binding.weatherText?.visibility = View.GONE
            positionOverlayText()
            return
        }
        binding.weatherText?.text = weather.displayText
        binding.weatherText?.visibility = View.VISIBLE
        positionOverlayText()
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
        positionOverlayText()
        startPrayerTicker()
    }

    private fun populateDailyNotes() {
        val formattedNotes = formatDailyNotes(prefs.dailyNotesList)
        val showNotes = prefs.showDailyNotesOnHome
        binding.dailyNotesCard.isVisible = showNotes
        if (showNotes) {
            val isEmpty = formattedNotes.isBlank()
            binding.dailyNotesText.text = if (isEmpty) {
                getString(R.string.daily_notes_empty_hint)
            } else {
                formattedNotes
            }
            binding.dailyNotesText.alpha = if (isEmpty) 0.75f else 1f
        }
        positionOverlayText()
    }

    private fun formatDailyNotes(rawNotes: String): String {
        return rawNotes
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n") { note ->
                if (note.startsWith("- ") || note.startsWith("* ") || note.matches(Regex("\\d+\\..*"))) {
                    note
                } else {
                    "- $note"
                }
            }
    }

    private fun updateWeatherLayout(horizontalGravity: Int = prefs.homeAlignment) {
        positionOverlayText(horizontalGravity)
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
                populateDailyNotes()
                requireContext().showToast(R.string.daily_notes_saved)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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

    private fun positionOverlayText(horizontalGravity: Int = prefs.homeAlignment) {
        binding.mainLayout.post {
            val spacing = 12.dpToPx()
            val weatherTopMargin = getDateTimeBottom()
                ?.plus(spacing)
                ?: 56.dpToPx()
            updateOverlayLayout(binding.weatherText, horizontalGravity, weatherTopMargin)

            val prayerTopMargin = if (binding.weatherText?.isVisible == true) {
                val weatherHeight = binding.weatherText?.height ?: 0
                weatherTopMargin + weatherHeight + spacing
            } else {
                weatherTopMargin
            }
            updateOverlayLayout(binding.prayerText, horizontalGravity, prayerTopMargin)

            val focusTopMargin = if (binding.prayerText?.isVisible == true) {
                val prayerHeight = binding.prayerText?.height ?: 0
                prayerTopMargin + prayerHeight + spacing
            } else if (binding.weatherText?.isVisible == true) {
                val weatherHeight = binding.weatherText?.height ?: 0
                weatherTopMargin + weatherHeight + spacing
            } else {
                weatherTopMargin
            }
            updateOverlayLayout(binding.focusModeStatus, horizontalGravity, focusTopMargin)

            val overlayBottom = when {
                binding.focusModeStatus.isVisible -> focusTopMargin + binding.focusModeStatus.height
                binding.prayerText.isVisible -> prayerTopMargin + binding.prayerText.height
                binding.weatherText.isVisible -> weatherTopMargin + binding.weatherText.height
                else -> getDateTimeBottom() ?: 0
            }
            val screenTimeBottom = if (binding.tvScreenTime?.isVisible == true) {
                binding.tvScreenTime.top + binding.tvScreenTime.height
            } else {
                0
            }
            val notesTopMargin = max(max(overlayBottom, screenTimeBottom) + spacing, 56.dpToPx())
            updateDailyNotesLayout(notesTopMargin)
        }
    }

    private fun getDateTimeBottom(): Int? {
        if (!binding.dateTimeLayout.isVisible) return null
        return binding.dateTimeLayout.top + binding.dateTimeLayout.height
    }

    private fun updateOverlayLayout(
        view: TextView?,
        horizontalGravity: Int,
        topMargin: Int,
    ) {
        val overlayView = view ?: return
        val params = overlayView.layoutParams as? FrameLayout.LayoutParams ?: return
        params.gravity = Gravity.TOP or horizontalGravity
        params.topMargin = topMargin
        overlayView.layoutParams = params
    }

    private fun updateDailyNotesLayout(topMargin: Int) {
        val params = binding.dailyNotesCard.layoutParams as? FrameLayout.LayoutParams ?: return
        params.gravity = Gravity.TOP or Gravity.END
        params.topMargin = topMargin
        binding.dailyNotesCard.layoutParams = params
        updateHomeAppsTopPadding(topMargin)
    }

    private fun updateHomeAppsTopPadding(notesTopMargin: Int) {
        val notesBottom = if (binding.dailyNotesCard.isVisible) {
            notesTopMargin + binding.dailyNotesCard.height + 12.dpToPx()
        } else {
            defaultHomeAppsPaddingTop
        }
        binding.homeAppsLayout.setPadding(
            binding.homeAppsLayout.paddingLeft,
            max(defaultHomeAppsPaddingTop, notesBottom),
            binding.homeAppsLayout.paddingRight,
            defaultHomeAppsPaddingBottom
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
                delay(30000)
            }
            dateTimeJob = null
        }
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
        if (requireContext().appUsagePermissionGranted().not()) return

        viewModel.getTodaysScreenTime()
        binding.tvScreenTime?.visibility = View.VISIBLE

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val horizontalMargin = if (isLandscape) 64.dpToPx() else 10.dpToPx()
        val marginTop = if (isLandscape) {
            if (prefs.dateTimeVisibility == Constants.DateTime.DATE_ONLY) 36.dpToPx() else 56.dpToPx()
        } else {
            if (prefs.dateTimeVisibility == Constants.DateTime.DATE_ONLY) 45.dpToPx() else 72.dpToPx()
        }
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = marginTop
            marginStart = horizontalMargin
            marginEnd = horizontalMargin
            gravity = if (prefs.homeAlignment == Gravity.END) Gravity.START else Gravity.END
        }
        binding.tvScreenTime?.layoutParams = params
        binding.tvScreenTime?.setPadding(10.dpToPx())
        positionOverlayText()
    }

    private fun populateHomeScreen(appCountUpdated: Boolean) {
        if (appCountUpdated) hideHomeApps()
        populateDateTime()
        populateDailyNotes()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            populateScreenTime()

        val homeAppsNum = prefs.homeAppsNum
        if (homeAppsNum == 0) return

        binding.homeApp1.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp1, prefs.appName1, prefs.appPackage1, prefs.appActivityClassName1, prefs.appUser1, prefs.isShortcut1, prefs.shortcutId1)) {
            prefs.appName1 = ""
            prefs.appPackage1 = ""
        }
        if (homeAppsNum == 1) return

        binding.homeApp2.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp2, prefs.appName2, prefs.appPackage2, prefs.appActivityClassName2, prefs.appUser2, prefs.isShortcut2, prefs.shortcutId2)) {
            prefs.appName2 = ""
            prefs.appPackage2 = ""
        }
        if (homeAppsNum == 2) return

        binding.homeApp3.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp3, prefs.appName3, prefs.appPackage3, prefs.appActivityClassName3, prefs.appUser3, prefs.isShortcut3, prefs.shortcutId3)) {
            prefs.appName3 = ""
            prefs.appPackage3 = ""
        }
        if (homeAppsNum == 3) return

        binding.homeApp4.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp4, prefs.appName4, prefs.appPackage4, prefs.appActivityClassName4, prefs.appUser4, prefs.isShortcut4, prefs.shortcutId4)) {
            prefs.appName4 = ""
            prefs.appPackage4 = ""
        }
        if (homeAppsNum == 4) return

        binding.homeApp5.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp5, prefs.appName5, prefs.appPackage5, prefs.appActivityClassName5, prefs.appUser5, prefs.isShortcut5, prefs.shortcutId5)) {
            prefs.appName5 = ""
            prefs.appPackage5 = ""
        }
        if (homeAppsNum == 5) return

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
        val iconSize = 24.dpToPx()
        drawable.setBounds(0, 0, iconSize, iconSize)
        textView.setCompoundDrawablesRelative(drawable, null, null, null)
        textView.compoundDrawablePadding = 12.dpToPx()
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
        viewModel.selectedApp(
            AppModel.App(
                appLabel = appName,
                key = null,
                appPackage = packageName,
                activityClassName = activityClassName,
                isNew = false,
                user = getUserHandleFromString(requireContext(), userString)
            ),
            Constants.FLAG_LAUNCH_APP
        )
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
        viewModel.getAppList(includeHiddenApps)
        try {
            findNavController().navigate(
                R.id.action_mainFragment_to_appListFragment,
                bundleOf(
                    Constants.Key.FLAG to flag,
                    Constants.Key.RENAME to rename
                )
            )
        } catch (e: Exception) {
            findNavController().navigate(
                R.id.appListFragment,
                bundleOf(
                    Constants.Key.FLAG to flag,
                    Constants.Key.RENAME to rename
                )
            )
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

    private fun updateStatusBarVisibility(isFocusModeActive: Boolean = prefs.isFocusModeActive()) {
        if (isFocusModeActive || !prefs.showStatusBar) {
            hideStatusBar()
        } else {
            showStatusBar()
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
        requireActivity().recreate()
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
        prefs.startFocusMode(durationInMillis)
        syncFocusModeState()
        viewModel.refreshHome(false)
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
                if (syncFocusModeState()) {
                    requireContext().showToast(R.string.focus_mode_blocked)
                    return
                }
                try {
                    findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
                    viewModel.firstOpen(false)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
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
        stopFocusModeTicker()
        super.onDestroyView()
        _binding = null
    }
}