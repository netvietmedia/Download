package com.ushareit.ui.base

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatActivity
import com.ushareit.core.analytics.FirebaseAnalyticsManager
import com.ushareit.core.locale.LocaleManager

open class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrapContext(newBase))
    }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        if (overrideConfiguration != null) {
            val langCode = LocaleManager.getSavedLanguage(this)
            val locale = LocaleManager.getLocale(langCode)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                overrideConfiguration.setLocales(LocaleList(locale))
            } else {
                @Suppress("DEPRECATION")
                overrideConfiguration.locale = locale
            }
            overrideConfiguration.setLayoutDirection(locale)
        }
        super.applyOverrideConfiguration(overrideConfiguration)
    }

    override fun onResume() {
        super.onResume()
        // Tự động ghi nhận màn hình (Screen View) lên Firebase Analytics
        val screenName = this.javaClass.simpleName
        FirebaseAnalyticsManager.logScreenView(screenName, this.javaClass.name)
    }
}
