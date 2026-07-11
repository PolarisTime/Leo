UPDATE public.fm_payment
SET version = 0
WHERE version IS NULL;

ALTER TABLE public.fm_payment
    ALTER COLUMN version SET DEFAULT 0,
    ALTER COLUMN version SET NOT NULL;

ALTER TABLE public.fm_payment
    ADD COLUMN payment_purpose character varying(32) NOT NULL DEFAULT 'STATEMENT_SETTLEMENT',
    ADD COLUMN source_purchase_order_id bigint,
    ADD COLUMN purchase_order_no character varying(64),
    ADD COLUMN supplier_code character varying(64),
    ADD COLUMN supplier_name character varying(128),
    ADD COLUMN settlement_company_id bigint,
    ADD COLUMN settlement_company_name character varying(128),
    ADD CONSTRAINT chk_fm_payment_purpose CHECK (
        payment_purpose IN ('STATEMENT_SETTLEMENT', 'PURCHASE_PREPAYMENT')
    ),
    ADD CONSTRAINT chk_fm_payment_purchase_prepayment CHECK (
        payment_purpose <> 'PURCHASE_PREPAYMENT'
        OR (
            business_type = '供应商'
            AND source_statement_id IS NULL
            AND source_purchase_order_id IS NOT NULL
            AND NULLIF(BTRIM(purchase_order_no), '') IS NOT NULL
            AND NULLIF(BTRIM(supplier_code), '') IS NOT NULL
            AND NULLIF(BTRIM(supplier_name), '') IS NOT NULL
            AND settlement_company_id IS NOT NULL
            AND NULLIF(BTRIM(settlement_company_name), '') IS NOT NULL
            AND amount > 0
        )
    ),
    ADD CONSTRAINT chk_fm_payment_statement_settlement CHECK (
        payment_purpose <> 'STATEMENT_SETTLEMENT'
        OR source_purchase_order_id IS NULL
    ),
    ADD CONSTRAINT chk_fm_payment_settlement_company_pair CHECK (
        (
            settlement_company_id IS NULL
            AND NULLIF(BTRIM(settlement_company_name), '') IS NULL
        )
        OR (
            settlement_company_id IS NOT NULL
            AND NULLIF(BTRIM(settlement_company_name), '') IS NOT NULL
        )
    ),
    ADD CONSTRAINT chk_fm_payment_version_nonnegative CHECK (version >= 0),
    ADD CONSTRAINT fk_fm_payment_source_purchase_order FOREIGN KEY (source_purchase_order_id)
        REFERENCES public.po_purchase_order(id);

COMMENT ON COLUMN public.fm_payment.payment_purpose IS '付款用途：STATEMENT_SETTLEMENT 对账付款，PURCHASE_PREPAYMENT 采购预付款';
COMMENT ON COLUMN public.fm_payment.source_purchase_order_id IS '采购预付款来源采购订单标识';
COMMENT ON COLUMN public.fm_payment.purchase_order_no IS '采购预付款来源采购订单号快照';
COMMENT ON COLUMN public.fm_payment.supplier_code IS '采购预付款供应商编码快照';
COMMENT ON COLUMN public.fm_payment.supplier_name IS '采购预付款供应商名称快照';
COMMENT ON COLUMN public.fm_payment.settlement_company_id IS '采购预付款结算主体标识快照';
COMMENT ON COLUMN public.fm_payment.settlement_company_name IS '采购预付款结算主体名称快照';

CREATE INDEX idx_fm_payment_purpose_status
    ON public.fm_payment (payment_purpose, status)
    WHERE deleted_flag = false;

CREATE INDEX idx_fm_payment_source_purchase_order
    ON public.fm_payment (source_purchase_order_id, status)
    WHERE deleted_flag = false
      AND source_purchase_order_id IS NOT NULL;

CREATE INDEX idx_fm_payment_supplier_code_date
    ON public.fm_payment (supplier_code, payment_date DESC)
    WHERE deleted_flag = false
      AND supplier_code IS NOT NULL;

