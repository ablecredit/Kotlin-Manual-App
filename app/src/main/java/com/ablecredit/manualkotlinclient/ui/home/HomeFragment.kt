package com.ablecredit.manualkotlinclient.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.ablecredit.manualkotlinclient.R
import com.ablecredit.manualkotlinclient.data.SampleLoanCaseData
import com.ablecredit.manualkotlinclient.data.model.LoanCaseItem
import com.ablecredit.manualkotlinclient.data.repository.AbleCreditRepository
import com.ablecredit.manualkotlinclient.data.repository.LoanCaseRepository
import com.ablecredit.manualkotlinclient.data.repository.SdkConfigRepository
import com.ablecredit.manualkotlinclient.ui.main.dialog.CreateLoanDialog
import com.ablecredit.sdk.model.AbleCreditLoanResponse
import com.ablecredit.sdk.model.AbleCreditResult
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import java.text.DateFormat

class HomeFragment : Fragment() {

    companion object {
        private const val ACTION_AUDIO = "audio"
        private const val ACTION_BUSINESS_PHOTO = "business_photo"
        private const val ACTION_COLLATERAL_PHOTO = "collateral_photo"
        private const val ACTION_FAMILY_PHOTO = "family_photo"
        private const val ACTION_REPORT = "report"
    }

    private lateinit var ableCreditRepository: AbleCreditRepository
    private lateinit var loanCaseRepository: LoanCaseRepository
    private lateinit var sdkConfigRepository: SdkConfigRepository

    private lateinit var fetchLoanReferenceInput: TextInputEditText
    private lateinit var loanCardsContainer: LinearLayout
    private lateinit var createLoanButton: Button
    private val visibleActions = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ableCreditRepository = AbleCreditRepository(requireActivity().application)
        loanCaseRepository = LoanCaseRepository(requireContext())
        sdkConfigRepository = SdkConfigRepository(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fetchLoanReferenceInput = view.findViewById(R.id.et_fetch_loan_reference)
        loanCardsContainer = view.findViewById(R.id.layout_loan_cards)
        createLoanButton = view.findViewById(R.id.btn_create_loan)
        val fetchButton = view.findViewById<Button>(R.id.btn_fetch_loan)

        fetchButton.setOnClickListener { fetchLoanCase() }
        createLoanButton.setOnClickListener { showCreateLoanDialog() }

        initFilters(view)
        renderLoanCards()
    }

    override fun onResume() {
        super.onResume()
        renderLoanCards()
    }

    private fun initFilters(root: View) {
        val switchView = root.findViewById<SwitchMaterial>(R.id.switch_filters)
        val enabled = sdkConfigRepository.isFiltersEnabled()
        switchView.isChecked = enabled
        applyFilter(enabled)
        switchView.setOnCheckedChangeListener { _, isChecked ->
            sdkConfigRepository.setFiltersEnabled(isChecked)
            applyFilter(isChecked)
            renderLoanCards()
        }
    }

    private fun applyFilter(enabled: Boolean) {
        visibleActions.clear()
        if (enabled) {
            visibleActions.addAll(
                listOf(ACTION_AUDIO, ACTION_BUSINESS_PHOTO, ACTION_COLLATERAL_PHOTO, ACTION_FAMILY_PHOTO, ACTION_REPORT)
            )
        }
    }

    private fun showCreateLoanDialog() {
        CreateLoanDialog.show(requireActivity() as AppCompatActivity) { loanRef, userName, branch, product, businessModel ->
            setCreateLoading(true)
            ableCreditRepository.createNewLoanCase(
                SampleLoanCaseData.buildCreateLoanPayload(loanRef, userName, branch, product, businessModel)
            ) { result ->
                requireActivity().runOnUiThread { handleCreateLoanResult(result, loanRef) }
            }
        }
    }

