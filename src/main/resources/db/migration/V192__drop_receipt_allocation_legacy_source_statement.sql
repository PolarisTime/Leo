-- 收款分配：废弃 source_statement_id，统一到 source_customer_statement_id
--
-- 背景：`fm_receipt_allocation` 长期并存两列指向同一个业务含义（客户对账单）：
--   - source_statement_id          早期列，NOT NULL，代码一直写它；
--   - source_customer_statement_id 后加的语义明确列（「客户」对账单，与物流对账单区分），
--                                  但**从未被写入**，一直是 NULL 或占位。
-- 实体 ReceiptAllocation 上还挂着 @Deprecated 的 getSourceStatementId/setSourceStatementId
-- 别名，让这份重复在代码里也看起来「都还在用」。
--
-- 破坏性收敛：把真实值搬到语义明确的新列，再把旧列删掉。
--
-- 数据核实（先查后改）：
--   - 生产库 master_prod_cutover_current_20260713_102500：fm_receipt_allocation 共 0 行；
--   - 开发库 leo：同样 0 行。
-- 因此本次迁移在生产上实际是纯结构变更；下面的 UPDATE 是为其它可能存量的环境兜底，
-- 且只在「旧列有值、新列为空」时覆盖，不会破坏已正确写入新列的数据。

-- 1) 先把旧列的值补到新列（幂等：新列已有值的不覆盖）
update public.fm_receipt_allocation
set source_customer_statement_id = source_statement_id
where source_customer_statement_id is null
  and source_statement_id is not null;

-- 2) 去掉旧列的 NOT NULL 约束，为删除做准备
--    （保留 NOT NULL 会导致代码切换到只写新列后立刻失败）
alter table public.fm_receipt_allocation
    alter column source_statement_id drop not null;

-- 3) 删除旧列
alter table public.fm_receipt_allocation
    drop column if exists source_statement_id;

-- 4) 新列补上 NOT NULL：业务上分配必然指向某张对账单
--    先确认没有 NULL 残留，否则 ALTER 会失败（在空表上恒成立）
do $$
begin
    if exists (select 1 from public.fm_receipt_allocation where source_customer_statement_id is null) then
        raise notice '存在 source_customer_statement_id 为空的分配行，跳过 NOT NULL 收紧，请人工核查';
    else
        alter table public.fm_receipt_allocation
            alter column source_customer_statement_id set not null;
    end if;
end $$;
