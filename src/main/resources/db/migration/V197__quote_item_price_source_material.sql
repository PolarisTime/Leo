-- 现货价「抗震牌号代取」来源落库: mk_quote_item_price.source_material
--
-- 背景:
--   报单比价的现货价支持「替代牌号代取」—— 行材质是 HRB400、行情没有 HRB400 时用 HRB400E 的价
--   代替（业务上就是"400 没货用 400E 代替，按 400E 的实际网价结算"）。代取价与手工精确填的价
--   在数值上完全无法区分，事后查不清这一格价从哪来，前端也无法打「代取」角标。
--
-- 口径:
--   1) source_material = 该现货价**实际取自**的材质；
--      NULL/空 = 就是本行(quote sheet item)自身材质的价；
--   2) 只有真正的代取才写值；以下情况应用层会落 NULL(见 QuoteSheetStore#normalizeSourceMaterial):
--      价格被清空 / 传空白 / 与本行材质相同(忽略大小写)；
--   3) 长度 16 与材质取值(HRB400E 等 7~10 字符)留足余量；不加 NOT NULL，历史行天然为 NULL，
--      语义正是「未代取」，无需回填;
--   4) 不建索引: 该列只随行一起读写，不做过滤条件。
--
-- 迁移前校验(只读):
--   select count(*) from information_schema.columns
--    where table_schema='public' and table_name='mk_quote_item_price' and column_name='source_material';
--   -- 期望 0
--
-- 迁移后校验(期望 1，且既有 96+ 行 source_material 全为 NULL):
--   select count(*) from information_schema.columns
--    where table_schema='public' and table_name='mk_quote_item_price' and column_name='source_material';
--   select count(*) filter (where source_material is not null) as filled,
--          count(*) as total
--     from public.mk_quote_item_price;
--
-- 注意: 本脚本一旦被任一环境执行就不能再改内容(Flyway 校验和), 后续修正只能新增更高版本。

alter table public.mk_quote_item_price
    add column source_material character varying(16);

comment on column public.mk_quote_item_price.source_material is
    '该现货价实际取自的材质(抗震牌号代取, 如行材质 HRB400 取 HRB400E 的价则记 HRB400E); 为空=本行材质自身价';
