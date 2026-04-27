CREATE TABLE IF NOT EXISTS sys_company_setting (
    id BIGINT PRIMARY KEY,
    company_name VARCHAR(128) NOT NULL UNIQUE,
    tax_no VARCHAR(64) NOT NULL,
    bank_name VARCHAR(128) NOT NULL,
    bank_account VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_name VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_name VARCHAR(64),
    updated_at TIMESTAMP,
    deleted_flag BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_sys_company_setting_tax_no ON sys_company_setting (tax_no);

CREATE TABLE IF NOT EXISTS md_settlement_account (
    id BIGINT PRIMARY KEY,
    account_name VARCHAR(128) NOT NULL,
    company_name VARCHAR(128) NOT NULL,
    bank_name VARCHAR(128) NOT NULL,
    bank_account VARCHAR(64) NOT NULL UNIQUE,
    usage_type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_name VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_name VARCHAR(64),
    updated_at TIMESTAMP,
    deleted_flag BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_md_settlement_account_company_name ON md_settlement_account (company_name);
CREATE INDEX IF NOT EXISTS idx_md_settlement_account_account_name ON md_settlement_account (account_name);

INSERT INTO sys_menu (id, menu_code, menu_name, parent_code, route_path, icon, sort_order, menu_type)
VALUES
    (2006, 'settlement-accounts', '结算账户', 'master', '/settlement-accounts', 'BankOutlined', 6, '菜单'),
    (10012, 'company-settings', '公司信息', 'system', '/company-settings', 'AccountBookOutlined', 12, '菜单')
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu_action (id, menu_code, action_code, action_name)
VALUES
    (20061, 'settlement-accounts', 'VIEW', '查看'),
    (20062, 'settlement-accounts', 'CREATE', '新增'),
    (20063, 'settlement-accounts', 'EDIT', '编辑'),
    (20064, 'settlement-accounts', 'DELETE', '删除'),
    (20065, 'settlement-accounts', 'AUDIT', '审核'),
    (20066, 'settlement-accounts', 'EXPORT', '导出'),
    (20067, 'settlement-accounts', 'PRINT', '打印'),
    (100121, 'company-settings', 'VIEW', '查看'),
    (100122, 'company-settings', 'CREATE', '新增'),
    (100123, 'company-settings', 'EDIT', '编辑'),
    (100124, 'company-settings', 'DELETE', '删除')
ON CONFLICT (menu_code, action_code) DO NOTHING;

INSERT INTO sys_role_action (id, role_id, menu_code, action_code)
SELECT
    (SELECT COALESCE(MAX(id), 0) FROM sys_role_action) + ROW_NUMBER() OVER (),
    r.id,
    ma.menu_code,
    ma.action_code
FROM sys_role r
JOIN sys_menu_action ma
  ON ma.menu_code IN ('settlement-accounts', 'company-settings')
WHERE r.role_code IN ('ADMIN', 'FINANCE_MANAGER')
ON CONFLICT (role_id, menu_code, action_code) DO NOTHING;
