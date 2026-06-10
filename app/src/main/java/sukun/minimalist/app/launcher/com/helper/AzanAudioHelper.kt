package sukun.minimalist.app.launcher.com.helper

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import sukun.minimalist.app.launcher.com.data.Constants
import sukun.minimalist.app.launcher.com.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val AZAN_CACHE_DIR = "azan"

fun isBundledAzanSound(sound: String): Boolean {
    return sound == Constants.AzanSound.MAKKAH || sound == Constants.AzanSound.MARYLEBONE
}

fun getAzanFileName(sound: String): String? = when (sound) {
    Constants.AzanSound.MAKKAH -> "azan_makkah.mp3"
    Constants.AzanSound.MARYLEBONE -> "azan_marylebone.mp3"
    else -> null
}

fun getAzanDownloadUrl(sound: String): String? {
    val fileName = getAzanFileName(sound) ?: return null
    return "${Constants.URL_AZAN_BASE}$fileName"
}

fun getAzanCacheFile(context: Context, sound: String): File? {
    val fileName = getAzanFileName(sound) ?: return null
    return File(File(context.filesDir, AZAN_CACHE_DIR), fileName)
}

fun isAzanCached(context: Context, sound: String): Boolean {
    val file = getAzanCacheFile(context, sound) ?: return false
    return file.isFile && file.length() > 0L
}

suspend fun downloadAzan(context: Context, sound: String): Boolean {
    if (!isBundledAzanSound(sound)) return true
    if (isAzanCached(context, sound)) return true
    if (!context.isNetworkAvailable()) return false

    return withContext(Dispatchers.IO) {
        val destination = getAzanCacheFile(context, sound) ?: return@withContext false
        val downloadUrl = getAzanDownloadUrl(sound) ?: return@withContext false
        destination.parentFile?.mkdirs()

        var connection: HttpURLConnection? = null
        try {
            connection = URL(downloadUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.connect()
            if (connection.responseCode !in 200..299) return@withContext false

            val tempFile = File(destination.parentFile, "${destination.name}.download")
            connection.inputStream.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            if (tempFile.length() <= 0L) {
                tempFile.delete()
                return@withContext false
            }
            if (destination.exists()) destination.delete()
            tempFile.renameTo(destination)
            destination.isFile && destination.length() > 0L
        } catch (_: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }
}

fun getAzanPlaybackFile(context: Context, sound: String): File? {
    if (!isBundledAzanSound(sound)) return null
    return getAzanCacheFile(context, sound)?.takeIf { it.isFile && it.length() > 0L }
}

fun resolveAzanCustomUri(prefs: Prefs): Uri? {
    return prefs.azanCustomUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
}

fun scheduleAzanDownloadWhenOnline(context: Context, sound: String) {
    if (!isBundledAzanSound(sound) || isAzanCached(context, sound)) return
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
        "${Constants.AZAN_DOWNLOAD_WORKER_NAME}_$sound",
        ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<AzanDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(AzanDownloadWorker.KEY_SOUND to sound))
            .build()
    )
}
