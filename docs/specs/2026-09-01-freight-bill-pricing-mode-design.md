---
title: 物流单按吨结算与整车一口价设计
date: 2026-09-01
status: proposed
scope: Leo 后端、Aries 前端、物流对账、打印与报表
---

# 物流单计价方式设计

## 1. 结论

物流单当前按“总吨位 × 每吨单价”计算运费。新增“整车一口价”是可行的，但不能只把前端输入值除以吨位后覆盖现有 `unitPrice`：那会丢失原始计价语义，编辑、打印、对账和后续扩展都会产生歧义。

推荐采用“计价模式 + 模式专属输入 + 标准化结果”的模型：

```text
pricingMode         计价模式
pricingAmount       用户输入的计价金额（与 pricingMode 组成完整语义）
unitPrice           每吨标准化单价（现有字段，后端计算）
totalFreight        实际结算总额（财务和对账使用）
```

模式固定为：

| 编码 | 中文 | `pricingAmount` 含义 | `unitPrice` 含义 | `totalFreight` 计算 |
| --- | --- | --- | --- | --- |
| `PER_TON` | 按吨结算 | 每吨单价（元/吨） | 等于 `pricingAmount` | `totalWeight × pricingAmount` |
| `VEHICLE_FLAT_PRICE` | 整车一口价 | 整车金额（元/车） | `pricingAmount ÷ totalWeight` 的标准化参考值 | `pricingAmount` 按金额精度保存 |

因此，“后端保存时用一口价除以吨位求出单价”本身可以保留，但只能作为标准化投影；用户原始输入由 `pricingMode + pricingAmount` 持久化，不能把推导值当成唯一事实。

## 2. 方案比较

| 方案 | 优点 | 问题 | 结论 |
| --- | --- | --- | --- |
| 一口价除以吨位后只保存到 `unitPrice` | 改动最少 | 丢失计价模式和原始报价，无法可靠回显、审计和打印 | 不采用 |
| `pricingMode + vehicleFlatAmount` | 语义直观 | 按吨模式字段为空，且整车金额与 `totalFreight` 永远重复 | 不采用 |
| `pricingMode + pricingAmount`，后端计算结果 | 输入事实统一、无模式专属空列，API 可建模为判别联合，后续扩展更自然 | 下游需理解 `pricingAmount` 的单位由模式决定 | **采用** |
| 独立计价规则表 | 可表达复杂阶梯、里程和多段费用 | 当前只有两种简单模式，增加表、版本和关联生命周期 | 当前不采用 |

`pricingAmount` 不是含义不明的通用“单价”：它必须和 `pricingMode` 一起出现，API、服务和前端都按判别联合处理。界面根据模式显示明确标签，不能固定显示“单价”。

## 3. 当前实现与问题

当前 Leo 物流单模型：

- `lg_freight_bill.unit_price` 是每吨单价，数据库精度为 `numeric(12,2)`；
- `lg_freight_bill.total_weight` 由导入的销售订单明细重量汇总；
- `lg_freight_bill.total_freight` 由后端执行 `totalWeight × unitPrice`，金额保留两位小数；
- `FreightBillRequest.unitPrice` 目前必填；Aries 物流单表单只有一个“单价”输入框；
- 物流对账和打印主要使用 `totalFreight`，部分打印明细仍展示 `unitPrice`。

如果直接复用 `unitPrice` 输入整车金额，会出现：

1. 保存后无法区分用户输入的是元/吨还是元/车；
2. 编辑时无法可靠回显原始整车报价；
3. `unitPrice` 除法四舍五入后再乘吨位，可能与原始一口价产生分；
4. 报表、对账和打印无法选择正确的单位；
5. 将来扩展按车、按趟、按距离或分段价时继续产生字段歧义。

## 4. 领域模型

### 4.1 字段定义

物流单头新增：

| 领域字段 | 数据库字段 | 类型 | 必填规则 | 说明 |
| --- | --- | --- | --- | --- |
| `pricingMode` | `pricing_mode` | `VARCHAR(32)` | 非空，默认 `PER_TON` | 仅允许 `PER_TON`、`VEHICLE_FLAT_PRICE` |
| `pricingAmount` | `pricing_amount` | `NUMERIC(18,8)` | 非空 | 用户原始输入；按吨模式单位为元/吨，整车模式单位为元/车 |

