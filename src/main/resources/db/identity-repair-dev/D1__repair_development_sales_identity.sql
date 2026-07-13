SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

LOCK TABLE public.md_customer,
           public.md_project,
           public.so_sales_order
    IN SHARE ROW EXCLUSIVE MODE;

LOCK TABLE public.so_sales_order_item,
           public.so_sales_outbound,
           public.so_sales_outbound_item,
           public.fm_invoice_issue,
           public.fm_invoice_issue_source_order,
           public.st_customer_statement,
           public.st_customer_statement_item,
           public.fm_receipt,
           public.lg_freight_bill_item
    IN SHARE MODE;

DO $$
BEGIN
    IF current_database() <> 'leo' THEN
        RAISE EXCEPTION 'D1 dev repair: target database must be leo';
    END IF;

    IF pg_is_in_recovery() THEN
        RAISE EXCEPTION 'D1 dev repair: cannot run on a standby';
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
        RAISE EXCEPTION 'D1 dev repair: main Flyway line must stop exactly at V29';
    END IF;
END $$;

CREATE TEMP TABLE approved_customer_project (
    slot text PRIMARY KEY,
    customer_id bigint NOT NULL UNIQUE,
    customer_code text NOT NULL UNIQUE,
    customer_name text NOT NULL UNIQUE,
    project_id bigint NOT NULL UNIQUE,
    project_code text NOT NULL UNIQUE,
    project_name text NOT NULL,
    insert_project boolean NOT NULL
) ON COMMIT DROP;

INSERT INTO approved_customer_project
VALUES
    ('historical',
     333662242446770176,
     'CUS0001',
     '浙江大东吴杭萧绿建科技有限公司',
     CAST('${repairHistoricalProjectId}' AS bigint),
     '${repairHistoricalProjectCode}',
     '苏州欧帝半导体科技有限公司半导体专用设备研发及生产项目',
     true),
    ('development',
     900000000000000001,
     'DEV-CUST-001',
     '开发测试客户',
     900000000000000003,
     'DEV-PROJ-001',
     '开发测试项目',
     false);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM approved_customer_project
        WHERE project_id <= 0
           OR project_code !~ '^[A-Za-z0-9][A-Za-z0-9._/-]{0,63}$'
    ) THEN
        RAISE EXCEPTION 'D1 dev repair: approved project identity is invalid';
    END IF;

    IF (SELECT COUNT(*) FROM public.md_customer) <> 2
       OR EXISTS (
            SELECT 1
            FROM approved_customer_project approved
            LEFT JOIN public.md_customer customer ON customer.id = approved.customer_id
            WHERE customer.id IS NULL
               OR customer.customer_code <> approved.customer_code
               OR BTRIM(customer.customer_name) <> BTRIM(approved.customer_name)
               OR BTRIM(customer.project_name, E' \t\n\r')
                  <> BTRIM(approved.project_name, E' \t\n\r')
               OR customer.status <> '正常'
               OR customer.deleted_flag
               OR customer.default_settlement_company_id IS NULL
       )
       OR EXISTS (
            SELECT 1
            FROM public.md_customer customer
            LEFT JOIN approved_customer_project approved ON approved.customer_id = customer.id
            WHERE approved.customer_id IS NULL
       ) THEN
        RAISE EXCEPTION 'D1 dev repair: customer snapshot drifted from the approved map';
    END IF;

    IF (SELECT COUNT(*) FROM public.md_project) <> 1
       OR NOT EXISTS (
            SELECT 1
            FROM public.md_project project
            JOIN approved_customer_project approved
              ON approved.project_id = project.id
             AND NOT approved.insert_project
            WHERE project.project_code = approved.project_code
              AND BTRIM(project.project_name) = BTRIM(approved.project_name)
              AND project.customer_id = approved.customer_id
              AND project.customer_code = approved.customer_code
              AND project.status = '正常'
              AND NOT project.deleted_flag
       ) THEN
        RAISE EXCEPTION 'D1 dev repair: existing project snapshot drifted from the approved map';
    END IF;
END $$;

DO $$
DECLARE
    candidate_ids bigint[] := ARRAY(
        SELECT project_id FROM approved_customer_project WHERE insert_project
    );
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
            RAISE EXCEPTION 'D1 dev repair: approved project identity collides with existing data';
        END IF;
    END LOOP;
END $$;

CREATE TEMP TABLE approved_order (
    order_id bigint PRIMARY KEY,
    slot text NOT NULL REFERENCES approved_customer_project(slot)
) ON COMMIT DROP;

INSERT INTO approved_order
VALUES
    (333565726478565376, 'development'),
    (333565922142846976, 'development'),
    (333566047812583424, 'development'),
    (333572209182244864, 'development'),
    (333572319848955904, 'development'),
    (333572387167535104, 'development'),
    (333668086718660608, 'historical'),
    (900000000000000301, 'development');

