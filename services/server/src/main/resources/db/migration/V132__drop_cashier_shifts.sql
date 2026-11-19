-- V132__drop_cashier_shifts.sql
-- Purge dormant cashier_shifts table (0 live rows) across all environments.
-- Shift drawer balancing is removed from the platform in favor of business-day cutoff.

DROP TABLE IF EXISTS cashier_shifts CASCADE;
