-- V128: Column length reductions (with safety checks)
-- All reductions only proceed if no existing data exceeds new limit

-- M3: Unify vehicle_plate to 16 (Chinese license plate max 8 chars, new energy 8 chars)
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM lg_freight_bill WHERE length(vehicle_plate) > 16) THEN
        ALTER TABLE lg_freight_bill ALTER COLUMN vehicle_plate TYPE VARCHAR(16);
    END IF;
END $$;

-- L2: phone/mobile 32→20 (China mobile 11 digits, international max ~20)
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM sys_user WHERE length(mobile) > 20) THEN
    ALTER TABLE sys_user ALTER COLUMN mobile TYPE VARCHAR(20);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM md_customer WHERE length(contact_phone) > 20) THEN
    ALTER TABLE md_customer ALTER COLUMN contact_phone TYPE VARCHAR(20);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM md_supplier WHERE length(contact_phone) > 20) THEN
    ALTER TABLE md_supplier ALTER COLUMN contact_phone TYPE VARCHAR(20);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM md_warehouse WHERE length(contact_phone) > 20) THEN
    ALTER TABLE md_warehouse ALTER COLUMN contact_phone TYPE VARCHAR(20);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM md_carrier WHERE length(contact_phone) > 20) THEN
    ALTER TABLE md_carrier ALTER COLUMN contact_phone TYPE VARCHAR(20);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM md_carrier WHERE length(vehicle_phone) > 20) THEN
    ALTER TABLE md_carrier ALTER COLUMN vehicle_phone TYPE VARCHAR(20);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM md_carrier WHERE length(vehicle_phone2) > 20) THEN
    ALTER TABLE md_carrier ALTER COLUMN vehicle_phone2 TYPE VARCHAR(20);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM md_carrier WHERE length(vehicle_phone3) > 20) THEN
    ALTER TABLE md_carrier ALTER COLUMN vehicle_phone3 TYPE VARCHAR(20);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM sys_department WHERE length(contact_phone) > 20) THEN
    ALTER TABLE sys_department ALTER COLUMN contact_phone TYPE VARCHAR(20);
END IF; END $$;

-- L3: category/material 32→16 (steel categories: 管材/型材/板材, materials: 热轧/冷轧)
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM md_material WHERE length(category) > 16 OR length(material) > 16) THEN
    ALTER TABLE md_material ALTER COLUMN category TYPE VARCHAR(16);
    ALTER TABLE md_material ALTER COLUMN material TYPE VARCHAR(16);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM md_material_category WHERE length(category_code) > 16) THEN
    ALTER TABLE md_material_category ALTER COLUMN category_code TYPE VARCHAR(16);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM po_purchase_order_item WHERE length(category) > 16 OR length(material) > 16) THEN
    ALTER TABLE po_purchase_order_item ALTER COLUMN category TYPE VARCHAR(16);
    ALTER TABLE po_purchase_order_item ALTER COLUMN material TYPE VARCHAR(16);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM so_sales_order_item WHERE length(category) > 16 OR length(material) > 16) THEN
    ALTER TABLE so_sales_order_item ALTER COLUMN category TYPE VARCHAR(16);
    ALTER TABLE so_sales_order_item ALTER COLUMN material TYPE VARCHAR(16);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM po_purchase_inbound_item WHERE length(category) > 16 OR length(material) > 16) THEN
    ALTER TABLE po_purchase_inbound_item ALTER COLUMN category TYPE VARCHAR(16);
    ALTER TABLE po_purchase_inbound_item ALTER COLUMN material TYPE VARCHAR(16);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM so_sales_outbound_item WHERE length(category) > 16 OR length(material) > 16) THEN
    ALTER TABLE so_sales_outbound_item ALTER COLUMN category TYPE VARCHAR(16);
    ALTER TABLE so_sales_outbound_item ALTER COLUMN material TYPE VARCHAR(16);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM ct_sales_contract_item WHERE length(category) > 16 OR length(material) > 16) THEN
    ALTER TABLE ct_sales_contract_item ALTER COLUMN category TYPE VARCHAR(16);
    ALTER TABLE ct_sales_contract_item ALTER COLUMN material TYPE VARCHAR(16);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM ct_purchase_contract_item WHERE length(category) > 16 OR length(material) > 16) THEN
    ALTER TABLE ct_purchase_contract_item ALTER COLUMN category TYPE VARCHAR(16);
    ALTER TABLE ct_purchase_contract_item ALTER COLUMN material TYPE VARCHAR(16);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM fm_invoice_issue_item WHERE length(category) > 16 OR length(material) > 16) THEN
    ALTER TABLE fm_invoice_issue_item ALTER COLUMN category TYPE VARCHAR(16);
    ALTER TABLE fm_invoice_issue_item ALTER COLUMN material TYPE VARCHAR(16);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM fm_invoice_receipt_item WHERE length(category) > 16 OR length(material) > 16) THEN
    ALTER TABLE fm_invoice_receipt_item ALTER COLUMN category TYPE VARCHAR(16);
    ALTER TABLE fm_invoice_receipt_item ALTER COLUMN material TYPE VARCHAR(16);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM lg_freight_bill_item WHERE length(category) > 16 OR length(material) > 16) THEN
    ALTER TABLE lg_freight_bill_item ALTER COLUMN category TYPE VARCHAR(16);
    ALTER TABLE lg_freight_bill_item ALTER COLUMN material TYPE VARCHAR(16);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM st_customer_statement_item WHERE length(category) > 16 OR length(material) > 16) THEN
    ALTER TABLE st_customer_statement_item ALTER COLUMN category TYPE VARCHAR(16);
    ALTER TABLE st_customer_statement_item ALTER COLUMN material TYPE VARCHAR(16);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM st_supplier_statement_item WHERE length(category) > 16 OR length(material) > 16) THEN
    ALTER TABLE st_supplier_statement_item ALTER COLUMN category TYPE VARCHAR(16);
    ALTER TABLE st_supplier_statement_item ALTER COLUMN material TYPE VARCHAR(16);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM st_freight_statement_item WHERE length(category) > 16 OR length(material) > 16) THEN
    ALTER TABLE st_freight_statement_item ALTER COLUMN category TYPE VARCHAR(16);
    ALTER TABLE st_freight_statement_item ALTER COLUMN material TYPE VARCHAR(16);
