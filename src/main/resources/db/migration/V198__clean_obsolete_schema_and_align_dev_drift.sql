-- Clean schema drift left by retired migration branches and align databases that
-- ran older V127/V128/V129/V144 variants with the current migration chain.

-- Retired notification module. The current application no longer contains
-- notification entities, controllers, menu routes, or permission resources.
DO $$
BEGIN
    IF to_regclass('public.sys_role_permission') IS NOT NULL THEN
        DELETE FROM sys_role_permission
        WHERE resource_code = 'notification';
    END IF;

    IF to_regclass('public.sys_role_action') IS NOT NULL THEN
        DELETE FROM sys_role_action
        WHERE menu_code IN ('notification-rule', 'notification-channel');
    END IF;

    IF to_regclass('public.sys_menu_action') IS NOT NULL THEN
        DELETE FROM sys_menu_action
        WHERE menu_code IN ('notification-rule', 'notification-channel');
    END IF;

    IF to_regclass('public.sys_menu') IS NOT NULL THEN
        DELETE FROM sys_menu
        WHERE menu_code IN ('notification-rule', 'notification-channel');
    END IF;
END $$;

DROP TABLE IF EXISTS notification_rule;
DROP TABLE IF EXISTS notification_push_channel;

-- Retired system_settings branch. Watermark settings now live in sys_no_rule.
DROP TABLE IF EXISTS system_settings;

-- Keep current settlement account JSON populated before dropping the old table.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'md_settlement_account'
    ) THEN
        WITH normalized_company AS (
            SELECT
                cs.id,
                cs.company_name,
                CASE
                    WHEN jsonb_typeof(cs.settlement_accounts_json) = 'array'
                        THEN cs.settlement_accounts_json
                    ELSE '[]'::jsonb
                END AS existing_accounts_json
            FROM sys_company_setting cs
        ),
        merged_accounts AS (
            SELECT
                cs.id AS company_id,
                COALESCE(
                    jsonb_agg(
                        DISTINCT jsonb_build_object(
                            'id', candidate.account_id,
                            'accountName', candidate.account_name,
                            'bankName', candidate.bank_name,
                            'bankAccount', candidate.bank_account,
                            'usageType', candidate.usage_type,
                            'status', candidate.status,
                            'remark', candidate.remark
                        )
                    ) FILTER (WHERE candidate.bank_account IS NOT NULL AND candidate.bank_account <> ''),
                    cs.existing_accounts_json
                ) AS accounts_json
            FROM normalized_company cs
            LEFT JOIN LATERAL (
                SELECT
                    sa.id AS account_id,
                    COALESCE(NULLIF(sa.account_name, ''), cs.company_name) AS account_name,
                    sa.bank_name,
                    sa.bank_account,
                    COALESCE(NULLIF(sa.usage_type, ''), '通用') AS usage_type,
                    COALESCE(NULLIF(sa.status, ''), '正常') AS status,
                    COALESCE(sa.remark, '') AS remark
                FROM md_settlement_account sa
                WHERE sa.deleted_flag = FALSE
                  AND sa.company_name = cs.company_name
                  AND COALESCE(sa.bank_name, '') <> ''
                  AND COALESCE(sa.bank_account, '') <> ''

                UNION ALL

                SELECT
                    CASE
                        WHEN account_item ->> 'id' ~ '^[0-9]+$'
                            THEN (account_item ->> 'id')::bigint
                        ELSE NULL
                    END AS account_id,
                    account_item ->> 'accountName' AS account_name,
                    account_item ->> 'bankName' AS bank_name,
                    account_item ->> 'bankAccount' AS bank_account,
                    COALESCE(NULLIF(account_item ->> 'usageType', ''), '通用') AS usage_type,
                    COALESCE(NULLIF(account_item ->> 'status', ''), '正常') AS status,
                    COALESCE(account_item ->> 'remark', '') AS remark
                FROM jsonb_array_elements(cs.existing_accounts_json) account_item
                WHERE COALESCE(account_item ->> 'bankAccount', '') <> ''
            ) candidate ON TRUE
            GROUP BY cs.id, cs.existing_accounts_json
        )
        UPDATE sys_company_setting cs
        SET settlement_accounts_json = merged_accounts.accounts_json
        FROM merged_accounts
        WHERE cs.id = merged_accounts.company_id;
    END IF;
END $$;

DROP TABLE IF EXISTS md_settlement_account;

