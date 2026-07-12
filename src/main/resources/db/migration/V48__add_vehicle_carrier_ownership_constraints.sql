-- Enforce that a selected vehicle belongs to the freight bill carrier.
CREATE UNIQUE INDEX uk_md_vehicle_id_carrier_identity
    ON public.md_vehicle (id, carrier_id);

CREATE INDEX idx_lg_freight_bill_vehicle_carrier_identity
    ON public.lg_freight_bill (vehicle_id, carrier_id);

ALTER TABLE public.lg_freight_bill
    ADD CONSTRAINT fk_lg_freight_bill_vehicle_carrier_identity
        FOREIGN KEY (vehicle_id, carrier_id)
        REFERENCES public.md_vehicle (id, carrier_id)
        ON DELETE RESTRICT NOT VALID,
    ADD CONSTRAINT chk_lg_freight_bill_vehicle_snapshot_pair
        CHECK (
            (vehicle_id IS NULL)
                = (NULLIF(BTRIM(vehicle_plate), '') IS NULL)
        ) NOT VALID;
