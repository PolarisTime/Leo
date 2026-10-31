-- V209: 台账调整写端点下线后的权限残留清理 + 保留列 COMMENT 口径统一。
--
-- 背景：V2LedgerAdjustmentController 的 POST/PUT/PATCH/{id}/status/DELETE 已随本期整改删除
-- （服务层早已全部抛「台账调整单已停用」，行为上长期不可达），仅保留 GET 只读端点。
-- 权限目录 PermissionCatalogService.syncCatalog 只 upsert 不删除，因此 4 个写权限码仍留在
-- sys_permission：实测 0 个角色授权（sys_role_permission 引用数 = 0），可安全删除。
-- 保留 ledger-adjustments:read。
--
-- 同时统一两处「注释与代码不一致」的列口径（列保留，仅改 COMMENT）：
--   1) lg_freight_bill_item.source_sales_outbound_item_id：DocumentFlowService 的历史单据流
--      仍以只读方式 JOIN 它，属保留列；
--   2) so_sales_order_item.source_purchase_order_item_id：注释原称「仅历史」，实际
--      SalesOrderItemMapper 仍可写、单据流/定价/守卫多处读，属仍在服役的兼容字段。

DELETE FROM public.sys_permission permission
 WHERE permission.code IN (
           'ledger-adjustments:create',
           'ledger-adjustments:update',
           'ledger-adjustments:delete',
           'ledger-adjustments:audit')
   AND NOT EXISTS (
           SELECT 1
             FROM public.sys_role_permission role_permission
            WHERE role_permission.permission_code = permission.code
              AND role_permission.deleted_flag = false);

COMMENT ON COLUMN public.lg_freight_bill_item.source_sales_outbound_item_id IS
    '保留列：历史单据流只读依赖（DocumentFlowService 按出库明细关联物流单），勿删；不在 deprecated 清理范围';
COMMENT ON COLUMN public.so_sales_order_item.source_purchase_order_item_id IS
    '仍在服役的兼容字段：采购订单直连来源（历史行原样保存，少数在读链路仍依赖），非只读历史；删列需先下线该能力';
