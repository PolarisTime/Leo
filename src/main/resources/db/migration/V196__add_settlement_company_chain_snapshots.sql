ALTER TABLE po_purchase_inbound
    ADD COLUMN IF NOT EXISTS settlement_company_id BIGINT,
    ADD COLUMN IF NOT EXISTS settlement_company_name VARCHAR(128);

ALTER TABLE po_purchase_inbound_item
    ADD COLUMN IF NOT EXISTS settlement_company_id BIGINT,
    ADD COLUMN IF NOT EXISTS settlement_company_name VARCHAR(128);

ALTER TABLE so_sales_order
    ADD COLUMN IF NOT EXISTS settlement_company_id BIGINT,
    ADD COLUMN IF NOT EXISTS settlement_company_name VARCHAR(128);

ALTER TABLE so_sales_order_item
    ADD COLUMN IF NOT EXISTS settlement_company_id BIGINT,
    ADD COLUMN IF NOT EXISTS settlement_company_name VARCHAR(128);

ALTER TABLE so_sales_outbound
    ADD COLUMN IF NOT EXISTS settlement_company_id BIGINT,
    ADD COLUMN IF NOT EXISTS settlement_company_name VARCHAR(128);

ALTER TABLE so_sales_outbound_item
    ADD COLUMN IF NOT EXISTS settlement_company_id BIGINT,
    ADD COLUMN IF NOT EXISTS settlement_company_name VARCHAR(128);

ALTER TABLE lg_freight_bill
    ADD COLUMN IF NOT EXISTS settlement_company_id BIGINT,
    ADD COLUMN IF NOT EXISTS settlement_company_name VARCHAR(128);

ALTER TABLE lg_freight_bill_item
    ADD COLUMN IF NOT EXISTS source_sales_outbound_item_id BIGINT,
    ADD COLUMN IF NOT EXISTS settlement_company_id BIGINT,
    ADD COLUMN IF NOT EXISTS settlement_company_name VARCHAR(128);

ALTER TABLE st_supplier_statement
    ADD COLUMN IF NOT EXISTS settlement_company_id BIGINT,
    ADD COLUMN IF NOT EXISTS settlement_company_name VARCHAR(128);

ALTER TABLE st_customer_statement
    ADD COLUMN IF NOT EXISTS settlement_company_id BIGINT,
    ADD COLUMN IF NOT EXISTS settlement_company_name VARCHAR(128);

ALTER TABLE st_freight_statement
    ADD COLUMN IF NOT EXISTS settlement_company_id BIGINT,
    ADD COLUMN IF NOT EXISTS settlement_company_name VARCHAR(128);

ALTER TABLE st_freight_statement_item
    ADD COLUMN IF NOT EXISTS source_sales_outbound_item_id BIGINT,
    ADD COLUMN IF NOT EXISTS settlement_company_id BIGINT,
    ADD COLUMN IF NOT EXISTS settlement_company_name VARCHAR(128);

ALTER TABLE fm_receipt
    ADD COLUMN IF NOT EXISTS settlement_company_id BIGINT,
    ADD COLUMN IF NOT EXISTS settlement_company_name VARCHAR(128);

ALTER TABLE fm_invoice_issue
    ADD COLUMN IF NOT EXISTS settlement_company_id BIGINT,
    ADD COLUMN IF NOT EXISTS settlement_company_name VARCHAR(128);

WITH default_company AS (
    SELECT id, company_name
    FROM sys_company_setting
    WHERE deleted_flag = FALSE
    ORDER BY CASE WHEN status = '正常' THEN 0 ELSE 1 END, id ASC
    LIMIT 1
)
UPDATE po_purchase_inbound_item item
SET settlement_company_id = COALESCE(purchase_order.settlement_company_id, default_company.id),
    settlement_company_name = COALESCE(purchase_order.settlement_company_name, default_company.company_name)
FROM default_company, po_purchase_order_item purchase_order_item
JOIN po_purchase_order purchase_order ON purchase_order.id = purchase_order_item.order_id
WHERE item.source_purchase_order_item_id = purchase_order_item.id
  AND item.settlement_company_id IS NULL;

