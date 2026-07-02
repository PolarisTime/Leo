ALTER TABLE sys_print_template
    ADD COLUMN IF NOT EXISTS settlement_company_id BIGINT,
    ADD COLUMN IF NOT EXISTS settlement_company_name VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_sys_print_template_bill_settlement
    ON sys_print_template (bill_type, settlement_company_id)
    WHERE deleted_flag = FALSE;

DROP INDEX IF EXISTS uk_sys_print_template_bill_type_code;
DROP INDEX IF EXISTS uk_sys_print_template_bill_type_name;

CREATE UNIQUE INDEX uk_sys_print_template_bill_type_code
    ON sys_print_template (bill_type, COALESCE(settlement_company_id, 0::BIGINT), template_code)
    WHERE deleted_flag = FALSE;

CREATE UNIQUE INDEX uk_sys_print_template_bill_type_name
    ON sys_print_template (bill_type, COALESCE(settlement_company_id, 0::BIGINT), template_name)
    WHERE deleted_flag = FALSE;

UPDATE sys_print_template t
SET settlement_company_id = c.id,
    settlement_company_name = c.company_name
FROM sys_company_setting c
WHERE t.deleted_flag = FALSE
  AND c.deleted_flag = FALSE
  AND c.company_name = '嘉兴颖捷建材有限公司'
  AND t.settlement_company_id IS NULL
  AND t.settlement_company_name IS NULL;
