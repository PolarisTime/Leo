ALTER TABLE public.lg_freight_bill
    VALIDATE CONSTRAINT fk_lg_freight_bill_vehicle_carrier_identity,
    VALIDATE CONSTRAINT chk_lg_freight_bill_vehicle_snapshot_pair;
