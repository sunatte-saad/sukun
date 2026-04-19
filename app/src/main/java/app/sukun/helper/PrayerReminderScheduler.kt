package app.sukun.helper

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.sukun.data.Constants
import app.sukun.data.Prefs

object PrayerReminderScheduler {

    fun scheduleNextReminder(context: Context, prefs: Prefs = Prefs(context)) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildPendingIntent(context, prefs)
        if (!prefs.showPrayerOnHome || !prefs.azanEnabled || prefs.prayerNextAt <= System.currentTimeMillis()) {
            alarmManager.cancel(pendingIntent)
            return
        }
        if (!canScheduleExactPrayerReminders(context)) {
            alarmManager.cancel(pendingIntent)
            return
        }
        alarmManager.cancel(pendingIntent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                prefs.prayerNextAt,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                prefs.prayerNextAt,
                pendingIntent
            )
        }
    }

    fun cancelReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(buildPendingIntent(context))
    }

    fun canScheduleExactPrayerReminders(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    private fun buildPendingIntent(
        context: Context,
        prefs: Prefs? = null,
    ): PendingIntent {
        val intent = Intent(context, PrayerReminderReceiver::class.java).apply {
            action = Constants.PRAYER_REMINDER_ACTION
            if (prefs != null) {
                putExtra(Constants.EXTRA_PRAYER_KEY, prefs.prayerNextKey)
                putExtra(Constants.EXTRA_PRAYER_TIME_MILLIS, prefs.prayerNextAt)
                putExtra(Constants.EXTRA_PRAYER_LABEL, prefs.prayerLocationLabel)
            }
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(
            context,
            Constants.Prayer.REMINDER_REQUEST_CODE,
            intent,
            flags
        )
    }
}
