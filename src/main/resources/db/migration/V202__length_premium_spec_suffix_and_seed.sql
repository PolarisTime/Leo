-- 定尺加价主数据补充「打印规格后缀」, 并让长度主数据覆盖现有商品长度取值。
-- 说明:
--   1) spec_suffix 承载「该定尺在打印规格里如何标注」, 例如 12米 -> "*12";
--      空/NULL 表示该定尺不在规格后追加任何内容(如 9米)。
--   2) 该映射此前写死在 Java 常量(PrintSpecLengthRule: "12米" -> "*12"), 新增定尺
--      或调整后缀必须改代码重启; 改成列之后由定尺主数据驱动打印解析。
--   3) 新增 9米 记录(加价 0): 商品资料长度校验要求命中定尺主数据, 现有商品里
--      9米/12米 都在用, 主数据缺少 9米 会让这些商品无法再编辑。
--   4) mk_quote_sheet / mk_quote_project_config 的 length_premium 默认值 30 是
--      QuoteDefaults.LENGTH_PREMIUM 的重复定义; 默认值改由应用按定尺主数据「12米」
--      取, 故删除列默认值, 避免出现「改主数据但列默认值仍是 30」的第二来源。
--   5) 列注释里的「12米加价」改为「定尺加价」: 加价不再只针对 12米。
-- 幂等: ADD COLUMN IF NOT EXISTS / 回填与排序按 length_name 定位 / 种子 NOT EXISTS + ON CONFLICT。

ALTER TABLE public.md_length_premium
    ADD COLUMN IF NOT EXISTS spec_suffix character varying(16);

COMMENT ON COLUMN public.md_length_premium.spec_suffix IS
    '打印规格后缀(如 *12): 打印时追加到商品规格之后; 空表示该定尺不追加';

-- 12米 的打印后缀沿用改造前的写死规则
UPDATE public.md_length_premium
SET spec_suffix = '*12',
    updated_at = CURRENT_TIMESTAMP,
    updated_name = 'flyway'
WHERE length_name = '12米'
  AND deleted_flag = false
  AND (spec_suffix IS NULL OR spec_suffix = '');

-- 种子 9米: 只在不存在同长定尺时插入, 避免覆盖运维已维护的数据
INSERT INTO public.md_length_premium
    (id, length_name, premium, sort_order, status, spec_suffix, remark, created_by, created_name)
SELECT 2, '9米', 0, 1, '正常', NULL, '定尺主数据最小集: 9米不加价、不追加打印规格后缀', 0, 'flyway'
WHERE NOT EXISTS (
    SELECT 1 FROM public.md_length_premium WHERE length_name = '9米' AND deleted_flag = false
)
ON CONFLICT DO NOTHING;

-- 展示/匹配顺序按数值升序: 9米=1, 12米=2
UPDATE public.md_length_premium
SET sort_order = 1,
    updated_at = CURRENT_TIMESTAMP,
    updated_name = 'flyway'
WHERE length_name = '9米' AND deleted_flag = false AND sort_order <> 1;

UPDATE public.md_length_premium
SET sort_order = 2,
    updated_at = CURRENT_TIMESTAMP,
    updated_name = 'flyway'
WHERE length_name = '12米' AND deleted_flag = false AND sort_order <> 2;

-- 去掉与 QuoteDefaults.LENGTH_PREMIUM 重复的列默认值(默认值改由应用按定尺主数据取)
ALTER TABLE public.mk_quote_sheet ALTER COLUMN length_premium DROP DEFAULT;
ALTER TABLE public.mk_quote_project_config ALTER COLUMN length_premium DROP DEFAULT;

COMMENT ON COLUMN public.mk_quote_sheet.length_premium IS '定尺加价(元/吨, 仅螺纹钢生效)';
COMMENT ON COLUMN public.mk_quote_project_config.length_premium IS
    '项目级定尺加价(元/吨): 未显式提供时按定尺主数据「12米」取, 取不到回退 QuoteDefaults';
