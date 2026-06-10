package sukun.minimalist.app.launcher.com.helper

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class AzanDownloadWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val sound = inputData.getString(KEY_SOUND).orEmpty()
        if (!isBundledAzanSound(sound)) return Result.success()
        return if (downloadAzan(applicationContext, sound)) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        const val KEY_SOUND = "azan_sound"
    }
}
