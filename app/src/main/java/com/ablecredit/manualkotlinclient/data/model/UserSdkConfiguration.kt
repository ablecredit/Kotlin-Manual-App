package com.ablecredit.manualkotlinclient.data.model

import org.json.JSONObject

data class UserSdkConfiguration(
    val id: String,
    val displayName: String,
    val apiKey: String,
    val tenantId: String,
    val baseUrl: String,
    val userId: String,
    val branchId: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", displayName)
        put("apiKey", apiKey)
        put("tenantId", tenantId)
        put("baseUrl", baseUrl)
        put("userId", userId)
        put("branchId", branchId)
    }

    companion object {
        fun fromJson(o: JSONObject) = UserSdkConfiguration(
            id = o.getString("id"),
            displayName = o.optString("name", "Custom"),
            apiKey = o.optString("apiKey", ""),
            tenantId = o.optString("tenantId", ""),
            baseUrl = o.optString("baseUrl", ""),
            userId = o.optString("userId", ""),
            branchId = o.optString("branchId", "")
        )
    }
}
