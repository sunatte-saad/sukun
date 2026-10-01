package sukun.minimalist.app.launcher.com.helper

import androidx.appcompat.app.AppCompatDelegate
import sukun.minimalist.app.launcher.com.data.Constants
import sukun.minimalist.app.launcher.com.data.Prefs

object PremiumAccess {

    const val LOCKED_ALPHA = 0.35f

    fun hasPremiumAccess(prefs: Prefs): Boolean = prefs.isProUser || isTrialActive(prefs)

    fun isTrialActive(prefs: Prefs): Boolean {
        if (prefs.isProUser) return false
        val start = prefs.firstOpenTime
        if (start == 0L) return true
        return System.currentTimeMillis() - start < Constants.PREMIUM_TRIAL_DURATION_MS
    }

    fun trialExpired(prefs: Prefs): Boolean = !prefs.isProUser && !isTrialActive(prefs)

    fun trialDaysRemaining(prefs: Prefs): Int {
        if (prefs.isProUser || !isTrialActive(prefs)) return 0
        val start = prefs.firstOpenTime
        if (start == 0L) return Constants.PREMIUM_TRIAL_DAYS
        val remainingMs = Constants.PREMIUM_TRIAL_DURATION_MS - (System.currentTimeMillis() - start)
        return ((remainingMs + Constants.ONE_DAY_IN_MILLIS - 1) / Constants.ONE_DAY_IN_MILLIS)
            .toInt()
            .coerceAtLeast(0)
    }

    fun lockedAlpha(prefs: Prefs): Float = if (hasPremiumAccess(prefs)) 1f else LOCKED_ALPHA

    fun effectiveClockStyle(prefs: Prefs): String {
        return if (prefs.clockStyle == Constants.ClockStyle.DAY_RING && !hasPremiumAccess(prefs)) {
            Constants.ClockStyle.STANDARD
        } else {
            prefs.clockStyle
        }
    }

    data class ExpiredTrialCleanup(
        val changed: Boolean,
        val wallpaperDisabled: Boolean,
        val prayerDisabled: Boolean,
        val themeChanged: Boolean,
    )

    /** Persist free-tier defaults once the 30-day trial ends (clock, wallpaper, prayer, etc.). */
    fun applyExpiredTrialDefaults(prefs: Prefs): ExpiredTrialCleanup {
        if (hasPremiumAccess(prefs)) {
            return ExpiredTrialCleanup(
                changed = false,
                wallpaperDisabled = false,
                prayerDisabled = false,
                themeChanged = false,
            )
        }
        var wallpaperDisabled = false
        var prayerDisabled = false
        var themeChanged = false
        var changed = false

        if (prefs.clockStyle != Constants.ClockStyle.STANDARD) {
            prefs.clockStyle = Constants.ClockStyle.STANDARD
            changed = true
        }
        if (prefs.dailyWallpaper) {
            prefs.dailyWallpaper = false
            wallpaperDisabled = true
            changed = true
        }
        if (prefs.appTheme == Constants.THEME_MODE_AMBIENT_LIGHT) {
            prefs.appTheme = AppCompatDelegate.MODE_NIGHT_YES
            themeChanged = true
            changed = true
        }
        if (prefs.mindfulMorningHard) {
            prefs.mindfulMorningHard = false
            changed = true
        }
        if (prefs.isFocusModeActive()) {
            prefs.clearFocusMode()
            changed = true
        }
        if (prefs.showPrayerOnHome) {
            prefs.showPrayerOnHome = false
            prayerDisabled = true
            changed = true
        }
        if (prefs.azanEnabled || prefs.azanSound != Constants.AzanSound.OFF) {
            prefs.azanEnabled = false
            prefs.azanSound = Constants.AzanSound.OFF
            changed = true
        }
        if (prefs.hourlyChimeSound == Constants.ChimeSound.CUSTOM) {
            prefs.hourlyChimeSound = Constants.ChimeSound.BUNDLED
            changed = true
        }
        return ExpiredTrialCleanup(changed, wallpaperDisabled, prayerDisabled, themeChanged)
    }
}
