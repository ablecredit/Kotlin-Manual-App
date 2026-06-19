package com.ablecredit.manualkotlinclient.ui.main.dialog

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ablecredit.manualkotlinclient.R
import com.ablecredit.manualkotlinclient.data.SampleLoanCaseData
import com.ablecredit.manualkotlinclient.data.model.LoanCaseItem
import com.ablecredit.manualkotlinclient.data.repository.DropdownItemsRepository
import com.ablecredit.manualkotlinclient.data.repository.FlowStepsRepository
import com.ablecredit.manualkotlinclient.data.repository.LoanCaseRepository
import com.ablecredit.manualkotlinclient.data.repository.WrapperSettingsRepository
import com.ablecredit.sdk.model.AbleCreditFileStatus
import com.ablecredit.sdk.model.AbleCreditFlowConfig
import com.ablecredit.sdk.model.AbleCreditFlowListener
import com.ablecredit.sdk.model.AbleCreditFlowStep
import com.ablecredit.sdk.model.AbleCreditLoanResponse
import com.ablecredit.sdk.model.AbleCreditResult
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FlowConfigDialog {
    private const val TAG = "AbleCreditWrapperFlow"

    fun interface OnFlowConfigReady {
        fun onConfig(config: AbleCreditFlowConfig)
    }

    fun show(activity: AppCompatActivity, onConfig: OnFlowConfigReady) = show(activity, null, onConfig)

    fun show(activity: AppCompatActivity, existingLoanReference: String?, onConfig: OnFlowConfigReady) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_flow_config, null)

        val toggleStartMode = dialogView.findViewById<MaterialButtonToggleGroup>(R.id.toggle_start_mode)
        val sectionNewLoan = dialogView.findViewById<LinearLayout>(R.id.section_new_loan)
        val sectionExistingLoan = dialogView.findViewById<LinearLayout>(R.id.section_existing_loan)
        val etLoanReference = dialogView.findViewById<TextInputEditText>(R.id.et_loan_reference)
        val etExistingLoanRef = dialogView.findViewById<TextInputEditText>(R.id.et_existing_loan_reference)
        val actProduct = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.act_product)
        val actBusinessModel = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.act_business_model)
        val btnAddProduct = dialogView.findViewById<MaterialButton>(R.id.btn_add_product)
        val btnAddBusinessModel = dialogView.findViewById<MaterialButton>(R.id.btn_add_business_model)
        val etUserName = dialogView.findViewById<TextInputEditText>(R.id.et_user_name)
        val etBranchName = dialogView.findViewById<TextInputEditText>(R.id.et_branch_name)

        val dropdownRepo = DropdownItemsRepository(activity)
        val productList = dropdownRepo.getProducts().toMutableList()
        val businessModelList = dropdownRepo.getBusinessModels().toMutableList()

        val productAdapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, productList)
        actProduct.setAdapter(productAdapter)
        if (productList.isNotEmpty()) actProduct.setText(productList[0], false)

        val businessAdapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, businessModelList)
        actBusinessModel.setAdapter(businessAdapter)
        if (businessModelList.isNotEmpty()) actBusinessModel.setText(businessModelList[0], false)

        btnAddProduct.setOnClickListener {
            showAddItemDialog(activity, "Add product", "e.g. Home Loan") { newItem ->
                dropdownRepo.addProduct(newItem)
                productAdapter.add(newItem)
                productAdapter.notifyDataSetChanged()
                actProduct.setText(newItem, false)
            }
        }
        btnAddBusinessModel.setOnClickListener {
            showAddItemDialog(activity, "Add business model", "e.g. Retail") { newItem ->
                dropdownRepo.addBusinessModel(newItem)
                businessAdapter.add(newItem)
                businessAdapter.notifyDataSetChanged()
                actBusinessModel.setText(newItem, false)
            }
        }

        val steps = FlowStepsRepository(activity).getSteps()

        val datePart = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val suffix = String.format(Locale.US, "%04d", System.currentTimeMillis() % 10000)
        etLoanReference.setText("LN-REF-$datePart-$suffix")

        if (!existingLoanReference.isNullOrEmpty()) {
            etExistingLoanRef.setText(existingLoanReference)
            toggleStartMode.check(R.id.btn_mode_existing_loan)
            sectionNewLoan.visibility = View.GONE
            sectionExistingLoan.visibility = View.VISIBLE
        } else {
            toggleStartMode.check(R.id.btn_mode_new_loan)
        }

        toggleStartMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.btn_mode_new_loan -> { sectionNewLoan.visibility = View.VISIBLE; sectionExistingLoan.visibility = View.GONE }
                R.id.btn_mode_existing_loan -> { sectionNewLoan.visibility = View.GONE; sectionExistingLoan.visibility = View.VISIBLE }
            }
        }

        val dialog = MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_SdkConfigDialog)
            .setView(dialogView)
            .create()

        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btn_flow_cancel)
        val btnStart = dialogView.findViewById<MaterialButton>(R.id.btn_flow_start)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnStart.setOnClickListener {
            if (steps.isEmpty()) {
                Toast.makeText(activity, "No flow steps configured. Set them in Profile.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val isNewLoan = toggleStartMode.checkedButtonId == R.id.btn_mode_new_loan
            val loanReferenceForPersist = if (isNewLoan) etLoanReference.text?.toString()?.trim() ?: "" else ""
            val hasCreateLoanCase = steps.contains(AbleCreditFlowStep.CREATE_LOAN_CASE)
            if (isNewLoan && !hasCreateLoanCase) {
                Toast.makeText(activity, "\"Create loan case\" step is disabled in Profile settings.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val normalizedSteps = normalizeSteps(steps, isNewLoan)
            if (normalizedSteps.isEmpty()) {
                Toast.makeText(activity, "No steps configured for existing-loan flow.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            try {
                val builder = AbleCreditFlowConfig.Builder()
                    .steps(*normalizedSteps.toTypedArray())
                    .listener(buildFlowListener(activity, loanReferenceForPersist))
                val config = if (isNewLoan) {
                    if (loanReferenceForPersist.isEmpty()) { Toast.makeText(activity, "Enter a loan reference", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                    val product = actProduct.text?.toString()?.trim() ?: ""
                    val businessModel = actBusinessModel.text?.toString()?.trim() ?: ""
                    if (product.isEmpty() || businessModel.isEmpty()) { Toast.makeText(activity, "Select product and business model", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                    val userName = etUserName.text?.toString()?.trim() ?: ""
                    val branchName = etBranchName.text?.toString()?.trim() ?: ""
                    val payload = SampleLoanCaseData.buildCreateLoanPayload(loanReferenceForPersist, userName, branchName, product, businessModel)
                    builder.withNewLoan(payload)
                } else {
                    val existingRef = etExistingLoanRef.text?.toString()?.trim() ?: ""
                    if (existingRef.isEmpty()) { Toast.makeText(activity, "Enter a loan reference", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                    builder.withExistingLoan(existingRef)
                }
                dialog.dismiss()
                onConfig.onConfig(config)
            } catch (e: IllegalArgumentException) {
                Toast.makeText(activity, e.message, Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun normalizeSteps(steps: List<AbleCreditFlowStep>, isNewLoan: Boolean): List<AbleCreditFlowStep> {
        val base = if (isNewLoan) {
            val l = mutableListOf(AbleCreditFlowStep.CREATE_LOAN_CASE)
            steps.filter { it != AbleCreditFlowStep.CREATE_LOAN_CASE }.forEach { l.add(it) }
            l
        } else {
            steps.filter { it != AbleCreditFlowStep.CREATE_LOAN_CASE }.toMutableList()
        }
        val withoutReport = base.filter { it != AbleCreditFlowStep.GENERATE_REPORT }.toMutableList()
        if (base.contains(AbleCreditFlowStep.GENERATE_REPORT)) withoutReport.add(AbleCreditFlowStep.GENERATE_REPORT)
        return withoutReport
    }

    private fun buildFlowListener(activity: AppCompatActivity, loanReference: String): AbleCreditFlowListener {
        val loanCaseRepository = LoanCaseRepository(activity.applicationContext)
        val wrapperSettings = WrapperSettingsRepository(activity.applicationContext)
        return object : AbleCreditFlowListener {
            override fun onLoanCreated(applicationId: String, response: AbleCreditLoanResponse) {
                val ref = response.data?.application?.loan_reference?.takeIf { it.isNotEmpty() } ?: loanReference
                Log.d(TAG, "Loan created SUCCESS | applicationId=$applicationId, loanReference=$ref")
                loanCaseRepository.saveOrUpdate(LoanCaseItem(applicationId, ref, System.currentTimeMillis()))
                if (wrapperSettings.isWrapperToastsEnabled()) flowSnackbar(activity, "Loan case created")
            }
            override fun onFileStatusChanged(step: AbleCreditFlowStep, uniqueId: String, status: AbleCreditFileStatus, message: String?) {
                val kind = if (step == AbleCreditFlowStep.RECORD_AUDIO) "Audio" else "Photo"
                Log.d(TAG, "$kind status=$status | step=$step, id=$uniqueId, msg=$message")
            }
            override fun onFlowCompleted(applicationId: String) {
                Log.d(TAG, "Flow completed | applicationId=$applicationId")
                loanCaseRepository.saveOrUpdate(LoanCaseItem(applicationId, loanReference, System.currentTimeMillis()))
                if (wrapperSettings.isWrapperToastsEnabled()) flowSnackbar(activity, "Flow completed")
            }
            override fun onFlowFailed(step: AbleCreditFlowStep, error: AbleCreditResult.Failure) {
                Log.e(TAG, "Flow FAILED at step=$step | code=${error.ableCreditErrorCode}, message=${error.message}")
                if (wrapperSettings.isWrapperToastsEnabled()) flowSnackbar(activity, error.message)
            }
            override fun onStepCompleted(step: AbleCreditFlowStep) {
                Log.d(TAG, "Step completed | step=$step")
            }
        }
    }

    private fun flowSnackbar(activity: AppCompatActivity, message: String?) {
        if (message == null) return
        activity.runOnUiThread {
            val anchor = activity.findViewById<View>(android.R.id.content)
            if (anchor != null) Snackbar.make(anchor, message, Snackbar.LENGTH_LONG).setTextMaxLines(6).show()
            else Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun showAddItemDialog(activity: AppCompatActivity, title: String, hint: String, callback: (String) -> Unit) {
        val addView = LayoutInflater.from(activity).inflate(R.layout.dialog_add_item, null)
        val tilNewItem = addView.findViewById<TextInputLayout>(R.id.til_new_item)
        val etNewItem = addView.findViewById<TextInputEditText>(R.id.et_new_item)
        tilNewItem.hint = hint
        val addDialog = MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_SdkConfigDialog)
            .setTitle(title).setView(addView)
            .setPositiveButton("Add", null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        addDialog.setOnShowListener {
            addDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = etNewItem.text?.toString()?.trim() ?: ""
                if (value.isEmpty()) { tilNewItem.error = "Enter a value"; return@setOnClickListener }
                tilNewItem.error = null
                callback(value)
                addDialog.dismiss()
            }
        }
        addDialog.show()
        if (etNewItem.requestFocus()) {
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(etNewItem, InputMethodManager.SHOW_IMPLICIT)
        }
    }
}
