CREATE TABLE IF NOT EXISTS fm_invoice_receipt (
    id BIGINT PRIMARY KEY,
    receive_no VARCHAR(32) NOT NULL UNIQUE,
    invoice_no VARCHAR(64) NOT NULL,
    supplier_name VARCHAR(128) NOT NULL,
    invoice_date DATE NOT NULL,
    invoice_type VARCHAR(32) NOT NULL,
    amount NUMERIC(14, 2) NOT NULL,
    tax_amount NUMERIC(14, 2) NOT NULL,
    status VARCHAR(16) NOT NULL,
    operator_name VARCHAR(64) NOT NULL,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_name VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_name VARCHAR(64),
    updated_at TIMESTAMP,
    deleted_flag BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_fm_invoice_receipt_supplier_date
    ON fm_invoice_receipt (supplier_name, invoice_date);

CREATE TABLE IF NOT EXISTS fm_invoice_issue (
    id BIGINT PRIMARY KEY,
    issue_no VARCHAR(32) NOT NULL UNIQUE,
    invoice_no VARCHAR(64) NOT NULL,
    customer_name VARCHAR(128) NOT NULL,
    project_name VARCHAR(200) NOT NULL,
    invoice_date DATE NOT NULL,
    invoice_type VARCHAR(32) NOT NULL,
    amount NUMERIC(14, 2) NOT NULL,
    tax_amount NUMERIC(14, 2) NOT NULL,
    status VARCHAR(16) NOT NULL,
    operator_name VARCHAR(64) NOT NULL,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_name VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_name VARCHAR(64),
    updated_at TIMESTAMP,
    deleted_flag BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_fm_invoice_issue_customer_date
    ON fm_invoice_issue (customer_name, invoice_date);

INSERT INTO sys_menu (id, menu_code, menu_name, parent_code, route_path, icon, sort_order, menu_type)
VALUES
    (9004, 'invoice-receipts', '收票单', 'finance', '/invoice-receipts', 'FileTextOutlined', 4, '菜单'),
    (9005, 'invoice-issues', '开票单', 'finance', '/invoice-issues', 'FileDoneOutlined', 5, '菜单')
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu_action (id, menu_code, action_code, action_name)
VALUES
    (90041, 'invoice-receipts', 'VIEW', '查看'),
    (90042, 'invoice-receipts', 'CREATE', '新增'),
    (90043, 'invoice-receipts', 'EDIT', '编辑'),
    (90044, 'invoice-receipts', 'DELETE', '删除'),
    (90045, 'invoice-receipts', 'AUDIT', '审核'),
    (90046, 'invoice-receipts', 'EXPORT', '导出'),
    (90047, 'invoice-receipts', 'PRINT', '打印'),
    (90051, 'invoice-issues', 'VIEW', '查看'),
    (90052, 'invoice-issues', 'CREATE', '新增'),
    (90053, 'invoice-issues', 'EDIT', '编辑'),
    (90054, 'invoice-issues', 'DELETE', '删除'),
    (90055, 'invoice-issues', 'AUDIT', '审核'),
    (90056, 'invoice-issues', 'EXPORT', '导出'),
    (90057, 'invoice-issues', 'PRINT', '打印')
ON CONFLICT (menu_code, action_code) DO NOTHING;

INSERT INTO sys_role_action (id, role_id, menu_code, action_code)
SELECT
    (SELECT COALESCE(MAX(id), 0) FROM sys_role_action) + ROW_NUMBER() OVER (),
    r.id,
    ma.menu_code,
    ma.action_code
FROM sys_role r
JOIN sys_menu_action ma
  ON ma.menu_code IN ('invoice-receipts', 'invoice-issues')
WHERE r.role_code IN ('ADMIN', 'FINANCE_MANAGER')
ON CONFLICT (role_id, menu_code, action_code) DO NOTHING;

INSERT INTO fm_invoice_receipt (
    id, receive_no, invoice_no, supplier_name, invoice_date, invoice_type, amount, tax_amount, status, operator_name, remark
) VALUES
    (700780000000000001, '2026SP000001', '031002600011', '江苏钢联供应链有限公司', DATE '2026-04-15', '增值税专票', 12000.00, 1560.00, '已收票', '财务主管-周敏', '对应 4 月首批采购入库')
ON CONFLICT (receive_no) DO NOTHING;

INSERT INTO fm_invoice_issue (
    id, issue_no, invoice_no, customer_name, project_name, invoice_date, invoice_type, amount, tax_amount, status, operator_name, remark
) VALUES
    (700781000000000001, '2026KP000001', '044002600021', '南京城建项目管理有限公司', '江北快速路一期', DATE '2026-04-18', '增值税专票', 8000.00, 1040.00, '已开票', '财务主管-周敏', '对应客户首笔回款开票')
ON CONFLICT (issue_no) DO NOTHING;
