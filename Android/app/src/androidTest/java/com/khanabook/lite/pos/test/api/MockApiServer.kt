package com.khanabook.lite.pos.test.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.util.concurrent.TimeUnit

class MockApiServer private constructor() {

    private val mockWebServer = MockWebServer()

    private val dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path ?: return ResponseFixtures.notFound("Endpoint")

            return when {
                path.contains("/api/auth/login", ignoreCase = true) -> handleLogin(request)
                path.contains("/api/auth/register", ignoreCase = true) -> handleSignup(request)
                path.contains("/api/auth/refresh", ignoreCase = true) -> handleRefreshToken(request)
                path.contains("/api/auth/logout", ignoreCase = true) -> handleLogout(request)
                path.contains("/api/sync/master", ignoreCase = true) -> handleMasterSync(request)
                path.contains("/api/bills", ignoreCase = true) && request.method == "POST" -> handleBillCreate(request)
                path.contains("/api/bills", ignoreCase = true) -> handleBillsList(request)
                path.contains("/api/orders", ignoreCase = true) && path.contains("/") -> handleOrderDetail(request)
                path.contains("/api/orders", ignoreCase = true) -> handleOrders(request)
                path.contains("/api/menu/sync", ignoreCase = true) -> handleMenuSync(request)
                path.contains("/api/reports", ignoreCase = true) -> handleReports(request)
                path.contains("/api/profile", ignoreCase = true) -> handleProfile(request)
                path.contains("/api/whatsapp", ignoreCase = true) -> handleWhatsApp(request)
                else -> ResponseFixtures.notFound("Endpoint")
            }
        }
    }

    init {
        mockWebServer.dispatcher = dispatcher
    }

    fun start(port: Int = 0) {
        mockWebServer.start(port)
    }

    fun shutdown() {
        mockWebServer.shutdown()
    }

    fun url(path: String): String {
        return mockWebServer.url(path).toString()
    }

    fun enqueue(response: MockResponse) {
        mockWebServer.enqueue(response)
    }

    private fun handleLogin(request: RecordedRequest): MockResponse {
        val body = request.body.readUtf8()
        return when {
            body.contains("wrongpass") || body.contains("Wrong") -> ResponseFixtures.loginFailure()
            body.isBlank() || !body.contains("phone") || !body.contains("password") -> 
                ResponseFixtures.badRequest("Phone and password are required")
            else -> ResponseFixtures.loginSuccess()
        }
    }

    private fun handleSignup(request: RecordedRequest): MockResponse {
        val body = request.body.readUtf8()
        return if (body.isBlank() || !body.contains("phone")) {
            ResponseFixtures.badRequest("Invalid registration data")
        } else {
            ResponseFixtures.signupSuccess()
        }
    }

    private fun handleRefreshToken(request: RecordedRequest): MockResponse {
        return if (request.getHeader("Authorization") != null) {
            ResponseFixtures.refreshTokenSuccess()
        } else {
            ResponseFixtures.unauthorized()
        }
    }

    private fun handleLogout(request: RecordedRequest): MockResponse {
        return if (request.getHeader("Authorization") != null) {
            ResponseFixtures.logoutSuccess()
        } else {
            ResponseFixtures.unauthorized()
        }
    }

    private fun handleMasterSync(request: RecordedRequest): MockResponse {
        return if (request.getHeader("Authorization") != null) {
            ResponseFixtures.masterSyncSuccess()
        } else {
            ResponseFixtures.unauthorized()
        }
    }

    private fun handleBillCreate(request: RecordedRequest): MockResponse {
        val body = request.body.readUtf8()
        return when {
            request.getHeader("Authorization") == null -> ResponseFixtures.unauthorized()
            body.isBlank() || !body.contains("items") -> ResponseFixtures.badRequest("At least one item is required")
            else -> ResponseFixtures.billCreateSuccess()
        }
    }

    private fun handleBillsList(request: RecordedRequest): MockResponse {
        return if (request.getHeader("Authorization") != null) {
            ResponseFixtures.billsList()
        } else {
            ResponseFixtures.unauthorized()
        }
    }

    private fun handleOrders(request: RecordedRequest): MockResponse {
        return if (request.getHeader("Authorization") != null) {
            when {
                request.path!!.contains("status=Pending", ignoreCase = true) -> 
                    ResponseFixtures.ordersByStatus("Pending")
                request.path!!.contains("status=Completed", ignoreCase = true) -> 
                    ResponseFixtures.ordersByStatus("Completed")
                else -> ResponseFixtures.ordersList()
            }
        } else {
            ResponseFixtures.unauthorized()
        }
    }

    private fun handleOrderDetail(request: RecordedRequest): MockResponse {
        val orderId = request.path?.substringAfterLast("/") ?: "ORD-001"
        return if (request.getHeader("Authorization") != null) {
            ResponseFixtures.orderDetail(orderId)
        } else {
            ResponseFixtures.unauthorized()
        }
    }

    private fun handleMenuSync(request: RecordedRequest): MockResponse {
        return if (request.getHeader("Authorization") != null) {
            ResponseFixtures.menuSyncSuccess()
        } else {
            ResponseFixtures.unauthorized()
        }
    }

    private fun handleReports(request: RecordedRequest): MockResponse {
        return if (request.getHeader("Authorization") != null) {
            when {
                request.path!!.contains("range=today", ignoreCase = true) -> 
                    ResponseFixtures.reportsData("Today")
                request.path!!.contains("range=week", ignoreCase = true) -> 
                    ResponseFixtures.reportsData("This Week")
                request.path!!.contains("range=month", ignoreCase = true) -> 
                    ResponseFixtures.reportsData("This Month")
                else -> ResponseFixtures.reportsData("Today")
            }
        } else {
            ResponseFixtures.unauthorized()
        }
    }

    private fun handleProfile(request: RecordedRequest): MockResponse {
        return if (request.getHeader("Authorization") != null) {
            ResponseFixtures.profile()
        } else {
            ResponseFixtures.unauthorized()
        }
    }

    private fun handleWhatsApp(request: RecordedRequest): MockResponse {
        return ResponseFixtures.whatsAppSuccess()
    }

    fun enqueueLoginSuccess() = enqueue(ResponseFixtures.loginSuccess())
    fun enqueueLoginFailure() = enqueue(ResponseFixtures.loginFailure())
    fun enqueueSignupSuccess() = enqueue(ResponseFixtures.signupSuccess())
    fun enqueueMasterSyncSuccess() = enqueue(ResponseFixtures.masterSyncSuccess())
    fun enqueueBillCreateSuccess() = enqueue(ResponseFixtures.billCreateSuccess())
    fun enqueueEmptyOrders() = enqueue(ResponseFixtures.emptyOrders())
    fun enqueueFilteredOrders(status: String) = enqueue(ResponseFixtures.ordersByStatus(status))
    fun enqueueOrderDetail(orderId: String) = enqueue(ResponseFixtures.orderDetail(orderId))
    fun enqueueReportsData(range: String) = enqueue(ResponseFixtures.reportsData(range))
    fun enqueueEmptyReports() = enqueue(ResponseFixtures.emptyReports())
    fun enqueueServerError() = enqueue(ResponseFixtures.serverError())
    fun enqueueUnauthorized() = enqueue(ResponseFixtures.unauthorized())
    fun enqueueForbidden() = enqueue(ResponseFixtures.forbidden())
    fun enqueueNotFound(resource: String = "Resource") = enqueue(ResponseFixtures.notFound(resource))

    fun enqueueSlowResponse(delayMs: Long = 10000) {
        val response = MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("""{"success": true, "message": "Delayed response"}""")
        enqueue(response.setBodyDelay(delayMs, TimeUnit.MILLISECONDS))
    }

    fun enqueueNetworkError() {
        shutdown()
    }

    companion object {
        fun create(): MockApiServer {
            return MockApiServer().apply {
                start()
            }
        }
        
        fun start(): MockApiServer {
            return MockApiServer().apply {
                start(0)
            }
        }
    }
}

fun createMockOkHttpClient(mockServer: MockApiServer): OkHttpClient {
    return OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(Interceptor { chain ->
            val request = chain.request().newBuilder()
                .url(mockServer.url("/"))
                .build()
            chain.proceed(request)
        })
        .build()
}
