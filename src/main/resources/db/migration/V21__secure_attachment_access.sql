ALTER TABLE sys_attachment
    ADD COLUMN IF NOT EXISTS access_key VARCHAR(64);

UPDATE sys_attachment
SET access_key = md5(id::text || ':' || COALESCE(created_at::text, '') || ':' || random()::text || ':' || clock_timestamp()::text)
WHERE access_key IS NULL
   OR access_key = '';

ALTER TABLE sys_attachment
    ALTER COLUMN access_key SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_attachment_access_key ON sys_attachment (access_key);

INSERT INTO sys_menu_action (id, menu_code, action_code, action_name)
VALUES
    (100111, 'attachment-management', 'VIEW', '查看'),
    (100112, 'attachment-management', 'EDIT', '编辑')
ON CONFLICT (menu_code, action_code) DO NOTHING;

INSERT INTO sys_role_action (id, role_id, menu_code, action_code)
SELECT
    (SELECT COALESCE(MAX(id), 0) FROM sys_role_action) + ROW_NUMBER() OVER (),
    r.id,
    ma.menu_code,
    ma.action_code
FROM sys_role r
JOIN sys_menu_action ma
  ON ma.menu_code = 'attachment-management'
 AND ma.action_code IN ('VIEW', 'EDIT')
ON CONFLICT (role_id, menu_code, action_code) DO NOTHING;
