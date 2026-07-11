ALTER TABLE public.lg_freight_bill
    ADD COLUMN carrier_code character varying(64);

-- Historical names are safe only when one active carrier code owns the name.
WITH unique_active_carrier_name AS (
    SELECT
        LOWER(BTRIM(carrier.carrier_name)) AS carrier_name_key,
        MIN(carrier.carrier_code) AS carrier_code
    FROM public.md_carrier carrier
    WHERE carrier.deleted_flag = false
      AND COALESCE(BTRIM(carrier.carrier_name), '') <> ''
      AND COALESCE(BTRIM(carrier.carrier_code), '') <> ''
    GROUP BY LOWER(BTRIM(carrier.carrier_name))
    HAVING COUNT(DISTINCT carrier.carrier_code) = 1
)
UPDATE public.lg_freight_bill freight_bill
SET carrier_code = unique_carrier.carrier_code
FROM unique_active_carrier_name unique_carrier
WHERE LOWER(BTRIM(freight_bill.carrier_name)) = unique_carrier.carrier_name_key
  AND freight_bill.carrier_code IS NULL;

DO $$
DECLARE
    unresolved_freight_bill_count bigint;
BEGIN
    SELECT COUNT(*)
    INTO unresolved_freight_bill_count
    FROM public.lg_freight_bill
    WHERE COALESCE(BTRIM(carrier_code), '') = '';

    IF unresolved_freight_bill_count > 0 THEN
        RAISE EXCEPTION
            '物流单物流商编码回填失败：% 条物流单无法唯一匹配有效物流商编码，请先清理同名或缺失物流商主数据',
            unresolved_freight_bill_count;
    END IF;
END
$$;

ALTER TABLE public.lg_freight_bill
    ALTER COLUMN carrier_code SET NOT NULL;

ALTER TABLE public.lg_freight_bill
    ADD CONSTRAINT chk_lg_freight_bill_carrier_code_not_blank
    CHECK (BTRIM(carrier_code) <> '');

-- A statement may only inherit one stable carrier identity from all source bills.
DO $$
DECLARE
    ambiguous_statement_count bigint;
    conflicting_statement_count bigint;
BEGIN
    SELECT COUNT(*)
    INTO ambiguous_statement_count
    FROM (
        SELECT freight_statement.id
        FROM public.st_freight_statement freight_statement
        JOIN public.st_freight_statement_item statement_item
          ON statement_item.statement_id = freight_statement.id
        JOIN public.lg_freight_bill source_bill
          ON source_bill.bill_no = BTRIM(statement_item.source_no)
        GROUP BY freight_statement.id
        HAVING COUNT(DISTINCT source_bill.carrier_code) > 1
    ) ambiguous_statement;

    IF ambiguous_statement_count > 0 THEN
        RAISE EXCEPTION
            '物流对账单来源物流单存在多个物流商编码：% 条记录需人工修复',
            ambiguous_statement_count;
    END IF;

    SELECT COUNT(*)
    INTO conflicting_statement_count
    FROM (
        SELECT
            freight_statement.id,
            BTRIM(freight_statement.carrier_code) AS statement_carrier_code,
            MIN(source_bill.carrier_code) AS source_carrier_code
        FROM public.st_freight_statement freight_statement
        JOIN public.st_freight_statement_item statement_item
          ON statement_item.statement_id = freight_statement.id
        JOIN public.lg_freight_bill source_bill
          ON source_bill.bill_no = BTRIM(statement_item.source_no)
        WHERE COALESCE(BTRIM(freight_statement.carrier_code), '') <> ''
        GROUP BY freight_statement.id, BTRIM(freight_statement.carrier_code)
        HAVING COUNT(DISTINCT source_bill.carrier_code) = 1
           AND BTRIM(freight_statement.carrier_code) <> MIN(source_bill.carrier_code)
    ) conflicting_statement;

    IF conflicting_statement_count > 0 THEN
        RAISE EXCEPTION
            '物流对账单物流商编码与来源物流单冲突：% 条记录需人工修复',
            conflicting_statement_count;
    END IF;
END
$$;

WITH statement_source_carrier AS (
    SELECT
        freight_statement.id AS statement_id,
        MIN(source_bill.carrier_code) AS carrier_code
    FROM public.st_freight_statement freight_statement
    JOIN public.st_freight_statement_item statement_item
      ON statement_item.statement_id = freight_statement.id
    JOIN public.lg_freight_bill source_bill
      ON source_bill.bill_no = BTRIM(statement_item.source_no)
    GROUP BY freight_statement.id
    HAVING COUNT(DISTINCT source_bill.carrier_code) = 1
)
UPDATE public.st_freight_statement freight_statement
SET carrier_code = source_carrier.carrier_code
FROM statement_source_carrier source_carrier
WHERE source_carrier.statement_id = freight_statement.id
  AND COALESCE(BTRIM(freight_statement.carrier_code), '') = '';

-- Legacy statements without resolvable source bills use the same fail-closed name rule.
WITH unique_active_carrier_name AS (
    SELECT
        LOWER(BTRIM(carrier.carrier_name)) AS carrier_name_key,
        MIN(carrier.carrier_code) AS carrier_code
    FROM public.md_carrier carrier
    WHERE carrier.deleted_flag = false
      AND COALESCE(BTRIM(carrier.carrier_name), '') <> ''
      AND COALESCE(BTRIM(carrier.carrier_code), '') <> ''
    GROUP BY LOWER(BTRIM(carrier.carrier_name))
    HAVING COUNT(DISTINCT carrier.carrier_code) = 1
)
UPDATE public.st_freight_statement freight_statement
SET carrier_code = unique_carrier.carrier_code
FROM unique_active_carrier_name unique_carrier
WHERE LOWER(BTRIM(freight_statement.carrier_name)) = unique_carrier.carrier_name_key
  AND COALESCE(BTRIM(freight_statement.carrier_code), '') = '';

DO $$
DECLARE
    unresolved_statement_count bigint;
BEGIN
    SELECT COUNT(*)
    INTO unresolved_statement_count
    FROM public.st_freight_statement
    WHERE COALESCE(BTRIM(carrier_code), '') = '';

    IF unresolved_statement_count > 0 THEN
        RAISE EXCEPTION
            '物流对账单物流商编码回填失败：% 条记录无法确定唯一物流商编码',
            unresolved_statement_count;
    END IF;
END
$$;

UPDATE public.st_freight_statement
SET carrier_code = BTRIM(carrier_code)
WHERE carrier_code <> BTRIM(carrier_code);

ALTER TABLE public.st_freight_statement
    ALTER COLUMN carrier_code SET NOT NULL;

ALTER TABLE public.st_freight_statement
    ADD CONSTRAINT chk_st_freight_statement_carrier_code_not_blank
    CHECK (BTRIM(carrier_code) <> '');

COMMENT ON COLUMN public.lg_freight_bill.carrier_code IS '物流商稳定身份编码快照';
COMMENT ON COLUMN public.st_freight_statement.carrier_code IS '物流商稳定身份编码快照';

CREATE INDEX idx_lg_freight_bill_carrier_code_date
    ON public.lg_freight_bill (carrier_code, bill_time DESC)
    WHERE deleted_flag = false;
