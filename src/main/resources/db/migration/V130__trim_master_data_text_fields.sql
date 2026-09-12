-- 主数据文本字段归一化：清理历史存量中未 trim 的首尾空白
--
-- 背景：应用层保存主数据时已统一 trim，但修复前写入的存量数据可能仍带首尾空白，
-- 会导致搜索/去重/引用快照比对不一致。本迁移对主数据文本列执行 btrim 一次性清洗。
-- 注意：名称类主数据被业务表冗余为快照（见 V119），trim 主数据后需将快照重新同步
-- 到 trim 后的值，故本迁移包含与 V119 相同的名称快照同步逻辑。
-- 幂等：仅更新与 btrim 结果不一致的行，重复执行无副作用。

-- 一、主数据文本列 trim
UPDATE md_supplier SET
    supplier_name = btrim(supplier_name),
    contact_name = btrim(contact_name),
    contact_phone = btrim(contact_phone),
    city = btrim(city),
    remark = btrim(remark)
WHERE coalesce(supplier_name, '') IS DISTINCT FROM btrim(coalesce(supplier_name, ''))
   OR coalesce(contact_name, '') IS DISTINCT FROM btrim(coalesce(contact_name, ''))
   OR coalesce(contact_phone, '') IS DISTINCT FROM btrim(coalesce(contact_phone, ''))
   OR coalesce(city, '') IS DISTINCT FROM btrim(coalesce(city, ''))
   OR coalesce(remark, '') IS DISTINCT FROM btrim(coalesce(remark, ''));

UPDATE md_customer SET
    customer_name = btrim(customer_name),
    contact_name = btrim(contact_name),
    contact_phone = btrim(contact_phone),
    city = btrim(city),
    settlement_mode = btrim(settlement_mode),
    project_name = btrim(project_name),
    project_name_abbr = btrim(project_name_abbr),
    project_address = btrim(project_address),
    default_settlement_company_name = btrim(default_settlement_company_name),
    remark = btrim(remark)
WHERE coalesce(customer_name, '') IS DISTINCT FROM btrim(coalesce(customer_name, ''))
   OR coalesce(contact_name, '') IS DISTINCT FROM btrim(coalesce(contact_name, ''))
   OR coalesce(contact_phone, '') IS DISTINCT FROM btrim(coalesce(contact_phone, ''))
   OR coalesce(city, '') IS DISTINCT FROM btrim(coalesce(city, ''))
   OR coalesce(settlement_mode, '') IS DISTINCT FROM btrim(coalesce(settlement_mode, ''))
   OR coalesce(project_name, '') IS DISTINCT FROM btrim(coalesce(project_name, ''))
   OR coalesce(project_name_abbr, '') IS DISTINCT FROM btrim(coalesce(project_name_abbr, ''))
   OR coalesce(project_address, '') IS DISTINCT FROM btrim(coalesce(project_address, ''))
   OR coalesce(default_settlement_company_name, '') IS DISTINCT FROM btrim(coalesce(default_settlement_company_name, ''))
   OR coalesce(remark, '') IS DISTINCT FROM btrim(coalesce(remark, ''));

UPDATE md_warehouse SET
    warehouse_name = btrim(warehouse_name),
    warehouse_type = btrim(warehouse_type),
    contact_name = btrim(contact_name),
    contact_phone = btrim(contact_phone),
    address = btrim(address),
    remark = btrim(remark)
WHERE coalesce(warehouse_name, '') IS DISTINCT FROM btrim(coalesce(warehouse_name, ''))
   OR coalesce(warehouse_type, '') IS DISTINCT FROM btrim(coalesce(warehouse_type, ''))
   OR coalesce(contact_name, '') IS DISTINCT FROM btrim(coalesce(contact_name, ''))
   OR coalesce(contact_phone, '') IS DISTINCT FROM btrim(coalesce(contact_phone, ''))
   OR coalesce(address, '') IS DISTINCT FROM btrim(coalesce(address, ''))
   OR coalesce(remark, '') IS DISTINCT FROM btrim(coalesce(remark, ''));

