package com.khanabook.lite.pos.data.remote.api

import com.khanabook.lite.pos.data.remote.ResetPasswordRequest
import com.khanabook.lite.pos.data.remote.PasswordResetOtpRequest
import com.khanabook.lite.pos.data.remote.dto.*
import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.Part
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class LogoUploadResponse(
        val logoUrl: String,
        val logoVersion: Int = 0
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
            @Query("deviceId") deviceId: String,
            @Query("terminalId") terminalId: String? = null,
            @Query("ignoreDeviceId") ignoreDeviceId: Boolean = false,
            @Query("page") page: Int = 0,
            @Query("size") size: Int = 500
        ): MasterSyncResponse

        // ── Counters ─────────────────────────────────────────────────────────
        @POST("api/v1/sync/restaurantprofile/counters/increment")
        suspend fun incrementCounters(): CounterResponse

        // ── User management ──────────────────────────────────────────────────
        @POST("api/v1/sync/config/users/update-mobile")
        suspend fun updateMobileNumber(@Body request: UpdateMobileRequest): retrofit2.Response<Unit>

        @POST("api/v1/sync/config/users/update-mobile/request")
        suspend fun requestMobileNumberUpdateOtp(@Body request: UpdateMobileOtpRequest): retrofit2.Response<Unit>

        // ── File uploads ─────────────────────────────────────────────────────
        @Multipart
        @POST("api/v1/restaurants/logo")
        suspend fun uploadLogo(@Part file: MultipartBody.Part): LogoUploadResponse

        @POST("api/v1/sync/terminal/activate")
        suspend fun activateTerminal(@Body request: TerminalActivationRequest): retrofit2.Response<okhttp3.ResponseBody>

        @GET("api/v1/sync/terminal/request-status/{requestId}")
        suspend fun getTerminalRequestStatus(@retrofit2.http.Path("requestId") requestId: Long): retrofit2.Response<okhttp3.ResponseBody>

        @POST("api/v1/sync/terminal/complete-activation")
        suspend fun completeActivation(@Body request: CompleteActivationRequest): retrofit2.Response<okhttp3.ResponseBody>

        @GET("api/v1/sync/terminal/list")
        suspend fun listTerminals(): List<TerminalListItem>

        @POST("api/v1/sync/terminal/reclaim")
        suspend fun reclaimTerminal(@Body request: TerminalReclaimRequest): TerminalActivationResponse

        // ── Push Notifications ────────────────────────────────────────────
        @POST("api/v1/notifications/device-token")
        suspend fun registerDeviceToken(@Body data: Map<String, String>): retrofit2.Response<Map<String, Any>>

        @DELETE("api/v1/notifications/device-token")
        suspend fun unregisterDeviceToken(@Query("deviceId") deviceId: String): retrofit2.Response<Map<String, Any>>

        @GET("api/v1/notifications")
        suspend fun getNotifications(@Query("limit") limit: Int = 50): retrofit2.Response<Map<String, Any>>

        @GET("api/v1/notifications/unread-count")
        suspend fun getUnreadNotificationCount(): retrofit2.Response<Map<String, Any>>

        @POST("api/v1/notifications/{id}/read")
        suspend fun markNotificationRead(@Path("id") id: Long): retrofit2.Response<Map<String, Any>>

        @POST("api/v1/notifications/mark-all-read")
        suspend fun markAllNotificationsRead(): retrofit2.Response<Map<String, Any>>

        // ── FSSAI ──────────────────────────────────────────────────────────
        @GET("api/v1/business/lookup/fssai")
        suspend fun lookupFssai(@Query("fssaiNo") fssaiNo: String): Map<String, Any>

        // ── Permissions ──────────────────────────────────────────────────────────

        @GET("api/v1/permissions/me")
        suspend fun getMyPermissions(): PermissionSyncResponse

        @POST("api/v1/permissions/request")
        suspend fun requestPermission(@Body body: PermissionRequestBody): Map<String, Any>

        @GET("api/v1/permissions/requests/pending")
        suspend fun getPendingPermissionRequests(): List<PermissionRequestDto>

        @POST("api/v1/permissions/requests/{requestId}/resolve")
        suspend fun resolvePermissionRequest(
            @Path("requestId") requestId: Long,
            @Body body: PermissionResolveBody
        )

        @POST("api/v1/permissions/grant")
        suspend fun grantPermission(@Body body: PermissionGrantBody)

        @POST("api/v1/permissions/revoke")
        suspend fun revokePermission(@Body body: PermissionRevokeBody)

        @GET("api/v1/permissions/templates")
        suspend fun getRoleTemplates(): List<RoleTemplateDto>

        @POST("api/v1/permissions/templates")
        suspend fun createRoleTemplate(@Body body: CreateTemplateBody): RoleTemplateDto

        @POST("api/v1/permissions/apply-template")
        suspend fun applyRoleTemplate(@Body body: ApplyTemplateBody)

        // ── Inventory (raw materials + recipes) ──────────────────────────────

        @GET("api/v1/inventory/materials")
        suspend fun getRawMaterials(): List<RawMaterialDto>

        @POST("api/v1/inventory/materials")
        suspend fun createRawMaterial(@Body body: CreateMaterialBody): RawMaterialDto

        @PUT("api/v1/inventory/materials/{id}")
        suspend fun updateRawMaterial(@Path("id") id: Long, @Body body: UpdateMaterialBody): RawMaterialDto

        @DELETE("api/v1/inventory/materials/{id}")
        suspend fun deleteRawMaterial(@Path("id") id: Long)

        @GET("api/v1/inventory/recipes/{menuItemId}")
        suspend fun getItemRecipes(@Path("menuItemId") menuItemId: Long): List<ItemRecipeDto>

        @POST("api/v1/inventory/recipes")
        suspend fun createRecipeLine(@Body body: CreateRecipeBody): ItemRecipeDto

        @DELETE("api/v1/inventory/recipes/{id}")
        suspend fun deleteRecipeLine(@Path("id") id: Long)

        // ── Analytics ────────────────────────────────────────────────────────

        @GET("api/v1/analytics/item-sales")
        suspend fun getItemSales(
            @Query("from") from: String,
            @Query("to") to: String
        ): List<ItemSalesRow>

        @GET("api/v1/analytics/hourly-sales")
        suspend fun getHourlySales(@Query("date") date: String): List<HourlySalesRow>

        @GET("api/v1/analytics/food-cost")
        suspend fun getFoodCost(
            @Query("from") from: String,
            @Query("to") to: String
        ): List<FoodCostRow>

        @GET("api/v1/business/staff")
        suspend fun getStaffList(): List<StaffListItem>

        @GET("api/v1/permissions/users/{userId}")
        suspend fun getUserPermissions(@Path("userId") userId: Long): UserPermissionsResponse

        // ── Easebuzz Payments ────────────────────────────────────────────────
        @POST("api/v1/payments/easebuzz/create-order")
        suspend fun createEasebuzzOrder(
            @Body request: CreateEasebuzzOrderRequest
        ): CreateEasebuzzOrderResponse

        @GET("api/v1/payments/easebuzz/status/{billId}")
        suspend fun getEasebuzzPaymentStatus(
            @Path("billId") billId: Long,
            @Query("refresh") refresh: Boolean = false
        ): Map<String, Any?>

        @POST("api/v1/payments/easebuzz/verify/{billId}")
        suspend fun verifyEasebuzzPayment(
            @Path("billId") billId: Long
        ): Map<String, Any?>

        @POST("api/v1/payments/easebuzz/refund/{billId}")
        suspend fun refundEasebuzzPayment(
            @Path("billId") billId: Long,
            @Body request: EasebuzzRefundRequest
        ): Map<String, Any?>

        @GET("api/v1/payments/easebuzz/refund-status/{billId}")
        suspend fun getEasebuzzRefundStatus(
            @Path("billId") billId: Long
        ): Map<String, Any?>

        @POST("api/v1/payments/easebuzz/cancel/{billId}")
        suspend fun cancelEasebuzzPayment(
            @Path("billId") billId: Long
        ): Map<String, Any?>

        @POST("api/v1/payments/easebuzz/create-link")
        suspend fun createEasebuzzPaymentLink(
            @Body request: CreateEasebuzzPaymentLinkRequest
        ): Map<String, Any?>

        @POST("api/v1/payments/easebuzz/create-link-for-bill")
        suspend fun createPaymentLinkForBill(
            @Body request: com.khanabook.lite.pos.data.remote.dto.CreatePaymentLinkForBillRequest
        ): Map<String, Any?>

        // ── Easebuzz Onboarding (owner-driven) ──────────────────────────────
        @GET("api/v1/restaurants/payment-config/easebuzz")
        suspend fun getEasebuzzConfig(): Map<String, Any?>

        @GET("api/v1/restaurants/payment-config/easebuzz/sub-merchant-status")
        suspend fun getEasebuzzOnboardingStatus(): com.khanabook.lite.pos.data.remote.dto.EasebuzzOnboardingStatusResponse

        @POST("api/v1/restaurants/payment-config/easebuzz/onboard")
        suspend fun onboardEasebuzz(
            @Body request: com.khanabook.lite.pos.data.remote.dto.EasebuzzOnboardingRequest
        ): com.khanabook.lite.pos.data.remote.dto.EasebuzzOnboardingResponse

        @POST("api/v1/restaurants/payment-config/easebuzz/resubmit")
        suspend fun resubmitEasebuzz(
            @Body request: com.khanabook.lite.pos.data.remote.dto.EasebuzzOnboardingRequest
        ): com.khanabook.lite.pos.data.remote.dto.EasebuzzOnboardingResponse

        @POST("api/v1/restaurants/payment-config/easebuzz/kyc-access-key")
        suspend fun generateKycAccessKey(): com.khanabook.lite.pos.data.remote.dto.EasebuzzKycAccessKeyResponse

        @POST("api/v1/restaurants/payment-config/easebuzz/verify-otp")
        suspend fun verifyEasebuzzOtp(
            @Body request: com.khanabook.lite.pos.data.remote.dto.EasebuzzOtpRequest
        ): Map<String, Any?>

        @POST("api/v1/restaurants/payment-config/easebuzz/resend-otp")
        suspend fun resendEasebuzzOtp(): Map<String, Any?>

        // ── Easebuzz KYC documents (incl. 2 address/business proofs) ───────

        @Multipart
        @POST("api/v1/restaurants/kyc-document")
        suspend fun uploadKycDocument(
            @Part file: MultipartBody.Part,
            @Part type: MultipartBody.Part
        ): Map<String, String>

        @GET("api/v1/business/kyc-document/{docType}/download")
        suspend fun downloadKycDocument(@Path("docType") docType: String): okhttp3.ResponseBody

        // ── Merchant Agreement (e-agreement, signed PDF) ──────────────────

        @GET("api/v1/business/merchant-agreement")
        suspend fun getMerchantAgreementStatus(): Map<String, Any?>

        @Multipart
        @POST("api/v1/business/merchant-agreement")
        suspend fun uploadMerchantAgreement(
            @Part file: MultipartBody.Part,
            @Part signerName: MultipartBody.Part,
            @Part agreementVersion: MultipartBody.Part
        ): Map<String, Any?>

        @GET("api/v1/business/merchant-agreement/download")
        suspend fun downloadMerchantAgreement(): okhttp3.ResponseBody

}

