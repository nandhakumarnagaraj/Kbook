
import java.util.Properties

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

fun localProperty(name: String, defaultValue: String = ""): String =
    localProperties.getProperty(name)?.takeUnless { it.isBlank() } ?: defaultValue

// WhatsApp/Meta tokens removed — OTP delivery is now handled entirely server-side.
// The server's PasswordResetOtpService holds and uses these credentials.
val backendUrl = localProperty("BACKEND_URL", "https://kbook.iadv.cloud/")
val googleWebClientId = localProperty("GOOGLE_WEB_CLIENT_ID")
val signingStoreFile = localProperty("SIGNING_STORE_FILE")
val signingStorePassword = localProperty("SIGNING_STORE_PASSWORD")
val signingKeyAlias = localProperty("SIGNING_KEY_ALIAS")
val signingKeyPassword = localProperty("SIGNING_KEY_PASSWORD")
val hasReleaseSigning =
    signingStoreFile.isNotBlank() &&
        signingStorePassword.isNotBlank() &&
        signingKeyAlias.isNotBlank() &&
        signingKeyPassword.isNotBlank()

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
        versionCode = 1
        versionName = "1.0"

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
            } else {
                // Fail fast if someone tries to assemble/bundle a release without signing configured.
                // Only applies to release tasks — debug/test builds are unaffected.
                gradle.taskGraph.whenReady {
                    allTasks
                        .filter { task ->
                            task.project == project &&
                            (task.name.startsWith("assemble") || task.name.startsWith("bundle")) &&
                            task.name.contains("Release", ignoreCase = true)
                        }
                        .forEach { task ->
                            task.doFirst {
                                throw GradleException(
                                    "Release signing not configured. " +
                                    "Add SIGNING_STORE_FILE, SIGNING_STORE_PASSWORD, " +
                                    "SIGNING_KEY_ALIAS and SIGNING_KEY_PASSWORD to local.properties."
                                )
                            }
                        }
                }
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
    kotlinOptions {
        jvmTarget = "17"
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

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
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
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.1.0")

    // Social Login
    implementation(libs.play.services.auth)


    // Google Sign-In via Credential Manager (modern API)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation(libs.coil.compose)

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

