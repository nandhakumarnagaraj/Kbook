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

data class UpiQrUploadResponse(
        val upiQrUrl: String,
        val upiQrVersion: Int = 0
)

interface KhanaBookApi {

        // ── Auth ────────────────────────────────────────────────────────────
        @POST("api/v1/auth/login")
        suspend fun login(@Body request: LoginRequest): AuthResponse

        @POST("api/v1/auth/signup")
        suspend fun signup(@Body request: SignupRequest): AuthResponse

        @POST("api/v1/auth/signup/request")
        suspend fun requestSignupOtp(@Body request: SignupOtpRequest)

        @POST("api/v1/auth/google")
        suspend fun loginWithGoogle(@Body request: GoogleLoginRequest): AuthResponse

        @POST("api/v1/auth/reset-password")
        suspend fun resetPassword(@Body request: ResetPasswordRequest)

        @POST("api/v1/auth/reset-password/request")
        suspend fun requestPasswordResetOtp(@Body request: PasswordResetOtpRequest)

        @GET("api/v1/auth/check-user")
        suspend fun checkUser(@Query("phoneNumber") phoneNumber: String): Boolean

        @POST("api/v1/auth/logout")
        suspend fun logout(): retrofit2.Response<Unit>

        // ── Sync push — uses SyncDto, NOT Room entities (fix #1) ────────────
        @POST("api/v1/sync/bills/push")
        suspend fun pushBills(@Body bills: List<BillSyncDto>): PushSyncResponse

        @POST("api/v1/sync/bills/items/push")
        suspend fun pushBillItems(@Body items: List<BillItemSyncDto>): PushSyncResponse

        @POST("api/v1/sync/bills/payments/push")
        suspend fun pushBillPayments(@Body payments: List<BillPaymentSyncDto>): PushSyncResponse

        @POST("api/v1/sync/restaurantprofile/push")
        suspend fun pushRestaurantProfiles(@Body profiles: List<RestaurantProfileSyncDto>): PushSyncResponse

        @POST("api/v1/sync/config/users/push")
        suspend fun pushUsers(@Body users: List<UserSyncDto>): PushSyncResponse

        @POST("api/v1/sync/menu/categories/push")
        suspend fun pushCategories(@Body categories: List<CategorySyncDto>): PushSyncResponse

        @POST("api/v1/sync/menuitem/push")
        suspend fun pushMenuItems(@Body items: List<MenuItemSyncDto>): PushSyncResponse

        @POST("api/v1/sync/itemvariant/push")
        suspend fun pushItemVariants(@Body variants: List<ItemVariantSyncDto>): PushSyncResponse

        @POST("api/v1/sync/stocklog/push")
        suspend fun pushStockLogs(@Body logs: List<StockLogSyncDto>): PushSyncResponse

        // ── Master pull (primary sync path) ─────────────────────────────────
        @GET("api/v1/sync/master/pull")
        suspend fun pullMasterSync(
            @Query("lastSyncTimestamp") lastSyncTimestamp: Long,
            @Query("deviceId") deviceId: String
        ): MasterSyncResponse

        // ── Counters ─────────────────────────────────────────────────────────
        @POST("api/v1/sync/restaurantprofile/counters/increment")
        suspend fun incrementCounters(): CounterResponse

        // ── User management ──────────────────────────────────────────────────
        @POST("api/v1/sync/config/users/update-mobile")
        suspend fun updateMobileNumber(@Body request: UpdateMobileRequest): retrofit2.Response<Unit>

        @POST("api/v1/sync/config/users/update-mobile/request")
        suspend fun requestMobileNumberUpdateOtp(@Body request: UpdateMobileOtpRequest): retrofit2.Response<Unit>

        // ── Payment gateway (Easebuzz) ───────────────────────────────────────
        @POST("api/v1/restaurants/payment-config/easebuzz")
        suspend fun saveEasebuzzConfig(@Body request: SaveRestaurantPaymentConfigRequest): RestaurantPaymentConfigResponse

        @GET("api/v1/restaurants/payment-config/easebuzz")
        suspend fun getEasebuzzConfig(): RestaurantPaymentConfigResponse

        @PATCH("api/v1/restaurants/payment-config/easebuzz/toggle")
        suspend fun toggleEasebuzzActive(@Query("enabled") enabled: Boolean): RestaurantPaymentConfigResponse

        @Multipart
        @POST("api/v1/restaurants/logo")
        suspend fun uploadLogo(@Part file: MultipartBody.Part): LogoUploadResponse

        @Multipart
        @POST("api/v1/restaurants/upi-qr")
        suspend fun uploadUpiQr(@Part file: MultipartBody.Part): UpiQrUploadResponse

        @POST("api/v1/payments/easebuzz/create-order")
        suspend fun createEasebuzzOrder(@Body request: CreateEasebuzzOrderRequest): CreateEasebuzzOrderResponse

        @GET("api/v1/payments/easebuzz/status/{billId}")
        suspend fun getEasebuzzPaymentStatus(@Path("billId") billId: Long): EasebuzzPaymentStatusResponse

        @POST("api/v1/payments/easebuzz/refund/{billId}")
        suspend fun initiateEasebuzzRefund(
                @Path("billId") billId: Long,
                @Body request: InitiateRefundRequest
        ): InitiateRefundResponse

        // ── Storefront ───────────────────────────────────────────────────────
        @GET("api/v1/storefront/orders")
        suspend fun getStorefrontOrders(): List<MerchantCustomerOrderSummaryResponse>

        @GET("api/v1/storefront/orders/{orderId}")
        suspend fun getStorefrontOrder(@Path("orderId") orderId: Long): MerchantCustomerOrderDetailResponse

        @PATCH("api/v1/storefront/orders/{orderId}/status")
        suspend fun updateStorefrontOrderStatus(
                @Path("orderId") orderId: Long,
                @Body request: UpdateCustomerOrderStatusRequest
        ): MerchantCustomerOrderDetailResponse
}
