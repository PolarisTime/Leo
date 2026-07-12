DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.fm_payment
        WHERE business_type NOT IN ('供应商', '物流商')
    ) THEN
        RAISE EXCEPTION 'V32: 付款单存在不支持的往来方类型';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.fm_payment payment
        LEFT JOIN public.md_supplier supplier
          ON payment.business_type = '供应商' AND supplier.supplier_code = payment.counterparty_code
        LEFT JOIN public.md_carrier carrier
          ON payment.business_type = '物流商' AND carrier.carrier_code = payment.counterparty_code
        WHERE payment.counterparty_id IS NULL
          AND COALESCE(supplier.id, carrier.id) IS NULL
    ) THEN
        RAISE EXCEPTION 'V32: 付款单往来方编码无法解析 typed party';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.fm_ledger_adjustment adjustment
        LEFT JOIN public.md_customer customer
          ON adjustment.counterparty_type = '客户' AND customer.customer_code = adjustment.counterparty_code
        LEFT JOIN public.md_supplier supplier
          ON adjustment.counterparty_type = '供应商' AND supplier.supplier_code = adjustment.counterparty_code
        LEFT JOIN public.md_carrier carrier
          ON adjustment.counterparty_type = '物流商' AND carrier.carrier_code = adjustment.counterparty_code
        WHERE adjustment.counterparty_id IS NULL
          AND COALESCE(customer.id, supplier.id, carrier.id) IS NULL
    ) THEN
        RAISE EXCEPTION 'V32: 台账调整往来方编码无法解析 typed party';
    END IF;
END $$;

UPDATE public.fm_payment payment
SET counterparty_type = '供应商',
    counterparty_id = supplier.id
FROM public.md_supplier supplier
WHERE payment.counterparty_id IS NULL
  AND payment.business_type = '供应商'
  AND supplier.supplier_code = payment.counterparty_code;

UPDATE public.fm_payment payment
SET counterparty_type = '物流商',
    counterparty_id = carrier.id
FROM public.md_carrier carrier
WHERE payment.counterparty_id IS NULL
  AND payment.business_type = '物流商'
  AND carrier.carrier_code = payment.counterparty_code;

UPDATE public.fm_payment_allocation allocation
SET source_supplier_statement_id = CASE WHEN payment.business_type = '供应商' THEN allocation.source_statement_id END,
    source_freight_statement_id = CASE WHEN payment.business_type = '物流商' THEN allocation.source_statement_id END
FROM public.fm_payment payment
WHERE payment.id = allocation.payment_id;

UPDATE public.fm_receipt_allocation
SET source_customer_statement_id = source_statement_id
WHERE source_customer_statement_id IS NULL;

UPDATE public.fm_ledger_adjustment adjustment SET counterparty_id = customer.id
FROM public.md_customer customer
WHERE adjustment.counterparty_id IS NULL
  AND adjustment.counterparty_type = '客户'
  AND customer.customer_code = adjustment.counterparty_code;

UPDATE public.fm_ledger_adjustment adjustment SET counterparty_id = supplier.id
FROM public.md_supplier supplier
WHERE adjustment.counterparty_id IS NULL
  AND adjustment.counterparty_type = '供应商'
  AND supplier.supplier_code = adjustment.counterparty_code;

UPDATE public.fm_ledger_adjustment adjustment SET counterparty_id = carrier.id
FROM public.md_carrier carrier
WHERE adjustment.counterparty_id IS NULL
  AND adjustment.counterparty_type = '物流商'
  AND carrier.carrier_code = adjustment.counterparty_code;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.fm_payment WHERE counterparty_type IS NULL OR counterparty_id IS NULL)
       OR EXISTS (SELECT 1 FROM public.fm_ledger_adjustment WHERE counterparty_id IS NULL)
       OR EXISTS (SELECT 1 FROM public.fm_receipt_allocation WHERE source_customer_statement_id IS NULL)
       OR EXISTS (
           SELECT 1 FROM public.fm_payment_allocation
           WHERE num_nonnulls(source_supplier_statement_id, source_freight_statement_id) <> 1
       ) THEN
        RAISE EXCEPTION 'V32: 财务 typed identity 回填后仍不完整';
    END IF;
END $$;