-- Migrate any remaining legacy menu-action grants to resource permissions, then
-- remove the old authorization table that the runtime no longer reads.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'sys_role_action'
    ) THEN
        WITH mapped AS (
            SELECT DISTINCT
                role_id,
                CASE menu_code
                    WHEN 'material' THEN 'material'
                    WHEN 'supplier' THEN 'supplier'
                    WHEN 'customer' THEN 'customer'
                    WHEN 'carrier' THEN 'carrier'
                    WHEN 'warehouse' THEN 'warehouse'
                    WHEN 'settlement-accounts' THEN 'company-setting'
                    WHEN 'purchase-order' THEN 'purchase-order'
                    WHEN 'purchase-inbound' THEN 'purchase-inbound'
                    WHEN 'sales-order' THEN 'sales-order'
                    WHEN 'sales-outbound' THEN 'sales-outbound'
                    WHEN 'freight-bill' THEN 'freight-bill'
                    WHEN 'purchase-contracts' THEN 'purchase-contract'
                    WHEN 'purchase-contract' THEN 'purchase-contract'
                    WHEN 'sales-contracts' THEN 'sales-contract'
                    WHEN 'sales-contract' THEN 'sales-contract'
                    WHEN 'supplier-statement' THEN 'supplier-statement'
                    WHEN 'customer-statement' THEN 'customer-statement'
                    WHEN 'freight-statement' THEN 'freight-statement'
                    WHEN 'receipt' THEN 'receipt'
                    WHEN 'payment' THEN 'payment'
                    WHEN 'invoice-receipt' THEN 'invoice-receipt'
                    WHEN 'invoice-issue' THEN 'invoice-issue'
                    WHEN 'receivable-payable' THEN 'receivable-payable'
                    WHEN 'ledger-adjustment' THEN 'ledger-adjustment'
                    WHEN 'general-setting' THEN 'general-setting'
                    WHEN 'company-setting' THEN 'company-setting'
                    WHEN 'operation-log' THEN 'operation-log'
                    WHEN 'user-account' THEN 'user-account'
                    WHEN 'user-accounts' THEN 'user-account'
                    WHEN 'permission' THEN 'permission'
                    WHEN 'permission-management' THEN 'permission'
                    WHEN 'role-settings' THEN 'role'
                    WHEN 'role-setting' THEN 'role'
                    WHEN 'role-action-editor' THEN 'role'
                    WHEN 'database' THEN 'database'
                    WHEN 'ops-support' THEN 'database'
                    WHEN 'session' THEN 'session'
                    WHEN 'api-key' THEN 'api-key'
                    WHEN 'security-key' THEN 'security-key'
                    WHEN 'print-template' THEN 'print-template'
                    WHEN 'department' THEN 'department'
                    WHEN 'material-category' THEN 'material-category'
                    WHEN 'vehicle' THEN 'vehicle'
                    WHEN 'rate-limit' THEN 'rate-limit'
                    ELSE menu_code
                END AS resource_code,
                CASE
                    WHEN menu_code = 'role-action-editor'
                         AND UPPER(BTRIM(action_code)) IN ('EDIT', 'UPDATE', 'MANAGE_PERMISSIONS')
                        THEN 'manage_permissions'
                    WHEN UPPER(BTRIM(action_code)) = 'VIEW' THEN 'read'
                    WHEN UPPER(BTRIM(action_code)) = 'READ' THEN 'read'
                    WHEN UPPER(BTRIM(action_code)) = 'CREATE' THEN 'create'
                    WHEN UPPER(BTRIM(action_code)) = 'ADD' THEN 'create'
                    WHEN UPPER(BTRIM(action_code)) = 'EDIT' THEN 'update'
                    WHEN UPPER(BTRIM(action_code)) = 'UPDATE' THEN 'update'
                    WHEN UPPER(BTRIM(action_code)) = 'DELETE' THEN 'delete'
                    WHEN UPPER(BTRIM(action_code)) = 'AUDIT' THEN 'audit'
                    WHEN UPPER(BTRIM(action_code)) = 'EXPORT' THEN 'export'
                    WHEN UPPER(BTRIM(action_code)) = 'PRINT' THEN 'print'
                    WHEN UPPER(BTRIM(action_code)) = 'MANAGE_PERMISSIONS' THEN 'manage_permissions'
                    ELSE LOWER(BTRIM(action_code))
                END AS action_code
            FROM sys_role_action
            WHERE deleted_flag = FALSE
              AND EXISTS (
                  SELECT 1
                  FROM sys_role role
                  WHERE role.id = sys_role_action.role_id
              )
        ),
        expanded AS (
            SELECT role_id, resource_code, action_code
            FROM mapped
            WHERE resource_code IS NOT NULL
              AND action_code IS NOT NULL
              AND resource_code <> ''
              AND action_code <> ''

            UNION

            SELECT role_id, resource_code, 'read'
            FROM mapped
            WHERE resource_code IS NOT NULL
              AND action_code IS NOT NULL
              AND resource_code <> ''
              AND action_code <> ''
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
    END IF;
END $$;

DROP TABLE IF EXISTS sys_role_action;
DROP TABLE IF EXISTS sys_bootstrap_marker;
DROP TABLE IF EXISTS sys_permission;
DROP TABLE IF EXISTS ops_ticket;

-- The global search view depends on document number columns that this migration
-- may realign for databases that ran older migration variants.
CREATE TEMP TABLE IF NOT EXISTS v198_saved_view_definition (
    view_name TEXT PRIMARY KEY,
    view_definition TEXT NOT NULL
) ON COMMIT DROP;

INSERT INTO v198_saved_view_definition (view_name, view_definition)
SELECT 'global_search_document', pg_get_viewdef('public.global_search_document'::regclass, true)
WHERE to_regclass('public.global_search_document') IS NOT NULL
ON CONFLICT (view_name) DO UPDATE
SET view_definition = EXCLUDED.view_definition;

DROP VIEW IF EXISTS global_search_document;

-- Align older development databases with the current V127/V128/V129/V144
-- definitions. All reductions are guarded by data-length checks.
CREATE OR REPLACE FUNCTION pg_temp.v198_alter_varchar_if_safe(
    p_table_name TEXT,
    p_column_name TEXT,
    p_max_length INTEGER
) RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    has_oversized_value BOOLEAN;
    current_type TEXT;
    current_max_length INTEGER;
BEGIN
    IF to_regclass(format('%I.%I', 'public', p_table_name)) IS NULL THEN
        RAISE NOTICE 'Skipping %.%: table does not exist', p_table_name, p_column_name;
        RETURN;
    END IF;

    SELECT data_type, character_maximum_length
    INTO current_type, current_max_length
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = p_table_name
      AND column_name = p_column_name;

    IF current_type IS NULL THEN
        RAISE NOTICE 'Skipping %.%: column does not exist', p_table_name, p_column_name;
        RETURN;
    END IF;

    IF current_type = 'character varying' AND current_max_length = p_max_length THEN
        RETURN;
    END IF;

    EXECUTE format(
        'SELECT EXISTS (SELECT 1 FROM %I.%I WHERE length(%I) > $1)',
        'public',
        p_table_name,
        p_column_name
    )
    INTO has_oversized_value
    USING p_max_length;

    IF has_oversized_value THEN
        RAISE NOTICE 'Skipping %.% shrink to VARCHAR(%): oversized values exist',
            p_table_name,
            p_column_name,
            p_max_length;
        RETURN;
    END IF;

    EXECUTE format(
        'ALTER TABLE %I.%I ALTER COLUMN %I TYPE VARCHAR(%s)',
        'public',
        p_table_name,
        p_column_name,
        p_max_length
    );
END $$;

ALTER TABLE md_customer ALTER COLUMN project_name TYPE VARCHAR(200);

DROP INDEX IF EXISTS idx_md_material_spec_sort;
ALTER TABLE md_material DROP COLUMN IF EXISTS spec_sort;
ALTER TABLE md_material ALTER COLUMN spec TYPE VARCHAR(64);
ALTER TABLE po_purchase_order_item ALTER COLUMN spec TYPE VARCHAR(64);
ALTER TABLE so_sales_order_item ALTER COLUMN spec TYPE VARCHAR(64);
ALTER TABLE po_purchase_inbound_item ALTER COLUMN spec TYPE VARCHAR(64);
ALTER TABLE so_sales_outbound_item ALTER COLUMN spec TYPE VARCHAR(64);
ALTER TABLE ct_sales_contract_item ALTER COLUMN spec TYPE VARCHAR(64);
ALTER TABLE ct_purchase_contract_item ALTER COLUMN spec TYPE VARCHAR(64);
ALTER TABLE fm_invoice_issue_item ALTER COLUMN spec TYPE VARCHAR(64);
ALTER TABLE fm_invoice_receipt_item ALTER COLUMN spec TYPE VARCHAR(64);
ALTER TABLE lg_freight_bill_item ALTER COLUMN spec TYPE VARCHAR(64);
ALTER TABLE st_customer_statement_item ALTER COLUMN spec TYPE VARCHAR(64);
ALTER TABLE st_supplier_statement_item ALTER COLUMN spec TYPE VARCHAR(64);
ALTER TABLE st_freight_statement_item ALTER COLUMN spec TYPE VARCHAR(64);
ALTER TABLE md_material ADD COLUMN IF NOT EXISTS spec_sort INTEGER
    GENERATED ALWAYS AS (CAST(NULLIF(regexp_replace(spec, '[^0-9]', '', 'g'), '') AS integer)) STORED;
CREATE INDEX IF NOT EXISTS idx_md_material_spec_sort ON md_material (spec_sort);

ALTER TABLE ct_sales_contract ALTER COLUMN contract_no TYPE VARCHAR(64);
ALTER TABLE ct_purchase_contract ALTER COLUMN contract_no TYPE VARCHAR(64);
ALTER TABLE so_sales_order ALTER COLUMN order_no TYPE VARCHAR(64);
ALTER TABLE po_purchase_order ALTER COLUMN order_no TYPE VARCHAR(64);
ALTER TABLE po_purchase_inbound ALTER COLUMN inbound_no TYPE VARCHAR(64);
ALTER TABLE so_sales_outbound ALTER COLUMN outbound_no TYPE VARCHAR(64);
ALTER TABLE lg_freight_bill ALTER COLUMN bill_no TYPE VARCHAR(64);
ALTER TABLE st_customer_statement ALTER COLUMN statement_no TYPE VARCHAR(64);
ALTER TABLE st_supplier_statement ALTER COLUMN statement_no TYPE VARCHAR(64);
ALTER TABLE st_freight_statement ALTER COLUMN statement_no TYPE VARCHAR(64);
ALTER TABLE fm_receipt ALTER COLUMN receipt_no TYPE VARCHAR(64);
ALTER TABLE fm_payment ALTER COLUMN payment_no TYPE VARCHAR(64);
ALTER TABLE fm_invoice_issue ALTER COLUMN issue_no TYPE VARCHAR(64);
ALTER TABLE fm_invoice_receipt ALTER COLUMN receive_no TYPE VARCHAR(64);
ALTER TABLE fm_invoice_issue ALTER COLUMN invoice_no TYPE VARCHAR(64);
ALTER TABLE fm_invoice_receipt ALTER COLUMN invoice_no TYPE VARCHAR(64);
ALTER TABLE sys_operation_log ALTER COLUMN business_no TYPE VARCHAR(64);
ALTER TABLE sys_operation_log ALTER COLUMN log_no TYPE VARCHAR(64);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM sys_user WHERE length(password_hash) > 128) THEN
        ALTER TABLE sys_user ALTER COLUMN password_hash TYPE VARCHAR(128);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM auth_refresh_token WHERE length(token_id) > 36) THEN
        ALTER TABLE auth_refresh_token ALTER COLUMN token_id TYPE VARCHAR(36);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM sys_operation_log WHERE length(request_method) > 8) THEN
        ALTER TABLE sys_operation_log ALTER COLUMN request_method TYPE VARCHAR(8);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM lg_freight_bill WHERE length(vehicle_plate) > 16) THEN
        ALTER TABLE lg_freight_bill ALTER COLUMN vehicle_plate TYPE VARCHAR(16);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM sys_user WHERE length(mobile) > 20) THEN
        ALTER TABLE sys_user ALTER COLUMN mobile TYPE VARCHAR(20);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM md_customer WHERE length(contact_phone) > 20) THEN
        ALTER TABLE md_customer ALTER COLUMN contact_phone TYPE VARCHAR(20);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM md_supplier WHERE length(contact_phone) > 20) THEN
        ALTER TABLE md_supplier ALTER COLUMN contact_phone TYPE VARCHAR(20);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM md_warehouse WHERE length(contact_phone) > 20) THEN
        ALTER TABLE md_warehouse ALTER COLUMN contact_phone TYPE VARCHAR(20);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM md_carrier WHERE length(contact_phone) > 20) THEN
        ALTER TABLE md_carrier ALTER COLUMN contact_phone TYPE VARCHAR(20);
    END IF;

    PERFORM pg_temp.v198_alter_varchar_if_safe('md_carrier', 'vehicle_phone', 20);
    PERFORM pg_temp.v198_alter_varchar_if_safe('md_carrier', 'vehicle_phone2', 20);
    PERFORM pg_temp.v198_alter_varchar_if_safe('md_carrier', 'vehicle_phone3', 20);

    IF NOT EXISTS (SELECT 1 FROM sys_department WHERE length(contact_phone) > 20) THEN
        ALTER TABLE sys_department ALTER COLUMN contact_phone TYPE VARCHAR(20);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM md_material WHERE length(category) > 16 OR length(material) > 16) THEN
        ALTER TABLE md_material ALTER COLUMN category TYPE VARCHAR(16);
        ALTER TABLE md_material ALTER COLUMN material TYPE VARCHAR(16);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM md_material_category WHERE length(category_code) > 16) THEN
        ALTER TABLE md_material_category ALTER COLUMN category_code TYPE VARCHAR(16);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM po_purchase_order_item WHERE length(category) > 16 OR length(material) > 16) THEN
        ALTER TABLE po_purchase_order_item ALTER COLUMN category TYPE VARCHAR(16);
        ALTER TABLE po_purchase_order_item ALTER COLUMN material TYPE VARCHAR(16);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM so_sales_order_item WHERE length(category) > 16 OR length(material) > 16) THEN
        ALTER TABLE so_sales_order_item ALTER COLUMN category TYPE VARCHAR(16);
        ALTER TABLE so_sales_order_item ALTER COLUMN material TYPE VARCHAR(16);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM po_purchase_inbound_item WHERE length(category) > 16 OR length(material) > 16) THEN
        ALTER TABLE po_purchase_inbound_item ALTER COLUMN category TYPE VARCHAR(16);
        ALTER TABLE po_purchase_inbound_item ALTER COLUMN material TYPE VARCHAR(16);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM so_sales_outbound_item WHERE length(category) > 16 OR length(material) > 16) THEN
        ALTER TABLE so_sales_outbound_item ALTER COLUMN category TYPE VARCHAR(16);
        ALTER TABLE so_sales_outbound_item ALTER COLUMN material TYPE VARCHAR(16);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM ct_sales_contract_item WHERE length(category) > 16 OR length(material) > 16) THEN
        ALTER TABLE ct_sales_contract_item ALTER COLUMN category TYPE VARCHAR(16);
        ALTER TABLE ct_sales_contract_item ALTER COLUMN material TYPE VARCHAR(16);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM ct_purchase_contract_item WHERE length(category) > 16 OR length(material) > 16) THEN
        ALTER TABLE ct_purchase_contract_item ALTER COLUMN category TYPE VARCHAR(16);
        ALTER TABLE ct_purchase_contract_item ALTER COLUMN material TYPE VARCHAR(16);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM fm_invoice_issue_item WHERE length(category) > 16 OR length(material) > 16) THEN
        ALTER TABLE fm_invoice_issue_item ALTER COLUMN category TYPE VARCHAR(16);
        ALTER TABLE fm_invoice_issue_item ALTER COLUMN material TYPE VARCHAR(16);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM fm_invoice_receipt_item WHERE length(category) > 16 OR length(material) > 16) THEN
        ALTER TABLE fm_invoice_receipt_item ALTER COLUMN category TYPE VARCHAR(16);
        ALTER TABLE fm_invoice_receipt_item ALTER COLUMN material TYPE VARCHAR(16);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM lg_freight_bill_item WHERE length(category) > 16 OR length(material) > 16) THEN
        ALTER TABLE lg_freight_bill_item ALTER COLUMN category TYPE VARCHAR(16);
        ALTER TABLE lg_freight_bill_item ALTER COLUMN material TYPE VARCHAR(16);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM st_customer_statement_item WHERE length(category) > 16 OR length(material) > 16) THEN
        ALTER TABLE st_customer_statement_item ALTER COLUMN category TYPE VARCHAR(16);
        ALTER TABLE st_customer_statement_item ALTER COLUMN material TYPE VARCHAR(16);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM st_supplier_statement_item WHERE length(category) > 16 OR length(material) > 16) THEN
        ALTER TABLE st_supplier_statement_item ALTER COLUMN category TYPE VARCHAR(16);
        ALTER TABLE st_supplier_statement_item ALTER COLUMN material TYPE VARCHAR(16);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM st_freight_statement_item WHERE length(category) > 16 OR length(material) > 16) THEN
        ALTER TABLE st_freight_statement_item ALTER COLUMN category TYPE VARCHAR(16);
        ALTER TABLE st_freight_statement_item ALTER COLUMN material TYPE VARCHAR(16);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM md_material WHERE length(unit) > 8 OR length(quantity_unit) > 8) THEN
        ALTER TABLE md_material ALTER COLUMN unit TYPE VARCHAR(8);
        ALTER TABLE md_material ALTER COLUMN quantity_unit TYPE VARCHAR(8);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM po_purchase_order_item WHERE length(unit) > 8 OR length(quantity_unit) > 8) THEN
        ALTER TABLE po_purchase_order_item ALTER COLUMN unit TYPE VARCHAR(8);
        ALTER TABLE po_purchase_order_item ALTER COLUMN quantity_unit TYPE VARCHAR(8);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM so_sales_order_item WHERE length(unit) > 8 OR length(quantity_unit) > 8) THEN
        ALTER TABLE so_sales_order_item ALTER COLUMN unit TYPE VARCHAR(8);
        ALTER TABLE so_sales_order_item ALTER COLUMN quantity_unit TYPE VARCHAR(8);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM po_purchase_inbound_item WHERE length(unit) > 8 OR length(quantity_unit) > 8) THEN
        ALTER TABLE po_purchase_inbound_item ALTER COLUMN unit TYPE VARCHAR(8);
        ALTER TABLE po_purchase_inbound_item ALTER COLUMN quantity_unit TYPE VARCHAR(8);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM so_sales_outbound_item WHERE length(unit) > 8 OR length(quantity_unit) > 8) THEN
        ALTER TABLE so_sales_outbound_item ALTER COLUMN unit TYPE VARCHAR(8);
        ALTER TABLE so_sales_outbound_item ALTER COLUMN quantity_unit TYPE VARCHAR(8);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM ct_sales_contract_item WHERE length(unit) > 8 OR length(quantity_unit) > 8) THEN
        ALTER TABLE ct_sales_contract_item ALTER COLUMN unit TYPE VARCHAR(8);
        ALTER TABLE ct_sales_contract_item ALTER COLUMN quantity_unit TYPE VARCHAR(8);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM ct_purchase_contract_item WHERE length(unit) > 8 OR length(quantity_unit) > 8) THEN
        ALTER TABLE ct_purchase_contract_item ALTER COLUMN unit TYPE VARCHAR(8);
        ALTER TABLE ct_purchase_contract_item ALTER COLUMN quantity_unit TYPE VARCHAR(8);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM fm_invoice_issue_item WHERE length(unit) > 8 OR length(quantity_unit) > 8) THEN
        ALTER TABLE fm_invoice_issue_item ALTER COLUMN unit TYPE VARCHAR(8);
        ALTER TABLE fm_invoice_issue_item ALTER COLUMN quantity_unit TYPE VARCHAR(8);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM fm_invoice_receipt_item WHERE length(unit) > 8 OR length(quantity_unit) > 8) THEN
        ALTER TABLE fm_invoice_receipt_item ALTER COLUMN unit TYPE VARCHAR(8);
        ALTER TABLE fm_invoice_receipt_item ALTER COLUMN quantity_unit TYPE VARCHAR(8);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM lg_freight_bill_item WHERE length(quantity_unit) > 8) THEN
        ALTER TABLE lg_freight_bill_item ALTER COLUMN quantity_unit TYPE VARCHAR(8);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM st_customer_statement_item WHERE length(unit) > 8 OR length(quantity_unit) > 8) THEN
        ALTER TABLE st_customer_statement_item ALTER COLUMN unit TYPE VARCHAR(8);
        ALTER TABLE st_customer_statement_item ALTER COLUMN quantity_unit TYPE VARCHAR(8);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM st_supplier_statement_item WHERE length(unit) > 8 OR length(quantity_unit) > 8) THEN
        ALTER TABLE st_supplier_statement_item ALTER COLUMN unit TYPE VARCHAR(8);
        ALTER TABLE st_supplier_statement_item ALTER COLUMN quantity_unit TYPE VARCHAR(8);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM st_freight_statement_item WHERE length(quantity_unit) > 8) THEN
        ALTER TABLE st_freight_statement_item ALTER COLUMN quantity_unit TYPE VARCHAR(8);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM so_sales_order WHERE length(sales_name) > 32) THEN
        ALTER TABLE so_sales_order ALTER COLUMN sales_name TYPE VARCHAR(32);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM ct_sales_contract WHERE length(sales_name) > 32) THEN
        ALTER TABLE ct_sales_contract ALTER COLUMN sales_name TYPE VARCHAR(32);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM ct_purchase_contract WHERE length(buyer_name) > 32) THEN
        ALTER TABLE ct_purchase_contract ALTER COLUMN buyer_name TYPE VARCHAR(32);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM po_purchase_order WHERE length(buyer_name) > 32) THEN
        ALTER TABLE po_purchase_order ALTER COLUMN buyer_name TYPE VARCHAR(32);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM md_customer WHERE length(contact_name) > 32) THEN
        ALTER TABLE md_customer ALTER COLUMN contact_name TYPE VARCHAR(32);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM md_supplier WHERE length(contact_name) > 32) THEN
        ALTER TABLE md_supplier ALTER COLUMN contact_name TYPE VARCHAR(32);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM md_warehouse WHERE length(contact_name) > 32) THEN
        ALTER TABLE md_warehouse ALTER COLUMN contact_name TYPE VARCHAR(32);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM md_carrier WHERE length(contact_name) > 32) THEN
        ALTER TABLE md_carrier ALTER COLUMN contact_name TYPE VARCHAR(32);
    END IF;

    PERFORM pg_temp.v198_alter_varchar_if_safe('md_carrier', 'vehicle_contact', 32);
    PERFORM pg_temp.v198_alter_varchar_if_safe('md_carrier', 'vehicle_contact2', 32);
    PERFORM pg_temp.v198_alter_varchar_if_safe('md_carrier', 'vehicle_contact3', 32);

    IF NOT EXISTS (SELECT 1 FROM md_project WHERE length(project_manager) > 32) THEN
        ALTER TABLE md_project ALTER COLUMN project_manager TYPE VARCHAR(32);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM sys_department WHERE length(manager_name) > 32) THEN
        ALTER TABLE sys_department ALTER COLUMN manager_name TYPE VARCHAR(32);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM fm_payment WHERE length(operator_name) > 32
        UNION ALL SELECT 1 FROM fm_receipt WHERE length(operator_name) > 32
        UNION ALL SELECT 1 FROM fm_invoice_issue WHERE length(operator_name) > 32
        UNION ALL SELECT 1 FROM fm_invoice_receipt WHERE length(operator_name) > 32
    ) THEN
        ALTER TABLE fm_payment ALTER COLUMN operator_name TYPE VARCHAR(32);
        ALTER TABLE fm_receipt ALTER COLUMN operator_name TYPE VARCHAR(32);
        ALTER TABLE fm_invoice_issue ALTER COLUMN operator_name TYPE VARCHAR(32);
        ALTER TABLE fm_invoice_receipt ALTER COLUMN operator_name TYPE VARCHAR(32);
    END IF;
