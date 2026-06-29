ALTER TABLE md_customer
    ADD COLUMN IF NOT EXISTS default_settlement_company_id BIGINT,
    ADD COLUMN IF NOT EXISTS default_settlement_company_name VARCHAR(128);

ALTER TABLE md_carrier
    ADD COLUMN IF NOT EXISTS default_settlement_company_id BIGINT,
    ADD COLUMN IF NOT EXISTS default_settlement_company_name VARCHAR(128);

ALTER TABLE po_purchase_order
    ADD COLUMN IF NOT EXISTS settlement_company_id BIGINT,
    ADD COLUMN IF NOT EXISTS settlement_company_name VARCHAR(128);

WITH default_company AS (
    SELECT id, company_name
    FROM sys_company_setting
    WHERE deleted_flag = FALSE
    ORDER BY CASE WHEN status = '正常' THEN 0 ELSE 1 END, id ASC
    LIMIT 1
)
UPDATE md_customer customer
SET default_settlement_company_id = default_company.id,
    default_settlement_company_name = default_company.company_name
FROM default_company
WHERE customer.deleted_flag = FALSE
  AND customer.default_settlement_company_id IS NULL;

WITH default_company AS (
    SELECT id, company_name
    FROM sys_company_setting
    WHERE deleted_flag = FALSE
    ORDER BY CASE WHEN status = '正常' THEN 0 ELSE 1 END, id ASC
    LIMIT 1
)
UPDATE md_carrier carrier
SET default_settlement_company_id = default_company.id,
    default_settlement_company_name = default_company.company_name
FROM default_company
WHERE carrier.deleted_flag = FALSE
  AND carrier.default_settlement_company_id IS NULL;

WITH default_company AS (
    SELECT id, company_name
    FROM sys_company_setting
    WHERE deleted_flag = FALSE
    ORDER BY CASE WHEN status = '正常' THEN 0 ELSE 1 END, id ASC
    LIMIT 1
)
UPDATE po_purchase_order purchase_order
SET settlement_company_id = default_company.id,
    settlement_company_name = default_company.company_name
FROM default_company
WHERE purchase_order.deleted_flag = FALSE
  AND purchase_order.settlement_company_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_md_customer_settlement_company
    ON md_customer (default_settlement_company_id)
    WHERE deleted_flag = FALSE;

CREATE INDEX IF NOT EXISTS idx_md_carrier_settlement_company
    ON md_carrier (default_settlement_company_id)
    WHERE deleted_flag = FALSE;

CREATE INDEX IF NOT EXISTS idx_po_purchase_order_settlement_company
    ON po_purchase_order (settlement_company_id)
    WHERE deleted_flag = FALSE;
