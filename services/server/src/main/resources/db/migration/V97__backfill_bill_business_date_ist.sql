-- Backfill bills whose business date (last_reset_date) disagrees with the IST calendar day
-- of created_at.
--
-- Cause: GenericSyncService filled a blank last_reset_date with LocalDate.now() — no ZoneId —
-- and the container JVM runs in UTC (no TZ set in docker-compose). For a bill created between
-- 00:00 and 05:29 IST that yields the PREVIOUS calendar day, so the bill was filed under the
-- wrong business day and day-close totals for both days are wrong.
--
-- The code fix (BillSyncService.applyServerBusinessDate, called unconditionally from
-- GenericSyncService) stops new rows being written this way, but it does not rewrite history.
-- This migration does that, once.
--
-- Safety: bills (restaurant_id, last_reset_date, terminal_series, daily_order_id) is uniquely
-- indexed over active rows (ux_bills_restaurant_terminal_daily_active from V26). Moving a bill
-- to its correct day could therefore land on a slot already occupied there. Such rows are
-- SKIPPED and reported rather than forced, because choosing a new order number for a bill that
-- already exists on a printed receipt is a business decision, not a migration's call.
--
-- Idempotent: re-running finds nothing, since the predicate is the mismatch itself.

DO $$
DECLARE
    fixed_count   INT := 0;
    skipped_count INT := 0;
    r             RECORD;
    now_ms        BIGINT := (EXTRACT(EPOCH FROM now()) * 1000)::BIGINT;
BEGIN
    FOR r IN
        SELECT b.id,
               b.restaurant_id,
               b.invoice_number,
               b.daily_order_id,
               b.daily_order_display,
               b.terminal_series,
               b.is_deleted,
               b.last_reset_date,
               to_char(to_timestamp(b.created_at / 1000) AT TIME ZONE 'Asia/Kolkata', 'YYYY-MM-DD') AS ist_date
        FROM bills b
        WHERE b.created_at IS NOT NULL
          AND b.last_reset_date IS NOT NULL
          AND b.last_reset_date <>
              to_char(to_timestamp(b.created_at / 1000) AT TIME ZONE 'Asia/Kolkata', 'YYYY-MM-DD')
        ORDER BY b.restaurant_id, b.created_at
    LOOP
        -- Only ACTIVE rows can violate the partial unique index, so only they need the guard.
        IF r.is_deleted = false
           AND r.daily_order_id IS NOT NULL
           AND EXISTS (
               SELECT 1
               FROM bills x
               WHERE x.restaurant_id = r.restaurant_id
                 AND x.is_deleted = false
                 AND x.id <> r.id
                 AND x.last_reset_date = r.ist_date
                 AND x.daily_order_id = r.daily_order_id
                 AND COALESCE(x.terminal_series, '') = COALESCE(r.terminal_series, '')
           )
        THEN
            skipped_count := skipped_count + 1;
            RAISE WARNING 'last_reset_date backfill SKIPPED: bill id=% invoice=% restaurant=% order % is already taken on %; needs manual renumbering.',
                r.id, r.invoice_number, r.restaurant_id, r.daily_order_display, r.ist_date;
        ELSE
            -- server_updated_at is bumped so delta pulls carry the correction to devices;
            -- updated_at is deliberately left alone so this does not look like a user edit.
            UPDATE bills
               SET last_reset_date   = r.ist_date,
                   server_updated_at = now_ms
             WHERE id = r.id;

            fixed_count := fixed_count + 1;
            RAISE NOTICE 'last_reset_date backfill: bill id=% invoice=% restaurant=% moved % -> %',
                r.id, r.invoice_number, r.restaurant_id, r.last_reset_date, r.ist_date;
        END IF;
    END LOOP;

    RAISE NOTICE 'last_reset_date backfill complete: % corrected, % skipped.', fixed_count, skipped_count;

    IF skipped_count > 0 THEN
        RAISE WARNING 'Some bills still have a business date that disagrees with created_at. Resolve the reported order-number clashes, then re-deploy or run this block again.';
    END IF;
END $$;
