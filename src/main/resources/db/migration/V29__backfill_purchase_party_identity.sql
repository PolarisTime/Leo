DO $$
BEGIN
    IF EXISTS (
        SELECT supplier_code
        FROM public.md_supplier
        GROUP BY supplier_code
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V29: md_supplier 全历史 supplier_code 不唯一';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.ct_purchase_contract contract
        LEFT JOIN LATERAL (
            SELECT COUNT(*) AS candidate_count
            FROM public.md_supplier supplier
            WHERE BTRIM(supplier.supplier_name) = BTRIM(contract.supplier_name)
        ) candidates ON true
        WHERE contract.supplier_id IS NULL
          AND NULLIF(BTRIM(contract.supplier_code), '') IS NULL
          AND candidates.candidate_count <> 1
    ) THEN
        RAISE EXCEPTION 'V29: 采购合同供应商名称存在零候选或多候选';
    END IF;

    IF EXISTS (
        WITH refs AS (
            SELECT 'po_purchase_order' AS source_table, id, supplier_code FROM public.po_purchase_order
            UNION ALL SELECT 'po_purchase_inbound', id, supplier_code FROM public.po_purchase_inbound
            UNION ALL SELECT 'po_purchase_refund', id, supplier_code FROM public.po_purchase_refund
            UNION ALL SELECT 'fm_invoice_receipt', id, supplier_code FROM public.fm_invoice_receipt
            UNION ALL SELECT 'st_supplier_statement', id, supplier_code FROM public.st_supplier_statement
            UNION ALL SELECT 'fm_supplier_refund_receipt', id, supplier_code FROM public.fm_supplier_refund_receipt
        )
        SELECT 1
        FROM refs
        LEFT JOIN public.md_supplier supplier ON supplier.supplier_code = refs.supplier_code
        WHERE NULLIF(BTRIM(refs.supplier_code), '') IS NULL OR supplier.id IS NULL
    ) THEN
        RAISE EXCEPTION 'V29: 采购链存在无法按 supplier_code 唯一解析的供应商';
    END IF;
END $$;

UPDATE public.ct_purchase_contract contract
SET supplier_id = supplier.id,
    supplier_code = supplier.supplier_code
FROM public.md_supplier supplier
WHERE contract.supplier_id IS NULL
  AND BTRIM(supplier.supplier_name) = BTRIM(contract.supplier_name);

UPDATE public.po_purchase_order target SET supplier_id = supplier.id
FROM public.md_supplier supplier WHERE target.supplier_id IS NULL AND supplier.supplier_code = target.supplier_code;
UPDATE public.po_purchase_inbound target SET supplier_id = supplier.id
FROM public.md_supplier supplier WHERE target.supplier_id IS NULL AND supplier.supplier_code = target.supplier_code;
UPDATE public.po_purchase_refund target SET supplier_id = supplier.id
FROM public.md_supplier supplier WHERE target.supplier_id IS NULL AND supplier.supplier_code = target.supplier_code;
UPDATE public.fm_invoice_receipt target SET supplier_id = supplier.id
FROM public.md_supplier supplier WHERE target.supplier_id IS NULL AND supplier.supplier_code = target.supplier_code;
UPDATE public.st_supplier_statement target SET supplier_id = supplier.id
FROM public.md_supplier supplier WHERE target.supplier_id IS NULL AND supplier.supplier_code = target.supplier_code;
UPDATE public.fm_supplier_refund_receipt target SET supplier_id = supplier.id
FROM public.md_supplier supplier WHERE target.supplier_id IS NULL AND supplier.supplier_code = target.supplier_code;

DO $$
BEGIN
    IF EXISTS (
        WITH refs AS (
            SELECT supplier_id FROM public.ct_purchase_contract
            UNION ALL SELECT supplier_id FROM public.po_purchase_order
            UNION ALL SELECT supplier_id FROM public.po_purchase_inbound
            UNION ALL SELECT supplier_id FROM public.po_purchase_refund
            UNION ALL SELECT supplier_id FROM public.fm_invoice_receipt
            UNION ALL SELECT supplier_id FROM public.st_supplier_statement
            UNION ALL SELECT supplier_id FROM public.fm_supplier_refund_receipt
        )
        SELECT 1 FROM refs WHERE supplier_id IS NULL
    ) THEN
        RAISE EXCEPTION 'V29: 采购链 supplier_id 回填后仍有空值';
    END IF;
END $$;
