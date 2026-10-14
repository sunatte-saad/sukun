package sukun.minimalist.app.launcher.com.data

import sukun.minimalist.app.launcher.com.R
import sukun.minimalist.app.launcher.com.helper.hasBeenDays

object FeatureNudge {
    enum class Kind(
        val id: String,
        val afterActiveMillis: Long,
        val titleRes: Int,
        val messageRes: Int,
        val actionRes: Int,
        val dismissRes: Int,
    ) {
        FOCUS(
            "focus",
            Constants.FEATURE_NUDGE_FOCUS_MILLIS,
            R.string.nudge_focus_title,
            R.string.nudge_focus_message,
            R.string.enable,
            R.string.skip,
        ),
        PRAYER(
            "prayer",
            Constants.FEATURE_NUDGE_PRAYER_MILLIS,
            R.string.nudge_prayer_title,
            R.string.nudge_prayer_message,
            R.string.enable,
            R.string.skip,
        ),
        WEATHER(
            "weather",
            Constants.FEATURE_NUDGE_WEATHER_MILLIS,
            R.string.nudge_weather_title,
            R.string.nudge_weather_message,
            R.string.enable,
            R.string.cancel,
        ),
        SCREEN_TIME(
            "screen_time",
            Constants.FEATURE_NUDGE_SCREEN_TIME_MILLIS,
            R.string.nudge_screen_time_title,
            R.string.nudge_screen_time_message,
            R.string.enable,
            R.string.skip,
        ),
        SIGN_IN(
            "sign_in",
            Constants.FEATURE_NUDGE_SIGN_IN_MILLIS,
            R.string.nudge_sign_in_title,
            R.string.nudge_sign_in_message,
            R.string.sign_in_with_google,
            R.string.skip,
        ),
    }

    /**
     * The earliest reminder whose active-time threshold has been reached.
     * At most one is due, and a previous reminder blocks the next for a full day.
     */
    fun dueKind(prefs: Prefs, activeMillis: Long): Kind? {
        if (!prefs.privacyAndSetupComplete || !prefs.onboardingComplete) return null
        if (prefs.onboardingTourActive) return null
        if (prefs.featureNudgeLastShown != 0L &&
            !prefs.featureNudgeLastShown.hasBeenDays(Constants.FEATURE_NUDGE_GAP_DAYS)
        ) return null
        val shown = prefs.featureNudgeShownIds
        return Kind.entries.firstOrNull { kind ->
            kind.id !in shown &&
                !isAlreadyEnabled(prefs, kind) &&
                activeMillis >= kind.afterActiveMillis
        }
    }

    fun markShown(prefs: Prefs, kind: Kind) {
        prefs.featureNudgeShownIds = prefs.featureNudgeShownIds + kind.id
        prefs.featureNudgeLastShown = System.currentTimeMillis()
    }

    private fun isAlreadyEnabled(prefs: Prefs, kind: Kind): Boolean = when (kind) {
        Kind.FOCUS -> prefs.showFocusOnHome
        Kind.PRAYER -> prefs.showPrayerOnHome
        Kind.WEATHER -> prefs.showWeatherOnHome
        Kind.SCREEN_TIME -> prefs.showScreenTimeOnHome
        Kind.SIGN_IN -> prefs.isSignedIn
    }
}
