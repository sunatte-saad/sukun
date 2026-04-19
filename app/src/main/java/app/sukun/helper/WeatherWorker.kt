package app.sukun.helper

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.sukun.data.Prefs
import kotlinx.coroutines.coroutineScope

class WeatherWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val prefs = Prefs(applicationContext)

    override suspend fun doWork(): Result = coroutineScope {
        if (!prefs.showWeatherOnHome) return@coroutineScope Result.success()

        val weather = refreshWeather(applicationContext, prefs)
        if (weather != null) Result.success() else Result.retry()
    }
}
