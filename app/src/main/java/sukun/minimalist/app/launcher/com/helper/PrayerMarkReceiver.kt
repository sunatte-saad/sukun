package sukun.minimalist.app.launcher.com.helper

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import sukun.minimalist.app.launcher.com.helper.sync.AnalyticsRollupManager
import sukun.minimalist.app.launcher.com.data.Constants
import sukun.minimalist.app.launcher.com.data.Prefs

class PrayerMarkReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prayerKey = intent.getStringExtra(Constants.EXTRA_PRAYER_KEY) ?: return
        val appContext = context.applicationContext
        Prefs(appContext).apply {
            logPrayer(prayerKey)
            pendingPrayerMarkedPopup = true
        }
        AnalyticsRollupManager.onPrayerMarked(appContext, prayerKey)
        appContext.startActivity(
            Intent(appContext, sukun.minimalist.app.launcher.com.MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(Constants.Prayer.MARK_NOTIFICATION_ID)
    }
}
