package com.ablecredit.manualkotlinclient.ui.testflows

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ablecredit.manualkotlinclient.R
import com.ablecredit.sdk.manager.AbleCredit
import com.ablecredit.sdk.model.AbleCreditDockedButton
import com.ablecredit.sdk.model.AbleCreditResult
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class TestFlowsActivity : AppCompatActivity() {

    private lateinit var loanReferenceInput: TextInputEditText

    companion object {
        fun createIntent(context: Context) = Intent(context, TestFlowsActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_flows)

        loanReferenceInput = findViewById(R.id.et_loan_reference)
        findViewById<MaterialButton>(R.id.btn_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        wireDirect()
        wireVisibilityTest()
        FlowBuilderController(
            activity = this,
            container = findViewById(R.id.flow_builder_container),
            addStepBtn = findViewById(R.id.btn_flow_builder_add_step),
            runBtn = findViewById(R.id.btn_flow_builder_run),
            loanRefFn = ::loanRef,
            generateReportFn = ::generateReport
        ).init()
    }

    private fun wireDirect() {
        btn(R.id.btn_direct_audio) { AbleCredit.recordAudio(this, loanRef()) }
        btn(R.id.btn_direct_business) { AbleCredit.captureBusinessPhotos(this, loanRef()) }
        btn(R.id.btn_direct_collateral) { AbleCredit.captureCollateralPhotos(this, loanRef()) }
        btn(R.id.btn_direct_family) { AbleCredit.captureFamilyPhotos(this, loanRef()) }
    }

    private fun wireVisibilityTest() {
        btn(R.id.btn_hidden_docked_button) {
            AbleCredit.recordAudio(this, loanRef(), null,
                AbleCreditDockedButton("This button should not be visible", false) {
                    Toast.makeText(it, "Should never happen", Toast.LENGTH_SHORT).show()
                    kotlin.Unit
                })
        }
    }

    private fun loanRef(): String = loanReferenceInput.text?.toString()?.trim() ?: ""

    private fun btn(id: Int, action: () -> Unit) {
        findViewById<MaterialButton>(id).setOnClickListener { action() }
    }

    fun generateReport(ctx: Context, loanReference: String) {
        if (loanReference.isEmpty()) {
            Toast.makeText(ctx, "Enter a loan reference first", Toast.LENGTH_SHORT).show()
            return
        }
        AbleCredit.requestReportGeneration(ctx, loanReference) { result ->
            when (result) {
                is AbleCreditResult.Success -> Toast.makeText(ctx, "Report requested successfully", Toast.LENGTH_SHORT).show()
                is AbleCreditResult.Failure -> Toast.makeText(ctx, "Report failed: ${result.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ─── Enums ───────────────────────────────────────────────────────────────

    enum class FlowStep(val label: String, val sdkKey: String) {
        AUDIO("Record audio", MockClientScreenActivity.STEP_AUDIO),
        BUSINESS("Business photos", MockClientScreenActivity.STEP_BUSINESS_PHOTOS),
        COLLATERAL("Collateral photos", MockClientScreenActivity.STEP_COLLATERAL_PHOTOS),
        FAMILY("Family photos", MockClientScreenActivity.STEP_FAMILY_PHOTOS),
        GENERATE_REPORT("Generate report", MockClientScreenActivity.STEP_GENERATE_REPORT)
    }

    enum class TransitionType(val label: String, val description: String) {
        DIRECT("Direct to SDK step", "Docked button opens the next SDK screen immediately"),
        CLIENT_SCREEN("Via client screen", "Docked button opens a mock client screen first, then continues to the next SDK step")
    }

    data class StepNode(var step: FlowStep, var transition: TransitionType = TransitionType.DIRECT)

    // ─── FlowBuilderController ────────────────────────────────────────────────

    inner class FlowBuilderController(
        private val activity: AppCompatActivity,
        private val container: LinearLayout,
        private val addStepBtn: MaterialButton,
        private val runBtn: MaterialButton,
        private val loanRefFn: () -> String,
        private val generateReportFn: (Context, String) -> Unit
    ) {
        private val nodes = mutableListOf(StepNode(FlowStep.AUDIO), StepNode(FlowStep.BUSINESS))

        fun init() {
            addStepBtn.setOnClickListener {
                showStepPicker(null) { step -> nodes.add(StepNode(step)); render() }
            }
            runBtn.setOnClickListener { runFlow() }
            render()
        }

        private fun render() {
            container.removeAllViews()
            nodes.forEachIndexed { i, node ->
                container.addView(buildStepRow(node, i))
                if (i < nodes.size - 1) container.addView(buildConnector(node))
            }
        }

        private fun buildStepRow(node: StepNode, index: Int): LinearLayout {
            val dp = activity.resources.displayMetrics.density
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val vpad = (6 * dp).toInt()
                setPadding(0, vpad, 0, vpad)
            }
            val stepBtn = MaterialButton(activity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = node.step.label
                setTextColor(ContextCompat.getColor(activity, R.color.black))
                strokeColor = ContextCompat.getColorStateList(activity, R.color.black)
                layoutParams = LinearLayout.LayoutParams(0, (40 * dp).toInt(), 1f)
                insetTop = 0; insetBottom = 0
                setOnClickListener { showStepPicker(node.step) { picked -> node.step = picked; render() } }
            }
            row.addView(stepBtn)
            val removeBtn = TextView(activity).apply {
                text = "✕"; textSize = 16f
                setTextColor(ContextCompat.getColor(activity, R.color.gray_400))
                gravity = Gravity.CENTER
                val hpad = (12 * dp).toInt()
                setPadding(hpad, 0, hpad / 2, 0)
                setOnClickListener {
                    if (nodes.size > 1) { nodes.removeAt(index); render() }
                    else Toast.makeText(activity, "At least one step required", Toast.LENGTH_SHORT).show()
                }
            }
            row.addView(removeBtn)
            return row
        }

        private fun buildConnector(node: StepNode): TextView {
            val dp = activity.resources.displayMetrics.density
            val tv = TextView(activity)
            applyConnectorStyle(tv, node.transition, dp)
            tv.setOnClickListener {
                showTransitionPicker(node.transition) { picked -> node.transition = picked; applyConnectorStyle(tv, node.transition, dp) }
            }
            return tv
        }

        private fun applyConnectorStyle(tv: TextView, type: TransitionType, dp: Float) {
            val hpad = (8 * dp).toInt(); val vpad = (6 * dp).toInt()
            tv.setPadding(hpad, vpad, hpad, vpad)
            if (type == TransitionType.DIRECT) {
                tv.text = "↓  Direct  ↓  (tap to change)"
                tv.setTextColor(ContextCompat.getColor(activity, R.color.gray_600))
                tv.setTypeface(null, Typeface.NORMAL); tv.textSize = 12f; tv.background = null
            } else {
                tv.text = "↓  [Client screen]  ↓  (tap to change)"
                tv.setTextColor(ContextCompat.getColor(activity, R.color.black))
                tv.setTypeface(null, Typeface.BOLD); tv.textSize = 12f
                tv.background = ContextCompat.getDrawable(activity, R.drawable.bg_client_screen_badge)
            }
        }

        private fun showStepPicker(current: FlowStep?, onPick: (FlowStep) -> Unit) {
            val sheet = BottomSheetDialog(activity)
            val dp = activity.resources.displayMetrics.density
            val content = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, (8 * dp).toInt(), 0, (24 * dp).toInt())
            }
            content.addView(TextView(activity).apply {
                text = "Choose SDK step"; textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(activity, R.color.black))
                val th = (20 * dp).toInt(); val tv = (12 * dp).toInt()
                setPadding(th, tv, th, tv)
            })
            FlowStep.values().forEach { step ->
                content.addView(buildSheetRow(step.label, null, step == current, dp) { sheet.dismiss(); onPick(step) })
            }
            sheet.setContentView(content); sheet.show()
        }

        private fun showTransitionPicker(current: TransitionType, onPick: (TransitionType) -> Unit) {
            val sheet = BottomSheetDialog(activity)
            val dp = activity.resources.displayMetrics.density
            val content = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, (8 * dp).toInt(), 0, (24 * dp).toInt())
            }
            content.addView(TextView(activity).apply {
                text = "Choose transition type"; textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(activity, R.color.black))
                val th = (20 * dp).toInt(); val tv = (12 * dp).toInt()
                setPadding(th, tv, th, tv)
            })
            TransitionType.values().forEach { type ->
                content.addView(buildSheetRow(type.label, type.description, type == current, dp) { sheet.dismiss(); onPick(type) })
            }
            sheet.setContentView(content); sheet.show()
        }

        private fun buildSheetRow(label: String, sublabel: String?, selected: Boolean, dp: Float, onClick: () -> Unit): LinearLayout {
            val hpad = (20 * dp).toInt(); val vpad = (14 * dp).toInt()
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(hpad, vpad, hpad, vpad)
                isClickable = true; isFocusable = true
                val tv = android.util.TypedValue()
                activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                background = ContextCompat.getDrawable(activity, tv.resourceId)
                setOnClickListener { onClick() }
            }
            val textCol = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(activity).apply {
                text = label; textSize = 14f
                setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(ContextCompat.getColor(activity, R.color.black))
            })
            if (sublabel != null) {
                textCol.addView(TextView(activity).apply {
                    text = sublabel; textSize = 12f
                    setTextColor(ContextCompat.getColor(activity, R.color.gray_600))
                })
            }
            row.addView(textCol)
            if (selected) {
                row.addView(TextView(activity).apply {
                    text = "✓"; textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(activity, R.color.black))
                    gravity = Gravity.CENTER_VERTICAL
                })
            }
            return row
        }

        private fun runFlow() {
            if (nodes.isEmpty()) { Toast.makeText(activity, "Add at least one step", Toast.LENGTH_SHORT).show(); return }
            val ref = loanRefFn()
            var chainedDockedButton: AbleCreditDockedButton? = null
            for (i in nodes.indices.reversed()) {
                val node = nodes[i]
                val nextDockedButton = chainedDockedButton
                if (i < nodes.size - 1) {
                    val nextNode = nodes[i + 1]
                    chainedDockedButton = if (node.transition == TransitionType.DIRECT) {
                        AbleCreditDockedButton("${nextNode.step.label} →", true) { ctx ->
                            launchStep(ctx, nextNode, ref, nextDockedButton); kotlin.Unit
                        }
                    } else {
                        AbleCreditDockedButton("Next: client review →", true) { ctx ->
                            ctx.startActivity(MockClientScreenActivity.createIntent(
                                ctx, ref,
                                "Client screen between \"${node.step.label}\" and \"${nextNode.step.label}\".",
                                nextNode.step.sdkKey, nextDockedButton
                            )); kotlin.Unit
                        }
                    }
                } else {
                    chainedDockedButton = null
                }
            }
            launchStep(activity, nodes[0], ref, chainedDockedButton)
        }

        private fun launchStep(ctx: Context, node: StepNode, ref: String, dockedButton: AbleCreditDockedButton?) {
            when (node.step) {
                FlowStep.AUDIO -> AbleCredit.recordAudio(ctx, ref, null, dockedButton)
                FlowStep.BUSINESS -> AbleCredit.captureBusinessPhotos(ctx, ref, null, dockedButton)
                FlowStep.COLLATERAL -> AbleCredit.captureCollateralPhotos(ctx, ref, null, dockedButton)
                FlowStep.FAMILY -> AbleCredit.captureFamilyPhotos(ctx, ref, null, dockedButton)
                FlowStep.GENERATE_REPORT -> generateReportFn(ctx, ref)
            }
        }
    }
}
