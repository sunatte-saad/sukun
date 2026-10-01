package sukun.minimalist.app.launcher.com.helper

import android.app.Activity
import android.os.Build
import android.widget.TextView
import android.widget.TimePicker
import androidx.appcompat.app.AlertDialog
import sukun.minimalist.app.launcher.com.R
import sukun.minimalist.app.launcher.com.data.Constants
import sukun.minimalist.app.launcher.com.data.Prefs

object FirstRunSetup {

    fun showPrivacyNotice(activity: Activity, onContinue: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.privacy_notice_title)
            .setMessage(R.string.privacy_notice_message)
            .setPositiveButton(R.string.continue_action) { _, _ -> onContinue() }
            .setCancelable(false)
            .show()
    }

    fun showWakeTimeQuestion(
        activity: Activity,
        prefs: Prefs,
        onFinished: () -> Unit,
    ) {
        showTimeQuestion(
            activity = activity,
            prefs = prefs,
            titleRes = R.string.setup_wake_title,
            messageRes = R.string.setup_wake_message,
            initialHour = prefs.mindfulMorningWakeHour,
            initialMinute = prefs.mindfulMorningWakeMinute,
        ) { hour, minute ->
            applyWakeTime(prefs, hour, minute)
            onFinished()
        }
    }

    fun showRestTimeQuestion(
        activity: Activity,
        prefs: Prefs,
        onFinished: () -> Unit,
    ) {
        showTimeQuestion(
            activity = activity,
            prefs = prefs,
            titleRes = R.string.setup_rest_title,
            messageRes = R.string.setup_rest_message,
            initialHour = prefs.dayEndHour,
            initialMinute = 0,
        ) { hour, minute ->
            applyRestTime(prefs, hour, minute)
            onFinished()
        }
    }

    private fun showTimeQuestion(
        activity: Activity,
        prefs: Prefs,
        titleRes: Int,
        messageRes: Int,
        initialHour: Int,
        initialMinute: Int,
        onPicked: (hour: Int, minute: Int) -> Unit,
    ) {
        val content = activity.layoutInflater.inflate(R.layout.dialog_setup_wake_time, null)
        content.findViewById<TextView>(R.id.setupTimeMessage).setText(messageRes)
        val picker = content.findViewById<TimePicker>(R.id.wakeTimePicker)
        picker.setIs24HourView(prefs.timeFormat24h)
        val hour = initialHour.coerceIn(0, 23)
        val minute = initialMinute.coerceIn(0, 59)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            picker.hour = hour
            picker.minute = minute
        } else {
            @Suppress("DEPRECATION")
            picker.currentHour = hour
            @Suppress("DEPRECATION")
            picker.currentMinute = minute
        }
        AlertDialog.Builder(activity)
            .setTitle(titleRes)
            .setView(content)
            .setPositiveButton(R.string.continue_action) { _, _ ->
                val pickedHour = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    picker.hour
                } else {
                    @Suppress("DEPRECATION")
                    picker.currentHour
                }
                val pickedMinute = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    picker.minute
                } else {
                    @Suppress("DEPRECATION")
                    picker.currentMinute
                }
                onPicked(pickedHour, pickedMinute)
            }
            .setCancelable(false)
            .show()
    }

    private fun applyWakeTime(prefs: Prefs, hour: Int, minute: Int) {
        prefs.mindfulMorningWakeHour = hour
        prefs.mindfulMorningWakeMinute = minute
        prefs.mindfulMorningEnabled = true
        prefs.dayStartHour = hour.coerceIn(0, 23)
        prefs.clockStyle = Constants.ClockStyle.DAY_RING
    }

    private fun applyRestTime(prefs: Prefs, hour: Int, minute: Int) {
        var endHour = hour.coerceIn(0, 23)
        if (minute >= 30) endHour = (endHour + 1).coerceAtMost(23)
        if (endHour <= prefs.dayStartHour) {
            endHour = (prefs.dayStartHour + 1).coerceAtMost(23)
        }
        prefs.dayEndHour = endHour
    }
}
