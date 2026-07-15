---
title: Leo 后端手写基础设施成熟框架替换审计
date: 2026-07-15
status: proposed
scope: leo 后端（Spring Boot 3.5 / Java 21）
---

# 1. 文档目的

本文检查 Leo 后端中仍由项目自行维护、但可能由成熟框架或基础设施产品接管的代码，评估替换收益、迁移风险和实施顺序。

本次审计不是以“减少手写代码”为唯一目标。采购重量、补退款、应付冲减、库存分配、来源单据约束等具有明确 ERP 语义的规则必须保留在领域层；只有通用基础设施、平台运维能力和重复样板代码才进入替换候选。

历史迁移与已实施内容见 [`2026-07-03-backend-nih-migration-plan.md`](2026-07-03-backend-nih-migration-plan.md)。本文不改写该历史记录，只描述 2026-07-15 的当前代码快照和后续建议。

# 2. 审计基线

| 维度 | 当前值 |
| --- | --- |
| 仓库 | `leo` |
| 分支 | `main`，与 `origin/main` ahead/behind 为 `0/0` |
| 工作区 | 存在物流单、销售订单、物流对账相关未提交修改；本次仅只读扫描并新增本文档 |
| Java 生产文件 | 约 768 个 |
| Java 生产代码 | 约 67,655 行 |
| 核心技术栈 | Spring Boot 3.5、Java 21、Spring Security、Spring Data JPA、PostgreSQL、Redis |
| 已使用成熟能力 | Actuator、Micrometer、OpenTelemetry、Flyway、MapStruct、Bucket4j、Tink、AWS SDK、Apache POI、Commons CSV、jjwt、TOTP |

统计数字用于判断数量级，不作为精确删行承诺。部分候选相互重叠，框架接入本身也会新增配置、适配器和测试。

# 3. 总体结论

当前识别出 9 组可评估的替换候选，其中 4 组值得优先推进、3 组仅在明确条件成立时推进、2 组收益较低。

| 结论 | 数量 | 预计影响 |
| --- | ---: | --- |
| 建议优先推进 | 4 组 | 可直接删除或明显简化约 2,000～3,000 行，并降低运行风险 |
| 条件式推进 | 3 组 | 条件满足时可继续减少约 3,000～4,000 行，但涉及架构或接口迁移 |
| 暂不替换 | 2 组 | 替换收益不足，或成熟框架不能覆盖现有业务语义 |

如果将登录、令牌、会话和 TOTP 全部迁移到外部身份提供方，理论上可累计减少约 5,000～7,000 行自维护代码；但这不是近期推荐目标，因为认证迁移风险远高于普通基础设施替换。

# 4. 优先替换项

## 4.1 数据库监控迁移到标准可观测性体系

### 当前实现

`system/database/service/DatabaseStatusService.java` 约 876 行，自行完成：

- PostgreSQL 连接、活动会话、表健康和索引健康查询；
- `pg_stat_statements` 探测与结果解析；
- Redis `INFO` 读取与字符串解析；
- HikariCP 参数和连接池状态读取；
- JDBC `ResultSet` 映射、比率计算和展示格式化。

### 目标方案

```text
Spring Boot Actuator
  → Micrometer / OpenTelemetry
  → Prometheus
  → Grafana
```

应用只保留 ERP 管理页面真正需要的少量摘要和跳转信息，完整的数据库、连接池和 Redis 监控交给标准指标体系。

### 判断

| 项目 | 结论 |
| --- | --- |
| 可简化规模 | 约 600～800 行 |
| 业务侵入 | 低 |
| 运维收益 | 高 |
| 优先级 | P0 |

项目已经引入 Actuator、Micrometer 和 OpenTelemetry，继续维护完整的自研数据库监控页面存在重复建设。

## 4.2 数据库备份和导出任务外部化

### 当前实现

`system/database` 包约 1,968 行，其中 `DatabaseExportTaskService` 自行维护：

