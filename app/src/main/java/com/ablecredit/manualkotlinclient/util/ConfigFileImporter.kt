package com.ablecredit.manualkotlinclient.util

import android.content.Context
import android.net.Uri
import com.ablecredit.manualkotlinclient.data.model.UserSdkConfiguration
import org.json.JSONArray
import org.json.JSONObject
import java.util.Scanner

object ConfigFileImporter {

    data class ImportResult(
        val success: Boolean,
        val configurations: List<UserSdkConfiguration> = emptyList(),
        val errorMessage: String = ""
    )

    fun importFromUri(context: Context, uri: Uri): ImportResult {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return ImportResult(success = false, errorMessage = "Cannot open file")
            val jsonString = Scanner(inputStream).useDelimiter("\\A").let {
                if (it.hasNext()) it.next() else ""
            }
            inputStream.close()
            parseConfigurations(jsonString)
        } catch (e: Exception) {
            ImportResult(success = false, errorMessage = "Error reading file: ${e.message}")
        }
    }

    private fun parseConfigurations(jsonString: String): ImportResult {
        return try {
            val root = JSONObject(jsonString)
            if (!root.has("configurations")) {
                return ImportResult(success = false, errorMessage = "Missing 'configurations' array in JSON")
            }
            val configurationsArray: JSONArray = root.getJSONArray("configurations")
            val configurations = mutableListOf<UserSdkConfiguration>()
            for (i in 0 until configurationsArray.length()) {
                val configObj = configurationsArray.getJSONObject(i)
                val name = configObj.optString("name", "Imported Config")
                val apiKey = configObj.optString("apiKey", "")
                val tenantId = configObj.optString("tenantId", "")
                val baseUrl = configObj.optString("baseUrl", "")
                val userId = configObj.optString("userId", "")
                val branchId = configObj.optString("branchId", "")
                if (apiKey.isEmpty() || tenantId.isEmpty() || baseUrl.isEmpty() || userId.isEmpty()) {
                    return ImportResult(
                        success = false,
                        errorMessage = "Configuration at index $i is missing required fields (apiKey, tenantId, baseUrl, userId)"
                    )
                }
                val id = "imported:${System.currentTimeMillis()}_$i"
                configurations.add(UserSdkConfiguration(id, name, apiKey, tenantId, baseUrl, userId, branchId))
            }
            if (configurations.isEmpty()) {
                return ImportResult(success = false, errorMessage = "No valid configurations found in file")
            }
            ImportResult(success = true, configurations = configurations)
        } catch (e: Exception) {
            ImportResult(success = false, errorMessage = "Parsing error: ${e.message}")
        }
    }
}
