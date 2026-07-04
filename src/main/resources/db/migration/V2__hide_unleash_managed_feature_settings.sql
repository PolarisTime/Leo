UPDATE sys_no_rule
SET deleted_flag = TRUE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = now()
WHERE setting_code IN (
    'UI_WEIGHT_ONLY_PURCHASE_INBOUNDS',
    'UI_WEIGHT_ONLY_SALES_OUTBOUNDS',
    'UI_SHOW_SNOWFLAKE_ID',
    'UI_WATERMARK_ENABLED'
)
AND deleted_flag = FALSE;
