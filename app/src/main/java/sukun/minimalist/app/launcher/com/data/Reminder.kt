package sukun.minimalist.app.launcher.com.data

import java.util.Calendar
import org.json.JSONArray
import org.json.JSONObject

data class Reminder(
    val id: Int,
    val title: String,
    val message: String,
    val type: String,
    val hour: Int,
    val minute: Int,
    val intervalHours: Int = 1,
    val days: Set<Int> = emptySet(),
    val enabled: Boolean = true,
    val fireCount: Int = 0,
    val doneCount: Int = 0
) {
    object Type {
        const val DAILY = "DAILY"
        const val HOURLY = "HOURLY"
        const val WEEKLY = "WEEKLY"
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("message", message)
        put("type", type)
        put("hour", hour)
        put("minute", minute)
        put("intervalHours", intervalHours)
        put("days", JSONArray(days.toList()))
        put("enabled", enabled)
        put("fireCount", fireCount)
        put("doneCount", doneCount)
    }

    companion object {
        fun fromJson(obj: JSONObject): Reminder {
            val daysArray = obj.optJSONArray("days") ?: JSONArray()
            val days = (0 until daysArray.length()).map { daysArray.getInt(it) }.toSet()
            return Reminder(
                id = obj.getInt("id"),
                title = obj.getString("title"),
                message = obj.getString("message"),
                type = obj.getString("type"),
                hour = obj.getInt("hour"),
                minute = obj.getInt("minute"),
                intervalHours = obj.optInt("intervalHours", 1),
                days = days,
                enabled = obj.optBoolean("enabled", true),
                fireCount = obj.optInt("fireCount", 0),
                doneCount = obj.optInt("doneCount", 0)
            )
        }
    }
}

fun List<Reminder>.toJsonString(): String {
    val arr = JSONArray()
    forEach { arr.put(it.toJson()) }
    return arr.toString()
}

fun String.toReminderList(): List<Reminder> {
    if (isBlank()) return emptyList()
    return try {
        val arr = JSONArray(this)
        (0 until arr.length()).map { Reminder.fromJson(arr.getJSONObject(it)) }
    } catch (_: Exception) {
        emptyList()
    }
}

fun Reminder.scheduleDescription(): String {
    val timeStr = String.format("%02d:%02d", hour, minute)
    return when (type) {
        Reminder.Type.DAILY -> "Daily at $timeStr"
        Reminder.Type.HOURLY -> {
            val label = if (intervalHours == 1) "1 hour" else "$intervalHours hours"
            "Every $label from $timeStr"
        }
        Reminder.Type.WEEKLY -> {
            val names = days.sorted().joinToString(", ") { calDayAbbr(it) }
            if (names.isEmpty()) "Weekly at $timeStr" else "$names at $timeStr"
        }
        else -> timeStr
    }
}

fun calDayAbbr(calDay: Int) = when (calDay) {
    Calendar.SUNDAY -> "Sun"
    Calendar.MONDAY -> "Mon"
    Calendar.TUESDAY -> "Tue"
    Calendar.WEDNESDAY -> "Wed"
    Calendar.THURSDAY -> "Thu"
    Calendar.FRIDAY -> "Fri"
    Calendar.SATURDAY -> "Sat"
    else -> ""
}
