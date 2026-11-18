# 06 - Data Ownership & Storage Boundary Matrix
> **KhanaBook POS & SaaS Platform Architecture**  
> **5-Tier Data Residency Classification & Latency Optimization**  

---

## 1. Executive Principles of Data Residency

In an offline-first restaurant POS system, storage residency is driven by three hard architectural constraints:
1. **Sub-10ms Checkout & Printing Latency:** Counter staff punching busy lunch rushes cannot tolerate network latency or round-trip serialization overhead. Hardware and KOT buffers MUST reside in local device memory.
2. **Zero-Trust Security Boundary:** Physical budget tablets on counter tables can be lost, stolen, or compromised. Central database credentials, bcrypt password hashes, and SaaS subscription gates MUST NEVER reside on Android SQLite.
3. **Multi-Terminal Coherence Without Network Lock Spikes:** Order sequence generation (`daily_order_id`) must function offline, while conflict detection and final legal invoice assignment are strictly coordinated without distributed locking bottlenecks.

---

## 2. Comprehensive 5-Tier Entity Classification Matrix

| Entity / Component | Storage Residency | Primary Database | Secondary Database | Rationale & Architectural Reason |
|:---|:---:|:---:|:---:|:---|
| **Printer Hardware Profiles** | **1. SQLite Only** | Android SQLite (`printer_profiles`) | *(None)* | Physical Bluetooth MAC addresses (e.g. `DC:0D:30:...`) and USB IDs are unique to each tablet. Syncing them to PostgreSQL would cause cross-device clobbering. |
| **Kitchen Print Queue (KOT)** | **1. SQLite Only** | Android SQLite (`kitchen_print_queue`) | *(None)* | Offline resilience. If kitchen printer runs out of paper, orders buffer locally and retry automatically on reconnect without cloud overhead. |
| **Kitchen Reprint Audit** | **1. SQLite Only** | Android SQLite (`kot_events`) | *(None)* | Tracks local ticket reprint counters and audit timestamps directly on the terminal. |
| **Sync Quarantine Dead-Letter** | **1. SQLite Only** | Android SQLite (`sync_quarantine_records`) | *(None)* | Stores rejected payloads for local operator inspection in the POS Sync Center without corrupting PostgreSQL schemas. |
| **Local Category Self-Healing** | **1. SQLite Only** | Android SQLite (`menu_item_category_repairs`) | *(None)* | Self-healing map when a dish is pulled whose category was not yet synced to this terminal. |
| **Atomic Terminal Counter** | **1. SQLite Only** | Android SQLite (`terminal_daily_counter`) | *(None)* | Sub-millisecond monotonic daily sequence generator per date and terminal series without network round-trips. |
| **Security Audit Compliance** | **2. PostgreSQL Only** | Cloud PostgreSQL (`security_audit_log`) | *(None)* | Tamper-proof compliance log (5,599+ rows). Persisting massive audit logs on budget 2GB RAM POS tablets would degrade SQLite cache performance. |
| **Transactional Email Outbox** | **2. PostgreSQL Only** | Cloud PostgreSQL (`email_outbox`) | *(None)* | Asynchronous PDF invoice and daily closing email dispatching runs on server thread pools (`JavaMailSender`), not on device battery. |
| **FCM Push Notification Outbox**| **2. PostgreSQL Only** | Cloud PostgreSQL (`notification_events`) | *(None)* | Central event queue for multicasting push notifications across merchant devices. |
| **FCM Device Registration** | **2. PostgreSQL Only** | Cloud PostgreSQL (`device_tokens`) | *(None)* | Google FCM registration tokens for cloud-to-device push routing. |
| **Terminal Fleet Approvals** | **2. PostgreSQL Only** | Cloud PostgreSQL (`device_registration_request`, `restaurant_terminal`) | *(None)* | Security barrier. Owner must approve new tablets from web dashboard or primary phone before POS access is granted. |
| **Authentication Credentials** | **2. PostgreSQL Only** | Cloud PostgreSQL (`users.password_hash`, `refresh_tokens`, `token_blocklist`) | *(None)* | Zero credential theft risk. If tablet is stolen from the counter, attacker cannot dump password hashes from SQLite. |
| **Security Rate Limiting** | **2. PostgreSQL Only** | Cloud PostgreSQL (`rate_limit_attempts`, `otp_requests`) | *(None)* | Central brute-force protection across all endpoints. |
| **SaaS Governance & Flags** | **2. PostgreSQL Only** | Cloud PostgreSQL (`feature_flag*`, `restaurantprofiles.is_suspended`) | *(None)* | Central subscription kill-switch and instant feature entitlement management. |
| **Cloud OCR Menu Processing** | **2. PostgreSQL Only** | Cloud PostgreSQL (`menu_extraction_jobs`) | *(None)* | Heavy asynchronous machine learning OCR jobs run on cloud servers. |
| **Orders & Invoices** | **3. Synced (Both)** | Android SQLite (`bills`) | Cloud PostgreSQL (`bills`) | Punch orders offline; sync financial ledger to cloud for web portal, analytics, and GST tax filing. |
| **Order Line Items** | **3. Synced (Both)** | Android SQLite (`bill_items`) | Cloud PostgreSQL (`bill_items`) | Line items punch instantly locally; cloud stores items for item-wise sales reports and tax audits. |
| **Payment Transactions** | **3. Synced (Both)** | Android SQLite (`bill_payments`) | Cloud PostgreSQL (`bill_payments`) | Cash/UPI splits punch locally; cloud reconciles gateway callbacks and payment status. |
| **Menu Catalog & Categories** | **3. Synced (Both)** | Android SQLite (`categories`, `menu_items`, `item_variants`) | Cloud PostgreSQL (`categories`, `menuitems`, `itemvariants`) | Catalog is cached in SQLite for offline instant order punching, synced bi-directionally when updated. |
| **Store Profile & Tax Rules** | **3. Synced (Both)** | Android SQLite (`restaurant_profile`) | Cloud PostgreSQL (`restaurantprofiles`) | Shop name, GSTIN, business hours cutoff, and footer are shared across all terminals. |
| **Staff Accounts** | **3. Synced (Both)** | Android SQLite (`users`) | Cloud PostgreSQL (`users`) | User metadata and roles are synced to SQLite; password hash is stripped. |
| **Daily Bill Number** | **4. Derived / Computed**| Derived locally (`daily_order_id`) | Validated/Repaired on Server | Minted offline by terminal starting from 1 each business day; repaired in-place on cloud if collisions occur. |
| **Display Order ID** | **4. Derived / Computed**| Computed (`terminal_series + daily_order_id`) | Mirrored on Server (`daily_order_display`) | Operational display tag (e.g. `A01`, `B05`). |
| **Fiscal Year & Invoice Series**| **4. Derived / Computed**| Computed (`YY + Series + Seq`) | Enforced on Server (`invoice_number`) | Tax-legal GST invoice identifier (e.g. `INV-A-2627-0001`). |
| **Taxes (CGST / SGST)** | **4. Derived / Computed**| Computed via `BillCalculator` | Re-verified on Server | Computed from item subtotal and shop GST percentage (e.g. 2.5% CGST + 2.5% SGST = 5.0% GST). |
| **Staff PIN Authentication** | **5. Temp / Cache Data** | Android SQLite (`staff_permissions`) | *(None)* | Local salt/hash of 4-digit cashier PIN allowing fast offline screen unlock. |
| **Permission Revision Marker**| **5. Temp / Cache Data** | Android SQLite (`permission_cache`) | Cloud PostgreSQL (`users.permission_revision`) | Monotonic revision counter: if server revokes staff permissions, client cache invalidates immediately. |
| **In-Flight Payment Lock** | **5. Temp / Cache Data** | Android SQLite (`payment_attempt_status`) | *(None)* | Prevents double-tapping or concurrent bill edits while UPI payment app intent is open. |
