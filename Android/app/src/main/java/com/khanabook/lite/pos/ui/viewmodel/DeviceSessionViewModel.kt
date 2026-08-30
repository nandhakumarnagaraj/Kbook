package com.khanabook.lite.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.domain.manager.SessionManager
import com.khanabook.lite.pos.data.repository.RestaurantRepository
import com.khanabook.lite.pos.data.remote.api.KhanaBookApi
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
    private val restaurantRepository: RestaurantRepository,
    private val api: KhanaBookApi
) : ViewModel() {

    private val _isPrimaryDevice = MutableStateFlow(false)
    val isPrimaryDevice: StateFlow<Boolean> = _isPrimaryDevice.asStateFlow()

    private val _terminalSeries = MutableStateFlow<String?>(null)
    val terminalSeries: StateFlow<String?> = _terminalSeries.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val terminalStatus = api.getTerminalStatus()
                _isPrimaryDevice.value = terminalStatus.isPrimary
                _terminalSeries.value = terminalStatus.terminalSeries
            } catch (e: Exception) {
                _isPrimaryDevice.value = false
                _terminalSeries.value = null
            }
        }
    }
}
