UPDATE po_purchase_order
SET status = '已删除',
    updated_at = CURRENT_TIMESTAMP,
    updated_name = 'flyway'
WHERE deleted_flag = TRUE
  AND status <> '已删除';

UPDATE po_purchase_inbound
SET status = '已删除',
    updated_at = CURRENT_TIMESTAMP,
    updated_name = 'flyway'
WHERE deleted_flag = TRUE
  AND status <> '已删除';

UPDATE so_sales_order
SET status = '已删除',
    updated_at = CURRENT_TIMESTAMP,
    updated_name = 'flyway'
WHERE deleted_flag = TRUE
  AND status <> '已删除';

UPDATE so_sales_outbound
SET status = '已删除',
    updated_at = CURRENT_TIMESTAMP,
    updated_name = 'flyway'
WHERE deleted_flag = TRUE
  AND status <> '已删除';

UPDATE ct_purchase_contract
SET status = '已删除',
    updated_at = CURRENT_TIMESTAMP,
    updated_name = 'flyway'
WHERE deleted_flag = TRUE
  AND status <> '已删除';

UPDATE ct_sales_contract
SET status = '已删除',
    updated_at = CURRENT_TIMESTAMP,
    updated_name = 'flyway'
WHERE deleted_flag = TRUE
  AND status <> '已删除';

UPDATE lg_freight_bill
SET status = '已删除',
    updated_at = CURRENT_TIMESTAMP,
    updated_name = 'flyway'
WHERE deleted_flag = TRUE
  AND status <> '已删除';

UPDATE st_supplier_statement
SET status = '已删除',
    updated_at = CURRENT_TIMESTAMP,
    updated_name = 'flyway'
WHERE deleted_flag = TRUE
  AND status <> '已删除';

UPDATE st_customer_statement
SET status = '已删除',
    updated_at = CURRENT_TIMESTAMP,
    updated_name = 'flyway'
WHERE deleted_flag = TRUE
  AND status <> '已删除';

UPDATE st_freight_statement
SET status = '已删除',
    updated_at = CURRENT_TIMESTAMP,
    updated_name = 'flyway'
WHERE deleted_flag = TRUE
  AND status <> '已删除';

UPDATE fm_receipt
SET status = '已删除',
    updated_at = CURRENT_TIMESTAMP,
    updated_name = 'flyway'
WHERE deleted_flag = TRUE
  AND status <> '已删除';

UPDATE fm_payment
SET status = '已删除',
    updated_at = CURRENT_TIMESTAMP,
    updated_name = 'flyway'
WHERE deleted_flag = TRUE
  AND status <> '已删除';

UPDATE fm_invoice_receipt
SET status = '已删除',
    updated_at = CURRENT_TIMESTAMP,
    updated_name = 'flyway'
WHERE deleted_flag = TRUE
  AND status <> '已删除';

UPDATE fm_invoice_issue
SET status = '已删除',
    updated_at = CURRENT_TIMESTAMP,
    updated_name = 'flyway'
WHERE deleted_flag = TRUE
  AND status <> '已删除';
