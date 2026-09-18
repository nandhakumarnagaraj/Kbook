# Root-Cause Analysis & Fix Plan: Wi-Fi Printing + Double-Click Save

> **Status:** Verified against source on 2026-09-18. Analysis only — no code changes made.
> **Scope constraint:** Fixes must not change existing interface types (Bluetooth, USB) or affect other working features. All proposed changes are isolated to the Wi-Fi transport internals and the settings save flow.

---

## Issue 1: Wi-Fi receipt/KOT printing fails in live operations while test prints work

### Symptoms

- Test prints requested from the printer configuration screen print fine.
- Live customer receipts and KOTs during billing: nothing prints, no error shown.

### The two payload paths (core differential)

Both paths use **the same transport, the same stored profile, the same socket code** — only the payload differs.

**Test print** — `SettingsViewModel.testPrint(role)` (`Android/app/src/main/java/com/khanabook/lite/pos/feature/settings/viewmodel/SettingsViewModel.kt`, lines 601–633):

- Loads the stored profile by role.
- Builds ~45 bytes of US-ASCII: `ESC @` + `ESC a 1` + `"KHANABOOK\n{ROLE} PRINTER TEST OK\n"` + dashes + `GS V B 0` (cut).
- `printerTransport.print(printer, testData)` → same dispatcher, same socket code as live.
- Reports `PrinterUiEvent.TestPrintSent` / `TestPrintFailed`.

**Live receipt** — `PrintRouter.printBill` → `InvoiceFormatter.formatForThermalPrinter` (`Android/app/src/main/java/com/khanabook/lite/pos/feature/billing/domain/InvoiceFormatter.kt`, line 97+):

- Every text string encoded with `Charset.forName("GBK")` (line 115) — not US-ASCII.
- Logo bitmap embedded when `includeLogoInPrint` is set: `loadLogoBitmap` (local disk or Coil network load, synchronously before the socket write) → `decodeBitmapToESC_POS(bitmap, targetWidth)` where targetWidth is 384 px (80 mm) or 256 px (58 mm).
- Full bill content: itemized table with wrapping, totals, GST/VAT, QR section. **Real payload ≈ 5–25 KB vs. the 45-byte test strip.**

**Live KOT** — same router, `KitchenTicketFormatter.format` (also GBK-encoded), with the retry queue path in `KitchenPrintQueueManager.flushPendingForPrinter`.

### Transport root cause — `WifiPrinterTransport` (`PrinterTransport.kt`, lines 37–57)

```kotlin
runCatching {
    Socket().use { socket ->
        socket.connect(InetSocketAddress(host, profile.port), CONNECT_TIMEOUT_MS) // 5_000
        socket.soTimeout = WRITE_TIMEOUT_MS                                        // 8_000
        socket.getOutputStream().use { output ->
            output.write(bytes)   // ONE unbounded write
            output.flush()
        }
    }
    true
}.getOrDefault(false)
```

Two code-provable defects, both invisible to the user:

1. **`soTimeout` only bounds reads.** The single `output.write(bytes)` has **no real deadline**. A printer that accepts the TCP connection but stalls mid-payload (thermal printers have small input buffers; a 10–25 KB logo + bill burst floods them) makes `write()` block indefinitely. The `Dispatchers.IO` thread hangs, the router coroutine never completes, and the live print silently "does nothing." The 45-byte test strip finishes instantly — before any stall can occur. **This is the strongest code-level match for the exact symptom (test works, live fails).**

2. **`runCatching{}.getOrDefault(false)` swallows everything** — exception class, message, and half-written state. Zero diagnostics. The customer-receipt branch in `PrintRouter` then just records `errorMsg = "print failed"` into a failures list (surfaced only as a toast via `_printResults`).

### Resilience gap — `PrintRouter.printBill`

- **Customer receipts have no retry and no queue.** Only `KITCHEN` targets get `maybeQueueKitchenRetry` (AUTO mode). A failed receipt is a dead end with a toast.
- Kitchen path is resilient by comparison: `KitchenPrintQueueManager` claims/queues/retries with a 30s safety-net flush.
- Minor: the legacy fallback profile built in `resolveTargets` (when no stored profiles exist) carries only `macAddress` and defaults `connectionType` to BLUETOOTH — Wi-Fi shops migrating from the legacy path should be aware, but it only applies when no stored profiles exist.

### Checklist mapping (field verification → code answers)

| Field check | What the code shows |
|---|---|
| 1. Print a saved live bill from the test tool | Not possible today — test print sends only the canned 45-byte strip. Fix B2 adds this. |
| 2. `Test-NetConnection <printer-ip> -Port 9100` from billing machine | Nothing in the code probes reachability at save- or print-time. Fix B4 adds a save-time probe. |
| 3. Inspect print queue / service | Receipts have no queue at all (only kitchen does) — fix C1 adds bounded retry. |
| 4. Payload consistency | The differential is real and confirmed: ~45 B US-ASCII vs. 5–25 KB GBK + bitmap. |

---

## Issue 2: Config screens' "Save" needs two clicks to save and navigate back

### Code evidence (Android settings flow)

1. **Wi-Fi/USB printer saves never enter the saving state.** `saveWifiPrinter` (`SettingsViewModel.kt:779`) and `saveUsbPrinter` use `_btConnectResult` and never set `_saveProfileLoading`. The Save button's disabled state is wired to `isSaving = saveProfileLoading` (`SettingsSharedComponents.kt` `ConfigActionButtons`, lines 189–190) — so these saves show no disabled/saving state and can be double-fired.

