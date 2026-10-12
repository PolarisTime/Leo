# ADR-003：行情同步保持单体，采集以独立运行时角色隔离，接口区分服务面与管理面

## 状态

已采纳，2026-10-11。第 2 条（采集独立运行时角色）与第 4 条（模块边界断言）**待实施**（开关已具备，尚未拆分部署与加断言），其余为现状确认与约束。

## 背景

钢材行情（我的钢铁 Mysteel + 西本 SteelX）是交付核定「取价」的数据来源：明细按品牌别名 → 钢厂、类别 → 品名、规格覆盖、长度加价匹配出网价，再叠加项目价格规定；2026-10 又新增了「按送货日期 / 报单日 + N 个交易日自动解析取价日与时段」的能力（见 `docs/specs/2026-10-11-project-pricing-basis-rules-draft.md`）。

由此引出一个架构问题：**是否应当把行情同步模块拆分出去、独立部署，并提供标准 RESTful 的管理接口与服务接口？**

## 现状证据

以下均为本仓库实测事实，不是推测：

| 维度 | 现状 | 出处 |
| --- | --- | --- |
| 模块边界 | `com.leo.erp.market` 共 77 个 Java 文件；**后端没有任何其他模块 import `erp.market.*`**（单向依赖：`market` → `master`） | `grep -rl "erp.market" src/main/java \| grep -v /market/` 为空 |
| 负载 | Mysteel 每天 3 次（默认 cron `0 35 10,13,16 * * *`），SteelX 每天 1 次（`0 20 10 * * *`）；`MysteelProperties.enabled` 已提供总开关 | `market/schedule/SteelQuoteScheduledTasks.java`、`SteelxQuoteScheduledTasks.java`、`mysteel/MysteelProperties.java` |
| 采集基础设施 | 抓取可经 **SSH 到外部主机**执行（`fetchSshHost`）、带限速与重试、需要会话/Cookie 与解密 | `mysteel/MysteelFetcher.java#fetchViaSsh`、`mysteel/MysteelPriceDecryptor.java` |
| 消费侧位置 | 取价在**交付核定关键路径**上；匹配逻辑深度依赖商品主数据（品牌别名、类别映射、规格覆盖） | `market/service/SteelQuoteMatchService.java` |
| 交易日历 | 交易日/休市以行情日历为准，含回退与中文说明 | `market/service/TradingCalendar.java`、`PricingQuoteResolver.java`、`mk_steel_quote_closed_day`（V177） |
| 数据归属 | 单 Postgres；`market` 包内实际混着**两个上下文**：行情采集/网价（`mk_steel_article`/`mk_steel_quote`/`mk_steel_quote_closed_day`）与报价单/供应商价（`mk_quote_*`、`mk_supplier_price_*`，用户 CRUD） | V127 迁移、`market/quotation/**` |
| 部署形态 | 单个 Spring Boot 应用（context-path `/api`，版本前缀 `/v2.0`）+ 单前端 SPA；行情侧 6 个控制器 | `market/web/*.java` |

## 业内做法与方案比较

| 方案 | 收益 | 成本与适用边界 | 结论 |
| --- | --- | --- | --- |
| A. 保持单体 + 采集独立运行时角色（同制品不同 profile） | 抓取所需的 SSH 私钥、外网、Cookie、限速与重试**全部隔离在 Worker**；抓取卡死/被封不影响 ERP 请求线程池；Worker 可随时重启；零代码拆分、零分布式事务 | 多一个部署单元；仍需人工保证「ERP 实例不跑采集」 | **采用**（第 1 步） |
| B. 采集服务独立部署，共享同一个库 | 进程与环境彻底隔离；复用现有表结构 | 两个服务共写同一批表，违反数据所有权；仍无法独立演进 schema；服务间鉴权/契约/追踪全都要新建 | 不采用 |
| C. 采集服务独立部署 + 独立库，通过事件/outbox 把行情推回 ERP 的本地读模型 | 数据所有权清晰、可独立扩缩容；ERP 仍走本地读，取价关键路径不加网络跳 | 需要事件基础设施、读模型一致性与补偿逻辑；团队需具备相应运维能力 | 暂不采用，作为触发后的目标形态 |
| D. 全面微服务化（采集、匹配、取价各自服务） | 理论上的独立演进 | 把「依赖主数据的业务规则」（品牌别名/类别映射/规格覆盖）推到跨服务，交付核定关键路径多一跳网络调用，故障域与延迟都变差 | 不采用 |

## 决策

