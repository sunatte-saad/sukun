package sukun.minimalist.app.launcher.com.helper.sync

import android.content.Context
import org.json.JSONObject
import sukun.minimalist.app.launcher.com.data.Constants
import sukun.minimalist.app.launcher.com.data.Prefs
import sukun.minimalist.app.launcher.com.data.PrayerLog
import java.util.Calendar
import java.util.Locale

object AnalyticsRollupManager {

    /**
     * Call after local Import or Drive restore so prayer marks in legacy [PRAYER_LOGS]
     * are merged into [PRAYER_ROLLUP_JSON] even when a prior migration flag was restored.
     */
    fun reconcileAfterRestore(context: Context) {
        reconcilePrayerAnalytics(context, forceMergeLegacy = true, markDirty = false)
    }

    fun ensureCurrent(context: Context) {
        val prefs = Prefs(context)
        loadPrayerRollup(prefs).also { rollup ->
            rollup.rolloverIfNeeded()
            savePrayerRollup(prefs, rollup)
        }
        if (prefs.showScreenTimeOnHome || prefs.screenTimeRollupJson.isNotBlank()) {
            loadScreenTimeRollup(prefs).also { rollup ->
                rollup.rolloverIfNeeded()
                saveScreenTimeRollup(prefs, rollup)
            }
        }
        reconcilePrayerAnalytics(context)
    }

    fun onPrayerMarked(context: Context, prayerKey: String) {
        val prefs = Prefs(context)
        val cal = Calendar.getInstance()
        val rollup = loadPrayerRollup(prefs).apply { rolloverIfNeeded(cal) }
        rollup.markDay(prayerKey, cal.get(Calendar.DAY_OF_MONTH))
        savePrayerRollup(prefs, rollup)
        AccountSyncManager.markLocalDirty(context)
    }

    fun onPrayerUnmarked(context: Context, prayerKey: String) {
        val prefs = Prefs(context)
        val cal = Calendar.getInstance()
        val rollup = loadPrayerRollup(prefs).apply { rolloverIfNeeded(cal) }
        rollup.unmarkDay(prayerKey, cal.get(Calendar.DAY_OF_MONTH))
        savePrayerRollup(prefs, rollup)
        AccountSyncManager.markLocalDirty(context)
    }

    fun recordScreenTimeMinutes(context: Context, dayOfMonth: Int, minutes: Int) {
        val prefs = Prefs(context)
        if (!prefs.showScreenTimeOnHome) return
        val rollup = loadScreenTimeRollup(prefs).apply { rolloverIfNeeded() }
        rollup.setDayMinutes(dayOfMonth, minutes)
        saveScreenTimeRollup(prefs, rollup)
        AccountSyncManager.markLocalDirty(context)
    }

    fun loadPrayerRollup(prefs: Prefs): PrayerRollup {
        val raw = prefs.prayerRollupJson
        if (raw.isBlank()) return PrayerRollup.emptyForNow()
        return try {
            PrayerRollup.fromJson(JSONObject(raw)) ?: PrayerRollup.emptyForNow()
        } catch (_: Exception) {
            PrayerRollup.emptyForNow()
        }
    }

    fun loadScreenTimeRollup(prefs: Prefs): ScreenTimeRollup {
        val raw = prefs.screenTimeRollupJson
        if (raw.isBlank()) return ScreenTimeRollup.emptyForNow()
        return try {
            ScreenTimeRollup.fromJson(JSONObject(raw)) ?: ScreenTimeRollup.emptyForNow()
        } catch (_: Exception) {
            ScreenTimeRollup.emptyForNow()
        }
    }

    fun savePrayerRollup(prefs: Prefs, rollup: PrayerRollup) {
        prefs.prayerRollupJson = rollup.toJson().toString()
    }

    fun saveScreenTimeRollup(prefs: Prefs, rollup: ScreenTimeRollup) {
        prefs.screenTimeRollupJson = rollup.toJson().toString()
    }

    fun applyPrayerRollup(prefs: Prefs, rollup: PrayerRollup?) {
        if (rollup == null) return
        rollup.rolloverIfNeeded()
        savePrayerRollup(prefs, rollup)
        prefs.prayerRollupMigrated = true
    }

    fun applyScreenTimeRollup(prefs: Prefs, rollup: ScreenTimeRollup?) {
        if (rollup == null) return
        rollup.rolloverIfNeeded()
        saveScreenTimeRollup(prefs, rollup)
    }

    fun prayerLogsForMonth(rollup: PrayerRollup, monthPrefix: String): List<PrayerLog> {
        if (!rollup.month.startsWith(monthPrefix.take(7))) return emptyList()
        val logs = mutableListOf<PrayerLog>()
        Constants.Prayer.ALL.forEach { key ->
            rollup.monthDays[key].orEmpty().forEach { day ->
                val dateKey = String.format(Locale.US, "%s-%02d", monthPrefix.take(7), day)
                logs.add(PrayerLog(key, dateKey, 0L))
            }
        }
        return logs
    }

