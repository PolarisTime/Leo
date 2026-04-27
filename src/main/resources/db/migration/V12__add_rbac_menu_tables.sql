-- ============================================================
-- RBAC 权限体系重构：菜单驱动权限模型
-- ============================================================

-- 1. 菜单定义表（树形结构）
CREATE TABLE IF NOT EXISTS sys_menu (
    id BIGINT PRIMARY KEY,
    menu_code VARCHAR(64) NOT NULL UNIQUE,
    menu_name VARCHAR(64) NOT NULL,
    parent_code VARCHAR(64),
    route_path VARCHAR(128),
    icon VARCHAR(64),
    sort_order INTEGER NOT NULL DEFAULT 0,
    menu_type VARCHAR(16) NOT NULL DEFAULT '菜单',
    status VARCHAR(16) NOT NULL DEFAULT '正常',
    created_by BIGINT NOT NULL DEFAULT 0,
    created_name VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_name VARCHAR(64),
    updated_at TIMESTAMP,
    deleted_flag BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_sys_menu_parent_code ON sys_menu (parent_code);

-- 2. 菜单操作表（每个菜单可配的操作）
CREATE TABLE IF NOT EXISTS sys_menu_action (
    id BIGINT PRIMARY KEY,
    menu_code VARCHAR(64) NOT NULL,
    action_code VARCHAR(32) NOT NULL,
    action_name VARCHAR(32) NOT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_name VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_name VARCHAR(64),
    updated_at TIMESTAMP,
    deleted_flag BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (menu_code, action_code)
);

CREATE INDEX IF NOT EXISTS idx_sys_menu_action_menu_code ON sys_menu_action (menu_code);

-- 3. 用户-角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_name VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_name VARCHAR(64),
    updated_at TIMESTAMP,
    deleted_flag BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (user_id, role_id)
);

CREATE INDEX IF NOT EXISTS idx_sys_user_role_user_id ON sys_user_role (user_id);
CREATE INDEX IF NOT EXISTS idx_sys_user_role_role_id ON sys_user_role (role_id);

-- 4. 角色-操作权限关联表
CREATE TABLE IF NOT EXISTS sys_role_action (
    id BIGINT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    menu_code VARCHAR(64) NOT NULL,
    action_code VARCHAR(32) NOT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_name VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_name VARCHAR(64),
    updated_at TIMESTAMP,
    deleted_flag BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (role_id, menu_code, action_code)
);

CREATE INDEX IF NOT EXISTS idx_sys_role_action_role_id ON sys_role_action (role_id);

-- ============================================================
-- 种子数据：菜单定义
-- ============================================================

-- 顶级目录
INSERT INTO sys_menu (id, menu_code, menu_name, parent_code, route_path, icon, sort_order, menu_type) VALUES
(1001, 'dashboard',     '工作台',     NULL, '/dashboard',     'HomeOutlined',        1, '菜单'),
(1002, 'master',        '主数据管理',  NULL, NULL,             'AppstoreOutlined',    2, '目录'),
(1003, 'purchase',      '采购管理',    NULL, NULL,             'ShoppingCartOutlined',3, '目录'),
(1004, 'sales',         '销售管理',    NULL, NULL,             'ShopOutlined',        4, '目录'),
(1005, 'freight',       '物流管理',    NULL, NULL,             'CarOutlined',         5, '目录'),
(1006, 'contracts',     '合同管理',    NULL, NULL,             'FileTextOutlined',    6, '目录'),
(1007, 'reports',       '报表中心',    NULL, NULL,             'TableOutlined',       7, '目录'),
(1008, 'statements',    '对账管理',    NULL, NULL,             'FileTextOutlined',    8, '目录'),
(1009, 'finance',       '财务管理',    NULL, NULL,             'WalletOutlined',      9, '目录'),
(1010, 'system',        '系统设置',    NULL, NULL,             'SettingOutlined',    10, '目录')
ON CONFLICT (menu_code) DO NOTHING;

-- 主数据管理子菜单
INSERT INTO sys_menu (id, menu_code, menu_name, parent_code, route_path, icon, sort_order, menu_type) VALUES
(2001, 'materials',   '商品资料',   'master',    '/materials',   'DatabaseOutlined', 1, '菜单'),
(2002, 'suppliers',   '供应商资料', 'master',    '/suppliers',   'TeamOutlined',     2, '菜单'),
(2003, 'customers',   '客户资料',   'master',    '/customers',   'UserOutlined',     3, '菜单'),
(2004, 'carriers',    '物流方资料', 'master',    '/carriers',    'CarOutlined',      4, '菜单'),
(2005, 'warehouses',  '仓库资料',   'master',    '/warehouses',  'BankOutlined',     5, '菜单')
ON CONFLICT (menu_code) DO NOTHING;

