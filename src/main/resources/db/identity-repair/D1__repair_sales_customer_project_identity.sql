SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

LOCK TABLE public.md_customer, public.md_project, public.so_sales_order
    IN SHARE ROW EXCLUSIVE MODE;

LOCK TABLE public.so_sales_order_item,
    public.so_sales_outbound,
    public.so_sales_outbound_item,
    public.lg_freight_bill_item
    IN SHARE MODE;

DO $$
BEGIN
    IF current_database() <> '${repairExpectedDatabase}' THEN
        RAISE EXCEPTION 'D1: target database mismatch';
    END IF;

    IF pg_is_in_recovery() THEN
        RAISE EXCEPTION 'D1: identity repair cannot run on a standby';
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
        RAISE EXCEPTION 'D1: main Flyway line must stop exactly at V29';
    END IF;
END $$;

CREATE TEMP TABLE expected_customer_projects (
    customer_id bigint PRIMARY KEY,
    customer_code text NOT NULL,
    customer_name text NOT NULL,
    project_id bigint NOT NULL UNIQUE,
    project_code text NOT NULL UNIQUE,
    project_name text NOT NULL,
    project_name_abbr text,
    project_address text,
    default_settlement_company_id bigint NOT NULL
) ON COMMIT DROP;

INSERT INTO expected_customer_projects (
    customer_id,
    customer_code,
    customer_name,
    project_id,
    project_code,
    project_name,
    project_name_abbr,
    project_address,
    default_settlement_company_id
)
VALUES
    (
        333229770902872064,
        '333229770902872064',
        '浙江景华建设有限公司',
        CAST('${repairProjectId333229770902872064}' AS bigint),
        '${repairProjectCode333229770902872064}',
        '海宁市袁花镇稻米加工中心项目',
        '稻米加工',
        '海宁市袁花镇',
        333230033843789824
    ),
    (
        333230231827521536,
        '333230231827521536',
        '浙江申源建设有限公司',
        CAST('${repairProjectId333230231827521536}' AS bigint),
        '${repairProjectCode333230231827521536}',
        '海宁市红宝热电有限公司等容量改造提升项目',
        '红宝热电',
        '海宁市长安镇',
        332601703884922880
    ),
    (
        333658602151616512,
        '333658602151616512',
        '浙江大东吴杭萧绿建科技有限公司',
        CAST('${repairProjectId333658602151616512}' AS bigint),
        '${repairProjectCode333658602151616512}',
        '苏州欧帝半导体科技有限公司半导体专用设备研发及生产项目',
        '苏州欧帝',
        '苏州市吴中区',
        332601703884922880
    ),
    (
        333922426578542592,
        '333922426578542592',
        '仓库',
        CAST('${repairProjectId333922426578542592}' AS bigint),
        '${repairProjectCode333922426578542592}',
        '仓库',
        '仓库',
        NULL,
        332601703884922880
    );

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM expected_customer_projects
        WHERE project_id <= 0
           OR project_code !~ '^[A-Za-z0-9][A-Za-z0-9._/-]{0,63}$'
    ) THEN
        RAISE EXCEPTION 'D1: approved project identity is invalid';
    END IF;

    IF EXISTS (
        (
            SELECT customer_id,
                   customer_code,
                   customer_name,
                   project_name,
                   project_name_abbr,
                   project_address,
                   default_settlement_company_id,
                   '正常'::text AS status,
                   false AS deleted_flag
            FROM expected_customer_projects
            EXCEPT
            SELECT id,
                   customer_code,
                   customer_name,
                   BTRIM(project_name, E' \t\r\n'),
                   project_name_abbr,
                   NULLIF(BTRIM(project_address), ''),
                   default_settlement_company_id,
                   status,
                   deleted_flag
            FROM public.md_customer
        )
        UNION ALL
        (
            SELECT id,
                   customer_code,
                   customer_name,
                   BTRIM(project_name, E' \t\r\n'),
                   project_name_abbr,
                   NULLIF(BTRIM(project_address), ''),
                   default_settlement_company_id,
                   status,
                   deleted_flag
            FROM public.md_customer
            EXCEPT
            SELECT customer_id,
                   customer_code,
                   customer_name,
                   project_name,
                   project_name_abbr,
                   project_address,
                   default_settlement_company_id,
                   '正常'::text,
                   false
            FROM expected_customer_projects
        )
    ) THEN
        RAISE EXCEPTION 'D1: customer snapshot drifted from the approved repair map';
    END IF;

    IF EXISTS (SELECT 1 FROM public.md_project) THEN
        RAISE EXCEPTION 'D1: md_project is no longer empty; re-audit the repair map';
    END IF;
END $$;

CREATE TEMP TABLE expected_orders (
    order_id bigint PRIMARY KEY,
    order_no text NOT NULL,
    customer_name text NOT NULL,
    project_name text NOT NULL,
    total_weight numeric NOT NULL,
    total_amount numeric NOT NULL,
    settlement_company_id bigint NOT NULL,
    status text NOT NULL,
    deleted_flag boolean NOT NULL,
    version bigint NOT NULL,
    customer_code text,
    customer_id bigint,
    project_id bigint
) ON COMMIT DROP;

