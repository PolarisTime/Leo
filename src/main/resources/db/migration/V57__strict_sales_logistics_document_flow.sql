ALTER TABLE public.lg_freight_bill
    ADD COLUMN version bigint NOT NULL DEFAULT 0,
    ADD COLUMN source_sales_order_id bigint;

ALTER TABLE public.lg_freight_bill_item
    ADD COLUMN source_sales_order_item_id bigint,
    ALTER COLUMN source_sales_outbound_item_id DROP NOT NULL;

ALTER TABLE public.so_sales_outbound
    ADD COLUMN source_freight_bill_id bigint;

ALTER TABLE public.so_sales_order
    ADD COLUMN sales_mode character varying(16);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.so_sales_order sales_order
        JOIN public.so_sales_order_item item ON item.order_id = sales_order.id
        GROUP BY sales_order.id
        HAVING bool_or(item.source_inbound_item_id IS NOT NULL)
           AND bool_or(item.source_purchase_order_item_id IS NOT NULL)
    ) THEN
        RAISE EXCEPTION 'V57: 历史销售订单同时包含正常销售和预售来源，无法自动推断销售模式';
    END IF;
END $$;

UPDATE public.so_sales_order sales_order
SET sales_mode = CASE
    WHEN EXISTS (
        SELECT 1
        FROM public.so_sales_order_item item
        WHERE item.order_id = sales_order.id
          AND item.source_purchase_order_item_id IS NOT NULL
    ) THEN 'PRESALE'
    ELSE 'NORMAL'
END;

ALTER TABLE public.so_sales_order
    ALTER COLUMN sales_mode SET NOT NULL,
    ADD CONSTRAINT chk_so_sales_order_sales_mode_v57
        CHECK (sales_mode IN ('NORMAL', 'PRESALE')) NOT VALID;

ALTER TABLE public.so_sales_order
    VALIDATE CONSTRAINT chk_so_sales_order_sales_mode_v57;

ALTER TABLE public.so_sales_order_item
    ADD CONSTRAINT chk_so_sales_order_item_source_required_v57
        CHECK (num_nonnulls(source_inbound_item_id, source_purchase_order_item_id) = 1) NOT VALID;

ALTER TABLE public.so_sales_order_item
    VALIDATE CONSTRAINT chk_so_sales_order_item_source_required_v57;

UPDATE public.so_sales_outbound
SET status = '草稿'
WHERE status = '预出库';

ALTER TABLE public.so_sales_outbound
    DROP CONSTRAINT IF EXISTS chk_outbound_status;

ALTER TABLE public.so_sales_outbound
    ADD CONSTRAINT chk_outbound_status_v57
    CHECK (status IN ('草稿', '已审核')) NOT VALID;

ALTER TABLE public.so_sales_outbound
    VALIDATE CONSTRAINT chk_outbound_status_v57;

ALTER TABLE public.so_sales_outbound
    RENAME CONSTRAINT chk_outbound_status_v57 TO chk_outbound_status;

ALTER TABLE public.so_sales_order
    DROP CONSTRAINT IF EXISTS chk_so_status;

ALTER TABLE public.so_sales_order
    ADD CONSTRAINT chk_so_status_v57
    CHECK (status IN ('草稿', '已审核', '待完善', '交付核定', '完成销售')) NOT VALID;

ALTER TABLE public.so_sales_order
    VALIDATE CONSTRAINT chk_so_status_v57;

ALTER TABLE public.so_sales_order
    RENAME CONSTRAINT chk_so_status_v57 TO chk_so_status;

ALTER TABLE public.lg_freight_bill
    ADD CONSTRAINT fk_lg_freight_bill_source_sales_order_v57
    FOREIGN KEY (source_sales_order_id) REFERENCES public.so_sales_order (id) NOT VALID;

ALTER TABLE public.lg_freight_bill
    VALIDATE CONSTRAINT fk_lg_freight_bill_source_sales_order_v57;

ALTER TABLE public.lg_freight_bill_item
    ADD CONSTRAINT fk_lg_freight_bill_item_source_sales_order_item_v57
    FOREIGN KEY (source_sales_order_item_id) REFERENCES public.so_sales_order_item (id) NOT VALID;

ALTER TABLE public.lg_freight_bill_item
    VALIDATE CONSTRAINT fk_lg_freight_bill_item_source_sales_order_item_v57;

ALTER TABLE public.so_sales_outbound
    ADD CONSTRAINT fk_so_sales_outbound_source_freight_bill_v57
    FOREIGN KEY (source_freight_bill_id) REFERENCES public.lg_freight_bill (id) NOT VALID;

ALTER TABLE public.so_sales_outbound
    VALIDATE CONSTRAINT fk_so_sales_outbound_source_freight_bill_v57;

CREATE INDEX idx_lg_freight_bill_source_sales_order
    ON public.lg_freight_bill (source_sales_order_id)
    WHERE source_sales_order_id IS NOT NULL;

CREATE INDEX idx_lg_freight_bill_item_source_sales_order_item
    ON public.lg_freight_bill_item (source_sales_order_item_id)
    WHERE source_sales_order_item_id IS NOT NULL;

CREATE INDEX idx_so_sales_outbound_source_freight_bill
    ON public.so_sales_outbound (source_freight_bill_id)
    WHERE source_freight_bill_id IS NOT NULL;

CREATE UNIQUE INDEX uk_lg_freight_bill_active_sales_order
    ON public.lg_freight_bill (source_sales_order_id)
    WHERE deleted_flag = FALSE AND source_sales_order_id IS NOT NULL;

CREATE UNIQUE INDEX uk_so_sales_outbound_active_freight_bill
    ON public.so_sales_outbound (source_freight_bill_id)
    WHERE deleted_flag = FALSE AND source_freight_bill_id IS NOT NULL;

ALTER TABLE public.lg_freight_bill_item
    ADD CONSTRAINT chk_lg_freight_bill_item_source_xor_v57
    CHECK (num_nonnulls(source_sales_outbound_item_id, source_sales_order_item_id) = 1) NOT VALID;

ALTER TABLE public.lg_freight_bill_item
    VALIDATE CONSTRAINT chk_lg_freight_bill_item_source_xor_v57;
