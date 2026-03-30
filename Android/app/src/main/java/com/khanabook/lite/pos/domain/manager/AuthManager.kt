package com.khanabook.lite.pos.domain.manager

import org.mindrot.jbcrypt.BCrypt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor() {

    suspend fun hashPassword(password: String): String = withContext(Dispatchers.Default) {
        BCrypt.hashpw(password, BCrypt.gensalt(12))
    }

    suspend fun verifyPassword(password: String, hash: String): Boolean = withContext(Dispatchers.Default) {
        try {
            BCrypt.checkpw(password, hash)
        } catch (e: Exception) {
            false 
        }
    }
}


