DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.md_project project
        LEFT JOIN LATERAL (
            SELECT COUNT(*) AS candidate_count
            FROM public.md_customer customer
            WHERE customer.customer_code = project.customer_code
        ) candidates ON true
        WHERE project.customer_id IS NULL
          AND candidates.candidate_count <> 1
    ) THEN
        RAISE EXCEPTION 'V27: md_project.customer_id 存在零候选或多候选客户';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.md_project project
        LEFT JOIN public.md_customer customer ON customer.id = project.customer_id
        WHERE project.customer_id IS NOT NULL
          AND (customer.id IS NULL OR customer.customer_code <> project.customer_code)
    ) THEN
        RAISE EXCEPTION 'V27: md_project 已有 customer_id 与客户编码快照冲突';
    END IF;
END $$;

UPDATE public.md_project project
SET customer_id = customer.id
FROM public.md_customer customer
WHERE project.customer_id IS NULL
  AND customer.customer_code = project.customer_code;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.md_project WHERE customer_id IS NULL) THEN
        RAISE EXCEPTION 'V27: md_project.customer_id 回填后仍有空值';
    END IF;
END $$;
