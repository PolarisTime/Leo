-- Deleted users must not keep active role bindings.
-- OOBE and role statistics depend on active bindings reflecting active users only.

UPDATE sys_user_role ur
SET deleted_flag = TRUE,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE ur.deleted_flag = FALSE
  AND EXISTS (
      SELECT 1
      FROM sys_user u
      WHERE u.id = ur.user_id
        AND u.deleted_flag = TRUE
  );
