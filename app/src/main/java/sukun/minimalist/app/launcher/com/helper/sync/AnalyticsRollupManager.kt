package sukun.minimalist.app.launcher.com.helper.sync

import android.content.Context
import org.json.JSONObject
import sukun.minimalist.app.launcher.com.data.Constants
import sukun.minimalist.app.launcher.com.data.Prefs
import sukun.minimalist.app.launcher.com.data.PrayerLog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object AnalyticsRollupManager {

    fun ensureCurrent(context: Context) {
        val prefs = Prefs(context)
        loadPrayerRollup(prefs).also { rollup ->
            rollup.rolloverIfNeeded()
            savePrayerRollup(prefs, rollup)
        }
        if (prefs.showScreenTimeOnHome) {
            loadScreenTimeRollup(prefs).also { rollup ->
                rollup.rolloverIfNeeded()
                saveScreenTimeRollup(prefs, rollup)
            }
        }
        migrateLegacyPrayerLogsIfNeeded(context)
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
        return if (raw.isBlank()) {
            PrayerRollup.emptyForNow()
        } else {
            PrayerRollup.fromJson(JSONObject(raw)) ?: PrayerRollup.emptyForNow()
        }
    }

    fun loadScreenTimeRollup(prefs: Prefs): ScreenTimeRollup {
        val raw = prefs.screenTimeRollupJson
        return if (raw.isBlank()) {
            ScreenTimeRollup.emptyForNow()
        } else {
            ScreenTimeRollup.fromJson(JSONObject(raw)) ?: ScreenTimeRollup.emptyForNow()
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

    private fun migrateLegacyPrayerLogsIfNeeded(context: Context) {
        val prefs = Prefs(context)
        if (prefs.prayerRollupMigrated) return
        val logs = prefs.getPrayerLogs()
        if (logs.isEmpty()) {
            prefs.prayerRollupMigrated = true
            return
        }
        val rollup = PrayerRollup.emptyForNow()
        val monthPrefix = rollup.month
        logs.filter { it.dateKey.startsWith(monthPrefix) }.forEach { log ->
            val day = log.dateKey.substringAfterLast('-').toIntOrNull() ?: return@forEach
            rollup.markDay(log.prayerKey, day)
        }
        val yearPrefix = rollup.year
        Constants.Prayer.ALL.forEach { key ->
            val priorMonths = logs
                .filter { it.dateKey.startsWith(yearPrefix) && !it.dateKey.startsWith(monthPrefix) }
                .filter { it.prayerKey == key }
                .map { it.dateKey }
                .toSet()
                .size
            if (priorMonths > 0) rollup.annual[key] = priorMonths
        }
        savePrayerRollup(prefs, rollup)
        prefs.prayerRollupMigrated = true
        AccountSyncManager.markLocalDirty(context)
    }

    fun todayPrayerKeys(prefs: Prefs): Set<String> {
        val rollup = loadPrayerRollup(prefs).apply { rolloverIfNeeded() }
        val today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        return Constants.Prayer.ALL.filter { today in rollup.daysMarkedThisMonth(it) }.toSet()
    }

    fun monthPrayerLogs(prefs: Prefs): List<PrayerLog> {
        val rollup = loadPrayerRollup(prefs).apply { rolloverIfNeeded() }
        return prayerLogsForMonth(rollup, rollup.month)
    }

    fun yearPrayerDayCounts(prefs: Prefs): Map<String, Int> {
        val rollup = loadPrayerRollup(prefs).apply { rolloverIfNeeded() }
        return prayerAnnualCounts(rollup, includeCurrentMonth = true)
    }
}
