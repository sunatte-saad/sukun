package app.sukun.helper

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.sukun.data.Constants
import app.sukun.data.Prefs
import java.util.Calendar

object HourlyChimeScheduler {

    fun scheduleNext(context: Context, prefs: Prefs = Prefs(context)) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildPendingIntent(context)

        alarmManager.cancel(pendingIntent)
        if (!prefs.hourlyChimeEnabled) return

        // Use inexact repeating — no SCHEDULE_EXACT_ALARM permission needed.
        // A minute or two of drift is fine for an hourly chime.
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            nextFullHourMillis(),
            AlarmManager.INTERVAL_HOUR,
            pendingIntent
        )
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(buildPendingIntent(context))
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, HourlyChimeReceiver::class.java).apply {
            action = Constants.HourlyChime.ACTION
        }
        return PendingIntent.getBroadcast(
            context,
            Constants.HourlyChime.REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun nextFullHourMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.HOUR_OF_DAY, 1)
        return cal.timeInMillis
    }
}
