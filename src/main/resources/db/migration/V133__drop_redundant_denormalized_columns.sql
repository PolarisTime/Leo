-- V133: Drop redundant denormalized columns
-- Service layer updated in sync to stop reading/writing these fields

-- Statement source Nos (items tables already track individual source_no)
ALTER TABLE st_customer_statement DROP COLUMN IF EXISTS source_order_nos;
ALTER TABLE st_supplier_statement DROP COLUMN IF EXISTS source_inbound_nos;
ALTER TABLE st_freight_statement DROP COLUMN IF EXISTS source_bill_nos;

-- Invoice/Contract source Nos (replaced by association tables in V132)
ALTER TABLE fm_invoice_issue DROP COLUMN IF EXISTS source_sales_order_nos;
ALTER TABLE fm_invoice_receipt DROP COLUMN IF EXISTS source_purchase_order_nos;
ALTER TABLE ct_purchase_contract DROP COLUMN IF EXISTS source_purchase_order_nos;

-- FreightBill outbound_no (items.source_no tracks individual outbounds)
ALTER TABLE lg_freight_bill DROP COLUMN IF EXISTS outbound_no;

-- FreightStatement attachment_ids (use sys_attachment_binding table)
ALTER TABLE st_freight_statement DROP COLUMN IF EXISTS attachment_ids;

-- UserAccount role_name (use sys_user_role join table)
ALTER TABLE sys_user DROP COLUMN IF EXISTS role_name;

-- RoleSetting permission_codes (use sys_role_permission join table)
ALTER TABLE sys_role DROP COLUMN IF EXISTS permission_codes;