现有字段调整：

| 字段 | 新语义 |
| --- | --- |
| `unitPrice` / `unit_price` | 后端保存的每吨标准化参考单价；按吨模式等于 `pricingAmount`，整车模式下由整车金额除以总吨位得到，建议精度调整为 `NUMERIC(18,8)` |
| `totalFreight` / `total_freight` | 最终应付运费，金额精度保持 `NUMERIC(14,2)`；整车模式直接取整车金额，不从已舍入的 `unitPrice` 反算 |

`pricingAmount` 保存报价依据，`totalFreight` 保存结算结果。整车模式下两者按两位金额精度数值相等；按吨模式下二者通过总吨位计算关联。两者不是重复事实，任何写入路径都必须在同一事务内重新计算并校验。

### 4.2 业务不变量

- `pricingMode` 缺省时按 `PER_TON` 处理，兼容现有客户端；显式传入未知模式返回 `422`。
- 新客户端必须提供 `pricingAmount >= 0`；单位和金额精度由 `pricingMode` 决定。旧客户端省略 `pricingMode`、`pricingAmount` 时，兼容层仅在按吨语义下使用现有 `unitPrice` 作为 `pricingAmount`。
- `unitPrice` 和 `totalFreight` 是计算结果。新客户端不得把它们作为计价输入，后端不得信任请求中的同名值。
- 整车模式必须能识别明确车辆（`vehicleId` 或有效 `vehiclePlate`），否则“整车”没有稳定业务对象，返回 `422`。
- 物流单明细仍必须从允许状态的销售订单整单导入；`totalWeight` 始终由后端根据明细重算，不能信任请求中的总重量。
- 总吨位必须大于零。当前明细已有正重量校验，因此整车模式不能出现除零。
- 草稿可修改计价模式和输入金额；已审核或已被对账/付款引用的物流单沿用现有下游保护，不允许改变计价模式、整车金额或标准化单价。

## 5. 计算规则与精度

后端在保存事务内按以下顺序执行：

1. 锁定并读取销售订单来源，按明细重算 `totalWeight`；
2. 校验计价模式和模式专属输入；
3. 计算 `unitPrice`、`totalFreight`；
4. 持久化物流单和明细，返回计算后的响应。

```text
PER_TON:
  pricingAmount = normalize(request.pricingAmount, 8)
  unitPrice = pricingAmount
  totalFreight = round(totalWeight × pricingAmount, 2, HALF_UP)

VEHICLE_FLAT_PRICE:
  pricingAmount = round(request.pricingAmount, 2, HALF_UP)
  totalFreight = pricingAmount
  unitPrice = round(totalFreight ÷ totalWeight, 8, HALF_UP)
```

示例：总吨位 `12.500` 吨、整车金额 `1250.00` 元：

```text
unitPrice = 1250.00 ÷ 12.500 = 100.000000 元/吨
totalFreight = 1250.00 元
```

不得使用已经舍入到两位的 `unitPrice × totalWeight` 覆盖整车模式的 `totalFreight`。对账、付款、报表和财务汇总一律以 `totalFreight` 为金额事实；`unitPrice` 仅用于标准化展示、排序或兼容既有下游字段。

## 6. API 契约

### 6.1 请求

`POST /api/v2/freight-bills` 和 `PUT /api/v2/freight-bills/{id}` 增加：

```json
{
  "pricingMode": "VEHICLE_FLAT_PRICE",
  "pricingAmount": 1250.00
}
```

请求规则：

- 旧客户端省略 `pricingMode`、`pricingAmount` 时视为 `PER_TON`，兼容层继续读取现有 `unitPrice`；该兼容只允许按吨模式，并设置下线期限；
- 新客户端统一发送 `pricingMode + pricingAmount`；前端字段标签和单位由模式决定；
- 模式非法、计价金额缺失/负数、整车模式车辆缺失或旧新输入冲突时返回 `422`；
- `totalWeight`、`totalFreight` 和整车模式下的 `unitPrice` 不接受为可信输入，均由后端计算。

