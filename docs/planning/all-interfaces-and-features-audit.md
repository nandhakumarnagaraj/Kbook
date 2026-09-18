# Full Interface & Feature Audit — All Printer Interfaces + App-Wide Sweep

> **Status:** Verified against source on 2026-09-18. Analysis only — no code changes made.
> **Companion doc:** `docs/planning/printing-and-save-fix-plan.md` (Wi-Fi transport + settings save plan).
> **Scope:** Same methodology as the Wi-Fi analysis, applied to **every printer interface (USB + Bluetooth + Wi-Fi)** and a code-level sweep of **all 12 feature modules** (~59,800 LOC Kotlin).

---

## Part 1 — Interface-by-Interface Audit (Printing)

### Shared pipeline (all interfaces)

```
BillingViewModel → PrintCoordinator → PrintRouter.printBill()
  → per-target async(Dispatchers.IO) → InvoiceFormatter / KitchenTicketFormatter (GBK payload)
  → PrinterTransportDispatcher.print(profile, bytes)
      ├─ BLUETOOTH → BluetoothPrinterTransport → BluetoothPrinterManager
      ├─ WIFI      → WifiPrinterTransport                    ← documented in companion doc
      └─ USB       → UsbPrinterTransport
```

The `PrinterTransport` interface (`suspend fun print(profile, bytes): Boolean`) is clean and identical for all three — every fix below is internal to one transport and cannot affect the others.

---

### 1A. USB — `UsbPrinterTransport.kt` — **GOOD, with 3 gaps**

Well-built: chunked 16 KB bulk writes with per-chunk progress (`bulkTransfer` returns bytes written, loop advances `offset`), 5s bulk timeout, session reuse via `ConcurrentHashMap<String, UsbSession>`, session teardown on failure, logged errors on every failure path, permission flow with 30s suspend timeout. **This is the model the Wi-Fi transport should copy.**

Gaps found:

| # | Severity | Issue | Detail |
|---|---|---|---|
| U1 | **✅ FIXED (428a55c8)** | **No USB detach receiver — stale sessions leak.** `closeSession` is only called from `print()` failure or manual `disconnect()`. The class registers a receiver for *permission* replies (`permissionReceiver`) but **never** registers `ACTION_USB_DEVICE_DETACHED`. After an unplug/replug, the cached `UsbSession` holds a dead `UsbDeviceConnection`; first print fails once (bulkTransfer −1), *then* self-heals because failure closes the session. Result: **one silently lost receipt after every unplug** — the exact class of symptom as the Wi-Fi bug, one occurrence per unplug. | Fix: register a detach receiver that calls `closeSession(deviceKey(device))`. |
| U2 | Medium | **No retry on transient bulk failure.** A single `bulkTransfer` −1 (device briefly busy) kills the session and the print. Kitchen queue retries; **receipts do not** (shared gap with Wi-Fi, see C1 in companion doc). | Fix: one retry after fresh session open; or rely on receipt retry from companion doc. |
| U3 | Low | **`deviceKey()` collision fallback is unstable.** Without serial permission (pre-Q or revoked), key = `usb:vid:pid:<deviceName.hashCode()>`. `deviceName` can change across hubs/OTG adapters → profile stored under old key no longer matches any attached device. `findDeviceByKey` has a VID:PID prefix fallback which mitigates it, but `sessions` map entries under the old key are orphaned until process death. | Fix: on `findDeviceByKey` prefix-match success, migrate (close old session, update nothing in DB — transport-internal only). |

Also verified OK: permission `PendingIntent.FLAG_IMMUTABLE`, Android 13+ `RECEIVER_EXPORTED` flag usage, `RECEIVER_NOT_EXPORTED` for discovery in BT (correct polarity — permission replies must come from the system so EXPORTED is right there).

---

### 1B. Bluetooth — `BluetoothPrinterManager.kt` + `BluetoothPrinterTransport` — **GOOD, with 3 gaps**

