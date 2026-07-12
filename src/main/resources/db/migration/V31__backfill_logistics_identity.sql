DO $$
BEGIN
    IF EXISTS (
        SELECT carrier_code FROM public.md_carrier GROUP BY carrier_code HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V31: md_carrier 全历史 carrier_code 不唯一';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM (
            SELECT carrier_code FROM public.lg_freight_bill
            UNION ALL SELECT carrier_code FROM public.st_freight_statement
        ) refs
        LEFT JOIN public.md_carrier carrier ON carrier.carrier_code = refs.carrier_code
        WHERE NULLIF(BTRIM(refs.carrier_code), '') IS NULL OR carrier.id IS NULL
    ) THEN
        RAISE EXCEPTION 'V31: 物流链存在无法按 carrier_code 解析的物流商';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.st_freight_statement_item statement_item
        LEFT JOIN LATERAL (
            SELECT COUNT(*) AS candidate_count
            FROM public.lg_freight_bill_item bill_item
            JOIN public.lg_freight_bill bill ON bill.id = bill_item.bill_id
            WHERE bill.bill_no = statement_item.source_no
              AND (statement_item.source_sales_outbound_item_id IS NULL
                   OR bill_item.source_sales_outbound_item_id = statement_item.source_sales_outbound_item_id)
        ) candidates ON true
        WHERE statement_item.source_freight_bill_item_id IS NULL
          AND candidates.candidate_count <> 1
    ) THEN
        RAISE EXCEPTION 'V31: 物流对账明细无法唯一定位直接物流单明细';
    END IF;
END $$;

UPDATE public.lg_freight_bill bill SET carrier_id = carrier.id
FROM public.md_carrier carrier WHERE bill.carrier_id IS NULL AND carrier.carrier_code = bill.carrier_code;
UPDATE public.st_freight_statement statement SET carrier_id = carrier.id
FROM public.md_carrier carrier WHERE statement.carrier_id IS NULL AND carrier.carrier_code = statement.carrier_code;

WITH source AS (
    SELECT statement_item.id AS statement_item_id,
           MIN(bill.id) AS bill_id,
           MIN(bill_item.id) AS item_id
    FROM public.st_freight_statement_item statement_item
    JOIN public.lg_freight_bill bill ON bill.bill_no = statement_item.source_no
    JOIN public.lg_freight_bill_item bill_item ON bill_item.bill_id = bill.id
      AND (statement_item.source_sales_outbound_item_id IS NULL
           OR bill_item.source_sales_outbound_item_id = statement_item.source_sales_outbound_item_id)
    WHERE statement_item.source_freight_bill_item_id IS NULL
    GROUP BY statement_item.id
    HAVING COUNT(*) = 1
)
UPDATE public.st_freight_statement_item statement_item
SET source_freight_bill_id = source.bill_id,
    source_freight_bill_item_id = source.item_id
FROM source
WHERE statement_item.id = source.statement_item_id;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.lg_freight_bill WHERE carrier_id IS NULL)
       OR EXISTS (SELECT 1 FROM public.st_freight_statement WHERE carrier_id IS NULL)
       OR EXISTS (SELECT 1 FROM public.st_freight_statement_item WHERE source_freight_bill_id IS NULL OR source_freight_bill_item_id IS NULL) THEN
        RAISE EXCEPTION 'V31: 物流稳定身份回填后仍有空值';
    END IF;
END $$;
