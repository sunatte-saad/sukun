package sukun.minimalist.app.launcher.com.helper

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import sukun.minimalist.app.launcher.com.data.Constants
import sukun.minimalist.app.launcher.com.data.Prefs
import kotlinx.coroutines.coroutineScope

class WallpaperWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    private val prefs = Prefs(applicationContext)

    override suspend fun doWork(): Result = coroutineScope {
        if (prefs.dailyWallpaper.not()) return@coroutineScope Result.success()
        if (prefs.isEffectivelyDarkTheme() && !isSukunDefault(applicationContext))
            return@coroutineScope Result.retry()

        val forceRefresh = inputData.getBoolean(KEY_FORCE_REFRESH, false)
        if (!forceRefresh && prefs.lastWallpaperUpdateTime > 0L &&
            System.currentTimeMillis() - prefs.lastWallpaperUpdateTime < Constants.ONE_DAY_IN_MILLIS
        ) {
            return@coroutineScope Result.success()
        }

        if (!applicationContext.isNetworkAvailable()) {
            applyOfflineFallback()
            return@coroutineScope Result.success()
        }

        val wallType = checkWallpaperType()
        val wallpaperUrl = getRandomWallpaperUrl(prefs.dailyWallpaperUrl)
        val success = when {
            prefs.dailyWallpaperUrl == wallpaperUrl -> true
            applyWallpaper(wallpaperUrl, wallType) -> true
            applyWallpaper(getDefaultWallpaperUrl(), wallType) -> true
            else -> {
                applyOfflineFallback()
                false
            }
        }

        if (success)
            Result.success()
        else
            Result.retry()
    }

    private suspend fun applyWallpaper(url: String, wallType: String): Boolean {
        val applied = setWallpaper(
            appContext = applicationContext,
            url = url,
            darkWallpaper = wallType == Constants.WALL_TYPE_DARK
        )
        if (applied) {
            prefs.dailyWallpaperUrl = url
            prefs.lastWallpaperUpdateTime = System.currentTimeMillis()
            prefs.wallpaperPendingSync = false
        }
        return applied
    }

    private fun applyOfflineFallback() {
        setPlainWallpaperByTheme(applicationContext, prefs.appTheme)
        prefs.wallpaperPendingSync = true
        scheduleSyncWhenOnline(applicationContext)
    }

    private fun checkWallpaperType(): String {
        return when (prefs.appTheme) {
            AppCompatDelegate.MODE_NIGHT_YES -> Constants.WALL_TYPE_DARK
            AppCompatDelegate.MODE_NIGHT_NO -> Constants.WALL_TYPE_LIGHT
            Constants.THEME_MODE_AMBIENT_LIGHT -> if (prefs.ambientThemeDark) {
                Constants.WALL_TYPE_DARK
            } else {
                Constants.WALL_TYPE_LIGHT
            }
            else -> if (applicationContext.isDarkThemeOn()) {
                Constants.WALL_TYPE_DARK
            } else {
                Constants.WALL_TYPE_LIGHT
            }
        }
    }

    companion object {
        const val KEY_FORCE_REFRESH = "force_refresh"

        fun scheduleSyncWhenOnline(context: Context) {
            if (!Prefs(context).dailyWallpaper) return
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                Constants.WALLPAPER_SYNC_WORKER_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<WallpaperWorker>()
                    .setConstraints(constraints)
                    .setInputData(workDataOf(KEY_FORCE_REFRESH to true))
                    .build()
            )
        }
    }
}
