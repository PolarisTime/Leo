-- 停用物流对账单的 COORD/LODOP 模板，统一使用 PDF_FORM 文件托管模板。
--
-- 源文件与清单登记已移除；本迁移显式停用数据库存量，避免增量构建残留资源
-- 导致 PrintTemplateFileSyncRunner 仍保留旧模板。
-- 幂等：仅更新仍启用或仍标记为默认的记录，重复执行无副作用。

UPDATE sys_print_template
SET status = 'DISABLED',
    is_default = FALSE,
    version_no = version_no + 1,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE bill_type = 'freight-statement'
  AND template_type = 'COORD'
  AND deleted_flag = FALSE
  AND (status IS DISTINCT FROM 'DISABLED' OR is_default IS DISTINCT FROM FALSE);
