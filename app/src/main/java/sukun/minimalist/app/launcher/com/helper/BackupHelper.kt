package sukun.minimalist.app.launcher.com.helper

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import sukun.minimalist.app.launcher.com.data.Prefs

object BackupHelper {

    private const val PREFS_FILENAME = "sukun.minimalist.app.launcher.com"
    const val FORMAT_VERSION = 1
    const val MAGIC = "sukun-backup"

    const val KEY_MAGIC = "_magic"
    const val KEY_VERSION = "_version"
    const val KEY_APP_VERSION = "_appVersion"
    const val KEY_CREATED_AT = "_createdAt"
    const val KEY_DATA = "data"

    /** Billing secrets — never export, import, or upload. Play Billing restores premium. */
    val BILLING_KEYS = setOf(
        "PRO_USER",
        "PRO_PURCHASE_TOKEN",
    )

    /** Never uploaded to Drive / never overwritten from cloud without local preserve. */
    val CLOUD_EXCLUDE_KEYS = BILLING_KEYS + setOf(
        "SYNC_PAYLOAD_UPDATED_AT",
        "SYNC_LAST_UPLOAD_AT",
        "SYNC_DECLINED_REMOTE_UPDATED_AT",
    )

    /** Keys always stripped from Export files. */
    val EXPORT_EXCLUDE_KEYS = BILLING_KEYS

    /** Keys always preserved from the current device on Import / Drive restore. */
    val IMPORT_PRESERVE_KEYS = BILLING_KEYS

    /** SharedPreferences keys whose integral JSON values must be restored as Longs. */
    private val LONG_PREF_KEYS = setOf(
        "FIRST_OPEN_TIME",
        "LAST_WALLPAPER_UPDATE_TIME",
        "SCREEN_TIME_LAST_UPDATED",
        "LAUNCHER_RECREATE_TIMESTAMP",
        "FOCUS_MODE_ENDS_AT",
        "FOCUS_MODE_LAST_DURATION",
        "WEATHER_LAST_UPDATED",
        "PRAYER_NEXT_AT",
        "PRAYER_LAST_UPDATED",
        "SHARE_SHOWN_TIME",
        "ACCOUNT_TRIAL_START",
        "SYNC_PAYLOAD_UPDATED_AT",
        "SYNC_LAST_UPLOAD_AT",
        "SYNC_DECLINED_REMOTE_UPDATED_AT",
    )

    fun buildBackupRoot(
        context: Context,
        excludeKeys: Set<String> = emptySet(),
        versionMs: Long? = null,
    ): JSONObject {
        val data = collectPrefsData(context, excludeKeys)
        return wrapBackupRoot(context, data, versionMs ?: System.currentTimeMillis())
    }

    fun buildCloudBackupRoot(context: Context, versionMs: Long): JSONObject =
        buildBackupRoot(context, CLOUD_EXCLUDE_KEYS, versionMs)

    fun backupContentFingerprint(root: JSONObject): String =
        root.optJSONObject(KEY_DATA)?.toString().orEmpty()

    fun backupUpdatedAt(root: JSONObject): Long = root.optLong(KEY_CREATED_AT, 0L)

