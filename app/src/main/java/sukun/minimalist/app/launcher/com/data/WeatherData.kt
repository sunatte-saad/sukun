package sukun.minimalist.app.launcher.com.data

data class WeatherData(
    val temperatureText: String,
    val locationLabel: String,
    val updatedAt: Long,
    val conditionText: String = "",
    val precipitationText: String = "",
) {
    val displayText: String
        get() {
            val location = locationLabel.ifBlank { "Weather" }
            val details = listOf(temperatureText, conditionText, precipitationText)
                .filter { it.isNotBlank() }
                .joinToString("  ")
            return listOf(details, location)
                .filter { it.isNotBlank() }
                .joinToString("  ")
        }
}
