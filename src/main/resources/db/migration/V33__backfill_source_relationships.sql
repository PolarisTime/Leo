UPDATE public.fm_receipt_allocation
SET source_customer_statement_id = source_statement_id
WHERE source_customer_statement_id IS NULL;

UPDATE public.fm_payment_allocation allocation
SET source_supplier_statement_id = allocation.source_statement_id
FROM public.fm_payment payment
WHERE payment.id = allocation.payment_id
  AND payment.business_type = '供应商'
  AND allocation.source_supplier_statement_id IS NULL;

UPDATE public.fm_payment_allocation allocation
SET source_freight_statement_id = allocation.source_statement_id
FROM public.fm_payment payment
WHERE payment.id = allocation.payment_id
  AND payment.business_type = '物流商'
  AND allocation.source_freight_statement_id IS NULL;

UPDATE public.st_freight_statement_item statement_item
SET source_freight_bill_id = bill_item.bill_id
FROM public.lg_freight_bill_item bill_item
WHERE bill_item.id = statement_item.source_freight_bill_item_id
  AND statement_item.source_freight_bill_id IS NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.po_purchase_inbound_item item
        LEFT JOIN public.po_purchase_order_item source ON source.id = item.source_purchase_order_item_id
        WHERE item.source_purchase_order_item_id IS NOT NULL AND source.id IS NULL
    ) THEN
        RAISE EXCEPTION 'V33: 采购入库明细存在孤儿采购订单明细来源';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.so_sales_order_item item
        LEFT JOIN public.po_purchase_inbound_item inbound ON inbound.id = item.source_inbound_item_id
        LEFT JOIN public.po_purchase_order_item purchase_item ON purchase_item.id = item.source_purchase_order_item_id
        WHERE (item.source_inbound_item_id IS NOT NULL AND inbound.id IS NULL)
           OR (item.source_purchase_order_item_id IS NOT NULL AND purchase_item.id IS NULL)
           OR num_nonnulls(item.source_inbound_item_id, item.source_purchase_order_item_id) > 1
    ) THEN
        RAISE EXCEPTION 'V33: 销售订单明细来源孤儿或同时绑定两类来源';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.so_sales_outbound_item item
        LEFT JOIN public.so_sales_order_item source ON source.id = item.source_sales_order_item_id
        WHERE item.source_sales_order_item_id IS NULL OR source.id IS NULL
    ) THEN
        RAISE EXCEPTION 'V33: 销售出库明细缺少有效销售订单明细来源';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.lg_freight_bill_item item
        LEFT JOIN public.so_sales_outbound_item source ON source.id = item.source_sales_outbound_item_id
        WHERE item.source_sales_outbound_item_id IS NULL OR source.id IS NULL
    ) THEN
        RAISE EXCEPTION 'V33: 物流单明细缺少有效销售出库明细来源';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.st_freight_statement_item statement_item
        LEFT JOIN public.lg_freight_bill_item bill_item ON bill_item.id = statement_item.source_freight_bill_item_id
        WHERE statement_item.source_freight_bill_id IS NULL
           OR statement_item.source_freight_bill_item_id IS NULL
           OR bill_item.id IS NULL
           OR bill_item.bill_id <> statement_item.source_freight_bill_id
    ) THEN
        RAISE EXCEPTION 'V33: 物流对账直接来源头明细不一致';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.fm_receipt_allocation allocation
        LEFT JOIN public.st_customer_statement statement ON statement.id = allocation.source_customer_statement_id
        WHERE statement.id IS NULL
    ) THEN
        RAISE EXCEPTION 'V33: 收款分配存在无效客户对账单来源';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.fm_payment_allocation allocation
        LEFT JOIN public.st_supplier_statement supplier_statement ON supplier_statement.id = allocation.source_supplier_statement_id
        LEFT JOIN public.st_freight_statement freight_statement ON freight_statement.id = allocation.source_freight_statement_id
        WHERE num_nonnulls(allocation.source_supplier_statement_id, allocation.source_freight_statement_id) <> 1
           OR (allocation.source_supplier_statement_id IS NOT NULL AND supplier_statement.id IS NULL)
           OR (allocation.source_freight_statement_id IS NOT NULL AND freight_statement.id IS NULL)
    ) THEN
        RAISE EXCEPTION 'V33: 付款分配 typed statement 来源无效';
    END IF;
END $$;
