package com.ablecredit.manualkotlinclient.data.model

/** How a loan on the Home tab came to be — drives the badge and what a tap on the card resumes. */
enum class LoanCreationSource { DIRECT, ORCHESTRATOR }

/**
 * Enough of an orchestrator flow to re-run it on the already-created loan (withExistingLoan).
 * Stored for loans created from the Scenarios tab. [hostScreens] is "single" or "chain"
 * (what a HOST step shows).
 */
data class OrchestratorFlowSnapshot(
    val steps: List<String>,
    val hostScreens: String = "single",
    val minAudioCount: Int = 0,
    val minPhotoCount: Int = 0,
    val generateReportOnSummary: Boolean = false
)

data class LoanCaseItem(
    val applicationId: String,
    val loanReference: String,
    val createdAt: Long,
    val source: LoanCreationSource = LoanCreationSource.DIRECT,
    val flow: OrchestratorFlowSnapshot? = null
)
