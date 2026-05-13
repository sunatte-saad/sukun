package app.sukun.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import app.sukun.R
import app.sukun.data.Constants
import app.sukun.data.PrayerLog
import app.sukun.data.Prefs
import app.sukun.databinding.FragmentPrayerAnalyticsBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PrayerAnalyticsFragment : Fragment() {

    private var _binding: FragmentPrayerAnalyticsBinding? = null
    private val binding get() = _binding!!

    private val prayers = listOf(
        Constants.Prayer.FAJR,
        Constants.Prayer.DHUHR,
        Constants.Prayer.ASR,
        Constants.Prayer.MAGHRIB,
        Constants.Prayer.ISHA,
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPrayerAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        populate()
    }

    private fun populate() {
        val prefs = Prefs(requireContext())
        val logs = prefs.getPrayerLogs()
        val cal = Calendar.getInstance()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val yearPrefix = String.format("%04d", cal.get(Calendar.YEAR))
        val monthPrefix = String.format("%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())

        // TODAY
        val todayLogs = logs.filter { it.dateKey == today }.map { it.prayerKey }.toSet()
        binding.tvTodayStats.text = buildTodayText(todayLogs)

        // THIS MONTH
        val daysElapsedMonth = cal.get(Calendar.DAY_OF_MONTH)
        val monthLogs = logs.filter { it.dateKey.startsWith(monthPrefix) }
        binding.tvMonthHeader.text = getString(R.string.prayer_this_month, monthName)
        binding.tvMonthStats.text = buildPeriodText(monthLogs, daysElapsedMonth)

        // THIS YEAR
        val daysElapsedYear = cal.get(Calendar.DAY_OF_YEAR)
        val yearLogs = logs.filter { it.dateKey.startsWith(yearPrefix) }
        binding.tvYearHeader.text = getString(R.string.prayer_this_year, yearPrefix)
        binding.tvYearStats.text = buildPeriodText(yearLogs, daysElapsedYear)
    }

    private fun buildTodayText(prayedKeys: Set<String>): String {
        return prayers.joinToString("\n") { key ->
            val name = getPrayerDisplayName(key)
            val mark = if (key in prayedKeys) "✓" else "–"
            "%-10s %s".format(name, mark)
        }
    }

    private fun buildPeriodText(logs: List<PrayerLog>, daysElapsed: Int): String {
        val sb = StringBuilder()
        var totalPrayed = 0
        prayers.forEach { key ->
            val daysPrayed = logs.filter { it.prayerKey == key }.map { it.dateKey }.toSet().size
            totalPrayed += daysPrayed
            val name = getPrayerDisplayName(key)
            sb.appendLine("%-10s %d / %d".format(name, daysPrayed, daysElapsed))
        }
        sb.append("─────────────────")
        sb.appendLine()
        sb.append("%-10s %d / %d".format(getString(R.string.total), totalPrayed, daysElapsed * 5))
        return sb.toString()
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
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
