ALTER TABLE sys_print_template
    DROP CONSTRAINT IF EXISTS chk_print_template_type;

ALTER TABLE sys_print_template
    ADD CONSTRAINT chk_print_template_type CHECK (template_type IN ('HTML', 'COORD', 'PDF_FORM'));

INSERT INTO sys_print_template (id, bill_type, template_name, template_type, template_html, is_default, deleted_flag)
VALUES (
    700540000000000024,
    'sales-order',
    '颖捷A4打印_带备注 PDF',
    'PDF_FORM',
    '{"form":"YINGJIE_A4_REMARK","template":"print-forms/yingjie-a4-remark.pdf"}',
    false,
    false
)
ON CONFLICT (id) DO UPDATE
SET bill_type = EXCLUDED.bill_type,
    template_name = EXCLUDED.template_name,
    template_type = EXCLUDED.template_type,
    template_html = EXCLUDED.template_html,
    deleted_flag = FALSE;
