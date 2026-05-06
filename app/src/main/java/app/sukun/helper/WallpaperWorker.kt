package app.sukun.helper

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.sukun.data.Constants
import app.sukun.data.Prefs
import kotlinx.coroutines.coroutineScope

class WallpaperWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    private val prefs = Prefs(applicationContext)

    override suspend fun doWork(): Result = coroutineScope {
        if (prefs.dailyWallpaper.not()) return@coroutineScope Result.success()
        if (prefs.appTheme == AppCompatDelegate.MODE_NIGHT_YES && !isSukunDefault(applicationContext))
            return@coroutineScope Result.retry()

        val success =
            run {
                val wallType = checkWallpaperType()
                val localWallpaper = getRandomLocalWallpaperAsset(applicationContext, prefs.dailyWallpaperUrl)
                if (localWallpaper != null) {
                    val (assetName, wallpaperKey) = localWallpaper
                    prefs.dailyWallpaperUrl = wallpaperKey
                    setWallpaperFromAsset(
                        appContext = applicationContext,
                        assetName = assetName,
                        darkWallpaper = wallType == Constants.WALL_TYPE_DARK
                    )
                } else {
                    val wallpaperUrl = getTodaysWallpaper(wallType, prefs.firstOpenTime)
                    if (prefs.dailyWallpaperUrl == wallpaperUrl)
                        true
                    else {
                        prefs.dailyWallpaperUrl = wallpaperUrl
                        setWallpaper(
                            appContext = applicationContext,
                            url = wallpaperUrl,
                            darkWallpaper = wallType == Constants.WALL_TYPE_DARK
                        )
                    }
                }
            }

        if (success)
            Result.success()
        else
            Result.retry()
    }

    private fun checkWallpaperType(): String {
        return when (prefs.appTheme) {
            AppCompatDelegate.MODE_NIGHT_YES -> Constants.WALL_TYPE_DARK
            AppCompatDelegate.MODE_NIGHT_NO -> Constants.WALL_TYPE_DARK
            else -> Constants.WALL_TYPE_DARK
        }
    }
}
