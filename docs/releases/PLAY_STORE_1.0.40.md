# Play Store release notes — 1.0.40 (versionCode 40)

**Artifact:** `apps/android/app/build/outputs/bundle/release/app-release.aab` (SHA-256 `250ca1b0…3f914076`)
**Source:** Sessions 036–066 in `ANDROID_POS_DOCUMENTATION/CHANGELOG.md`, reduced to user-visible changes.

> 1.0.39 was blocked in Play review because the Easebuzz checkout activity made portrait a required hardware feature. 1.0.40 fixes that and reuses this same user-facing copy; versionCode 39 is spent.

## What's new (Play Console, English — 441 / 500 characters)

```text
Orders just got easier to scan.
- New order table: each order sits on its own card with brighter amounts and times, even spacing between columns, and a one-tap View button.
- Redesigned Order Details: clear order and invoice numbers, readable labels, itemised totals, and colour-coded Print, Share, Change Payment Mode and Cancel Order buttons.
- Smaller, cleaner messages that no longer cover the till.
- General polish and stability fixes.
```

## Shorter variant (if a tighter note is preferred — 218 characters)

```text
Orders are easier to scan with a new card-style table, and Order Details has a clearer layout with colour-coded Print, Share, Change Payment Mode and Cancel Order buttons. Smaller on-screen messages and general polish.
```

## Deliberately left out

- Internal colour/token names, dp values, session IDs and screenshot filenames.
- Test-data observation that order A02 is cancelled on the QA device.
- The missing runtime test of the release build (release-only caveat, not a user-facing change).
