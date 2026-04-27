ALTER TABLE fm_payment
    ADD COLUMN IF NOT EXISTS source_supplier_statement_id BIGINT;

ALTER TABLE fm_receipt
    ADD COLUMN IF NOT EXISTS source_customer_statement_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_fm_payment_source_statement
    ON fm_payment (source_supplier_statement_id, status)
    WHERE deleted_flag = FALSE;

CREATE INDEX IF NOT EXISTS idx_fm_receipt_source_statement
    ON fm_receipt (source_customer_statement_id, status)
    WHERE deleted_flag = FALSE;
