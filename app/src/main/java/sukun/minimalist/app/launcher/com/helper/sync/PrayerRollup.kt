package sukun.minimalist.app.launcher.com.helper.sync

import org.json.JSONArray
import org.json.JSONObject
import sukun.minimalist.app.launcher.com.data.Constants
import java.util.Calendar

data class PrayerRollup(
    var year: String = "",
    var annual: MutableMap<String, Int> = mutableMapOf(),
    var month: String = "",
    var monthDays: MutableMap<String, MutableList<Int>> = mutableMapOf(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("y", year)
        put("a", JSONObject().apply {
            annual.forEach { (k, v) -> put(k, CompactNumber.encode(v)) }
        })
        put("m", month)
        put("d", JSONObject().apply {
            monthDays.forEach { (k, days) ->
                put(k, JSONArray(days))
            }
        })
    }

    companion object {
        fun fromJson(obj: JSONObject?): PrayerRollup? {
            if (obj == null) return null
            val annual = mutableMapOf<String, Int>()
            obj.optJSONObject("a")?.let { a ->
                a.keys().forEach { key ->
                    annual[key] = when (val value = a.opt(key)) {
                        is Number -> value.toInt()
                        else -> CompactNumber.decode(value?.toString() ?: "0")
                    }
                }
            }
            val monthDays = mutableMapOf<String, MutableList<Int>>()
            obj.optJSONObject("d")?.let { d ->
                d.keys().forEach { key ->
                    val arr = d.optJSONArray(key) ?: return@forEach
                    monthDays[key] = (0 until arr.length()).map { i ->
                        when (val value = arr.opt(i)) {
                            is Number -> value.toInt()
                            else -> CompactNumber.decode(value?.toString() ?: "0")
                        }
                    }.toMutableList()
                }
            }
            return PrayerRollup(
                year = obj.optString("y", ""),
                annual = annual,
                month = obj.optString("m", ""),
                monthDays = monthDays,
            )
        }

        fun emptyForNow(): PrayerRollup {
            val cal = Calendar.getInstance()
            val year = String.format("%04d", cal.get(Calendar.YEAR))
            val month = String.format(
                "%04d-%02d",
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
            )
            return PrayerRollup(year = year, month = month)
        }
    }

    fun daysMarkedThisMonth(prayerKey: String): Set<Int> =
        monthDays[prayerKey]?.toSet() ?: emptySet()

    fun annualCount(prayerKey: String): Int = annual[prayerKey] ?: 0

    fun markDay(prayerKey: String, dayOfMonth: Int) {
        val days = monthDays.getOrPut(prayerKey) { mutableListOf() }
        if (dayOfMonth !in days) days.add(dayOfMonth)
    }

    fun unmarkDay(prayerKey: String, dayOfMonth: Int) {
        monthDays[prayerKey]?.remove(dayOfMonth)
    }

    fun rolloverIfNeeded(now: Calendar = Calendar.getInstance()) {
        val currentYear = String.format("%04d", now.get(Calendar.YEAR))
        val currentMonth = String.format(
            "%04d-%02d",
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH) + 1,
        )
        val yearChanged = year.isNotEmpty() && year != currentYear
        val monthChanged = month.isNotEmpty() && month != currentMonth
        if (yearChanged) {
            // The stored month belongs to the old year, so it must not be flushed forward.
            annual.clear()
            monthDays.clear()
        } else if (monthChanged) {
            flushMonthToAnnual()
            monthDays.clear()
        }
        year = currentYear
        month = currentMonth
    }

    private fun flushMonthToAnnual() {
        Constants.Prayer.ALL.forEach { key ->
            val count = monthDays[key]?.size ?: 0
            if (count > 0) {
                annual[key] = (annual[key] ?: 0) + count
            }
        }
    }
}
