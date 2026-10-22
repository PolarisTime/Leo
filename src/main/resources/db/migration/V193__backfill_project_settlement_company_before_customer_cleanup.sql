-- 项目结算主体回填：客户默认结算主体下线前，把还能推断出来的项目主体补齐
--
-- 背景：结算主体的选择权收敛到「项目资料」——客户资料不再维护默认结算主体。
-- V126 已经用客户默认主体回填过一次项目；本次是同一个口径的兜底，覆盖：
--   - V126 之后新建、但当时客户还没有默认主体的项目；
--   - 客户默认主体在 V126 之后才补录的项目。
--
-- 幂等：只补 settlement_company_id 仍为空的项目，已有主体一律不覆盖（可能是人工调整过的，
-- 不能拿客户旧默认值覆盖）。
update public.md_project project
set settlement_company_id = customer.default_settlement_company_id,
    settlement_company_name = customer.default_settlement_company_name
from public.md_customer customer
where project.customer_id = customer.id
  and project.settlement_company_id is null
  and customer.default_settlement_company_id is not null;

-- 回填后仍缺主体的项目：不阻断迁移（历史数据可能确实没有可推断的来源），但要留痕，
-- 便于部署后按项目资料逐个补齐。
do $$
declare
    missing_count integer;
begin
    select count(*) into missing_count
    from public.md_project
    where deleted_flag = false
      and settlement_company_id is null;

    if missing_count > 0 then
        raise notice '仍有 % 个有效项目未配置结算主体，请到项目资料中补齐（客户资料不再提供默认主体）', missing_count;
    end if;
end $$;
