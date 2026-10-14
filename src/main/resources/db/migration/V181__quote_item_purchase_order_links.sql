-- 报价单商品行 × 采购订单明细行: 一行可关联多张订单明细行, 并记录本次在该明细行开出的吨位。
--
-- 背景: 原先 mk_quote_item.purchase_order_item_id 是单值字段, 一条 130 吨的报单挂到一条订货
-- 10 吨的明细行上就没有额度可用了; 行业做法(SAP 分配表 / Oracle firm allocation)是把"关联"
-- 与"该关联上的分配数量"一起存, 因此这里显式带 allocated_ton。
--
-- 兼容: 旧的三列(purchase_order_id / purchase_order_no / purchase_order_item_id)继续保留并镜像
-- 第一条关联, 历史读取与汇总口径不受影响; 汇总查询按「旧列 ∪ 本表」按 (明细行, 订单明细行)
-- 去重求和, 避免同一条关联被计两次。

CREATE TABLE public.mk_quote_item_purchase_order (
    id bigint NOT NULL,
    item_id bigint NOT NULL,
    purchase_order_id bigint NOT NULL,
    purchase_order_no character varying(64),
    purchase_order_item_id bigint NOT NULL,
    allocated_ton numeric(18,8) NOT NULL DEFAULT 0,
    sort_no integer NOT NULL DEFAULT 0,
    CONSTRAINT mk_quote_item_purchase_order_pkey PRIMARY KEY (id),
    CONSTRAINT uk_quote_item_purchase_order UNIQUE (item_id, purchase_order_item_id),
    CONSTRAINT fk_quote_item_purchase_order_item FOREIGN KEY (item_id)
        REFERENCES public.mk_quote_item (id)
);

COMMENT ON TABLE public.mk_quote_item_purchase_order IS '报价单行×关联的采购订单明细行(含本次开出吨位)';
COMMENT ON COLUMN public.mk_quote_item_purchase_order.allocated_ton IS '本次在该订单明细行开出的吨位(默认按关联顺序填满本行报单吨位)';
COMMENT ON COLUMN public.mk_quote_item_purchase_order.sort_no IS '关联顺序(自动分配与展示顺序)';

-- 索引名必须避开 mk_quote_item 上已有的 idx_quote_item_purchase_order(_item):
-- 那是早先迁移给旧单值列建的同名索引, 本表需要独立命名。
CREATE INDEX idx_quote_item_po_link_item
    ON public.mk_quote_item_purchase_order (item_id);

CREATE INDEX idx_quote_item_po_link_poi
    ON public.mk_quote_item_purchase_order (purchase_order_item_id);

-- 回填现有单值关联。
-- id 直接取 item_id: 全系统 id 由同一个雪花生成器产生, 不会再发放同一个值, 因此不会与后续
-- 新插入的关联行冲突, 也不需要在迁移里做额外的 id 分配。
INSERT INTO public.mk_quote_item_purchase_order (
    id, item_id, purchase_order_id, purchase_order_no, purchase_order_item_id, allocated_ton, sort_no
)
SELECT item.id,
       item.id,
       item.purchase_order_id,
       item.purchase_order_no,
       item.purchase_order_item_id,
       COALESCE(item.ton, 0),
       0
  FROM public.mk_quote_item item
 WHERE item.purchase_order_item_id IS NOT NULL
   AND item.purchase_order_id IS NOT NULL;
