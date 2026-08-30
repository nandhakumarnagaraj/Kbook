package com.khanabook.lite.pos.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.data.repository.MerchantAgreementRepository
import com.khanabook.lite.pos.data.repository.RestaurantRepository
import com.khanabook.lite.pos.domain.manager.SessionManager
import com.khanabook.lite.pos.domain.util.AgreementPdfGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class AgreementStatus(
    val hasAgreement: Boolean = false,
    val signedAt: Long? = null,
    val signerName: String? = null,
    val agreementVersion: String? = null,
    val originalFilename: String? = null
)

sealed class AgreementUiState {
    data object Loading : AgreementUiState()
    data class Ready(val status: AgreementStatus) : AgreementUiState()
    data class Error(val message: String) : AgreementUiState()
}

sealed class AgreementEvent {
    data class Toast(val message: String, val isError: Boolean = false) : AgreementEvent()
    data class OpenFile(val file: File) : AgreementEvent()
}

@HiltViewModel
class MerchantAgreementViewModel @Inject constructor(
    private val repository: MerchantAgreementRepository,
    private val sessionManager: SessionManager,
    private val restaurantRepository: RestaurantRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<AgreementUiState>(AgreementUiState.Loading)
    val uiState: StateFlow<AgreementUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AgreementEvent>()
    val events = _events.asSharedFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _isPrimaryDevice = MutableStateFlow(false)
    val isPrimaryDevice: StateFlow<Boolean> = _isPrimaryDevice.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = AgreementUiState.Loading
            val profile = restaurantRepository.getProfileFlow().firstOrNull()
            _isPrimaryDevice.value = profile?.deviceId == sessionManager.getDeviceId()
            repository.getStatus()
                .onSuccess { map -> _uiState.value = AgreementUiState.Ready(parseStatus(map)) }
                .onFailure { e ->
                    _uiState.value = AgreementUiState.Error(e.message ?: "Failed to load agreement status")
                }
        }
    }

    private fun parseStatus(map: Map<String, Any?>): AgreementStatus {
        return AgreementStatus(
            hasAgreement = map["hasAgreement"] as? Boolean ?: false,
            signedAt = (map["signedAt"] as? Number)?.toLong(),
            signerName = map["signerName"] as? String,
            agreementVersion = map["agreementVersion"] as? String,
            originalFilename = map["originalFilename"] as? String
        )
    }

    fun signAndUpload(signerName: String, signature: Bitmap?) {
        viewModelScope.launch {
            if (signerName.isBlank()) {
                _events.emit(AgreementEvent.Toast("Please enter the signer name", true))
                return@launch
            }
            if (signature == null) {
                _events.emit(AgreementEvent.Toast("Please provide a signature", true))
                return@launch
            }
            _isSubmitting.value = true
            try {
                val file = AgreementPdfGenerator.generate(context, signerName.trim(), signature)
                repository.upload(signerName.trim(), AgreementPdfGenerator.AGREEMENT_VERSION, file)
                    .onSuccess {
                        _events.emit(AgreementEvent.Toast("Agreement signed and uploaded successfully"))
                        load()
                    }
                    .onFailure { e ->
                        _events.emit(AgreementEvent.Toast(e.message ?: "Upload failed", true))
                    }
            } catch (e: Exception) {
                _events.emit(AgreementEvent.Toast(e.message ?: "Failed to generate agreement", true))
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun downloadAndOpen() {
        viewModelScope.launch {
            _isSubmitting.value = true
            repository.download()
                .onSuccess { file -> _events.emit(AgreementEvent.OpenFile(file)) }
                .onFailure { e ->
                    _events.emit(AgreementEvent.Toast(e.message ?: "Download failed", true))
                }
            _isSubmitting.value = false
        }
    }
}