Well-built: per-MAC mutexes (connect on printer A never blocks printer B — documented rationale), secure→insecure RFCOMM fallback with 3 attempts + backoff (OEM stack quirk handling), stale-socket liveness probe via 1-byte dummy write, connect outside global mutex, `queryHealth` DLE EOT support, ACL connect/disconnect receiver cleans maps.

Gaps found:

| # | Severity | Issue | Detail |
|---|---|---|---|
| B1 | **✅ FIXED (428a55c8)** | **Liveness probe writes a stray `0x00` byte to the printer.** `connect()`'s socket-health check does `currentStream.write(byteArrayOf(0))`. Most ESC/POS firmwares ignore NUL, but some cheap thermal boards **print a garbage character or enter an error state** on unexpected control bytes. Worse: if a *real* print immediately follows, the printer may have buffered `0x00` mid-stream. The probe should be read-side only (`inputStream.available()` / socket.isClosed checks) or use DLE EOT (already implemented in `queryHealth`!) instead of injecting bytes into the data stream. | Fix: replace dummy-write probe with `socket.isConnected && !socket.isClosed` + optional `queryHealth` DLE EOT (non-fatal). |
| B2 | **✅ FIXED (428a55c8)** | **ACL_DISCONNECTED receiver mutates shared maps without `printerMutex`.** The `connectionReceiver.onReceive` (main thread) removes from `activeSockets`/`outputStreams` and updates `lastConnectedMac`/`_isConnected` directly. `connect()` Phase 1 carefully does map mutation under `printerMutex` — but the receiver does the same operations lock-free. Two writers (receiver vs. connect Phase 1) can race: e.g., receiver removes a socket while connect Phase 1 is mid-removal → `staleSocket` double-close (harmless) or `_connectedDeviceMacs` lost update (visible UI flicker / wrong flush trigger for `KitchenPrintQueueManager` which listens to `connectedDeviceEvents`). | Fix: receiver should only *signal* (e.g., emit to a flow); all map mutation happens in one place under `printerMutex` (a suspend `handleDisconnect(mac)` called from a scope). |
| B3 | Low | **`printBytes()` (no-MAC overload) targets `lastConnectedMac`** — a global "most recent" cursor. Any caller using it in a two-printer setup (customer + kitchen) can route a receipt to the kitchen printer if a kitchen reconnect happened between resolve and write. Current `BluetoothPrinterTransport.print` correctly uses `printBytesTo(mac, …)`, so this is a latent trap, not an active bug. Grep shows no production caller of the 1-arg overload — candidate for `@Deprecated`. | Fix: deprecate/remove or route through profile-bound MAC only. |

Also verified OK: `createBond` polling loop caps at 30×500 ms; discovery receiver unregistered on `stopScan` (no leak); `RECEIVER_NOT_EXPORTED` for ACTION_FOUND (correct); SecurityException guarded on all adapter calls for revoked-permission races.

---

### 1C. Wi-Fi — summarized (full detail in companion doc)

Unbounded single `write()` (soTimeout only bounds reads), silent `runCatching{}.getOrDefault(false)`, no chunking, no retry, receipts never queued. **Wi-Fi is the only transport without chunked writes, per-chunk timeouts, or failure logging** — USB does all three; Bluetooth logs and tears down on error.

**Cross-transport summary table:**

| Property | USB | Bluetooth | Wi-Fi |
|---|---|---|---|
| Chunked writes | ✅ 16 KB loop | ➖ (RFCOMM stream, single write OK — stream handles pacing) | ❌ one giant write |
| Write deadline | ✅ 5s per chunk | ➖ (socket-level) | ❌ soTimeout doesn't bound writes |
| Error logging | ✅ everywhere | ✅ everywhere | ❌ swallowed |
| Connection reuse + liveness | ✅ session cache | ✅ + dummy-write probe (flawed, B1) | ➖ fresh socket per print (fine) |
| Auto-retry | ❌ (U2) | ✅ connectWithRetry (3×) | ❌ |
| Queue on failure | ✅ kitchen queue | ✅ kitchen queue | ✅ kitchen queue, ❌ receipts |
| Detach/ACL cleanup | ❌ no detach receiver (U1) | ✅ ACL receiver (but racy, B2) | ➖ n/a (no persistent conn) |