1. **不拆分服务**：行情同步继续留在模块化单体 `leo` 内，继续通过 `/api/v2.0` 提供接口。REST 是**接口设计**问题，不是部署拓扑问题——同一进程内同样可以把资源建模、权限、契约、限流做标准。
2. **采集以独立运行时角色隔离（待实施）**：同一份制品，用配置区分角色：
   - ERP 实例：关闭采集——`leo.market.steel-quote.enabled=false`（我的钢铁，另可按 `leo.market.steel-quote.sync.enabled` 只停同步）、`leo.market.steelx-quote.enabled=false`（西本）；定时任务已判定 `MysteelProperties#isEnabled()` / `sync.enabled` 与 `SteelxProperties#isEnabled()`，因此**无需改代码即可**让 ERP 实例不抓取，只承载业务接口；
   - Worker 实例：只开采集与管理接口（建议用 `@ConditionalOnProperty` 把 `market/schedule`、`mysteel`、`steelx` 整体挂到开关上），不接业务流量。
   这样以最小代价获得方案 A 收益，且完全可逆。
3. **接口区分服务面与管理面**（现在就能落地的契约工作）：
   - **服务面**（只读、可缓存、稳定契约）：`/v2.0/steel-quotes`、`/v2.0/steel-quote-calendars`、`/v2.0/material-price-matches`、`/v2.0/projects/{id}/pricing-quote-resolutions`；
   - **管理面**（低频、强权限、可审计）：`/v2.0/steel-quote-syncs`、`/v2.0/steel-quote-backfills`、休市日标记；
   - 服务面：明确 OpenAPI 契约与版本策略、独立限流与缓存策略、补数与重放必须幂等；管理面：审计日志、更严权限、禁止匿名访问。
4. **边界硬化（待实施）**：禁止 `market` 之外的模块访问 `market.domain` / `market.repository`，一律走已发布的端口/DTO（`master/api/ProjectPricingBasisQuery` 这类只读端口是范式）；建议加一条 ArchUnit/静态检查断言把它变成编译期或 CI 约束。
5. **将来若真拆分，顺序固定**：先抽「采集器」，**匹配逻辑与取价基准解析留在 ERP**（它们依赖商品主数据、属于业务规则、在交付核定关键路径上）；拆库时 `mk_steel_*` 归采集服务，ERP 保留本地读模型，通过 outbox/事件同步，**禁止两个服务共写同一批表**。

## 触发条件（满足其中 ≥2 条，回到本决策重新评估）

1. 出现 ERP 之外的消费方（BI、门户、移动端、外部系统）要按 SLA 使用行情数据；
2. 采集规模增长一个数量级（多市场/多城市/多账号，或频率显著提高，不再是每天个位数请求）；
3. 需要多地部署或独立扩缩容（当前网络位置问题已由 SSH 跳板解决）；
4. 合规要求凭证与数据物理隔离（私钥、Cookie、外部账号不进 ERP 进程）；
5. 有独立团队/负责人 own 行情数据与采集链路。

## 反指标（当前全部命中，故不拆分）

- 单团队、单库、单进程部署，无服务网格与分布式追踪；
- 取价在关键路径且强依赖主数据；
- 负载极低（每天 3 + 1 次），无扩容与 SLA 诉求；
- 拆出去需要新建：服务间鉴权、契约版本与兼容策略、配置中心、独立备份与告警——运维成本远超收益。

## 后果

- **正面**：关键路径无跨网络调用；取价匹配继续与主数据同库同事务，规则演进成本低；采集的脆弱性通过运行时角色隔离，不再与 ERP 请求线程池耦合。
- **负面**：ERP 与 Worker 需要两份部署配置，若忘记关 ERP 侧采集会导致重复抓取（需在验收清单里检查启动日志与开关状态）；暂未获得独立扩缩容能力。
- **中性**：本 ADR 不改变任何对外 API 契约；未来若触发拆分，服务面接口可原样保留，前端不需要改。

## 验证与质量门禁

- 模块边界：加断言后，任何 `market` 之外的模块引用 `market.domain`/`market.repository` 应使 CI 失败；
- 角色隔离：ERP 角色下启动，采集 cron 不触发、不产生 `mk_steel_article` 新记录；Worker 角色下不暴露业务写接口；
- 接口分面：服务面/管理面权限码分离的测试（现有 `MATERIAL_PRICE_MATCHES_READ`、`STEEL_QUOTE_CALENDARS_READ` 等）；管理面操作需审计日志。

## 关联文档

- `docs/architecture/adr-001-structured-document-status-transitions.md`、`adr-002-controlled-value-codes-and-chinese-data-migration.md`
- `docs/specs/2026-10-11-project-pricing-basis-rules-draft.md`（取价基准与交易日历）
- `docs/specs/2026-07-24-modular-monolith-decoupling-plan.md`（模块化单体既定方向）
