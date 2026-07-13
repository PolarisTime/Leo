SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

LOCK TABLE public.md_carrier, public.md_vehicle, public.lg_freight_bill
    IN SHARE ROW EXCLUSIVE MODE;

DO $$
BEGIN
    IF current_database() <> '${repairExpectedDatabase}' THEN
        RAISE EXCEPTION 'D2: target database mismatch';
    END IF;

    IF pg_is_in_recovery() THEN
        RAISE EXCEPTION 'D2: identity repair cannot run on a standby';
    END IF;

    IF (SELECT MAX(version::integer)
        FROM public.flyway_schema_history
        WHERE success = true) <> 47
       OR NOT EXISTS (
            SELECT 1 FROM public.flyway_schema_history WHERE success = true
       )
       OR EXISTS (
            SELECT 1 FROM public.flyway_schema_history WHERE success = false
       ) THEN
        RAISE EXCEPTION 'D2: main Flyway line must stop exactly at V47';
    END IF;
END $$;

CREATE TEMP TABLE approved_vehicle (
    vehicle_id bigint PRIMARY KEY,
    carrier_id bigint NOT NULL,
    carrier_code text NOT NULL,
    carrier_name text NOT NULL,
    plate text NOT NULL,
    sort_order integer NOT NULL
) ON COMMIT DROP;

INSERT INTO approved_vehicle
VALUES
    (
        ${repair_vehicle_zhe_a0s829_id}::bigint,
        333230420273405952,
        '333230420273405952',
        '陈永祥',
        '浙A0S829',
        0
    ),
    (
        ${repair_vehicle_zhe_a9v967_id}::bigint,
        333230420273405952,
        '333230420273405952',
        '陈永祥',
        '浙A9V967',
        1
    );

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM approved_vehicle WHERE vehicle_id <= 0)
       OR (SELECT COUNT(DISTINCT vehicle_id) FROM approved_vehicle) <> 2
       OR (SELECT COUNT(DISTINCT plate) FROM approved_vehicle) <> 2 THEN
        RAISE EXCEPTION 'D2: approved vehicle identities are invalid';
    END IF;

    IF (SELECT COUNT(*) FROM public.md_carrier) <> 1
       OR NOT EXISTS (
            SELECT 1
            FROM public.md_carrier carrier
            WHERE carrier.id = 333230420273405952
              AND carrier.carrier_code = '333230420273405952'
              AND carrier.carrier_name = '陈永祥'
              AND carrier.status = '正常'
              AND carrier.deleted_flag = false
              AND carrier.default_settlement_company_id = 332601703884922880
       ) THEN
        RAISE EXCEPTION 'D2: carrier snapshot drifted from the approved repair map';
    END IF;

    IF EXISTS (SELECT 1 FROM public.md_vehicle) THEN
        RAISE EXCEPTION 'D2: md_vehicle is no longer empty; re-audit the repair map';
    END IF;
END $$;

CREATE TEMP TABLE approved_freight_bill (
    bill_id bigint PRIMARY KEY,
    bill_no text NOT NULL,
    carrier_id bigint NOT NULL,
    carrier_code text NOT NULL,
    carrier_name text NOT NULL,
    vehicle_plate text NOT NULL,
    total_weight numeric NOT NULL,
    total_freight numeric NOT NULL,
    settlement_company_id bigint NOT NULL,
    status text NOT NULL,
    deleted_flag boolean NOT NULL,
    vehicle_id bigint
) ON COMMIT DROP;

INSERT INTO approved_freight_bill
VALUES
    (333658934759923712, '333658934759923712', 333230420273405952,
     '333230420273405952', '陈永祥', '浙A0S829', 35.23800000, 1761.90,
     332601703884922880, '已审核', false, NULL),
    (334012134176333824, '334012134176333824', 333230420273405952,
     '333230420273405952', '陈永祥', '浙A9V967', 42.79900000, 1711.96,
     332601703884922880, '已审核', false, NULL),
    (334254260587864064, '334254260587864064', 333230420273405952,
     '333230420273405952', '陈永祥', '浙A0S829', 45.10400000, 1804.16,
     332601703884922880, '已审核', false, NULL),
    (334254367710388224, '334254367710388224', 333230420273405952,
     '333230420273405952', '陈永祥', '浙A9V967', 45.29800000, 1811.92,
     332601703884922880, '已审核', false, NULL);

