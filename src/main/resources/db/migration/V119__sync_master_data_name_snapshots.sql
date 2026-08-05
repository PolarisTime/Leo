-- 统一各业务表冗余的主数据名称快照（反规范化快照漂移修复）
--
-- 背景：业务表冗余存储 customer_name/supplier_name/project_name/warehouse_name/
-- carrier_name/material_name 等主数据名称快照，主数据改名后未级联同步，导致同一主体
-- 新旧名称混杂。ReferenceSnapshotSyncService 改名级联自本版本生效，本迁移修复历史存量，
-- 将各表名称统一为 md_* 主数据当前名称。
-- 注意：业务表 material_name 实为 md_material.brand 快照。
-- 幂等：仅更新名称不一致的行，重复执行无副作用。

-- 客户
UPDATE so_sales_order t SET customer_name = c.customer_name FROM md_customer c
WHERE t.customer_id = c.id AND t.customer_name IS DISTINCT FROM c.customer_name;
UPDATE so_sales_outbound t SET customer_name = c.customer_name FROM md_customer c
WHERE t.customer_id = c.id AND t.customer_name IS DISTINCT FROM c.customer_name;
UPDATE lg_freight_bill_item t SET customer_name = c.customer_name FROM md_customer c
WHERE t.customer_id = c.id AND t.customer_name IS DISTINCT FROM c.customer_name;
UPDATE st_customer_statement t SET customer_name = c.customer_name FROM md_customer c
WHERE t.customer_id = c.id AND t.customer_name IS DISTINCT FROM c.customer_name;
UPDATE st_freight_statement_item t SET customer_name = c.customer_name FROM md_customer c
WHERE t.customer_id = c.id AND t.customer_name IS DISTINCT FROM c.customer_name;
UPDATE fm_receipt t SET customer_name = c.customer_name FROM md_customer c
WHERE t.customer_id = c.id AND t.customer_name IS DISTINCT FROM c.customer_name;
UPDATE fm_invoice_issue t SET customer_name = c.customer_name FROM md_customer c
WHERE t.customer_id = c.id AND t.customer_name IS DISTINCT FROM c.customer_name;
UPDATE ct_sales_contract t SET customer_name = c.customer_name FROM md_customer c
WHERE t.customer_id = c.id AND t.customer_name IS DISTINCT FROM c.customer_name;

-- 供应商
UPDATE po_purchase_order t SET supplier_name = s.supplier_name FROM md_supplier s
WHERE t.supplier_id = s.id AND t.supplier_name IS DISTINCT FROM s.supplier_name;
UPDATE po_purchase_inbound t SET supplier_name = s.supplier_name FROM md_supplier s
WHERE t.supplier_id = s.id AND t.supplier_name IS DISTINCT FROM s.supplier_name;
UPDATE po_purchase_refund t SET supplier_name = s.supplier_name FROM md_supplier s
WHERE t.supplier_id = s.id AND t.supplier_name IS DISTINCT FROM s.supplier_name;
UPDATE ct_purchase_contract t SET supplier_name = s.supplier_name FROM md_supplier s
WHERE t.supplier_id = s.id AND t.supplier_name IS DISTINCT FROM s.supplier_name;
UPDATE fm_invoice_receipt t SET supplier_name = s.supplier_name FROM md_supplier s
WHERE t.supplier_id = s.id AND t.supplier_name IS DISTINCT FROM s.supplier_name;
UPDATE fm_supplier_refund_receipt t SET supplier_name = s.supplier_name FROM md_supplier s
WHERE t.supplier_id = s.id AND t.supplier_name IS DISTINCT FROM s.supplier_name;
UPDATE st_supplier_statement t SET supplier_name = s.supplier_name FROM md_supplier s
WHERE t.supplier_id = s.id AND t.supplier_name IS DISTINCT FROM s.supplier_name;

