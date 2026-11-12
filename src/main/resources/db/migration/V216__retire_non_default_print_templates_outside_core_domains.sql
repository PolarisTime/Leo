-- 打印模板只保留三类业务域的多模板：采购 / 销售 / 物流。
--
-- 口径（2026-11-12 用户确认）：采购、销售、物流三类单据允许存在多套非默认模板
-- （例如销售订单的 A4/A5、颖捷/熠祺版本，物流单的 A 版/配送单），其余单据类型
-- （客户对账、收付款、报表等）只保留脚本化登记的默认模板，非默认模板一律停用。
--
-- 停用口径沿用 V83：不物理删除，便于回溯与恢复 —— status=DISABLED + deleted_flag=TRUE。
-- 对应模板文件已从 src/main/resources/print-forms/ 与 print-template-manifest.json 移除，
-- PrintTemplateFileSyncRunner 之后不会再把它们登记回来（清单是唯一事实源）。
UPDATE public.sys_print_template
SET status = 'DISABLED',
    deleted_flag = TRUE,
    is_default = FALSE,
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE is_default = FALSE
  AND deleted_flag = FALSE
  AND bill_type NOT IN (
      -- 采购
      'purchase-order',
      'purchase-inbound',
      -- 销售
      'sales-order',
      'sales-outbound',
      'sales-return',
      -- 物流
      'freight-bill',
      'freight-statement'
  );
