-- 交易日例外表: 法定节假日休市（CLOSED）与调休上班日（OPEN）。
--
-- 背景：覆盖矩阵要区分「这天本来没有行情（休市）」与「这天该有行情却漏了（缺失）」。
-- 原先只有"同步/补数时站点未发布 → 记一条 mk_steel_quote_closed_day"这一条**被动**来源，
-- 于是从没尝试同步过的节假日（例如国庆 10-01~10-07）一直显示成缺失，用户会去点、
-- 点了也只会失败。这里补上**主动**的交易日历：每年国务院办公厅公布次年安排后，
-- 追加一个迁移脚本写入对应年份的日期即可。
--
-- 两个方向都要记：
--   CLOSED —— 工作日但放假（国庆、春节、清明…），矩阵显示「休市」。
--   OPEN   —— 周六/周日但调休上班（如 2026-10-10 周六），矩阵必须显示为可同步的交易日，
--             否则前端按「周末即休市」的兜底规则会把它算成休市、该日行情永远补不进来。
create table if not exists mk_market_trading_day_override
(
    trade_date date        not null primary key,
    kind       varchar(8)  not null,
    label      varchar(64) not null,
    created_at timestamptz not null default now(),
    constraint ck_market_trading_day_override_kind check (kind in ('CLOSED', 'OPEN'))
);

comment on table mk_market_trading_day_override is
    '交易日例外: CLOSED=法定节假日休市, OPEN=调休上班日; 供行情覆盖矩阵判定交易日';
comment on column mk_market_trading_day_override.kind is 'CLOSED(休市) / OPEN(调休上班)';
comment on column mk_market_trading_day_override.label is '节假日名或调休说明, 便于排查';

-- 2025 年(国办发明电〔2024〕7 号)
insert into mk_market_trading_day_override (trade_date, kind, label)
select day::date, 'CLOSED', h.label
from (values ('2025-01-01', '2025-01-01', '元旦'),
             ('2025-01-28', '2025-02-04', '春节'),
             ('2025-04-04', '2025-04-06', '清明节'),
             ('2025-05-01', '2025-05-05', '劳动节'),
             ('2025-05-31', '2025-06-02', '端午节'),
             ('2025-10-01', '2025-10-08', '国庆节、中秋节')) as h(start_date, end_date, label)
         cross join lateral generate_series(h.start_date::date, h.end_date::date, interval '1 day') as day
on conflict (trade_date) do nothing;

insert into mk_market_trading_day_override (trade_date, kind, label)
select d::date, 'OPEN', '调休上班'
from (values ('2025-01-26'), ('2025-02-08'), ('2025-04-27'), ('2025-09-28'), ('2025-10-11')) as t(d)
on conflict (trade_date) do nothing;

-- 2026 年(国办发明电〔2025〕7 号)
insert into mk_market_trading_day_override (trade_date, kind, label)
select day::date, 'CLOSED', h.label
from (values ('2026-01-01', '2026-01-03', '元旦'),
             ('2026-02-15', '2026-02-23', '春节'),
             ('2026-04-04', '2026-04-06', '清明节'),
             ('2026-05-01', '2026-05-05', '劳动节'),
             ('2026-06-19', '2026-06-21', '端午节'),
             ('2026-09-25', '2026-09-27', '中秋节'),
             ('2026-10-01', '2026-10-07', '国庆节')) as h(start_date, end_date, label)
         cross join lateral generate_series(h.start_date::date, h.end_date::date, interval '1 day') as day
on conflict (trade_date) do nothing;

insert into mk_market_trading_day_override (trade_date, kind, label)
select d::date, 'OPEN', '调休上班'
from (values ('2026-01-04'), ('2026-02-14'), ('2026-02-28'), ('2026-05-09'),
             ('2026-09-20'), ('2026-10-10')) as t(d)
on conflict (trade_date) do nothing;
