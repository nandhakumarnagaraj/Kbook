# Security — OWASP Mobile Top 10, Network & Data Protection

## When to Trigger

- Implementing authentication/authorization (login, tokens, sessions)
- Storing sensitive data (credentials, API keys, user PII)
- Configuring network security (HTTPS, certificate pinning)
- Handling payment or billing data
- Implementing biometric authentication
- Reviewing code for security vulnerabilities
- Building KhanaBook's auth flow with Spring Boot backend

## Stack Context

| Layer | Technology |
|-------|-----------|
| Network | Retrofit + OkHttp with TLS 1.2+ |
| Auth | JWT tokens (access + refresh) |
| Secure Storage | EncryptedSharedPreferences / DataStore |
| Biometrics | BiometricPrompt API |
| DI | Hilt |
| Backend | Spring Boot (JWT issuer) |

---

## Step-by-Step Workflow

### 1. Network Security Configuration

```xml
<!-- res/xml/network_security_config.xml -->
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <!-- Production: only allow HTTPS with pinned certificates -->
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">api.khanabook.com</domain>
        <pin-set expiration="2027-01-01">
            <!-- Primary pin (SHA-256 of SubjectPublicKeyInfo) -->
            <pin digest="SHA-256">base64EncodedPrimaryPin=</pin>
            <!-- Backup pin (different CA) -->
            <pin digest="SHA-256">base64EncodedBackupPin=</pin>
        </pin-set>
    </domain-config>

    <!-- Debug: allow localhost for development -->
    <debug-overrides>
        <trust-anchors>
            <certificates src="user" />
            <certificates src="system" />
        </trust-anchors>
    </debug-overrides>
</network-security-config>
```

```xml
<!-- AndroidManifest.xml -->
<application
    android:networkSecurityConfig="@xml/network_security_config"
    android:usesCleartextTraffic="false"
    ... >
```

### 2. OkHttp Certificate Pinning (Programmatic Backup)

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .authenticator(tokenAuthenticator)
            .certificatePinner(
                CertificatePinner.Builder()
                    .add("api.khanabook.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
                    .add("api.khanabook.com", "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=")
                    .build()
            )
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
```

### 3. Secure Token Storage

```kotlin
@Singleton
class SecureTokenStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "khanabook_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveAccessToken(token: String) {
        securePrefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
    }

    fun getAccessToken(): String? = securePrefs.getString(KEY_ACCESS_TOKEN, null)

    fun saveRefreshToken(token: String) {
        securePrefs.edit().putString(KEY_REFRESH_TOKEN, token).apply()
    }

    fun getRefreshToken(): String? = securePrefs.getString(KEY_REFRESH_TOKEN, null)

    fun clearTokens() {
        securePrefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
    }
}
```

### 4. Auth Interceptor & Token Refresh

```kotlin
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStore: SecureTokenStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Skip auth for login/register endpoints
        if (request.url.encodedPath.contains("/auth/")) {
            return chain.proceed(request)
        }

        val token = tokenStore.getAccessToken()
        val authenticatedRequest = request.newBuilder()
            .apply {
                if (token != null) {
                    header("Authorization", "Bearer $token")
                }
            }
            .build()

        return chain.proceed(authenticatedRequest)
    }
}

@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenStore: SecureTokenStore,
    private val authApi: Lazy<AuthApiService> // Lazy to avoid circular dependency
) : Authenticator {

    private val mutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Don't retry if already attempted refresh
        if (response.request.header("X-Retry") != null) return null

        return runBlocking {
            mutex.withLock {
                val refreshToken = tokenStore.getRefreshToken() ?: return@runBlocking null

                try {
                    val newTokens = authApi.get().refreshToken(RefreshRequest(refreshToken))
                    tokenStore.saveAccessToken(newTokens.accessToken)
                    tokenStore.saveRefreshToken(newTokens.refreshToken)

                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${newTokens.accessToken}")
                        .header("X-Retry", "true")
                        .build()
                } catch (e: Exception) {
                    tokenStore.clearTokens()
                    null // Force re-login
                }
            }
        }
    }
}
```

### 5. Input Validation & SQL Injection Prevention

```kotlin
// Room uses parameterized queries by default — SAFE
@Query("SELECT * FROM orders WHERE table_number = :tableNumber")
suspend fun getOrdersByTable(tableNumber: Int): List<OrderEntity>

