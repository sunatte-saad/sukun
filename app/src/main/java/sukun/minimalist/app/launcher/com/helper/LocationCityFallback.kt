package sukun.minimalist.app.launcher.com.helper

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Geocoder
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sukun.minimalist.app.launcher.com.MainViewModel
import sukun.minimalist.app.launcher.com.R
import sukun.minimalist.app.launcher.com.data.Constants
import sukun.minimalist.app.launcher.com.data.Prefs
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.TimeZone

internal data class GuessedCity(
    val name: String,
    val latitude: String = "",
    val longitude: String = "",
    val label: String = name,
) {
    val hasCoords: Boolean
        get() = latitude.isNotBlank() && longitude.isNotBlank()
}

private var cityPromptJob: Job? = null
private var cityPromptDialog: AlertDialog? = null

fun Context.needsManualCityPrompt(prefs: Prefs): Boolean {
    if (!prefs.privacyAndSetupComplete) return false
    if (hasWeatherLocationPermission()) return false
    if (prefs.hasUsableStoredLocation()) return false
    return prefs.showWeatherOnHome || prefs.showPrayerOnHome
}

/**
 * When location permission is missing and no city/coords are stored, guess a city
 * and require the user to confirm or type one. Returns true if the prompt is showing
 * or will show, so callers can skip Google fallbacks and avoid cancelling azan.
 */
fun Context.promptForManualCityIfNeeded(
    prefs: Prefs,
    viewModel: MainViewModel,
    scope: CoroutineScope? = null,
    onConfirmed: (() -> Unit)? = null,
): Boolean {
    if (!needsManualCityPrompt(prefs)) return false
    val activity = findFragmentActivity() ?: return false
    if (activity.isFinishing || activity.isDestroyed) return false
    if (cityPromptDialog?.isShowing == true) return true
    if (cityPromptJob?.isActive == true) return true

    val dialogScope = scope ?: activity.lifecycleScope
    cityPromptJob = dialogScope.launch {
        val guess = guessCityForManualLocation(activity)
        if (activity.isFinishing || activity.isDestroyed) return@launch
        if (!needsManualCityPrompt(prefs)) return@launch
        showCityConfirmDialog(activity, prefs, viewModel, guess, onConfirmed)
    }
    return true
}

fun Context.refreshAfterManualLocation(viewModel: MainViewModel) {
    viewModel.setWeatherWorker()
    viewModel.loadWeather(forceRefresh = true)
    if (Prefs(this).showPrayerOnHome) {
        viewModel.refreshPrayerData()
    }
    viewModel.refreshHome(false)
}

internal suspend fun guessCityForManualLocation(context: Context): GuessedCity {
    if (context.isNetworkAvailable()) {
        fetchCityFromIp()?.let { return it }
    }
    return guessCityFromTimezoneAndLocale()
}

private suspend fun fetchCityFromIp(): GuessedCity? {
    parseIpWho(fetchIpJson(Constants.URL_IP_GEOLOCATION))?.let { return it }
    return parseIpApiCo(fetchIpJson(Constants.URL_IP_GEOLOCATION_FALLBACK))
}

private suspend fun fetchIpJson(urlString: String): JSONObject? = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null
    try {
        val url = URL(urlString)
        connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = Constants.IP_GEOLOCATION_TIMEOUT_MS
        connection.readTimeout = Constants.IP_GEOLOCATION_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.doInput = true
        connection.setRequestProperty("User-Agent", "SukunLauncher")
        connection.setRequestProperty("Accept", "application/json")
        connection.connect()
        if (connection.responseCode !in 200..299) return@withContext null
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        JSONObject(body)
    } catch (_: Exception) {
        null
    } finally {
        connection?.disconnect()
    }
}