WITH default_company AS (
    SELECT id, company_name
    FROM sys_company_setting
    WHERE deleted_flag = FALSE
    ORDER BY CASE WHEN status = '正常' THEN 0 ELSE 1 END, id ASC
    LIMIT 1
)
UPDATE po_purchase_inbound_item item
SET settlement_company_id = default_company.id,
    settlement_company_name = default_company.company_name
FROM default_company
WHERE item.settlement_company_id IS NULL;

WITH inbound_subject AS (
    SELECT inbound_id,
           MIN(settlement_company_id) AS settlement_company_id,
           MIN(settlement_company_name) AS settlement_company_name,
           COUNT(DISTINCT settlement_company_id) AS subject_count
    FROM po_purchase_inbound_item
    WHERE settlement_company_id IS NOT NULL
    GROUP BY inbound_id
)
UPDATE po_purchase_inbound inbound
SET settlement_company_id = inbound_subject.settlement_company_id,
    settlement_company_name = inbound_subject.settlement_company_name
FROM inbound_subject
WHERE inbound.id = inbound_subject.inbound_id
  AND inbound_subject.subject_count = 1
  AND inbound.deleted_flag = FALSE
  AND inbound.settlement_company_id IS NULL;

WITH inbound_subject AS (
    SELECT inbound_id,
           COUNT(DISTINCT settlement_company_id) AS subject_count
    FROM po_purchase_inbound_item
    WHERE settlement_company_id IS NOT NULL
    GROUP BY inbound_id
)
UPDATE po_purchase_inbound inbound
SET settlement_company_id = NULL,
    settlement_company_name = '多结算主体'
FROM inbound_subject
WHERE inbound.id = inbound_subject.inbound_id
  AND inbound_subject.subject_count > 1
  AND inbound.deleted_flag = FALSE
  AND inbound.settlement_company_id IS NULL;

WITH default_company AS (
    SELECT id, company_name
    FROM sys_company_setting
    WHERE deleted_flag = FALSE
    ORDER BY CASE WHEN status = '正常' THEN 0 ELSE 1 END, id ASC
    LIMIT 1
)
UPDATE po_purchase_inbound inbound
SET settlement_company_id = default_company.id,
    settlement_company_name = default_company.company_name
FROM default_company
WHERE inbound.deleted_flag = FALSE
  AND inbound.settlement_company_id IS NULL;

UPDATE so_sales_order_item item
SET settlement_company_id = source_item.settlement_company_id,
    settlement_company_name = source_item.settlement_company_name
FROM po_purchase_inbound_item source_item
WHERE item.source_inbound_item_id = source_item.id
  AND item.settlement_company_id IS NULL;

UPDATE so_sales_order_item item
SET settlement_company_id = purchase_order.settlement_company_id,
    settlement_company_name = purchase_order.settlement_company_name
FROM po_purchase_order_item purchase_order_item
JOIN po_purchase_order purchase_order ON purchase_order.id = purchase_order_item.order_id
WHERE item.source_purchase_order_item_id = purchase_order_item.id
  AND item.settlement_company_id IS NULL;

WITH default_company AS (
    SELECT id, company_name
    FROM sys_company_setting
    WHERE deleted_flag = FALSE
    ORDER BY CASE WHEN status = '正常' THEN 0 ELSE 1 END, id ASC
    LIMIT 1
)
UPDATE so_sales_order_item item
SET settlement_company_id = default_company.id,
    settlement_company_name = default_company.company_name
FROM default_company
WHERE item.settlement_company_id IS NULL;

UPDATE so_sales_order sales_order
SET settlement_company_id = customer.default_settlement_company_id,
    settlement_company_name = customer.default_settlement_company_name
FROM md_customer customer
WHERE sales_order.customer_code = customer.customer_code
  AND customer.deleted_flag = FALSE
  AND sales_order.deleted_flag = FALSE
  AND sales_order.settlement_company_id IS NULL;

UPDATE so_sales_order sales_order
SET settlement_company_id = customer.default_settlement_company_id,
    settlement_company_name = customer.default_settlement_company_name
