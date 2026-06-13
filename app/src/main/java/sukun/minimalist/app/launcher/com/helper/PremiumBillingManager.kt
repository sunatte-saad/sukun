package sukun.minimalist.app.launcher.com.helper



import android.app.Activity

import android.util.Log

import com.android.billingclient.api.AcknowledgePurchaseParams

import com.android.billingclient.api.BillingClient

import com.android.billingclient.api.BillingClientStateListener

import com.android.billingclient.api.BillingFlowParams

import com.android.billingclient.api.BillingResult

import com.android.billingclient.api.PendingPurchasesParams

import com.android.billingclient.api.ProductDetails

import com.android.billingclient.api.Purchase

import com.android.billingclient.api.PurchasesUpdatedListener

import com.android.billingclient.api.QueryProductDetailsParams

import com.android.billingclient.api.QueryPurchasesParams

import sukun.minimalist.app.launcher.com.BuildConfig

import sukun.minimalist.app.launcher.com.R

import sukun.minimalist.app.launcher.com.data.Constants

import sukun.minimalist.app.launcher.com.data.Prefs



class PremiumBillingManager(

    private val activity: Activity,

    private val prefs: Prefs,

) : PurchasesUpdatedListener {



    private val billingClient: BillingClient = BillingClient.newBuilder(activity)

        .setListener(this)

        .enablePendingPurchases(

            PendingPurchasesParams.newBuilder()

                .enableOneTimeProducts()

                .build()

        )

        .build()



    private var premiumProductDetails: ProductDetails? = null

    private var isConnecting = false

    private var pendingPurchaseLaunch = false



    var onPremiumStatusChanged: (() -> Unit)? = null



    fun start() {

        if (billingClient.isReady) {

            queryExistingPurchases()

            queryPremiumProductDetails()

            return

        }

        if (isConnecting) return

        isConnecting = true

        billingClient.startConnection(object : BillingClientStateListener {

            override fun onBillingSetupFinished(billingResult: BillingResult) {

                isConnecting = false

                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {

                    queryExistingPurchases()

                    queryPremiumProductDetails()

                    if (pendingPurchaseLaunch) {

                        pendingPurchaseLaunch = false

                        launchPremiumPurchase()

                    }

                } else {

                    Log.w(TAG, "Billing setup failed: ${billingResult.debugMessage}")

                    pendingPurchaseLaunch = false

                    showUnavailableMessage(productMissing = false)

                }

            }



            override fun onBillingServiceDisconnected() {

                isConnecting = false

            }

        })

    }



    fun launchPremiumPurchase() {

        if (prefs.isProUser) {

            activity.showToast(R.string.premium_already_active)

            return

        }

        if (!billingClient.isReady) {

            pendingPurchaseLaunch = true

            activity.showToast(R.string.premium_purchase_connecting)

            start()

            return

        }



        val productDetails = premiumProductDetails

        if (productDetails == null) {

            queryPremiumProductDetails(launchAfterQuery = true)

            return

        }



        launchBillingFlow(productDetails)

    }



    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {

        when (billingResult.responseCode) {

            BillingClient.BillingResponseCode.OK -> {

                purchases?.forEach(::handlePurchase)

            }

            BillingClient.BillingResponseCode.USER_CANCELED -> Unit

            else -> activity.showToast(R.string.premium_purchase_failed)

        }

    }



    fun endConnection() {

        if (billingClient.isReady) {

            billingClient.endConnection()

        }

    }



    private fun launchBillingFlow(productDetails: ProductDetails) {

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()

            .setProductDetails(productDetails)

            .build()



        val billingFlowParams = BillingFlowParams.newBuilder()

            .setProductDetailsParamsList(listOf(productDetailsParams))

            .build()



        val result = billingClient.launchBillingFlow(activity, billingFlowParams)

        if (result.responseCode != BillingClient.BillingResponseCode.OK) {

            activity.showToast(R.string.premium_purchase_failed)

        }

    }



    private fun queryPremiumProductDetails(launchAfterQuery: Boolean = false) {

        if (!billingClient.isReady) return



        val product = QueryProductDetailsParams.Product.newBuilder()

            .setProductId(Constants.PREMIUM_PRODUCT_ID)

            .setProductType(BillingClient.ProductType.INAPP)

            .build()



        val params = QueryProductDetailsParams.newBuilder()

            .setProductList(listOf(product))

            .build()



        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->

            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {

                Log.w(TAG, "Product query failed: ${billingResult.debugMessage}")

                if (launchAfterQuery) showUnavailableMessage(productMissing = true)

                return@queryProductDetailsAsync

            }

            premiumProductDetails = productDetailsList.firstOrNull()

            if (launchAfterQuery) {

                val details = premiumProductDetails

                if (details != null) {

                    launchBillingFlow(details)

                } else {

                    showUnavailableMessage(productMissing = true)

                }

            }

        }

    }



    private fun queryExistingPurchases() {

        if (!billingClient.isReady) return



        val params = QueryPurchasesParams.newBuilder()

            .setProductType(BillingClient.ProductType.INAPP)

            .build()



        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->

            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {

                Log.w(TAG, "Purchase query failed: ${billingResult.debugMessage}")

                return@queryPurchasesAsync

            }

            val hasPremium = purchases.any { purchase ->

                purchase.products.contains(Constants.PREMIUM_PRODUCT_ID) &&

                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED

            }

            if (hasPremium) {

                purchases

                    .filter { it.products.contains(Constants.PREMIUM_PRODUCT_ID) }

                    .forEach(::handlePurchase)

            } else {

                prefs.revokePremium()

            }

            onPremiumStatusChanged?.invoke()

        }

    }



    private fun handlePurchase(purchase: Purchase) {

        if (!purchase.products.contains(Constants.PREMIUM_PRODUCT_ID)) return



        when (purchase.purchaseState) {

            Purchase.PurchaseState.PURCHASED -> {

                if (!purchase.isAcknowledged) {

                    val params = AcknowledgePurchaseParams.newBuilder()

                        .setPurchaseToken(purchase.purchaseToken)

                        .build()

                    billingClient.acknowledgePurchase(params) { result ->

                        if (result.responseCode == BillingClient.BillingResponseCode.OK) {

                            grantPremium(purchase.purchaseToken)

                        }

                    }

                } else {

                    grantPremium(purchase.purchaseToken)

                }

            }

            Purchase.PurchaseState.PENDING -> Unit

            else -> Unit

        }

    }



    private fun grantPremium(token: String) {

        prefs.unlockPremium(token)

        activity.showToast(R.string.premium_enabled)

        onPremiumStatusChanged?.invoke()

    }



    private fun showUnavailableMessage(productMissing: Boolean) {

        when {

            BuildConfig.DEBUG && BuildConfig.APPLICATION_ID.endsWith(".debug") -> {

                activity.showToast(R.string.premium_purchase_play_store_required, android.widget.Toast.LENGTH_LONG)

            }

            productMissing -> {

                activity.showToast(

                    activity.getString(

                        R.string.premium_purchase_product_missing,

                        Constants.PREMIUM_PRODUCT_ID,

                    ),

                    android.widget.Toast.LENGTH_LONG,

                )

            }

            else -> activity.showToast(R.string.premium_purchase_unavailable)

        }

    }



    companion object {

        private const val TAG = "PremiumBillingManager"

    }

}


