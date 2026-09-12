-- 修正默认物流对账 PDF 文件模板的 source_checksum 口径。
--
-- 背景：V117 登记 DEFAULT_FREIGHT_STATEMENT_PDF_FORM 时写入的是文件原始字节 SHA-256
-- （22c68d57ff3f6cb7d728710701102cbf5ef6c24bf9e0f7921aab83b8b9c3d3eb），而运行时
-- PrintTemplateFileSyncRunner.readClasspathText 会按 UTF-8 解码并 trim 后再计算
-- SHA-256（0a14f88e2285c27999db0d60fe5210947a5f51dd417410ac0e608e744870e55c）。
-- 两者口径不一致，新环境首次启动同步时校验值必定不匹配。
--
-- 本迁移把静态元数据校正为运行时 trim 口径的 SHA-256，使数据库中登记的校验值准确。
-- 模板正文仍由 PrintTemplateFileSyncRunner 在启动时从文件写入：新环境首次启动仍会正常
-- 同步一次并递增 version_no（占位正文与文件内容不同），本脚本只保证静态元数据准确。
-- 幂等：仅更新仍以 FILE 托管且未删除的该模板记录，重复执行无副作用。
-- 注：seed 线 S14 已做同样校正，本迁移把该修复补齐到主线。

UPDATE sys_print_template
SET source_checksum = '0a14f88e2285c27999db0d60fe5210947a5f51dd417410ac0e608e744870e55c',
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE template_code = 'DEFAULT_FREIGHT_STATEMENT_PDF_FORM'
  AND sync_mode = 'FILE'
  AND template_type = 'PDF_FORM'
  AND deleted_flag = FALSE;