    private fun handleCreateLoanResult(
        result: AbleCreditResult<AbleCreditLoanResponse>,
        fallbackLoanReference: String
    ) {
        setCreateLoading(false)
        when (result) {
            is AbleCreditResult.Success -> {
                val applicationId = extractApplicationId(result.data)
                if (applicationId.isEmpty()) {
                    Toast.makeText(requireContext(), "Loan created but application id is unavailable", Toast.LENGTH_LONG).show()
                    renderLoanCards()
                    return
                }
                loanCaseRepository.saveOrUpdate(LoanCaseItem(applicationId, fallbackLoanReference, System.currentTimeMillis()))
                Toast.makeText(requireContext(), "Loan case created", Toast.LENGTH_SHORT).show()
                renderLoanCards()
            }
            is AbleCreditResult.Failure -> {
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
    }

    private fun setCreateLoading(loading: Boolean) {
        createLoanButton.isEnabled = !loading
        createLoanButton.text = if (loading) "Creating..." else "+ New loan"
    }

    private fun renderLoanCards() {
        loanCardsContainer.removeAllViews()
        val loans = loanCaseRepository.getAll()
        if (loans.isEmpty()) {
            val emptyState = TextView(requireContext()).apply {
                text = "No loan cases yet. Tap \"+ New loan\" to create one."
                setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_600))
                textSize = 14f
            }
            loanCardsContainer.addView(emptyState)
            return
        }
        val inflater = LayoutInflater.from(requireContext())
        for (loan in loans) {
            val cardView = inflater.inflate(R.layout.item_loan_case_card, loanCardsContainer, false)
            bindCard(cardView, loan)
            loanCardsContainer.addView(cardView)
        }
    }

    private fun bindCard(cardView: View, loanCaseItem: LoanCaseItem) {
        val title = cardView.findViewById<TextView>(R.id.tv_loan_reference)
        val subTitle = cardView.findViewById<TextView>(R.id.tv_loan_meta)
        val actionChipGroup = cardView.findViewById<ChipGroup>(R.id.chip_group_card_actions)
        val moreButton = cardView.findViewById<ImageButton>(R.id.btn_more_actions)
        val reportProgress = cardView.findViewById<ProgressBar>(R.id.progress_report)

        title.text = if (loanCaseItem.loanReference.isEmpty()) "Loan Reference: not available"
                     else loanCaseItem.loanReference

        val createdTime = DateFormat.getDateTimeInstance().format(loanCaseItem.createdAt)
        subTitle.text = "${loanCaseItem.applicationId}  ·  $createdTime"

        actionChipGroup.removeAllViews()
        maybeAddActionChip(actionChipGroup, loanCaseItem, ACTION_AUDIO, "Audio", reportProgress)
        maybeAddActionChip(actionChipGroup, loanCaseItem, ACTION_BUSINESS_PHOTO, "Business", reportProgress)
        maybeAddActionChip(actionChipGroup, loanCaseItem, ACTION_COLLATERAL_PHOTO, "Collateral", reportProgress)
        maybeAddActionChip(actionChipGroup, loanCaseItem, ACTION_FAMILY_PHOTO, "Family", reportProgress)
        maybeAddActionChip(actionChipGroup, loanCaseItem, ACTION_REPORT, "Report", reportProgress)

        moreButton.setOnClickListener { showActionMenu(it, loanCaseItem, reportProgress) }
    }

    private fun maybeAddActionChip(
        group: ChipGroup,
        loanCaseItem: LoanCaseItem,
        actionId: String,
        label: String,
        reportProgress: ProgressBar
    ) {
        if (!visibleActions.contains(actionId)) return
        val chip = Chip(requireContext()).apply {
            text = label
            isCheckable = false
            isClickable = true
            setChipBackgroundColorResource(R.color.white)
            setChipStrokeColorResource(R.color.black)
            chipStrokeWidth = resources.getDimension(R.dimen.chip_stroke_width)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
            setOnClickListener { executeAction(loanCaseItem, actionId, reportProgress) }
        }
        group.addView(chip)
    }

