package sukun.minimalist.app.launcher.com.ui

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
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import sukun.minimalist.app.launcher.com.MainViewModel
import sukun.minimalist.app.launcher.com.R
import sukun.minimalist.app.launcher.com.data.Constants
import sukun.minimalist.app.launcher.com.data.PrayerLog
import sukun.minimalist.app.launcher.com.data.Prefs
import sukun.minimalist.app.launcher.com.databinding.BottomSheetPrayerSettingsBinding
import sukun.minimalist.app.launcher.com.MainActivity
import sukun.minimalist.app.launcher.com.helper.PremiumAccess
import sukun.minimalist.app.launcher.com.helper.PrayerReminderScheduler
import sukun.minimalist.app.launcher.com.helper.downloadAzan
import sukun.minimalist.app.launcher.com.helper.getColorFromAttr
import sukun.minimalist.app.launcher.com.helper.hasWeatherLocationPermission
import sukun.minimalist.app.launcher.com.helper.isAzanCached
import sukun.minimalist.app.launcher.com.helper.isBundledAzanSound
import sukun.minimalist.app.launcher.com.helper.isNetworkAvailable
import sukun.minimalist.app.launcher.com.helper.scheduleAzanDownloadWhenOnline
import sukun.minimalist.app.launcher.com.helper.showToast
import kotlinx.coroutines.launch
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
        binding.chipAzanOff.setOnClickListener { selectAzan(Constants.AzanSound.OFF) }
        binding.chipAzanMakkah.setOnClickListener { selectAzan(Constants.AzanSound.MAKKAH) }
        binding.chipAzanMarylebone.setOnClickListener { selectAzan(Constants.AzanSound.MARYLEBONE) }
        binding.chipAzanCustom.setOnClickListener { selectAzan(Constants.AzanSound.CUSTOM) }
        binding.customAzanRow.setOnClickListener {
            if (prefs.showPrayerOnHome && requirePremiumAccess()) {
                customAzanPickerLauncher.launch(arrayOf("audio/*"))
            }
        }
    }

    private fun updateUI() {
        val isOn = prefs.showPrayerOnHome
        binding.prayerToggle.text = getString(if (isOn) R.string.on else R.string.off)
        binding.prayerSubSettings.isVisible = isOn
        if (isOn) {
            updateAzanChips()
            updateCustomAzanRow()
            ensureAzanDownloaded()
        }
        val hasLogs = prefs.getPrayerLogs().isNotEmpty()
        val showAnalytics = isOn || hasLogs
        binding.analyticsDivider.isVisible = showAnalytics
        binding.analyticsSection.isVisible = showAnalytics
        if (showAnalytics) populateAnalytics()
        applyPremiumVisuals()
    }

    private fun applyPremiumVisuals() {
        val alpha = PremiumAccess.lockedAlpha(prefs)
        binding.analyticsSection.alpha = alpha
        binding.chipAzanCustom.alpha = alpha
        binding.customAzanRow.alpha = alpha
    }

    private fun requirePremiumAccess(): Boolean {
        if (PremiumAccess.hasPremiumAccess(prefs)) return true
        if (PremiumAccess.trialExpired(prefs)) {
            requireContext().showToast(R.string.premium_trial_ended)
        } else {
            requireContext().showToast(R.string.premium_feature_requires_upgrade)
        }
        (requireActivity() as? MainActivity)?.showUpgradeDialog()
        return false
    }

    private fun togglePrayer() {
        if (!prefs.showPrayerOnHome && !requirePremiumAccess()) return
        prefs.showPrayerOnHome = !prefs.showPrayerOnHome
        updateUI()
        if (prefs.showPrayerOnHome) {
            requestNotificationPermissionIfNeeded()
            refreshPrayer(promptForAlarmPermission = true)
        } else {
            refreshPrayer()
        }
        listener?.onPrayerSettingsChanged()
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

    private fun selectAzan(sound: String) {
        if (sound == Constants.AzanSound.CUSTOM) {
            if (requirePremiumAccess()) customAzanPickerLauncher.launch(arrayOf("audio/*"))
            return
        }
        if (prefs.azanSound == sound) return
        prefs.azanSound = sound
        prefs.azanEnabled = sound != Constants.AzanSound.OFF
        updateAzanChips()
        updateCustomAzanRow()
        if (prefs.azanEnabled) {
            ensureExactAlarmPermissionIfNeeded()
            requestAzanDownload(sound)
        }
        refreshPrayer(promptForAlarmPermission = true)
        listener?.onPrayerSettingsChanged()
    }

    private fun ensureAzanDownloaded() {
        val sound = prefs.azanSound
        if (!prefs.azanEnabled || !isBundledAzanSound(sound) || isAzanCached(requireContext(), sound)) return
        requestAzanDownload(sound, showOnlineToast = false)
    }

    private fun requestAzanDownload(sound: String, showOnlineToast: Boolean = true) {
        if (!isBundledAzanSound(sound)) return
        viewLifecycleOwner.lifecycleScope.launch {
            val downloaded = downloadAzan(requireContext(), sound)
            when {
                downloaded && showOnlineToast ->
                    requireContext().showToast(R.string.azan_audio_downloaded)
                downloaded -> Unit
                requireContext().isNetworkAvailable() ->
                    requireContext().showToast(R.string.azan_download_failed, Toast.LENGTH_LONG)
                else -> {
                    scheduleAzanDownloadWhenOnline(requireContext(), sound)
                    requireContext().showToast(R.string.azan_will_download_when_online, Toast.LENGTH_LONG)
                }
            }
        }
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
        if (!PremiumAccess.hasPremiumAccess(prefs)) return
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
