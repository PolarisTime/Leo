SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';
SET LOCAL TIME ZONE 'UTC';
SET LOCAL datestyle = 'ISO, YMD';

LOCK TABLE public.md_customer,
           public.md_project,
           public.so_sales_order,
           public.so_sales_order_item,
           public.so_sales_outbound,
           public.so_sales_outbound_item,
           public.lg_freight_bill_item,
           public.ct_sales_contract,
           public.fm_invoice_issue,
           public.st_customer_statement,
           public.st_customer_statement_item,
           public.fm_receipt
    IN SHARE ROW EXCLUSIVE MODE;

DO $$
BEGIN
    IF current_database() <> convert_from(decode('${repairExpectedDatabaseB64}', 'base64'), 'UTF8') THEN
        RAISE EXCEPTION 'D3: target database mismatch';
    END IF;

    IF pg_is_in_recovery() THEN
        RAISE EXCEPTION 'D3: identity repair cannot run on a standby';
    END IF;

    IF to_regprocedure('pg_catalog.sha256(bytea)') IS NULL THEN
        RAISE EXCEPTION 'D3: PostgreSQL SHA-256 function is unavailable';
    END IF;

    IF (SELECT MAX(version::integer)
        FROM public.flyway_schema_history
        WHERE success = true) <> 29
       OR NOT EXISTS (
            SELECT 1 FROM public.flyway_schema_history WHERE success = true
       )
       OR EXISTS (
            SELECT 1 FROM public.flyway_schema_history WHERE success = false
       ) THEN
        RAISE EXCEPTION 'D3: main Flyway line must stop exactly at V29';
    END IF;
END $$;

CREATE TEMP TABLE d3_manifest (doc jsonb NOT NULL) ON COMMIT DROP;

INSERT INTO d3_manifest (doc)
VALUES (convert_from(decode('${repairD3ManifestB64}', 'base64'), 'UTF8')::jsonb);

DO $$
DECLARE
    manifest jsonb := (SELECT doc FROM d3_manifest);
    manifest_digest text;
    declared_source_digest text;
    expected_source_digest text := lower('${repairD3SourceDumpSha256}');
BEGIN
    manifest_digest := encode(
        pg_catalog.sha256(decode('${repairD3ManifestB64}', 'base64')),
        'hex'
    );
    IF lower('${repairD3ManifestSha256}') !~ '^[0-9a-f]{64}$'
       OR lower('${repairD3ManifestSha256}') <> manifest_digest THEN
        RAISE EXCEPTION 'D3: manifest digest mismatch';
    END IF;

    IF manifest->>'schema' <> 'd3-map-v1'
       OR manifest->>'targetDatabase' <> current_database()
       OR manifest->>'snapshotSha256' !~ '^[0-9a-f]{64}$'
       OR expected_source_digest !~ '^[0-9a-f]{64}$' THEN
        RAISE EXCEPTION 'D3: manifest header is invalid';
    END IF;

    declared_source_digest := lower(manifest->>'sourceDumpSha256');
    IF declared_source_digest <> expected_source_digest THEN
        RAISE EXCEPTION 'D3: source dump digest mismatch';
    END IF;

    IF jsonb_typeof(manifest->'idGeneration') <> 'object'
       OR (manifest->'idGeneration'->>'machineId') !~ '^[0-9]+$'
       OR (manifest->'idGeneration'->>'afterEpochMs') !~ '^[0-9]+$'
       OR (manifest->'idGeneration'->>'beforeEpochMs') !~ '^[0-9]+$'
       OR (manifest->'idGeneration'->>'machineId')::bigint NOT BETWEEN 1 AND 1023
       OR (manifest->'idGeneration'->>'afterEpochMs')::bigint
            > (manifest->'idGeneration'->>'beforeEpochMs')::bigint THEN
        RAISE EXCEPTION 'D3: snowflake generation window is invalid';
    END IF;

    IF jsonb_typeof(manifest->'projects') <> 'array'
       OR jsonb_array_length(manifest->'projects') = 0
       OR jsonb_typeof(manifest->'orders') <> 'array' THEN
        RAISE EXCEPTION 'D3: repair map arrays are invalid';
    END IF;
END $$;

CREATE TEMP TABLE d3_project_input (
    slot text PRIMARY KEY,
    customer_id_text text UNIQUE NOT NULL,
    project_id_text text UNIQUE NOT NULL,
    project_code text UNIQUE NOT NULL
) ON COMMIT DROP;

