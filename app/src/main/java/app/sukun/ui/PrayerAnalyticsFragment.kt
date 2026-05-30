package app.sukun.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import app.sukun.data.Constants
import app.sukun.R
import app.sukun.data.Prefs
import app.sukun.data.PrayerLog
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
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

        val monthPrefix = String.format(
            "%04d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1
        )

        val monthName = SimpleDateFormat(
            "MMMM yyyy",
            Locale.getDefault()
        ).format(Date())

        val todayLogs = logs.filter { it.dateKey == today }
            .map { it.prayerKey }
            .toSet()

        val daysElapsedMonth = cal.get(Calendar.DAY_OF_MONTH)
        val daysElapsedYear = cal.get(Calendar.DAY_OF_YEAR)

        val monthLogs = logs.filter { it.dateKey.startsWith(monthPrefix) }
        val yearLogs = logs.filter { it.dateKey.startsWith(yearPrefix) }

        binding.tvTodayStats.text = buildTodayDashboard(todayLogs)

        binding.tvMonthHeader.text = "This Month • $monthName"
        binding.tvMonthStats.text =
            buildMonthAnalytics(monthLogs, daysElapsedMonth)

        binding.tvYearHeader.text = "This Year • $yearPrefix"
        binding.tvYearStats.text =
            buildYearAnalytics(yearLogs, daysElapsedYear)
    }

    // ---------------- TODAY ----------------
    private fun buildTodayDashboard(prayedKeys: Set<String>): String {
        val doneCount = prayers.count { it in prayedKeys }
        val rate = (doneCount * 100) / 5

        return buildString {
            appendLine("TODAY OVERVIEW")
            appendLine("Completion: $doneCount / 5 ($rate%)")
            appendLine("")

            prayers.forEach { key ->
                val name = getPrayerDisplayName(key)
                val done = key in prayedKeys

                val status = if (done) "✓ Completed" else "○ Missed"
                val icon = if (done) "🔥" else "—"

                appendLine("$icon $name  →  $status")
            }
        }
    }

    // ---------------- MONTH ----------------
    private fun buildMonthAnalytics(
        logs: List<PrayerLog>,
        daysElapsed: Int
    ): String {

        val sb = StringBuilder()

        sb.appendLine("MONTH ANALYTICS")
        sb.appendLine("Days elapsed: $daysElapsed")
        sb.appendLine("")

        val stats = prayers.map { key ->
            val daysPrayed = logs
                .filter { it.prayerKey == key }
                .map { it.dateKey }
                .toSet()
                .size

            val rate = if (daysElapsed > 0)
                (daysPrayed * 100) / daysElapsed
            else 0

            Triple(key, daysPrayed, rate)
        }

        val best = stats.maxByOrNull { it.third }
        val worst = stats.minByOrNull { it.third }

        stats.forEach { (key, days, rate) ->
            val name = getPrayerDisplayName(key)

            sb.appendLine("• $name")
            sb.appendLine("  $days days  |  $rate% consistency")
            sb.appendLine(progressBar(rate))
            sb.appendLine("")
        }

        sb.appendLine("──────────────")
        sb.appendLine("INSIGHTS")

        best?.let {
            sb.appendLine("Best: ${getPrayerDisplayName(it.first)} (${it.third}%)")
        }

        worst?.let {
            sb.appendLine("Needs attention: ${getPrayerDisplayName(it.first)} (${it.third}%)")
        }

        sb.appendLine("")
        sb.appendLine("STREAK")
        sb.appendLine("🔥 ${calculateStreak(logs)} days (approx)")

        return sb.toString()
    }

    // ---------------- YEAR ----------------
    private fun buildYearAnalytics(
        logs: List<PrayerLog>,
        daysElapsed: Int
    ): String {

        val sb = StringBuilder()

        sb.appendLine("YEAR ANALYTICS")
        sb.appendLine("")

        var total = 0

        prayers.forEach { key ->

            val daysPrayed = logs
                .filter { it.prayerKey == key }
                .map { it.dateKey }
                .toSet()
                .size

            total += daysPrayed

            val rate = if (daysElapsed > 0)
                (daysPrayed * 100) / daysElapsed
            else 0

            sb.appendLine("${getPrayerDisplayName(key)}")
            sb.appendLine("$daysPrayed / $daysElapsed days")
            sb.appendLine("$rate% consistency")
            sb.appendLine(progressBar(rate))
            sb.appendLine("")
        }

        val avg = total / 5
        val overallRate = if (daysElapsed > 0)
            (avg * 100) / daysElapsed
        else 0

        sb.appendLine("──────────────")
        sb.appendLine("OVERALL PERFORMANCE")
        sb.appendLine("Average prayers/day tracked: $avg")
        sb.appendLine("Overall consistency: $overallRate%")

        return sb.toString()
    }

    // ---------------- PROGRESS BAR ----------------
    private fun progressBar(percent: Int): String {
        val filled = percent / 10
        val empty = 10 - filled

        return "█".repeat(filled) + "░".repeat(empty) + " $percent%"
    }

    // ---------------- STREAK (simple heuristic) ----------------
    private fun calculateStreak(logs: List<PrayerLog>): Int {
        val dates = logs.map { it.dateKey }.toSet().sorted()

        var streak = 0
        val cal = Calendar.getInstance()

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        var currentDate = today

        while (dates.contains(currentDate)) {
            streak++

            cal.time = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(currentDate)!!
            cal.add(Calendar.DAY_OF_YEAR, -1)

            currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        }

        return streak
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