package com.ablecredit.manualkotlinclient.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import com.ablecredit.manualkotlinclient.data.repository.WrapperSettingsRepository
import com.ablecredit.manualkotlinclient.ui.main.dialog.CreateLoanDialog
import com.ablecredit.manualkotlinclient.ui.main.dialog.FlowConfigDialog
import com.ablecredit.sdk.model.AbleCreditLoanResponse
import com.ablecredit.sdk.model.AbleCreditResult
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import java.text.DateFormat

class HomeFragment : Fragment() {

    companion object {
        private const val TAG = "AbleCreditWrapperFlow"
        private const val ACTION_AUDIO = "audio"
        private const val ACTION_BUSINESS_PHOTO = "business_photo"
        private const val ACTION_COLLATERAL_PHOTO = "collateral_photo"
        private const val ACTION_FAMILY_PHOTO = "family_photo"
        private const val ACTION_REPORT = "report"
    }

    private lateinit var ableCreditRepository: AbleCreditRepository
    private lateinit var loanCaseRepository: LoanCaseRepository
    private lateinit var sdkConfigRepository: SdkConfigRepository
    private lateinit var wrapperSettingsRepository: WrapperSettingsRepository

    private lateinit var fetchLoanReferenceInput: TextInputEditText
    private lateinit var fetchLoanByIdInput: TextInputEditText
    private lateinit var loanCardsContainer: LinearLayout
    private lateinit var createLoanButton: Button
    private var createLoanProgressDialog: AlertDialog? = null
    private val visibleActions = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ableCreditRepository = AbleCreditRepository(requireActivity().application)
        loanCaseRepository = LoanCaseRepository(requireContext())
        sdkConfigRepository = SdkConfigRepository(requireContext())
        wrapperSettingsRepository = WrapperSettingsRepository(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fetchLoanReferenceInput = view.findViewById(R.id.et_fetch_loan_reference)
        fetchLoanByIdInput = view.findViewById(R.id.et_fetch_loan_by_id)
        loanCardsContainer = view.findViewById(R.id.layout_loan_cards)
        createLoanButton = view.findViewById(R.id.btn_create_loan)
        val fetchButton = view.findViewById<Button>(R.id.btn_fetch_loan)
        val fetchByIdButton = view.findViewById<Button>(R.id.btn_fetch_loan_by_id)

        fetchButton.setOnClickListener { fetchLoanCase() }
        fetchByIdButton.setOnClickListener { fetchLoanCaseById() }
        createLoanButton.setOnClickListener { showCreateLoanDialog() }
        view.findViewById<View>(R.id.btn_test_flows).setOnClickListener {
            startActivity(com.ablecredit.manualkotlinclient.ui.testflows.TestFlowsActivity.createIntent(requireContext()))
        }

        setupDirectCallSection(view)
        initFilters(view)
        renderLoanCards()
    }

    override fun onResume() {
        super.onResume()
        renderLoanCards()
    }

