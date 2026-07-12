ALTER TABLE public.lg_freight_bill
    ADD COLUMN carrier_id bigint,
    ADD COLUMN vehicle_id bigint;

ALTER TABLE public.st_freight_statement
    ADD COLUMN carrier_id bigint;

ALTER TABLE public.st_freight_statement_item
    ADD COLUMN source_freight_bill_id bigint,
    ADD COLUMN source_freight_bill_item_id bigint;

COMMENT ON COLUMN public.lg_freight_bill.carrier_id IS '物流商内部标识，引用 md_carrier(id)';
COMMENT ON COLUMN public.lg_freight_bill.vehicle_id IS '车辆内部标识，引用 md_vehicle(id)';
COMMENT ON COLUMN public.st_freight_statement.carrier_id IS '物流商内部标识，引用 md_carrier(id)';
COMMENT ON COLUMN public.st_freight_statement_item.source_freight_bill_id IS '直接来源物流单标识，引用 lg_freight_bill(id)';
COMMENT ON COLUMN public.st_freight_statement_item.source_freight_bill_item_id IS '直接来源物流单明细标识，引用 lg_freight_bill_item(id)';
