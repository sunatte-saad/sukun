package sukun.minimalist.app.launcher.com.helper.sync

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class ScreenTimeRollup(
    var year: String = "",
    var annualMinutes: Int = 0,
    var month: String = "",
    var dailyMinutes: MutableList<Int> = mutableListOf(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("y", year)
        put("a", CompactNumber.encode(annualMinutes))
        put("m", month)
        put("d", JSONArray().apply {
            dailyMinutes.forEach { put(CompactNumber.encode(it)) }
        })
    }

    companion object {
        fun fromJson(obj: JSONObject?): ScreenTimeRollup? {
            if (obj == null) return null
            val daily = mutableListOf<Int>()
            obj.optJSONArray("d")?.let { arr ->
                for (i in 0 until arr.length()) {
                    daily.add(CompactNumber.decode(arr.optString(i, "0")))
                }
            }
            return ScreenTimeRollup(
                year = obj.optString("y", ""),
                annualMinutes = CompactNumber.decode(obj.optString("a", "0")),
                month = obj.optString("m", ""),
                dailyMinutes = daily,
            )
        }

        fun emptyForNow(): ScreenTimeRollup {
            val cal = Calendar.getInstance()
            val year = String.format("%04d", cal.get(Calendar.YEAR))
            val month = String.format(
                "%04d-%02d",
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
            )
            return ScreenTimeRollup(year = year, month = month)
        }
    }

    fun monthTotalMinutes(): Int = dailyMinutes.sum()

    fun setDayMinutes(dayOfMonth: Int, minutes: Int) {
        while (dailyMinutes.size < dayOfMonth) dailyMinutes.add(0)
        if (dayOfMonth <= 0) return
        dailyMinutes[dayOfMonth - 1] = minutes.coerceAtLeast(0)
    }

    fun dayMinutes(dayOfMonth: Int): Int {
        if (dayOfMonth <= 0 || dayOfMonth > dailyMinutes.size) return 0
        return dailyMinutes[dayOfMonth - 1]
    }

    fun rolloverIfNeeded(now: Calendar = Calendar.getInstance()) {
        val currentYear = String.format("%04d", now.get(Calendar.YEAR))
        val currentMonth = String.format(
            "%04d-%02d",
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH) + 1,
        )
        if (year.isNotEmpty() && year != currentYear) {
            annualMinutes = 0
            year = currentYear
        } else if (year.isEmpty()) {
            year = currentYear
        }
        if (month.isNotEmpty() && month != currentMonth) {
            annualMinutes += monthTotalMinutes()
            month = currentMonth
            dailyMinutes.clear()
        } else if (month.isEmpty()) {
            month = currentMonth
        }
    }
}
