-- Optimistic locking for voucher_settings: a save based on a stale read fails
-- (HTTP 409) instead of silently overwriting a newer change. Additive only.
ALTER TABLE voucher_settings ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
