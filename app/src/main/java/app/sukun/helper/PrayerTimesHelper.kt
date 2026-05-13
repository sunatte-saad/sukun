package app.sukun.helper

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import app.sukun.R
import app.sukun.data.Constants
import app.sukun.data.Prefs
import app.sukun.data.PrayerState
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class PrayerLocationResult(
    val label: String,
    val latitude: String,
    val longitude: String,
)

suspend fun getCachedPrayerState(prefs: Prefs): PrayerState? = withContext(Dispatchers.IO) {
    if (prefs.prayerNextKey.isBlank() || prefs.prayerNextAt <= 0L || prefs.prayerDisplayTime.isBlank()) {
        return@withContext null
    }
    PrayerState(
        prayerKey = prefs.prayerNextKey,
        prayerTimeMillis = prefs.prayerNextAt,
        prayerTimeText = prefs.prayerDisplayTime,
        locationLabel = prefs.prayerLocationLabel,
        updatedAt = prefs.prayerLastUpdated
    )
}

suspend fun refreshPrayerState(
    context: Context,
    prefs: Prefs,
    forceLocationRefresh: Boolean = false,
): PrayerState? = withContext(Dispatchers.IO) {
    if (!prefs.showPrayerOnHome) {
        prefs.clearPrayerCache()
        return@withContext null
    }

    val location = resolvePrayerLocation(context, prefs, forceLocationRefresh) ?: return@withContext null
    val prayerState = calculatePrayerState(
        latitude = location.latitude.toDoubleOrNull() ?: return@withContext null,
        longitude = location.longitude.toDoubleOrNull() ?: return@withContext null,
        locationLabel = location.label
    )

    prefs.prayerLatitude = location.latitude
    prefs.prayerLongitude = location.longitude
    prefs.prayerLocationLabel = location.label
    prefs.prayerNextKey = prayerState.prayerKey
    prefs.prayerNextAt = prayerState.prayerTimeMillis
    prefs.prayerDisplayTime = prayerState.prayerTimeText
    prefs.prayerLastUpdated = prayerState.updatedAt
    prayerState
}

fun PrayerState.isExpired(now: Long = System.currentTimeMillis()): Boolean {
    return prayerTimeMillis <= now
}

fun PrayerState.getPrayerName(context: Context): String {
    return context.getString(prayerNameRes(prayerKey))
}

fun getPrayerName(context: Context, prayerKey: String): String {
    return context.getString(prayerNameRes(prayerKey))
}

fun PrayerState.toOverlayText(context: Context, now: Long = System.currentTimeMillis()): String {
    return context.getString(
        R.string.prayer_overlay_text,
        getPrayerName(context),
        prayerTimeText,
        formatPrayerRemainingTime(remainingMillis(now))
    )
}

fun formatPrayerRemainingTime(remainingMillis: Long): String {
    val totalSeconds = (remainingMillis / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0L -> String.format(Locale.getDefault(), "%dh %02dm left", hours, minutes)
        minutes > 0L -> String.format(Locale.getDefault(), "%dm left", minutes)
        else -> String.format(Locale.getDefault(), "%ds left", seconds)
    }
}

private suspend fun resolvePrayerLocation(
    context: Context,
    prefs: Prefs,
    forceLocationRefresh: Boolean,
): PrayerLocationResult? = withContext(Dispatchers.IO) {
    when (prefs.prayerSourceMode) {
        Constants.PrayerSource.DEVICE -> {
            resolveDevicePrayerLocation(context, prefs)
                ?: storedPrayerLocation(
                    label = prefs.prayerLocationLabel.ifBlank { context.getString(R.string.current_location) },
                    prefs = prefs,
                    ignoreCache = false
                )
        }

        else -> {
            storedPrayerLocation(
                label = prefs.prayerLocationLabel.ifBlank { prefs.prayerLocationQuery },
                prefs = prefs,
                ignoreCache = forceLocationRefresh
            ) ?: geocodePrayerLocation(context, prefs.prayerLocationQuery)?.also {
                prefs.prayerLocationLabel = it.label
                prefs.prayerLatitude = it.latitude
                prefs.prayerLongitude = it.longitude
            }
        }
    }
}

private fun storedPrayerLocation(
    label: String,
    prefs: Prefs,
    ignoreCache: Boolean,
): PrayerLocationResult? {
    if (ignoreCache || prefs.prayerLatitude.isBlank() || prefs.prayerLongitude.isBlank()) return null
    return PrayerLocationResult(
        label = label,
        latitude = prefs.prayerLatitude,
        longitude = prefs.prayerLongitude
    )
}

private suspend fun geocodePrayerLocation(
    context: Context,
    query: String,
): PrayerLocationResult? = withContext(Dispatchers.IO) {
    if (query.isBlank()) return@withContext null
    reverseLookupByCity(context, query) ?: geocodePrayerLocationFromApi(query)
}

private suspend fun reverseLookupByCity(
    context: Context,
    query: String,
): PrayerLocationResult? = withContext(Dispatchers.IO) {
    if (!Geocoder.isPresent()) return@withContext null
    try {
        val geocoder = Geocoder(context, Locale.getDefault())
        val address = geocoder.getFromLocationName(query, 1)?.firstOrNull() ?: return@withContext null
        PrayerLocationResult(
            label = listOf(
                address.locality,
                address.subAdminArea,
                address.adminArea,
                address.countryCode
            ).filter { it.isNotBlank() }.distinct().joinToString(", ").ifBlank { query },
            latitude = address.latitude.toString(),
            longitude = address.longitude.toString()
        )
    } catch (_: Exception) {
        null
    }
}

