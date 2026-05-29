package app.sukun.data

import android.content.Context
import android.content.SharedPreferences
import android.view.Gravity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import app.sukun.helper.AmbientThemeController
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Prefs(context: Context) {
    private val PREFS_FILENAME = "app.sukun"

    private val FIRST_OPEN = "FIRST_OPEN"
    private val FIRST_OPEN_TIME = "FIRST_OPEN_TIME"
    private val FIRST_SETTINGS_OPEN = "FIRST_SETTINGS_OPEN"
    private val FIRST_HIDE = "FIRST_HIDE"
    private val USER_STATE = "USER_STATE"
    private val LOCK_MODE = "LOCK_MODE"
    private val HOME_APPS_NUM = "HOME_APPS_NUM"
    private val AUTO_SHOW_KEYBOARD = "AUTO_SHOW_KEYBOARD"
    private val KEYBOARD_MESSAGE = "KEYBOARD_MESSAGE"
    private val DAILY_WALLPAPER = "DAILY_WALLPAPER"
    private val DAILY_WALLPAPER_URL = "DAILY_WALLPAPER_URL"
    private val HOME_ALIGNMENT = "HOME_ALIGNMENT"
    private val HOME_BOTTOM_ALIGNMENT = "HOME_BOTTOM_ALIGNMENT"
    private val SHOW_HOME_APP_ICONS = "SHOW_HOME_APP_ICONS"
    private val APP_LABEL_ALIGNMENT = "APP_LABEL_ALIGNMENT"
    private val STATUS_BAR = "STATUS_BAR"
    private val DATE_TIME_VISIBILITY = "DATE_TIME_VISIBILITY"
    private val CLOCK_STYLE = "CLOCK_STYLE"
    private val DAY_START_HOUR = "DAY_START_HOUR"
    private val DAY_END_HOUR = "DAY_END_HOUR"
    private val SWIPE_LEFT_ENABLED = "SWIPE_LEFT_ENABLED"
    private val SWIPE_RIGHT_ENABLED = "SWIPE_RIGHT_ENABLED"
    private val HIDDEN_APPS = "HIDDEN_APPS"
    private val HIDDEN_APPS_UPDATED = "HIDDEN_APPS_UPDATED"
    private val SHOW_HINT_COUNTER = "SHOW_HINT_COUNTER"
    private val APP_THEME = "APP_THEME"
    private val AMBIENT_THEME_DARK = "AMBIENT_THEME_DARK"
    private val APP_LANGUAGE = "APP_LANGUAGE"
    private val PRO_USER = "PRO_USER"
    private val PRO_PURCHASE_TOKEN = "PRO_PURCHASE_TOKEN"
    private val ABOUT_CLICKED = "ABOUT_CLICKED"
    private val RATE_CLICKED = "RATE_CLICKED"
    private val WALLPAPER_MSG_SHOWN = "WALLPAPER_MSG_SHOWN"
    private val SHARE_SHOWN_TIME = "SHARE_SHOWN_TIME"
    private val SWIPE_DOWN_ACTION = "SWIPE_DOWN_ACTION"
    private val TEXT_SIZE_SCALE = "TEXT_SIZE_SCALE"
    private val HIDE_SET_DEFAULT_LAUNCHER = "HIDE_SET_DEFAULT_LAUNCHER"
    private val APP_DRAWER_FAST_SCROLLER = "APP_DRAWER_FAST_SCROLLER"
    private val SCREEN_TIME_LAST_UPDATED = "SCREEN_TIME_LAST_UPDATED"
    private val LAUNCHER_RESTART_TIMESTAMP = "LAUNCHER_RECREATE_TIMESTAMP"
    private val SHOWN_ON_DAY_OF_YEAR = "SHOWN_ON_DAY_OF_YEAR"
    private val HOME_BUTTON_SHOW_RECENTS = "HOME_BUTTON_SHOW_RECENTS"
    private val FOCUS_MODE_ENDS_AT = "FOCUS_MODE_ENDS_AT"
    private val FOCUS_MODE_LAST_DURATION = "FOCUS_MODE_LAST_DURATION"
    private val FOCUS_MODE_LOCK_NOTIFICATIONS = "FOCUS_MODE_LOCK_NOTIFICATIONS"
    private val FOCUS_MODE_HIDE_STATUS_BAR = "FOCUS_MODE_HIDE_STATUS_BAR"
    private val DOUBLE_TAP_ACTION = "DOUBLE_TAP_ACTION"
    private val SHOW_WEATHER_ON_HOME = "SHOW_WEATHER_ON_HOME"
    private val WEATHER_UNITS = "WEATHER_UNITS"
    private val WEATHER_SOURCE_MODE = "WEATHER_SOURCE_MODE"
    private val WEATHER_LOCATION_QUERY = "WEATHER_LOCATION_QUERY"
    private val WEATHER_LOCATION_LABEL = "WEATHER_LOCATION_LABEL"
    private val WEATHER_LATITUDE = "WEATHER_LATITUDE"
    private val WEATHER_LONGITUDE = "WEATHER_LONGITUDE"
    private val WEATHER_TEMPERATURE_TEXT = "WEATHER_TEMPERATURE_TEXT"
    private val WEATHER_CONDITION_TEXT = "WEATHER_CONDITION_TEXT"
    private val WEATHER_PRECIPITATION_TEXT = "WEATHER_PRECIPITATION_TEXT"
    private val WEATHER_LAST_UPDATED = "WEATHER_LAST_UPDATED"
    private val SHOW_PRAYER_ON_HOME = "SHOW_PRAYER_ON_HOME"
    private val SHOW_DAILY_NOTES_ON_HOME = "SHOW_DAILY_NOTES_ON_HOME"
    private val SHOW_REMINDERS_ON_HOME = "SHOW_REMINDERS_ON_HOME"
    private val DAILY_NOTES_LIST = "DAILY_NOTES_LIST"
    private val PRAYER_SOURCE_MODE = "PRAYER_SOURCE_MODE"
    private val PRAYER_LOCATION_QUERY = "PRAYER_LOCATION_QUERY"
    private val PRAYER_LOCATION_LABEL = "PRAYER_LOCATION_LABEL"
    private val PRAYER_LATITUDE = "PRAYER_LATITUDE"
    private val PRAYER_LONGITUDE = "PRAYER_LONGITUDE"
    private val PRAYER_NEXT_KEY = "PRAYER_NEXT_KEY"
    private val PRAYER_NEXT_AT = "PRAYER_NEXT_AT"
    private val PRAYER_DISPLAY_TIME = "PRAYER_DISPLAY_TIME"
    private val PRAYER_LAST_UPDATED = "PRAYER_LAST_UPDATED"
    private val AZAN_ENABLED = "AZAN_ENABLED"
    private val AZAN_SOUND = "AZAN_SOUND"
    private val AZAN_CUSTOM_URI = "AZAN_CUSTOM_URI"
    private val HOURLY_CHIME_ENABLED = "HOURLY_CHIME_ENABLED"
    private val HOURLY_CHIME_START_HOUR = "HOURLY_CHIME_START_HOUR"
    private val HOURLY_CHIME_END_HOUR = "HOURLY_CHIME_END_HOUR"
    private val RECENT_APPS = "RECENT_APPS"
    private val REMINDERS_JSON = "REMINDERS_JSON"
    private val PRAYER_LOGS = "PRAYER_LOGS"

    private val APP_NAME_1 = "APP_NAME_1"
    private val APP_NAME_2 = "APP_NAME_2"
    private val APP_NAME_3 = "APP_NAME_3"
    private val APP_NAME_4 = "APP_NAME_4"
    private val APP_NAME_5 = "APP_NAME_5"
    private val APP_NAME_6 = "APP_NAME_6"
    private val APP_NAME_7 = "APP_NAME_7"
    private val APP_NAME_8 = "APP_NAME_8"
    private val APP_PACKAGE_1 = "APP_PACKAGE_1"
    private val APP_PACKAGE_2 = "APP_PACKAGE_2"
    private val APP_PACKAGE_3 = "APP_PACKAGE_3"
    private val APP_PACKAGE_4 = "APP_PACKAGE_4"
    private val APP_PACKAGE_5 = "APP_PACKAGE_5"
    private val APP_PACKAGE_6 = "APP_PACKAGE_6"
    private val APP_PACKAGE_7 = "APP_PACKAGE_7"
    private val APP_PACKAGE_8 = "APP_PACKAGE_8"
    private val APP_ACTIVITY_CLASS_NAME_1 = "APP_ACTIVITY_CLASS_NAME_1"
    private val APP_ACTIVITY_CLASS_NAME_2 = "APP_ACTIVITY_CLASS_NAME_2"
    private val APP_ACTIVITY_CLASS_NAME_3 = "APP_ACTIVITY_CLASS_NAME_3"
    private val APP_ACTIVITY_CLASS_NAME_4 = "APP_ACTIVITY_CLASS_NAME_4"
    private val APP_ACTIVITY_CLASS_NAME_5 = "APP_ACTIVITY_CLASS_NAME_5"
    private val APP_ACTIVITY_CLASS_NAME_6 = "APP_ACTIVITY_CLASS_NAME_6"
    private val APP_ACTIVITY_CLASS_NAME_7 = "APP_ACTIVITY_CLASS_NAME_7"
    private val APP_ACTIVITY_CLASS_NAME_8 = "APP_ACTIVITY_CLASS_NAME_8"
    private val APP_USER_1 = "APP_USER_1"
    private val APP_USER_2 = "APP_USER_2"
    private val APP_USER_3 = "APP_USER_3"
    private val APP_USER_4 = "APP_USER_4"
    private val APP_USER_5 = "APP_USER_5"
    private val APP_USER_6 = "APP_USER_6"
    private val APP_USER_7 = "APP_USER_7"
    private val APP_USER_8 = "APP_USER_8"

    private val APP_NAME_SWIPE_LEFT = "APP_NAME_SWIPE_LEFT"
    private val APP_NAME_SWIPE_RIGHT = "APP_NAME_SWIPE_RIGHT"
    private val APP_PACKAGE_SWIPE_LEFT = "APP_PACKAGE_SWIPE_LEFT"
    private val APP_PACKAGE_SWIPE_RIGHT = "APP_PACKAGE_SWIPE_RIGHT"
    private val APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT = "APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT"
    private val APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT = "APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT"
    private val APP_USER_SWIPE_LEFT = "APP_USER_SWIPE_LEFT"
    private val APP_USER_SWIPE_RIGHT = "APP_USER_SWIPE_RIGHT"
    private val CLOCK_APP_PACKAGE = "CLOCK_APP_PACKAGE"
    private val CLOCK_APP_USER = "CLOCK_APP_USER"
    private val CLOCK_APP_CLASS_NAME = "CLOCK_APP_CLASS_NAME"
    private val CALENDAR_APP_PACKAGE = "CALENDAR_APP_PACKAGE"
    private val CALENDAR_APP_USER = "CALENDAR_APP_USER"
    private val CALENDAR_APP_CLASS_NAME = "CALENDAR_APP_CLASS_NAME"
    private val SCREEN_TIME_APP_PACKAGE = "SCREEN_TIME_APP_PACKAGE"
    private val SCREEN_TIME_APP_USER = "SCREEN_TIME_APP_USER"
    private val SCREEN_TIME_APP_CLASS_NAME = "SCREEN_TIME_APP_CLASS_NAME"

    private val IS_SHORTCUT_1 = "IS_SHORTCUT_1"
    private val SHORTCUT_ID_1 = "SHORTCUT_ID_1"
    private val IS_SHORTCUT_2 = "IS_SHORTCUT_2"
    private val SHORTCUT_ID_2 = "SHORTCUT_ID_2"
    private val IS_SHORTCUT_3 = "IS_SHORTCUT_3"
    private val SHORTCUT_ID_3 = "SHORTCUT_ID_3"
    private val IS_SHORTCUT_4 = "IS_SHORTCUT_4"
    private val SHORTCUT_ID_4 = "SHORTCUT_ID_4"
    private val IS_SHORTCUT_5 = "IS_SHORTCUT_5"
    private val SHORTCUT_ID_5 = "SHORTCUT_ID_5"
    private val IS_SHORTCUT_6 = "IS_SHORTCUT_6"
    private val SHORTCUT_ID_6 = "SHORTCUT_ID_6"
    private val IS_SHORTCUT_7 = "IS_SHORTCUT_7"
    private val SHORTCUT_ID_7 = "SHORTCUT_ID_7"
    private val IS_SHORTCUT_8 = "IS_SHORTCUT_8"
    private val SHORTCUT_ID_8 = "SHORTCUT_ID_8"

    private val SHORTCUT_ID_SWIPE_LEFT = "SHORTCUT_ID_SWIPE_LEFT"
    private val IS_SHORTCUT_SWIPE_LEFT = "IS_SHORTCUT_SWIPE_LEFT"
    private val SHORTCUT_ID_SWIPE_RIGHT = "SHORTCUT_ID_SWIPE_RIGHT"
    private val IS_SHORTCUT_SWIPE_RIGHT = "IS_SHORTCUT_SWIPE_RIGHT"

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_FILENAME, 0)

    var firstOpen: Boolean
        get() = prefs.getBoolean(FIRST_OPEN, true)
        set(value) = prefs.edit { putBoolean(FIRST_OPEN, value).apply() }

    var firstOpenTime: Long
        get() = prefs.getLong(FIRST_OPEN_TIME, 0L)
        set(value) = prefs.edit { putLong(FIRST_OPEN_TIME, value).apply() }

    var firstSettingsOpen: Boolean
        get() = prefs.getBoolean(FIRST_SETTINGS_OPEN, true)
        set(value) = prefs.edit { putBoolean(FIRST_SETTINGS_OPEN, value).apply() }

    var firstHide: Boolean
        get() = prefs.getBoolean(FIRST_HIDE, true)
        set(value) = prefs.edit { putBoolean(FIRST_HIDE, value).apply() }

    var userState: String
        get() = prefs.getString(USER_STATE, Constants.UserState.START).toString()
        set(value) = prefs.edit { putString(USER_STATE, value).apply() }

    var lockModeOn: Boolean
        get() = prefs.getBoolean(LOCK_MODE, false)
        set(value) = prefs.edit { putBoolean(LOCK_MODE, value).apply() }

    var autoShowKeyboard: Boolean
        get() = true
        set(value) = prefs.edit { putBoolean(AUTO_SHOW_KEYBOARD, value).apply() }

    var keyboardMessageShown: Boolean
        get() = prefs.getBoolean(KEYBOARD_MESSAGE, false)
        set(value) = prefs.edit { putBoolean(KEYBOARD_MESSAGE, value).apply() }

    var dailyWallpaper: Boolean
        get() = prefs.getBoolean(DAILY_WALLPAPER, false)
        set(value) = prefs.edit { putBoolean(DAILY_WALLPAPER, value).apply() }

    var dailyWallpaperUrl: String
        get() = prefs.getString(DAILY_WALLPAPER_URL, "").toString()
        set(value) = prefs.edit { putString(DAILY_WALLPAPER_URL, value).apply() }

    var homeAppsNum: Int
        get() = prefs.getInt(HOME_APPS_NUM, 4).coerceIn(0, 5)
        set(value) = prefs.edit { putInt(HOME_APPS_NUM, value.coerceIn(0, 5)).apply() }

    var homeAlignment: Int
        get() = prefs.getInt(HOME_ALIGNMENT, Gravity.START)
        set(value) = prefs.edit { putInt(HOME_ALIGNMENT, value).apply() }

    var homeBottomAlignment: Boolean
        get() = prefs.getBoolean(HOME_BOTTOM_ALIGNMENT, false)
        set(value) = prefs.edit { putBoolean(HOME_BOTTOM_ALIGNMENT, value).apply() }

    var showHomeAppIcons: Boolean
        get() = prefs.getBoolean(SHOW_HOME_APP_ICONS, false)
        set(value) = prefs.edit { putBoolean(SHOW_HOME_APP_ICONS, value).apply() }

    var appLabelAlignment: Int
        get() = prefs.getInt(APP_LABEL_ALIGNMENT, Gravity.START)
        set(value) = prefs.edit { putInt(APP_LABEL_ALIGNMENT, value).apply() }

    var showStatusBar: Boolean
        get() = prefs.getBoolean(STATUS_BAR, false)
        set(value) = prefs.edit { putBoolean(STATUS_BAR, value).apply() }

    var dateTimeVisibility: Int
        get() = prefs.getInt(DATE_TIME_VISIBILITY, Constants.DateTime.ON)
        set(value) = prefs.edit { putInt(DATE_TIME_VISIBILITY, value).apply() }

    var clockStyle: String
        get() = prefs.getString(CLOCK_STYLE, Constants.ClockStyle.STANDARD).toString()
        set(value) = prefs.edit { putString(CLOCK_STYLE, value).apply() }

    var dayStartHour: Int
        get() = prefs.getInt(DAY_START_HOUR, Constants.DEFAULT_DAY_START_HOUR)
        set(value) = prefs.edit { putInt(DAY_START_HOUR, value).apply() }

    var dayEndHour: Int
        get() = prefs.getInt(DAY_END_HOUR, Constants.DEFAULT_DAY_END_HOUR)
        set(value) = prefs.edit { putInt(DAY_END_HOUR, value).apply() }

    var swipeLeftEnabled: Boolean
        get() = prefs.getBoolean(SWIPE_LEFT_ENABLED, true)
        set(value) = prefs.edit { putBoolean(SWIPE_LEFT_ENABLED, value).apply() }

    var swipeRightEnabled: Boolean
        get() = prefs.getBoolean(SWIPE_RIGHT_ENABLED, true)
        set(value) = prefs.edit { putBoolean(SWIPE_RIGHT_ENABLED, value).apply() }

    var appTheme: Int
        get() = prefs.getInt(APP_THEME, AppCompatDelegate.MODE_NIGHT_YES)
        set(value) = prefs.edit { putInt(APP_THEME, value).apply() }

    /** Resolved dark state when [appTheme] is [Constants.THEME_MODE_AMBIENT_LIGHT]. */
    var ambientThemeDark: Boolean
        get() = prefs.getBoolean(AMBIENT_THEME_DARK, true)
        set(value) = prefs.edit { putBoolean(AMBIENT_THEME_DARK, value).apply() }

    fun isAmbientLightTheme(): Boolean = appTheme == Constants.THEME_MODE_AMBIENT_LIGHT

    fun resolveLaunchNightMode(): Int {
        return when (appTheme) {
            Constants.THEME_MODE_AMBIENT_LIGHT -> {
                if (!isProUser) AppCompatDelegate.MODE_NIGHT_YES
                else AmbientThemeController.nightModeForDark(ambientThemeDark)
            }
            else -> appTheme
        }
    }

    fun isEffectivelyDarkTheme(): Boolean {
        return when (appTheme) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            Constants.THEME_MODE_AMBIENT_LIGHT -> ambientThemeDark
            else -> false
        }
    }

    var appLanguage: String
        get() = prefs.getString(APP_LANGUAGE, "").toString()
        set(value) = prefs.edit { putString(APP_LANGUAGE, value).apply() }

    var textSizeScale: Float
        get() = prefs.getFloat(TEXT_SIZE_SCALE, 1.0f).coerceIn(0.5f, 2.0f)
        set(value) = prefs.edit { putFloat(TEXT_SIZE_SCALE, value.coerceIn(0.5f, 2.0f)).apply() }

    var hideSetDefaultLauncher: Boolean
        get() = prefs.getBoolean(HIDE_SET_DEFAULT_LAUNCHER, false)
        set(value) = prefs.edit { putBoolean(HIDE_SET_DEFAULT_LAUNCHER, value).apply() }

    var appDrawerFastScroller: Boolean
        get() = true
        set(value) = prefs.edit { putBoolean(APP_DRAWER_FAST_SCROLLER, value).apply() }

    var screenTimeLastUpdated: Long
        get() = prefs.getLong(SCREEN_TIME_LAST_UPDATED, 0L)
        set(value) = prefs.edit { putLong(SCREEN_TIME_LAST_UPDATED, value).apply() }

    var launcherRestartTimestamp: Long
        get() = prefs.getLong(LAUNCHER_RESTART_TIMESTAMP, 0L)
        set(value) = prefs.edit { putLong(LAUNCHER_RESTART_TIMESTAMP, value).apply() }

    var shownOnDayOfYear: Int
        get() = prefs.getInt(SHOWN_ON_DAY_OF_YEAR, 0)
        set(value) = prefs.edit { putInt(SHOWN_ON_DAY_OF_YEAR, value).apply() }

    var homeButtonShowRecents: Boolean
        get() = true
        set(value) = prefs.edit { putBoolean(HOME_BUTTON_SHOW_RECENTS, value).apply() }

    var focusModeEndsAt: Long
        get() = prefs.getLong(FOCUS_MODE_ENDS_AT, 0L)
        set(value) = prefs.edit { putLong(FOCUS_MODE_ENDS_AT, value).apply() }

    var focusModeLastDuration: Long
        get() = prefs.getLong(FOCUS_MODE_LAST_DURATION, Constants.FocusModeDuration.FIFTEEN_MIN)
        set(value) = prefs.edit { putLong(FOCUS_MODE_LAST_DURATION, value).apply() }

    var focusModeLockNotifications: Boolean
        get() = prefs.getBoolean(FOCUS_MODE_LOCK_NOTIFICATIONS, false)
        set(value) = prefs.edit { putBoolean(FOCUS_MODE_LOCK_NOTIFICATIONS, value).apply() }

    /** When true (default), focus mode hides the status bar like before. When false, status bar follows the global setting. */
    var focusModeHideStatusBar: Boolean
        get() = prefs.getBoolean(FOCUS_MODE_HIDE_STATUS_BAR, true)
        set(value) = prefs.edit { putBoolean(FOCUS_MODE_HIDE_STATUS_BAR, value).apply() }

    var doubleTapAction: String
        get() = prefs.getString(DOUBLE_TAP_ACTION, Constants.DoubleTapAction.LOCK).toString()
        set(value) = prefs.edit { putString(DOUBLE_TAP_ACTION, value).apply() }

    var showWeatherOnHome: Boolean
        get() = prefs.getBoolean(SHOW_WEATHER_ON_HOME, false)
        set(value) = prefs.edit { putBoolean(SHOW_WEATHER_ON_HOME, value).apply() }

    var weatherUnits: String
        get() = prefs.getString(WEATHER_UNITS, Constants.WeatherUnit.CELSIUS).toString()
        set(value) = prefs.edit { putString(WEATHER_UNITS, value).apply() }

    var weatherSourceMode: String
        get() = prefs.getString(WEATHER_SOURCE_MODE, Constants.WeatherSource.DEVICE).toString()
        set(value) = prefs.edit { putString(WEATHER_SOURCE_MODE, value).apply() }

    var weatherLocationQuery: String
        get() = prefs.getString(WEATHER_LOCATION_QUERY, "").toString()
        set(value) = prefs.edit { putString(WEATHER_LOCATION_QUERY, value).apply() }

    var weatherLocationLabel: String
        get() = prefs.getString(WEATHER_LOCATION_LABEL, "").toString()
        set(value) = prefs.edit { putString(WEATHER_LOCATION_LABEL, value).apply() }

    var weatherLatitude: String
        get() = prefs.getString(WEATHER_LATITUDE, "").toString()
        set(value) = prefs.edit { putString(WEATHER_LATITUDE, value).apply() }

    var weatherLongitude: String
        get() = prefs.getString(WEATHER_LONGITUDE, "").toString()
        set(value) = prefs.edit { putString(WEATHER_LONGITUDE, value).apply() }

    var weatherTemperatureText: String
        get() = prefs.getString(WEATHER_TEMPERATURE_TEXT, "").toString()
        set(value) = prefs.edit { putString(WEATHER_TEMPERATURE_TEXT, value).apply() }

    var weatherConditionText: String
        get() = prefs.getString(WEATHER_CONDITION_TEXT, "").toString()
        set(value) = prefs.edit { putString(WEATHER_CONDITION_TEXT, value).apply() }

    var weatherPrecipitationText: String
        get() = prefs.getString(WEATHER_PRECIPITATION_TEXT, "").toString()
        set(value) = prefs.edit { putString(WEATHER_PRECIPITATION_TEXT, value).apply() }

    var weatherLastUpdated: Long
        get() = prefs.getLong(WEATHER_LAST_UPDATED, 0L)
        set(value) = prefs.edit { putLong(WEATHER_LAST_UPDATED, value).apply() }

    var showPrayerOnHome: Boolean
        get() = prefs.getBoolean(SHOW_PRAYER_ON_HOME, true)
        set(value) = prefs.edit { putBoolean(SHOW_PRAYER_ON_HOME, value).apply() }

    var isProUser: Boolean
        get() = prefs.getBoolean(PRO_USER, true)
        set(value) = prefs.edit { putBoolean(PRO_USER, value).apply() }

    var proPurchaseToken: String
        get() = prefs.getString(PRO_PURCHASE_TOKEN, "").toString()
        set(value) = prefs.edit { putString(PRO_PURCHASE_TOKEN, value).apply() }

    fun unlockPremium(token: String? = null) {
        isProUser = true
        proPurchaseToken = token.orEmpty()
    }

    fun revokePremium() {
        isProUser = false
        proPurchaseToken = ""
    }

    var showDailyNotesOnHome: Boolean
        get() = prefs.getBoolean(SHOW_DAILY_NOTES_ON_HOME, false)
        set(value) = prefs.edit { putBoolean(SHOW_DAILY_NOTES_ON_HOME, value).apply() }

    var showRemindersOnHome: Boolean
        get() = prefs.getBoolean(SHOW_REMINDERS_ON_HOME, true)
        set(value) = prefs.edit { putBoolean(SHOW_REMINDERS_ON_HOME, value).apply() }

    var dailyNotesList: String
        get() = prefs.getString(DAILY_NOTES_LIST, "").toString()
        set(value) = prefs.edit { putString(DAILY_NOTES_LIST, value).apply() }

    var prayerSourceMode: String
        get() = prefs.getString(PRAYER_SOURCE_MODE, Constants.PrayerSource.DEVICE).toString()
        set(value) = prefs.edit { putString(PRAYER_SOURCE_MODE, value).apply() }

    var prayerLocationQuery: String
        get() = prefs.getString(PRAYER_LOCATION_QUERY, "").toString()
        set(value) = prefs.edit { putString(PRAYER_LOCATION_QUERY, value).apply() }

    var prayerLocationLabel: String
        get() = prefs.getString(PRAYER_LOCATION_LABEL, "").toString()
        set(value) = prefs.edit { putString(PRAYER_LOCATION_LABEL, value).apply() }

    var prayerLatitude: String
        get() = prefs.getString(PRAYER_LATITUDE, "").toString()
        set(value) = prefs.edit { putString(PRAYER_LATITUDE, value).apply() }

    var prayerLongitude: String
        get() = prefs.getString(PRAYER_LONGITUDE, "").toString()
        set(value) = prefs.edit { putString(PRAYER_LONGITUDE, value).apply() }

    var prayerNextKey: String
        get() = prefs.getString(PRAYER_NEXT_KEY, "").toString()
        set(value) = prefs.edit { putString(PRAYER_NEXT_KEY, value).apply() }

    var prayerNextAt: Long
        get() = prefs.getLong(PRAYER_NEXT_AT, 0L)
        set(value) = prefs.edit { putLong(PRAYER_NEXT_AT, value).apply() }

    var prayerDisplayTime: String
        get() = prefs.getString(PRAYER_DISPLAY_TIME, "").toString()
        set(value) = prefs.edit { putString(PRAYER_DISPLAY_TIME, value).apply() }

    var prayerLastUpdated: Long
        get() = prefs.getLong(PRAYER_LAST_UPDATED, 0L)
        set(value) = prefs.edit { putLong(PRAYER_LAST_UPDATED, value).apply() }

    var azanEnabled: Boolean
        get() = prefs.getBoolean(AZAN_ENABLED, true)
        set(value) = prefs.edit { putBoolean(AZAN_ENABLED, value).apply() }

    var azanSound: String
        get() = prefs.getString(AZAN_SOUND, Constants.AzanSound.MAKKAH).toString()
        set(value) = prefs.edit { putString(AZAN_SOUND, value).apply() }

    var azanCustomUri: String
        get() = prefs.getString(AZAN_CUSTOM_URI, "").toString()
        set(value) = prefs.edit { putString(AZAN_CUSTOM_URI, value).apply() }

    var hourlyChimeEnabled: Boolean
        get() = prefs.getBoolean(HOURLY_CHIME_ENABLED, false)
        set(value) = prefs.edit { putBoolean(HOURLY_CHIME_ENABLED, value).apply() }

    var hourlyChimeStartHour: Int
        get() = prefs.getInt(HOURLY_CHIME_START_HOUR, Constants.HourlyChime.DEFAULT_START_HOUR)
        set(value) = prefs.edit { putInt(HOURLY_CHIME_START_HOUR, value).apply() }

    var hourlyChimeEndHour: Int
        get() = prefs.getInt(HOURLY_CHIME_END_HOUR, Constants.HourlyChime.DEFAULT_END_HOUR)
        set(value) = prefs.edit { putInt(HOURLY_CHIME_END_HOUR, value).apply() }

    var remindersJson: String
        get() = prefs.getString(REMINDERS_JSON, "").toString()
        set(value) = prefs.edit { putString(REMINDERS_JSON, value).apply() }

    var hiddenApps: MutableSet<String>
        get() = prefs.getStringSet(HIDDEN_APPS, mutableSetOf()) as MutableSet<String>
        set(value) = prefs.edit { putStringSet(HIDDEN_APPS, value).apply() }

    var recentApps: List<String>
        get() = prefs.getString(RECENT_APPS, "")
            .orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        set(value) = prefs.edit { putString(RECENT_APPS, value.joinToString(",")).apply() }

    var hiddenAppsUpdated: Boolean
        get() = prefs.getBoolean(HIDDEN_APPS_UPDATED, false)
        set(value) = prefs.edit { putBoolean(HIDDEN_APPS_UPDATED, value).apply() }

    var toShowHintCounter: Int
        get() = prefs.getInt(SHOW_HINT_COUNTER, 1)
        set(value) = prefs.edit { putInt(SHOW_HINT_COUNTER, value).apply() }

    var aboutClicked: Boolean
        get() = prefs.getBoolean(ABOUT_CLICKED, false)
        set(value) = prefs.edit { putBoolean(ABOUT_CLICKED, value).apply() }

    var rateClicked: Boolean
        get() = prefs.getBoolean(RATE_CLICKED, false)
        set(value) = prefs.edit { putBoolean(RATE_CLICKED, value).apply() }

    var wallpaperMsgShown: Boolean
        get() = prefs.getBoolean(WALLPAPER_MSG_SHOWN, false)
        set(value) = prefs.edit { putBoolean(WALLPAPER_MSG_SHOWN, value).apply() }

    var shareShownTime: Long
        get() = prefs.getLong(SHARE_SHOWN_TIME, 0L)
        set(value) = prefs.edit { putLong(SHARE_SHOWN_TIME, value).apply() }

    var swipeDownAction: Int
        get() = prefs.getInt(SWIPE_DOWN_ACTION, Constants.SwipeDownAction.NOTIFICATIONS)
        set(value) = prefs.edit { putInt(SWIPE_DOWN_ACTION, value).apply() }

    var appName1: String
        get() = prefs.getString(APP_NAME_1, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_1, value).apply() }

    var appName2: String
        get() = prefs.getString(APP_NAME_2, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_2, value).apply() }

    var appName3: String
        get() = prefs.getString(APP_NAME_3, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_3, value).apply() }

    var appName4: String
        get() = prefs.getString(APP_NAME_4, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_4, value).apply() }

    var appName5: String
        get() = prefs.getString(APP_NAME_5, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_5, value).apply() }

    var appName6: String
        get() = prefs.getString(APP_NAME_6, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_6, value).apply() }

    var appName7: String
        get() = prefs.getString(APP_NAME_7, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_7, value).apply() }

    var appName8: String
        get() = prefs.getString(APP_NAME_8, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_8, value).apply() }

    var appPackage1: String
        get() = prefs.getString(APP_PACKAGE_1, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_1, value).apply() }

    var appPackage2: String
        get() = prefs.getString(APP_PACKAGE_2, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_2, value).apply() }

    var appPackage3: String
        get() = prefs.getString(APP_PACKAGE_3, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_3, value).apply() }

    var appPackage4: String
        get() = prefs.getString(APP_PACKAGE_4, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_4, value).apply() }

    var appPackage5: String
        get() = prefs.getString(APP_PACKAGE_5, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_5, value).apply() }

    var appPackage6: String
        get() = prefs.getString(APP_PACKAGE_6, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_6, value).apply() }

    var appPackage7: String
        get() = prefs.getString(APP_PACKAGE_7, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_7, value).apply() }

    var appPackage8: String
        get() = prefs.getString(APP_PACKAGE_8, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_8, value).apply() }

    var appActivityClassName1: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_1, null)
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_1, value).apply() }

    var appActivityClassName2: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_2, null)
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_2, value).apply() }

    var appActivityClassName3: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_3, null)
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_3, value).apply() }

    var appActivityClassName4: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_4, null)
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_4, value).apply() }

    var appActivityClassName5: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_5, null)
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_5, value).apply() }

    var appActivityClassName6: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_6, null)
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_6, value).apply() }

    var appActivityClassName7: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_7, null)
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_7, value).apply() }

    var appActivityClassName8: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_8, null)
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_8, value).apply() }

    var appUser1: String
        get() = prefs.getString(APP_USER_1, "").toString()
        set(value) = prefs.edit { putString(APP_USER_1, value).apply() }

    var appUser2: String
        get() = prefs.getString(APP_USER_2, "").toString()
        set(value) = prefs.edit { putString(APP_USER_2, value).apply() }

    var appUser3: String
        get() = prefs.getString(APP_USER_3, "").toString()
        set(value) = prefs.edit { putString(APP_USER_3, value).apply() }

    var appUser4: String
        get() = prefs.getString(APP_USER_4, "").toString()
        set(value) = prefs.edit { putString(APP_USER_4, value).apply() }

    var appUser5: String
        get() = prefs.getString(APP_USER_5, "").toString()
        set(value) = prefs.edit { putString(APP_USER_5, value).apply() }

    var appUser6: String
        get() = prefs.getString(APP_USER_6, "").toString()
        set(value) = prefs.edit { putString(APP_USER_6, value).apply() }

    var appUser7: String
        get() = prefs.getString(APP_USER_7, "").toString()
        set(value) = prefs.edit { putString(APP_USER_7, value).apply() }

    var appUser8: String
        get() = prefs.getString(APP_USER_8, "").toString()
        set(value) = prefs.edit { putString(APP_USER_8, value).apply() }

    var appNameSwipeLeft: String
        get() = prefs.getString(APP_NAME_SWIPE_LEFT, "Camera").toString()
        set(value) = prefs.edit { putString(APP_NAME_SWIPE_LEFT, value).apply() }

    var appNameSwipeRight: String
        get() = prefs.getString(APP_NAME_SWIPE_RIGHT, "Phone").toString()
        set(value) = prefs.edit { putString(APP_NAME_SWIPE_RIGHT, value).apply() }

    var appPackageSwipeLeft: String
        get() = prefs.getString(APP_PACKAGE_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_SWIPE_LEFT, value).apply() }

    var appActivityClassNameSwipeLeft: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT, null)
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT, value).apply() }

    var appPackageSwipeRight: String
        get() = prefs.getString(APP_PACKAGE_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_SWIPE_RIGHT, value).apply() }

    var appActivityClassNameSwipeRight: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT, null)
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT, value).apply() }

    var appUserSwipeLeft: String
        get() = prefs.getString(APP_USER_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit { putString(APP_USER_SWIPE_LEFT, value).apply() }

    var appUserSwipeRight: String
        get() = prefs.getString(APP_USER_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit { putString(APP_USER_SWIPE_RIGHT, value).apply() }

    var clockAppPackage: String
        get() = prefs.getString(CLOCK_APP_PACKAGE, "").toString()
        set(value) = prefs.edit { putString(CLOCK_APP_PACKAGE, value).apply() }

    var clockAppUser: String
        get() = prefs.getString(CLOCK_APP_USER, "").toString()
        set(value) = prefs.edit { putString(CLOCK_APP_USER, value).apply() }

    var clockAppClassName: String?
        get() = prefs.getString(CLOCK_APP_CLASS_NAME, null)
        set(value) = prefs.edit { putString(CLOCK_APP_CLASS_NAME, value).apply() }

    var calendarAppPackage: String
        get() = prefs.getString(CALENDAR_APP_PACKAGE, "").toString()
        set(value) = prefs.edit { putString(CALENDAR_APP_PACKAGE, value).apply() }

    var calendarAppUser: String
        get() = prefs.getString(CALENDAR_APP_USER, "").toString()
        set(value) = prefs.edit { putString(CALENDAR_APP_USER, value).apply() }

    var calendarAppClassName: String?
        get() = prefs.getString(CALENDAR_APP_CLASS_NAME, null)
        set(value) = prefs.edit { putString(CALENDAR_APP_CLASS_NAME, value).apply() }

    var screenTimeAppPackage: String
        get() = prefs.getString(SCREEN_TIME_APP_PACKAGE, "").toString()
        set(value) = prefs.edit { putString(SCREEN_TIME_APP_PACKAGE, value).apply() }

    var screenTimeAppUser: String
        get() = prefs.getString(SCREEN_TIME_APP_USER, "").toString()
        set(value) = prefs.edit { putString(SCREEN_TIME_APP_USER, value).apply() }

    var screenTimeAppClassName: String?
        get() = prefs.getString(SCREEN_TIME_APP_CLASS_NAME, null)
        set(value) = prefs.edit { putString(SCREEN_TIME_APP_CLASS_NAME, value).apply() }

    var isShortcut1: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_1, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_1, value).apply() }

    var shortcutId1: String
        get() = prefs.getString(SHORTCUT_ID_1, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_1, value).apply() }

    var isShortcut2: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_2, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_2, value).apply() }

    var shortcutId2: String
        get() = prefs.getString(SHORTCUT_ID_2, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_2, value).apply() }

    var isShortcut3: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_3, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_3, value).apply() }

    var shortcutId3: String
        get() = prefs.getString(SHORTCUT_ID_3, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_3, value).apply() }

    var isShortcut4: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_4, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_4, value).apply() }

    var shortcutId4: String
        get() = prefs.getString(SHORTCUT_ID_4, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_4, value).apply() }

    var isShortcut5: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_5, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_5, value).apply() }

    var shortcutId5: String
        get() = prefs.getString(SHORTCUT_ID_5, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_5, value).apply() }

    var isShortcut6: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_6, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_6, value).apply() }

    var shortcutId6: String
        get() = prefs.getString(SHORTCUT_ID_6, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_6, value).apply() }

    var isShortcut7: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_7, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_7, value).apply() }

    var shortcutId7: String
        get() = prefs.getString(SHORTCUT_ID_7, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_7, value).apply() }

    var isShortcut8: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_8, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_8, value).apply() }

    var shortcutId8: String
        get() = prefs.getString(SHORTCUT_ID_8, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_8, value).apply() }

    var shortcutIdSwipeLeft: String
        get() = prefs.getString(SHORTCUT_ID_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_SWIPE_LEFT, value).apply() }

    var isShortcutSwipeLeft: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_SWIPE_LEFT, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_SWIPE_LEFT, value).apply() }

    var shortcutIdSwipeRight: String
        get() = prefs.getString(SHORTCUT_ID_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_SWIPE_RIGHT, value).apply() }

    var isShortcutSwipeRight: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_SWIPE_RIGHT, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_SWIPE_RIGHT, value).apply() }

    fun getAppName(location: Int): String {
        return when (location) {
            1 -> prefs.getString(APP_NAME_1, "").toString()
            2 -> prefs.getString(APP_NAME_2, "").toString()
            3 -> prefs.getString(APP_NAME_3, "").toString()
            4 -> prefs.getString(APP_NAME_4, "").toString()
            5 -> prefs.getString(APP_NAME_5, "").toString()
            6 -> prefs.getString(APP_NAME_6, "").toString()
            7 -> prefs.getString(APP_NAME_7, "").toString()
            8 -> prefs.getString(APP_NAME_8, "").toString()
            else -> ""
        }
    }

    fun getAppPackage(location: Int): String {
        return when (location) {
            1 -> prefs.getString(APP_PACKAGE_1, "").toString()
            2 -> prefs.getString(APP_PACKAGE_2, "").toString()
            3 -> prefs.getString(APP_PACKAGE_3, "").toString()
            4 -> prefs.getString(APP_PACKAGE_4, "").toString()
            5 -> prefs.getString(APP_PACKAGE_5, "").toString()
            6 -> prefs.getString(APP_PACKAGE_6, "").toString()
            7 -> prefs.getString(APP_PACKAGE_7, "").toString()
            8 -> prefs.getString(APP_PACKAGE_8, "").toString()
            else -> ""
        }
    }

    fun getAppActivityClassName(location: Int): String {
        return when (location) {
            1 -> prefs.getString(APP_ACTIVITY_CLASS_NAME_1, "").toString()
            2 -> prefs.getString(APP_ACTIVITY_CLASS_NAME_2, "").toString()
            3 -> prefs.getString(APP_ACTIVITY_CLASS_NAME_3, "").toString()
            4 -> prefs.getString(APP_ACTIVITY_CLASS_NAME_4, "").toString()
            5 -> prefs.getString(APP_ACTIVITY_CLASS_NAME_5, "").toString()
            6 -> prefs.getString(APP_ACTIVITY_CLASS_NAME_6, "").toString()
            7 -> prefs.getString(APP_ACTIVITY_CLASS_NAME_7, "").toString()
            8 -> prefs.getString(APP_ACTIVITY_CLASS_NAME_8, "").toString()
            else -> ""
        }
    }

    fun getAppUser(location: Int): String {
        return when (location) {
            1 -> prefs.getString(APP_USER_1, "").toString()
            2 -> prefs.getString(APP_USER_2, "").toString()
            3 -> prefs.getString(APP_USER_3, "").toString()
            4 -> prefs.getString(APP_USER_4, "").toString()
            5 -> prefs.getString(APP_USER_5, "").toString()
            6 -> prefs.getString(APP_USER_6, "").toString()
            7 -> prefs.getString(APP_USER_7, "").toString()
            8 -> prefs.getString(APP_USER_8, "").toString()
            else -> ""
        }
    }

    fun getShortcutId(location: Int): String {
        return when (location) {
            1 -> shortcutId1
            2 -> shortcutId2
            3 -> shortcutId3
            4 -> shortcutId4
            5 -> shortcutId5
            6 -> shortcutId6
            7 -> shortcutId7
            8 -> shortcutId8
            else -> ""
        }
    }

    fun getIsShortcut(location: Int): Boolean {
        return when (location) {
            1 -> isShortcut1
            2 -> isShortcut2
            3 -> isShortcut3
            4 -> isShortcut4
            5 -> isShortcut5
            6 -> isShortcut6
            7 -> isShortcut7
            8 -> isShortcut8
            else -> false
        }
    }

    fun setAppActivityClassName(location: Int, activityClassName: String) {
        when (location) {
            1 -> appActivityClassName1 = activityClassName
            2 -> appActivityClassName2 = activityClassName
            3 -> appActivityClassName3 = activityClassName
            4 -> appActivityClassName4 = activityClassName
            5 -> appActivityClassName5 = activityClassName
            6 -> appActivityClassName6 = activityClassName
            7 -> appActivityClassName7 = activityClassName
            8 -> appActivityClassName8 = activityClassName
        }
    }

    fun updateAppActivityClassName(packageName: String, activityClassName: String) {
        for (i in 1..8) {
            if (getAppPackage(i) == packageName) setAppActivityClassName(i, activityClassName)
        }
        if (clockAppPackage == packageName) clockAppClassName = activityClassName
        if (calendarAppPackage == packageName) calendarAppClassName = activityClassName
        if (screenTimeAppPackage == packageName) screenTimeAppClassName = activityClassName
        if (appPackageSwipeLeft == packageName) appActivityClassNameSwipeLeft = activityClassName
        if (appPackageSwipeRight == packageName) appActivityClassNameSwipeRight = activityClassName
    }

    fun getAppRenameLabel(appPackage: String): String = prefs.getString(appPackage, "").toString()

    fun setAppRenameLabel(appPackage: String, renameLabel: String) = prefs.edit { putString(appPackage, renameLabel).apply() }

    fun pushRecentApp(packageName: String, maxSize: Int = 6) {
        if (packageName.isBlank()) return
        val updated = recentApps.toMutableList().apply {
            remove(packageName)
            add(0, packageName)
            while (size > maxSize) removeAt(lastIndex)
        }
        recentApps = updated
    }

    fun clearWeatherCache() {
        weatherTemperatureText = ""
        weatherConditionText = ""
        weatherPrecipitationText = ""
        weatherLastUpdated = 0L
    }

    fun clearWeatherLocation() {
        weatherLocationQuery = ""
        weatherLocationLabel = ""
        weatherLatitude = ""
        weatherLongitude = ""
        clearWeatherCache()
    }

    fun clearPrayerCache() {
        prayerNextKey = ""
        prayerNextAt = 0L
        prayerDisplayTime = ""
        prayerLastUpdated = 0L
    }

    private var prayerLogsJson: String
        get() = prefs.getString(PRAYER_LOGS, "[]") ?: "[]"
        set(value) = prefs.edit { putString(PRAYER_LOGS, value).apply() }

    fun logPrayer(prayerKey: String) {
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val logs = getPrayerLogs().toMutableList()
        logs.removeAll { it.prayerKey == prayerKey && it.dateKey == dateKey }
        logs.add(PrayerLog(prayerKey, dateKey, System.currentTimeMillis()))
        val cutoff = System.currentTimeMillis() - 2L * 365 * 24 * 60 * 60 * 1000
        val trimmed = logs.filter { it.timestamp > cutoff }
        val arr = JSONArray()
        trimmed.forEach { log ->
            arr.put(JSONObject().apply {
                put("p", log.prayerKey)
                put("d", log.dateKey)
                put("t", log.timestamp)
            })
        }
        prayerLogsJson = arr.toString()
    }

    fun unmarkPrayer(prayerKey: String) {
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val logs = getPrayerLogs().toMutableList()
        logs.removeAll { it.prayerKey == prayerKey && it.dateKey == dateKey }
        val cutoff = System.currentTimeMillis() - 2L * 365 * 24 * 60 * 60 * 1000
        val trimmed = logs.filter { it.timestamp > cutoff }
        val arr = JSONArray()
        trimmed.forEach { log ->
            arr.put(JSONObject().apply {
                put("p", log.prayerKey)
                put("d", log.dateKey)
                put("t", log.timestamp)
            })
        }
        prayerLogsJson = arr.toString()
    }

    fun getPrayerLogs(): List<PrayerLog> {
        return try {
            val arr = JSONArray(prayerLogsJson)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                PrayerLog(
                    prayerKey = obj.getString("p"),
                    dateKey = obj.getString("d"),
                    timestamp = obj.getLong("t"),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clearPrayerLocation() {
        prayerLocationQuery = ""
        prayerLocationLabel = ""
        prayerLatitude = ""
        prayerLongitude = ""
        clearPrayerCache()
    }

    fun startFocusMode(durationInMillis: Long) {
        focusModeEndsAt = System.currentTimeMillis() + durationInMillis
        focusModeLastDuration = durationInMillis
    }

    fun clearFocusMode() {
        focusModeEndsAt = 0L
    }

    fun isFocusModeActive(): Boolean {
        if (focusModeEndsAt == 0L) return false
        if (focusModeEndsAt <= System.currentTimeMillis()) {
            clearFocusMode()
            return false
        }
        return true
    }

    fun getFocusModeRemainingMillis(): Long {
        return if (isFocusModeActive()) focusModeEndsAt - System.currentTimeMillis() else 0L
    }
}