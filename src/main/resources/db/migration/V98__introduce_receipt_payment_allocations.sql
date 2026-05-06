CREATE TABLE IF NOT EXISTS fm_receipt_allocation (
    id BIGINT PRIMARY KEY,
    receipt_id BIGINT NOT NULL,
    line_no INTEGER NOT NULL,
    source_statement_id BIGINT NOT NULL,
    allocated_amount NUMERIC(14, 2) NOT NULL,
    CONSTRAINT fk_fm_receipt_allocation_head FOREIGN KEY (receipt_id) REFERENCES fm_receipt (id)
);

CREATE INDEX IF NOT EXISTS idx_fm_receipt_allocation_head
    ON fm_receipt_allocation (receipt_id, line_no);

CREATE INDEX IF NOT EXISTS idx_fm_receipt_allocation_statement
    ON fm_receipt_allocation (source_statement_id);

CREATE TABLE IF NOT EXISTS fm_payment_allocation (
    id BIGINT PRIMARY KEY,
    payment_id BIGINT NOT NULL,
    line_no INTEGER NOT NULL,
    source_statement_id BIGINT NOT NULL,
    allocated_amount NUMERIC(14, 2) NOT NULL,
    CONSTRAINT fk_fm_payment_allocation_head FOREIGN KEY (payment_id) REFERENCES fm_payment (id)
);

CREATE INDEX IF NOT EXISTS idx_fm_payment_allocation_head
    ON fm_payment_allocation (payment_id, line_no);

CREATE INDEX IF NOT EXISTS idx_fm_payment_allocation_statement
    ON fm_payment_allocation (source_statement_id);

INSERT INTO fm_receipt_allocation (id, receipt_id, line_no, source_statement_id, allocated_amount)
SELECT receipt.id,
       receipt.id,
       1,
       receipt.source_customer_statement_id,
       receipt.amount
FROM fm_receipt receipt
WHERE receipt.source_customer_statement_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM fm_receipt_allocation allocation
      WHERE allocation.receipt_id = receipt.id
  );

INSERT INTO fm_payment_allocation (id, payment_id, line_no, source_statement_id, allocated_amount)
SELECT payment.id,
       payment.id,
       1,
       payment.source_statement_id,
       payment.amount
FROM fm_payment payment
WHERE payment.source_statement_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM fm_payment_allocation allocation
      WHERE allocation.payment_id = payment.id
  );
