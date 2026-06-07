package sukun.minimalist.app.launcher.com.helper

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

object BackupHelper {

    private const val PREFS_FILENAME = "sukun.minimalist.app.launcher.com"
    private const val FORMAT_VERSION = 1
    private const val MAGIC = "sukun-backup"

    private const val KEY_MAGIC = "_magic"
    private const val KEY_VERSION = "_version"
    private const val KEY_APP_VERSION = "_appVersion"
    private const val KEY_CREATED_AT = "_createdAt"
    private const val KEY_DATA = "data"

    fun exportToUri(context: Context, uri: Uri): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)
            val data = JSONObject()
            prefs.all.forEach { (key, value) ->
                when (value) {
                    is Boolean, is Int, is Long, is Float, is String -> data.put(key, value)
                    is Set<*> -> {
                        val arr = JSONArray()
                        value.filterIsInstance<String>().forEach { arr.put(it) }
                        data.put(key, JSONObject().apply {
                            put("_type", "stringSet")
                            put("values", arr)
                        })
                    }
                    null -> {}
                }
            }
            val root = JSONObject().apply {
                put(KEY_MAGIC, MAGIC)
                put(KEY_VERSION, FORMAT_VERSION)
                put(KEY_APP_VERSION, runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull() ?: "")
                put(KEY_CREATED_AT, System.currentTimeMillis())
                put(KEY_DATA, data)
            }
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
            val root = JSONObject(text)
            root.optString(KEY_MAGIC) == MAGIC && root.has(KEY_DATA)
        } catch (_: Exception) {
            false
        }
    }

    fun importFromUri(context: Context, uri: Uri): Boolean {
        return try {
            val text = readText(context, uri) ?: return false
            val root = JSONObject(text)
            if (root.optString(KEY_MAGIC) != MAGIC) return false
            val data = root.optJSONObject(KEY_DATA) ?: return false

            val prefs = context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)
            prefs.edit(commit = true) {
                clear()
                val keys = data.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    when (val v = data.get(key)) {
                        is Boolean -> putBoolean(key, v)
                        is Int -> putInt(key, v)
                        is Long -> putLong(key, v)
                        is Double -> putFloat(key, v.toFloat())
                        is String -> putString(key, v)
                        is JSONObject -> {
                            if (v.optString("_type") == "stringSet") {
                                val arr = v.optJSONArray("values") ?: JSONArray()
                                val set = (0 until arr.length()).map { arr.getString(it) }.toSet()
                                putStringSet(key, set)
                            }
                        }
                    }
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun readText(context: Context, uri: Uri): String? {
        return context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
        }
    }
}
