package com.ushareit.core.analytics

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Firebase Analytics & Crashlytics Manager cho á»©ng dá»¥ng ShareNow.
 * Cung cáº¥p hÆ¡n 30 sá»± kiá»‡n Ä‘o lÆ°á»ng cá»‘t lÃµi vÃ  cÃ¡c tiá»‡n Ã­ch ghi váº¿t lá»—i sá»± cá»‘.
 */
object FirebaseAnalyticsManager {

    private const val TAG = "ShareNow_Analytics"
    private var analytics: FirebaseAnalytics? = null
    private var crashlytics: FirebaseCrashlytics? = null
    private var isInitialized = false

    /**
     * Khá»Ÿi táº¡o Firebase Analytics vÃ  Crashlytics
     */
    fun init(context: Context) {
        if (isInitialized) return

        try {
            analytics = FirebaseAnalytics.getInstance(context.applicationContext)
            crashlytics = FirebaseCrashlytics.getInstance()

            // Cáº¥u hÃ¬nh thÃ´ng tin há»‡ thá»‘ng máº·c Ä‘á»‹nh cho Crashlytics
            crashlytics?.apply {
                setCustomKey("app_package", context.packageName)
                setCustomKey("device_model", "${Build.MANUFACTURER} ${Build.MODEL}")
                setCustomKey("android_os", Build.VERSION.SDK_INT)
            }

            isInitialized = true
            Log.i(TAG, "Firebase Analytics & Crashlytics initialized successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase Analytics / Crashlytics: ${e.message}", e)
        }
    }

    // =========================================================================
    // CRASHLYTICS UTILITIES (Ghi váº¿t & BÃ¡o cÃ¡o ngoáº¡i lá»‡)
    // =========================================================================

    /**
     * Ghi láº¡i má»™t máº©u tin nháº­t kÃ½ Ä‘iá»u hÆ°á»›ng (Breadcrumb log) vÃ o Crashlytics
     */
    fun log(message: String) {
        Log.d(TAG, "[Crashlytics Log] $message")
        try {
            crashlytics?.log(message)
        } catch (e: Exception) {
            Log.w(TAG, "Could not send log to Crashlytics: ${e.message}")
        }
    }

    /**
     * Ghi nháº­n lá»—i phi cháº¿t ngÆ°á»i (Non-fatal Exception) vá» Crashlytics
     */
    fun recordException(throwable: Throwable, reason: String? = null) {
        Log.e(TAG, "[Crashlytics Exception] reason=$reason, error=${throwable.message}", throwable)
        try {
            reason?.let { crashlytics?.log("Reason: $it") }
            crashlytics?.recordException(throwable)
        } catch (e: Exception) {
            Log.w(TAG, "Could not record exception: ${e.message}")
        }
    }

    /**
     * Äáº·t ID Ä‘á»‹nh danh ngÆ°á»i dÃ¹ng (User ID) trÃªn cáº£ Analytics vÃ  Crashlytics
     */
    fun setUserId(userId: String) {
        try {
            analytics?.setUserId(userId)
            crashlytics?.setUserId(userId)
            Log.d(TAG, "User ID set: $userId")
        } catch (e: Exception) {
            Log.w(TAG, "Could not set user ID: ${e.message}")
        }
    }

    /**
     * Gáº¯n thÃªm thuá»™c tÃ­nh tÃ¹y biáº¿n vÃ o Crashlytics
     */
    fun setCustomCrashKey(key: String, value: String) {
        crashlytics?.setCustomKey(key, value)
    }

    fun setCustomCrashKey(key: String, value: Long) {
        crashlytics?.setCustomKey(key, value)
    }

    fun setCustomCrashKey(key: String, value: Boolean) {
        crashlytics?.setCustomKey(key, value)
    }

    // =========================================================================
    // HÃ€M Báº®T Sá»° KIá»†N GENERIC
    // =========================================================================

    private fun logEvent(eventName: String, params: Bundle? = null) {
        try {
            analytics?.logEvent(eventName, params)
            crashlytics?.log("Analytics Event: $eventName")
            Log.d(TAG, "Logged event: $eventName | params: $params")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to log event $eventName: ${e.message}")
        }
    }

    // =========================================================================
    // 1. NHÃ“M VÃ’NG Äá»œI & Há»† THá»NG (APP LIFECYCLE & SYSTEM EVENTS - 4 events)
    // =========================================================================

    /** 1. Ghi nháº­n khi má»Ÿ á»©ng dá»¥ng vá»›i thÃ´ng sá»‘ phiÃªn báº£n */
    fun logAppOpen(appVersion: String, deviceLocale: String) {
        val bundle = Bundle().apply {
            putString("app_version", appVersion)
            putString("device_locale", deviceLocale)
            putLong("timestamp", System.currentTimeMillis())
        }
        logEvent("app_open_custom", bundle)
    }

