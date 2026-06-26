ALTER TABLE md_material_category
    ADD COLUMN IF NOT EXISTS purchase_weigh_over_tolerance_percent NUMERIC(5, 2) NOT NULL DEFAULT 5.00,
    ADD COLUMN IF NOT EXISTS purchase_weigh_under_tolerance_percent NUMERIC(5, 2) NOT NULL DEFAULT 5.00;

ALTER TABLE md_material_category
    DROP CONSTRAINT IF EXISTS chk_material_category_purchase_weigh_over_tolerance_percent;

ALTER TABLE md_material_category
    ADD CONSTRAINT chk_material_category_purchase_weigh_over_tolerance_percent
        CHECK (purchase_weigh_over_tolerance_percent >= 0 AND purchase_weigh_over_tolerance_percent <= 100);

ALTER TABLE md_material_category
    DROP CONSTRAINT IF EXISTS chk_material_category_purchase_weigh_under_tolerance_percent;

ALTER TABLE md_material_category
    ADD CONSTRAINT chk_material_category_purchase_weigh_under_tolerance_percent
        CHECK (purchase_weigh_under_tolerance_percent >= 0 AND purchase_weigh_under_tolerance_percent <= 100);
