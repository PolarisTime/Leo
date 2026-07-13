SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';
SET LOCAL TIME ZONE 'UTC';
SET LOCAL datestyle = 'ISO, YMD';

LOCK TABLE public.md_carrier,
           public.md_vehicle,
           public.lg_freight_bill
    IN SHARE ROW EXCLUSIVE MODE;

DO $$
BEGIN
    IF current_database() <> convert_from(decode('${repairExpectedDatabaseB64}', 'base64'), 'UTF8') THEN
        RAISE EXCEPTION 'D4: target database mismatch';
    END IF;

    IF pg_is_in_recovery() THEN
        RAISE EXCEPTION 'D4: identity repair cannot run on a standby';
    END IF;

    IF to_regprocedure('pg_catalog.sha256(bytea)') IS NULL THEN
        RAISE EXCEPTION 'D4: PostgreSQL SHA-256 function is unavailable';
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
        RAISE EXCEPTION 'D4: main Flyway line must stop exactly at V47';
    END IF;
END $$;

CREATE TEMP TABLE d4_manifest (doc jsonb NOT NULL) ON COMMIT DROP;

INSERT INTO d4_manifest (doc)
VALUES (convert_from(decode('${repairD4ManifestB64}', 'base64'), 'UTF8')::jsonb);

DO $$
DECLARE
    manifest jsonb := (SELECT doc FROM d4_manifest);
    manifest_digest text;
    expected_source_digest text := lower('${repairD4SourceDumpSha256}');
BEGIN
    manifest_digest := encode(
        pg_catalog.sha256(decode('${repairD4ManifestB64}', 'base64')),
        'hex'
    );
    IF lower('${repairD4ManifestSha256}') !~ '^[0-9a-f]{64}$'
       OR lower('${repairD4ManifestSha256}') <> manifest_digest THEN
        RAISE EXCEPTION 'D4: manifest digest mismatch';
    END IF;

    IF manifest->>'schema' <> 'd4-map-v1'
       OR manifest->>'targetDatabase' <> current_database()
       OR manifest->>'snapshotSha256' !~ '^[0-9a-f]{64}$'
       OR expected_source_digest !~ '^[0-9a-f]{64}$'
       OR lower(manifest->>'sourceDumpSha256') <> expected_source_digest THEN
        RAISE EXCEPTION 'D4: manifest header is invalid';
    END IF;

    IF jsonb_typeof(manifest->'idGeneration') <> 'object'
       OR (manifest->'idGeneration'->>'machineId') !~ '^[0-9]+$'
       OR (manifest->'idGeneration'->>'afterEpochMs') !~ '^[0-9]+$'
       OR (manifest->'idGeneration'->>'beforeEpochMs') !~ '^[0-9]+$'
       OR (manifest->'idGeneration'->>'machineId')::bigint NOT BETWEEN 1 AND 1023
       OR (manifest->'idGeneration'->>'afterEpochMs')::bigint
            > (manifest->'idGeneration'->>'beforeEpochMs')::bigint THEN
        RAISE EXCEPTION 'D4: snowflake generation window is invalid';
    END IF;

    IF jsonb_typeof(manifest->'vehicles') <> 'array'
       OR jsonb_array_length(manifest->'vehicles') = 0
       OR jsonb_typeof(manifest->'bills') <> 'array'
       OR jsonb_array_length(manifest->'bills') = 0 THEN
        RAISE EXCEPTION 'D4: repair map arrays are invalid';
    END IF;
END $$;

CREATE TEMP TABLE d4_vehicle_input (
    slot text PRIMARY KEY,
    vehicle_id_text text UNIQUE NOT NULL,
    source_bill_id_text text UNIQUE NOT NULL,
    sort_order integer NOT NULL
) ON COMMIT DROP;

INSERT INTO d4_vehicle_input (slot, vehicle_id_text, source_bill_id_text, sort_order)
SELECT item.slot,
       item.vehicle_id_text,
       item.source_bill_id_text,
       item.sort_order
FROM d4_manifest manifest
CROSS JOIN LATERAL jsonb_to_recordset(manifest.doc->'vehicles') AS item(
    slot text,
    vehicle_id_text text,
    source_bill_id_text text,
    sort_order integer
);

CREATE TEMP TABLE d4_bill_input (
    bill_id_text text PRIMARY KEY,
    vehicle_slot text NOT NULL
) ON COMMIT DROP;

INSERT INTO d4_bill_input (bill_id_text, vehicle_slot)
SELECT item.bill_id_text,
       item.vehicle_slot
FROM d4_manifest manifest
CROSS JOIN LATERAL jsonb_to_recordset(manifest.doc->'bills') AS item(
    bill_id_text text,
    vehicle_slot text
);

DO $$
DECLARE
    generation_machine_id bigint := ((SELECT doc FROM d4_manifest)->'idGeneration'->>'machineId')::bigint;
    generation_after_ms bigint := ((SELECT doc FROM d4_manifest)->'idGeneration'->>'afterEpochMs')::bigint;
    generation_before_ms bigint := ((SELECT doc FROM d4_manifest)->'idGeneration'->>'beforeEpochMs')::bigint;
