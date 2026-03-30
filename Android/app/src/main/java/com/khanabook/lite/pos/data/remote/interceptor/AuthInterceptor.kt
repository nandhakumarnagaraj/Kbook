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
    requestBuilder.addHeader("ngrok-skip-browser-warning", "true")

    
    val isExternalApi = request.url.host.contains("facebook.com") || request.url.host.contains("google")
    val isAuthPath = path.contains("/auth/login") || path.contains("/auth/signup") || path.contains("/auth/google") || path.contains("/auth/check-user") || path.contains("/auth/reset-password")
    
    if (!(isAuthPath || isExternalApi)) {
        
        val token = sessionManager.getAuthToken()

        
        
        val isValidJwt = !token.isNullOrBlank() && 
                         token.length > 100 && 
                         token.split(".").size == 3

        if (isValidJwt) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }
    }

    return chain.proceed(requestBuilder.build())
  }
}
