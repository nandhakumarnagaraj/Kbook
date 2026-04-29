package com.khanabook.lite.pos.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.data.repository.RestaurantRepository
import com.khanabook.lite.pos.domain.util.MultipartUtils
import com.khanabook.lite.pos.domain.util.UserMessageSanitizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val restaurantRepository: RestaurantRepository
) : ViewModel() {

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _upiQrUploadLoading = MutableStateFlow(false)
    val upiQrUploadLoading: StateFlow<Boolean> = _upiQrUploadLoading.asStateFlow()

    private val _upiQrUploadSuccess = MutableStateFlow(false)
    val upiQrUploadSuccess: StateFlow<Boolean> = _upiQrUploadSuccess.asStateFlow()

    fun clearMessages() {
        _error.value = null
        _upiQrUploadSuccess.value = false
    }

    fun showError(message: String) {
        _error.value = message
    }

    fun uploadUpiQr(context: Context, uri: Uri, onUploaded: (String) -> Unit) {
        viewModelScope.launch {
            _upiQrUploadLoading.value = true
            _error.value = null
            _upiQrUploadSuccess.value = false
            try {
                val part = withContext(Dispatchers.IO) {
                    MultipartUtils.imageUriToPart(context.applicationContext, uri)
                }
                val url = restaurantRepository.uploadUpiQr(part)
                _upiQrUploadSuccess.value = true
                onUploaded(url)
            } catch (e: Exception) {
                _error.value = UserMessageSanitizer.sanitize(e, "UPI QR upload failed. Please try again.")
            } finally {
                _upiQrUploadLoading.value = false
            }
        }
    }
}
