package com.khanabook.lite.pos.feature.notifications.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.feature.notifications.data.NotificationEntity
import com.khanabook.lite.pos.feature.notifications.data.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    /** List of all push notifications, ordered by creation date descending. */
    val notifications: StateFlow<List<NotificationEntity>> = notificationRepository.getNotifications()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** Unread notification count for the badge indicator. */
    val unreadCount: StateFlow<Int> = notificationRepository.getUnreadCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    /** True while a server refresh is in flight, so the UI can show progress. */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** One-shot error message emitted when a refresh fails; null clears it. */
    private val _refreshError = MutableStateFlow<String?>(null)
    val refreshError: StateFlow<String?> = _refreshError.asStateFlow()

    init {
        refreshFromServer()
    }

    fun refreshFromServer() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            _refreshError.value = null
            val result = notificationRepository.refreshFromServer()
            result.onFailure {
                _refreshError.value = "Couldn't refresh notifications. Check your connection and try again."
            }
            _isRefreshing.value = false
        }
    }

    /** Clears the refresh error after it has been shown. */
    fun consumeRefreshError() {
        _refreshError.value = null
    }

    fun markAsRead(id: Long) {
        viewModelScope.launch {
            notificationRepository.markAsRead(id)
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            notificationRepository.markAllAsRead()
        }
    }
}
