-- 内置默认角色
-- 角色: 采购专员 / 销售专员 / 财务专员 / 仓库管理员
-- 物流职责已合并到以上 4 个角色中，不再单独设立物流专员

INSERT INTO sys_role (id, role_code, role_name, role_type, data_scope, permission_codes, permission_count, permission_summary, user_count, status, remark, deleted_flag)
VALUES
    (700520000000000002, 'PURCHASER',  '采购专员', '业务角色', '本部门', '', 0, '', 0, '正常', '系统内置角色', FALSE),
    (700520000000000003, 'SALES',      '销售专员', '业务角色', '本部门', '', 0, '', 0, '正常', '系统内置角色', FALSE),
    (700520000000000004, 'FINANCE',    '财务专员', '业务角色', '全部',   '', 0, '', 0, '正常', '系统内置角色', FALSE),
    (700520000000000005, 'WAREHOUSE',  '仓库管理员','业务角色','本部门', '', 0, '', 0, '正常', '系统内置角色', FALSE)
ON CONFLICT (role_code) DO UPDATE
SET role_name   = EXCLUDED.role_name,
    role_type   = EXCLUDED.role_type,
    data_scope  = EXCLUDED.data_scope,
    permission_codes = '',
    permission_count = 0,
    permission_summary = '',
    user_count  = 0,
    status      = '正常',
    remark      = EXCLUDED.remark,
    deleted_flag = FALSE,
    updated_name = 'flyway',
    updated_at   = CURRENT_TIMESTAMP;

-- ── 采购专员 (PURCHASER) [含物流单/物流对账单/物流方管理] ──
INSERT INTO sys_role_permission (id, role_id, resource_code, action_code)
SELECT
    (SELECT COALESCE(MAX(id), 0) FROM sys_role_permission) + ROW_NUMBER() OVER (ORDER BY rp.resource_code, rp.action_code),
    p.id,
    rp.resource_code,
    rp.action_code
FROM sys_role p
CROSS JOIN (VALUES
    -- 采购订单/入库/合同
    ('purchase-order',     'read'),
    ('purchase-order',     'create'),
    ('purchase-order',     'update'),
    ('purchase-order',     'delete'),
    ('purchase-order',     'audit'),
    ('purchase-order',     'export'),
    ('purchase-order',     'print'),
    ('purchase-inbound',   'read'),
    ('purchase-inbound',   'create'),
    ('purchase-inbound',   'update'),
    ('purchase-inbound',   'delete'),
    ('purchase-inbound',   'audit'),
    ('purchase-inbound',   'export'),
    ('purchase-inbound',   'print'),
    ('purchase-contract',  'read'),
    ('purchase-contract',  'create'),
    ('purchase-contract',  'update'),
    ('purchase-contract',  'delete'),
    ('purchase-contract',  'audit'),
    ('purchase-contract',  'export'),
    ('purchase-contract',  'print'),
    -- 供应商对账单
    ('supplier-statement', 'read'),
    ('supplier-statement', 'create'),
    ('supplier-statement', 'update'),
    ('supplier-statement', 'audit'),
    ('supplier-statement', 'export'),
    ('supplier-statement', 'print'),
    -- 物流单（采购发货）
    ('freight-bill',       'read'),
    ('freight-bill',       'create'),
    ('freight-bill',       'update'),
    ('freight-bill',       'delete'),
    ('freight-bill',       'audit'),
    ('freight-bill',       'export'),
    ('freight-bill',       'print'),
    -- 物流对账单
    ('freight-statement',  'read'),
    ('freight-statement',  'create'),
    ('freight-statement',  'update'),
    ('freight-statement',  'audit'),
    ('freight-statement',  'export'),
    ('freight-statement',  'print'),
    -- 物流方管理
    ('carrier',            'read'),
    ('carrier',            'create'),
    ('carrier',            'update'),
    -- 主数据/报表
    ('supplier',           'read'),
    ('material',           'read'),
    ('warehouse',          'read'),
    ('inventory-report',   'read'),
    ('io-report',          'read'),
    ('dashboard',          'read')
) AS rp(resource_code, action_code)
WHERE p.role_code = 'PURCHASER' AND p.deleted_flag = FALSE
ON CONFLICT (role_id, resource_code, action_code) DO UPDATE
SET deleted_flag = FALSE,
    updated_name = 'flyway',
    updated_at   = CURRENT_TIMESTAMP;

-- ── 销售专员 (SALES) [含物流单/物流对账单/物流方管理] ──
INSERT INTO sys_role_permission (id, role_id, resource_code, action_code)
SELECT
    (SELECT COALESCE(MAX(id), 0) FROM sys_role_permission) + ROW_NUMBER() OVER (ORDER BY rp.resource_code, rp.action_code),
    s.id,
    rp.resource_code,
    rp.action_code
