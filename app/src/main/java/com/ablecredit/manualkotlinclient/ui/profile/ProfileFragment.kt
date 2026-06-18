package com.ablecredit.manualkotlinclient.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.ablecredit.manualkotlinclient.R
import com.ablecredit.manualkotlinclient.data.repository.AbleCreditRepository
import com.ablecredit.manualkotlinclient.data.repository.LoanCaseRepository
import com.ablecredit.manualkotlinclient.data.repository.SdkConfigRepository
import com.ablecredit.manualkotlinclient.data.repository.WrapperSettingsRepository
import com.ablecredit.manualkotlinclient.ui.flowconfig.FlowConfigActivity
import com.ablecredit.manualkotlinclient.ui.main.MainActivity
import com.ablecredit.sdk.manager.AbleCredit
import com.google.android.material.switchmaterial.SwitchMaterial

class ProfileFragment : Fragment() {

    private lateinit var sdkConfigRepository: SdkConfigRepository
    private lateinit var ableCreditRepository: AbleCreditRepository
    private lateinit var loanCaseRepository: LoanCaseRepository
    private lateinit var wrapperSettingsRepository: WrapperSettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sdkConfigRepository = SdkConfigRepository(requireContext())
        ableCreditRepository = AbleCreditRepository(requireActivity().application)
        loanCaseRepository = LoanCaseRepository(requireContext())
        wrapperSettingsRepository = WrapperSettingsRepository(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_profile, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.tv_user_id).text = sdkConfigRepository.getSavedUserId()
        view.findViewById<TextView>(R.id.tv_base_url).text = sdkConfigRepository.getSavedBaseUrl()
        view.findViewById<TextView>(R.id.tv_tenant_id).text = sdkConfigRepository.getSavedTenantId()

        setupToastSettings(view)
        view.findViewById<View>(R.id.btn_flow_config).setOnClickListener {
            startActivity(Intent(requireContext(), FlowConfigActivity::class.java))
        }
        view.findViewById<View>(R.id.btn_logout).setOnClickListener { performLogout() }
    }

    private fun setupToastSettings(root: View) {
        val sdkSwitch = root.findViewById<SwitchMaterial>(R.id.switch_sdk_toasts)
        sdkSwitch.isChecked = AbleCredit.sdkToastsEnabled
        sdkSwitch.setOnCheckedChangeListener { _, isChecked ->
            AbleCredit.setSdkToastsEnabled(isChecked)
            wrapperSettingsRepository.setSdkToastsEnabled(isChecked)
        }

        val headerSwitch = root.findViewById<SwitchMaterial>(R.id.switch_sdk_header)
        headerSwitch.isChecked = wrapperSettingsRepository.isSdkHeaderEnabled()
        headerSwitch.setOnCheckedChangeListener { _, isChecked ->
            AbleCredit.setShowSdkHeader(isChecked)
            wrapperSettingsRepository.setSdkHeaderEnabled(isChecked)
        }

        val wrapperSwitch = root.findViewById<SwitchMaterial>(R.id.switch_wrapper_toasts)
        wrapperSwitch.isChecked = wrapperSettingsRepository.isWrapperToastsEnabled()
        wrapperSwitch.setOnCheckedChangeListener { _, isChecked ->
            wrapperSettingsRepository.setWrapperToastsEnabled(isChecked)
        }
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
