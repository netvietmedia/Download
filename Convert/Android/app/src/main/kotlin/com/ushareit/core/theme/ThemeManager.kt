package com.ushareit.core.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.tencent.mmkv.MMKV

object ThemeManager {
    const val MODE_SYSTEM = 0
    const val MODE_LIGHT = 1
    const val MODE_DARK = 2

    private const val KEY_THEME_MODE = "key_app_theme_mode"

    fun getSavedThemeMode(): Int {
        return try {
            MMKV.defaultMMKV()?.decodeInt(KEY_THEME_MODE, MODE_LIGHT) ?: MODE_LIGHT
        } catch (e: Exception) {
            MODE_LIGHT
        }
    }

    fun applyTheme(mode: Int = getSavedThemeMode()) {
        try {
            MMKV.defaultMMKV()?.encode(KEY_THEME_MODE, mode)
        } catch (e: Exception) {
            // ignore
        }
        val nightMode = when (mode) {
            MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    fun getThemeTitle(context: Context, mode: Int = getSavedThemeMode()): String {
        return when (mode) {
            MODE_DARK -> context.getString(com.nextai.sharenow.R.string.me_theme_dark)
            else -> context.getString(com.nextai.sharenow.R.string.me_theme_light)
        }
    }
}
