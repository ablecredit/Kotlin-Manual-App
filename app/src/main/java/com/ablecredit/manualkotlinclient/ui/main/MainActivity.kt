package com.ablecredit.manualkotlinclient.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ablecredit.manualkotlinclient.R
import com.ablecredit.manualkotlinclient.data.model.UserSdkConfiguration
import com.ablecredit.manualkotlinclient.data.repository.AbleCreditRepository
import com.ablecredit.manualkotlinclient.data.repository.SdkConfigRepository
import com.ablecredit.manualkotlinclient.ui.dashboard.DashboardActivity
import com.ablecredit.manualkotlinclient.ui.main.dialog.SdkConfigurationDialog
import com.ablecredit.manualkotlinclient.util.ConfigFileImporter
import com.ablecredit.sdk.model.AbleCreditResult
import com.ablecredit.sdk.model.constants.AbleCreditErrorCodes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var sdkConfigRepository: SdkConfigRepository
    private lateinit var ableCreditRepository: AbleCreditRepository
    private lateinit var loginButton: Button
    private lateinit var loadingOverlay: View
    private val ioExecutor = Executors.newSingleThreadExecutor()

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) handleConfigFileImport(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sdkConfigRepository = SdkConfigRepository(applicationContext)
        ableCreditRepository = AbleCreditRepository(application)
        loginButton = findViewById(R.id.btn_login)
        loadingOverlay = findViewById(R.id.loading_overlay)

        if (sdkConfigRepository.isSdkInitialized()) {
            restoreSdkSessionAsync()
            return
        }

        loginButton.setOnClickListener { openConfigurationDialog() }
    }

    private fun restoreSdkSessionAsync() {
        setLoading(true)
        ioExecutor.execute {
            val restored = tryRestoreSdkSession()
            runOnUiThread {
                setLoading(false)
                if (restored) {
                    openDashboardAndFinish()
                } else {
                    loginButton.setOnClickListener { openConfigurationDialog() }
                }
            }
        }
    }

    private fun tryRestoreSdkSession(): Boolean {
        val apiKey = sdkConfigRepository.getSavedApiKey().trim()
        val tenantId = sdkConfigRepository.getSavedTenantId().trim()
        val baseUrl = sdkConfigRepository.getSavedBaseUrl().trim()
        val branchId = sdkConfigRepository.getSavedBranchId().trim()
        val savedUserId = sdkConfigRepository.getSavedUserId().trim()
        val userId = if (savedUserId.isNotEmpty()) {
            savedUserId
        } else {
            val selectionId = sdkConfigRepository.getSavedConfigTag()
            if (selectionId.startsWith("user:")) {
                val cfg: UserSdkConfiguration? = sdkConfigRepository.findUserConfiguration(selectionId)
                cfg?.userId?.trim() ?: ""
            } else {
                ""
            }
        }

        if (apiKey.isEmpty() || tenantId.isEmpty() || baseUrl.isEmpty() || userId.isEmpty()) {
            sdkConfigRepository.clearSavedConfiguration()
            return false
        }

        val result = ableCreditRepository.configure(
            apiKey, tenantId, userId, baseUrl, branchId.ifEmpty { null }
        )

        return when (result) {
            is AbleCreditResult.Success -> true
            is AbleCreditResult.Failure -> {
                if (result.ableCreditErrorCode == AbleCreditErrorCodes.SDK_ALREADY_INITIALIZED) return true
                sdkConfigRepository.clearSavedConfiguration()
                runOnUiThread { Toast.makeText(this, result.message, Toast.LENGTH_LONG).show() }
                false
            }
            else -> {
                sdkConfigRepository.clearSavedConfiguration()
                false
            }
        }
    }

    fun openConfigFilePickerForImport() {
        filePickerLauncher.launch("application/json")
    }

    private fun handleConfigFileImport(uri: Uri) {
        ioExecutor.execute {
            val result = ConfigFileImporter.importFromUri(this, uri)
            runOnUiThread {
                if (!result.success) {
                    Toast.makeText(this, "Import failed: ${result.errorMessage}", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                var successCount = 0
                for (config in result.configurations) {
                    try {
                        sdkConfigRepository.addUserConfiguration(
                            config.displayName, config.apiKey, config.tenantId,
                            config.baseUrl, config.userId, config.branchId
                        )
                        successCount++
                    } catch (_: Exception) {
                    }
                }
                Toast.makeText(this, "Imported $successCount configuration(s)", Toast.LENGTH_LONG).show()
                if (successCount > 0) openConfigurationDialog()
            }
        }
    }

    private fun openConfigurationDialog() {
        SdkConfigurationDialog.show(
            this,
            sdkConfigRepository,
            ::openConfigFilePickerForImport
        ) { apiKey, tenantId, userId, baseUrl, branchId, selectionId, dismissOnSuccess ->
            val loadingDialog = createInitializingDialog()
            loadingDialog.show()
            ioExecutor.execute {
                val result = ableCreditRepository.configure(
                    apiKey, tenantId, userId, baseUrl, branchId.ifEmpty { null }
                )
                runOnUiThread {
                    if (loadingDialog.isShowing) loadingDialog.dismiss()
                    when (result) {
                        is AbleCreditResult.Success -> {
                            sdkConfigRepository.saveSuccessfulConfiguration(
                                apiKey, tenantId, userId, baseUrl, selectionId, branchId
                            )
                            dismissOnSuccess()
                            Toast.makeText(this, "Welcome!", Toast.LENGTH_SHORT).show()
                            openDashboardAndFinish()
                        }
                        is AbleCreditResult.Failure -> {
                            Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    private fun createInitializingDialog(): AlertDialog {
        return MaterialAlertDialogBuilder(this)
            .setMessage(R.string.sdk_initializing_loader)
            .setCancelable(false)
            .create()
            .also { it.setCanceledOnTouchOutside(false) }
    }

    private fun setLoading(loading: Boolean) {
        loadingOverlay.visibility = if (loading) View.VISIBLE else View.GONE
        loginButton.isEnabled = !loading
    }

    override fun onDestroy() {
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun openDashboardAndFinish() {
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }
}
