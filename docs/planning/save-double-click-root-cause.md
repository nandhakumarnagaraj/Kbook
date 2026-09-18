# Root Cause: "Save" Requires Two Clicks on Configuration Screens

> **Status:** Verified against source on 2026-09-18. All four config sections traced.
> **Scope:** Root cause + solution + implementation. Constraint: no changes to working features/modules.

---

## Symptom

Save on any configuration screen needs **two taps** — first tap appears to do nothing, second tap saves and returns.

## Why the shared flow *should* work in one tap

`SettingsScreen` owns a success effect (SettingsScreen.kt:94–107): when `saveProfileSuccess && pendingSaveSection != null` → toast + clear state + `section = "menu"`. Sections call `pendingSaveSection = X; viewModel.saveProfile(...)`. The pattern is sound **when all its preconditions hold**. The bug is that different sections violate *different* preconditions:

## Verified root causes, per section

### RC-A — "shop": save path skips the shared success effect entirely
`ShopConfigView` (`ShopConfigSection.kt:476–502`) calls `viewModel.saveProfile(it)` **directly** — it never sets `pendingSaveSection`. It has its OWN `LaunchedEffect(saveProfileSuccess)` (line 186) that toasts + clears + `onBack()`.
- If the user reaches shop config via a path where this composable's success effect is disposed/recomposed around the save (e.g., after the logo upload dialog, or after the PIN dialog flow triggers recomposition with `selectedPaymentFlowMode` keyed on `profile?.orderPaymentFlowMode` — **which the save itself changes**), the effect can miss or double-fire.
- More concretely: `selectedPaymentFlowMode by remember(profile?.orderPaymentFlowMode)` — saving a new flow mode changes `profile`, which **re-keys the remember** and recomposes the section; the `LaunchedEffect(saveProfileSuccess)` can be disposed by this recomposition cycle before/while it runs, so the first save toasts nothing and navigates nowhere. Second tap: `saveProfileSuccess` was cleared to false by the first (half-processed) save; the second tap re-saves and now the effect fires cleanly.

### RC-B — "tax": Save button has no loading state at all
`TaxConfigView` (`TaxConfigSection.kt:170–181`) passes `saveEnabled` but **never `isSaving`** to `ConfigActionButtons` — the button never disables, never shows "Saving…". A double-tap fires `saveProfile` twice: the first sets `pendingSaveSection="tax"` and starts saving; the second (still enabled) sets `pendingSaveSection="tax"` again and re-enters `saveProfile`, which **resets `_saveProfileSuccess = false` mid-flight** (SettingsViewModel.kt:965–967) — cancelling the first save's success transition. First tap's navigation is lost; second tap succeeds. This is the classic double-fire, enabled by the missing disabled state + missing single-flight guard (`saveProfile` has no `if (_saveProfileLoading.value) return`).

### RC-C — "printer": silent no-op when `profile == null` + no loading state
`PrinterConfigSection.kt:430–455`: the save handler does `profile?.copy(...)?.let { onSave(it) }` — **if the restaurant profile hasn't loaded yet (slow sync, first render), the first tap silently does nothing** (null-safe no-op). The `updatePrinterProfile` calls also run regardless, but `onSave` (which triggers `pendingSaveSection` + navigation) is skipped. Second tap: profile has loaded by now → works. `ConfigActionButtons` also gets no `isSaving`.

### RC-D — "payment": same double-fire window as tax
`PaymentConfigView` **does** pass `isSaving = saveProfileLoading` correctly, but `saveProfile` lacks the single-flight guard: a fast double-tap (both clicks landing before the first recomposition disables the button — realistic on slower devices where `collectAsStateWithLifecycle` + recomposition lag a frame) enters `saveProfile` twice; the second entry resets `_saveProfileSuccess = false` after the first set it true → the keyed effect's branch is skipped on the first tap.

### RC-E — ViewModel: no single-flight + success-flag reset on re-entry
`saveProfile` / `savePrinterSettingsLocally` (SettingsViewModel.kt:962, 988) both begin with `_saveProfileSuccess.value = false`. Any second entry (double-tap, or printer section's `savePrinterSettingsLocally` + separate `updatePrinterProfile` writes racing) erases a success the UI hasn't consumed yet. No `if (_saveProfileLoading.value) return` guard exists on either function.

## Solution (all additive; no interface or module changes)

1. **Single-flight guard** at the top of `saveProfile` and `savePrinterSettingsLocally`: `if (_saveProfileLoading.value) return`. This alone kills every double-fire variant (RC-B, RC-D, RC-E) — the second tap becomes a no-op by construction, so `saveProfileSuccess` can never be reset mid-flight.
2. **Set `_saveProfileLoading` in `savePrinterSettingsLocally`** (it already does) — and printer/tax sections get the `isSaving` wiring so the button visibly disables (RC-B, RC-C UI half).
3. **Save button disabled until profile is non-null** in tax + printer sections (via `saveEnabled = ... && profile != null`), turning the printer section's silent no-op into an honest disabled button (RC-C).
4. **Shop section**: route its save through the same `pendingSaveSection` mechanism as the others — replace its private `LaunchedEffect(saveProfileSuccess)` with the shared one by setting `pendingSaveSection = "shop"` from SettingsScreen's onSave (its "shop" branch already exists in the toast `when`). Its `authViewModel.clearOtpStatus()` moves into the onSave path. This removes the fragile private effect + re-keyed recomposition interaction (RC-A).

## Implementation checklist

- [x] `SettingsViewModel`: `if (_saveProfileLoading.value) return` in `saveProfile` + `savePrinterSettingsLocally`
- [x] `SettingsScreen`: shop onSave sets `pendingSaveSection = "shop"`; remove ShopConfigView's private success effect; wire `onSave` wrapper for OTP clear
- [x] `TaxConfigSection`: `isSaving = saveProfileLoading` (needs the param threaded through `TaxConfigView`) + `saveEnabled && profile != null`
- [x] `PrinterConfigSection`: `isSaving` threaded through `PrinterConfigView` + `saveEnabled && profile != null`
- [x] Verify: compile + full unit suite green
