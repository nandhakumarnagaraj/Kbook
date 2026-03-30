package com.khanabook.lite.pos.test.api

import okhttp3.mockwebserver.MockResponse
import java.util.concurrent.TimeUnit

object ResponseFixtures {

    private const val HEADER_CONTENT_TYPE = "Content-Type"
    private const val HEADER_APP_JSON = "application/json"
    private const val HEADER_AUTH = "Authorization"

    private val validAccessToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test_access_token"
    private val validRefreshToken = "refresh_token_valid_12345"

    fun loginSuccess(): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .setHeader(HEADER_CONTENT_TYPE, HEADER_APP_JSON)
            .setHeader(HEADER_AUTH, "Bearer $validAccessToken")
            .setBody("""
                {
                    "success": true,
                    "message": "Login successful",
                    "data": {
                        "user": {
                            "id": "user_001",
                            "name": "Test User",
                            "phone": "9876543210",
                            "email": "test@example.com",
                            "role": "owner"
                        },
                        "tokens": {
                            "accessToken": "$validAccessToken",
                            "refreshToken": "$validRefreshToken",
                            "expiresIn": 86400
                        }
                    }
                }
            """.trimIndent())
    }

    fun loginFailure(): MockResponse {
        return MockResponse()
            .setResponseCode(401)
            .setHeader(HEADER_CONTENT_TYPE, HEADER_APP_JSON)
            .setBody("""
                {
                    "success": false,
                    "message": "Authentication failed",
                    "error": {
                        "code": "AUTH_FAILED",
                        "details": "Invalid phone number or password"
                    }
                }
            """.trimIndent())
    }

    fun signupSuccess(): MockResponse {
        return MockResponse()
            .setResponseCode(201)
            .setHeader(HEADER_CONTENT_TYPE, HEADER_APP_JSON)
            .setBody("""
                {
                    "success": true,
                    "message": "Registration successful",
                    "data": {
                        "user": {
                            "id": "user_002",
                            "name": "New User",
                            "phone": "9876543211",
                            "email": "newuser@example.com",
                            "role": "owner"
                        },
                        "tokens": {
                            "accessToken": "$validAccessToken",
                            "refreshToken": "$validRefreshToken",
                            "expiresIn": 86400
                        }
                    }
                }
            """.trimIndent())
    }

    fun refreshTokenSuccess(): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .setHeader(HEADER_CONTENT_TYPE, HEADER_APP_JSON)
            .setBody("""
                {
                    "success": true,
                    "data": {
                        "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.refreshed_token",
                        "refreshToken": "new_refresh_token",
                        "expiresIn": 86400
                    }
                }
            """.trimIndent())
    }

    fun logoutSuccess(): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .setHeader(HEADER_CONTENT_TYPE, HEADER_APP_JSON)
            .setBody("""
                {
                    "success": true,
                    "message": "Logged out successfully"
                }
            """.trimIndent())
    }

    fun masterSyncSuccess(): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .setHeader(HEADER_CONTENT_TYPE, HEADER_APP_JSON)
            .setBody("""
                {
                    "success": true,
                    "data": {
                        "categories": [
                            {"id": "cat_001", "name": "Starters", "priority": 1, "active": true},
                            {"id": "cat_002", "name": "Main Course", "priority": 2, "active": true},
                            {"id": "cat_003", "name": "Beverages", "priority": 3, "active": true},
                            {"id": "cat_004", "name": "Desserts", "priority": 4, "active": true}
                        ],
                        "menuItems": [
                            {"id": "item_001", "name": "Burger", "price": 150.00, "categoryId": "cat_001", "active": true, "variants": []},
                            {"id": "item_002", "name": "Pizza", "price": 250.00, "categoryId": "cat_002", "active": true, "variants": [
                                {"id": "var_001", "name": "Small", "price": 200.00},
                                {"id": "var_002", "name": "Large", "price": 350.00}
                            ]},
                            {"id": "item_003", "name": "Pasta", "price": 180.00, "categoryId": "cat_002", "active": true, "variants": []},
                            {"id": "item_004", "name": "Coke", "price": 50.00, "categoryId": "cat_003", "active": true, "variants": []},
                            {"id": "item_005", "name": "Coffee", "price": 80.00, "categoryId": "cat_003", "active": true, "variants": []}
                        ],
                        "profile": {
                            "restaurantName": "Test Restaurant",
                            "address": "123 Test Street",
                            "phone": "9876543210",
                            "gstin": "XX123456789012X"
                        },
                        "settings": {
                            "currency": "INR",
                            "taxPercentage": 18
                        }
                    },
                    "lastSyncTime": "2024-01-15T10:30:00Z"
                }
            """.trimIndent())
    }

    fun billCreateSuccess(): MockResponse {
        val timestamp = System.currentTimeMillis()
        return MockResponse()
            .setResponseCode(201)
            .setHeader(HEADER_CONTENT_TYPE, HEADER_APP_JSON)
            .setBody("""
                {
                    "success": true,
                    "message": "Bill created successfully",
                    "data": {
                        "billId": "BILL-$timestamp",
                        "orderId": "ORD-$timestamp",
                        "status": "completed",
                        "subtotal": 400.00,
                        "tax": 72.00,
                        "total": 472.00,
                        "items": [
                            {"name": "Burger", "quantity": 2, "price": 150.00, "total": 300.00},
                            {"name": "Coke", "quantity": 2, "price": 50.00, "total": 100.00}
                        ],
                        "paymentMethod": "cash",
                        "createdAt": "2024-01-15T10:35:00Z"
                    }
                }
            """.trimIndent())
    }

    fun billsList(): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .setHeader(HEADER_CONTENT_TYPE, HEADER_APP_JSON)
            .setBody("""
                {
                    "success": true,
                    "data": [
                        {"id": "BILL-001", "orderId": "ORD-001", "total": 472.00, "status": "completed", "createdAt": "2024-01-15T10:35:00Z"},
                        {"id": "BILL-002", "orderId": "ORD-002", "total": 295.00, "status": "completed", "createdAt": "2024-01-15T09:20:00Z"}
                    ]
                }
            """.trimIndent())
    }

    fun ordersList(): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .setHeader(HEADER_CONTENT_TYPE, HEADER_APP_JSON)
            .setBody("""
                {
                    "success": true,
                    "data": [
                        {"id": "ORD-001", "billId": "BILL-001", "status": "pending", "total": 472.00, "itemCount": 4, "createdAt": "2024-01-15T10:35:00Z"},
                        {"id": "ORD-002", "billId": "BILL-002", "status": "completed", "total": 295.00, "itemCount": 2, "createdAt": "2024-01-15T09:20:00Z"}
                    ]
                }
            """.trimIndent())
    }

    fun ordersByStatus(status: String): MockResponse {
        val orders = when (status.lowercase()) {
            "pending" -> """{"id": "ORD-001", "status": "pending", "total": 472.00, "itemCount": 4}"""
            "completed" -> """{"id": "ORD-002", "status": "completed", "total": 295.00, "itemCount": 2}"""
            "cancelled" -> """{"id": "ORD-003", "status": "cancelled", "total": 150.00, "itemCount": 1}"""
            else -> """{"id": "ORD-001", "status": "$status", "total": 300.00, "itemCount": 2}"""
        }
        return MockResponse()
            .setResponseCode(200)
            .setHeader(HEADER_CONTENT_TYPE, HEADER_APP_JSON)
            .setBody("""{"success": true, "data": [$orders]}""")
    }

    fun emptyOrders(): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .setHeader(HEADER_CONTENT_TYPE, HEADER_APP_JSON)
            .setBody("""{"success": true, "data": [], "message": "No orders found"}""")
    }

    fun orderDetail(orderId: String): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .setHeader(HEADER_CONTENT_TYPE, HEADER_APP_JSON)
            .setBody("""
                {
                    "success": true,
                    "data": {
                        "id": "$orderId",
                        "billId": "BILL-001",
                        "status": "pending",
                        "items": [
                            {"name": "Burger", "quantity": 2, "price": 150.00, "total": 300.00},
                            {"name": "Coke", "quantity": 2, "price": 50.00, "total": 100.00}
                        ],
                        "subtotal": 400.00,
                        "tax": 72.00,
                        "total": 472.00,
                        "paymentMethod": "cash",
                        "customerName": null,
                        "createdAt": "2024-01-15T10:35:00Z",
                        "updatedAt": "2024-01-15T10:35:00Z"
                    }
                }
            """.trimIndent())
    }

    fun menuSyncSuccess(): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .setHeader(HEADER_CONTENT_TYPE, HEADER_APP_JSON)
            .setBody("""
                {
                    "success": true,
                    "message": "Menu synchronized successfully",
                    "data": {
                        "syncedCategories": 4,
                        "syncedItems": 5,
                        "updatedItems": 2,
                        "newItems": 3,
                        "deletedItems": 0
                    }
                }
            """.trimIndent())
    }

    fun reportsData(range: String): MockResponse {
        val (revenue, orders) = when (range.lowercase()) {
            "today" -> Pair(2500.00, 25)
            "this week" -> Pair(15000.00, 150)
            "this month" -> Pair(65000.00, 650)
            else -> Pair(2500.00, 25)
        }
        return MockResponse()
            .setResponseCode(200)
            .setHeader(HEADER_CONTENT_TYPE, HEADER_APP_JSON)
            .setBody("""
                {
                    "success": true,
                    "data": {
                        "range": "$range",
                        "totalRevenue": $revenue,
                        "orderCount": $orders,
                        "averageOrderValue": ${revenue / orders},
                        "chartData": [
                            {"date": "2024-01-15", "revenue": 1200.00, "orders": 12},
                            {"date": "2024-01-14", "revenue": 1500.00, "orders": 15},
                            {"date": "2024-01-13", "revenue": 800.00, "orders": 8}
                        ]
                    }
                }
            """.trimIndent())
    }

    fun emptyReports(): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .setHeader(HEADER_CONTENT_TYPE, HEADER_APP_JSON)
            .setBody("""
                {
                    "success": true,
                    "data": {
                        "range": "Today",
                        "totalRevenue": 0,
                        "orderCount": 0,
                        "averageOrderValue": 0,
                        "chartData": []
                    }
                }
            """.trimIndent())
    }

    fun profile(): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .setHeader(HEADER_CONTENT_TYPE, HEADER_APP_JSON)
            .setBody("""
                {
                    "success": true,
                    "data": {
                        "id": "user_001",
                        "name": "Test User",
                        "phone": "9876543210",
                        "email": "test@example.com",
                        "restaurantName": "Test Restaurant",
                        "address": "123 Test Street",
                        "gstin": "XX123456789012X",
                        "role": "owner"
                    }
                }
            """.trimIndent())
    }

    fun whatsAppSuccess(): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .setHeader(HEADER_CONTENT_TYPE, HEADER_APP_JSON)
            .setBody("""
                {
                    "success": true,
                    "message": "Message sent successfully",
                    "data": {
                        "messageId": "wamid.test123",
                        "status": "sent"
                    }
                }
            """.trimIndent())
    }

    fun badRequest(message: String): MockResponse {
        return MockResponse()
            .setResponseCode(400)
            .setHeader(HEADER_CONTENT_TYPE, HEADER_APP_JSON)
            .setBody("""
                {
                    "success": false,
                    "message": "$message",
                    "error": {"code": "VALIDATION_ERROR", "details": "$message"}
                }
            """.trimIndent())
    }

    fun unauthorized(): MockResponse {
        return MockResponse()
            .setResponseCode(401)
            .setHeader(HEADER_CONTENT_TYPE, HEADER_APP_JSON)
            .setBody("""
                {
                    "success": false,
                    "message": "Unauthorized",
                    "error": {"code": "UNAUTHORIZED", "details": "Session expired. Please login again."}
                }
            """.trimIndent())
    }

    fun forbidden(): MockResponse {
        return MockResponse()
            .setResponseCode(403)
            .setHeader(HEADER_CONTENT_TYPE, HEADER_APP_JSON)
            .setBody("""
                {
                    "success": false,
                    "message": "Forbidden",
                    "error": {"code": "FORBIDDEN", "details": "You don't have permission to access this resource"}
                }
            """.trimIndent())
    }

    fun notFound(resource: String): MockResponse {
        return MockResponse()
            .setResponseCode(404)
            .setHeader(HEADER_CONTENT_TYPE, HEADER_APP_JSON)
            .setBody("""
                {
                    "success": false,
                    "message": "$resource not found",
                    "error": {"code": "NOT_FOUND", "details": "The requested $resource does not exist"}
                }
            """.trimIndent())
    }

    fun conflict(message: String): MockResponse {
        return MockResponse()
            .setResponseCode(409)
            .setHeader(HEADER_CONTENT_TYPE, HEADER_APP_JSON)
            .setBody("""
                {
                    "success": false,
                    "message": "$message",
                    "error": {"code": "CONFLICT", "details": "$message"}
                }
            """.trimIndent())
    }

    fun serverError(): MockResponse {
        return MockResponse()
            .setResponseCode(500)
            .setHeader(HEADER_CONTENT_TYPE, HEADER_APP_JSON)
            .setBody("""
                {
                    "success": false,
                    "message": "Internal server error",
                    "error": {"code": "SERVER_ERROR", "details": "Something went wrong on our end"}
                }
            """.trimIndent())
    }

    fun serviceUnavailable(): MockResponse {
        return MockResponse()
            .setResponseCode(503)
            .setHeader(HEADER_CONTENT_TYPE, HEADER_APP_JSON)
            .setBody("""
                {
                    "success": false,
                    "message": "Service temporarily unavailable",
                    "error": {"code": "SERVICE_UNAVAILABLE", "details": "Please try again later"}
                }
            """.trimIndent())
    }
}