FROM md_customer customer
WHERE sales_order.customer_name = customer.customer_name
  AND sales_order.project_name = customer.project_name
  AND customer.deleted_flag = FALSE
  AND sales_order.deleted_flag = FALSE
  AND sales_order.settlement_company_id IS NULL;

WITH default_company AS (
    SELECT id, company_name
    FROM sys_company_setting
    WHERE deleted_flag = FALSE
    ORDER BY CASE WHEN status = '正常' THEN 0 ELSE 1 END, id ASC
    LIMIT 1
)
UPDATE so_sales_order sales_order
SET settlement_company_id = default_company.id,
    settlement_company_name = default_company.company_name
FROM default_company
WHERE sales_order.deleted_flag = FALSE
  AND sales_order.settlement_company_id IS NULL;

UPDATE so_sales_outbound_item item
SET settlement_company_id = source_item.settlement_company_id,
    settlement_company_name = source_item.settlement_company_name
FROM so_sales_order_item source_item
WHERE item.source_sales_order_item_id = source_item.id
  AND item.settlement_company_id IS NULL;

WITH outbound_subject AS (
    SELECT outbound_id,
           MIN(sales_order.settlement_company_id) AS settlement_company_id,
           MIN(sales_order.settlement_company_name) AS settlement_company_name,
           COUNT(DISTINCT sales_order.settlement_company_id) AS subject_count
    FROM so_sales_outbound_item outbound_item
    JOIN so_sales_order_item order_item ON order_item.id = outbound_item.source_sales_order_item_id
    JOIN so_sales_order sales_order ON sales_order.id = order_item.order_id
    WHERE sales_order.settlement_company_id IS NOT NULL
    GROUP BY outbound_id
)
UPDATE so_sales_outbound outbound
SET settlement_company_id = outbound_subject.settlement_company_id,
    settlement_company_name = outbound_subject.settlement_company_name
FROM outbound_subject
WHERE outbound.id = outbound_subject.outbound_id
  AND outbound_subject.subject_count = 1
  AND outbound.deleted_flag = FALSE
  AND outbound.settlement_company_id IS NULL;

WITH outbound_subject AS (
    SELECT outbound_id,
           COUNT(DISTINCT settlement_company_id) AS subject_count
    FROM so_sales_outbound_item
    WHERE settlement_company_id IS NOT NULL
    GROUP BY outbound_id
)
UPDATE so_sales_outbound outbound
SET settlement_company_id = NULL,
    settlement_company_name = '多结算主体'
FROM outbound_subject
WHERE outbound.id = outbound_subject.outbound_id
  AND outbound_subject.subject_count > 1
  AND outbound.deleted_flag = FALSE
  AND outbound.settlement_company_id IS NULL;

UPDATE lg_freight_bill freight_bill
SET settlement_company_id = carrier.default_settlement_company_id,
    settlement_company_name = carrier.default_settlement_company_name
FROM md_carrier carrier
WHERE freight_bill.carrier_name = carrier.carrier_name
  AND carrier.deleted_flag = FALSE
  AND freight_bill.deleted_flag = FALSE
  AND freight_bill.settlement_company_id IS NULL;

UPDATE lg_freight_bill_item item
SET source_sales_outbound_item_id = source_item.id,
    settlement_company_id = source_item.settlement_company_id,
    settlement_company_name = source_item.settlement_company_name
FROM so_sales_outbound outbound
JOIN so_sales_outbound_item source_item ON source_item.outbound_id = outbound.id
WHERE item.source_no = outbound.outbound_no
  AND item.source_sales_outbound_item_id IS NULL
  AND item.material_code = source_item.material_code
  AND COALESCE(item.batch_no, '') = COALESCE(source_item.batch_no, '')
  AND COALESCE(item.warehouse_name, '') = COALESCE(source_item.warehouse_name, '')
  AND item.quantity = source_item.quantity;

