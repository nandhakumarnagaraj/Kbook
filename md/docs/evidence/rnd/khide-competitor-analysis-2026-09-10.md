# Khide Owner competitor analysis

Research date: 2026-09-10. Target APK: `C:\Users\nandh\Desktop\com.khide.restaurant.apk`.

Treat the sibling reports as complementary: [Arow competitor analysis](arow-competitor-analysis-2026-09-08.md) and the [deeper Arow workflow analysis](arow-deep-analysis-2026-09-08.md) describe the comparison methodology and evidence labels used here.

Scope: static inspection of the supplied APK (decompiled with apktool 2.11.1), research against the publisher's public listings, and comparison with the current KhanaBook source. No APK execution, account creation, payment, authenticated access or competitor API probing was performed. This is a competitive product/architecture assessment, not a penetration test or a runtime reliability verdict.

## Main conclusions

- Khide Owner (`com.khide.restaurant`, "Khide Owner") is a mature, closed-source, Firebase-native restaurant POS (version 116.1), published by **Ultimate Digital Solutions Pvt. Ltd / Siddhartha Mondal** (West Bengal, India). It is the owner-franchise flagship of a multi-app ecosystem that also queries `com.khide.waiterapp`, `com.khide.kitchenapp` and `com.khide.chain`.
- Its APK carries **concrete code evidence** for offline order billing with an outbox pattern, five-printer connection types (Bluetooth, BLE, USB, Wi-Fi, Ethernet) with queueing and status monitoring, dine-in table management, a portion+extra modifier model, a full credit/Khata ledger, staff attendance/payroll, raw-material recipes and reconciliation, cash drawer, GST/GSTR-1 reporting, WhatsApp and email bill delivery, and Gemini-backed photo-menu and voice-order extraction.
- **Offline-first is no longer a unique KhanaBook claim.** Khide ships `offline_mode` preferences, an offline order worker (`order_sync_oneshot`/`order_sync_periodic`), a foreground outbox watcher, a Firestore health monitor and a sync-status screen that distinguishes pending and failed orders. Its offline behavior must still be verified at runtime and its eligibility restrictions were not traced; but its code surface is comparable to KhanaBook's and must be beaten by tests, not by architecture alone.
- The clearest candidate gaps in the reviewed KhanaBook code relative to this APK are: USB/Ethernet printing with status monitoring and a print queue, a persisted table/session model, portion+extra modifiers with single-select and recipe factors, a Khata (credit customer) ledger, expense ledger, staff attendance/payroll with Aadhaar/voter document types, WhatsApp native billing, AI voice ordering, GSTR-1 JSON/CSV export, multi-outlet dashboard, and in-app forced/optional update management.
- The clearest KhanaBook advantages: a self-hosted Spring Boot backend (not Firebase-only), Easebuzz sub-merchant digital payments against Khide's UPI-link-only approach, recipe-based raw-material consumption trusted server-side, and an explicit owner/staff offline-first segmentation with a five-terminal model. Khide also bundles heavier tracking SDKs (Microsoft Clarity session recording, Facebook, Google Analytics) that argue against matching that stack.

Machine-readable local evidence: [Khide APK inventory](khide-apk-evidence-2026-09-10.json).

## Identity and positioning

