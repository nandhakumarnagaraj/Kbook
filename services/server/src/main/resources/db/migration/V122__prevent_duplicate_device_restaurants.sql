-- One live shop per device — created only when the data allows it.
--
-- WHY: an install that already runs a live shop must not provision a second one. Also
--      remember (see AuthServiceImpl#rejectSecondShopOnSameInstall) which enforces the
--      same rule in code.
--
-- WHAT: adds a partial unique index over restaurantprofiles(device_id) for live,
--       non-suspended rows. Deleted or suspended shops do not participate, so retiring a
--       duplicate never blocks a fresh signup on that hardware.
--
-- HOW: guarded exactly like V107 (restaurantprofiles singleton). The index is created only
--      when no duplicate group exists; otherwise the migration RAISES A WARNING and leaves
--      the data untouched for a human decision.
--
-- WHEN: Flyway runs this at application bootstrap, inside a transaction.
--
-- LIMITATIONS / WHY THE GUARD EXISTS:
--   A bare CREATE UNIQUE INDEX fails when duplicates already exist:
--       ERROR: could not create unique index "uq_restaurantprofiles_live_device"
--       DETAIL: Key (device_id)=(…) is duplicated.
--   Because Flyway migrates during startup, that failure is not a skipped migration — it is
--   a FAILED BOOT, i.e. a full API outage. Production on 2026-11-17 held 20 device_id values
--   with more than one live shop (45 rows, 25 surplus); 17 of those rows carry bills and are
--   therefore real, distinct businesses. 14 of the 20 groups are browser/client identifiers
--   (server, web-admin, dev_*) rather than hardware, so the rule as written also forbids two
--   shops created from one web browser. A constraint that cannot be applied must not be able
--   to take the platform offline, so this migration is advisory until the data is reconciled.
--
--   BECAUSE IT IS ADVISORY, IT CAN NEVER BE RETRIED: Flyway applies this file once. If the
--   index is skipped here it stays missing for good, silently. ops/verify-schema-invariants.sh
--   reports that state after every deploy and can create the index once the data is clean.
--
-- TO RESOLVE BY HAND (inspect first; there is no safe automatic choice):
--   SELECT device_id, count(*), array_agg(restaurant_id ORDER BY restaurant_id)
--   FROM restaurantprofiles
--   WHERE is_deleted = false AND (is_suspended = false OR is_suspended IS NULL)
--   GROUP BY device_id HAVING count(*) > 1;
--
-- Idempotent: re-running creates nothing twice and warns about the same rows.

DO $$
DECLARE
    offending text;
BEGIN
    SELECT string_agg(d.device_id || ' (' || d.cnt || ' rows)', ', ' ORDER BY d.device_id)
      INTO offending
      FROM (
            SELECT device_id, count(*) AS cnt
              FROM restaurantprofiles
             WHERE is_deleted = false
               AND (is_suspended = false OR is_suspended IS NULL)
               AND device_id IS NOT NULL
             GROUP BY device_id
            HAVING count(*) > 1
           ) d;

    IF offending IS NULL THEN
        EXECUTE 'CREATE UNIQUE INDEX IF NOT EXISTS uq_restaurantprofiles_live_device'
             || ' ON restaurantprofiles (device_id)'
             || ' WHERE is_deleted = false'
             || '   AND (is_suspended = false OR is_suspended IS NULL)';

        EXECUTE 'COMMENT ON INDEX uq_restaurantprofiles_live_device IS '
             || quote_literal('At most one live shop per device. Deleted and suspended '
             || 'profiles are excluded so a retired duplicate frees its hardware.');
        RAISE NOTICE 'uq_restaurantprofiles_live_device created';
    ELSE
        RAISE WARNING
            'uq_restaurantprofiles_live_device was NOT created: % device_id value(s) still '
            'run more than one live shop. The signup guard (rejectSecondShopOnSameInstall) '
            'remains the active protection. Offending device_id values: %',
            (SELECT count(*) FROM (
                SELECT device_id FROM restaurantprofiles
                 WHERE is_deleted = false AND (is_suspended = false OR is_suspended IS NULL)
                   AND device_id IS NOT NULL
                 GROUP BY device_id HAVING count(*) > 1) x),
            offending;
    END IF;
END $$;
