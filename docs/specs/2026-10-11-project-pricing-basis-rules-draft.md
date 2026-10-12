# 取价基准自动化（按日期自动取价）设计草案

- 日期：2026-10-11
- 状态：草案（口径已与业务确认，待评审后实施）
- 关联：`SteelQuoteMatchService`、`md_project_price_rule`（V160）、`mk_steel_quote`（V127）、`mk_steel_quote_closed_day`（V177）

## 1. 背景与现状

交付核定「取价」当前是**全手工**流程（`SalesOrderNetPriceFiller` → `GET /api/v2/material-price-matches?quoteDate&period&source&region`）：

1. 人工在弹窗里选「价格规定 + 行情日期 + 时段（上午/中午/下午）」，数据源与地区取自项目（`quote_source` / `quote_region`）；
2. 后端按「日期 + 时段」取出该时段行情行，逐商品做品牌别名→钢厂、类别→品名、规格覆盖、材质、长度加价匹配，返回网价；
3. 前端 `净价 = 网价 ± 价格规定幅度`（ADD/SUBTRACT，单选互斥、不叠加）写回明细 `unit_price`。

**问题**：

- 取价日期与单据上的 `order_date`（报单日期）、`delivery_date`（送货日期）**没有任何绑定**，靠人记、靠人选；
- 时段也要人工选（不传时后端取当天最新文章的时段）；
- 单据**只快照了价格规定**（`price_rule_id/name/mode/value`），**不保存实际取价日与时段**，事后无法还原「这单当时按哪天的行情赋的价」；
- 没有任何「基准日 + 偏移 N 天」的概念，也没有休市日（`mk_steel_quote_closed_day`）的结算口径。

## 2. 目标与业务口径（已确认）

**目标**：填入/带入日期后自动解析出「实际取价日 + 实际取价时段」，再套价格规定赋价；同时保留人工覆盖与可追溯快照。

业务实际存在两种基准：

- **A. 送货日期当天**的上/中/下午价；
- **B. 报单日期的第 X 天**的上/中/下午价。

确认的口径：

| # | 决策点 | 结论 |
| --- | --- | --- |
| 1 | 配置位置 | **项目资料页配置，默认「送货当日」；交付核定弹窗可临时覆盖** |
| 2 | 「第 X 天」口径 | **交易日**：报单日之后第 X 个**有行情交易日**（X=0 表示报单日当天，且报单日本身须为交易日） |
| 3 | 报单日期为休市日 | **新增配置**「是否按前一个交易日结算」：<br>开启 → 基准日取报单日**之前最近的有行情交易日**，再叠加 X；<br>关闭（默认） → 忽略休市，**向后**取报单日之后第 X 个交易日（X 至少按 1 计） |
| 4 | 时段 | **三种策略全部实现并在界面/文档中说明**：自动取当日最新时段 / 项目固定时段 / 弹窗手工指定 |

## 3. 数据模型

### 3.1 项目配置（新增列，故意与现有 `allowed_*` 风格一致）

| 列 | 类型 | 默认 | 含义 |
| --- | --- | --- | --- |
| `price_date_basis` | varchar(16) | `DELIVERY` | 取价基准：`DELIVERY` 送货日期 / `ORDER` 报单日+X / `MANUAL` 每次手工 |
| `price_date_offset_days` | integer | 0 | X（**交易日**数），仅 `ORDER` 使用；校验 0..30 |
| `price_closed_day_prev_settle` | boolean | false | 报单日为休市日时：true=按前一个交易日结算；false=向后取第 X 个交易日 |
| `price_period_policy` | varchar(16) | `AUTO` | 时段策略：`AUTO` 当日最新 / `FIXED` 固定 / `MANUAL` 每次手工 |
| `price_period_fixed` | varchar(8) | null | 固定时段（上午/中午/下午），仅 `FIXED` 必填 |

