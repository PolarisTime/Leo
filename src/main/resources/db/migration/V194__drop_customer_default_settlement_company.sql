-- 客户默认结算主体下线：结算主体改为「项目资料」唯一选择
--
-- 背景：客户资料里的「默认结算主体」原本是新建项目/单据时自动带出的默认值，
-- 但同一客户下的多个项目经常分属不同结算主体，客户级默认反而制造错误快照。
-- 现在结算主体只在项目资料里选择（md_project.settlement_company_id，V126 已上线），
-- 单据按所选项目快照主体，打印模板仍按主体绑定、按单据快照过滤。
--
-- 执行顺序：
--   1) V193 已把客户默认主体回填到缺失的项目；
--   2) 本脚本删掉客户侧的两列及其索引/外键。
--
-- 破坏性说明：这两列不再被任何代码读取，删除后无法恢复；如某项目仍未配置主体，
-- 需要在项目资料里手工选择（V193 会打印剩余数量）。

-- 外键先删（依赖列，列删掉后约束也会消失，但显式删除便于排查失败点）
alter table public.md_customer
    drop constraint if exists fk_md_customer_default_settlement_company;

-- 支撑外键的普通索引（V36）与业务部分索引（基线）随列一起删除；显式 drop 避免遗留
drop index if exists public.idx_md_customer_default_settlement_company_id_fk;
drop index if exists public.idx_md_customer_settlement_company;

alter table public.md_customer
    drop column if exists default_settlement_company_name,
    drop column if exists default_settlement_company_id;

comment on column public.md_project.settlement_company_id is
    '项目结算主体，引用 sys_company_setting(id)；结算主体的唯一选择入口（客户资料不再维护默认主体）';
