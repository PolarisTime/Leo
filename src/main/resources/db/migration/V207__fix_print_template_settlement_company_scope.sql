UPDATE sys_print_template
SET settlement_company_id = NULL,
    settlement_company_name = NULL
WHERE deleted_flag = FALSE
  AND bill_type NOT IN (
      'purchase-order',
      'sales-order',
      'purchase-inbound',
      'sales-outbound',
      'freight-bill',
      'customer-statement',
      'supplier-statement',
      'freight-statement',
      'receipt',
      'invoice-issue'
  )
  AND settlement_company_id IS NOT NULL;

DROP INDEX IF EXISTS uk_sys_print_template_bill_type_code;
DROP INDEX IF EXISTS uk_sys_print_template_bill_type_name;

CREATE UNIQUE INDEX uk_sys_print_template_bill_type_code
    ON sys_print_template (bill_type, COALESCE(settlement_company_id, 0::BIGINT), template_code)
    WHERE deleted_flag = FALSE;

CREATE UNIQUE INDEX uk_sys_print_template_bill_type_name
    ON sys_print_template (bill_type, COALESCE(settlement_company_id, 0::BIGINT), template_name)
    WHERE deleted_flag = FALSE;
