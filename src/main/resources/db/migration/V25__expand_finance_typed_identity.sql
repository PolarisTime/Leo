ALTER TABLE public.fm_payment
    ADD COLUMN counterparty_type character varying(32),
    ADD COLUMN counterparty_id bigint;

ALTER TABLE public.fm_payment_allocation
    ADD COLUMN source_supplier_statement_id bigint,
    ADD COLUMN source_freight_statement_id bigint;

ALTER TABLE public.fm_receipt_allocation
    ADD COLUMN source_customer_statement_id bigint;

ALTER TABLE public.fm_ledger_adjustment
    ADD COLUMN counterparty_id bigint;

COMMENT ON COLUMN public.fm_payment.counterparty_type IS '往来方类型：供应商或物流商';
COMMENT ON COLUMN public.fm_payment.counterparty_id IS '按 counterparty_type 指向对应主数据内部标识';
COMMENT ON COLUMN public.fm_payment_allocation.source_supplier_statement_id IS '供应商对账单来源标识，引用 st_supplier_statement(id)';
COMMENT ON COLUMN public.fm_payment_allocation.source_freight_statement_id IS '物流对账单来源标识，引用 st_freight_statement(id)';
COMMENT ON COLUMN public.fm_receipt_allocation.source_customer_statement_id IS '客户对账单来源标识，引用 st_customer_statement(id)';
COMMENT ON COLUMN public.fm_ledger_adjustment.counterparty_id IS '按 counterparty_type 指向对应主数据内部标识';