    /** 2. Ghi nháº­n khi phÃ¡t hiá»‡n app Ä‘Æ°á»£c cáº­p nháº­t tá»« báº£n cÅ© */
    fun logAppUpdateDetected(oldVersion: String, newVersion: String) {
        val bundle = Bundle().apply {
            putString("old_version", oldVersion)
            putString("new_version", newVersion)
        }
        logEvent("app_update_detected", bundle)
    }

    /** 3. Báº¯t Ä‘áº§u phiÃªn lÃ m viá»‡c má»›i */
    fun logSessionStart() {
        val bundle = Bundle().apply {
            putLong("start_time", System.currentTimeMillis())
        }
        logEvent("session_start_custom", bundle)
    }

    /** 4. Káº¿t quáº£ xin cáº¥p quyá»n há»‡ thá»‘ng (Storage, Location, Nearby Devices, ...) */
    fun logPermissionResult(permission: String, isGranted: Boolean) {
        val bundle = Bundle().apply {
            putString("permission_name", permission)
            putBoolean("is_granted", isGranted)
        }
        logEvent("permission_result", bundle)
    }

    // =========================================================================
    // 2. NHÃ“M TRUYá»€N Táº¢I Tá»†P P2P (CORE TRANSFER P2P - 12 events)
    // =========================================================================

    /** 5. Báº¥m nÃºt 'Gá»­i' táº¡i mÃ n hÃ¬nh chÃ­nh */
    fun logTransferSendClick(sourceScreen: String = "home_main") {
        val bundle = Bundle().apply {
            putString("source_screen", sourceScreen)
        }
        logEvent("transfer_send_click", bundle)
    }

    /** 6. Báº¥m nÃºt 'Nháº­n' táº¡i mÃ n hÃ¬nh chÃ­nh */
    fun logTransferReceiveClick(sourceScreen: String = "home_main") {
        val bundle = Bundle().apply {
            putString("source_screen", sourceScreen)
        }
        logEvent("transfer_receive_click", bundle)
    }

    /** 7. Chá»n cháº¿ Ä‘á»™ truyá»n P2P (Wi-Fi Direct, Local Hotspot, WebShare) */
    fun logP2PModeSelected(mode: String) {
        val bundle = Bundle().apply {
            putString("transfer_mode", mode)
        }
        logEvent("p2p_mode_selected", bundle)
    }

    /** 8. TÃ¬m tháº¥y thiáº¿t bá»‹ Ä‘á»‘i tÃ¡c qua radar / mDNS */
    fun logDeviceDiscovered(deviceType: String = "android", signalStrength: Int = 0) {
        val bundle = Bundle().apply {
            putString("device_type", deviceType)
            putInt("signal_strength", signalStrength)
        }
        logEvent("p2p_device_discovered", bundle)
    }

    /** 9. Báº¯t Ä‘áº§u báº¯t tay káº¿t ná»‘i tá»›i thiáº¿t bá»‹ Ä‘á»‘i tÃ¡c */
    fun logP2PConnectionStart(targetDeviceName: String, mode: String) {
        val bundle = Bundle().apply {
            putString("target_device", targetDeviceName)
            putString("mode", mode)
        }
        logEvent("p2p_connection_start", bundle)
    }

    /** 10. Káº¿t ná»‘i P2P thÃ nh cÃ´ng */
    fun logP2PConnected(targetDeviceName: String, mode: String, durationMs: Long) {
        val bundle = Bundle().apply {
            putString("target_device", targetDeviceName)
            putString("mode", mode)
            putLong("handshake_duration_ms", durationMs)
        }
        logEvent("p2p_connected", bundle)
    }

    /** 11. Báº¯t tay káº¿t ná»‘i P2P tháº¥t báº¡i */
    fun logP2PConnectFailed(targetDeviceName: String, reason: String, errorCode: Int = 0) {
        val bundle = Bundle().apply {
            putString("target_device", targetDeviceName)
            putString("error_reason", reason)
            putInt("error_code", errorCode)
        }
        logEvent("p2p_connect_failed", bundle)
        recordException(Exception("P2P Connection Failed: $reason (code: $errorCode)"))
    }

    /** 12. Báº¯t Ä‘áº§u phiÃªn gá»­i nháº­n dá»¯ liá»‡u */
    fun logTransferSessionStart(totalFiles: Int, totalSizeBytes: Long) {
        val bundle = Bundle().apply {
            putInt("total_files", totalFiles)
            putLong("total_size_bytes", totalSizeBytes)
            putDouble("total_size_mb", totalSizeBytes / (1024.0 * 1024.0))
        }
        logEvent("transfer_session_start", bundle)
    }

    /** 13. Truyá»n thÃ nh cÃ´ng 1 tá»‡p tin */
    fun logTransferFileSuccess(fileName: String, fileExtension: String, fileSizeBytes: Long, durationMs: Long, speedMBps: Double) {
        val bundle = Bundle().apply {
            putString("file_name", fileName)
            putString("file_ext", fileExtension.lowercase())
            putLong("file_size_bytes", fileSizeBytes)
            putLong("duration_ms", durationMs)
            putDouble("speed_mbps", speedMBps)
        }
        logEvent("transfer_file_success", bundle)
    }

