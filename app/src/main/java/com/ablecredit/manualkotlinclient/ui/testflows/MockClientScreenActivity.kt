package com.ablecredit.manualkotlinclient.ui.testflows

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ablecredit.manualkotlinclient.R
import com.ablecredit.sdk.manager.AbleCredit
import com.ablecredit.sdk.model.AbleCreditDockedButton
import com.ablecredit.sdk.model.AbleCreditResult
import com.google.android.material.button.MaterialButton

class MockClientScreenActivity : AppCompatActivity() {

    companion object {
        const val STEP_BUSINESS_PHOTOS = "business_photos"
        const val STEP_COLLATERAL_PHOTOS = "collateral_photos"
        const val STEP_FAMILY_PHOTOS = "family_photos"
        const val STEP_AUDIO = "audio"
        const val STEP_GENERATE_REPORT = "generate_report"
        const val STEP_DONE = "done"

        private const val EXTRA_LOAN_REFERENCE = "extra_loan_reference"
        private const val EXTRA_DESCRIPTION = "extra_description"
        private const val EXTRA_NEXT_SDK_STEP = "extra_next_sdk_step"

        private var pendingNextDockedButton: AbleCreditDockedButton? = null

        fun createIntent(
            context: Context,
            loanReference: String,
            description: String,
            nextSdkStep: String,
            nextStepDockedButton: AbleCreditDockedButton?
        ): Intent {
            pendingNextDockedButton = nextStepDockedButton
            return Intent(context, MockClientScreenActivity::class.java).apply {
                putExtra(EXTRA_LOAN_REFERENCE, loanReference)
                putExtra(EXTRA_DESCRIPTION, description)
                putExtra(EXTRA_NEXT_SDK_STEP, nextSdkStep)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mock_client_screen)

        val loanReference = intent.getStringExtra(EXTRA_LOAN_REFERENCE) ?: ""
        val description = intent.getStringExtra(EXTRA_DESCRIPTION) ?: ""
        val nextStep = intent.getStringExtra(EXTRA_NEXT_SDK_STEP) ?: STEP_DONE
        val nextDockedButton = pendingNextDockedButton.also { pendingNextDockedButton = null }

        findViewById<TextView>(R.id.tv_loan_reference).text =
            loanReference.ifEmpty { "(no loan reference)" }
        findViewById<TextView>(R.id.tv_description).text =
            description.ifEmpty { "The client app is doing its own work here before handing back to the SDK." }

        val continueBtn = findViewById<MaterialButton>(R.id.btn_continue_to_sdk)
        when (nextStep) {
            STEP_DONE -> {
                continueBtn.text = "Finish"
                continueBtn.setOnClickListener { finish() }
            }
            STEP_GENERATE_REPORT -> {
                continueBtn.text = "Generate report"
                continueBtn.setOnClickListener { generateReport(loanReference) }
            }
            else -> {
                continueBtn.text = "Continue to SDK step →"
                continueBtn.setOnClickListener { launchNextSdkStep(loanReference, nextStep, nextDockedButton) }
            }
        }

        findViewById<MaterialButton>(R.id.btn_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun launchNextSdkStep(loanReference: String, step: String, dockedButton: AbleCreditDockedButton?) {
        when (step) {
            STEP_BUSINESS_PHOTOS -> AbleCredit.captureBusinessPhotos(this, loanReference, null, dockedButton)
            STEP_COLLATERAL_PHOTOS -> AbleCredit.captureCollateralPhotos(this, loanReference, null, dockedButton)
            STEP_FAMILY_PHOTOS -> AbleCredit.captureFamilyPhotos(this, loanReference, null, dockedButton)
            STEP_AUDIO -> AbleCredit.recordAudio(this, loanReference, null, dockedButton)
        }
    }

    private fun generateReport(loanReference: String) {
        if (loanReference.isEmpty()) {
            Toast.makeText(this, "No loan reference — cannot generate report", Toast.LENGTH_SHORT).show()
            return
        }
        AbleCredit.requestReportGeneration(this, loanReference) { result ->
            when (result) {
                is AbleCreditResult.Success -> Toast.makeText(this, "Report requested successfully", Toast.LENGTH_SHORT).show()
                is AbleCreditResult.Failure -> Toast.makeText(this, "Report failed: ${result.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
