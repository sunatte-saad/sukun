package app.sukun.data

data class WeatherData(
    val temperatureText: String,
    val locationLabel: String,
    val updatedAt: Long,
    val conditionText: String = "",
    val precipitationText: String = "",
) {
    val displayText: String
        get() {
            val parts = listOf(temperatureText, conditionText)
                .filter { it.isNotBlank() }
                .joinToString("  ")
            return parts.ifBlank { "Weather" }
        }
}
