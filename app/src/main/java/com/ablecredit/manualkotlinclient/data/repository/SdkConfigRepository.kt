package com.ablecredit.manualkotlinclient.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.ablecredit.manualkotlinclient.data.model.UserSdkConfiguration
import org.json.JSONArray
import org.json.JSONObject

class SdkConfigRepository(context: Context) {

    companion object {
        const val PREFS_NAME = "SdkPrefs"
        const val KEY_SDK_INITIALIZED = "sdk_initialized"
        const val KEY_API_KEY = "api_key"
        const val KEY_TENANT_ID = "tenant_id"
        const val KEY_USER_ID = "user_id"
        const val KEY_BASE_URL = "base_url"
        const val KEY_CONFIG_TAGS = "config_tags"
        const val DEFAULT_CONFIG_TAGS = "default"
        const val KEY_BRANCH_ID = "branch_id"
        private const val KEY_PRESET_OVERRIDES = "preset_overrides_json"
        private const val KEY_USER_CONFIGS = "user_configs_json"
        private const val KEY_FILTERS_ENABLED = "filters_enabled"
        const val DEFAULT_USER_ID = ""
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isSdkInitialized(): Boolean = prefs.getBoolean(KEY_SDK_INITIALIZED, false)

    fun getSavedConfigTag(): String =
        prefs.getString(KEY_CONFIG_TAGS, DEFAULT_CONFIG_TAGS) ?: DEFAULT_CONFIG_TAGS

    fun getSavedApiKey(): String = prefs.getString(KEY_API_KEY, "") ?: ""
    fun getSavedTenantId(): String = prefs.getString(KEY_TENANT_ID, "") ?: ""
    fun getSavedBaseUrl(): String = prefs.getString(KEY_BASE_URL, "") ?: ""
    fun getSavedUserId(): String = prefs.getString(KEY_USER_ID, "") ?: ""
    fun getSavedBranchId(): String = prefs.getString(KEY_BRANCH_ID, "") ?: ""

    fun saveSuccessfulConfiguration(
        apiKey: String,
        tenantId: String,
        userId: String,
        baseUrl: String,
        selectionId: String,
        branchId: String = ""
    ) {
        prefs.edit()
            .putBoolean(KEY_SDK_INITIALIZED, true)
            .putString(KEY_API_KEY, apiKey)
            .putString(KEY_TENANT_ID, tenantId)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_BASE_URL, baseUrl)
            .putString(KEY_CONFIG_TAGS, selectionId)
            .putString(KEY_BRANCH_ID, branchId)
            .apply()
    }

    fun isFiltersEnabled(): Boolean = prefs.getBoolean(KEY_FILTERS_ENABLED, false)

    fun setFiltersEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FILTERS_ENABLED, enabled).apply()
    }

    fun clearSavedConfiguration() {
        prefs.edit()
            .putBoolean(KEY_SDK_INITIALIZED, false)
            .remove(KEY_API_KEY)
            .remove(KEY_TENANT_ID)
            .remove(KEY_USER_ID)
            .remove(KEY_BASE_URL)
            .remove(KEY_CONFIG_TAGS)
            .remove(KEY_BRANCH_ID)
            .apply()
    }

    private fun presetOverridesRoot(): JSONObject = try {
        val raw = prefs.getString(KEY_PRESET_OVERRIDES, null)
        if (raw.isNullOrEmpty()) JSONObject() else JSONObject(raw)
    } catch (e: Exception) {
        JSONObject()
    }

    private fun persistPresetOverrides(root: JSONObject) {
        prefs.edit().putString(KEY_PRESET_OVERRIDES, root.toString()).apply()
    }

    fun putPresetOverride(presetTag: String, apiKey: String, tenantId: String, baseUrl: String) {
        try {
            val root = presetOverridesRoot()
            val o = JSONObject().apply {
                put("apiKey", apiKey)
                put("tenantId", tenantId)
                put("baseUrl", baseUrl)
            }
            root.put(presetTag, o)
            persistPresetOverrides(root)
        } catch (_: Exception) {
        }
    }

    fun mergePresetOverrideInto(presetTag: String, outApiTenantUrl: Array<String>) {
        if (outApiTenantUrl.size < 3) return
        try {
            val root = presetOverridesRoot()
            if (!root.has(presetTag)) return
            val o = root.getJSONObject(presetTag)
            if (o.has("apiKey")) outApiTenantUrl[0] = o.optString("apiKey", outApiTenantUrl[0])
            if (o.has("tenantId")) outApiTenantUrl[1] = o.optString("tenantId", outApiTenantUrl[1])
            if (o.has("baseUrl")) outApiTenantUrl[2] = o.optString("baseUrl", outApiTenantUrl[2])
        } catch (_: Exception) {
        }
    }

    fun loadUserConfigurations(): List<UserSdkConfiguration> {
        val list = mutableListOf<UserSdkConfiguration>()
        try {
            val raw = prefs.getString(KEY_USER_CONFIGS, null) ?: return list
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                list.add(UserSdkConfiguration.fromJson(arr.getJSONObject(i)))
            }
        } catch (_: Exception) {
        }
        return list
    }

    private fun persistUserConfigs(arr: JSONArray) {
        prefs.edit().putString(KEY_USER_CONFIGS, arr.toString()).apply()
    }

    fun addUserConfiguration(
        displayName: String,
        apiKey: String,
        tenantId: String,
        baseUrl: String,
        userId: String,
        branchId: String = ""
    ): UserSdkConfiguration {
        val id = "user:${System.currentTimeMillis()}"
        val cfg = UserSdkConfiguration(id, displayName, apiKey, tenantId, baseUrl, userId, branchId)
        try {
            val raw = prefs.getString(KEY_USER_CONFIGS, null)
            val arr = if (raw.isNullOrEmpty()) JSONArray() else JSONArray(raw)
            arr.put(cfg.toJson())
            persistUserConfigs(arr)
        } catch (_: Exception) {
        }
        return cfg
    }

    fun updateUserConfiguration(updated: UserSdkConfiguration) {
        try {
            val raw = prefs.getString(KEY_USER_CONFIGS, null)
            val arr = if (raw.isNullOrEmpty()) JSONArray() else JSONArray(raw)
            val next = JSONArray()
            var replaced = false
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (updated.id == o.optString("id")) {
                    next.put(updated.toJson())
                    replaced = true
                } else {
                    next.put(o)
                }
            }
            if (!replaced) next.put(updated.toJson())
            persistUserConfigs(next)
        } catch (_: Exception) {
        }
    }

    fun findUserConfiguration(id: String): UserSdkConfiguration? =
        loadUserConfigurations().firstOrNull { it.id == id }

    fun setLastSelectionId(selectionId: String) {
        prefs.edit().putString(KEY_CONFIG_TAGS, selectionId).apply()
    }
}
