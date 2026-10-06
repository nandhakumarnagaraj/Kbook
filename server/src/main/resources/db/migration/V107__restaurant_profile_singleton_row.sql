-- One profile row per restaurant, and clean up the legacy duplicate stubs.
--
-- A restaurant has exactly ONE profile row: restaurant_id is its identity. The table's
-- only unique key was the generic (restaurant_id, device_id, local_id) composite, which
-- does not express that. The two sides disagreed on what local_id means:
--   * AuthServiceImpl.signup creates the stub under local_id = 1 (device_id NULL);
--   * the app keeps one local profile row keyed on the restaurant's server id and pushes
--     it with local_id = restaurant_id (MasterSyncProcessor.pushPendingProfile).
-- Different tuples for the same restaurant, so the push matched no existing row and
-- INSERTed a second one. That second row then broke every single-row reader of this table
-- with IncorrectResultSizeDataAccessException ("Query did not return a unique result:
-- 2 results were returned") -> HTTP 500 on login (correct password only; a wrong
-- password returned a clean 400), on every authenticated request via JwtRequestFilter,
-- on invoices, on tax compliance and on terminal approval.
--
-- GenericSyncService now resolves a profile push by restaurant_id, so the push updates
-- the existing row instead of forking a new one, and the readers pick a single row
-- deterministically. This migration makes the guarantee structural: the database refuses
-- a second row for a restaurant even if some other code path (or a buggy client) manages
-- to insert one.

-- 1) Delete the leftover bare stubs of already-duplicated restaurants.
--
--    Only the SIGNUP-SHAPED stub is removed: local_id = 1 AND no business data (no currency,
--    no whatsapp number). That shape is what AuthServiceImpl.signup writes and what nothing
--    else writes - signup sets only shop_name, the reset dates and the logo/QR versions,
--    and never a currency or a whatsapp number, while the row a device pushes always carries
--    the currency. So this can only ever delete an empty placeholder, never a row that holds
--    business data. Note device_id is NOT NULL (BaseSyncEntity) and signup fills it with the
--    signing-in device's id, so it cannot be part of the shape test.
--
--    Everything else is left alone: rows are never merged or rewritten, since two real rows
--    for one restaurant cannot be reconciled automatically (they may disagree on shop name,
--    address and upi details) and silently picking one would be a data decision, not a
--    migration.
--
--    This resolves the production duplicates: all 3 live duplicated restaurants were a
--    bare signup stub plus the row a device had pushed (stub ids 119, 123, 126).
--
--    Idempotent by construction: no shape-matching stub is left behind, so a re-run
--    deletes nothing.
DELETE FROM restaurantprofiles stub
WHERE stub.local_id = 1
  AND (stub.currency IS NULL OR stub.currency = '')
  AND (stub.whatsapp_number IS NULL OR stub.whatsapp_number = '')
  AND EXISTS (
        SELECT 1 FROM restaurantprofiles other
        WHERE other.restaurant_id = stub.restaurant_id
          AND other.id <> stub.id
  );

-- 2) Structural guarantee: at most one profile row per restaurant.
--
--    Created only when step 1 emptied every duplicate group, and the reason is a hard
--    deployment constraint. A second terminal pushes the same profile with its OWN
--    device_id, which the old (device_id, local_id) matching treated as a different row
--    - so a restaurant can hold several rows that all carry real device data. Step 1
--    keeps those (they are not empty stubs), and CREATE UNIQUE INDEX would then fail, and
--    because Flyway runs each migration in a transaction that failure would roll back and
--    leave the server unable to boot. A migration must never be able to take the POS
--    offline, so the index is conditional and the unresolved data is reported instead.
--
--    Enforcement of the "one row" rule therefore has two independent layers: the sync
--    resolution in GenericSyncService (every push updates the one row) and this index
--    once the data allows it. The readers are duplicate-safe in the meantime, so a
--    restaurant that still has duplicates stays usable.
--
--    To resolve the rest by hand (only needed for rows with real data on both sides -
--    inspect them first, there is no safe automatic choice):
--
--      SELECT restaurant_id, COUNT(*), array_agg(id ORDER BY id)
--      FROM restaurantprofiles GROUP BY restaurant_id HAVING COUNT(*) > 1;
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM restaurantprofiles GROUP BY restaurant_id HAVING COUNT(*) > 1
    ) THEN
        -- Plain CREATE UNIQUE INDEX: it runs inside Flyway's transaction, where
        -- CONCURRENTLY is not allowed, and this table is small.
        EXECUTE 'CREATE UNIQUE INDEX IF NOT EXISTS uq_restaurantprofiles_restaurant_id'
             || ' ON restaurantprofiles (restaurant_id)';
    ELSE
        RAISE WARNING
            'restaurantprofiles still holds rows for the restaurant_id values below;'
            ' uq_restaurantprofiles_restaurant_id was NOT created. A restaurant with more'
            ' than one profile row is served by the newest row only, and its login/API'
            ' surface stays available - but pick a survivor per restaurant_id before'
            ' relying on the database constraint. Offending restaurant_id values: %',
            (SELECT string_agg(d.restaurant_id::text, ', ' ORDER BY d.restaurant_id)
             FROM (SELECT restaurant_id FROM restaurantprofiles
                   GROUP BY restaurant_id HAVING COUNT(*) > 1) d);
    END IF;
END $$;