INSERT INTO d3_project_input (slot, customer_id_text, project_id_text, project_code)
SELECT item.slot,
       item.customer_id_text,
       item.project_id_text,
       item.project_code
FROM d3_manifest manifest
CROSS JOIN LATERAL jsonb_to_recordset(manifest.doc->'projects') AS item(
    slot text,
    customer_id_text text,
    project_id_text text,
    project_code text
);

DO $$
DECLARE
    generation_machine_id bigint := ((SELECT doc FROM d3_manifest)->'idGeneration'->>'machineId')::bigint;
    generation_after_ms bigint := ((SELECT doc FROM d3_manifest)->'idGeneration'->>'afterEpochMs')::bigint;
    generation_before_ms bigint := ((SELECT doc FROM d3_manifest)->'idGeneration'->>'beforeEpochMs')::bigint;
BEGIN
    IF EXISTS (
        SELECT 1 FROM d3_project_input
        WHERE slot !~ '^p[0-9]+$'
           OR customer_id_text !~ '^[1-9][0-9]{0,18}$'
           OR project_id_text !~ '^[1-9][0-9]{0,18}$'
           OR project_code !~ '^[A-Za-z0-9][A-Za-z0-9._/-]{0,63}$'
           OR customer_id_text::numeric > power(2::numeric, 63) - 1
           OR project_id_text::numeric > power(2::numeric, 63) - 1
    ) THEN
        RAISE EXCEPTION 'D3: project identity input is invalid';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM d3_project_input input
        WHERE ((input.project_id_text::bigint >> 12) & 1023) <> generation_machine_id
           OR ((input.project_id_text::bigint >> 22) + 1704038400000)
                NOT BETWEEN generation_after_ms AND generation_before_ms
    ) THEN
        RAISE EXCEPTION 'D3: project identity is outside the approved Snowflake window';
    END IF;

    IF (SELECT COUNT(*) FROM d3_project_input)
           <> (SELECT jsonb_array_length(doc->'projects') FROM d3_manifest)
       OR (SELECT COUNT(DISTINCT slot) FROM d3_project_input)
           <> (SELECT COUNT(*) FROM d3_project_input)
       OR (SELECT COUNT(DISTINCT customer_id_text) FROM d3_project_input)
           <> (SELECT COUNT(*) FROM d3_project_input)
       OR (SELECT COUNT(DISTINCT project_id_text) FROM d3_project_input)
           <> (SELECT COUNT(*) FROM d3_project_input) THEN
        RAISE EXCEPTION 'D3: project identity map contains duplicates';
    END IF;
END $$;

CREATE TEMP TABLE d3_project_map ON COMMIT DROP AS
SELECT input.slot,
       input.customer_id_text::bigint AS customer_id,
       input.project_id_text::bigint AS project_id,
       input.project_code,
       customer.customer_code,
       customer.customer_name,
       BTRIM(customer.project_name, E' \t\r\n') AS project_name,
       customer.project_name_abbr,
       NULLIF(BTRIM(customer.project_address), '') AS project_address,
       customer.default_settlement_company_id,
       customer.status AS project_status
FROM d3_project_input input
JOIN public.md_customer customer ON customer.id = input.customer_id_text::bigint;

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM d3_project_map)
           <> (SELECT COUNT(*) FROM d3_project_input)
       OR EXISTS (
            SELECT 1 FROM d3_project_map
            WHERE customer_code IS NULL
               OR customer_name IS NULL
               OR project_name IS NULL
               OR default_settlement_company_id IS NULL
               OR project_status IS NULL
               OR project_name = ''
       ) THEN
        RAISE EXCEPTION 'D3: approved customer/project map is not present in the target snapshot';
    END IF;

    IF EXISTS (SELECT 1 FROM public.md_project) THEN
        RAISE EXCEPTION 'D3: md_project is no longer empty; re-audit the repair map';
    END IF;
END $$;

CREATE TEMP TABLE d3_order_input (
    order_id_text text PRIMARY KEY,
    slot text NOT NULL
) ON COMMIT DROP;