CREATE INDEX idx_fm_payment_settlement_company_date
    ON public.fm_payment (settlement_company_id, payment_date DESC)
    WHERE deleted_flag = false
      AND settlement_company_id IS NOT NULL;

CREATE TABLE public.fm_supplier_refund_receipt (
    id bigint NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    refund_receipt_no character varying(64) NOT NULL,
    purchase_refund_id bigint NOT NULL,
    supplier_code character varying(64) NOT NULL,
    supplier_name character varying(128) NOT NULL,
    settlement_company_id bigint,
    settlement_company_name character varying(128),
    receipt_date date NOT NULL,
    receipt_method character varying(32) NOT NULL,
    amount numeric(14,2) NOT NULL,
    status character varying(16) NOT NULL,
    operator_name character varying(32) NOT NULL,
    remark character varying(255),
    created_by bigint DEFAULT 0 NOT NULL,
    created_name character varying(64) DEFAULT 'system' NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by bigint,
    updated_name character varying(64),
    updated_at timestamp without time zone,
    deleted_flag boolean DEFAULT false NOT NULL,
    CONSTRAINT fm_supplier_refund_receipt_pkey PRIMARY KEY (id),
    CONSTRAINT fm_supplier_refund_receipt_no_key UNIQUE (refund_receipt_no),
    CONSTRAINT chk_fm_supplier_refund_receipt_version CHECK (version >= 0),
    CONSTRAINT chk_fm_supplier_refund_receipt_amount CHECK (amount > 0),
    CONSTRAINT chk_fm_supplier_refund_receipt_status CHECK (status IN ('草稿', '已收款')),
    CONSTRAINT chk_fm_supplier_refund_receipt_settlement_pair CHECK (
        (
            settlement_company_id IS NULL
            AND NULLIF(BTRIM(settlement_company_name), '') IS NULL
        )
        OR (
            settlement_company_id IS NOT NULL
            AND NULLIF(BTRIM(settlement_company_name), '') IS NOT NULL
        )
    ),
    CONSTRAINT fk_fm_supplier_refund_receipt_refund FOREIGN KEY (purchase_refund_id)
        REFERENCES public.po_purchase_refund(id)
);

COMMENT ON TABLE public.fm_supplier_refund_receipt IS '供应商退款到账单，支持同一采购退款分次到账';
COMMENT ON COLUMN public.fm_supplier_refund_receipt.purchase_refund_id IS '来源采购退款单标识';
COMMENT ON COLUMN public.fm_supplier_refund_receipt.supplier_code IS '来源采购退款单供应商编码快照';
COMMENT ON COLUMN public.fm_supplier_refund_receipt.supplier_name IS '来源采购退款单供应商名称快照';
COMMENT ON COLUMN public.fm_supplier_refund_receipt.settlement_company_id IS '来源采购退款单结算主体标识快照';
COMMENT ON COLUMN public.fm_supplier_refund_receipt.settlement_company_name IS '来源采购退款单结算主体名称快照';

CREATE INDEX idx_fm_supplier_refund_receipt_refund_status
    ON public.fm_supplier_refund_receipt (purchase_refund_id, status)
    WHERE deleted_flag = false;

CREATE INDEX idx_fm_supplier_refund_receipt_supplier_date
    ON public.fm_supplier_refund_receipt (supplier_code, receipt_date DESC)
    WHERE deleted_flag = false;

CREATE INDEX idx_fm_supplier_refund_receipt_settlement_date
    ON public.fm_supplier_refund_receipt (settlement_company_id, receipt_date DESC)
    WHERE deleted_flag = false;

CREATE INDEX idx_fm_supplier_refund_receipt_no_trgm
    ON public.fm_supplier_refund_receipt USING gin (refund_receipt_no public.gin_trgm_ops)
    WHERE deleted_flag = false;

CREATE INDEX idx_fm_supplier_refund_receipt_supplier_name_trgm
    ON public.fm_supplier_refund_receipt USING gin (supplier_name public.gin_trgm_ops)
    WHERE deleted_flag = false;

