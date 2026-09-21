-- 物流单来源占用从订单级下沉到销售订单明细行级，以支持行级拆分/部分导入。
-- 该迁移只新增行级占用表并回填，不删除订单级 lg_freight_bill_source_order 数据；
-- 订单级关系保留为派生化展示/兼容。

CREATE TABLE public.lg_freight_bill_source_item (
    id bigint PRIMARY KEY,
    freight_bill_id bigint NOT NULL,
    source_sales_order_item_id bigint NOT NULL,
    quantity integer NOT NULL,
    active_flag boolean NOT NULL DEFAULT TRUE,
    deleted_flag boolean NOT NULL DEFAULT FALSE,
    created_by bigint NOT NULL DEFAULT 0,
    created_name character varying(64) NOT NULL DEFAULT 'system',
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by bigint,
    updated_name character varying(64),
    updated_at timestamp without time zone,
    CONSTRAINT chk_freight_source_item_quantity
        CHECK (quantity > 0),
    CONSTRAINT uk_freight_source_item_pair
        UNIQUE (freight_bill_id, source_sales_order_item_id),
    CONSTRAINT fk_freight_source_item_bill
        FOREIGN KEY (freight_bill_id) REFERENCES public.lg_freight_bill (id),
    CONSTRAINT fk_freight_source_item_sales_item
        FOREIGN KEY (source_sales_order_item_id) REFERENCES public.so_sales_order_item (id) NOT VALID
);

ALTER TABLE public.lg_freight_bill_source_item
    VALIDATE CONSTRAINT fk_freight_source_item_sales_item;

CREATE INDEX idx_freight_source_item_source_active
    ON public.lg_freight_bill_source_item (source_sales_order_item_id)
    WHERE active_flag = TRUE;

CREATE INDEX idx_freight_source_item_bill
    ON public.lg_freight_bill_source_item (freight_bill_id)
    WHERE active_flag = TRUE;

COMMENT ON TABLE public.lg_freight_bill_source_item IS
    '物流单行级来源销售订单明细占用表：quantity 为本单占用的明细数量';

-- 回填前先检测同一物流单内重复引用同一销售订单明细，存在时拒绝执行并要求人工清理，
-- 否则回填聚合结果会掩盖脏数据，且 uk_freight_source_item_pair 无法建立。
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.lg_freight_bill_item item
        JOIN public.lg_freight_bill bill
          ON bill.id = item.bill_id
         AND bill.deleted_flag = FALSE
        WHERE item.source_sales_order_item_id IS NOT NULL
        GROUP BY item.bill_id, item.source_sales_order_item_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'V156: 存在同一有效物流单重复引用同一销售订单明细，请人工清理后再执行行级占用回填';
    END IF;
END $$;

-- 按 (物流单, 来源销售订单明细) 聚合 lg_freight_bill_item.quantity 回填行级占用。
-- 主键从现有行的 MAX(id) 派生，避免与既有占用行冲突。
WITH source_pairs AS (
    SELECT item.bill_id AS freight_bill_id,
           item.source_sales_order_item_id,
           SUM(item.quantity)::integer AS quantity
    FROM public.lg_freight_bill_item item
    JOIN public.lg_freight_bill bill
      ON bill.id = item.bill_id
     AND bill.deleted_flag = FALSE
    WHERE item.source_sales_order_item_id IS NOT NULL
    GROUP BY item.bill_id, item.source_sales_order_item_id
    HAVING SUM(item.quantity) > 0
), id_base AS (
    SELECT COALESCE(MAX(id), 0) AS max_id
    FROM public.lg_freight_bill_source_item
), numbered_pairs AS (
    SELECT source_pairs.*,
           ROW_NUMBER() OVER (ORDER BY source_pairs.freight_bill_id, source_pairs.source_sales_order_item_id)
               AS row_number
    FROM source_pairs
)
INSERT INTO public.lg_freight_bill_source_item (
    id,
    freight_bill_id,
    source_sales_order_item_id,
    quantity,
    active_flag,
    deleted_flag,
    created_by,
    created_name,
    created_at
)
SELECT id_base.max_id + numbered_pairs.row_number,
       numbered_pairs.freight_bill_id,
       numbered_pairs.source_sales_order_item_id,
       numbered_pairs.quantity,
       TRUE,
       FALSE,
       0,
       'flyway',
       CURRENT_TIMESTAMP
FROM numbered_pairs
CROSS JOIN id_base;

-- 行级占用取代订单级唯一占用：移除订单级唯一索引，使同一销售订单可拆分到多张物流单。
DROP INDEX IF EXISTS public.uk_freight_source_order_active_sales;
