-- Remove legacy demo data from V7/V9 while keeping system metadata required by OOBE.

DELETE FROM st_customer_statement_item
WHERE statement_id IN (
    SELECT id FROM st_customer_statement
    WHERE id >= 700000000000000000 AND id < 701000000000000000
);

DELETE FROM st_supplier_statement_item
WHERE statement_id IN (
    SELECT id FROM st_supplier_statement
    WHERE id >= 700000000000000000 AND id < 701000000000000000
);

DELETE FROM st_freight_statement_item
WHERE statement_id IN (
    SELECT id FROM st_freight_statement
    WHERE id >= 700000000000000000 AND id < 701000000000000000
);

DELETE FROM st_customer_statement
WHERE id >= 700000000000000000 AND id < 701000000000000000;

DELETE FROM st_supplier_statement
WHERE id >= 700000000000000000 AND id < 701000000000000000;

DELETE FROM st_freight_statement
WHERE id >= 700000000000000000 AND id < 701000000000000000;

DELETE FROM fm_receipt
WHERE id >= 700000000000000000 AND id < 701000000000000000;

DELETE FROM fm_payment
WHERE id >= 700000000000000000 AND id < 701000000000000000;

DELETE FROM lg_freight_bill_item
WHERE bill_id IN (
    SELECT id FROM lg_freight_bill
    WHERE id >= 700000000000000000 AND id < 701000000000000000
);

DELETE FROM lg_freight_bill
WHERE id >= 700000000000000000 AND id < 701000000000000000;

DELETE FROM so_sales_outbound_item
WHERE outbound_id IN (
    SELECT id FROM so_sales_outbound
    WHERE id >= 700000000000000000 AND id < 701000000000000000
);

DELETE FROM so_sales_outbound
WHERE id >= 700000000000000000 AND id < 701000000000000000;

DELETE FROM so_sales_order_item
WHERE order_id IN (
    SELECT id FROM so_sales_order
    WHERE id >= 700000000000000000 AND id < 701000000000000000
);

DELETE FROM so_sales_order
WHERE id >= 700000000000000000 AND id < 701000000000000000;

DELETE FROM po_purchase_inbound_item
WHERE inbound_id IN (
    SELECT id FROM po_purchase_inbound
    WHERE id >= 700000000000000000 AND id < 701000000000000000
);

DELETE FROM po_purchase_inbound
WHERE id >= 700000000000000000 AND id < 701000000000000000;

DELETE FROM po_purchase_order_item
WHERE order_id IN (
    SELECT id FROM po_purchase_order
    WHERE id >= 700000000000000000 AND id < 701000000000000000
);

DELETE FROM po_purchase_order
WHERE id >= 700000000000000000 AND id < 701000000000000000;

DELETE FROM ct_purchase_contract_item
WHERE contract_id IN (
    SELECT id FROM ct_purchase_contract
    WHERE id >= 700000000000000000 AND id < 701000000000000000
);

DELETE FROM ct_purchase_contract
WHERE id >= 700000000000000000 AND id < 701000000000000000;

DELETE FROM ct_sales_contract_item
WHERE contract_id IN (
    SELECT id FROM ct_sales_contract
    WHERE id >= 700000000000000000 AND id < 701000000000000000
);

DELETE FROM ct_sales_contract
WHERE id >= 700000000000000000 AND id < 701000000000000000;

DELETE FROM md_material
WHERE id >= 700000000000000000 AND id < 701000000000000000;

DELETE FROM md_supplier
WHERE id >= 700000000000000000 AND id < 701000000000000000;

DELETE FROM md_customer
WHERE id >= 700000000000000000 AND id < 701000000000000000;

DELETE FROM md_carrier
WHERE id >= 700000000000000000 AND id < 701000000000000000;

DELETE FROM md_warehouse
WHERE id >= 700000000000000000 AND id < 701000000000000000;

