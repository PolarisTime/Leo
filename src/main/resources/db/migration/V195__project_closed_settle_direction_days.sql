-- 项目取价基准: 「休市按前一交易日结算」布尔 → 「方向(PREV/NEXT) + 第 X 个交易日」
--
-- 背景：
--   旧布尔 price_closed_day_prev_settle 只能表达「往前 1 个交易日」，而业务需要
--   「往前 / 延后第 X 个交易日（0..30）」；且送货日期（DELIVERY）基准同样要在基准日休市时
--   换到一个真实交易日 —— 旧布尔只对 ORDER 生效。V195 起 DELIVERY 与 ORDER 共用一套规则，
--   MANUAL 不参与（每次手工选日期，只给 suggestedDate）。
--
-- 口径：
--   1) md_project.price_closed_settle_direction ∈ {PREV 往前, NEXT 延后}，默认 PREV；
--      md_project.price_closed_settle_days 0..30，默认 1；
--   2) days = 0 只在 ORDER + NEXT 合法（休市不做方向位移，直接从报单日向后顺延），
--      其余组合（PREV / DELIVERY）必须 ≥1 —— 这类组合规则由应用层
--      ProjectPricingBasisQuery#requireValidClosedSettle 统一校验（422），
--      DB 这里只兜「0..30 范围 + 枚举取值」这一层，避免历史数据/手工 SQL 写入脏值；
--   3) 旧列 price_closed_day_prev_settle / price_closed_settle_prev 保留但置为 deprecated，
--      只为历史只读与回滚留痕，待清理发布（后续版本）删除。
--
-- 回填必须保证历史行为零漂移（对照旧实现 PricingQuoteResolver）：
--   ORDER + prev_settle=true                        → (PREV, 1)  旧：before(报单日,1) 再叠加偏移
--   ORDER + prev_settle=false + offset=0            → (NEXT, 1)  旧：after(报单日, max(0,1))
--   ORDER + prev_settle=false + offset>0            → (NEXT, 0)  旧：after(报单日, offset)
--   其余（DELIVERY / MANUAL）                       → (PREV, 1)  旧布尔对它们本就无效；
--                                                                 DELIVERY 旧行为 = 最近的前一个交易日
--
-- 注意：本脚本一旦被任一环境执行就不能再改内容（Flyway 校验和），后续修正只能新增更高版本。

alter table public.md_project
    add column price_closed_settle_direction character varying(8) default 'PREV'::character varying not null,
    add column price_closed_settle_days integer default 1 not null;

comment on column public.md_project.price_closed_settle_direction is
    '基准日(送货日/报单日)休市时的位移方向: PREV 往前第X个交易日 / NEXT 延后第X个交易日; MANUAL 不参与';
comment on column public.md_project.price_closed_settle_days is
    '基准日休市位移的交易日数(0..30): 0 仅 ORDER+NEXT 有效(不做方向位移, 直接向后顺延取价)';
comment on column public.md_project.price_closed_day_prev_settle is
    'DEPRECATED(V195 起): 已由 price_closed_settle_direction + price_closed_settle_days 取代, 仅为历史只读保留, 待清理发布删除';

alter table public.md_project
    add constraint chk_project_price_closed_settle_direction
        check (price_closed_settle_direction in ('PREV', 'NEXT')),
    add constraint chk_project_price_closed_settle_days
        check (price_closed_settle_days >= 0 and price_closed_settle_days <= 30);

do $$
declare
    order_prev_rows integer;
    order_next_zero_offset_rows integer;
    order_next_offset_rows integer;
    other_rows integer;
begin
    select count(*) into order_prev_rows
        from public.md_project
        where price_date_basis = 'ORDER' and price_closed_day_prev_settle = true;
    select count(*) into order_next_zero_offset_rows
        from public.md_project
        where price_date_basis = 'ORDER' and price_closed_day_prev_settle = false
          and price_date_offset_days = 0;
    select count(*) into order_next_offset_rows
        from public.md_project
        where price_date_basis = 'ORDER' and price_closed_day_prev_settle = false
          and price_date_offset_days > 0;
    select count(*) into other_rows
        from public.md_project
        where price_date_basis is distinct from 'ORDER';

    update public.md_project
        set price_closed_settle_direction = 'PREV',
            price_closed_settle_days = 1
        where price_date_basis = 'ORDER' and price_closed_day_prev_settle = true;

    update public.md_project
        set price_closed_settle_direction = 'NEXT',
            price_closed_settle_days = 1
        where price_date_basis = 'ORDER' and price_closed_day_prev_settle = false
          and price_date_offset_days = 0;

    update public.md_project
        set price_closed_settle_direction = 'NEXT',
            price_closed_settle_days = 0
        where price_date_basis = 'ORDER' and price_closed_day_prev_settle = false
          and price_date_offset_days > 0;

    raise notice 'V195 md_project 回填: ORDER+prev_settle=true -> (PREV,1) % 行; ORDER+false+offset=0 -> (NEXT,1) % 行; ORDER+false+offset>0 -> (NEXT,0) % 行; 其余(basis 非 ORDER, 走列默认 (PREV,1)) % 行',
        order_prev_rows, order_next_zero_offset_rows, order_next_offset_rows, other_rows;
end
$$;

-- 销售订单: 交付核定的取价快照同口径换列（旧列 price_closed_settle_prev 保留并 deprecated）。
alter table public.so_sales_order
    add column price_closed_settle_direction character varying(8),
    add column price_closed_settle_days integer;

comment on column public.so_sales_order.price_closed_settle_direction is
    '本次生效的基准日休市位移方向(快照): PREV 往前第X个交易日 / NEXT 延后第X个交易日';
comment on column public.so_sales_order.price_closed_settle_days is
    '本次生效的基准日休市位移交易日数(快照, 0..30; 0 仅 ORDER+NEXT)';
comment on column public.so_sales_order.price_closed_settle_prev is
    'DEPRECATED(V195 起): 已由 price_closed_settle_direction + price_closed_settle_days 取代, 仅为历史只读保留, 待清理发布删除';

alter table public.so_sales_order
    add constraint chk_sales_order_price_closed_settle_direction
        check (price_closed_settle_direction is null or price_closed_settle_direction in ('PREV', 'NEXT')),
    add constraint chk_sales_order_price_closed_settle_days
        check (price_closed_settle_days is null
            or (price_closed_settle_days >= 0 and price_closed_settle_days <= 30));

do $$
declare
    snapshot_rows integer;
begin
    -- 历史快照等价回填：只处理「已记录取价过程」的行（price_date_basis 非空）。
    -- 未记录取价过程的单据保持全空（前端按「未记录取价日」展示，不做回填），
    -- 否则会给一张从未取过价的单据凭空造出「当时的休市位移配置」。
    select count(*) into snapshot_rows
        from public.so_sales_order
        where price_date_basis is not null;

    update public.so_sales_order
        set price_closed_settle_direction = case
                when price_date_basis = 'ORDER' and price_closed_settle_prev is not true then 'NEXT'
                else 'PREV'
            end,
            price_closed_settle_days = case
                when price_date_basis = 'ORDER' and price_closed_settle_prev is not true
                     and coalesce(price_date_offset_days, 0) > 0 then 0
                else 1
            end
        where price_date_basis is not null;

    raise notice 'V195 so_sales_order 快照回填: 已记录取价过程的单据 % 行（其余历史行保持为空）',
        snapshot_rows;
end
$$;
