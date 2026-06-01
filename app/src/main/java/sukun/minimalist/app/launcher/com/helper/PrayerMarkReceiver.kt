package sukun.minimalist.app.launcher.com.helper

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import sukun.minimalist.app.launcher.com.data.Constants
import sukun.minimalist.app.launcher.com.data.Prefs

class PrayerMarkReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prayerKey = intent.getStringExtra(Constants.EXTRA_PRAYER_KEY) ?: return
        Prefs(context.applicationContext).logPrayer(prayerKey)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(Constants.Prayer.MARK_NOTIFICATION_ID)
    }
}
