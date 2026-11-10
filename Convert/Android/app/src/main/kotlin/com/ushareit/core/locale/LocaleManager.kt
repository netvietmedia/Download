package com.ushareit.core.locale

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.tencent.mmkv.MMKV
import java.util.Locale

data class AppLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val flag: String
) {
    val fullTitle: String
        get() = "$nativeName ($displayName) $flag"
}

object LocaleManager {

    const val DEFAULT_LANGUAGE = "en"
    private const val KEY_APP_LANGUAGE = "key_app_language_code"
    private const val PREFS_NAME = "share_now_locale_prefs"

    val SUPPORTED_LANGUAGES = listOf(
        AppLanguage("en", "English", "English", "🇺🇸"),
        AppLanguage("vi", "Vietnamese", "Tiếng Việt", "🇻🇳"),
        AppLanguage("es", "Spanish", "Español", "🇪🇸"),
        AppLanguage("fr", "French", "Français", "🇫🇷"),
        AppLanguage("zh", "Chinese", "中文 (简体)", "🇨🇳"),
        AppLanguage("hi", "Hindi", "हिन्दी", "🇮🇳"),
        AppLanguage("in", "Indonesian", "Bahasa Indonesia", "🇮🇩"),
        AppLanguage("ar", "Arabic", "العربية", "🇸🇦"),
        AppLanguage("de", "German", "Deutsch", "🇩🇪"),
        AppLanguage("pt", "Portuguese", "Português", "🇧🇷"),
        AppLanguage("ja", "Japanese", "日本語", "🇯🇵"),
        AppLanguage("ko", "Korean", "한국어", "🇰🇷"),
        AppLanguage("ru", "Russian", "Русский", "🇷🇺")
    )

    fun getLocale(langCode: String): Locale {
        return when (langCode.lowercase(Locale.ROOT)) {
            "zh", "zh-cn", "zh-hans" -> Locale.SIMPLIFIED_CHINESE
            "in", "id" -> Locale("in", "ID")
            "pt", "pt-br" -> Locale("pt", "BR")
            "ar" -> Locale("ar")
            else -> Locale(langCode)
        }
    }

    fun getLanguageTag(langCode: String): String {
        return when (langCode.lowercase(Locale.ROOT)) {
            "zh" -> "zh-CN"
            "in", "id" -> "id"
            "pt" -> "pt-BR"
            else -> langCode
        }
    }

    fun getSavedLanguage(context: Context? = null): String {
        try {
            val mmkv = MMKV.defaultMMKV()
            val saved = mmkv?.decodeString(KEY_APP_LANGUAGE)
            if (!saved.isNullOrEmpty()) {
                return saved
            }
        } catch (_: Exception) {}

        if (context != null) {
            try {
                val sp: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val saved = sp.getString(KEY_APP_LANGUAGE, null)
                if (!saved.isNullOrEmpty()) {
                    return saved
                }
            } catch (_: Exception) {}
        }
        return DEFAULT_LANGUAGE
    }

    private fun persistLanguage(context: Context?, langCode: String) {
        try {
            MMKV.defaultMMKV()?.encode(KEY_APP_LANGUAGE, langCode)
        } catch (_: Exception) {}

        if (context != null) {
            try {
                val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                sp.edit().putString(KEY_APP_LANGUAGE, langCode).apply()
            } catch (_: Exception) {}
        }
    }

    fun init(context: Context) {
        val langCode = getSavedLanguage(context)
        try {
            val kv = MMKV.defaultMMKV()
            if (kv != null && !kv.containsKey(KEY_APP_LANGUAGE)) {
                persistLanguage(context, DEFAULT_LANGUAGE)
            }
        } catch (_: Exception) {}
        applyLanguage(context, langCode)
    }

    fun applyCurrentLanguage(context: Context) {
        val langCode = getSavedLanguage(context)
        applyLanguage(context, langCode)
    }

    fun applyLanguage(context: Context, langCode: String) {
        persistLanguage(context, langCode)

        val locale = getLocale(langCode)
        Locale.setDefault(locale)

        // 1. Cập nhật configuration của context và application context
        val res = context.resources
        val config = Configuration(res.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        config.setLayoutDirection(locale)
        @Suppress("DEPRECATION")
        res.updateConfiguration(config, res.displayMetrics)

        try {
            val appRes = context.applicationContext.resources
            val appConfig = Configuration(appRes.configuration)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                appConfig.setLocales(LocaleList(locale))
            } else {
                @Suppress("DEPRECATION")
                appConfig.locale = locale
            }
            appConfig.setLayoutDirection(locale)
            @Suppress("DEPRECATION")
            appRes.updateConfiguration(appConfig, appRes.displayMetrics)
        } catch (_: Exception) {}

        // 2. Đồng bộ với AndroidX AppCompat Per-app language
        try {
            val tag = getLanguageTag(langCode)
            val appLocales = LocaleListCompat.forLanguageTags(tag)
            AppCompatDelegate.setApplicationLocales(appLocales)
        } catch (_: Exception) {}
    }

    fun getCurrentLanguageInfo(context: Context? = null): AppLanguage {
        val code = getSavedLanguage(context)
        return SUPPORTED_LANGUAGES.find { it.code.equals(code, ignoreCase = true) }
            ?: SUPPORTED_LANGUAGES[0]
    }

    fun getLanguageStatusTitle(context: Context? = null): String {
        val info = getCurrentLanguageInfo(context)
        return "${info.nativeName} ${info.flag}"
    }

    fun wrapContext(context: Context): Context {
        val langCode = getSavedLanguage(context)
        val locale = getLocale(langCode)
        Locale.setDefault(locale)

        val res = context.resources
        val config = Configuration(res.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        config.setLayoutDirection(locale)
        @Suppress("DEPRECATION")
        res.updateConfiguration(config, res.displayMetrics)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(config)
        } else {
            context
        }
    }
}
