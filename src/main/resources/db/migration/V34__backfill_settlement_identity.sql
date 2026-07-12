WITH source_identity AS (
    SELECT item.inbound_id AS document_id, MIN(source_order.settlement_company_id) AS settlement_company_id
    FROM public.po_purchase_inbound_item item
    JOIN public.po_purchase_order_item source_item ON source_item.id = item.source_purchase_order_item_id
    JOIN public.po_purchase_order source_order ON source_order.id = source_item.order_id
    WHERE source_order.settlement_company_id IS NOT NULL
    GROUP BY item.inbound_id
    HAVING COUNT(DISTINCT source_order.settlement_company_id) = 1
)
UPDATE public.po_purchase_inbound target
SET settlement_company_id = source_identity.settlement_company_id
FROM source_identity
WHERE target.id = source_identity.document_id
  AND target.settlement_company_id IS NULL;

UPDATE public.po_purchase_refund target
SET settlement_company_id = source.settlement_company_id
FROM public.po_purchase_order source
WHERE source.id = target.source_purchase_order_id
  AND target.settlement_company_id IS NULL;

WITH source_identity AS (
    SELECT item.outbound_id AS document_id, MIN(source_order.settlement_company_id) AS settlement_company_id
    FROM public.so_sales_outbound_item item
    JOIN public.so_sales_order_item source_item ON source_item.id = item.source_sales_order_item_id
    JOIN public.so_sales_order source_order ON source_order.id = source_item.order_id
    WHERE source_order.settlement_company_id IS NOT NULL
    GROUP BY item.outbound_id
    HAVING COUNT(DISTINCT source_order.settlement_company_id) = 1
)
UPDATE public.so_sales_outbound target
SET settlement_company_id = source_identity.settlement_company_id
FROM source_identity
WHERE target.id = source_identity.document_id
  AND target.settlement_company_id IS NULL;

WITH source_identity AS (
    SELECT bridge.issue_id AS document_id, MIN(source.settlement_company_id) AS settlement_company_id
    FROM public.fm_invoice_issue_source_order bridge
    JOIN public.so_sales_order source ON source.id = bridge.sales_order_id
    WHERE source.settlement_company_id IS NOT NULL
    GROUP BY bridge.issue_id
    HAVING COUNT(DISTINCT source.settlement_company_id) = 1
)
UPDATE public.fm_invoice_issue target
SET settlement_company_id = source_identity.settlement_company_id
FROM source_identity
WHERE target.id = source_identity.document_id
  AND target.settlement_company_id IS NULL;

WITH source_identity AS (
    SELECT bridge.receipt_id AS document_id, MIN(source.settlement_company_id) AS settlement_company_id
    FROM public.fm_invoice_receipt_source_order bridge
    JOIN public.po_purchase_order source ON source.id = bridge.purchase_order_id
    WHERE source.settlement_company_id IS NOT NULL
    GROUP BY bridge.receipt_id
    HAVING COUNT(DISTINCT source.settlement_company_id) = 1
)
UPDATE public.fm_invoice_receipt target
SET settlement_company_id = source_identity.settlement_company_id
FROM source_identity
WHERE target.id = source_identity.document_id
  AND target.settlement_company_id IS NULL;

WITH source_identity AS (
    SELECT item.statement_id AS document_id, MIN(source_order.settlement_company_id) AS settlement_company_id
    FROM public.st_customer_statement_item item
    JOIN public.so_sales_order_item source_item ON source_item.id = item.source_sales_order_item_id
    JOIN public.so_sales_order source_order ON source_order.id = source_item.order_id
    WHERE source_order.settlement_company_id IS NOT NULL
    GROUP BY item.statement_id
    HAVING COUNT(DISTINCT source_order.settlement_company_id) = 1
)
UPDATE public.st_customer_statement target
SET settlement_company_id = source_identity.settlement_company_id
FROM source_identity
WHERE target.id = source_identity.document_id
  AND target.settlement_company_id IS NULL;

WITH source_identity AS (
    SELECT item.statement_id AS document_id, MIN(source_inbound.settlement_company_id) AS settlement_company_id
    FROM public.st_supplier_statement_item item
    JOIN public.po_purchase_inbound_item source_item ON source_item.id = item.source_inbound_item_id
    JOIN public.po_purchase_inbound source_inbound ON source_inbound.id = source_item.inbound_id
    WHERE source_inbound.settlement_company_id IS NOT NULL
    GROUP BY item.statement_id
    HAVING COUNT(DISTINCT source_inbound.settlement_company_id) = 1
)
UPDATE public.st_supplier_statement target
SET settlement_company_id = source_identity.settlement_company_id
FROM source_identity
WHERE target.id = source_identity.document_id
  AND target.settlement_company_id IS NULL;

WITH source_identity AS (
    SELECT item.statement_id AS document_id, MIN(source_bill.settlement_company_id) AS settlement_company_id
    FROM public.st_freight_statement_item item
    JOIN public.lg_freight_bill source_bill ON source_bill.id = item.source_freight_bill_id
    WHERE source_bill.settlement_company_id IS NOT NULL
    GROUP BY item.statement_id
    HAVING COUNT(DISTINCT source_bill.settlement_company_id) = 1
)
UPDATE public.st_freight_statement target
SET settlement_company_id = source_identity.settlement_company_id
FROM source_identity
WHERE target.id = source_identity.document_id
  AND target.settlement_company_id IS NULL;

UPDATE public.fm_receipt target
SET settlement_company_id = source.settlement_company_id
FROM public.st_customer_statement source
WHERE source.id = target.source_customer_statement_id
  AND target.settlement_company_id IS NULL;

UPDATE public.fm_supplier_refund_receipt target
SET settlement_company_id = source.settlement_company_id
FROM public.po_purchase_refund source
WHERE source.id = target.purchase_refund_id
  AND target.settlement_company_id IS NULL;

DO $$
BEGIN
    IF EXISTS (
        WITH refs AS (
            SELECT settlement_company_id FROM public.po_purchase_order
            UNION ALL SELECT settlement_company_id FROM public.po_purchase_inbound
            UNION ALL SELECT settlement_company_id FROM public.po_purchase_refund
            UNION ALL SELECT settlement_company_id FROM public.so_sales_order
            UNION ALL SELECT settlement_company_id FROM public.so_sales_outbound
            UNION ALL SELECT settlement_company_id FROM public.lg_freight_bill
            UNION ALL SELECT settlement_company_id FROM public.st_customer_statement
            UNION ALL SELECT settlement_company_id FROM public.st_supplier_statement
            UNION ALL SELECT settlement_company_id FROM public.st_freight_statement
            UNION ALL SELECT settlement_company_id FROM public.fm_invoice_issue
            UNION ALL SELECT settlement_company_id FROM public.fm_invoice_receipt
            UNION ALL SELECT settlement_company_id FROM public.fm_receipt
            UNION ALL SELECT settlement_company_id FROM public.fm_payment
            UNION ALL SELECT settlement_company_id FROM public.fm_supplier_refund_receipt
            UNION ALL SELECT settlement_company_id FROM public.fm_ledger_adjustment
        )
        SELECT 1
        FROM refs
        LEFT JOIN public.sys_company_setting company ON company.id = refs.settlement_company_id
        WHERE refs.settlement_company_id IS NOT NULL AND company.id IS NULL
    ) THEN
        RAISE EXCEPTION 'V34: settlement_company_id 存在孤儿引用';
    END IF;
END $$;
