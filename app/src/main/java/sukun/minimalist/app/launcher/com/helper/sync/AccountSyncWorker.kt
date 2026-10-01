package sukun.minimalist.app.launcher.com.helper.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import sukun.minimalist.app.launcher.com.data.Prefs
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Nightly Google Drive sync (push local changes; silent pull when local is empty). */
class AccountSyncWorker(
    appContext: Context,
    params: androidx.work.WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!Prefs(applicationContext).isSignedIn) return Result.success()
        AccountSyncManager.runScheduledSync(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "account_drive_sync"

        fun schedule(context: Context) {
            val now = Calendar.getInstance()
            val nextMidnight = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now.timeInMillis) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            val initialDelayMs = nextMidnight.timeInMillis - now.timeInMillis
            val request = PeriodicWorkRequestBuilder<AccountSyncWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