INSERT INTO public.sys_menu (
    id,
    menu_code,
    menu_name,
    parent_code,
    route_path,
    icon,
    sort_order,
    menu_type,
    status,
    created_by,
    created_name,
    created_at,
    deleted_flag
)
SELECT
    (SELECT COALESCE(MAX(id), 0) + 1 FROM public.sys_menu),
    'supplier-refund-receipt',
    '供应商退款到账单',
    'finance',
    '/supplier-refund-receipt',
    'RollbackOutlined',
    8,
    '菜单',
    '正常',
    0,
    'flyway',
    CURRENT_TIMESTAMP,
    false
WHERE NOT EXISTS (
    SELECT 1
    FROM public.sys_menu
    WHERE menu_code = 'supplier-refund-receipt'
);

WITH action_seed(menu_code, action_code, action_name, ordinal) AS (
    VALUES
        ('supplier-refund-receipt', 'VIEW', '查看', 1),
        ('supplier-refund-receipt', 'CREATE', '新增', 2),
        ('supplier-refund-receipt', 'EDIT', '编辑', 3),
        ('supplier-refund-receipt', 'DELETE', '删除', 4),
        ('supplier-refund-receipt', 'AUDIT', '收款', 5),
        ('supplier-refund-receipt', 'EXPORT', '导出', 6),
        ('supplier-refund-receipt', 'PRINT', '打印', 7)
),
max_action_id AS (
    SELECT COALESCE(MAX(id), 0) AS value
    FROM public.sys_menu_action
)
INSERT INTO public.sys_menu_action (
    id,
    menu_code,
    action_code,
    action_name,
    created_by,
    created_name,
    created_at,
    deleted_flag
)
SELECT
    max_action_id.value + ROW_NUMBER() OVER (ORDER BY action_seed.ordinal),
    action_seed.menu_code,
    action_seed.action_code,
    action_seed.action_name,
    0,
    'flyway',
    CURRENT_TIMESTAMP,
    false
FROM action_seed
CROSS JOIN max_action_id
ON CONFLICT (menu_code, action_code) DO UPDATE SET
    action_name = EXCLUDED.action_name,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP,
    deleted_flag = false;

WITH permission_seed(action_code, ordinal) AS (
    VALUES
        ('read', 1),
        ('create', 2),
        ('update', 3),
        ('delete', 4),
        ('audit', 5),
        ('export', 6),
        ('print', 7)
),
max_permission_id AS (
    SELECT COALESCE(MAX(id), 0) AS value
    FROM public.sys_role_permission
)
INSERT INTO public.sys_role_permission (
    id,
    role_id,
    resource_code,
    action_code,
    created_by,
    created_name,
    created_at,
    deleted_flag
)
SELECT
    max_permission_id.value + ROW_NUMBER() OVER (ORDER BY role.id, permission_seed.ordinal),
    role.id,
    'supplier-refund-receipt',
    permission_seed.action_code,
    0,
    'flyway',
    CURRENT_TIMESTAMP,
    false
FROM public.sys_role role
CROSS JOIN permission_seed
CROSS JOIN max_permission_id
WHERE role.role_code = 'ADMIN'
  AND role.deleted_flag = false
ON CONFLICT (role_id, resource_code, action_code) DO UPDATE SET
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP,
    deleted_flag = false;

INSERT INTO public.sys_no_rule (
    id,
    setting_code,
    setting_name,
    bill_name,
    prefix,
    date_rule,
    serial_length,
    reset_rule,
    sample_no,
    status,
    remark,
    created_by,
    created_name,
    created_at,
    deleted_flag,
    current_period,
    next_serial_value
)
SELECT
    (SELECT COALESCE(MAX(id), 0) + 1 FROM public.sys_no_rule),
    'RULE_SRR',
    '供应商退款到账单编号规则',
    '供应商退款到账单',
    'SRR{yyyy}{seq}',
    'yyyy',
    6,
    'YEARLY',
    'SRR2026000001',
    '正常',
    '供应商退款到账单系统自动编号',
    0,
    'flyway',
    CURRENT_TIMESTAMP,
    false,
    NULL,
    1
WHERE NOT EXISTS (
    SELECT 1
    FROM public.sys_no_rule
    WHERE setting_code = 'RULE_SRR'
);
