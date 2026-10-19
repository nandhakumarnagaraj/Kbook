-- The orders.kot_view / orders.kot_ready / orders.kot_void permission keys were
-- defined server-side but never enforced by Android (KOT is device-local). They are
-- removed from PermissionKey, so drop any stored grants and requests that reference them.
DELETE FROM staff_permissions
WHERE permission_key IN ('orders.kot_view', 'orders.kot_ready', 'orders.kot_void');

DELETE FROM permission_requests
WHERE permission_key IN ('orders.kot_view', 'orders.kot_ready', 'orders.kot_void');
