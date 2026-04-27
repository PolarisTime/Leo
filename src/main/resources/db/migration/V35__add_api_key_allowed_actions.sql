ALTER TABLE auth_api_key
    ADD COLUMN IF NOT EXISTS allowed_actions VARCHAR(512);

UPDATE auth_api_key
SET allowed_actions = 'VIEW,CREATE,EDIT,DELETE,EXPORT'
WHERE allowed_actions IS NULL OR BTRIM(allowed_actions) = '';
