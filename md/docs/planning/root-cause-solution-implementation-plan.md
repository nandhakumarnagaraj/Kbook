# Root Causes, Solutions & Implementation Plan

> **Status:** Final plan, 2026-09-18. No code changes made yet.
> **Sources:** `printing-and-save-fix-plan.md` (Wi-Fi + save) and `all-interfaces-and-features-audit.md` (USB/BT/all features), both verified against source.
> **Prime directive:** Zero behavior change for working features. Every phase ships independently and is independently revertible.

---

## The Safety Contract (applies to every change below)

These are guarantees, not suggestions:

1. **No interface signature changes.** `PrinterTransport.print(profile, bytes): Boolean` stays exactly as-is. Bluetooth, USB, and Wi-Fi dispatch routing (`PrinterTransportDispatcher`) is untouched.
2. **Additive-only edits.** New code paths (receivers, retries, guards) are added alongside old ones; nothing is removed or reordered in existing happy paths.
3. **Each phase compiles, passes the existing test suite, and ships alone.** If any phase regresses, revert that phase without touching the others.
4. **Feature freeze list** — these work today and must be byte-for-byte identical in behavior after all phases:
   - Bluetooth connect → print → reconnect-after-unplug flow (incl. secure→insecure RFCOMM retry)
   - USB permission prompt → session reuse → chunked bulk write flow
   - Test print from config screen (all 3 interfaces)
   - Kitchen queue: enqueue → claim → 30s safety-net flush → markPrinted
   - PDF fallback when no printer configured
   - Sync engine (NonCancellable cycle, checkpoint-on-success, conflict quarantine)
   - KOT event ledger (NEW/ADD/VOID, terminal-ownership guard)
   - Payment finalization + recovery (atomic, idempotent operationIds)
   - Notifications offline read-state queue
   - All navigation flows outside the Settings save bug

---

## Part 1 — Root Causes (verified, with receipts)

### RC1 — Wi-Fi live prints silently fail (test prints work)
**File:** `PrinterTransport.kt` → `WifiPrinterTransport` (lines 37–57)
- Test payload: ~45 bytes US-ASCII, completes instantly. Live payload: 5–25 KB (GBK text + logo bitmap + full bill).
- `socket.soTimeout = 8_000` **does not bound writes** — only reads. The single unbounded `output.write(bytes)` blocks forever when a thermal printer's small input buffer floods on a large burst. The IO thread hangs; the router coroutine never completes; no error, no retry.
- `runCatching{}.getOrDefault(false)` swallows every exception — zero diagnostics.
- Receipts get exactly one attempt (`PrintRouter` only queues KITCHEN targets on failure).

### RC2 — Double-click Save on config screens
**Files:** `SettingsViewModel.kt`, `SettingsScreen.kt`
- `saveWifiPrinter`/`saveUsbPrinter` never set `_saveProfileLoading` → Save button never disables.
- `saveProfile`/`savePrinterSettingsLocally` have no single-flight guard → double-tap = two coroutines.
- Navigation-on-success lives in `LaunchedEffect(saveProfileSuccess, pendingSaveSection)` — if `pendingSaveSection` (a plain `remember`) is reset before the effect keys settle, the toast+navigate branch is skipped → the save "did nothing" → second click.

### RC3 — One lost receipt after every USB unplug
**File:** `UsbPrinterTransport.kt`
- Sessions cached in a map, but **no `ACTION_USB_DEVICE_DETACHED` receiver**. After unplug/replug, the cached session holds a dead `UsbDeviceConnection` → first `bulkTransfer` returns −1 → session closes → **that print is lost**, the next one works. Self-healing, but one lost print per unplug.

### RC4 — Bluetooth probe injects a stray byte into the print stream
**File:** `BluetoothPrinterManager.kt` (`connect()` liveness check)
- Socket health check does `stream.write(byteArrayOf(0))` — a NUL written into the printer's data stream. Most firmwares ignore it; cheap boards print garbage or fault. The safe alternative already exists in the same file (`queryHealth` uses DLE EOT read-side).

### RC5 — Ambient coroutine leaks (app-wide)
- `NotificationRepository`: recursive `GlobalScope` FCM retry, fixed 5s, prefs-capped, outlives everything.
- `Utils.showAppToast`, `PermissionManager.ioScope`, `KitchenPrintQueueManager.scope`: per-call/unmanaged scopes of the same family. No user-visible failure today — hygiene debt.

### Not root causes (verified healthy — do not touch)
Sync engine, billing ownership isolation, KOT ledger, payment validation, RFCOMM retry logic, USB chunking model, GBK encoding (for current codepages).

---

## Part 2 — Solutions (per root cause)

| RC | Solution | Key design decision |
|---|---|---|
| RC1 | Chunked writes (4 KB) + `withTimeout` around the write block + `Log.w` on failure + single 300 ms retry — all inside `WifiPrinterTransport` | Copy the proven USB pattern. Same `Boolean` contract. Fresh-socket-per-print stays (no pooling). |
| RC2 | `if (_saveProfileLoading.value) return` guard in all 4 save fns; set the flag in `saveWifiPrinter`/`saveUsbPrinter` too; keep navigation in the existing `LaunchedEffect` but stop resetting `pendingSaveSection` from two places | Smallest diff: the effect logic is actually sound once double-entry is impossible and the flag is consistent. |
| RC3 | Register a detach receiver in `init`-equivalent path calling `closeSession(deviceKey(device))` | Mirrors the existing `permissionReceiver` registration pattern already in the file. |
| RC4 | Replace dummy-write with `socket.isConnected && !socket.isClosed` check; optional non-fatal `queryHealth` DLE EOT | Read-side only; no bytes injected into the data stream. |
| RC5 | One injected `@ApplicationScope` CoroutineScope; migrate GlobalScope/ambient scopes onto it; FCM retry moves to WorkManager (backoff-aware) | App already uses WorkManager for sync — reuse `enqueueMasterSyncOnce`-style wiring. |

