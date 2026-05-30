package app.sukun.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppCooldownConfig(
    val packageName: String,
    val maxOpens: Int = 0,
    val maxDurationMinutes: Int = 0,
    val cooloffMinutes: Int = 30
) {
    val hasAnyLimit: Boolean get() = maxOpens > 0 || maxDurationMinutes > 0
}

class AppCooldownManager(private val prefs: Prefs) {

    data class DailyUsage(
        val date: String,
        val packageName: String,
        val openCount: Int = 0,
        val totalDurationMs: Long = 0L,
        val cooloffEndsAt: Long = 0L,
        val lastLaunchTimeMs: Long = 0L
    ) {
        val isCoolingOff: Boolean get() = cooloffEndsAt > System.currentTimeMillis()
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val today: String get() = dateFormat.format(Date())
    private val maxSessionMs = 4L * 60 * 60 * 1000

    fun getConfig(packageName: String): AppCooldownConfig? =
        prefs.getCooldownConfig(packageName)

    fun setConfig(config: AppCooldownConfig) = prefs.setCooldownConfig(config)

    fun removeConfig(packageName: String) {
        prefs.removeCooldownConfig(packageName)
        prefs.clearCooldownDailyUsage(packageName)
    }

    fun isInCooldown(packageName: String): Boolean =
        getUsage(packageName)?.isCoolingOff == true

    fun getCooloffEndsAt(packageName: String): Long =
        getUsage(packageName)?.cooloffEndsAt ?: 0L

    fun getOpenCount(packageName: String): Int =
        getUsage(packageName)?.openCount ?: 0

    fun getTotalDurationMs(packageName: String): Long =
        getUsage(packageName)?.totalDurationMs ?: 0L

    fun recordLaunch(packageName: String) {
        if (getConfig(packageName) == null) return
        val usage = getUsageOrNew(packageName)
        // Always count the open and start tracking the session, even during cool-off
        val newUsage = usage.copy(
            openCount = usage.openCount + 1,
            lastLaunchTimeMs = System.currentTimeMillis()
        )
        saveUsage(packageName, newUsage)
        if (!usage.isCoolingOff) checkAndApplyCooldown(packageName, newUsage)
    }

    fun recordReturnToLauncher(packageName: String) {
        if (getConfig(packageName) == null) return
        val usage = getUsage(packageName) ?: return
        if (usage.lastLaunchTimeMs == 0L) return

        val elapsed = minOf(System.currentTimeMillis() - usage.lastLaunchTimeMs, maxSessionMs)
        val newUsage = usage.copy(
            totalDurationMs = usage.totalDurationMs + elapsed,
            lastLaunchTimeMs = 0L
        )
        saveUsage(packageName, newUsage)

        if (usage.isCoolingOff) {
            // User opened the app during cool-off ("continue anyway") — reset the cool-off timer
            val config = getConfig(packageName) ?: return
            val cooloffEnds = System.currentTimeMillis() + config.cooloffMinutes * 60_000L
            saveUsage(packageName, newUsage.copy(cooloffEndsAt = cooloffEnds))
        } else {
            checkAndApplyCooldown(packageName, newUsage)
        }
    }

    private fun checkAndApplyCooldown(packageName: String, usage: DailyUsage) {
        val config = getConfig(packageName) ?: return
        val opensExceeded = config.maxOpens > 0 && usage.openCount >= config.maxOpens
        val durationExceeded = config.maxDurationMinutes > 0 &&
                usage.totalDurationMs >= config.maxDurationMinutes * 60_000L
        if (opensExceeded || durationExceeded) {
            val cooloffEnds = System.currentTimeMillis() + config.cooloffMinutes * 60_000L
            saveUsage(packageName, usage.copy(cooloffEndsAt = cooloffEnds, lastLaunchTimeMs = 0L))
        }
    }

    fun getCooledOffPackages(): Set<String> =
        prefs.getAllCooldownPackages().filter { isInCooldown(it) }.toSet()

    fun getConfiguredPackages(): Set<String> = prefs.getAllCooldownPackages()

    private fun getUsage(packageName: String): DailyUsage? {
        val usage = prefs.getCooldownDailyUsage(packageName) ?: return null
        return if (usage.date == today) usage else null
    }

    private fun getUsageOrNew(packageName: String): DailyUsage =
        getUsage(packageName) ?: DailyUsage(date = today, packageName = packageName)

    private fun saveUsage(packageName: String, usage: DailyUsage) =
        prefs.saveCooldownDailyUsage(packageName, usage)
}
