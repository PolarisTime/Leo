-- ============================================================
-- route_path 同步为单数（与 menu_code / resource_code 一致）
-- ============================================================

UPDATE sys_menu SET route_path = '/material'            WHERE menu_code = 'material';
UPDATE sys_menu SET route_path = '/supplier'            WHERE menu_code = 'supplier';
UPDATE sys_menu SET route_path = '/customer'            WHERE menu_code = 'customer';
UPDATE sys_menu SET route_path = '/carrier'             WHERE menu_code = 'carrier';
UPDATE sys_menu SET route_path = '/warehouse'           WHERE menu_code = 'warehouse';
UPDATE sys_menu SET route_path = '/purchase-order'      WHERE menu_code = 'purchase-order';
UPDATE sys_menu SET route_path = '/purchase-inbound'    WHERE menu_code = 'purchase-inbound';
UPDATE sys_menu SET route_path = '/sales-order'         WHERE menu_code = 'sales-order';
UPDATE sys_menu SET route_path = '/sales-outbound'      WHERE menu_code = 'sales-outbound';
UPDATE sys_menu SET route_path = '/freight-bill'        WHERE menu_code = 'freight-bill';
UPDATE sys_menu SET route_path = '/purchase-contract'   WHERE menu_code = 'purchase-contract';
UPDATE sys_menu SET route_path = '/sales-contract'      WHERE menu_code = 'sales-contract';
UPDATE sys_menu SET route_path = '/supplier-statement'  WHERE menu_code = 'supplier-statement';
UPDATE sys_menu SET route_path = '/customer-statement'  WHERE menu_code = 'customer-statement';
UPDATE sys_menu SET route_path = '/freight-statement'   WHERE menu_code = 'freight-statement';
UPDATE sys_menu SET route_path = '/receipt'             WHERE menu_code = 'receipt';
UPDATE sys_menu SET route_path = '/payment'             WHERE menu_code = 'payment';
UPDATE sys_menu SET route_path = '/invoice-receipt'     WHERE menu_code = 'invoice-receipt';
UPDATE sys_menu SET route_path = '/invoice-issue'       WHERE menu_code = 'invoice-issue';
UPDATE sys_menu SET route_path = '/receivable-payable'  WHERE menu_code = 'receivable-payable';
UPDATE sys_menu SET route_path = '/general-setting'     WHERE menu_code = 'general-setting';
UPDATE sys_menu SET route_path = '/company-setting'     WHERE menu_code = 'company-setting';
UPDATE sys_menu SET route_path = '/operation-log'       WHERE menu_code = 'operation-log';
UPDATE sys_menu SET route_path = '/department'          WHERE menu_code = 'department';
UPDATE sys_menu SET route_path = '/database'            WHERE menu_code = 'database';
UPDATE sys_menu SET route_path = '/session'             WHERE menu_code = 'session';
UPDATE sys_menu SET route_path = '/api-key'             WHERE menu_code = 'api-key';
UPDATE sys_menu SET route_path = '/security-key'        WHERE menu_code = 'security-key';
UPDATE sys_menu SET route_path = '/print-template'      WHERE menu_code = 'print-template';
