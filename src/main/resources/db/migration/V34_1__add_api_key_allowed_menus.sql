ALTER TABLE auth_api_key
    ADD COLUMN IF NOT EXISTS allowed_menus VARCHAR(2000);

UPDATE auth_api_key
SET allowed_menus = ''
WHERE allowed_menus IS NULL;
