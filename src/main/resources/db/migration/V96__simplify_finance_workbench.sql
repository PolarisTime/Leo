ALTER TABLE public.fm_payment
    DROP CONSTRAINT IF EXISTS chk_fm_payment_supplier_total_v58;

ALTER TABLE public.fm_payment
    ADD CONSTRAINT chk_fm_payment_direct_v96
    CHECK (
        payment_purpose <> 'SUPPLIER_PAYMENT'
        OR (
            business_type IN ('供应商', '物流商')
            AND counterparty_type = business_type
            AND counterparty_id IS NOT NULL
            AND NULLIF(BTRIM(counterparty_name), '') IS NOT NULL
            AND settlement_company_id IS NOT NULL
            AND NULLIF(BTRIM(settlement_company_name), '') IS NOT NULL
            AND amount > 0
            AND source_statement_id IS NULL
            AND source_purchase_order_id IS NULL
            AND purchase_order_no IS NULL
        )
    ) NOT VALID;

ALTER TABLE public.fm_payment
    VALIDATE CONSTRAINT chk_fm_payment_direct_v96;

ALTER TABLE public.fm_receipt
    DROP CONSTRAINT IF EXISTS chk_fm_receipt_party_shape_v58;

ALTER TABLE public.fm_receipt
    ADD CONSTRAINT chk_fm_receipt_party_shape_v96
    CHECK (
        (
            counterparty_type = '客户'
            AND receipt_purpose = 'CUSTOMER_STATEMENT_SETTLEMENT'
            AND customer_id IS NOT NULL
            AND counterparty_id = customer_id
        )
        OR (
            counterparty_type = '供应商'
            AND receipt_purpose IN ('SUPPLIER_PREPAYMENT_REFUND', 'SUPPLIER_OTHER_RECEIPT')
            AND customer_id IS NULL
            AND project_id IS NULL
            AND source_customer_statement_id IS NULL
        )
    ) NOT VALID;

ALTER TABLE public.fm_receipt
    VALIDATE CONSTRAINT chk_fm_receipt_party_shape_v96;

CREATE INDEX idx_so_sales_outbound_finance_overview_v96
    ON public.so_sales_outbound (settlement_company_id, outbound_date, customer_id)
    WHERE deleted_flag = FALSE AND status = '已审核';

CREATE INDEX idx_po_purchase_inbound_finance_overview_v96
    ON public.po_purchase_inbound (settlement_company_id, inbound_date, supplier_id)
    WHERE deleted_flag = FALSE AND status IN ('已审核', '完成入库');

CREATE INDEX idx_lg_freight_bill_finance_overview_v96
    ON public.lg_freight_bill (settlement_company_id, bill_time, carrier_id)
    WHERE deleted_flag = FALSE AND status = '已审核';

CREATE INDEX idx_fm_receipt_finance_overview_v96
    ON public.fm_receipt (settlement_company_id, receipt_date, counterparty_type, counterparty_id)
    WHERE deleted_flag = FALSE AND status = '已审核';

CREATE INDEX idx_fm_payment_finance_overview_v96
    ON public.fm_payment (settlement_company_id, payment_date, counterparty_type, counterparty_id)
    WHERE deleted_flag = FALSE AND status = '已审核';

UPDATE public.sys_menu
SET menu_code = 'finance-overview',
    menu_name = '财务概览',
    route_path = '/finance-overview',
    icon = 'CalculatorOutlined',
    sort_order = 1,
    status = '正常',
    deleted_flag = FALSE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'cash-ledger';

UPDATE public.sys_menu
SET status = '禁用',
    deleted_flag = TRUE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code IN ('customer-statement', 'freight-statement');

COMMENT ON CONSTRAINT chk_fm_payment_direct_v96 ON public.fm_payment
    IS '简单付款单直接按供应商或物流商及结算主体确认付款，不再强制关联对账单';

COMMENT ON CONSTRAINT chk_fm_receipt_party_shape_v96 ON public.fm_receipt
    IS '简单客户收款按客户与结算主体确认，项目和对账单均为可选历史信息';

COMMENT ON TABLE public.st_customer_statement
    IS '历史客户对账资料；新收款流程不再以客户对账单作为前置条件';

COMMENT ON TABLE public.st_freight_statement
    IS '历史物流对账资料；新付款流程不再以物流对账单作为前置条件';
