package com.ablecredit.manualkotlinclient.ui.scenarios

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.ablecredit.manualkotlinclient.data.repository.AbleCreditRepository
import com.ablecredit.sdk.model.AbleCreditFileStatus
import com.ablecredit.sdk.model.AbleCreditFlowConfig
import com.ablecredit.sdk.model.AbleCreditFlowListener
import com.ablecredit.sdk.model.AbleCreditFlowStep
import com.ablecredit.sdk.model.AbleCreditLoanResponse
import com.ablecredit.sdk.model.AbleCreditResult

private const val TAG = "TwoStageFlowHelper"

/**
 * Stage 2 of the two-stage orchestrator scenario, shared between the scenario's own first run
 * ([ScenariosFragment]'s "Two-Stage Flow" and "Client Handoff" cards) — both need the same
 * client-screen interstitial and stage-2 config shape.
 */
object TwoStageFlowHelper {

    /** Stand-in for the client app's own screen between the two SDK stages. */
    fun showClientScreenDialog(context: Context, applicationId: String, onResume: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("Client screen")
            .setMessage(
                "Audio + photos complete for application $applicationId.\n\n" +
                    "This screen represents control returning to the host app. Tap the button to " +
                    "resume the SDK for the existing-loan stage (audio + photos + report)."
            )
            .setCancelable(false)
            .setPositiveButton("Resume SDK") { _, _ -> onResume() }
            .show()
    }

    /**
     * Real-screen stand-in for the client app's own screen between the two SDK stages — used by
     * the Client Handoff scenario instead of [showClientScreenDialog]'s dialog. Launches
     * [ClientHandoffActivity], which shows a toast as soon as it's reached and calls [onResume]
     * when the user taps its "Resume SDK" button.
     */
    fun showClientScreenActivity(context: Context, applicationId: String, onResume: () -> Unit) {
        ClientHandoffActivity.start(context, applicationId, onResume)
    }

    /**
     * steps(RECORD_AUDIO, CAPTURE_BUSINESS_PHOTOS) + generateReportOn(CAPTURE_BUSINESS_PHOTOS):
     * generateReportOn attaches report generation directly to the photos screen's flow-done
     * button — no separate summary screen needed.
     */
    fun runStageTwoReport(
        context: Context,
        ableCreditRepository: AbleCreditRepository,
        applicationId: String,
        onFlowFinished: () -> Unit
    ) {
        val config = AbleCreditFlowConfig.Builder()
            .steps(
                AbleCreditFlowStep.RECORD_AUDIO,
                AbleCreditFlowStep.CAPTURE_BUSINESS_PHOTOS
            )
            .generateReportOn(AbleCreditFlowStep.CAPTURE_BUSINESS_PHOTOS)
            .listener(stageListener(context, onFlowFinished))
            .withExistingLoan(applicationId)
        ableCreditRepository.startLoanFlow(context, config)
    }

    /**
     * Variant of [runStageTwoReport] used by the Client Handoff scenario: no photo capture at
     * all — report generation is attached directly to [AbleCreditFlowStep.RECORD_AUDIO]'s own
     * flow-done button, so tapping it on the audio (business PD) screen generates the report.
     */
    fun runStageTwoReportOnAudio(
        context: Context,
        ableCreditRepository: AbleCreditRepository,
        applicationId: String,
        onFlowFinished: () -> Unit
    ) {
        val config = AbleCreditFlowConfig.Builder()
            .steps(AbleCreditFlowStep.RECORD_AUDIO)
            .generateReportOn(AbleCreditFlowStep.RECORD_AUDIO)
            .listener(stageListener(context, onFlowFinished))
            .withExistingLoan(applicationId)
        ableCreditRepository.startLoanFlow(context, config)
    }

    private fun stageListener(
        context: Context,
        onFlowFinished: () -> Unit
    ): AbleCreditFlowListener = object : AbleCreditFlowListener {
        override fun onLoanCreated(applicationId: String, response: AbleCreditLoanResponse) {
            // Not expected in stage 2 — the loan already exists (withExistingLoan).
        }

        override fun onFileStatusChanged(
            step: AbleCreditFlowStep,
            uniqueId: String,
            status: AbleCreditFileStatus,
            message: String?
        ) {
            Log.d(TAG, "File status | step=$step uniqueId=$uniqueId status=$status message=$message")
        }

        override fun onFlowCompleted(applicationId: String) {
            Log.d(TAG, "Stage completed | applicationId=$applicationId")
            onFlowFinished()
        }

        override fun onFlowFailed(step: AbleCreditFlowStep, error: AbleCreditResult.Failure) {
            Log.e(TAG, "Stage FAILED at step=$step | code=${error.ableCreditErrorCode}, message=${error.message}")
            Toast.makeText(context, "Stage failed at $step: ${error.message}", Toast.LENGTH_LONG).show()
        }

        override fun onStepCompleted(step: AbleCreditFlowStep) {
            Log.d(TAG, "Step completed | step=$step")
        }

        override fun onFlowClosed(step: AbleCreditFlowStep?, applicationId: String?) {
            Log.d(TAG, "Stage closed by user | step=$step applicationId=$applicationId")
        }

        override fun onStepButtonClicked(step: AbleCreditFlowStep, applicationId: String): Boolean {
            return false
        }

        override fun onReportGenerationTriggered(step: AbleCreditFlowStep, applicationId: String) {
            Log.d(TAG, "Report generation triggered | step=$step, applicationId=$applicationId")
        }

        override fun onReportGenerationResult(
            step: AbleCreditFlowStep,
            applicationId: String,
            success: Boolean,
            message: String?
        ) {
            Log.d(TAG, "Report generation result | step=$step, success=$success, message=$message")
        }
    }
}
