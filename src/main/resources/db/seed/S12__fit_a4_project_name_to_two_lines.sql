UPDATE public.sys_print_template
SET source_checksum = '6293f0759ed5c08644a297cb9b3ad7bc30f16c2b1e3058ee53b23babd8404430',
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE template_code = 'SALES_ORDER_YINGJIE_A4_REMARK_PDF'
  AND sync_mode = 'FILE'
  AND template_type = 'PDF_FORM'
  AND deleted_flag = FALSE;

WITH adjusted AS (
    SELECT
        id,
        jsonb_set(
            template_html::jsonb,
            '{fields,projectName}',
            (template_html::jsonb #> '{fields,projectName}')
                || '{"top": 96, "height": 28, "fontSize": 12, "minimumFontSize": 8, "maxLines": 2, "multiline": true, "vertical": "middle", "lineHeight": 1.0, "verticalPadding": 1}'::jsonb,
            TRUE
        ) AS template_json
    FROM public.sys_print_template
    WHERE template_code = 'TPL_333661633949728768'
      AND template_type = 'PDF_FORM'
      AND sync_mode = 'MANUAL'
      AND deleted_flag = FALSE
      AND template_html IS NOT NULL
)
UPDATE public.sys_print_template template
SET template_html = adjusted.template_json::text,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP,
    version_no = template.version_no + 1
FROM adjusted
WHERE template.id = adjusted.id
  AND template.template_html IS DISTINCT FROM adjusted.template_json::text;
