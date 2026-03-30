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
2. **Setup Secrets:** Create a `local.properties` file in the root folder (`Android/local.properties`) and add your secure keys:
   ```properties
   # API Keys for Retrofit Base URL
   SERVER_URL="https://your-api-domain.com"
   ```
3. **Build & Run:** Open the `Android` project in Android Studio, sync Gradle, and run on a real device (best for testing Bluetooth printing and OCR scanning).

---

## 🖥️ Backend Server (KhanaBook SaaS)

The engine that receives synced offline data, handles conflict resolution, and ensures strict multi-tenant cloud storage.

### 🛠️ Tech Stack
- **Framework:** Java 17 & Spring Boot 3.2.3
- **Database:** PostgreSQL with Spring Data JPA & Hibernate
- **Security:** Spring Security & stateless JWT Authentication
- **Rate Limiting:** Bucket4j (Protects Auth & Sync endpoints from brute force)
- **API Documentation:** SpringDoc OpenAPI (Swagger UI)

---

## 🍱 Recent Improvements & Stability Updates

### 🧾 Billing & Reports
- **Clean Order Numbering:** Optimized Daily Order IDs to show only the 3-digit counter (e.g., `001`, `002`) in tables and reports for better readability.
- **Integrated Sharing:** Added a dedicated WhatsApp share button to every row in the **Order Details** screen, allowing instant invoice delivery for any past order.

### 🔌 Hardware & Connectivity
- **Robust Printer Discovery:** Enhanced Bluetooth scanning to reliably detect new/unpaired thermal printers. Added automatic Location/GPS status checks required for Android hardware discovery.
- **Unified Printer State:** Implemented a Singleton manager for Bluetooth connectivity, ensuring your printer stays connected across all screens (Settings, Billing, etc.).

### 📲 WhatsApp Smart Sharing
- **Smart Two-Step Flow:** Overhauled the sharing logic to support **unsaved numbers**. The app now automatically opens the correct customer chat and then prompts to attach the PDF invoice, bypassing WhatsApp's security restrictions for new contacts.
- **Android 14 Compatibility:** Fixed PDF attachment issues by implementing explicit URI permission granting (`ClipData`) required by newer Android versions.

### 🛡️ Core Stability
- **Security Hardening:** Standardized encryption and removed sensitive plaintext logging.
- **Data Integrity:** Standardized all primary keys to `Long` and migrated monetary fields to `BigDecimal` for absolute financial precision.
- **Crash Recovery:** Hardened `GlobalCrashHandler` with recursive crash detection and loop protection.

---

## 📜 License & Privacy

**KhanaBook Server:** MIT License. See the `server/LICENSE` file for more details.  
**KhanaBook Lite (Android):** Internal/private project. All rights reserved.

Happy Billing! ☕🥡
# CI/CD Test: Tue Mar 24 06:45:44 UTC 2026
# Final CI/CD Verification: Tue Mar 24 07:42:40 UTC 2026
# CI/CD Final Test: Tue Mar 24 07:55:10 UTC 2026
