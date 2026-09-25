# Known Issues

## Category push rejected with HTTP 409 — reorder/rename/delete do not persist across restart on affected tenant

**Status:** Open (pre-existing platform/sync behavior, not a regression from the Manage Categories feature)

**Symptom (verified on-device, Moto, 1.0.30):**
- Manage Categories drag-reorder applies instantly in-session (chip order updates on Save).
- After an app/process restart, category order reverts to server order.
- `logcat` on the device shows: `Conflict while pushing categories ... {"error":"This record was modified by another device. ...","path":"/api/v1/sync/menu/categories/push"}` (HTTP 409) on every category push attempt.

**Root cause trace:**
- Any category edit — reorder (`CategoryRepository.reorderCategories`), rename (`updateCategory`), delete — marks the row `isSynced = false` and bumps `updatedAt`, then triggers a background sync push.
- The server rejects the push with a per-record version (optimistic concurrency) 409.
- The client's conflict recovery pulls server state with `lastSyncTimestamp = 0`, which rewrites local categories to server order and marks them synced — silently discarding the local change.
- The version skew pre-exists new code: `updateCategory` (used by the existing Edit/Delete flow) sets identical fields, so category writes from a device have the same failure mode once server-side rows are seeded/modified outside that device's last sync.

**Scope:**
- In-process behavior of the Manage Categories dialog (drag-reorder, Save, inline add, rename/delete delegation, Cancel) is fully verified and correct.
- Persistence of reorder/rename/delete across restart depends on resolving this sync 409 (server-side transaction/version handling), which is out of scope for the UI feature.