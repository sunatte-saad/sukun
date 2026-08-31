package sukun.minimalist.app.launcher.com.helper

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import sukun.minimalist.app.launcher.com.R
import sukun.minimalist.app.launcher.com.data.Constants
import sukun.minimalist.app.launcher.com.data.Prefs
import java.util.Calendar

/** Shared hourly-chime effects used by the alarm receiver and Settings previews. */
object HourlyChimeEffects {

    private const val CHIME_MAX_PLAYBACK_MS = 2500L
    private const val FLASH_SEQUENCE_MS = 1400L

    fun holdMsForStyle(style: String): Long = when (style) {
        Constants.ChimeStyle.FLASH -> FLASH_SEQUENCE_MS + 300L
        Constants.ChimeStyle.VIBRATE -> 600L
        else -> CHIME_MAX_PLAYBACK_MS + 500L
    }

    fun playStyle(context: Context, style: String, prefs: Prefs = Prefs(context)) {
        val appContext = context.applicationContext
        when (style) {
            Constants.ChimeStyle.VIBRATE -> doVibrate(appContext)
            Constants.ChimeStyle.FLASH -> doFlash(appContext)
            Constants.ChimeStyle.SILENT_NOTIFICATION -> {
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                postSilentNotification(appContext, hour)
            }
            else -> playChime(appContext, prefs)
        }
    }

    fun playSound(context: Context, sound: String, customUri: String = Prefs(context).hourlyChimeCustomUri) {
        val appContext = context.applicationContext
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager?.ringerMode == AudioManager.RINGER_MODE_SILENT) return

        when (sound) {
            Constants.ChimeSound.DEFAULT -> playDefaultNotificationSound(appContext)
            Constants.ChimeSound.CUSTOM -> {
                val uri = customUri.takeIf { it.isNotEmpty() }
                if (uri != null) playUriSound(appContext, Uri.parse(uri))
                else playDefaultNotificationSound(appContext)
            }
            else -> {
                if (!playBundledChime(appContext)) playFallbackTone(audioManager)
            }
        }
    }

    fun doVibrate(context: Context) {
        val pattern = longArrayOf(0, 120, 160, 120)
        val vibrator = resolveVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        val wakeLock = acquireWakeLock(context, "sukun:hourly_chime_vibrate", 800L)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (_: Exception) {
        } finally {
            Handler(Looper.getMainLooper()).postDelayed({
                releaseWakeLock(wakeLock)
            }, 500L)
        }
    }

    fun doFlash(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
        val cameraId = findTorchCameraId(cameraManager) ?: return
        val handler = Handler(Looper.getMainLooper())
        val wakeLock = acquireWakeLock(context, "sukun:hourly_chime_flash", FLASH_SEQUENCE_MS + 500L)

        fun setTorch(on: Boolean) {
            try {
                cameraManager.setTorchMode(cameraId, on)
            } catch (_: Exception) {
            }
        }

        try {
            val blinks = 3
            val onMs = 200L
            val cycleMs = 400L
            for (i in 0 until blinks) {
                val onAt = i * cycleMs
                val offAt = onAt + onMs
                handler.postDelayed({ setTorch(true) }, onAt)
                handler.postDelayed({
                    setTorch(false)
                    if (i == blinks - 1) releaseWakeLock(wakeLock)
                }, offAt)
            }
        } catch (_: Exception) {
            setTorch(false)
            releaseWakeLock(wakeLock)
        }
    }

    fun postSilentNotification(context: Context, hour: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel(Constants.HourlyChime.NOTIFICATION_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    Constants.HourlyChime.NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.hourly_chime),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
        val timeLabel = String.format("%02d:00", hour)
        val notification = NotificationCompat.Builder(context, Constants.HourlyChime.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.hourly_chime))
            .setContentText(timeLabel)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setSound(null)
            .setVibrate(null)
            .build()
        nm.notify(Constants.HourlyChime.NOTIFICATION_ID, notification)
    }

    fun playChime(context: Context, prefs: Prefs) {
        playSound(context, prefs.hourlyChimeSound, prefs.hourlyChimeCustomUri)
    }

    private fun resolveVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun findTorchCameraId(cameraManager: CameraManager): String? {
        var fallbackWithFlash: String? = null
        for (id in cameraManager.cameraIdList) {
            val chars = try {
                cameraManager.getCameraCharacteristics(id)
            } catch (_: Exception) {
                continue
            }
            val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            if (!hasFlash) continue
            val facing = chars.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_BACK) return id
            if (fallbackWithFlash == null) fallbackWithFlash = id
        }
        return fallbackWithFlash
    }

    private fun acquireWakeLock(context: Context, tag: String, timeoutMs: Long): PowerManager.WakeLock? {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return null
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag).also {
                it.setReferenceCounted(false)
                it.acquire(timeoutMs)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun releaseWakeLock(wakeLock: PowerManager.WakeLock?) {
        try {
            if (wakeLock?.isHeld == true) wakeLock.release()
        } catch (_: Exception) {
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
