# Blast Radius — what a change in one slice can break

KhanaBook is offline-first with up to 5 terminals per restaurant. A change can
therefore break three surfaces at once: the device UI, the sync contract, and the
server. This table says, per slice, what to re-verify before merging.

Read this before touching anything in the **Money path** column.

## Slice map (Android)

| Slice | Owns | Depends on (allowed) | Must never depend on |
|---|---|---|---|
| `feature/billing` | bills, items, payments, invoice formatting | core, menu, auth (session) | **printing UI internals**, reports |
| `feature/printing` | printer profiles, ESC/POS, KOT, PDF | core, billing **data types** (`BillWithItems`) | billing ViewModels/screens |
| `feature/sync` | push/pull engine, master sync, quarantine | core, every feature's **data/DAO** layer | feature UI |
| `feature/payments` | Easebuzz checkout, return handling | core, billing data | printing |
| `feature/menu` | categories, items, variants | core | billing, printing |
| `feature/staff` | permissions, session roles | core, auth data | billing UI |
| `core/*` | db, di, network, theme, util | — | **nothing under `feature/`** |

### Known, deliberate violations (do not copy)

`core` currently imports feature types in several places — `core/database/AppDatabase.kt`,
`core/di/DatabaseModule.kt`, `core/util/Utils.kt`, `core/navigation/AppNavGraph.kt`.
These exist because Room DAOs, Hilt bindings and Receipt printing were built before
the slice split. They are the reason an ArchUnit "core must not import feature" rule
cannot be switched on today: it would fail on ~100 imports. Fixing that is a
dedicated refactor (move entities/DAOs to `core/database/entities`), not a drive-by.

**Therefore:** slice boundaries are currently a review convention, not an enforced
rule. If you add a NEW `core -> feature` import, you are making the eventual
refactor bigger — route through an interface in `core` instead.

## Blast radius by change type

| Change | Affected surfaces | Must re-verify |
|---|---|---|
| **Bill/payment data field** | Room schema + push DTO + server entity + pull DTO | schema migration (v83+), `SyncDtoContractTest`, `MasterSyncPullContractTest`, server `SyncDtoEntityFieldParityTest` |
| **Print formatting** (`InvoiceFormatter`, `KitchenTicketFormatter`, PDF) | physical 58mm **and** 80mm, PDF, share link | `PrintRouterTest`, `KitchenTicketFormatterTest`, live print on both widths |
| **Sync push semantics** | every terminal, every entity family | `GenericSync*Test`, `BillIdempotentUpsertTest`, `TerminalTypeEnforcementTest`, `TerminalAccessControlGapTest` |
| **Permission/roles** | offline authorization on all terminals | `PermissionManager` tests, `OfflineAuthDecider`, sync permission revalidation |
| **Payment/webhook** | real money | `PaymentSetValidatorTest`, `StuckPaymentSweepService`, reconciliation job, dashboard |
| **Room version bump** | every installed terminal | migration test from the previous release version, not just a fresh install |

## Terminal-count scaling

Nothing in a slice may assume "the other terminal is online". If a change needs
data from another device, it must arrive through pull, and it must degrade
correctly when the device has never synced (empty local state is the normal
first-boot state, not an error).