DO $$
BEGIN
    IF EXISTS (
        (
            SELECT bill_id,
                   bill_no,
                   carrier_id,
                   carrier_code,
                   carrier_name,
                   vehicle_plate,
                   total_weight,
                   total_freight,
                   settlement_company_id,
                   status,
                   deleted_flag,
                   vehicle_id
            FROM approved_freight_bill
            EXCEPT
            SELECT id,
                   bill_no,
                   carrier_id,
                   carrier_code,
                   carrier_name,
                   UPPER(BTRIM(vehicle_plate)),
                   total_weight,
                   total_freight,
                   settlement_company_id,
                   status,
                   deleted_flag,
                   vehicle_id
            FROM public.lg_freight_bill
        )
        UNION ALL
        (
            SELECT id,
                   bill_no,
                   carrier_id,
                   carrier_code,
                   carrier_name,
                   UPPER(BTRIM(vehicle_plate)),
                   total_weight,
                   total_freight,
                   settlement_company_id,
                   status,
                   deleted_flag,
                   vehicle_id
            FROM public.lg_freight_bill
            EXCEPT
            SELECT bill_id,
                   bill_no,
                   carrier_id,
                   carrier_code,
                   carrier_name,
                   vehicle_plate,
                   total_weight,
                   total_freight,
                   settlement_company_id,
                   status,
                   deleted_flag,
                   vehicle_id
            FROM approved_freight_bill
        )
    ) THEN
        RAISE EXCEPTION 'D2: freight bill snapshot drifted from the approved repair set';
    END IF;
END $$;

DO $$
DECLARE
    affected_rows integer;
BEGIN
    INSERT INTO public.md_vehicle (
        id,
        carrier_id,
        plate,
        contact,
        phone,
        remark,
        sort_order,
        created_by,
        created_name,
        created_at,
        updated_by,
        updated_name,
        updated_at,
        deleted_flag
    )
    SELECT vehicle_id,
           carrier_id,
           plate,
           NULL,
           NULL,
           '生产历史身份迁移',
           sort_order,
           0,
           'flyway-identity-repair',
           CURRENT_TIMESTAMP,
           NULL,
           NULL,
           NULL,
           false
    FROM approved_vehicle;

    GET DIAGNOSTICS affected_rows = ROW_COUNT;
    IF affected_rows <> 2 THEN
        RAISE EXCEPTION 'D2: expected to insert 2 vehicles, inserted %', affected_rows;
    END IF;
END $$;

DO $$
DECLARE
    affected_rows integer;
BEGIN
    UPDATE public.lg_freight_bill target
    SET vehicle_id = vehicle.vehicle_id
    FROM approved_vehicle vehicle
    WHERE target.carrier_id = vehicle.carrier_id
      AND UPPER(BTRIM(target.vehicle_plate)) = vehicle.plate;

    GET DIAGNOSTICS affected_rows = ROW_COUNT;
    IF affected_rows <> 4 THEN
        RAISE EXCEPTION 'D2: expected to update 4 freight bills, updated %', affected_rows;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.lg_freight_bill freight_bill
        LEFT JOIN public.md_vehicle vehicle
            ON vehicle.id = freight_bill.vehicle_id
           AND vehicle.carrier_id = freight_bill.carrier_id
        WHERE NULLIF(BTRIM(freight_bill.vehicle_plate), '') IS NOT NULL
          AND (
              vehicle.id IS NULL
              OR UPPER(BTRIM(vehicle.plate)) <> UPPER(BTRIM(freight_bill.vehicle_plate))
          )
    )
       OR EXISTS (
            SELECT 1
            FROM public.lg_freight_bill
            WHERE NULLIF(BTRIM(vehicle_plate), '') IS NULL
              AND vehicle_id IS NOT NULL
       ) THEN
        RAISE EXCEPTION 'D2: repaired vehicle identity failed post-validation';
    END IF;
END $$;
