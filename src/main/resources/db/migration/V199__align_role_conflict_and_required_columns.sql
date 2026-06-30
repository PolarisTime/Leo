-- Align schema details that are required by current JPA entity mappings.

ALTER TABLE sys_role_conflict
    ADD COLUMN IF NOT EXISTS created_name VARCHAR(64),
    ADD COLUMN IF NOT EXISTS updated_name VARCHAR(64);

UPDATE sys_role_conflict
SET created_by = COALESCE(created_by, 0),
    created_name = COALESCE(NULLIF(created_name, ''), 'system'),
    updated_name = COALESCE(NULLIF(updated_name, ''), 'system');

ALTER TABLE sys_role_conflict
    ALTER COLUMN created_by SET DEFAULT 0,
    ALTER COLUMN created_by SET NOT NULL,
    ALTER COLUMN created_name SET DEFAULT 'system',
    ALTER COLUMN created_name SET NOT NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM sys_upload_rule
        WHERE module_key IS NULL
    ) THEN
        RAISE EXCEPTION 'Cannot set sys_upload_rule.module_key NOT NULL: null values exist';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM sys_print_template
        WHERE template_type IS NULL
    ) THEN
        RAISE EXCEPTION 'Cannot set sys_print_template.template_type NOT NULL: null values exist';
    END IF;
END $$;

ALTER TABLE sys_upload_rule
    ALTER COLUMN module_key SET NOT NULL;

ALTER TABLE sys_print_template
    ALTER COLUMN template_type SET NOT NULL;
