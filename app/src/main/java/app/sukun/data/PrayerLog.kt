package app.sukun.data

data class PrayerLog(
    val prayerKey: String,
    val dateKey: String,  // "yyyy-MM-dd"
    val timestamp: Long,
)
