package com.khanabook.lite.pos.data.remote.api

import com.khanabook.lite.pos.data.remote.ResetPasswordRequest
import com.khanabook.lite.pos.data.remote.PasswordResetOtpRequest
import com.khanabook.lite.pos.data.remote.dto.*
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.Part
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class LogoUploadResponse(
        val logoUrl: String,
        val logoVersion: Int = 0
)

interface KhanaBookApi {

        // ── Auth ────────────────────────────────────────────────────────────
        @POST("api/v2/auth/login")
        suspend fun login(@Body request: LoginRequest): AuthResponse

        @POST("api/v2/auth/signup")
        suspend fun signup(@Body request: SignupRequest): AuthResponse

        @POST("api/v2/auth/signup/request")
        suspend fun requestSignupOtp(@Body request: SignupOtpRequest)

        @POST("api/v2/auth/google")
        suspend fun loginWithGoogle(@Body request: GoogleLoginRequest): AuthResponse

        @POST("api/v2/auth/reset-password")
        suspend fun resetPassword(@Body request: ResetPasswordRequest)

        @POST("api/v2/auth/reset-password/request")
        suspend fun requestPasswordResetOtp(@Body request: PasswordResetOtpRequest)

        @GET("api/v2/auth/check-user")
        suspend fun checkUser(@Query("phoneNumber") phoneNumber: String): Boolean

        @POST("api/v2/auth/logout")
        suspend fun logout(): retrofit2.Response<Unit>

        // ── Sync push — uses SyncDto, NOT Room entities (fix #1) ────────────
        @POST("api/v2/sync/bills/push")
        suspend fun pushBills(@Body bills: List<BillSyncDto>): PushSyncResponse

        @POST("api/v2/sync/bills/items/push")
        suspend fun pushBillItems(@Body items: List<BillItemSyncDto>): PushSyncResponse

        @POST("api/v2/sync/bills/payments/push")
        suspend fun pushBillPayments(@Body payments: List<BillPaymentSyncDto>): PushSyncResponse

        @POST("api/v2/sync/restaurantprofile/push")
        suspend fun pushRestaurantProfiles(@Body profiles: List<RestaurantProfileSyncDto>): PushSyncResponse

        @POST("api/v2/sync/config/users/push")
        suspend fun pushUsers(@Body users: List<UserSyncDto>): PushSyncResponse

        @POST("api/v2/sync/menu/categories/push")
        suspend fun pushCategories(@Body categories: List<CategorySyncDto>): PushSyncResponse

        @POST("api/v2/sync/menuitem/push")
        suspend fun pushMenuItems(@Body items: List<MenuItemSyncDto>): PushSyncResponse

        @POST("api/v2/sync/itemvariant/push")
        suspend fun pushItemVariants(@Body variants: List<ItemVariantSyncDto>): PushSyncResponse

        @POST("api/v2/sync/stocklog/push")
        suspend fun pushStockLogs(@Body logs: List<StockLogSyncDto>): PushSyncResponse

        // ── Master pull (primary sync path) ─────────────────────────────────
        @GET("api/v2/sync/master/pull")
        suspend fun pullMasterSync(
            @Query("lastSyncTimestamp") lastSyncTimestamp: Long,
            @Query("deviceId") deviceId: String
        ): MasterSyncResponse

        // ── Counters ─────────────────────────────────────────────────────────
        @POST("api/v2/sync/restaurantprofile/counters/increment")
        suspend fun incrementCounters(): CounterResponse

        // ── User management ──────────────────────────────────────────────────
        @POST("api/v2/sync/config/users/update-mobile")
        suspend fun updateMobileNumber(@Body request: UpdateMobileRequest): retrofit2.Response<Unit>

        @POST("api/v2/sync/config/users/update-mobile/request")
        suspend fun requestMobileNumberUpdateOtp(@Body request: UpdateMobileOtpRequest): retrofit2.Response<Unit>

        // ── File uploads ─────────────────────────────────────────────────────
        @Multipart
        @POST("api/v2/restaurants/logo")
        suspend fun uploadLogo(@Part file: MultipartBody.Part): LogoUploadResponse

