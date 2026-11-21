# KhanaBook Printing & Billing Architecture Specification

**Document Version:** 1.0.0  
**Status:** Verified on Live Hardware (Android 15 / ESC/POS)  
**Target Applications:** `apps/android` (POS Engine), `services/server` (Master Sync & Webhooks)

---

## 1. Executive Summary

This specification defines the unified billing workflows, thermal printing transports, and event-based Kitchen Order Ticket (KOT) lifecycles for KhanaBook. It documents both the domain rules and the low-level hardware adaptations necessary to operate reliably across real-world commercial restaurant hardware (Bluetooth SPP, Wi-Fi raw TCP 9100, and USB-OTG Host mode).

---

## 2. Master Billing & Printing Matrix

| Restaurant Mode (`orderPaymentFlowMode`) | Order Type (`orderType`) | Billing Entry (`quickMode`) | User Action | Order Status | Print Dispatch Mode | Tickets Dispatched |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Pay-After-Food** | **Dine-In** | Normal / Quick | "Send to Kitchen (KOT)" | `draft` | `AUTO` | **KOT Only** (Customer receipt suppressed) |
| **Pay-After-Food** | **Dine-In** | Active Orders | "Settle Bill" | `completed` | `MANUAL_RECEIPT_ONLY` | **Customer Receipt Only** |
| **Pay-After-Food** | **Takeaway** | Normal / Quick | "Proceed to Pay" | `completed` | `AUTO` | **Both KOT & Customer Receipt** |
| **Pay-Before-Food** | **Dine-In** | Normal / Quick | "Proceed to Pay" | `completed` | `AUTO` | **Both KOT & Customer Receipt** |
| **Pay-Before-Food** | **Takeaway** | Normal / Quick | "Proceed to Pay" | `completed` | `AUTO` | **Both KOT & Customer Receipt** |

---

## 3. UI Entry Modes

### 3.1 Normal Billing (`quickMode = false`)
* **Step 1 (Table / Customer Selection):** Cashier chooses Dine-In (Table name/number) or Takeaway (Customer name and mobile number).
* **Step 2 (Menu Selection & Cart):** Category browsing, item search, variant management, and special instruction notes.
* **Step 3 (Action Step):**
  * If `PAY_AFTER_FOOD` and `Dine-In`: Bottom bar displays green **"Send to Kitchen (KOT)"** &rarr; transitions order to `draft` without taking upfront payment.
  * If `PAY_BEFORE_FOOD` or `Takeaway`: Bottom bar displays gold **"Proceed to Pay"** &rarr; leads to Payment Step.

### 3.2 Quick Billing (`quickMode = true`)
* **Direct Cart Entry:** Step 1 is bypassed; cashier immediately lands on the menu/cart grid.
* **Header Toggle:** Offers explicit **[ Dine-In ]** and **[ Takeaway ]** segmented pills.
* **Automated Defaults:** Sets `customerName = "Quick Bill"`.
* **Pay-After-Food Resilience:**
  * If set to **Dine-In**: Displays green **"Send to Kitchen (KOT)"** &rarr; order saves to Active Orders as `draft` and dispatches KOT. No payment is collected upfront.
  * If set to **Takeaway**: Displays gold **"Proceed to Pay"** &rarr; takes upfront payment and prints both KOT and Customer Receipt.

---

## 4. Event-Based KOT Ticket Lifecycle

KhanaBook enforces an append-only, delta-based KOT system (`KotEventType`):

1. **`NEW`:** Initial order placement. Prints the full initial ticket for kitchen preparation.
2. **`ADD`:** Items added to an existing active table order. Prints **only the newly added items**; existing prepared items are never duplicated.
3. **`VOID`:** Items removed or quantities decreased. Prints a **`[VOID]`** ticket with removed quantities so the kitchen halts preparation.
4. **`CANCEL`:** Entire draft order cancelled. Prints an **`*** ORDER CANCELLED ***`** ticket with cancellation reason.
5. **`REPRINT`:** Deliberate manual reprint from Active Orders or Reprint KOT screen. Prints full ticket watermarked **`*** DUPLICATE / REPRINT ***`** and records an audit log.

---

## 5. Hardware Routing & Pacing Engine (`PrintRouter`)

