package com.khanabook.lite.pos.data.remote.interceptor

import com.khanabook.lite.pos.BuildConfig
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

    // Always send device ID so the server can validate device binding
    val deviceId = sessionManager.getDeviceId()
    if (deviceId.isNotBlank()) {
        requestBuilder.addHeader("X-Device-Id", deviceId)
    }

    // FIX #13 — API versioning: send version code so the server can track
    // which app versions are active and issue deprecation warnings.
    requestBuilder.addHeader("X-App-Version", BuildConfig.VERSION_CODE.toString())
    requestBuilder.addHeader("X-App-Platform", "android")

    // Public endpoints — any path under /auth/ or containing /login
    val isPublicAuthPath = path.contains("/auth/") || path.contains("/login")

    if (!isPublicAuthPath) {
        val token = sessionManager.getAuthToken()
        // Only attach structurally-valid JWTs (header.payload.signature = 2 dots minimum)
        if (!token.isNullOrBlank() && token.count { it == '.' } >= 2) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }
    }

    val response = chain.proceed(requestBuilder.build())

    // If the server rejects our token, clear auth state but preserve local app state.
    if (response.code == 401 && !isPublicAuthPath) {
        sessionManager.invalidateAuthSession()
    }

    return response
  }
}
