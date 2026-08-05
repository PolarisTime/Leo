-- 统一各业务表冗余的结算主体名称快照（反规范化快照漂移修复）
--
-- 背景：业务表（物流单、销售单、对账单等）冗余存储 settlement_company_name，
-- 主数据 sys_company_setting 改名后未级联同步，导致同一结算主体出现新旧名称混杂
-- （如"颖捷建材"与"嘉兴颖捷建材有限公司"）。CompanySettingService 改名级联同步
-- 自本版本生效，本迁移修复历史存量数据：将各表名称统一为 sys_company_setting 当前名称。
-- 幂等：仅更新名称不一致的行，重复执行无副作用。

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
