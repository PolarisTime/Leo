-- Add @Version optimistic locking columns to high-contention entities.
-- Hibernate populates NULL as version 0 on first write.

ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS version BIGINT;
ALTER TABLE fm_payment ADD COLUMN IF NOT EXISTS version BIGINT;
ALTER TABLE fm_receipt ADD COLUMN IF NOT EXISTS version BIGINT;
ALTER TABLE st_supplier_statement ADD COLUMN IF NOT EXISTS version BIGINT;
ALTER TABLE st_customer_statement ADD COLUMN IF NOT EXISTS version BIGINT;
ALTER TABLE st_freight_statement ADD COLUMN IF NOT EXISTS version BIGINT;
