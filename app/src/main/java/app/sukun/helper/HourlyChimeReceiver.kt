package app.sukun.helper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Handler
import android.os.Looper
import app.sukun.R
import app.sukun.data.Constants
import app.sukun.data.Prefs
import java.util.Calendar

private const val CHIME_MAX_PLAYBACK_MS = 2500L

class HourlyChimeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val prefs = Prefs(context.applicationContext)
        if (!prefs.hourlyChimeEnabled) {
            HourlyChimeScheduler.cancel(appContext)
            pendingResult.finish()
            return
        }
        try {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (hour >= prefs.hourlyChimeStartHour && hour <= prefs.hourlyChimeEndHour) {
                playChime(appContext, prefs)
            }
            HourlyChimeScheduler.scheduleNext(appContext, prefs)
        } finally {
            Handler(Looper.getMainLooper()).postDelayed({
                pendingResult.finish()
            }, CHIME_MAX_PLAYBACK_MS + 500)
        }
    }

    private fun playChime(context: Context, prefs: Prefs) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager?.ringerMode == AudioManager.RINGER_MODE_SILENT) return

        when (prefs.hourlyChimeSound) {
            Constants.ChimeSound.DEFAULT -> playDefaultNotificationSound(context)
            Constants.ChimeSound.CUSTOM -> {
                val uri = prefs.hourlyChimeCustomUri.takeIf { it.isNotEmpty() }
                if (uri != null) playUriSound(context, Uri.parse(uri)) else playDefaultNotificationSound(context)
            }
            else -> {
                if (!playBundledChime(context)) playFallbackTone(audioManager)
            }
        }
    }

    private fun playDefaultNotificationSound(context: Context) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            playUriSound(context, uri)
        } catch (_: Exception) {
        }
    }

    private fun playUriSound(context: Context, uri: Uri) {
        try {
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            player.setDataSource(context, uri)
            var released = false
            fun releasePlayer() {
                if (released) return
                released = true
                try {
                    if (player.isPlaying) player.stop()
                } catch (_: Exception) {
                }
                player.release()
            }
            player.setOnCompletionListener { releasePlayer() }
            player.setOnErrorListener { mediaPlayer, _, _ ->
                if (!released) {
                    released = true
                    mediaPlayer.release()
                }
                true
            }
            player.prepare()
            player.start()
            Handler(Looper.getMainLooper()).postDelayed({ releasePlayer() }, CHIME_MAX_PLAYBACK_MS)
        } catch (_: Exception) {
        }
    }

    private fun playBundledChime(context: Context): Boolean {
        try {
            val afd = context.resources.openRawResourceFd(R.raw.hourly_chime) ?: return false
            val player = MediaPlayer()
            afd.use {
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                player.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            var released = false
            fun releasePlayer() {
                if (released) return
                released = true
                try {
                    if (player.isPlaying) player.stop()
                } catch (_: Exception) {
                }
                player.release()
            }
            player.setOnCompletionListener { releasePlayer() }
            player.setOnErrorListener { mediaPlayer, _, _ ->
                if (!released) {
                    released = true
                    mediaPlayer.release()
                }
                true
            }
            player.prepare()
            player.start()
            Handler(Looper.getMainLooper()).postDelayed({
                releasePlayer()
            }, CHIME_MAX_PLAYBACK_MS)
            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun playFallbackTone(audioManager: AudioManager?) {
        try {
            val stream = if (audioManager?.ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
                AudioManager.STREAM_NOTIFICATION
            } else {
                AudioManager.STREAM_ALARM
            }
            val toneGen = ToneGenerator(stream, 85)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 180)
            Handler(Looper.getMainLooper()).postDelayed({
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 180)
                Handler(Looper.getMainLooper()).postDelayed({
                    toneGen.release()
                }, 350)
            }, 300)
        } catch (_: Exception) {
        }
    }
}
