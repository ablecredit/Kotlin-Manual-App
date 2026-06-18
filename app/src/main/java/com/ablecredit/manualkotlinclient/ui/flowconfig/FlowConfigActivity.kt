package com.ablecredit.manualkotlinclient.ui.flowconfig

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ablecredit.manualkotlinclient.R
import com.ablecredit.manualkotlinclient.data.repository.FlowStepsRepository
import com.ablecredit.manualkotlinclient.ui.main.dialog.FlowStepAdapter

class FlowConfigActivity : AppCompatActivity() {
    private lateinit var flowStepsRepository: FlowStepsRepository
    private lateinit var flowStepAdapter: FlowStepAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flow_config)
        flowStepsRepository = FlowStepsRepository(this)
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        setupFlowSteps()
        findViewById<View>(R.id.btn_save_flow_steps).setOnClickListener { saveFlowSteps() }
    }

    private fun setupFlowSteps() {
        val recycler = findViewById<RecyclerView>(R.id.rv_flow_steps)
        var touchHelper: ItemTouchHelper? = null
        flowStepAdapter = FlowStepAdapter(
            FlowStepAdapter.rowsFor(flowStepsRepository.getSteps())
        ) { holder -> touchHelper?.startDrag(holder) }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = flowStepAdapter
        touchHelper = flowStepAdapter.buildTouchHelper()
        touchHelper.attachToRecyclerView(recycler)
    }

    private fun saveFlowSteps() {
        val steps = flowStepAdapter.selectedStepsInOrder()
        if (steps.isEmpty()) { Toast.makeText(this, "Select at least one step", Toast.LENGTH_SHORT).show(); return }
        flowStepsRepository.saveSteps(steps)
        Toast.makeText(this, "Flow steps saved", Toast.LENGTH_SHORT).show()
        finish()
    }
}
