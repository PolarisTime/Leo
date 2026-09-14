-- 销售退货单（反向出库）：明细挂原销售出库明细，可选关联物流单，支持部分退货与超退防护。
-- 纯新增表，不修改 so_sales_order / so_sales_outbound / lg_freight_bill 的原始数据与状态。
-- 退货明细的 source_sales_outbound_item_id 为必填锚点，source_sales_order_item_id 由来源出库明细推导。

CREATE TABLE public.so_sales_return (
    id bigint NOT NULL,
    return_no character varying(64) NOT NULL,
    sales_order_no character varying(256),
    customer_id bigint,
    customer_name character varying(128) NOT NULL,
    project_id bigint,
    project_name character varying(200) NOT NULL,
    warehouse_id bigint,
    warehouse_name character varying(128) NOT NULL,
    settlement_company_id bigint,
    settlement_company_name character varying(128),
    return_date date NOT NULL,
    total_weight numeric(18,8) NOT NULL,
    total_amount numeric(14,2) NOT NULL,
    status character varying(16) NOT NULL,
    remark character varying(255),
    version bigint NOT NULL DEFAULT 0,
    created_by bigint DEFAULT 0 NOT NULL,
    created_name character varying(64) DEFAULT 'system'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by bigint,
    updated_name character varying(64),
    updated_at timestamp without time zone,
    deleted_flag boolean DEFAULT false NOT NULL,
    CONSTRAINT so_sales_return_pkey PRIMARY KEY (id),
    CONSTRAINT uk_so_sales_return_no UNIQUE (return_no),
    CONSTRAINT fk_so_sales_return_customer FOREIGN KEY (customer_id) REFERENCES public.md_customer (id),
    CONSTRAINT fk_so_sales_return_project FOREIGN KEY (project_id) REFERENCES public.md_project (id),
    CONSTRAINT fk_so_sales_return_warehouse FOREIGN KEY (warehouse_id) REFERENCES public.md_warehouse (id),
    CONSTRAINT chk_so_sales_return_status CHECK (status IN ('草稿', '已审核'))
);

CREATE TABLE public.so_sales_return_item (
    id bigint NOT NULL,
    return_id bigint NOT NULL,
    line_no integer NOT NULL,
    material_code character varying(64) NOT NULL,
    material_id bigint,
    brand character varying(64) NOT NULL,
    category character varying(16) NOT NULL,
    material character varying(16) NOT NULL,
    spec character varying(64) NOT NULL,
    length character varying(32),
    unit character varying(8) NOT NULL,
    source_sales_outbound_item_id bigint NOT NULL,
    source_sales_order_item_id bigint,
    source_freight_bill_id bigint,
    settlement_company_id bigint,
    settlement_company_name character varying(128),
    warehouse_name character varying(128),
    warehouse_id bigint,
    batch_no character varying(64),
    batch_no_normalized character varying(64)
        GENERATED ALWAYS AS (NULLIF(BTRIM(batch_no), '')) STORED,
    quantity integer NOT NULL,
    quantity_unit character varying(8) DEFAULT '件'::character varying NOT NULL,
    piece_weight_ton numeric(18,8) NOT NULL,
    pieces_per_bundle integer NOT NULL,
    weight_ton numeric(18,8) NOT NULL,
    unit_price numeric(12,2) NOT NULL,
    amount numeric(14,2) NOT NULL,
    CONSTRAINT so_sales_return_item_pkey PRIMARY KEY (id),
    CONSTRAINT fk_so_sales_return_item_head FOREIGN KEY (return_id) REFERENCES public.so_sales_return (id),
    CONSTRAINT fk_so_sales_return_item_source_outbound_item
        FOREIGN KEY (source_sales_outbound_item_id) REFERENCES public.so_sales_outbound_item (id),
    CONSTRAINT fk_so_sales_return_item_source_order_item
        FOREIGN KEY (source_sales_order_item_id) REFERENCES public.so_sales_order_item (id),
    CONSTRAINT fk_so_sales_return_item_source_freight_bill
        FOREIGN KEY (source_freight_bill_id) REFERENCES public.lg_freight_bill (id),
    CONSTRAINT fk_so_sales_return_item_material FOREIGN KEY (material_id) REFERENCES public.md_material (id),
    CONSTRAINT fk_so_sales_return_item_warehouse FOREIGN KEY (warehouse_id) REFERENCES public.md_warehouse (id),
    CONSTRAINT chk_so_sales_return_item_quantity_non_negative CHECK (quantity >= 0)
);

COMMENT ON TABLE public.so_sales_return IS '销售退货单头：反向出库记录，挂原销售出库明细，可选关联物流单，不改写出库、订单与物流单。';
COMMENT ON COLUMN public.so_sales_return.return_no IS '退货单号，业务唯一，创建时默认取雪花ID。';
COMMENT ON COLUMN public.so_sales_return.sales_order_no IS '来源销售订单号快照。';
COMMENT ON COLUMN public.so_sales_return.status IS '单据状态，允许值：草稿 / 已审核。';

COMMENT ON TABLE public.so_sales_return_item IS '销售退货单明细：记录来源出库明细、物料/仓库/批次快照与退货数量金额。';
COMMENT ON COLUMN public.so_sales_return_item.source_sales_outbound_item_id IS '来源销售出库明细ID，退货数量上限锚点。';
COMMENT ON COLUMN public.so_sales_return_item.source_sales_order_item_id IS '来源销售订单明细ID，由来源出库明细推导，用于订单派生数量聚合。';
COMMENT ON COLUMN public.so_sales_return_item.source_freight_bill_id IS '可选来源物流单ID，用于校验物流单已审核且包含该销售订单。';
COMMENT ON COLUMN public.so_sales_return_item.batch_no_normalized IS '规范化批次号，仅用于稳定库存维度。';

CREATE INDEX idx_so_sales_return_status_date
    ON public.so_sales_return (status, return_date DESC, id DESC) WHERE deleted_flag = false;
CREATE INDEX idx_so_sales_return_customer
    ON public.so_sales_return (customer_id) WHERE deleted_flag = false;
CREATE INDEX idx_so_sales_return_project
    ON public.so_sales_return (project_id) WHERE deleted_flag = false;
CREATE INDEX idx_so_sales_return_item_return ON public.so_sales_return_item (return_id);
CREATE INDEX idx_so_sales_return_item_source_outbound_item
    ON public.so_sales_return_item (source_sales_outbound_item_id);
CREATE INDEX idx_so_sales_return_item_source_order_item
    ON public.so_sales_return_item (source_sales_order_item_id);
