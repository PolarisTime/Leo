CREATE TABLE IF NOT EXISTS fm_ledger_adjustment (
    id BIGINT PRIMARY KEY,
    version BIGINT,
    adjustment_no VARCHAR(64) NOT NULL UNIQUE,
    direction VARCHAR(16) NOT NULL,
    counterparty_type VARCHAR(32) NOT NULL,
    counterparty_code VARCHAR(64) NOT NULL,
    counterparty_name VARCHAR(128) NOT NULL,
    project_id BIGINT,
    project_name VARCHAR(200),
    adjustment_date DATE NOT NULL,
    amount NUMERIC(14, 2) NOT NULL,
    adjustment_type VARCHAR(32) NOT NULL,
    effect VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    operator_name VARCHAR(32) NOT NULL,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_name VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_name VARCHAR(64),
    updated_at TIMESTAMP,
    deleted_flag BOOLEAN NOT NULL DEFAULT FALSE
);

ALTER TABLE fm_ledger_adjustment DROP CONSTRAINT IF EXISTS chk_ledger_adjustment_direction;
ALTER TABLE fm_ledger_adjustment ADD CONSTRAINT chk_ledger_adjustment_direction
    CHECK (direction IN ('应收', '应付'));

ALTER TABLE fm_ledger_adjustment DROP CONSTRAINT IF EXISTS chk_ledger_adjustment_counterparty_type;
ALTER TABLE fm_ledger_adjustment ADD CONSTRAINT chk_ledger_adjustment_counterparty_type
    CHECK (counterparty_type IN ('客户', '供应商', '物流商'));

ALTER TABLE fm_ledger_adjustment DROP CONSTRAINT IF EXISTS chk_ledger_adjustment_effect;
ALTER TABLE fm_ledger_adjustment ADD CONSTRAINT chk_ledger_adjustment_effect
    CHECK (effect IN ('增加余额', '减少余额'));

ALTER TABLE fm_ledger_adjustment DROP CONSTRAINT IF EXISTS chk_ledger_adjustment_type;
ALTER TABLE fm_ledger_adjustment ADD CONSTRAINT chk_ledger_adjustment_type
    CHECK (adjustment_type IN ('坏账', '抹零', '折让', '其他调整'));

ALTER TABLE fm_ledger_adjustment DROP CONSTRAINT IF EXISTS chk_ledger_adjustment_status;
ALTER TABLE fm_ledger_adjustment ADD CONSTRAINT chk_ledger_adjustment_status
    CHECK (status IN ('草稿', '已审核'));

ALTER TABLE fm_ledger_adjustment DROP CONSTRAINT IF EXISTS chk_ledger_adjustment_amount;
ALTER TABLE fm_ledger_adjustment ADD CONSTRAINT chk_ledger_adjustment_amount
    CHECK (amount > 0);

CREATE INDEX IF NOT EXISTS idx_fm_ledger_adjustment_status
    ON fm_ledger_adjustment (status)
    WHERE deleted_flag = FALSE;

CREATE INDEX IF NOT EXISTS idx_fm_ledger_adjustment_counterparty
    ON fm_ledger_adjustment (direction, counterparty_type, counterparty_code)
    WHERE deleted_flag = FALSE;

CREATE INDEX IF NOT EXISTS idx_fm_ledger_adjustment_date
    ON fm_ledger_adjustment (adjustment_date)
    WHERE deleted_flag = FALSE;

INSERT INTO sys_menu (id, menu_code, menu_name, parent_code, route_path, icon, sort_order, menu_type, status, created_by, created_name)
SELECT 9008, 'ledger-adjustment', '台账调整单', 'finance', '/ledger-adjustment', 'AuditOutlined', 7, '菜单', '正常', 0, 'system'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_menu
    WHERE menu_code = 'ledger-adjustment'
      AND deleted_flag = FALSE
);

