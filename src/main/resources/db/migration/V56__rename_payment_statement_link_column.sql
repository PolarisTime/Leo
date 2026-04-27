ALTER TABLE fm_payment
    RENAME COLUMN source_supplier_statement_id TO source_statement_id;

DROP INDEX IF EXISTS idx_fm_payment_source_statement;

CREATE INDEX IF NOT EXISTS idx_fm_payment_source_statement
    ON fm_payment (source_statement_id, status)
    WHERE deleted_flag = FALSE;
