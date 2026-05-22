-- V129: Expand document number fields 32→64
-- H1: NoRule.prefix=64 but all document no fields were 32
-- Typical no format: "SH-Steel-PO-20260522-00001" = 31 chars; with longer prefix risks truncation

-- Contract numbers
ALTER TABLE ct_sales_contract ALTER COLUMN contract_no TYPE VARCHAR(64);
ALTER TABLE ct_purchase_contract ALTER COLUMN contract_no TYPE VARCHAR(64);

-- Order numbers
ALTER TABLE so_sales_order ALTER COLUMN order_no TYPE VARCHAR(64);
ALTER TABLE po_purchase_order ALTER COLUMN order_no TYPE VARCHAR(64);

-- Inbound / Outbound numbers
ALTER TABLE po_purchase_inbound ALTER COLUMN inbound_no TYPE VARCHAR(64);
ALTER TABLE so_sales_outbound ALTER COLUMN outbound_no TYPE VARCHAR(64);

-- Bill numbers
ALTER TABLE lg_freight_bill ALTER COLUMN bill_no TYPE VARCHAR(64);

-- Statement numbers
ALTER TABLE st_customer_statement ALTER COLUMN statement_no TYPE VARCHAR(64);
ALTER TABLE st_supplier_statement ALTER COLUMN statement_no TYPE VARCHAR(64);
ALTER TABLE st_freight_statement ALTER COLUMN statement_no TYPE VARCHAR(64);

-- Finance numbers
ALTER TABLE fm_receipt ALTER COLUMN receipt_no TYPE VARCHAR(64);
ALTER TABLE fm_payment ALTER COLUMN payment_no TYPE VARCHAR(64);
ALTER TABLE fm_invoice_issue ALTER COLUMN issue_no TYPE VARCHAR(64);
ALTER TABLE fm_invoice_receipt ALTER COLUMN receive_no TYPE VARCHAR(64);
ALTER TABLE fm_invoice_issue ALTER COLUMN invoice_no TYPE VARCHAR(64);
ALTER TABLE fm_invoice_receipt ALTER COLUMN invoice_no TYPE VARCHAR(64);

-- Budget ticket numbers (if table exists)
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'ops_ticket') THEN
        ALTER TABLE ops_ticket ALTER COLUMN ticket_no TYPE VARCHAR(64);
    END IF;
END $$;

-- Operation log business numbers
ALTER TABLE sys_operation_log ALTER COLUMN business_no TYPE VARCHAR(64);
ALTER TABLE sys_operation_log ALTER COLUMN log_no TYPE VARCHAR(64);
