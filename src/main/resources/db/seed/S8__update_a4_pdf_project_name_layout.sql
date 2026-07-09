UPDATE sys_print_template
SET source_checksum = 'c4dabaa2286f96695ff7913652a459e0f9c9e50182b40071c755331535ab61d6',
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE template_code = 'SALES_ORDER_YINGJIE_A4_REMARK_PDF'
  AND sync_mode = 'FILE'
  AND template_type = 'PDF_FORM'
  AND deleted_flag = FALSE;

WITH manual_template AS (
    SELECT
        id,
        template_html::jsonb AS template_json
    FROM sys_print_template
    WHERE template_code = 'TPL_333661633949728768'
      AND template_type = 'PDF_FORM'
      AND sync_mode = 'MANUAL'
      AND deleted_flag = FALSE
      AND template_html IS NOT NULL
),
title_adjusted AS (
    SELECT
        id,
        jsonb_set(template_json, '{static,0,top}', '28'::jsonb, TRUE) AS template_json
    FROM manual_template
),
label_adjusted AS (
    SELECT
        id,
        jsonb_set(
            jsonb_set(
                jsonb_set(
                    jsonb_set(
                        jsonb_set(
                            jsonb_set(
                                template_json,
                                '{static,8,text}',
                                to_jsonb('销售单号：'::text),
                                TRUE
                            ),
                            '{static,8,width}',
                            '68'::jsonb,
                            TRUE
                        ),
                        '{static,10,text}',
                        to_jsonb('单据日期：'::text),
                        TRUE
                    ),
                    '{static,10,width}',
                    '68'::jsonb,
                    TRUE
                ),
                '{static,12,text}',
                to_jsonb('物流车号：'::text),
                TRUE
            ),
            '{static,12,width}',
            '68'::jsonb,
            TRUE
        ) AS template_json
    FROM title_adjusted
),
field_adjusted AS (
    SELECT
        id,
        jsonb_set(
            jsonb_set(
                jsonb_set(
                    jsonb_set(
                        template_json #- '{fields,projectName,minimumFontSize}',
                        '{fields,billNo}',
                        (template_json #> '{fields,billNo}') || '{"left": 456, "width": 102}'::jsonb,
                        TRUE
                    ),
                    '{fields,billDate}',
                    (template_json #> '{fields,billDate}') || '{"left": 456, "width": 102}'::jsonb,
                    TRUE
                ),
                '{fields,vehiclePlate}',
                (template_json #> '{fields,vehiclePlate}') || '{"left": 456, "width": 102}'::jsonb,
                TRUE
            ),
            '{fields,projectName}',
            ((template_json #> '{fields,projectName}') - 'minimumFontSize')
                || '{"height": 28, "fontSize": 12, "multiline": true, "vertical": "middle", "lineHeight": 1.0, "verticalPadding": 1}'::jsonb,
            TRUE
        ) AS template_json
    FROM label_adjusted
)
UPDATE sys_print_template template
SET template_html = field_adjusted.template_json::text,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP,
    version_no = template.version_no + 1
FROM field_adjusted
WHERE template.id = field_adjusted.id
  AND template.template_html IS DISTINCT FROM field_adjusted.template_json::text;
