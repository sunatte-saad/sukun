package sukun.minimalist.app.launcher.com.helper

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import sukun.minimalist.app.launcher.com.data.Constants
import sukun.minimalist.app.launcher.com.data.Prefs
import sukun.minimalist.app.launcher.com.data.Reminder
import sukun.minimalist.app.launcher.com.data.toReminderList
import java.util.Calendar

object ReminderScheduler {

    fun scheduleAll(context: Context) {
        val prefs = Prefs(context)
        if (!prefs.showRemindersOnHome) {
            cancelAll(context)
            return
        }
        val reminders = prefs.remindersJson.toReminderList()
        reminders.forEach { if (it.enabled) schedule(context, it) else cancel(context, it.id) }
    }

    fun cancelAll(context: Context) {
        Prefs(context).remindersJson.toReminderList().forEach { cancel(context, it.id) }
    }

    fun schedule(context: Context, reminder: Reminder) {
        if (!Prefs(context).showRemindersOnHome) {
            cancel(context, reminder.id)
            return
        }
        if (!reminder.enabled) {
            cancel(context, reminder.id)
            return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) return
        val triggerAt = computeNextTrigger(reminder) ?: return
        val pi = buildPendingIntent(context, reminder)
        alarmManager.cancel(pi)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel(context: Context, reminderId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            Constants.Reminder.BASE_REQUEST_CODE + reminderId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pi)
    }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    fun computeNextTrigger(reminder: Reminder): Long? {
        val now = System.currentTimeMillis()
        return when (reminder.type) {
            Reminder.Type.DAILY -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, reminder.hour)
                    set(Calendar.MINUTE, reminder.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
                cal.timeInMillis
            }
            Reminder.Type.HOURLY -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, reminder.hour)
                    set(Calendar.MINUTE, reminder.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                while (cal.timeInMillis <= now) {
                    cal.add(Calendar.HOUR_OF_DAY, reminder.intervalHours)
                }
                cal.timeInMillis
            }
            Reminder.Type.WEEKLY -> {
                if (reminder.days.isEmpty()) return null
                for (i in 0..7) {
                    val cal = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, i)
                        set(Calendar.HOUR_OF_DAY, reminder.hour)
                        set(Calendar.MINUTE, reminder.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    if (cal.get(Calendar.DAY_OF_WEEK) in reminder.days && cal.timeInMillis > now) {
                        return cal.timeInMillis
                    }
                }
                null
            }
            Reminder.Type.ONCE -> {
                if (reminder.dateMillis > now) reminder.dateMillis else null
            }
            else -> null
        }
    }

    private fun buildPendingIntent(context: Context, reminder: Reminder): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = Constants.Reminder.ACTION
            putExtra(Constants.Reminder.EXTRA_ID, reminder.id)
            putExtra(Constants.Reminder.EXTRA_TITLE, reminder.title)
            putExtra(Constants.Reminder.EXTRA_MESSAGE, reminder.message)
        }
        return PendingIntent.getBroadcast(
            context,
            Constants.Reminder.BASE_REQUEST_CODE + reminder.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