FROM sys_role s
CROSS JOIN (VALUES
    -- 销售订单/出库/合同
    ('sales-order',        'read'),
    ('sales-order',        'create'),
    ('sales-order',        'update'),
    ('sales-order',        'delete'),
    ('sales-order',        'audit'),
    ('sales-order',        'export'),
    ('sales-order',        'print'),
    ('sales-outbound',     'read'),
    ('sales-outbound',     'create'),
    ('sales-outbound',     'update'),
    ('sales-outbound',     'delete'),
    ('sales-outbound',     'audit'),
    ('sales-outbound',     'export'),
    ('sales-outbound',     'print'),
    ('sales-contract',     'read'),
    ('sales-contract',     'create'),
    ('sales-contract',     'update'),
    ('sales-contract',     'delete'),
    ('sales-contract',     'audit'),
    ('sales-contract',     'export'),
    ('sales-contract',     'print'),
    -- 客户对账单
    ('customer-statement', 'read'),
    ('customer-statement', 'create'),
    ('customer-statement', 'update'),
    ('customer-statement', 'audit'),
    ('customer-statement', 'export'),
    ('customer-statement', 'print'),
    -- 物流单（销售发货）
    ('freight-bill',       'read'),
    ('freight-bill',       'create'),
    ('freight-bill',       'update'),
    ('freight-bill',       'delete'),
    ('freight-bill',       'audit'),
    ('freight-bill',       'export'),
    ('freight-bill',       'print'),
    -- 物流对账单
    ('freight-statement',  'read'),
    ('freight-statement',  'create'),
    ('freight-statement',  'update'),
    ('freight-statement',  'audit'),
    ('freight-statement',  'export'),
    ('freight-statement',  'print'),
    -- 物流方管理
    ('carrier',            'read'),
    ('carrier',            'create'),
    ('carrier',            'update'),
    -- 主数据/报表
    ('customer',           'read'),
    ('material',           'read'),
    ('warehouse',          'read'),
    ('inventory-report',   'read'),
    ('io-report',          'read'),
    ('dashboard',          'read')
) AS rp(resource_code, action_code)
WHERE s.role_code = 'SALES' AND s.deleted_flag = FALSE
ON CONFLICT (role_id, resource_code, action_code) DO UPDATE
SET deleted_flag = FALSE,
    updated_name = 'flyway',
    updated_at   = CURRENT_TIMESTAMP;

-- ── 财务专员 (FINANCE) [含物流对账单审核/物流单审计导出] ──
INSERT INTO sys_role_permission (id, role_id, resource_code, action_code)
SELECT
    (SELECT COALESCE(MAX(id), 0) FROM sys_role_permission) + ROW_NUMBER() OVER (ORDER BY rp.resource_code, rp.action_code),
    f.id,
    rp.resource_code,
    rp.action_code
FROM sys_role f
CROSS JOIN (VALUES
    ('receipt',                    'read'),
    ('receipt',                    'create'),
    ('receipt',                    'update'),
    ('receipt',                    'delete'),
    ('receipt',                    'audit'),
    ('receipt',                    'export'),
    ('receipt',                    'print'),
    ('payment',                    'read'),
    ('payment',                    'create'),
    ('payment',                    'update'),
    ('payment',                    'delete'),
    ('payment',                    'audit'),
    ('payment',                    'export'),
    ('payment',                    'print'),
    ('invoice-receipt',            'read'),
    ('invoice-receipt',            'create'),
    ('invoice-receipt',            'update'),
    ('invoice-receipt',            'delete'),
    ('invoice-receipt',            'audit'),
    ('invoice-receipt',            'export'),
    ('invoice-receipt',            'print'),
    ('invoice-issue',              'read'),
    ('invoice-issue',              'create'),
    ('invoice-issue',              'update'),
    ('invoice-issue',              'delete'),
    ('invoice-issue',              'audit'),
    ('invoice-issue',              'export'),
    ('invoice-issue',              'print'),
    ('receivable-payable',         'read'),
    ('receivable-payable',         'export'),
    -- 三张对账单审计/导出
    ('supplier-statement',         'read'),
    ('supplier-statement',         'audit'),
    ('supplier-statement',         'export'),
    ('customer-statement',         'read'),
    ('customer-statement',         'audit'),
    ('customer-statement',         'export'),
    ('freight-statement',          'read'),
    ('freight-statement',          'audit'),
    ('freight-statement',          'export'),
    -- 物流单审计/导出
    ('freight-bill',               'read'),
    ('freight-bill',               'audit'),
    ('freight-bill',               'export'),
    -- 报表
    ('pending-invoice-receipt-report', 'read'),
    ('pending-invoice-receipt-report', 'export'),
    ('inventory-report',           'read'),
    ('inventory-report',           'export'),
    ('io-report',                  'read'),
    ('io-report',                  'export'),
    ('dashboard',                  'read')
) AS rp(resource_code, action_code)
WHERE f.role_code = 'FINANCE' AND f.deleted_flag = FALSE
ON CONFLICT (role_id, resource_code, action_code) DO UPDATE
SET deleted_flag = FALSE,
    updated_name = 'flyway',
    updated_at   = CURRENT_TIMESTAMP;

-- ── 仓库管理员 (WAREHOUSE) [含物流单处理/物流方查看] ──
INSERT INTO sys_role_permission (id, role_id, resource_code, action_code)
SELECT
    (SELECT COALESCE(MAX(id), 0) FROM sys_role_permission) + ROW_NUMBER() OVER (ORDER BY rp.resource_code, rp.action_code),
    w.id,
    rp.resource_code,
    rp.action_code
FROM sys_role w
CROSS JOIN (VALUES
    -- 入库/出库
    ('purchase-inbound', 'read'),
    ('purchase-inbound', 'update'),
    ('purchase-inbound', 'audit'),
    ('purchase-inbound', 'export'),
    ('sales-outbound',   'read'),
    ('sales-outbound',   'export'),
    -- 物流单（出入库关联）
    ('freight-bill',     'read'),
    ('freight-bill',     'update'),
    ('freight-bill',     'export'),
    -- 主数据/物流方
    ('material',         'read'),
    ('warehouse',        'read'),
    ('carrier',          'read'),
    -- 报表
    ('inventory-report', 'read'),
    ('inventory-report', 'export'),
    ('io-report',        'read'),
    ('io-report',        'export'),
    ('dashboard',        'read')
) AS rp(resource_code, action_code)
WHERE w.role_code = 'WAREHOUSE' AND w.deleted_flag = FALSE
ON CONFLICT (role_id, resource_code, action_code) DO UPDATE
SET deleted_flag = FALSE,
    updated_name = 'flyway',
    updated_at   = CURRENT_TIMESTAMP;