END $$;

ALTER TABLE po_purchase_order ALTER COLUMN order_date TYPE TIMESTAMP;
ALTER TABLE ct_purchase_contract ALTER COLUMN sign_date TYPE TIMESTAMP;
ALTER TABLE ct_purchase_contract ALTER COLUMN effective_date TYPE TIMESTAMP;
ALTER TABLE ct_purchase_contract ALTER COLUMN expire_date TYPE TIMESTAMP;
ALTER TABLE ct_sales_contract ALTER COLUMN sign_date TYPE TIMESTAMP;
ALTER TABLE ct_sales_contract ALTER COLUMN effective_date TYPE TIMESTAMP;
ALTER TABLE ct_sales_contract ALTER COLUMN expire_date TYPE TIMESTAMP;
ALTER TABLE so_sales_order ALTER COLUMN delivery_date TYPE TIMESTAMP;
ALTER TABLE po_purchase_inbound ALTER COLUMN inbound_date TYPE TIMESTAMP;
ALTER TABLE so_sales_outbound ALTER COLUMN outbound_date TYPE TIMESTAMP;
ALTER TABLE lg_freight_bill ALTER COLUMN bill_time TYPE TIMESTAMP;
ALTER TABLE fm_receipt ALTER COLUMN receipt_date TYPE TIMESTAMP;
ALTER TABLE fm_payment ALTER COLUMN payment_date TYPE TIMESTAMP;
ALTER TABLE fm_invoice_issue ALTER COLUMN invoice_date TYPE TIMESTAMP;
ALTER TABLE fm_invoice_receipt ALTER COLUMN invoice_date TYPE TIMESTAMP;
ALTER TABLE st_customer_statement ALTER COLUMN start_date TYPE TIMESTAMP;
ALTER TABLE st_customer_statement ALTER COLUMN end_date TYPE TIMESTAMP;
ALTER TABLE st_supplier_statement ALTER COLUMN start_date TYPE TIMESTAMP;
ALTER TABLE st_supplier_statement ALTER COLUMN end_date TYPE TIMESTAMP;
ALTER TABLE st_freight_statement ALTER COLUMN start_date TYPE TIMESTAMP;
ALTER TABLE st_freight_statement ALTER COLUMN end_date TYPE TIMESTAMP;

