-- V127: Safe column length adjustments (no data loss risk)
-- Expansions: spec, projectName
-- Reductions: passwordHash, tokenId, requestMethod

-- M1: bcrypt output is 60 chars, argon2id max ~128 chars
ALTER TABLE sys_user ALTER COLUMN password_hash TYPE VARCHAR(128);

-- H2: Unify project_name across all tables to 200
ALTER TABLE md_customer ALTER COLUMN project_name TYPE VARCHAR(200);

-- L6: spec for complex steel descriptions (e.g. "不锈钢无缝钢管 304 89*4.5 6m GB/T14976")
ALTER TABLE md_material ALTER COLUMN spec TYPE VARCHAR(64);
ALTER TABLE po_purchase_order_item ALTER COLUMN spec TYPE VARCHAR(64);
ALTER TABLE so_sales_order_item ALTER COLUMN spec TYPE VARCHAR(64);
ALTER TABLE po_purchase_inbound_item ALTER COLUMN spec TYPE VARCHAR(64);
ALTER TABLE so_sales_outbound_item ALTER COLUMN spec TYPE VARCHAR(64);
ALTER TABLE ct_sales_contract_item ALTER COLUMN spec TYPE VARCHAR(64);
ALTER TABLE ct_purchase_contract_item ALTER COLUMN spec TYPE VARCHAR(64);
ALTER TABLE fm_invoice_issue_item ALTER COLUMN spec TYPE VARCHAR(64);
ALTER TABLE fm_invoice_receipt_item ALTER COLUMN spec TYPE VARCHAR(64);
ALTER TABLE lg_freight_bill_item ALTER COLUMN spec TYPE VARCHAR(64);
ALTER TABLE st_customer_statement_item ALTER COLUMN spec TYPE VARCHAR(64);
ALTER TABLE st_supplier_statement_item ALTER COLUMN spec TYPE VARCHAR(64);
ALTER TABLE st_freight_statement_item ALTER COLUMN spec TYPE VARCHAR(64);

-- L9: UUID is fixed 36 characters
ALTER TABLE auth_refresh_token ALTER COLUMN token_id TYPE VARCHAR(36);

-- L7: HTTP methods max 6 chars (DELETE)
ALTER TABLE sys_operation_log ALTER COLUMN request_method TYPE VARCHAR(8);