data class TerminalListItem(
    @SerializedName("terminalId") val terminalId: String,
    @SerializedName("terminalName") val terminalName: String?,
    @SerializedName("terminalSeries") val terminalSeries: String,
    @SerializedName("isActive") val isActive: Boolean?,
    @SerializedName("lastActiveAt") val lastActiveAt: Long?
)

data class TerminalReclaimRequest(
    @SerializedName("terminalSeries") val terminalSeries: String,
    @SerializedName("deviceId") val deviceId: String
)

data class TerminalActivationRequest(
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("deviceModel") val deviceModel: String? = null
)

data class TerminalActivationResponse(
    @SerializedName("terminalId") val terminalId: String? = null,
    @SerializedName("terminalName") val terminalName: String? = null,
    @SerializedName("terminalSeries") val terminalSeries: String,
    @SerializedName("isActive") val isActive: Boolean? = null,
    @SerializedName("registeredAt") val registeredAt: Long? = null,
    @SerializedName("lastVerifiedAt") val lastVerifiedAt: Long? = null,
    @SerializedName("terminalToken") val terminalToken: String? = null
)

/**
 * Response when the server returns 202 Accepted (device pending admin approval).
 */
data class TerminalPendingResponse(
    @SerializedName("status") val status: String,
    @SerializedName("requestId") val requestId: Long?,
    @SerializedName("message") val message: String?,
    @SerializedName("challengeCode") val challengeCode: String? = null,
    @SerializedName("challengeExpiresAt") val challengeExpiresAt: Long? = null
)

data class CompleteActivationRequest(
    @SerializedName("requestId") val requestId: Long,
    @SerializedName("deviceId") val deviceId: String
)