WITH statement_subject AS (
    SELECT statement_item.statement_id,
           MIN(inbound.settlement_company_id) AS settlement_company_id,
           MIN(inbound.settlement_company_name) AS settlement_company_name,
           COUNT(DISTINCT inbound.settlement_company_id) AS subject_count
    FROM st_supplier_statement_item statement_item
    JOIN po_purchase_inbound inbound ON inbound.inbound_no = statement_item.source_no
    WHERE inbound.settlement_company_id IS NOT NULL
    GROUP BY statement_item.statement_id
)
UPDATE st_supplier_statement statement
SET settlement_company_id = statement_subject.settlement_company_id,
    settlement_company_name = statement_subject.settlement_company_name
FROM statement_subject
WHERE statement.id = statement_subject.statement_id
  AND statement_subject.subject_count = 1
  AND statement.deleted_flag = FALSE
  AND statement.settlement_company_id IS NULL;

WITH statement_subject AS (
    SELECT statement_item.statement_id,
           MIN(sales_order.settlement_company_id) AS settlement_company_id,
           MIN(sales_order.settlement_company_name) AS settlement_company_name,
           COUNT(DISTINCT sales_order.settlement_company_id) AS subject_count
    FROM st_customer_statement_item statement_item
    JOIN so_sales_order sales_order ON sales_order.order_no = statement_item.source_no
    WHERE sales_order.settlement_company_id IS NOT NULL
    GROUP BY statement_item.statement_id
)
UPDATE st_customer_statement statement
SET settlement_company_id = statement_subject.settlement_company_id,
    settlement_company_name = statement_subject.settlement_company_name
FROM statement_subject
WHERE statement.id = statement_subject.statement_id
  AND statement_subject.subject_count = 1
  AND statement.deleted_flag = FALSE
  AND statement.settlement_company_id IS NULL;

UPDATE st_freight_statement statement
SET settlement_company_id = carrier.default_settlement_company_id,
    settlement_company_name = carrier.default_settlement_company_name
FROM md_carrier carrier
WHERE statement.carrier_name = carrier.carrier_name
  AND carrier.deleted_flag = FALSE
  AND statement.deleted_flag = FALSE
  AND statement.settlement_company_id IS NULL;

UPDATE st_freight_statement_item statement_item
SET source_sales_outbound_item_id = bill_item.source_sales_outbound_item_id,
    settlement_company_id = bill_item.settlement_company_id,
    settlement_company_name = bill_item.settlement_company_name
FROM lg_freight_bill_item bill_item
WHERE statement_item.source_no = bill_item.source_no
  AND statement_item.source_sales_outbound_item_id IS NULL
  AND statement_item.material_code = bill_item.material_code
  AND COALESCE(statement_item.batch_no, '') = COALESCE(bill_item.batch_no, '')
  AND COALESCE(statement_item.warehouse_name, '') = COALESCE(bill_item.warehouse_name, '')
  AND statement_item.quantity = bill_item.quantity;

WITH receipt_subject AS (
    SELECT allocation.receipt_id,
           MIN(statement.settlement_company_id) AS settlement_company_id,
           MIN(statement.settlement_company_name) AS settlement_company_name,
           COUNT(DISTINCT statement.settlement_company_id) AS subject_count
    FROM fm_receipt_allocation allocation
    JOIN st_customer_statement statement ON statement.id = allocation.source_statement_id
    WHERE statement.settlement_company_id IS NOT NULL
    GROUP BY allocation.receipt_id
)
UPDATE fm_receipt receipt
SET settlement_company_id = receipt_subject.settlement_company_id,
    settlement_company_name = receipt_subject.settlement_company_name
FROM receipt_subject
WHERE receipt.id = receipt_subject.receipt_id
  AND receipt_subject.subject_count = 1
  AND receipt.deleted_flag = FALSE
  AND receipt.settlement_company_id IS NULL;

WITH receipt_subject AS (
    SELECT allocation.receipt_id,
           COUNT(DISTINCT statement.settlement_company_id) AS subject_count
    FROM fm_receipt_allocation allocation
    JOIN st_customer_statement statement ON statement.id = allocation.source_statement_id
    WHERE statement.settlement_company_id IS NOT NULL
    GROUP BY allocation.receipt_id
)
UPDATE fm_receipt receipt
SET settlement_company_id = NULL,
    settlement_company_name = '多结算主体'