-- 项目
UPDATE so_sales_order t SET project_name = p.project_name FROM md_project p
WHERE t.project_id = p.id AND t.project_name IS DISTINCT FROM p.project_name;
UPDATE so_sales_outbound t SET project_name = p.project_name FROM md_project p
WHERE t.project_id = p.id AND t.project_name IS DISTINCT FROM p.project_name;
UPDATE lg_freight_bill_item t SET project_name = p.project_name FROM md_project p
WHERE t.project_id = p.id AND t.project_name IS DISTINCT FROM p.project_name;
UPDATE st_customer_statement t SET project_name = p.project_name FROM md_project p
WHERE t.project_id = p.id AND t.project_name IS DISTINCT FROM p.project_name;
UPDATE st_freight_statement_item t SET project_name = p.project_name FROM md_project p
WHERE t.project_id = p.id AND t.project_name IS DISTINCT FROM p.project_name;
UPDATE fm_receipt t SET project_name = p.project_name FROM md_project p
WHERE t.project_id = p.id AND t.project_name IS DISTINCT FROM p.project_name;
UPDATE fm_invoice_issue t SET project_name = p.project_name FROM md_project p
WHERE t.project_id = p.id AND t.project_name IS DISTINCT FROM p.project_name;
UPDATE fm_ledger_adjustment t SET project_name = p.project_name FROM md_project p
WHERE t.project_id = p.id AND t.project_name IS DISTINCT FROM p.project_name;
UPDATE ct_sales_contract t SET project_name = p.project_name FROM md_project p
WHERE t.project_id = p.id AND t.project_name IS DISTINCT FROM p.project_name;

-- 仓库
UPDATE so_sales_order_item t SET warehouse_name = w.warehouse_name FROM md_warehouse w
WHERE t.warehouse_id = w.id AND t.warehouse_name IS DISTINCT FROM w.warehouse_name;
UPDATE so_sales_outbound t SET warehouse_name = w.warehouse_name FROM md_warehouse w
WHERE t.warehouse_id = w.id AND t.warehouse_name IS DISTINCT FROM w.warehouse_name;
UPDATE so_sales_outbound_item t SET warehouse_name = w.warehouse_name FROM md_warehouse w
WHERE t.warehouse_id = w.id AND t.warehouse_name IS DISTINCT FROM w.warehouse_name;
UPDATE po_purchase_order_item t SET warehouse_name = w.warehouse_name FROM md_warehouse w
WHERE t.warehouse_id = w.id AND t.warehouse_name IS DISTINCT FROM w.warehouse_name;
UPDATE po_purchase_inbound t SET warehouse_name = w.warehouse_name FROM md_warehouse w
WHERE t.warehouse_id = w.id AND t.warehouse_name IS DISTINCT FROM w.warehouse_name;
UPDATE po_purchase_inbound_item t SET warehouse_name = w.warehouse_name FROM md_warehouse w
WHERE t.warehouse_id = w.id AND t.warehouse_name IS DISTINCT FROM w.warehouse_name;
UPDATE po_purchase_refund_item t SET warehouse_name = w.warehouse_name FROM md_warehouse w
WHERE t.warehouse_id = w.id AND t.warehouse_name IS DISTINCT FROM w.warehouse_name;
UPDATE lg_freight_bill_item t SET warehouse_name = w.warehouse_name FROM md_warehouse w
WHERE t.warehouse_id = w.id AND t.warehouse_name IS DISTINCT FROM w.warehouse_name;
UPDATE st_freight_statement_item t SET warehouse_name = w.warehouse_name FROM md_warehouse w
WHERE t.warehouse_id = w.id AND t.warehouse_name IS DISTINCT FROM w.warehouse_name;
UPDATE fm_invoice_issue_item t SET warehouse_name = w.warehouse_name FROM md_warehouse w
WHERE t.warehouse_id = w.id AND t.warehouse_name IS DISTINCT FROM w.warehouse_name;
UPDATE fm_invoice_receipt_item t SET warehouse_name = w.warehouse_name FROM md_warehouse w
WHERE t.warehouse_id = w.id AND t.warehouse_name IS DISTINCT FROM w.warehouse_name;

-- 承运商
UPDATE lg_freight_bill t SET carrier_name = c.carrier_name FROM md_carrier c
WHERE t.carrier_id = c.id AND t.carrier_name IS DISTINCT FROM c.carrier_name;
UPDATE st_freight_statement t SET carrier_name = c.carrier_name FROM md_carrier c
WHERE t.carrier_id = c.id AND t.carrier_name IS DISTINCT FROM c.carrier_name;

-- 材料（业务表 material_name 实为 md_material.brand 快照）
UPDATE lg_freight_bill_item t SET material_name = m.brand FROM md_material m
WHERE t.material_id = m.id AND t.material_name IS DISTINCT FROM m.brand;
UPDATE st_freight_statement_item t SET material_name = m.brand FROM md_material m
WHERE t.material_id = m.id AND t.material_name IS DISTINCT FROM m.brand;