约束：`CHECK (price_date_basis IN ('DELIVERY','ORDER','MANUAL'))`、`CHECK (price_period_policy IN ('AUTO','FIXED','MANUAL'))`、`CHECK (price_date_offset_days BETWEEN 0 AND 30)`、`CHECK (price_period_fixed IS NULL OR price_period_fixed IN ('上午','中午','下午'))`；业务校验：`FIXED` 时 `price_period_fixed` 必填，非 `FIXED` 时强制置空（避免脏配置影响后续策略切换）。

### 3.2 销售订单取价快照（新增列）

| 列 | 类型 | 含义 |
| --- | --- | --- |
| `price_target_date` | date | 规则算出的**目标日**（回退前），用于审计「原本要取哪天」 |
| `price_quote_date` | date | **实际取价日**（回退后真正取到行情的那天） |
| `price_quote_period` | varchar(8) | **实际取价时段** |
| `price_date_basis` | varchar(16) | 本次生效的基准（可能是弹窗级覆盖值） |
| `price_date_offset_days` | integer | 本次生效的 X |
| `price_closed_settle_prev` | boolean | 本次生效的「休市按前一交易日结算」开关 |

历史单据这些列为空，前端按「未记录取价日」展示，不做回填。

## 4. 基准日与时段解析算法

### 4.1 交易日历

「有行情交易日」= 该日在 `mk_steel_quote` 中至少存在一个时段的行情（等价于日历接口 `periods` 非空）；休市（`closed=true`）与「无行情」在结算口径上同样处理：**该日不可作基准日**。判定统一走 `mk_steel_quote_closed_day` + 行情明细，不在前端推算（法定节假日与调休不服从「周末无行情」的直觉）。

记：

- `T`：交易日集合（按日期升序）；
- `prev(d)` = `max{ t ∈ T : t < d }`（不存在则视为不可解析）；
- `after(d, n)` = 严格晚于 `d` 的第 `n` 个交易日（`n ≥ 1`）；
- `at(d, 0)`：若 `d ∈ T` 则为 `d`，否则未定义。

### 4.2 真值表

**basis = DELIVERY（默认）**

| 情形 | 目标日 target | 实际取价日 |
| --- | --- | --- |
| 送货日是交易日 | 送货日 | 送货日 |
| 送货日休市/无行情 | 送货日 | `prev(送货日)`（**按最近一个交易日的报价结算**） |
| `prev` 不存在（超出日历可用范围） | 送货日 | 解析失败 → 提示人工指定（不猜） |

> 送货日不用「向后取」：货已按当日行情议定，往前找最近一次报价才是结算惯例；这与口径 3「报单日休市可选按前一交易日结算」同一逻辑。

**basis = ORDER（报单日 + X 个交易日）**

| 情形 | `settlePrevOnClosed` | 实际取价日 |
| --- | --- | --- |
| 报单日是交易日 | —（开关不参与） | `X = 0` → 报单日；`X ≥ 1` → `after(报单日, X)` |
| 报单日休市 | **true** | 令 `b = prev(报单日)`，`X = 0` → `b`；`X ≥ 1` → `after(b, X)` |
| 报单日休市 | **false（默认）** | `after(报单日, max(X, 1))`（报单日当天没有行情，必须向后取） |

**basis = MANUAL**

弹窗手工选日期。所选日若休市/无行情：**不自动替换**（用户是显式选择），返回 `availablePeriods` 与 `suggestedDate = prev(所选日)` 供界面一键采纳。

### 4.3 时段解析（三种策略，全部实现）

记 `P(d)` = 日期 `d` 的可用时段列表（按时间顺序：上午 → 中午 → 下午）。

| 策略 | 行为 | 该日缺目标时段时 |
| --- | --- | --- |
| `AUTO`（默认） | 取 `P(实际取价日)` 的**最后一个**时段（= 当前后端「取当天最新文章时段」的行为） | 不适用 |
| `FIXED` | 取项目/单据指定的时段 | **回退**到 `P(d)` 中不晚于目标时段的最后一个时段（如指定「下午」而当日只有上午、中午 → 取中午），并在界面标注「实际时段 ≠ 配置时段」；`P(d)` 为空则先走日期回退 |
| `MANUAL` | 弹窗手工选时段（默认预选 `AUTO` 结果） | 同上，界面提示可用时段 |

