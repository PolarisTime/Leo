UPDATE public.sys_print_template
SET source_checksum = '238404fd19d0ba1902be4389863542e19c544dc7378cc9e4ef608884d5df4d13',
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE template_code = 'DEFAULT_CUSTOMER_STATEMENT_PDF_FORM'
  AND sync_mode = 'FILE'
  AND template_type = 'PDF_FORM'
  AND deleted_flag = FALSE;
