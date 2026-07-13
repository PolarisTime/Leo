SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

LOCK TABLE public.sys_print_template
    IN SHARE ROW EXCLUSIVE MODE;

LOCK TABLE public.sys_company_setting
    IN SHARE MODE;

DO $$
BEGIN
    IF (SELECT MAX(version::integer)
        FROM public.flyway_seed_history
        WHERE success = true) <> 8
       OR EXISTS (
            SELECT 1 FROM public.flyway_seed_history WHERE success = false
       ) THEN
        RAISE EXCEPTION 'S9: seed Flyway line must stop exactly at S8';
    END IF;
END $$;

CREATE TEMP TABLE approved_settlement_company (
    old_company_id bigint PRIMARY KEY,
    new_company_id bigint NOT NULL UNIQUE,
    company_name text NOT NULL UNIQUE
) ON COMMIT DROP;

INSERT INTO approved_settlement_company
VALUES (
    307698191887761408,
    332284010484989952,
    '嘉兴颖捷建材有限公司'
);

CREATE TEMP TABLE approved_print_template (
    template_id bigint PRIMARY KEY,
    template_code text NOT NULL UNIQUE
) ON COMMIT DROP;

INSERT INTO approved_print_template
VALUES
    (322775358715723776, 'TPL_322775358715723776'),
    (700540000000000024, 'TPL_700540000000000024'),
    (700540000000000026, 'TPL_700540000000000026'),
    (700540000000000028, 'TPL_700540000000000028'),
    (700540000000000029, 'SALES_ORDER_YINGJIE_A4_REMARK_PDF');

DO $$
DECLARE
    approved_orphan_count integer;
    affected_rows integer;
BEGIN
    IF (SELECT COUNT(*) FROM approved_print_template) <> 5
       OR (SELECT COUNT(*)
           FROM public.sys_company_setting target
           JOIN approved_settlement_company company
             ON company.new_company_id = target.id
            AND company.company_name = target.company_name
           WHERE target.status = '正常'
             AND NOT target.deleted_flag) <> 1
       OR (SELECT COUNT(*)
           FROM public.sys_company_setting target
           JOIN approved_settlement_company company
             ON company.company_name = target.company_name
           WHERE target.status = '正常'
             AND NOT target.deleted_flag) <> 1 THEN
        RAISE EXCEPTION 'S9: approved settlement company snapshot drifted';
    END IF;

    SELECT COUNT(*)
    INTO approved_orphan_count
    FROM approved_print_template approved
    JOIN public.sys_print_template target
      ON target.id = approved.template_id
     AND target.template_code = approved.template_code
    CROSS JOIN approved_settlement_company company
    LEFT JOIN public.sys_company_setting current_company
      ON current_company.id = target.settlement_company_id
    WHERE target.settlement_company_id = company.old_company_id
      AND BTRIM(target.settlement_company_name) = BTRIM(company.company_name)
      AND current_company.id IS NULL;

    IF approved_orphan_count NOT IN (0, 5)
       OR EXISTS (
            SELECT 1
            FROM public.sys_print_template target
            LEFT JOIN public.sys_company_setting current_company
              ON current_company.id = target.settlement_company_id
            LEFT JOIN approved_print_template approved
              ON approved.template_id = target.id
             AND approved.template_code = target.template_code
            CROSS JOIN approved_settlement_company company
            WHERE target.settlement_company_id IS NOT NULL
              AND current_company.id IS NULL
              AND (
                  approved.template_id IS NULL
                  OR target.settlement_company_id <> company.old_company_id
                  OR BTRIM(target.settlement_company_name)
                     <> BTRIM(company.company_name)
              )
       ) THEN
        RAISE EXCEPTION 'S9: print template orphan snapshot drifted';
    END IF;

    UPDATE public.sys_print_template target
    SET settlement_company_id = company.new_company_id,
        settlement_company_name = company.company_name,
        updated_by = 0,
        updated_name = 'flyway',
        updated_at = CURRENT_TIMESTAMP
    FROM approved_print_template approved
    CROSS JOIN approved_settlement_company company
    WHERE target.id = approved.template_id
      AND target.template_code = approved.template_code
      AND target.settlement_company_id = company.old_company_id
      AND BTRIM(target.settlement_company_name) = BTRIM(company.company_name)
      AND NOT EXISTS (
          SELECT 1
          FROM public.sys_company_setting current_company
          WHERE current_company.id = target.settlement_company_id
      );

    GET DIAGNOSTICS affected_rows = ROW_COUNT;
    IF affected_rows <> approved_orphan_count THEN
        RAISE EXCEPTION
            'S9: expected to update % print templates, updated %',
            approved_orphan_count,
            affected_rows;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.sys_print_template target
        LEFT JOIN public.sys_company_setting company
          ON company.id = target.settlement_company_id
        WHERE target.settlement_company_id IS NOT NULL
          AND company.id IS NULL
    ) THEN
        RAISE EXCEPTION 'S9: repaired print template identity failed post-validation';
    END IF;
END $$;
