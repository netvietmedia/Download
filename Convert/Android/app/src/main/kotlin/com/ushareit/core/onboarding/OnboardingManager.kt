package com.ushareit.core.onboarding

import com.tencent.mmkv.MMKV

object OnboardingManager {

    private const val KEY_ONBOARDING_COMPLETED = "key_app_onboarding_completed"

    fun isOnboardingCompleted(): Boolean {
        return try {
            MMKV.defaultMMKV()?.decodeBool(KEY_ONBOARDING_COMPLETED, false) ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun setOnboardingCompleted(completed: Boolean = true) {
        try {
            MMKV.defaultMMKV()?.encode(KEY_ONBOARDING_COMPLETED, completed)
        } catch (e: Exception) {
            // ignore
        }
    }

    fun resetOnboarding() {
        try {
            MMKV.defaultMMKV()?.encode(KEY_ONBOARDING_COMPLETED, false)
        } catch (e: Exception) {
            // ignore
        }
    }
}
