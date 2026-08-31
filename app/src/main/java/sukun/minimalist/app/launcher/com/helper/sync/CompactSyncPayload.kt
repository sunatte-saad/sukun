package sukun.minimalist.app.launcher.com.helper.sync

import org.json.JSONObject
import sukun.minimalist.app.launcher.com.data.Prefs

data class CompactSyncPayload(
    val version: Int = VERSION,
    val updatedAt: Long = System.currentTimeMillis(),
    val trialStart: Long = 0L,
    val settings: JSONObject = JSONObject(),
    val prayer: PrayerRollup? = null,
    val screenTime: ScreenTimeRollup? = null,
) {
    fun toJsonString(): String = toJson().toString()

    fun toJson(): JSONObject = JSONObject().apply {
        put("v", version)
        put("t", updatedAt)
        if (trialStart > 0L) put("ts", trialStart)
        put("s", settings)
        prayer?.let { put("p", it.toJson()) }
        screenTime?.let { put("st", it.toJson()) }
    }

    /** Compare backup content ignoring the upload timestamp. */
    fun contentEquals(other: CompactSyncPayload): Boolean =
        syncFingerprint() == other.syncFingerprint()

    private fun syncFingerprint(): String = JSONObject().apply {
        if (trialStart > 0L) put("ts", trialStart)
        put("s", settings)
        prayer?.let { put("p", it.toJson()) }
        screenTime?.let { put("st", it.toJson()) }
    }.toString()

    companion object {
        const val VERSION = 2

        fun fromJsonString(raw: String): CompactSyncPayload? {
            return try {
                fromJson(JSONObject(raw))
            } catch (_: Exception) {
                null
            }
        }

        fun fromJson(obj: JSONObject): CompactSyncPayload {
            return CompactSyncPayload(
                version = obj.optInt("v", VERSION),
                updatedAt = obj.optLong("t", 0L),
                trialStart = obj.optLong("ts", 0L),
                settings = obj.optJSONObject("s") ?: JSONObject(),
                prayer = PrayerRollup.fromJson(obj.optJSONObject("p")),
                screenTime = ScreenTimeRollup.fromJson(obj.optJSONObject("st")),
            )
        }

        fun build(prefs: Prefs, prayer: PrayerRollup, screenTime: ScreenTimeRollup?): CompactSyncPayload {
            val version = prefs.syncPayloadUpdatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
            return CompactSyncPayload(
                updatedAt = version,
                trialStart = prefs.accountTrialStart,
                settings = SyncSettingsCodec.encode(prefs),
                prayer = prayer,
                screenTime = if (prefs.showScreenTimeOnHome) screenTime else null,
            )
        }
    }
}
