-- DRY RUN for V97__backfill_bill_business_date_ist.sql
-- Read-only. Shows exactly which rows the migration will correct and which it will skip.
-- Run on the VPS against kbook_saas BEFORE deploying.

SELECT b.id,
       b.restaurant_id,
       b.invoice_number,
       b.daily_order_display,
       b.terminal_series,
       b.is_deleted,
       b.last_reset_date                                                                        AS current_day,
       to_char(to_timestamp(b.created_at / 1000) AT TIME ZONE 'Asia/Kolkata', 'YYYY-MM-DD')     AS correct_day,
       to_char(to_timestamp(b.created_at / 1000) AT TIME ZONE 'Asia/Kolkata', 'YYYY-MM-DD HH24:MI') AS created_ist,
       CASE
           WHEN b.is_deleted = false
                AND b.daily_order_id IS NOT NULL
                AND EXISTS (
                    SELECT 1
                    FROM bills x
                    WHERE x.restaurant_id = b.restaurant_id
                      AND x.is_deleted = false
                      AND x.id <> b.id
                      AND x.last_reset_date = to_char(to_timestamp(b.created_at / 1000) AT TIME ZONE 'Asia/Kolkata', 'YYYY-MM-DD')
                      AND x.daily_order_id = b.daily_order_id
                      AND COALESCE(x.terminal_series, '') = COALESCE(b.terminal_series, '')
                )
               THEN 'SKIP - order number already taken on the correct day'
           ELSE 'WILL FIX'
       END                                                                                      AS action
FROM bills b
WHERE b.created_at IS NOT NULL
  AND b.last_reset_date IS NOT NULL
  AND b.last_reset_date <>
      to_char(to_timestamp(b.created_at / 1000) AT TIME ZONE 'Asia/Kolkata', 'YYYY-MM-DD')
ORDER BY b.restaurant_id, b.created_at;

-- Confirm the unique index the migration must not violate actually exists.
-- V26 skips creating it if duplicates were present when it ran, so this may return no rows.
SELECT indexname
FROM pg_indexes
WHERE tablename = 'bills'
  AND indexname = 'ux_bills_restaurant_terminal_daily_active';
