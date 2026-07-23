# Staff

## Prototype route

`/proto/owner/staff`

## Existing production route

`/business/staff`

## Layout anatomy

- `kb-page-header title="Staff"` with primary "Add staff" action.
- Tabs: Active / Inactive.
- `kb-data-table` (desktop) / mobile card list.
- `kb-drawer` for add / edit staff.
- `kb-confirmation-dialog` for deactivate.
- `kb-dialog` for one-time temp password reveal (using `kb-copy-button`).

## Shared components required

All shell primitives, `kb-data-table`, `kb-mobile-data-card`, `kb-status-badge`, `kb-drawer`, `kb-dialog`, `kb-confirmation-dialog`, `kb-form-field`, `kb-button`, `kb-copy-button`, `kb-empty-state`, `kb-error-state`, `kb-skeleton`, `kb-toast`.

## Unique page components

- `KbStaffFormComponent` — name, phone, email, role; validation mirrors existing rules.
- `KbTempPasswordDialogComponent` — one-time reveal of a temporary password with a copy action and an explicit dismiss.

## Desktop layout

- Tabs above the table.
- Columns: Name, Phone, Email, Role, Status, ⋯.

## Tablet layout

- Reduce columns (drop email or phone into the row's expanded state).

## Mobile layout

- Tabs stack; table becomes mobile card list.
- Add staff CTA becomes a full-width button below the tabs.

## Loading / empty / error

- Loading: 5 skeleton rows.
- Empty (Active tab): "No active staff yet" + Add CTA.
- Empty (Inactive tab): "No deactivated staff".
- Error: retry.

## Validation state

Preserve existing validators exactly: name (min length), phone (regex / country rules), email (format), role (enum).

## Success state

- Add: drawer closes; if backend returns a temp password, open `KbTempPasswordDialogComponent`; otherwise toast "Staff added".
- Edit: drawer closes; toast "Staff updated".
- Deactivate: row moves to Inactive tab; toast.

## Sensitive actions

- **Deactivate staff**: `kb-confirmation-dialog` with staff name, consequence "This person will lose access immediately. You can reactivate them later.", `confirmLabel="Deactivate staff"`, tone `danger`. Prevent double-submit.

## Temp-password dialog rules

- Show password once, in `--kb-font-mono`.
- `kb-copy-button` copies the value.
- Dialog explicitly warns "This password will not be shown again."
- On close, clear the password from component state — do NOT log it, do NOT keep it in a service.
- Never persist to `localStorage`, `sessionStorage`, or app-wide state.

## Accessibility requirements

- Tabs: real `role="tablist"` with tab buttons and `aria-controls` panels.
- Deactivate dialog: `role="alertdialog"`, focus on Cancel by default.
- Copy button announces via live region ("Password copied").
- Password reveal element uses `aria-live="polite"` so screen-reader users hear it once when shown.

## Existing functionality that must be retained

- Staff list / add / edit / deactivate endpoints unchanged.
- Existing name / phone / email / role validation.
- Existing password-generation / rotation behavior — the UI must not change its source of truth.
- Existing role restriction (OWNER).

## Visual acceptance criteria

- Role uses `kb-status-badge` with tone mapping: owner=primary, manager=info, cashier=neutral.
- Active / Inactive tabs visually distinct; selected tab has `--kb-color-primary` bottom accent.
- No gradients, no serif.