---

## Part 3 — Implementation Plan (4 phases, each shippable alone)

### Phase 1 — Wi-Fi transport fix (RC1) — *the bug you reported*
**Files:** `PrinterTransport.kt` (WifiPrinterTransport body only)
**Est:** ~40 lines changed, 0 signature changes

1. Add `private const val CHUNK_SIZE = 4096` and `WRITE_DEADLINE_MS = 10_000`.
2. Wrap the whole write in `withTimeout(WRITE_DEADLINE_MS) { ... }`.
3. Loop: `while (offset < bytes.size) { out.write(chunk); out.flush(); offset += len }`.
4. Replace `.getOrDefault(false)` with explicit `catch (e: Exception) { Log.w(TAG, "WiFi print to $host:$port failed (${e.javaClass.simpleName}: ${e.message})", e); false }`.
5. On failure: one retry after 300 ms (fresh socket), attempt logged.
6. Keep `CONNECT_TIMEOUT_MS = 5_000` as-is.

**Verify:** unit test chunk loop with a fake stream (new test file); manual: large bill + logo over congested Wi-Fi; **test print unchanged**; BT/USB untouched.

### Phase 2 — USB detach + Bluetooth probe (RC3, RC4)
**Files:** `UsbPrinterTransport.kt`, `BluetoothPrinterManager.kt`
**Est:** ~30 lines USB, ~10 lines BT

**USB:**
1. Add `detachReceiver` (same shape as existing `permissionReceiver`), filter `UsbManager.ACTION_USB_DEVICE_DETACHED`.
2. In `onReceive`: `closeSession(deviceKey(device))`. Register alongside `ensureReceiverRegistered()` (or in constructor).
3. No behavior change while a device stays attached — the receiver only fires on detach.

**Bluetooth:**
1. In `connect()`'s liveness check, replace the dummy-write block with: `currentSocket.isConnected && !currentSocket.isClosed` → treat as alive.
2. Do NOT wire `queryHealth` into the connect path yet (it adds ~600 ms latency); only swap the probe. DLE EOT integration is a later enhancement if paper/cover detection is wanted.

**Verify:** USB unplug→replug→print works **on the first try**; BT receipt has no leading garbage character; reconnect flow unchanged; `KitchenPrintQueueManagerTest` passes (queue flush listens to BT events — unaffected).

### Phase 3 — Save single-flight + consistent loading (RC2)
**Files:** `SettingsViewModel.kt`, (screen untouched or minimal)
**Est:** ~15 lines

1. Top of `saveProfile`, `savePrinterSettingsLocally`: `if (_saveProfileLoading.value) return`.
2. In `saveWifiPrinter`, `saveUsbPrinter`: set `_saveProfileLoading.value = true` at coroutine start, `false` in `finally` (same as `saveProfile` does).
3. Leave the `LaunchedEffect(saveProfileSuccess, pendingSaveSection)` navigation as-is — with double-entry blocked, the effect fires exactly once per save.

**Verify:** single tap saves + toasts + returns; rapid double-tap produces exactly one save (watch Room/logcat); every section (shop, payment, tax, printer) unchanged otherwise.

### Phase 4 — Scope hygiene (RC5) — *optional, last, lowest risk appetite required*
**Files:** `NotificationRepository.kt`, `Utils.kt`, `PermissionManager.kt`, DI module
**Est:** ~50 lines

1. Provide `@ApplicationScope CoroutineScope(SupervisorJob() + Dispatchers.IO)` in the existing Hilt module.
2. Replace `GlobalScope.launch` ×3 in `NotificationRepository` with injected scope.
3. FCM token retry → WorkManager expedited work with exponential backoff (reuse app's existing WorkManager setup) — preserves current retry semantics (cap 5) but process-safe.
4. `showAppToast` uses injected scope (it's in `Utils.kt` object — if DI is awkward there, a single lazily-held scope object is acceptable).
5. Do NOT touch `KitchenPrintQueueManager.scope` lifecycle (works; changing it risks the queue timing guarantees).

**Verify:** FCM token registration retry still fires on airplane-mode-on→off; notifications still arrive; toasts still show. This phase can be deferred indefinitely with zero user impact.

---

## Rollback & regression gates

| Phase | Gate before merge | Rollback unit |
|---|---|---|
| 1 | New chunk/timeout unit test green; `WifiPrinterTransportTest` green; manual large-bill print | Revert `PrinterTransport.kt` only |
| 2 | USB replug test; BT garbage test; full existing printing test suite | Revert 2 files |
| 3 | Manual double-tap test; all settings tests | Revert `SettingsViewModel.kt` only |
| 4 | Token retry test; notifications smoke test | Revert 4 files (or skip entirely) |

**Full suite per phase:** `WifiPrinterTransportTest`, `KitchenPrintQueueManagerTest`, `PrintRouterTest`, `BillingLogicTest`, `SyncManagerTest`, `MasterSyncProcessorImageMergeTest`, `PaymentSetValidatorTest`, `SessionManagerTest`.

---

## Sequencing recommendation

**Phase 1 first** (your actual reported bug, smallest blast radius) → **Phase 3 second** (5-minute fix, high annoyance value) → **Phase 2 third** (needs a physical printer on the desk to verify honestly) → **Phase 4 whenever** (pure hygiene).
