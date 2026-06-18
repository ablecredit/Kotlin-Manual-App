package com.ablecredit.manualkotlinclient.ui.flow

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.ablecredit.manualkotlinclient.R
import com.ablecredit.manualkotlinclient.data.repository.AbleCreditRepository
import com.ablecredit.manualkotlinclient.ui.main.dialog.FlowConfigDialog

class FlowFragment : Fragment() {
    private lateinit var ableCreditRepository: AbleCreditRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ableCreditRepository = AbleCreditRepository(requireActivity().application)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_flow, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.btn_start_loan_flow).setOnClickListener {
            FlowConfigDialog.show(requireActivity() as AppCompatActivity) { config ->
                ableCreditRepository.startLoanFlow(requireContext(), config)
            }
        }
    }
}
