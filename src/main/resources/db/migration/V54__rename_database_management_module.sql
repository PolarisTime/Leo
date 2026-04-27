UPDATE sys_menu
SET menu_code = 'database-management',
    route_path = '/database-management'
WHERE menu_code = 'ops-support';

UPDATE sys_menu_action
SET menu_code = 'database-management'
WHERE menu_code = 'ops-support';

UPDATE sys_role_action
SET menu_code = 'database-management'
WHERE menu_code = 'ops-support';

UPDATE auth_api_key
SET allowed_menus = normalized.allowed_menus
FROM (
    SELECT
        ak.id,
        COALESCE(
            string_agg(DISTINCT CASE
                WHEN BTRIM(token.menu_code) = 'ops-support' THEN 'database-management'
                ELSE BTRIM(token.menu_code)
            END, ',' ORDER BY CASE
                WHEN BTRIM(token.menu_code) = 'ops-support' THEN 'database-management'
                ELSE BTRIM(token.menu_code)
            END),
            ''
        ) AS allowed_menus
    FROM auth_api_key ak
    LEFT JOIN LATERAL regexp_split_to_table(COALESCE(ak.allowed_menus, ''), ',') AS token(menu_code) ON TRUE
    GROUP BY ak.id
) AS normalized
WHERE auth_api_key.id = normalized.id
  AND auth_api_key.allowed_menus IS NOT NULL
  AND POSITION('ops-support' IN auth_api_key.allowed_menus) > 0;

UPDATE sys_upload_rule
SET module_key = 'database-management',
    rule_code = 'PAGE_UPLOAD_DATABASE_MANAGEMENT',
    rule_name = '数据库管理上传命名规则',
    remark = REPLACE(COALESCE(remark, ''), '运维支持', '数据库管理')
WHERE module_key = 'ops-support';

UPDATE sys_attachment_binding
SET module_key = 'database-management'
WHERE module_key = 'ops-support';