private fun parseIpWho(json: JSONObject?): GuessedCity? {
    json ?: return null
    if (json.optBoolean("success", true).not()) return null
    val city = json.optString("city").trim()
    if (city.isBlank()) return null
    val lat = json.optDouble("latitude", Double.NaN)
    val lng = json.optDouble("longitude", Double.NaN)
    return GuessedCity(
        name = city,
        latitude = lat.takeUnless { it.isNaN() }?.toString().orEmpty(),
        longitude = lng.takeUnless { it.isNaN() }?.toString().orEmpty(),
        label = listOf(city, json.optString("country").trim())
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
            .ifBlank { city },
    )
}

private fun parseIpApiCo(json: JSONObject?): GuessedCity? {
    json ?: return null
    if (json.has("error") && json.optBoolean("error")) return null
    val city = json.optString("city").trim()
    if (city.isBlank()) return null
    val lat = json.optDouble("latitude", Double.NaN)
    val lng = json.optDouble("longitude", Double.NaN)
    return GuessedCity(
        name = city,
        latitude = lat.takeUnless { it.isNaN() }?.toString().orEmpty(),
        longitude = lng.takeUnless { it.isNaN() }?.toString().orEmpty(),
        label = listOf(city, json.optString("country_name").trim().ifBlank { json.optString("country") })
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
            .ifBlank { city },
    )
}

internal fun guessCityFromTimezoneAndLocale(): GuessedCity {
    val zoneId = TimeZone.getDefault().id
    TIMEZONE_CITIES[zoneId]?.let { return it }

    val fromId = cityNameFromTimeZoneId(zoneId)
    if (!fromId.isNullOrBlank() && !isGenericTimeZoneName(fromId)) {
        return GuessedCity(name = fromId)
    }

    val country = Locale.getDefault().country.uppercase(Locale.US)
    COUNTRY_CAPITALS[country]?.let { return it }

    val displayName = TimeZone.getDefault().getDisplayName(Locale.getDefault()).trim()
    return GuessedCity(
        name = displayName.takeIf { it.isNotBlank() && !isGenericTimeZoneName(it) } ?: fromId.orEmpty()
    )
}

private fun cityNameFromTimeZoneId(zoneId: String): String? {
    if (zoneId.isBlank()) return null
    if (zoneId.startsWith("Etc/", ignoreCase = true)) return null
    if (zoneId.startsWith("SystemV/", ignoreCase = true)) return null
    val last = zoneId.substringAfterLast('/').replace('_', ' ').trim()
    return last.takeIf { it.isNotBlank() && it != zoneId }
}

private fun isGenericTimeZoneName(name: String): Boolean {
    val n = name.trim()
    if (n.isBlank()) return true
    val upper = n.uppercase(Locale.US)
    return upper.startsWith("GMT") ||
            upper.startsWith("UTC") ||
            upper.startsWith("UCT") ||
            upper in GENERIC_ZONE_NAMES
}

