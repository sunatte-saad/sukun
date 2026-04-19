package app.sukun.helper

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.sukun.R
import app.sukun.data.Prefs
import app.sukun.helper.canOpenNotificationsInFocusMode
import app.sukun.helper.getFocusModeAllowedPackages

class MyAccessibilityService : AccessibilityService() {
    private val prefs by lazy { Prefs(applicationContext) }
    private val keyguardManager by lazy {
        getSystemService(KEYGUARD_SERVICE) as KeyguardManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString().orEmpty()
        if (shouldBlockPackage(packageName)) {
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

    private fun shouldBlockPackage(packageName: String): Boolean {
        if (!prefs.isFocusModeActive() || packageName.isBlank()) return false
        if (packageName == SYSTEM_UI_PACKAGE) {
            return !keyguardManager.isKeyguardLocked && !prefs.canOpenNotificationsInFocusMode()
        }
        return packageName !in applicationContext.getFocusModeAllowedPackages(prefs)
    }

    companion object {
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    }
}