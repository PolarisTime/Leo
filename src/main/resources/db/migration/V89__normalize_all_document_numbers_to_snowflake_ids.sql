-- Normalize string references before replacing the source document numbers.
UPDATE public.po_purchase_inbound target
SET purchase_order_no = source.id::text
FROM public.po_purchase_order source
WHERE target.purchase_order_no = source.order_no;

UPDATE public.po_purchase_inbound_import_batch target
SET source_purchase_order_no = source.id::text
FROM public.po_purchase_order source
WHERE target.source_purchase_order_no = source.order_no;

UPDATE public.so_sales_order target
SET purchase_order_no = source.id::text
FROM public.po_purchase_order source
WHERE target.purchase_order_no = source.order_no;

UPDATE public.fm_payment target
SET purchase_order_no = source.id::text
FROM public.po_purchase_order source
WHERE target.purchase_order_no = source.order_no;

UPDATE public.po_purchase_refund target
SET purchase_order_no = source.id::text
FROM public.po_purchase_order source
WHERE target.purchase_order_no = source.order_no;

UPDATE public.fm_invoice_receipt_item target
SET source_no = source.id::text
FROM public.po_purchase_order source
WHERE target.source_no = source.order_no;

UPDATE public.so_sales_order target
SET purchase_inbound_no = source.id::text
FROM public.po_purchase_inbound source
WHERE target.purchase_inbound_no = source.inbound_no;

UPDATE public.st_supplier_statement_item target
SET source_no = source.id::text
FROM public.po_purchase_inbound source
WHERE target.source_no = source.inbound_no;

UPDATE public.so_sales_outbound target
SET sales_order_no = source.id::text
FROM public.so_sales_order source
WHERE target.sales_order_no = source.order_no;

UPDATE public.lg_freight_bill_source_order target
SET source_sales_order_no = source.id::text
FROM public.so_sales_order source
WHERE target.source_sales_order_no = source.order_no;

UPDATE public.st_customer_statement_item target
SET source_no = source.id::text
FROM public.so_sales_order source
WHERE target.source_no = source.order_no;

UPDATE public.lg_freight_bill_item target
SET source_no = source.id::text
FROM public.so_sales_order source
WHERE target.source_no = source.order_no;

UPDATE public.fm_invoice_issue_item target
SET source_no = source.id::text
FROM public.so_sales_order source
WHERE target.source_no = source.order_no;

UPDATE public.lg_freight_bill_item target
SET source_no = source.id::text
FROM public.so_sales_outbound source
WHERE target.source_no = source.outbound_no;

UPDATE public.st_freight_statement_item target
SET source_no = source.id::text
FROM public.lg_freight_bill source
WHERE target.source_no = source.bill_no;

UPDATE public.po_purchase_order
SET order_no = id::text
WHERE order_no IS DISTINCT FROM id::text;

UPDATE public.po_purchase_inbound
SET inbound_no = id::text
WHERE inbound_no IS DISTINCT FROM id::text;

UPDATE public.so_sales_order
SET order_no = id::text
WHERE order_no IS DISTINCT FROM id::text;

UPDATE public.so_sales_outbound
SET outbound_no = id::text
WHERE outbound_no IS DISTINCT FROM id::text;

UPDATE public.lg_freight_bill
SET bill_no = id::text
WHERE bill_no IS DISTINCT FROM id::text;

UPDATE public.st_customer_statement
SET statement_no = id::text
WHERE statement_no IS DISTINCT FROM id::text;

UPDATE public.st_freight_statement
SET statement_no = id::text
WHERE statement_no IS DISTINCT FROM id::text;

UPDATE public.st_supplier_statement
SET statement_no = id::text
WHERE statement_no IS DISTINCT FROM id::text;

UPDATE public.fm_receipt
SET receipt_no = id::text
WHERE receipt_no IS DISTINCT FROM id::text;

UPDATE public.fm_payment
SET payment_no = id::text
WHERE payment_no IS DISTINCT FROM id::text;

UPDATE public.fm_ledger_adjustment
SET adjustment_no = id::text
WHERE adjustment_no IS DISTINCT FROM id::text;

UPDATE public.ct_purchase_contract
SET contract_no = id::text
WHERE contract_no IS DISTINCT FROM id::text;

UPDATE public.ct_sales_contract
SET contract_no = id::text
WHERE contract_no IS DISTINCT FROM id::text;

UPDATE public.fm_cash_reversal
SET reversal_no = id::text
WHERE reversal_no IS DISTINCT FROM id::text;

UPDATE public.fm_invoice_issue
SET issue_no = id::text
WHERE issue_no IS DISTINCT FROM id::text;

UPDATE public.fm_invoice_receipt
SET receive_no = id::text
WHERE receive_no IS DISTINCT FROM id::text;

UPDATE public.fm_supplier_refund_receipt
SET refund_receipt_no = id::text
WHERE refund_receipt_no IS DISTINCT FROM id::text;

UPDATE public.po_purchase_refund
SET refund_no = id::text
WHERE refund_no IS DISTINCT FROM id::text;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.po_purchase_order WHERE order_no IS DISTINCT FROM id::text
        UNION ALL SELECT 1 FROM public.po_purchase_inbound WHERE inbound_no IS DISTINCT FROM id::text
        UNION ALL SELECT 1 FROM public.so_sales_order WHERE order_no IS DISTINCT FROM id::text
        UNION ALL SELECT 1 FROM public.so_sales_outbound WHERE outbound_no IS DISTINCT FROM id::text
        UNION ALL SELECT 1 FROM public.lg_freight_bill WHERE bill_no IS DISTINCT FROM id::text
        UNION ALL SELECT 1 FROM public.st_customer_statement WHERE statement_no IS DISTINCT FROM id::text
        UNION ALL SELECT 1 FROM public.st_freight_statement WHERE statement_no IS DISTINCT FROM id::text
        UNION ALL SELECT 1 FROM public.st_supplier_statement WHERE statement_no IS DISTINCT FROM id::text
        UNION ALL SELECT 1 FROM public.fm_receipt WHERE receipt_no IS DISTINCT FROM id::text
        UNION ALL SELECT 1 FROM public.fm_payment WHERE payment_no IS DISTINCT FROM id::text
        UNION ALL SELECT 1 FROM public.fm_ledger_adjustment WHERE adjustment_no IS DISTINCT FROM id::text
        UNION ALL SELECT 1 FROM public.ct_purchase_contract WHERE contract_no IS DISTINCT FROM id::text
        UNION ALL SELECT 1 FROM public.ct_sales_contract WHERE contract_no IS DISTINCT FROM id::text
        UNION ALL SELECT 1 FROM public.fm_cash_reversal WHERE reversal_no IS DISTINCT FROM id::text
        UNION ALL SELECT 1 FROM public.fm_invoice_issue WHERE issue_no IS DISTINCT FROM id::text
        UNION ALL SELECT 1 FROM public.fm_invoice_receipt WHERE receive_no IS DISTINCT FROM id::text
        UNION ALL SELECT 1 FROM public.fm_supplier_refund_receipt WHERE refund_receipt_no IS DISTINCT FROM id::text
        UNION ALL SELECT 1 FROM public.po_purchase_refund WHERE refund_no IS DISTINCT FROM id::text
    ) THEN
        RAISE EXCEPTION 'document number normalization failed';
    END IF;
END
$$;
