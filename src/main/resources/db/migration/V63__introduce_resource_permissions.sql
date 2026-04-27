-- Resource-based RBAC. Menus remain navigation metadata; permissions are scoped to resources.

CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    resource_code VARCHAR(64) NOT NULL,
    action_code VARCHAR(32) NOT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_name VARCHAR(64) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_name VARCHAR(64),
    updated_at TIMESTAMP,
    deleted_flag BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (role_id, resource_code, action_code)
);

CREATE INDEX IF NOT EXISTS idx_sys_role_permission_role_id ON sys_role_permission (role_id);
CREATE INDEX IF NOT EXISTS idx_sys_role_permission_resource ON sys_role_permission (resource_code);

WITH mapped AS (
    SELECT DISTINCT
        role_id,
        CASE menu_code
            WHEN 'materials' THEN 'material'
            WHEN 'suppliers' THEN 'supplier'
            WHEN 'customers' THEN 'customer'
            WHEN 'carriers' THEN 'carrier'
            WHEN 'warehouses' THEN 'warehouse'
            WHEN 'settlement-accounts' THEN 'company-setting'
            WHEN 'purchase-orders' THEN 'purchase-order'
            WHEN 'purchase-inbounds' THEN 'purchase-inbound'
            WHEN 'sales-orders' THEN 'sales-order'
            WHEN 'sales-outbounds' THEN 'sales-outbound'
            WHEN 'freight-bills' THEN 'freight-bill'
            WHEN 'purchase-contracts' THEN 'purchase-contract'
            WHEN 'sales-contracts' THEN 'sales-contract'
            WHEN 'supplier-statements' THEN 'supplier-statement'
            WHEN 'customer-statements' THEN 'customer-statement'
            WHEN 'freight-statements' THEN 'freight-statement'
            WHEN 'receipts' THEN 'receipt'
            WHEN 'payments' THEN 'payment'
            WHEN 'invoice-receipts' THEN 'invoice-receipt'
            WHEN 'invoice-issues' THEN 'invoice-issue'
            WHEN 'receivables-payables' THEN 'receivable-payable'
            WHEN 'general-settings' THEN 'general-setting'
            WHEN 'company-settings' THEN 'company-setting'
            WHEN 'operation-logs' THEN 'operation-log'
            WHEN 'user-accounts' THEN 'user-account'
            WHEN 'permission-management' THEN 'permission'
            WHEN 'role-settings' THEN 'role'
            WHEN 'role-action-editor' THEN 'role'
            WHEN 'database-management' THEN 'database'
            WHEN 'ops-support' THEN 'database'
            WHEN 'session-management' THEN 'session'
            WHEN 'api-key-management' THEN 'api-key'
            WHEN 'security-keys' THEN 'security-key'
            WHEN 'print-templates' THEN 'print-template'
            ELSE menu_code
        END AS resource_code,
        CASE
            WHEN menu_code = 'role-action-editor' AND UPPER(BTRIM(action_code)) IN ('EDIT', 'UPDATE', 'MANAGE_PERMISSIONS') THEN 'manage_permissions'
            WHEN UPPER(BTRIM(action_code)) = 'VIEW' THEN 'read'
            WHEN UPPER(BTRIM(action_code)) = 'CREATE' THEN 'create'
            WHEN UPPER(BTRIM(action_code)) = 'EDIT' THEN 'update'
            WHEN UPPER(BTRIM(action_code)) = 'DELETE' THEN 'delete'
            WHEN UPPER(BTRIM(action_code)) = 'AUDIT' THEN 'audit'
            WHEN UPPER(BTRIM(action_code)) = 'EXPORT' THEN 'export'
            WHEN UPPER(BTRIM(action_code)) = 'PRINT' THEN 'print'
            ELSE LOWER(BTRIM(action_code))
        END AS action_code
    FROM sys_role_action
    WHERE deleted_flag = FALSE
),
expanded AS (
    SELECT role_id, resource_code, action_code
    FROM mapped
    WHERE resource_code IS NOT NULL
      AND action_code IS NOT NULL
    UNION
    SELECT role_id, resource_code, 'read'
    FROM mapped
    WHERE resource_code IS NOT NULL
      AND action_code IS NOT NULL
      AND action_code <> 'read'
)
INSERT INTO sys_role_permission (id, role_id, resource_code, action_code)
SELECT
    (SELECT COALESCE(MAX(id), 0) FROM sys_role_permission)
        + ROW_NUMBER() OVER (ORDER BY deduped.role_id, deduped.resource_code, deduped.action_code),
    deduped.role_id,
    deduped.resource_code,
    deduped.action_code
