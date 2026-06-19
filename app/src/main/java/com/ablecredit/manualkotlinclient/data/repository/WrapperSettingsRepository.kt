package com.ablecredit.manualkotlinclient.data.repository

import android.content.Context
import android.content.SharedPreferences

class WrapperSettingsRepository(context: Context) {
    companion object {
        private const val PREFS_NAME = "WrapperSettingsPrefs"
        private const val KEY_WRAPPER_TOASTS = "wrapper_toasts_enabled"
        private const val KEY_SDK_TOASTS = "sdk_toasts_enabled"
        private const val KEY_SDK_HEADER = "sdk_header_enabled"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isWrapperToastsEnabled(): Boolean = prefs.getBoolean(KEY_WRAPPER_TOASTS, false)
    fun setWrapperToastsEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_WRAPPER_TOASTS, enabled).apply() }

    fun isSdkToastsEnabled(): Boolean = prefs.getBoolean(KEY_SDK_TOASTS, true)
    fun setSdkToastsEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_SDK_TOASTS, enabled).apply() }

    fun isSdkHeaderEnabled(): Boolean = prefs.getBoolean(KEY_SDK_HEADER, true)
    fun setSdkHeaderEnabled(enabled: Boolean) { prefs.edit().putBoolean(KEY_SDK_HEADER, enabled).apply() }
}
