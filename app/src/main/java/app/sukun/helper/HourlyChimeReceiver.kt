package app.sukun.helper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import app.sukun.data.Prefs
import java.util.Calendar

class HourlyChimeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs(context.applicationContext)
        if (!prefs.hourlyChimeEnabled) {
            HourlyChimeScheduler.cancel(context.applicationContext)
            return
        }
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour >= prefs.hourlyChimeStartHour && hour < prefs.hourlyChimeEndHour) {
            playChime()
        }
        // setInexactRepeating handles re-firing automatically — no manual reschedule needed
    }

    private fun playChime() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            Handler(Looper.getMainLooper()).postDelayed({
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                Handler(Looper.getMainLooper()).postDelayed({
                    toneGen.release()
                }, 300)
            }, 250)
        } catch (_: Exception) {
        }
    }
}
