-- 修正 V187 的状态 CHECK 约束取值。
-- 背景: V187 把约束写成 ('正常','停用'), 但应用侧的 StatusConstants.DISABLED 来自
-- DocumentStatus.DISABLED.label(), 实际文案是「禁用」。两者不一致会导致应用写入「禁用」时
-- 被数据库约束拒绝(服务层校验通过、DB 报错), 属于必须修复的契约错配。
-- 说明: V187 已在环境执行过, 按迁移政策不得改写, 只能新增更高版本修复。
-- 数据影响: 现有 69 行种子均为「正常」, 无需数据修正。
-- 幂等: 先 DROP IF EXISTS 再 ADD, 重复执行安全。

ALTER TABLE public.md_quote_region DROP CONSTRAINT IF EXISTS ck_quote_region_status;

ALTER TABLE public.md_quote_region
    ADD CONSTRAINT ck_quote_region_status CHECK (status::text IN ('正常', '禁用'));

COMMENT ON COLUMN public.md_quote_region.status IS '状态: 正常 / 禁用; 仅正常参与抓取与下拉';
