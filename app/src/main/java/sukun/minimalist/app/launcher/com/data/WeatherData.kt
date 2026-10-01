package sukun.minimalist.app.launcher.com.data

data class WeatherData(
    val temperatureText: String,
    val locationLabel: String,
    val updatedAt: Long,
    val conditionText: String = "",
    val precipitationText: String = "",
) {
    val displayText: String
        get() = listOf(temperatureText, conditionText, precipitationText, locationLabel)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
}