---

## Part 2 — App-Wide Feature Sweep (all 12 modules)

Method: pattern sweep for the same defect classes found in printing (silent failure, unbounded I/O, scope leaks, state races), then targeted reads of each module's core domain file.

**Pattern-sweep results (whole app):**
- 26 `catch (_)` silent swallows across the app (most are legit close()/cleanup; flagged offenders below)
- 9 leaked/ambient `CoroutineScope(SupervisorJob())` or `GlobalScope` usages (flagged where harmful)
- 1 `Thread.sleep` on an IO coroutine thread (BT health probe — B1 area)

### 2.1 Printing/KDS (see Part 1) — plus:

| # | Severity | Issue | Detail |
|---|---|---|---|
| P1 | **High** | **`KitchenPrintQueueManager` scope is never cancelled.** `@Singleton` with `scope = CoroutineScope(SupervisorJob() + IO)`, `destroy()` exists but nothing calls it in a Hilt app (no `@Dispose` lifecycle). The `init` block launches a `while(isActive) delay(30s)` loop + 2 collectors that live for process lifetime. Acceptable for a true singleton, but `destroy()` is dead code — and if Hilt ever recreates the graph (process restart within same process via `denyCleartext` edge, etc.), duplicate loops stack. | Fix: delete `destroy()` or wire the loop through `.launch` in an injected `@ApplicationScope`. Low urgency. |
| P2 | Medium | **Receipt retry gap** (= companion doc C1, applies to all interfaces). Kitchen tickets survive printer outages via the 30s safety-net loop; customer receipts get one shot everywhere. On USB/BT the failure mode is rarer (chunked/paced writes), but the asymmetry remains: `maybeQueueKitchenRetry` only, no `maybeQueueReceiptRetry`. | Fix: bounded receipt retry (companion doc C1) — benefits all three interfaces at once. |

### 2.2 Billing (`BillRepository.kt`) — **GOOD, no high findings**

Verified solid: terminal-ownership isolation (`isLocallyOwned` defense-in-depth on every mutation — server-imported history is read-only), atomic finalization (`finalizeOnlineBillAtomically` with outcome enum), KOT event ledger with revision numbering and owner-terminal guard mirroring PrintRouter, payment recovery with idempotent `operationId`s, inventory consumption tied to finalize events.

| # | Severity | Issue | Detail |
|---|---|---|---|
| BL1 | Low | **`updateOrderStatus` writes the bill twice + two targeted updates.** `billDao.updateBill(copy)` then `billDao.updateOrderStatus(id, …)` then `billDao.updatePaymentStatus(id, …)` — the latter two are redundant with the `copy()` (they set the same fields, non-atomically). If a sync-pull lands between them, ordering matters; also 3 writes per status change. | Fix: single `@Transaction` DAO method. Cosmetic-to-minor; no observed defect. |
| BL2 | Low | **Date-parse fallbacks swallow to `0L`/`Long.MAX_VALUE`.** `getBillsByDateRange(startDate, endDate)` catches parse errors and returns epoch-0 / MAX — a malformed range silently returns everything or nothing with no log. | Fix: log the parse failure; consider propagating as validation error. |

### 2.3 Sync (`SyncManager.kt`) — **GOOD — the strongest module**

Verified solid: NonCancellable wrapping of the whole push+pull cycle (prevents 409-causing mid-flight aborts — documented rationale), checkpoint committed only after full success (timestamp race fix documented in-line), paged pull with per-page persistence (OOM-safe, 50-page cap), conflict recovery with quarantine path, clock-drift detection (3-min threshold), debounce window for rapid triggers, `hasPendingSync` follow-up run loop.

