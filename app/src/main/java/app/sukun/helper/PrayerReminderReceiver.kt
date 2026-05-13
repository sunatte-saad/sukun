package app.sukun.helper

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import app.sukun.R
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
                val withinWindow = prayerKey.isNotBlank() &&
                        prayerTimeMillis > 0L &&
                        abs(System.currentTimeMillis() - prayerTimeMillis) <= Constants.Prayer.REMINDER_WINDOW_MILLIS

                if (prefs.showPrayerOnHome && withinWindow) {
                    if (prefs.azanEnabled) {
                        AzanPlaybackService.start(appContext, prayerKey)
                    }
                    showPrayerMarkNotification(appContext, prayerKey)
                }

                refreshPrayerState(appContext, prefs, forceLocationRefresh = prefs.prayerSourceMode == Constants.PrayerSource.DEVICE)
                if (prefs.showPrayerOnHome) {
                    PrayerReminderScheduler.scheduleNextReminder(appContext, prefs)
                } else {
                    PrayerReminderScheduler.cancelReminder(appContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showPrayerMarkNotification(context: Context, prayerKey: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.Prayer.NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.prayer_reminder),
                NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(channel)
        }

        val markIntent = Intent(context, PrayerMarkReceiver::class.java).apply {
            action = Constants.PRAYER_MARK_ACTION
            putExtra(Constants.EXTRA_PRAYER_KEY, prayerKey)
        }
        val markPending = PendingIntent.getBroadcast(
            context,
            Constants.Prayer.MARK_REQUEST_CODE,
            markIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prayerName = getPrayerName(context, prayerKey)
        val notification = NotificationCompat.Builder(context, Constants.Prayer.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(prayerName)
            .setContentText(context.getString(R.string.prayer_time_now))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .addAction(0, context.getString(R.string.prayer_mark_done), markPending)
            .build()

        nm.notify(Constants.Prayer.MARK_NOTIFICATION_ID, notification)
    }
}
