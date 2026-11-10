package com.ushareit.core.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.nextai.sharenow.R
import com.ushareit.core.analytics.FirebaseAnalyticsManager
import com.ushareit.core.subscription.BillingManager

object AdMediationManager : Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {

    private const val TAG = "AdMediation"

    // Production AdMob Unit IDs (Cấu hình chính thức từ Google AdMob Console)
    const val ADMOB_APP_ID = "ca-app-pub-4108090245719282~9321607115"
    const val BANNER_ID = "ca-app-pub-4108090245719282/2341265794"
    const val INTERSTITIAL_ID = "ca-app-pub-4108090245719282/2268938590"
    const val NATIVE_ID = "ca-app-pub-4108090245719282/1091588291"
    const val APP_OPEN_ID = "ca-app-pub-4108090245719282/3505998647"

    // Fallback Rewarded ID
    const val REWARDED_ID = "ca-app-pub-4108090245719282/2268938590"

    private var isInitialized = false

    // Interstitial Ad
    private var interstitialAd: InterstitialAd? = null
    private var isLoadingInterstitial = false
    private var interstitialLoadStartTime = 0L

    // Rewarded Ad
    private var rewardedAd: RewardedAd? = null
    private var isLoadingRewarded = false
    private var rewardedLoadStartTime = 0L

    // App Open Ad
    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAppOpen = false
    private var appOpenLoadStartTime = 0L

    // State Tracking
    private var isShowingAppOpenAd = false
    private var isShowingFullScreenAd = false
    private var currentActivity: Activity? = null
    private var isLifecycleRegistered = false
    private var hasShownColdStartAppOpen = false

    fun isInterstitialReady(): Boolean = interstitialAd != null
    fun isAppOpenReady(): Boolean = appOpenAd != null

