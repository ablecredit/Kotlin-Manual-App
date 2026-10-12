package com.ablecredit.manualkotlinclient.ui.scenarios

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.ablecredit.manualkotlinclient.R
import com.ablecredit.manualkotlinclient.data.SampleLoanCaseData
import com.ablecredit.manualkotlinclient.data.model.LoanCaseItem
import com.ablecredit.manualkotlinclient.data.repository.AbleCreditRepository
import com.ablecredit.manualkotlinclient.data.repository.LoanCaseRepository
import com.ablecredit.manualkotlinclient.ui.main.dialog.CreateLoanDialog
import com.ablecredit.sdk.manager.AbleCredit
import com.ablecredit.sdk.model.AbleCreditDockedButton
import com.ablecredit.sdk.model.AbleCreditFileStatus
import com.ablecredit.sdk.model.AbleCreditFlowConfig
import com.ablecredit.sdk.model.AbleCreditFlowListener
import com.ablecredit.sdk.model.AbleCreditFlowStep
import com.ablecredit.sdk.model.AbleCreditLoanResponse
import com.ablecredit.sdk.model.AbleCreditPhotoValidation
import com.ablecredit.sdk.model.AbleCreditResult
import com.google.android.material.textfield.TextInputEditText

private const val TAG = "ScenariosFragment"

class ScenariosFragment : Fragment() {

    private lateinit var ableCreditRepository: AbleCreditRepository
    private lateinit var loanCaseRepository: LoanCaseRepository

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_scenarios, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ableCreditRepository = AbleCreditRepository(requireActivity().application)
        loanCaseRepository = LoanCaseRepository(requireContext())

