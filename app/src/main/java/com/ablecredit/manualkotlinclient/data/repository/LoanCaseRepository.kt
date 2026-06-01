package com.ablecredit.manualkotlinclient.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.ablecredit.manualkotlinclient.data.model.LoanCaseItem
import org.json.JSONArray

class LoanCaseRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "LoanCasePrefs"
        private const val KEY_CASES = "loan_cases"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveOrUpdate(item: LoanCaseItem) {
        val all = getAll().toMutableList()
        val index = all.indexOfFirst { it.applicationId == item.applicationId }
        if (index >= 0) all[index] = item else all.add(item)
        persist(all)
    }

    fun getAll(): List<LoanCaseItem> {
        val raw = prefs.getString(KEY_CASES, "[]") ?: "[]"
        val out = mutableListOf<LoanCaseItem>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val appId = obj.optString("applicationId", "")
                if (appId.isEmpty()) continue
                out.add(
                    LoanCaseItem(
                        applicationId = appId,
                        loanReference = obj.optString("loanReference", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {
        }
        return out.sortedByDescending { it.createdAt }
    }

    fun clear() {
        prefs.edit().remove(KEY_CASES).apply()
    }

    private fun persist(cases: List<LoanCaseItem>) {
        val arr = JSONArray()
        for (item in cases) {
            try {
                val obj = org.json.JSONObject().apply {
                    put("applicationId", item.applicationId)
                    put("loanReference", item.loanReference)
                    put("createdAt", item.createdAt)
                }
                arr.put(obj)
            } catch (_: Exception) {
            }
        }
        prefs.edit().putString(KEY_CASES, arr.toString()).apply()
    }
}