### 5.1 Single Printer Mode (`PrinterMode.SINGLE`)
In Single Printer mode, one physical printer outputs both kitchen tickets and customer receipts.
* **Strict Serialization:** Both roles share the same `connectionTargetKey()`. Payloads bound for the same device are grouped and dispatched sequentially:
  1. **Kitchen KOT** prints first (store logo is suppressed to conserve paper).
  2. **Inter-Ticket Pacing Delay:** An adaptive hardware pause clears the printer's receive buffer before the next payload:
     * **Bluetooth:** `2,500ms` delay.
     * **Wi-Fi / USB:** `1,500ms` delay.
  3. **Customer Receipt** prints second (with logo and store headers).

### 5.2 Dual Printer Mode (`PrinterMode.DUAL`)
* **Parallel Execution:** Customer Receipt Printer and Kitchen KOT Printer are dispatched concurrently via independent coroutines.
* **Interface Independence:** Supports heterogeneous setups (e.g., Counter = USB / Bluetooth, Kitchen = Wi-Fi).
* **Offline Fallback Queue:** If the kitchen printer runs out of paper or goes offline, `KitchenPrintQueueManager` catches the failure, logs the error, and enqueues the ticket for automatic or manual one-tap retry.

---

## 6. Physical Transports & Low-Level Protections

### 6.1 Bluetooth SPP (`BluetoothPrinterTransport` & `BluetoothPrinterManager`)
* **Multi-Socket Concurrency:** Supports multiple concurrent RFCOMM connections via an internal socket registry (`activeSockets`).
* **Fast-Fail Bounded Timeout:** Each RFCOMM connection attempt is wrapped in a strict 6,000ms timeout (`withTimeout(6_000L)`) across 2 retry attempts (secure then insecure RFCOMM fallback). Offline printers fail fast without blocking the POS UI.
* **Single Mode Buffer Pacing:** 2,500ms inter-ticket delay prevents 9600-baud thermal controller buffer overruns.

### 6.2 Wi-Fi Raw TCP 9100 (`WifiPrinterTransport`)
* **Socket Watchdog Guard:** A scheduled watchdog executor forcibly terminates stuck sockets after `WRITE_DEADLINE_MS = 15,000` to unblock Java `OutputStream.write()`.
* **Chunked Writes:** Payloads are transmitted in 4 KB chunks with `tcpNoDelay = true`.
* **Subnet Fail-Open:** IP subnet mismatches generate warnings rather than hard failures, permitting multi-VLAN restaurant layouts.

### 6.3 USB-OTG Host Mode (`UsbPrinterTransport`)
* **Class Support:** Supports both standard Printer Class (`0x07`) and Vendor-Specific Class (`0xFF`).
* **Chunking & Timeout:** 16 KB chunks with a 5,000ms bulk-transfer timeout (`BULK_TIMEOUT_MS = 5_000`).
* **Hot-Plug / Re-Plug Resilience:** Dynamically listens for `ACTION_USB_DEVICE_DETACHED` and purges dead sessions so subsequent prints immediately open fresh connections.
* **Permission Self-Healing:** Auto-requests Android USB permissions via system dialog if permissions drop across phone reboots.
* **Manifest Host Declaration:** `<uses-feature android:name="android.hardware.usb.host" android:required="false" />`.

---

## 7. ESC/POS Formatting & Roll Adaptation

### 7.1 58mm vs 80mm Roll Adaptation
* **Motorized Cut Command (`GS V 0x42 0x00`):**
  * **80mm Rolls:** Auto-cutter command is dispatched at ticket end.
  * **58mm Rolls:** Manual-tear printers ignore cut blades; sending cutter bytes causes firmware to print stray ASCII glyphs (`?`, `V`). Cut commands are strictly suppressed on 58mm profiles, replaced with clean line feeds.

### 7.2 Text Formatting & Tabular Numbers
* Raw ESC/POS text layout with explicit column widths.
* Tabular number formatting (`font-variant-numeric: tabular-nums`) prevents metric jitter.
* ESC/POS raster bitmap commands (`GS v 0 0`) suppressed in favor of pure-text rendering to ensure compatibility with clone thermal controllers.
