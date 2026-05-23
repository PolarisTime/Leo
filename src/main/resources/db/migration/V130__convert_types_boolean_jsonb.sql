-- V130: Type conversions
-- M2: isDefault VARCHAR(1) → BOOLEAN
-- L1: JSON text fields → JSONB

-- Convert is_default from VARCHAR(1) to BOOLEAN
-- Must DROP DEFAULT first, then convert, then SET DEFAULT
ALTER TABLE sys_print_template ALTER COLUMN is_default DROP DEFAULT;
ALTER TABLE sys_print_template
    ALTER COLUMN is_default TYPE BOOLEAN
    USING (is_default = '1' OR is_default = 'true');
ALTER TABLE sys_print_template ALTER COLUMN is_default SET DEFAULT FALSE;

-- Convert JSON text fields to JSONB (PostgreSQL native JSON type)
-- with validation, indexing and query support
ALTER TABLE sys_user ALTER COLUMN preferences_json DROP DEFAULT;
ALTER TABLE sys_user
    ALTER COLUMN preferences_json TYPE JSONB
    USING (CASE WHEN preferences_json IS NULL THEN NULL
                ELSE preferences_json::JSONB END);

ALTER TABLE sys_company_setting ALTER COLUMN settlement_accounts_json DROP DEFAULT;
ALTER TABLE sys_company_setting
    ALTER COLUMN settlement_accounts_json TYPE JSONB
    USING (CASE WHEN settlement_accounts_json IS NULL THEN NULL
                ELSE settlement_accounts_json::JSONB END);
