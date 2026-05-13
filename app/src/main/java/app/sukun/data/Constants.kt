package app.sukun.data

object Constants {

    object Key {
        const val FLAG = "flag"
        const val RENAME = "rename"
    }

    object Dialog {
        const val ABOUT = "ABOUT"
        const val WALLPAPER = "WALLPAPER"
        const val REVIEW = "REVIEW"
        const val RATE = "RATE"
        const val SHARE = "SHARE"
        const val HIDDEN = "HIDDEN"
        const val KEYBOARD = "KEYBOARD"
        const val DIGITAL_WELLBEING = "DIGITAL_WELLBEING"
    }

    object UserState {
        const val START = "START"
        const val WALLPAPER = "WALLPAPER"
        const val REVIEW = "REVIEW"
        const val RATE = "RATE"
        const val SHARE = "SHARE"
    }

    object DateTime {
        const val OFF = 0
        const val ON = 1
        const val DATE_ONLY = 2

        fun isTimeVisible(dateTimeVisibility: Int): Boolean {
            return dateTimeVisibility == ON
        }

        fun isDateVisible(dateTimeVisibility: Int): Boolean {
            return dateTimeVisibility == ON || dateTimeVisibility == DATE_ONLY
        }
    }

    object ClockStyle {
        const val STANDARD = "standard"
        const val DAY_RING = "day_ring"
    }

    object SwipeDownAction {
        const val SEARCH = 1
        const val NOTIFICATIONS = 2
    }

    object WeatherSource {
        const val MANUAL = "manual"
        const val DEVICE = "device"
        const val GOOGLE = "google"
    }

    object PrayerSource {
        const val MANUAL = "manual"
        const val DEVICE = "device"
    }

    object WeatherUnit {
        const val CELSIUS = "celsius"
        const val FAHRENHEIT = "fahrenheit"
    }

    object Prayer {
        const val FAJR = "fajr"
        const val DHUHR = "dhuhr"
        const val ASR = "asr"
        const val MAGHRIB = "maghrib"
        const val ISHA = "isha"
        const val DISPLAY_STALE_MINUTES = 30
        const val REMINDER_WINDOW_MILLIS = 600000L
        const val NOTIFICATION_CHANNEL_ID = "prayer_reminders"
        const val NOTIFICATION_ID = 3011
        const val MARK_NOTIFICATION_ID = 3012
        const val MARK_REQUEST_CODE = 6402
        const val REMINDER_REQUEST_CODE = 6401
    }

    object PrayerCalc {
        const val METHOD = "muslim_world_league"
        const val MADHAB = "shafi"
    }

    object AzanSound {
        const val MAKKAH = "makkah"
        const val MARYLEBONE = "marylebone"
        const val CUSTOM = "custom"
    }

    object FocusModeDuration {
        const val FIFTEEN_MIN = 900000L
        const val THIRTY_MIN = 1800000L
        const val ONE_HOUR = 3600000L
        const val TWO_HOURS = 7200000L
    }

    object DoubleTapAction {
        const val LOCK = "lock"
        const val FOCUS = "focus"
    }

    object CharacterIndicator {
        const val SHOW = 102
        const val HIDE = 101
    }

    val CLOCK_APP_PACKAGES = arrayOf(
        "com.google.android.deskclock", //Google Clock
        "com.sec.android.app.clockpackage", //Samsung Clock
        "com.oneplus.deskclock", //OnePlus Clock
        "com.miui.clock", //Xiaomi Clock
    )

    const val WALL_TYPE_LIGHT = "light"
    const val WALL_TYPE_DARK = "dark"

//    const val THEME_MODE_DARK = 0
//    const val THEME_MODE_LIGHT = 1
//    const val THEME_MODE_SYSTEM = 2

    const val FLAG_LAUNCH_APP = 100
    const val FLAG_HIDDEN_APPS = 101

    const val FLAG_SET_HOME_APP_1 = 1
    const val FLAG_SET_HOME_APP_2 = 2
    const val FLAG_SET_HOME_APP_3 = 3
    const val FLAG_SET_HOME_APP_4 = 4
    const val FLAG_SET_HOME_APP_5 = 5
    const val FLAG_SET_HOME_APP_6 = 6
    const val FLAG_SET_HOME_APP_7 = 7
    const val FLAG_SET_HOME_APP_8 = 8

