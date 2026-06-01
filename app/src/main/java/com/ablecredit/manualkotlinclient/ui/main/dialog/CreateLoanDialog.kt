package com.ablecredit.manualkotlinclient.ui.main.dialog

import android.content.Context
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ablecredit.manualkotlinclient.R
import com.ablecredit.manualkotlinclient.data.repository.DropdownItemsRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CreateLoanDialog {

    fun interface OnCreateRequested {
        fun onCreateLoan(
            loanReference: String,
            userName: String,
            branchName: String,
            product: String,
            businessModel: String
        )
    }

    fun show(activity: AppCompatActivity, listener: OnCreateRequested) {
        val repo = DropdownItemsRepository(activity)
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_create_loan, null)

        val etLoanRef = dialogView.findViewById<TextInputEditText>(R.id.et_loan_reference)
        val etUserName = dialogView.findViewById<TextInputEditText>(R.id.et_user_name)
        val etBranchName = dialogView.findViewById<TextInputEditText>(R.id.et_branch_name)
        val actProduct = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.act_product)
        val actBusinessModel = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.act_business_model)
        val btnAddProduct = dialogView.findViewById<MaterialButton>(R.id.btn_add_product)
        val btnAddBusinessModel = dialogView.findViewById<MaterialButton>(R.id.btn_add_business_model)

        val datePart = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val timeSuffix = String.format(Locale.US, "%04d", System.currentTimeMillis() % 10000)
        etLoanRef.setText("LN-REF-$datePart-$timeSuffix")

        val productList = repo.getProducts().toMutableList()
        val businessModelList = repo.getBusinessModels().toMutableList()

        val productAdapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, productList)
        actProduct.setAdapter(productAdapter)
        actProduct.setText(productList[0], false)

        val businessAdapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, businessModelList)
        actBusinessModel.setAdapter(businessAdapter)
        actBusinessModel.setText(businessModelList[0], false)

        btnAddProduct.setOnClickListener {
            showAddItemDialog(activity, "Add product", "e.g. Home Loan") { newItem ->
                repo.addProduct(newItem)
                productAdapter.add(newItem)
                productAdapter.notifyDataSetChanged()
                actProduct.setText(newItem, false)
            }
        }

        btnAddBusinessModel.setOnClickListener {
            showAddItemDialog(activity, "Add business model", "e.g. Retail") { newItem ->
                repo.addBusinessModel(newItem)
                businessAdapter.add(newItem)
                businessAdapter.notifyDataSetChanged()
                actBusinessModel.setText(newItem, false)
            }
        }

        val dialog = MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_SdkConfigDialog)
            .setView(dialogView)
            .setPositiveButton(R.string.create_loan_continue, null)
            .setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }
            .create()

        dialog.setOnShowListener {
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.setOnClickListener {
                val product = actProduct.text?.toString()?.trim() ?: ""
                val businessModel = actBusinessModel.text?.toString()?.trim() ?: ""
                if (product.isEmpty() || businessModel.isEmpty()) {
                    Toast.makeText(activity, "Select product and business model", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val loanRef = etLoanRef.text?.toString()?.trim() ?: ""
                Toast.makeText(activity, "Creating loan...", Toast.LENGTH_SHORT).show()
                listener.onCreateLoan(
                    loanRef,
                    etUserName.text?.toString() ?: "",
                    etBranchName.text?.toString() ?: "",
                    product,
                    businessModel
                )
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showAddItemDialog(
        activity: AppCompatActivity,
        title: String,
        hint: String,
        callback: (String) -> Unit
    ) {
        val addView = LayoutInflater.from(activity).inflate(R.layout.dialog_add_item, null)
        val tilNewItem = addView.findViewById<TextInputLayout>(R.id.til_new_item)
        val etNewItem = addView.findViewById<TextInputEditText>(R.id.et_new_item)
        tilNewItem.hint = hint

        val addDialog = MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_SdkConfigDialog)
            .setTitle(title)
            .setView(addView)
            .setPositiveButton("Add", null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        addDialog.setOnShowListener {
            val positive = addDialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.setOnClickListener {
                val value = etNewItem.text?.toString()?.trim() ?: ""
                if (value.isEmpty()) {
                    tilNewItem.error = "Enter a value"
                    return@setOnClickListener
                }
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
