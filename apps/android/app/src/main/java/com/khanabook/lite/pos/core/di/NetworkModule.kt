package com.khanabook.lite.pos.core.di

import com.khanabook.lite.pos.BuildConfig
import com.khanabook.lite.pos.core.network.KhanaBookApi
import com.khanabook.lite.pos.core.network.AuthInterceptor
import com.khanabook.lite.pos.feature.sync.domain.NetworkMonitor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import android.net.ConnectivityManager
import android.content.Context

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BACKEND_BASE_URL = BuildConfig.BACKEND_URL

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            // Debug APKs log the whole exchange, whatever backend they point at: these builds
            // never reach a merchant, and seeing the real bytes is the only way to explain a
            // KYC call that the server answers with an empty portal URL. BuildConfig.DEBUG is
            // false in every release/App Bundle, so a shipped binary logs nothing — the bodies
            // carry auth tokens and the KYC portal access key.
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        authInterceptor: AuthInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .addInterceptor(authInterceptor)
            .build()
    }

    @Provides
    @Singleton
    @Named("AuthOkHttpClient")
    fun provideAuthOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        authInterceptor: AuthInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .addInterceptor(authInterceptor)
            .build()
    }

    @Provides
    @Singleton
    @Named("BackendRetrofit")
    fun provideBackendRetrofit(@Named("AuthOkHttpClient") okHttpClient: OkHttpClient): Retrofit {
        val baseUrlWithPrefix = if (BACKEND_BASE_URL.endsWith("/")) {
            BACKEND_BASE_URL
        } else {
            BACKEND_BASE_URL + "/"
        }
        return Retrofit.Builder()
            .baseUrl(baseUrlWithPrefix)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
    }

    @Provides
    @Singleton
    fun provideKhanaBookApi(@Named("BackendRetrofit") retrofit: Retrofit): KhanaBookApi {
        return retrofit.create(KhanaBookApi::class.java)
    }

    @Provides
    @Singleton
    fun provideNetworkMonitor(@ApplicationContext context: android.content.Context): NetworkMonitor {
        return NetworkMonitor(context)
    }

    @Provides
    @Singleton
    fun provideConnectivityManager(@ApplicationContext context: Context): ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
}