// NEVER build raw queries with string concatenation
// ❌ WRONG: @RawQuery with user input
// ✅ RIGHT: Always use @Query with parameters

// Input validation for user-facing fields
object InputValidator {
    fun validateTableNumber(input: String): ValidationResult {
        val number = input.toIntOrNull()
            ?: return ValidationResult.Error("Must be a number")
        if (number !in 1..100) {
            return ValidationResult.Error("Table number must be 1-100")
        }
        return ValidationResult.Valid(number)
    }

    fun validatePrice(input: String): ValidationResult {
        val price = input.toBigDecimalOrNull()
            ?: return ValidationResult.Error("Invalid price format")
        if (price <= BigDecimal.ZERO) {
            return ValidationResult.Error("Price must be positive")
        }
        if (price > BigDecimal("99999.99")) {
            return ValidationResult.Error("Price exceeds maximum")
        }
        return ValidationResult.Valid(price)
    }

    fun sanitizeItemName(input: String): String {
        return input.trim()
            .take(100) // Max length
            .replace(Regex("[<>\"'&]"), "") // Remove HTML-unsafe chars
    }
}
```

### 6. Biometric Authentication (for POS Lock)

```kotlin
@Singleton
class BiometricAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun canAuthenticate(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun showBiometricPrompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock KhanaBook")
            .setSubtitle("Authenticate to access POS")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        val biometricPrompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }
            }
        )

        biometricPrompt.authenticate(promptInfo)
    }
}
```

### 7. ProGuard/R8 Rules for Security

```proguard
# Keep token/auth models from obfuscation issues with serialization
-keep class com.khanabook.data.model.auth.** { *; }

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}
-assumenosideeffects class timber.log.Timber {
    public static void d(...);
    public static void v(...);
    public static void i(...);
}
```

---

## OWASP Mobile Top 10 Checklist for KhanaBook

| # | Risk | Mitigation |
|---|------|-----------|
| M1 | Improper Credential Usage | JWT with short-lived access tokens + refresh flow |
| M2 | Inadequate Supply Chain | Pin dependencies, use verified libraries only |
| M3 | Insecure Auth/Authorization | Token-based auth, role checks on backend |
| M4 | Insufficient Input/Output Validation | `InputValidator` on all user inputs |
| M5 | Insecure Communication | TLS 1.2+, cert pinning, no cleartext |
| M6 | Inadequate Privacy Controls | No PII in logs, encrypted local storage |
| M7 | Insufficient Binary Protection | R8/ProGuard enabled, root detection |
| M8 | Security Misconfiguration | `android:debuggable="false"` in release |
| M9 | Insecure Data Storage | EncryptedSharedPreferences for secrets |
| M10 | Insufficient Cryptography | AES-256-GCM via AndroidKeyStore |

---

## Anti-Patterns to Avoid

| Anti-Pattern | Correct Approach |
|-------------|-----------------|
| Storing tokens in plain SharedPreferences | Use `EncryptedSharedPreferences` |
| Logging tokens or PII | Strip sensitive data from all logs |
| Allowing cleartext HTTP | `cleartextTrafficPermitted="false"` |
| Hardcoding API keys in source | Use BuildConfig fields or secure storage |
| No certificate pinning | Pin at least 2 certs (primary + backup) |
| Trusting all certificates in debug | Use `debug-overrides` in network config |
| No token expiry handling | Implement `Authenticator` for auto-refresh |
| Storing passwords locally | Never store passwords; use tokens |

---

## Verification Checklist

- [ ] `network_security_config.xml` configured with cert pinning
- [ ] `cleartextTrafficPermitted="false"` in manifest
- [ ] Tokens stored in `EncryptedSharedPreferences`
- [ ] `AuthInterceptor` adds Bearer token to requests
- [ ] `TokenAuthenticator` handles 401 with refresh flow
- [ ] No secrets in source code or BuildConfig (use CI injection)
- [ ] ProGuard/R8 enabled for release builds
- [ ] Logging stripped in release builds
- [ ] Input validation on all user-facing fields
- [ ] Room queries use parameterized `@Query` (no raw SQL concat)
- [ ] Biometric/device credential lock available for POS
- [ ] `android:debuggable="false"` in release manifest
- [ ] No sensitive data in Logcat (grep for tokens/passwords)
- [ ] Backup rules exclude sensitive files (`android:fullBackupContent`)
