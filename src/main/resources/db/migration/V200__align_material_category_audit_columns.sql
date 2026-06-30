-- Align material category audit metadata with AbstractAuditableEntity.

UPDATE md_material_category
SET created_by = 0
WHERE created_by IS NULL;

ALTER TABLE md_material_category
    ALTER COLUMN created_by SET DEFAULT 0,
    ALTER COLUMN created_by SET NOT NULL;
