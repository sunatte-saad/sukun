package app.sukun.helper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.sukun.data.Constants
import app.sukun.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs

class PrayerReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val prefs = Prefs(appContext)
                val prayerKey = intent.getStringExtra(Constants.EXTRA_PRAYER_KEY).orEmpty()
                val prayerTimeMillis = intent.getLongExtra(Constants.EXTRA_PRAYER_TIME_MILLIS, 0L)
                val shouldPlayAzan = prefs.showPrayerOnHome &&
                        prefs.azanEnabled &&
                        prayerKey.isNotBlank() &&
                        prayerTimeMillis > 0L &&
                        abs(System.currentTimeMillis() - prayerTimeMillis) <= Constants.Prayer.REMINDER_WINDOW_MILLIS

                if (shouldPlayAzan) {
                    AzanPlaybackService.start(appContext, prayerKey)
                }

                refreshPrayerState(appContext, prefs, forceLocationRefresh = prefs.prayerSourceMode == Constants.PrayerSource.DEVICE)
                if (prefs.showPrayerOnHome && prefs.azanEnabled) {
                    PrayerReminderScheduler.scheduleNextReminder(appContext, prefs)
                } else {
                    PrayerReminderScheduler.cancelReminder(appContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