private fun showCityConfirmDialog(
    activity: FragmentActivity,
    prefs: Prefs,
    viewModel: MainViewModel,
    guess: GuessedCity,
    onConfirmed: (() -> Unit)?,
) {
    if (cityPromptDialog?.isShowing == true) return

    val dialogView = activity.layoutInflater.inflate(R.layout.dialog_location_city_confirm, null)
    val messageView = dialogView.findViewById<TextView>(R.id.locationCityConfirmMessage)
    val input = dialogView.findViewById<EditText>(R.id.locationCityConfirmInput)
    val confirm = dialogView.findViewById<TextView>(R.id.locationCityConfirmButton)

    fun cityText(): String = input.text?.toString()?.trim().orEmpty()

    fun bindMessage(city: String) {
        val shown = city.ifBlank { "—" }
        messageView.text = activity.getString(R.string.location_city_confirm_message, shown)
    }

    fun updateConfirmEnabled() {
        val enabled = cityText().isNotBlank()
        confirm.isEnabled = enabled
        confirm.alpha = if (enabled) 1f else 0.4f
    }

    if (guess.name.isNotBlank()) {
        input.setText(guess.name)
        input.setSelection(guess.name.length)
    }
    bindMessage(cityText())
    updateConfirmEnabled()
    input.doAfterTextChanged {
        bindMessage(cityText())
        updateConfirmEnabled()
    }

    val dialog = AlertDialog.Builder(activity)
        .setView(dialogView)
        .setCancelable(false)
        .create()
    dialog.setCanceledOnTouchOutside(false)
    dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    dialog.setOnDismissListener {
        if (cityPromptDialog === dialog) cityPromptDialog = null
    }

    var saving = false
    fun submit() {
        val city = cityText()
        if (city.isBlank() || saving) return
        saving = true
        confirm.isEnabled = false
        confirm.alpha = 0.4f
        input.hideKeyboard()
        activity.lifecycleScope.launch {
            val resolved = resolveConfirmedCity(activity, city, guess)
            if (activity.isFinishing || activity.isDestroyed) return@launch
            prefs.applySharedManualLocation(
                query = city,
                label = resolved.label.ifBlank { city },
                latitude = resolved.latitude,
                longitude = resolved.longitude,
            )
            cityPromptDialog = null
            dialog.dismiss()
            activity.refreshAfterManualLocation(viewModel)
            onConfirmed?.invoke()
        }
    }

    confirm.setOnClickListener { submit() }
    input.setOnEditorActionListener { _, actionId, _ ->
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            submit()
            true
        } else {
            false
        }
    }

    cityPromptDialog = dialog
    dialog.show()
}

private suspend fun resolveConfirmedCity(
    context: Context,
    city: String,
    guess: GuessedCity,
): GuessedCity = withContext(Dispatchers.IO) {
    val sameAsGuess = city.equals(guess.name, ignoreCase = true)
    if (sameAsGuess && guess.hasCoords) return@withContext guess

    geocodeWeatherLocation(city)?.let { result ->
        return@withContext GuessedCity(
            name = city,
            latitude = result.latitude,
            longitude = result.longitude,
            label = result.label.ifBlank { city },
        )
    }

    geocodeWithAndroidGeocoder(context, city)?.let { return@withContext it }

    if (sameAsGuess) guess else GuessedCity(name = city, label = city)
}

