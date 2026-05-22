-- V132: Create association tables for comma-separated source order numbers
-- M5: Replace String.join(", ", nos) anti-pattern with normalized join tables

-- InvoiceIssue ↔ SalesOrder (replaces fm_invoice_issue.source_sales_order_nos)
CREATE TABLE IF NOT EXISTS fm_invoice_issue_source_order (
    id BIGINT PRIMARY KEY,
    issue_id BIGINT NOT NULL,
    sales_order_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_name VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_issue_src_order_issue FOREIGN KEY (issue_id) REFERENCES fm_invoice_issue (id),
    CONSTRAINT fk_issue_src_order_so FOREIGN KEY (sales_order_id) REFERENCES so_sales_order (id),
    CONSTRAINT uq_issue_src_order UNIQUE (issue_id, sales_order_id)
);
CREATE INDEX IF NOT EXISTS idx_issue_src_issue_id ON fm_invoice_issue_source_order (issue_id);
CREATE INDEX IF NOT EXISTS idx_issue_src_order_id ON fm_invoice_issue_source_order (sales_order_id);

-- InvoiceReceipt ↔ PurchaseOrder (replaces fm_invoice_receipt.source_purchase_order_nos)
CREATE TABLE IF NOT EXISTS fm_invoice_receipt_source_order (
    id BIGINT PRIMARY KEY,
    receipt_id BIGINT NOT NULL,
    purchase_order_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_name VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_receipt_src_order_receipt FOREIGN KEY (receipt_id) REFERENCES fm_invoice_receipt (id),
    CONSTRAINT fk_receipt_src_order_po FOREIGN KEY (purchase_order_id) REFERENCES po_purchase_order (id),
    CONSTRAINT uq_receipt_src_order UNIQUE (receipt_id, purchase_order_id)
);
CREATE INDEX IF NOT EXISTS idx_receipt_src_receipt_id ON fm_invoice_receipt_source_order (receipt_id);
CREATE INDEX IF NOT EXISTS idx_receipt_src_order_id ON fm_invoice_receipt_source_order (purchase_order_id);

-- PurchaseContract ↔ PurchaseOrder (replaces ct_purchase_contract.source_purchase_order_nos)
CREATE TABLE IF NOT EXISTS ct_contract_purchase_order (
    id BIGINT PRIMARY KEY,
    contract_id BIGINT NOT NULL,
    purchase_order_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_name VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ctr_po_contract FOREIGN KEY (contract_id) REFERENCES ct_purchase_contract (id),
    CONSTRAINT fk_ctr_po_order FOREIGN KEY (purchase_order_id) REFERENCES po_purchase_order (id),
    CONSTRAINT uq_ctr_po UNIQUE (contract_id, purchase_order_id)
);
CREATE INDEX IF NOT EXISTS idx_ctr_po_contract_id ON ct_contract_purchase_order (contract_id);
CREATE INDEX IF NOT EXISTS idx_ctr_po_order_id ON ct_contract_purchase_order (purchase_order_id);
