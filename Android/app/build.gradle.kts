
import java.net.URI
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

fun localProperty(name: String, defaultValue: String = ""): String =
    localProperties.getProperty(name)?.takeUnless { it.isBlank() } ?: defaultValue

fun configValue(name: String, defaultValue: String = ""): String =
    localProperty(name).takeUnless { it.isBlank() }
        ?: providers.gradleProperty(name).orNull?.takeUnless { it.isBlank() }
        ?: System.getenv(name)?.takeUnless { it.isBlank() }
        ?: defaultValue

fun isPlaceholder(value: String): Boolean {
    val normalized = value.trim()
    if (normalized.isBlank()) return true
    val placeholderMarkers = listOf(
        "YOUR_",
        "your_",
        "example.com",
        "apps.googleusercontent.com",
        "release-key.jks"
    )
    return placeholderMarkers.any { marker ->
        normalized == marker || normalized.startsWith(marker) || normalized.contains("<") || normalized.contains(">")
    }
}

// WhatsApp/Meta tokens removed — OTP delivery is now handled entirely server-side.
// The server's PasswordResetOtpService holds and uses these credentials.
val backendUrl = configValue("BACKEND_URL", "https://kbook.iadv.cloud/")
val googleWebClientId = configValue("GOOGLE_WEB_CLIENT_ID")
val signingStoreFile = configValue("SIGNING_STORE_FILE")
val signingStorePassword = configValue("SIGNING_STORE_PASSWORD")
val signingKeyAlias = configValue("SIGNING_KEY_ALIAS")
val signingKeyPassword = configValue("SIGNING_KEY_PASSWORD")
val releaseVersionCode = configValue("RELEASE_VERSION_CODE", "1").toIntOrNull() ?: 1
val releaseVersionName = configValue("RELEASE_VERSION_NAME", "1.0.0")
val hasReleaseSigning =
    signingStoreFile.isNotBlank() &&
        signingStorePassword.isNotBlank() &&
        signingKeyAlias.isNotBlank() &&
        signingKeyPassword.isNotBlank()
val hasExplicitReleaseVersion =
    configValue("RELEASE_VERSION_CODE").isNotBlank() &&
        configValue("RELEASE_VERSION_NAME").isNotBlank()
val expectedProductionHost = "kbook.iadv.cloud"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

android {
    namespace = "com.khanabook.lite.pos"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.khanabook.lite.pos"
        minSdk = 26
        targetSdk = 35
        versionCode = releaseVersionCode
        versionName = releaseVersionName

        testInstrumentationRunner = "com.khanabook.lite.pos.test.util.HiltTestRunner"

        buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(signingStoreFile)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    room {
        schemaDirectory("$projectDir/schemas")
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

gradle.taskGraph.whenReady {
    allTasks
        .filter { task ->
            task.project == project &&
                (task.name.startsWith("assemble") || task.name.startsWith("bundle")) &&
                task.name.contains("Release", ignoreCase = true)
        }
        .forEach { task ->
            task.doFirst {
                if (!hasReleaseSigning) {
                    throw GradleException(
                        "Release signing not configured. " +
                            "Add SIGNING_STORE_FILE, SIGNING_STORE_PASSWORD, " +
                            "SIGNING_KEY_ALIAS and SIGNING_KEY_PASSWORD to local.properties."
                    )
                }

                if (!hasExplicitReleaseVersion) {
                    throw GradleException(
                        "Release version not configured. " +
                            "Set RELEASE_VERSION_CODE and RELEASE_VERSION_NAME via local.properties, " +
                            "Gradle properties, or environment variables."
                    )
                }

                if (isPlaceholder(googleWebClientId)) {
                    throw GradleException(
                        "GOOGLE_WEB_CLIENT_ID is missing or still set to a placeholder. " +
                            "Configure the production web client ID before building a release."
                    )
                }

                val parsedBackendUrl = runCatching { URI(backendUrl) }.getOrNull()
                    ?: throw GradleException("BACKEND_URL is invalid: $backendUrl")
                val parsedHost = parsedBackendUrl.host
                    ?: throw GradleException("BACKEND_URL must include a valid host. Found: $backendUrl")

                if (!parsedBackendUrl.scheme.equals("https", ignoreCase = true)) {
                    throw GradleException(
                        "Release builds must use an HTTPS BACKEND_URL. Found: $backendUrl"
                    )
                }

                if (!parsedHost.equals(expectedProductionHost, ignoreCase = true)) {
                    throw GradleException(
                        "Release builds must target $expectedProductionHost to stay aligned with " +
                            "network pinning and production backend config. Found host: $parsedHost"
                    )
                }
            }
        }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Security
    implementation("org.mindrot:jbcrypt:0.4")
    implementation(libs.sqlcipher)
    implementation(libs.androidx.sqlite.ktx)
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("androidx.biometric:biometric:1.1.0")

    // Social Login
    implementation(libs.play.services.auth)


    // Google Sign-In via Credential Manager (modern API)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation(libs.coil.compose)
    implementation("androidx.browser:browser:1.8.0")

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.zxing.android)
    implementation(libs.mlkit.text.recognition)
    
    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.extensions)
    implementation(libs.pdfbox.android)
    implementation(libs.accompanist.pager)
    implementation(libs.accompanist.pager.indicators)
    implementation(libs.lottie.compose)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.mockk.agent)
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.50")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.2.0")
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