2. **No single-flight guard.** `saveProfile` (line 962) and `savePrinterSettingsLocally` (line 988) have no `if (_saveProfileLoading.value) return` at entry — a second tap launches a second coroutine while the first is in flight.

3. **Navigation-on-success lives in a keyed UI effect.** `SettingsScreen.kt:94-107`:
   ```kotlin
   LaunchedEffect(saveProfileSuccess, pendingSaveSection) {
       val savedSection = pendingSaveSection
       if (saveProfileSuccess && savedSection != null) {
           KhanaToast.show(message, ToastKind.Success)
           viewModel.clearSaveProfileState()
           pendingSaveSection = null
           section = "menu"
       }
   }
   ```
   `pendingSaveSection` is a plain `remember` variable in the UI. Any recomposition ordering where it is rewritten/reset before the effect keys settle → the toast + navigate branch is skipped → the save appears to have done nothing → the operator taps Save again. This is the "first click settles state, second click acts" signature.

4. **Stale dirty-state risk.** `ShopConfigSection` keeps an unsaved-changes `BackHandler` (`ShopConfigSection.kt:145-163`). If `isDirty` is not reset synchronously on successful save, the first back/section-nav after saving pops the "Unsaved Changes" dialog instead of leaving — requiring a second click to actually return. *(Verify the save reset in ShopConfigSection 460–510 before changing.)*

---

## Fix Plan

Ordered by leverage. **B and E are the highest-priority items.** Everything is additive; the `PrinterTransport` interface signature (`suspend fun print(profile, bytes): Boolean`) and the Bluetooth/USB transports are untouched.

### A. Diagnose first (zero behavior change)

1. **Error propagation in `WifiPrinterTransport`** — Replace `runCatching{}.getOrDefault(false)` with a `runCatching` that logs the exception (`Log.w("WifiPrinterTransport", "print to ${host}:${port} failed", e)`), still returning `Boolean`. Answers "why does test pass and live fail" with hard logcat data.
2. **Payload-parity test print** — In `testPrint(role)`, after the canned strip, also build and print a real payload from the most recent saved bill (`billDao` latest → `InvoiceFormatter.formatForThermalPrinter` with the target's `paperSize`/`includeLogo`). The config screen then reproduces checklist item 1 on demand — no device needed to prove payload-vs-network.

### B. Fix the Wi-Fi transport so large payloads survive (isolated to `WifiPrinterTransport`)

3. **Real write deadline** — Bound the whole write operation (e.g., wrap in `withTimeout`, or use a socket write deadline mechanism), instead of relying on `soTimeout` which only bounds reads.
4. **Chunked writes** — Write the payload in 1–4 KB chunks with `flush()` between chunks so the printer's input buffer isn't flooded in one burst. This is the single most likely live-only failure.
5. **One-shot retry (optional)** — A single retry after ~300 ms for Wi-Fi inside `WifiPrinterTransport` only. Still `Boolean` in, `Boolean` out.

### C. Receipt resilience (additive)

6. **Bounded receipt retry in `PrintRouter`** — One capped retry for `CUSTOMER` targets that fail (mirroring the kitchen queue but simpler), keeping `_printResults` emission so existing toast/reprint UI still works. `KITCHEN` queue behavior unchanged.

### D. Wi-Fi save-time reachability probe (config UX only)

7. **Probe on save** — In `saveWifiPrinter`, after persisting, fire a non-blocking probe (short ASCII strip through `WifiPrinterTransport`) using the new settings and surface success/warn in the dialog. Does not block save, does not affect BT/USB.

### E. UI single-flight + navigate-once (Android settings)

8. **Single-flight guard** — Add `if (_saveProfileLoading.value) return` at the top of `saveProfile`, `savePrinterSettingsLocally`, `saveWifiPrinter`, `saveUsbPrinter`; set `_saveProfileLoading = true` in the Wi-Fi/USB saves too (so the Save button disables consistently).
9. **Navigate in the success path** — Move navigate-to-`"menu"` out of the keyed `LaunchedEffect` into the save success flow (e.g., let the ViewModel own `pendingSaveSection`, or invoke a navigation callback in the success branch), and reset `isDirty` in that same branch so the unsaved-changes `BackHandler` can never intercept the return.

### F. Web-admin (Angular; equivalent of the useRef idiom)

10. Per the earlier exploration, `business-settings-page` stays on-page after save and modals close on success. Verify each save handler begins with a submitting guard (`if (this.submitting()) return;`) and that navigation (where applicable) happens sequentially inside the success `next:` callback — not after a state flag the modal clears independently.

### Explicitly out of scope

- **GBK text encoding** — Works for ASCII printer codepages; only flag if live bills print "boxes" for Devanagari/Gujarati shop names. (Separate issue if reported.)
- **Legacy fallback profile routing** (`PrintRouter.kt:270-280`) — Defaults to Bluetooth; only applies when no stored profiles exist.

---

## Verification checklist (post-implementation)

- [ ] Test print still works on Bluetooth, USB, and Wi-Fi (all three transports untouched behaviorally for small payloads).
- [ ] Live receipt + KOT print on Wi-Fi with logo enabled, 80mm and 58mm paper sizes.
- [ ] Simulated stall: large payload (logo + 30+ items) on a congested network completes or times out with a **logged error** instead of hanging forever.
- [ ] Config screen Save: single tap saves, toasts, and returns to menu on the first click; rapid double-tap does not produce duplicate saves.
- [ ] `WifiPrinterTransportTest`, `KitchenPrintQueueManagerTest`, `PrintRouterTest` still pass; new unit test for chunked write + timeout behavior.
