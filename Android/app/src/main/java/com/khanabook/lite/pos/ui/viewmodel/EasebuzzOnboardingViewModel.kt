package com.khanabook.lite.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.data.remote.dto.EasebuzzOnboardingRequest
import com.khanabook.lite.pos.data.remote.dto.EasebuzzOnboardingStatusResponse
import com.khanabook.lite.pos.data.repository.EasebuzzOnboardingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class OnboardingStep {
    data object BusinessDetails : OnboardingStep()
    data object BankDetails : OnboardingStep()
    data object OtpVerification : OnboardingStep()
    data object KycStatus : OnboardingStep()
}

sealed class OnboardingUiState {
    data object Loading : OnboardingUiState()
    data object NotStarted : OnboardingUiState()
    data class InProgress(val step: OnboardingStep) : OnboardingUiState()
    data class AwaitingKyc(val status: EasebuzzOnboardingStatusResponse) : OnboardingUiState()
    data class Active(val status: EasebuzzOnboardingStatusResponse) : OnboardingUiState()
    data class Rejected(val status: EasebuzzOnboardingStatusResponse) : OnboardingUiState()
    data class Error(val message: String) : OnboardingUiState()
}

sealed class OnboardingEvent {
    data class Toast(val message: String, val isError: Boolean = false) : OnboardingEvent()
    data object OnboardingComplete : OnboardingEvent()
}