> `FIXED` 选择「回退 + 显著标注」而不是「报错阻断」：交付核定是日终作业，因下午未更新行情卡住整单不划算；但**绝不静默**——响应体与界面都回显 `requestedPeriod` 与 `period`（实际）。

### 4.4 解析结果（供前端展示与快照）

```
targetDate       基准日（送货日期 / 报单日期 / 手工日期；本身可能是休市日）
targetIsTradingDay 基准日是否为有行情交易日
quoteDate        实际取价日（交易日历回退后）
period           实际取价时段；无可取行情时为空
quoteAvailable   实际取价日是否取到了行情（前端据此提示「该日无行情」）
suggestedDate    建议日期（MANUAL 且基准日无行情时给出上一个交易日）
explanation      中文解析说明（界面直接展示，避免前端复述规则）
dateFallback     { applied, from, to, reason }   // 日期回退（休市/无行情）
periodFallback   { applied, from, to, reason }   // 时段回退（FIXED/MANUAL 缺时段）
availablePeriods 实际取价日的可用时段
```

> `targetDate` 取「基准日」而不是「规则推出的交易日」：前者是业务上真正被引用的日期（送货日/报单日/手工日），
> 事后才能一眼看出「这单本该按哪天算」；规则推出的交易日就是 `quoteDate`，两者在无回退时相同、有回退时成对留痕。

## 5. API 契约

新增只读资源（v2，复数名词，一层嵌套）：

```
GET /api/v2/projects/{projectId}/pricing-quote-resolutions
```

查询参数（均可选，用于单据级临时覆盖）：

| 参数 | 说明 |
| --- | --- |
| `orderDate` / `deliveryDate` | 单据日期（ISO `yyyy-MM-dd`）；对应 basis 缺失时返回 422 校验错误 |
| `basis` | 覆盖项目配置：`DELIVERY` / `ORDER` / `MANUAL` |
| `offsetDays` | 覆盖 X（0..30） |
| `settlePrevOnClosed` | 覆盖休市结算开关 |
| `periodPolicy` | 覆盖时段策略：`AUTO` / `FIXED` / `MANUAL` |
| `period` | 指定时段（`FIXED` / `MANUAL` 时的目标时段） |
| `quoteDate` | 仅 `MANUAL`：手工指定日期 |

响应 `200`：

```json
{
  "projectId": "1234567890123456789",
  "basis": "ORDER",
  "offsetDays": 2,
  "settlePrevOnClosed": false,
  "periodPolicy": "AUTO",
  "requestedPeriod": null,
  "targetDate": "2026-10-09",
  "targetIsTradingDay": true,
  "quoteDate": "2026-10-13",
  "period": "中午",
  "quoteAvailable": true,
  "suggestedDate": null,
  "explanation": "报单日期 2026-10-09 为交易日, 取之后第 2 个交易日 2026-10-13",
  "dateFallback": null,
  "periodFallback": null,
  "availablePeriods": ["上午", "中午"]
}
```

失败语义：项目不存在 `404`；缺必填单据日期或参数越界 `422`（`VALIDATION_ERROR`）；窗口内不存在可用交易日 `422` 并附中文原因（不返回空壳结果，避免前端拿空日期去取价）。

权限复用现有 `MATERIAL_PRICE_MATCHES_READ`（同一业务动作，不新增权限点）。雪花 ID 一律十进制字符串（`@JsonSerialize(using = ToStringSerializer.class)`）。

> 取价本身仍走既有 `GET /api/v2/material-price-matches?quoteDate&period&source&region`：解析与匹配分离，前端可先展示「将取哪天哪时段」再做一次取价，人工覆盖时也可直接调用匹配接口。

## 6. 前端交互

### 6.1 项目资料页（`ProjectEditorOverlay`）

新增分组「取价基准」（与「允许品牌/材质」同风格，抽 `ProjectPricingBasisFields.tsx`）：

