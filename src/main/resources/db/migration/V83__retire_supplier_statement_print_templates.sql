UPDATE public.sys_print_template
SET status = 'DISABLED',
    deleted_flag = TRUE,
    is_default = FALSE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE bill_type = 'supplier-statement'
  AND (status <> 'DISABLED' OR deleted_flag = FALSE OR is_default = TRUE);