DELETE FROM auth_refresh_token
WHERE user_id IN (
    SELECT id FROM sys_user
    WHERE login_name IN ('buyer01', 'sales01', 'finance01', 'ops01')
      AND remark = 'Mock 用户'
);

DELETE FROM auth_api_key
WHERE user_id IN (
    SELECT id FROM sys_user
    WHERE login_name IN ('buyer01', 'sales01', 'finance01', 'ops01')
      AND remark = 'Mock 用户'
);

DELETE FROM sys_user_role
WHERE user_id IN (
    SELECT id FROM sys_user
    WHERE login_name IN ('buyer01', 'sales01', 'finance01', 'ops01')
      AND remark = 'Mock 用户'
)
OR role_id IN (
    SELECT id FROM sys_role
    WHERE role_code IN ('PURCHASER', 'SALES_MANAGER', 'FINANCE_MANAGER', 'OPS_SUPPORT')
);

DELETE FROM sys_role_permission
WHERE role_id IN (
    SELECT id FROM sys_role
    WHERE role_code IN ('PURCHASER', 'SALES_MANAGER', 'FINANCE_MANAGER', 'OPS_SUPPORT')
);

DELETE FROM sys_role_action
WHERE role_id IN (
    SELECT id FROM sys_role
    WHERE role_code IN ('PURCHASER', 'SALES_MANAGER', 'FINANCE_MANAGER', 'OPS_SUPPORT')
);

DELETE FROM sys_user
WHERE login_name IN ('buyer01', 'sales01', 'finance01', 'ops01')
  AND remark = 'Mock 用户';

DELETE FROM sys_role
WHERE role_code IN ('PURCHASER', 'SALES_MANAGER', 'FINANCE_MANAGER', 'OPS_SUPPORT');

DELETE FROM sys_bootstrap_marker
WHERE marker_code IN ('MOCK_DATA_V7')
   OR id = 700000000000000001;

INSERT INTO sys_role (
    id, role_code, role_name, role_type, data_scope, permission_codes, permission_count, permission_summary,
    user_count, status, remark
) VALUES (
    700520000000000001, 'ADMIN', '系统管理员', '平台角色', '全部', '', 0, '', 0, '正常', '系统内置角色'
)
ON CONFLICT (role_code) DO UPDATE
SET role_name = EXCLUDED.role_name,
    role_type = EXCLUDED.role_type,
    data_scope = EXCLUDED.data_scope,
    permission_codes = '',
    permission_count = 0,
    permission_summary = '',
    user_count = 0,
    status = '正常',
    remark = EXCLUDED.remark,
    deleted_flag = FALSE,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO sys_role_action (id, role_id, menu_code, action_code)
SELECT
    (SELECT COALESCE(MAX(id), 0) FROM sys_role_action) + ROW_NUMBER() OVER (ORDER BY ma.menu_code, ma.action_code),
    admin_role.id,
    ma.menu_code,
    ma.action_code
FROM sys_role admin_role
CROSS JOIN sys_menu_action ma
WHERE admin_role.role_code = 'ADMIN'
  AND admin_role.deleted_flag = FALSE
  AND ma.deleted_flag = FALSE
ON CONFLICT (role_id, menu_code, action_code) DO UPDATE
SET deleted_flag = FALSE,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP;

