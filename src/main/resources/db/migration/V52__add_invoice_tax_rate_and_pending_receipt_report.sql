ALTER TABLE sys_company_setting
    ADD COLUMN IF NOT EXISTS tax_rate NUMERIC(6, 4) NOT NULL DEFAULT 0.1300;

UPDATE sys_company_setting
SET tax_rate = 0.1300
WHERE tax_rate IS NULL;

ALTER TABLE fm_invoice_receipt
    ADD COLUMN IF NOT EXISTS invoice_title VARCHAR(128);

UPDATE fm_invoice_receipt
SET invoice_title = supplier_name
WHERE invoice_title IS NULL OR invoice_title = '';

CREATE INDEX IF NOT EXISTS idx_fm_invoice_receipt_item_source_purchase_order_item_id
    ON fm_invoice_receipt_item (source_purchase_order_item_id);

INSERT INTO sys_menu (id, menu_code, menu_name, parent_code, route_path, icon, sort_order, menu_type)
VALUES
    (9006, 'pending-invoice-receipt-report', '未收票报表', 'finance', '/pending-invoice-receipt-report', 'FileSearchOutlined', 6, '菜单')
ON CONFLICT (menu_code) DO NOTHING;

INSERT INTO sys_menu_action (id, menu_code, action_code, action_name)
VALUES
    (90061, 'pending-invoice-receipt-report', 'VIEW', '查看'),
    (90062, 'pending-invoice-receipt-report', 'EXPORT', '导出')
ON CONFLICT (menu_code, action_code) DO NOTHING;

INSERT INTO sys_role_action (id, role_id, menu_code, action_code)
SELECT
    (SELECT COALESCE(MAX(id), 0) FROM sys_role_action) + ROW_NUMBER() OVER (),
    r.id,
    ma.menu_code,
    ma.action_code
FROM sys_role r
JOIN sys_menu_action ma
  ON ma.menu_code = 'pending-invoice-receipt-report'
WHERE r.role_code IN ('ADMIN', 'FINANCE_MANAGER', 'PURCHASER')
ON CONFLICT (role_id, menu_code, action_code) DO NOTHING;
