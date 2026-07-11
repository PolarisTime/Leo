ALTER TABLE public.fm_ledger_adjustment
    ADD COLUMN settlement_company_id bigint,
    ADD COLUMN settlement_company_name character varying(128),
    ADD CONSTRAINT chk_fm_ledger_adjustment_settlement_company_pair CHECK (
        (
            settlement_company_id IS NULL
            AND NULLIF(BTRIM(settlement_company_name), '') IS NULL
        )
        OR (
            settlement_company_id IS NOT NULL
            AND NULLIF(BTRIM(settlement_company_name), '') IS NOT NULL
        )
    );

COMMENT ON COLUMN public.fm_ledger_adjustment.settlement_company_id
    IS '结算主体标识快照';
COMMENT ON COLUMN public.fm_ledger_adjustment.settlement_company_name
    IS '结算主体名称快照';

CREATE INDEX idx_fm_ledger_adjustment_settlement_company_date
    ON public.fm_ledger_adjustment (settlement_company_id, adjustment_date DESC)
    WHERE deleted_flag = false;
