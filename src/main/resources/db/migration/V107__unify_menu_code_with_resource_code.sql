-- ============================================================
-- 统一 menu_code 与 resource_code：消除命名不一致
-- ============================================================

-- 叶子节点：menu_code 重命名为 resource_code
UPDATE sys_menu SET menu_code = 'material'            WHERE menu_code = 'materials';
UPDATE sys_menu SET menu_code = 'supplier'            WHERE menu_code = 'suppliers';
UPDATE sys_menu SET menu_code = 'customer'            WHERE menu_code = 'customers';
UPDATE sys_menu SET menu_code = 'carrier'             WHERE menu_code = 'carriers';
UPDATE sys_menu SET menu_code = 'warehouse'           WHERE menu_code = 'warehouses';
UPDATE sys_menu SET menu_code = 'purchase-order'      WHERE menu_code = 'purchase-orders';
UPDATE sys_menu SET menu_code = 'purchase-inbound'    WHERE menu_code = 'purchase-inbounds';
UPDATE sys_menu SET menu_code = 'sales-order'         WHERE menu_code = 'sales-orders';
UPDATE sys_menu SET menu_code = 'sales-outbound'      WHERE menu_code = 'sales-outbounds';
UPDATE sys_menu SET menu_code = 'freight-bill'        WHERE menu_code = 'freight-bills';
UPDATE sys_menu SET menu_code = 'supplier-statement'  WHERE menu_code = 'supplier-statements';
UPDATE sys_menu SET menu_code = 'customer-statement'  WHERE menu_code = 'customer-statements';
UPDATE sys_menu SET menu_code = 'freight-statement'   WHERE menu_code = 'freight-statements';
UPDATE sys_menu SET menu_code = 'receipt'             WHERE menu_code = 'receipts';
UPDATE sys_menu SET menu_code = 'payment'             WHERE menu_code = 'payments';
UPDATE sys_menu SET menu_code = 'invoice-receipt'     WHERE menu_code = 'invoice-receipts';
UPDATE sys_menu SET menu_code = 'invoice-issue'       WHERE menu_code = 'invoice-issues';
UPDATE sys_menu SET menu_code = 'receivable-payable'  WHERE menu_code = 'receivables-payables';
UPDATE sys_menu SET menu_code = 'general-setting'     WHERE menu_code = 'general-settings';
UPDATE sys_menu SET menu_code = 'company-setting'     WHERE menu_code = 'company-settings';
UPDATE sys_menu SET menu_code = 'operation-log'       WHERE menu_code = 'operation-logs';
UPDATE sys_menu SET menu_code = 'department'          WHERE menu_code = 'departments';
UPDATE sys_menu SET menu_code = 'database'            WHERE menu_code = 'database-management';
UPDATE sys_menu SET menu_code = 'session'             WHERE menu_code = 'session-management';
UPDATE sys_menu SET menu_code = 'api-key'             WHERE menu_code = 'api-key-management';
UPDATE sys_menu SET menu_code = 'security-key'        WHERE menu_code = 'security-keys';
UPDATE sys_menu SET menu_code = 'print-template'      WHERE menu_code = 'print-templates';

-- sys_menu_action 同步
UPDATE sys_menu_action SET menu_code = 'material'            WHERE menu_code = 'materials';
UPDATE sys_menu_action SET menu_code = 'supplier'            WHERE menu_code = 'suppliers';
UPDATE sys_menu_action SET menu_code = 'customer'            WHERE menu_code = 'customers';
UPDATE sys_menu_action SET menu_code = 'carrier'             WHERE menu_code = 'carriers';
UPDATE sys_menu_action SET menu_code = 'warehouse'           WHERE menu_code = 'warehouses';
UPDATE sys_menu_action SET menu_code = 'purchase-order'      WHERE menu_code = 'purchase-orders';
UPDATE sys_menu_action SET menu_code = 'purchase-inbound'    WHERE menu_code = 'purchase-inbounds';
UPDATE sys_menu_action SET menu_code = 'sales-order'         WHERE menu_code = 'sales-orders';
UPDATE sys_menu_action SET menu_code = 'sales-outbound'      WHERE menu_code = 'sales-outbounds';
UPDATE sys_menu_action SET menu_code = 'freight-bill'        WHERE menu_code = 'freight-bills';
UPDATE sys_menu_action SET menu_code = 'supplier-statement'  WHERE menu_code = 'supplier-statements';
UPDATE sys_menu_action SET menu_code = 'customer-statement'  WHERE menu_code = 'customer-statements';
UPDATE sys_menu_action SET menu_code = 'freight-statement'   WHERE menu_code = 'freight-statements';
UPDATE sys_menu_action SET menu_code = 'receipt'             WHERE menu_code = 'receipts';
UPDATE sys_menu_action SET menu_code = 'payment'             WHERE menu_code = 'payments';
UPDATE sys_menu_action SET menu_code = 'invoice-receipt'     WHERE menu_code = 'invoice-receipts';
UPDATE sys_menu_action SET menu_code = 'invoice-issue'       WHERE menu_code = 'invoice-issues';
UPDATE sys_menu_action SET menu_code = 'receivable-payable'  WHERE menu_code = 'receivables-payables';
UPDATE sys_menu_action SET menu_code = 'general-setting'     WHERE menu_code = 'general-settings';
UPDATE sys_menu_action SET menu_code = 'company-setting'     WHERE menu_code = 'company-settings';
UPDATE sys_menu_action SET menu_code = 'operation-log'       WHERE menu_code = 'operation-logs';
UPDATE sys_menu_action SET menu_code = 'department'          WHERE menu_code = 'departments';
UPDATE sys_menu_action SET menu_code = 'database'            WHERE menu_code = 'database-management';
UPDATE sys_menu_action SET menu_code = 'session'             WHERE menu_code = 'session-management';
UPDATE sys_menu_action SET menu_code = 'api-key'             WHERE menu_code = 'api-key-management';
UPDATE sys_menu_action SET menu_code = 'security-key'        WHERE menu_code = 'security-keys';
UPDATE sys_menu_action SET menu_code = 'print-template'      WHERE menu_code = 'print-templates';
