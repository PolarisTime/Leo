-- 更新默认物流对账 PDF 文件模板的校验值。
-- 模板正文由 PrintTemplateFileSyncRunner 从 classpath 文件同步，避免在种子脚本中复制布局 JSON。
UPDATE sys_print_template
SET source_checksum = '0a14f88e2285c27999db0d60fe5210947a5f51dd417410ac0e608e744870e55c',
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE template_code = 'DEFAULT_FREIGHT_STATEMENT_PDF_FORM'
  AND sync_mode = 'FILE'
  AND template_type = 'PDF_FORM'
  AND deleted_flag = FALSE;
