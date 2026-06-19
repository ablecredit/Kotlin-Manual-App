package com.ablecredit.manualkotlinclient.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.ablecredit.sdk.model.AbleCreditFlowStep
import org.json.JSONArray

class FlowStepsRepository(context: Context) {
    companion object {
        private const val PREFS_NAME = "FlowStepsPrefs"
        private const val KEY_STEPS = "flow_steps"
        val DEFAULT_STEPS = listOf(
            AbleCreditFlowStep.RECORD_AUDIO,
            AbleCreditFlowStep.CAPTURE_BUSINESS_PHOTOS,
            AbleCreditFlowStep.CAPTURE_FAMILY_PHOTOS,
            AbleCreditFlowStep.CAPTURE_COLLATERAL_PHOTOS,
            AbleCreditFlowStep.CREATE_LOAN_CASE
        )
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSteps(): List<AbleCreditFlowStep> {
        val raw = prefs.getString(KEY_STEPS, null) ?: return ArrayList(DEFAULT_STEPS)
        val out = mutableListOf<AbleCreditFlowStep>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                try { out.add(AbleCreditFlowStep.valueOf(arr.optString(i, ""))) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        return if (out.isEmpty()) ArrayList(DEFAULT_STEPS) else out
    }

    fun saveSteps(steps: List<AbleCreditFlowStep>) {
        val arr = JSONArray()
        steps.forEach { arr.put(it.name) }
        prefs.edit().putString(KEY_STEPS, arr.toString()).apply()
    }
}
