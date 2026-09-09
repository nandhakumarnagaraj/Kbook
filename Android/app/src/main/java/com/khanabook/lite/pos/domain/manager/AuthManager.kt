package com.khanabook.lite.pos.domain.manager

import android.util.Log
import org.mindrot.jbcrypt.BCrypt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AuthManager"

@Singleton
class AuthManager @Inject constructor() {

    suspend fun hashPassword(password: String): String = withContext(Dispatchers.Default) {
        BCrypt.hashpw(password, BCrypt.gensalt(12))
    }

    suspend fun verifyPassword(password: String, hash: String): Boolean = withContext(Dispatchers.Default) {
        try {
            BCrypt.checkpw(password, hash)
        } catch (e: Exception) {
            Log.e(TAG, "BCrypt verification error", e)
            false
        }
    }

    suspend fun verifyManagerPin(enteredPin: String, sessionManager: SessionManager): Boolean = withContext(Dispatchers.Default) {
        val pinHash = sessionManager.getPinHash()
        if (!pinHash.isNullOrBlank()) {
            verifyPassword(enteredPin, pinHash)
        } else {
            Log.w(TAG, "No manager PIN configured — access denied until PIN is set")
            false
        }
    }
}