-- Align current constraints after historical checksum drift.
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

ALTER TABLE sys_role DROP CONSTRAINT IF EXISTS chk_role_status;
ALTER TABLE sys_role ADD CONSTRAINT chk_role_status CHECK (status IN ('正常', '禁用'));

ALTER TABLE sys_department DROP CONSTRAINT IF EXISTS chk_department_status;
ALTER TABLE sys_department ADD CONSTRAINT chk_department_status CHECK (status IN ('正常', '禁用'));

ALTER TABLE sys_company_setting DROP CONSTRAINT IF EXISTS chk_company_status;
ALTER TABLE sys_company_setting ADD CONSTRAINT chk_company_status CHECK (status IN ('正常', '禁用'));

ALTER TABLE sys_no_rule DROP CONSTRAINT IF EXISTS chk_no_rule_status;
ALTER TABLE sys_no_rule ADD CONSTRAINT chk_no_rule_status CHECK (status IN ('正常', '禁用'));

ALTER TABLE sys_upload_rule DROP CONSTRAINT IF EXISTS chk_upload_rule_status;

UPDATE sys_upload_rule
SET status = '正常'
WHERE status = '启用';

ALTER TABLE sys_upload_rule ADD CONSTRAINT chk_upload_rule_status CHECK (status IN ('正常', '禁用'));

