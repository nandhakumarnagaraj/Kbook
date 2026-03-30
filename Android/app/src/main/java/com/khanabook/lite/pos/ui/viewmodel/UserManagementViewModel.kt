package com.khanabook.lite.pos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.data.local.entity.UserEntity
import com.khanabook.lite.pos.data.repository.UserRepository
import com.khanabook.lite.pos.domain.manager.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class UserManagementViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    val allUsers: Flow<List<UserEntity>> = userRepository.getAllUsers()

    fun addUser(name: String, phone: String, password: String) {
        viewModelScope.launch {
            val user = UserEntity(
                name = name,
                email = phone, 
                whatsappNumber = phone,
                isActive = true,
                createdAt = System.currentTimeMillis()
            )
            userRepository.insertUser(user)
        }
    }

    fun deleteUser(user: UserEntity) {
        viewModelScope.launch {
            userRepository.deleteUser(user)
        }
    }

    fun toggleUserStatus(userId: Long, isActive: Boolean) {
        viewModelScope.launch {
            userRepository.setActivationStatus(userId, isActive)
        }
    }
}
