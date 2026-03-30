package com.khanabook.lite.pos.data.remote.interceptor

import com.khanabook.lite.pos.domain.manager.SessionManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {

    private lateinit var sessionManager: SessionManager
    private lateinit var authInterceptor: AuthInterceptor
    private lateinit var chain: Interceptor.Chain
    private lateinit var mockResponse: Response

    @Before
    fun setUp() {
        sessionManager = mockk()
        authInterceptor = AuthInterceptor(sessionManager)
        chain = mockk()
        mockResponse = Response.Builder()
            .request(Request.Builder().url("https://api.example.com").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()
    }

    @Test
    fun `intercept - when token is valid - adds Authorization header`() {
        val validToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
        every { sessionManager.getAuthToken() } returns validToken
        
        val request = Request.Builder().url("https://api.khanabook.com/api/v1/bills").build()
        every { chain.request() } returns request
        
        val requestSlot = slot<Request>()
        every { chain.proceed(capture(requestSlot)) } returns mockResponse

        
        authInterceptor.intercept(chain)

        
        val capturedRequest = requestSlot.captured
        val authHeader = capturedRequest.header("Authorization")
        assertEquals("Bearer $validToken", authHeader)
    }

    @Test
    fun `intercept - when token is null - does NOT add Authorization header`() {
        
        every { sessionManager.getAuthToken() } returns null
        
        val request = Request.Builder().url("https://api.khanabook.com/api/v1/bills").build()
        every { chain.request() } returns request
        
        val requestSlot = slot<Request>()
        every { chain.proceed(capture(requestSlot)) } returns mockResponse

        
        authInterceptor.intercept(chain)

        
        val capturedRequest = requestSlot.captured
        assertNull(capturedRequest.header("Authorization"))
    }

    @Test
    fun `intercept - when token has no dots - does NOT add Authorization header`() {
        
        every { sessionManager.getAuthToken() } returns "invalidtoken"
        
        val request = Request.Builder().url("https://api.khanabook.com/api/v1/bills").build()
        every { chain.request() } returns request
        
        val requestSlot = slot<Request>()
        every { chain.proceed(capture(requestSlot)) } returns mockResponse

        
        authInterceptor.intercept(chain)

        
        val capturedRequest = requestSlot.captured
        assertNull(capturedRequest.header("Authorization"))
    }

    @Test
    fun `intercept - when request is auth endpoint - skips token even if valid`() {
        
        every { sessionManager.getAuthToken() } returns "valid.jwt.token"
        
        val request = Request.Builder().url("https://api.khanabook.com/api/v1/auth/login").build()
        every { chain.request() } returns request
        
        val requestSlot = slot<Request>()
        every { chain.proceed(capture(requestSlot)) } returns mockResponse

        
        authInterceptor.intercept(chain)

        
        val capturedRequest = requestSlot.captured
        assertNull(capturedRequest.header("Authorization"))
    }

    @Test
    fun `intercept - when request path contains login - skips token even if valid`() {
        
        every { sessionManager.getAuthToken() } returns "valid.jwt.token"
        
        val request = Request.Builder().url("https://api.khanabook.com/login").build()
        every { chain.request() } returns request
        
        val requestSlot = slot<Request>()
        every { chain.proceed(capture(requestSlot)) } returns mockResponse

        
        authInterceptor.intercept(chain)

        
        val capturedRequest = requestSlot.captured
        assertNull(capturedRequest.header("Authorization"))
    }
}
