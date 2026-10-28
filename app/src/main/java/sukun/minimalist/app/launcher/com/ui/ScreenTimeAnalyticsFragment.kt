package sukun.minimalist.app.launcher.com.ui

import android.content.Context
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
import sukun.minimalist.app.launcher.com.helper.usageStats.ComponentForegroundStat
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
        val ctx = context ?: return
        binding.tvTodayStats.text = getString(R.string.screen_time_loading)
        binding.tvWeekStats.text = ""
        binding.tvMonthStats.text = ""
        viewLifecycleOwner.lifecycleScope.launch {
            val stats = withContext(Dispatchers.IO) { queryStats(ctx) }
            val views = _binding ?: return@launch
            if (stats == null) {
                views.tvTodayStats.text = ctx.getString(R.string.screen_time_load_failed)
                views.tvWeekStats.text = ""
                views.tvMonthStats.text = ""
                return@launch
            }
            views.tvTodayStats.text = stats.todayText
            views.tvWeekHeader.text = stats.weekHeader
            views.tvWeekStats.text = stats.weekText
            views.tvMonthHeader.text = stats.monthHeader
            views.tvMonthStats.text = stats.monthText
        }
    }

    private data class ScreenStats(
        val todayText: String,
        val weekHeader: String,
        val weekText: String,
        val monthHeader: String,
        val monthText: String,
    )

    private fun queryStats(ctx: Context): ScreenStats? {
        val wrapper = EventLogWrapper(ctx)
        val cal = Calendar.getInstance()
        val daysElapsed = cal.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
        val earliestDaysAgo = maxOf(6, daysElapsed - 1)

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

        val (rangeStart, _) = dayBounds(earliestDaysAgo)
        val foregroundStats = try {
            wrapper.getForegroundStatsByTimestamps(rangeStart, System.currentTimeMillis())
        } catch (_: Exception) {
            return null
        }

        fun fetchMs(daysAgo: Int): Long {
            val (start, end) = dayBounds(daysAgo)
            val clipped = ArrayList<ComponentForegroundStat>()
            for (stat in foregroundStats) {
                val begin = maxOf(stat.beginTime, start)
                val finish = minOf(stat.endTime, end)
                if (finish > begin) {
                    clipped.add(ComponentForegroundStat(begin, finish, stat.packageName))
                }
            }
            return try {
                wrapper.aggregateForegroundStats(clipped).sumOf { it.timeUsed }
            } catch (_: Exception) {
                0L
            }
        }

        // Today
        val todayMs = fetchMs(0)
        val todayText = buildString {
            appendLine(ctx.getString(R.string.screen_time_total_label, ctx.formattedTimeSpent(todayMs)))
        }.trimEnd()

        // Last 7 days
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val dateFormat = SimpleDateFormat("d MMM", Locale.getDefault())
        val weekLines = (6 downTo 0).map { daysAgo ->
            val c = Calendar.getInstance().also { it.add(Calendar.DAY_OF_YEAR, -daysAgo) }
            val day = if (daysAgo == 0) ctx.getString(R.string.screen_time_today_label)
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
            appendLine(ctx.getString(R.string.screen_time_avg_label, ctx.formattedTimeSpent(avgMs)))
        }.trimEnd()
        val weekHeader = ctx.getString(R.string.screen_time_last_7_days)

        // This month
        val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
        var monthTotalMs = 0L
        for (d in 0 until daysElapsed) monthTotalMs += fetchMs(d)
        val monthAvgMs = if (daysElapsed > 0) monthTotalMs / daysElapsed else 0L
        val monthText = buildString {
            appendLine(ctx.getString(R.string.screen_time_total_label, ctx.formattedTimeSpent(monthTotalMs)))
            appendLine(ctx.getString(R.string.screen_time_avg_label, ctx.formattedTimeSpent(monthAvgMs)))
        }.trimEnd()

        return ScreenStats(todayText, weekHeader, weekText, monthName, monthText)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