    /** 14. Truyá»n 1 tá»‡p tin tháº¥t báº¡i */
    fun logTransferFileFailed(fileName: String, fileSizeBytes: Long, errorReason: String) {
        val bundle = Bundle().apply {
            putString("file_name", fileName)
            putLong("file_size_bytes", fileSizeBytes)
            putString("error_reason", errorReason)
        }
        logEvent("transfer_file_failed", bundle)
    }

    /** 15. HoÃ n táº¥t toÃ n bá»™ phiÃªn truyá»n tá»‡p */
    fun logTransferSessionCompleted(totalFiles: Int, totalSizeBytes: Long, totalDurationMs: Long, avgSpeedMBps: Double) {
        val bundle = Bundle().apply {
            putInt("total_files", totalFiles)
            putLong("total_size_bytes", totalSizeBytes)
            putLong("total_duration_ms", totalDurationMs)
            putDouble("avg_speed_mbps", avgSpeedMBps)
        }
        logEvent("transfer_session_completed", bundle)
    }

    /** 16. Há»§y bá» phiÃªn truyá»n táº£i giá»¯a chá»«ng */
    fun logTransferCancelled(filesRemaining: Int, bytesRemaining: Long) {
        val bundle = Bundle().apply {
            putInt("files_remaining", filesRemaining)
            putLong("bytes_remaining", bytesRemaining)
        }
        logEvent("transfer_cancelled", bundle)
    }

    // =========================================================================
    // 3. NHÃ“M QUáº¢N LÃ Tá»†P TIN & Dá»ŒN RÃC (FILE MANAGER & CLEANIT - 6 events)
    // =========================================================================

    /** 17. Má»Ÿ xem danh má»¥c tá»‡p (Apps, Photos, Videos, Music, Documents, Zips) */
    fun logFileCategoryBrowse(category: String, totalItems: Int) {
        val bundle = Bundle().apply {
            putString("category_name", category)
            putInt("total_items", totalItems)
        }
        logEvent("file_browse_category", bundle)
    }

    /** 18. Thao tÃ¡c vá»›i tá»‡p tin (Open, Delete, Share External, Rename) */
    fun logFileAction(action: String, fileType: String, fileSizeBytes: Long) {
        val bundle = Bundle().apply {
            putString("action_type", action)
            putString("file_type", fileType)
            putLong("file_size_bytes", fileSizeBytes)
        }
        logEvent("file_action_executed", bundle)
    }

    /** 19. Báº¯t Ä‘áº§u quÃ©t rÃ¡c há»‡ thá»‘ng Cleanit */
    fun logCleanScanStart() {
        logEvent("clean_scan_start")
    }

    /** 20. QuÃ©t rÃ¡c hoÃ n táº¥t */
    fun logCleanScanCompleted(junkSizeBytes: Long, junkCategoriesCount: Int) {
        val bundle = Bundle().apply {
            putLong("junk_size_bytes", junkSizeBytes)
            putDouble("junk_size_mb", junkSizeBytes / (1024.0 * 1024.0))
            putInt("categories_count", junkCategoriesCount)
        }
        logEvent("clean_scan_completed", bundle)
    }

    /** 21. Thá»±c hiá»‡n dá»n rÃ¡c thÃ nh cÃ´ng */
    fun logCleanJunkExecuted(cleanedSizeBytes: Long, durationMs: Long) {
        val bundle = Bundle().apply {
            putLong("cleaned_size_bytes", cleanedSizeBytes)
            putDouble("cleaned_size_mb", cleanedSizeBytes / (1024.0 * 1024.0))
            putLong("duration_ms", durationMs)
        }
        logEvent("clean_junk_executed", bundle)
    }

    /** 22. Cáº£nh bÃ¡o dung lÆ°á»£ng bá»™ nhá»› mÃ¡y xuá»‘ng má»©c tháº¥p */
    fun logStorageWarning(freeStorageBytes: Long, totalStorageBytes: Long, freePercent: Float) {
        val bundle = Bundle().apply {
            putLong("free_storage_bytes", freeStorageBytes)
            putLong("total_storage_bytes", totalStorageBytes)
            putFloat("free_percent", freePercent)
        }
        logEvent("storage_low_warning", bundle)
    }

    // =========================================================================
    // 4. NHÃ“M XU THÆ¯á»žNG, WEBSHARE & TIá»†N ÃCH (COIN REWARDS & WEBSHARE - 5 events)
    // =========================================================================

    /** 23. Má»Ÿ xem mÃ n hÃ¬nh tÃ­ch lÅ©y Xu thÆ°á»Ÿng / Cá»­a hÃ ng Ä‘á»•i quÃ  */
    fun logCoinRewardView(currentCoins: Int) {
        val bundle = Bundle().apply {
            putInt("current_coins", currentCoins)
        }
        logEvent("coin_reward_view", bundle)
    }

