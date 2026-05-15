INSERT INTO md_project (id, project_code, project_name, project_name_abbr, project_address, customer_code, status, created_by, created_name, created_at, deleted_flag)
SELECT
    c.id,
    c.customer_code || '-PRJ',
    COALESCE(NULLIF(TRIM(c.project_name), ''), c.customer_name),
    c.project_name_abbr,
    c.project_address,
    c.customer_code,
    '正常',
    0,
    'system',
    NOW(),
    FALSE
FROM md_customer c
WHERE c.deleted_flag = FALSE
  AND NOT EXISTS (SELECT 1 FROM md_project p WHERE p.customer_code = c.customer_code);

UPDATE so_sales_order so
SET customer_code = sub.customer_code,
    project_id    = sub.project_id
FROM (
    SELECT DISTINCT so2.id AS sales_order_id, c.customer_code, mp.id AS project_id
    FROM so_sales_order so2
    JOIN md_customer c ON c.customer_name = so2.customer_name AND c.deleted_flag = FALSE
    JOIN md_project mp ON mp.project_name = so2.project_name AND mp.customer_code = c.customer_code
    WHERE so2.deleted_flag = FALSE
      AND (so2.customer_code IS NULL OR so2.project_id IS NULL)
) sub
WHERE so.id = sub.sales_order_id;

UPDATE fm_receipt r
SET customer_code = sub.customer_code,
    project_id    = sub.project_id
FROM (
    SELECT DISTINCT r2.id AS receipt_id, c.customer_code, mp.id AS project_id
    FROM fm_receipt r2
    JOIN md_customer c ON c.customer_name = r2.customer_name AND c.deleted_flag = FALSE
    JOIN md_project mp ON mp.project_name = r2.project_name AND mp.customer_code = c.customer_code
    WHERE r2.deleted_flag = FALSE
      AND (r2.customer_code IS NULL OR r2.project_id IS NULL)
) sub
WHERE r.id = sub.receipt_id;

UPDATE st_customer_statement s
SET customer_code = sub.customer_code,
    project_id    = sub.project_id
FROM (
    SELECT DISTINCT s2.id AS statement_id, c.customer_code, mp.id AS project_id
    FROM st_customer_statement s2
    JOIN md_customer c ON c.customer_name = s2.customer_name AND c.deleted_flag = FALSE
    JOIN md_project mp ON mp.project_name = s2.project_name AND mp.customer_code = c.customer_code
    WHERE s2.deleted_flag = FALSE
      AND (s2.customer_code IS NULL OR s2.project_id IS NULL)
) sub
WHERE s.id = sub.statement_id;

UPDATE st_customer_statement_item si
SET project_id    = s.project_id,
    customer_code = s.customer_code
FROM st_customer_statement s
WHERE si.statement_id = s.id
  AND s.deleted_flag = FALSE
  AND (si.project_id IS NULL OR si.customer_code IS NULL);
