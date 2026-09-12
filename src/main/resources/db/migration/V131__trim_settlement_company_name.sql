-- 结算主体名称快照归一化：trim 系统结算主体名称并重跑快照同步
--
-- 背景：V130 已清洗主数据文本字段，但系统结算主体名称（sys_company_setting.company_name）
-- 及其在业务表中的冗余快照 settlement_company_name（见 V118）未纳入，本迁移单独处理。
-- 幂等：仅更新有空白差异或名称不一致的行，重复执行无副作用。

-- 一、结算主体名称 trim
UPDATE sys_company_setting SET company_name = btrim(company_name)
WHERE coalesce(company_name, '') IS DISTINCT FROM btrim(coalesce(company_name, ''));

-- 二、重跑名称快照同步（与 V118 相同逻辑，将业务表统一到 trim 后的名称）

UPDATE lg_freight_bill t
SET settlement_company_name = cs.company_name
FROM sys_company_setting cs
WHERE t.settlement_company_id = cs.id
  AND t.settlement_company_name IS DISTINCT FROM cs.company_name;
UPDATE lg_freight_bill_item t
SET settlement_company_name = cs.company_name
FROM sys_company_setting cs
WHERE t.settlement_company_id = cs.id
  AND t.settlement_company_name IS DISTINCT FROM cs.company_name;
UPDATE so_sales_order t
SET settlement_company_name = cs.company_name
FROM sys_company_setting cs
WHERE t.settlement_company_id = cs.id
  AND t.settlement_company_name IS DISTINCT FROM cs.company_name;
UPDATE so_sales_order_item t
SET settlement_company_name = cs.company_name
FROM sys_company_setting cs
WHERE t.settlement_company_id = cs.id
  AND t.settlement_company_name IS DISTINCT FROM cs.company_name;
UPDATE so_sales_outbound t
SET settlement_company_name = cs.company_name
FROM sys_company_setting cs
WHERE t.settlement_company_id = cs.id
  AND t.settlement_company_name IS DISTINCT FROM cs.company_name;
UPDATE so_sales_outbound_item t
SET settlement_company_name = cs.company_name
FROM sys_company_setting cs
WHERE t.settlement_company_id = cs.id
  AND t.settlement_company_name IS DISTINCT FROM cs.company_name;
UPDATE po_purchase_order t
SET settlement_company_name = cs.company_name
FROM sys_company_setting cs
WHERE t.settlement_company_id = cs.id
  AND t.settlement_company_name IS DISTINCT FROM cs.company_name;
UPDATE po_purchase_inbound t
SET settlement_company_name = cs.company_name
FROM sys_company_setting cs
WHERE t.settlement_company_id = cs.id
  AND t.settlement_company_name IS DISTINCT FROM cs.company_name;
UPDATE po_purchase_inbound_item t
SET settlement_company_name = cs.company_name
FROM sys_company_setting cs
WHERE t.settlement_company_id = cs.id
  AND t.settlement_company_name IS DISTINCT FROM cs.company_name;
UPDATE po_purchase_refund t
SET settlement_company_name = cs.company_name
FROM sys_company_setting cs
WHERE t.settlement_company_id = cs.id
  AND t.settlement_company_name IS DISTINCT FROM cs.company_name;
UPDATE st_customer_statement t
SET settlement_company_name = cs.company_name
FROM sys_company_setting cs
WHERE t.settlement_company_id = cs.id
  AND t.settlement_company_name IS DISTINCT FROM cs.company_name;
UPDATE st_freight_statement t
SET settlement_company_name = cs.company_name
FROM sys_company_setting cs
WHERE t.settlement_company_id = cs.id
  AND t.settlement_company_name IS DISTINCT FROM cs.company_name;
UPDATE st_freight_statement_item t
SET settlement_company_name = cs.company_name
FROM sys_company_setting cs
WHERE t.settlement_company_id = cs.id
  AND t.settlement_company_name IS DISTINCT FROM cs.company_name;
UPDATE st_supplier_statement t
SET settlement_company_name = cs.company_name
FROM sys_company_setting cs
WHERE t.settlement_company_id = cs.id
  AND t.settlement_company_name IS DISTINCT FROM cs.company_name;
UPDATE fm_receipt t
SET settlement_company_name = cs.company_name
FROM sys_company_setting cs
WHERE t.settlement_company_id = cs.id
  AND t.settlement_company_name IS DISTINCT FROM cs.company_name;
UPDATE fm_payment t
SET settlement_company_name = cs.company_name
FROM sys_company_setting cs
WHERE t.settlement_company_id = cs.id
  AND t.settlement_company_name IS DISTINCT FROM cs.company_name;
UPDATE fm_invoice_issue t
SET settlement_company_name = cs.company_name
FROM sys_company_setting cs
WHERE t.settlement_company_id = cs.id
  AND t.settlement_company_name IS DISTINCT FROM cs.company_name;
UPDATE fm_invoice_receipt t
SET settlement_company_name = cs.company_name
FROM sys_company_setting cs
WHERE t.settlement_company_id = cs.id
  AND t.settlement_company_name IS DISTINCT FROM cs.company_name;
UPDATE fm_cash_reversal t
SET settlement_company_name = cs.company_name
FROM sys_company_setting cs
WHERE t.settlement_company_id = cs.id
  AND t.settlement_company_name IS DISTINCT FROM cs.company_name;
UPDATE fm_ledger_adjustment t
SET settlement_company_name = cs.company_name
FROM sys_company_setting cs
WHERE t.settlement_company_id = cs.id
  AND t.settlement_company_name IS DISTINCT FROM cs.company_name;
UPDATE fm_supplier_refund_receipt t
SET settlement_company_name = cs.company_name
FROM sys_company_setting cs
WHERE t.settlement_company_id = cs.id
  AND t.settlement_company_name IS DISTINCT FROM cs.company_name;
UPDATE sys_print_template t
SET settlement_company_name = cs.company_name
FROM sys_company_setting cs
WHERE t.settlement_company_id = cs.id
  AND t.settlement_company_name IS DISTINCT FROM cs.company_name;