    /** 24. HoÃ n thÃ nh nhiá»‡m vá»¥ nháº­n Xu (chuyá»ƒn file, dá»n rÃ¡c, Ä‘iá»ƒm danh) */
    fun logCoinTaskCompleted(taskId: String, coinsEarned: Int) {
        val bundle = Bundle().apply {
            putString("task_id", taskId)
            putInt("coins_earned", coinsEarned)
        }
        logEvent("coin_task_completed", bundle)
    }

    /** 25. Äá»•i Xu láº¥y pháº§n thÆ°á»Ÿng / ngÃ y dÃ¹ng VIP */
    fun logCoinRewardRedeemed(rewardId: String, coinsSpent: Int) {
        val bundle = Bundle().apply {
            putString("reward_id", rewardId)
            putInt("coins_spent", coinsSpent)
        }
        logEvent("coin_reward_redeemed", bundle)
    }

    /** 25b. Báº­t dá»‹ch vá»¥ WebShare phÃ¡t mÃ¡y chá»§ ná»™i bá»™ káº¿t ná»‘i PC qua trÃ¬nh duyá»‡t */
    fun logWebShareServerStarted(port: Int = 8888) {
        val bundle = Bundle().apply {
            putInt("server_port", port)
        }
        logEvent("webshare_server_started", bundle)
    }

    /** 25c. TrÃ¬nh duyá»‡t mÃ¡y tÃ­nh PC káº¿t ná»‘i thÃ nh cÃ´ng vÃ o WebShare */
    fun logWebShareClientConnected(clientIp: String) {
        val bundle = Bundle().apply {
            putString("client_ip", clientIp)
        }
        logEvent("webshare_client_connected", bundle)
    }

    // =========================================================================
    // 5. NHÃ“M TRÃŒNH PHÃT ÄA PHÆ¯Æ NG TIá»†N (MEDIA PLAYER - 2 events)
    // =========================================================================

    /** 26. Báº¯t Ä‘áº§u phÃ¡t bÃ i hÃ¡t / video */
    fun logMediaPlay(mediaType: String, format: String, durationSec: Long) {
        val bundle = Bundle().apply {
            putString("media_type", mediaType) // "audio", "video"
            putString("format", format)
            putLong("duration_sec", durationSec)
        }
        logEvent("media_play", bundle)
    }

    /** 27. HoÃ n táº¥t phÃ¡t háº¿t bÃ i hÃ¡t / video */
    fun logMediaCompleted(mediaType: String, durationSec: Long) {
        val bundle = Bundle().apply {
            putString("media_type", mediaType)
            putLong("duration_sec", durationSec)
        }
        logEvent("media_completed", bundle)
    }

    // =========================================================================
    // 6. NHÃ“M QUáº¢NG CÃO ADMOB & DOANH THU (ADMOB ADS & REVENUE TRACKING)
    // =========================================================================