    const val FLAG_SET_SWIPE_LEFT_APP = 11
    const val FLAG_SET_SWIPE_RIGHT_APP = 12
    const val FLAG_SET_CLOCK_APP = 13
    const val FLAG_SET_CALENDAR_APP = 14
    const val FLAG_SET_SCREEN_TIME_APP = 15

    const val REQUEST_CODE_ENABLE_ADMIN = 666
    const val REQUEST_CODE_LAUNCHER_SELECTOR = 678

    const val HINT_RATE_US = 15

    const val LONG_PRESS_DELAY_MS = 500L
    const val ONE_DAY_IN_MILLIS = 86400000L
    const val ONE_HOUR_IN_MILLIS = 3600000L
    const val ONE_MINUTE_IN_MILLIS = 60000L
    const val MIN_CUSTOM_FOCUS_MINUTES = 1L
    const val MAX_CUSTOM_FOCUS_MINUTES = 1440L
    const val DEFAULT_DAY_START_HOUR = 6
    const val DEFAULT_DAY_END_HOUR = 22

    const val MIN_ANIM_REFRESH_RATE = 30f

    const val URL_DOUBLE_TAP = ""
    const val URL_SUKUN_PLAY_STORE = ""
    const val URL_WALLPAPERS = "https://unsplash.com/s/photos/phone-wallpaper"
    const val URL_DEFAULT_DARK_WALLPAPER = "https://images.unsplash.com/photo-1512551980832-13df02babc9e"
    const val URL_DEFAULT_LIGHT_WALLPAPER = "https://images.unsplash.com/photo-1515549832467-8783363e19b6"
    const val URL_DUCK_SEARCH = "https://duck.co/?q="
    const val URL_GOOGLE_WEATHER = "https://www.google.com/search?q=weather"
    const val URL_WEATHER_GEOCODING = "https://geocoding-api.open-meteo.com/v1/search"
    const val URL_WEATHER_FORECAST = "https://api.open-meteo.com/v1/forecast"

    const val DIGITAL_WELLBEING_PACKAGE_NAME = "com.google.android.apps.wellbeing"
    const val DIGITAL_WELLBEING_ACTIVITY = "com.google.android.apps.wellbeing.settings.TopLevelSettingsActivity"
    const val DIGITAL_WELLBEING_SAMSUNG_PACKAGE_NAME = "com.samsung.android.forest"
    const val DIGITAL_WELLBEING_SAMSUNG_ACTIVITY = "com.samsung.android.forest.launcher.LauncherActivity"
    const val WALLPAPER_WORKER_NAME = "WALLPAPER_WORKER_NAME"
    const val WEATHER_WORKER_NAME = "WEATHER_WORKER_NAME"
    const val WEATHER_REFRESH_HOURS = 4L
    const val WEATHER_STALE_MINUTES = 30
    object HourlyChime {
        const val DEFAULT_START_HOUR = 8
        const val DEFAULT_END_HOUR = 22
        const val ACTION = "app.sukun.HOURLY_CHIME"
        const val REQUEST_CODE = 7501
    }

    object Reminder {
        const val NOTIFICATION_CHANNEL_ID = "custom_reminders"
        const val ACTION = "app.sukun.REMINDER_FIRE"
        const val ACTION_DONE = "app.sukun.REMINDER_DONE"
        const val EXTRA_ID = "extra_reminder_id"
        const val EXTRA_TITLE = "extra_reminder_title"
        const val EXTRA_MESSAGE = "extra_reminder_message"
        const val BASE_REQUEST_CODE = 9000
        const val BASE_NOTIFICATION_ID = 9000
        const val BASE_DONE_REQUEST_CODE = 10000
    }

    const val PRAYER_REMINDER_ACTION = "app.sukun.PRAYER_REMINDER"
    const val PRAYER_MARK_ACTION = "app.sukun.PRAYER_MARK"
    const val PRAYER_BOOT_ACTION = "app.sukun.PRAYER_BOOT"
    const val PRAYER_STOP_ACTION = "app.sukun.PRAYER_STOP"
    const val EXTRA_PRAYER_KEY = "extra_prayer_key"
    const val EXTRA_PRAYER_TIME_MILLIS = "extra_prayer_time_millis"
    const val EXTRA_PRAYER_LABEL = "extra_prayer_label"
}