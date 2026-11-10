package com.ushareit.core.subscription

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.android.billingclient.api.*
import com.tencent.mmkv.MMKV

/**
 * Quản lý Google Play Billing v9 cho Share Now
 * Tích hợp đầy đủ 3 gói cước chính thức:
 * 1. Weekly: Product ID 'weekly', Base Plan 'weekly-plan', Giá $9
 * 2. Monthly: Product ID 'monthlyplan', Base Plan 'monthly-plan', Giá $49
 * 3. Lifetime: Product ID 'lifetimeplan', Base Plan 'lifetime-plan', Giá $99
 */
object BillingManager {

    private const val TAG = "BillingManager"
    private const val KEY_IS_VIP = "pref_key_is_vip_verified_v2"

    // Product IDs & Plan IDs chính thức từ Google Play Console
    const val PRODUCT_WEEKLY = "weekly"
    const val PLAN_WEEKLY = "weekly-plan"
    const val PRICE_WEEKLY = "$9"

    const val PRODUCT_MONTHLY = "monthlyplan"
    const val PLAN_MONTHLY = "monthly-plan"
    const val PRICE_MONTHLY = "$49"

    const val PRODUCT_LIFETIME = "lifetimeplan"
    const val PLAN_LIFETIME = "lifetime-plan"
    const val PRICE_LIFETIME = "$99"

    private var billingClient: BillingClient? = null
    private val productDetailsMap = mutableMapOf<String, ProductDetails>()
    private var isConnected = false

    fun isVipUser(): Boolean {
        return MMKV.defaultMMKV().decodeBool(KEY_IS_VIP, false)
    }

    fun setVipState(isVip: Boolean) {
        MMKV.defaultMMKV().encode(KEY_IS_VIP, isVip)
        Log.i(TAG, "VIP State updated: $isVip")
    }

    fun initBilling(context: Context, onSetupComplete: (() -> Unit)? = null) {
        if (billingClient != null && isConnected) {
            verifyActivePurchases()
            onSetupComplete?.invoke()
            return
        }

        val pendingPurchasesParams = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .build()

        billingClient = BillingClient.newBuilder(context)
            .setListener { billingResult, purchases ->
                handlePurchases(billingResult, purchases)
            }
            .enablePendingPurchases(pendingPurchasesParams)
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.i(TAG, "Google Play Billing Client Connected")
                    isConnected = true
                    verifyActivePurchases()
                    queryAllProducts {
                        onSetupComplete?.invoke()
                    }
                } else {
                    Log.w(TAG, "Billing Setup failed with code: ${billingResult.responseCode} - ${billingResult.debugMessage}")
                    isConnected = false
                    setVipState(false)
                    onSetupComplete?.invoke()
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing Service Disconnected. Will retry on demand.")
                isConnected = false
            }
        })
    }

    private fun verifyActivePurchases() {
        val client = billingClient ?: return
        val subParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        client.queryPurchasesAsync(subParams) { subResult, subPurchases ->
            val hasActiveSub = subResult.responseCode == BillingClient.BillingResponseCode.OK &&
                subPurchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            if (hasActiveSub) {
                setVipState(true)
            } else {
                val inappParams = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
                client.queryPurchasesAsync(inappParams) { inappResult, inappPurchases ->
                    val hasActiveInApp = inappResult.responseCode == BillingClient.BillingResponseCode.OK &&
                        inappPurchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    setVipState(hasActiveInApp)
                }
            }
        }
    }

    private fun queryAllProducts(onFinish: (() -> Unit)? = null) {
        val client = billingClient ?: return

        // 1. Truy vấn các gói Subscriptions (Weekly, Monthly, Lifetime)
        val subProducts = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_WEEKLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_LIFETIME)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val subParams = QueryProductDetailsParams.newBuilder()
            .setProductList(subProducts)
            .build()

        client.queryProductDetailsAsync(subParams) { billingResult, queryResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val detailsList = queryResult.productDetailsList
                if (detailsList != null) {
                    for (details in detailsList) {
                        productDetailsMap[details.productId] = details
                        Log.i(TAG, "Loaded SUB Product: ${details.productId}")
                    }
                }
            } else {
                Log.w(TAG, "Query SUBS failed: ${billingResult.responseCode} - ${billingResult.debugMessage}")
            }

            // 2. Dự phòng truy vấn Lifetime dưới dạng In-App One-time Purchase (nếu Google Play Console cấu hình dạng INAPP)
            if (!productDetailsMap.containsKey(PRODUCT_LIFETIME)) {
                val inappProducts = listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_LIFETIME)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
                val inappParams = QueryProductDetailsParams.newBuilder()
                    .setProductList(inappProducts)
                    .build()

                client.queryProductDetailsAsync(inappParams) { inappResult, inappQueryResult ->
                    if (inappResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        val inappList = inappQueryResult.productDetailsList
                        if (inappList != null) {
                            for (details in inappList) {
                                productDetailsMap[details.productId] = details
                                Log.i(TAG, "Loaded INAPP Product: ${details.productId}")
                            }
                        }
                    }
                    onFinish?.invoke()
                }
            } else {
                onFinish?.invoke()
            }
        }
    }

    fun launchPurchaseFlow(
        activity: Activity,
        productId: String,
        planId: String,
        onSuccess: () -> Unit
    ) {
        val client = billingClient
        val productDetails = productDetailsMap[productId]

        if (client != null && isConnected && productDetails != null) {
            val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)

            // Nếu là Subscription, gán OfferToken tương ứng với Plan ID
            if (productDetails.productType == BillingClient.ProductType.SUBS) {
                val offer = productDetails.subscriptionOfferDetails?.find {
                    it.basePlanId.equals(planId, ignoreCase = true)
                } ?: productDetails.subscriptionOfferDetails?.firstOrNull()

                val offerToken = offer?.offerToken
                if (!offerToken.isNullOrEmpty()) {
                    productDetailsParamsBuilder.setOfferToken(offerToken)
                }
            }

            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productDetailsParamsBuilder.build()))
                .build()

            val response = client.launchBillingFlow(activity, flowParams)
            Log.i(TAG, "LaunchBillingFlow response code: ${response.responseCode}")
        } else {
            Log.w(TAG, "ProductDetails for $productId not loaded yet. Retrying billing setup...")
            initBilling(activity) {
                val retryDetails = productDetailsMap[productId]
                val retryClient = billingClient
                if (retryClient != null && isConnected && retryDetails != null) {
                    activity.runOnUiThread {
                        launchPurchaseFlow(activity, productId, planId, onSuccess)
                    }
                } else {
                    activity.runOnUiThread {
                        Toast.makeText(
                            activity,
                            "Google Play Billing is connecting ($productId). Please sign in to Google Play Store.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun handlePurchases(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    setVipState(true)
                    val prodId = purchase.products.firstOrNull() ?: "unknown_product"
                    com.ushareit.core.analytics.FirebaseAnalyticsManager.logInAppPurchased(
                        productId = prodId,
                        orderId = purchase.orderId,
                        purchaseTime = purchase.purchaseTime
                    )
                    // Xác nhận đơn hàng (Acknowledge) với Google Play
                    if (!purchase.isAcknowledged) {
                        val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                        billingClient?.acknowledgePurchase(acknowledgeParams) { ackResult ->
                            Log.i(TAG, "AcknowledgePurchase code: ${ackResult.responseCode}")
                        }
                    }
                }
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.i(TAG, "User canceled billing flow")
        } else {
            Log.w(TAG, "Billing error: ${billingResult.responseCode} - ${billingResult.debugMessage}")
        }
    }
}
