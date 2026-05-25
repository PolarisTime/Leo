-- Keep the built-in ADMIN role aligned with the current resource catalog.
-- This migration only adds missing permissions and does not remove custom grants.

WITH catalog(resource_code, action_code) AS (
    VALUES
        ('dashboard', 'read'),
        ('material', 'read'), ('material', 'create'), ('material', 'update'), ('material', 'delete'), ('material', 'audit'), ('material', 'export'), ('material', 'print'),
        ('supplier', 'read'), ('supplier', 'create'), ('supplier', 'update'), ('supplier', 'delete'), ('supplier', 'audit'), ('supplier', 'export'), ('supplier', 'print'),
        ('customer', 'read'), ('customer', 'create'), ('customer', 'update'), ('customer', 'delete'), ('customer', 'audit'), ('customer', 'export'), ('customer', 'print'),
        ('carrier', 'read'), ('carrier', 'create'), ('carrier', 'update'), ('carrier', 'delete'), ('carrier', 'audit'), ('carrier', 'export'), ('carrier', 'print'),
        ('warehouse', 'read'), ('warehouse', 'create'), ('warehouse', 'update'), ('warehouse', 'delete'), ('warehouse', 'audit'), ('warehouse', 'export'), ('warehouse', 'print'),
        ('purchase-order', 'read'), ('purchase-order', 'create'), ('purchase-order', 'update'), ('purchase-order', 'delete'), ('purchase-order', 'audit'), ('purchase-order', 'export'), ('purchase-order', 'print'),
        ('purchase-inbound', 'read'), ('purchase-inbound', 'create'), ('purchase-inbound', 'update'), ('purchase-inbound', 'delete'), ('purchase-inbound', 'audit'), ('purchase-inbound', 'export'), ('purchase-inbound', 'print'),
        ('sales-order', 'read'), ('sales-order', 'create'), ('sales-order', 'update'), ('sales-order', 'delete'), ('sales-order', 'audit'), ('sales-order', 'export'), ('sales-order', 'print'),
        ('sales-outbound', 'read'), ('sales-outbound', 'create'), ('sales-outbound', 'update'), ('sales-outbound', 'delete'), ('sales-outbound', 'audit'), ('sales-outbound', 'export'), ('sales-outbound', 'print'),
        ('freight-bill', 'read'), ('freight-bill', 'create'), ('freight-bill', 'update'), ('freight-bill', 'delete'), ('freight-bill', 'audit'), ('freight-bill', 'export'), ('freight-bill', 'print'),
        ('purchase-contract', 'read'), ('purchase-contract', 'create'), ('purchase-contract', 'update'), ('purchase-contract', 'delete'), ('purchase-contract', 'audit'), ('purchase-contract', 'export'), ('purchase-contract', 'print'),
        ('sales-contract', 'read'), ('sales-contract', 'create'), ('sales-contract', 'update'), ('sales-contract', 'delete'), ('sales-contract', 'audit'), ('sales-contract', 'export'), ('sales-contract', 'print'),
        ('inventory-report', 'read'), ('inventory-report', 'export'), ('inventory-report', 'print'),
        ('io-report', 'read'), ('io-report', 'export'), ('io-report', 'print'),
        ('pending-invoice-receipt-report', 'read'), ('pending-invoice-receipt-report', 'export'), ('pending-invoice-receipt-report', 'print'),
        ('supplier-statement', 'read'), ('supplier-statement', 'create'), ('supplier-statement', 'update'), ('supplier-statement', 'delete'), ('supplier-statement', 'audit'), ('supplier-statement', 'export'), ('supplier-statement', 'print'),
        ('customer-statement', 'read'), ('customer-statement', 'create'), ('customer-statement', 'update'), ('customer-statement', 'delete'), ('customer-statement', 'audit'), ('customer-statement', 'export'), ('customer-statement', 'print'),
        ('freight-statement', 'read'), ('freight-statement', 'create'), ('freight-statement', 'update'), ('freight-statement', 'delete'), ('freight-statement', 'audit'), ('freight-statement', 'export'), ('freight-statement', 'print'),
        ('receipt', 'read'), ('receipt', 'create'), ('receipt', 'update'), ('receipt', 'delete'), ('receipt', 'audit'), ('receipt', 'export'), ('receipt', 'print'),
        ('payment', 'read'), ('payment', 'create'), ('payment', 'update'), ('payment', 'delete'), ('payment', 'audit'), ('payment', 'export'), ('payment', 'print'),
        ('invoice-receipt', 'read'), ('invoice-receipt', 'create'), ('invoice-receipt', 'update'), ('invoice-receipt', 'delete'), ('invoice-receipt', 'audit'), ('invoice-receipt', 'export'), ('invoice-receipt', 'print'),
        ('invoice-issue', 'read'), ('invoice-issue', 'create'), ('invoice-issue', 'update'), ('invoice-issue', 'delete'), ('invoice-issue', 'audit'), ('invoice-issue', 'export'), ('invoice-issue', 'print'),
        ('receivable-payable', 'read'), ('receivable-payable', 'create'), ('receivable-payable', 'update'), ('receivable-payable', 'delete'), ('receivable-payable', 'audit'), ('receivable-payable', 'export'), ('receivable-payable', 'print'),
        ('general-setting', 'read'), ('general-setting', 'update'),
        ('company-setting', 'read'), ('company-setting', 'create'), ('company-setting', 'update'), ('company-setting', 'delete'),
        ('operation-log', 'read'),
        ('department', 'read'), ('department', 'create'), ('department', 'update'), ('department', 'delete'),
        ('user-account', 'read'), ('user-account', 'create'), ('user-account', 'update'), ('user-account', 'delete'),
        ('permission', 'read'),
        ('role', 'read'), ('role', 'create'), ('role', 'update'), ('role', 'delete'), ('role', 'manage_permissions'),
        ('access-control', 'read'),
        ('database', 'read'), ('database', 'update'), ('database', 'export'),
        ('session', 'read'), ('session', 'update'),
        ('api-key', 'read'), ('api-key', 'create'), ('api-key', 'update'),
        ('security-key', 'read'), ('security-key', 'update'),
        ('print-template', 'read'), ('print-template', 'create'), ('print-template', 'update'), ('print-template', 'delete')
),
missing AS (
    SELECT
        r.id AS role_id,
        c.resource_code,
        c.action_code
    FROM sys_role r
    CROSS JOIN catalog c
    WHERE r.deleted_flag = FALSE
      AND (r.role_code = 'ADMIN' OR r.role_name = '系统管理员')
      AND NOT EXISTS (
          SELECT 1
          FROM sys_role_permission rp
          WHERE rp.role_id = r.id
            AND rp.resource_code = c.resource_code
            AND rp.action_code = c.action_code
            AND rp.deleted_flag = FALSE
      )
)
INSERT INTO sys_role_permission (id, role_id, resource_code, action_code)
SELECT
    (SELECT COALESCE(MAX(id), 700520000000000000) FROM sys_role_permission)
        + ROW_NUMBER() OVER (ORDER BY role_id, resource_code, action_code),
    role_id,
    resource_code,
    action_code
FROM missing;
