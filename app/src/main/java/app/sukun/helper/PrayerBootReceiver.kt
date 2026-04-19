package app.sukun.helper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.sukun.data.Prefs
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
                if (!prefs.showPrayerOnHome || !prefs.azanEnabled) {
                    PrayerReminderScheduler.cancelReminder(appContext)
                    return@launch
                }
                refreshPrayerState(
                    appContext,
                    prefs,
                    forceLocationRefresh = prefs.prayerSourceMode == app.sukun.data.Constants.PrayerSource.DEVICE
                )
                PrayerReminderScheduler.scheduleNextReminder(appContext, prefs)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
