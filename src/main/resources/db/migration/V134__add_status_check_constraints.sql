-- V134: Add CHECK constraints for well-defined status fields only
-- Only master data and system config statuses, which are confirmed "正常"/"禁用" or "启用"/"禁用"

-- Master data (confirmed: @Pattern(regexp = "正常|禁用"))
ALTER TABLE md_customer DROP CONSTRAINT IF EXISTS chk_customer_status;
ALTER TABLE md_customer ADD CONSTRAINT chk_customer_status CHECK (status IN ('正常', '禁用'));

ALTER TABLE md_supplier DROP CONSTRAINT IF EXISTS chk_supplier_status;
ALTER TABLE md_supplier ADD CONSTRAINT chk_supplier_status CHECK (status IN ('正常', '禁用'));

ALTER TABLE md_warehouse DROP CONSTRAINT IF EXISTS chk_warehouse_status;
ALTER TABLE md_warehouse ADD CONSTRAINT chk_warehouse_status CHECK (status IN ('正常', '禁用'));

ALTER TABLE md_carrier DROP CONSTRAINT IF EXISTS chk_carrier_status;
ALTER TABLE md_carrier ADD CONSTRAINT chk_carrier_status CHECK (status IN ('正常', '禁用'));

ALTER TABLE md_material_category DROP CONSTRAINT IF EXISTS chk_material_category_status;
ALTER TABLE md_material_category ADD CONSTRAINT chk_material_category_status CHECK (status IN ('正常', '禁用'));

ALTER TABLE md_project DROP CONSTRAINT IF EXISTS chk_project_status;
ALTER TABLE md_project ADD CONSTRAINT chk_project_status CHECK (status IN ('正常', '禁用'));

-- User: stores enum name via @Enumerated(EnumType.STRING)
ALTER TABLE sys_user DROP CONSTRAINT IF EXISTS chk_sys_user_status;
ALTER TABLE sys_user ADD CONSTRAINT chk_sys_user_status CHECK (status IN ('NORMAL', 'DISABLED'));

-- Role / Department: plain String field, stores Chinese display values
ALTER TABLE sys_role DROP CONSTRAINT IF EXISTS chk_role_status;
ALTER TABLE sys_role ADD CONSTRAINT chk_role_status CHECK (status IN ('正常', '禁用'));

ALTER TABLE sys_department DROP CONSTRAINT IF EXISTS chk_department_status;
ALTER TABLE sys_department ADD CONSTRAINT chk_department_status CHECK (status IN ('正常', '禁用'));

-- ApiKey: stores displayName via @Convert(ApiKeyStatusConverter)
ALTER TABLE auth_api_key DROP CONSTRAINT IF EXISTS chk_api_key_status;
ALTER TABLE auth_api_key ADD CONSTRAINT chk_api_key_status CHECK (status IN ('有效', '已禁用'));

-- System config statuses
ALTER TABLE sys_company_setting DROP CONSTRAINT IF EXISTS chk_company_status;
ALTER TABLE sys_company_setting ADD CONSTRAINT chk_company_status CHECK (status IN ('正常', '禁用'));

ALTER TABLE sys_no_rule DROP CONSTRAINT IF EXISTS chk_no_rule_status;
ALTER TABLE sys_no_rule ADD CONSTRAINT chk_no_rule_status CHECK (status IN ('正常', '禁用'));

ALTER TABLE sys_upload_rule DROP CONSTRAINT IF EXISTS chk_upload_rule_status;
ALTER TABLE sys_upload_rule ADD CONSTRAINT chk_upload_rule_status CHECK (status IN ('启用', '禁用'));

ALTER TABLE sys_security_secret DROP CONSTRAINT IF EXISTS chk_security_secret_status;
ALTER TABLE sys_security_secret ADD CONSTRAINT chk_security_secret_status CHECK (status IN ('启用', '禁用'));

-- RefreshToken revoke_reason (nullable, keep NOT VALID for legacy data safety)
ALTER TABLE auth_refresh_token DROP CONSTRAINT IF EXISTS chk_refresh_token_revoke_reason;
ALTER TABLE auth_refresh_token ADD CONSTRAINT chk_refresh_token_revoke_reason
    CHECK (revoke_reason IS NULL OR revoke_reason IN ('手动撤销', '密码更改', '登出', '已过期'));
