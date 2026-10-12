package com.ablecredit.manualkotlinclient.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.ablecredit.manualkotlinclient.data.model.LoanCaseItem
import com.ablecredit.manualkotlinclient.data.model.LoanCreationSource
import com.ablecredit.manualkotlinclient.data.model.OrchestratorFlowSnapshot
import org.json.JSONArray
import org.json.JSONObject

class LoanCaseRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "LoanCasePrefs"
        private const val KEY_CASES = "loan_cases"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Adds or replaces the loan. A save that carries no flow snapshot / orchestrator source never
     * wipes what an earlier save stored for the same loan (the Scenarios tab saves the richer item).
     */
    @Synchronized
    fun saveOrUpdate(item: LoanCaseItem) {
        val all = getAll().toMutableList()
        val index = all.indexOfFirst { it.applicationId == item.applicationId }
        if (index >= 0) {
            val existing = all[index]
            all[index] = item.copy(
                flow = item.flow ?: existing.flow,
                source = if (item.source == LoanCreationSource.ORCHESTRATOR ||
                    existing.source == LoanCreationSource.ORCHESTRATOR
                ) LoanCreationSource.ORCHESTRATOR else LoanCreationSource.DIRECT
            )
        } else {
            all.add(item)
        }
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
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        source = if (obj.optString("source") == LoanCreationSource.ORCHESTRATOR.name)
                            LoanCreationSource.ORCHESTRATOR else LoanCreationSource.DIRECT,
                        flow = obj.optJSONObject("flow")?.let { snapshotFromJson(it) }
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
                    put("source", item.source.name)
                    item.flow?.let { put("flow", snapshotToJson(it)) }
                }
                arr.put(obj)
            } catch (_: Exception) {
            }
        }
        prefs.edit().putString(KEY_CASES, arr.toString()).apply()
    }

    private fun snapshotToJson(f: OrchestratorFlowSnapshot) = JSONObject().apply {
        put("steps", JSONArray(f.steps))
        put("hostScreens", f.hostScreens)
        put("minAudioCount", f.minAudioCount)
        put("minPhotoCount", f.minPhotoCount)
        put("generateReportOnSummary", f.generateReportOnSummary)
    }

    private fun snapshotFromJson(o: JSONObject): OrchestratorFlowSnapshot {
        val arr = o.optJSONArray("steps") ?: JSONArray()
        return OrchestratorFlowSnapshot(
            steps = (0 until arr.length()).map { arr.optString(it) },
            hostScreens = o.optString("hostScreens", "single"),
            minAudioCount = o.optInt("minAudioCount", 0),
            minPhotoCount = o.optInt("minPhotoCount", 0),
            generateReportOnSummary = o.optBoolean("generateReportOnSummary", false)
        )
    }
}
