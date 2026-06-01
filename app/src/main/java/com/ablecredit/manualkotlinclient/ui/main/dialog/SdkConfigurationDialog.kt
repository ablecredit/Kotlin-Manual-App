package com.ablecredit.manualkotlinclient.ui.main.dialog

import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ablecredit.manualkotlinclient.R
import com.ablecredit.manualkotlinclient.data.model.UserSdkConfiguration
import com.ablecredit.manualkotlinclient.data.repository.SdkConfigRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText

object SdkConfigurationDialog {

    fun interface OnInitializeRequested {
        fun onInitialize(
            apiKey: String,
            tenantId: String,
            userId: String,
            baseUrl: String,
            branchId: String,
            selectionId: String,
            dismissOnSuccess: () -> Unit
        )
    }

    fun show(
        activity: AppCompatActivity,
        sdkConfigRepository: SdkConfigRepository,
        listener: OnInitializeRequested
    ) = show(activity, sdkConfigRepository, null, listener)

    fun show(
        activity: AppCompatActivity,
        sdkConfigRepository: SdkConfigRepository,
        onImportRequested: (() -> Unit)?,
        listener: OnInitializeRequested
    ) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_sdk_configuration, null)

        val createNewButton = dialogView.findViewById<MaterialButton>(R.id.btn_create_new_configuration)
        val importButton = dialogView.findViewById<MaterialButton>(R.id.btn_import_configuration)
        val actEnv = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.act_environment_preset)
        val etApiKey = dialogView.findViewById<TextInputEditText>(R.id.et_api_key)
        val etTenantId = dialogView.findViewById<TextInputEditText>(R.id.et_tenant_id)
        val etUserId = dialogView.findViewById<TextInputEditText>(R.id.et_user_id)
        val etBaseUrl = dialogView.findViewById<TextInputEditText>(R.id.et_base_url)
        val etBranchId = dialogView.findViewById<TextInputEditText>(R.id.et_branch_id)

        etUserId.setText(SdkConfigRepository.DEFAULT_USER_ID)

        val selectionIds = mutableListOf<String>()
        val selectionLabels = mutableListOf<String>()

        fun rebuildOptions() {
            selectionIds.clear()
            selectionLabels.clear()
            for (u in sdkConfigRepository.loadUserConfigurations()) {
                selectionIds.add(u.id)
                selectionLabels.add(u.displayName)
            }
            val adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, selectionLabels)
            actEnv.setAdapter(adapter)
        }

        rebuildOptions()

        var currentSelectionId = normalizeSavedSelectionId(sdkConfigRepository)

        var baselineApi = ""
        var baselineTenant = ""
        var baselineUserId = ""
        var baselineBaseUrl = ""
        var baselineBranchId = ""

        fun captureBaseline() {
            baselineApi = etApiKey.text?.toString() ?: ""
            baselineTenant = etTenantId.text?.toString() ?: ""
            baselineUserId = etUserId.text?.toString() ?: ""
            baselineBaseUrl = etBaseUrl.text?.toString() ?: ""
            baselineBranchId = etBranchId.text?.toString() ?: ""
        }

        fun loadFieldsForSelection() {
            if (currentSelectionId.startsWith("user:")) {
                val cfg: UserSdkConfiguration? = sdkConfigRepository.findUserConfiguration(currentSelectionId)
                etApiKey.setText(cfg?.apiKey ?: "")
                etTenantId.setText(cfg?.tenantId ?: "")
                etBaseUrl.setText(cfg?.baseUrl ?: "")
                etUserId.setText(cfg?.userId ?: SdkConfigRepository.DEFAULT_USER_ID)
                etBranchId.setText(cfg?.branchId ?: "")
            } else {
                etApiKey.setText("")
                etTenantId.setText("")
                etBaseUrl.setText("")
                etUserId.setText(SdkConfigRepository.DEFAULT_USER_ID)
                etBranchId.setText("")
            }
            captureBaseline()
        }

        val initialIndex = selectionIds.indexOf(currentSelectionId)
        if (initialIndex in selectionLabels.indices) {
            actEnv.setText(selectionLabels[initialIndex], false)
        }
        loadFieldsForSelection()

        actEnv.setOnItemClickListener { _, _, position, _ ->
            if (position in selectionIds.indices) {
                currentSelectionId = selectionIds[position]
                loadFieldsForSelection()
            }
        }

        val builder = MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_SdkConfigDialog)
            .setView(dialogView)
            .setPositiveButton(R.string.sdk_config_initialize, null)
            .setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }

        val dialog = builder.create()
        dialog.setCanceledOnTouchOutside(true)

        createNewButton.setOnClickListener {
            promptNewConfigurationName(activity) { name ->
                if (name.trim().isEmpty()) {
                    Toast.makeText(activity, "Enter a name", Toast.LENGTH_SHORT).show()
                    return@promptNewConfigurationName
                }
                val created = sdkConfigRepository.addUserConfiguration(
                    name.trim(), "", "", "", etUserId.text?.toString()?.trim() ?: ""
                )
                rebuildOptions()
                currentSelectionId = created.id
                val idx = selectionIds.indexOf(created.id)
                if (idx >= 0) actEnv.setText(selectionLabels[idx], false)
                loadFieldsForSelection()
            }
        }

        if (onImportRequested != null) {
            importButton.setOnClickListener {
                dialog.dismiss()
                onImportRequested()
            }
        }

        dialog.setOnShowListener {
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.text = activity.getString(R.string.sdk_config_initialize)
            positive.setOnClickListener {
                val apiKey = etApiKey.text?.toString()?.trim() ?: ""
                val tenantId = etTenantId.text?.toString()?.trim() ?: ""
                val userId = etUserId.text?.toString()?.trim() ?: ""
                val baseUrl = etBaseUrl.text?.toString()?.trim() ?: ""
                val branchId = etBranchId.text?.toString()?.trim() ?: ""

                if (apiKey.isEmpty() || tenantId.isEmpty() || userId.isEmpty() || baseUrl.isEmpty()) {
                    Toast.makeText(activity, "API key, tenant ID, user ID, and base URL are required", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (!currentSelectionId.startsWith("user:")) {
                    Toast.makeText(activity, "Create/select a configuration first", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                val dirty = apiKey != baselineApi || tenantId != baselineTenant ||
                        userId != baselineUserId || baseUrl != baselineBaseUrl || branchId != baselineBranchId

                if (!dirty) {
                    val selId = currentSelectionId
                    listener.onInitialize(apiKey, tenantId, userId, baseUrl, branchId, selId) {
                        sdkConfigRepository.setLastSelectionId(selId)
                        dialog.dismiss()
                    }
                    return@setOnClickListener
                }

                val selId = currentSelectionId
                MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_SdkConfigDialog)
                    .setTitle(R.string.sdk_config_unsaved_title)
                    .setMessage(R.string.sdk_config_unsaved_message)
                    .setPositiveButton(R.string.sdk_config_save_and_initialize) { _, _ ->
                        persistCurrentSelection(sdkConfigRepository, selId, apiKey, tenantId, userId, baseUrl, branchId)
                        listener.onInitialize(apiKey, tenantId, userId, baseUrl, branchId, selId) {
                            sdkConfigRepository.setLastSelectionId(selId)
                            dialog.dismiss()
                        }
                    }
                    .setNeutralButton(R.string.sdk_config_initialize_without_save) { _, _ ->
                        listener.onInitialize(apiKey, tenantId, userId, baseUrl, branchId, selId) {
                            sdkConfigRepository.setLastSelectionId(selId)
                            dialog.dismiss()
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }

        dialog.show()
    }

    private fun normalizeSavedSelectionId(repo: SdkConfigRepository): String {
        val saved = repo.getSavedConfigTag()
        if (saved.startsWith("user:") && repo.findUserConfiguration(saved) != null) return saved
        val users = repo.loadUserConfigurations()
        return if (users.isEmpty()) "" else users[0].id
    }

    private fun persistCurrentSelection(
        repo: SdkConfigRepository,
        selectionId: String,
        apiKey: String,
        tenantId: String,
        userId: String,
        baseUrl: String,
        branchId: String
    ) {
        if (selectionId.startsWith("user:")) {
            val existing = repo.findUserConfiguration(selectionId)
            val name = existing?.displayName ?: "Custom"
            repo.updateUserConfiguration(
                UserSdkConfiguration(selectionId, name, apiKey, tenantId, baseUrl, userId, branchId)
            )
        }
    }

    private fun promptNewConfigurationName(
        activity: AppCompatActivity,
        onNameChosen: (String) -> Unit
    ) {
        val nameView = LayoutInflater.from(activity).inflate(R.layout.dialog_new_config_name, null)
        val et = nameView.findViewById<TextInputEditText>(R.id.et_new_config_name)

        MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_SdkConfigDialog)
            .setTitle(R.string.sdk_config_new_config_title)
            .setView(nameView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onNameChosen(et?.text?.toString() ?: "")
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
