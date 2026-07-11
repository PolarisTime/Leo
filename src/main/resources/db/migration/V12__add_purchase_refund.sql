CREATE TABLE public.po_purchase_refund (
    id bigint NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    refund_no character varying(64) NOT NULL,
    source_purchase_order_id bigint NOT NULL,
    purchase_order_no character varying(64) NOT NULL,
    supplier_code character varying(64) NOT NULL,
    supplier_name character varying(128) NOT NULL,
    settlement_company_id bigint,
    settlement_company_name character varying(128),
    refund_date date NOT NULL,
    total_quantity integer NOT NULL,
    total_weight numeric(18,8) NOT NULL,
    total_amount numeric(14,2) NOT NULL,
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
    CONSTRAINT po_purchase_refund_pkey PRIMARY KEY (id),
    CONSTRAINT po_purchase_refund_refund_no_key UNIQUE (refund_no),
    CONSTRAINT chk_po_purchase_refund_status CHECK (status IN ('草稿', '已审核')),
    CONSTRAINT chk_po_purchase_refund_version_nonnegative CHECK (version >= 0),
    CONSTRAINT chk_po_purchase_refund_totals_nonnegative CHECK (
        total_quantity >= 0 AND total_weight >= 0 AND total_amount >= 0
    ),
    CONSTRAINT fk_po_purchase_refund_source_order FOREIGN KEY (source_purchase_order_id)
        REFERENCES public.po_purchase_order(id)
);

CREATE TABLE public.po_purchase_refund_item (
    id bigint NOT NULL,
    refund_id bigint NOT NULL,
    source_purchase_order_item_id bigint NOT NULL,
    line_no integer NOT NULL,
    material_code character varying(64) NOT NULL,
    brand character varying(64) NOT NULL,
    category character varying(16) NOT NULL,
    material character varying(16) NOT NULL,
    spec character varying(64) NOT NULL,
    length character varying(32),
    unit character varying(8) NOT NULL,
    warehouse_name character varying(128),
    batch_no character varying(64),
    quantity integer NOT NULL,
    quantity_unit character varying(8) NOT NULL,
    piece_weight_ton numeric(18,8) NOT NULL,
    pieces_per_bundle integer NOT NULL,
    weight_ton numeric(18,8) NOT NULL,
    unit_price numeric(12,2) NOT NULL,
    amount numeric(14,2) NOT NULL,
    CONSTRAINT po_purchase_refund_item_pkey PRIMARY KEY (id),
    CONSTRAINT uk_po_purchase_refund_item_line UNIQUE (refund_id, line_no),
    CONSTRAINT uk_po_purchase_refund_item_source UNIQUE (refund_id, source_purchase_order_item_id),
    CONSTRAINT chk_po_purchase_refund_item_line_positive CHECK (line_no > 0),
    CONSTRAINT chk_po_purchase_refund_item_values_nonnegative CHECK (
        quantity >= 0
        AND piece_weight_ton >= 0
        AND pieces_per_bundle >= 0
        AND weight_ton >= 0
        AND unit_price >= 0
        AND amount >= 0
    ),
    CONSTRAINT fk_po_purchase_refund_item_head FOREIGN KEY (refund_id)
        REFERENCES public.po_purchase_refund(id) ON DELETE CASCADE,
    CONSTRAINT fk_po_purchase_refund_item_source FOREIGN KEY (source_purchase_order_item_id)
        REFERENCES public.po_purchase_order_item(id)
);

CREATE UNIQUE INDEX uk_po_purchase_refund_source_active
    ON public.po_purchase_refund (source_purchase_order_id)
    WHERE deleted_flag = false;

CREATE INDEX idx_po_purchase_refund_supplier_date
    ON public.po_purchase_refund (supplier_code, refund_date DESC)
    WHERE deleted_flag = false;

CREATE INDEX idx_po_purchase_refund_status_date
    ON public.po_purchase_refund (status, refund_date DESC)
    WHERE deleted_flag = false;

CREATE INDEX idx_po_purchase_refund_item_refund_id
    ON public.po_purchase_refund_item (refund_id);

CREATE INDEX idx_po_purchase_refund_item_source_id
    ON public.po_purchase_refund_item (source_purchase_order_item_id);

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
    'purchase-refund',
    '采购退款单',
    'purchase',
    '/purchase-refund',
    'RollbackOutlined',
    3,
    '菜单',
    '正常',
    0,
    'flyway',
    CURRENT_TIMESTAMP,
    false
WHERE NOT EXISTS (
    SELECT 1 FROM public.sys_menu WHERE menu_code = 'purchase-refund'
);

WITH action_seed(menu_code, action_code, action_name, ordinal) AS (
    VALUES
        ('purchase-refund', 'VIEW', '查看', 1),
        ('purchase-refund', 'CREATE', '新增', 2),
        ('purchase-refund', 'EDIT', '编辑', 3),
        ('purchase-refund', 'DELETE', '删除', 4),
        ('purchase-refund', 'AUDIT', '审核', 5),
        ('purchase-refund', 'EXPORT', '导出', 6),
        ('purchase-refund', 'PRINT', '打印', 7)
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
    false
FROM action_seed
CROSS JOIN max_action_id
ON CONFLICT (menu_code, action_code) DO UPDATE SET
    action_name = EXCLUDED.action_name,
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
    'RULE_PR',
    '采购退款单编号规则',
    '采购退款单',
    'PR{yyyy}{seq}',
    'yyyy',
    6,
    'YEARLY',
    'PR2026000001',
    '正常',
    '采购退款单系统自动编号',
    0,
    'flyway',
    CURRENT_TIMESTAMP,
    false,
    NULL,
    1
WHERE NOT EXISTS (
    SELECT 1 FROM public.sys_no_rule WHERE setting_code = 'RULE_PR'
);

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
    'purchase-refund',
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