    fun exportToUri(context: Context, uri: Uri): Boolean {
        return try {
            val root = buildBackupRoot(context, excludeKeys = EXPORT_EXCLUDE_KEYS)
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(root.toString(2).toByteArray(Charsets.UTF_8))
            } ?: return false
            true
        } catch (_: Exception) {
            false
        }
    }

    fun isValidBackup(context: Context, uri: Uri): Boolean {
        return try {
            val text = readText(context, uri) ?: return false
            isValidBackupJson(text)
        } catch (_: Exception) {
            false
        }
    }

    fun isValidBackupJson(text: String): Boolean {
        return try {
            val root = JSONObject(text)
            root.optString(KEY_MAGIC) == MAGIC && root.has(KEY_DATA)
        } catch (_: Exception) {
            false
        }
    }

    fun isFullBackupJson(text: String): Boolean = isValidBackupJson(text)

    fun importFromUri(context: Context, uri: Uri): Boolean {
        return try {
            val text = readText(context, uri) ?: return false
            applyBackupJson(context, text, preserveKeys = IMPORT_PRESERVE_KEYS)
        } catch (_: Exception) {
            false
        }
    }

    fun applyBackupJson(
        context: Context,
        json: String,
        preserveKeys: Set<String> = IMPORT_PRESERVE_KEYS,
    ): Boolean {
        return try {
            val root = JSONObject(json)
            if (root.optString(KEY_MAGIC) != MAGIC) return false
            applyBackupRoot(context, root, preserveKeys)
        } catch (_: Exception) {
            false
        }
    }

    data class TrialClocks(
        val firstOpenTime: Long,
        val accountTrialStart: Long,
    )

    fun captureTrialClocks(context: Context): TrialClocks {
        val prefs = Prefs(context.applicationContext)
        return TrialClocks(prefs.firstOpenTime, prefs.accountTrialStart)
    }

    /**
     * Trial must never reset or extend via backup: keep the earliest non-zero clock
     * from before restore vs values that arrived in the backup.
     */
    fun enforceMonotonicTrial(context: Context, before: TrialClocks) {
        val prefs = Prefs(context.applicationContext)
        val candidates = mutableListOf<Long>()
        if (before.firstOpenTime > 0L) candidates += before.firstOpenTime
        if (before.accountTrialStart > 0L) candidates += before.accountTrialStart
        if (prefs.firstOpenTime > 0L) candidates += prefs.firstOpenTime
        if (prefs.accountTrialStart > 0L) candidates += prefs.accountTrialStart
        val earliest = candidates.minOrNull() ?: return
        prefs.firstOpenTime = earliest
        prefs.accountTrialStart = earliest
    }

    fun applyBackupRoot(
        context: Context,
        root: JSONObject,
        preserveKeys: Set<String> = IMPORT_PRESERVE_KEYS,
    ): Boolean {
        return try {
            val data = root.optJSONObject(KEY_DATA) ?: return false
            val beforeTrial = captureTrialClocks(context)
            val prefsStore = context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)
            val preserved = readPreservedValues(prefsStore, preserveKeys)
            // Strip billing + never apply trial reset from crafted keys blindly —
            // trial is corrected after write via enforceMonotonicTrial.
            val keysToStrip = BILLING_KEYS
            prefsStore.edit(commit = true) {
                clear()
                applyDataObject(this, data, skipKeys = keysToStrip)
                restorePreservedValues(this, preserved)
            }
            enforceMonotonicTrial(context, beforeTrial)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun applyBackupRootWithoutSyncDirty(
        context: Context,
        root: JSONObject,
        preserveKeys: Set<String> = CLOUD_EXCLUDE_KEYS,
        updatedAt: Long,
    ): Boolean {
        val prefs = Prefs(context.applicationContext)
        var ok = false
        prefs.runWithoutSyncDirty {
            ok = applyBackupRoot(context, root, preserveKeys)
            if (ok) {
                prefs.syncPayloadUpdatedAt = updatedAt
                prefs.syncLastUploadAt = updatedAt
            }
        }
        return ok
    }

    // ---- Internal-storage backup (keyed by Google account ID) ----

    private fun internalBackupFile(context: Context, accountId: String): java.io.File {
        val safe = accountId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return java.io.File(context.filesDir, "sukun_backup_$safe.json")
    }

    fun hasInternalBackup(context: Context, accountId: String): Boolean =
        internalBackupFile(context, accountId).exists()

    fun exportToInternal(context: Context, accountId: String): Boolean {
        return try {
            val root = buildBackupRoot(context, excludeKeys = EXPORT_EXCLUDE_KEYS)
            internalBackupFile(context, accountId).writeText(root.toString(2), Charsets.UTF_8)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun importFromInternal(context: Context, accountId: String): Boolean {
        return try {
            val file = internalBackupFile(context, accountId)
            if (!file.exists()) return false
            applyBackupJson(context, file.readText(Charsets.UTF_8), preserveKeys = IMPORT_PRESERVE_KEYS)
        } catch (_: Exception) {
            false
        }
    }

    private fun collectPrefsData(context: Context, excludeKeys: Set<String>): JSONObject {
        val prefs = context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)
        val data = JSONObject()
        prefs.all.forEach { (key, value) ->
            if (key in excludeKeys) return@forEach
            putPrefValue(data, key, value)
        }
        return data
    }

    private fun wrapBackupRoot(context: Context, data: JSONObject, versionMs: Long): JSONObject {
        return JSONObject().apply {
            put(KEY_MAGIC, MAGIC)
            put(KEY_VERSION, FORMAT_VERSION)
            put(KEY_APP_VERSION, runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: "")
            put(KEY_CREATED_AT, versionMs)
            put(KEY_DATA, data)
        }
    }

    private fun readPreservedValues(
        prefs: SharedPreferences,
        preserveKeys: Set<String>,
    ): Map<String, Any?> {
        if (preserveKeys.isEmpty()) return emptyMap()
        return preserveKeys.mapNotNull { key ->
            when (val value = prefs.all[key]) {
                null -> null
                else -> key to value
            }
        }.toMap()
    }

    private fun restorePreservedValues(
        editor: SharedPreferences.Editor,
        preserved: Map<String, Any?>,
    ) {
        preserved.forEach { (key, value) ->
            putPrefValue(editor, key, value)
        }
    }

    private fun applyDataObject(
        editor: SharedPreferences.Editor,
        data: JSONObject,
        skipKeys: Set<String> = emptySet(),
    ) {
        val keys = data.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key in skipKeys) continue
            when (val value = data.get(key)) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> {
                    if (key in LONG_PREF_KEYS) editor.putLong(key, value.toLong())
                    else editor.putInt(key, value)
                }
                is Long -> editor.putLong(key, value)
                is Double -> editor.putFloat(key, value.toFloat())
                is String -> editor.putString(key, value)
                is JSONObject -> {
                    if (value.optString("_type") == "stringSet") {
                        val arr = value.optJSONArray("values") ?: JSONArray()
                        val set = (0 until arr.length()).map { arr.getString(it) }.toSet()
                        editor.putStringSet(key, set)
                    }
                }
            }
        }
    }

    private fun putPrefValue(target: JSONObject, key: String, value: Any?) {
        when (value) {
            is Boolean, is Int, is Long, is Float, is String -> target.put(key, value)
            is Set<*> -> {
                val arr = JSONArray()
                value.filterIsInstance<String>().forEach { arr.put(it) }
                target.put(key, JSONObject().apply {
                    put("_type", "stringSet")
                    put("values", arr)
                })
            }
            null -> {}
        }
    }

    private fun putPrefValue(editor: SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is String -> editor.putString(key, value)
            is Set<*> -> {
                val set = value.filterIsInstance<String>().toSet()
                editor.putStringSet(key, set)
            }
        }
    }

    private fun readText(context: Context, uri: Uri): String? {
        return context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
        }
    }
}
