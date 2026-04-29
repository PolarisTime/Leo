-- Convert all business number rules to magic-function template format.
-- Legacy format (date + prefix + serial) → Magic format (prefix{date}{seq}).

UPDATE sys_no_rule
SET prefix = 'PO{yyyy}{seq}',
    date_rule = 'yyyy',
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE setting_code = 'RULE_PO' AND (prefix NOT LIKE '%{%}' OR prefix IS NULL);

UPDATE sys_no_rule
SET prefix = 'PI{yyyy}{seq}',
    date_rule = 'yyyy',
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE setting_code = 'RULE_PI' AND (prefix NOT LIKE '%{%}' OR prefix IS NULL);

UPDATE sys_no_rule
SET prefix = 'SO{yyyy}{seq}',
    date_rule = 'yyyy',
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE setting_code = 'RULE_SO' AND (prefix NOT LIKE '%{%}' OR prefix IS NULL);

UPDATE sys_no_rule
SET prefix = 'OB{yyyy}{seq}',
    date_rule = 'yyyy',
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE setting_code = 'RULE_OB' AND (prefix NOT LIKE '%{%}' OR prefix IS NULL);

UPDATE sys_no_rule
SET prefix = 'FB{yyyy}{seq}',
    date_rule = 'yyyy',
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE setting_code = 'RULE_FB' AND (prefix NOT LIKE '%{%}' OR prefix IS NULL);

UPDATE sys_no_rule
SET prefix = 'SS{yyyy}{seq}',
    date_rule = 'yyyy',
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE setting_code = 'RULE_SS' AND (prefix NOT LIKE '%{%}' OR prefix IS NULL);

UPDATE sys_no_rule
SET prefix = 'CS{yyyy}{seq}',
    date_rule = 'yyyy',
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE setting_code = 'RULE_CS' AND (prefix NOT LIKE '%{%}' OR prefix IS NULL);

UPDATE sys_no_rule
SET prefix = 'FS{yyyy}{seq}',
    date_rule = 'yyyy',
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE setting_code = 'RULE_FS' AND (prefix NOT LIKE '%{%}' OR prefix IS NULL);

UPDATE sys_no_rule
SET prefix = 'LOT{yyyy}{seq}',
    date_rule = 'yyyy',
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE setting_code = 'RULE_BATCH_NO' AND (prefix NOT LIKE '%{%}' OR prefix IS NULL);
