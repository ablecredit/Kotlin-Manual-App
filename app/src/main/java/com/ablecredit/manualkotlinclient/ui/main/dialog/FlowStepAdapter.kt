package com.ablecredit.manualkotlinclient.ui.main.dialog

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.ablecredit.manualkotlinclient.R
import com.ablecredit.sdk.model.AbleCreditFlowStep
import com.google.android.material.checkbox.MaterialCheckBox

class FlowStepAdapter(
    initial: List<Pair<AbleCreditFlowStep, Boolean>>,
    private val dragStartListener: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<FlowStepAdapter.StepViewHolder>() {

    private data class Row(val step: AbleCreditFlowStep, var checked: Boolean)

    private val rows = initial.map { Row(it.first, it.second) }.toMutableList()
    var onStepsChanged: (() -> Unit)? = null

    fun selectedStepsInOrder(): List<AbleCreditFlowStep> = rows.filter { it.checked }.map { it.step }

    fun allRowsInOrder(): List<Pair<AbleCreditFlowStep, Boolean>> = rows.map { it.step to it.checked }

    fun moveItem(from: Int, to: Int) {
        if (from < to) for (i in from until to) rows.swap(i, i + 1)
        else for (i in from downTo to + 1) rows.swap(i, i - 1)
        notifyItemMoved(from, to)
        onStepsChanged?.invoke()
    }

    private fun <T> MutableList<T>.swap(i: Int, j: Int) { val tmp = this[i]; this[i] = this[j]; this[j] = tmp }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_flow_step, parent, false)
        return StepViewHolder(view)
    }

    override fun getItemCount() = rows.size

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: StepViewHolder, position: Int) {
        val row = rows[holder.adapterPosition.takeIf { it != RecyclerView.NO_ID.toInt() } ?: position]
        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.text = labelForStep(row.step)
        holder.checkBox.isChecked = row.checked
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                rows[pos].checked = isChecked
                onStepsChanged?.invoke()
            }
        }
        holder.dragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) dragStartListener(holder)
            false
        }
    }

    class StepViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkBox: MaterialCheckBox = view.findViewById(R.id.cb_step)
        val dragHandle: ImageView = view.findViewById(R.id.iv_drag_handle)
    }

    fun buildTouchHelper(): ItemTouchHelper {
        val callback = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun isLongPressDragEnabled() = false
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                moveItem(vh.adapterPosition, target.adapterPosition)
                return true
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        }
        return ItemTouchHelper(callback)
    }

    companion object {
        fun labelForStep(step: AbleCreditFlowStep): String = when (step) {
            AbleCreditFlowStep.CREATE_LOAN_CASE -> "Create loan case"
            AbleCreditFlowStep.RECORD_AUDIO -> "Record audio"
            AbleCreditFlowStep.CAPTURE_BUSINESS_PHOTOS -> "Capture business photos"
            AbleCreditFlowStep.CAPTURE_FAMILY_PHOTOS -> "Capture family photos"
            AbleCreditFlowStep.CAPTURE_COLLATERAL_PHOTOS -> "Capture collateral photos"
            AbleCreditFlowStep.GENERATE_REPORT -> "Generate report"
            else -> step.name
        }

        fun rowsFor(enabled: List<AbleCreditFlowStep>): List<Pair<AbleCreditFlowStep, Boolean>> {
            val enabledSet = enabled.toSet()
            val result = enabled.map { it to true }.toMutableList()
            for (step in AbleCreditFlowStep.values()) {
                if (!enabledSet.contains(step)) result.add(step to false)
            }
            return result
        }
    }
}
