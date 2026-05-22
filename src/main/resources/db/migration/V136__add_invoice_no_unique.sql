-- V136: Add UNIQUE constraint to invoice_no fields
-- Invoice numbers must be unique at business level

ALTER TABLE fm_invoice_issue DROP CONSTRAINT IF EXISTS uq_invoice_issue_invoice_no;
ALTER TABLE fm_invoice_issue ADD CONSTRAINT uq_invoice_issue_invoice_no UNIQUE (invoice_no);

ALTER TABLE fm_invoice_receipt DROP CONSTRAINT IF EXISTS uq_invoice_receipt_invoice_no;
ALTER TABLE fm_invoice_receipt ADD CONSTRAINT uq_invoice_receipt_invoice_no UNIQUE (invoice_no);