        // ── Storefront ───────────────────────────────────────────────────────
        @GET("api/v2/storefront/orders")
        suspend fun getStorefrontOrders(): List<MerchantCustomerOrderSummaryResponse>

        @GET("api/v2/storefront/orders/{orderId}")
        suspend fun getStorefrontOrder(@Path("orderId") orderId: Long): MerchantCustomerOrderDetailResponse

        @PATCH("api/v2/storefront/orders/{orderId}/status")
        suspend fun updateStorefrontOrderStatus(
                @Path("orderId") orderId: Long,
                @Body request: UpdateCustomerOrderStatusRequest
        ): MerchantCustomerOrderDetailResponse

        // ── Easebuzz Payments ────────────────────────────────────────────────
        @POST("api/v2/payments/easebuzz/create-order")
        suspend fun createEasebuzzOrder(
            @Query("deviceId") deviceId: String,
            @Body request: CreateEasebuzzOrderRequest
        ): CreateEasebuzzOrderResponse

        @GET("api/v2/payments/easebuzz/status/{billId}")
        suspend fun getEasebuzzPaymentStatus(
            @Query("deviceId") deviceId: String,
            @Path("billId") billId: Long,
            @Query("refresh") refresh: Boolean = false
        ): EasebuzzPaymentStatusResponse

        @POST("api/v2/payments/easebuzz/verify/{billId}")
        suspend fun verifyEasebuzzPayment(
            @Query("deviceId") deviceId: String,
            @Path("billId") billId: Long
        ): EasebuzzVerifyResponse

        @POST("api/v2/payments/easebuzz/refund/{billId}")
        suspend fun refundEasebuzzPayment(
            @Query("deviceId") deviceId: String,
            @Path("billId") billId: Long,
            @Body request: EasebuzzRefundRequest
        ): EasebuzzRefundResponse

        // ── Easebuzz Sub-Merchant KYC ────────────────────────────────────────
        @GET("api/v2/restaurants/payment-config/easebuzz/sub-merchant-status")
        suspend fun getEasebuzzSubMerchantStatus(
            @Query("deviceId") deviceId: String
        ): EasebuzzSubMerchantStatusResponse

        // ── GST / FSSAI Lookup ───────────────────────────────────────────────
        @GET("api/v2/business/lookup/gst")
        suspend fun lookupGst(@Query("gstin") gstin: String): Map<String, Any>

        @GET("api/v2/business/lookup/fssai")
        suspend fun lookupFssai(@Query("fssaiNo") fssaiNo: String): Map<String, Any>

        @GET("api/v2/business/lookup/both")
        suspend fun lookupBoth(
            @Query("gstin") gstin: String?,
            @Query("fssaiNo") fssaiNo: String?
        ): Map<String, Any>

        // ── Marketplace Orders ──────────────────────────────────────────────
        @GET("api/v2/business/marketplace-orders")
        suspend fun getMarketplaceOrders(@Query("deviceId") deviceId: String): List<MarketplaceOrderDto>

        @GET("api/v2/business/marketplace-orders/pending")
        suspend fun getPendingMarketplaceOrders(@Query("deviceId") deviceId: String): List<MarketplaceOrderDto>

        @GET("api/v2/business/marketplace-orders/counts")
        suspend fun getMarketplaceOrderCounts(@Query("deviceId") deviceId: String): Map<String, Long>

        @POST("api/v2/business/marketplace-orders/{orderId}/accept")
        suspend fun acceptMarketplaceOrder(@Path("orderId") orderId: Long, @Query("deviceId") deviceId: String): MarketplaceOrderDto

        @POST("api/v2/business/marketplace-orders/{orderId}/reject")
        suspend fun rejectMarketplaceOrder(@Path("orderId") orderId: Long, @Query("deviceId") deviceId: String, @Body body: Map<String, String>): MarketplaceOrderDto

        @POST("api/v2/business/marketplace-orders/{orderId}/mark-ready")
        suspend fun markMarketplaceOrderReady(@Path("orderId") orderId: Long, @Query("deviceId") deviceId: String): MarketplaceOrderDto
}
