# 06 - Relationships & Constraints Forensic Audit
> **KhanaBook POS & Cloud SaaS Platform Architecture**  
> **Target Catalogs:** PostgreSQL 16 (`information_schema.table_constraints`) & Room v84 Schema Export (`84.json`)  
> **Audit Mode:** STRICT READ-ONLY Catalog Inspection  

---

## 1. Constraint Counts & Overview

| Constraint Category | PostgreSQL 16 (`public`) | SQLite (Room v84) | Discrepancy & Behavioral Impact |
|---|:---:|:---:|---|
| **Primary Keys** | **25 PKs** (1 per table) | **18 PKs** (1 per table) | Every table in both engines enforces an explicit PK. |
| **Foreign Keys** | **31 FKs** | **6 FKs** | PostgreSQL enforces strict relational integrity; SQLite deliberately relaxes non-critical FKs to allow out-of-order delta batch insertions. |
| **Unique Constraints** | **58 Unique Constraints** | **12 Unique Indices** | PostgreSQL enforces multi-column tenant uniqueness (`restaurant_id, device_id, local_id`). |
| **Check Constraints** | **10 Check Constraints** | **0 Check Constraints** | PostgreSQL enforces positive financial limits (`CHECK (total_amount >= 0)`); SQLite lacks check constraints and relies on application validators. |
| **Total Constraints** | **124 Constraints** | **36 Explicit Constraints** | PostgreSQL provides strict cloud-level relational guards. |

---

## 2. PostgreSQL Live Foreign Keys & Cascade Rules

| Child Table | Constraint Name | Child Column | Parent Table | Parent Column | Delete Rule | Update Rule |
|---|---|---|---|---|:---:|:---:|
| `bill_items` | `fk_bill_items_bill` | `bill_id` | `bills` | `id` | **CASCADE** | NO ACTION |
| `bill_items` | `fk_bill_items_server_bill` | `server_bill_id` | `bills` | `id` | **CASCADE** | NO ACTION |
| `bill_items` | `fk_bill_items_restaurant` | `restaurant_id` | `restaurantprofiles` | `restaurant_id` | NO ACTION | NO ACTION |
| `bill_items` | `fk_bill_items_menuitem` | `server_menu_item_id` | `menuitems` | `id` | **SET NULL** | NO ACTION |
| `bill_items` | `fk_bill_items_variant` | `server_variant_id` | `itemvariants` | `id` | **SET NULL** | NO ACTION |
| `bill_payments` | `fk_bill_payments_bill` | `bill_id` | `bills` | `id` | **CASCADE** | NO ACTION |
| `bill_payments` | `fk_bill_payments_server_bill` | `server_bill_id` | `bills` | `id` | **CASCADE** | NO ACTION |
| `bill_payments` | `fk_bill_payments_restaurant`| `restaurant_id` | `restaurantprofiles` | `restaurant_id` | NO ACTION | NO ACTION |
| `bills` | `fk_bills_restaurant` | `restaurant_id` | `restaurantprofiles` | `restaurant_id` | NO ACTION | NO ACTION |
| `bills` | `fk_bills_created_by` | `created_by` | `users` | `id` | **SET NULL** | NO ACTION |
| `categories` | `fk_categories_restaurant` | `restaurant_id` | `restaurantprofiles` | `restaurant_id` | NO ACTION | NO ACTION |
| `menuitems` | `fk_menuitems_restaurant` | `restaurant_id` | `restaurantprofiles` | `restaurant_id` | NO ACTION | NO ACTION |
| `menuitems` | `fk_menuitems_category` | `category_id` | `categories` | `id` | NO ACTION | NO ACTION |
| `itemvariants` | `fk_itemvariants_menuitem` | `menu_item_id` | `menuitems` | `id` | **CASCADE** | NO ACTION |
| `restaurant_terminal`| `fk_terminal_restaurant` | `restaurant_id` | `restaurantprofiles` | `restaurant_id` | **CASCADE** | NO ACTION |
| `refresh_tokens` | `fk_refresh_tokens_user` | `user_id` | `users` | `id` | **CASCADE** | NO ACTION |
| `security_audit_log` | `fk_audit_user` | `user_id` | `users` | `id` | **SET NULL** | NO ACTION |

---

## 3. SQLite Room v84 Foreign Keys & Relaxations

In SQLite Room v84, exactly 6 foreign keys are enforced:
1. `bills.created_by` $\to$ `users.id` (`onDelete = SET_NULL`)
2. `bill_items.bill_id` $\to$ `bills.id` (`onDelete = CASCADE`)
3. `bill_items.menu_item_id` $\to$ `menu_items.id` (`onDelete = SET_NULL`)
4. `bill_items.variant_id` $\to$ `item_variants.id` (`onDelete = SET_NULL`)
5. `bill_payments.bill_id` $\to$ `bills.id` (`onDelete = CASCADE`)
6. `item_variants.menu_item_id` $\to$ `menu_items.id` (`onDelete = CASCADE`)

### Intentional Relaxation: Menu Items to Categories
In `MenuItemEntity.kt`:
While `menu_items.category_id` references `categories.id`, Android sync handles out-of-order pulls where a menu item arrives before its remote category. If strict database foreign keys rejected the row, sync would abort. Instead, `menu_item_category_repairs` stores the assignment until the category entity arrives.

---

## 4. PostgreSQL Check Constraints (Business Limit Enforcement)

PostgreSQL catalog query of `pg_constraint WHERE contype = 'c'` reveals 10 check constraints:
1. `bills_total_amount_check`: `CHECK (total_amount >= 0.00)`
2. `bills_subtotal_check`: `CHECK (subtotal >= 0.00)`
3. `bills_gst_percentage_check`: `CHECK (gst_percentage >= 0.00)`
4. `bill_items_price_check`: `CHECK (price >= 0.00)`
5. `bill_items_quantity_check`: `CHECK (quantity > 0)`
6. `bill_payments_amount_check`: `CHECK (amount >= 0.00)`
7. `menuitems_base_price_check`: `CHECK (base_price >= 0.00)`
8. `itemvariants_price_check`: `CHECK (price >= 0.00)`
9. `restaurantprofiles_gst_percentage_check`: `CHECK (gst_percentage >= 0.00 AND gst_percentage <= 100.00)`
10. `restaurant_terminal_series_check`: `CHECK (terminal_series IN ('A', 'B', 'C', 'D', 'E'))`

---

## 5. Relationships Present in Application Code but Missing in Database

1. **`bills` $\leftrightarrow$ `local_cashier_shifts`:**
   - Application Code: `BillEntity.kt` has optional `cashier_shift_id` concepts in shift viewmodels.
   - Database State: No foreign key constraint exists in either PostgreSQL or SQLite between bills and cashier shifts.
2. **`printer_profiles` $\leftrightarrow$ `kitchen_print_queue`:**
   - Application Code: Queue jobs target a printer by MAC address (`printer_mac`).
   - Database State: No foreign key constraint exists; hardware bindings are dynamic.
