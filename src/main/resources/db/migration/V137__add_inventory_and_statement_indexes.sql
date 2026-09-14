-- 性能补索引（V136 库存台账 / 客户对账明细）。
-- 纯新增索引，不修改任何既有表结构、约束与历史数据，可在空库顺序执行。

-- 1) 库存事务来源单据维度部分索引：
--    反审核/删除来源单据时按 (source_document_type, source_document_id) 定位待软删事务，
--    单据流与红字占用查询也按该组合过滤未删除事务；部分索引避免索引已删除历史行。
CREATE INDEX idx_inv_transaction_source_document
    ON public.inv_transaction (source_document_type, source_document_id)
    WHERE deleted_flag = false;

-- 2) 客户对账明细来源销售订单明细部分索引：
--    红字/对账占用与单据流查询按 source_sales_order_item_id 反查来源订单明细，
--    NULL 行不参与匹配，使用部分索引减小体积并保持选择率。
CREATE INDEX idx_st_customer_statement_item_source_order_item
    ON public.st_customer_statement_item (source_sales_order_item_id)
    WHERE source_sales_order_item_id IS NOT NULL;
