# 取价基准自动化（按日期自动取价）设计草案

- 日期：2026-10-11
- 修订：2026-10-22 休市结算由「是否按前一交易日结算」布尔改为「方向 + X 个交易日」，并让送货日期基准同样适用（V195）
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
| 3 | 基准日（送货日 / 报单日）为休市日 | **配置「方向 + X 个交易日」**：`price_closed_settle_direction ∈ {PREV 往前, NEXT 延后}`（默认 `PREV`）+ `price_closed_settle_days`（0..30，默认 1）。<br>休市基准日 = 基准日往前 / 延后第 X 个**交易日**；<br>**DELIVERY**：取价日 = 休市基准日；<br>**ORDER**：取价日 = `after(休市基准日, offsetDays)`（`offsetDays=0` 即休市基准日本身）。<br>唯一例外：`ORDER` + `NEXT` + `days=0` 表示「不做方向位移」，直接从报单日向后顺延（等价旧「关闭」口径）。<br>**DELIVERY 与 ORDER 共用同一套**；`MANUAL` 不参与（每次手工选日期） |
| 4 | 时段 | **三种策略全部实现并在界面/文档中说明**：自动取当日最新时段 / 项目固定时段 / 弹窗手工指定 |

## 3. 数据模型

### 3.1 项目配置（新增列，故意与现有 `allowed_*` 风格一致）

| 列 | 类型 | 默认 | 含义 |
| --- | --- | --- | --- |
| `price_date_basis` | varchar(16) | `DELIVERY` | 取价基准：`DELIVERY` 送货日期 / `ORDER` 报单日+X / `MANUAL` 每次手工 |
| `price_date_offset_days` | integer | 0 | X（**交易日**数），仅 `ORDER` 使用；校验 0..30 |
| `price_closed_settle_direction` | varchar(8) | `PREV` | 基准日休市时的位移方向：`PREV` 往前第 X 个交易日 / `NEXT` 延后第 X 个交易日（**DELIVERY 与 ORDER 有效**） |
| `price_closed_settle_days` | integer | 1 | 基准日休市位移的交易日数 0..30；0 仅 `ORDER`+`NEXT` 合法 |
| `price_period_policy` | varchar(16) | `AUTO` | 时段策略：`AUTO` 当日最新 / `FIXED` 固定 / `MANUAL` 每次手工 |
| `price_period_fixed` | varchar(8) | null | 固定时段（上午/中午/下午），仅 `FIXED` 必填 |

约束：`CHECK (price_date_basis IN ('DELIVERY','ORDER','MANUAL'))`、`CHECK (price_period_policy IN ('AUTO','FIXED','MANUAL'))`、`CHECK (price_date_offset_days BETWEEN 0 AND 30)`、`CHECK (price_closed_settle_direction IN ('PREV','NEXT'))`、`CHECK (price_closed_settle_days BETWEEN 0 AND 30)`、`CHECK (price_period_fixed IS NULL OR price_period_fixed IN ('上午','中午','下午'))`。

业务校验（422，**唯一实现** `ProjectPricingBasisQuery#requireValidClosedSettle`，项目写入 / 交付核定快照 / 取价解析三处共用）：天数统一 0..30（**所有基准**都校验）；`DELIVERY` 必须 ≥1；`ORDER`+`PREV` 必须 ≥1；`ORDER`+`NEXT` 允许 0；`MANUAL` 不参与解析，**只做 0..30 范围校验**，不限制方向与 0 的组合（项目从 `ORDER+NEXT+0` 切到 `MANUAL` 时表单里这两个字段是禁用态、用户改不了，不能因此 422 卡死保存）。另有：`FIXED` 时 `price_period_fixed` 必填，非 `FIXED` 时强制置空（避免脏配置影响后续策略切换）。

旧列 `price_closed_day_prev_settle`（boolean）自 V195 起 **deprecated**：不再被代码读写，只为历史只读与回滚留痕保留，待清理发布删除。V195 回填映射（保证历史行为零漂移）：`ORDER & true → (PREV,1)`；`ORDER & false & offset=0 → (NEXT,1)`；`ORDER & false & offset>0 → (NEXT,0)`；其余（`DELIVERY`/`MANUAL`）→ `(PREV,1)`。

### 3.2 销售订单取价快照（新增列）

