package app.sukun.data

data class WeatherData(
    val temperatureText: String,
    val locationLabel: String,
    val updatedAt: Long,
) {
    val displayText: String
        get() = listOf(temperatureText, locationLabel)
            .filter { it.isNotBlank() }
            .joinToString("  ")
}
