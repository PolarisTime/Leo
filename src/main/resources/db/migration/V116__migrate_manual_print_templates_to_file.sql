-- 迁移生产库 4 个手动打印模板为文件托管（sync_mode=FILE）
--
-- 背景：生产库 sys_print_template 仅有 4 个 MANUAL 模板（销售订单 A4/A5 × 颖捷/熠祺结算主体），
-- 内容直接存数据库。本迁移将其切换为文件托管：内容由 PrintTemplateFileSyncRunner 启动时从
-- src/main/resources/print-forms/ 同步，DB 只保留元数据。
--
-- 关键点：
-- 1. 只更新 sync_mode / source_ref / source_checksum，不修改 settlement_company_id/name，
--    结算主体绑定保留，打印作业按结算主体筛选逻辑不受影响。
-- 2. source_checksum 为对应文件内容的 SHA-256（小写 hex），与 PrintTemplateChecksum 一致，
--    保证启动同步时不产生版本号递增。
-- 3. WHERE sync_mode='MANUAL' 保证幂等：重复执行或仅在 CI/本地空库执行时不命中，无副作用。
-- 4. 不修改 template_html：当前内容与文件内容一致（文件由本迁移前从库中导出），启动同步会验证。
--
-- 文件与模板 id 对应：
--   print-forms/sales-order-a4-yingjie.layout.json  -> 333289255952457728 (A4 颖捷, PDF_FORM)
--   print-forms/sales-order-a4-yiqi.layout.json     -> 333289339880480768 (A4 熠祺, PDF_FORM)
--   print-forms/sales-order-a5-yingjie.lodop.txt    -> 333290398657028096 (A5 颖捷, COORD)
--   print-forms/sales-order-a5-yiqi.lodop.txt       -> 333290451962437632 (A5 熠祺, COORD)

UPDATE sys_print_template
SET sync_mode = 'FILE',
    source_ref = 'print-forms/sales-order-a4-yingjie.layout.json',
    source_checksum = '552fe2584eeff57ed78b64014efe09640de6136879391193d559268144b7285e',
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 333289255952457728
  AND sync_mode = 'MANUAL'
  AND deleted_flag = false;

UPDATE sys_print_template
SET sync_mode = 'FILE',
    source_ref = 'print-forms/sales-order-a4-yiqi.layout.json',
    source_checksum = 'de63bd2a0dc2c37aa2d5ce4ad05a8f131e1050f646af5fe31b025caf5f937379',
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 333289339880480768
  AND sync_mode = 'MANUAL'
  AND deleted_flag = false;

UPDATE sys_print_template
SET sync_mode = 'FILE',
    source_ref = 'print-forms/sales-order-a5-yingjie.lodop.txt',
    source_checksum = 'dd435bced183b47fe3bd386eb755614c3e0134d2f75c1de548f5050169cc52e5',
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 333290398657028096
  AND sync_mode = 'MANUAL'
  AND deleted_flag = false;

UPDATE sys_print_template
SET sync_mode = 'FILE',
    source_ref = 'print-forms/sales-order-a5-yiqi.lodop.txt',
    source_checksum = 'dd435bced183b47fe3bd386eb755614c3e0134d2f75c1de548f5050169cc52e5',
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 333290451962437632
  AND sync_mode = 'MANUAL'
  AND deleted_flag = false;
