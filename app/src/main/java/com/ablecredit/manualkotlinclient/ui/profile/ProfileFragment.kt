package com.ablecredit.manualkotlinclient.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ablecredit.manualkotlinclient.R
import com.ablecredit.manualkotlinclient.data.repository.AbleCreditRepository
import com.ablecredit.manualkotlinclient.data.repository.LoanCaseRepository
import com.ablecredit.manualkotlinclient.data.repository.SdkConfigRepository
import com.ablecredit.manualkotlinclient.ui.main.MainActivity

class ProfileFragment : Fragment() {

    private lateinit var sdkConfigRepository: SdkConfigRepository
    private lateinit var ableCreditRepository: AbleCreditRepository
    private lateinit var loanCaseRepository: LoanCaseRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sdkConfigRepository = SdkConfigRepository(requireContext())
        ableCreditRepository = AbleCreditRepository(requireActivity().application)
        loanCaseRepository = LoanCaseRepository(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_profile, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.tv_user_id).text = sdkConfigRepository.getSavedUserId()
        view.findViewById<TextView>(R.id.tv_base_url).text = sdkConfigRepository.getSavedBaseUrl()
        view.findViewById<TextView>(R.id.tv_tenant_id).text = sdkConfigRepository.getSavedTenantId()

        view.findViewById<Button>(R.id.btn_logout).setOnClickListener { performLogout() }
    }

    private fun performLogout() {
        ableCreditRepository.clearSdkData()
        sdkConfigRepository.clearSavedConfiguration()
        loanCaseRepository.clear()
        Toast.makeText(requireContext(), "Logged out", Toast.LENGTH_SHORT).show()

        val intent = Intent(requireContext(), MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        requireActivity().finish()
    }
}
