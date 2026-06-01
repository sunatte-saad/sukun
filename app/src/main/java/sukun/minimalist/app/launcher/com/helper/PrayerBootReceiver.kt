package sukun.minimalist.app.launcher.com.helper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import sukun.minimalist.app.launcher.com.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PrayerBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val prefs = Prefs(appContext)
                if (!prefs.showPrayerOnHome) {
                    PrayerReminderScheduler.cancelReminder(appContext)
                    return@launch
                }
                refreshPrayerState(
                    appContext,
                    prefs,
                    forceLocationRefresh = prefs.prayerSourceMode == sukun.minimalist.app.launcher.com.data.Constants.PrayerSource.DEVICE
                )
                PrayerReminderScheduler.scheduleNextReminder(appContext, prefs)
            } finally {
                pendingResult.finish()
            }
        }
        // Reschedule hourly chime and reminders on boot (runs on main thread, lightweight)
        val appContext = context.applicationContext
        HourlyChimeScheduler.scheduleNext(appContext)
        if (Prefs(appContext).showRemindersOnHome) {
            ReminderScheduler.scheduleAll(appContext)
        } else {
            ReminderScheduler.cancelAll(appContext)
        }
    }
}
