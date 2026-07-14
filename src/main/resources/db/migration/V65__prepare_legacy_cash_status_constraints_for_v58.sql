-- V58 is immutable and changes legacy cash statuses before replacing their
-- old check constraints. Legacy databases with effective cash documents must
-- apply this migration first, then run V58-V64 with Flyway outOfOrder enabled.
-- A clean sequential V1-V65 migration reaches V65 after V58 and safely no-ops.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.flyway_schema_history
        WHERE version = '58'
          AND success
    ) THEN
        RETURN;
    END IF;

    ALTER TABLE public.fm_payment
        DROP CONSTRAINT IF EXISTS chk_payment_status;

    ALTER TABLE public.fm_payment
        ADD CONSTRAINT chk_payment_status
        CHECK (status IN ('草稿', '已付款', '已审核')) NOT VALID;

    ALTER TABLE public.fm_payment
        VALIDATE CONSTRAINT chk_payment_status;

    ALTER TABLE public.fm_receipt
        DROP CONSTRAINT IF EXISTS chk_receipt_status;

    ALTER TABLE public.fm_receipt
        ADD CONSTRAINT chk_receipt_status
        CHECK (status IN ('草稿', '已收款', '已审核')) NOT VALID;

    ALTER TABLE public.fm_receipt
        VALIDATE CONSTRAINT chk_receipt_status;
END $$;
