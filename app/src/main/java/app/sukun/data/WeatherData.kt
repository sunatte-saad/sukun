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
            val title = locationLabel.ifBlank { "Weather" }
            val detail = listOf(temperatureText, conditionText, precipitationText)
                .filter { it.isNotBlank() }
                .joinToString("  ")
            return listOf(title, detail)
                .filter { it.isNotBlank() }
                .joinToString("\n")
        }
}
