package com.ablecredit.manualkotlinclient.ui.hostdemo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ablecredit.manualkotlinclient.R
import com.ablecredit.manualkotlinclient.data.SampleLoanCaseData
import com.ablecredit.manualkotlinclient.ui.main.dialog.CreateLoanDialog
import com.ablecredit.sdk.manager.AbleCredit
import com.ablecredit.sdk.model.AbleCreditAudioValidation
import com.ablecredit.sdk.model.AbleCreditFileStatus
import com.ablecredit.sdk.model.AbleCreditFlowConfig
import com.ablecredit.sdk.model.AbleCreditFlowController
import com.ablecredit.sdk.model.AbleCreditFlowDirection
import com.ablecredit.sdk.model.AbleCreditFlowListener
import com.ablecredit.sdk.model.AbleCreditFlowStep
import com.ablecredit.sdk.model.AbleCreditLoanResponse
import com.ablecredit.sdk.model.AbleCreditPhotoValidation
import com.ablecredit.sdk.model.AbleCreditResult

/**
 * Plain reference integration of the HOST step (Video -> cl1 -> cl2), using only SDK calls and
 * ordinary Android code. Assumes the SDK is already configured (Profile tab).
 *
 *   Create -> Audio -> Photos -> HOST -> Audio -> Photos -> Summary (report)
 */
class HostDemoActivity : AppCompatActivity() {

    private lateinit var log: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_host_demo)
        log = findViewById(R.id.tv_host_demo_log)

        findViewById<Button>(R.id.btn_host_demo_new_loan).setOnClickListener {
            CreateLoanDialog.show(this) { loanRef, userName, branch, product, businessModel ->
                val payload = SampleLoanCaseData.buildCreateLoanPayload(loanRef, userName, branch, product, businessModel)
                startHostChainFlow(this, payload)
            }
        }
        findViewById<Button>(R.id.btn_host_demo_existing_loan).setOnClickListener {
            val applicationId = findViewById<EditText>(R.id.et_host_demo_application_id).text.toString().trim()
            if (applicationId.isEmpty()) {
                Toast.makeText(this, "Enter an application _id", Toast.LENGTH_SHORT).show()
            } else {
                startHostFlowExistingLoan(this, applicationId)
            }
        }
    }

    // ── 1. Start the flow ────────────────────────────────────────────────────────────────────

    fun startHostChainFlow(context: Context, payload: Map<String, Any?>) {
        val config = AbleCreditFlowConfig.Builder()
            .steps(
                AbleCreditFlowStep.CREATE_LOAN_CASE,
                AbleCreditFlowStep.RECORD_AUDIO,
                AbleCreditFlowStep.CAPTURE_BUSINESS_PHOTOS,
                AbleCreditFlowStep.HOST,                    // Video → cl1 → cl2 live here
                AbleCreditFlowStep.RECORD_AUDIO,
                AbleCreditFlowStep.CAPTURE_BUSINESS_PHOTOS,
                AbleCreditFlowStep.SHOW_SUMMARY,
            )
            .generateReportOn(AbleCreditFlowStep.SHOW_SUMMARY)
            .listener(hostFlowListener(context))
            .withNewLoan(payload)     // terminal: returns AbleCreditFlowConfig (no .build())
            // or .withExistingLoan(applicationId) instead, and drop CREATE_LOAN_CASE from steps

        AbleCredit.startLoanFlow(context, config)
    }

    /** Existing loan: same steps without CREATE_LOAN_CASE; takes the application `_id`. */
    private fun startHostFlowExistingLoan(context: Context, applicationId: String) {
        val config = AbleCreditFlowConfig.Builder()
            .steps(
                AbleCreditFlowStep.RECORD_AUDIO,
                AbleCreditFlowStep.CAPTURE_BUSINESS_PHOTOS,
                AbleCreditFlowStep.HOST,
                AbleCreditFlowStep.RECORD_AUDIO,
                AbleCreditFlowStep.CAPTURE_BUSINESS_PHOTOS,
                AbleCreditFlowStep.SHOW_SUMMARY,
            )
            .generateReportOn(AbleCreditFlowStep.SHOW_SUMMARY)
            .listener(hostFlowListener(context))
            .withExistingLoan(applicationId)
        AbleCredit.startLoanFlow(context, config)
    }

    // ── 2. Listener ──────────────────────────────────────────────────────────────────────────

    private fun hostFlowListener(context: Context): AbleCreditFlowListener {
        val appContext = context.applicationContext
        return object : AbleCreditFlowListener {

            override fun onLoanCreated(applicationId: String, response: AbleCreditLoanResponse) =
                logLine("onLoanCreated applicationId=$applicationId")

            override fun onHostStep(
                activity: Activity,                       // the SDK's Photos screen
                applicationId: String,
                direction: AbleCreditFlowDirection,       // FORWARD or BACKWARD
                controller: AbleCreditFlowController,
            ) {
                logLine("onHostStep direction=$direction applicationId=$applicationId")
                // 1. keep the controller where the host screens can reach it
                HostFlowHolder.controller = controller
                // 2. open the first host screen from `activity`, not from the app context
                activity.startActivity(
                    Intent(activity, HostScreenActivity::class.java)
                        .putExtra("applicationId", applicationId)
                        .putExtra("position", 0)
                )
            }

            override fun onFileStatusChanged(
                step: AbleCreditFlowStep, uniqueId: String, status: AbleCreditFileStatus, message: String?,
            ) = logLine("onFileStatusChanged step=$step status=$status")

            override fun onStepCompleted(step: AbleCreditFlowStep) = logLine("onStepCompleted step=$step")

            override fun onReportGenerationTriggered(step: AbleCreditFlowStep, applicationId: String) =
                logLine("onReportGenerationTriggered step=$step")

            override fun onReportGenerationResult(
                step: AbleCreditFlowStep, applicationId: String, success: Boolean, message: String?,
            ) = logLine("onReportGenerationResult success=$success message=$message")

            override fun onFlowCompleted(applicationId: String) {
                logLine("onFlowCompleted applicationId=$applicationId")
                Toast.makeText(appContext, "Host flow completed", Toast.LENGTH_LONG).show()
            }

            override fun onFlowClosed(step: AbleCreditFlowStep?, applicationId: String?) =
                logLine("onFlowClosed step=$step")

            override fun onFlowFailed(step: AbleCreditFlowStep, error: AbleCreditResult.Failure) {
                logLine("onFlowFailed step=$step message=${error.message}")
                Toast.makeText(appContext, "Flow failed at $step: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun logLine(line: String) {
        android.util.Log.d("HostDemo", line)
        if (!isDestroyed) runOnUiThread { log.append(line + "\n") }
    }
}