The exact package `com.khide.restaurant` belongs to **Khide Owner**, a restaurant management/POS app published by **Siddhartha Mondal** (Ultimate Digital Solutions Pvt. Ltd). Third-party mirrors (Softonic, CNET) list it as free, approximately 4.7 stars, and describe real-time order processing, automatic print, menu management, staff taking table orders with unique access codes, inventory tracking and sales reports. [Softonic listing](https://khide-owner.en.softonic.com/android), [CNET listing](https://download.cnet.com/khide-owner/3000-android-khide-owner.html). These are publisher-adjacent claims, not verified device tests.

The app queries four sibling packages: `com.khide.restaurant`, `com.khide.waiterapp`, `com.khide.kitchenapp`, `com.khide.chain`. Android `<queries>` declarations are evidence of intended inter-app workflows (staff waiter app, kitchen display app, multi-branch chain app), not proof those apps are installed or integrated.

Firebase identity (from packaged strings): project `restaurantv2-b7f6e`, storage `restaurantv2-b7f6e.firebasestorage.app`, GCM sender `814354778637`, with Firestore, Realtime Database (`asia-southeast1`), Cloud Functions (`asia-south2`), App Check (debug + Play Integrity) and Remote Config all registered.

## Supplied APK: verified metadata

| Property | Observed value |
|---|---|
| Package / application label | `com.khide.restaurant` / `Khide Owner` |
| Version | `116.1`, version code `116` |
| Size | 82,408,806 bytes, approximately 78.6 MiB |
| Android support declared | Minimum API 26 (Android 8); target and compile API 36 |
| Launcher | `com.khide.restaurant.MainActivity` |
| App code | 6,650 first-party `com/khide/restaurant/*` smali files across seven DEX modules (52,830 smali files total, heavy R8 obfuscation) |
| Distribution | `android:requiredSplitTypes="base__abi,base__density"`; split metadata present; this base APK should not be assumed to install alone |
| Localization | No `values-*` locale directories in this base APK; English strings only (`app_name` = "Khide Owner") |

Artifact SHA-256:

```text
e4c695b92263d22ca3541b8cacd31653dde01e301847b301cda8c7adc969bc86
```

A valid-looking APK structure and Play stamp are not independent confirmation that this file is the publisher's current release or safe to run. The JSON preserves the selected evidence rather than the full contents.

## Architecture inferred from packaged code

| Layer | Evidence | Interpretation and limits |
|---|---|---|
| Android UI | Screens/models/service/components packages with Kotlin `*Kt` composable files; single `MainActivity` + Compose Navigation | Near-total Jetpack Compose implementation; obfuscation renames most helper classes to single letters (`a`–`z`, `a0`–`z7`) while keeping screen/model file names |
| Cloud data | Firestore, Functions, Messaging, App Check, Remote Config, Realtime Database registrations; `FirestoreUtils`, `FirestoreRepository`, `CloudFunctionHelper` | Firebase is the primary backend. No third-party POS backend host found; business rules run in Cloud Functions and the client |
| Offline sync | `OfflineOrderHelper`, `OrderSyncWorker`, `OrderSyncScheduler`, `ForegroundOutboxWatcher`, `FirestoreHealthMonitor/Scheduler/Worker`, Pending/Failed order UI strings | A genuine offline outbox pattern around orders; enforcement and conflict-resolution correctness are untested |
| Printing | `thermal/` package: BLE/Bluetooth/Ethernet/USB/Wi-Fi connections, `EscPosPrinter` (dantsu), job queue with priority, `PrinterStatusMonitor` with paper/cutter alerts, `BlogBase64Cache` for logo | ESC/POS thermal printing over five transport types; the firmware feature detection targets `KPC307`-class printers; not a tested device matrix |
| Payments | `PaymentMode` enum (CASH, UPI, CARD, BANK_TRANSFER, CHEQUE, OTHERS), `PaymentSplit`, `PrepaidUpiInfo`, `UpiPayShortUrlMinter`, `BillShortUrlMinter`, `QRCodeUtil` | UPI-link and UPI-intent style payments minted through a backend short-URL service. No Razorpay/Cashfree-style aggregator SDK is packaged |
| AI / OCR / voice | Cloud functions `processMenuPhoto`, `parseVoiceMenuCreate`, `parseVoiceOrder`; Gemini call/photo-upload counters | Photo menu extraction, voice menu creation and AI voice ordering hit `asia-south2-restaurantv2-b7f6e.cloudfunctions.net`; per-user quotas are tracked |
| Diagnostics / analytics | Google Analytics/Firebase, Microsoft Clarity SDK (`com.microsoft.clarity`, `assets/clarity.js`), Facebook SDK | Session-recording analytics is bundled; actual collection behavior and consent handling were not verified |
| Export | `opencsv`, Jackson (`fasterxml`), `GSTReportUtils` (GSTR-1 JSON/CSV), `ReportExportUtils` (PDF/CSV) | Spreadsheet/compliance export is plausible; no file was generated during this review |

## Product behavior exposed by APK code

### Offline operation: now a peer claim, not a differentiator

- `OfflineOrderHelper` reads `offline_mode_prefs`, `offline_mode_enabled`, `offline_mode_user_preference`, `offline_mode_auto_enabled` and a `cached_last_order_number`. `C:\Users\nandh\Desktop\com.khide.restaurant_decompiled\smali_classes3\com\khide\restaurant\utils\OfflineOrderHelper.smali`
- `OrderSyncWorker` registers `order_sync_oneshot` and `order_sync_periodic` WorkManager jobs and drives a Firestore repository; `ForegroundOutboxWatcher` and `FirestoreHealthMonitor` keep offline work alive in the foreground. `smali_classes3\com\khide\restaurant\utils\OrderSyncWorker.smali`
- `SyncStatusScreenKt` renders "Everything synced", pending-order rows and failed-order rows with summary chips — an explicit user-facing sync state machine. `smali_classes3\com\khide\restaurant\screens\SyncStatusScreenKt.smali`
- `SafeSignOut` blocks sign-out while a pending outbox exists. The offline `cached_last_order_number` implies per-device offline order numbering, analogous to KhanaBook's terminal series.

Confidence that offline billing exists in connected code is high, but role/eligibility constraints, numbering collisions and conflict resolution were not traced or executed.

### Kitchen, printing and dining room

- **Printing is the deepest area.** Five connection types, auto-reconnection, printer status monitoring (paper, cutter), queued print jobs with priority, logo bitmaps, and templates for simple, detailed, combined and unified bills, KOT and amendment KOT, credit-repayment receipts, staff-payment receipts, menu QR and table QR. USB attachment is handled at launch via a vendor-ID filter covering common thermal-printer vendors.
- **Dining rooms.** `Table` model has statuses `Available`/`Occupied`/`Bill Requested`/`Maintenance` and types `Regular`/`Terrace`/`VIP`; `TableManagementScreenKt` adds areas, table reordering, table transfer, sessions (open/closed), ready-items sections, bill sheets per table, and a "specific table" staff-role string (`Khide Staff - Specific Table`).
- **Companion apps.** Kitchen and waiter apps are queried separately, so kitchen display is delegated to the ecosystem rather than embedded here (KOT changes still print amendments in this app).

### Menu customization

`MenuItem` carries `portions` (`name` + `price` + `recipeFactor`), `extras` (`name` + `price`), `extrasSingleSelect`, `portionWiseExtras`, `trackQuantity`, `lowStockThreshold`, `inStock` and `photoAutoTried`. This is a persisted portion + paid-extra + single-select modifier model with recipe factors — materially beyond KhanaBook's current variant-only menu items.

### Payments and customer credit

- Payment modes: cash, UPI, card, bank transfer, cheque, others; `PaymentSplit` and `rescaleSplitsToNewTotal` support split settlement.
- `UpiPayShortUrlMinter` mints `vpa` + `amount` + `payeeName`(Merchant) + `note`("Bill") + `createdAt`/`expiresAt` records for single and combined bills — a customer-facing UPI pay link, not an aggregator SDK. There is no evidence of a full checkout/payment gateway integration.
- `CreditUtils` + `CreditTransaction` (`CREDIT`/`REPAYMENT`) + `CreditCustomerBulkUpload` + per-day credit and ledger views constitute a real Khata/credit ledger, which the Arow review did not establish for KhanaBook.

### Owner operations and compliance

- `GSTReportUtils` computes GST summaries, menu/mode-wise breakdowns, top-selling items, top add-ons, GSTR-1 JSON and CSV export; extra-charge categories include delivery, spl service, packaging, handling, pickup.
- `CashDrawerUtilsKt` manages drawer days, opening balance, carry-forward, cash movements, closed/reopened shifts, expenses and payroll lines.
- `StaffUtilsKt` + `EmployeesScreen` cover staff profiles (document types `Aadhaar`/`Passport`/`Voter ID`/`Other`), attendance with `COMP_OFF_EARNED`/`COMP_OFF_USED`, shift types and staff payments.
- `BusinessDayConfig` reads a per-restaurant business-day start (rollover) setting from Firestore; `DayCloseScheduler`/`DayCloseWorker` schedule day-close work.
- `RawMaterialUtilsKt` covers suppliers, purchases, recipes with `recipeFactor`, stock reconciliations with variance confirmation, low-stock alerts, usage reports and supplier payments — deeper than a simple inventory counter.
- `AllOutletsDashboardScreen` aggregates stats across outlets (`activeRestaurantId`, `switchOutlet`) and `AccountSessions`/`AccountSwitcherDialog` enable multi-account roaming.
- `MarketingScreen` walks owners through a public business profile (photo → video → location) — an acquisition/listing feature with no KhanaBook analogue.
- `MyRestaurantScreen` contains an `ActivationSteps` component and employee roles `waiter`/`chef`/`table`.

### Localization

Only the default English string set is present in this base APK (no `values-*` directories, no `locales_config.xml`). This mirrors the current KhanaBook state; regional-language support remains unconfirmed for Khide too.

## Comparison with the current KhanaBook code

This compares a partially inspectable competitor binary with an editable local source tree, so the evidence is asymmetric. KhanaBook's working tree has ongoing changes; findings describe the inspected snapshot, not a frozen production release.

| Area | Khide evidence | KhanaBook evidence / implication |
|---|---|---|
| Offline billing | Offline-mode prefs, order sync worker, outbox watcher, pending/failed sync UI | [SessionManager](../../Android/app/src/main/java/com/khanabook/lite/pos/domain/manager/SessionManager.kt) documents owner/staff offline-first billing, [SyncManager](../../Android/app/src/main/java/com/khanabook/lite/pos/domain/manager/SyncManager.kt) syncs. No longer a unique claim — must be proven with recovery tests |
| Terminal identity | `cached_last_order_number` per device, outbox | [TerminalManagementService](../../server/src/main/java/com/khanabook/saas/service/TerminalManagementService.java) enforces five active terminals with per-terminal series. Khide's offline numbering rules were not traced |
| Printing | 5 transports, auto-reconnect, status monitor, print queue, unified/combined/KOT-amendment templates | [PrinterConnectionType](../../Android/app/src/main/java/com/khanabook/lite/pos/domain/model/PrinterConnectionType.kt) covers Bluetooth and Wi-Fi only; [PrintRouter](../../Android/app/src/main/java/com/khanabook/lite/pos/domain/manager/PrintRouter.kt) is the routing seam. **Gap**: USB, Ethernet, queue, status monitoring |
| Table management | Floor/area, statuses, sessions, transfer, specific-table staff role | Dine-in tables are labels in `customerName` (see Arow deep analysis); no table entity. **Gap** |
| Modifiers | Portions + extras, single-select, per-part price, recipe factor | [ItemVariantEntity](../../Android/app/src/main/java/com/khanabook/lite/pos/data/local/entity/ItemVariantEntity.kt) variants only; no modifier-group/add-on model in bill items. **Gap** |
| Khata / credit | Credit ledger, bulk upload, repayments, per-day views | Customer data exists; no credit ledger product established. **Gap** |
| Expenses | Expense ledger + categories + calendar | Server inventory purchasing exists; not a general expense ledger. **Gap** |
| Inventory | Recipes, reconciliation, suppliers, purchases, usage report | KhanaBook has [InventoryScreen](../../Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/InventoryScreen.kt) + server [InventoryService](../../server/src/main/java/com/khanabook/saas/service/InventoryService.java) with recipe consumption. Comparable; benchmark reconciliation |
| Menu OCR | Photo menu → Cloud Function → editable preview; voice menu create; Gemini quotas | [MenuExtractionController](../../server/src/main/java/com/khanabook/saas/controller/MenuExtractionController.java) exists. Benchmark accuracy and setup time |
| Voice ordering | `parseVoiceOrder` AI voice order + match UI | No voice-ordering path found in KhanaBook screens. **Candidate gap** |
| Digital payments | UPI pay links, UPI-intent, no aggregator SDK | [EasebuzzOnboardingScreen](../../Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/EasebuzzOnboardingScreen.kt), [PaymentController](../../server/src/main/java/com/khanabook/saas/controller/PaymentController.java): sub-merchant digital payments. **KhanaBook advantage** |
| GST / compliance | GSTR-1 JSON/CSV, menu/mode breakdown | ReportsScreen + server [GstFssaiController](../../server/src/main/java/com/khanabook/saas/controller/GstFssaiController.java). Location of GSTR-1 export needs comparison |
| WhatsApp / email bills | `WhatsAppBillSender`, email receipts + Gmail reconnect | Not established in KhanaBook Android. **Candidate gap** |
| Staff operations | Attendance, shifts, comp-off, document IDs, payroll | Role access and permissions exist ([StaffPermissionScreen](../../Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/StaffPermissionScreen.kt)); attendance/payroll absent. **Candidate gap** |
| Multi-outlet | All-outlets dashboard, account switching, chain app | Single-restaurant model enforced by `RestaurantProfileEntity`; multi-branch not found in routes. **Candidate gap** |
| Update management | Forced/optional update dialogs + download progress | Present? `UpdateManager` equivalent not seen in KhanaBook screens. **Check** |
| Localization | English-only base APK | Same limitation; regional languages remain a shared gap |
| Backend | Firebase-only (Firestore/Functions) | Self-hosted Spring Boot at `kbook.iadv.cloud`. **KhanaBook advantage for control/portability** |
| Analytics / privacy | Microsoft Clarity session recording, Facebook, Google Analytics | Lower tracking surface is easier to defend in enterprise/DFO contexts |

## Permissions and trust observations

The manifest includes Bluetooth (legacy + scan + connect), USB, fine/coarse location, camera, microphone (voice ordering), contacts, notifications, foreground-service printing, request-ignore-battery-optimizations, full-screen intent, and boot-completed receivers. It does not declare SMS/phone-state permission. Permission declarations do not prove misuse; dependencies contribute entries.

`allowBackup="true"` with empty `backup_rules.xml` and a cloud-backup-only `data_extraction_rules.xml` means no explicit exclusions were declared. Together with the bundled **Microsoft Clarity** session-recording SDK and Facebook SDK, this is grounds for a future authorized privacy/storage review, not a demonstrated vulnerability. No runtime storage, traffic or consent flow was inspected.

## Recommended follow-up priorities

These are analytical recommendations based on the evidence above, not a commitment to implement changes.

1. **Re-prove the offline differentiator against this peer.** Khide demonstrably ships the same offline vocabulary (outbox, health monitor, sync status). KhanaBook's win must be demonstrated: concurrent owner/staff billing, process death, reconnection, returns, printer failures and reconciliation with no duplicates across its supported terminal count.
2. **Close the table + modifier gaps first.** Floor/area sessions and portion+extra modifiers are the two most concrete, most-demonstrable feature deltas in the dine-in workflow; both feed printing and inventory.
3. **Evaluate printing breadth.** USB/Ethernet support plus a status-monitored print queue is Khide's strongest technical area; inspect merchant demand for these transports before committing scope.
4. **Consider owner-operations additions.** Khata ledger, expense ledger, staff attendance/payroll and GSTR-1 export are self-contained, high-visibility owner features with strong packaged evidence here.
5. **Verify digital-payments parity.** Khide's UPI-link model is simpler than Easebuzz sub-merchant settlement; confirm whether customers/payees perceive a difference in payment success and reconciliation before matching it.
6. **Benchmark AI extraction** (photo menu + voice) side by side on the same menus before treating OCR as parity.
7. For a subsequent hands-on comparison, obtain a matching split-APK set or a dedicated test device with the official installed app and an authorized demo account, then capture onboarding, offline owner/staff behavior, table sessions, split payments, refunds and report reconciliation. This static assessment cannot settle those runtime questions.