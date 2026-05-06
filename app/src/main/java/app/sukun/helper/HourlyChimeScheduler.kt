package app.sukun.helper

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.sukun.data.Constants
import app.sukun.data.Prefs
import java.util.Calendar

object HourlyChimeScheduler {

    fun scheduleNext(context: Context, prefs: Prefs = Prefs(context)) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildPendingIntent(context)

        alarmManager.cancel(pendingIntent)
        if (!prefs.hourlyChimeEnabled) return

        val triggerAtMillis = nextChimeMillis(prefs)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
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

    private fun nextChimeMillis(prefs: Prefs): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.HOUR_OF_DAY, 1)

        val startHour = prefs.hourlyChimeStartHour.coerceIn(0, 23)
        val endHour = prefs.hourlyChimeEndHour.coerceIn(0, 23)
        val nextHour = cal.get(Calendar.HOUR_OF_DAY)
        if (nextHour < startHour) {
            cal.set(Calendar.HOUR_OF_DAY, startHour)
        } else if (nextHour >= endHour) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            cal.set(Calendar.HOUR_OF_DAY, startHour)
        }
        return cal.timeInMillis
    }
}
