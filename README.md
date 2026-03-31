# 🍱 KhanaBook: Built for the Heart of the Shop

Hey there! Welcome to the engine room of **KhanaBook**! 👋

Running a food stall, cafe, or a bustling restaurant is hard enough. KhanaBook is here to make the "business side" of things feel effortless. It is a companion for the people who keep our neighborhoods fed, whether it's the local biryani corner or a breakfast joint.

This repository contains both the **KhanaBook Lite (Android App)** and the **KhanaBook SaaS (Backend Server)**.

---

## 🔌 Why "Offline-First"? Because Life Happens.

Most billing apps break the moment the Wi-Fi drops. We built this differently. We know how it goes—the kitchen is heating up, orders are flying in, and suddenly... the internet goes out. In a busy shop, that shouldn't be a crisis. 

We built KhanaBook to be **offline-first**. It works whether you're in a basement cafe or a street-side stall.
- **⚡ Works Everywhere:** Create bills instantly without worrying about a signal.
- **🔄 Smart Syncing:** The moment you're back online, the app quietly syncs everything (bills, menu changes, users) to the server via our robust **Bidirectional Master Sync**. Your data is safely recorded without you ever having to hit "refresh."

---

## ✨ Features That Actually Matter

We focused on the things that actually matter when you're in the thick of it:

- **🥡 Your Menu, Your Way:** Organize your dishes into simple categories. We handle variants and pricing in a few taps.
- **🧾 Simple, Honest Billing:** GST, variants, and payments are handled in a flow that feels natural. Send professional-looking invoices directly to your customer's WhatsApp, or print them on the spot with a **Bluetooth thermal printer** ESC/POS integration.
- **📸 Smart Menu Import (OCR):** Too busy to type the menu? Just take a picture of your physical menu—KhanaBook uses Google ML Kit OCR to parse and import it instantly.
- **🔒 Your Data is Yours:** We use **Multi-Tenancy** on the server to strictly isolate restaurant data, and local **SQLCipher** (AES-256 encryption) on the app.

---

## 📱 Android App (KhanaBook Lite)

A mobile Point of Sale (POS) app that doesn't need the internet to work and keeps your data exactly where it belongs: with you.

### 🛠️ Tech Stack & Architecture
- **Language/UI:** Kotlin & Jetpack Compose (MVVM Architecture)
- **Dependency Injection:** Dagger Hilt
- **Local Database:** Room with SQLCipher for AES-256 encrypted local storage.
- **Networking/Sync:** Retrofit & OkHttp. Background sync handled aggressively and reliably via Android's **WorkManager** (`MasterSyncWorker`). 
- **AI/ML:** Google ML Kit (Text Recognition) & CameraX for OCR.
- **Hardware:** Bluetooth/Thermal Printer ESC/POS integration.
- **Authentication:** Google Sign-In, Firebase AppCheck, JWT Tokens, and offline fallback authentication.

### 🚀 Getting Started
1. **Requirements:** **Android Studio (Ladybug or newer)** and **JDK 17**.
2. **Setup Secrets:** Create `Android/local.properties` and keep credentials only there. You can copy the template from `Android/secrets.properties.example`. Required entries:
   ```properties
   BACKEND_URL=https://your-api-domain.com/
   GOOGLE_WEB_CLIENT_ID=your_google_web_client_id.apps.googleusercontent.com
   SIGNING_STORE_FILE=app/release-key.jks
   SIGNING_STORE_PASSWORD=your_keystore_password
   SIGNING_KEY_ALIAS=your_key_alias
   SIGNING_KEY_PASSWORD=your_key_password
   ```
   Do not commit `local.properties`, keystores, or signing files.
3. **Build & Run:** Open the `Android` project in Android Studio, sync Gradle, and run on a real device.

---

## 🖥️ Backend Server (KhanaBook SaaS)

The engine that receives synced offline data, handles conflict resolution, and ensures strict multi-tenant cloud storage.

### 🛠️ Tech Stack
- **Framework:** Java 17 & Spring Boot 3.5.x
- **Database:** PostgreSQL with Spring Data JPA & Hibernate
- **Migrations:** Flyway
- **Security:** Spring Security & stateless JWT Authentication
- **Rate Limiting:** Bucket4j (Protects Auth & Sync endpoints from brute force)
- **API Documentation:** SpringDoc OpenAPI (Swagger UI)

---

## 🍱 Recent Improvements & Stability Updates

### 🛡️ Security & Architecture (Latest)
- **Centralized OTP Handling:** Moved all WhatsApp OTP generation and delivery logic to the server. Android clients no longer require Meta/WhatsApp API tokens, reducing the APK's attack surface and secret exposure.
- **Persistent OTP Storage:** Implemented a dedicated `otp_requests` table on the server with automated hourly cleanup. This ensures OTPs survive server restarts and support multi-instance deployments.
- **Android Secret Hardening:** Removed hardcoded Backend URLs and Google Client IDs from the app resources. These are now injected via `local.properties` and `BuildConfig`/`resValue` during the build process.

### 🧾 Billing & Reports
- **Clean Order Numbering:** Optimized Daily Order IDs to show only the 3-digit counter (e.g., `001`, `002`) in tables and reports for better readability.
- **Integrated Sharing:** Added a dedicated WhatsApp share button to every row in the **Order Details** screen, allowing instant invoice delivery for any past order.

### 🔌 Hardware & Connectivity
- **Robust Printer Discovery:** Enhanced Bluetooth scanning to reliably detect new/unpaired thermal printers. Added automatic Location/GPS status checks required for Android hardware discovery.
- **Unified Printer State:** Implemented a Singleton manager for Bluetooth connectivity, ensuring your printer stays connected across all screens (Settings, Billing, etc.).

### 📲 WhatsApp Smart Sharing
- **Smart Two-Step Flow:** Overhauled the sharing logic to support **unsaved numbers**. The app now automatically opens the correct customer chat and then prompts to attach the PDF invoice.
- **Android 14 Compatibility:** Fixed PDF attachment issues by implementing explicit URI permission granting (`ClipData`) required by newer Android versions.

---

## 📜 License & Privacy

**KhanaBook Server:** MIT License. See the `server/LICENSE` file for more details.  
**KhanaBook Lite (Android):** Internal/private project. All rights reserved.

Happy Billing! ☕🥡

# Final Verification: Tue Mar 31 12:43:10 UTC 2026
