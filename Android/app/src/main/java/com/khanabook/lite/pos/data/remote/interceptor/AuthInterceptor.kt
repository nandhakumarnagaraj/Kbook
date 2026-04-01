package com.khanabook.lite.pos.data.remote.interceptor

import com.khanabook.lite.pos.domain.manager.SessionManager
import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor @Inject constructor(private val sessionManager: SessionManager) :
        Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    val path = request.url.encodedPath

    val requestBuilder = request.newBuilder()

    // Public auth endpoints that don't require a token
    val isPublicAuthPath = path.contains("/auth/login") ||
            path.contains("/auth/signup") ||
            path.contains("/auth/google") ||
            path.contains("/auth/check-user") ||
            path.contains("/auth/reset-password")

    if (!isPublicAuthPath) {
        val token = sessionManager.getAuthToken()
        if (!token.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }
    }

    val response = chain.proceed(requestBuilder.build())

    // If the server rejects our token, clear the session so the UI navigates to login
    if (response.code == 401 && !isPublicAuthPath) {
        sessionManager.clearSession()
    }

    return response
  }
}