| 列 | 类型 | 含义 |
| --- | --- | --- |
| `price_target_date` | date | 规则算出的**目标日**（回退前），用于审计「原本要取哪天」 |
| `price_quote_date` | date | **实际取价日**（回退后真正取到行情的那天） |
| `price_quote_period` | varchar(8) | **实际取价时段** |
| `price_date_basis` | varchar(16) | 本次生效的基准（可能是弹窗级覆盖值） |
| `price_date_offset_days` | integer | 本次生效的 X |
| `price_closed_settle_direction` | varchar(8) | 本次生效的「基准日休市位移方向」：`PREV` / `NEXT` |
| `price_closed_settle_days` | integer | 本次生效的「基准日休市位移交易日数」（0..30） |

历史单据若**从未记录取价过程**（`price_date_basis` 为空），这些列保持为空，前端按「未记录取价日」展示；V195 只对**已记录取价过程**的历史行按 md_project 同一映射做等价回填。旧列 `price_closed_settle_prev` 自 V195 起 deprecated，保留待清理发布删除。

## 4. 基准日与时段解析算法

### 4.1 交易日历

「有行情交易日」= 该日在 `mk_steel_quote` 中至少存在一个时段的行情（等价于日历接口 `periods` 非空）；休市（`closed=true`）与「无行情」在结算口径上同样处理：**该日不可作基准日**。判定统一走 `mk_steel_quote_closed_day` + 行情明细，不在前端推算（法定节假日与调休不服从「周末无行情」的直觉）。

记：

- `T`：交易日集合（按日期升序）；
- `prev(d)` = `max{ t ∈ T : t < d }` = `before(d, 1)`（不存在则视为不可解析）；
- `before(d, n)` = 严格早于 `d` 的第 `n` 个交易日（`n ≥ 1`，与 `after` 对称；回溯到日历起点仍不足返回空）；
- `after(d, n)` = 严格晚于 `d` 的第 `n` 个交易日（`n ≥ 1`）；
- `move(d, dir, n)` = `dir=PREV` → `before(d, n)`；`dir=NEXT` → `after(d, n)`；
- `at(d, 0)`：若 `d ∈ T` 则为 `d`，否则未定义。

### 4.2 真值表

**basis = DELIVERY（默认）**

| 情形 | 目标日 target | 实际取价日 |
| --- | --- | --- |
| 送货日是交易日 | 送货日 | 送货日 |
| 送货日休市/无行情，`direction=PREV` | 送货日 | `before(送货日, days)`（**往前第 X 个交易日**，`days ≥ 1`） |
| 送货日休市/无行情，`direction=NEXT` | 送货日 | `after(送货日, days)`（**延后第 X 个交易日**，`days ≥ 1`）；不足 → 「行情尚未发布」 |
| `before` 不存在（超出日历可用范围） | 送货日 | 解析失败 → 提示人工指定（不猜） |

> 送货日不再固定「最近的前一个交易日」，而是按项目配置的方向与天数位移到休市基准日；
> `NEXT` 位移比 `PREV` 晚，货已议定却要往后找价时需要业务明确选择，不能由代码替业务假设。

**basis = ORDER（报单日 + X 个交易日）**

| 情形 | `direction` / `days` | 实际取价日 |
| --- | --- | --- |
| 报单日是交易日 | 方向/天数不参与 | `offsetDays = 0` → 报单日；`≥ 1` → `after(报单日, offsetDays)` |
| 报单日休市 | `PREV`（`days ≥ 1`） | `base = before(报单日, days)`；`quoteDate = offsetDays == 0 ? base : after(base, offsetDays)` |
| 报单日休市 | `NEXT`（`days ≥ 1`） | `base = after(报单日, days)`；`quoteDate = offsetDays == 0 ? base : after(base, offsetDays)` |
| 报单日休市 | `NEXT` + `days = 0` | 不做方向位移：`base = 报单日`，`quoteDate = after(报单日, max(offsetDays, 1))`（= 旧「关闭」口径） |
| 向前回溯不足（`before` 返回空） | `PREV` | **422**：可用行情范围内找不到更早的交易日，提示人工指定 |
| 向后取不足 X 个交易日（顺延到已发布行情末端，通常是目标交易日的行情尚未发布） | — | **不换日、不报错**：`quoteDate = null`、`quoteAvailable = false`，附中文说明「…的行情尚未发布(可用行情范围 A 至 B), 请稍后重试或手工指定取价日期」 |

