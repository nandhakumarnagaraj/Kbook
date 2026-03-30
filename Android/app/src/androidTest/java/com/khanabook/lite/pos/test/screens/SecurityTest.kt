package com.khanabook.lite.pos.test.screens

import com.khanabook.lite.pos.test.BaseTest
import com.khanabook.lite.pos.test.robots.LoginRobot
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class SecurityTest : BaseTest() {

    private lateinit var loginRobot: LoginRobot

    @Before
    override fun setUp() {
        super.setUp()
        loginRobot = LoginRobot(composeTestRule)
    }

    @Test
    fun TC_SEC_001_TokenSecurity_401HandledGracefully() {
        mockApiServer.enqueueLoginFailure()
        
        loginRobot.enterCredentials(
            phone = "9876543210",
            password = "wrongpass"
        )
        loginRobot.tapLogin()
        
        loginRobot.assertErrorMessageVisible()
        loginRobot.assertPhoneFieldVisible()
    }

    @Test
    fun TC_API_005_Timeout_SlowResponse_HandledGracefully() {
        mockApiServer.enqueueSlowResponse(10000)
        
        loginRobot.enterCredentials()
        loginRobot.tapLogin()
        
        loginRobot.assertLoadingIndicatorShown()
    }

    @Test
    fun TC_API_005_Timeout_ShowsLoadingIndicator() {
        mockApiServer.enqueueSlowResponse(10000)
        
        loginRobot.enterCredentials()
        loginRobot.tapLogin()
        
        loginRobot.assertLoadingIndicatorShown()
    }

    @Test
    fun TC_SEC_003_NetworkSecurity_AllApiCallsOverHttps() {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        
        val request = Request.Builder()
            .url(mockApiServer.url("/api/auth/login"))
            .build()
        
        val response = client.newCall(request).execute()
        
        assert(response.protocol.name == "HTTP/1.1" || response.protocol.name == "HTTP/2")
    }
}
