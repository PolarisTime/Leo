SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

LOCK TABLE public.md_carrier,
           public.md_vehicle,
           public.lg_freight_bill
    IN SHARE ROW EXCLUSIVE MODE;

DO $$
BEGIN
    IF current_database() <> 'leo' THEN
        RAISE EXCEPTION 'D2 dev repair: target database must be leo';
    END IF;

    IF pg_is_in_recovery() THEN
        RAISE EXCEPTION 'D2 dev repair: cannot run on a standby';
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
        RAISE EXCEPTION 'D2 dev repair: main Flyway line must stop exactly at V47';
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
VALUES (
    CAST('${repairVehicleId}' AS bigint),
    332300467059032064,
    'CAR0001',
    '测试物流',
    '浙A12345',
    0
);

CREATE TEMP TABLE approved_bill (
    bill_id bigint PRIMARY KEY,
    vehicle_id bigint NOT NULL REFERENCES approved_vehicle(vehicle_id)
) ON COMMIT DROP;

INSERT INTO approved_bill
VALUES (333737053147627520, CAST('${repairVehicleId}' AS bigint));

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM approved_vehicle
        WHERE vehicle_id <= 0 OR plate = '' OR sort_order < 0
    ) THEN
        RAISE EXCEPTION 'D2 dev repair: approved vehicle identity is invalid';
    END IF;

    IF (SELECT COUNT(*) FROM public.md_carrier) <> 1
       OR NOT EXISTS (
            SELECT 1
            FROM public.md_carrier carrier
            JOIN approved_vehicle approved ON approved.carrier_id = carrier.id
            WHERE carrier.carrier_code = approved.carrier_code
              AND carrier.carrier_name = approved.carrier_name
              AND carrier.status = '正常'
              AND NOT carrier.deleted_flag
       )
       OR EXISTS (SELECT 1 FROM public.md_vehicle) THEN
        RAISE EXCEPTION 'D2 dev repair: carrier or vehicle snapshot drifted from the approved map';
    END IF;

    IF (SELECT COUNT(*) FROM public.lg_freight_bill
        WHERE NULLIF(BTRIM(vehicle_plate), '') IS NOT NULL) <> 1
       OR NOT EXISTS (
            SELECT 1
            FROM public.lg_freight_bill bill
            JOIN approved_bill map ON map.bill_id = bill.id
            JOIN approved_vehicle vehicle ON vehicle.vehicle_id = map.vehicle_id
            WHERE bill.carrier_id = vehicle.carrier_id
              AND bill.carrier_code = vehicle.carrier_code
              AND bill.carrier_name = vehicle.carrier_name
              AND UPPER(BTRIM(bill.vehicle_plate)) = UPPER(vehicle.plate)
              AND bill.vehicle_id IS NULL
       )
       OR EXISTS (
            SELECT 1
            FROM public.lg_freight_bill bill
            LEFT JOIN approved_bill map ON map.bill_id = bill.id
            WHERE NULLIF(BTRIM(bill.vehicle_plate), '') IS NOT NULL
              AND map.bill_id IS NULL
       )
       OR EXISTS (
            SELECT 1
            FROM public.lg_freight_bill
            WHERE NULLIF(BTRIM(vehicle_plate), '') IS NULL
              AND vehicle_id IS NOT NULL
       ) THEN
        RAISE EXCEPTION 'D2 dev repair: freight bill snapshot drifted from the approved map';
    END IF;
END $$;

DO $$
DECLARE
    candidate_ids bigint[] := ARRAY(SELECT vehicle_id FROM approved_vehicle);
    relation record;
    collision_found boolean;
BEGIN
    FOR relation IN
        SELECT namespace.nspname AS schema_name,
               class.relname AS table_name,
               attribute.attname AS column_name
        FROM pg_class class
        JOIN pg_namespace namespace ON namespace.oid = class.relnamespace
        JOIN pg_attribute attribute ON attribute.attrelid = class.oid
        WHERE class.relkind IN ('r', 'p')
          AND namespace.nspname = 'public'
          AND attribute.attnum > 0
          AND NOT attribute.attisdropped
          AND attribute.atttypid = 'int8'::regtype
          AND (attribute.attname = 'id' OR right(attribute.attname, 3) = '_id')
    LOOP
        EXECUTE format(
            'SELECT EXISTS (SELECT 1 FROM %I.%I WHERE %I = ANY ($1))',
            relation.schema_name,
            relation.table_name,
            relation.column_name
        ) INTO collision_found USING candidate_ids;
        IF collision_found THEN
            RAISE EXCEPTION 'D2 dev repair: approved vehicle identity collides with existing data';
        END IF;
    END LOOP;
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
           '开发历史身份 repair',
           sort_order,
           0,
           'flyway-dev-identity-repair',
           CURRENT_TIMESTAMP,
           NULL,
           NULL,
           NULL,
           false
    FROM approved_vehicle;

    GET DIAGNOSTICS affected_rows = ROW_COUNT;
    IF affected_rows <> 1 THEN
        RAISE EXCEPTION 'D2 dev repair: expected to insert 1 vehicle, inserted %', affected_rows;
    END IF;
END $$;

DO $$
DECLARE
    affected_rows integer;
BEGIN
    UPDATE public.lg_freight_bill target
    SET vehicle_id = map.vehicle_id
    FROM approved_bill map
    WHERE target.id = map.bill_id;

    GET DIAGNOSTICS affected_rows = ROW_COUNT;
    IF affected_rows <> 1 THEN
        RAISE EXCEPTION 'D2 dev repair: expected to update 1 freight bill, updated %', affected_rows;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.lg_freight_bill bill
        JOIN approved_bill map ON map.bill_id = bill.id
        LEFT JOIN public.md_vehicle vehicle
          ON vehicle.id = bill.vehicle_id
         AND vehicle.carrier_id = bill.carrier_id
        WHERE vehicle.id IS NULL
           OR UPPER(BTRIM(vehicle.plate)) <> UPPER(BTRIM(bill.vehicle_plate))
           OR bill.vehicle_id <> map.vehicle_id
    ) THEN
        RAISE EXCEPTION 'D2 dev repair: repaired vehicle identity failed post-validation';
    END IF;
END $$;
