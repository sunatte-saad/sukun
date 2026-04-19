package app.sukun.helper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.sukun.R
import app.sukun.data.Constants
import app.sukun.data.Prefs

class AzanPlaybackService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private var audioManager: AudioManager? = null
    private var boostedAlarmVolumeFrom: Int? = null
    private var boostedAlarmVolumeTo: Int? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == Constants.PRAYER_STOP_ACTION) {
            stopSelf()
            return START_NOT_STICKY
        }
        val prayerKey = intent?.getStringExtra(Constants.EXTRA_PRAYER_KEY).orEmpty()
        startForeground(Constants.Prayer.NOTIFICATION_ID, buildNotification(prayerKey))
        registerScreenOffStopReceiver()
        playAzan(prayerKey)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        screenOffReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        screenOffReceiver = null
        mediaPlayer?.stopSafely()
        mediaPlayer?.release()
        mediaPlayer = null
        restoreAlarmVolume()
        super.onDestroy()
    }

    private fun playAzan(prayerKey: String) {
        mediaPlayer?.stopSafely()
        mediaPlayer?.release()

        val prefs = Prefs(applicationContext)
        val mediaUri = resolveAzanUri(prefs)
        val player = MediaPlayer()
        mediaPlayer = player
        try {
            boostAlarmVolume()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            player.setVolume(1f, 1f)
            player.setDataSource(applicationContext, mediaUri)
            player.setOnCompletionListener { stopSelf() }
            player.setOnErrorListener { _, _, _ ->
                stopSelf()
                true
            }
            player.prepare()
            player.start()
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun resolveAzanUri(prefs: Prefs): Uri {
        return when (prefs.azanSound) {
            Constants.AzanSound.CUSTOM -> {
                prefs.azanCustomUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
                    ?: defaultAzanUri()
            }

            Constants.AzanSound.MARYLEBONE -> rawUri(R.raw.azan_marylebone)
            else -> rawUri(R.raw.azan_makkah)
        }
    }

    private fun defaultAzanUri(): Uri = rawUri(R.raw.azan_makkah)

    private fun rawUri(rawRes: Int): Uri {
        return Uri.parse("android.resource://$packageName/$rawRes")
    }

    private fun registerScreenOffStopReceiver() {
        if (screenOffReceiver != null) return
        screenOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                    stopSelf()
                }
            }
        }
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    private fun boostAlarmVolume() {
        val manager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audioManager = manager
        val maxVolume = manager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val currentVolume = manager.getStreamVolume(AudioManager.STREAM_ALARM)
        if (maxVolume <= 0 || currentVolume >= maxVolume) return
        boostedAlarmVolumeFrom = currentVolume
        boostedAlarmVolumeTo = maxVolume
        manager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
    }

    private fun restoreAlarmVolume() {
        val manager = audioManager ?: return
        val initialVolume = boostedAlarmVolumeFrom ?: return
        val boostedVolume = boostedAlarmVolumeTo ?: return
        if (manager.getStreamVolume(AudioManager.STREAM_ALARM) == boostedVolume) {
            manager.setStreamVolume(AudioManager.STREAM_ALARM, initialVolume, 0)
        }
        boostedAlarmVolumeFrom = null
        boostedAlarmVolumeTo = null
    }

    private fun buildNotification(prayerKey: String): Notification {
        createNotificationChannel()
        val prayerName = when {
            prayerKey.isBlank() -> getString(R.string.prayer_time_now)
            else -> getString(
                R.string.playing_azan_for_prayer,
                getPrayerName(this, prayerKey)
            )
        }
        return NotificationCompat.Builder(this, Constants.Prayer.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.prayer_reminder))
            .setContentText(prayerName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .addAction(R.drawable.ic_close, getString(R.string.stop), buildStopPendingIntent())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun buildStopPendingIntent(): PendingIntent {
        val intent = Intent(this, AzanPlaybackService::class.java).apply {
            action = Constants.PRAYER_STOP_ACTION
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getService(this, Constants.Prayer.NOTIFICATION_ID, intent, flags)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val existingChannel = manager.getNotificationChannel(Constants.Prayer.NOTIFICATION_CHANNEL_ID)
        if (existingChannel != null) return
        val channel = NotificationChannel(
            Constants.Prayer.NOTIFICATION_CHANNEL_ID,
            getString(R.string.prayer_reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.prayer_reminder_channel_description)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        fun start(context: Context, prayerKey: String) {
            val intent = Intent(context, AzanPlaybackService::class.java).apply {
                putExtra(Constants.EXTRA_PRAYER_KEY, prayerKey)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}

private fun MediaPlayer.stopSafely() {
    try {
        if (isPlaying) stop()
    } catch (_: Exception) {
    }
}
