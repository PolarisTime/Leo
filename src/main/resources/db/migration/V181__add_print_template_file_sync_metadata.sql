ALTER TABLE sys_print_template
    ADD COLUMN IF NOT EXISTS sync_mode VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN IF NOT EXISTS source_ref VARCHAR(255),
    ADD COLUMN IF NOT EXISTS source_checksum VARCHAR(64);

UPDATE sys_print_template
SET sync_mode = 'MANUAL'
WHERE sync_mode IS NULL OR btrim(sync_mode) = '';

UPDATE sys_print_template
SET sync_mode = 'FILE',
    source_ref = 'print-forms/yingjie-a4-remark.layout.json',
    source_checksum = NULL,
    updated_at = now()
WHERE bill_type = 'sales-order'
  AND template_type = 'PDF_FORM'
  AND template_code = 'SALES_ORDER_YINGJIE_A4_REMARK_PDF';

ALTER TABLE sys_print_template
    DROP CONSTRAINT IF EXISTS chk_print_template_sync_mode,
    DROP CONSTRAINT IF EXISTS chk_print_template_file_source;

ALTER TABLE sys_print_template
    ADD CONSTRAINT chk_print_template_sync_mode
        CHECK (sync_mode IN ('MANUAL', 'FILE')),
    ADD CONSTRAINT chk_print_template_file_source
        CHECK (
            (sync_mode = 'FILE' AND source_ref IS NOT NULL AND btrim(source_ref) <> '')
            OR sync_mode = 'MANUAL'
        );
