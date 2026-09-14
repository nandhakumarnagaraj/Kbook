package com.khanabook.lite.pos.feature.staff.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khanabook.lite.pos.feature.auth.data.UserEntity
import com.khanabook.lite.pos.feature.auth.data.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
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
