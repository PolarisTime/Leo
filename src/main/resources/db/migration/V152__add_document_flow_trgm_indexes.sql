-- 单据流按引用单号做 LIKE '%单号%' 反查: 为引用列补 pg_trgm GIN 索引,
-- 使包含匹配走索引而非顺序扫描(分层批量反查的每次查询从全表扫描降为索引扫描)。
-- 引用列允许逗号分隔多值, 故不适用 B-tree 精确索引; pg_trgm 支持非左锚定 LIKE。

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_doc_flow_inbound_purchase_order_no
    ON public.po_purchase_inbound USING gin (purchase_order_no gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_doc_flow_sales_order_purchase_order_no
    ON public.so_sales_order USING gin (purchase_order_no gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_doc_flow_sales_order_purchase_inbound_no
    ON public.so_sales_order USING gin (purchase_inbound_no gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_doc_flow_outbound_sales_order_no
    ON public.so_sales_outbound USING gin (sales_order_no gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_doc_flow_return_sales_order_no
    ON public.so_sales_return USING gin (sales_order_no gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_doc_flow_freight_item_source_no
    ON public.lg_freight_bill_item USING gin (source_no gin_trgm_ops);
