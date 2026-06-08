ALTER TABLE sys_print_template
    ADD COLUMN IF NOT EXISTS template_code VARCHAR(96),
    ADD COLUMN IF NOT EXISTS engine VARCHAR(32),
    ADD COLUMN IF NOT EXISTS asset_ref VARCHAR(255),
    ADD COLUMN IF NOT EXISTS version_no INTEGER DEFAULT 1,
    ADD COLUMN IF NOT EXISTS status VARCHAR(16) DEFAULT 'ACTIVE';

UPDATE sys_print_template
SET template_code = 'TPL_' || id::text
WHERE template_code IS NULL OR btrim(template_code) = '';

UPDATE sys_print_template
SET engine = CASE template_type
                 WHEN 'COORD' THEN 'LODOP'
                 WHEN 'PDF_FORM' THEN 'PDF_FORM'
                 ELSE 'BROWSER_HTML'
             END
WHERE engine IS NULL OR btrim(engine) = '';

UPDATE sys_print_template
SET version_no = 1
WHERE version_no IS NULL OR version_no < 1;

UPDATE sys_print_template
SET status = 'ACTIVE'
WHERE status IS NULL OR btrim(status) = '';

UPDATE sys_print_template
SET template_code = 'SALES_OUTBOUND_YINGJIE_A4',
    engine = 'LODOP',
    status = 'ACTIVE'
WHERE id = 700540000000000021;

UPDATE sys_print_template
SET template_code = 'SALES_OUTBOUND_YINGJIE_A4_REMARK',
    engine = 'LODOP',
    status = 'ACTIVE'
WHERE id = 700540000000000022;

UPDATE sys_print_template
SET template_code = 'SALES_ORDER_YINGJIE_A4_REMARK_PDF',
    bill_type = 'sales-order',
    template_name = '颖捷A4打印_带备注 PDF',
    template_type = 'PDF_FORM',
    engine = 'PDF_FORM',
    asset_ref = 'print-forms/yingjie-a4-remark.pdf',
    template_html = '{"form":"YINGJIE_A4_REMARK","template":"print-forms/yingjie-a4-remark.pdf"}',
    version_no = 1,
    status = 'ACTIVE',
    deleted_flag = FALSE
WHERE id = 700540000000000029;

UPDATE sys_print_template
SET asset_ref = 'print-forms/yingjie-a4-remark.pdf',
    engine = 'PDF_FORM',
    template_html = '{"form":"YINGJIE_A4_REMARK","template":"print-forms/yingjie-a4-remark.pdf"}'
WHERE template_type = 'PDF_FORM';

UPDATE sys_print_template
SET engine = 'LODOP',
    asset_ref = NULL
WHERE template_type = 'COORD';

UPDATE sys_print_template
SET engine = 'BROWSER_HTML',
    asset_ref = NULL
WHERE template_type = 'HTML';

ALTER TABLE sys_print_template
    ALTER COLUMN template_code SET NOT NULL,
    ALTER COLUMN engine SET NOT NULL,
    ALTER COLUMN version_no SET NOT NULL,
    ALTER COLUMN status SET NOT NULL;

DROP INDEX IF EXISTS uk_sys_print_template_bill_type_code;

CREATE UNIQUE INDEX uk_sys_print_template_bill_type_code
    ON sys_print_template (bill_type, template_code)
    WHERE deleted_flag = FALSE;

ALTER TABLE sys_print_template
    DROP CONSTRAINT IF EXISTS chk_print_template_type,
    DROP CONSTRAINT IF EXISTS chk_print_template_engine,
    DROP CONSTRAINT IF EXISTS chk_print_template_status,
    DROP CONSTRAINT IF EXISTS chk_print_template_type_engine,
    DROP CONSTRAINT IF EXISTS chk_print_template_pdf_asset,
    DROP CONSTRAINT IF EXISTS chk_print_template_version_no;

ALTER TABLE sys_print_template
    ADD CONSTRAINT chk_print_template_type
        CHECK (template_type IN ('HTML', 'COORD', 'PDF_FORM')),
    ADD CONSTRAINT chk_print_template_engine
        CHECK (engine IN ('BROWSER_HTML', 'LODOP', 'PDF_FORM')),
    ADD CONSTRAINT chk_print_template_status
        CHECK (status IN ('ACTIVE', 'DISABLED')),
    ADD CONSTRAINT chk_print_template_type_engine
        CHECK (
            (template_type = 'HTML' AND engine = 'BROWSER_HTML')
            OR (template_type = 'COORD' AND engine = 'LODOP')
            OR (template_type = 'PDF_FORM' AND engine = 'PDF_FORM')
        ),
    ADD CONSTRAINT chk_print_template_pdf_asset
        CHECK (
            (template_type = 'PDF_FORM' AND asset_ref IS NOT NULL AND btrim(asset_ref) <> '')
            OR (template_type <> 'PDF_FORM' AND asset_ref IS NULL)
        ),
    ADD CONSTRAINT chk_print_template_version_no
        CHECK (version_no >= 1);