        view.findViewById<View>(R.id.row_record_with_docked_report).setOnClickListener {
            runRecordWithDockedReport()
        }
        view.findViewById<View>(R.id.row_two_stage_intercept).setOnClickListener {
            runTwoStageInterceptFlow()
        }
        view.findViewById<View>(R.id.row_client_handoff).setOnClickListener {
            runClientHandoffFlow()
        }
        view.findViewById<View>(R.id.row_host_step_single).setOnClickListener {
            runHostStepFlow(chain = false, minAudioCount = 0)
        }
        view.findViewById<View>(R.id.row_host_step_chain).setOnClickListener {
            runHostStepFlow(chain = true, minAudioCount = 2)
        }
    }

    /**
     * Scenario: Record audio with a docked "Generate report" button.
     */
    private fun runRecordWithDockedReport() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_direct_loan_reference, null)
        val input = dialogView.findViewById<TextInputEditText>(R.id.et_direct_loan_reference)
        AlertDialog.Builder(requireContext())
            .setTitle("Application ID")
            .setMessage("Leave empty to test SDK error handling.")
            .setView(dialogView)
            .setPositiveButton("Run") { _, _ ->
                val loanRef = input.text?.toString()?.trim().orEmpty()
                launchRecordWithDockedReport(loanRef)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun launchRecordWithDockedReport(loanRef: String) {
        AbleCredit.recordAudio(
            context = requireContext(),
            loanApplicationId = loanRef,
            dockedButton = AbleCreditDockedButton(
                label = "Generate report",
                isGenerateReport = true,
                onClick = { ctx ->
                    AbleCredit.requestReportGeneration(ctx, loanRef) { result ->
                        when (result) {
                            is AbleCreditResult.Success ->
                                Toast.makeText(ctx, "Report requested successfully", Toast.LENGTH_SHORT).show()
                            is AbleCreditResult.Failure ->
                                Toast.makeText(ctx, "Report failed: ${result.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
            ),
        )
    }

    /** Stand-in for the client app's own screen between the two SDK stages (dialog version). */
    private fun showClientScreen(applicationId: String) {
        TwoStageFlowHelper.showClientScreenDialog(requireContext(), applicationId) {
            TwoStageFlowHelper.runStageTwoReport(
                requireContext(), ableCreditRepository, applicationId
            ) {
                Toast.makeText(requireContext(), "Two-stage flow completed", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Scenario: orchestrator flow called TWICE, with the app's own client screen in between —
     * simulating a client app that owns the transition between two SDK capture stages. Uses
     * [AbleCreditFlowConfig.Builder.interceptStepButton] for the handoff between stages.
     *
     * The card asks for the loan payload up front (via the existing [CreateLoanDialog] form), then:
     * 1. `AbleCredit.startLoanFlow(withNewLoan(payload) steps=[CREATE_LOAN_CASE, RECORD_AUDIO,
     *    CAPTURE_BUSINESS_PHOTOS] interceptStepButton(CAPTURE_BUSINESS_PHOTOS))` — stage 1.
     *    Tapping the photos screen's own "Next" button calls `onStepButtonClicked
     *    (CAPTURE_BUSINESS_PHOTOS, applicationId)` directly — the SDK does NOT advance/finish/call
     *    onFlowCompleted on its own; the client screen is shown from inside that callback.
     * 2. (an app-owned client screen shown here, with a button to resume the SDK)
     * 3. `AbleCredit.startLoanFlow(withExistingLoan(applicationId) steps=[RECORD_AUDIO,
     *    CAPTURE_BUSINESS_PHOTOS], generateReportOn(CAPTURE_BUSINESS_PHOTOS))` — stage 2: audio +
     *    photos again; the photos screen's own flow-done button becomes the "Generate Report"
     *    action, resuming against the loan created in stage 1.
     */
    private fun runTwoStageInterceptFlow() {
        CreateLoanDialog.show(requireActivity() as AppCompatActivity) { loanRef, userName, branch, product, businessModel ->
            val payload = SampleLoanCaseData.buildCreateLoanPayload(loanRef, userName, branch, product, businessModel)
            startInterceptStageOne(payload)
        }
    }

    private fun startInterceptStageOne(payload: Map<String, Any>) {
        val config = try {
            AbleCreditFlowConfig.Builder()
                .steps(
                    AbleCreditFlowStep.CREATE_LOAN_CASE,
                    AbleCreditFlowStep.RECORD_AUDIO,
                    AbleCreditFlowStep.CAPTURE_BUSINESS_PHOTOS
                )
                .interceptStepButton(AbleCreditFlowStep.CAPTURE_BUSINESS_PHOTOS)
                .photoValidation(
                    AbleCreditFlowStep.CAPTURE_BUSINESS_PHOTOS,
                    AbleCreditPhotoValidation(2, null)
                )
                .listener(interceptStageOneListener())
                .withNewLoan(payload)
        } catch (e: IllegalArgumentException) {
            Toast.makeText(requireContext(), e.message, Toast.LENGTH_LONG).show()
            return
        }
        ableCreditRepository.startLoanFlow(requireContext(), config)
    }

    private fun interceptStageOneListener(): AbleCreditFlowListener = object : AbleCreditFlowListener {
        override fun onLoanCreated(applicationId: String, response: AbleCreditLoanResponse) {
            Log.d(TAG, "Loan created by startLoanFlow | applicationId=$applicationId")
            val reference = extractLoanReference(response)
            loanCaseRepository.saveOrUpdate(LoanCaseItem(applicationId, reference ?: "", System.currentTimeMillis()))
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
            // Not expected: CAPTURE_BUSINESS_PHOTOS is intercepted, so the SDK never reaches its
            // own completion for this step — the handoff happens in onStepButtonClicked.
            Log.d(TAG, "Stage 1 completed normally | applicationId=$applicationId")
        }

        override fun onFlowFailed(step: AbleCreditFlowStep, error: AbleCreditResult.Failure) {
            Log.e(TAG, "Stage 1 FAILED at step=$step | code=${error.ableCreditErrorCode}, message=${error.message}")
            Toast.makeText(requireContext(), "Stage 1 failed at $step: ${error.message}", Toast.LENGTH_LONG).show()
        }

        override fun onStepCompleted(step: AbleCreditFlowStep) {
            Log.d(TAG, "Stage 1 step completed | step=$step")
        }

        override fun onFlowClosed(step: AbleCreditFlowStep?, applicationId: String?) {
            Log.d(TAG, "Stage 1 closed by user | step=$step applicationId=$applicationId")
        }

        override fun onStepButtonClicked(step: AbleCreditFlowStep, applicationId: String): Boolean {
            Log.d(TAG, "Step button intercepted | step=$step applicationId=$applicationId")
            showClientScreen(applicationId)
            return true
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

    /** Stand-in for the client app's own screen between the two SDK stages — real screen, not a dialog. */
    private fun showClientScreenAsActivity(applicationId: String) {
        TwoStageFlowHelper.showClientScreenActivity(requireContext(), applicationId) {
            TwoStageFlowHelper.runStageTwoReportOnAudio(
                requireContext(), ableCreditRepository, applicationId
            ) {
                Toast.makeText(requireContext(), "Two-stage flow completed", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Scenario: copy of [runTwoStageInterceptFlow], with three differences:
     * 1. No photo capture at all — CAPTURE_BUSINESS_PHOTOS is removed from both stages. The
     *    intercepted step (and the report-generation step in stage 2) is RECORD_AUDIO instead.
     * 2. The client handoff between stages is a real screen ([ClientHandoffActivity]), not an
     *    AlertDialog — it shows a toast ("Client screen reached") as soon as it's reached.
     * 3. Stage 2 ends right on RECORD_AUDIO's own flow-done button via
     *    generateReportOn(RECORD_AUDIO) — no SHOW_SUMMARY screen.
     */
    private fun runClientHandoffFlow() {
        CreateLoanDialog.show(requireActivity() as AppCompatActivity) { loanRef, userName, branch, product, businessModel ->
            val payload = SampleLoanCaseData.buildCreateLoanPayload(loanRef, userName, branch, product, businessModel)
            startMinPhotosStageOne(payload)
        }
    }

    private fun startMinPhotosStageOne(payload: Map<String, Any>) {
        val config = try {
            AbleCreditFlowConfig.Builder()
                .steps(
                    AbleCreditFlowStep.CREATE_LOAN_CASE,
                    AbleCreditFlowStep.RECORD_AUDIO
                )
                .interceptStepButton(AbleCreditFlowStep.RECORD_AUDIO)
                .listener(minPhotosStageOneListener())
                .withNewLoan(payload)
        } catch (e: IllegalArgumentException) {
            Toast.makeText(requireContext(), e.message, Toast.LENGTH_LONG).show()
            return
        }
        ableCreditRepository.startLoanFlow(requireContext(), config)
    }

    private fun minPhotosStageOneListener(): AbleCreditFlowListener = object : AbleCreditFlowListener {
        override fun onLoanCreated(applicationId: String, response: AbleCreditLoanResponse) {
            Log.d(TAG, "Loan created by startLoanFlow | applicationId=$applicationId")
            val reference = extractLoanReference(response)
            loanCaseRepository.saveOrUpdate(LoanCaseItem(applicationId, reference ?: "", System.currentTimeMillis()))
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
            // Not expected: RECORD_AUDIO is intercepted, so the SDK never reaches its own
            // completion for this step — the handoff happens in onStepButtonClicked.
            Log.d(TAG, "Stage 1 completed normally | applicationId=$applicationId")
        }

        override fun onFlowFailed(step: AbleCreditFlowStep, error: AbleCreditResult.Failure) {
            Log.e(TAG, "Stage 1 FAILED at step=$step | code=${error.ableCreditErrorCode}, message=${error.message}")
            Toast.makeText(requireContext(), "Stage 1 failed at $step: ${error.message}", Toast.LENGTH_LONG).show()
        }

        override fun onStepCompleted(step: AbleCreditFlowStep) {
            Log.d(TAG, "Stage 1 step completed | step=$step")
        }

        override fun onFlowClosed(step: AbleCreditFlowStep?, applicationId: String?) {
            Log.d(TAG, "Stage 1 closed by user | step=$step applicationId=$applicationId")
        }

        override fun onStepButtonClicked(step: AbleCreditFlowStep, applicationId: String): Boolean {
            Log.d(TAG, "Step button intercepted | step=$step applicationId=$applicationId")
            showClientScreenAsActivity(applicationId)
            return true
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

    /**
     * Scenario: ONE SDK flow with the client's own screen(s) in the middle (see [HostStepFlow]).
     * [chain] makes the host step Video → cl1 → cl2 (kept on the stack via
     * proceed(keepHostScreens = true)); [minAudioCount] gates the Audio steps. The created loan is
     * saved to Home tagged Orchestrator with its flow, so tapping it there resumes this flow.
     */
    private fun runHostStepFlow(chain: Boolean, minAudioCount: Int) {
        CreateLoanDialog.show(requireActivity() as AppCompatActivity) { loanRef, userName, branch, product, businessModel ->
            val payload = SampleLoanCaseData.buildCreateLoanPayload(loanRef, userName, branch, product, businessModel)
            HostStepFlow.start(
                requireContext(),
                HostStepFlow.snapshot(chain, minAudioCount),
                newLoanPayload = payload
            )
        }
    }

    private fun extractLoanReference(response: AbleCreditLoanResponse?): String? {
        return try {
            response?.data?.application?.loan_reference?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }
}
