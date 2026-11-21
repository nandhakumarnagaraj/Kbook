# Arow competitor analysis

Research date: 2026-09-08. Target APK: `C:/Users/nandh/Downloads/com.arowapp.arowapp.apk`.

Follow-up: [Deeper bytecode and workflow analysis](arow-deep-analysis-2026-09-08.md) strengthens the offline findings and clarifies table management, modifiers, printer limits and KhanaBook's actual billing entry points. Use that follow-up where its evidence is more specific.

Scope: static inspection of the supplied APK, research against the publisher's public pages, and comparison with the current KhanaBook source. No APK execution, account creation, payment, authenticated access or competitor API probing was performed. This is a competitive product/architecture assessment, not a penetration test or a runtime reliability verdict.

## Main conclusions

- Arow is a close product competitor, with APK evidence for station-wise kitchen printing, paired kitchen displays, price tiers, coupons, expenses and QR-order settings.
- Its offline settings explicitly describe owner-only billing and staff restrictions to avoid invoice conflicts. A separate string describes a 14-day offline subscription-verification limit. These are strong investigation leads, but enforcement was not traced or tested.
- KhanaBook's owner/staff offline-first design is a potential differentiator. Its advantage needs to be demonstrated with multi-terminal recovery tests; source architecture alone does not establish better reliability.
- The clearest candidate gaps in the reviewed KhanaBook code are station routing, a dedicated paired kitchen display, USB printing, regional-language resources, and some owner-management workflows.
- Pricing and aggregator claims require care: the headline offer does not establish annual cost, and the publisher's terms qualify online-order commissions and aggregator automation, as detailed below.

Machine-readable local evidence: [APK inventory](arow-apk-evidence-2026-09-08.json). Evidence labels throughout this report distinguish publisher claims, packaged resources, and analytical inferences.

## Identity and positioning