| # | Severity | Issue | Detail |
|---|---|---|---|
| S1 | Low | **Dead deprecated code still compiled.** `pullMasterSyncPages`/`mergeMasterSyncPages` marked `@Deprecated(HIDDEN)` but still present (~40 lines). | Fix: delete. |
| S2 | Low | **`logWarn` DEBUG branch is inverted-ish.** Debug: logs with throwable; Release: logs message only. Intent was probably "verbose only in debug" but both branches call `Log.w` — release logs still emit (fine), just noting the asymmetry is deliberate-looking but slightly odd. | No action. |

### 2.4 Notifications (`NotificationRepository.kt`) — **2 real findings**

Otherwise well-built: offline read-state queue in prefs with mark-all supersede, server-merge preserving local read flags against REPLACE upserts, 90-day retention mirror, banner cursor dedup.

| # | Severity | Issue | Detail |
|---|---|---|---|
| N1 | **High** | **`GlobalScope` recursive retry can loop forever and outlive the app.** `scheduleTokenRegistrationRetry` launches in `GlobalScope`, retries after 5s, and on failure calls itself again if `shouldKeepRetryingToken` (cap 5 via prefs counter — but the counter is **per-token** and never reset on success path variance; also `registerDeviceToken` early-returns if token matches last registered, so a *changed* server rejection leaves the counter stale). Recursive `GlobalScope.launch` chains with prefs-based caps are unbounded-lifetime work + no backoff growth (fixed 5s). | Fix: replace with WorkManager periodic/expedited work (the app already uses WorkManager for sync), or an injected application scope with exponential backoff. |
| N2 | Medium | **`registerCurrentDeviceTokenInBackground` = `GlobalScope.launch`** around a suspend that hits FCM + network. Process-lifetime leak class; app has `@ApplicationScope` patterns available elsewhere (`KitchenPrintQueueManager` uses its own scope; `PermissionManager` has `ioScope`). | Fix: inject shared app scope. Same fix family as P1. |

### 2.5 Core utils (`Utils.kt`)

| # | Severity | Issue | Detail |
|---|---|---|---|
| CU1 | Medium | **`showAppToast` creates a new `CoroutineScope(SupervisorJob())` per call** (line ~118) and another at line 425. Toast scopes are short (post-delay), but SupervisorJob-per-call means nothing cancels them if the call site's lifecycle dies — a toast can show after screen exit. Harmless visually, but it's the same ambient-scope anti-pattern as N2. | Fix: single shared app-scope toast helper. |

### 2.6 Auth / Staff / Menu / Reports / Settings / Inventory / Payments / Onboarding

Pattern-level scan; no high-severity findings surfaced beyond what's above. Notable *positives* worth preserving during any refactor:

- `PermissionManager` uses its own `ioScope` (same family as P1 — consolidate when fixing N2/CU1).
- `AuthInterceptor`, `KeystoreBackedPreferences`, `LegacyEncryptedMigration` all wrap risky ops in `runCatching` **with logging** — the correct pattern that Wi-Fi transport lacks.
- `PaymentSetValidator`, `BackendErrorParser` use `runCatching` with typed results — good.
- Reports (`ReportGenerator`, `ReportsViewModel`) uses `runCatching` ×2 with error propagation to UI state — good.

### 2.7 Settings save flow (= companion doc Issue 2)

Cross-referenced during this sweep: `saveWifiPrinter`/`saveUsbPrinter` still bypass `_saveProfileLoading` (no button-disable), `saveProfile`/`savePrinterSettingsLocally` have no single-flight guard, navigation-on-success lives in keyed `LaunchedEffect` — full plan in companion doc §E. **Nothing new found beyond the companion doc.**