BEGIN
    IF EXISTS (
        SELECT 1 FROM d4_vehicle_input
        WHERE slot !~ '^v[0-9]+$'
           OR vehicle_id_text !~ '^[1-9][0-9]{0,18}$'
           OR source_bill_id_text !~ '^[1-9][0-9]{0,18}$'
           OR vehicle_id_text::numeric > power(2::numeric, 63) - 1
           OR source_bill_id_text::numeric > power(2::numeric, 63) - 1
           OR sort_order < 0
    ) OR EXISTS (
        SELECT 1 FROM d4_bill_input
        WHERE bill_id_text !~ '^[1-9][0-9]{0,18}$'
           OR bill_id_text::numeric > power(2::numeric, 63) - 1
           OR vehicle_slot !~ '^v[0-9]+$'
    ) THEN
        RAISE EXCEPTION 'D4: vehicle identity input is invalid';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM d4_vehicle_input input
        WHERE ((input.vehicle_id_text::bigint >> 12) & 1023) <> generation_machine_id
           OR ((input.vehicle_id_text::bigint >> 22) + 1704038400000)
                NOT BETWEEN generation_after_ms AND generation_before_ms
    ) THEN
        RAISE EXCEPTION 'D4: vehicle identity is outside the approved Snowflake window';
    END IF;

    IF (SELECT COUNT(DISTINCT slot) FROM d4_vehicle_input)
           <> (SELECT COUNT(*) FROM d4_vehicle_input)
       OR (SELECT COUNT(DISTINCT vehicle_id_text) FROM d4_vehicle_input)
           <> (SELECT COUNT(*) FROM d4_vehicle_input)
       OR (SELECT COUNT(DISTINCT source_bill_id_text) FROM d4_vehicle_input)
           <> (SELECT COUNT(*) FROM d4_vehicle_input)
       OR EXISTS (
            SELECT 1 FROM d4_bill_input bill
            LEFT JOIN d4_vehicle_input vehicle ON vehicle.slot = bill.vehicle_slot
            WHERE vehicle.slot IS NULL
       ) THEN
        RAISE EXCEPTION 'D4: vehicle identity map contains duplicates or unknown slots';
    END IF;

    IF (SELECT COUNT(*) FROM d4_bill_input)
           <> (SELECT COUNT(*) FROM public.lg_freight_bill
               WHERE NULLIF(BTRIM(vehicle_plate), '') IS NOT NULL) THEN
        RAISE EXCEPTION 'D4: approved freight bill map does not cover the target snapshot';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.lg_freight_bill bill
        LEFT JOIN d4_bill_input input ON input.bill_id_text::bigint = bill.id
        WHERE NULLIF(BTRIM(bill.vehicle_plate), '') IS NOT NULL
          AND input.bill_id_text IS NULL
    ) OR EXISTS (
        SELECT 1
        FROM d4_bill_input input
        LEFT JOIN public.lg_freight_bill bill ON bill.id = input.bill_id_text::bigint
        WHERE bill.id IS NULL
    ) THEN
        RAISE EXCEPTION 'D4: freight bill map does not match the target snapshot';
    END IF;

    IF EXISTS (SELECT 1 FROM public.md_vehicle)
       OR EXISTS (
            SELECT 1 FROM public.lg_freight_bill
            WHERE vehicle_id IS NOT NULL
              AND NULLIF(BTRIM(vehicle_plate), '') IS NOT NULL
       ) THEN
        RAISE EXCEPTION 'D4: freight vehicle identities are already populated';
    END IF;
END $$;

CREATE TEMP TABLE d4_vehicle_map ON COMMIT DROP AS
SELECT vehicle.slot,
       vehicle.vehicle_id_text::bigint AS vehicle_id,
       vehicle.source_bill_id_text::bigint AS source_bill_id,
       vehicle.sort_order,
       bill.carrier_id,
       UPPER(BTRIM(bill.vehicle_plate)) AS plate,
       carrier.carrier_code,
       carrier.carrier_name
FROM d4_vehicle_input vehicle
JOIN public.lg_freight_bill bill ON bill.id = vehicle.source_bill_id_text::bigint
JOIN public.md_carrier carrier ON carrier.id = bill.carrier_id;

CREATE TEMP TABLE d4_bill_map ON COMMIT DROP AS
SELECT input.bill_id_text::bigint AS bill_id,
       vehicle.vehicle_id,
       vehicle.carrier_id,
       vehicle.plate