新契约的业务形态等价于以下判别联合：

```text
{ pricingMode: "PER_TON", pricingAmount: 元/吨 }
{ pricingMode: "VEHICLE_FLAT_PRICE", pricingAmount: 元/车 }
```

### 6.2 响应

列表和详情响应增加：

```json
{
  "pricingMode": "VEHICLE_FLAT_PRICE",
  "pricingAmount": 1250.00,
  "unitPrice": 100.000000,
  "totalWeight": 12.500,
  "totalFreight": 1250.00
}
```

Aries 的 Zod schema、请求类型和保存字段必须同步更新。响应中的 `pricingAmount` 是用户原始计价输入，`unitPrice` 在两种模式下都代表每吨标准化值。

## 7. 数据库迁移

当前主线最高版本为 `V126`，实施时新增递增 Flyway，例如 `V127__add_freight_bill_pricing_mode.sql`，不得修改 `V1__baseline.sql` 或已执行脚本。

迁移内容分为两条连续脚本，避免旧版本服务遇到尚未完成回填的非空约束：

**V127：兼容 schema**

1. `lg_freight_bill` 新增 `pricing_mode VARCHAR(32) NOT NULL DEFAULT 'PER_TON'`；
2. 新增可空的 `pricing_amount NUMERIC(18,8)`；
3. 将 `unit_price` 扩大到 `NUMERIC(18,8)`，避免整车金额除以重量后丢失参考精度；
4. 不在本脚本中删除旧字段或设置 `pricing_amount NOT NULL`，确认旧版本仍可读写。

**V128：数据回填与约束**

1. 存量数据回填 `pricing_mode = 'PER_TON'`、`pricing_amount = unit_price`，保持既有运费含义不变；
2. 校验行数、空值、金额和抽样重算结果；
3. 设置 `pricing_amount NOT NULL`，增加模式白名单和金额非负约束；
4. 迁移成功后再部署支持整车模式的 Leo/Aries。

该迁移只增加字段和扩宽精度，不改变历史 `total_freight`。如果目标环境存在已执行旧版本服务，必须先停写或先部署兼容版本，禁止旧服务在新约束下继续写入。

## 8. 前端交互

物流单编辑表单增加计价方式选择：

- `按吨结算`：输入控件绑定 `pricingAmount`，显示“每吨运价（元/吨）”；
- `整车一口价`：同一模式化控件绑定 `pricingAmount`，显示“整车运费（元/车）”；不能继续把控件命名为 `unitPrice`；
- 整车模式要求先选择车辆，再允许保存；
- 切换模式时不自动把旧输入值当作新模式金额。应清空或明确转换，并在切换前提示用户确认；
- 保存后回显后端返回的 `pricingMode`、`pricingAmount`、`unitPrice` 和 `totalFreight`；
- 列表和详情显示计价方式，金额列优先显示 `totalFreight`。`unitPrice` 在整车模式标注“参考元/吨”，避免误读。

前端只负责交互校验和展示，不能自行用页面上的总重量推导最终金额；后端仍必须重新读取来源明细并计算。

## 9. 下游影响

实施前必须逐项核对以下消费者：

| 下游 | 处理要求 |
| --- | --- |
| 物流对账 | 继续按 `totalFreight` 进入对账和付款；如需展示单价，同时读取 `pricingMode` 和 `unitPrice` |
| 打印模板 | 增加计价方式和“整车运费/参考元每吨”标签；不能在整车模式固定打印“单价（元/吨）”而不说明其为参考值 |
| 报表/导出 | 金额统计使用 `totalFreight`；导出增加计价方式和计价金额字段，避免用户把 `unitPrice` 当实际报价 |
| 事件/outbox | 事件 payload 增加 `pricingMode`、`pricingAmount` 和 schema 版本；消费者只使用 `totalFreight` 做金额结算 |
| 搜索/排序 | 物流单金额排序使用 `totalFreight`；不要用标准化 `unitPrice` 代替实际结算金额 |
| 缓存/草稿 | 计价方式和 `pricingAmount` 必须进入草稿序列化、缓存 key 失效和恢复校验 |