---

## Part 3 — Consolidated Fix Plan (all interfaces + all features)

Ordered by impact. **All printer fixes are internal to one transport/class — the `PrinterTransport` interface and cross-interface behavior are untouched, per the constraint.**

### Tier 1 — Ship first (user-visible failure classes)

| ID | Fix | Files | Interfaces affected | Risk |
|---|---|---|---|---|
| W1 | Wi-Fi chunked writes + real write deadline + error logging (+ retry) | `PrinterTransport.kt` (WifiPrinterTransport only) | Wi-Fi | Low — pattern already proven in USB transport |
| U1 | USB detach receiver → `closeSession(deviceKey)` | `UsbPrinterTransport.kt` | USB | Low — additive receiver |
| B1 | Replace dummy-write liveness probe with DLE EOT / read-side check | `BluetoothPrinterManager.kt` | Bluetooth | Low — `queryHealth` already implements the safe version |
| E1 | Settings single-flight guard + consistent loading state + navigate-once | `SettingsViewModel.kt`, `SettingsScreen.kt` | none (UI) | Low |

### Tier 2 — Resilience

| ID | Fix | Files | Interfaces affected |
|---|---|---|---|
| C1 | Bounded receipt retry (mirrors kitchen queue, capped) — fixes all 3 interfaces at once | `PrintRouter.kt` | Wi-Fi + USB + BT |
| U2 | USB: one retry after fresh session open on bulk failure | `UsbPrinterTransport.kt` | USB |
| B2 | BT ACL receiver: signal-only, mutate maps under `printerMutex` | `BluetoothPrinterManager.kt` | Bluetooth |
| N1 | FCM token retry → WorkManager / app-scope with backoff | `NotificationRepository.kt` | none |
| N2 + CU1 + P1 | Consolidate ambient scopes into one injected `@ApplicationScope` | `NotificationRepository.kt`, `Utils.kt`, `KitchenPrintQueueManager.kt`, `PermissionManager.kt` | none |

### Tier 3 — Hygiene

| ID | Fix | Files |
|---|---|---|
| B3 | Deprecate `printBytes()` lastConnectedMac overload | `BluetoothPrinterManager.kt` |
| U3 | USB key migration on VID:PID prefix match | `UsbPrinterTransport.kt` |
| BL1 | Single-transaction status update | `BillRepository.kt` + `BillDao` |
| BL2 | Log date-parse fallbacks | `BillRepository.kt` |
| S1 | Delete dead deprecated sync code | `SyncManager.kt` |

### Explicitly out of scope / no action
- GBK encoding (works for current codepages; revisit only if non-Latin garbage is reported)
- Bluetooth RFCOMM retry logic (correct as-is)
- Sync engine core (correct as-is — best-in-repo module)
- USB chunk size (16 KB is fine)

---

## Verification checklist (post-implementation)

**Per-interface regression (all must pass):**
- [ ] Bluetooth: print receipt + KOT, reconnect-after-unplug, airplane-mode-off recovery
- [ ] USB: print receipt + KOT, **unplug → replug → print immediately** (validates U1), permission revoke/regrant
- [ ] Wi-Fi: print receipt + KOT with logo enabled, 80mm + 58mm, congested-network large bill (validates W1)
- [ ] Manual receipt reprint falls back to PDF when no printer configured (PrintCoordinator path unchanged)

**Cross-cutting:**
- [ ] Kitchen queue flush still fires on BT connect + 30s safety net (P1 refactor must not change timing)
- [ ] Config screen Save: single click saves + returns; double-tap produces one save (E1)
- [ ] Sync: no behavioral change (S1 is deletion of dead code only)
- [ ] Existing tests pass: `WifiPrinterTransportTest`, `KitchenPrintQueueManagerTest`, `PrintRouterTest`, `BillingLogicTest`, `MasterSyncProcessorImageMergeTest`, `SyncManagerTest`