WITH actions(ord, code, name) AS (
    VALUES
        (1, 'VIEW', '查看'),
        (2, 'CREATE', '新增'),
        (3, 'EDIT', '编辑'),
        (4, 'DELETE', '删除'),
        (5, 'AUDIT', '审核'),
        (6, 'EXPORT', '导出'),
        (7, 'PRINT', '打印')
)
INSERT INTO sys_menu_action (id, menu_code, action_code, action_name, created_by, created_name)
SELECT 9008 * 10 + actions.ord, 'ledger-adjustment', actions.code, actions.name, 0, 'system'
FROM actions
ON CONFLICT (menu_code, action_code) DO NOTHING;

WITH admin_roles AS (
    SELECT id AS role_id
    FROM sys_role
    WHERE role_code = 'ADMIN'
      AND deleted_flag = FALSE
),
actions AS (
    SELECT action_code
    FROM sys_menu_action
    WHERE menu_code = 'ledger-adjustment'
      AND deleted_flag = FALSE
)
INSERT INTO sys_role_action (id, role_id, menu_code, action_code, created_by, created_name)
SELECT
    COALESCE((SELECT MAX(id) FROM sys_role_action), 0) + ROW_NUMBER() OVER (),
    admin_roles.role_id,
    'ledger-adjustment',
    actions.action_code,
    0,
    'system'
FROM admin_roles
CROSS JOIN actions
ON CONFLICT (role_id, menu_code, action_code) DO NOTHING;

WITH admin_roles AS (
    SELECT id AS role_id
    FROM sys_role
    WHERE (role_code = 'ADMIN' OR role_name = '系统管理员')
      AND deleted_flag = FALSE
),
actions AS (
    SELECT *
    FROM (VALUES
        ('read'),
        ('create'),
        ('update'),
        ('delete'),
        ('audit'),
        ('export'),
        ('print')
    ) AS action_values(action_code)
)
INSERT INTO sys_role_permission (id, role_id, resource_code, action_code, created_by, created_name)
SELECT
    COALESCE((SELECT MAX(id) FROM sys_role_permission), 700520000000000000) + ROW_NUMBER() OVER (),
    admin_roles.role_id,
    'ledger-adjustment',
    actions.action_code,
    0,
    'system'
FROM admin_roles
CROSS JOIN actions
ON CONFLICT (role_id, resource_code, action_code) DO UPDATE
SET deleted_flag = FALSE,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP;

WITH finance_roles AS (
    SELECT id AS role_id
    FROM sys_role
    WHERE role_code = 'FINANCE'
      AND deleted_flag = FALSE
),
actions AS (
    SELECT *
    FROM (VALUES
        ('read'),
        ('create'),
        ('update'),
        ('delete'),
        ('audit'),
        ('export'),
        ('print')
    ) AS action_values(action_code)
)
INSERT INTO sys_role_permission (id, role_id, resource_code, action_code, created_by, created_name)
SELECT
    COALESCE((SELECT MAX(id) FROM sys_role_permission), 700520000000000000) + ROW_NUMBER() OVER (),
    finance_roles.role_id,
    'ledger-adjustment',
    actions.action_code,
    0,
    'system'
FROM finance_roles
CROSS JOIN actions
ON CONFLICT (role_id, resource_code, action_code) DO UPDATE
SET deleted_flag = FALSE,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP;

WITH rule_seed(setting_code, setting_name, bill_name, prefix, date_rule, serial_length, reset_rule, sample_no, remark) AS (
    VALUES ('RULE_LA', '台账调整单编号规则', '台账调整单', 'LA{yyyy}{seq}', 'yyyy', 6, 'YEARLY', 'LA2026000001', '台账调整单系统自动编号')
)
INSERT INTO sys_no_rule (
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
    created_name
)
SELECT
    COALESCE((SELECT MAX(id) FROM sys_no_rule), 700000000000000000) + 1,
    setting_code,
    setting_name,
    bill_name,
    prefix,
    date_rule,
    serial_length,
    reset_rule,
    sample_no,
    '正常',
    remark,
    0,
    'system'
FROM rule_seed
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_no_rule
    WHERE setting_code = 'RULE_LA'
      AND deleted_flag = FALSE
);