private fun geocodeWithAndroidGeocoder(context: Context, query: String): GuessedCity? {
    if (query.isBlank() || !Geocoder.isPresent()) return null
    return try {
        val address = Geocoder(context, Locale.getDefault())
            .getFromLocationName(query, 1)
            ?.firstOrNull()
            ?: return null
        val label = listOf(
            address.locality,
            address.subAdminArea,
            address.adminArea,
            address.countryCode,
        ).filter { !it.isNullOrBlank() }.distinct().joinToString(", ").ifBlank { query }
        GuessedCity(
            name = query,
            latitude = address.latitude.toString(),
            longitude = address.longitude.toString(),
            label = label,
        )
    } catch (_: Exception) {
        null
    }
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private val GENERIC_ZONE_NAMES = setOf(
    "EST", "EDT", "CST", "CDT", "MST", "MDT", "PST", "PDT",
    "WET", "CET", "EET", "MET", "WEST", "CEST", "EEST",
)

private val TIMEZONE_CITIES = mapOf(
    "Asia/Karachi" to GuessedCity("Karachi", "24.8607", "67.0011"),
    "Asia/Kolkata" to GuessedCity("Kolkata", "22.5726", "88.3639"),
    "Asia/Calcutta" to GuessedCity("Kolkata", "22.5726", "88.3639"),
    "Asia/Dhaka" to GuessedCity("Dhaka", "23.8103", "90.4125"),
    "Asia/Jakarta" to GuessedCity("Jakarta", "-6.2088", "106.8456"),
    "Asia/Makassar" to GuessedCity("Makassar", "-5.1477", "119.4327"),
    "Asia/Jayapura" to GuessedCity("Jayapura", "-2.5916", "140.6690"),
    "Asia/Pontianak" to GuessedCity("Pontianak", "-0.0263", "109.3425"),
    "Asia/Singapore" to GuessedCity("Singapore", "1.3521", "103.8198"),
    "Asia/Kuala_Lumpur" to GuessedCity("Kuala Lumpur", "3.1390", "101.6869"),
    "Asia/Manila" to GuessedCity("Manila", "14.5995", "120.9842"),
    "Asia/Bangkok" to GuessedCity("Bangkok", "13.7563", "100.5018"),
    "Asia/Ho_Chi_Minh" to GuessedCity("Ho Chi Minh City", "10.8231", "106.6297"),
    "Asia/Saigon" to GuessedCity("Ho Chi Minh City", "10.8231", "106.6297"),
    "Asia/Shanghai" to GuessedCity("Shanghai", "31.2304", "121.4737"),
    "Asia/Hong_Kong" to GuessedCity("Hong Kong", "22.3193", "114.1694"),
    "Asia/Taipei" to GuessedCity("Taipei", "25.0330", "121.5654"),
    "Asia/Tokyo" to GuessedCity("Tokyo", "35.6762", "139.6503"),
    "Asia/Seoul" to GuessedCity("Seoul", "37.5665", "126.9780"),
    "Asia/Dubai" to GuessedCity("Dubai", "25.2048", "55.2708"),
    "Asia/Riyadh" to GuessedCity("Riyadh", "24.7136", "46.6753"),
    "Asia/Mecca" to GuessedCity("Mecca", "21.3891", "39.8579"),
    "Asia/Qatar" to GuessedCity("Doha", "25.2854", "51.5310"),
    "Asia/Kuwait" to GuessedCity("Kuwait City", "29.3759", "47.9774"),
    "Asia/Bahrain" to GuessedCity("Manama", "26.2235", "50.5876"),
    "Asia/Muscat" to GuessedCity("Muscat", "23.5880", "58.3829"),
    "Asia/Baghdad" to GuessedCity("Baghdad", "33.3152", "44.3661"),
    "Asia/Tehran" to GuessedCity("Tehran", "35.6892", "51.3890"),
    "Asia/Kabul" to GuessedCity("Kabul", "34.5553", "69.2075"),
    "Asia/Tashkent" to GuessedCity("Tashkent", "41.2995", "69.2401"),
    "Asia/Almaty" to GuessedCity("Almaty", "43.2220", "76.8512"),
    "Asia/Colombo" to GuessedCity("Colombo", "6.9271", "79.8612"),
    "Asia/Kathmandu" to GuessedCity("Kathmandu", "27.7172", "85.3240"),
    "Asia/Yangon" to GuessedCity("Yangon", "16.8409", "96.1735"),
    "Asia/Rangoon" to GuessedCity("Yangon", "16.8409", "96.1735"),
    "Asia/Jerusalem" to GuessedCity("Jerusalem", "31.7683", "35.2137"),
    "Asia/Beirut" to GuessedCity("Beirut", "33.8938", "35.5018"),
    "Asia/Damascus" to GuessedCity("Damascus", "33.5138", "36.2765"),
    "Asia/Amman" to GuessedCity("Amman", "31.9454", "35.9284"),
    "Asia/Istanbul" to GuessedCity("Istanbul", "41.0082", "28.9784"),
    "Europe/Istanbul" to GuessedCity("Istanbul", "41.0082", "28.9784"),
    "Europe/London" to GuessedCity("London", "51.5074", "-0.1278"),
    "Europe/Paris" to GuessedCity("Paris", "48.8566", "2.3522"),
    "Europe/Berlin" to GuessedCity("Berlin", "52.5200", "13.4050"),
    "Europe/Madrid" to GuessedCity("Madrid", "40.4168", "-3.7038"),
    "Europe/Rome" to GuessedCity("Rome", "41.9028", "12.4964"),
    "Europe/Amsterdam" to GuessedCity("Amsterdam", "52.3676", "4.9041"),
    "Europe/Moscow" to GuessedCity("Moscow", "55.7558", "37.6173"),
    "Europe/Athens" to GuessedCity("Athens", "37.9838", "23.7275"),
    "Africa/Cairo" to GuessedCity("Cairo", "30.0444", "31.2357"),
    "Africa/Casablanca" to GuessedCity("Casablanca", "33.5731", "-7.5898"),
    "Africa/Tunis" to GuessedCity("Tunis", "36.8065", "10.1815"),
    "Africa/Algiers" to GuessedCity("Algiers", "36.7538", "3.0588"),
    "Africa/Lagos" to GuessedCity("Lagos", "6.5244", "3.3792"),
    "Africa/Johannesburg" to GuessedCity("Johannesburg", "-26.2041", "28.0473"),
    "Africa/Nairobi" to GuessedCity("Nairobi", "-1.2921", "36.8219"),
    "Africa/Khartoum" to GuessedCity("Khartoum", "15.5007", "32.5599"),
    "America/New_York" to GuessedCity("New York", "40.7128", "-74.0060"),
    "America/Chicago" to GuessedCity("Chicago", "41.8781", "-87.6298"),
    "America/Denver" to GuessedCity("Denver", "39.7392", "-104.9903"),
    "America/Los_Angeles" to GuessedCity("Los Angeles", "34.0522", "-118.2437"),
    "America/Toronto" to GuessedCity("Toronto", "43.6532", "-79.3832"),
    "America/Vancouver" to GuessedCity("Vancouver", "49.2827", "-123.1207"),
    "America/Mexico_City" to GuessedCity("Mexico City", "19.4326", "-99.1332"),
    "America/Sao_Paulo" to GuessedCity("São Paulo", "-23.5558", "-46.6396"),
    "America/Buenos_Aires" to GuessedCity("Buenos Aires", "-34.6037", "-58.3816"),
    "America/Argentina/Buenos_Aires" to GuessedCity("Buenos Aires", "-34.6037", "-58.3816"),
    "Australia/Sydney" to GuessedCity("Sydney", "-33.8688", "151.2093"),
    "Australia/Melbourne" to GuessedCity("Melbourne", "-37.8136", "144.9631"),
    "Australia/Perth" to GuessedCity("Perth", "-31.9505", "115.8605"),
    "Australia/Brisbane" to GuessedCity("Brisbane", "-27.4705", "153.0260"),
    "Pacific/Auckland" to GuessedCity("Auckland", "-36.8509", "174.7645"),
)

private val COUNTRY_CAPITALS = mapOf(
    "PK" to GuessedCity("Islamabad", "33.6844", "73.0479"),
    "IN" to GuessedCity("New Delhi", "28.6139", "77.2090"),
    "BD" to GuessedCity("Dhaka", "23.8103", "90.4125"),
    "ID" to GuessedCity("Jakarta", "-6.2088", "106.8456"),
    "SA" to GuessedCity("Riyadh", "24.7136", "46.6753"),
    "AE" to GuessedCity("Abu Dhabi", "24.4539", "54.3773"),
    "EG" to GuessedCity("Cairo", "30.0444", "31.2357"),
    "TR" to GuessedCity("Ankara", "39.9334", "32.8597"),
    "MY" to GuessedCity("Kuala Lumpur", "3.1390", "101.6869"),
    "NG" to GuessedCity("Abuja", "9.0765", "7.3986"),
    "US" to GuessedCity("Washington", "38.9072", "-77.0369"),
    "GB" to GuessedCity("London", "51.5074", "-0.1278"),
    "CA" to GuessedCity("Ottawa", "45.4215", "-75.6972"),
    "AU" to GuessedCity("Canberra", "-35.2809", "149.1300"),
    "FR" to GuessedCity("Paris", "48.8566", "2.3522"),
    "DE" to GuessedCity("Berlin", "52.5200", "13.4050"),
    "IR" to GuessedCity("Tehran", "35.6892", "51.3890"),
    "IQ" to GuessedCity("Baghdad", "33.3152", "44.3661"),
    "JO" to GuessedCity("Amman", "31.9454", "35.9284"),
    "MA" to GuessedCity("Rabat", "34.0209", "-6.8416"),
    "DZ" to GuessedCity("Algiers", "36.7538", "3.0588"),
    "TN" to GuessedCity("Tunis", "36.8065", "10.1815"),
    "QA" to GuessedCity("Doha", "25.2854", "51.5310"),
    "KW" to GuessedCity("Kuwait City", "29.3759", "47.9774"),
    "OM" to GuessedCity("Muscat", "23.5880", "58.3829"),
    "BH" to GuessedCity("Manama", "26.2235", "50.5876"),
    "SG" to GuessedCity("Singapore", "1.3521", "103.8198"),
    "PH" to GuessedCity("Manila", "14.5995", "120.9842"),
    "TH" to GuessedCity("Bangkok", "13.7563", "100.5018"),
    "VN" to GuessedCity("Hanoi", "21.0278", "105.8342"),
    "CN" to GuessedCity("Beijing", "39.9042", "116.4074"),
    "JP" to GuessedCity("Tokyo", "35.6762", "139.6503"),
    "KR" to GuessedCity("Seoul", "37.5665", "126.9780"),
    "ZA" to GuessedCity("Pretoria", "-25.7479", "28.2293"),
    "KE" to GuessedCity("Nairobi", "-1.2921", "36.8219"),
    "BR" to GuessedCity("Brasília", "-15.7975", "-47.8919"),
    "MX" to GuessedCity("Mexico City", "19.4326", "-99.1332"),
    "AR" to GuessedCity("Buenos Aires", "-34.6037", "-58.3816"),
    "NZ" to GuessedCity("Wellington", "-41.2866", "174.7756"),
    "NL" to GuessedCity("Amsterdam", "52.3676", "4.9041"),
    "ES" to GuessedCity("Madrid", "40.4168", "-3.7038"),
    "IT" to GuessedCity("Rome", "41.9028", "12.4964"),
    "RU" to GuessedCity("Moscow", "55.7558", "37.6173"),
    "UA" to GuessedCity("Kyiv", "50.4501", "30.5234"),
    "PL" to GuessedCity("Warsaw", "52.2297", "21.0122"),
    "SE" to GuessedCity("Stockholm", "59.3293", "18.0686"),
    "NO" to GuessedCity("Oslo", "59.9139", "10.7522"),
    "DK" to GuessedCity("Copenhagen", "55.6761", "12.5683"),
    "IE" to GuessedCity("Dublin", "53.3498", "-6.2603"),
    "PT" to GuessedCity("Lisbon", "38.7223", "-9.1393"),
    "GR" to GuessedCity("Athens", "37.9838", "23.7275"),
    "AF" to GuessedCity("Kabul", "34.5553", "69.2075"),
    "LK" to GuessedCity("Sri Jayawardenepura Kotte", "6.8941", "79.9026"),
    "NP" to GuessedCity("Kathmandu", "27.7172", "85.3240"),
    "MM" to GuessedCity("Naypyidaw", "19.7633", "96.0785"),
    "SD" to GuessedCity("Khartoum", "15.5007", "32.5599"),
    "LY" to GuessedCity("Tripoli", "32.8872", "13.1913"),
    "YE" to GuessedCity("Sanaa", "15.3694", "44.1910"),
    "PS" to GuessedCity("Jerusalem", "31.7683", "35.2137"),
    "LB" to GuessedCity("Beirut", "33.8938", "35.5018"),
    "SY" to GuessedCity("Damascus", "33.5138", "36.2765"),
)
