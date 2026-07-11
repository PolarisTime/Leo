ALTER TABLE public.po_purchase_order
    ADD COLUMN supplier_code character varying(64);

ALTER TABLE public.po_purchase_inbound
    ADD COLUMN supplier_code character varying(64);

-- Refunds already carry the supplier identity selected for their source order.
UPDATE public.po_purchase_order purchase_order
SET supplier_code = refund.supplier_code
FROM public.po_purchase_refund refund
WHERE refund.source_purchase_order_id = purchase_order.id
  AND refund.deleted_flag = false
  AND COALESCE(BTRIM(refund.supplier_code), '') <> ''
  AND purchase_order.supplier_code IS NULL;

-- A historical name is safe to backfill only when it maps to exactly one code.
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
UPDATE public.po_purchase_order purchase_order
SET supplier_code = unique_supplier_name.supplier_code
FROM unique_supplier_name
WHERE LOWER(BTRIM(purchase_order.supplier_name)) = unique_supplier_name.supplier_name_key
  AND purchase_order.supplier_code IS NULL;

-- Prefer the stable identity of linked source orders for inbound documents.
WITH inbound_source_supplier AS (
    SELECT
        inbound_item.inbound_id,
        MIN(source_order.supplier_code) AS supplier_code
    FROM public.po_purchase_inbound_item inbound_item
    JOIN public.po_purchase_order_item source_order_item
      ON source_order_item.id = inbound_item.source_purchase_order_item_id
    JOIN public.po_purchase_order source_order
      ON source_order.id = source_order_item.order_id
    WHERE COALESCE(BTRIM(source_order.supplier_code), '') <> ''
    GROUP BY inbound_item.inbound_id
    HAVING COUNT(DISTINCT source_order.supplier_code) = 1
)
UPDATE public.po_purchase_inbound inbound
SET supplier_code = inbound_source_supplier.supplier_code
FROM inbound_source_supplier
WHERE inbound_source_supplier.inbound_id = inbound.id
  AND inbound.supplier_code IS NULL;

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
UPDATE public.po_purchase_inbound inbound
SET supplier_code = unique_supplier_name.supplier_code
FROM unique_supplier_name
WHERE LOWER(BTRIM(inbound.supplier_name)) = unique_supplier_name.supplier_name_key
  AND inbound.supplier_code IS NULL;

DO $$
DECLARE
    unresolved_order_count bigint;
    unresolved_inbound_count bigint;
BEGIN
    SELECT COUNT(*)
    INTO unresolved_order_count
    FROM public.po_purchase_order
    WHERE COALESCE(BTRIM(supplier_code), '') = '';

    SELECT COUNT(*)
    INTO unresolved_inbound_count
    FROM public.po_purchase_inbound
    WHERE COALESCE(BTRIM(supplier_code), '') = '';

    IF unresolved_order_count > 0 OR unresolved_inbound_count > 0 THEN
        RAISE EXCEPTION
            '采购供应商编码回填失败：采购订单 % 条、采购入库 % 条无法唯一匹配供应商编码',
            unresolved_order_count,
            unresolved_inbound_count;
    END IF;
END
$$;

ALTER TABLE public.po_purchase_order
    ALTER COLUMN supplier_code SET NOT NULL;

ALTER TABLE public.po_purchase_inbound
    ALTER COLUMN supplier_code SET NOT NULL;

COMMENT ON COLUMN public.po_purchase_order.supplier_code IS '供应商稳定身份编码快照';
COMMENT ON COLUMN public.po_purchase_inbound.supplier_code IS '供应商稳定身份编码快照';

CREATE INDEX idx_po_purchase_order_supplier_code_date
    ON public.po_purchase_order (supplier_code, order_date DESC)
    WHERE deleted_flag = false;

CREATE INDEX idx_po_purchase_inbound_supplier_code_date
    ON public.po_purchase_inbound (supplier_code, inbound_date DESC)
    WHERE deleted_flag = false;

CREATE INDEX idx_po_purchase_refund_supplier_code_date
    ON public.po_purchase_refund (supplier_code, refund_date DESC)
    WHERE deleted_flag = false;
