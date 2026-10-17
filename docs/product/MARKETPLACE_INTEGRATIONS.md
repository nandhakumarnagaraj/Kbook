# Marketplace Integrations (Swiggy / Zomato)

> **Status: NOT BUILT — deliberately out of scope.**
> Verified against the working tree at `main` / HEAD `afa5cc86` on 2026-10-17.
>
> This file exists so nobody re-discovers the leftover schema and assumes an
> integration is half-finished. There is no integration code, and none is planned.

## 1. Decision

`docs/meta/AGENTS.md:55`:

> **Do NOT build (removed or never planned):** Zomato/Swiggy/marketplace integrations ·
> storefront ordering · Android raw-material inventory UI · …

The same boundary is stated in `docs/product/PRODUCT.md` (Android scope) and
`docs/product/FEATURE.md` (feature matrix). Marketplace work was scoped out at product
level; the residue below predates that decision.

## 2. What exists in the repo

No controller, service, repository, entity, DTO, scheduled job, webhook handler, client
call, admin page, or Android screen for either platform. `grep -ri "swiggy|zomato"`
over application code returns only:

| Hit | What it is |
|---|---|
| `RestaurantProfile.java:123-145` | Legacy JPA fields on the restaurant profile |
| `GenericSyncService.java:1261-1262` | Sync preserves the two `*_enabled` flags |
| `BrandedStartFrame.kt` comments | "Zomato-style" design reference only |
| `AppDatabase.kt:119` | Legacy payment-mode strings in a migration check |

### 2.1 Legacy profile columns

`restaurantprofiles` carries, mapped in `RestaurantProfile.java`:

| Column | Type | Purpose (legacy) |
|---|---|---|
| `zomato_enabled` | boolean | per-platform integration toggle |
| `swiggy_enabled` | boolean | per-platform integration toggle |
| `zomato_outlet_id` | varchar | partner outlet identifier |
| `swiggy_store_id` | varchar | partner store identifier |
| `zomato_api_key` | varchar | partner credential |
| `zomato_webhook_secret` | varchar | partner webhook signing |
| `swiggy_api_key` | varchar | partner credential |
| `swiggy_webhook_secret` | varchar | partner webhook signing |
| `marketplace_notes` | text | free-text notes |

Introduced by `V20__add_restaurant_marketplace_fields.sql` and
`V21__add_marketplace_integration_fields.sql`.

### 2.2 Legacy tables

Created in `V21`, expanded by `V53` and `V57`:

| Table | Columns | Referenced by code? |
|---|---|---|
| `marketplace_orders` | `restaurant_id`, `bill_id`, `platform` (`ZOMATO`/`SWIGGY`), `platform_order_id`, `platform_status`, customer fields, `subtotal`, `tax_amount`, `total_amount`, `payment_mode`, `raw_payload`, timestamps, `order_status`, `accepted_at`, `rejected_at`, `rejected_reason`, `ready_at`, `completed_at` | **No** — no entity or repository |
| `marketplace_order_items` | `marketplace_order_id`, `bill_item_id`, `platform_item_id`, `item_name`, `variant_name`, `price`, `quantity`, `item_total`, `special_instruction` | **No** |

Both tables have FKs into `restaurantprofiles`, `bills`, and `bill_items`, and
`marketplace_orders` has `UNIQUE (platform, platform_order_id)`.

### 2.3 Stale documentation

`docs/specs/v2-third-party-integration-inventory.md:143` still lists
`apps/web-admin/src/app/pages/marketplace-setup/marketplace-setup-page.component.ts` — that
page does not exist. Treat that inventory as historical.

## 3. Why the residue matters

1. **False signal.** A `marketplace_orders` table plus credential columns reads like a
   paused integration; it is not. Anyone (human or agent) may try to "finish" it.
2. **Credential surface.** `zomato_api_key`, `swiggy_api_key`, and the webhook secrets
   are mapped columns. They are not used or serialized by any DTO today, but they
   should be confirmed unreachable and eventually dropped.
3. **Schema weight.** Two unused tables with foreign keys and indexes still take part
   in migrations, backups, and `hibernate` validation.
4. **Sync coupling.** `GenericSyncService` explicitly preserves the two enable flags,
   so the fields survive a profile push even though nothing sets them.

## 4. If an integration is ever approved

Minimum work, in order — none of this exists today:

1. **Product decision:** which platform, dine-in/delivery scope, who owns order
   accept/reject, and whether marketplace orders create KhanaBook bills at all.
2. **Partner onboarding:** API credentials per restaurant (or a KhanaBook-level app),
   webhook signing secrets, and a rotation policy.
3. **Menu mapping:** platform item/variant IDs ↔ KhanaBook menu items, including
   availability and price overrides.
4. **Order ingestion:** signed webhook receiver, idempotency on
   `(platform, platform_order_id)`, durable retry/dead-letter (reuse the
   `webhook_retry_jobs` pattern from payments).
5. **Order lifecycle:** accept/reject/ready/complete with outbound status calls.
6. **Billing/payment mapping:** decide whether marketplace orders appear in reports,
   GST, daily closing, and reconciliation; marketplace payment modes must not corrupt
   the existing `PaymentMode` model.
7. **Surface:** server endpoints + web owner UI; Android POS only if the product
   decision requires it (current guidance says no marketplace UI on the terminal).
8. **Observability:** metrics, audit events, and a reconciliation pass per platform.

Security requirements in all cases: signature verification, idempotency, tenant
scoping, no credentials in logs, and no client-side trust of platform payloads.

## 5. Removal / deprecation plan (recommended default)

The cheapest correct state is to delete the residue rather than carry it:

| Phase | Action | Risk |
|---|---|---|
| 0 | Confirm nothing outside this repo reads the columns/tables (ops/BI) | None |
| 1 | Stop preserving `zomato_enabled` / `swiggy_enabled` in `GenericSyncService`; stop mapping the fields in `RestaurantProfile` | Low — sync payload behaviour changes |
| 2 | One migration: drop `marketplace_order_items`, `marketplace_orders`, then the nine profile columns | Medium — irreversible; take a backup first |
| 3 | Delete the stale reference in `v2-third-party-integration-inventory.md` and link this file | None |

Do **not** drop the columns before phase 1 ships, or Hibernate validation will fail on
the old JAR during a rolling deploy.

## 6. Verification

```bash
# No integration code should exist
grep -rli "swiggy\|zomato" services/server/src/main/java apps/android/app/src/main/java apps/web-admin/src

# Schema residue
grep -rn "marketplace" services/server/src/main/resources/db/migration/*.sql

# Entity fields
grep -n "zomato\|swiggy\|marketplace" \
  services/server/src/main/java/com/khanabook/saas/feature/restaurants/data/RestaurantProfile.java
```

Expected today: no controller/service/client hits; only the profile entity, the sync
preservation lines, and migrations `V20`, `V21`, `V53`, `V57`.

## Related

- `docs/meta/AGENTS.md` — product scope (authoritative)
- `docs/product/PRODUCT.md` — platform and module scope
- `docs/product/FEATURE.md` — current feature matrix
- `docs/specs/v2-third-party-integration-inventory.md` — historical inventory (stale)
