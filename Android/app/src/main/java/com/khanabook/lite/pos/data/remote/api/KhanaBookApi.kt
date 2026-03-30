package com.khanabook.lite.pos.data.remote.api

import com.khanabook.lite.pos.data.local.entity.*
import com.khanabook.lite.pos.data.remote.ResetPasswordRequest
import com.khanabook.lite.pos.data.remote.PasswordResetOtpRequest
import com.khanabook.lite.pos.data.remote.dto.*
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface KhanaBookApi {

        @POST("api/v1/auth/login")
        suspend fun login(@Body request: LoginRequest): AuthResponse

        @POST("api/v1/auth/signup")
        suspend fun signup(@Body request: SignupRequest): AuthResponse

        @POST("api/v1/auth/signup/request")
        suspend fun requestSignupOtp(@Body request: SignupOtpRequest)

        @POST("api/v1/auth/google")
        suspend fun loginWithGoogle(@Body request: GoogleLoginRequest): AuthResponse

        @POST("api/v1/sync/bills/push")
        suspend fun pushBills(@Body bills: List<BillEntity>): PushSyncResponse

        @GET("api/v1/sync/bills/pull")
        suspend fun pullBills(
                @Query("lastSyncTimestamp") lastSyncTimestamp: Long,
                @Query("deviceId") deviceId: String
        ): List<BillEntity>

        @POST("api/v1/sync/bills/items/push")
        suspend fun pushBillItems(@Body items: List<BillItemEntity>): PushSyncResponse

        @GET("api/v1/sync/bills/items/pull")
        suspend fun pullBillItems(
            @Query("lastSyncTimestamp") lastSyncTimestamp: Long,
            @Query("deviceId") deviceId: String
        ): List<BillItemEntity>

        @POST("api/v1/sync/bills/payments/push")
        suspend fun pushBillPayments(@Body payments: List<BillPaymentEntity>): PushSyncResponse

        @GET("api/v1/sync/bills/payments/pull")
        suspend fun pullBillPayments(
            @Query("lastSyncTimestamp") lastSyncTimestamp: Long,
            @Query("deviceId") deviceId: String
        ): List<BillPaymentEntity>

        
        @POST("api/v1/sync/restaurantprofile/push")
        suspend fun pushRestaurantProfiles(@Body profiles: List<RestaurantProfileEntity>): PushSyncResponse

        @GET("api/v1/sync/restaurantprofile/pull")
        suspend fun pullRestaurantProfiles(
                @Query("lastSyncTimestamp") lastSyncTimestamp: Long,
                @Query("deviceId") deviceId: String
        ): List<RestaurantProfileEntity>

        @POST("api/v1/sync/config/users/push")
        suspend fun pushUsers(@Body users: List<UserEntity>): PushSyncResponse

        @GET("api/v1/sync/config/users/pull")
        suspend fun pullUsers(
            @Query("lastSyncTimestamp") lastSyncTimestamp: Long,
            @Query("deviceId") deviceId: String
        ): List<UserEntity>

        
        @POST("api/v1/sync/menu/categories/push")
        suspend fun pushCategories(@Body categories: List<CategoryEntity>): PushSyncResponse

        @GET("api/v1/sync/menu/categories/pull")
        suspend fun pullCategories(
                @Query("lastSyncTimestamp") lastSyncTimestamp: Long,
                @Query("deviceId") deviceId: String
        ): List<CategoryEntity>

        @POST("api/v1/sync/menuitem/push")
        suspend fun pushMenuItems(@Body items: List<MenuItemEntity>): PushSyncResponse

        @GET("api/v1/sync/menuitem/pull")
        suspend fun pullMenuItems(
            @Query("lastSyncTimestamp") lastSyncTimestamp: Long,
            @Query("deviceId") deviceId: String
        ): List<MenuItemEntity>

        @POST("api/v1/sync/itemvariant/push")
        suspend fun pushItemVariants(@Body variants: List<ItemVariantEntity>): PushSyncResponse

        @GET("api/v1/sync/itemvariant/pull")
        suspend fun pullItemVariants(
            @Query("lastSyncTimestamp") lastSyncTimestamp: Long,
            @Query("deviceId") deviceId: String
        ): List<ItemVariantEntity>

        @POST("api/v1/sync/stocklog/push")
        suspend fun pushStockLogs(@Body logs: List<StockLogEntity>): PushSyncResponse

        @GET("api/v1/sync/stocklog/pull")
        suspend fun pullStockLogs(
            @Query("lastSyncTimestamp") lastSyncTimestamp: Long,
            @Query("deviceId") deviceId: String
        ): List<StockLogEntity>

        @GET("api/v1/sync/master/pull")
        suspend fun pullMasterSync(
            @Query("lastSyncTimestamp") lastSyncTimestamp: Long,
            @Query("deviceId") deviceId: String
        ): MasterSyncResponse

        @POST("api/v1/sync/restaurantprofile/counters/increment")
        suspend fun incrementCounters(): CounterResponse

        @POST("api/v1/auth/reset-password")
        suspend fun resetPassword(@Body request: ResetPasswordRequest)

        @POST("api/v1/auth/reset-password/request")
        suspend fun requestPasswordResetOtp(@Body request: PasswordResetOtpRequest)

        @GET("api/v1/auth/check-user")
        suspend fun checkUser(@Query("phoneNumber") phoneNumber: String): Boolean

        @POST("api/v1/sync/config/users/update-mobile")
        suspend fun updateMobileNumber(@Body request: UpdateMobileRequest): retrofit2.Response<Unit>

        @POST("api/v1/sync/config/users/update-mobile/request")
        suspend fun requestMobileNumberUpdateOtp(@Body request: UpdateMobileOtpRequest): retrofit2.Response<Unit>
}
