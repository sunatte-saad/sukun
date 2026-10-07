package sukun.minimalist.app.launcher.com.helper

import sukun.minimalist.app.launcher.com.data.Constants
import sukun.minimalist.app.launcher.com.data.Prefs
import java.util.Calendar

/**
 * Decides which premium-trial ending popup to show. One slot per resume; prefs persist
 * each milestone so it does not repeat. Last calendar day uses timed slots instead of
 * a generic "1 day before" reminder.
 */
object TrialReminder {

    enum class Slot {
        DAYS_15,
        DAYS_7,
        DAYS_3,
        DAYS_2,
        LAST_MORNING,
        LAST_EVENING,
        LAST_END,
    }

    fun dueSlot(prefs: Prefs, now: Long = System.currentTimeMillis()): Slot? {
        if (prefs.isProUser) return null
        if (!PremiumAccess.isTrialActive(prefs)) return null
        val trialEnd = PremiumAccess.trialEndMillis(prefs)
        if (trialEnd == 0L || now >= trialEnd) return null

        val remainingMs = trialEnd - now
        val days = calendarDaysUntilEnd(trialEnd, now)
        val hour = hourOfDay(now)

        if (days <= 0) {
            return lastDaySlot(prefs, hour, remainingMs)
        }

        return when {
            days == Constants.TrialReminder.DAYS_2 && !isShown(prefs, Slot.DAYS_2) -> Slot.DAYS_2
            days == Constants.TrialReminder.DAYS_3 && !isShown(prefs, Slot.DAYS_3) -> Slot.DAYS_3
            days in 4..Constants.TrialReminder.DAYS_7 && !isShown(prefs, Slot.DAYS_7) -> Slot.DAYS_7
            days in 8..Constants.TrialReminder.DAYS_15 && !isShown(prefs, Slot.DAYS_15) -> Slot.DAYS_15
            else -> null
        }
    }

    fun calendarDaysUntilEnd(trialEnd: Long, now: Long): Int {
        if (now >= trialEnd) return -1
        return ((trialEnd.convertEpochToMidnight() - now.convertEpochToMidnight()) /
            Constants.ONE_DAY_IN_MILLIS).toInt()
    }

    fun isShown(prefs: Prefs, slot: Slot): Boolean = when (slot) {
        Slot.DAYS_15 -> prefs.trialReminder15dShown || prefs.premiumNudgeShown
        Slot.DAYS_7 -> prefs.trialReminder7dShown
        Slot.DAYS_3 -> prefs.trialReminder3dShown
        Slot.DAYS_2 -> prefs.trialReminder2dShown
        Slot.LAST_MORNING -> prefs.trialReminderLastMorningShown
        Slot.LAST_EVENING -> prefs.trialReminderLastEveningShown
        Slot.LAST_END -> prefs.trialReminderLastEndShown
    }

    fun markShown(prefs: Prefs, slot: Slot) {
        when (slot) {
            Slot.DAYS_15 -> prefs.trialReminder15dShown = true
            Slot.DAYS_7 -> prefs.trialReminder7dShown = true
            Slot.DAYS_3 -> prefs.trialReminder3dShown = true
            Slot.DAYS_2 -> prefs.trialReminder2dShown = true
            Slot.LAST_MORNING -> prefs.trialReminderLastMorningShown = true
            Slot.LAST_EVENING -> prefs.trialReminderLastEveningShown = true
            Slot.LAST_END -> prefs.trialReminderLastEndShown = true
        }
    }

    private fun lastDaySlot(prefs: Prefs, hour: Int, remainingMs: Long): Slot? {
        val c = Constants.TrialReminder
        val endDue = hour >= c.DAY_END_HOUR || remainingMs <= c.LAST_END_REMAINING_MS
        return when {
            endDue -> if (!isShown(prefs, Slot.LAST_END)) Slot.LAST_END else null
            hour >= c.EVENING_HOUR ->
                if (!isShown(prefs, Slot.LAST_EVENING)) Slot.LAST_EVENING else null
            hour >= c.MORNING_HOUR ->
                if (!isShown(prefs, Slot.LAST_MORNING)) Slot.LAST_MORNING else null
            else -> null
        }
    }

    private fun hourOfDay(now: Long): Int {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = now
        return calendar.get(Calendar.HOUR_OF_DAY)
    }
}
