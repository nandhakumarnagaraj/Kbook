-- Inventory optimistic locking (Phase 5): add Hibernate @Version column to
-- raw_materials so concurrent stock deductions throw OptimisticLockException
-- instead of silently clobbering quantities. Existing rows start at version 0.
ALTER TABLE raw_materials ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
