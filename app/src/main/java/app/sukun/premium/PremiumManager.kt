package app.sukun.premium

import android.content.Context
import app.sukun.data.Prefs

object PremiumManager {

    private fun prefs(context: Context): Prefs = Prefs(context.applicationContext)

    fun isPremiumUser(context: Context): Boolean {
        return prefs(context).isProUser
    }

    fun hasAccess(context: Context, feature: PremiumFeature): Boolean {
        return when (feature) {
            PremiumFeature.WEATHER,
            PremiumFeature.PRAYER,
            PremiumFeature.DAILY_WALLPAPER,
            PremiumFeature.DAILY_NOTES,
            PremiumFeature.ADVANCED_THEMES,
            PremiumFeature.GENERAL -> isPremiumUser(context)
        }
    }

    fun unlockPremium(context: Context, token: String? = null) {
        prefs(context).unlockPremium(token)
    }

    fun revokePremium(context: Context) {
        prefs(context).revokePremium()
    }
}