## 10. 测试与验收

### 后端

- 按吨模式保持现有 `12.5 × 100 = 1250.00` 行为；
- 整车模式正确计算 `pricingAmount ÷ totalWeight`，并保持 `totalFreight` 等于整车计价金额；
- 总吨位为零、负数或缺失时拒绝保存；
- `pricingAmount` 为空、负数或与旧 `unitPrice` 输入冲突时返回 `422`；
- 整车模式缺少车辆身份时拒绝保存；
- 高精度除法和金额舍入符合 `HALF_UP`，不因 `unitPrice × totalWeight` 二次舍入改变整车金额；
- 草稿编辑切换模式后重新计算；已审核或下游引用后修改被阻断；
- 历史数据默认按吨模式读取，物流对账、付款和报表金额不变；
- Controller、DTO、Mapper、Flyway 和跨模块 `FreightBillStatementSourceQuery` 契约同步通过。

### 前端

- 计价方式切换显示正确输入项和单位；
- 整车模式不能在未选择车辆时提交；
- 保存请求只发送对应模式字段，不发送相互冲突的价格输入；
- 详情和列表正确回显模式、整车金额、参考吨价和总运费；
- Zod schema 拒绝非法模式、负数和缺失的模式专属字段；
- 导入销售订单后重量变化时，整车模式仍以服务端返回的总吨位和整车金额为准；
- 中英文文案、打印入口和草稿恢复均覆盖。

## 11. 实施范围与规模评估

预计是中等规模跨端变更，不涉及销售订单、物流单来源明细或状态机重构：

- Leo：实体、请求/响应 DTO、服务计算、Mapper、跨模块查询快照、Flyway、打印运行时和约 4-8 个测试文件；
- Aries：物流单 Zod schema、页面配置、计价方式控件、保存/回显适配、语言包和相关测试；
- 数据库：建议 2 个 post-baseline Flyway 脚本（扩展列、回填并收紧约束），必要时扩宽 `unit_price` 精度；
- 需要联调物流对账、打印、报表和事件消费者，不能只验证物流单保存接口。

## 12. 发布与回滚

推荐发布顺序：

1. 先在生产副本执行 V127/V128 迁移演练、精度校验和历史数据抽样；
2. 执行只增加可空列并扩宽精度的 V127，确认旧版本仍可读写；
3. 部署能够读取旧数据、默认 `PER_TON` 并同时写入新列的 Leo 兼容版本；
4. 执行 V128 回填 `pricingMode/pricingAmount` 并设置非空、模式白名单和金额约束；
5. 发布支持整车模式的 Leo/Aries，执行物流单、对账、打印、报表和事件冒烟后恢复流量。

代码回滚不能回滚数据库字段。若新版本已经产生整车数据，优先前滚修复；只有在没有整车新数据或已验证反向迁移时，才允许回退到旧代码。任何备份恢复都必须明确恢复点之后新增数据的丢失范围并取得业务批准。

## 13. 暂不纳入

- 不新增独立计价规则表或工作流引擎；
- 不把物流单明细的 `settlementMode` 改造成物流单头计价方式；两者属于不同业务语义；
- 不在本次实现按趟、按距离、分段价、最低运费或阶梯价；这些模式应在确认计价规则、舍入和审计要求后单独扩展；
- 不修改历史物流单的 `totalFreight`，不通过全库重算改变既有财务事实。

## 14. 实施前置条件

- 产品确认整车一口价是否必须绑定车辆，以及车辆缺失时的业务例外；
- 财务确认 `totalFreight` 是唯一结算金额，整车金额是否需要在打印和导出中显示；
- 后端确认 `unit_price` 扩宽到 `NUMERIC(18,8)` 对现有查询、打印和接口精度的影响；
- 前端确认旧草稿和旧客户端兼容策略；
- 完成迁移脚本、接口契约和跨模块消费者清单后，再进入代码实现。
