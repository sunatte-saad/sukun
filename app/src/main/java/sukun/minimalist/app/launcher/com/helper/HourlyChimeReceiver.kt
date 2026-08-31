package sukun.minimalist.app.launcher.com.helper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import sukun.minimalist.app.launcher.com.data.Constants
import sukun.minimalist.app.launcher.com.data.Prefs
import java.util.Calendar

class HourlyChimeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val prefs = Prefs(appContext)
        if (!prefs.hourlyChimeEnabled) {
            HourlyChimeScheduler.cancel(appContext)
            pendingResult.finish()
            return
        }
        val holdMs = HourlyChimeEffects.holdMsForStyle(prefs.hourlyChimeStyle)
        try {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (isInChimeWindow(hour, prefs.hourlyChimeStartHour, prefs.hourlyChimeEndHour)) {
                HourlyChimeEffects.playStyle(appContext, prefs.hourlyChimeStyle, prefs)
            }
            HourlyChimeScheduler.scheduleNext(appContext, prefs)
        } finally {
            Handler(Looper.getMainLooper()).postDelayed({
                pendingResult.finish()
            }, holdMs)
        }
    }

    private fun isInChimeWindow(hour: Int, startHour: Int, endHour: Int): Boolean {
        val start = startHour.coerceIn(0, 23)
        val end = endHour.coerceIn(0, 23)
        return if (start <= end) {
            hour in start..end
        } else {
            // Overnight window, e.g. 22 → 8
            hour >= start || hour <= end
        }
    }
}
