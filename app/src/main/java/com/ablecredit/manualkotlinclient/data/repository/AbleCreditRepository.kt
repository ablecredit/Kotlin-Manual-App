package com.ablecredit.manualkotlinclient.data.repository

import android.app.Application
import android.widget.Toast
import com.ablecredit.sdk.manager.AbleCredit
import com.ablecredit.sdk.model.AbleCreditFileStatus
import com.ablecredit.sdk.model.AbleCreditFileUploadListener
import com.ablecredit.sdk.model.AbleCreditLoanResponse
import com.ablecredit.sdk.model.AbleCreditResult
import kotlin.Unit

class AbleCreditRepository(private val application: Application) {

    fun configure(
        apiKey: String,
        tenantId: String,
        userId: String,
        baseUrl: String,
        branchId: String? = null
    ): AbleCreditResult<Boolean> =
        AbleCredit.configure(application, apiKey, tenantId, userId, baseUrl, branchId)

    fun clearSdkData() {
        AbleCredit.clearSdkData { Unit }
    }

    fun createNewLoanCase(
        payload: Map<String, Any>,
        onResult: (AbleCreditResult<AbleCreditLoanResponse>) -> Unit
    ) {
        AbleCredit.createNewLoanCase(application, payload, onResult)
    }

    fun fetchLoanDetails(
        loanReference: String,
        onResult: (AbleCreditResult<AbleCreditLoanResponse>) -> Unit
    ) {
        AbleCredit.fetchLoanDetails(application, loanReference, onResult)
    }

    fun viewLoanApplications() {
        AbleCredit.viewLoanApplications(application)
    }

    fun recordAudio(loanApplicationId: String) {
        AbleCredit.recordAudio(application, loanApplicationId, object : AbleCreditFileUploadListener {
            override fun onStatusChanged(uniqueId: String, status: AbleCreditFileStatus, message: String?) {
                if (status == AbleCreditFileStatus.FAILED && message != null) {
                    Toast.makeText(application, message, Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    fun captureBusinessPhotos(loanApplicationId: String) {
        AbleCredit.captureBusinessPhotos(application, loanApplicationId, photoUploadListener())
    }

    fun captureCollateralPhotos(loanApplicationId: String) {
        AbleCredit.captureCollateralPhotos(application, loanApplicationId, photoUploadListener())
    }

    fun captureFamilyPhotos(loanApplicationId: String) {
        AbleCredit.captureFamilyPhotos(application, loanApplicationId, photoUploadListener())
    }

    private fun photoUploadListener() = object : AbleCreditFileUploadListener {
        override fun onStatusChanged(uniqueId: String, status: AbleCreditFileStatus, message: String?) {
            if (status == AbleCreditFileStatus.FAILED && message != null) {
                Toast.makeText(application, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun requestReportGeneration(
        loanApplicationId: String,
        onResult: (AbleCreditResult<Unit>) -> Unit
    ) {
        AbleCredit.requestReportGeneration(application, loanApplicationId, onResult)
    }
}