    fun prayerAnnualCounts(rollup: PrayerRollup, includeCurrentMonth: Boolean = true): Map<String, Int> {
        val counts = rollup.annual.toMutableMap()
        if (includeCurrentMonth) {
            Constants.Prayer.ALL.forEach { key ->
                val monthCount = rollup.monthDays[key]?.size ?: 0
                if (monthCount > 0) counts[key] = (counts[key] ?: 0) + monthCount
            }
        }
        return counts
    }

    private fun reconcilePrayerAnalytics(
        context: Context,
        forceMergeLegacy: Boolean = false,
        markDirty: Boolean = true,
    ) {
        val prefs = Prefs(context)
        val logs = prefs.getPrayerLogs()
        val rollup = loadPrayerRollup(prefs).apply { rolloverIfNeeded() }
        val rollupHasData = !rollup.isEmpty()

        if (logs.isEmpty()) {
            prefs.prayerRollupMigrated = true
            if (!rollupHasData && prefs.prayerRollupJson.isBlank()) {
                savePrayerRollup(prefs, rollup)
            }
            return
        }

        // Remigrate when never migrated, or when restore left logs but an empty rollup
        // (common when PRAYER_ROLLUP_MIGRATED was true but the JSON blob was missing).
        if (prefs.prayerRollupMigrated && rollupHasData && !forceMergeLegacy) return

        mergeLegacyLogsIntoRollup(rollup, logs)
        savePrayerRollup(prefs, rollup)
        prefs.prayerRollupMigrated = true
        if (markDirty && (forceMergeLegacy || !rollupHasData)) {
            AccountSyncManager.markLocalDirty(context)
        }
    }

    private fun mergeLegacyLogsIntoRollup(rollup: PrayerRollup, logs: List<PrayerLog>) {
        val monthPrefix = rollup.month
        logs.filter { it.dateKey.startsWith(monthPrefix) }.forEach { log ->
            val day = log.dateKey.substringAfterLast('-').toIntOrNull() ?: return@forEach
            rollup.markDay(log.prayerKey, day)
        }
        val yearPrefix = rollup.year
        Constants.Prayer.ALL.forEach { key ->
            val priorDays = logs
                .filter { it.dateKey.startsWith(yearPrefix) && !it.dateKey.startsWith(monthPrefix) }
                .filter { it.prayerKey == key }
                .map { it.dateKey }
                .toSet()
                .size
            if (priorDays > rollup.annualCount(key)) rollup.annual[key] = priorDays
        }
    }

    fun todayPrayerKeys(prefs: Prefs): Set<String> {
        val rollup = loadPrayerRollup(prefs).apply { rolloverIfNeeded() }
        val today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        val fromRollup = Constants.Prayer.ALL.filter { today in rollup.daysMarkedThisMonth(it) }.toSet()
        if (fromRollup.isNotEmpty()) return fromRollup
        // Fallback to legacy logs if rollup is empty after a partial restore.
        val dateKey = String.format(
            Locale.US,
            "%04d-%02d-%02d",
            Calendar.getInstance().get(Calendar.YEAR),
            Calendar.getInstance().get(Calendar.MONTH) + 1,
            today,
        )
        return prefs.getPrayerLogs()
            .filter { it.dateKey == dateKey }
            .map { it.prayerKey }
            .toSet()
    }

    fun monthPrayerLogs(prefs: Prefs): List<PrayerLog> {
        val rollup = loadPrayerRollup(prefs).apply { rolloverIfNeeded() }
        val fromRollup = prayerLogsForMonth(rollup, rollup.month)
        if (fromRollup.isNotEmpty()) return fromRollup
        val monthPrefix = rollup.month
        return prefs.getPrayerLogs().filter { it.dateKey.startsWith(monthPrefix) }
    }

    /** Days prayed per prayer in the current month (at most one mark per calendar day). */
    fun monthPrayerDayCounts(prefs: Prefs): Map<String, Int> {
        val rollup = loadPrayerRollup(prefs).apply { rolloverIfNeeded() }
        val fromRollup = Constants.Prayer.ALL.associateWith { key ->
            rollup.monthDays[key]?.size ?: 0
        }
        if (fromRollup.values.any { it > 0 }) return fromRollup
        val monthPrefix = rollup.month
        val logs = prefs.getPrayerLogs().filter { it.dateKey.startsWith(monthPrefix) }
        if (logs.isEmpty()) return fromRollup
        return Constants.Prayer.ALL.associateWith { key ->
            logs.filter { it.prayerKey == key }.map { it.dateKey }.toSet().size
        }
    }

    fun yearPrayerDayCounts(prefs: Prefs): Map<String, Int> {
        val rollup = loadPrayerRollup(prefs).apply { rolloverIfNeeded() }
        val fromRollup = prayerAnnualCounts(rollup, includeCurrentMonth = true)
        if (fromRollup.values.any { it > 0 }) return fromRollup
        val yearPrefix = rollup.year
        val logs = prefs.getPrayerLogs().filter { it.dateKey.startsWith(yearPrefix) }
        if (logs.isEmpty()) return fromRollup
        return Constants.Prayer.ALL.associateWith { key ->
            logs.filter { it.prayerKey == key }.map { it.dateKey }.toSet().size
        }
    }
}

private fun PrayerRollup.isEmpty(): Boolean =
    monthDays.values.all { it.isEmpty() } && annual.values.all { it <= 0 }
