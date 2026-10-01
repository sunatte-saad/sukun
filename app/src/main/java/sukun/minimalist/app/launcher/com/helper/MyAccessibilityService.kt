package sukun.minimalist.app.launcher.com.helper

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import sukun.minimalist.app.launcher.com.BuildConfig
import sukun.minimalist.app.launcher.com.R
import sukun.minimalist.app.launcher.com.data.Prefs

class MyAccessibilityService : AccessibilityService() {
    private val prefs by lazy { Prefs(applicationContext) }
    private var notificationShadeActive = false
    private var lastBlockedPackage: String? = null
    private var actionReceiverRegistered = false

    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_LOCK_SCREEN -> lockScreen()
                ACTION_SHOW_RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerActionReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString().orEmpty()
        updateNotificationShadeState(packageName, event.eventType)
        if (shouldBlockPackage(packageName, event)) {
            notificationShadeActive = false
            lastBlockedPackage = packageName
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }

        handleGestureAction(event)
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        unregisterActionReceiver()
        super.onDestroy()
    }

    private fun handleGestureAction(event: AccessibilityEvent) {
        val description = event.contentDescription?.toString()
            ?: event.source?.contentDescription?.toString()
            ?: return
        when (description) {
            getString(R.string.lock_layout_description) -> lockScreen()
            getString(R.string.recents_layout_description) -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        }
    }

    private fun lockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }
    }

    private fun shouldBlockPackage(packageName: String, event: AccessibilityEvent): Boolean {
        if (!prefs.isFocusModeActive() || packageName.isBlank()) return false
        if (packageName == SYSTEM_UI_PACKAGE) {
            return !prefs.canOpenNotificationsInFocusMode()
        }
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && packageName == lastBlockedPackage) {
            return true
        }
        if (notificationShadeActive
            && packageName != BuildConfig.APPLICATION_ID
            && packageName != ANDROID_PACKAGE
            && packageName != SYSTEM_UI_PACKAGE
        ) {
            return true
        }
        return packageName !in applicationContext.getFocusModeAllowedPackages(prefs)
    }

    private fun updateNotificationShadeState(packageName: String, eventType: Int) {
        if (!prefs.isFocusModeActive()) {
            notificationShadeActive = false
            return
        }
        when {
            packageName == SYSTEM_UI_PACKAGE && (
                eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                    eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
                    eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                    eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
                ) -> {
                notificationShadeActive = true
                if (!prefs.canOpenNotificationsInFocusMode()) {
                    lastBlockedPackage = null
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }

            packageName == BuildConfig.APPLICATION_ID || packageName == ANDROID_PACKAGE -> {
                notificationShadeActive = false
                lastBlockedPackage = null
            }
        }
    }

    private fun registerActionReceiver() {
        if (actionReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(ACTION_LOCK_SCREEN)
            addAction(ACTION_SHOW_RECENTS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(actionReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(actionReceiver, filter)
        }
        actionReceiverRegistered = true
    }

    private fun unregisterActionReceiver() {
        if (!actionReceiverRegistered) return
        try {
            unregisterReceiver(actionReceiver)
        } catch (_: Exception) {
        }
        actionReceiverRegistered = false
    }

    companion object {
        private const val ANDROID_PACKAGE = "android"
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        const val ACTION_LOCK_SCREEN = "${BuildConfig.APPLICATION_ID}.ACTION_LOCK_SCREEN"
        const val ACTION_SHOW_RECENTS = "${BuildConfig.APPLICATION_ID}.ACTION_SHOW_RECENTS"

        fun lockScreen(context: Context) {
            context.sendBroadcast(
                Intent(ACTION_LOCK_SCREEN)
                    .setPackage(context.packageName)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            )
        }

        fun showRecents(context: Context) {
            context.sendBroadcast(
                Intent(ACTION_SHOW_RECENTS)
                    .setPackage(context.packageName)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            )
        }
    }
}
