ALTER TABLE public.fm_receipt
    ADD COLUMN counterparty_type character varying(32),
    ADD COLUMN counterparty_id bigint,
    ADD COLUMN counterparty_code character varying(64),
    ADD COLUMN counterparty_name character varying(128),
    ADD COLUMN receipt_purpose character varying(32);

CREATE TABLE public.fm_supplier_ledger_lock (
    settlement_company_id bigint NOT NULL,
    supplier_id bigint NOT NULL,
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_fm_supplier_ledger_lock PRIMARY KEY (settlement_company_id, supplier_id),
    CONSTRAINT fk_fm_supplier_ledger_lock_company
        FOREIGN KEY (settlement_company_id) REFERENCES public.sys_company_setting (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_fm_supplier_ledger_lock_supplier
        FOREIGN KEY (supplier_id) REFERENCES public.md_supplier (id)
        ON DELETE RESTRICT
);

COMMENT ON TABLE public.fm_supplier_ledger_lock
    IS '按结算主体与供应商串行化资金审核和预付款余额校验的事务锁根';

UPDATE public.fm_receipt
SET counterparty_type = '客户',
    counterparty_id = customer_id,
    counterparty_code = customer_code,
    counterparty_name = customer_name,
    receipt_purpose = 'CUSTOMER_STATEMENT_SETTLEMENT'
WHERE counterparty_type IS NULL;

ALTER TABLE public.fm_receipt
    ALTER COLUMN customer_name DROP NOT NULL,
    ALTER COLUMN project_name DROP NOT NULL,
    ALTER COLUMN customer_id DROP NOT NULL,
    ALTER COLUMN project_id DROP NOT NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.fm_supplier_refund_receipt legacy
        JOIN public.fm_receipt receipt
          ON receipt.id = legacy.id
          OR receipt.receipt_no = legacy.refund_receipt_no
    ) THEN
        RAISE EXCEPTION 'V58: 旧供应商退款到账单与通用收款单存在ID或单号冲突，无法无损迁移';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.fm_supplier_refund_receipt legacy
        WHERE legacy.supplier_id IS NULL
           OR NULLIF(BTRIM(legacy.supplier_name), '') IS NULL
           OR legacy.settlement_company_id IS NULL
           OR NULLIF(BTRIM(legacy.settlement_company_name), '') IS NULL
           OR legacy.amount IS NULL
           OR legacy.amount <= 0
    ) THEN
        RAISE EXCEPTION 'V58: 旧供应商退款到账单缺少供应商或结算主体身份，或金额不合法';
    END IF;
END $$;

INSERT INTO public.fm_receipt (
    id,
    receipt_no,
    counterparty_type,
    counterparty_id,
    counterparty_code,
    counterparty_name,
    receipt_purpose,
    customer_name,
    project_name,
    receipt_date,
    pay_type,
    amount,
    status,
    operator_name,
    remark,
    created_by,
    created_name,
    created_at,
    updated_by,
    updated_name,
    updated_at,
    deleted_flag,
    version,
    settlement_company_id,
    settlement_company_name
)
SELECT
    legacy.id,
    legacy.refund_receipt_no,
    '供应商',
    legacy.supplier_id,
    legacy.supplier_code,
    legacy.supplier_name,
    'SUPPLIER_PREPAYMENT_REFUND',
    NULL,
    NULL,
    legacy.receipt_date,
    legacy.receipt_method,
    legacy.amount,
    CASE WHEN legacy.status = '已收款' THEN '已审核' ELSE legacy.status END,
    legacy.operator_name,
    legacy.remark,
    legacy.created_by,
    legacy.created_name,
    legacy.created_at,
    legacy.updated_by,
    legacy.updated_name,
    legacy.updated_at,
    legacy.deleted_flag,
    legacy.version,
    legacy.settlement_company_id,
    legacy.settlement_company_name
FROM public.fm_supplier_refund_receipt legacy;

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
         AND receipt.amount = legacy.amount
         AND receipt.settlement_company_id = legacy.settlement_company_id
         AND receipt.settlement_company_name = legacy.settlement_company_name
         AND receipt.deleted_flag = legacy.deleted_flag
        WHERE receipt.id IS NULL
    ) THEN
        RAISE EXCEPTION 'V58: 旧供应商退款到账单迁移结果不完整';
    END IF;
END $$;

UPDATE public.fm_payment
SET status = '已审核'
WHERE status = '已付款';

UPDATE public.fm_receipt
SET status = '已审核'
WHERE status = '已收款';

ALTER TABLE public.fm_payment
    DROP CONSTRAINT IF EXISTS chk_payment_status;

ALTER TABLE public.fm_payment
    ADD CONSTRAINT chk_payment_status_v58
    CHECK (status IN ('草稿', '已审核')) NOT VALID;

ALTER TABLE public.fm_payment
    VALIDATE CONSTRAINT chk_payment_status_v58;

ALTER TABLE public.fm_payment
    RENAME CONSTRAINT chk_payment_status_v58 TO chk_payment_status;

ALTER TABLE public.fm_receipt
    DROP CONSTRAINT IF EXISTS chk_receipt_status;

ALTER TABLE public.fm_receipt
    ADD CONSTRAINT chk_receipt_status_v58
    CHECK (status IN ('草稿', '已审核')) NOT VALID;

ALTER TABLE public.fm_receipt
    VALIDATE CONSTRAINT chk_receipt_status_v58;

ALTER TABLE public.fm_receipt
    RENAME CONSTRAINT chk_receipt_status_v58 TO chk_receipt_status;

