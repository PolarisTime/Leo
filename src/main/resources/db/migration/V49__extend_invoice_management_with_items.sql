ALTER TABLE fm_invoice_receipt
    ADD COLUMN IF NOT EXISTS source_purchase_order_nos VARCHAR(500);

ALTER TABLE fm_invoice_issue
    ADD COLUMN IF NOT EXISTS source_sales_order_nos VARCHAR(500);

CREATE TABLE IF NOT EXISTS fm_invoice_receipt_item (
    id BIGINT PRIMARY KEY,
    receipt_id BIGINT NOT NULL,
    line_no INTEGER NOT NULL,
    source_no VARCHAR(64) NOT NULL,
    source_purchase_order_item_id BIGINT,
    material_code VARCHAR(64) NOT NULL,
    brand VARCHAR(64) NOT NULL,
    category VARCHAR(32) NOT NULL,
    material VARCHAR(32) NOT NULL,
    spec VARCHAR(32) NOT NULL,
    length VARCHAR(32),
    unit VARCHAR(16) NOT NULL,
    warehouse_name VARCHAR(128),
    batch_no VARCHAR(64),
    quantity INTEGER NOT NULL,
    quantity_unit VARCHAR(16) NOT NULL,
    piece_weight_ton NUMERIC(12, 3) NOT NULL,
    pieces_per_bundle INTEGER NOT NULL,
    weight_ton NUMERIC(14, 3) NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    amount NUMERIC(14, 2) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_fm_invoice_receipt_item_receipt
    ON fm_invoice_receipt_item (receipt_id, line_no);

CREATE INDEX IF NOT EXISTS idx_fm_invoice_receipt_item_source
    ON fm_invoice_receipt_item (source_no, source_purchase_order_item_id);

CREATE TABLE IF NOT EXISTS fm_invoice_issue_item (
    id BIGINT PRIMARY KEY,
    issue_id BIGINT NOT NULL,
    line_no INTEGER NOT NULL,
    source_no VARCHAR(64) NOT NULL,
    source_sales_order_item_id BIGINT,
    material_code VARCHAR(64) NOT NULL,
    brand VARCHAR(64) NOT NULL,
    category VARCHAR(32) NOT NULL,
    material VARCHAR(32) NOT NULL,
    spec VARCHAR(32) NOT NULL,
    length VARCHAR(32),
    unit VARCHAR(16) NOT NULL,
    warehouse_name VARCHAR(128),
    batch_no VARCHAR(64),
    quantity INTEGER NOT NULL,
    quantity_unit VARCHAR(16) NOT NULL,
    piece_weight_ton NUMERIC(12, 3) NOT NULL,
    pieces_per_bundle INTEGER NOT NULL,
    weight_ton NUMERIC(14, 3) NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    amount NUMERIC(14, 2) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_fm_invoice_issue_item_issue
    ON fm_invoice_issue_item (issue_id, line_no);

CREATE INDEX IF NOT EXISTS idx_fm_invoice_issue_item_source
    ON fm_invoice_issue_item (source_no, source_sales_order_item_id);
