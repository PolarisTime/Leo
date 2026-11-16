-- 配置归并：项目资料成为唯一的「品牌/品种/商品/加价/代取/指定品牌」配置点，
-- 报单比价配置（mk_quote_project_config / mk_quote_project_brand）从此只保留「每品牌运费」。
--
-- 背景（2026-11-16 用户定稿）：同一套「参与比价的品牌」此前分散在两处 ——
-- 项目资料 md_project.allowed_brands（采购/销售品牌校验用）与比价配置的品牌行（运费/品种等）。
-- 两处各自维护必然漂移（典型脏数据：「项目允许 A/B、比价却在用 C」），因此把配置收敛到项目资料，
-- 比价配置只留运费；GET /quote-project-configs/{id} 改为按项目资料投影，PUT 只写运费。
--
-- 本脚本一次性完成四件事：
--   1) md_project 新增 5 列（商品键 / 品牌品种 / 12米加价 / HRB400E 代取 / 指定品牌）；
--   2) 从 mk_quote_project_config 回填 4 项标量，从 mk_quote_project_brand 汇总品牌→品种；
--   3) allowed_brands 为空但比价配置有品牌的项目：按 sort_order 回填品牌清单，
--      并把 allowed_brand_severity 置 OFF（保留清单但不点亮采购/销售的品牌校验，避免行为突变）；
--   4) 清理历史占位品牌「基准价」：三处清单剔除该值，并删除含该品牌的品牌行。
--
-- 说明：
--   * 只加列、不改已有列语义；mk_quote_project_config 的旧标量列保留（不再被读，便于回溯）；
--   * 空库执行时全部数据语句 0 行受影响，脚本可顺序执行；
--   * 「基准价」不是真实品牌（md_material.brand 无此值），是历史配置里的占位行。

ALTER TABLE public.md_project
    ADD COLUMN quote_products text,
    ADD COLUMN quote_brand_categories jsonb,
    ADD COLUMN quote_length_premium numeric(12,2),
    ADD COLUMN quote_hrb400e_fallback boolean NOT NULL DEFAULT false,
    ADD COLUMN quote_designated_brands character varying(512);

COMMENT ON COLUMN public.md_project.quote_products IS
    '参与报单比价的商品键(逗号分隔: 类别|材质|规格|长度); 空表示全部商品可选';
COMMENT ON COLUMN public.md_project.quote_brand_categories IS
    '品牌→启用品种(JSONB, 形如 {"中天":["直条","盘螺"]}); 缺键或空数组表示该品牌全部品种';
COMMENT ON COLUMN public.md_project.quote_length_premium IS
    '12米定尺加价(元/吨); 空表示未配置, 取价时回退定尺主数据默认值';
COMMENT ON COLUMN public.md_project.quote_hrb400e_fallback IS
    '是否允许 HRB400 商品按 HRB400E 价格代取'; 
COMMENT ON COLUMN public.md_project.quote_designated_brands IS
    '指定品牌(逗号分隔, 仅在报单比价里展示徽标, 不参与校验)';

-- 1) 标量回填：有比价配置的项目，把商品键/加价/代取/指定品牌搬到项目资料。
--    指定品牌里剔除占位「基准价」，空结果归一为 NULL。
UPDATE public.md_project project
   SET quote_products = config.products,
       quote_length_premium = config.length_premium,
       quote_hrb400e_fallback = config.hrb400e_fallback,
       quote_designated_brands = NULLIF(
           array_to_string(
               array(SELECT btrim(token)
                       FROM unnest(string_to_array(config.designated_brands, ',')) AS token
                      WHERE btrim(token) <> '' AND btrim(token) <> '基准价'),
               ','),
           '')
  FROM public.mk_quote_project_config config
 WHERE config.project_id = project.id
   AND config.deleted_flag = false;

-- 2) 品牌→品种汇总：categories 是逗号分隔文本，空值不写键（缺键 = 该品牌全部品种）。
UPDATE public.md_project project
   SET quote_brand_categories = aggregated.categories
  FROM (
        SELECT config.project_id,
               jsonb_object_agg(brand.brand_name, brand.categories_json) AS categories
          FROM public.mk_quote_project_config config
          JOIN (
                SELECT brand_row.config_id,
                       brand_row.brand_name,
                       to_jsonb(array(SELECT btrim(token)
                                       FROM unnest(string_to_array(brand_row.categories, ',')) AS token
                                      WHERE btrim(token) <> '')) AS categories_json
                  FROM public.mk_quote_project_brand brand_row
                 WHERE brand_row.brand_name <> '基准价'
                   AND brand_row.categories IS NOT NULL
               ) brand ON brand.config_id = config.id
         WHERE config.deleted_flag = false
           AND jsonb_array_length(brand.categories_json) > 0
         GROUP BY config.project_id
       ) aggregated
 WHERE aggregated.project_id = project.id;

-- 3) 只在比价配置里配过品牌的项目：按配置顺序回填到项目资料，并关闭品牌校验力度。
--    关校验而不删清单：清单保留可被报单比价继续使用，同时不改变采购/销售的既有放行行为。
UPDATE public.md_project project
   SET allowed_brands = aggregated.brands,
       allowed_brand_severity = 'OFF'
  FROM (
        SELECT config.project_id,
               string_agg(brand.brand_name, ',' ORDER BY brand.sort_order, brand.id) AS brands
          FROM public.mk_quote_project_config config
          JOIN public.mk_quote_project_brand brand ON brand.config_id = config.id
         WHERE config.deleted_flag = false
           AND brand.brand_name <> '基准价'
         GROUP BY config.project_id
       ) aggregated
 WHERE aggregated.project_id = project.id
   AND (project.allowed_brands IS NULL OR btrim(project.allowed_brands) = '')
   AND aggregated.brands IS NOT NULL;

-- 4) 清理历史占位品牌「基准价」：先清项目清单，再清品种映射的键，最后删品牌行。
UPDATE public.md_project
   SET allowed_brands = NULLIF(
           array_to_string(
               array(SELECT btrim(token)
                       FROM unnest(string_to_array(md_project.allowed_brands, ',')) AS token
                      WHERE btrim(token) <> '' AND btrim(token) <> '基准价'),
               ','),
           '')
 WHERE allowed_brands LIKE '%基准价%';

UPDATE public.md_project
   SET quote_brand_categories = quote_brand_categories - '基准价'
 WHERE quote_brand_categories ? '基准价';

DELETE FROM public.mk_quote_project_brand WHERE brand_name = '基准价';
