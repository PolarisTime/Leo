CREATE TABLE public.fm_cash_reversal (
    id bigint NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    reversal_no character varying(64) NOT NULL,
    original_payment_id bigint,
    original_receipt_id bigint,
    counterparty_type character varying(32) NOT NULL,
    counterparty_id bigint NOT NULL,
    counterparty_code character varying(64),
    counterparty_name character varying(128) NOT NULL,
    settlement_company_id bigint NOT NULL,
    settlement_company_name character varying(128) NOT NULL,
    reversal_date date NOT NULL,
    amount numeric(14,2) NOT NULL,
    reason character varying(255) NOT NULL,
    status character varying(16) NOT NULL,
    operator_name character varying(32) NOT NULL,
    remark character varying(255),
    created_by bigint NOT NULL DEFAULT 0,
    created_name character varying(64) NOT NULL DEFAULT 'system',
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by bigint,
    updated_name character varying(64),
    updated_at timestamp without time zone,
    deleted_flag boolean NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_fm_cash_reversal PRIMARY KEY (id),
    CONSTRAINT uk_fm_cash_reversal_no UNIQUE (reversal_no),
    CONSTRAINT chk_fm_cash_reversal_version CHECK (version >= 0),
    CONSTRAINT chk_fm_cash_reversal_source CHECK (
        num_nonnulls(original_payment_id, original_receipt_id) = 1
    ),
    CONSTRAINT chk_fm_cash_reversal_amount CHECK (amount > 0),
    CONSTRAINT chk_fm_cash_reversal_status CHECK (status IN ('草稿', '已审核')),
    CONSTRAINT chk_fm_cash_reversal_supplier CHECK (
        counterparty_type = '供应商'
        AND counterparty_id IS NOT NULL
        AND NULLIF(BTRIM(counterparty_name), '') IS NOT NULL
        AND settlement_company_id IS NOT NULL
        AND NULLIF(BTRIM(settlement_company_name), '') IS NOT NULL
    ),
    CONSTRAINT fk_fm_cash_reversal_payment
        FOREIGN KEY (original_payment_id) REFERENCES public.fm_payment (id) ON DELETE RESTRICT,
    CONSTRAINT fk_fm_cash_reversal_receipt
        FOREIGN KEY (original_receipt_id) REFERENCES public.fm_receipt (id) ON DELETE RESTRICT,
    CONSTRAINT fk_fm_cash_reversal_supplier
        FOREIGN KEY (counterparty_id) REFERENCES public.md_supplier (id) ON DELETE RESTRICT,
    CONSTRAINT fk_fm_cash_reversal_company
        FOREIGN KEY (settlement_company_id) REFERENCES public.sys_company_setting (id) ON DELETE RESTRICT
);

COMMENT ON TABLE public.fm_cash_reversal IS '已审核供应商付款或收款的来源约束资金冲销单';
COMMENT ON COLUMN public.fm_cash_reversal.original_payment_id IS '原付款单标识，与原收款单标识二选一';
COMMENT ON COLUMN public.fm_cash_reversal.original_receipt_id IS '原收款单标识，与原付款单标识二选一';
COMMENT ON COLUMN public.fm_cash_reversal.amount IS '本次冲销金额，同一原单已审核累计冲销不得超过原金额';

CREATE INDEX idx_fm_cash_reversal_payment_status
    ON public.fm_cash_reversal (original_payment_id, status)
    WHERE deleted_flag = FALSE AND original_payment_id IS NOT NULL;

CREATE INDEX idx_fm_cash_reversal_receipt_status
    ON public.fm_cash_reversal (original_receipt_id, status)
    WHERE deleted_flag = FALSE AND original_receipt_id IS NOT NULL;

CREATE INDEX idx_fm_cash_reversal_supplier_ledger
    ON public.fm_cash_reversal (
        settlement_company_id,
        counterparty_id,
        reversal_date,
        id
    )
    WHERE deleted_flag = FALSE;

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
    'cash-reversal',
    '资金冲销单',
    'finance',
    '/cash-reversal',
    'RollbackOutlined',
    9,
    '菜单',
    '正常',
    0,
    'flyway',
    CURRENT_TIMESTAMP,
    FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM public.sys_menu WHERE menu_code = 'cash-reversal'
);

WITH action_seed(menu_code, action_code, action_name, ordinal) AS (
    VALUES
        ('cash-reversal', 'VIEW', '查看', 1),
        ('cash-reversal', 'CREATE', '新增', 2),
        ('cash-reversal', 'EDIT', '编辑', 3),
        ('cash-reversal', 'DELETE', '删除', 4),
        ('cash-reversal', 'AUDIT', '审核', 5),
        ('cash-reversal', 'EXPORT', '导出', 6),
        ('cash-reversal', 'PRINT', '打印', 7)
),
max_action_id AS (
    SELECT COALESCE(MAX(id), 0) AS value FROM public.sys_menu_action
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
    FALSE
FROM action_seed
CROSS JOIN max_action_id
ON CONFLICT (menu_code, action_code) DO UPDATE SET
    action_name = EXCLUDED.action_name,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP,
    deleted_flag = FALSE;

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
    SELECT COALESCE(MAX(id), 0) AS value FROM public.sys_role_permission
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
    'cash-reversal',
    permission_seed.action_code,
    0,
    'flyway',
    CURRENT_TIMESTAMP,
    FALSE
FROM public.sys_role role
CROSS JOIN permission_seed
CROSS JOIN max_permission_id
WHERE role.role_code = 'ADMIN'
  AND role.deleted_flag = FALSE
ON CONFLICT (role_id, resource_code, action_code) DO UPDATE SET
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP,
    deleted_flag = FALSE;

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
    'RULE_CR',
    '资金冲销单编号规则',
    '资金冲销单',
    'CR{yyyy}{seq}',
    'yyyy',
    6,
    'YEARLY',
    'CR2026000001',
    '正常',
    '资金冲销单系统自动编号',
    0,
    'flyway',
    CURRENT_TIMESTAMP,
    FALSE,
    NULL,
    1
WHERE NOT EXISTS (
    SELECT 1 FROM public.sys_no_rule WHERE setting_code = 'RULE_CR'
);