INSERT INTO d3_order_input (order_id_text, slot)
SELECT item.order_id_text, item.slot
FROM d3_manifest manifest
CROSS JOIN LATERAL jsonb_to_recordset(manifest.doc->'orders') AS item(
    order_id_text text,
    slot text
);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM d3_order_input
        WHERE order_id_text !~ '^[1-9][0-9]{0,18}$'
           OR order_id_text::numeric > power(2::numeric, 63) - 1
           OR slot !~ '^p[0-9]+$'
    ) THEN
        RAISE EXCEPTION 'D3: sales order identity input is invalid';
    END IF;

    IF (SELECT COUNT(*) FROM d3_order_input)
           <> (SELECT COUNT(*) FROM public.so_sales_order) THEN
        RAISE EXCEPTION 'D3: approved sales order map does not cover the target snapshot';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM d3_order_input input
        LEFT JOIN d3_project_map project ON project.slot = input.slot
        WHERE project.slot IS NULL
    ) THEN
        RAISE EXCEPTION 'D3: sales order map references an unknown project slot';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.so_sales_order sales_order
        LEFT JOIN d3_order_input input ON input.order_id_text::bigint = sales_order.id
        WHERE input.order_id_text IS NULL
    ) OR EXISTS (
        SELECT 1
        FROM d3_order_input input
        LEFT JOIN public.so_sales_order sales_order
          ON sales_order.id = input.order_id_text::bigint
        WHERE sales_order.id IS NULL
    ) THEN
        RAISE EXCEPTION 'D3: sales order map does not match the target snapshot';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.so_sales_order
        WHERE customer_id IS NOT NULL OR project_id IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'D3: sales orders already contain identity values';
    END IF;

    IF EXISTS (
            SELECT 1
            FROM public.so_sales_outbound_item outbound_item
            LEFT JOIN public.so_sales_order_item source_item
                ON source_item.id = outbound_item.source_sales_order_item_id
            LEFT JOIN public.so_sales_order source_order
                ON source_order.id = source_item.order_id
            WHERE source_order.id IS NULL
       )
       OR EXISTS (
            SELECT 1
            FROM public.lg_freight_bill_item freight_item
            LEFT JOIN public.so_sales_outbound_item source_item
                ON source_item.id = freight_item.source_sales_outbound_item_id
            WHERE source_item.id IS NULL
       ) THEN
        RAISE EXCEPTION 'D3: downstream sales identity chain drifted';
    END IF;

    IF EXISTS (SELECT 1 FROM public.ct_sales_contract)
       OR EXISTS (SELECT 1 FROM public.fm_invoice_issue)
       OR EXISTS (SELECT 1 FROM public.st_customer_statement)
       OR EXISTS (SELECT 1 FROM public.st_customer_statement_item)
       OR EXISTS (SELECT 1 FROM public.fm_receipt) THEN
        RAISE EXCEPTION 'D3: additional sales documents require an approved repair map';
    END IF;
END $$;

CREATE TEMP TABLE d3_order_map ON COMMIT DROP AS
SELECT input.order_id_text::bigint AS order_id,
       project.customer_id,
       project.project_id,
       project.customer_code,
       project.customer_name,
       project.project_name
FROM d3_order_input input
JOIN d3_project_map project ON project.slot = input.slot;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.so_sales_order sales_order
        JOIN d3_order_map map ON map.order_id = sales_order.id
        WHERE BTRIM(sales_order.customer_name) <> BTRIM(map.customer_name)
           OR BTRIM(sales_order.project_name) <> BTRIM(map.project_name)
    ) THEN
        RAISE EXCEPTION 'D3: sales order snapshot does not match its approved project map';
    END IF;
END $$;

DO $$
DECLARE
    candidate_ids bigint[] := ARRAY(SELECT project_id FROM d3_project_map);
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
            RAISE EXCEPTION 'D3: approved project identity collides with an existing identity column';
        END IF;
    END LOOP;
END $$;

DO $$
DECLARE
    actual_digest text;
    snapshot_payload jsonb;
    expected_digest text := lower((SELECT doc->>'snapshotSha256' FROM d3_manifest));
