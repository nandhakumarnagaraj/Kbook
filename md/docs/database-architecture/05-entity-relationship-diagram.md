# 05 - Entity-Relationship Diagrams (ERD)
> **KhanaBook POS & SaaS Database Architecture**  
> **Engine Models:** Android Room SQLite (v84) & PostgreSQL 16 (`kbook_saas` at Flyway V130)  

---

## 1. Android POS Local SQLite Architecture (Room v84)

```mermaid
erDiagram
    restaurant_profile ||--o{ bills : "issues"
    restaurant_profile ||--o{ categories : "defines"
    restaurant_profile ||--o{ printer_profiles : "configures (local)"
    restaurant_profile ||--o{ terminal_daily_counter : "maintains"
    users ||--o{ bills : "punches"
    users ||--o{ staff_permissions : "has PIN & perms"
    categories ||--o{ menu_items : "contains"
    menu_items ||--o{ item_variants : "has portions"
    bills ||--|{ bill_items : "contains lines"
    bills ||--|{ bill_payments : "settled by"
    bills ||--o{ kitchen_print_queue : "enqueues KOT"
    bills ||--o{ kot_events : "logs KOT reprints"
    printer_profiles ||--o{ kitchen_print_queue : "receives prints"
    menu_items ||--o{ bill_items : "referenced in"
    item_variants ||--o{ bill_items : "portion priced"

    bills {
        INTEGER id PK
        INTEGER daily_order_id "Bill Number (resets daily)"
        TEXT daily_order_display "Display Bill No (e.g. A01)"
        INTEGER lifetime_order_id "Invoice Number sequence"
        TEXT invoice_number "GST Legal Invoice (INV-A-2627-0001)"
        TEXT public_token "Global Unique Receipt UUID"
        TEXT total_amount "Monetary total"
        INTEGER is_synced "Local dirty flag"
    }

    bill_items {
        INTEGER id PK
        INTEGER bill_id FK
        INTEGER menu_item_id FK
        TEXT item_name
        INTEGER quantity
        TEXT price
        INTEGER sent_to_kot "Local print state"
    }

    bill_payments {
        INTEGER id PK
        INTEGER bill_id FK
        TEXT payment_mode "CASH, UPI, PART_*"
        TEXT amount
        TEXT operation_id "Hardware Idempotency Key"
    }

    printer_profiles {
        INTEGER id PK
        TEXT role "CUSTOMER vs KITCHEN"
        TEXT connection_type "BLUETOOTH, USB, WIFI"
        TEXT mac_address "Device-local hardware pairing"
        TEXT paper_size "58mm vs 80mm"
    }

    kitchen_print_queue {
        INTEGER id PK
        INTEGER bill_id FK
        TEXT printer_mac
        TEXT status "PENDING, SENT, FAILED"
        INTEGER attempts
    }
```

---

## 2. PostgreSQL SaaS Central Production ERD (`kbook_saas`)

```mermaid
erDiagram
    restaurantprofiles ||--|{ restaurant_terminal : "provisions"
    restaurantprofiles ||--|{ users : "employs"
    restaurantprofiles ||--o{ bills : "owns ledger"
    restaurantprofiles ||--o{ categories : "defines menu"
    restaurantprofiles ||--o{ email_outbox : "queues EOD"
    restaurantprofiles ||--o{ security_audit_log : "audited in"
    restaurantprofiles ||--o{ device_tokens : "receives FCM"
    users ||--o{ refresh_tokens : "authenticates"
    users ||--o{ bills : "punched by"
    categories ||--o{ menuitems : "classifies"
    menuitems ||--o{ itemvariants : "has variants"
    bills ||--|{ bill_items : "contains lines"
    bills ||--|{ bill_payments : "tendered by"
    feature_flag ||--o{ feature_flag_override : "overridden by"
    restaurantprofiles ||--o{ feature_flag_override : "custom toggles"

    restaurantprofiles {
        BIGINT restaurant_id PK
        VARCHAR shop_name
        VARCHAR gstin
        TIME business_day_cutoff_time "Day roll (00:00 - 23:59)"
        BOOLEAN is_suspended "SaaS governance"
    }

    bills {
        BIGINT id PK
        BIGINT local_id "Client SQLite ID"
        BIGINT restaurant_id FK
        BIGINT daily_order_id "Bill Number (resets daily)"
        BIGINT lifetime_order_id "Monotonic invoice sequence"
        VARCHAR invoice_number "GST Legal Invoice Number"
        UUID public_token UK "Web Sharing & Public Receipt"
        NUMERIC total_amount
        BIGINT server_updated_at "Delta Sync Authority"
    }

    restaurant_terminal {
        VARCHAR terminal_id PK
        BIGINT restaurant_id FK
        VARCHAR terminal_series "Assigned Series A..E"
        VARCHAR terminal_name
        VARCHAR approval_status "APPROVED, PENDING"
        BIGINT last_heartbeat
    }
```

---

## 3. Bi-Directional Delta Sync & Idempotency Pipeline

```mermaid
flowchart TD
    subgraph AndroidPOS ["Android POS Counter (Offline-First)"]
        UI["Compose Cashier UI"] -->|"Punch Order"| LocalSave["Room DB Write (is_synced = 0)"]
        LocalSave -->|"Sub-10ms"| ESCPOS["Bluetooth / LAN Thermal Printer"]
        LocalSave -->|"Trigger"| WM["WorkManager Background Daemon"]
    end

    subgraph NetworkMesh ["Network Wire (HTTPS / REST Payloads)"]
        WM -->|"POST /api/v1/sync/master/push<br/>(Batch Delta < 4KB)"| CloudPush["GenericSyncService / BillSyncService"]
        CloudPull["GET /api/v1/sync/master/pull?sinceTimestamp=..."] -->|"Delta Stream"| WM
    end

    subgraph CloudCore ["Central SaaS Cloud (PostgreSQL 16)"]
        CloudPush -->|"1. Verify Idempotency (operation_id)"| IdempCheck{"Duplicate Key?"}
        IdempCheck -->|"Yes"| AckIdemp["Ack Original Server ID"]
        IdempCheck -->|"No"| CollCheck{"Daily Bill No Collision?"}
        CollCheck -->|"Yes"| AutoRepair["Repair in-place: Next Free Series No"]
        CollCheck -->|"No"| CommitPG["Transactional Commit to PostgreSQL"]
        AutoRepair --> CommitPG
        CommitPG -->|"Stamp server_updated_at"| UpdateDelta["Ready for Multi-Terminal Pull"]
    end

    AckIdemp --> LocalSave
    UpdateDelta --> CloudPull
```
