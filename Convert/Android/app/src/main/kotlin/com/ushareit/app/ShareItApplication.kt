package com.ushareit.app

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import com.tencent.mmkv.MMKV
import com.ushareit.core.ads.AdMediationManager
import com.ushareit.core.locale.LocaleManager
import com.ushareit.core.mcds.MCDSManager
import com.ushareit.core.net.NetworkClient

class ShareItApplication : Application() {

    companion object {
        const val TAG = "ShareNow_App"
        lateinit var instance: ShareItApplication
            private set

        init {
            try {
                System.loadLibrary("shareit_native_core")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Native library not loaded in test/mock environment: ${e.message}")
            }
        }
    }

    external fun getNativeEngineVersion(): String

    override fun attachBaseContext(base: Context) {
        try {
            MMKV.initialize(base)
        } catch (_: Exception) {}
        super.attachBaseContext(LocaleManager.wrapContext(base))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LocaleManager.applyCurrentLanguage(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 0. Khởi tạo Firebase SDK & Crashlytics
        try {
            com.google.firebase.FirebaseApp.initializeApp(this)
            com.ushareit.core.analytics.FirebaseAnalyticsManager.init(this)
            com.ushareit.core.analytics.FirebaseAnalyticsManager.logAppOpen(com.nextai.sharenow.BuildConfig.VERSION_NAME, java.util.Locale.getDefault().language)
            Log.i(TAG, "Firebase & Crashlytics initialized successfully.")
        } catch (e: Exception) {
            Log.w(TAG, "Firebase auto-initialization fallback: ${e.message}")
        }

        // 1. Khởi tạo Memory Monitor & Logging Task
        try {
            com.ushareit.core.monitor.MemoryLogger.init(this)
        } catch (e: Exception) {
            Log.w(TAG, "MemoryLogger init warning: ${e.message}")
        }

        // 1.1 Khởi tạo MMKV Storage
        MMKV.initialize(this)
        com.ushareit.core.downloader.MonetizedDomainRegistry.init(this)

        // 1.1 Khởi tạo đa ngôn ngữ i18n
        LocaleManager.init(this)

        // 1.2 Áp dụng chủ đề sáng / tối người dùng đã lưu
        com.ushareit.core.theme.ThemeManager.applyTheme()

        // 2. Khởi tạo Network Client kết nối Backend cổng 6030
        NetworkClient.init(this)

        // 3. Nạp cấu hình Dynamic Remote Config (MCDS)
        MCDSManager.init(this)

        // 4. Khởi tạo Ad Mediation Layer
        AdMediationManager.init(this)

        // 5. Khởi động P2P Socket Server ngầm sẵn sàng nhận tệp bất cứ lúc nào
        com.ushareit.core.p2p.P2PTransferManager.startServerIfNeeded(this)

        Log.i(TAG, "Share Now Application Initialized Successfully!")
    }
}
