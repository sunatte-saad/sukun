package app.sukun.billing

import android.content.Context

object BillingManager {

    fun initialize(context: Context) {
        // TODO: initialize real billing client / purchase restore logic here.
    }

    fun isBillingAvailable(context: Context): Boolean {
        return false
    }

    fun purchasePremium(context: Context) {
        // TODO: launch Google Play billing flow to purchase premium.
    }

    fun restorePurchases(context: Context) {
        // TODO: restore previous premium purchases.
    }
}