-- Department codes are unique only for active rows in the current schema.
ALTER TABLE sys_department DROP CONSTRAINT IF EXISTS sys_department_department_code_key;
DROP INDEX IF EXISTS sys_department_department_code_key;
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_department_code_active
    ON sys_department (department_code)
    WHERE deleted_flag = FALSE;

DO $$
DECLARE
    saved_definition TEXT;
BEGIN
    SELECT view_definition
    INTO saved_definition
    FROM v198_saved_view_definition
    WHERE view_name = 'global_search_document';

    IF saved_definition IS NOT NULL THEN
        EXECUTE 'CREATE OR REPLACE VIEW global_search_document AS ' || saved_definition;
    END IF;
END $$;

-- Remove obsolete database maintenance objects. pg_stat_statements remains
-- optional and read-only; the application handles absence gracefully.
DROP FUNCTION IF EXISTS fn_cache_warmup();
DROP FUNCTION IF EXISTS fn_reset_query_stats();

DROP VIEW IF EXISTS v_top_slow_queries;
DROP VIEW IF EXISTS v_cache_efficiency;
DROP VIEW IF EXISTS v_table_bloat;
DROP VIEW IF EXISTS v_unused_indexes;

DO $$
BEGIN
    BEGIN
        DROP EXTENSION IF EXISTS pg_buffercache;
    EXCEPTION
        WHEN insufficient_privilege OR dependent_objects_still_exist THEN
            RAISE NOTICE 'Skipping DROP EXTENSION pg_buffercache: %', SQLERRM;
    END;

    BEGIN
        DROP EXTENSION IF EXISTS pg_prewarm;
    EXCEPTION
        WHEN insufficient_privilege OR dependent_objects_still_exist THEN
            RAISE NOTICE 'Skipping DROP EXTENSION pg_prewarm: %', SQLERRM;
    END;

    BEGIN
        DROP EXTENSION IF EXISTS pg_repack;
    EXCEPTION
        WHEN insufficient_privilege OR dependent_objects_still_exist THEN
            RAISE NOTICE 'Skipping DROP EXTENSION pg_repack: %', SQLERRM;
    END;
END $$;
