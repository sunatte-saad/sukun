package sukun.minimalist.app.launcher.com.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import sukun.minimalist.app.launcher.com.data.Constants
import sukun.minimalist.app.launcher.com.R
import sukun.minimalist.app.launcher.com.data.Prefs
import sukun.minimalist.app.launcher.com.databinding.FragmentPrayerAnalyticsBinding
import sukun.minimalist.app.launcher.com.helper.sync.AnalyticsRollupManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PrayerAnalyticsFragment : Fragment() {

    private var _binding: FragmentPrayerAnalyticsBinding? = null
    private val binding get() = _binding!!

    private val prayers = Constants.Prayer.ALL

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPrayerAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        AnalyticsRollupManager.ensureCurrent(requireContext())
        populate()
    }

    private fun populate() {
        val prefs = Prefs(requireContext())
        val cal = Calendar.getInstance()
        val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
        val yearPrefix = String.format("%04d", cal.get(Calendar.YEAR))
        val daysElapsedMonth = cal.get(Calendar.DAY_OF_MONTH)
        val daysElapsedYear = cal.get(Calendar.DAY_OF_YEAR)

        val todayKeys = AnalyticsRollupManager.todayPrayerKeys(prefs)
        val monthLogs = AnalyticsRollupManager.monthPrayerLogs(prefs)
        val yearCounts = AnalyticsRollupManager.yearPrayerDayCounts(prefs)

        binding.tvTodayStats.text = buildTodayDashboard(todayKeys)
        binding.tvMonthHeader.text = monthName
        binding.tvMonthStats.text = buildPrayerCountsFromLogs(monthLogs, daysElapsedMonth, padStart = 2)
        binding.tvYearHeader.text = yearPrefix
        binding.tvYearStats.text = buildPrayerCountsFromAnnual(yearCounts, daysElapsedYear, padStart = 3)
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

    private fun buildPrayerCountsFromLogs(
        logs: List<sukun.minimalist.app.launcher.com.data.PrayerLog>,
        daysElapsed: Int,
        padStart: Int,
    ): String {
        return buildString {
            prayers.forEach { key ->
                val daysPrayed = logs.count { it.prayerKey == key }
                val name = getPrayerDisplayName(key).padEnd(8)
                appendLine("$name ${daysPrayed.toString().padStart(padStart)} / ${daysElapsed.toString().padStart(padStart)}")
            }
        }
    }

    private fun buildPrayerCountsFromAnnual(
        counts: Map<String, Int>,
        daysElapsed: Int,
        padStart: Int,
    ): String {
        return buildString {
            prayers.forEach { key ->
                val daysPrayed = counts[key] ?: 0
                val name = getPrayerDisplayName(key).padEnd(8)
                appendLine("$name ${daysPrayed.toString().padStart(padStart)} / ${daysElapsed.toString().padStart(padStart)}")
            }
        }
    }

    private fun getPrayerDisplayName(key: String): String {
        return getString(
            when (key) {
                Constants.Prayer.FAJR -> R.string.prayer_fajr
                Constants.Prayer.DHUHR -> R.string.prayer_dhuhr
                Constants.Prayer.ASR -> R.string.prayer_asr
                Constants.Prayer.MAGHRIB -> R.string.prayer_maghrib
                Constants.Prayer.ISHA -> R.string.prayer_isha
                else -> R.string.prayer_time_now
            },
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