private suspend fun geocodePrayerLocationFromApi(query: String): PrayerLocationResult? =
    withContext(Dispatchers.IO) {
        val geocodeUrl = Uri.parse(Constants.URL_WEATHER_GEOCODING).buildUpon()
            .appendQueryParameter("name", query)
            .appendQueryParameter("count", "1")
            .appendQueryParameter("language", "en")
            .appendQueryParameter("format", "json")
            .toString()
        val json = getPrayerJsonObject(geocodeUrl) ?: return@withContext null
        val results: JSONArray = json.optJSONArray("results") ?: return@withContext null
        if (results.length() == 0) return@withContext null
        val firstResult = results.optJSONObject(0) ?: return@withContext null
        PrayerLocationResult(
            label = listOf(
                firstResult.optString("name"),
                firstResult.optString("admin1"),
                firstResult.optString("country_code")
            ).filter { it.isNotBlank() }.distinct().joinToString(", "),
            latitude = firstResult.optDouble("latitude").toString(),
            longitude = firstResult.optDouble("longitude").toString()
        )
    }

@SuppressLint("MissingPermission")
private suspend fun resolveDevicePrayerLocation(
    context: Context,
    prefs: Prefs,
): PrayerLocationResult? = withContext(Dispatchers.IO) {
    if (!context.hasWeatherLocationPermission()) return@withContext null
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return@withContext null

    val providers = buildList {
        add(LocationManager.PASSIVE_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        add(LocationManager.GPS_PROVIDER)
    }.filter {
        try {
            locationManager.isProviderEnabled(it)
        } catch (_: Exception) {
            false
        }
    }

    val bestLocation = providers.mapNotNull { provider ->
        try {
            locationManager.getLastKnownLocation(provider)
        } catch (_: Exception) {
            null
        }
    }.maxByOrNull(Location::getTime)

    if (bestLocation == null) {
        return@withContext storedPrayerLocation(
            label = prefs.prayerLocationLabel.ifBlank { context.getString(R.string.current_location) },
            prefs = prefs,
            ignoreCache = false
        )
    }

    val locationLabel = reverseGeocodePrayerLocation(
        context = context,
        latitude = bestLocation.latitude,
        longitude = bestLocation.longitude
    ) ?: context.getString(R.string.current_location)

    PrayerLocationResult(
        label = locationLabel,
        latitude = bestLocation.latitude.toString(),
        longitude = bestLocation.longitude.toString()
    )
}

private suspend fun reverseGeocodePrayerLocation(
    context: Context,
    latitude: Double,
    longitude: Double,
): String? = withContext(Dispatchers.IO) {
    if (!Geocoder.isPresent()) return@withContext null
    try {
        val geocoder = Geocoder(context, Locale.getDefault())
        val address = geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
            ?: return@withContext null
        listOf(
            address.locality,
            address.subAdminArea,
            address.adminArea,
            address.countryCode
        ).filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
            .ifBlank { null }
    } catch (_: Exception) {
        null
    }
}

private fun calculatePrayerState(
    latitude: Double,
    longitude: Double,
    locationLabel: String,
): PrayerState {
    val coordinates = Coordinates(latitude, longitude)
    val now = Date()
    val nextPrayer = findNextPrayer(coordinates, now)
    val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    return PrayerState(
        prayerKey = nextPrayer.first,
        prayerTimeMillis = nextPrayer.second.time,
        prayerTimeText = formatter.format(nextPrayer.second),
        locationLabel = locationLabel,
        updatedAt = System.currentTimeMillis()
    )
}

private fun findNextPrayer(coordinates: Coordinates, now: Date): Pair<String, Date> {
    val todayTimes = buildPrayerTimes(coordinates, now)
    val prayers = listOf(
        Constants.Prayer.FAJR to todayTimes.fajr,
        Constants.Prayer.DHUHR to todayTimes.dhuhr,
        Constants.Prayer.ASR to todayTimes.asr,
        Constants.Prayer.MAGHRIB to todayTimes.maghrib,
        Constants.Prayer.ISHA to todayTimes.isha
    )
    return prayers.firstOrNull { it.second.after(now) } ?: run {
        val tomorrow = Date(now.time + Constants.ONE_DAY_IN_MILLIS)
        val tomorrowPrayerTimes = buildPrayerTimes(coordinates, tomorrow)
        Constants.Prayer.FAJR to tomorrowPrayerTimes.fajr
    }
}

private fun buildPrayerTimes(coordinates: Coordinates, date: Date): PrayerTimes {
    val params = when (Constants.PrayerCalc.METHOD) {
        else -> CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters
    }
    params.madhab = when (Constants.PrayerCalc.MADHAB) {
        "hanafi" -> Madhab.HANAFI
        else -> Madhab.SHAFI
    }
    return PrayerTimes(coordinates, DateComponents.from(date), params)
}

private fun prayerNameRes(prayerKey: String): Int {
    return when (prayerKey) {
        Constants.Prayer.FAJR -> R.string.prayer_fajr
        Constants.Prayer.DHUHR -> R.string.prayer_dhuhr
        Constants.Prayer.ASR -> R.string.prayer_asr
        Constants.Prayer.MAGHRIB -> R.string.prayer_maghrib
        Constants.Prayer.ISHA -> R.string.prayer_isha
        else -> R.string.prayer_next
    }
}

private suspend fun getPrayerJsonObject(urlString: String): JSONObject? = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null
    try {
        val url = URL(urlString)
        connection = url.openConnection() as HttpURLConnection
        connection.doInput = true
        connection.connect()
        connection.inputStream.bufferedReader().use { reader ->
            JSONObject(reader.readText())
        }
    } catch (_: Exception) {
        null
    } finally {
        connection?.disconnect()
    }
}
