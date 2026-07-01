UPDATE sys_print_template
SET template_html = replace(
        template_html,
        '嘉兴颖捷建材有限公司（供货单）',
        '{{#if settlementCompanyName}}{{settlementCompanyName}}{{else}}嘉兴颖捷建材有限公司{{/if}}（供货单）'
    ),
    version_no = GREATEST(version_no, 1) + 1,
    updated_at = now()
WHERE bill_type IN ('sales-order', 'sales-outbound')
  AND template_type = 'COORD'
  AND template_html LIKE '%嘉兴颖捷建材有限公司（供货单）%';

UPDATE sys_print_template
SET template_html = replace(
        template_html,
        '嘉兴颖捷建材有限公司（供货单）',
        '${settlementCompanyName}（供货单）'
    ),
    source_checksum = CASE
        WHEN sync_mode = 'FILE'
            AND source_ref = 'print-forms/yingjie-a4-remark.layout.json'
        THEN NULL
        ELSE source_checksum
    END,
    version_no = GREATEST(version_no, 1) + 1,
    updated_at = now()
WHERE bill_type IN ('sales-order', 'sales-outbound')
  AND template_type = 'PDF_FORM'
  AND template_html LIKE '%嘉兴颖捷建材有限公司（供货单）%';