FROM receipt_subject
WHERE receipt.id = receipt_subject.receipt_id
  AND receipt_subject.subject_count > 1
  AND receipt.deleted_flag = FALSE
  AND receipt.settlement_company_id IS NULL;

WITH invoice_subject AS (
    SELECT item.issue_id AS invoice_id,
           MIN(sales_order.settlement_company_id) AS settlement_company_id,
           MIN(sales_order.settlement_company_name) AS settlement_company_name,
           COUNT(DISTINCT sales_order.settlement_company_id) AS subject_count
    FROM fm_invoice_issue_item item
    JOIN so_sales_order_item order_item ON order_item.id = item.source_sales_order_item_id
    JOIN so_sales_order sales_order ON sales_order.id = order_item.order_id
    WHERE sales_order.settlement_company_id IS NOT NULL
    GROUP BY item.issue_id
)
UPDATE fm_invoice_issue invoice
SET settlement_company_id = invoice_subject.settlement_company_id,
    settlement_company_name = invoice_subject.settlement_company_name
FROM invoice_subject
WHERE invoice.id = invoice_subject.invoice_id
  AND invoice_subject.subject_count = 1
  AND invoice.deleted_flag = FALSE
  AND invoice.settlement_company_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_po_purchase_inbound_settlement_company
    ON po_purchase_inbound (settlement_company_id)
    WHERE deleted_flag = FALSE;

CREATE INDEX IF NOT EXISTS idx_po_purchase_inbound_item_settlement_company
    ON po_purchase_inbound_item (settlement_company_id);

CREATE INDEX IF NOT EXISTS idx_so_sales_order_settlement_company
    ON so_sales_order (settlement_company_id)
    WHERE deleted_flag = FALSE;

CREATE INDEX IF NOT EXISTS idx_so_sales_order_item_settlement_company
    ON so_sales_order_item (settlement_company_id);

CREATE INDEX IF NOT EXISTS idx_so_sales_outbound_settlement_company
    ON so_sales_outbound (settlement_company_id)
    WHERE deleted_flag = FALSE;

CREATE INDEX IF NOT EXISTS idx_so_sales_outbound_item_settlement_company
    ON so_sales_outbound_item (settlement_company_id);

CREATE INDEX IF NOT EXISTS idx_lg_freight_bill_settlement_company
    ON lg_freight_bill (settlement_company_id)
    WHERE deleted_flag = FALSE;

CREATE INDEX IF NOT EXISTS idx_lg_freight_bill_item_source_outbound_item
    ON lg_freight_bill_item (source_sales_outbound_item_id);

CREATE INDEX IF NOT EXISTS idx_lg_freight_bill_item_settlement_company
    ON lg_freight_bill_item (settlement_company_id);

CREATE INDEX IF NOT EXISTS idx_st_supplier_statement_settlement_company
    ON st_supplier_statement (settlement_company_id)
    WHERE deleted_flag = FALSE;

CREATE INDEX IF NOT EXISTS idx_st_customer_statement_settlement_company
    ON st_customer_statement (settlement_company_id)
    WHERE deleted_flag = FALSE;

CREATE INDEX IF NOT EXISTS idx_st_freight_statement_settlement_company
    ON st_freight_statement (settlement_company_id)
    WHERE deleted_flag = FALSE;

CREATE INDEX IF NOT EXISTS idx_st_freight_statement_item_source_outbound_item
    ON st_freight_statement_item (source_sales_outbound_item_id);

CREATE INDEX IF NOT EXISTS idx_st_freight_statement_item_settlement_company
    ON st_freight_statement_item (settlement_company_id);

CREATE INDEX IF NOT EXISTS idx_fm_receipt_settlement_company
    ON fm_receipt (settlement_company_id)
    WHERE deleted_flag = FALSE;

CREATE INDEX IF NOT EXISTS idx_fm_invoice_issue_settlement_company
    ON fm_invoice_issue (settlement_company_id)
    WHERE deleted_flag = FALSE;
