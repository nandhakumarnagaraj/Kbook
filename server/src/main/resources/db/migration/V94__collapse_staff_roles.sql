-- Role model collapse (2026-09-05): roles are now OWNER, SHOP_STAFF, KBOOK_ADMIN.
-- All legacy staff/management roles merge into SHOP_STAFF; OWNER and KBOOK_ADMIN are preserved.
-- (V12 previously folded MANAGER/CASHIER/KITCHEN into OWNER, so those rows are already OWNER.)
UPDATE users
SET role = 'SHOP_STAFF'
WHERE role IN ('SHOP_ADMIN', 'WAITER', 'CASHIER', 'MANAGER', 'OPERATIONS');