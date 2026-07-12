DO $$
BEGIN
    IF EXISTS (
        SELECT material_code FROM public.md_material GROUP BY material_code HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V28: md_material 全历史 material_code 不唯一';
    END IF;

    IF EXISTS (
        WITH refs AS (
            SELECT material_code FROM public.ct_purchase_contract_item
            UNION ALL SELECT material_code FROM public.ct_sales_contract_item
            UNION ALL SELECT material_code FROM public.po_purchase_order_item
            UNION ALL SELECT material_code FROM public.po_purchase_inbound_item
            UNION ALL SELECT material_code FROM public.po_purchase_refund_item
            UNION ALL SELECT material_code FROM public.so_sales_order_item
            UNION ALL SELECT material_code FROM public.so_sales_outbound_item
            UNION ALL SELECT material_code FROM public.lg_freight_bill_item
            UNION ALL SELECT material_code FROM public.fm_invoice_issue_item
            UNION ALL SELECT material_code FROM public.fm_invoice_receipt_item
            UNION ALL SELECT material_code FROM public.st_customer_statement_item
            UNION ALL SELECT material_code FROM public.st_supplier_statement_item
            UNION ALL SELECT material_code FROM public.st_freight_statement_item
        )
        SELECT 1 FROM refs
        LEFT JOIN public.md_material material ON material.material_code = refs.material_code
        WHERE material.id IS NULL
    ) THEN
        RAISE EXCEPTION 'V28: 业务明细存在无法按 material_code 解析的商品';
    END IF;

    IF EXISTS (
        SELECT BTRIM(warehouse_name)
        FROM public.md_warehouse
        GROUP BY BTRIM(warehouse_name)
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V28: md_warehouse 全历史 warehouse_name 不唯一';
    END IF;

    IF EXISTS (
        WITH refs AS (
            SELECT warehouse_name FROM public.po_purchase_order_item
            UNION ALL SELECT warehouse_name FROM public.po_purchase_inbound
            UNION ALL SELECT warehouse_name FROM public.po_purchase_inbound_item
            UNION ALL SELECT warehouse_name FROM public.po_purchase_refund_item
            UNION ALL SELECT warehouse_name FROM public.so_sales_order_item
            UNION ALL SELECT warehouse_name FROM public.so_sales_outbound
            UNION ALL SELECT warehouse_name FROM public.so_sales_outbound_item
            UNION ALL SELECT warehouse_name FROM public.lg_freight_bill_item
            UNION ALL SELECT warehouse_name FROM public.fm_invoice_issue_item
            UNION ALL SELECT warehouse_name FROM public.fm_invoice_receipt_item
            UNION ALL SELECT warehouse_name FROM public.st_freight_statement_item
        )
        SELECT 1 FROM refs
        LEFT JOIN public.md_warehouse warehouse ON BTRIM(warehouse.warehouse_name) = BTRIM(refs.warehouse_name)
        WHERE NULLIF(BTRIM(refs.warehouse_name), '') IS NOT NULL AND warehouse.id IS NULL
    ) THEN
        RAISE EXCEPTION 'V28: 业务明细存在无法按 warehouse_name 解析的仓库';
    END IF;
END $$;

UPDATE public.ct_purchase_contract_item target SET material_id = material.id
FROM public.md_material material WHERE target.material_id IS NULL AND material.material_code = target.material_code;
UPDATE public.ct_sales_contract_item target SET material_id = material.id
FROM public.md_material material WHERE target.material_id IS NULL AND material.material_code = target.material_code;
UPDATE public.po_purchase_order_item target SET material_id = material.id
FROM public.md_material material WHERE target.material_id IS NULL AND material.material_code = target.material_code;
UPDATE public.po_purchase_inbound_item target SET material_id = material.id
FROM public.md_material material WHERE target.material_id IS NULL AND material.material_code = target.material_code;
UPDATE public.po_purchase_refund_item target SET material_id = material.id
FROM public.md_material material WHERE target.material_id IS NULL AND material.material_code = target.material_code;
UPDATE public.so_sales_order_item target SET material_id = material.id
FROM public.md_material material WHERE target.material_id IS NULL AND material.material_code = target.material_code;
UPDATE public.so_sales_outbound_item target SET material_id = material.id
FROM public.md_material material WHERE target.material_id IS NULL AND material.material_code = target.material_code;
UPDATE public.lg_freight_bill_item target SET material_id = material.id
FROM public.md_material material WHERE target.material_id IS NULL AND material.material_code = target.material_code;
UPDATE public.fm_invoice_issue_item target SET material_id = material.id
FROM public.md_material material WHERE target.material_id IS NULL AND material.material_code = target.material_code;
UPDATE public.fm_invoice_receipt_item target SET material_id = material.id
FROM public.md_material material WHERE target.material_id IS NULL AND material.material_code = target.material_code;
UPDATE public.st_customer_statement_item target SET material_id = material.id
FROM public.md_material material WHERE target.material_id IS NULL AND material.material_code = target.material_code;
UPDATE public.st_supplier_statement_item target SET material_id = material.id
FROM public.md_material material WHERE target.material_id IS NULL AND material.material_code = target.material_code;
UPDATE public.st_freight_statement_item target SET material_id = material.id
FROM public.md_material material WHERE target.material_id IS NULL AND material.material_code = target.material_code;

UPDATE public.po_purchase_order_item target SET warehouse_id = warehouse.id
FROM public.md_warehouse warehouse WHERE target.warehouse_id IS NULL AND BTRIM(warehouse.warehouse_name) = BTRIM(target.warehouse_name);
UPDATE public.po_purchase_inbound target SET warehouse_id = warehouse.id
FROM public.md_warehouse warehouse WHERE target.warehouse_id IS NULL AND BTRIM(warehouse.warehouse_name) = BTRIM(target.warehouse_name);
UPDATE public.po_purchase_inbound_item target SET warehouse_id = warehouse.id
FROM public.md_warehouse warehouse WHERE target.warehouse_id IS NULL AND BTRIM(warehouse.warehouse_name) = BTRIM(target.warehouse_name);
UPDATE public.po_purchase_refund_item target SET warehouse_id = warehouse.id
FROM public.md_warehouse warehouse WHERE target.warehouse_id IS NULL AND BTRIM(warehouse.warehouse_name) = BTRIM(target.warehouse_name);
UPDATE public.so_sales_order_item target SET warehouse_id = warehouse.id
FROM public.md_warehouse warehouse WHERE target.warehouse_id IS NULL AND BTRIM(warehouse.warehouse_name) = BTRIM(target.warehouse_name);
UPDATE public.so_sales_outbound target SET warehouse_id = warehouse.id
FROM public.md_warehouse warehouse WHERE target.warehouse_id IS NULL AND BTRIM(warehouse.warehouse_name) = BTRIM(target.warehouse_name);
UPDATE public.so_sales_outbound_item target SET warehouse_id = warehouse.id
FROM public.md_warehouse warehouse WHERE target.warehouse_id IS NULL AND BTRIM(warehouse.warehouse_name) = BTRIM(target.warehouse_name);
UPDATE public.lg_freight_bill_item target SET warehouse_id = warehouse.id
FROM public.md_warehouse warehouse WHERE target.warehouse_id IS NULL AND BTRIM(warehouse.warehouse_name) = BTRIM(target.warehouse_name);
UPDATE public.fm_invoice_issue_item target SET warehouse_id = warehouse.id
FROM public.md_warehouse warehouse WHERE target.warehouse_id IS NULL AND BTRIM(warehouse.warehouse_name) = BTRIM(target.warehouse_name);
UPDATE public.fm_invoice_receipt_item target SET warehouse_id = warehouse.id
FROM public.md_warehouse warehouse WHERE target.warehouse_id IS NULL AND BTRIM(warehouse.warehouse_name) = BTRIM(target.warehouse_name);
UPDATE public.st_freight_statement_item target SET warehouse_id = warehouse.id
FROM public.md_warehouse warehouse WHERE target.warehouse_id IS NULL AND BTRIM(warehouse.warehouse_name) = BTRIM(target.warehouse_name);

UPDATE public.po_purchase_inbound_item item
SET warehouse_id = inbound.warehouse_id
FROM public.po_purchase_inbound inbound
WHERE inbound.id = item.inbound_id AND item.warehouse_id IS NULL;

UPDATE public.po_purchase_refund_item item
SET material_id = source.material_id,
    warehouse_id = COALESCE(item.warehouse_id, source.warehouse_id)
FROM public.po_purchase_order_item source
WHERE source.id = item.source_purchase_order_item_id;

UPDATE public.so_sales_order_item item
SET material_id = COALESCE(item.material_id, inbound.material_id),
    warehouse_id = COALESCE(item.warehouse_id, inbound.warehouse_id)
FROM public.po_purchase_inbound_item inbound
WHERE inbound.id = item.source_inbound_item_id;

UPDATE public.so_sales_order_item item
SET material_id = COALESCE(item.material_id, purchase_item.material_id),
    warehouse_id = COALESCE(item.warehouse_id, purchase_item.warehouse_id)
FROM public.po_purchase_order_item purchase_item
WHERE purchase_item.id = item.source_purchase_order_item_id;

UPDATE public.so_sales_outbound_item item
SET material_id = source.material_id,
    warehouse_id = COALESCE(item.warehouse_id, source.warehouse_id)
FROM public.so_sales_order_item source
WHERE source.id = item.source_sales_order_item_id;

UPDATE public.so_sales_outbound_item item
SET warehouse_id = outbound.warehouse_id
FROM public.so_sales_outbound outbound
WHERE outbound.id = item.outbound_id AND item.warehouse_id IS NULL;

UPDATE public.lg_freight_bill_item item
SET material_id = source.material_id,
    warehouse_id = COALESCE(item.warehouse_id, source.warehouse_id)
FROM public.so_sales_outbound_item source
WHERE source.id = item.source_sales_outbound_item_id;

UPDATE public.fm_invoice_issue_item item
SET material_id = source.material_id,
    warehouse_id = COALESCE(item.warehouse_id, source.warehouse_id)
FROM public.so_sales_order_item source
WHERE source.id = item.source_sales_order_item_id;

UPDATE public.fm_invoice_receipt_item item
SET material_id = source.material_id,
    warehouse_id = COALESCE(item.warehouse_id, source.warehouse_id)
FROM public.po_purchase_order_item source
WHERE source.id = item.source_purchase_order_item_id;

UPDATE public.st_customer_statement_item item
SET material_id = source.material_id,
    warehouse_id = source.warehouse_id
FROM public.so_sales_order_item source
WHERE source.id = item.source_sales_order_item_id;

UPDATE public.st_supplier_statement_item item
SET material_id = source.material_id,
    warehouse_id = source.warehouse_id
FROM public.po_purchase_inbound_item source
WHERE source.id = item.source_inbound_item_id;

UPDATE public.st_freight_statement_item item
SET material_id = source.material_id,
    warehouse_id = COALESCE(item.warehouse_id, source.warehouse_id)
FROM public.so_sales_outbound_item source
WHERE source.id = item.source_sales_outbound_item_id;

DO $$
BEGIN
    IF EXISTS (
        WITH refs AS (
            SELECT material_id FROM public.ct_purchase_contract_item
            UNION ALL SELECT material_id FROM public.ct_sales_contract_item
            UNION ALL SELECT material_id FROM public.po_purchase_order_item
            UNION ALL SELECT material_id FROM public.po_purchase_inbound_item
            UNION ALL SELECT material_id FROM public.po_purchase_refund_item
            UNION ALL SELECT material_id FROM public.so_sales_order_item
            UNION ALL SELECT material_id FROM public.so_sales_outbound_item
            UNION ALL SELECT material_id FROM public.lg_freight_bill_item
            UNION ALL SELECT material_id FROM public.fm_invoice_issue_item
            UNION ALL SELECT material_id FROM public.fm_invoice_receipt_item
            UNION ALL SELECT material_id FROM public.st_customer_statement_item
            UNION ALL SELECT material_id FROM public.st_supplier_statement_item
            UNION ALL SELECT material_id FROM public.st_freight_statement_item
        ) SELECT 1 FROM refs WHERE material_id IS NULL
    ) THEN
        RAISE EXCEPTION 'V28: material_id 回填后仍有空值';
    END IF;

    IF EXISTS (SELECT 1 FROM public.po_purchase_inbound WHERE warehouse_id IS NULL)
       OR EXISTS (SELECT 1 FROM public.po_purchase_inbound_item WHERE warehouse_id IS NULL)
       OR EXISTS (SELECT 1 FROM public.so_sales_outbound WHERE warehouse_id IS NULL)
       OR EXISTS (SELECT 1 FROM public.so_sales_outbound_item WHERE warehouse_id IS NULL) THEN
        RAISE EXCEPTION 'V28: 有效出入库事实 warehouse_id 回填后仍有空值';
    END IF;
END $$;
