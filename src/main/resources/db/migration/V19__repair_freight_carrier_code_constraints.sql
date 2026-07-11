DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_lg_freight_bill_carrier_code_not_blank'
          AND conrelid = 'public.lg_freight_bill'::regclass
    ) THEN
        ALTER TABLE public.lg_freight_bill
            ADD CONSTRAINT chk_lg_freight_bill_carrier_code_not_blank
            CHECK (BTRIM(carrier_code) <> '');
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_st_freight_statement_carrier_code_not_blank'
          AND conrelid = 'public.st_freight_statement'::regclass
    ) THEN
        ALTER TABLE public.st_freight_statement
            ADD CONSTRAINT chk_st_freight_statement_carrier_code_not_blank
            CHECK (BTRIM(carrier_code) <> '');
    END IF;
END
$$;
