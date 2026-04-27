ALTER TABLE auth_api_key
    ADD COLUMN IF NOT EXISTS usage_scope VARCHAR(32);

UPDATE auth_api_key
SET usage_scope = '全部接口'
WHERE COALESCE(BTRIM(usage_scope), '') = '';

ALTER TABLE auth_api_key
    ALTER COLUMN usage_scope SET DEFAULT '全部接口';

ALTER TABLE auth_api_key
    ALTER COLUMN usage_scope SET NOT NULL;