> **叠加关系**：ORDER 是两段——先「方向位移」得到休市基准日，再「顺延 `offsetDays`」。
> 方向与顺延同向（`NEXT`）时等价于取更远的那个交易日；方向相反（`PREV`）时先往前再往后。
> DELIVERY 只有「方向位移」一段。
>
> 向后取一律**逐日顺延**：后面的日子仍是休市/无行情（不在交易日集合内）就继续顺延到下一天，
> 直到凑满第 X 个有行情的交易日；`after` 只在顺延到已发布行情末端仍不足时返回空。
> 行情发布后重新解析即自动取到价，因此「行情尚未发布」是正常中间态而不是错误。
> 唯一的 422 仍是「向前找」的情况。

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
quoteDate        实际取价日（休市位移与顺延后）；行情尚未发布时为空
priceClosedSettleDirection 本次生效的「基准日休市位移方向」（PREV/NEXT；MANUAL 不参与）
priceClosedSettleDays      本次生效的「基准日休市位移交易日数」（0..30）
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
| `priceClosedSettleDirection` | 覆盖休市位移方向：`PREV` / `NEXT`（非法值 422） |
| `priceClosedSettleDays` | 覆盖休市位移交易日数（0..30；方向/基准的组合规则见 3.1，非法 422） |
| `periodPolicy` | 覆盖时段策略：`AUTO` / `FIXED` / `MANUAL` |
| `period` | 指定时段（`FIXED` / `MANUAL` 时的目标时段） |
| `quoteDate` | 仅 `MANUAL`：手工指定日期 |

> 命名统一：本项目/取价解析接口的**查询参数与响应字段**都用全名
> `priceClosedSettleDirection` / `priceClosedSettleDays`，不用缩写；旧参数 `settlePrevOnClosed` 已移除。

响应 `200`：

```json
{
  "projectId": "1234567890123456789",
  "basis": "ORDER",
  "offsetDays": 2,
  "priceClosedSettleDirection": "PREV",
  "priceClosedSettleDays": 1,
  "periodPolicy": "AUTO",
  "requestedPeriod": null,
  "targetDate": "2026-10-09",
  "targetIsTradingDay": true,
  "quoteDate": "2026-10-13",
  "period": "中午",
  "quoteAvailable": true,
  "suggestedDate": null,
  "explanation": "报单日期 2026年10月09日 为交易日, 取之后第 2 个交易日 2026年10月13日",
  "dateFallback": null,
  "periodFallback": null,
  "availablePeriods": ["上午", "中午"]
}
```

失败语义：项目不存在 `404`；缺必填单据日期、`offsetDays`/`priceClosedSettleDays` 越界、`priceClosedSettleDirection` 非法、`PREV`/`DELIVERY` 的天数为 0 → `422`（`VALIDATION_ERROR`）；`ORDER` 向后顺延到已发布行情末端仍不足第 X 个交易日时返回 `200` 且 `quoteDate = null`、`quoteAvailable = false`（行情尚未发布：不猜日期、不阻断，界面提示稍后重试或手工指定）；只有「向前找」找不到可用交易日（送货日/报单日早于整个可用行情范围）才 `422` 并附中文原因（不返回空壳结果，避免前端拿空日期去取价）。

权限复用现有 `MATERIAL_PRICE_MATCHES_READ`（同一业务动作，不新增权限点）。雪花 ID 一律十进制字符串（`@JsonSerialize(using = ToStringSerializer.class)`）。

> 取价本身仍走既有 `GET /api/v2/material-price-matches?quoteDate&period&source&region`：解析与匹配分离，前端可先展示「将取哪天哪时段」再做一次取价，人工覆盖时也可直接调用匹配接口。

## 6. 前端交互

### 6.1 项目资料页（`ProjectEditorOverlay`）

新增分组「取价基准」（与「允许品牌/材质」同风格，抽 `ProjectPricingBasisFields.tsx`）：

- 取价基准：送货日期（默认）/ 报单日期 + N 个交易日 / 每次手工选择；
- N（0..30，仅报单日期基准可编辑）；
- 基准日休市时：方向（往前 `PREV` / 延后 `NEXT`，默认往前）+ 第 X 个交易日（0..30，默认 1；`DELIVERY` 与 `PREV` 必须 ≥1）——**送货日期基准同样可编辑**，MANUAL 时禁用；
- 时段策略：自动取当日最新时段（默认）/ 固定时段 / 每次手工选择；
- 固定时段：上午/中午/下午（仅固定策略可编辑）。

