package sukun.minimalist.app.launcher.com.helper

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import sukun.minimalist.app.launcher.com.data.Constants
import sukun.minimalist.app.launcher.com.data.Prefs
import java.util.Calendar

object HourlyChimeScheduler {

    fun scheduleNext(context: Context, prefs: Prefs = Prefs(context)) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildPendingIntent(context)

        alarmManager.cancel(pendingIntent)
        if (!prefs.hourlyChimeEnabled) return

        val triggerAtMillis = nextChimeMillis(prefs)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Fallback to inexact if permission not granted
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun canScheduleExactChime(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return alarmManager.canScheduleExactAlarms()
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
        val nowMillis = cal.timeInMillis

        // Set to the top of the current hour
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        // Always start by looking at the next hour
        cal.add(Calendar.HOUR_OF_DAY, 1)

        // Only skip if the next top-of-hour is essentially "now" (clock skew / early fire).
        // A large buffer here incorrectly skipped the upcoming hour when enabling near :55.
        if (cal.timeInMillis - nowMillis < 15_000L) {
            cal.add(Calendar.HOUR_OF_DAY, 1)
        }

        val startHour = prefs.hourlyChimeStartHour.coerceIn(0, 23)
        val endHour = prefs.hourlyChimeEndHour.coerceIn(0, 23)
        
        val nextHour = cal.get(Calendar.HOUR_OF_DAY)
        
        // Handle the quiet hours boundary
        if (startHour <= endHour) {
            // Normal range: e.g., 8:00 to 22:00
            if (nextHour < startHour) {
                cal.set(Calendar.HOUR_OF_DAY, startHour)
            } else if (nextHour > endHour) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, startHour)
            }
        } else {
            // Overnight range: e.g., 22:00 to 08:00
            if (nextHour in (endHour + 1)..<startHour) {
                cal.set(Calendar.HOUR_OF_DAY, startHour)
            }
        }

        return cal.timeInMillis
    }
}
