UPDATE public.sys_no_rule
SET status = '禁用',
    deleted_flag = TRUE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE setting_code IN (
    'UI_WATERMARK_ENABLED',
    'SYS_WATERMARK_CONTENT',
    'SYS_WATERMARK_FONT_SIZE',
    'SYS_WATERMARK_ROTATE',
    'SYS_WATERMARK_COLOR',
    'SYS_WATERMARK_DENSITY',
    'SYS_ATTACHMENT_WATERMARK_ENABLED'
);
