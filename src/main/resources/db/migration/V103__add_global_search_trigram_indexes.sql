CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_po_purchase_order_order_no_trgm
    ON po_purchase_order USING gin (order_no gin_trgm_ops)
    WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_po_purchase_order_supplier_name_trgm
    ON po_purchase_order USING gin (supplier_name gin_trgm_ops)
    WHERE deleted_flag = FALSE;

CREATE INDEX IF NOT EXISTS idx_po_purchase_inbound_inbound_no_trgm
    ON po_purchase_inbound USING gin (inbound_no gin_trgm_ops)
    WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_po_purchase_inbound_purchase_order_no_trgm
    ON po_purchase_inbound USING gin (purchase_order_no gin_trgm_ops)
    WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_po_purchase_inbound_supplier_name_trgm
    ON po_purchase_inbound USING gin (supplier_name gin_trgm_ops)
    WHERE deleted_flag = FALSE;

CREATE INDEX IF NOT EXISTS idx_so_sales_order_order_no_trgm
    ON so_sales_order USING gin (order_no gin_trgm_ops)
    WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_so_sales_order_purchase_order_no_trgm
    ON so_sales_order USING gin (purchase_order_no gin_trgm_ops)
    WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_so_sales_order_customer_name_trgm
    ON so_sales_order USING gin (customer_name gin_trgm_ops)
    WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_so_sales_order_project_name_trgm
    ON so_sales_order USING gin (project_name gin_trgm_ops)
    WHERE deleted_flag = FALSE;

CREATE INDEX IF NOT EXISTS idx_so_sales_outbound_outbound_no_trgm
    ON so_sales_outbound USING gin (outbound_no gin_trgm_ops)
    WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_so_sales_outbound_sales_order_no_trgm
    ON so_sales_outbound USING gin (sales_order_no gin_trgm_ops)
    WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_so_sales_outbound_customer_name_trgm
    ON so_sales_outbound USING gin (customer_name gin_trgm_ops)
    WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_so_sales_outbound_project_name_trgm
    ON so_sales_outbound USING gin (project_name gin_trgm_ops)
    WHERE deleted_flag = FALSE;

CREATE INDEX IF NOT EXISTS idx_lg_freight_bill_bill_no_trgm
    ON lg_freight_bill USING gin (bill_no gin_trgm_ops)
    WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_lg_freight_bill_carrier_name_trgm
    ON lg_freight_bill USING gin (carrier_name gin_trgm_ops)
    WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_lg_freight_bill_customer_name_trgm
    ON lg_freight_bill USING gin (customer_name gin_trgm_ops)
    WHERE deleted_flag = FALSE;

CREATE INDEX IF NOT EXISTS idx_ct_purchase_contract_contract_no_trgm
    ON ct_purchase_contract USING gin (contract_no gin_trgm_ops)
    WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_ct_purchase_contract_supplier_name_trgm
    ON ct_purchase_contract USING gin (supplier_name gin_trgm_ops)
    WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_ct_purchase_contract_buyer_name_trgm
    ON ct_purchase_contract USING gin (buyer_name gin_trgm_ops)
    WHERE deleted_flag = FALSE;

CREATE INDEX IF NOT EXISTS idx_ct_sales_contract_contract_no_trgm
    ON ct_sales_contract USING gin (contract_no gin_trgm_ops)
    WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_ct_sales_contract_customer_name_trgm
    ON ct_sales_contract USING gin (customer_name gin_trgm_ops)
    WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_ct_sales_contract_project_name_trgm
    ON ct_sales_contract USING gin (project_name gin_trgm_ops)
    WHERE deleted_flag = FALSE;

CREATE INDEX IF NOT EXISTS idx_st_supplier_statement_statement_no_trgm
    ON st_supplier_statement USING gin (statement_no gin_trgm_ops)
    WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_st_supplier_statement_supplier_name_trgm
    ON st_supplier_statement USING gin (supplier_name gin_trgm_ops)
    WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_st_supplier_statement_source_inbound_nos_trgm
    ON st_supplier_statement USING gin (source_inbound_nos gin_trgm_ops)
    WHERE deleted_flag = FALSE;

CREATE INDEX IF NOT EXISTS idx_st_customer_statement_statement_no_trgm
    ON st_customer_statement USING gin (statement_no gin_trgm_ops)
    WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_st_customer_statement_customer_name_trgm
    ON st_customer_statement USING gin (customer_name gin_trgm_ops)
    WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_st_customer_statement_project_name_trgm
    ON st_customer_statement USING gin (project_name gin_trgm_ops)
    WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_st_customer_statement_source_order_nos_trgm
    ON st_customer_statement USING gin (source_order_nos gin_trgm_ops)
    WHERE deleted_flag = FALSE;

CREATE INDEX IF NOT EXISTS idx_st_freight_statement_statement_no_trgm
    ON st_freight_statement USING gin (statement_no gin_trgm_ops)
    WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_st_freight_statement_carrier_name_trgm
    ON st_freight_statement USING gin (carrier_name gin_trgm_ops)
    WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_st_freight_statement_source_bill_nos_trgm
    ON st_freight_statement USING gin (source_bill_nos gin_trgm_ops)
    WHERE deleted_flag = FALSE;

CREATE INDEX IF NOT EXISTS idx_fm_receipt_receipt_no_trgm
    ON fm_receipt USING gin (receipt_no gin_trgm_ops)
    WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_fm_receipt_customer_name_trgm
    ON fm_receipt USING gin (customer_name gin_trgm_ops)
    WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_fm_receipt_project_name_trgm
    ON fm_receipt USING gin (project_name gin_trgm_ops)
    WHERE deleted_flag = FALSE;

CREATE INDEX IF NOT EXISTS idx_fm_payment_payment_no_trgm
    ON fm_payment USING gin (payment_no gin_trgm_ops)
    WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_fm_payment_counterparty_name_trgm
    ON fm_payment USING gin (counterparty_name gin_trgm_ops)
    WHERE deleted_flag = FALSE;

CREATE INDEX IF NOT EXISTS idx_fm_invoice_receipt_receive_no_trgm
    ON fm_invoice_receipt USING gin (receive_no gin_trgm_ops)
    WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_fm_invoice_receipt_invoice_no_trgm
    ON fm_invoice_receipt USING gin (invoice_no gin_trgm_ops)
    WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_fm_invoice_receipt_supplier_name_trgm
    ON fm_invoice_receipt USING gin (supplier_name gin_trgm_ops)
    WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_fm_invoice_receipt_source_purchase_order_nos_trgm
    ON fm_invoice_receipt USING gin (source_purchase_order_nos gin_trgm_ops)
    WHERE deleted_flag = FALSE;

CREATE INDEX IF NOT EXISTS idx_fm_invoice_issue_issue_no_trgm
    ON fm_invoice_issue USING gin (issue_no gin_trgm_ops)
    WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_fm_invoice_issue_invoice_no_trgm
    ON fm_invoice_issue USING gin (invoice_no gin_trgm_ops)
    WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_fm_invoice_issue_customer_name_trgm
    ON fm_invoice_issue USING gin (customer_name gin_trgm_ops)
    WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_fm_invoice_issue_project_name_trgm
    ON fm_invoice_issue USING gin (project_name gin_trgm_ops)
    WHERE deleted_flag = FALSE;
CREATE INDEX IF NOT EXISTS idx_fm_invoice_issue_source_sales_order_nos_trgm
    ON fm_invoice_issue USING gin (source_sales_order_nos gin_trgm_ops)
    WHERE deleted_flag = FALSE;
