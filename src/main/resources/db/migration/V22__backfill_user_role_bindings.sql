INSERT INTO sys_user_role (id, user_id, role_id)
SELECT
    (SELECT COALESCE(MAX(id), 0) FROM sys_user_role) + ROW_NUMBER() OVER (ORDER BY u.id, r.id),
    u.id,
    r.id
FROM sys_user u
CROSS JOIN LATERAL regexp_split_to_table(COALESCE(u.role_name, ''), '\s*,\s*') AS role_token(token)
JOIN sys_role r
  ON r.deleted_flag = FALSE
 AND (
     r.role_code = role_token.token
     OR r.role_name = role_token.token
 )
WHERE u.deleted_flag = FALSE
  AND role_token.token <> ''
ON CONFLICT (user_id, role_id) DO NOTHING;