- 取价基准：送货日期（默认）/ 报单日期 + N 个交易日 / 每次手工选择；
- N（0..30，仅报单日期基准可编辑）；
- 报单日为休市时按前一个交易日结算（开关，仅报单日期基准可编辑）；
- 时段策略：自动取当日最新时段（默认）/ 固定时段 / 每次手工选择；
- 固定时段：上午/中午/下午（仅固定策略可编辑）。

联动：非适用字段禁用并清空，避免脏配置。

### 6.2 交付核定取价弹窗（`SalesOrderNetPriceFiller`）

- 打开时先调解析接口，展示「目标日 / 实际取价日 / 时段（实际，含回退标注）」，回退时给出中文说明（如「10-09 为交易日，按报单日 + 2 个交易日取到 10-13」或「下午无行情，已按当日最后一个可用时段（中午）取价」）；
- 提供「临时覆盖」：基准、N、时段策略、时段、手工日期（覆盖后重新解析，并按解析结果拉取匹配）；
- 取价成功后写回明细 `unitPrice`（既有逻辑与提示不变）；
- 保存时把 `priceTargetDate / priceQuoteDate / priceQuotePeriod / priceDateBasis / priceDateOffsetDays / priceClosedSettlePrev` 随单据一起提交（快照）。

## 7. 边界与测试计划

**后端**

- 基准日真值表：报单日为交易日（X=0/1/N）、报单日休市 × 开关开/关、送货日为交易日/休市、`prev` 不存在、跨月跨年、调休上班日有行情；
- 时段策略：`AUTO` 取最后时段、`FIXED` 命中、`FIXED` 缺时段回退到不晚于目标的最后时段、`FIXED` 当日无行情 → 日期回退、`MANUAL` 指定日无行情返回建议日期；
- 校验：X 越界、`FIXED` 缺固定时段、非 `FIXED` 残留固定时段被清空、缺对应单据日期、项目不存在；
- 迁移：V179 默认值与 CHECK 约束；既有项目全部落到 `DELIVERY/AUTO`；历史单据快照列为空；
- 快照：保存/更新时写入且被响应回显；未取价保存时保持为空（不写脏值）。

**前端**

- 项目编辑：新建默认值、保存载荷、编辑回填、联动禁用与清空；
- 弹窗：解析成功展示、日期回退提示、时段回退提示、人工覆盖后重新解析、保存载荷带快照字段、无网价行提示不回归；
- 现有项目页弹窗的 `.ant-select` 序号会因新字段位移，需要同步修正既有用例。

## 8. 非目标

- 不做「按送货日期 + X 天」以外的复合规则（如按品类分别取不同基准）；
- 不做自动重算已保存单据的价格（历史单据仍按快照只读）；
- 不改行情抓取/补数链路与 `mk_steel_quote` 结构；
- 不做多数据源混取（仍按项目 `quote_source` 单源取价）。

## 9. 已知限制：销售订单缺少「报单日期」（待业务确认）

`ORDER` 基准需要单据的报单日期，但当前 `so_sales_order` 只有 `delivery_date`（`order_date` 属于
`po_purchase_order`），销售订单的表单、请求/响应契约里也没有该字段。现状：

- 解析接口与真值表已完整支持 `ORDER`（调用方传 `orderDate` 即可正常工作，含休市结算开关）；
- 交付核定弹窗在「项目配置为 ORDER 且单据无报单日期」时会展示后端 422 说明（`按报单日期取价需要提供报单日期`），
  用户可以临时把基准覆盖为「送货日期」或「每次手工选择」继续作业；
- 待业务确认后再补数据来源，候选：① 给销售订单新增可编辑的「报单日期」字段（默认取落单当天）；
  ② 取来源单据（采购订单 / 报单比价）的报单日期，取不到再回退落单当天；③ 直接以销售订单创建日期作为报单日期。

在此之前，`ORDER` 基准在销售订单场景下不可用；`DELIVERY`（默认）与 `MANUAL` 不受影响。
