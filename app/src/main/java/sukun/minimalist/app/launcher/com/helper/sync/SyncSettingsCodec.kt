package sukun.minimalist.app.launcher.com.helper.sync

import org.json.JSONArray
import org.json.JSONObject
import sukun.minimalist.app.launcher.com.data.Prefs

/** Maps launcher settings to short keys for a compact cloud backup payload. */
object SyncSettingsCodec {

    private data class BoolKey(
        val short: String,
        val read: (Prefs) -> Boolean,
        val write: (Prefs, Boolean) -> Unit,
    )

    private data class IntKey(
        val short: String,
        val read: (Prefs) -> Int,
        val write: (Prefs, Int) -> Unit,
    )

    private data class FloatKey(
        val short: String,
        val read: (Prefs) -> Float,
        val write: (Prefs, Float) -> Unit,
    )

    private data class StringKey(
        val short: String,
        val read: (Prefs) -> String,
        val write: (Prefs, String) -> Unit,
    )

    private val boolKeys = listOf(
        BoolKey("lm", { it.lockModeOn }, { p, v -> p.lockModeOn = v }),
        BoolKey("hi", { it.showHomeAppIcons }, { p, v -> p.showHomeAppIcons = v }),
        BoolKey("sb", { it.showStatusBar }, { p, v -> p.showStatusBar = v }),
        BoolKey("hba", { it.homeBottomAlignment }, { p, v -> p.homeBottomAlignment = v }),
        BoolKey("sle", { it.swipeLeftEnabled }, { p, v -> p.swipeLeftEnabled = v }),
        BoolKey("sre", { it.swipeRightEnabled }, { p, v -> p.swipeRightEnabled = v }),
        BoolKey("atd", { it.ambientThemeDark }, { p, v -> p.ambientThemeDark = v }),
        BoolKey("afs", { it.appDrawerFastScroller }, { p, v -> p.appDrawerFastScroller = v }),
        BoolKey("hbr", { it.homeButtonShowRecents }, { p, v -> p.homeButtonShowRecents = v }),
        BoolKey("st", { it.showScreenTimeOnHome }, { p, v -> p.showScreenTimeOnHome = v }),
        BoolKey("pr", { it.showPrayerOnHome }, { p, v -> p.showPrayerOnHome = v }),
        BoolKey("wx", { it.showWeatherOnHome }, { p, v -> p.showWeatherOnHome = v }),
        BoolKey("dn", { it.showDailyNotesOnHome }, { p, v -> p.showDailyNotesOnHome = v }),
        BoolKey("td", { it.showTodoOnHome }, { p, v -> p.showTodoOnHome = v }),
        BoolKey("rm", { it.showRemindersOnHome }, { p, v -> p.showRemindersOnHome = v }),
        BoolKey("az", { it.azanEnabled }, { p, v -> p.azanEnabled = v }),
        BoolKey("hc", { it.hourlyChimeEnabled }, { p, v -> p.hourlyChimeEnabled = v }),
        BoolKey("mm", { it.mindfulMorningEnabled }, { p, v -> p.mindfulMorningEnabled = v }),
        BoolKey("dw", { it.dailyWallpaper }, { p, v -> p.dailyWallpaper = v }),
        BoolKey("ask", { it.autoShowKeyboard }, { p, v -> p.autoShowKeyboard = v }),
        BoolKey("mmh", { it.mindfulMorningHard }, { p, v -> p.mindfulMorningHard = v }),
    )

    private val intKeys = listOf(
        IntKey("han", { it.homeAppsNum }, { p, v -> p.homeAppsNum = v }),
        IntKey("ha", { it.homeAlignment }, { p, v -> p.homeAlignment = v }),
        IntKey("ala", { it.appLabelAlignment }, { p, v -> p.appLabelAlignment = v }),
        IntKey("dtv", { it.dateTimeVisibility }, { p, v -> p.dateTimeVisibility = v }),
        IntKey("dsh", { it.dayStartHour }, { p, v -> p.dayStartHour = v }),
        IntKey("deh", { it.dayEndHour }, { p, v -> p.dayEndHour = v }),
        IntKey("th", { it.appTheme }, { p, v -> p.appTheme = v }),
        IntKey("sda", { it.swipeDownAction }, { p, v -> p.swipeDownAction = v }),
        IntKey("hcs", { it.hourlyChimeStartHour }, { p, v -> p.hourlyChimeStartHour = v }),
        IntKey("hce", { it.hourlyChimeEndHour }, { p, v -> p.hourlyChimeEndHour = v }),
        IntKey("mmd", { it.mindfulMorningDurationHours }, { p, v -> p.mindfulMorningDurationHours = v }),
        IntKey("mwh", { it.mindfulMorningWakeHour }, { p, v -> p.mindfulMorningWakeHour = v }),
        IntKey("mwm", { it.mindfulMorningWakeMinute }, { p, v -> p.mindfulMorningWakeMinute = v }),
    )

