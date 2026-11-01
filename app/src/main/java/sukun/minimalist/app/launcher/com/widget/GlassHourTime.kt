package sukun.minimalist.app.launcher.com.widget

/**
 * Hour and minute pairs for the glass hour clock.
 * Hours sit on the top row and minutes on the bottom row, two digits each.
 */
object GlassHourTime {

    fun digits(hourOfDay: Int, minute: Int, use24Hour: Boolean): CharArray {
        val hours = hourPair(hourOfDay, use24Hour)
        val minutes = minutePair(minute)
        return charArrayOf(hours[0], hours[1], minutes[0], minutes[1])
    }

    fun spokenLabel(hourOfDay: Int, minute: Int, use24Hour: Boolean): String {
        val safeMinute = minute.coerceIn(0, 59)
        val hourText = if (use24Hour) {
            hourPair(hourOfDay, use24Hour = true)
        } else {
            displayHour(hourOfDay).toString()
        }
        return "%s:%02d".format(hourText, safeMinute)
    }

    private fun hourPair(hourOfDay: Int, use24Hour: Boolean): String {
        val hour = if (use24Hour) hourOfDay.coerceIn(0, 23) else displayHour(hourOfDay)
        return "%02d".format(hour)
    }

    private fun minutePair(minute: Int): String = "%02d".format(minute.coerceIn(0, 59))

    private fun displayHour(hourOfDay: Int): Int {
        val twelve = hourOfDay.coerceIn(0, 23) % 12
        return if (twelve == 0) 12 else twelve
    }
}
