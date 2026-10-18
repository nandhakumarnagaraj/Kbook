package com.khanabook.lite.pos.feature.payments.viewmodel
import com.khanabook.lite.pos.feature.auth.data.RestaurantRepository

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.feature.payments.data.EasebuzzOnboardingRequest
import com.khanabook.lite.pos.feature.payments.data.EasebuzzOnboardingStatusResponse
import com.khanabook.lite.pos.feature.payments.data.EasebuzzOnboardingRepository
import com.khanabook.lite.pos.feature.payments.data.EasebuzzPaymentReadiness
import com.khanabook.lite.pos.core.util.UserMessageSanitizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File
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

    /** KYC lapsed after approval: the server flags this and payment creation is refused. */
    data class Expired(val status: EasebuzzOnboardingStatusResponse) : OnboardingUiState()
    data class Error(val message: String) : OnboardingUiState()
}

sealed class OnboardingEvent {
    data class Toast(
        val message: String,
        val isError: Boolean = false,
        /** Snackbar action label. When set the snackbar stays until dismissed or tapped. */
        val actionLabel: String? = null,
        /** URL opened when the action is tapped. */
        val actionUrl: String? = null,
    ) : OnboardingEvent()
    data object OnboardingComplete : OnboardingEvent()
    data class OpenFile(val file: File) : OnboardingEvent()
}

sealed class PaymentReadinessUiState {
    data object Loading : PaymentReadinessUiState()
    data class Ready(val readiness: EasebuzzPaymentReadiness) : PaymentReadinessUiState()
    data class Unavailable(val message: String) : PaymentReadinessUiState()
}

