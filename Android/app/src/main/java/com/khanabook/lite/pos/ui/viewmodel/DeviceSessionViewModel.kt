package com.khanabook.lite.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.domain.manager.SessionManager
import com.khanabook.lite.pos.data.repository.RestaurantRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceSessionViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val restaurantRepository: RestaurantRepository
) : ViewModel() {

    private val _isPrimaryDevice = MutableStateFlow(false)
    val isPrimaryDevice: StateFlow<Boolean> = _isPrimaryDevice.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = restaurantRepository.getProfileFlow().firstOrNull()
            _isPrimaryDevice.value = profile?.deviceId != null &&
                profile.deviceId == sessionManager.getDeviceId()
        }
    }
}
