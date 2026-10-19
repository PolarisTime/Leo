-- 「商品信息」筛选的 trigram 索引：6 张单据明细表的 brand / material / spec / length。
--
-- 背景：列表页的商品筛选（采购订单/采购入库/销售订单/销售出库/销售退货/物流单）走
-- Specs.collectionKeywordLike，对明细行做「任选一列 ILIKE '%关键字%'」，例如：
--   brand ILIKE '%中天%' OR material ILIKE '%HRB400%' OR spec ILIKE '%10%' OR length ILIKE '%9米%'
-- 此前这 6 张表只有 material_id / 主表外键等 btree 索引，四个文本列没有任何索引，
-- 每次按品牌/材质/规格/长度筛选都会全表顺序扫描，随单据量增长线性劣化。
--
-- 为什么用 gin_trgm_ops：ILIKE '%x%' 前后通配，btree 完全用不上；
-- pg_trgm 的 GIN 索引支持 ~~*（ILIKE）运算符。与 V186__keyword_trigram_indexes.sql
-- 处理主表关键字字段的做法一致（该迁移注释也写了「建了但用不上等于白建」）。
--
-- ⚠️ 为什么是「每列一个索引」而不是「一个多列索引」——这是实测结论，不是偏好：
--   1. 建 (brand, material, spec, length) 多列 GIN 后，上述 OR 查询**仍然是 Seq Scan**，
--      多列 GIN 只能在所有列同时用同一个操作符连接时使用，无法服务跨列 OR。
--   2. 改为每列一个 GIN 后，执行计划变成 BitmapOr + 四个 Bitmap Index Scan，四列索引全部命中。
--   验证方式（开发库实测，enable_seqscan=off 排除小表统计信息干扰）：
--     set enable_seqscan = off;
--     explain select id from po_purchase_order_item
--      where brand ilike '%中天%' or material ilike '%HRB400%'
--         or spec ilike '%10%' or length ilike '%9米%';
--     → BitmapOr
--         -> Bitmap Index Scan on ..._brand_trgm
--         -> Bitmap Index Scan on ..._material_trgm
--         -> Bitmap Index Scan on ..._spec_trgm
--         -> Bitmap Index Scan on ..._length_trgm
--
-- 说明：开发库数据量小（百行级），不强制关闭 seqscan 时规划器仍会选 Seq Scan，
-- 这是正常的成本选择，不代表索引无效；生产数据量下会走 BitmapOr。
--
-- 注意：本迁移未包含 material_code —— 该列已有 btree，但 ILIKE '%x%' 用不上 btree。
-- 由于 Specs.PRODUCT_SEARCH_FIELDS 也包含 material_code，这里一并补 trigram 索引，
-- 保持「字段清单里的每一列都可被索引服务」。

-- ── 采购订单明细 ──────────────────────────────────────────────
create index if not exists idx_po_purchase_order_item_brand_trgm
    on public.po_purchase_order_item using gin (brand public.gin_trgm_ops);
create index if not exists idx_po_purchase_order_item_material_trgm
    on public.po_purchase_order_item using gin (material public.gin_trgm_ops);
create index if not exists idx_po_purchase_order_item_spec_trgm
    on public.po_purchase_order_item using gin (spec public.gin_trgm_ops);
create index if not exists idx_po_purchase_order_item_length_trgm
    on public.po_purchase_order_item using gin (length public.gin_trgm_ops);
create index if not exists idx_po_purchase_order_item_material_code_trgm
    on public.po_purchase_order_item using gin (material_code public.gin_trgm_ops);

-- ── 采购入库明细 ──────────────────────────────────────────────
create index if not exists idx_po_purchase_inbound_item_brand_trgm
    on public.po_purchase_inbound_item using gin (brand public.gin_trgm_ops);
create index if not exists idx_po_purchase_inbound_item_material_trgm
    on public.po_purchase_inbound_item using gin (material public.gin_trgm_ops);
create index if not exists idx_po_purchase_inbound_item_spec_trgm
    on public.po_purchase_inbound_item using gin (spec public.gin_trgm_ops);
create index if not exists idx_po_purchase_inbound_item_length_trgm
    on public.po_purchase_inbound_item using gin (length public.gin_trgm_ops);
create index if not exists idx_po_purchase_inbound_item_material_code_trgm
    on public.po_purchase_inbound_item using gin (material_code public.gin_trgm_ops);

-- ── 销售订单明细 ──────────────────────────────────────────────
create index if not exists idx_so_sales_order_item_brand_trgm
    on public.so_sales_order_item using gin (brand public.gin_trgm_ops);
create index if not exists idx_so_sales_order_item_material_trgm
    on public.so_sales_order_item using gin (material public.gin_trgm_ops);
create index if not exists idx_so_sales_order_item_spec_trgm
    on public.so_sales_order_item using gin (spec public.gin_trgm_ops);
create index if not exists idx_so_sales_order_item_length_trgm
    on public.so_sales_order_item using gin (length public.gin_trgm_ops);
create index if not exists idx_so_sales_order_item_material_code_trgm
    on public.so_sales_order_item using gin (material_code public.gin_trgm_ops);

-- ── 销售出库明细 ──────────────────────────────────────────────
create index if not exists idx_so_sales_outbound_item_brand_trgm
    on public.so_sales_outbound_item using gin (brand public.gin_trgm_ops);
create index if not exists idx_so_sales_outbound_item_material_trgm
    on public.so_sales_outbound_item using gin (material public.gin_trgm_ops);
create index if not exists idx_so_sales_outbound_item_spec_trgm
    on public.so_sales_outbound_item using gin (spec public.gin_trgm_ops);
create index if not exists idx_so_sales_outbound_item_length_trgm
    on public.so_sales_outbound_item using gin (length public.gin_trgm_ops);
create index if not exists idx_so_sales_outbound_item_material_code_trgm
    on public.so_sales_outbound_item using gin (material_code public.gin_trgm_ops);

-- ── 销售退货明细 ──────────────────────────────────────────────
create index if not exists idx_so_sales_return_item_brand_trgm
    on public.so_sales_return_item using gin (brand public.gin_trgm_ops);
create index if not exists idx_so_sales_return_item_material_trgm
    on public.so_sales_return_item using gin (material public.gin_trgm_ops);
create index if not exists idx_so_sales_return_item_spec_trgm
    on public.so_sales_return_item using gin (spec public.gin_trgm_ops);
create index if not exists idx_so_sales_return_item_length_trgm
    on public.so_sales_return_item using gin (length public.gin_trgm_ops);
create index if not exists idx_so_sales_return_item_material_code_trgm
    on public.so_sales_return_item using gin (material_code public.gin_trgm_ops);

-- ── 物流单明细 ────────────────────────────────────────────────
create index if not exists idx_lg_freight_bill_item_brand_trgm
    on public.lg_freight_bill_item using gin (brand public.gin_trgm_ops);
create index if not exists idx_lg_freight_bill_item_material_trgm
    on public.lg_freight_bill_item using gin (material public.gin_trgm_ops);
create index if not exists idx_lg_freight_bill_item_spec_trgm
    on public.lg_freight_bill_item using gin (spec public.gin_trgm_ops);
create index if not exists idx_lg_freight_bill_item_length_trgm
    on public.lg_freight_bill_item using gin (length public.gin_trgm_ops);
create index if not exists idx_lg_freight_bill_item_material_code_trgm
    on public.lg_freight_bill_item using gin (material_code public.gin_trgm_ops);
