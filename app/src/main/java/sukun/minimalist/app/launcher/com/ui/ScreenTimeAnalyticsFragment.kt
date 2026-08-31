package sukun.minimalist.app.launcher.com.ui

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sukun.minimalist.app.launcher.com.R
import sukun.minimalist.app.launcher.com.databinding.FragmentScreenTimeAnalyticsBinding
import sukun.minimalist.app.launcher.com.helper.appUsagePermissionGranted
import sukun.minimalist.app.launcher.com.helper.formattedTimeSpent
import sukun.minimalist.app.launcher.com.helper.usageStats.EventLogWrapper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ScreenTimeAnalyticsFragment : Fragment() {

    private var _binding: FragmentScreenTimeAnalyticsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentScreenTimeAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && requireContext().appUsagePermissionGranted()) {
            populate()
        } else {
            binding.tvNoPermission.visibility = View.VISIBLE
            binding.tvTodayStats.visibility = View.GONE
            binding.tvWeekHeader.visibility = View.GONE
            binding.tvWeekStats.visibility = View.GONE
            binding.tvMonthHeader.visibility = View.GONE
            binding.tvMonthStats.visibility = View.GONE
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun populate() {
        viewLifecycleOwner.lifecycleScope.launch {
            val stats = withContext(Dispatchers.IO) { queryStats() }
            binding.tvTodayStats.text = stats.todayText
            binding.tvWeekHeader.text = stats.weekHeader
            binding.tvWeekStats.text = stats.weekText
            binding.tvMonthHeader.text = stats.monthHeader
            binding.tvMonthStats.text = stats.monthText
        }
    }

    private data class ScreenStats(
        val todayText: String,
        val weekHeader: String,
        val weekText: String,
        val monthHeader: String,
        val monthText: String,
    )

    private fun queryStats(): ScreenStats {
        val ctx = requireContext()
        val wrapper = EventLogWrapper(ctx)
        val cal = Calendar.getInstance()

        fun dayBounds(daysAgo: Int): Pair<Long, Long> {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, -daysAgo)
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            val start = c.timeInMillis
            val end = if (daysAgo == 0) System.currentTimeMillis()
            else { c.add(Calendar.DAY_OF_YEAR, 1); c.timeInMillis }
            return start to end
        }

        fun fetchMs(daysAgo: Int): Long {
            val (start, end) = dayBounds(daysAgo)
            return try {
                wrapper.aggregateForegroundStats(
                    wrapper.getForegroundStatsByTimestamps(start, end)
                ).sumOf { it.timeUsed }
            } catch (_: Exception) { 0L }
        }

        // Today
        val todayMs = fetchMs(0)
        val todayText = buildString {
            appendLine(getString(R.string.screen_time_total_label, ctx.formattedTimeSpent(todayMs)))
        }.trimEnd()

        // Last 7 days
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val dateFormat = SimpleDateFormat("d MMM", Locale.getDefault())
        val weekLines = (6 downTo 0).map { daysAgo ->
            val c = Calendar.getInstance().also { it.add(Calendar.DAY_OF_YEAR, -daysAgo) }
            val day = if (daysAgo == 0) getString(R.string.screen_time_today_label)
                      else dayFormat.format(c.time)
            val date = dateFormat.format(c.time)
            val ms = fetchMs(daysAgo)
            Triple(day, date, ms)
        }
        val totalWeekMs = weekLines.sumOf { it.third }
        val avgMs = totalWeekMs / 7
        val weekText = buildString {
            weekLines.forEach { (day, date, ms) ->
                val dayPad = day.padEnd(4)
                val datePad = date.padEnd(7)
                appendLine("$dayPad  $datePad  ${ctx.formattedTimeSpent(ms)}")
            }
            appendLine()
            appendLine(getString(R.string.screen_time_avg_label, ctx.formattedTimeSpent(avgMs)))
        }.trimEnd()
        val weekHeader = getString(R.string.screen_time_last_7_days)

        // This month
        val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
        val daysElapsed = cal.get(Calendar.DAY_OF_MONTH)
        var monthTotalMs = 0L
        for (d in 0 until daysElapsed) monthTotalMs += fetchMs(d)
        val monthAvgMs = if (daysElapsed > 0) monthTotalMs / daysElapsed else 0L
        val monthText = buildString {
            appendLine(getString(R.string.screen_time_total_label, ctx.formattedTimeSpent(monthTotalMs)))
            appendLine(getString(R.string.screen_time_avg_label, ctx.formattedTimeSpent(monthAvgMs)))
        }.trimEnd()

        return ScreenStats(todayText, weekHeader, weekText, monthName, monthText)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
