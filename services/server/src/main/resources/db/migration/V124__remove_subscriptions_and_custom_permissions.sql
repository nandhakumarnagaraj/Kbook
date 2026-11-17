-- Migration V124: Remove subscriptions and custom permission systems.
--
-- WHY: KhanaBook has transitioned to a 100% Free Lifetime tier for all restaurants.
--      Subscription plans and paywalls are permanently removed.
--      Staff authorization has also been simplified to a strict 3-role model
--      (OWNER, SHOP_STAFF, KBOOK_ADMIN) with constant, role-bound permissions.
--      Dynamic per-user permission grants/revokes, monotonic revision counters,
--      and custom role templates are obsolete.
--
-- SAFETY & PRECONDITIONS:
--   - Verified that no active client application (Android POS or Web Admin) relies
--     on dynamic role templates or subscription billing.
--   - Verified zero external foreign keys pointing to any of these tables.
--   - A full database backup was taken immediately prior to this migration.
--   - All statements use IF EXISTS and CASCADE for idempotent execution.
--
-- OBJECTS REMOVED:
--   1. restaurant_subscriptions (superseded by universal free tier)
--   2. subscription_plans (superseded by universal free tier)
--   3. role_templates (0 rows in production, dead feature)
--   4. staff_permission_revision (obsolete: permissions are now constant by role)
--   5. staff_permissions (obsolete: permissions are now constant by role)
--   6. permission_requests (0 rows in production, obsolete)

-- 1. Drop subscription tables
DROP TABLE IF EXISTS restaurant_subscriptions CASCADE;
DROP TABLE IF EXISTS subscription_plans CASCADE;

-- 2. Drop custom role and permission tables
DROP TABLE IF EXISTS role_templates CASCADE;
DROP TABLE IF EXISTS staff_permission_revision CASCADE;
DROP TABLE IF EXISTS staff_permissions CASCADE;
DROP TABLE IF EXISTS permission_requests CASCADE;
