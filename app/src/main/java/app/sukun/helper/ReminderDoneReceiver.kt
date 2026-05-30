package app.sukun.helper

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.sukun.data.Constants
import app.sukun.data.Prefs
import app.sukun.data.toJsonString
import app.sukun.data.toReminderList

class ReminderDoneReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(Constants.Reminder.EXTRA_ID, -1)
        if (id == -1) return

        val prefs = Prefs(context)
        val allReminders = prefs.remindersJson.toReminderList().toMutableList()
        val idx = allReminders.indexOfFirst { it.id == id }
        if (idx >= 0) {
            allReminders[idx] = allReminders[idx].copy(doneCount = allReminders[idx].doneCount + 1)
            prefs.remindersJson = allReminders.toJsonString()
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(Constants.Reminder.BASE_NOTIFICATION_ID + id)
    }
}
