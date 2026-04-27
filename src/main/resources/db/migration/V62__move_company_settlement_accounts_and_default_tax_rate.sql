ALTER TABLE sys_company_setting
    ADD COLUMN IF NOT EXISTS settlement_accounts_json TEXT NOT NULL DEFAULT '[]';

WITH merged_accounts AS (
    SELECT
        cs.id AS company_id,
        COALESCE(
            jsonb_agg(
                jsonb_build_object(
                    'id', candidate.account_id,
                    'accountName', candidate.account_name,
                    'bankName', candidate.bank_name,
                    'bankAccount', candidate.bank_account,
                    'usageType', candidate.usage_type,
                    'status', candidate.status,
                    'remark', candidate.remark
                )
                ORDER BY candidate.sort_order, candidate.account_id
            ) FILTER (WHERE candidate.bank_account IS NOT NULL AND candidate.bank_account <> ''),
            '[]'::jsonb
        )::text AS accounts_json
    FROM sys_company_setting cs
    LEFT JOIN LATERAL (
        SELECT DISTINCT ON (source.bank_account)
            source.account_id,
            source.account_name,
            source.bank_name,
            source.bank_account,
            source.usage_type,
            source.status,
            source.remark,
            source.sort_order
        FROM (
            SELECT
                cs.id AS account_id,
                cs.company_name AS account_name,
                cs.bank_name,
                cs.bank_account,
                '通用'::VARCHAR AS usage_type,
                COALESCE(NULLIF(cs.status, ''), '正常') AS status,
                COALESCE(cs.remark, '') AS remark,
                0 AS sort_order
            WHERE COALESCE(cs.bank_name, '') <> ''
              AND COALESCE(cs.bank_account, '') <> ''

            UNION ALL

            SELECT
                sa.id AS account_id,
                COALESCE(NULLIF(sa.account_name, ''), cs.company_name) AS account_name,
                sa.bank_name,
                sa.bank_account,
                COALESCE(NULLIF(sa.usage_type, ''), '通用') AS usage_type,
                COALESCE(NULLIF(sa.status, ''), '正常') AS status,
                COALESCE(sa.remark, '') AS remark,
                1 AS sort_order
            FROM md_settlement_account sa
            WHERE sa.deleted_flag = FALSE
              AND sa.company_name = cs.company_name
              AND COALESCE(sa.bank_name, '') <> ''
              AND COALESCE(sa.bank_account, '') <> ''
        ) source
        ORDER BY source.bank_account, source.sort_order DESC, source.account_id
    ) candidate ON TRUE
    GROUP BY cs.id
)
UPDATE sys_company_setting cs
SET settlement_accounts_json = merged_accounts.accounts_json
FROM merged_accounts
WHERE cs.id = merged_accounts.company_id;

INSERT INTO sys_no_rule (
    id,
    setting_code,
    setting_name,
    bill_name,
    prefix,
    date_rule,
    serial_length,
    reset_rule,
    sample_no,
    current_period,
    next_serial_value,
    status,
    remark
)
SELECT
    700500000000000110,
    'SYS_DEFAULT_TAX_RATE',
    '默认税率',
    '发票税率',
    'SYS',
    'yyyy',
    1,
    'YEARLY',
    TO_CHAR(
        COALESCE(
            (SELECT tax_rate FROM sys_company_setting WHERE deleted_flag = FALSE ORDER BY id ASC LIMIT 1),
            0.1300
        ),
        'FM999999990.0000'
    ),
    NULL,
    NULL,
    '正常',
    '用于发票默认税率与税额自动计算'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_no_rule
    WHERE setting_code = 'SYS_DEFAULT_TAX_RATE'
      AND deleted_flag = FALSE
);

UPDATE sys_menu
SET deleted_flag = TRUE,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'settlement-accounts'
  AND deleted_flag = FALSE;

UPDATE sys_menu_action
SET deleted_flag = TRUE,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'settlement-accounts'
  AND deleted_flag = FALSE;

UPDATE sys_role_action
SET deleted_flag = TRUE,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE menu_code = 'settlement-accounts'
  AND deleted_flag = FALSE;