INSERT INTO expected_orders
VALUES
    (333658764227911680, '333658764227911680', '浙江大东吴杭萧绿建科技有限公司',
     '苏州欧帝半导体科技有限公司半导体专用设备研发及生产项目',
     35.23800000, 114523.50, 332601703884922880, '完成销售', false, 0, NULL, NULL, NULL),
    (333968132546764800, '333968132546764800', '浙江景华建设有限公司',
     '海宁市袁花镇稻米加工中心项目',
     30.81100000, 97165.04, 333230033843789824, '完成销售', false, 0, NULL, NULL, NULL),
    (333969088940351488, '333969088940351488', '浙江申源建设有限公司',
     '海宁市红宝热电有限公司等容量改造提升项目',
     11.98800000, 37522.44, 332601703884922880, '完成销售', false, 0, NULL, NULL, NULL),
    (334012019617308672, '334012019617308672', '仓库', '仓库',
     5.99400000, 18611.37, 332601703884922880, '完成销售', false, 1, NULL, NULL, NULL),
    (334253683334193152, '334253683334193152', '浙江景华建设有限公司',
     '海宁市袁花镇稻米加工中心项目',
     39.11000000, 121280.96, 333230033843789824, '完成销售', false, 4, NULL, NULL, NULL),
    (334253844672290816, '334253844672290816', '浙江景华建设有限公司',
     '海宁市袁花镇稻米加工中心项目',
     43.24600000, 136271.78, 333230033843789824, '完成销售', false, 4, NULL, NULL, NULL),
    (334254067603742720, '334254067603742720', '浙江景华建设有限公司',
     '海宁市袁花镇稻米加工中心项目',
     2.05200000, 6740.82, 333230033843789824, '完成销售', false, 5, NULL, NULL, NULL);

CREATE TEMP TABLE actual_orders ON COMMIT DROP AS
SELECT id AS order_id,
       order_no::text,
       customer_name::text,
       project_name::text,
       total_weight,
       total_amount,
       settlement_company_id,
       status::text,
       deleted_flag,
       version,
       customer_code::text,
       customer_id,
       project_id
FROM public.so_sales_order;

DO $$
BEGIN
    IF EXISTS (
        (SELECT * FROM expected_orders EXCEPT SELECT * FROM actual_orders)
        UNION ALL
        (SELECT * FROM actual_orders EXCEPT SELECT * FROM expected_orders)
    ) THEN
        RAISE EXCEPTION 'D1: sales order snapshot drifted from the approved repair set';
    END IF;

    IF (SELECT COUNT(*) FROM public.so_sales_order_item) <> 25
       OR (SELECT COUNT(*) FROM public.so_sales_outbound) <> 7
       OR (SELECT COUNT(*) FROM public.so_sales_outbound_item) <> 25
       OR (SELECT COUNT(*) FROM public.lg_freight_bill_item) <> 25
       OR EXISTS (
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
        RAISE EXCEPTION 'D1: downstream sales identity chain drifted';
    END IF;

    IF EXISTS (SELECT 1 FROM public.ct_sales_contract)
       OR EXISTS (SELECT 1 FROM public.fm_invoice_issue)
       OR EXISTS (SELECT 1 FROM public.st_customer_statement)
       OR EXISTS (SELECT 1 FROM public.st_customer_statement_item)
       OR EXISTS (SELECT 1 FROM public.fm_receipt) THEN
        RAISE EXCEPTION 'D1: additional sales documents require a new approved repair map';
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
           '正常',
           '生产历史身份迁移：由客户与销售订单快照恢复',
           0,
           'flyway-identity-repair',
           CURRENT_TIMESTAMP,
           NULL,
           NULL,
           NULL,
           false,
           customer_id
    FROM expected_customer_projects;

    GET DIAGNOSTICS affected_rows = ROW_COUNT;
    IF affected_rows <> 4 THEN
        RAISE EXCEPTION 'D1: expected to insert 4 projects, inserted %', affected_rows;
    END IF;
END $$;

DO $$
DECLARE
    affected_rows integer;
BEGIN
    UPDATE public.so_sales_order target
    SET customer_code = repair.customer_code,
        customer_id = repair.customer_id,
        project_id = repair.project_id
    FROM expected_customer_projects repair
    WHERE target.customer_name = repair.customer_name
      AND target.project_name = repair.project_name;

    GET DIAGNOSTICS affected_rows = ROW_COUNT;
    IF affected_rows <> 7 THEN
        RAISE EXCEPTION 'D1: expected to update 7 sales orders, updated %', affected_rows;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.so_sales_order sales_order
        LEFT JOIN expected_customer_projects repair
            ON repair.customer_id = sales_order.customer_id
           AND repair.project_id = sales_order.project_id
        LEFT JOIN public.md_project project
            ON project.id = sales_order.project_id
           AND project.customer_id = sales_order.customer_id
        WHERE repair.customer_id IS NULL
           OR project.id IS NULL
           OR sales_order.customer_code <> repair.customer_code
           OR sales_order.customer_name <> repair.customer_name
           OR sales_order.project_name <> repair.project_name
    ) THEN
        RAISE EXCEPTION 'D1: repaired sales identity failed post-validation';
    END IF;
END $$;
