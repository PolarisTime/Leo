ALTER TABLE so_sales_order ADD COLUMN IF NOT EXISTS customer_code VARCHAR(64);
ALTER TABLE so_sales_order ADD COLUMN IF NOT EXISTS project_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_so_sales_order_project_id ON so_sales_order (project_id);
CREATE INDEX IF NOT EXISTS idx_so_sales_order_customer_code ON so_sales_order (customer_code);

ALTER TABLE fm_receipt ADD COLUMN IF NOT EXISTS customer_code VARCHAR(64);
ALTER TABLE fm_receipt ADD COLUMN IF NOT EXISTS project_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_fm_receipt_project_id ON fm_receipt (project_id);
CREATE INDEX IF NOT EXISTS idx_fm_receipt_customer_code ON fm_receipt (customer_code);

ALTER TABLE st_customer_statement ADD COLUMN IF NOT EXISTS customer_code VARCHAR(64);
ALTER TABLE st_customer_statement ADD COLUMN IF NOT EXISTS project_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_st_customer_statement_project_id ON st_customer_statement (project_id);
CREATE INDEX IF NOT EXISTS idx_st_customer_statement_customer_code ON st_customer_statement (customer_code);

ALTER TABLE st_customer_statement_item ADD COLUMN IF NOT EXISTS project_id BIGINT;
ALTER TABLE st_customer_statement_item ADD COLUMN IF NOT EXISTS customer_code VARCHAR(64);