    override fun onDestroyView() {
        dismissCreateLoanProgress()
        super.onDestroyView()
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
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_new_loan_choice, null)
        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_SdkConfigDialog)
            .setView(view)
            .create()
        view.findViewById<View>(R.id.btn_create_loan_only).setOnClickListener {
            dialog.dismiss()
            showCreateLoanOnlyDialog()
        }
        view.findViewById<View>(R.id.btn_create_and_run_flow).setOnClickListener {
            dialog.dismiss()
            showCreateLoanThroughFlow()
        }
        view.findViewById<View>(R.id.btn_choice_cancel).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showCreateLoanOnlyDialog() {
        CreateLoanDialog.show(requireActivity() as AppCompatActivity) { loanRef, userName, branch, product, businessModel ->
            setCreateLoading(true)
            showCreateLoanProgress()
            ableCreditRepository.createNewLoanCase(
                SampleLoanCaseData.buildCreateLoanPayload(loanRef, userName, branch, product, businessModel)
            ) { result ->
                requireActivity().runOnUiThread { handleCreateLoanResult(result, loanRef) }
            }
        }
    }

    private fun showCreateLoanProgress() {
        dismissCreateLoanProgress()
        val padding = (24 * resources.displayMetrics.density).toInt()
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        val pb = ProgressBar(requireContext()).apply {
            isIndeterminate = true
            val size = (28 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size)
        }
        row.addView(pb)
        val tv = TextView(requireContext()).apply {
            setText(R.string.creating_loan_loader)
            textSize = 14f
            setPadding(padding, 0, 0, 0)
        }
        row.addView(tv)
        createLoanProgressDialog = AlertDialog.Builder(requireContext())
            .setView(row)
            .setCancelable(false)
            .create()
        createLoanProgressDialog?.show()
    }

    private fun dismissCreateLoanProgress() {
        createLoanProgressDialog?.dismiss()
        createLoanProgressDialog = null
    }

    private fun showCreateLoanThroughFlow() {
        FlowConfigDialog.show(requireActivity() as AppCompatActivity) { config ->
            ableCreditRepository.startLoanFlow(requireContext(), config)
        }
    }

    private fun handleCreateLoanResult(
        result: AbleCreditResult<AbleCreditLoanResponse>,
        fallbackLoanReference: String
    ) {
        setCreateLoading(false)
        dismissCreateLoanProgress()
        when (result) {
            is AbleCreditResult.Success -> {
                val applicationId = extractApplicationId(result.data)
                Log.d(TAG, "Loan created SUCCESS | applicationId=$applicationId, loanReference=$fallbackLoanReference")
                if (applicationId.isEmpty()) {
                    wrapperToast("Loan created but loan reference is unavailable", true)
                    renderLoanCards()
                    return
                }
                loanCaseRepository.saveOrUpdate(LoanCaseItem(applicationId, fallbackLoanReference, System.currentTimeMillis()))
                wrapperToast("Loan case created", false)
                renderLoanCards()
            }
            is AbleCreditResult.Failure -> {
                Log.e(TAG, "Loan created FAILED | code=${result.ableCreditErrorCode}, message=${result.message}")
                wrapperFailure(result.message)
            }
            else -> {}
        }
    }

    private fun setCreateLoading(loading: Boolean) {
        createLoanButton.isEnabled = !loading
        createLoanButton.text = if (loading) "Creating..." else "+ New loan"
    }

    private fun wrapperToast(message: String, longDuration: Boolean) {
        if (!wrapperSettingsRepository.isWrapperToastsEnabled()) return
        Toast.makeText(
            requireContext(), message,
            if (longDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        ).show()
    }

    private fun wrapperSnackbar(message: String) {
        if (!wrapperSettingsRepository.isWrapperToastsEnabled()) return
        val root = view ?: return
        Snackbar.make(root, message, Snackbar.LENGTH_LONG).setTextMaxLines(6).show()
    }

    private fun wrapperFailure(message: String?) {
        if (!wrapperSettingsRepository.isWrapperToastsEnabled()) return
        val text = if (!message.isNullOrEmpty()) message else "Operation failed"
        val root = view ?: return
        Snackbar.make(root, text, Snackbar.LENGTH_INDEFINITE)
            .setTextMaxLines(6)
            .setAction("Dismiss") {}
            .show()
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
        val copyButton = cardView.findViewById<ImageButton>(R.id.btn_copy_loan_reference)
        val moreButton = cardView.findViewById<ImageButton>(R.id.btn_more_actions)
        val reportProgress = cardView.findViewById<ProgressBar>(R.id.progress_report)

        title.text = if (loanCaseItem.loanReference.isEmpty()) "Loan Reference: not available"
                     else loanCaseItem.loanReference

        copyButton.visibility = if (loanCaseItem.loanReference.isEmpty()) View.GONE else View.VISIBLE
        copyButton.setOnClickListener { copyLoanReference(loanCaseItem.loanReference) }

        val createdTime = DateFormat.getDateTimeInstance().format(loanCaseItem.createdAt)
        subTitle.text = "${loanCaseItem.applicationId}  ·  $createdTime"

        actionChipGroup.removeAllViews()
        maybeAddActionChip(actionChipGroup, loanCaseItem, ACTION_AUDIO, "Audio", reportProgress)
        maybeAddActionChip(actionChipGroup, loanCaseItem, ACTION_BUSINESS_PHOTO, "Business", reportProgress)
        maybeAddActionChip(actionChipGroup, loanCaseItem, ACTION_COLLATERAL_PHOTO, "Collateral", reportProgress)
        maybeAddActionChip(actionChipGroup, loanCaseItem, ACTION_FAMILY_PHOTO, "Family", reportProgress)
        maybeAddActionChip(actionChipGroup, loanCaseItem, ACTION_REPORT, "Report", reportProgress)
        addCompleteFlowChip(actionChipGroup, loanCaseItem)

        moreButton.setOnClickListener { showActionMenu(it, loanCaseItem, reportProgress) }
    }

    private fun copyLoanReference(loanReference: String) {
        if (loanReference.isEmpty()) return
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Loan reference", loanReference))
        Log.d(TAG, "Loan reference copied to clipboard | loanReference='$loanReference'")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(requireContext(), "Loan reference copied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addCompleteFlowChip(group: ChipGroup, loanCaseItem: LoanCaseItem) {
        val chip = Chip(requireContext()).apply {
            text = "Complete flow"
            isCheckable = false
            isClickable = true
            setChipBackgroundColorResource(R.color.black)
            chipStrokeWidth = 0f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            setOnClickListener { startCompleteFlow(loanCaseItem) }
        }
        group.addView(chip)
    }

    private fun startCompleteFlow(loanCaseItem: LoanCaseItem) {
        FlowConfigDialog.show(
            requireActivity() as AppCompatActivity,
            loanCaseItem.applicationId
        ) { config -> ableCreditRepository.startLoanFlow(requireContext(), config) }
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
        val appId = loanCaseItem.applicationId
        when (actionId) {
            ACTION_AUDIO -> {
                Log.d(TAG, "Audio capture triggered | loanApplicationId=$appId")
                ableCreditRepository.recordAudio(appId)
            }
            ACTION_BUSINESS_PHOTO -> {
                Log.d(TAG, "Business photo capture triggered | loanApplicationId=$appId")
                ableCreditRepository.captureBusinessPhotos(appId)
            }
            ACTION_COLLATERAL_PHOTO -> {
                Log.d(TAG, "Collateral photo capture triggered | loanApplicationId=$appId")
                ableCreditRepository.captureCollateralPhotos(appId)
            }
            ACTION_FAMILY_PHOTO -> {
                Log.d(TAG, "Family photo capture triggered | loanApplicationId=$appId")
                ableCreditRepository.captureFamilyPhotos(appId)
            }
            ACTION_REPORT -> {
                Log.d(TAG, "Report generation triggered | loanApplicationId=$appId")
                reportProgress.visibility = View.VISIBLE
                ableCreditRepository.requestReportGeneration(appId) { result ->
                    requireActivity().runOnUiThread {
                        reportProgress.visibility = View.GONE
                        when (result) {
                            is AbleCreditResult.Success -> {
                                Log.d(TAG, "Report generation SUCCESS | loanApplicationId=$appId")
                                wrapperToast("Report request submitted", false)
                            }
                            is AbleCreditResult.Failure -> {
                                Log.e(TAG, "Report generation FAILED | loanApplicationId=$appId, code=${result.ableCreditErrorCode}, message=${result.message}")
                                wrapperFailure(result.message)
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }

    private fun setupDirectCallSection(root: View) {
        val reportProgress = root.findViewById<ProgressBar>(R.id.progress_direct_report)

        root.findViewById<View>(R.id.chip_direct_record_audio).setOnClickListener {
            promptDirectLoanApplicationId("Record audio") { appId ->
                Log.d(TAG, "Direct recordAudio triggered | loanApplicationId='$appId'")
                ableCreditRepository.recordAudio(appId)
            }
        }
        root.findViewById<View>(R.id.chip_direct_business_photos).setOnClickListener {
            promptDirectLoanApplicationId("Capture business photos") { appId ->
                Log.d(TAG, "Direct captureBusinessPhotos triggered | loanApplicationId='$appId'")
                ableCreditRepository.captureBusinessPhotos(appId)
            }
        }
        root.findViewById<View>(R.id.chip_direct_collateral_photos).setOnClickListener {
            promptDirectLoanApplicationId("Capture collateral photos") { appId ->
                Log.d(TAG, "Direct captureCollateralPhotos triggered | loanApplicationId='$appId'")
                ableCreditRepository.captureCollateralPhotos(appId)
            }
        }
        root.findViewById<View>(R.id.chip_direct_family_photos).setOnClickListener {
            promptDirectLoanApplicationId("Capture family photos") { appId ->
                Log.d(TAG, "Direct captureFamilyPhotos triggered | loanApplicationId='$appId'")
                ableCreditRepository.captureFamilyPhotos(appId)
            }
        }
        root.findViewById<View>(R.id.chip_direct_generate_report).setOnClickListener {
            promptDirectLoanApplicationId("Generate report") { appId ->
                Log.d(TAG, "Direct requestReportGeneration triggered | loanApplicationId='$appId'")
                reportProgress.visibility = View.VISIBLE
                ableCreditRepository.requestReportGeneration(appId) { result ->
                    requireActivity().runOnUiThread {
                        reportProgress.visibility = View.GONE
                        when (result) {
                            is AbleCreditResult.Success -> {
                                Log.d(TAG, "Direct report SUCCESS | loanApplicationId='$appId'")
                                wrapperToast("Report request submitted", false)
                            }
                            is AbleCreditResult.Failure -> {
                                Log.e(TAG, "Direct report FAILED | loanApplicationId='$appId', code=${result.ableCreditErrorCode}, message=${result.message}")
                                wrapperFailure(result.message)
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }

    private fun promptDirectLoanApplicationId(actionLabel: String, callback: (String) -> Unit) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_direct_loan_reference, null)
        val input = dialogView.findViewById<TextInputEditText>(R.id.et_direct_loan_reference)
        MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_SdkConfigDialog)
            .setTitle("$actionLabel (direct call)")
            .setView(dialogView)
            .setPositiveButton(actionLabel) { _, _ ->
                val appId = input.text?.toString()?.trim() ?: ""
                callback(appId)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun fetchLoanCase() {
        val loanRef = fetchLoanReferenceInput.text?.toString()?.trim() ?: ""
        if (loanRef.isEmpty()) {
            Toast.makeText(requireContext(), "Enter a loan reference first", Toast.LENGTH_SHORT).show()
            return
        }
        ableCreditRepository.getLoanByReference(loanRef) { result ->
            requireActivity().runOnUiThread { handleFetchResult(loanRef, result) }
        }
    }

    private fun fetchLoanCaseById() {
        val applicationId = fetchLoanByIdInput.text?.toString()?.trim() ?: ""
        if (applicationId.isEmpty()) {
            Toast.makeText(requireContext(), "Enter a loan ID first", Toast.LENGTH_SHORT).show()
            return
        }
        ableCreditRepository.getLoanById(applicationId) { result ->
            requireActivity().runOnUiThread { handleFetchResult(applicationId, result) }
        }
    }

    private fun handleFetchResult(
        loanApplicationId: String,
        result: AbleCreditResult<AbleCreditLoanResponse>
    ) {
        when (result) {
            is AbleCreditResult.Success -> {
                val appId = extractApplicationId(result.data)
                val loanRef = try {
                    result.data?.data?.application?.loan_reference ?: ""
                } catch (_: Exception) { "" }
                Log.d(TAG, "Fetch loan SUCCESS | applicationId=$appId, loanApplicationId=$loanApplicationId")
                if (appId.isNotEmpty()) {
                    loanCaseRepository.saveOrUpdate(LoanCaseItem(appId, loanRef, System.currentTimeMillis()))
                    renderLoanCards()
                }
                AlertDialog.Builder(requireContext())
                    .setTitle("Fetch successful")
                    .setMessage(result.data?.data?.toString() ?: "No payload")
                    .setPositiveButton("OK", null)
                    .show()
            }
            is AbleCreditResult.Failure -> {
                Log.e(TAG, "Fetch loan FAILED | loanApplicationId=$loanApplicationId, code=${result.ableCreditErrorCode}, message=${result.message}")
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