- `ThreadPoolExecutor` 和自定义工作线程；
- 排队、执行、完成、失败、过期状态；
- 应用重启后的中断任务协调；
- 下载令牌、下载资源和过期清理；
- `pg_dump` 外部进程调用与文件生命周期。

### 目标方案

首选把数据库备份交给 `pgBackRest`、PostgreSQL Operator 或部署平台。ERP 只查询备份结果，不直接持有数据库口令并执行完整备份。

如果业务明确要求“从 ERP 页面发起导出”，则采用：

- Spring Batch：持久化 Job/Step、失败恢复和执行历史；
- Quartz：需要持久化调度时使用；
- 对象存储：保存导出产物并生成短期下载地址；
- ERP 表：仅保存业务申请、平台任务 ID 和下载状态。

### 判断

| 项目 | 结论 |
| --- | --- |
| 可简化规模 | 约 600～900 行 |
| 安全收益 | 减少应用持有高权限数据库口令和执行外部进程的范围 |
| 可靠性收益 | 避免 JVM 线程池承担持久任务恢复职责 |
| 优先级 | P0 |

## 4.3 复杂业务单据使用 Spring StateMachine

### 当前实现

当前没有引入 Spring StateMachine。状态迁移由以下机制共同完成：

- `StatusConstants` 使用字符串维护状态和 `当前状态->目标状态` 集合；
- `AbstractCrudService.updateStatus` 执行通用迁移流程；
- `CrudStatusGuard` 拼接字符串并校验允许集合；
- `WorkflowTransitionGuard` 校验进入或离开保护状态时的审核权限；
- 各模块 `beforeStatusUpdate`、审核命令服务和下游 Guard 编写业务前置条件与副作用。

核心通用代码约 767 行；计入采购、销售、出入库的状态 Guard 和同步服务后，受影响代码超过 1,500 行。

### 适用边界

只对存在多状态、反向迁移、跨单据联动和完成态保护的聚合使用状态机：

- 采购订单；
- 采购入库；
- 销售订单；
- 销售出库；
- 后续确认确有复杂生命周期的对账或合同单据。

客户、供应商、仓库、打印模板等简单的“正常/禁用”状态继续使用枚举和普通校验，不引入状态机。

### 职责边界

Spring StateMachine 负责：

- 状态、事件和合法迁移；
- Guard 的统一触发；
- 迁移监听与审计上下文；
- 拒绝前端直接写入完成态。

领域服务继续负责：

- 入库数量、重量和来源一致性校验；
- 盘螺过磅差额和补退款计算；
- 未付应付冲减；
- 库存、财务和下游引用保护；
- 数据库事务、锁和幂等。

### 接口要求

状态接口应表达业务事件，而不是允许客户端提交任意目标状态：

```http
POST /purchase-orders/{id}/audit
POST /purchase-orders/{id}/reverse-audit
POST /purchase-inbounds/{id}/audit
POST /purchase-inbounds/{id}/reverse-audit
```

不再把通用 `PATCH /{id}/status` 作为复杂单据的主要状态入口。

### 判断

| 项目 | 结论 |
| --- | --- |
| 净删行 | 有限；框架配置、事件和测试会抵消部分删行 |
| 主要收益 | 状态规则集中、事件语义明确、迁移图可测试、减少非法入口 |
| 主要风险 | 把财务和库存副作用错误塞入状态机 Action，导致事务边界模糊 |
| 优先级 | P1，先以采购订单和采购入库试点 |

## 4.4 复杂查询逐步采用 jOOQ

### 当前实现

当前约 18 个文件、3,500 行代码使用 `JdbcTemplate`、`EntityManager` 或手工 JDBC 映射，集中在：

- 库存报表；
- 现金流水和应收应付；
- 采购、销售、物流来源候选查询；
- 聚合报表和复杂分页查询。

### 目标方案

