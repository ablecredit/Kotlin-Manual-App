package com.ablecredit.manualkotlinclient.ui.scenarios

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ablecredit.manualkotlinclient.R
import com.google.android.material.button.MaterialButton

/**
 * Real screen (not a dialog) standing in for the client app's own screen between the two SDK
 * orchestrator stages. Shows a toast as soon as it's reached, then hands control back to the SDK
 * for stage 2 when the user taps "Resume SDK".
 *
 * The resume callback can't travel through the Intent (it's a lambda), so it's stashed in a
 * companion holder and consumed exactly once in [onCreate].
 */
class ClientHandoffActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_APPLICATION_ID = "extra_application_id"

        private var pendingOnResume: (() -> Unit)? = null

        fun start(context: Context, applicationId: String, onResume: () -> Unit) {
            pendingOnResume = onResume
            val intent = Intent(context, ClientHandoffActivity::class.java)
            intent.putExtra(EXTRA_APPLICATION_ID, applicationId)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client_handoff)

        val applicationId = intent.getStringExtra(EXTRA_APPLICATION_ID) ?: ""
        val onResume = pendingOnResume
        pendingOnResume = null

        val loanReferenceView = findViewById<TextView>(R.id.tv_loan_reference)
        loanReferenceView.text = applicationId.ifEmpty { "(no application ID)" }

        val descriptionView = findViewById<TextView>(R.id.tv_description)
        descriptionView.text = "Audio complete for application $applicationId. This screen represents control returning to the host app."

        Toast.makeText(this, "Client screen reached", Toast.LENGTH_SHORT).show()

        val resumeButton = findViewById<MaterialButton>(R.id.btn_resume_sdk)
        resumeButton.setOnClickListener {
            onResume?.invoke()
            finish()
        }

        val backButton = findViewById<ImageButton>(R.id.btn_back)
        backButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }
}