-- 采购管理子菜单
INSERT INTO sys_menu (id, menu_code, menu_name, parent_code, route_path, icon, sort_order, menu_type) VALUES
(3001, 'purchase-orders',    '采购订单', 'purchase', '/purchase-orders',    'ProfileOutlined', 1, '菜单'),
(3002, 'purchase-inbounds',  '采购入库', 'purchase', '/purchase-inbounds',  'InboxOutlined',   2, '菜单')
ON CONFLICT (menu_code) DO NOTHING;

-- 销售管理子菜单
INSERT INTO sys_menu (id, menu_code, menu_name, parent_code, route_path, icon, sort_order, menu_type) VALUES
(4001, 'sales-orders',    '销售订单', 'sales', '/sales-orders',    'FileDoneOutlined', 1, '菜单'),
(4002, 'sales-outbounds', '销售出库', 'sales', '/sales-outbounds', 'SwapOutlined',     2, '菜单')
ON CONFLICT (menu_code) DO NOTHING;

-- 物流管理子菜单
INSERT INTO sys_menu (id, menu_code, menu_name, parent_code, route_path, icon, sort_order, menu_type) VALUES
(5001, 'freight-bills', '物流单', 'freight', '/freight-bills', 'CarOutlined', 1, '菜单')
ON CONFLICT (menu_code) DO NOTHING;

-- 合同管理子菜单
INSERT INTO sys_menu (id, menu_code, menu_name, parent_code, route_path, icon, sort_order, menu_type) VALUES
(6001, 'purchase-contracts', '采购合同', 'contracts', '/purchase-contracts', 'ProfileOutlined',  1, '菜单'),
(6002, 'sales-contracts',    '销售合同', 'contracts', '/sales-contracts',    'FileDoneOutlined', 2, '菜单')
ON CONFLICT (menu_code) DO NOTHING;

-- 报表中心子菜单
INSERT INTO sys_menu (id, menu_code, menu_name, parent_code, route_path, icon, sort_order, menu_type) VALUES
(7001, 'inventory-report', '商品库存报表', 'reports', '/inventory-report', 'BarChartOutlined', 1, '菜单'),
(7002, 'io-report',        '出入库报表',   'reports', '/io-report',        'SwapOutlined',     2, '菜单')
ON CONFLICT (menu_code) DO NOTHING;

-- 对账管理子菜单
INSERT INTO sys_menu (id, menu_code, menu_name, parent_code, route_path, icon, sort_order, menu_type) VALUES
(8001, 'supplier-statements', '供应商对账单', 'statements', '/supplier-statements', 'FileSearchOutlined', 1, '菜单'),
(8002, 'customer-statements', '客户对账单',   'statements', '/customer-statements', 'FileTextOutlined',   2, '菜单'),
(8003, 'freight-statements',  '物流对账单',   'statements', '/freight-statements',  'FileSyncOutlined',   3, '菜单')
ON CONFLICT (menu_code) DO NOTHING;

-- 财务管理子菜单
INSERT INTO sys_menu (id, menu_code, menu_name, parent_code, route_path, icon, sort_order, menu_type) VALUES
(9001, 'receipts',           '收款单',   'finance', '/receipts',           'AccountBookOutlined', 1, '菜单'),
(9002, 'payments',           '付款单',   'finance', '/payments',           'CreditCardOutlined',  2, '菜单'),
(9003, 'receivables-payables','应收应付', 'finance', '/receivables-payables','CalculatorOutlined', 3, '菜单')
ON CONFLICT (menu_code) DO NOTHING;

-- 系统设置子菜单
INSERT INTO sys_menu (id, menu_code, menu_name, parent_code, route_path, icon, sort_order, menu_type) VALUES
(10001, 'general-settings',    '通用设置',     'system', '/general-settings',    'SettingOutlined',         1, '菜单'),
(10002, 'operation-logs',      '操作日志',     'system', '/operation-logs',      'FileSearchOutlined',      2, '菜单'),
(10003, 'permission-management','权限管理',    'system', '/permission-management','TeamOutlined',           3, '菜单'),
(10004, 'user-accounts',       '用户账户',     'system', '/user-accounts',       'UserOutlined',            4, '菜单'),
(10005, 'role-settings',       '角色设置',     'system', '/role-settings',       'AccountBookOutlined',     5, '菜单'),
(10006, 'ops-support',         '数据库管理',   'system', '/ops-support',         'DatabaseOutlined',        6, '菜单'),
(10007, 'session-management',  '会话管理',     'system', '/session-management',  'SafetyCertificateOutlined',7, '菜单'),
(10008, 'api-key-management',  'API Key 管理', 'system', '/api-key-management',  'SafetyCertificateOutlined',8, '菜单'),
(10009, 'print-templates',     '打印模板',     'system', '/print-templates',     'PrinterOutlined',         9, '菜单')
ON CONFLICT (menu_code) DO NOTHING;

-- ============================================================
-- 种子数据：菜单操作（业务菜单有 CRUD + 审核/导出/打印，系统菜单仅有查看）
-- ============================================================

