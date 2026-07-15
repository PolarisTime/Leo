UPDATE public.lg_freight_bill
SET status = '草稿'
WHERE status = '未审核';

ALTER TABLE public.lg_freight_bill
    DROP CONSTRAINT IF EXISTS chk_freight_bill_status;

ALTER TABLE public.lg_freight_bill
    ADD CONSTRAINT chk_freight_bill_status
        CHECK (status IN ('草稿', '已审核')) NOT VALID;

ALTER TABLE public.lg_freight_bill
    VALIDATE CONSTRAINT chk_freight_bill_status;

UPDATE public.st_freight_statement
SET status = '草稿'
WHERE status = '待审核';

ALTER TABLE public.st_freight_statement
    DROP CONSTRAINT IF EXISTS chk_freight_stmt_status;

ALTER TABLE public.st_freight_statement
    ADD CONSTRAINT chk_freight_stmt_status
        CHECK (status IN ('草稿', '已审核')) NOT VALID;

ALTER TABLE public.st_freight_statement
    VALIDATE CONSTRAINT chk_freight_stmt_status;

CREATE TABLE public.lg_freight_bill_source_order (
    id bigint PRIMARY KEY,
    freight_bill_id bigint NOT NULL,
    source_sales_order_id bigint NOT NULL,
    source_sales_order_no character varying(64) NOT NULL,
    active_flag boolean NOT NULL DEFAULT TRUE,
    deleted_flag boolean NOT NULL DEFAULT FALSE,
    created_by bigint NOT NULL DEFAULT 0,
    created_name character varying(64) NOT NULL DEFAULT 'system',
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by bigint,
    updated_name character varying(64),
    updated_at timestamp without time zone,
    CONSTRAINT fk_freight_source_order_bill
        FOREIGN KEY (freight_bill_id) REFERENCES public.lg_freight_bill (id),
    CONSTRAINT fk_freight_source_order_sales
        FOREIGN KEY (source_sales_order_id) REFERENCES public.so_sales_order (id),
    CONSTRAINT uk_freight_source_order_pair
        UNIQUE (freight_bill_id, source_sales_order_id)
);

CREATE UNIQUE INDEX uk_freight_source_order_active_sales
    ON public.lg_freight_bill_source_order (source_sales_order_id)
    WHERE active_flag = TRUE;

CREATE INDEX idx_freight_source_order_bill
    ON public.lg_freight_bill_source_order (freight_bill_id)
    WHERE active_flag = TRUE;

INSERT INTO public.lg_freight_bill_source_order (
    id,
    freight_bill_id,
    source_sales_order_id,
    source_sales_order_no,
    active_flag,
    created_by,
    created_name,
    created_at
)
SELECT bill.id,
       bill.id,
       bill.source_sales_order_id,
       sales_order.order_no,
       NOT bill.deleted_flag,
       bill.created_by,
       bill.created_name,
       bill.created_at
FROM public.lg_freight_bill bill
JOIN public.so_sales_order sales_order ON sales_order.id = bill.source_sales_order_id
WHERE bill.source_sales_order_id IS NOT NULL;

ALTER TABLE public.lg_freight_bill_item
    ADD CONSTRAINT fk_lg_freight_bill_item_source_sales_order_item
        FOREIGN KEY (source_sales_order_item_id)
        REFERENCES public.so_sales_order_item (id) NOT VALID;

ALTER TABLE public.lg_freight_bill_item
    VALIDATE CONSTRAINT fk_lg_freight_bill_item_source_sales_order_item;

CREATE INDEX idx_lg_freight_bill_item_source_sales_order_item
    ON public.lg_freight_bill_item (source_sales_order_item_id)
    WHERE source_sales_order_item_id IS NOT NULL;

ALTER TABLE public.lg_freight_bill
    ALTER COLUMN customer_name DROP NOT NULL,
    ALTER COLUMN project_name DROP NOT NULL;

COMMENT ON COLUMN public.lg_freight_bill.customer_name IS
    '已废弃：客户信息按物流明细保存，兼容发布后删除';
COMMENT ON COLUMN public.lg_freight_bill.project_name IS
    '已废弃：项目信息按物流明细保存，兼容发布后删除';
COMMENT ON COLUMN public.st_freight_statement.sign_status IS
    '已废弃：物流对账仅使用审核状态，兼容发布后删除';
COMMENT ON COLUMN public.lg_freight_bill_item.source_sales_order_item_id IS
    '物流明细直接来源销售订单明细标识';
