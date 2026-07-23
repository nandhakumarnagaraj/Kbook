# KBOOK_ADMIN businesses

## Prototype route

`/proto/admin/businesses`

## Existing production route

`/admin/businesses`

## Layout anatomy

- `kb-page-header title="Businesses"`.
- `kb-filter-toolbar`: search, status filter chips (all / active / pending / suspended), plan filter if the existing app has one.
- `kb-data-table` of businesses (desktop) / mobile card list.
- `kb-drawer` for business detail.
- `kb-confirmation-dialog`s for activate / suspend.

## Shared components required

All shell primitives, `kb-filter-toolbar`, `kb-data-table`, `kb-mobile-data-card`, `kb-status-badge`, `kb-currency`, `kb-drawer`, `kb-confirmation-dialog`, `kb-form-field`, `kb-button`, `kb-empty-state`, `kb-error-state`, `kb-skeleton`, `kb-toast`.

## Unique page components

- `KbBusinessDetailPanelComponent` — projected into the drawer; sections: profile, owner contact, plan, terminal count, recent activity, danger zone.

## Desktop layout

- Toolbar row + table.
- Columns: Business name, Owner, Plan, Terminals, Status, Joined, ⋯.

## Tablet layout

- Reduced column set (drop Plan or Joined).

## Mobile layout

- Table becomes mobile card list; drawer full-width.

## Loading / empty / error

- Loading: 8 skeleton rows.
- Empty: "No businesses match your filters" + clear filters CTA.
- Error: retry.

## Validation state

- Suspend reason required, min 20 chars — visible to the restaurant per existing policy. Preserve existing validators.
- Activate: no free-text validation; optional note.

## Success state

- Activate: row status becomes `active`; toast "Business activated".
- Suspend: row status becomes `suspended`; toast; reason recorded per existing backend behavior.

## Sensitive actions

- **Activate business**: `kb-confirmation-dialog title="Activate {name}"`, consequence "The business regains access to the platform.", `confirmLabel="Activate business"`, tone `default`.
- **Suspend business**: `kb-confirmation-dialog title="Suspend {name}"`, consequence "The business immediately loses access. The reason you provide is shown to their team.", requires a reason `<textarea>` in the projected slot, `confirmLabel="Suspend business"`, tone `danger`, `requireTypedConfirmation="{name}"` to reduce accidental suspensions of the wrong business.

Prevent double-submit in both cases.

## Accessibility requirements

- Suspend dialog uses `role="alertdialog"`, default focus on Cancel.
- Typed-confirmation input is a real `kb-form-field` with an explicit label.
- Reason textarea is properly labelled and error-associated via `aria-describedby`.
- Announce activate/suspend success via a polite live region + toast.

## Existing functionality that must be retained

- Businesses list / detail / activate / suspend endpoints unchanged.
- Existing search / filter query parameters.
- Existing reason-visibility policy.
- Existing role restriction (KBOOK_ADMIN).

## Visual acceptance criteria

- Neutral, executive tone — no gradients, no serif.
- Status badge tones: active=success, pending=warning, suspended=danger.
- Drawer detail panel uses `--kb-radius-xl` and `--kb-shadow-lg`.