保留 Spring Data JPA 处理普通实体 CRUD；仅为复杂查询引入 jOOQ Codegen：

```text
普通 CRUD                 → Spring Data JPA
复杂报表/多表聚合/窗口函数 → jOOQ
极少数数据库特定优化 SQL   → 明确封装的原生 SQL
```

jOOQ 的目标不是消灭 SQL，而是提供 Schema 生成类型、字段和 JOIN 编译期检查、动态条件组合及稳定的 Record 映射。

### 判断

| 项目 | 结论 |
| --- | --- |
| 可简化规模 | 约 500～1,000 行映射和动态拼装样板代码 |
| 主要收益 | 数据库字段重构可编译检查，减少 ResultSet 和字符串字段错误 |
| 迁移方式 | 新查询优先使用，旧查询按维护频率逐步迁移 |
| 优先级 | P1 |

# 5. 条件式替换项

## 5.1 外部身份提供方

认证 Service、JWT、Session、API Key 和 TOTP 约 4,200 行；权限模块另有约 2,100 行。

只有出现以下需求时，才建议评估 Keycloak 或其他 OIDC 身份提供方：

- 多套系统统一登录；
- 企业目录、LDAP 或第三方身份源；
- 集中密码、MFA 和会话策略；
- 合规要求由独立身份平台承担认证审计。

外部身份提供方可以接管登录、密码、令牌、会话、TOTP 和账号锁定，但不能替代 ERP 内部的：

- 资源操作权限；
- 单据审核权限；
- 部门、本人和自定义数据范围；
- 菜单可见性；
- ERP API Key 的业务授权。

该方案理论上可减少约 2,500～4,000 行，但属于架构级迁移，当前优先级为 P2。

## 5.2 权限接入层收敛到 Spring Security

当前自定义 `@RequiresPermission`、权限 Aspect、模块 Guard 和 Principal 获取逻辑，可逐步收敛到 Spring Security Method Security：

```java
@PreAuthorize("@erpAuthorization.canAudit('purchase-order')")
```

可以用自定义 `AuthorizationManager` 统一资源权限入口，预计简化 300～600 行接入样板代码。

`ResourcePermissionCatalog`、菜单映射和数据范围仍然属于业务授权模型，应保留。该项应在状态事件接口稳定后实施，优先级为 P2。

## 5.3 分页参数采用 Spring Data Pageable

自定义 `PageQuery`、参数解析器和配置约 376 行，可由 `Pageable`、`@PageableDefault` 和 Spring Data 排序解析接管一部分，预计简化 150～250 行。

以下现有约束仍需保留：

- 排序字段白名单；
- 前端页码是否从 1 开始的兼容转换；
- 不同模块默认页大小；
- 稳定排序和最大页大小限制。

收益较低，优先级为 P3。

# 6. 暂不替换项

## 6.1 通用导入导出框架

`common/excel` 约 858 行，存在注解解析、Record 反射构造、类型转换和错误聚合代码，但底层已经使用成熟的 Apache POI 与 Commons CSV。

当前不建议仅为减少反射代码更换库。替换条件应是出现明确运行问题，例如：

- 大文件导致内存峰值过高；
- 需要真正的流式导入或导出；
- 多模板映射规则已经超过现有注解模型承载能力。

发生这些问题时再评估 POI SAX/Streaming、FastExcel 或专用批处理方案。打印模板、套打坐标和 ERP 字段映射仍需保留。

## 6.2 ID 与业务单号

`SnowflakeIdGenerator` 本身约 68 行，可以替换为 PostgreSQL Sequence 或成熟 TSID 实现，但主键策略变更会影响数据库、缓存键、接口和历史数据，收益不足。

业务单号包含前缀、日期、预分配和并发规则，属于业务能力，不能简单用 UUID 或框架默认 ID 替代。

# 7. 明确保留的实现

以下代码虽然由项目维护，但已有成熟底层库或包含明确业务语义，不应纳入近期替换：

