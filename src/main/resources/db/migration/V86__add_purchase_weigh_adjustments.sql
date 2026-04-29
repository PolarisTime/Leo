ALTER TABLE md_material_category
    ADD COLUMN IF NOT EXISTS purchase_weigh_required BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE md_material_category
SET purchase_weigh_required = TRUE
WHERE deleted_flag = FALSE
  AND (category_code IN ('WIRE_ROD', 'WIRE') OR category_name IN ('盘螺', '线材'));

ALTER TABLE po_purchase_inbound_item
    ADD COLUMN IF NOT EXISTS weigh_weight_ton NUMERIC(14, 3);

ALTER TABLE po_purchase_inbound_item
    ADD COLUMN IF NOT EXISTS weight_adjustment_ton NUMERIC(14, 3) NOT NULL DEFAULT 0;

ALTER TABLE po_purchase_inbound_item
    ADD COLUMN IF NOT EXISTS weight_adjustment_amount NUMERIC(14, 2) NOT NULL DEFAULT 0;

ALTER TABLE st_supplier_statement_item
    ADD COLUMN IF NOT EXISTS weigh_weight_ton NUMERIC(14, 3);

ALTER TABLE st_supplier_statement_item
    ADD COLUMN IF NOT EXISTS weight_adjustment_ton NUMERIC(14, 3) NOT NULL DEFAULT 0;

ALTER TABLE st_supplier_statement_item
    ADD COLUMN IF NOT EXISTS weight_adjustment_amount NUMERIC(14, 2) NOT NULL DEFAULT 0;

UPDATE po_purchase_inbound_item
SET weight_adjustment_ton = COALESCE(weight_adjustment_ton, 0),
    weight_adjustment_amount = COALESCE(weight_adjustment_amount, 0);

UPDATE st_supplier_statement_item
SET weight_adjustment_ton = COALESCE(weight_adjustment_ton, 0),
    weight_adjustment_amount = COALESCE(weight_adjustment_amount, 0);
