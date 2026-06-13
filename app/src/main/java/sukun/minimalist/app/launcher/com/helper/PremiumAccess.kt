package sukun.minimalist.app.launcher.com.helper

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
}