END IF; END $$;

-- L4: unit/quantityUnit 16→8 (吨/件/根/平方米/米 = 1-3 chars)
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM md_material WHERE length(unit) > 8 OR length(quantity_unit) > 8) THEN
    ALTER TABLE md_material ALTER COLUMN unit TYPE VARCHAR(8);
    ALTER TABLE md_material ALTER COLUMN quantity_unit TYPE VARCHAR(8);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM po_purchase_order_item WHERE length(unit) > 8 OR length(quantity_unit) > 8) THEN
    ALTER TABLE po_purchase_order_item ALTER COLUMN unit TYPE VARCHAR(8);
    ALTER TABLE po_purchase_order_item ALTER COLUMN quantity_unit TYPE VARCHAR(8);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM so_sales_order_item WHERE length(unit) > 8 OR length(quantity_unit) > 8) THEN
    ALTER TABLE so_sales_order_item ALTER COLUMN unit TYPE VARCHAR(8);
    ALTER TABLE so_sales_order_item ALTER COLUMN quantity_unit TYPE VARCHAR(8);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM po_purchase_inbound_item WHERE length(unit) > 8 OR length(quantity_unit) > 8) THEN
    ALTER TABLE po_purchase_inbound_item ALTER COLUMN unit TYPE VARCHAR(8);
    ALTER TABLE po_purchase_inbound_item ALTER COLUMN quantity_unit TYPE VARCHAR(8);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM so_sales_outbound_item WHERE length(unit) > 8 OR length(quantity_unit) > 8) THEN
    ALTER TABLE so_sales_outbound_item ALTER COLUMN unit TYPE VARCHAR(8);
    ALTER TABLE so_sales_outbound_item ALTER COLUMN quantity_unit TYPE VARCHAR(8);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM ct_sales_contract_item WHERE length(unit) > 8 OR length(quantity_unit) > 8) THEN
    ALTER TABLE ct_sales_contract_item ALTER COLUMN unit TYPE VARCHAR(8);
    ALTER TABLE ct_sales_contract_item ALTER COLUMN quantity_unit TYPE VARCHAR(8);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM ct_purchase_contract_item WHERE length(unit) > 8 OR length(quantity_unit) > 8) THEN
    ALTER TABLE ct_purchase_contract_item ALTER COLUMN unit TYPE VARCHAR(8);
    ALTER TABLE ct_purchase_contract_item ALTER COLUMN quantity_unit TYPE VARCHAR(8);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM fm_invoice_issue_item WHERE length(unit) > 8 OR length(quantity_unit) > 8) THEN
    ALTER TABLE fm_invoice_issue_item ALTER COLUMN unit TYPE VARCHAR(8);
    ALTER TABLE fm_invoice_issue_item ALTER COLUMN quantity_unit TYPE VARCHAR(8);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM fm_invoice_receipt_item WHERE length(unit) > 8 OR length(quantity_unit) > 8) THEN
    ALTER TABLE fm_invoice_receipt_item ALTER COLUMN unit TYPE VARCHAR(8);
    ALTER TABLE fm_invoice_receipt_item ALTER COLUMN quantity_unit TYPE VARCHAR(8);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM lg_freight_bill_item WHERE length(quantity_unit) > 8) THEN
    ALTER TABLE lg_freight_bill_item ALTER COLUMN quantity_unit TYPE VARCHAR(8);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM st_customer_statement_item WHERE length(unit) > 8 OR length(quantity_unit) > 8) THEN
    ALTER TABLE st_customer_statement_item ALTER COLUMN unit TYPE VARCHAR(8);
    ALTER TABLE st_customer_statement_item ALTER COLUMN quantity_unit TYPE VARCHAR(8);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM st_supplier_statement_item WHERE length(unit) > 8 OR length(quantity_unit) > 8) THEN
    ALTER TABLE st_supplier_statement_item ALTER COLUMN unit TYPE VARCHAR(8);
    ALTER TABLE st_supplier_statement_item ALTER COLUMN quantity_unit TYPE VARCHAR(8);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM st_freight_statement_item WHERE length(quantity_unit) > 8) THEN
    ALTER TABLE st_freight_statement_item ALTER COLUMN quantity_unit TYPE VARCHAR(8);
