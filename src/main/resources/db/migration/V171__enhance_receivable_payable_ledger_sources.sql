ALTER TABLE st_supplier_statement ADD COLUMN IF NOT EXISTS supplier_code VARCHAR(64);
ALTER TABLE st_freight_statement ADD COLUMN IF NOT EXISTS carrier_code VARCHAR(64);
ALTER TABLE fm_payment ADD COLUMN IF NOT EXISTS counterparty_code VARCHAR(64);

WITH supplier_by_name AS (
    SELECT DISTINCT ON (LOWER(BTRIM(supplier_name)))
        LOWER(BTRIM(supplier_name)) AS supplier_name_key,
        supplier_code
    FROM md_supplier
    WHERE deleted_flag = FALSE
      AND COALESCE(BTRIM(supplier_name), '') <> ''
    ORDER BY LOWER(BTRIM(supplier_name)), supplier_code ASC
)
UPDATE st_supplier_statement statement
SET supplier_code = supplier_by_name.supplier_code
FROM supplier_by_name
WHERE statement.deleted_flag = FALSE
  AND COALESCE(BTRIM(statement.supplier_code), '') = ''
  AND LOWER(BTRIM(statement.supplier_name)) = supplier_by_name.supplier_name_key;

WITH carrier_by_name AS (
    SELECT DISTINCT ON (LOWER(BTRIM(carrier_name)))
        LOWER(BTRIM(carrier_name)) AS carrier_name_key,
        carrier_code
    FROM md_carrier
    WHERE deleted_flag = FALSE
      AND COALESCE(BTRIM(carrier_name), '') <> ''
    ORDER BY LOWER(BTRIM(carrier_name)), carrier_code ASC
)
UPDATE st_freight_statement statement
SET carrier_code = carrier_by_name.carrier_code
FROM carrier_by_name
WHERE statement.deleted_flag = FALSE
  AND COALESCE(BTRIM(statement.carrier_code), '') = ''
  AND LOWER(BTRIM(statement.carrier_name)) = carrier_by_name.carrier_name_key;

WITH supplier_by_name AS (
    SELECT DISTINCT ON (LOWER(BTRIM(supplier_name)))
        LOWER(BTRIM(supplier_name)) AS supplier_name_key,
        supplier_code
    FROM md_supplier
    WHERE deleted_flag = FALSE
      AND COALESCE(BTRIM(supplier_name), '') <> ''
    ORDER BY LOWER(BTRIM(supplier_name)), supplier_code ASC
)
UPDATE fm_payment payment
SET counterparty_code = supplier_by_name.supplier_code
FROM supplier_by_name
WHERE payment.deleted_flag = FALSE
  AND payment.business_type = '供应商'
  AND COALESCE(BTRIM(payment.counterparty_code), '') = ''
  AND LOWER(BTRIM(payment.counterparty_name)) = supplier_by_name.supplier_name_key;

WITH carrier_by_name AS (
    SELECT DISTINCT ON (LOWER(BTRIM(carrier_name)))
        LOWER(BTRIM(carrier_name)) AS carrier_name_key,
        carrier_code
    FROM md_carrier
    WHERE deleted_flag = FALSE
      AND COALESCE(BTRIM(carrier_name), '') <> ''
    ORDER BY LOWER(BTRIM(carrier_name)), carrier_code ASC
)
UPDATE fm_payment payment
SET counterparty_code = carrier_by_name.carrier_code
FROM carrier_by_name
WHERE payment.deleted_flag = FALSE
  AND payment.business_type = '物流商'
  AND COALESCE(BTRIM(payment.counterparty_code), '') = ''
  AND LOWER(BTRIM(payment.counterparty_name)) = carrier_by_name.carrier_name_key;

CREATE INDEX IF NOT EXISTS idx_st_supplier_statement_supplier_code
    ON st_supplier_statement (supplier_code)
    WHERE deleted_flag = FALSE;

CREATE INDEX IF NOT EXISTS idx_st_freight_statement_carrier_code
    ON st_freight_statement (carrier_code)
    WHERE deleted_flag = FALSE;

CREATE INDEX IF NOT EXISTS idx_fm_payment_counterparty_code
    ON fm_payment (business_type, counterparty_code)
    WHERE deleted_flag = FALSE;
