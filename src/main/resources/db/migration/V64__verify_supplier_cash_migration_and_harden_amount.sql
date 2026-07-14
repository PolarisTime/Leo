DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.fm_supplier_refund_receipt legacy
        LEFT JOIN public.fm_receipt receipt
          ON receipt.id = legacy.id
         AND receipt.receipt_no = legacy.refund_receipt_no
         AND receipt.counterparty_type = '供应商'
         AND receipt.counterparty_id = legacy.supplier_id
         AND receipt.counterparty_code IS NOT DISTINCT FROM legacy.supplier_code
         AND receipt.counterparty_name = legacy.supplier_name
         AND receipt.receipt_purpose = 'SUPPLIER_PREPAYMENT_REFUND'
         AND receipt.customer_id IS NULL
         AND receipt.customer_code IS NULL
         AND receipt.customer_name IS NULL
         AND receipt.project_id IS NULL
         AND receipt.project_name IS NULL
         AND receipt.source_customer_statement_id IS NULL
         AND receipt.receipt_date = legacy.receipt_date::timestamp
         AND receipt.pay_type = legacy.receipt_method
         AND receipt.amount = legacy.amount
         AND receipt.status = CASE
             WHEN legacy.status = '已收款' THEN '已审核'
             ELSE legacy.status
         END
         AND receipt.operator_name = legacy.operator_name
         AND receipt.remark IS NOT DISTINCT FROM legacy.remark
         AND receipt.created_by = legacy.created_by
         AND receipt.created_name = legacy.created_name
         AND receipt.created_at = legacy.created_at
         AND receipt.updated_by IS NOT DISTINCT FROM legacy.updated_by
         AND receipt.updated_name IS NOT DISTINCT FROM legacy.updated_name
         AND receipt.updated_at IS NOT DISTINCT FROM legacy.updated_at
         AND receipt.deleted_flag = legacy.deleted_flag
         AND receipt.version = legacy.version
         AND receipt.settlement_company_id = legacy.settlement_company_id
         AND receipt.settlement_company_name = legacy.settlement_company_name
        WHERE receipt.id IS NULL
    ) THEN
        RAISE EXCEPTION 'V64: 旧供应商退款到账单迁入通用收款单后存在字段缺失或快照不一致';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.fm_payment
        WHERE amount IS NULL OR amount <= 0
    ) THEN
        RAISE EXCEPTION 'V64: 历史付款单存在金额小于等于0的数据，需先形成业务确认的数据修复迁移';
    END IF;
END $$;

ALTER TABLE public.fm_payment
    ADD CONSTRAINT chk_fm_payment_amount_positive
    CHECK (amount > 0) NOT VALID;

ALTER TABLE public.fm_payment
    VALIDATE CONSTRAINT chk_fm_payment_amount_positive;

COMMENT ON CONSTRAINT chk_fm_payment_amount_positive ON public.fm_payment
    IS '所有付款用途的表头金额必须大于0';
