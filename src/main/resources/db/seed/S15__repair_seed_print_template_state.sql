-- 收敛修复 seed 线打印模板元数据状态（追加式修复，不改写历史 seed 脚本）。
--
-- 背景：seed 线 S1-S14 从未被应用/CI 加载，其中部分登记仍指向已从 print-forms/ 删除的
-- 源文件，且 S1/S3 的 ON CONFLICT DO UPDATE 会把命中记录重新置回 ACTIVE。历史 seed 脚本
-- 不可修改，因此新增本脚本：只要 seed 线在任一环境按序执行到 S15，最终状态即与主线
-- V120 以及当前文件系统一致。
--
-- 处理内容：
-- 1) S1/S3 中 source_ref 指向已删除文件的登记统一停用（freight-statement COORD、
--    inventory-report、purchase-contract、sales-contract、supplier-statement 共 5 条）：
--      - print-forms/freight-statement-summary.lodop.txt
--      - print-forms/default-report.layout.json
--      - print-forms/default-purchase-contract.layout.json
--      - print-forms/default-sales-contract.layout.json
--      - print-forms/default-supplier-statement.layout.json
--    仅置 DISABLED、保持 deleted_flag=FALSE，与 PrintTemplateFileSyncRunner 对
--    "源文件已移除"记录的自动停用行为一致，避免启动同步重复处理。
-- 2) freight-statement 的 COORD 模板停用，与 V120 完全同构（V120 处理主线存量，
--    S15 覆盖 seed 线同名场景），抵消 S1 的 ON CONFLICT DO UPDATE 重新激活。
-- 3) 上述已退役模块（报表、采购/销售合同、供应商对账）的登记一并在停用范围内。
--
-- 幂等：仅更新 deleted_flag=FALSE 且状态/默认标记尚未收敛的记录，重复执行无副作用。
-- 与 V120 的关系：freight-statement COORD 段与 V120 同构，可在同一库中安全并存与重复执行。

UPDATE sys_print_template
SET status = 'DISABLED',
    is_default = FALSE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE deleted_flag = FALSE
  AND (
      template_code IN (
          'TPL_700540000000000026',
          'DEFAULT_REPORT_PDF_FORM',
          'DEFAULT_PURCHASE_CONTRACT_PDF_FORM',
          'DEFAULT_SALES_CONTRACT_PDF_FORM',
          'DEFAULT_SUPPLIER_STATEMENT_PDF_FORM'
      )
      OR source_ref IN (
          'print-forms/freight-statement-summary.lodop.txt',
          'print-forms/default-report.layout.json',
          'print-forms/default-purchase-contract.layout.json',
          'print-forms/default-sales-contract.layout.json',
          'print-forms/default-supplier-statement.layout.json'
      )
  )
  AND (status IS DISTINCT FROM 'DISABLED' OR is_default IS DISTINCT FROM FALSE);

UPDATE sys_print_template
SET status = 'DISABLED',
    is_default = FALSE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE bill_type = 'freight-statement'
  AND template_type = 'COORD'
  AND deleted_flag = FALSE
  AND (status IS DISTINCT FROM 'DISABLED' OR is_default IS DISTINCT FROM FALSE);