@HiltViewModel
class EasebuzzOnboardingViewModel @Inject constructor(
    private val repository: EasebuzzOnboardingRepository,
    private val restaurantRepository: com.khanabook.lite.pos.feature.auth.data.RestaurantRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<OnboardingUiState>(OnboardingUiState.Loading)
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _paymentReadiness = MutableStateFlow<PaymentReadinessUiState>(PaymentReadinessUiState.Loading)
    val paymentReadiness: StateFlow<PaymentReadinessUiState> = _paymentReadiness.asStateFlow()

    fun loadPaymentReadiness() {
        viewModelScope.launch {
            _paymentReadiness.value = PaymentReadinessUiState.Loading
            repository.getPaymentReadiness()
                .onSuccess { readiness ->
                    _paymentReadiness.value = PaymentReadinessUiState.Ready(readiness)
                    mirrorEasebuzzSwitch(readiness.easebuzzEnabled)
                }
                .onFailure { e ->
                    _paymentReadiness.value = PaymentReadinessUiState.Unavailable(
                        UserMessageSanitizer.sanitize(e, "Cannot verify online payment setup. Reconnect and open this screen again.")
                    )
                }
        }
    }

    /**
     * The server row owns the Easebuzz switch: it is owner-gated and confirmed server-side before
     * it is stored (RestaurantRepository.savePaymentProfile), so the local row only mirrors it.
     *
     * The mirror can lag - a web-admin change, or a till whose master sync has not run yet - and a
     * stale copy keeps offering "Send Payment Link" in billing. Opening payment settings is the
     * moment the server's answer is in hand, so correct the mirror here rather than only printing
     * a sentence about the disagreement.
     *
     * saveProfileLocally, not saveProfile: this is the server's value being mirrored, not a local
     * edit, so the row must not be marked unsynced and no push may be triggered.
     */
    private suspend fun mirrorEasebuzzSwitch(serverEnabled: Boolean) {
        val profile = restaurantRepository.getProfileFlow().firstOrNull() ?: return
        if (profile.easebuzzEnabled == serverEnabled) return
        restaurantRepository.saveProfileLocally(profile.copy(easebuzzEnabled = serverEnabled))
    }

    private val _events = MutableSharedFlow<OnboardingEvent>()
    val events = _events.asSharedFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _currentStep = MutableStateFlow<OnboardingStep>(OnboardingStep.BusinessDetails)
    val currentStep: StateFlow<OnboardingStep> = _currentStep.asStateFlow()

    // IFSC auto-fetch result
    private val _ifscBankInfo = MutableStateFlow<IfscBankInfo?>(null)
    val ifscBankInfo: StateFlow<IfscBankInfo?> = _ifscBankInfo.asStateFlow()

    // FSSAI auto-fetch result
    private val _fssaiInfo = MutableStateFlow<com.khanabook.lite.pos.feature.payments.data.FssaiLookupResponse?>(null)
    val fssaiInfo: StateFlow<com.khanabook.lite.pos.feature.payments.data.FssaiLookupResponse?> = _fssaiInfo.asStateFlow()

    private val _isLookingUpFssai = MutableStateFlow(false)
    val isLookingUpFssai: StateFlow<Boolean> = _isLookingUpFssai.asStateFlow()

    // Retained form data across steps
    var businessName = ""
    var legalEntityName = ""
    // No default: a Pvt Ltd owner who never opened the dropdown used to submit as a sole
    // proprietorship, which drives the wrong Easebuzz nature code and the wrong proof rules.
    var businessType = ""
    // Easebuzz fixes the MCC at creation and cannot change it afterwards, so the outlet format
    // must be chosen explicitly rather than defaulting every restaurant to dine-in.
    var restaurantFormat = ""
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

    /**
     * Which submit endpoint the flow must end on. A merchant that already exists at Easebuzz
     * (REJECTED) cannot go through /onboard - the server answers ONBOARDING_ALREADY_SUBMITTED -
     * so an edit started from the rejection screen has to finish on /resubmit.
     */
    private var resubmitMode = false

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

    fun lookupFssai(fssaiNo: String) {
        val clean = fssaiNo.trim()
        if (clean.length != 14 || !clean.all { it.isDigit() }) {
            _fssaiInfo.value = null
            return
        }
        viewModelScope.launch {
            _isLookingUpFssai.value = true
            repository.lookupFssai(clean)
                .onSuccess { info ->
                    if (info.valid) {
                        _fssaiInfo.value = info
                    } else {
                        _fssaiInfo.value = null
                    }
                }
                .onFailure {
                    _fssaiInfo.value = null
                }
            _isLookingUpFssai.value = false
        }
    }

    fun clearFssaiInfo() {
        _fssaiInfo.value = null
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
                        status.status == "KYC_EXPIRED" -> OnboardingUiState.Expired(status)
                        status.status == "KYC_SUBMITTED" || status.status == "PENDING_KYC" || status.status == "CPV_PENDING" ->
                            OnboardingUiState.AwaitingKyc(status)
                        status.hasSubMerchant -> OnboardingUiState.AwaitingKyc(status)
                        else -> {
                            _currentStep.value = OnboardingStep.BusinessDetails
                            OnboardingUiState.InProgress(OnboardingStep.BusinessDetails)
                        }
                    }
                }
                .onFailure { e ->
                    _uiState.value = OnboardingUiState.Error(
                        UserMessageSanitizer.sanitize(e, "Unable to verify Easebuzz account status. Please reconnect and retry.")
                    )
                }
        }
    }

    fun startOnboarding() {
        startOnboardingFlow(resubmit = false)
    }

    private fun startOnboardingFlow(resubmit: Boolean) {
        resubmitMode = resubmit
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
                restaurantFormat = restaurantFormat.trim().ifBlank { null },
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
            val submission = if (resubmitMode) repository.resubmit(request) else repository.onboard(request)
            submission
                .onSuccess { response ->
                    if (response.status == "success") {
                        if (resubmitMode) {
                            // No new OTP: the sub-merchant is already registered. Easebuzz re-reviews
                            // the corrected details and sends a new KYC webhook.
                            resubmitMode = false
                            _events.emit(OnboardingEvent.Toast("Details resubmitted. Easebuzz will re-review your KYC."))
                            goToStep(OnboardingStep.KycStatus)
                            loadStatus()
                        } else {
                            _events.emit(OnboardingEvent.Toast("Registration submitted successfully!"))
                            goToStep(OnboardingStep.OtpVerification)
                        }
                    } else {
                        _events.emit(OnboardingEvent.Toast(
                            response.message ?: "Submission failed. Please check your details.",
                            isError = true
                        ))
                    }
                }
                .onFailure { e ->
                    _events.emit(OnboardingEvent.Toast(
                        UserMessageSanitizer.sanitize(e, "Network error. Please try again."),
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
                        UserMessageSanitizer.sanitize(e, "Verification failed. Please try again."),
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
                        UserMessageSanitizer.sanitize(e, "Failed to resend OTP"),
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
        // Same edit flow, but the final submit must reach /resubmit: the merchant already has a
        // sub-merchant id at Easebuzz, and /onboard would be refused for a REJECTED row.
        startOnboardingFlow(resubmit = true)
    }

    /**
     * Records which document the merchant has and hands them the Easebuzz portal, the only place
     * a document can go: Easebuzz has no document-ingest API. Nothing is uploaded, so there is
     * no "uploaded successfully" outcome to report.
     */
    fun requestKycPortalLink(docType: String, proofType: String? = null) {
        viewModelScope.launch {
            _isSubmitting.value = true
            repository.requestKycPortalLink(docType, proofType)
                .onSuccess { result ->
                    // The message is only useful if it can be acted on: without a portal
                    // link this leaves the merchant on a screen with no way forward, which
                    // is exactly what the upload path used to do.
                    val kycUrl = result["kycUrl"]?.takeIf { it.isNotBlank() }
                    _events.emit(OnboardingEvent.Toast(
                        result["message"]
                            ?: "Upload this document on the Easebuzz portal to finish verification.",
                        isError = true,
                        actionLabel = if (kycUrl != null) "Open portal" else null,
                        actionUrl = kycUrl
                    ))
                    loadStatus()
                }
                .onFailure { e ->
                    // The status payload already carries the portal link. When the link request
                    // fails — an older server that has no such endpoint, or a dropped
                    // connection — the merchant must still get a way forward instead of a dead
                    // end on the one screen that exists to unblock them.
                    val cachedUrl = cachedKycPortalUrl()
                    if (cachedUrl != null) {
                        _events.emit(OnboardingEvent.Toast(
                            "Upload this document on the Easebuzz portal to finish verification.",
                            isError = true,
                            actionLabel = "Open portal",
                            actionUrl = cachedUrl
                        ))
                    } else {
                        _events.emit(OnboardingEvent.Toast(
                            UserMessageSanitizer.sanitize(e, "Could not open the Easebuzz portal. Please try again."),
                            isError = true
                        ))
                    }
                }
            _isSubmitting.value = false
        }
    }

    /** Portal link from the last loaded status, usable when the link request itself fails. */
    private fun cachedKycPortalUrl(): String? = when (val state = _uiState.value) {
        is OnboardingUiState.AwaitingKyc -> state.status.kycUrl
        is OnboardingUiState.Active -> state.status.kycUrl
        is OnboardingUiState.Rejected -> state.status.kycUrl
        is OnboardingUiState.Expired -> state.status.kycUrl
        else -> null
    }?.takeIf { it.isNotBlank() }

    fun downloadKycDocument(docType: String) {
        viewModelScope.launch {
            _isSubmitting.value = true
            repository.downloadKycDocument(docType)
                .onSuccess { file ->
                    _events.emit(OnboardingEvent.OpenFile(file))
                }
                .onFailure { e ->
                    _events.emit(OnboardingEvent.Toast(
                        UserMessageSanitizer.sanitize(e, "Could not download document."),
                        isError = true
                    ))
                }
            _isSubmitting.value = false
        }
    }
}

data class IfscBankInfo(
    val bankName: String,
    val branchName: String,
    val city: String = "",
    val state: String = ""
)
