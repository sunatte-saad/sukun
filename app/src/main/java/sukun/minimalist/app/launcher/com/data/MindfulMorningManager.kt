package sukun.minimalist.app.launcher.com.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import sukun.minimalist.app.launcher.com.helper.PremiumAccess
import java.util.Calendar

class MindfulMorningManager(private val context: Context, private val prefs: Prefs) {

    companion object {
        const val MASKED_LABEL = "..."
    }

    private val socialPackages = setOf(
        "com.instagram.android",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.twitter.android",
        "com.x.android",
        "com.zhiliaoapp.musically",
        "com.snapchat.android",
        "com.reddit.frontpage",
        "com.linkedin.android",
        "com.pinterest",
        "com.google.android.youtube",
        "com.netflix.mediaclient",
        "com.tumblr"
    )

    fun isInWindow(): Boolean {
        if (!prefs.mindfulMorningEnabled) return false
        val now = System.currentTimeMillis()
        val start = wakeStartToday()
        val end = start + prefs.mindfulMorningDurationHours * 3600_000L
        return now in start until end
    }

    /** Grey out / hide the name while the morning window is active. */
    fun isBlocked(packageName: String): Boolean {
        if (!isInWindow()) return false
        return isSocialApp(packageName)
    }

    /** Hard severity: social apps cannot be opened during the window. */
    fun isLaunchBlocked(packageName: String): Boolean {
        return prefs.mindfulMorningHard &&
            PremiumAccess.hasPremiumAccess(prefs) &&
            isBlocked(packageName)
    }

    fun getBlockedUntilTime(): Long {
        if (!prefs.mindfulMorningEnabled) return 0L
        return wakeStartToday() + prefs.mindfulMorningDurationHours * 3600_000L
    }

    private fun wakeStartToday(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, prefs.mindfulMorningWakeHour.coerceIn(0, 23))
            set(Calendar.MINUTE, prefs.mindfulMorningWakeMinute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun isSocialApp(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (socialPackages.contains(packageName)) return true
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                appInfo.category == ApplicationInfo.CATEGORY_SOCIAL
        } catch (_: Exception) {
            false
        }
    }
}
