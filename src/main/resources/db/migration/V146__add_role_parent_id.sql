-- RBAC1: Role Hierarchy — 角色继承
ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS parent_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_sys_role_parent_id ON sys_role(parent_id) WHERE deleted_flag = FALSE;
