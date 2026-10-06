# â”€â”€ KhanaBookLite ProGuard Rules â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

# Keep line numbers in stack traces for debuggability (hidden source file name)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# â”€â”€ Retrofit / Gson (data models must survive obfuscation) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
# Keep all fields in remote DTO / API model classes for Gson deserialization
# NOTE: The vertical-slice restructure (99c320d8) moved all DTOs out of
# data.remote.api / data.remote.dto into feature.*.data and core.network.
# The old package rules below became dead letters, so R8 renamed/stripped Gson
# request fields in minified release builds -> server saw {} bodies and
# returned 400 "idToken is required" / "loginId is required" (AAB-only bug).

# 1) Canonical Gson rules: any @SerializedName-annotated field survives
#    (allowobfuscation is safe: the JSON name comes from the annotation,
#    not the field name).
-keepclasseswithmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * extends com.google.gson.JsonSerializer
-keep class * extends com.google.gson.JsonDeserializer

# 2) Keep fields of every Retrofit @Body / response model class, wherever they
#    live now (feature slices + core.network). Belt-and-braces so models
#    WITHOUT @SerializedName (plain-name fields like ExtractedItemDto) also
#    keep their JSON keys.
-keepclassmembers class com.khanabook.lite.pos.core.network.** { <fields>; }
-keepclassmembers class com.khanabook.lite.pos.feature.*.data.** { <fields>; }

# 3) Reset-password DTOs (live outside the old packages too)
-keepclassmembers class com.khanabook.lite.pos.feature.auth.data.ResetPasswordRequest { *; }
-keepclassmembers class com.khanabook.lite.pos.feature.auth.data.PasswordResetOtpRequest { *; }

# Legacy locations (pre-restructure; kept harmlessly in case old artifacts build)
-keepclassmembers class com.khanabook.lite.pos.data.remote.api.** { *; }
-keepclassmembers class com.khanabook.lite.pos.data.remote.dto.** { *; }

# Retain generic type info used by Retrofit/Gson
-keepattributes Signature
-keepattributes *Annotation*

# Retrofit internals
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# â”€â”€ jBCrypt â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
-keep class org.mindrot.jbcrypt.** { *; }

# â”€â”€ SQLCipher â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
-keep class net.zetetic.** { *; }
-dontwarn net.zetetic.**
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
-keepclassmembers class net.zetetic.database.sqlcipher.** {
    native <methods>;
    <fields>;
    <init>(...);
}
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }

# â”€â”€ Room â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.migration.Migration { *; }
-keepclassmembers class * extends androidx.room.migration.Migration { *; }
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class androidx.sqlite.db.** { *; }
-dontwarn androidx.sqlite.db.**
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# â”€â”€ Hilt / Dagger â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# â”€â”€ ZXing (QR code) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
-keep class com.journeyapps.barcodescanner.** { *; }

# â”€â”€ Google Sign-In / Credential Manager â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class androidx.credentials.** { *; }
-dontwarn androidx.credentials.**

# Google Play Services Auth / Credential Manager (scoped instead of broad wildcards)
-keep class com.google.android.gms.auth.api.identity.** { *; }
-keep class com.google.android.gms.common.api.** { *; }
-dontwarn com.google.android.gms.**
-keep class com.google.api.client.** { *; }
-dontwarn com.google.api.client.**
-keep class com.google.auth.** { *; }
-dontwarn com.google.auth.**

# Easebuzz Payment SDK - keep all classes (both in.easebuzz and legacy com.easebuzz)
-keep class in.easebuzz.** { *; }
-dontwarn in.easebuzz.**
-keep class com.easebuzz.** { *; }
-dontwarn com.easebuzz.**