FROM (
    SELECT DISTINCT role_id, resource_code, action_code
    FROM expanded
) deduped
ON CONFLICT (role_id, resource_code, action_code) DO NOTHING;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'auth_api_key' AND column_name = 'allowed_menus'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'auth_api_key' AND column_name = 'allowed_resources'
    ) THEN
        ALTER TABLE auth_api_key RENAME COLUMN allowed_menus TO allowed_resources;
    ELSIF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'auth_api_key' AND column_name = 'allowed_resources'
    ) THEN
        ALTER TABLE auth_api_key ADD COLUMN allowed_resources VARCHAR(2000);
    END IF;
END $$;

WITH mapped AS (
    SELECT
        ak.id,
        COALESCE(
            string_agg(DISTINCT mapped_token.resource_code, ',' ORDER BY mapped_token.resource_code)
                FILTER (WHERE mapped_token.resource_code IS NOT NULL AND mapped_token.resource_code <> ''),
            ''
        ) AS resources
    FROM auth_api_key ak
    LEFT JOIN LATERAL regexp_split_to_table(COALESCE(ak.allowed_resources, ''), ',') AS token(menu_code) ON TRUE
    LEFT JOIN LATERAL (
        SELECT CASE BTRIM(token.menu_code)
            WHEN 'materials' THEN 'material'
            WHEN 'suppliers' THEN 'supplier'
            WHEN 'customers' THEN 'customer'
            WHEN 'carriers' THEN 'carrier'
            WHEN 'warehouses' THEN 'warehouse'
            WHEN 'settlement-accounts' THEN 'company-setting'
            WHEN 'purchase-orders' THEN 'purchase-order'
            WHEN 'purchase-inbounds' THEN 'purchase-inbound'
            WHEN 'sales-orders' THEN 'sales-order'
            WHEN 'sales-outbounds' THEN 'sales-outbound'
            WHEN 'freight-bills' THEN 'freight-bill'
            WHEN 'purchase-contracts' THEN 'purchase-contract'
            WHEN 'sales-contracts' THEN 'sales-contract'
            WHEN 'supplier-statements' THEN 'supplier-statement'
            WHEN 'customer-statements' THEN 'customer-statement'
            WHEN 'freight-statements' THEN 'freight-statement'
            WHEN 'receipts' THEN 'receipt'
            WHEN 'payments' THEN 'payment'
            WHEN 'invoice-receipts' THEN 'invoice-receipt'
            WHEN 'invoice-issues' THEN 'invoice-issue'
            WHEN 'receivables-payables' THEN 'receivable-payable'
            WHEN 'general-settings' THEN 'general-setting'
            WHEN 'company-settings' THEN 'company-setting'
            WHEN 'operation-logs' THEN 'operation-log'
            WHEN 'user-accounts' THEN 'user-account'
            WHEN 'permission-management' THEN 'permission'
            WHEN 'role-settings' THEN 'role'
            WHEN 'role-action-editor' THEN 'role'
            WHEN 'database-management' THEN 'database'
            WHEN 'ops-support' THEN 'database'
            WHEN 'session-management' THEN 'session'
            WHEN 'api-key-management' THEN 'api-key'
            WHEN 'security-keys' THEN 'security-key'
            WHEN 'print-templates' THEN 'print-template'
            ELSE BTRIM(token.menu_code)
        END AS resource_code
    ) mapped_token ON TRUE
    GROUP BY ak.id
)
UPDATE auth_api_key ak
SET allowed_resources = mapped.resources
FROM mapped
WHERE ak.id = mapped.id;

WITH mapped AS (
    SELECT
        ak.id,
        COALESCE(
            string_agg(DISTINCT mapped_token.action_code, ',' ORDER BY mapped_token.action_code)
                FILTER (WHERE mapped_token.action_code IS NOT NULL AND mapped_token.action_code <> ''),
            ''
        ) AS actions
    FROM auth_api_key ak
    LEFT JOIN LATERAL regexp_split_to_table(COALESCE(ak.allowed_actions, ''), ',') AS token(action_code) ON TRUE
    LEFT JOIN LATERAL (
        SELECT CASE BTRIM(token.action_code)
            WHEN 'VIEW' THEN 'read'
            WHEN 'CREATE' THEN 'create'
            WHEN 'EDIT' THEN 'update'
            WHEN 'DELETE' THEN 'delete'
            WHEN 'AUDIT' THEN 'audit'
            WHEN 'EXPORT' THEN 'export'
            WHEN 'PRINT' THEN 'print'
            ELSE LOWER(BTRIM(token.action_code))
        END AS action_code
    ) mapped_token ON TRUE
    GROUP BY ak.id
)
UPDATE auth_api_key ak
SET allowed_actions = mapped.actions
FROM mapped
WHERE ak.id = mapped.id;