The exact package `com.arowapp.arowapp` belongs to **Restaurant Billing App - Arow**, published as **Arow Labs**. The retrieved Google Play listing displayed 5K+ downloads and approximately 4.5 stars. It targets Indian restaurant billing and operations. [Google Play listing](https://play.google.com/store/apps/details?id=com.arowapp.arowapp)

The homepage leads with fast receipt creation, offline use, automatic menu import from an image, and round-the-clock human support. It also advertises customer QR ordering, anonymous feedback, expense categories, ingredient/wastage tracking, low-stock warnings, staff permissions, and reporting. These form a broader operations pitch around the billing workflow. [Arow homepage](https://www.arowapp.com/)

## Advertised capabilities to compare with KhanaBook

| Area | Publisher's advertised behavior |
|---|---|
| Billing | GST/FSSAI invoice fields; cash, UPI, card and split payments; charges, discounts and rounding |
| Kitchen | Station-routed KOT, KDS, notes, variants and add-ons |
| Dining room | Areas, table status, captain ordering and staff permissions |
| Customer retention | Customer history, credit orders, coupons and loyalty |
| Owner controls | Inventory alerts, PDF reports and multiple outlets |
| Continuity | Offline billing followed by automatic synchronization |
| Localization | Eleven languages, including English, Hindi and major regional languages |

These are listing claims, not successful device tests. [Google Play feature description](https://play.google.com/store/apps/details?id=com.arowapp.arowapp)

The first-party store separately describes photo-based menu setup, staff onboarding, split tender, downloadable reports and promotional coupons. Its FAQ describes installation, printer setup and basic training assistance, plus a one-year plan covering features, updates and support. This page was available only as an older search-index snapshot during this review. [Indexed Arow store](https://store.arowapp.com/)

## Pricing and commercially significant qualifications

| Evidence | What can be concluded |
|---|---|
| Homepage offer | Advertises a ₹299 introductory offer. Duration, included devices/features and renewal amount were not exposed in retrieved content. [Homepage](https://www.arowapp.com/) |
| Indexed store | Advertised software starting at ₹2,999, against a ₹5,999 reference price. Snapshot was crawled about five months earlier; direct retrieval returned 404. This is a historical pricing clue, not a verified current quote. [Store](https://store.arowapp.com/) |
| Subscription terms | Describes yearly subscriptions and non-refundable plans. Elsewhere mentions a monthly option only if available. [Terms, sections 3 and 9](https://www.arowapp.com/terms) |
| QR ordering | Terms specify 2.5% commission on orders paid online. Custom-priced zero-commission plans have a 3,000-orders/month fair-use limit, with 2.5% applying beyond it. [Terms, section 9](https://www.arowapp.com/terms) |

The observed footer's pricing link returned to the homepage, so no complete current plan matrix was verified. The displayed offer is insufficient to estimate annual ownership cost. [Homepage navigation](https://www.arowapp.com/)

**Aggregator integration needs qualification.** The Play listing advertises Zomato/Swiggy integration. [Play description](https://play.google.com/store/apps/details?id=com.arowapp.arowapp) However, Arow's own terms explicitly say it does not provide integration automation and instead forwards restaurant requests to the platforms for approval. Treat automated aggregator ingestion as unverified until an actual approved integration is demonstrated. [Terms, section 8](https://www.arowapp.com/terms)

## Devices and printing

Arow markets standard Android hardware, offline billing, a cloud dashboard, and Bluetooth, USB and network KOT printing. Its comparison pages are evidence of Arow's own positioning only; their claims about other POS vendors were not validated. Minimum Android version and exact model compatibility require the APK manifest and device tests. [Arow's Android/printing comparison](https://www.arowapp.com/alternatives/posist)

Arow's hardware article describes a built-in ESC/POS print bridge and Android tablet pairing, with 58mm and 80mm thermal-printer workflows. This makes commodity-printer compatibility an appropriate practical comparison, but broad compatibility wording is not a tested device matrix. [Arow printing article](https://www.arowapp.com/blog/top-5-thermal-printers)

## Evidence limits and source consistency

- Play snapshots exposed update dates of August 20, September 1 and September 6, 2026, depending on locale/cache. No reliable current version number was surfaced. Use the local manifest's version for this APK. [Default Play listing](https://play.google.com/store/apps/details?id=com.arowapp.arowapp), [Indonesian listing](https://play.google.com/store/apps/details?hl=id&id=com.arowapp.arowapp)
- The homepage displayed both 1,000+ restaurants and a separate counter of 100 restaurants. Customer totals and processing-error claims are unverified marketing figures. [Homepage](https://www.arowapp.com/)
- The Play-linked privacy URL was `https://www.arowapp.com/privacy`; retrieval failed in this session. The developer website linked from Play was `https://shushantsaxenaaa.github.io/`; retrieval also failed. These are observed links, not audited policies.
- The public site links to `/login` and `/kds`, but this review did not access authenticated functionality. No account was created, APK installed, transaction submitted or competitor API probed.

## Implications for KhanaBook — analysis, not verified feature gaps

1. Evaluate onboarding friction: timed menu import and printer pairing are useful acquisition benchmarks.
2. Evaluate the entire dining workflow: waiter order, station KOT, kitchen status, split payment and owner report should be assessed as one connected scenario.
3. Test offline recovery with two devices and reconcile sales, payments and inventory after reconnection; an offline claim alone does not establish consistency.
4. Compare the effective bill: subscription, device/outlet limits, training, printing and QR-order commissions all affect merchant cost.
5. Confirm feature gaps against KhanaBook's code and a running Arow session before choosing roadmap work. Advertised features are candidates for investigation, not proof that KhanaBook lacks them or that Arow implements them reliably.

## Supplied APK: verified metadata

| Property | Observed value |
|---|---|
| Package / application label | `com.arowapp.arowapp` / `arow app` |
| Version | `v1.1.14-c083314`, version code `160` |
| Size | 39,972,583 bytes, approximately 38.12 MiB |
| Android support declared | Minimum API 24 (Android 7); target and compile API 36 |
| Launcher | `com.arowapp.arowapp.MainActivity` |
| Archive | 5,218 entries; five DEX files; 29,424 defined classes |
| Recognizable app namespace | 148 classes; obfuscation means this is not the total amount of first-party code |
| Distribution | Required split metadata is present; no native libraries are in this supplied base APK |
| Signature verification | Android SDK `apksigner` verified v2 and v3 signatures and a source stamp; one RSA-4096 signer |

Artifact SHA-256:

```text
57a4d05c0e1e32c767624a13ba3853af3d7eedcfc3701d437e34e9948783fca0
```

Signer certificate SHA-256:

```text
8a78266c17c4a49ad40e9471728d8a59713c05a172d5602d7e63a5d09c572f25
```

A valid signature establishes the APK's internal signing integrity, not independent confirmation that this downloaded file is the publisher's current release or that it is safe to run.

The manifest declares `android:requiredSplitTypes="base__abi,base__density"` and `com.android.vending.splits.required=true`. This file should not be assumed to install alone. A matching complete split set or an already installed app is needed for the next runtime stage. A settings-layout footer still says `v1.1.6 (Goldman Build)`; the manifest version above is authoritative for this file.

Method: Android SDK `aapt` inspected the manifest, compiled XML and metadata; `apksigner` checked signing; a local dependency-free Node script inspected ZIP entries, DEX identifiers/string constants and selected layout text. No method-level decompilation/control-flow audit was performed. The JSON preserves the selected evidence rather than the APK's full contents.

## Architecture inferred from packaged code

| Layer | Evidence | Interpretation and limits |
|---|---|---|
| Android UI | Numerous first-party `Activity` classes and `res/layout/activity_*.xml` resources | Substantial native Activity/XML implementation. Some Compose classes are also bundled; this is not proof every screen uses the same UI framework. |
| Identity and cloud data | Firebase Auth, Firestore, Messaging and Functions classes; a project-specific Cloud Functions host | Firebase-based services likely support core workflows. The full backend architecture and access rules are not visible here. |
| Local printer configuration | `PrinterDatabase`, generated database implementation, and printer-table migration strings | Local Room-backed printer persistence is indicated. Do not infer that the complete sales database is Room-based. |
| Payments | Razorpay classes, a Razorpay settings activity and payment-related strings | Razorpay integration exists in the package; enabled merchant flows were not verified. |
| Subscriptions | RevenueCat and Google Play Billing classes | Subscription-management dependencies are bundled. Vendor SDK example prices are not evidence of Arow's actual plans. |
| Diagnostics / exports | Bugsnag, Firebase Analytics and Apache POI classes | Diagnostics and spreadsheet-related capabilities are plausible. Actual data collection and export behavior remain untested. |

Embedded hosts include `arowapp.com`, `bill.arowapp.com`, `us-central1-arow-app-billing.cloudfunctions.net`, and domains for Firebase Storage, Razorpay, RevenueCat and Bugsnag. These were read as strings only, not contacted. Other hosts in the JSON can originate from SDK documentation, examples or fallback code; the list is not an inventory of verified live endpoints.

Class counts are dependency-presence evidence, not a basis for judging quality, authorship, performance or security. Missing recognizable classes can reflect obfuscation or separate splits.

## Product behavior exposed by APK resources

### Offline operation: the most important distinction

`res/layout/activity_settings_app.xml` contains:

> Offline Billing Mode (Owner Only)

Its explanatory text says local offline billing is enabled on the owner phone and staff devices are restricted to prevent offline invoice conflicts. DEX string constants separately include:

> 14-day offline limit reached. Connect to the internet to verify subscription.

Other strings refer to enabling offline mode in settings, falling back to offline Firestore, reconnect-triggered synchronization and offline-saved refunds. Together these indicate intended offline workflows with eligibility/subscription constraints. They do **not** establish which subscriptions enforce these rules, whether the strings remain reachable, or how correctly conflicts are resolved.

The competitive test is specific: disconnect an owner phone and a staff phone, try concurrent bills, then reconnect and compare invoices, payments, stock and reports. Perform this only in an authorized test account on a complete installed app.

### Kitchen and printer workflows

- App-settings XML describes printing items only to their assigned station's KOT printer.
- Printer migration text adds an `assignedStation` field with an `All` default.
- KDS settings describe opening `arowapp.com/kds` on a TV, tablet or phone and scanning the display's QR code to pair it. Automatic KOT sending and an audible alert have dedicated settings.
- USB printer connection/interface/endpoint error strings corroborate packaged USB support paths, but do not establish compatibility with any particular printer.

These are more concrete than a marketing feature list, while still short of a working end-to-end demonstration.

### Owner controls and billing details

- Price-tier settings allow up to three custom tiers in addition to Standard. Their explanatory text says a tier named exactly `Delivery` is automatically used for delivery orders.
- Business-day settings describe an adjustable rollover time for restaurants trading past midnight, such as 03:00.
- App resources expose pay-later customer credit, cooking instructions, WhatsApp bill sharing, tax rounding, receipt presentation options and spoken bill totals.
- Named activities/models support coupons, expenses, inventory history, customer feedback, multiple outlets and a merchant wallet.
- Wallet strings describe order-related fees and recharge requirements. Do not conflate this merchant fee wallet with customer credit/Khata or assume its exact charging rules from strings alone.
- AI-menu-upload resources include a prices-only update option; modifier-group, add-on and variant resources are also packaged.

`res/xml/locales_config.xml` declares 17 language codes: `en`, `hi`, `bn`, `mr`, `kn`, `te`, `ur`, `es`, `fr`, `ta`, `gu`, `ml`, `ne`, `ja`, `ko`, `fil`, `zh`. This differs from the eleven languages named in the Play description. Declared locale support does not prove complete translations: language resources can live in missing splits, and no translated screen was exercised.

## Comparison with the current KhanaBook code

This compares a partially inspectable competitor binary with an editable local source tree, so the evidence is asymmetric. KhanaBook's working tree has ongoing changes; findings describe the inspected snapshot, not a frozen production release.

| Area | Arow evidence | KhanaBook evidence / implication |
|---|---|---|
| Staff offline billing | Owner-only setting and staff-conflict warning | [SessionManager](../../Android/app/src/main/java/com/khanabook/lite/pos/domain/manager/SessionManager.kt) allows owner/staff POS and documents offline-first staff bill creation. [SyncManager](../../Android/app/src/main/java/com/khanabook/lite/pos/domain/manager/SyncManager.kt) implements synchronization. Potential advantage, pending recovery tests. |
| Terminal model | Team/outlet resources; actual limits unknown | [TerminalManagementService](../../server/src/main/java/com/khanabook/saas/service/TerminalManagementService.java) enforces five active terminals. Do not compare Arow's device count without a verified plan. |
| Station KOT | Station-setting text and printer migration | [PrinterRole](../../Android/app/src/main/java/com/khanabook/lite/pos/domain/model/PrinterRole.kt) distinguishes customer and kitchen; [PrintRouter](../../Android/app/src/main/java/com/khanabook/lite/pos/domain/manager/PrintRouter.kt) is the relevant routing seam. No equivalent station assignment was found in the reviewed paths. |
| Live kitchen display | QR-paired browser display instructions | [ReprintKdsScreen](../../Android/app/src/main/java/com/khanabook/lite/pos/ui/screens/ReprintKdsScreen.kt) searches bills and prints kitchen tickets; its name does not imply a live display. No dedicated KDS route exists in the reviewed [web routes](../../web-admin/src/app/app.routes.ts). |
| Printer connections | USB paths plus publisher Bluetooth/network claims | [PrinterConnectionType](../../Android/app/src/main/java/com/khanabook/lite/pos/domain/model/PrinterConnectionType.kt) currently supports Bluetooth and Wi-Fi, not USB. |
| Menu image import | AI upload UI and update-prices option | KhanaBook already has menu-extraction functionality: [MenuExtractionController](../../server/src/main/java/com/khanabook/saas/controller/MenuExtractionController.java). Benchmark accuracy and setup time instead of labeling OCR a missing feature. |
| Inventory | Inventory/settings/history classes | KhanaBook already has inventory, purchase, recipe and wastage workflows. Compare restaurant scenarios and reconciliation, not just checkbox presence. |
| Owner-management additions | Coupons, expenses, price tiers and outlet settings | No equivalent dedicated coupon/loyalty/expense/price-tier/outlet screens were found in the reviewed Android screen names and web routes. Candidate gaps require a product walkthrough before implementation. |
| Business-day rollover | Configurable day-start text | No corresponding cutoff field was found in the reviewed [restaurant profile](../../Android/app/src/main/java/com/khanabook/lite/pos/data/local/entity/RestaurantProfileEntity.kt). Check order numbering, reports and closing together before adding it. |
| Localization | Seventeen declared locales; completeness unverified | Source inspection found the default Android `values/strings.xml` but no localized `values-*` string sets. Regional-language support is a candidate gap. |

## Permissions and trust observations

The manifest includes Bluetooth discovery/connectivity, fine/coarse location, camera, notifications, network access, billing, biometric authentication and advertising/attribution identifiers. It does not declare contact-reading, SMS-reading or microphone-recording permissions. Permission declarations do not prove data collection or misuse; dependencies can contribute manifest entries.

`allowBackup` is enabled. Inspected backup XML contains no explicit exclusions. That is an item for a future authorized storage review, not a demonstrated vulnerability: sensitivity depends on what is persisted and applicable platform backup behavior. No runtime storage or traffic was inspected, and this report makes no security finding from SDK presence or manifest permissions alone.

## Recommended follow-up priorities

These are analytical recommendations based on the evidence above, not a commitment to implement changes.

1. **Prove the offline differentiator.** Exercise concurrent owner/staff billing, process death, reconnection, retries, returns and printer failures across KhanaBook's supported terminal count. Reconcile invoices, payments and inventory with no duplicates or lost operations before making reliability claims.
2. **Validate station KOT and a real KDS with merchants.** Arow has unusually concrete evidence for both. Define station assignment, duplicate prevention, order updates and disconnected-display behavior before selecting scope.
3. **Benchmark onboarding and hardware.** Time a menu-photo import through the first printed bill. Measure correction effort and test the actual printers used by target restaurants; evaluate USB demand alongside existing Bluetooth/Wi-Fi support.
4. **Prioritize small, frequent operational needs.** Validate business-day rollover, delivery pricing and regional-language demand; then investigate coupons and expense entry against merchant interviews.
5. **Verify total cost and live integrations.** Obtain a current written plan quote and an authorized Arow demo. Include devices, outlets, renewals, QR fees, wallet charges and the exact aggregator workflow.

For a subsequent hands-on comparison, obtain a complete matching split-APK set or use a dedicated test device with the official installed app and an authorized demo account. Use synthetic menu/customer/order data. Capture onboarding, owner/staff offline behavior, kitchen routing, refunds, report reconciliation and subscription limits. This static assessment cannot settle those runtime questions.
