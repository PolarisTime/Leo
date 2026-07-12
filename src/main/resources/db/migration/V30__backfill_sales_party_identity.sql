DO $$
BEGIN
    IF EXISTS (
        SELECT customer_code FROM public.md_customer GROUP BY customer_code HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V30: md_customer 全历史 customer_code 不唯一';
    END IF;

    IF EXISTS (
        SELECT BTRIM(customer_name) FROM public.md_customer
        GROUP BY BTRIM(customer_name) HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V30: 仍依赖客户名称回填的历史数据存在同名客户，需先人工清洗';
    END IF;

    IF EXISTS (
        SELECT customer_id, BTRIM(project_name)
        FROM public.md_project
        GROUP BY customer_id, BTRIM(project_name)
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V30: 同一客户范围内存在同名项目，需先人工清洗';
    END IF;
END $$;

UPDATE public.so_sales_order target
SET customer_id = customer.id
FROM public.md_customer customer
WHERE target.customer_id IS NULL
  AND customer.customer_code = target.customer_code;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.so_sales_order sales_order
        LEFT JOIN public.md_project project ON project.id = sales_order.project_id
        WHERE sales_order.project_id IS NOT NULL
          AND (project.id IS NULL
               OR project.customer_id <> sales_order.customer_id
               OR BTRIM(project.project_name) <> BTRIM(sales_order.project_name))
    ) THEN
        RAISE EXCEPTION 'V30: 销售订单存在历史项目错绑、跨客户或快照冲突';
    END IF;
END $$;

UPDATE public.so_sales_order target
SET project_id = project.id
FROM public.md_project project
WHERE target.project_id IS NULL
  AND project.customer_id = target.customer_id
  AND BTRIM(project.project_name) = BTRIM(target.project_name);

UPDATE public.ct_sales_contract target
SET customer_id = customer.id,
    customer_code = customer.customer_code
FROM public.md_customer customer
WHERE target.customer_id IS NULL
  AND BTRIM(customer.customer_name) = BTRIM(target.customer_name);

UPDATE public.ct_sales_contract target
SET project_id = project.id
FROM public.md_project project
WHERE target.project_id IS NULL
  AND project.customer_id = target.customer_id
  AND BTRIM(project.project_name) = BTRIM(target.project_name);

WITH source_identity AS (
    SELECT outbound_item.outbound_id AS document_id,
           MIN(source_order.customer_id) AS customer_id,
           MIN(source_order.project_id) AS project_id
    FROM public.so_sales_outbound_item outbound_item
    JOIN public.so_sales_order_item source_item ON source_item.id = outbound_item.source_sales_order_item_id
    JOIN public.so_sales_order source_order ON source_order.id = source_item.order_id
    GROUP BY outbound_item.outbound_id
    HAVING COUNT(DISTINCT source_order.customer_id) = 1
       AND COUNT(DISTINCT source_order.project_id) = 1
)
UPDATE public.so_sales_outbound target
SET customer_id = source_identity.customer_id,
    project_id = source_identity.project_id
FROM source_identity
WHERE target.id = source_identity.document_id
  AND (target.customer_id IS NULL OR target.project_id IS NULL);

UPDATE public.so_sales_outbound target
SET customer_id = customer.id
FROM public.md_customer customer
WHERE target.customer_id IS NULL
  AND BTRIM(customer.customer_name) = BTRIM(target.customer_name);

UPDATE public.so_sales_outbound target
SET project_id = project.id
FROM public.md_project project
WHERE target.project_id IS NULL
  AND project.customer_id = target.customer_id
  AND BTRIM(project.project_name) = BTRIM(target.project_name);

WITH source_identity AS (
    SELECT bridge.issue_id AS document_id,
           MIN(source.customer_id) AS customer_id,
           MIN(source.project_id) AS project_id
    FROM public.fm_invoice_issue_source_order bridge
    JOIN public.so_sales_order source ON source.id = bridge.sales_order_id
    GROUP BY bridge.issue_id
    HAVING COUNT(DISTINCT source.customer_id) = 1
       AND COUNT(DISTINCT source.project_id) = 1
)
UPDATE public.fm_invoice_issue target
SET customer_id = source_identity.customer_id,
    project_id = source_identity.project_id
FROM source_identity
WHERE target.id = source_identity.document_id
  AND (target.customer_id IS NULL OR target.project_id IS NULL);

UPDATE public.fm_invoice_issue target
SET customer_id = customer.id
FROM public.md_customer customer
WHERE target.customer_id IS NULL
  AND BTRIM(customer.customer_name) = BTRIM(target.customer_name);

UPDATE public.fm_invoice_issue target
SET project_id = project.id
FROM public.md_project project
WHERE target.project_id IS NULL
  AND project.customer_id = target.customer_id
  AND BTRIM(project.project_name) = BTRIM(target.project_name);

UPDATE public.st_customer_statement_item statement_item
SET customer_id = source_order.customer_id,
    project_id = COALESCE(statement_item.project_id, source_order.project_id),
    customer_code = COALESCE(statement_item.customer_code, source_order.customer_code)
FROM public.so_sales_order_item source_item
JOIN public.so_sales_order source_order ON source_order.id = source_item.order_id
WHERE source_item.id = statement_item.source_sales_order_item_id;

WITH source_identity AS (
    SELECT statement_id AS document_id,
           MIN(customer_id) AS customer_id,
           MIN(project_id) AS project_id
    FROM public.st_customer_statement_item
    GROUP BY statement_id
    HAVING COUNT(DISTINCT customer_id) = 1
       AND COUNT(DISTINCT project_id) = 1
)
UPDATE public.st_customer_statement target
SET customer_id = source_identity.customer_id,
    project_id = COALESCE(target.project_id, source_identity.project_id)
FROM source_identity
WHERE target.id = source_identity.document_id
  AND (target.customer_id IS NULL OR target.project_id IS NULL);

UPDATE public.st_customer_statement target
SET customer_id = customer.id
FROM public.md_customer customer
WHERE target.customer_id IS NULL
  AND (customer.customer_code = target.customer_code
       OR (NULLIF(BTRIM(target.customer_code), '') IS NULL
           AND BTRIM(customer.customer_name) = BTRIM(target.customer_name)));

UPDATE public.st_customer_statement target
SET project_id = project.id
FROM public.md_project project
WHERE target.project_id IS NULL
  AND project.customer_id = target.customer_id
  AND BTRIM(project.project_name) = BTRIM(target.project_name);

UPDATE public.fm_receipt target
SET customer_id = source.customer_id,
    project_id = COALESCE(target.project_id, source.project_id)
FROM public.st_customer_statement source
WHERE source.id = target.source_customer_statement_id
  AND (target.customer_id IS NULL OR target.project_id IS NULL);

UPDATE public.fm_receipt target
SET customer_id = customer.id
FROM public.md_customer customer
WHERE target.customer_id IS NULL
  AND (customer.customer_code = target.customer_code
       OR (NULLIF(BTRIM(target.customer_code), '') IS NULL
           AND BTRIM(customer.customer_name) = BTRIM(target.customer_name)));

UPDATE public.fm_receipt target
SET project_id = project.id
FROM public.md_project project
WHERE target.project_id IS NULL
  AND project.customer_id = target.customer_id
  AND BTRIM(project.project_name) = BTRIM(target.project_name);

UPDATE public.lg_freight_bill_item target
SET customer_id = source_header.customer_id,
    project_id = source_header.project_id
FROM public.so_sales_outbound_item source_item
JOIN public.so_sales_outbound source_header ON source_header.id = source_item.outbound_id
WHERE source_item.id = target.source_sales_outbound_item_id
  AND (target.customer_id IS NULL OR target.project_id IS NULL);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.so_sales_order WHERE customer_id IS NULL OR project_id IS NULL)
       OR EXISTS (SELECT 1 FROM public.ct_sales_contract WHERE customer_id IS NULL OR project_id IS NULL)
       OR EXISTS (SELECT 1 FROM public.so_sales_outbound WHERE customer_id IS NULL OR project_id IS NULL)
       OR EXISTS (SELECT 1 FROM public.fm_invoice_issue WHERE customer_id IS NULL OR project_id IS NULL)
       OR EXISTS (SELECT 1 FROM public.st_customer_statement WHERE customer_id IS NULL OR project_id IS NULL)
       OR EXISTS (SELECT 1 FROM public.st_customer_statement_item WHERE customer_id IS NULL OR project_id IS NULL)
       OR EXISTS (SELECT 1 FROM public.fm_receipt WHERE customer_id IS NULL OR project_id IS NULL)
       OR EXISTS (SELECT 1 FROM public.lg_freight_bill_item WHERE customer_id IS NULL OR project_id IS NULL) THEN
        RAISE EXCEPTION 'V30: 销售客户/项目身份回填后仍有空值';
    END IF;

    IF EXISTS (
        WITH refs AS (
            SELECT customer_id, project_id FROM public.so_sales_order
            UNION ALL SELECT customer_id, project_id FROM public.ct_sales_contract
            UNION ALL SELECT customer_id, project_id FROM public.so_sales_outbound
            UNION ALL SELECT customer_id, project_id FROM public.fm_invoice_issue
            UNION ALL SELECT customer_id, project_id FROM public.st_customer_statement
            UNION ALL SELECT customer_id, project_id FROM public.fm_receipt
            UNION ALL SELECT customer_id, project_id FROM public.lg_freight_bill_item
        )
        SELECT 1 FROM refs
        JOIN public.md_project project ON project.id = refs.project_id
        WHERE project.customer_id <> refs.customer_id
    ) THEN
        RAISE EXCEPTION 'V30: 回填后仍存在跨客户项目关系';
    END IF;
END $$;