    private fun showActionMenu(anchor: View, loanCaseItem: LoanCaseItem, reportProgress: ProgressBar) {
        PopupMenu(requireContext(), anchor).apply {
            menu.add(0, 1, 1, "Record audio")
            menu.add(0, 2, 2, "Capture business photos")
            menu.add(0, 3, 3, "Capture collateral photos")
            menu.add(0, 4, 4, "Capture family photos")
            menu.add(0, 5, 5, "Generate report")
            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    1 -> { executeAction(loanCaseItem, ACTION_AUDIO, reportProgress); true }
                    2 -> { executeAction(loanCaseItem, ACTION_BUSINESS_PHOTO, reportProgress); true }
                    3 -> { executeAction(loanCaseItem, ACTION_COLLATERAL_PHOTO, reportProgress); true }
                    4 -> { executeAction(loanCaseItem, ACTION_FAMILY_PHOTO, reportProgress); true }
                    5 -> { executeAction(loanCaseItem, ACTION_REPORT, reportProgress); true }
                    else -> false
                }
            }
            show()
        }
    }

    private fun executeAction(loanCaseItem: LoanCaseItem, actionId: String, reportProgress: ProgressBar) {
        when (actionId) {
            ACTION_AUDIO -> ableCreditRepository.recordAudio(loanCaseItem.applicationId)
            ACTION_BUSINESS_PHOTO -> ableCreditRepository.captureBusinessPhotos(loanCaseItem.applicationId)
            ACTION_COLLATERAL_PHOTO -> ableCreditRepository.captureCollateralPhotos(loanCaseItem.applicationId)
            ACTION_FAMILY_PHOTO -> ableCreditRepository.captureFamilyPhotos(loanCaseItem.applicationId)
            ACTION_REPORT -> {
                reportProgress.visibility = View.VISIBLE
                ableCreditRepository.requestReportGeneration(loanCaseItem.applicationId) { result ->
                    requireActivity().runOnUiThread {
                        reportProgress.visibility = View.GONE
                        when (result) {
                            is AbleCreditResult.Success ->
                                Toast.makeText(requireContext(), "Report request submitted", Toast.LENGTH_SHORT).show()
                            is AbleCreditResult.Failure ->
                                Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                            else -> {}
                        }
                    }
                }
            }
        }
    }

    private fun fetchLoanCase() {
        val loanRef = fetchLoanReferenceInput.text?.toString()?.trim() ?: ""
        if (loanRef.isEmpty()) {
            Toast.makeText(requireContext(), "Enter a loan reference first", Toast.LENGTH_SHORT).show()
            return
        }
        ableCreditRepository.fetchLoanDetails(loanRef) { result ->
            requireActivity().runOnUiThread { handleFetchResult(loanRef, result) }
        }
    }

    private fun handleFetchResult(
        loanReference: String,
        result: AbleCreditResult<AbleCreditLoanResponse>
    ) {
        when (result) {
            is AbleCreditResult.Success -> {
                val appId = extractApplicationId(result.data)
                if (appId.isNotEmpty()) {
                    loanCaseRepository.saveOrUpdate(LoanCaseItem(appId, loanReference, System.currentTimeMillis()))
                    renderLoanCards()
                }
                AlertDialog.Builder(requireContext())
                    .setTitle("Fetch successful")
                    .setMessage(result.data?.data?.toString() ?: "No payload")
                    .setPositiveButton("OK", null)
                    .show()
            }
            is AbleCreditResult.Failure -> {
                AlertDialog.Builder(requireContext())
                    .setTitle("Fetch failed (${result.ableCreditErrorCode})")
                    .setMessage(result.message)
                    .setPositiveButton("OK", null)
                    .show()
            }
            else -> {}
        }
    }

    private fun extractApplicationId(response: AbleCreditLoanResponse?): String {
        return try {
            response?.data?.application?._id ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}