CREATE TEMP TABLE approved_order_map ON COMMIT DROP AS
SELECT approved_order.order_id,
       project.customer_id,
       project.customer_code,
       project.customer_name,
       project.project_id,
       project.project_name
FROM approved_order
JOIN approved_customer_project project ON project.slot = approved_order.slot;

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM approved_order_map) <> 8
       OR (SELECT COUNT(*) FROM public.so_sales_order) <> 8
       OR EXISTS (
            SELECT 1
            FROM public.so_sales_order sales_order
            LEFT JOIN approved_order_map map ON map.order_id = sales_order.id
            WHERE map.order_id IS NULL
       )
       OR EXISTS (
            SELECT 1
            FROM approved_order_map map
            LEFT JOIN public.so_sales_order sales_order ON sales_order.id = map.order_id
            WHERE sales_order.id IS NULL
       )
       OR EXISTS (
            SELECT 1
            FROM public.so_sales_order sales_order
            JOIN approved_order_map map ON map.order_id = sales_order.id
            WHERE BTRIM(sales_order.customer_name) <> BTRIM(map.customer_name)
               OR BTRIM(sales_order.project_name) <> BTRIM(map.project_name)
               OR (NULLIF(BTRIM(sales_order.customer_code), '') IS NOT NULL
                   AND sales_order.customer_code <> map.customer_code)
               OR (sales_order.customer_id IS NOT NULL
                   AND sales_order.customer_id <> map.customer_id)
               OR (sales_order.project_id IS NOT NULL
                   AND sales_order.project_id <> map.project_id)
       ) THEN
        RAISE EXCEPTION 'D1 dev repair: sales order snapshot drifted from the approved map';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.so_sales_outbound_item outbound_item
        LEFT JOIN public.so_sales_order_item source_item
          ON source_item.id = outbound_item.source_sales_order_item_id
        LEFT JOIN public.so_sales_order source_order ON source_order.id = source_item.order_id
        WHERE source_order.id IS NULL
    )
       OR EXISTS (
            SELECT 1
            FROM public.lg_freight_bill_item freight_item
            LEFT JOIN public.so_sales_outbound_item source_item
              ON source_item.id = freight_item.source_sales_outbound_item_id
            WHERE source_item.id IS NULL
       )
       OR EXISTS (
            SELECT 1
            FROM public.fm_invoice_issue_source_order source
            LEFT JOIN public.so_sales_order sales_order ON sales_order.id = source.sales_order_id
            WHERE sales_order.id IS NULL
       )
       OR EXISTS (
            SELECT 1
            FROM public.st_customer_statement_item statement_item
            LEFT JOIN public.so_sales_order_item source_item
              ON source_item.id = statement_item.source_sales_order_item_id
            WHERE source_item.id IS NULL
       ) THEN
        RAISE EXCEPTION 'D1 dev repair: downstream sales identity chain drifted';
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
    SELECT approved.project_id,
           approved.project_code,
           approved.project_name,
           customer.project_name_abbr,
           NULLIF(BTRIM(customer.project_address), ''),
           NULL,
           approved.customer_code,
           customer.status,
           '开发历史身份 repair：由客户与销售订单快照恢复',
           0,
           'flyway-dev-identity-repair',
           CURRENT_TIMESTAMP,
           NULL,
           NULL,
           NULL,
           false,
           approved.customer_id
    FROM approved_customer_project approved
    JOIN public.md_customer customer ON customer.id = approved.customer_id
    WHERE approved.insert_project;

    GET DIAGNOSTICS affected_rows = ROW_COUNT;
    IF affected_rows <> 1 THEN
        RAISE EXCEPTION 'D1 dev repair: expected to insert 1 project, inserted %', affected_rows;
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
    FROM approved_order_map map
    WHERE target.id = map.order_id;

    GET DIAGNOSTICS affected_rows = ROW_COUNT;
    IF affected_rows <> 8 THEN
        RAISE EXCEPTION 'D1 dev repair: expected to update 8 sales orders, updated %', affected_rows;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.so_sales_order sales_order
        JOIN approved_order_map map ON map.order_id = sales_order.id
        LEFT JOIN public.md_project project
          ON project.id = sales_order.project_id
         AND project.customer_id = sales_order.customer_id
        WHERE project.id IS NULL
           OR sales_order.customer_code <> map.customer_code
           OR sales_order.customer_id <> map.customer_id
           OR sales_order.project_id <> map.project_id
    ) THEN
        RAISE EXCEPTION 'D1 dev repair: repaired sales identity failed post-validation';
    END IF;
END $$;
