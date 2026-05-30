package app.sukun.helper

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.sukun.MainActivity
import app.sukun.R
import app.sukun.data.Constants
import app.sukun.data.Prefs
import app.sukun.data.toJsonString
import app.sukun.data.toReminderList

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs(context).showRemindersOnHome) return
        val id = intent.getIntExtra(Constants.Reminder.EXTRA_ID, -1)
        if (id == -1) return
        val title = intent.getStringExtra(Constants.Reminder.EXTRA_TITLE) ?: return
        val message = intent.getStringExtra(Constants.Reminder.EXTRA_MESSAGE) ?: ""

        val prefs = Prefs(context)
        val allReminders = prefs.remindersJson.toReminderList().toMutableList()
        val idx = allReminders.indexOfFirst { it.id == id }
        if (idx >= 0) {
            allReminders[idx] = allReminders[idx].copy(fireCount = allReminders[idx].fireCount + 1)
            prefs.remindersJson = allReminders.toJsonString()
            if (allReminders[idx].enabled) ReminderScheduler.schedule(context, allReminders[idx])
        }

        showNotification(context, id, title, message)
    }

    private fun showNotification(context: Context, id: Int, title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager, context)

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, id, launchIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val doneIntent = Intent(context, ReminderDoneReceiver::class.java).apply {
            action = Constants.Reminder.ACTION_DONE
            putExtra(Constants.Reminder.EXTRA_ID, id)
        }
        val donePi = PendingIntent.getBroadcast(
            context,
            Constants.Reminder.BASE_DONE_REQUEST_CODE + id,
            doneIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, Constants.Reminder.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .addAction(0, context.getString(R.string.reminder_done), donePi)
            .build()

        manager.notify(Constants.Reminder.BASE_NOTIFICATION_ID + id, notification)
    }

    private fun ensureChannel(manager: NotificationManager, context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (manager.getNotificationChannel(Constants.Reminder.NOTIFICATION_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            Constants.Reminder.NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.reminder_channel_description)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }
}