    /**
     * Sá»± kiá»‡n doanh thu quáº£ng cÃ¡o AdMob chÃ­nh thá»©c (Ad Revenue / Paid Event)
     * ÄÃ¢y lÃ  event chuáº©n cá»§a Firebase (FirebaseAnalytics.Event.AD_IMPRESSION)
     * Tá»± Ä‘á»™ng Ä‘á»“ng bá»™ vá»›i Google Ads & Firebase Dashboard Ä‘á»ƒ Ä‘o lÆ°á»ng tROAS vÃ  LTV.
     */
    fun logAdPaidEvent(
        adUnitId: String,
        adFormat: String,
        valueMicros: Long,
        currencyCode: String,
        adSource: String = "AdMob",
        network: String = "admob"
    ) {
        val value = valueMicros / 1_000_000.0
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.AD_PLATFORM, "admob")
            putString(FirebaseAnalytics.Param.AD_SOURCE, adSource)
            putString(FirebaseAnalytics.Param.AD_UNIT_NAME, adUnitId)
            putString(FirebaseAnalytics.Param.AD_FORMAT, adFormat)
            putDouble(FirebaseAnalytics.Param.VALUE, value)
            putString(FirebaseAnalytics.Param.CURRENCY, currencyCode)
            putString("network", network)
        }
        // 1. Ghi nháº­n event chuáº©n há»‡ thá»‘ng Firebase
        logEvent(FirebaseAnalytics.Event.AD_IMPRESSION, bundle)
        // 2. Ghi nháº­n event tÃ¹y biáº¿n Ä‘á»ƒ theo dÃµi chi tiáº¿t trÃªn DebugView
        val customBundle = Bundle(bundle).apply {
            putLong("value_micros", valueMicros)
        }
        logEvent("ad_revenue_tracked", customBundle)
        Log.i(TAG, "ðŸ’° Ad Revenue Tracked: format=$adFormat, value=$value $currencyCode, unit=$adUnitId")
    }

    /** Ghi nháº­n khi gá»­i yÃªu cáº§u táº£i quáº£ng cÃ¡o lÃªn AdMob (Ad Request Started) */
    fun logAdRequested(adUnitId: String, adFormat: String) {
        val bundle = Bundle().apply {
            putString("ad_unit_id", adUnitId)
            putString("ad_format", adFormat)
            putLong("timestamp", System.currentTimeMillis())
        }
        logEvent("ad_request_started", bundle)
    }

    /** Ghi nháº­n khi quáº£ng cÃ¡o táº£i thÃ nh cÃ´ng vÃ  sáºµn sÃ ng hiá»ƒn thá»‹ (Ad Loaded) */
    fun logAdLoaded(adUnitId: String, adFormat: String, durationMs: Long = 0, adNetwork: String = "admob") {
        val bundle = Bundle().apply {
            putString("ad_unit_id", adUnitId)
            putString("ad_format", adFormat)
            putLong("load_duration_ms", durationMs)
            putString("ad_network", adNetwork)
        }
        logEvent("ad_load_success", bundle)
    }

    /** Ghi nháº­n khi táº£i quáº£ng cÃ¡o tháº¥t báº¡i (Ad Load Failed) */
    fun logAdLoadFailed(adUnitId: String, adFormat: String, errorCode: Int, errorMessage: String) {
        val bundle = Bundle().apply {
            putString("ad_unit_id", adUnitId)
            putString("ad_format", adFormat)
            putInt("error_code", errorCode)
            putString("error_message", errorMessage)
        }
        logEvent("ad_load_failed", bundle)
    }

    /** 28. Hiá»ƒn thá»‹ quáº£ng cÃ¡o thÃ nh cÃ´ng trÃªn mÃ n hÃ¬nh (Ad Impression) */
    fun logAdImpression(adUnitId: String, adFormat: String, network: String = "admob") {
        val bundle = Bundle().apply {
            putString("ad_unit_id", adUnitId)
            putString("ad_format", adFormat) // "banner", "interstitial", "rewarded", "app_open", "native"
            putString("network", network)
        }
        logEvent("ad_impression_tracked", bundle)
    }

    /** 29. Nháº¥p vÃ o quáº£ng cÃ¡o (Ad Clicked) */
    fun logAdClick(adUnitId: String, adFormat: String) {
        val bundle = Bundle().apply {
            putString("ad_unit_id", adUnitId)
            putString("ad_format", adFormat)
        }
        logEvent("ad_click_tracked", bundle)
    }

    /** Ghi nháº­n khi quáº£ng cÃ¡o má»Ÿ toÃ n mÃ n hÃ¬nh tháº¥t báº¡i (Ad Show Failed) */
    fun logAdShowFailed(adUnitId: String, adFormat: String, errorCode: Int, errorMessage: String) {
        val bundle = Bundle().apply {
            putString("ad_unit_id", adUnitId)
            putString("ad_format", adFormat)
            putInt("error_code", errorCode)
            putString("error_message", errorMessage)
        }
        logEvent("ad_show_failed", bundle)
    }

    /** Ghi nháº­n khi ngÆ°á»i dÃ¹ng Ä‘Ã³ng quáº£ng cÃ¡o toÃ n mÃ n hÃ¬nh (Ad Dismissed) */
    fun logAdDismissed(adUnitId: String, adFormat: String) {
        val bundle = Bundle().apply {
            putString("ad_unit_id", adUnitId)
            putString("ad_format", adFormat)
        }
        logEvent("ad_dismissed", bundle)
    }

    /** Ghi nháº­n khi hoÃ n thÃ nh xem quáº£ng cÃ¡o nháº­n thÆ°á»Ÿng (Rewarded Ad Earned) */
    fun logAdRewardEarned(adUnitId: String, rewardType: String, amount: Int) {
        val bundle = Bundle().apply {
            putString("ad_unit_id", adUnitId)
            putString("reward_type", rewardType)
            putInt("reward_amount", amount)
        }
        logEvent("ad_reward_earned", bundle)
    }

    /** 30. Xem mÃ n hÃ¬nh mua gÃ³i VIP / Gá»¡ quáº£ng cÃ¡o */
    fun logPaywallView(fromTrigger: String) {
        val bundle = Bundle().apply {
            putString("trigger_point", fromTrigger)
        }
        logEvent("paywall_view", bundle)
    }

    /** 31. Mua thÃ nh cÃ´ng gÃ³i Ä‘Äƒng kÃ½ VIP */

    /** Ghi nhận khi người dùng bấm vào card 'Gửi' (Send) tại màn hình chính */
    fun logCardSendClick(sourceScreen: String = "home_main") {
        val bundle = Bundle().apply {
            putString("card_name", "send")
            putString("source_screen", sourceScreen)
            putLong("timestamp", System.currentTimeMillis())
        }
        logEvent("click_card_send", bundle)
        logEvent("transfer_send_click", bundle)
        Log.i(TAG, "📤 Click Card Send Tracked")
    }

    /** Ghi nhận khi người dùng bấm vào card 'Nhận' (Receive) tại màn hình chính */
    fun logCardReceiveClick(sourceScreen: String = "home_main") {
        val bundle = Bundle().apply {
            putString("card_name", "receive")
            putString("source_screen", sourceScreen)
            putLong("timestamp", System.currentTimeMillis())
        }
        logEvent("click_card_receive", bundle)
        logEvent("transfer_receive_click", bundle)
        Log.i(TAG, "📥 Click Card Receive Tracked")
    }

    /** Ghi nhận khi người dùng hoàn tất thanh toán In-app (In-app purchased) */
    fun logInAppPurchased(
        productId: String,
        orderId: String? = null,
        price: String? = null,
        currency: String = "USD",
        purchaseTime: Long = System.currentTimeMillis()
    ) {
        val bundle = Bundle().apply {
            putString("product_id", productId)
            orderId?.let { putString("order_id", it) }
            price?.let { putString("price", it) }
            putString("currency", currency)
            putLong("purchase_time", purchaseTime)
            putBoolean("is_success", true)
        }
        logEvent("in_app_purchased", bundle)
        logEvent("inapp_purchased", bundle)
        val fbBundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.ITEM_ID, productId)
            putString(FirebaseAnalytics.Param.CURRENCY, currency)
        }
        logEvent(FirebaseAnalytics.Event.PURCHASE, fbBundle)
        Log.i(TAG, "💳 In-App Purchased Tracked: product=$productId, order=$orderId, price=$price")
    }

    // =========================================================================
    // 9. NHÓM TRÌNH DUYỆT & TẢI VIDEO (BROWSER & VIDEO DOWNLOADER TRACKING)
    // =========================================================================

    /** Ghi nhận thao tác trên thanh Top Bar của Browser (home, url_focus, url_clear, open_tabs, open_downloads...) */
    fun logBrowserTopBarAction(action: String, currentUrl: String = "") {
        val bundle = Bundle().apply {
            putString("action", action)
            if (currentUrl.isNotEmpty()) putString("current_url", currentUrl.take(100))
            putLong("timestamp", System.currentTimeMillis())
        }
        logEvent("browser_topbar_action", bundle)
        logButtonClick("browser_topbar_$action", "BrowserTab", currentUrl.take(80))
    }

    /** Ghi nhận khi người dùng nhập URL hoặc tìm kiếm trên Browser */
    fun logBrowserUrlLoad(
        url: String,
        host: String,
        isSearchQuery: Boolean,
        source: String,
        isMonetized: Boolean
    ) {
        val bundle = Bundle().apply {
            putString("url", url.take(100))
            putString("host", host.take(60))
            putBoolean("is_search_query", isSearchQuery)
            putString("source", source) // "address_bar", "quick_site", "paste_detect", "tab_switch"
            putBoolean("is_monetized_site", isMonetized)
            putLong("timestamp", System.currentTimeMillis())
        }
        logEvent("browser_url_load", bundle)
        if (isSearchQuery) {
            val searchBundle = Bundle().apply {
                putString(FirebaseAnalytics.Param.SEARCH_TERM, url.substringAfter("q=").substringBefore("&").take(80))
            }
            logEvent(FirebaseAnalytics.Event.SEARCH, searchBundle)
        }
    }

    /** Ghi nhận khi trang web trong Browser tải hoàn tất */
    fun logBrowserPageLoaded(url: String, host: String, title: String, isMonetized: Boolean) {
        val bundle = Bundle().apply {
            putString("url", url.take(100))
            putString("host", host.take(60))
            putString("page_title", title.take(80))
            putBoolean("is_monetized_site", isMonetized)
        }
        logEvent("browser_page_loaded", bundle)
    }

    /** Ghi nhận khi bấm vào các icon trang web nhanh (TikTok, YouTube, Facebook, Instagram...) */
    fun logBrowserQuickSiteClick(siteName: String, url: String) {
        val bundle = Bundle().apply {
            putString("site_name", siteName)
            putString("url", url)
            putLong("timestamp", System.currentTimeMillis())
        }
        logEvent("browser_quick_site_click", bundle)
        logButtonClick("browser_site_$siteName", "BrowserTab", url)
    }

    /** Ghi nhận khi bấm nút Paste Link & Detect Video */
    fun logBrowserPasteDetectClick(hasClipboardUrl: Boolean, host: String = "") {
        val bundle = Bundle().apply {
            putBoolean("has_clipboard_url", hasClipboardUrl)
            if (host.isNotEmpty()) putString("host", host.take(60))
            putLong("timestamp", System.currentTimeMillis())
        }
        logEvent("browser_paste_detect_click", bundle)
        logButtonClick("browser_paste_detect", "BrowserTab", host)
    }

    /** Ghi nhận các thao tác quản lý Tab trong Browser (open_sheet, new_tab, switch_tab, close_tab, close_all_tabs) */
    fun logBrowserTabAction(action: String, tabsCount: Int, tabUrl: String = "") {
        val bundle = Bundle().apply {
            putString("action", action)
            putInt("tabs_count", tabsCount)
            if (tabUrl.isNotEmpty()) putString("tab_url", tabUrl.take(100))
        }
        logEvent("browser_tab_action", bundle)
        logButtonClick("browser_tab_$action", "BrowserTabsSheet", "count=$tabsCount")
    }

    /** Ghi nhận khi người dùng bấm vào nút nổi (Bubble) Download */
    fun logBrowserBubbleClick(hasDetectedVideo: Boolean, detectedCount: Int, host: String) {
        val bundle = Bundle().apply {
            putBoolean("has_detected_video", hasDetectedVideo)
            putInt("detected_count", detectedCount)
            putString("host", host.take(60))
            putLong("timestamp", System.currentTimeMillis())
        }
        logEvent("browser_sniffer_bubble_click", bundle)
        logButtonClick("browser_bubble_sniffer", "BrowserTab", "ready=$hasDetectedVideo,count=$detectedCount")
    }

    /** Ghi nhận khi hệ thống Sniffer phát hiện được video mới trên trang web */
    fun logBrowserVideoDetected(
        host: String,
        format: String,
        qualityLabel: String,
        detectedCount: Int,
        isMonetized: Boolean
    ) {
        val bundle = Bundle().apply {
            putString("host", host.take(60))
            putString("format", format)
            putString("quality_label", qualityLabel.take(60))
            putInt("detected_count", detectedCount)
            putBoolean("is_monetized_site", isMonetized)
        }
        logEvent("browser_video_detected", bundle)
    }

    /** Ghi nhận các thao tác trong bảng chọn Video Detected (open_modal, select_stream, preview_stream, confirm_download) */
    fun logBrowserDetectedModalAction(
        action: String,
        host: String,
        qualityLabel: String = "",
        format: String = "",
        sizeBytes: Long = 0L,
        isLikelyAd: Boolean = false
    ) {
        val bundle = Bundle().apply {
            putString("action", action)
            putString("host", host.take(60))
            if (qualityLabel.isNotEmpty()) putString("quality_label", qualityLabel.take(60))
            if (format.isNotEmpty()) putString("format", format)
            putLong("size_bytes", sizeBytes)
            putBoolean("is_likely_ad", isLikelyAd)
        }
        logEvent("browser_detected_modal_action", bundle)
        logButtonClick("browser_modal_$action", "BrowserDetectedSheet", "$qualityLabel ($format)")
    }

    /** Ghi nhận khi bắt đầu tải một video xuống từ Browser */
    fun logBrowserDownloadStart(
        taskId: String,
        title: String,
        host: String,
        qualityLabel: String,
        format: String,
        isMonetized: Boolean
    ) {
        val bundle = Bundle().apply {
            putString("task_id", taskId)
            putString("video_title", title.take(80))
            putString("host", host.take(60))
            putString("quality_label", qualityLabel.take(60))
            putString("format", format)
            putBoolean("is_monetized", isMonetized)
            putLong("timestamp", System.currentTimeMillis())
        }
        logEvent("browser_download_start", bundle)
    }

    /** Ghi nhận khi tải video hoàn tất 100% */
    fun logBrowserDownloadCompleted(
        taskId: String,
        title: String,
        format: String,
        qualityLabel: String,
        sizeBytes: Long,
        durationMs: Long,
        isMonetized: Boolean
    ) {
        val bundle = Bundle().apply {
            putString("task_id", taskId)
            putString("video_title", title.take(80))
            putString("format", format)
            putString("quality_label", qualityLabel.take(60))
            putLong("size_bytes", sizeBytes)
            putDouble("size_mb", sizeBytes / (1024.0 * 1024.0))
            putLong("duration_ms", durationMs)
            putBoolean("is_monetized", isMonetized)
        }
        logEvent("browser_download_completed", bundle)
    }

    /** Ghi nhận khi tải video thất bại */
    fun logBrowserDownloadFailed(
        taskId: String,
        title: String,
        format: String,
        errorReason: String,
        isMonetized: Boolean
    ) {
        val bundle = Bundle().apply {
            putString("task_id", taskId)
            putString("video_title", title.take(80))
            putString("format", format)
            putString("error_reason", errorReason.take(90))
            putBoolean("is_monetized", isMonetized)
        }
        logEvent("browser_download_failed", bundle)
    }

    /** Ghi nhận các thao tác trong mục Quản lý Video Download (pause, resume, retry, delete, play, save_to_gallery...) */
    fun logBrowserDownloadTaskAction(
        action: String,
        taskTitle: String = "",
        status: String = "",
        extraInfo: String = ""
    ) {
        val bundle = Bundle().apply {
            putString("action", action)
            if (taskTitle.isNotEmpty()) putString("video_title", taskTitle.take(80))
            if (status.isNotEmpty()) putString("task_status", status)
            if (extraInfo.isNotEmpty()) putString("extra_info", extraInfo.take(80))
            putLong("timestamp", System.currentTimeMillis())
        }
        logEvent("browser_download_task_action", bundle)
        logButtonClick("browser_dl_$action", "BrowserDownloadsSheet", taskTitle.take(60))
    }

    /** Ghi nhận khi mở xem video từ danh sách web giới hạn (3 video đầu miễn phí) */
    fun logBrowserMonetizedVideoOpen(fileName: String, openedCount: Int, remainingFree: Int) {
        val bundle = Bundle().apply {
            putString("file_name", fileName.take(80))
            putInt("opened_count", openedCount)
            putInt("remaining_free", remainingFree)
        }
        logEvent("browser_monetized_video_open", bundle)
    }

    /** Ghi nhận khi chạm giới hạn video thứ 4 từ web monetized và bật màn hình Paywall */
    fun logBrowserMonetizedPaywallShow(fileName: String, openedCount: Int) {
        val bundle = Bundle().apply {
            putString("file_name", fileName.take(80))
            putInt("opened_count", openedCount)
            putString("trigger_reason", "monetized_limit_reached")
        }
        logEvent("browser_monetized_paywall_show", bundle)
        logPaywallView("browser_monetized_video_limit")
    }

    /** Ghi nhận khi trình duyệt chặn popup/redirect quảng cáo độc hại */
    fun logBrowserAdBlocked(adUrlOrHost: String, blockType: String = "redirect_popup") {
        val bundle = Bundle().apply {
            putString("ad_url_or_host", adUrlOrHost.take(80))
            putString("block_type", blockType)
        }
        logEvent("browser_ad_blocked", bundle)
    }

    fun logSubscriptionPurchaseSuccess(planId: String, price: Double, currency: String) {
        val bundle = Bundle().apply {
            putString("plan_id", planId)
            putDouble("price", price)
            putString("currency", currency)
        }
        logEvent("subscription_purchase_success", bundle)
    }

    // =========================================================================
    // 7. NHÃ“M CÃ€I Äáº¶T & TRáº¢I NGHIá»†M NGÆ¯á»œI DÃ™NG (SETTINGS & UX - 3 events)
    // =========================================================================

    /** 32. Thay Ä‘á»•i ngÃ´n ngá»¯ á»©ng dá»¥ng */
    fun logLanguageChanged(oldLang: String, newLang: String) {
        val bundle = Bundle().apply {
            putString("old_language", oldLang)
            putString("new_language", newLang)
        }
        logEvent("language_changed", bundle)
    }

    /** 33. Chuyá»ƒn Ä‘á»•i giao diá»‡n sÃ¡ng / tá»‘i */
    fun logThemeToggled(newTheme: String) {
        val bundle = Bundle().apply {
            putString("theme_mode", newTheme) // "light", "dark", "system"
        }
        logEvent("theme_toggled", bundle)
    }

    /** 34. NgÆ°á»i dÃ¹ng gá»­i Ã½ kiáº¿n Ä‘Ã³ng gÃ³p / bÃ¡o lá»—i */
    fun logUserFeedbackSubmitted(category: String, hasAttachment: Boolean) {
        val bundle = Bundle().apply {
            putString("feedback_category", category)
            putBoolean("has_attachment", hasAttachment)
        }
        logEvent("user_feedback_submitted", bundle)
    }

    // =========================================================================
    // 8. SCREEN TRACKING & BUTTON CLICK EVENTS (THEO DÃ•I MÃ€N HÃŒNH & TÆ¯Æ NG TÃC)
    // =========================================================================

    /** Ghi nháº­n khi ngÆ°á»i dÃ¹ng chuyá»ƒn tá»›i má»™t mÃ n hÃ¬nh (Screen View) */
    fun logScreenView(screenName: String, screenClass: String = screenName) {
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass)
            putLong("timestamp", System.currentTimeMillis())
        }
        logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
        logEvent("screen_view_custom", bundle)
        Log.i(TAG, "ðŸ“± Screen View Tracked: $screenName ($screenClass)")
    }

    /** Ghi nháº­n khi ngÆ°á»i dÃ¹ng click vÃ o button / item tÆ°Æ¡ng tÃ¡c */
    fun logButtonClick(buttonName: String, screenName: String = "", extraInfo: String = "") {
        val bundle = Bundle().apply {
            putString("button_name", buttonName)
            if (screenName.isNotEmpty()) putString("screen_name", screenName)
            if (extraInfo.isNotEmpty()) putString("extra_info", extraInfo)
            putLong("timestamp", System.currentTimeMillis())
        }
        logEvent("button_clicked", bundle)
        Log.d(TAG, "ðŸ”˜ Button Click Tracked: $buttonName on $screenName ($extraInfo)")
    }
}
