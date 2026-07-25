-- A plate may be captured before the vehicle is registered. A selected vehicle
-- must still retain its plate snapshot for audit and display purposes.
ALTER TABLE public.lg_freight_bill
    DROP CONSTRAINT chk_lg_freight_bill_vehicle_snapshot_pair,
    ADD CONSTRAINT chk_lg_freight_bill_vehicle_snapshot_pair
        CHECK (
            vehicle_id IS NULL
                OR NULLIF(BTRIM(vehicle_plate), '') IS NOT NULL
        ) NOT VALID;

ALTER TABLE public.lg_freight_bill
    VALIDATE CONSTRAINT chk_lg_freight_bill_vehicle_snapshot_pair;

COMMENT ON CONSTRAINT chk_lg_freight_bill_vehicle_snapshot_pair
    ON public.lg_freight_bill
    IS '允许仅保留未建档车牌；已关联车辆时必须保留车牌快照';