    fun init(context: Context) {
        if (BillingManager.isVipUser()) {
            Log.i(TAG, "User is VIP -> Ads disabled.")
            return
        }

        // Đăng ký ActivityLifecycleCallbacks và ProcessLifecycleOwner observer
        val app = context.applicationContext as? Application
        if (app != null && !isLifecycleRegistered) {
            isLifecycleRegistered = true
            app.registerActivityLifecycleCallbacks(this)
            try {
                ProcessLifecycleOwner.get().lifecycle.addObserver(this)
                Log.i(TAG, "ProcessLifecycleOwner registered successfully for App Open Ads.")
            } catch (e: Throwable) {
                Log.w(TAG, "ProcessLifecycleOwner setup warning: ${e.message}")
            }
        }

        try {
            // Khởi tạo Google Mobile Ads SDK với cấu hình Live / Production (Key thật 100%)
            MobileAds.initialize(context) { status ->
                Log.i(TAG, "Google Mobile Ads initialized (Production Mode): ${status.adapterStatusMap}")
                isInitialized = true
                loadInterstitial(context.applicationContext)
                loadAppOpen(context.applicationContext)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Ads init fallback: ${e.message}")
        }
    }

    // =========================================================================
    // LIFECYCLE CALLBACKS (Process & Activity - Cứ từ nền mở lên là bật Open App)
    // =========================================================================

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) {
            currentActivity = null
        }
    }

    /**
     * Bắt sự kiện khi app được mở lại từ dưới nền lên foreground (ProcessLifecycleOwner)
     * "cứ từ dưới nền mở app lên là bật open app"
     */
    override fun onStart(owner: LifecycleOwner) {
        Log.i(TAG, "App returned to foreground (ProcessLifecycleOwner.onStart)")
        val activity = currentActivity ?: return
        if (BillingManager.isVipUser() || isShowingAppOpenAd || isShowingFullScreenAd) return

        val actName = activity.javaClass.simpleName
        if (actName.contains("Splash", ignoreCase = true) || actName.contains("Paywall", ignoreCase = true)) {
            return
        }

        showAppOpenIfReady(activity)
    }

    // =========================================================================
    // 1. INTERSTITIAL AD (Quảng cáo toàn màn hình)
    // =========================================================================

    fun loadInterstitial(context: Context) {
        if (BillingManager.isVipUser() || interstitialAd != null || isLoadingInterstitial) return

        isLoadingInterstitial = true
        interstitialLoadStartTime = System.currentTimeMillis()
        FirebaseAnalyticsManager.logAdRequested(INTERSTITIAL_ID, "interstitial")

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            INTERSTITIAL_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    val duration = System.currentTimeMillis() - interstitialLoadStartTime
                    Log.i(TAG, "Interstitial Ad loaded in ${duration}ms")
                    interstitialAd = ad
                    isLoadingInterstitial = false

                    FirebaseAnalyticsManager.logAdLoaded(INTERSTITIAL_ID, "interstitial", duration)

                    ad.setOnPaidEventListener { adValue ->
                        FirebaseAnalyticsManager.logAdPaidEvent(
                            adUnitId = INTERSTITIAL_ID,
                            adFormat = "interstitial",
                            valueMicros = adValue.valueMicros,
                            currencyCode = adValue.currencyCode,
                            adSource = ad.responseInfo.loadedAdapterResponseInfo?.adSourceName ?: "AdMob"
                        )
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.w(TAG, "Interstitial Ad failed to load: ${loadAdError.message} (code: ${loadAdError.code})")
                    interstitialAd = null
                    isLoadingInterstitial = false

                    FirebaseAnalyticsManager.logAdLoadFailed(
                        adUnitId = INTERSTITIAL_ID,
                        adFormat = "interstitial",
                        errorCode = loadAdError.code,
                        errorMessage = loadAdError.message
                    )
                }
            }
        )
    }

    fun showInterstitialIfReady(context: Context, onAdClosed: () -> Unit) {
        if (BillingManager.isVipUser() || isShowingFullScreenAd || isShowingAppOpenAd) {
            onAdClosed()
            return
        }

        val activity = context as? Activity
        val ad = interstitialAd
        if (activity != null && ad != null) {
            isShowingFullScreenAd = true
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    Log.i(TAG, "Interstitial Ad showed full screen")
                    FirebaseAnalyticsManager.logAdImpression(INTERSTITIAL_ID, "interstitial")
                }

                override fun onAdClicked() {
                    Log.i(TAG, "Interstitial Ad clicked")
                    FirebaseAnalyticsManager.logAdClick(INTERSTITIAL_ID, "interstitial")
                }

                override fun onAdDismissedFullScreenContent() {
                    Log.i(TAG, "Interstitial Ad dismissed")
                    FirebaseAnalyticsManager.logAdDismissed(INTERSTITIAL_ID, "interstitial")
                    isShowingFullScreenAd = false
                    interstitialAd = null
                    loadInterstitial(activity.applicationContext)
                    onAdClosed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.w(TAG, "Interstitial Ad failed to show: ${adError.message} (code: ${adError.code})")
                    FirebaseAnalyticsManager.logAdShowFailed(
                        adUnitId = INTERSTITIAL_ID,
                        adFormat = "interstitial",
                        errorCode = adError.code,
                        errorMessage = adError.message
                    )
                    isShowingFullScreenAd = false
                    interstitialAd = null
                    loadInterstitial(activity.applicationContext)
                    onAdClosed()
                }
            }
            ad.show(activity)
        } else {
            loadInterstitial(context.applicationContext)
            onAdClosed()
        }
    }

    // =========================================================================
    // 2. REWARDED AD (Quảng cáo xem video nhận thưởng)
    // =========================================================================

    fun loadRewarded(context: Context) {
        if (BillingManager.isVipUser() || rewardedAd != null || isLoadingRewarded) return

        isLoadingRewarded = true
        rewardedLoadStartTime = System.currentTimeMillis()
        FirebaseAnalyticsManager.logAdRequested(REWARDED_ID, "rewarded")

        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            REWARDED_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    val duration = System.currentTimeMillis() - rewardedLoadStartTime
                    Log.i(TAG, "Rewarded Ad loaded in ${duration}ms")
                    rewardedAd = ad
                    isLoadingRewarded = false

                    FirebaseAnalyticsManager.logAdLoaded(REWARDED_ID, "rewarded", duration)

                    ad.setOnPaidEventListener { adValue ->
                        FirebaseAnalyticsManager.logAdPaidEvent(
                            adUnitId = REWARDED_ID,
                            adFormat = "rewarded",
                            valueMicros = adValue.valueMicros,
                            currencyCode = adValue.currencyCode,
                            adSource = ad.responseInfo.loadedAdapterResponseInfo?.adSourceName ?: "AdMob"
                        )
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.w(TAG, "Rewarded Ad failed to load: ${loadAdError.message}")
                    rewardedAd = null
                    isLoadingRewarded = false

                    FirebaseAnalyticsManager.logAdLoadFailed(
                        adUnitId = REWARDED_ID,
                        adFormat = "rewarded",
                        errorCode = loadAdError.code,
                        errorMessage = loadAdError.message
                    )
                }
            }
        )
    }

    fun showRewardedIfReady(
        activity: Activity,
        onUserEarnedReward: (type: String, amount: Int) -> Unit,
        onAdClosed: () -> Unit
    ) {
        if (BillingManager.isVipUser() || isShowingFullScreenAd || isShowingAppOpenAd) {
            onUserEarnedReward("vip_bypass", 1)
            onAdClosed()
            return
        }

        val ad = rewardedAd
        if (ad != null) {
            isShowingFullScreenAd = true
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    Log.i(TAG, "Rewarded Ad showed")
                    FirebaseAnalyticsManager.logAdImpression(REWARDED_ID, "rewarded")
                }

                override fun onAdClicked() {
                    FirebaseAnalyticsManager.logAdClick(REWARDED_ID, "rewarded")
                }

                override fun onAdDismissedFullScreenContent() {
                    FirebaseAnalyticsManager.logAdDismissed(REWARDED_ID, "rewarded")
                    isShowingFullScreenAd = false
                    rewardedAd = null
                    loadRewarded(activity.applicationContext)
                    onAdClosed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    FirebaseAnalyticsManager.logAdShowFailed(
                        adUnitId = REWARDED_ID,
                        adFormat = "rewarded",
                        errorCode = adError.code,
                        errorMessage = adError.message
                    )
                    isShowingFullScreenAd = false
                    rewardedAd = null
                    loadRewarded(activity.applicationContext)
                    onAdClosed()
                }
            }

            ad.show(activity) { rewardItem ->
                Log.i(TAG, "User earned reward: ${rewardItem.type} x ${rewardItem.amount}")
                FirebaseAnalyticsManager.logAdRewardEarned(REWARDED_ID, rewardItem.type, rewardItem.amount)
                onUserEarnedReward(rewardItem.type, rewardItem.amount)
            }
        } else {
            loadRewarded(activity.applicationContext)
            onAdClosed()
        }
    }

    // =========================================================================
    // 3. APP OPEN AD (Quảng cáo mở ứng dụng: ca-app-pub-4108090245719282/3505998647)
    // =========================================================================

    fun loadAppOpen(context: Context, onLoaded: (() -> Unit)? = null) {
        if (BillingManager.isVipUser() || appOpenAd != null || isLoadingAppOpen) return

        isLoadingAppOpen = true
        appOpenLoadStartTime = System.currentTimeMillis()
        FirebaseAnalyticsManager.logAdRequested(APP_OPEN_ID, "app_open")

        val adRequest = AdRequest.Builder().build()
        AppOpenAd.load(
            context,
            APP_OPEN_ID,
            adRequest,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    val duration = System.currentTimeMillis() - appOpenLoadStartTime
                    Log.i(TAG, "App Open Ad loaded in ${duration}ms")
                    appOpenAd = ad
                    isLoadingAppOpen = false

                    FirebaseAnalyticsManager.logAdLoaded(APP_OPEN_ID, "app_open", duration)

                    ad.setOnPaidEventListener { adValue ->
                        FirebaseAnalyticsManager.logAdPaidEvent(
                            adUnitId = APP_OPEN_ID,
                            adFormat = "app_open",
                            valueMicros = adValue.valueMicros,
                            currencyCode = adValue.currencyCode,
                            adSource = ad.responseInfo.loadedAdapterResponseInfo?.adSourceName ?: "AdMob"
                        )
                    }

                    onLoaded?.invoke()

                    // Bật ads Open App nếu app vừa mở (cold start) và người dùng đã vào MainActivity
                    if (!hasShownColdStartAppOpen) {
                        currentActivity?.let { act ->
                            val actName = act.javaClass.simpleName
                            if (actName.contains("MainActivity", ignoreCase = true)) {
                                hasShownColdStartAppOpen = true
                                showAppOpenIfReady(act)
                            }
                        }
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.w(TAG, "App Open Ad failed to load: ${loadAdError.message}")
                    appOpenAd = null
                    isLoadingAppOpen = false

                    FirebaseAnalyticsManager.logAdLoadFailed(
                        adUnitId = APP_OPEN_ID,
                        adFormat = "app_open",
                        errorCode = loadAdError.code,
                        errorMessage = loadAdError.message
                    )
                }
            }
        )
    }

    fun showAppOpenIfReady(activity: Activity, onAdClosed: () -> Unit = {}) {
        if (BillingManager.isVipUser() || isShowingAppOpenAd || isShowingFullScreenAd) {
            onAdClosed()
            return
        }

        val ad = appOpenAd
        if (ad != null) {
            isShowingAppOpenAd = true
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    Log.i(TAG, "App Open Ad showed full screen")
                    FirebaseAnalyticsManager.logAdImpression(APP_OPEN_ID, "app_open")
                }

                override fun onAdClicked() {
                    FirebaseAnalyticsManager.logAdClick(APP_OPEN_ID, "app_open")
                }

                override fun onAdDismissedFullScreenContent() {
                    Log.i(TAG, "App Open Ad dismissed")
                    FirebaseAnalyticsManager.logAdDismissed(APP_OPEN_ID, "app_open")
                    isShowingAppOpenAd = false
                    appOpenAd = null
                    loadAppOpen(activity.applicationContext)
                    onAdClosed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.w(TAG, "App Open Ad failed to show: ${adError.message}")
                    FirebaseAnalyticsManager.logAdShowFailed(
                        adUnitId = APP_OPEN_ID,
                        adFormat = "app_open",
                        errorCode = adError.code,
                        errorMessage = adError.message
                    )
                    isShowingAppOpenAd = false
                    appOpenAd = null
                    loadAppOpen(activity.applicationContext)
                    onAdClosed()
                }
            }
            ad.show(activity)
        } else {
            loadAppOpen(activity.applicationContext)
            onAdClosed()
        }
    }

    // =========================================================================
    // 4. BANNER AD SETUP (Adaptive Banners & Collapsible Banners)
    // =========================================================================

    fun getAdaptiveBannerAdSize(activity: Activity): AdSize {
        val displayMetrics = activity.resources.displayMetrics
        val adWidthPixels = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val windowMetrics = activity.windowManager.currentWindowMetrics
            windowMetrics.bounds.width()
        } else {
            @Suppress("DEPRECATION")
            displayMetrics.widthPixels
        }
        val density = displayMetrics.density
        val adWidth = (adWidthPixels / density).toInt()
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
    }

    /**
     * Tải và gắn Collapsible Banner Ad (Dạng gập mở) vào ViewGroup container
     * Được áp dụng cho toàn bộ phần Select File theo đúng yêu cầu
     */
    fun loadCollapsibleBanner(
        activity: Activity,
        container: ViewGroup,
        adUnitId: String = BANNER_ID,
        collapsiblePosition: String = "bottom"
    ): AdView? {
        if (BillingManager.isVipUser()) {
            container.visibility = View.GONE
            return null
        }

        return try {
            val adView = AdView(activity)
            adView.adUnitId = adUnitId
            val adSize = getAdaptiveBannerAdSize(activity)
            adView.setAdSize(adSize)

            container.removeAllViews()
            container.addView(adView)
            setupBannerAdView(adView, adUnitId)

            val extras = Bundle().apply {
                putString("collapsible", collapsiblePosition)
            }
            val adRequest = AdRequest.Builder()
                .addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
                .build()
            adView.loadAd(adRequest)
            adView
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load collapsible banner: ${e.message}")
            null
        }
    }

    /**
     * Tải và gắn Adaptive Banner Ad vào một ViewGroup container (FrameLayout / LinearLayout)
     */
    fun loadAdaptiveBanner(
        activity: Activity,
        container: ViewGroup,
        adUnitId: String = BANNER_ID
    ): AdView? {
        if (BillingManager.isVipUser()) {
            container.visibility = View.GONE
            return null
        }

        return try {
            val adView = AdView(activity)
            adView.adUnitId = adUnitId
            val adSize = getAdaptiveBannerAdSize(activity)
            adView.setAdSize(adSize)

            container.removeAllViews()
            container.addView(adView)
            setupBannerAdView(adView, adUnitId)

            val adRequest = AdRequest.Builder().build()
            adView.loadAd(adRequest)
            adView
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load adaptive banner: ${e.message}")
            null
        }
    }

    fun loadBannerAd(adView: AdView, adUnitId: String = BANNER_ID) {
        if (BillingManager.isVipUser()) {
            adView.visibility = View.GONE
            return
        }
        setupBannerAdView(adView, adUnitId)
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
    }

    fun setupBannerAdView(adView: AdView, adUnitId: String = BANNER_ID) {
        if (BillingManager.isVipUser()) {
            adView.visibility = View.GONE
            return
        }

        FirebaseAnalyticsManager.logAdRequested(adUnitId, "banner")

        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                Log.i(TAG, "Banner Ad loaded successfully")
                adView.visibility = View.VISIBLE
                FirebaseAnalyticsManager.logAdLoaded(adUnitId, "banner")
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                Log.w(TAG, "Banner Ad failed to load: ${loadAdError.message}")
                FirebaseAnalyticsManager.logAdLoadFailed(
                    adUnitId = adUnitId,
                    adFormat = "banner",
                    errorCode = loadAdError.code,
                    errorMessage = loadAdError.message
                )
            }

            override fun onAdImpression() {
                Log.i(TAG, "Banner Ad impression logged")
                FirebaseAnalyticsManager.logAdImpression(adUnitId, "banner")
            }

            override fun onAdClicked() {
                Log.i(TAG, "Banner Ad clicked")
                FirebaseAnalyticsManager.logAdClick(adUnitId, "banner")
            }
        }

        adView.setOnPaidEventListener { adValue ->
            FirebaseAnalyticsManager.logAdPaidEvent(
                adUnitId = adUnitId,
                adFormat = "banner",
                valueMicros = adValue.valueMicros,
                currencyCode = adValue.currencyCode,
                adSource = adView.responseInfo?.loadedAdapterResponseInfo?.adSourceName ?: "AdMob"
            )
        }
    }

    // =========================================================================
    // 5. NATIVE AD (Quảng cáo gốc nâng cao: Key ca-app-pub-4108090245719282/1091588291)
    // =========================================================================

    fun loadNativeAd(
        context: Context,
        onLoaded: (NativeAd) -> Unit,
        onFailed: ((LoadAdError) -> Unit)? = null
    ) {
        if (BillingManager.isVipUser()) return

        FirebaseAnalyticsManager.logAdRequested(NATIVE_ID, "native")
        val startTime = System.currentTimeMillis()

        val adLoader = AdLoader.Builder(context, NATIVE_ID)
            .forNativeAd { nativeAd ->
                val duration = System.currentTimeMillis() - startTime
                Log.i(TAG, "Native Ad loaded in ${duration}ms")
                FirebaseAnalyticsManager.logAdLoaded(NATIVE_ID, "native", duration)

                nativeAd.setOnPaidEventListener { adValue ->
                    FirebaseAnalyticsManager.logAdPaidEvent(
                        adUnitId = NATIVE_ID,
                        adFormat = "native",
                        valueMicros = adValue.valueMicros,
                        currencyCode = adValue.currencyCode,
                        adSource = nativeAd.responseInfo?.loadedAdapterResponseInfo?.adSourceName ?: "AdMob"
                    )
                }
                onLoaded(nativeAd)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.w(TAG, "Native Ad failed to load: ${loadAdError.message}")
                    FirebaseAnalyticsManager.logAdLoadFailed(
                        adUnitId = NATIVE_ID,
                        adFormat = "native",
                        errorCode = loadAdError.code,
                        errorMessage = loadAdError.message
                    )
                    onFailed?.invoke(loadAdError)
                }

                override fun onAdImpression() {
                    Log.i(TAG, "Native Ad impression logged")
                    FirebaseAnalyticsManager.logAdImpression(NATIVE_ID, "native")
                }

                override fun onAdClicked() {
                    Log.i(TAG, "Native Ad clicked")
                    FirebaseAnalyticsManager.logAdClick(NATIVE_ID, "native")
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .build()
            )
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
        val headlineView = adView.findViewById<TextView>(R.id.ad_headline)
        headlineView?.text = nativeAd.headline
        adView.headlineView = headlineView

        val bodyView = adView.findViewById<TextView>(R.id.ad_body)
        if (nativeAd.body != null) {
            bodyView?.text = nativeAd.body
            bodyView?.visibility = View.VISIBLE
            adView.bodyView = bodyView
        } else {
            bodyView?.visibility = View.GONE
        }

        val ctaView = adView.findViewById<TextView>(R.id.ad_call_to_action)
        if (nativeAd.callToAction != null) {
            ctaView?.text = nativeAd.callToAction
            ctaView?.visibility = View.VISIBLE
            adView.callToActionView = ctaView
        } else {
            ctaView?.visibility = View.GONE
        }

        val iconView = adView.findViewById<ImageView>(R.id.ad_app_icon)
        val icon = nativeAd.icon
        if (icon?.drawable != null) {
            iconView?.setImageDrawable(icon.drawable)
            iconView?.visibility = View.VISIBLE
            adView.iconView = iconView
        } else {
            iconView?.visibility = View.GONE
        }

        val mediaView = adView.findViewById<MediaView>(R.id.ad_media_view)
        val mediaContent = nativeAd.mediaContent
        if (mediaView != null && mediaContent != null && mediaContent.hasVideoContent()) {
            mediaView.mediaContent = mediaContent
            mediaView.visibility = View.VISIBLE
            adView.mediaView = mediaView
        } else if (mediaView != null && mediaContent != null && mediaContent.aspectRatio > 0f) {
            mediaView.mediaContent = mediaContent
            mediaView.visibility = View.VISIBLE
            adView.mediaView = mediaView
        } else {
            mediaView?.visibility = View.GONE
        }

        adView.setNativeAd(nativeAd)
    }

    fun bindNativeAdToContainer(container: ViewGroup, nativeAd: NativeAd) {
        val inflater = LayoutInflater.from(container.context)
        val adView = inflater.inflate(R.layout.layout_native_ad_card, container, false) as NativeAdView
        populateNativeAdView(nativeAd, adView)
        container.removeAllViews()
        container.addView(adView)
        container.visibility = View.VISIBLE
    }
}