UPDATE md_project SET
    project_name = btrim(project_name),
    project_name_abbr = btrim(project_name_abbr),
    project_address = btrim(project_address),
    project_manager = btrim(project_manager),
    customer_code = btrim(customer_code),
    settlement_company_name = btrim(settlement_company_name),
    remark = btrim(remark)
WHERE coalesce(project_name, '') IS DISTINCT FROM btrim(coalesce(project_name, ''))
   OR coalesce(project_name_abbr, '') IS DISTINCT FROM btrim(coalesce(project_name_abbr, ''))
   OR coalesce(project_address, '') IS DISTINCT FROM btrim(coalesce(project_address, ''))
   OR coalesce(project_manager, '') IS DISTINCT FROM btrim(coalesce(project_manager, ''))
   OR coalesce(customer_code, '') IS DISTINCT FROM btrim(coalesce(customer_code, ''))
   OR coalesce(settlement_company_name, '') IS DISTINCT FROM btrim(coalesce(settlement_company_name, ''))
   OR coalesce(remark, '') IS DISTINCT FROM btrim(coalesce(remark, ''));

UPDATE md_carrier SET
    carrier_name = btrim(carrier_name),
    contact_name = btrim(contact_name),
    contact_phone = btrim(contact_phone),
    vehicle_type = btrim(vehicle_type),
    price_mode = btrim(price_mode),
    default_settlement_company_name = btrim(default_settlement_company_name),
    remark = btrim(remark)
WHERE coalesce(carrier_name, '') IS DISTINCT FROM btrim(coalesce(carrier_name, ''))
   OR coalesce(contact_name, '') IS DISTINCT FROM btrim(coalesce(contact_name, ''))
   OR coalesce(contact_phone, '') IS DISTINCT FROM btrim(coalesce(contact_phone, ''))
   OR coalesce(vehicle_type, '') IS DISTINCT FROM btrim(coalesce(vehicle_type, ''))
   OR coalesce(price_mode, '') IS DISTINCT FROM btrim(coalesce(price_mode, ''))
   OR coalesce(default_settlement_company_name, '') IS DISTINCT FROM btrim(coalesce(default_settlement_company_name, ''))
   OR coalesce(remark, '') IS DISTINCT FROM btrim(coalesce(remark, ''));

UPDATE md_material SET
    brand = btrim(brand),
    material = btrim(material),
    category = btrim(category),
    spec = btrim(spec),
    length = btrim(length),
    unit = btrim(unit),
    quantity_unit = btrim(quantity_unit),
    remark = btrim(remark)
WHERE coalesce(brand, '') IS DISTINCT FROM btrim(coalesce(brand, ''))
   OR coalesce(material, '') IS DISTINCT FROM btrim(coalesce(material, ''))
   OR coalesce(category, '') IS DISTINCT FROM btrim(coalesce(category, ''))
   OR coalesce(spec, '') IS DISTINCT FROM btrim(coalesce(spec, ''))
   OR coalesce(length, '') IS DISTINCT FROM btrim(coalesce(length, ''))
   OR coalesce(unit, '') IS DISTINCT FROM btrim(coalesce(unit, ''))
   OR coalesce(quantity_unit, '') IS DISTINCT FROM btrim(coalesce(quantity_unit, ''))
   OR coalesce(remark, '') IS DISTINCT FROM btrim(coalesce(remark, ''));

-- 单据费用行文本列 trim
UPDATE bd_document_charge_item SET
    charge_name = btrim(charge_name),
    unit = btrim(unit),
    remark = btrim(remark)
WHERE coalesce(charge_name, '') IS DISTINCT FROM btrim(coalesce(charge_name, ''))
   OR coalesce(unit, '') IS DISTINCT FROM btrim(coalesce(unit, ''))
   OR coalesce(remark, '') IS DISTINCT FROM btrim(coalesce(remark, ''));

-- 二、名称快照同步（与 V119 相同逻辑，将业务表统一到 trim 后的名称）
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
