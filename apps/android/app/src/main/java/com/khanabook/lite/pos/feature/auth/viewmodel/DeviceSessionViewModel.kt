package com.khanabook.lite.pos.feature.auth.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.feature.auth.domain.SessionManager
import com.khanabook.lite.pos.feature.auth.data.RestaurantRepository
import com.khanabook.lite.pos.core.network.KhanaBookApi
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

    val canUploadDocuments: Boolean
        get() = sessionManager.isOwner()

    private val _terminalSeries = MutableStateFlow<String?>(null)
    val terminalSeries: StateFlow<String?> = _terminalSeries.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val terminalStatus = api.getTerminalStatus()
                _terminalSeries.value = terminalStatus.terminalSeries
            } catch (e: Exception) {
                _terminalSeries.value = null
            }
        }
    }
}