| 能力 | 当前基础 | 结论 |
| --- | --- | --- |
| HTTP 幂等 | Redis Lua，请求指纹、处理中/已完成/参数冲突语义 | 保留；没有可直接覆盖当前协议的 Spring 标准组件 |
| 业务操作日志 | MVC 拦截、业务单号、模块、操作人和结果状态 | 保留；JaVers/Envers 的实体差异审计不能直接替代 |
| 限流 | Bucket4j + Spring Web 接入 | 已使用成熟框架，保留接入层 |
| 加密 | Google Tink AEAD | 已使用成熟框架 |
| TOTP | `dev.samstevens.totp` | 已使用成熟框架 |
| JWT | jjwt + Spring Security | 除非整体迁移 OIDC，否则保留 |
| 对象存储 | AWS SDK v2 | 已使用成熟框架 |
| DTO 映射 | MapStruct | 已使用成熟框架 |
| Flyway | Flyway PostgreSQL | 已使用成熟框架 |
| 打印渲染 | iText、Apache POI、C-Lodop 适配 | 业务模板和坐标布局必须由项目维护 |
| 财务、库存、重量规则 | ERP 领域服务 | 必须保留在领域层，不交给通用工作流或规则引擎 |

# 8. 推荐实施路线

## 阶段一：移出平台职责

1. 对照现有管理页面字段建立 Actuator/Micrometer 指标覆盖表。
2. 在 Grafana 建立 PostgreSQL、Redis、HikariCP 看板。
3. 将数据库备份迁移到 pgBackRest 或部署平台。
4. 删除已由平台覆盖的数据库监控 SQL、Redis `INFO` 解析和 JVM 备份线程池。

阶段目标：减少约 1,200～1,700 行自研运维代码，并降低数据库高权限凭据驻留风险。

## 阶段二：采购状态机试点

1. 将采购订单和采购入库状态改为类型化枚举。
2. 定义业务事件，而不是允许客户端直接提交目标状态。
3. 用 Spring StateMachine 表达迁移和 Guard 调度。
4. 保持库存、财务、过磅和补退款逻辑在领域服务内。
5. 用事务应用服务编排状态机、数据库锁、状态持久化和跨单据同步。
6. 覆盖审核、反审核、重复事件、并发审核和 Action 失败回滚。

阶段目标：先验证一致性和可维护性，不以删行数量作为验收标准。

## 阶段三：复杂查询类型化

1. 建立 jOOQ Codegen 与 Flyway Schema 的生成流程。
2. 新增复杂报表优先使用 jOOQ。
3. 按修改频率迁移手写 ResultSet 映射，不进行全量机械重写。
4. 保留 JPA 聚合写入和普通 CRUD。

## 阶段四：身份与权限决策

1. 先确认是否存在多系统 SSO、LDAP 或集中身份治理需求。
2. 有明确需求时再制作 Keycloak/OIDC PoC。
3. 独立评估 Spring Security `AuthorizationManager`，不得与身份迁移绑成一次大改。

# 9. 验收原则

每一类替换必须满足：

- 现有业务行为和权限边界有明确基线；
- 新组件故障模式、降级策略和可观测指标已定义；
- 数据库结构变更通过新的递增 Flyway 脚本实施；
- 不修改已执行的迁移脚本；
- 关键状态、财务、库存和权限路径有自动化回归；
- 可独立回滚，不与无关业务功能混合发布；
- 框架接入后的维护代码确实少于被替换代码，避免只增加一层适配器。

# 10. 最终建议

近期只推进以下四项：

1. 数据库监控标准化；
2. 数据库备份外部化；
3. 采购领域 Spring StateMachine 试点；
4. 新复杂查询采用 jOOQ。

认证中心、权限接入和分页统一必须在需求成立时再推进。HTTP 幂等、操作日志、Excel 映射、ID、打印及 ERP 领域规则保持现状，避免为了使用框架而制造新的复杂度。