@HiltViewModel
class EasebuzzOnboardingViewModel @Inject constructor(
    private val repository: EasebuzzOnboardingRepository,
    private val restaurantRepository: com.khanabook.lite.pos.data.repository.RestaurantRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<OnboardingUiState>(OnboardingUiState.Loading)
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<OnboardingEvent>()
    val events = _events.asSharedFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _currentStep = MutableStateFlow<OnboardingStep>(OnboardingStep.BusinessDetails)
    val currentStep: StateFlow<OnboardingStep> = _currentStep.asStateFlow()

    // IFSC auto-fetch result
    private val _ifscBankInfo = MutableStateFlow<IfscBankInfo?>(null)
    val ifscBankInfo: StateFlow<IfscBankInfo?> = _ifscBankInfo.asStateFlow()

    // Retained form data across steps
    var businessName = ""
    var legalEntityName = ""
    var businessType = "SOLE_PROPRIETORSHIP"
    var pan = ""
    var gst = ""
    var businessAddress = ""
    var state = ""
    var contactEmail = ""
    var contactPhone = ""
    var bankAccountNo = ""
    var ifsc = ""
    var bankName = ""
    var branchName = ""
    var beneficiaryName = ""
    var fssaiNumber = ""

    init {
        prefillFromProfile()
        loadStatus()
    }

    private fun prefillFromProfile() {
        viewModelScope.launch {
            val profile = restaurantRepository.getProfileFlow().firstOrNull()
            if (profile != null && businessName.isBlank()) {
                businessName = profile.shopName ?: ""
                contactPhone = profile.whatsappNumber ?: ""
                contactEmail = profile.email ?: ""
                businessAddress = profile.shopAddress ?: ""
                fssaiNumber = profile.fssaiNumber ?: ""
                gst = profile.gstin ?: ""
            }
        }
    }

    fun lookupIfsc(ifscCode: String) {
        if (ifscCode.length != 11 || !isValidIfsc(ifscCode)) {
            _ifscBankInfo.value = null
            return
        }
        viewModelScope.launch {
            try {
                val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    java.net.URL("https://ifsc.razorpay.com/$ifscCode").readText()
                }
                val json = org.json.JSONObject(response)
                val info = IfscBankInfo(
                    bankName = json.optString("BANK", ""),
                    branchName = json.optString("BRANCH", ""),
                    city = json.optString("CITY", ""),
                    state = json.optString("STATE", "")
                )
                _ifscBankInfo.value = info
                // Auto-fill bank details
                if (info.bankName.isNotBlank()) bankName = info.bankName
                if (info.branchName.isNotBlank()) branchName = info.branchName
            } catch (e: Exception) {
                _ifscBankInfo.value = null
            }
        }
    }

    companion object {
        private val PAN_REGEX = Regex("^[A-Z]{5}[0-9]{4}[A-Z]$")
        private val IFSC_REGEX = Regex("^[A-Z]{4}0[A-Z0-9]{6}$")

        fun isValidPan(pan: String): Boolean = PAN_REGEX.matches(pan)
        fun isValidIfsc(ifsc: String): Boolean = IFSC_REGEX.matches(ifsc)
    }

    fun loadStatus() {
        viewModelScope.launch {
            _uiState.value = OnboardingUiState.Loading
            repository.getOnboardingStatus()
                .onSuccess { status ->
                    _uiState.value = when {
                        status.isActive -> OnboardingUiState.Active(status)
                        status.status == "REJECTED" -> OnboardingUiState.Rejected(status)
                        status.status == "KYC_SUBMITTED" || status.status == "PENDING_KYC" ->
                            OnboardingUiState.AwaitingKyc(status)
                        status.hasSubMerchant -> OnboardingUiState.AwaitingKyc(status)
                        else -> OnboardingUiState.NotStarted
                    }
                }
                .onFailure { e ->
                    _uiState.value = OnboardingUiState.NotStarted
                }
        }
    }

    fun startOnboarding() {
        _currentStep.value = OnboardingStep.BusinessDetails
        _uiState.value = OnboardingUiState.InProgress(OnboardingStep.BusinessDetails)
    }

    fun goToStep(step: OnboardingStep) {
        _currentStep.value = step
        _uiState.value = OnboardingUiState.InProgress(step)
    }

    fun goBack(): Boolean {
        return when (_currentStep.value) {
            OnboardingStep.BankDetails -> {
                goToStep(OnboardingStep.BusinessDetails)
                true
            }
            OnboardingStep.OtpVerification -> {
                goToStep(OnboardingStep.BankDetails)
                true
            }
            else -> false
        }
    }

    fun submitBusinessDetails() {
        goToStep(OnboardingStep.BankDetails)
    }

    fun submitBankDetailsAndOnboard() {
        viewModelScope.launch {
            _isSubmitting.value = true
            val request = EasebuzzOnboardingRequest(
                businessName = businessName.trim(),
                legalEntityName = legalEntityName.trim().ifBlank { null },
                businessType = businessType,
                pan = pan.trim().uppercase(),
                gst = gst.trim().ifBlank { null },
                businessAddress = businessAddress.trim(),
                state = state.trim(),
                contactEmail = contactEmail.trim(),
                contactPhone = contactPhone.trim(),
                bankAccountNo = bankAccountNo.trim(),
                ifsc = ifsc.trim().uppercase(),
                bankName = bankName.trim(),
                branchName = branchName.trim(),
                beneficiaryName = beneficiaryName.trim(),
                fssaiNumber = fssaiNumber.trim().ifBlank { null }
            )
            repository.onboard(request)
                .onSuccess { response ->
                    if (response.status == "success") {
                        _events.emit(OnboardingEvent.Toast("Registration submitted successfully!"))
                        goToStep(OnboardingStep.OtpVerification)
                    } else {
                        _events.emit(OnboardingEvent.Toast(
                            response.message ?: "Submission failed. Please check your details.",
                            isError = true
                        ))
                    }
                }
                .onFailure { e ->
                    _events.emit(OnboardingEvent.Toast(
                        e.message ?: "Network error. Please try again.",
                        isError = true
                    ))
                }
            _isSubmitting.value = false
        }
    }

    fun verifyOtp(otp: String) {
        viewModelScope.launch {
            _isSubmitting.value = true
            repository.verifyOtp(otp)
                .onSuccess { result ->
                    val status = result["status"]?.toString() ?: ""
                    if (status.contains("true", ignoreCase = true) || status == "success") {
                        _events.emit(OnboardingEvent.Toast("OTP verified! Generating KYC access..."))
                        generateKycAccessKey()
                    } else {
                        _events.emit(OnboardingEvent.Toast(
                            result["error"]?.toString() ?: "Invalid OTP. Please try again.",
                            isError = true
                        ))
                    }
                }
                .onFailure { e ->
                    _events.emit(OnboardingEvent.Toast(
                        e.message ?: "Verification failed. Please try again.",
                        isError = true
                    ))
                }
            _isSubmitting.value = false
        }
    }

    fun resendOtp() {
        viewModelScope.launch {
            repository.resendOtp()
                .onSuccess {
                    _events.emit(OnboardingEvent.Toast("OTP resent to your registered phone"))
                }
                .onFailure { e ->
                    _events.emit(OnboardingEvent.Toast(
                        e.message ?: "Failed to resend OTP",
                        isError = true
                    ))
                }
        }
    }

    private suspend fun generateKycAccessKey() {
        repository.generateKycAccessKey()
            .onSuccess {
                _events.emit(OnboardingEvent.Toast("KYC portal ready! Please complete verification."))
                goToStep(OnboardingStep.KycStatus)
                loadStatus()
            }
            .onFailure {
                goToStep(OnboardingStep.KycStatus)
                loadStatus()
            }
    }

    fun resubmitAfterRejection() {
        // Delegates to startOnboarding() which lets user edit and re-submit through the normal flow.
        // The backend /resubmit endpoint is called via submitBankDetailsAndOnboard() after re-edit.
        startOnboarding()
    }
}

data class IfscBankInfo(
    val bankName: String,
    val branchName: String,
    val city: String = "",
    val state: String = ""
)
