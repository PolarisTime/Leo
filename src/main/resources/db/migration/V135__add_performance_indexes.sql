-- V135: Add performance indexes for high-frequency query fields
-- Focus on status, document numbers, and foreign key columns

-- Status indexes (filtered by common queries)
CREATE INDEX IF NOT EXISTS idx_so_sales_order_status ON so_sales_order (status);
CREATE INDEX IF NOT EXISTS idx_po_purchase_order_status ON po_purchase_order (status);
CREATE INDEX IF NOT EXISTS idx_po_purchase_inbound_status ON po_purchase_inbound (status);
CREATE INDEX IF NOT EXISTS idx_so_sales_outbound_status ON so_sales_outbound (status);
CREATE INDEX IF NOT EXISTS idx_lg_freight_bill_status ON lg_freight_bill (status);
CREATE INDEX IF NOT EXISTS idx_ct_sales_contract_status ON ct_sales_contract (status);
CREATE INDEX IF NOT EXISTS idx_ct_purchase_contract_status ON ct_purchase_contract (status);
CREATE INDEX IF NOT EXISTS idx_st_customer_statement_status ON st_customer_statement (status);
CREATE INDEX IF NOT EXISTS idx_st_supplier_statement_status ON st_supplier_statement (status);
CREATE INDEX IF NOT EXISTS idx_st_freight_statement_status ON st_freight_statement (status);
CREATE INDEX IF NOT EXISTS idx_fm_payment_status ON fm_payment (status);
CREATE INDEX IF NOT EXISTS idx_fm_receipt_status ON fm_receipt (status);
CREATE INDEX IF NOT EXISTS idx_fm_invoice_issue_status ON fm_invoice_issue (status);
CREATE INDEX IF NOT EXISTS idx_fm_invoice_receipt_status ON fm_invoice_receipt (status);

-- Document date indexes (time-range queries)
CREATE INDEX IF NOT EXISTS idx_so_sales_order_delivery_date ON so_sales_order (delivery_date);
CREATE INDEX IF NOT EXISTS idx_po_purchase_order_order_date ON po_purchase_order (order_date);
CREATE INDEX IF NOT EXISTS idx_po_purchase_inbound_inbound_date ON po_purchase_inbound (inbound_date);
CREATE INDEX IF NOT EXISTS idx_so_sales_outbound_outbound_date ON so_sales_outbound (outbound_date);
CREATE INDEX IF NOT EXISTS idx_lg_freight_bill_bill_time ON lg_freight_bill (bill_time);

-- Foreign key indexes for item tables (JOIN performance)
CREATE INDEX IF NOT EXISTS idx_so_sales_order_item_order_id ON so_sales_order_item (order_id);
CREATE INDEX IF NOT EXISTS idx_so_sales_outbound_item_outbound_id ON so_sales_outbound_item (outbound_id);
CREATE INDEX IF NOT EXISTS idx_po_purchase_inbound_item_inbound_id ON po_purchase_inbound_item (inbound_id);
CREATE INDEX IF NOT EXISTS idx_ct_sales_contract_item_contract_id ON ct_sales_contract_item (contract_id);
CREATE INDEX IF NOT EXISTS idx_ct_purchase_contract_item_contract_id ON ct_purchase_contract_item (contract_id);
CREATE INDEX IF NOT EXISTS idx_lg_freight_bill_item_bill_id ON lg_freight_bill_item (bill_id);
CREATE INDEX IF NOT EXISTS idx_st_customer_statement_item_statement_id ON st_customer_statement_item (statement_id);
CREATE INDEX IF NOT EXISTS idx_st_supplier_statement_item_statement_id ON st_supplier_statement_item (statement_id);
CREATE INDEX IF NOT EXISTS idx_st_freight_statement_item_statement_id ON st_freight_statement_item (statement_id);
CREATE INDEX IF NOT EXISTS idx_fm_invoice_issue_item_issue_id ON fm_invoice_issue_item (issue_id);
CREATE INDEX IF NOT EXISTS idx_fm_invoice_receipt_item_receipt_id ON fm_invoice_receipt_item (receipt_id);
CREATE INDEX IF NOT EXISTS idx_fm_payment_allocation_payment_id ON fm_payment_allocation (payment_id);
CREATE INDEX IF NOT EXISTS idx_fm_receipt_allocation_receipt_id ON fm_receipt_allocation (receipt_id);

-- Customer/Supplier name indexes (search/lookup)
CREATE INDEX IF NOT EXISTS idx_md_customer_name ON md_customer (customer_name);
CREATE INDEX IF NOT EXISTS idx_md_supplier_name ON md_supplier (supplier_name);
CREATE INDEX IF NOT EXISTS idx_md_carrier_name ON md_carrier (carrier_name);
CREATE INDEX IF NOT EXISTS idx_md_warehouse_name ON md_warehouse (warehouse_name);
CREATE INDEX IF NOT EXISTS idx_md_material_code ON md_material (material_code);
