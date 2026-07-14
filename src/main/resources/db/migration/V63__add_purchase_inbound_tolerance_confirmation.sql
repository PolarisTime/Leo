ALTER TABLE public.po_purchase_inbound_item
    ADD COLUMN tolerance_direction character varying(8),
    ADD COLUMN tolerance_limit_percent numeric(8,4),
    ADD COLUMN tolerance_actual_percent numeric(24,4),
    ADD COLUMN tolerance_reason_code character varying(64),
    ADD COLUMN tolerance_remark character varying(255),
    ADD COLUMN tolerance_confirmed_by bigint,
    ADD COLUMN tolerance_confirmed_name character varying(64),
    ADD COLUMN tolerance_confirmed_at timestamp without time zone;

ALTER TABLE public.po_purchase_inbound_item
    ADD CONSTRAINT chk_purchase_inbound_tolerance_percent
        CHECK (
            (tolerance_limit_percent IS NULL
                OR tolerance_limit_percent BETWEEN 0 AND 100)
            AND (tolerance_actual_percent IS NULL OR tolerance_actual_percent >= 0)
        ),
    ADD CONSTRAINT chk_purchase_inbound_tolerance_confirmation_complete
        CHECK (
            (tolerance_reason_code IS NULL
                AND tolerance_direction IS NULL
                AND tolerance_limit_percent IS NULL
                AND tolerance_actual_percent IS NULL
                AND tolerance_remark IS NULL
                AND tolerance_confirmed_by IS NULL
                AND tolerance_confirmed_name IS NULL
                AND tolerance_confirmed_at IS NULL)
            OR
            (tolerance_reason_code IS NOT NULL
                AND tolerance_direction IS NOT NULL
                AND tolerance_limit_percent IS NOT NULL
                AND tolerance_actual_percent IS NOT NULL
                AND tolerance_confirmed_by IS NOT NULL
                AND tolerance_confirmed_by >= 0
                AND NULLIF(BTRIM(tolerance_confirmed_name), '') IS NOT NULL
                AND tolerance_confirmed_at IS NOT NULL)
        ),
    ADD CONSTRAINT chk_purchase_inbound_tolerance_direction
        CHECK (tolerance_direction IS NULL OR tolerance_direction IN ('上差', '下差')),
    ADD CONSTRAINT chk_purchase_inbound_tolerance_reason
        CHECK (
            tolerance_reason_code IS NULL
            OR tolerance_reason_code IN (
                'TRANSPORT_LOSS',
                'HANDLING_LOSS',
                'MEASUREMENT_DIFFERENCE',
                'SUPPLIER_CONFIRMED',
                'MOISTURE_OR_IMPURITY_CHANGE',
                'THEORETICAL_WEIGHT_DEVIATION',
                'OTHER'
            )
        ),
    ADD CONSTRAINT chk_purchase_inbound_tolerance_other_remark
        CHECK (
            tolerance_reason_code <> 'OTHER'
            OR nullif(btrim(tolerance_remark), '') IS NOT NULL
        );

COMMENT ON COLUMN public.po_purchase_inbound_item.tolerance_reason_code IS
    '过磅超差审核原因代码，仅超出品类容差时写入';
COMMENT ON COLUMN public.po_purchase_inbound_item.tolerance_actual_percent IS
    '审核时实际超差比例快照';
