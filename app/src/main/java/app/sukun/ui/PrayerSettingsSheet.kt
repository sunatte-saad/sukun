package app.sukun.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import app.sukun.MainViewModel
import app.sukun.R
import app.sukun.data.Constants
import app.sukun.data.PrayerLog
import app.sukun.data.Prefs
import app.sukun.databinding.BottomSheetPrayerSettingsBinding
import app.sukun.helper.PrayerReminderScheduler
import app.sukun.helper.getColorFromAttr
import app.sukun.helper.hasWeatherLocationPermission
import app.sukun.helper.hideKeyboard
import app.sukun.helper.isLocationServicesEnabled
import app.sukun.helper.showKeyboard
import app.sukun.helper.showToast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PrayerSettingsSheet : DialogFragment() {

    interface Listener {
        fun onPrayerSettingsChanged()
    }

    private var _binding: BottomSheetPrayerSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private var listener: Listener? = null

    private val prayers = listOf(
        Constants.Prayer.FAJR,
        Constants.Prayer.DHUHR,
        Constants.Prayer.ASR,
        Constants.Prayer.MAGHRIB,
        Constants.Prayer.ISHA,
    )

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
                    || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                prefs.prayerSourceMode = Constants.PrayerSource.DEVICE
                prefs.clearPrayerCache()
                refreshPrayer(forceLocationRefresh = true, promptForAlarmPermission = true)
                updateUI()
                listener?.onPrayerSettingsChanged()
            } else {
                requireContext().showToast(R.string.prayer_permission_needed)
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    private val customAzanPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {}
            prefs.azanSound = Constants.AzanSound.CUSTOM
            prefs.azanCustomUri = uri.toString()
            prefs.azanEnabled = true
            updateAzanChips()
            updateCustomAzanRow()
            refreshPrayer(promptForAlarmPermission = true)
            requireContext().showToast(R.string.prayer_azan_saved)
            listener?.onPrayerSettingsChanged()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, 0)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetPrayerSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        setupClickListeners()
        updateUI()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setGravity(Gravity.BOTTOM)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setDimAmount(0.4f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.setBackgroundBlurRadius(40)
                val params = window.attributes
                params.blurBehindRadius = 40
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                window.attributes = params
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    private fun setupClickListeners() {
        binding.prayerToggleRow.setOnClickListener { togglePrayer() }
        binding.chipManual.setOnClickListener { selectManualSource() }
        binding.chipDevice.setOnClickListener { selectDeviceSource() }
        binding.locationValueLabel.setOnClickListener { openLocationEditor() }
        binding.btnSaveLocation.setOnClickListener { saveLocation() }
        binding.btnCloseLocation.setOnClickListener {
            binding.locationEditLayout.isVisible = false
            binding.etLocation.hideKeyboard()
        }
        binding.chipAzanOff.setOnClickListener { selectAzan(Constants.AzanSound.OFF) }
        binding.chipAzanMakkah.setOnClickListener { selectAzan(Constants.AzanSound.MAKKAH) }
        binding.chipAzanMarylebone.setOnClickListener { selectAzan(Constants.AzanSound.MARYLEBONE) }
        binding.chipAzanCustom.setOnClickListener { selectAzan(Constants.AzanSound.CUSTOM) }
        binding.customAzanRow.setOnClickListener {
            if (prefs.showPrayerOnHome) customAzanPickerLauncher.launch(arrayOf("audio/*"))
        }
    }

    private fun updateUI() {
        val isOn = prefs.showPrayerOnHome
        binding.prayerToggle.text = getString(if (isOn) R.string.on else R.string.off)
        binding.prayerSubSettings.isVisible = isOn
        if (isOn) {
            updateSourceChips()
            updateLocationSection()
            updateAzanChips()
            updateCustomAzanRow()
        }
        val hasLogs = prefs.getPrayerLogs().isNotEmpty()
        val showAnalytics = isOn || hasLogs
        binding.analyticsDivider.isVisible = showAnalytics
        binding.analyticsSection.isVisible = showAnalytics
        if (showAnalytics) populateAnalytics()
    }

    private fun togglePrayer() {
        if (!prefs.showPrayerOnHome && !prefs.isProUser) {
            requireContext().showToast(R.string.premium_feature_requires_upgrade)
            return
        }
        prefs.showPrayerOnHome = !prefs.showPrayerOnHome
        updateUI()
        if (prefs.showPrayerOnHome) {
            requestNotificationPermissionIfNeeded()
            when (prefs.prayerSourceMode) {
                Constants.PrayerSource.DEVICE -> {
                    if (requireContext().hasWeatherLocationPermission()) {
                        refreshPrayer(forceLocationRefresh = true, promptForAlarmPermission = true)
                    } else {
                        selectDeviceSource()
                    }
                }
                else -> {
                    if (prefs.prayerLocationQuery.isBlank()) {
                        requireContext().showToast(R.string.prayer_location_required)
                    } else {
                        refreshPrayer(promptForAlarmPermission = true)
                    }
                }
            }
        } else {
            refreshPrayer()
        }
        listener?.onPrayerSettingsChanged()
    }

    private fun updateSourceChips() {
        val isDevice = prefs.prayerSourceMode == Constants.PrayerSource.DEVICE
        setChipState(binding.chipManual, !isDevice)
        setChipState(binding.chipDevice, isDevice)
    }

    private fun updateLocationSection() {
        val source = prefs.prayerSourceMode
        binding.locationSection.isVisible = true
        if (source == Constants.PrayerSource.DEVICE) {
            binding.locationEditLayout.isVisible = false
            binding.locationValueLabel.text =
                prefs.prayerLocationLabel.ifBlank { getString(R.string.current_location) }
        } else {
            binding.locationValueLabel.text = when {
                prefs.prayerLocationQuery.isBlank() -> getString(R.string.not_set)
                prefs.prayerLocationLabel.isNotBlank() -> prefs.prayerLocationLabel
                else -> prefs.prayerLocationQuery
            }
        }
    }

    private fun updateAzanChips() {
        val sound = prefs.azanSound
        setChipState(binding.chipAzanOff, sound == Constants.AzanSound.OFF)
        setChipState(binding.chipAzanMakkah, sound == Constants.AzanSound.MAKKAH)
        setChipState(binding.chipAzanMarylebone, sound == Constants.AzanSound.MARYLEBONE)
        setChipState(binding.chipAzanCustom, sound == Constants.AzanSound.CUSTOM)
    }

    private fun updateCustomAzanRow() {
        binding.customAzanRow.isVisible = prefs.azanSound == Constants.AzanSound.CUSTOM
        binding.customAzanFile.text = if (prefs.azanCustomUri.isBlank())
            getString(R.string.not_set)
        else
            getString(R.string.change)
    }

    private fun setChipState(chip: TextView, selected: Boolean) {
        chip.setBackgroundResource(
            if (selected) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected
        )
        chip.setTextColor(
            requireContext().getColorFromAttr(
                if (selected) R.attr.primaryInverseColor else R.attr.primaryColor
            )
        )
    }

    private fun selectSource(source: String) {
        if (source == Constants.PrayerSource.MANUAL) {
            selectManualSource()
            return
        }
        if (prefs.prayerSourceMode == source) return
        prefs.prayerSourceMode = source
        prefs.clearPrayerCache()
        refreshPrayer(forceLocationRefresh = source == Constants.PrayerSource.DEVICE)
        updateSourceChips()
        updateLocationSection()
        listener?.onPrayerSettingsChanged()
    }

    private fun selectManualSource() {
        if (prefs.prayerSourceMode != Constants.PrayerSource.MANUAL) {
            prefs.prayerSourceMode = Constants.PrayerSource.MANUAL
            prefs.clearPrayerCache()
            updateSourceChips()
            updateLocationSection()
            listener?.onPrayerSettingsChanged()
        }
        openLocationEditor()
    }

    private fun selectDeviceSource() {
        if (requireContext().hasWeatherLocationPermission()) {
            selectSource(Constants.PrayerSource.DEVICE)
            return
        }
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun openLocationEditor() {
        when (prefs.prayerSourceMode) {
            Constants.PrayerSource.DEVICE -> {
                if (!requireContext().hasWeatherLocationPermission()) {
                    locationPermissionLauncher.launch(
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
            Constants.PrayerSource.MANUAL -> {
                binding.locationEditLayout.isVisible = true
                val prefill = prefs.prayerLocationQuery.ifBlank { prefs.prayerLocationLabel }
                binding.etLocation.setText(prefill)
                binding.etLocation.setSelection(binding.etLocation.text?.length ?: 0)
                binding.etLocation.showKeyboard()
            }
        }
    }

    private fun saveLocation() {
        val query = binding.etLocation.text?.toString()?.trim().orEmpty()
        if (query.isBlank()) {
            requireContext().showToast(R.string.prayer_location_required)
            binding.etLocation.showKeyboard()
            return
        }
        prefs.prayerLocationQuery = query
        prefs.prayerLocationLabel = query
        prefs.prayerLatitude = ""
        prefs.prayerLongitude = ""
        prefs.clearPrayerCache()
        binding.locationEditLayout.isVisible = false
        binding.etLocation.hideKeyboard()
        refreshPrayer()
        updateLocationSection()
        listener?.onPrayerSettingsChanged()
        requireContext().showToast(R.string.prayer_saved)

        if (prefs.showWeatherOnHome && prefs.weatherSourceMode == Constants.WeatherSource.MANUAL
            && prefs.weatherLocationQuery.isBlank()
        ) {
            AlertDialog.Builder(requireContext())
                .setMessage(R.string.use_same_location_for_weather)
                .setPositiveButton(R.string.yes) { _, _ ->
                    prefs.weatherLocationQuery = query
                    prefs.weatherLocationLabel = query
                    prefs.clearWeatherCache()
                    listener?.onPrayerSettingsChanged()
                }
                .setNegativeButton(R.string.no, null)
                .show()
        }
    }

    private fun selectAzan(sound: String) {
        if (sound == Constants.AzanSound.CUSTOM) {
            customAzanPickerLauncher.launch(arrayOf("audio/*"))
            return
        }
        if (prefs.azanSound == sound) return
        prefs.azanSound = sound
        prefs.azanEnabled = sound != Constants.AzanSound.OFF
        updateAzanChips()
        updateCustomAzanRow()
        if (prefs.azanEnabled) ensureExactAlarmPermissionIfNeeded()
        refreshPrayer(promptForAlarmPermission = true)
        listener?.onPrayerSettingsChanged()
    }

    private fun refreshPrayer(
        forceLocationRefresh: Boolean = false,
        promptForAlarmPermission: Boolean = false,
    ) {
        if (!prefs.showPrayerOnHome) {
            viewModel.cancelPrayerReminder(clearCachedPrayer = true)
            return
        }
        if (prefs.prayerSourceMode == Constants.PrayerSource.MANUAL && prefs.prayerLocationQuery.isBlank()) {
            viewModel.cancelPrayerReminder(clearCachedPrayer = true)
            return
        }
        val canRefresh = when (prefs.prayerSourceMode) {
            Constants.PrayerSource.DEVICE -> requireContext().hasWeatherLocationPermission()
            else -> prefs.prayerLocationQuery.isNotBlank()
        }
        if (canRefresh) {
            if (promptForAlarmPermission) ensureExactAlarmPermissionIfNeeded()
            viewModel.refreshPrayerData(forceLocationRefresh = forceLocationRefresh)
        } else {
            viewModel.cancelPrayerReminder(clearCachedPrayer = true)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun ensureExactAlarmPermissionIfNeeded() {
        if (PrayerReminderScheduler.canScheduleExactPrayerReminders(requireContext())) return
        requireContext().showToast(R.string.prayer_exact_alarm_permission_needed, Toast.LENGTH_LONG)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${requireContext().packageName}")
                )
            )
        }
    }

    // ── Analytics ──────────────────────────────────────────────────────────

    private fun populateAnalytics() {
        val logs = prefs.getPrayerLogs()
        val cal = Calendar.getInstance()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val yearPrefix = String.format("%04d", cal.get(Calendar.YEAR))
        val monthPrefix = String.format("%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())

        val todayLogs = logs.filter { it.dateKey == today }.map { it.prayerKey }.toSet()
        val daysElapsedMonth = cal.get(Calendar.DAY_OF_MONTH)
        val daysElapsedYear = cal.get(Calendar.DAY_OF_YEAR)

        binding.tvTodayStats.text = buildTodayDashboard(todayLogs)
        binding.tvMonthHeader.text = monthName
        binding.tvMonthStats.text = buildPrayerCounts(logs.filter { it.dateKey.startsWith(monthPrefix) }, daysElapsedMonth, padStart = 2)
        binding.tvYearHeader.text = yearPrefix
        binding.tvYearStats.text = buildPrayerCounts(logs.filter { it.dateKey.startsWith(yearPrefix) }, daysElapsedYear, padStart = 3)
    }

    private fun buildTodayDashboard(prayedKeys: Set<String>): String {
        return buildString {
            prayers.forEach { key ->
                val name = getPrayerDisplayName(key).padEnd(8)
                val mark = if (key in prayedKeys) "✓" else "·"
                appendLine("$name $mark")
            }
        }
    }

    private fun buildPrayerCounts(logs: List<PrayerLog>, daysElapsed: Int, padStart: Int): String {
        return buildString {
            prayers.forEach { key ->
                val daysPrayed = logs.filter { it.prayerKey == key }.map { it.dateKey }.toSet().size
                val name = getPrayerDisplayName(key).padEnd(8)
                val count = daysPrayed.toString().padStart(padStart)
                val total = daysElapsed.toString().padStart(padStart)
                appendLine("$name $count / $total")
            }
        }
    }

    private fun getPrayerDisplayName(key: String) = getString(
        when (key) {
            Constants.Prayer.FAJR -> R.string.prayer_fajr
            Constants.Prayer.DHUHR -> R.string.prayer_dhuhr
            Constants.Prayer.ASR -> R.string.prayer_asr
            Constants.Prayer.MAGHRIB -> R.string.prayer_maghrib
            Constants.Prayer.ISHA -> R.string.prayer_isha
            else -> R.string.prayer_time_now
        }
    )

    companion object {
        const val TAG = "PrayerSettingsSheet"
        fun newInstance() = PrayerSettingsSheet()
    }
}
