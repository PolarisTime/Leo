-- 采购入库单软删除后，其明细行仍保留（审计需要），并通过 RESTRICT 外键
-- fk_po_purchase_inbound_item_source_identity 物理锁定来源采购订单明细。
-- 后果：一张「已删除」的采购入库单会继续阻止采购订单修改——订单行被移除时
-- 数据库直接拒绝删除，用户在界面上看到的却是「仍被已删除的采购入库单引用」。
--
-- 业务口径：已删除的单据不应再影响上游单据。修法是在订单行被移除前，由应用层
-- 把这些残留引用置空（PurchaseInboundItemRepository.detachSourceReferencesOfDeletedInbounds，
-- 只处理 deleted_flag = true 的入库单），因此该列必须允许 NULL。
--
-- NULL 语义：来源订单行已被删除（只会出现在已软删除的入库单明细上）；
-- 活跃入库单的来源行仍由 RESTRICT + 业务守卫双重保护，不受本次变更影响。
ALTER TABLE public.po_purchase_inbound_item
    ALTER COLUMN source_purchase_order_item_id DROP NOT NULL;

COMMENT ON COLUMN public.po_purchase_inbound_item.source_purchase_order_item_id IS
    '来源采购订单明细 ID; 来源行被删除后置为 NULL(仅已软删除的入库单会出现), 活跃入库单不允许为空';