BEGIN
    SELECT jsonb_build_object(
        'schema', 'd3-snapshot-v1',
        'projectMap', COALESCE((
            SELECT jsonb_agg(
                jsonb_build_array(slot, customer_id::text, project_id::text, project_code)
                ORDER BY slot
            ) FROM d3_project_map
        ), '[]'::jsonb),
        'orderMap', COALESCE((
            SELECT jsonb_agg(
                jsonb_build_array(order_id::text, customer_id::text, project_id::text)
                ORDER BY order_id
            ) FROM d3_order_map
        ), '[]'::jsonb),
        'tables', jsonb_build_object(
            'md_customer', COALESCE((SELECT jsonb_agg(to_jsonb(t) ORDER BY t.id)
                                     FROM public.md_customer t), '[]'::jsonb),
            'md_project', COALESCE((SELECT jsonb_agg(to_jsonb(t) ORDER BY t.id)
                                    FROM public.md_project t), '[]'::jsonb),
            'so_sales_order', COALESCE((SELECT jsonb_agg(to_jsonb(t) ORDER BY t.id)
                                        FROM public.so_sales_order t), '[]'::jsonb),
            'so_sales_order_item', COALESCE((SELECT jsonb_agg(to_jsonb(t) ORDER BY t.id)
                                             FROM public.so_sales_order_item t), '[]'::jsonb),
            'so_sales_outbound', COALESCE((SELECT jsonb_agg(to_jsonb(t) ORDER BY t.id)
                                           FROM public.so_sales_outbound t), '[]'::jsonb),
            'so_sales_outbound_item', COALESCE((SELECT jsonb_agg(to_jsonb(t) ORDER BY t.id)
                                                FROM public.so_sales_outbound_item t), '[]'::jsonb),
            'lg_freight_bill_item', COALESCE((SELECT jsonb_agg(to_jsonb(t) ORDER BY t.id)
                                              FROM public.lg_freight_bill_item t), '[]'::jsonb),
            'ct_sales_contract', COALESCE((SELECT jsonb_agg(to_jsonb(t) ORDER BY t.id)
                                           FROM public.ct_sales_contract t), '[]'::jsonb),
            'fm_invoice_issue', COALESCE((SELECT jsonb_agg(to_jsonb(t) ORDER BY t.id)
                                          FROM public.fm_invoice_issue t), '[]'::jsonb),
            'st_customer_statement', COALESCE((SELECT jsonb_agg(to_jsonb(t) ORDER BY t.id)
                                               FROM public.st_customer_statement t), '[]'::jsonb),
            'st_customer_statement_item', COALESCE((SELECT jsonb_agg(to_jsonb(t) ORDER BY t.id)
                                                    FROM public.st_customer_statement_item t), '[]'::jsonb),
            'fm_receipt', COALESCE((SELECT jsonb_agg(to_jsonb(t) ORDER BY t.id)
                                    FROM public.fm_receipt t), '[]'::jsonb)
        )
    ) INTO snapshot_payload;

    actual_digest := encode(
        pg_catalog.sha256(convert_to(snapshot_payload::text, 'UTF8')),
        'hex'
    );
    IF expected_digest !~ '^[0-9a-f]{64}$' OR actual_digest <> expected_digest THEN
        RAISE EXCEPTION 'D3: frozen sales snapshot fingerprint mismatch';
    END IF;
END $$;

DO $$
DECLARE
    affected_rows integer;
BEGIN
    INSERT INTO public.md_project (
        id,
        project_code,
        project_name,
        project_name_abbr,
        project_address,
        project_manager,
        customer_code,
        status,
        remark,
        created_by,
        created_name,
        created_at,
        updated_by,
        updated_name,
        updated_at,
        deleted_flag,
        customer_id
    )
    SELECT project_id,
           project_code,
           project_name,
           project_name_abbr,
           project_address,
           NULL,
           customer_code,
           project_status,
           'identity-repair D3',
           0,
           'flyway-identity-repair',
           CURRENT_TIMESTAMP,
           NULL,
           NULL,
           NULL,
           false,
           customer_id
    FROM d3_project_map;

    GET DIAGNOSTICS affected_rows = ROW_COUNT;
    IF affected_rows <> (SELECT COUNT(*) FROM d3_project_map) THEN
        RAISE EXCEPTION 'D3: project insert count mismatch';
    END IF;
END $$;

DO $$
DECLARE
    affected_rows integer;
BEGIN
    UPDATE public.so_sales_order target
    SET customer_code = map.customer_code,
        customer_id = map.customer_id,
        project_id = map.project_id
    FROM d3_order_map map
    WHERE target.id = map.order_id;

    GET DIAGNOSTICS affected_rows = ROW_COUNT;
    IF affected_rows <> (SELECT COUNT(*) FROM d3_order_map) THEN
        RAISE EXCEPTION 'D3: sales order update count mismatch';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.so_sales_order sales_order
        LEFT JOIN public.md_project project
          ON project.id = sales_order.project_id
         AND project.customer_id = sales_order.customer_id
        LEFT JOIN d3_order_map map ON map.order_id = sales_order.id
        WHERE map.order_id IS NULL
           OR project.id IS NULL
           OR sales_order.customer_id IS NULL
           OR sales_order.project_id IS NULL
           OR BTRIM(sales_order.customer_name) <> BTRIM(map.customer_name)
           OR BTRIM(sales_order.project_name) <> BTRIM(map.project_name)
    ) THEN
        RAISE EXCEPTION 'D3: repaired sales identity failed post-validation';
    END IF;
END $$;