-- 业务菜单操作（查看/新增/编辑/删除/审核/导出/打印）
INSERT INTO sys_menu_action (id, menu_code, action_code, action_name)
SELECT m.id * 10 + a.ord, m.menu_code, a.code, a.name
FROM sys_menu m
CROSS JOIN (VALUES
    (1, 'VIEW',   '查看'),
    (2, 'CREATE', '新增'),
    (3, 'EDIT',   '编辑'),
    (4, 'DELETE', '删除'),
    (5, 'AUDIT',  '审核'),
    (6, 'EXPORT', '导出'),
    (7, 'PRINT',  '打印')
) AS a(ord, code, name)
WHERE m.menu_type = '菜单'
  AND m.parent_code IN ('master', 'purchase', 'sales', 'freight', 'contracts', 'statements', 'finance')
ON CONFLICT (menu_code, action_code) DO NOTHING;

-- 报表菜单操作（仅查看/导出/打印）
INSERT INTO sys_menu_action (id, menu_code, action_code, action_name)
SELECT m.id * 10 + a.ord, m.menu_code, a.code, a.name
FROM sys_menu m
CROSS JOIN (VALUES
    (1, 'VIEW',   '查看'),
    (6, 'EXPORT', '导出'),
    (7, 'PRINT',  '打印')
) AS a(ord, code, name)
WHERE m.parent_code = 'reports'
ON CONFLICT (menu_code, action_code) DO NOTHING;

-- 系统菜单操作（仅查看）
INSERT INTO sys_menu_action (id, menu_code, action_code, action_name)
SELECT m.id * 10 + 1, m.menu_code, 'VIEW', '查看'
FROM sys_menu m
WHERE m.parent_code = 'system' OR m.menu_code = 'dashboard'
ON CONFLICT (menu_code, action_code) DO NOTHING;

-- ============================================================
-- 种子数据：迁移旧角色数据到 sys_role_action
-- ============================================================

-- 管理员角色：拥有所有菜单所有操作
INSERT INTO sys_role_action (id, role_id, menu_code, action_code)
SELECT
    (SELECT COALESCE(MAX(id), 0) FROM sys_role_action) + ROW_NUMBER() OVER (),
    r.id,
    ma.menu_code,
    ma.action_code
FROM sys_role r
CROSS JOIN sys_menu_action ma
WHERE r.role_code = 'ADMIN'
ON CONFLICT (role_id, menu_code, action_code) DO NOTHING;

-- 采购主管：采购/主数据/合同相关
INSERT INTO sys_role_action (id, role_id, menu_code, action_code)
SELECT
    (SELECT COALESCE(MAX(id), 0) FROM sys_role_action) + ROW_NUMBER() OVER (),
    r.id,
    ma.menu_code,
    ma.action_code
FROM sys_role r
CROSS JOIN sys_menu_action ma
WHERE r.role_code = 'PURCHASER'
  AND (ma.menu_code IN ('materials', 'suppliers', 'warehouses', 'purchase-orders', 'purchase-inbounds', 'purchase-contracts')
       OR ma.menu_code = 'dashboard')
ON CONFLICT (role_id, menu_code, action_code) DO NOTHING;

-- 销售经理：销售/合同相关
INSERT INTO sys_role_action (id, role_id, menu_code, action_code)
SELECT
    (SELECT COALESCE(MAX(id), 0) FROM sys_role_action) + ROW_NUMBER() OVER (),
    r.id,
    ma.menu_code,
    ma.action_code
FROM sys_role r
CROSS JOIN sys_menu_action ma
WHERE r.role_code = 'SALES_MANAGER'
  AND (ma.menu_code IN ('customers', 'sales-orders', 'sales-outbounds', 'sales-contracts', 'supplier-statements', 'customer-statements')
       OR ma.menu_code = 'dashboard')
ON CONFLICT (role_id, menu_code, action_code) DO NOTHING;

-- 财务专员：财务相关
INSERT INTO sys_role_action (id, role_id, menu_code, action_code)
SELECT
    (SELECT COALESCE(MAX(id), 0) FROM sys_role_action) + ROW_NUMBER() OVER (),
    r.id,
    ma.menu_code,
    ma.action_code
FROM sys_role r
CROSS JOIN sys_menu_action ma
WHERE r.role_code = 'FINANCE_MANAGER'
  AND (ma.menu_code IN ('receipts', 'payments', 'receivables-payables', 'supplier-statements', 'customer-statements', 'freight-statements')
       OR ma.menu_code = 'dashboard')
ON CONFLICT (role_id, menu_code, action_code) DO NOTHING;

-- ============================================================
-- 种子数据：迁移旧用户角色到 sys_user_role
-- ============================================================

INSERT INTO sys_user_role (id, user_id, role_id)
SELECT
    ROW_NUMBER() OVER () + (SELECT COALESCE(MAX(id), 0) FROM sys_user_role),
    u.id,
    r.id
FROM sys_user u
JOIN sys_role r ON r.role_code = u.role_name
WHERE u.role_name IS NOT NULL AND u.deleted_flag = FALSE
ON CONFLICT (user_id, role_id) DO NOTHING;
