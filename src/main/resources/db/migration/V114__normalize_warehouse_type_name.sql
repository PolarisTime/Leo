-- “自营仓”是仓库类型的标准术语；清理旧界面写入的“自有仓”。
UPDATE public.md_warehouse
SET warehouse_type = '自营仓',
    updated_by = 0,
    updated_name = 'flyway',
    updated_at = CURRENT_TIMESTAMP
WHERE warehouse_type = '自有仓';
