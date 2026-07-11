ALTER TABLE public.fm_invoice_receipt
    ADD COLUMN supplier_code character varying(64),
    ADD COLUMN settlement_company_id bigint,
    ADD COLUMN settlement_company_name character varying(128);

WITH receipt_source_identity AS (
    SELECT
        receipt_item.receipt_id,
        MIN(source_order.supplier_code) AS supplier_code,
        CASE
            WHEN COUNT(DISTINCT source_order.settlement_company_id) = 1
             AND COUNT(DISTINCT source_order.settlement_company_name) = 1
            THEN MIN(source_order.settlement_company_id)
        END AS settlement_company_id,
        CASE
            WHEN COUNT(DISTINCT source_order.settlement_company_id) = 1
             AND COUNT(DISTINCT source_order.settlement_company_name) = 1
            THEN MIN(source_order.settlement_company_name)
        END AS settlement_company_name
    FROM public.fm_invoice_receipt_item receipt_item
    JOIN public.po_purchase_order_item source_item
      ON source_item.id = receipt_item.source_purchase_order_item_id
    JOIN public.po_purchase_order source_order
      ON source_order.id = source_item.order_id
    WHERE COALESCE(BTRIM(source_order.supplier_code), '') <> ''
    GROUP BY receipt_item.receipt_id
    HAVING COUNT(DISTINCT source_order.supplier_code) = 1
)
UPDATE public.fm_invoice_receipt receipt
SET supplier_code = source_identity.supplier_code,
    settlement_company_id = source_identity.settlement_company_id,
    settlement_company_name = source_identity.settlement_company_name
FROM receipt_source_identity source_identity
WHERE source_identity.receipt_id = receipt.id;

WITH unique_supplier_name AS (
    SELECT
        LOWER(BTRIM(supplier.supplier_name)) AS supplier_name_key,
        MIN(supplier.supplier_code) AS supplier_code
    FROM public.md_supplier supplier
    WHERE COALESCE(BTRIM(supplier.supplier_name), '') <> ''
      AND COALESCE(BTRIM(supplier.supplier_code), '') <> ''
    GROUP BY LOWER(BTRIM(supplier.supplier_name))
    HAVING COUNT(DISTINCT supplier.supplier_code) = 1
)
UPDATE public.fm_invoice_receipt receipt
SET supplier_code = unique_supplier_name.supplier_code
FROM unique_supplier_name
WHERE LOWER(BTRIM(receipt.supplier_name)) = unique_supplier_name.supplier_name_key
  AND receipt.supplier_code IS NULL;

DO $$
DECLARE
    unresolved_receipt_count bigint;
BEGIN
    SELECT COUNT(*)
    INTO unresolved_receipt_count
    FROM public.fm_invoice_receipt
    WHERE COALESCE(BTRIM(supplier_code), '') = '';

    IF unresolved_receipt_count > 0 THEN
        RAISE EXCEPTION
            '收票单供应商编码回填失败：% 条收票单无法唯一匹配供应商编码',
            unresolved_receipt_count;
    END IF;
END
$$;

ALTER TABLE public.fm_invoice_receipt
    ALTER COLUMN supplier_code SET NOT NULL;

UPDATE public.fm_invoice_receipt
SET invoice_title = settlement_company_name
WHERE settlement_company_name IS NOT NULL
  AND COALESCE(BTRIM(settlement_company_name), '') <> '';

COMMENT ON COLUMN public.fm_invoice_receipt.supplier_code IS '供应商稳定身份编码快照';
COMMENT ON COLUMN public.fm_invoice_receipt.settlement_company_id IS '采购结算主体标识快照';
COMMENT ON COLUMN public.fm_invoice_receipt.settlement_company_name IS '采购结算主体名称快照';

CREATE INDEX idx_fm_invoice_receipt_supplier_code_date
    ON public.fm_invoice_receipt (supplier_code, invoice_date DESC)
    WHERE deleted_flag = false;

CREATE INDEX idx_fm_invoice_receipt_settlement_company_date
    ON public.fm_invoice_receipt (settlement_company_id, invoice_date DESC)
    WHERE deleted_flag = false;
