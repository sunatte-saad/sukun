package sukun.minimalist.app.launcher.com.data

data class PrayerState(
    val prayerKey: String,
    val prayerTimeMillis: Long,
    val prayerTimeText: String,
    val locationLabel: String,
    val updatedAt: Long,
) {
    fun remainingMillis(now: Long = System.currentTimeMillis()): Long {
        return (prayerTimeMillis - now).coerceAtLeast(0L)
    }
}