FROM d4_bill_input input
JOIN d4_vehicle_map vehicle ON vehicle.slot = input.vehicle_slot;

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM d4_vehicle_map)
           <> (SELECT COUNT(*) FROM d4_vehicle_input)
       OR (SELECT COUNT(*) FROM d4_bill_map)
           <> (SELECT COUNT(*) FROM d4_bill_input)
       OR EXISTS (
            SELECT 1
            FROM d4_vehicle_map vehicle
            WHERE vehicle.plate = ''
               OR vehicle.carrier_id IS NULL
               OR vehicle.carrier_code IS NULL
               OR vehicle.carrier_name IS NULL
       )
       OR EXISTS (
            SELECT 1
            FROM public.lg_freight_bill bill
            JOIN d4_bill_map map ON map.bill_id = bill.id
            WHERE bill.carrier_id <> map.carrier_id
               OR UPPER(BTRIM(bill.vehicle_plate)) <> map.plate
               OR bill.vehicle_id IS NOT NULL
       )
       OR EXISTS (
            SELECT 1 FROM d4_vehicle_map first_map
            JOIN d4_vehicle_map second_map
              ON first_map.vehicle_id <> second_map.vehicle_id
             AND first_map.carrier_id = second_map.carrier_id
             AND first_map.plate = second_map.plate
       ) THEN
        RAISE EXCEPTION 'D4: approved vehicle map does not match the target snapshot';
    END IF;
END $$;

DO $$
DECLARE
    candidate_ids bigint[] := ARRAY(SELECT vehicle_id FROM d4_vehicle_map);
    relation record;
    collision_found boolean;
BEGIN
    FOR relation IN
        SELECT namespace.nspname,
               class.relname,
               attribute.attname
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
            relation.nspname,
            relation.relname,
            relation.attname
        ) INTO collision_found USING candidate_ids;
        IF collision_found THEN
            RAISE EXCEPTION 'D4: approved vehicle identity collides with an existing identity column';
        END IF;
    END LOOP;
END $$;

DO $$
DECLARE
    actual_digest text;
    snapshot_payload jsonb;
    expected_digest text := lower((SELECT doc->>'snapshotSha256' FROM d4_manifest));
BEGIN
    SELECT jsonb_build_object(
        'schema', 'd4-snapshot-v1',
        'vehicleMap', COALESCE((
            SELECT jsonb_agg(
                jsonb_build_array(slot, vehicle_id::text, source_bill_id::text,
                                  carrier_id::text, plate, sort_order)
                ORDER BY slot
            ) FROM d4_vehicle_map
        ), '[]'::jsonb),
        'billMap', COALESCE((
            SELECT jsonb_agg(
                jsonb_build_array(bill_id::text, vehicle_id::text)
                ORDER BY bill_id
            ) FROM d4_bill_map
        ), '[]'::jsonb),
        'tables', jsonb_build_object(
            'md_carrier', COALESCE((SELECT jsonb_agg(to_jsonb(t) ORDER BY t.id)
                                    FROM public.md_carrier t), '[]'::jsonb),
            'md_vehicle', COALESCE((SELECT jsonb_agg(to_jsonb(t) ORDER BY t.id)
                                    FROM public.md_vehicle t), '[]'::jsonb),
            'lg_freight_bill', COALESCE((SELECT jsonb_agg(to_jsonb(t) ORDER BY t.id)
                                         FROM public.lg_freight_bill t), '[]'::jsonb)
        )
    ) INTO snapshot_payload;

    actual_digest := encode(
        pg_catalog.sha256(convert_to(snapshot_payload::text, 'UTF8')),
        'hex'
    );
    IF expected_digest !~ '^[0-9a-f]{64}$' OR actual_digest <> expected_digest THEN
        RAISE EXCEPTION 'D4: frozen freight snapshot fingerprint mismatch';
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
           'identity-repair D4',
           sort_order,
           0,
           'flyway-identity-repair',
           CURRENT_TIMESTAMP,
           NULL,
           NULL,
           NULL,
           false
    FROM d4_vehicle_map;

    GET DIAGNOSTICS affected_rows = ROW_COUNT;
    IF affected_rows <> (SELECT COUNT(*) FROM d4_vehicle_map) THEN
        RAISE EXCEPTION 'D4: vehicle insert count mismatch';
    END IF;
END $$;

DO $$
DECLARE
    affected_rows integer;
BEGIN
    UPDATE public.lg_freight_bill target
    SET vehicle_id = map.vehicle_id
    FROM d4_bill_map map
    WHERE target.id = map.bill_id;

    GET DIAGNOSTICS affected_rows = ROW_COUNT;
    IF affected_rows <> (SELECT COUNT(*) FROM d4_bill_map) THEN
        RAISE EXCEPTION 'D4: freight bill update count mismatch';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.lg_freight_bill bill
        LEFT JOIN public.md_vehicle vehicle
          ON vehicle.id = bill.vehicle_id
         AND vehicle.carrier_id = bill.carrier_id
        WHERE NULLIF(BTRIM(bill.vehicle_plate), '') IS NOT NULL
          AND (
              vehicle.id IS NULL
              OR UPPER(BTRIM(vehicle.plate)) <> UPPER(BTRIM(bill.vehicle_plate))
          )
    ) OR EXISTS (
        SELECT 1
        FROM public.lg_freight_bill
        WHERE NULLIF(BTRIM(vehicle_plate), '') IS NULL
          AND vehicle_id IS NOT NULL
    ) OR EXISTS (
        SELECT 1
        FROM d4_bill_map map
        LEFT JOIN public.lg_freight_bill bill ON bill.id = map.bill_id
        WHERE bill.vehicle_id <> map.vehicle_id
    ) THEN
        RAISE EXCEPTION 'D4: repaired vehicle identity failed post-validation';
    END IF;
END $$;