    private val floatKeys = listOf(
        FloatKey("tss", { it.textSizeScale }, { p, v -> p.textSizeScale = v }),
    )

    private val stringKeys = listOf(
        StringKey("cs", { it.clockStyle }, { p, v -> p.clockStyle = v }),
        StringKey("lng", { it.appLanguage }, { p, v -> p.appLanguage = v }),
        StringKey("dta", { it.doubleTapAction }, { p, v -> p.doubleTapAction = v }),
        StringKey("hcst", { it.hourlyChimeStyle }, { p, v -> p.hourlyChimeStyle = v }),
        StringKey("hcsn", { it.hourlyChimeSound }, { p, v -> p.hourlyChimeSound = v }),
        StringKey("hcu", { it.hourlyChimeCustomUri }, { p, v -> p.hourlyChimeCustomUri = v }),
        StringKey("psm", { it.prayerSourceMode }, { p, v -> p.prayerSourceMode = v }),
        StringKey("plq", { it.prayerLocationQuery }, { p, v -> p.prayerLocationQuery = v }),
        StringKey("pll", { it.prayerLocationLabel }, { p, v -> p.prayerLocationLabel = v }),
        StringKey("plat", { it.prayerLatitude }, { p, v -> p.prayerLatitude = v }),
        StringKey("plng", { it.prayerLongitude }, { p, v -> p.prayerLongitude = v }),
        StringKey("azs", { it.azanSound }, { p, v -> p.azanSound = v }),
        StringKey("azu", { it.azanCustomUri }, { p, v -> p.azanCustomUri = v }),
        StringKey("wsm", { it.weatherSourceMode }, { p, v -> p.weatherSourceMode = v }),
        StringKey("wlq", { it.weatherLocationQuery }, { p, v -> p.weatherLocationQuery = v }),
        StringKey("wll", { it.weatherLocationLabel }, { p, v -> p.weatherLocationLabel = v }),
        StringKey("wlat", { it.weatherLatitude }, { p, v -> p.weatherLatitude = v }),
        StringKey("wlng", { it.weatherLongitude }, { p, v -> p.weatherLongitude = v }),
        StringKey("wu", { it.weatherUnits }, { p, v -> p.weatherUnits = v }),
        StringKey("stap", { it.screenTimeAppPackage }, { p, v -> p.screenTimeAppPackage = v }),
        StringKey("stau", { it.screenTimeAppUser }, { p, v -> p.screenTimeAppUser = v }),
        StringKey("dnl", { it.dailyNotesList }, { p, v -> p.dailyNotesList = v }),
        StringKey("tij", { it.todoItemsJson }, { p, v -> p.todoItemsJson = v }),
    )

    fun encode(prefs: Prefs): JSONObject {
        val obj = JSONObject()
        boolKeys.forEach { key ->
            if (key.read(prefs)) obj.put(key.short, 1)
        }
        intKeys.forEach { key ->
            obj.put(key.short, key.read(prefs))
        }
        floatKeys.forEach { key ->
            obj.put(key.short, key.read(prefs).toDouble())
        }
        stringKeys.forEach { key ->
            val value = key.read(prefs)
            if (value.isNotEmpty()) obj.put(key.short, value)
        }
        if (prefs.hiddenApps.isNotEmpty()) {
            obj.put("hid", JSONArray(prefs.hiddenApps.toList()))
        }
        return obj
    }

    fun decode(prefs: Prefs, obj: JSONObject?) {
        if (obj == null) return
        boolKeys.forEach { key ->
            if (obj.has(key.short)) key.write(prefs, obj.optInt(key.short) == 1)
        }
        intKeys.forEach { key ->
            if (obj.has(key.short)) key.write(prefs, obj.optInt(key.short))
        }
        floatKeys.forEach { key ->
            if (obj.has(key.short)) key.write(prefs, obj.optDouble(key.short).toFloat())
        }
        stringKeys.forEach { key ->
            if (obj.has(key.short)) key.write(prefs, obj.optString(key.short))
        }
        obj.optJSONArray("hid")?.let { arr ->
            prefs.hiddenApps = (0 until arr.length()).map { arr.getString(it) }.toMutableSet()
        }
    }
}
