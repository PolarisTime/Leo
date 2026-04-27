ALTER TABLE sys_upload_rule
    ADD COLUMN IF NOT EXISTS module_key VARCHAR(64);

UPDATE sys_upload_rule
SET module_key = 'legacy-page-upload'
WHERE rule_code = 'PAGE_UPLOAD'
  AND (module_key IS NULL OR module_key = '');

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_upload_rule_module_key
    ON sys_upload_rule (module_key)
    WHERE deleted_flag = FALSE;
