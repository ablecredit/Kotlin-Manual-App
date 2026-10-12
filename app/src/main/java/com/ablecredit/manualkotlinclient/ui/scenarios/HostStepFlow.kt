package com.ablecredit.manualkotlinclient.ui.scenarios

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.ablecredit.manualkotlinclient.data.model.LoanCaseItem
import com.ablecredit.manualkotlinclient.data.model.LoanCreationSource
import com.ablecredit.manualkotlinclient.data.model.OrchestratorFlowSnapshot
import com.ablecredit.manualkotlinclient.data.repository.LoanCaseRepository
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

private const val TAG = "HostStepFlow"

/**
 * One orchestrator flow with the client's own screen(s) in the middle (a HOST step):
 *
 *   Create → Audio → Photos → [HOST] → Audio → Photos → Summary (report)
 *
 * The flow stays alive underneath the host screens, so system back works at every point. Shared
 * by the Scenarios tab (new loan) and Home (resume a loan created by a scenario, via its
 * [OrchestratorFlowSnapshot]).
 */
object HostStepFlow {

    val HOST_STEPS = listOf(
        "CREATE_LOAN_CASE",
        "RECORD_AUDIO",
        "CAPTURE_BUSINESS_PHOTOS",
        "HOST",
        "RECORD_AUDIO",
        "CAPTURE_BUSINESS_PHOTOS",
        "SHOW_SUMMARY"
    )

    fun snapshot(chain: Boolean, minAudioCount: Int) = OrchestratorFlowSnapshot(
        steps = HOST_STEPS,
        hostScreens = if (chain) "chain" else "single",
        minAudioCount = minAudioCount,
        minPhotoCount = 2,
        generateReportOnSummary = true
    )

    /** Starts the flow against a new loan (payload) or, when [existingLoanId] is set, an existing one. */
    fun start(
        context: Context,
        snapshot: OrchestratorFlowSnapshot,
        newLoanPayload: Map<String, Any>? = null,
        existingLoanId: String? = null
    ) {
        val loanCaseRepository = LoanCaseRepository(context.applicationContext)
        val config = try {
            val steps = snapshot.steps
                .map { AbleCreditFlowStep.valueOf(it) }
                .filter { existingLoanId == null || it != AbleCreditFlowStep.CREATE_LOAN_CASE }
            val builder = AbleCreditFlowConfig.Builder()
                .steps(*steps.toTypedArray())
                .listener(listener(context, snapshot, loanCaseRepository))
            if (snapshot.minAudioCount > 0 && steps.contains(AbleCreditFlowStep.RECORD_AUDIO)) {
                builder.audioValidation(AbleCreditAudioValidation(minCount = snapshot.minAudioCount))
            }
            if (snapshot.minPhotoCount > 0) {
                builder.photoValidation(AbleCreditPhotoValidation(minCount = snapshot.minPhotoCount))
            }
            if (snapshot.generateReportOnSummary && steps.contains(AbleCreditFlowStep.SHOW_SUMMARY)) {
                builder.generateReportOn(AbleCreditFlowStep.SHOW_SUMMARY)
            }
            if (existingLoanId != null) builder.withExistingLoan(existingLoanId)
            else builder.withNewLoan(requireNotNull(newLoanPayload))
        } catch (e: IllegalArgumentException) {
            Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
            return
        }
        AbleCredit.startLoanFlow(context, config)
    }

    private fun listener(
        context: Context,
        snapshot: OrchestratorFlowSnapshot,
        loanCaseRepository: LoanCaseRepository
    ): AbleCreditFlowListener {
        val appContext = context.applicationContext
        return object : AbleCreditFlowListener {
            override fun onLoanCreated(applicationId: String, response: AbleCreditLoanResponse) {
                val reference = try {
                    response.data?.application?.loan_reference.orEmpty()
                } catch (_: Exception) { "" }
                Log.d(TAG, "Loan created | applicationId=$applicationId loanReference=$reference")
                loanCaseRepository.saveOrUpdate(
                    LoanCaseItem(
                        applicationId, reference, System.currentTimeMillis(),
                        LoanCreationSource.ORCHESTRATOR, snapshot
                    )
                )
            }

            override fun onHostStep(
                activity: Activity,
                applicationId: String,
                direction: AbleCreditFlowDirection,
                controller: AbleCreditFlowController
            ) {
                Log.d(TAG, "Host step | direction=$direction applicationId=$applicationId")
                ClientChainActivity.start(activity, applicationId, snapshot.hostScreens == "chain", controller)
            }

            override fun onFileStatusChanged(
                step: AbleCreditFlowStep, uniqueId: String, status: AbleCreditFileStatus, message: String?
            ) {
                Log.d(TAG, "File status | step=$step uniqueId=$uniqueId status=$status message=$message")
            }

            override fun onStepCompleted(step: AbleCreditFlowStep) {
                Log.d(TAG, "Step completed | step=$step")
            }

            override fun onReportGenerationTriggered(step: AbleCreditFlowStep, applicationId: String) {
                Log.d(TAG, "Report generation triggered | step=$step applicationId=$applicationId")
            }

            override fun onReportGenerationResult(
                step: AbleCreditFlowStep, applicationId: String, success: Boolean, message: String?
            ) {
                Log.d(TAG, "Report generation result | step=$step success=$success message=$message")
            }

            override fun onStepButtonClicked(step: AbleCreditFlowStep, applicationId: String): Boolean = false

            override fun onFlowCompleted(applicationId: String) {
                Log.d(TAG, "Flow completed | applicationId=$applicationId")
                Toast.makeText(appContext, "Flow completed", Toast.LENGTH_LONG).show()
            }

            override fun onFlowClosed(step: AbleCreditFlowStep?, applicationId: String?) {
                Log.d(TAG, "Flow closed by user | step=$step applicationId=$applicationId")
            }

            override fun onFlowFailed(step: AbleCreditFlowStep, error: AbleCreditResult.Failure) {
                Log.e(TAG, "Flow FAILED at step=$step | code=${error.ableCreditErrorCode} message=${error.message}")
                Toast.makeText(appContext, "Flow failed at $step: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
