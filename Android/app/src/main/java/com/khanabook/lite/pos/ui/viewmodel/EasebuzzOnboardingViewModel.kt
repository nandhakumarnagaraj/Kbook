package com.khanabook.lite.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.data.remote.dto.EasebuzzOnboardRequest
import com.khanabook.lite.pos.data.repository.EasebuzzPaymentRepository
import com.khanabook.lite.pos.data.repository.RestaurantRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the owner-facing Easebuzz sub-merchant onboarding form (and the resubmit-after-rejection
 * flow). Documents are NOT collected here — they are uploaded later on Easebuzz's hosted KYC portal.
 */
@HiltViewModel
class EasebuzzOnboardingViewModel @Inject constructor(
    private val easebuzzRepo: EasebuzzPaymentRepository,
    private val restaurantRepository: RestaurantRepository
) : ViewModel() {

    /** Business types Easebuzz recognises (value sent to API, label shown to the owner). */
    val businessTypes: List<Pair<String, String>> = listOf(
        "SOLE_PROPRIETORSHIP" to "Sole Proprietorship",
        "PARTNERSHIP" to "Partnership",
        "PRIVATE_LIMITED" to "Private Limited",
        "PUBLIC_LIMITED" to "Public Limited",
        "INDIVIDUAL" to "Individual / Freelancer"
    )

    data class FormState(
        val businessName: String = "",
        val legalEntityName: String = "",
        val businessType: String = "SOLE_PROPRIETORSHIP",
        val pan: String = "",
        val gst: String = "",
        val fssaiNumber: String = "",
        val fssaiExpiryDate: Long? = null,
        val businessAddress: String = "",
        val state: String = "",
        val bankAccountNo: String = "",
        val confirmAccountNo: String = "",
        val ifsc: String = "",
        val bankName: String = "",
        val branchName: String = "",
        val beneficiaryName: String = "",
        val contactEmail: String = "",
        val contactPhone: String = "",
        val errors: Map<String, String> = emptyMap(),
        val prefilled: Boolean = false
    )

    sealed interface SubmitState {
        data object Idle : SubmitState
        data object Submitting : SubmitState
        data class Success(val subMerchantId: String?, val message: String?) : SubmitState
        data class Error(val message: String) : SubmitState
    }

    private val _form = MutableStateFlow(FormState())
    val form: StateFlow<FormState> = _form.asStateFlow()

    private val _submit = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submit: StateFlow<SubmitState> = _submit.asStateFlow()

    init {
        prefillFromProfile()
    }

    private fun prefillFromProfile() {
        viewModelScope.launch {
            val p = restaurantRepository.getProfile()
            _form.update { s ->
                if (p == null) s.copy(prefilled = true)
                else s.copy(
                    businessName = s.businessName.ifBlank { p.shopName ?: "" },
                    legalEntityName = s.legalEntityName.ifBlank { p.shopName ?: "" },
                    businessAddress = s.businessAddress.ifBlank { p.shopAddress ?: "" },
                    fssaiNumber = s.fssaiNumber.ifBlank { p.fssaiNumber ?: "" },
                    gst = s.gst.ifBlank { p.gstin ?: "" },
                    contactEmail = s.contactEmail.ifBlank { p.email ?: "" },
                    contactPhone = s.contactPhone.ifBlank { p.whatsappNumber ?: "" },
                    prefilled = true
                )
            }
        }
    }

    fun update(transform: (FormState) -> FormState) {
        _form.update { transform(it).copy(errors = emptyMap()) }
    }

    private fun validate(s: FormState): Map<String, String> {
        val e = mutableMapOf<String, String>()
        if (s.businessName.isBlank()) e["businessName"] = "Required"
        if (s.legalEntityName.isBlank()) e["legalEntityName"] = "Required (must match PAN/GST)"
        if (s.fssaiNumber.isBlank()) e["fssaiNumber"] = "FSSAI license is mandatory"
        if (s.businessAddress.isBlank()) e["businessAddress"] = "Required"
        if (s.state.isBlank()) e["state"] = "Required"
        if (s.beneficiaryName.isBlank()) e["beneficiaryName"] = "Required"
        if (s.bankName.isBlank()) e["bankName"] = "Required"
        if (s.bankAccountNo.isBlank()) e["bankAccountNo"] = "Required"
        else if (s.confirmAccountNo != s.bankAccountNo) e["confirmAccountNo"] = "Account numbers do not match"
        if (s.ifsc.isBlank()) e["ifsc"] = "Required"
        else if (!IFSC_REGEX.matches(s.ifsc.trim().uppercase())) e["ifsc"] = "Invalid IFSC"
        if (s.contactEmail.isBlank()) e["contactEmail"] = "Required"
        else if (!EMAIL_REGEX.matches(s.contactEmail.trim())) e["contactEmail"] = "Invalid email"
        if (s.contactPhone.isBlank()) e["contactPhone"] = "Required"
        else if (!PHONE_REGEX.matches(s.contactPhone.trim())) e["contactPhone"] = "Enter a 10-digit number"
        if (s.pan.isNotBlank() && !PAN_REGEX.matches(s.pan.trim().uppercase())) e["pan"] = "Invalid PAN"
        return e
    }

    /** @param isResubmit true after a KYC rejection (pushes an update instead of a fresh create). */
    fun submit(isResubmit: Boolean) {
        val s = _form.value
        val errors = validate(s)
        if (errors.isNotEmpty()) {
            _form.update { it.copy(errors = errors) }
            return
        }
        _submit.value = SubmitState.Submitting
        viewModelScope.launch {
            try {
                val req = EasebuzzOnboardRequest(
                    businessName = s.businessName.trim(),
                    legalEntityName = s.legalEntityName.trim(),
                    businessType = s.businessType,
                    pan = s.pan.trim().uppercase().ifBlank { null },
                    gst = s.gst.trim().uppercase().ifBlank { null },
                    fssaiNumber = s.fssaiNumber.trim(),
                    fssaiExpiryDate = s.fssaiExpiryDate,
                    businessAddress = s.businessAddress.trim(),
                    state = s.state.trim(),
                    bankAccountNo = s.bankAccountNo.trim(),
                    ifsc = s.ifsc.trim().uppercase(),
                    bankName = s.bankName.trim(),
                    branchName = s.branchName.trim().ifBlank { null },
                    beneficiaryName = s.beneficiaryName.trim(),
                    contactEmail = s.contactEmail.trim(),
                    contactPhone = s.contactPhone.trim()
                )
                val resp = if (isResubmit) easebuzzRepo.resubmitSubMerchant(req)
                           else easebuzzRepo.onboardSubMerchant(req)
                if (resp.status.equals("success", ignoreCase = true)) {
                    _submit.value = SubmitState.Success(resp.subMerchantId, resp.message)
                } else {
                    _submit.value = SubmitState.Error(resp.message?.ifBlank { null } ?: "Onboarding could not be completed")
                }
            } catch (ex: Exception) {
                _submit.value = SubmitState.Error(ex.message ?: "Network error — please try again")
            }
        }
    }

    fun resetSubmit() {
        _submit.value = SubmitState.Idle
    }

    private companion object {
        val IFSC_REGEX = Regex("^[A-Z]{4}0[A-Z0-9]{6}$")
        val PAN_REGEX = Regex("^[A-Z]{5}[0-9]{4}[A-Z]$")
        val PHONE_REGEX = Regex("^[0-9]{10}$")
        val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