WITH mapped AS (
    SELECT DISTINCT
        ra.role_id,
        CASE ra.menu_code
            WHEN 'dashboard' THEN 'dashboard'
            WHEN 'materials' THEN 'material'
            WHEN 'suppliers' THEN 'supplier'
            WHEN 'customers' THEN 'customer'
            WHEN 'carriers' THEN 'carrier'
            WHEN 'warehouses' THEN 'warehouse'
            WHEN 'purchase-orders' THEN 'purchase-order'
            WHEN 'purchase-inbounds' THEN 'purchase-inbound'
            WHEN 'sales-orders' THEN 'sales-order'
            WHEN 'sales-outbounds' THEN 'sales-outbound'
            WHEN 'freight-bills' THEN 'freight-bill'
            WHEN 'purchase-contracts' THEN 'purchase-contract'
            WHEN 'sales-contracts' THEN 'sales-contract'
            WHEN 'inventory-report' THEN 'inventory-report'
            WHEN 'io-report' THEN 'io-report'
            WHEN 'pending-invoice-receipt-report' THEN 'pending-invoice-receipt-report'
            WHEN 'supplier-statements' THEN 'supplier-statement'
            WHEN 'customer-statements' THEN 'customer-statement'
            WHEN 'freight-statements' THEN 'freight-statement'
            WHEN 'receipts' THEN 'receipt'
            WHEN 'payments' THEN 'payment'
            WHEN 'invoice-receipts' THEN 'invoice-receipt'
            WHEN 'invoice-issues' THEN 'invoice-issue'
            WHEN 'receivables-payables' THEN 'receivable-payable'
            WHEN 'general-settings' THEN 'general-setting'
            WHEN 'company-settings' THEN 'company-setting'
            WHEN 'operation-logs' THEN 'operation-log'
            WHEN 'departments' THEN 'department'
            WHEN 'user-accounts' THEN 'user-account'
            WHEN 'permission-management' THEN 'permission'
            WHEN 'role-settings' THEN 'role'
            WHEN 'role-action-editor' THEN 'role'
            WHEN 'database-management' THEN 'database'
            WHEN 'ops-support' THEN 'database'
            WHEN 'session-management' THEN 'session'
            WHEN 'api-key-management' THEN 'api-key'
            WHEN 'security-keys' THEN 'security-key'
            WHEN 'print-templates' THEN 'print-template'
            ELSE NULL
        END AS resource_code,
        CASE
            WHEN ra.menu_code = 'role-action-editor'
                 AND UPPER(BTRIM(ra.action_code)) IN ('EDIT', 'UPDATE', 'MANAGE_PERMISSIONS')
                THEN 'manage_permissions'
            WHEN UPPER(BTRIM(ra.action_code)) = 'VIEW' THEN 'read'
            WHEN UPPER(BTRIM(ra.action_code)) = 'CREATE' THEN 'create'
            WHEN UPPER(BTRIM(ra.action_code)) = 'EDIT' THEN 'update'
            WHEN UPPER(BTRIM(ra.action_code)) = 'DELETE' THEN 'delete'
            WHEN UPPER(BTRIM(ra.action_code)) = 'AUDIT' THEN 'audit'
            WHEN UPPER(BTRIM(ra.action_code)) = 'EXPORT' THEN 'export'
            WHEN UPPER(BTRIM(ra.action_code)) = 'PRINT' THEN 'print'
            ELSE LOWER(BTRIM(ra.action_code))
        END AS action_code
    FROM sys_role_action ra
    JOIN sys_role admin_role ON admin_role.id = ra.role_id
    WHERE admin_role.role_code = 'ADMIN'
      AND admin_role.deleted_flag = FALSE
      AND ra.deleted_flag = FALSE
),
expanded AS (
    SELECT role_id, resource_code, action_code
    FROM mapped
    WHERE resource_code IS NOT NULL
      AND action_code IS NOT NULL
    UNION
    SELECT role_id, resource_code, 'read'
    FROM mapped
    WHERE resource_code IS NOT NULL
      AND action_code IS NOT NULL
      AND action_code <> 'read'
)
INSERT INTO sys_role_permission (id, role_id, resource_code, action_code)
SELECT
    (SELECT COALESCE(MAX(id), 0) FROM sys_role_permission)
        + ROW_NUMBER() OVER (ORDER BY deduped.role_id, deduped.resource_code, deduped.action_code),
    deduped.role_id,
    deduped.resource_code,
    deduped.action_code
FROM (
    SELECT DISTINCT role_id, resource_code, action_code
    FROM expanded
) deduped
ON CONFLICT (role_id, resource_code, action_code) DO UPDATE
SET deleted_flag = FALSE,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP;
