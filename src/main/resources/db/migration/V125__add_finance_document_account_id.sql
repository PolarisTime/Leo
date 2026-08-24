ALTER TABLE fm_receipt
    ADD COLUMN account_id bigint;

ALTER TABLE fm_payment
    ADD COLUMN account_id bigint;

CREATE INDEX idx_fm_receipt_account_id
    ON fm_receipt (account_id)
    WHERE deleted_flag = false;

CREATE INDEX idx_fm_payment_account_id
    ON fm_payment (account_id)
    WHERE deleted_flag = false;
