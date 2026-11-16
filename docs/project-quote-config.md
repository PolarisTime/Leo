# 项目资料 = 报单比价配置的唯一配置点（2026-11-16 定稿）

## 背景

此前「参与比价的品牌」分散在两处：

| 位置 | 内容 | 用途 |
| --- | --- | --- |
| `md_project.allowed_brands` | 允许的品牌清单 | 采购/销售的品牌范围校验 |
| `mk_quote_project_config` + `mk_quote_project_brand` | 参与品牌、每品牌运费、启用品种、商品规格、定尺加价、抗震代取、指定品牌 | 报单比价渲染与取价 |

两处各自维护必然漂移（典型脏数据：「项目允许 A/B、比价却在用 C」），读路径还带有
「项目资料为空就回退到比价配置」的隐式回退（历史 5 个项目依赖它），前端另有「首次打开把有效品牌
自动写回配置」的隐式写。用户定稿：**项目资料是唯一配置点，比价配置只保留每品牌运费。**

## 目标模型

```
md_project
├─ allowed_brands            ← 品牌清单（唯一来源，决定列与顺序）
├─ allowed_brand_severity    ← OFF/WARN/BLOCK（采购/销售校验力度）
├─ quote_brand_categories    ← JSONB {品牌: [品种]}，缺键/空数组 = 全部品种
├─ quote_products            ← 参与比价的商品键 CSV（类别|材质|规格|长度），空 = 全部
├─ quote_length_premium      ← 定尺加价（元/吨），空 = 回退定尺主数据默认值
├─ quote_hrb400e_fallback    ← 抗震牌号代取开关
└─ quote_designated_brands   ← 指定品牌（仅报单展示徽标，不参与过滤）

mk_quote_project_config / mk_quote_project_brand
└─ 只保留：每品牌运费 freight（+ 运费编辑的乐观锁 version 与 remark）
```

## 接口契约

| 接口 | 行为 |
| --- | --- |
| `GET /api/v2/quote-project-configs/{projectId}` | 响应形状不变（`brands[{brandName,freight,categories,sortOrder}]` + `lengthPremium/products/designatedBrands/hrb400eFallback/version/quoteSource`），但值改为**项目资料派生**：品牌与顺序=`allowed_brands`，`categories`=`quote_brand_categories`，其余标量取 `quote_*`；`freight` 仍来自运费表，缺行按 0；无运费配置时 `version=0` 且不落库 |
| `PUT /api/v2/quote-project-configs/{projectId}` | 只写 `brands[].freight` + `remark`；旧字段（products/categories/lengthPremium/hrb400eFallback/designatedBrands/sortOrder）忽略不报错；品牌不在 `allowed_brands` 内 → 422；版本不符 → 412 |
| `GET /api/v2/quote-project-configs/{projectId}/effective-brands` | 只读 `allowed_brands`（删除回退分支），`fallback` 恒 `false`；`基准价` 作为防御过滤保留 |

## 报价单侧（易漏点）

`QuoteSheetStore` 原先用 `mk_quote_project_brand` 的运费行做品牌真源与快照同步。配置归并后必须改读
项目资料，否则「项目资料新增品牌、运费行未补」会导致：

- 行级写价 422「现货价品牌不在品牌列表中」；
- 单据品牌快照永远追不上项目资料。

现为三处统一走 `ProjectQuery.findQuoteSettingsById(...).allowedBrands()`（有清单时为准，空清单回退旧行为）：

- `validate`：项目允许品牌 → 配置品牌 → 请求品牌；
- `effectiveBrandNamesOf`：项目允许品牌优先；
- `syncBrandSnapshot`：按项目资料品牌与顺序重建快照，运费按品牌名从配置取，缺行按 0。

## 数据迁移（V220）

1. `md_project` 新增上述 5 列并写 COMMENT；
2. 有比价配置的项目：回填 `quote_products / quote_length_premium / quote_hrb400e_fallback / quote_designated_brands`，`brand.categories` 汇总为 JSONB；
3. `allowed_brands` 为空但有配置品牌的项目：按 `sort_order` 回填品牌清单，并把 `allowed_brand_severity` 置 `OFF`（保留清单、不点亮采购/销售校验，避免行为突变）；
4. 清理占位品牌「基准价」：允许品牌 / 指定品牌 / 品种键 / 品牌行四处剔除。

旧列（`mk_quote_project_config` 的 4 个标量、`mk_quote_project_brand.categories`）保留不读写，
删列属破坏性变更，需独立发布。

## 测试与验收

- `ProjectQuoteConfigMergePostgresTest`（`LEO_TEST_POSTGRES=true`）：列契约、回填一致性、力度、品种汇总、
  基准价清零、JSONB 往返，6 例；
- `QuoteProjectConfigStoreTest`：投影、只写运费、越界 422、412；
- `QuoteSheetStoreTest`：项目允许品牌无运费行时放行并补齐快照（运费 0）；项目资料外品牌 422；
- `ProjectServiceTest` / `QuoteBrandCategoriesTest`：新字段读写与 JSONB 边界。
