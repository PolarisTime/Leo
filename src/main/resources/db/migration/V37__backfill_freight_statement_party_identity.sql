DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.st_freight_statement_item statement_item
        LEFT JOIN public.lg_freight_bill_item bill_item
          ON bill_item.id = statement_item.source_freight_bill_item_id
        WHERE bill_item.id IS NULL
           OR bill_item.customer_id IS NULL
           OR bill_item.project_id IS NULL
    ) THEN
        RAISE EXCEPTION
            'V37: 物流对账明细存在缺失来源物流明细或来源客户/项目身份不完整';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.st_freight_statement_item statement_item
        JOIN public.lg_freight_bill_item bill_item
          ON bill_item.id = statement_item.source_freight_bill_item_id
        WHERE (statement_item.customer_id IS NOT NULL
               AND statement_item.customer_id <> bill_item.customer_id)
           OR (statement_item.project_id IS NOT NULL
               AND statement_item.project_id <> bill_item.project_id)
    ) THEN
        RAISE EXCEPTION
            'V37: 物流对账明细已有客户/项目身份与直接来源物流明细冲突';
    END IF;
END $$;

UPDATE public.st_freight_statement_item statement_item
SET customer_id = bill_item.customer_id,
    project_id = bill_item.project_id
FROM public.lg_freight_bill_item bill_item
WHERE bill_item.id = statement_item.source_freight_bill_item_id
  AND (statement_item.customer_id IS NULL OR statement_item.project_id IS NULL);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.st_freight_statement_item statement_item
        LEFT JOIN public.md_project project ON project.id = statement_item.project_id
        WHERE statement_item.customer_id IS NULL
           OR statement_item.project_id IS NULL
           OR project.id IS NULL
           OR project.customer_id <> statement_item.customer_id
    ) THEN
        RAISE EXCEPTION
            'V37: 物流对账明细客户/项目身份回填后仍为空或项目不属于客户';
    END IF;
END $$;
