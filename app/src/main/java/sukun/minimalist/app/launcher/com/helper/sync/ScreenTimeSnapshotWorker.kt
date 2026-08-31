package sukun.minimalist.app.launcher.com.helper.sync

import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import sukun.minimalist.app.launcher.com.data.Prefs
import sukun.minimalist.app.launcher.com.helper.appUsagePermissionGranted
import sukun.minimalist.app.launcher.com.helper.usageStats.EventLogWrapper
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ScreenTimeSnapshotWorker(
    appContext: Context,
    params: androidx.work.WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val prefs = Prefs(context)
        if (!prefs.showScreenTimeOnHome) return Result.success()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return Result.success()
        if (!context.appUsagePermissionGranted()) return Result.success()

        AnalyticsRollupManager.ensureCurrent(context)
        val cal = Calendar.getInstance()
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val (start, end) = dayBounds(cal, 0)
        val minutes = try {
            val wrapper = EventLogWrapper(context)
            wrapper.aggregateForegroundStats(
                wrapper.getForegroundStatsByTimestamps(start, end),
            ).sumOf { it.timeUsed } / 60_000L
        } catch (_: Exception) {
            0L
        }
        AnalyticsRollupManager.recordScreenTimeMinutes(context, day, minutes.toInt())
        AccountSyncManager.pushToDriveIfLocalNewer(context)
        return Result.success()
    }

    private fun dayBounds(cal: Calendar, daysAgo: Int): Pair<Long, Long> {
        val c = cal.clone() as Calendar
        c.add(Calendar.DAY_OF_YEAR, -daysAgo)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        val start = c.timeInMillis
        val end = if (daysAgo == 0) System.currentTimeMillis()
        else {
            c.add(Calendar.DAY_OF_YEAR, 1)
            c.timeInMillis
        }
        return start to end
    }

    companion object {
        private const val WORK_NAME = "screen_time_snapshot"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScreenTimeSnapshotWorker>(6, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