联动：非适用字段禁用并清空，避免脏配置。

### 6.2 交付核定取价弹窗（`SalesOrderNetPriceFiller`）

- 打开时先调解析接口，展示「目标日 / 实际取价日 / 时段（实际，含回退标注）」，回退时给出中文说明（如「10-09 为交易日，按报单日 + 2 个交易日取到 10-13」或「下午无行情，已按当日最后一个可用时段（中午）取价」）；
- 提供「临时覆盖」：基准、N、时段策略、时段、手工日期（覆盖后重新解析，并按解析结果拉取匹配）；
- 取价成功后写回明细 `unitPrice`（既有逻辑与提示不变）；
- 保存时把 `priceTargetDate / priceQuoteDate / priceQuotePeriod / priceDateBasis / priceDateOffsetDays / priceClosedSettleDirection / priceClosedSettleDays` 随单据一起提交（快照）。

## 7. 边界与测试计划

**后端**

- 基准日真值表：报单日为交易日（X=0/1/N）、报单日休市 ×（`PREV`/`NEXT` × 天数 ≥1 / `NEXT`+0）、送货日为交易日/休市 ×（`PREV`/`NEXT`）、`before` 不存在、`after` 顺延到末端、跨月跨年、调休上班日有行情；
- 叠加关系：`ORDER` + `PREV` + 顺延天数（方向相反，先往前再往后）、`ORDER` + `NEXT` + 顺延天数（同向，等价更远那个）、`ORDER` + `NEXT` + 天数为 0（旧「关闭」口径，必须与旧实现同日）；
- 逐日顺延与「行情尚未发布」：报单日之后连续休市（长假）要一直顺延到下一天有行情；偏移交易日数正好取到末端时正常取价；末端不足 X 个交易日（含行情日历为空、`DELIVERY`+`NEXT`、`ORDER`+`NEXT`）返回 `quoteDate=null / quoteAvailable=false` 与中文说明而不报错；`before` 不存在仍是 422；
- 时段策略：`AUTO` 取最后时段、`FIXED` 命中、`FIXED` 缺时段回退到不晚于目标的最后时段、`FIXED` 当日无行情 → 日期回退、`MANUAL` 指定日无行情返回建议日期；
- 校验（422）：X 越界、`priceClosedSettleDays` 越界（<0 或 >30，含 `MANUAL`）、`priceClosedSettleDirection` 非法（含 `MANUAL`）、`DELIVERY` 的天数为 0、`ORDER`+`PREV` 的天数为 0、`FIXED` 缺固定时段、非 `FIXED` 残留固定时段被清空、缺对应单据日期、项目不存在；`MANUAL` 的方向/天数组合不拦（只做范围校验）；
- 迁移：V195 两表新列默认值与 CHECK 约束、9 行历史项目按四条映射回填后 `(basis, direction, days, offset)` 与旧行为一致、旧列保留并 deprecated；历史单据快照等价回填（仅已记录取价过程的行）；
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

## 9. 销售订单的「报单日期」（V180 已补）

`ORDER` 基准的锚点是单据报单日期，而销售订单原先只有送货日期（`order_date` 只存在于采购订单），
即 `ORDER` 基准在销售订单上无法落地。V180 补齐：

- 列 `so_sales_order.order_date date NOT NULL DEFAULT CURRENT_DATE`；历史行按 `created_at::date` **回填**
  （不是升级当天，否则历史单据会被记成规则上线日）；配套 `deleted_flag = false` 的部分索引；
- 实体 / 请求 / 响应新增 `orderDate`；服务侧在请求未带该字段时**保留原值**（更新场景）或**落到当天**
  （新建或老调用方），既有调用方与测试无需改动；
- 前端在销售订单「表单 / 列表列 / 详情」三处新增可编辑的「报单日期」，新建默认落单当天
  （走既有惰性 `defaultDraftValues` 机制，避免在渲染期取当前时间），编辑时显示后端值；
  同时纳入保存白名单与 strict 契约（漏了会被载荷裁剪或直接抛契约错误）；
- 日期格式：前端下发 `yyyy-MM-dd HH:mm:ss`（与 `deliveryDate` 同款），后端 `FlexibleLocalDateDeserializer`
  同时接受该格式与 ISO `yyyy-MM-dd`；响应统一输出 ISO `yyyy-MM-dd`。