END IF; END $$;

-- L5: person name fields 64→32 (Chinese names typically 2-4 chars)
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM so_sales_order WHERE length(sales_name) > 32) THEN
    ALTER TABLE so_sales_order ALTER COLUMN sales_name TYPE VARCHAR(32);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM ct_sales_contract WHERE length(sales_name) > 32) THEN
    ALTER TABLE ct_sales_contract ALTER COLUMN sales_name TYPE VARCHAR(32);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM ct_purchase_contract WHERE length(buyer_name) > 32) THEN
    ALTER TABLE ct_purchase_contract ALTER COLUMN buyer_name TYPE VARCHAR(32);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM po_purchase_order WHERE length(buyer_name) > 32) THEN
    ALTER TABLE po_purchase_order ALTER COLUMN buyer_name TYPE VARCHAR(32);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM md_customer WHERE length(contact_name) > 32) THEN
    ALTER TABLE md_customer ALTER COLUMN contact_name TYPE VARCHAR(32);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM md_supplier WHERE length(contact_name) > 32) THEN
    ALTER TABLE md_supplier ALTER COLUMN contact_name TYPE VARCHAR(32);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM md_warehouse WHERE length(contact_name) > 32) THEN
    ALTER TABLE md_warehouse ALTER COLUMN contact_name TYPE VARCHAR(32);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM md_carrier WHERE length(contact_name) > 32) THEN
    ALTER TABLE md_carrier ALTER COLUMN contact_name TYPE VARCHAR(32);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM md_carrier WHERE length(vehicle_contact) > 32) THEN
    ALTER TABLE md_carrier ALTER COLUMN vehicle_contact TYPE VARCHAR(32);
    ALTER TABLE md_carrier ALTER COLUMN vehicle_contact2 TYPE VARCHAR(32);
    ALTER TABLE md_carrier ALTER COLUMN vehicle_contact3 TYPE VARCHAR(32);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM md_project WHERE length(project_manager) > 32) THEN
    ALTER TABLE md_project ALTER COLUMN project_manager TYPE VARCHAR(32);
END IF; END $$;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM sys_department WHERE length(manager_name) > 32) THEN
    ALTER TABLE sys_department ALTER COLUMN manager_name TYPE VARCHAR(32);
END IF; END $$;

-- Operator names across finance/logistics tables
DO $$ BEGIN IF NOT EXISTS (
    SELECT 1 FROM fm_payment WHERE length(operator_name) > 32
    UNION ALL SELECT 1 FROM fm_receipt WHERE length(operator_name) > 32
    UNION ALL SELECT 1 FROM fm_invoice_issue WHERE length(operator_name) > 32
    UNION ALL SELECT 1 FROM fm_invoice_receipt WHERE length(operator_name) > 32
) THEN
    ALTER TABLE fm_payment ALTER COLUMN operator_name TYPE VARCHAR(32);
    ALTER TABLE fm_receipt ALTER COLUMN operator_name TYPE VARCHAR(32);
    ALTER TABLE fm_invoice_issue ALTER COLUMN operator_name TYPE VARCHAR(32);
    ALTER TABLE fm_invoice_receipt ALTER COLUMN operator_name TYPE VARCHAR(32);
END IF; END $$;
