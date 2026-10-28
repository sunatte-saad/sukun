package sukun.minimalist.app.launcher.com.helper

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
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import sukun.minimalist.app.launcher.com.R
import sukun.minimalist.app.launcher.com.data.Constants
import sukun.minimalist.app.launcher.com.data.Prefs

class AzanPlaybackService : Service() {

    private val playbackThread = HandlerThread("sukun-azan").apply { start() }
    private val playbackHandler = Handler(playbackThread.looper)

    @Volatile
    private var mediaPlayer: MediaPlayer? = null
    @Volatile
    private var playbackToken = 0
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
        val token = ++playbackToken
        playbackHandler.post { playAzan(token) }
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
        playbackToken++
        val player = mediaPlayer
        mediaPlayer = null
        playbackHandler.post {
            releasePlayer(player)
            restoreAlarmVolume()
        }
        playbackThread.quitSafely()
        super.onDestroy()
    }

    private fun playAzan(token: Int) {
        if (token != playbackToken) return
        releasePlayer(mediaPlayer)
        mediaPlayer = null
        if (token != playbackToken) return

        val prefs = Prefs(applicationContext)
        val cachedFile = getAzanPlaybackFile(applicationContext, prefs.azanSound)
        val customUri = if (prefs.azanSound == Constants.AzanSound.CUSTOM) {
            resolveAzanCustomUri(prefs)
        } else {
            null
        }
        if (cachedFile == null && customUri == null) {
            if (isBundledAzanSound(prefs.azanSound)) {
                scheduleAzanDownloadWhenOnline(applicationContext, prefs.azanSound)
            }
            stopSelf()
            return
        }

        val player = MediaPlayer()
        try {
            boostAlarmVolume()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            player.setVolume(1f, 1f)
            if (cachedFile != null) {
                player.setDataSource(cachedFile.absolutePath)
            } else {
                player.setDataSource(applicationContext, customUri!!)
            }
            if (token != playbackToken) {
                player.release()
                return
            }
            mediaPlayer = player
            player.setOnPreparedListener {
                if (token != playbackToken || mediaPlayer !== player) return@setOnPreparedListener
                try {
                    player.start()
                } catch (_: Exception) {
                    stopSelf()
                }
            }
            player.setOnCompletionListener {
                if (token == playbackToken && mediaPlayer === player) stopSelf()
            }
            player.setOnErrorListener { _, _, _ ->
                if (token == playbackToken && mediaPlayer === player) stopSelf()
                true
            }
            player.prepareAsync()
        } catch (_: Exception) {
            if (mediaPlayer === player) mediaPlayer = null
            releasePlayer(player)
            stopSelf()
        }
    }

    private fun releasePlayer(player: MediaPlayer?) {
        if (player == null) return
        player.setOnPreparedListener(null)
        player.setOnCompletionListener(null)
        player.setOnErrorListener(null)
        player.stopSafely()
        try {
            player.release()
        } catch (_: Exception) {
        }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        }
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