ALTER TABLE public.fm_receipt
    ALTER COLUMN counterparty_type SET NOT NULL,
    ALTER COLUMN counterparty_id SET NOT NULL,
    ALTER COLUMN counterparty_name SET NOT NULL,
    ALTER COLUMN receipt_purpose SET NOT NULL;

ALTER TABLE public.fm_payment
    DROP CONSTRAINT chk_fm_payment_purpose,
    ADD CONSTRAINT chk_fm_payment_purpose
    CHECK (payment_purpose IN (
        'STATEMENT_SETTLEMENT',
        'PURCHASE_PREPAYMENT',
        'SUPPLIER_PAYMENT'
    ));

ALTER TABLE public.fm_payment
    ADD CONSTRAINT chk_fm_payment_supplier_total_v58
    CHECK (
        (
            payment_purpose = 'SUPPLIER_PAYMENT'
            AND business_type = '供应商'
            AND counterparty_type = '供应商'
            AND counterparty_id IS NOT NULL
            AND NULLIF(BTRIM(counterparty_name), '') IS NOT NULL
            AND settlement_company_id IS NOT NULL
            AND NULLIF(BTRIM(settlement_company_name), '') IS NOT NULL
            AND amount > 0
            AND source_statement_id IS NULL
            AND source_purchase_order_id IS NULL
            AND purchase_order_no IS NULL
        )
        OR payment_purpose IN ('STATEMENT_SETTLEMENT', 'PURCHASE_PREPAYMENT')
    ) NOT VALID;

ALTER TABLE public.fm_payment
    VALIDATE CONSTRAINT chk_fm_payment_supplier_total_v58;

ALTER TABLE public.fm_receipt
    ADD CONSTRAINT chk_fm_receipt_counterparty_type_v58
    CHECK (counterparty_type IN ('客户', '供应商')) NOT VALID,
    ADD CONSTRAINT chk_fm_receipt_purpose_v58
    CHECK (receipt_purpose IN (
        'CUSTOMER_STATEMENT_SETTLEMENT',
        'SUPPLIER_PREPAYMENT_REFUND',
        'SUPPLIER_OTHER_RECEIPT'
    )) NOT VALID,
    ADD CONSTRAINT chk_fm_receipt_party_shape_v58
    CHECK (
        (counterparty_type = '客户'
            AND receipt_purpose = 'CUSTOMER_STATEMENT_SETTLEMENT'
            AND customer_id IS NOT NULL
            AND counterparty_id = customer_id
            AND project_id IS NOT NULL)
        OR
        (counterparty_type = '供应商'
            AND receipt_purpose IN ('SUPPLIER_PREPAYMENT_REFUND', 'SUPPLIER_OTHER_RECEIPT')
            AND customer_id IS NULL
            AND project_id IS NULL
            AND source_customer_statement_id IS NULL)
    ) NOT VALID,
    ADD CONSTRAINT chk_fm_receipt_ledger_shape_v58
    CHECK (
        amount > 0
        AND NULLIF(BTRIM(counterparty_name), '') IS NOT NULL
        AND (
            counterparty_type = '客户'
            OR (
                counterparty_type = '供应商'
                AND settlement_company_id IS NOT NULL
                AND NULLIF(BTRIM(settlement_company_name), '') IS NOT NULL
            )
        )
    ) NOT VALID;

ALTER TABLE public.fm_receipt
    VALIDATE CONSTRAINT chk_fm_receipt_counterparty_type_v58;

ALTER TABLE public.fm_receipt
    VALIDATE CONSTRAINT chk_fm_receipt_purpose_v58;

ALTER TABLE public.fm_receipt
    VALIDATE CONSTRAINT chk_fm_receipt_party_shape_v58;

ALTER TABLE public.fm_receipt
    VALIDATE CONSTRAINT chk_fm_receipt_ledger_shape_v58;

CREATE INDEX idx_fm_receipt_counterparty_ledger_v58
    ON public.fm_receipt (
        settlement_company_id,
        counterparty_type,
        counterparty_id,
        receipt_date,
        id
    )
    WHERE deleted_flag = FALSE;

CREATE INDEX idx_fm_payment_counterparty_ledger_v58
    ON public.fm_payment (
        settlement_company_id,
        counterparty_type,
        counterparty_id,
        payment_date,
        id
    )
    WHERE deleted_flag = FALSE;

UPDATE public.sys_menu
SET status = '禁用',
    deleted_flag = TRUE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'supplier-refund-receipt';

UPDATE public.sys_menu_action
SET deleted_flag = TRUE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'supplier-refund-receipt';

UPDATE public.sys_role_permission
SET deleted_flag = TRUE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE resource_code = 'supplier-refund-receipt';

UPDATE public.sys_no_rule
SET status = '禁用',
    deleted_flag = TRUE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE setting_code = 'RULE_SRR';

COMMENT ON COLUMN public.fm_receipt.counterparty_type IS '往来方类型：客户或供应商';
COMMENT ON COLUMN public.fm_receipt.counterparty_id IS '按往来方类型指向客户或供应商稳定标识';
COMMENT ON COLUMN public.fm_receipt.counterparty_code IS '往来方编码快照';
COMMENT ON COLUMN public.fm_receipt.counterparty_name IS '往来方名称快照';
COMMENT ON COLUMN public.fm_receipt.receipt_purpose IS '客户对账收款、供应商预付款退款或供应商其他收款';
COMMENT ON COLUMN public.fm_payment.payment_purpose IS '供应商或物流对账付款、采购预付款或供应商总额付款';
