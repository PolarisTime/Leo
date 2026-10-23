-- 比价项目配置: 把 2 条「project_id 存了 project_code」的错位行回填成真实项目 id
--
-- 背景(2026-10-23 只读排查 task-41 实测):
--   mk_quote_project_config.project_id 共 8 行, 其中 2 行存的是 md_project.project_code 而不是
--   md_project.id —— 这两行的 project_id 恰好等于另一个真实项目的 code:
--     project_id 348871302062284800 → 真实 id 348871385747038208 (嘉兴瑞辰汽车零部件项目)
--     project_id 349270537135988736 → 真实 id 349270654979153920 (周王庙镇应急消防一体化中心项目)
--   其余 6 行按 id 命中。错位会让「按项目资料取品牌」的规则查不到项目, 从而静默落到回退分支。
--
-- 口径:
--   1) 只改 project_id, 品牌行(mk_quote_project_brand)、运费、品种一律不动 —— 用户选择口径 B
--      (空值回退), 不把品牌写进 md_project.allowed_brands, 以免触发采购/销售的品牌校验;
--   2) 目标项目必须存在且 code 精确等于旧值; 目标 id 若已有配置行则跳过(唯一键 project_id),
--      宁可留给人处理, 也不让迁移炸在唯一约束上;
--   3) version + 1: 该行确实变了, 让 @Version 如实递增; 持有旧版本号的客户端保存时会得到
--      412「版本已变更, 请刷新后重试」, 这是正确语义(不能让人用旧快照覆盖);
--   4) updated_at / updated_name 留痕, 便于事后追溯这行是迁移改的。
--
-- 迁移前校验(执行时二次确认, 只读):
--   select count(*) from mk_quote_project_config c
--    where c.deleted_flag = false and not exists (select 1 from md_project p where p.id = c.project_id);
--   -- 期望 2
--
-- 迁移后校验(期望 0):
--   select count(*) from mk_quote_project_config c
--    where c.deleted_flag = false
--      and not exists (select 1 from md_project p where p.id = c.project_id)
--      and exists (select 1 from md_project p2 where btrim(p2.project_code) = c.project_id::text);
--
-- 注意: 本脚本一旦被任一环境执行就不能再改内容(Flyway 校验和), 后续修正只能新增更高版本。

update public.mk_quote_project_config as c
set project_id   = p.id,
    version      = c.version + 1,
    updated_at   = now(),
    updated_name = 'flyway-V196'
from public.md_project as p
where p.deleted_flag = false
  and btrim(p.project_code) = c.project_id::text
  and p.id <> c.project_id
  -- 目标项目已有配置行时不动, 避免撞 uk_quote_project_config_project_id
  and not exists (
      select 1
      from public.mk_quote_project_config as existing
      where existing.project_id = p.id
        and existing.deleted_flag = false
  );

comment on column public.mk_quote_project_config.project_id is
    '所属项目 id(引用 md_project.id)。V196 起不再允许写入 project_code; 历史 2 行错位已回填。';
