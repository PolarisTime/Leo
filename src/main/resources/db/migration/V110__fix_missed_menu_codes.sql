-- V107 遗漏的 menu_code 重命名
UPDATE sys_menu SET menu_code = 'purchase-contract' WHERE menu_code = 'purchase-contracts';
UPDATE sys_menu SET menu_code = 'sales-contract'    WHERE menu_code = 'sales-contracts';
UPDATE sys_menu SET route_path = '/purchase-contract' WHERE route_path = '/purchase-contracts';
UPDATE sys_menu SET route_path = '/sales-contract'    WHERE route_path = '/sales-contracts';

UPDATE sys_menu_action SET menu_code = 'purchase-contract' WHERE menu_code = 'purchase-contracts';
UPDATE sys_menu_action SET menu_code = 'sales-contract'    WHERE menu_code = 'sales-contracts';
