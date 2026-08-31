package sukun.minimalist.app.launcher.com.helper.sync

import android.content.Context
import android.util.Log
import org.json.JSONObject
import sukun.minimalist.app.launcher.com.helper.BackupHelper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Stores the unified Sukun backup JSON in the user's Google Drive app-data folder. */
object GoogleDriveBackupHelper {

    private const val TAG = "SukunDrive"
    private const val BACKUP_FILE_NAME = "sukun-backup.json"
    private const val DRIVE_API = "https://www.googleapis.com/drive/v3"
    private const val DRIVE_UPLOAD = "https://www.googleapis.com/upload/drive/v3"

    data class RemoteBackup(
        val updatedAt: Long,
        val payloadJson: String,
        val isFullFormat: Boolean,
    )

    suspend fun download(context: Context, accessToken: String): RemoteBackup? {
        return try {
            val file = findBackupFile(accessToken) ?: return null
            val fileId = file.optString("id").takeIf { it.isNotBlank() } ?: return null
            val modifiedTime = parseDriveTime(file.optString("modifiedTime"))
            val payload = downloadFileContent(accessToken, fileId) ?: return null
            val updatedAt = maxOf(
                modifiedTime,
                runCatching {
                    BackupHelper.backupUpdatedAt(JSONObject(payload))
                }.getOrDefault(0L),
            )
            RemoteBackup(
                updatedAt = updatedAt,
                payloadJson = payload,
                isFullFormat = BackupHelper.isFullBackupJson(payload),
            )
        } catch (e: Exception) {
            Log.e(TAG, "download failed", e)
            null
        }
    }

    suspend fun upload(accessToken: String, root: JSONObject, updatedAt: Long): Boolean {
        return try {
            val payload = root.toString()
            val existing = findBackupFile(accessToken)
            val ok = if (existing != null) {
                updateFileContent(accessToken, existing.getString("id"), payload)
            } else {
                createFile(accessToken, payload)
            }
            if (ok) Log.i(TAG, "upload ok updatedAt=$updatedAt")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "upload failed", e)
            false
        }
    }

    suspend fun delete(accessToken: String): Boolean {
        return try {
            val existing = findBackupFile(accessToken) ?: return true
            val fileId = existing.optString("id").takeIf { it.isNotBlank() } ?: return true
            val url = "$DRIVE_API/files/$fileId"
            val ok = driveDelete(accessToken, url)
            if (ok) Log.i(TAG, "delete ok fileId=$fileId")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "delete failed", e)
            false
        }
    }

    private fun findBackupFile(accessToken: String): JSONObject? {
        val query = URLEncoder.encode(
            "name='$BACKUP_FILE_NAME' and trashed=false",
            StandardCharsets.UTF_8.name(),
        )
        val url = "$DRIVE_API/files?spaces=appDataFolder&fields=files(id,name,modifiedTime)&q=$query"
        val response = driveGet(accessToken, url) ?: return null
        val files = response.optJSONArray("files") ?: return null
        if (files.length() == 0) return null
        return files.getJSONObject(0)
    }

    private fun downloadFileContent(accessToken: String, fileId: String): String? {
        val url = "$DRIVE_API/files/$fileId?alt=media"
        return driveGetRaw(accessToken, url)
    }

    private fun createFile(accessToken: String, payload: String): Boolean {
        val metadata = JSONObject().apply {
            put("name", BACKUP_FILE_NAME)
            put("parents", org.json.JSONArray().put("appDataFolder"))
        }
        val boundary = "sukun_${System.currentTimeMillis()}"
        val body = buildMultipartBody(
            boundary,
            metadata.toString(),
            "application/json; charset=UTF-8",
            payload,
            "application/json; charset=UTF-8",
        )
        val url = "$DRIVE_UPLOAD/files?uploadType=multipart&fields=id"
        return drivePost(accessToken, url, body, "multipart/related; boundary=$boundary") != null
    }

    private fun updateFileContent(accessToken: String, fileId: String, payload: String): Boolean {
        val url = "$DRIVE_UPLOAD/files/$fileId?uploadType=media"
        return drivePatch(accessToken, url, payload, "application/json; charset=UTF-8")
    }

    private fun buildMultipartBody(
        boundary: String,
        metaPart: String,
        metaType: String,
        filePart: String,
        fileType: String,
    ): ByteArray {
        val builder = StringBuilder()
        builder.append("--").append(boundary).append("\r\n")
        builder.append("Content-Type: ").append(metaType).append("\r\n\r\n")
        builder.append(metaPart).append("\r\n")
        builder.append("--").append(boundary).append("\r\n")
        builder.append("Content-Type: ").append(fileType).append("\r\n\r\n")
        builder.append(filePart).append("\r\n")
        builder.append("--").append(boundary).append("--\r\n")
        return builder.toString().toByteArray(StandardCharsets.UTF_8)
    }

    private fun driveGet(accessToken: String, urlString: String): JSONObject? {
        val raw = driveGetRaw(accessToken, urlString) ?: return null
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            null
        }
    }

    private fun driveGetRaw(accessToken: String, urlString: String): String? {
        val connection = openConnection(urlString, "GET", accessToken)
        return readResponse(connection)
    }

    private fun drivePost(
        accessToken: String,
        urlString: String,
        body: ByteArray,
        contentType: String,
    ): String? {
        val connection = openConnection(urlString, "POST", accessToken)
        connection.setRequestProperty("Content-Type", contentType)
        connection.doOutput = true
        connection.outputStream.use { it.write(body) }
        return readResponse(connection)
    }

    private fun drivePatch(
        accessToken: String,
        urlString: String,
        body: String,
        contentType: String,
    ): Boolean {
        val connection = openConnection(urlString, "PATCH", accessToken)
        connection.setRequestProperty("Content-Type", contentType)
        connection.doOutput = true
        OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { it.write(body) }
        return connection.responseCode in 200..299
    }

    private fun driveDelete(accessToken: String, urlString: String): Boolean {
        val connection = openConnection(urlString, "DELETE", accessToken)
        return try {
            val code = connection.responseCode
            code in 200..299 || code == 404
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(urlString: String, method: String, accessToken: String): HttpURLConnection {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
        return connection
    }

    private fun readResponse(connection: HttpURLConnection): String? {
        return try {
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                Log.w(TAG, "Drive HTTP ${connection.responseCode}: ${connection.responseMessage}")
                connection.errorStream
            }
            stream?.use {
                BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readText()
            }?.takeIf { connection.responseCode in 200..299 }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseDriveTime(raw: String): Long {
        if (raw.isBlank()) return 0L
        val patterns = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
        )
        for (pattern in patterns) {
            try {
                val fmt = java.text.SimpleDateFormat(pattern, java.util.Locale.US)
                fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
                return fmt.parse(raw)?.time ?: 0L
            } catch (_: Exception) {
            }
        }
        return 0L
    }
}
