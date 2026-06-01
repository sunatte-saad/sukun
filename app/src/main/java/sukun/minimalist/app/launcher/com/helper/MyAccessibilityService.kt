package sukun.minimalist.app.launcher.com.helper

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import sukun.minimalist.app.launcher.com.BuildConfig
import sukun.minimalist.app.launcher.com.R
import sukun.minimalist.app.launcher.com.data.Prefs
import sukun.minimalist.app.launcher.com.helper.canOpenNotificationsInFocusMode
import sukun.minimalist.app.launcher.com.helper.getFocusModeAllowedPackages

class MyAccessibilityService : AccessibilityService() {
    private val prefs by lazy { Prefs(applicationContext) }
    private val keyguardManager by lazy {
        getSystemService(KEYGUARD_SERVICE) as KeyguardManager
    }
    private var notificationShadeActive = false
    private var lastBlockedPackage: String? = null

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

        try {
            val source: AccessibilityNodeInfo = event.source ?: return
            if (source.className != "android.widget.FrameLayout") return

            when (source.contentDescription) {
                getString(R.string.lock_layout_description) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                }
                getString(R.string.recents_layout_description) -> {
                    performGlobalAction(GLOBAL_ACTION_RECENTS)
                }
            }
        } catch (e: Exception) {
            return
        }
    }

    override fun onInterrupt() {

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

    companion object {
        private const val ANDROID_PACKAGE = "android"
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    }
}
