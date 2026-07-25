---
title: Leo 后端模块化单体解耦计划
date: 2026-07-24
last_reviewed: 2026-07-25
status: in-progress
scope: leo 后端（Spring Boot 3.5 / Java 21 / Spring Data JPA / Spring Modulith）
---

# 1. 决策摘要

Leo 后端继续采用模块化单体，不拆分微服务，不引入消息队列，不进行大规模分层重写。

当前采购、销售、采购入库、销售出库和物流主流程已经跑通。架构整改的第一优先级是解除模块双向依赖，降低后续修改的联动范围；整改不得以“架构升级”为理由重写已经稳定运行的业务规则、单据流程、事务边界或数据库模型。

本计划作出以下明确决策：

| 事项 | 决策 |
| --- | --- |
| 部署形态 | 保留单 JVM、单数据库的模块化单体 |
| 首要代码任务 | 小步解除模块环和跨模块 Repository/实体写入 |
| 主业务链 | 已跑通，只做等价解耦，不做流程重写 |
| 对账与财务 | 当前未实际使用，只隔离边界，不投入复杂重构 |
| 强一致协作 | 使用同步应用端口，并保留原事务和锁顺序 |
| 最终一致副作用 | 复用 Spring Modulith 可靠事件，不新增 MQ |
| 单据状态 | 逐模块替换反射保护，不一次性引入状态机 |
| 打印配置 | 人工导入是接受的运维前置条件，不建设自动 seed 或自动导入 |
| 验证方式 | 编译、静态分析、依赖规则、Flyway 校验和主流程烟雾检查，不恢复活动测试目录 |

## 1.1 在线调研依据与本地落地

本节资料于 2026-07-25 通过公开官方文档核对。采用原则时以本项目的单人维护、单 JVM、单数据库和主流程已稳定为约束，不把参考架构整套照搬。

| 来源 | 原始结论 | 本项目落地 |
| --- | --- | --- |
| [Spring Modulith 1.4 Fundamentals](https://docs.spring.io/spring-modulith/reference/1.4/fundamentals.html) | 模块由提供接口、内部实现和所需接口组成；支持从简单模块逐步演进；命名接口和 `allowedDependencies` 可以表达公开边界 | 复用当前 `spring-modulith 1.4.12`，只为真实跨域调用建立小型 `api` 命名接口，不新增通用端口层 |
| [Spring Modulith 1.4 Verification](https://docs.spring.io/spring-modulith/reference/1.4/verification.html) | `ApplicationModules.verify()` 检查模块环、对内部包的非法访问和显式依赖白名单 | 依赖规则必须进入 CI；受仓库测试文件政策限制，验证器放在独立架构工具入口，不放入 `src/test`，也不在生产启动时执行 |
| [Spring Modulith 1.4 Events](https://docs.spring.io/spring-modulith/reference/1.4/events.html) | Spring 事件默认同步执行；事务事件发布登记可与原事务一起持久化，失败记录可保留和重投；完成记录需要治理 | 强一致领域动作继续用显式同步端口；操作日志等次要副作用使用现有可靠事件，并监控、重投和清理 `event_publication` |
| [Spring Framework Transaction-bound Events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html) | `@TransactionalEventListener` 默认绑定 `AFTER_COMMIT`，也可选择提交前、回滚后或完成后阶段 | 每类事件必须在接口或文档中声明事务阶段；禁止仅靠注解名称猜测一致性语义 |
| [Martin Fowler: Monolith First](https://martinfowler.com/bliki/MonolithFirst.html) | 微服务有额外成本，且稳定边界通常要在实践中形成；单体内保持良好模块化是后续演进前提 | 当前规模继续使用模块化单体；只有出现独立团队、独立扩缩容或独立发布的真实需求才重评服务拆分 |
| [Flyway Baseline On Migrate](https://documentation.red-gate.com/fd/flyway-baseline-on-migrate-setting-277578974.html) | 官方明确警告：启用该选项会移除“避免误迁移错误数据库”的安全网 | 生产显式关闭 `baseline-on-migrate`，历史库接管使用一次性人工流程 |
| [OWASP Secrets Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html) | 密钥不应硬编码在源码；应集中管理其生成、审计、轮换和生命周期 | 数据库转储及离线初始化制品不得携带真实业务数据或具体数据主密钥，密钥轮换必须包含密文重加密方案 |

Spring Modulith 官方允许用开放模块辅助旧系统渐进迁移，但同时指出完全模块化后继续使用开放模块通常代表封装不足。本项目不采用“把所有模块设为 `OPEN` 后宣布完成”的做法；如短期使用开放模块，必须登记待收口依赖和删除条件。

## 1.2 ADR-001：采用渐进式模块化单体

状态：`Accepted`。

| 方案 | 收益 | 代价与风险 | 决策 |
| --- | --- | --- | --- |
| 保持当前目录分域，不增加边界门禁 | 当前投入最低 | 双向依赖继续增长，目录无法保护模块内部 | 拒绝 |
| 渐进式模块化单体、同步端口、可靠副作用事件 | 保留单事务和单库，能按依赖边小步迁移，回归范围可控 | 迁移期仍会保留部分旧依赖，需要持续维护违规基线 | 采用 |
| 一次性完整六边形架构或全量 DDD 重写 | 理论分层最整齐 | 大量接口和搬迁不产生直接业务价值，极易破坏已跑通流程 | 拒绝 |
| 拆分微服务和独立数据库 | 可独立部署和扩缩容 | 当前没有独立团队或扩缩容需求，却立即引入网络、分布式事务和运维成本 | 拒绝 |
| 所有跨域协作都改为异步事件 | 编译依赖较少 | 来源额度、删除保护和状态同步会失去当前强一致语义 | 拒绝 |

接受的权衡：本计划不追求短期清零全部违规依赖，也不追求领域模型形式完整；优先确保每个迭代只减少一组耦合，并保持现有事务、锁顺序和业务结果。

## 1.3 架构重访触发条件

只有满足下列至少一项并有运行数据支持时，才重新评估部署形态：

1. 某模块需要与主应用明显不同的扩缩容策略；
2. 出现能够独立负责、独立值班和独立发布的长期团队；
3. 合规要求某类数据或处理链必须物理隔离；
4. 单数据库事务或资源竞争已经成为无法通过索引、查询和容量治理解决的瓶颈；
5. 外部系统集成需要明确的异步边界、重放和独立可用性目标；
6. 对账或财务实际启用并形成经过确认、与交易主链不同的生命周期。

在触发条件出现前，增加服务、消息队列、CQRS 或事件溯源均视为 YAGNI。

# 2. 业务基线与约束

## 2.1 已跑通的主流程

以下能力已经进入可用状态，架构改造必须保持其现有行为：

```text
采购订单 ──> 采购入库
    │            │
    └────────────┴──> 销售订单 ──> 销售出库
                              └──> 物流单
```

该图表达业务来源关系，不表示所有单据必须串成一条线。物流单当前以销售订单为主要来源，不要求先生成销售出库单。

必须保持的主链不变量包括：

1. 来源明细 ID、来源单号和来源行号可追溯；
2. 来源数量、重量和排他占用在同一事务内校验；
3. 采购入库过磅、重量回写和采购完成状态同步保持现有口径；
4. 销售订单、销售出库和物流单的来源限制及反向删除保护保持现有行为；
5. 仓库、批号、商品、客户、供应商、项目和结算主体快照不丢失；
6. 软删除、审核、反审核和完成态的领域限制继续在业务层执行；
7. 雪花 ID 单据编号和现有数据库唯一约束不变化。

## 2.2 低使用模块的处理边界

客户对账、物流对账、付款、收款及财务汇总当前未实际投入日常业务。本计划只要求：

1. 消除 `statement <-> finance` 的双向编译依赖；
2. 阻止它们反向污染采购、销售和物流主链；
3. 保证现有代码继续编译、应用继续启动、已有数据可以读取；
4. 不补做未确认的财务业务规则；
5. 不建设会计总账、复式记账、CQRS、事件溯源或新的结算引擎；
6. 不因解耦任务主动启用或推广这些模块。

如果以后确认实际启用对账或财务，应另行进行业务口径评审。届时以真实使用场景决定是否继续深化领域模型，不在本计划中预留复杂框架。

## 2.3 打印配置运维前置条件

打印模板由运维人员人工导入是已接受的前置条件：

1. 新环境完成应用和数据库部署后，由运维人员按结算主体、单据类型导入所需模板；
2. 模板版本、校验值和导入时间记录在发布清单中；
3. 未导入模板时，打印功能不可用属于环境未就绪，不属于应用启动失败；
4. 发布烟雾检查必须验证实际使用的模板能够打印或导出；
5. 本计划忽略 `db/seed/S*.sql` 的自动执行问题；
6. 现有 `PrintTemplateFileSyncRunner` 只同步已经人工导入且标记为 `FILE` 的记录内容，不视为模板自动导入；本计划不修改它、不增加启动时自动插入，也不让离线初始化脚本自动携带模板数据。

# 3. 当前架构证据

截至本文快照，代码已经具备部分模块化和并发控制基础，但边界尚未形成可执行约束。

| 现状 | 证据 | 判断 |
| --- | --- | --- |
| 已引入 Spring Modulith | [`pom.xml`](../../pom.xml#L99) | 可以复用现有能力，不需要增加新的分布式基础设施 |
| Modulith 尚未成为完整边界门禁 | 仅 [`attachment.api/package-info.java`](../../src/main/java/com/leo/erp/attachment/api/package-info.java#L1) 声明了 `@NamedInterface`，尚无根模块声明和 `ApplicationModules.verify()` runner | 附件公开契约已经可识别，但顶层模块封装和依赖方向校验仍未启用 |
| `sales` 反向依赖 `logistics` | [`SalesOrderDownstreamMutationGuard.java`](../../src/main/java/com/leo/erp/sales/order/service/SalesOrderDownstreamMutationGuard.java#L9) | 上游模块直接读取下游 Repository，形成反向依赖 |
| `logistics` 直接依赖 `sales` 实体和 Repository | [`FreightBillApplyService.java`](../../src/main/java/com/leo/erp/logistics/bill/service/FreightBillApplyService.java#L17) | 与上一项组合成编译环 |
| `finance` 依赖 `statement` 实体和查询服务 | [`PaymentStatementAllocationValidator.java`](../../src/main/java/com/leo/erp/finance/payment/service/PaymentStatementAllocationValidator.java#L10) | 依赖方向可接受，但必须只依赖公开应用 API |
| `statement` 反向查询 `finance` Repository | [`StatementSettlementSyncService.java`](../../src/main/java/com/leo/erp/statement/service/StatementSettlementSyncService.java#L5) | 与上一项组合成编译环 |
| 结算事件使用同步 `@EventListener` | [`StatementSettlementEventListener.java`](../../src/main/java/com/leo/erp/statement/service/StatementSettlementEventListener.java#L27) | 当前具有同事务强一致性，但事件语义不明确 |
| 来源锁要求已有事务并统一排序 | [`SourceAllocationLockService.java`](../../src/main/java/com/leo/erp/common/concurrency/SourceAllocationLockService.java#L57) | 这是主流程正确性的基础，解耦时必须保留 |
| 核心聚合使用乐观锁 | [`SalesOrder.java`](../../src/main/java/com/leo/erp/sales/order/domain/entity/SalesOrder.java#L22) | 并发写冲突能够失败闭合，应继续保留 |
| 通用状态保护依赖反射 | [`CrudStatusGuard.java`](../../src/main/java/com/leo/erp/common/service/CrudStatusGuard.java#L13) | `getStatus` 缺失时会静默跳过保护，属于领域风险 |
| 状态集合跨聚合共享 | [`StatusConstants.java`](../../src/main/java/com/leo/erp/common/support/StatusConstants.java#L73) | 一个模块的状态变化可能影响其他模块 |
| Service 广泛依赖 Web DTO | [`MaterialService.java`](../../src/main/java/com/leo/erp/master/material/service/MaterialService.java#L16) | 应在高变更路径逐步收口，不做全量迁移 |
| Repository 返回 Web Response | [`SalesOrderSourceCandidateQueryRepository.java`](../../src/main/java/com/leo/erp/sales/order/repository/SalesOrderSourceCandidateQueryRepository.java#L6) | 查询投影与 HTTP 响应模型没有分离 |
| 分配汇总查询曾伪造 JPA 实体 | [`ItemAllocationNativeRepository.java`](../../src/main/java/com/leo/erp/allocation/repository/ItemAllocationNativeRepository.java#L16) | 已改为只读 JDBC Repository，并删除无关表占位实体及继承的 CRUD API |
| 单账号迁移与创建者约束冲突 | [`V103__enforce_single_account.sql`](../../src/main/resources/db/migration/V103__enforce_single_account.sql#L29)、[`V107__add_operational_record_ownership.sql`](../../src/main/resources/db/migration/V107__add_operational_record_ownership.sql#L1) | 已通过独立 `owner_user_id` 与不可变创建审计解耦；兼容期后仍需更高版本迁移强制非空 |
| 附件存在性校验曾穿透所有业务实体 | [`RecordExistenceRegistry.java`](../../src/main/java/com/leo/erp/attachment/service/RecordExistenceRegistry.java#L13)、[`AttachmentRecordAccessService.java`](../../src/main/java/com/leo/erp/attachment/service/AttachmentRecordAccessService.java#L32) | 已改为公开命名接口中的显式记录端口，未注册模块和无效记录失败拒绝，附件模块不再加载跨模块 JPA 实体；打印、调度和运费对账也只依赖 `attachment.api` |
| 模块名称与可绑定实体有两份清单 | [`ModuleCatalog.java`](../../src/main/java/com/leo/erp/common/support/ModuleCatalog.java#L38)、[`RecordExistenceRegistry.java`](../../src/main/java/com/leo/erp/attachment/service/RecordExistenceRegistry.java#L17) | 展示清单与附件能力注册已分离；附件接口只接受实际注册了存在性端口的模块 |
| 关键写入副作用允许缺失 | [`PaymentService.java`](../../src/main/java/com/leo/erp/finance/payment/service/PaymentService.java#L63)、[`ReceiptService.java`](../../src/main/java/com/leo/erp/finance/receipt/service/ReceiptService.java#L62) | 可选 setter 注入把配置错误延迟到运行期，部分审计或同步可能静默关闭 |
| 业务审计已有可靠事件 | [`BusinessOperationAuditListener.java`](../../src/main/java/com/leo/erp/system/operationlog/event/BusinessOperationAuditListener.java#L19)、[`V79__add_reliable_business_operation_events.sql`](../../src/main/resources/db/migration/V79__add_reliable_business_operation_events.sql#L24) | 审计等最终一致副作用已有成熟落点 |

静态扫描还显示：约 77 个 Service 文件导入 `web` 类型，49 个业务 Service 跨顶层模块直接导入其他模块的 Repository，顶层包之间存在 12 组双向依赖。数量只用于判断整改规模，不作为要求一次性清零的承诺。

# 4. 目标模块结构

## 4.1 总体形态

目标仍然是一个 Spring Boot 应用、一个 PostgreSQL 数据库和一套统一部署流水线：

```text
Web / Scheduler / Startup Adapter
                 │
                 ▼
        Module Application API
                 │
                 ▼
       Owned Domain + Repository
                 │
                 ▼
             PostgreSQL
```

每个业务模块继续拥有自己的实体、Repository、应用服务和 Web 适配器。只有确实被其他模块调用的能力才新增小型 `api` 包，不为了形式统一批量移动现有文件。

## 4.2 目标编译依赖方向

下图中的箭头表示“左侧模块允许依赖右侧模块公开 API”，不是业务单据流向：

```text
logistics ───────> sales.api
sales ───────────> purchase.api
purchase ────────> master.api
statement ───────> sales.api
statement ───────> logistics.api
finance ─────────> statement.api
finance ─────────> purchase.api

所有模块 ────────> common 的稳定契约与基础设施抽象
config ──────────> 所有模块，仅负责装配
```

约束如下：

1. `common` 不得反向导入 `auth`、`security`、`system` 或业务模块；
2. 业务模块不得导入其他模块的 `repository`、`domain.entity` 或 `web.dto`；
3. 跨模块写入必须调用目标模块公开的同步端口；
4. 跨模块查询返回稳定的只读 record，不返回 JPA 实体；
5. 下游引用保护通过端口反转解决，不允许上游直接查询下游 Repository；
6. 组合根 `config` 可以依赖所有模块，但不得承载业务规则；
7. 报表和全局搜索允许在专用只读查询适配器中跨表 JOIN，但不得通过该通道修改业务数据；
8. 不为简单 CRUD 增加没有实际调用方的接口层。

## 4.3 下游保护的端口反转

采购订单和销售订单必须知道是否已经被下游引用，但不应因此依赖下游实现。

目标模式：

```text
purchase-api 定义 PurchaseOrderReferenceGuard
    ▲
    ├── purchase 自身入库引用实现
    └── sales 提供销售引用实现

sales-api 定义 SalesOrderReferenceGuard
    ▲
    ├── sales-outbound 提供出库引用实现
    └── logistics 提供物流引用实现
```

上游服务只调用本模块拥有的 Guard 接口集合。下游实现只读取自己的 Repository，并返回稳定的引用结果。这样保留删除和反审核保护，同时移除上游对下游表结构的编译依赖。

不得把这些保护改成异步事件。删除、反审核和来源变更必须在提交前确定能否执行。

## 4.4 公开 API 的最小约定

每个新增模块 API 只允许包含以下类型：

- 不带 JPA 注解的不可变 record；
- 面向具体业务动作的查询或命令端口；
- 明确的业务异常语义；
- 必要的事件契约。

禁止在公开 API 中暴露：

- `JpaRepository`、`EntityManager` 或持久化实体；
- Controller Request/Response；
- `HttpServletRequest`、`MultipartFile`、`MediaType` 等传输类型；
- 允许调用方任意设置聚合状态的通用 setter；
- 为未来可能需求预留但当前没有调用方的扩展点。

## 4.5 模块边界执行机制

模块边界不能只靠目录约定和代码评审。目标采用 Spring Modulith 的显式模块检测，并按主流程迁移进度逐步扩大硬门禁范围。

第一批只声明 `purchase`、`sales` 和 `logistics`，避免当前其他历史包被一次性纳入后造成全仓阻断：

```yaml
spring:
  modulith:
    detection-strategy: explicitly-annotated
```

模块根包通过 `package-info.java` 声明允许依赖，公开 `api` 子包通过命名接口暴露。以下仅展示形态，实际白名单必须从已确认调用方生成，不能把临时内部依赖写成永久许可：

```java
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = "sales::api"
)
package com.leo.erp.logistics;
```

```java
@org.springframework.modulith.NamedInterface("api")
package com.leo.erp.sales.api;
```

CI 使用两层门禁：

1. 现有依赖导入基线脚本阻止新增跨模块 `repository`、`domain.entity` 和 `web.dto` 引用；旧违规必须登记且数量单调下降；
2. 独立的非测试架构 runner 调用 `ApplicationModules.of(LeoApplication.class).verify()`，检查已声明模块的 DAG、内部包访问和 `allowedDependencies` 白名单。

受仓库测试文件政策限制，runner 放在 `tools/architecture` 或等价的非测试工具目录，不新增 JUnit/ArchUnit 文件。它只在 CI 和开发者显式检查时执行，不挂入生产应用启动。迁移初期 runner 可以报告尚未纳管的历史包，但已声明且已收口的模块必须失败阻断。

禁止长期把所有模块设为 `OPEN`。任何临时开放或白名单例外必须记录调用方、被调用方、负责人、原因、替代端口和删除条件；没有删除条件的例外不允许合入。

# 5. 同步端口与可靠事件的选择

## 5.1 选择规则

| 场景 | 机制 | 一致性 | 失败行为 | 示例 |
| --- | --- | --- | --- | --- |
| 来源存在性、状态、数量和重量校验 | 同步查询端口 | 同事务强一致 | 失败回滚当前命令 | 入库校验采购来源、销售校验入库来源 |
| 下游引用保护 | 同步 Guard 端口 | 提交前确定 | 存在引用则拒绝操作 | 订单删除、反审核、来源行修改 |
| 跨聚合业务状态同步 | 同步命令端口或应用协调器 | 同事务强一致 | 任一聚合失败则整体回滚 | 入库完成后同步采购订单状态 |
| 操作日志、审计快照 | Spring Modulith 可靠事件 | 提交后最终一致 | 主事务成功，处理器可重试 | `BusinessOperationEvent`、采购订单 JaVers 快照 |
| 缓存失效 | 提交后事件 | 最终一致 | 缓存短暂陈旧，可重试或依赖 TTL | 账号资料变化后的仪表盘缓存失效 |
| 外部系统通知 | 可靠事件 | 最终一致 | 失败不回滚已完成业务 | 未来确有外部集成时使用 |

判断顺序：

1. 结果是否决定当前命令能否提交；
2. 是否必须与当前数据库事务一起回滚；
3. 是否允许短暂不一致；
4. 消费者是否能幂等重试；
5. 事件是否包含消费者完成处理所需的不可变快照。

前两项任一为“是”时使用同步端口。只有允许提交后处理且消费者可幂等时，才使用可靠事件。

## 5.2 当前结算事件的处理

`PaymentSettlementSyncService` 发布事件后，由普通 `@EventListener` 在当前线程处理；监听器继续调用带 `@Transactional` 的同步服务，因此当前实际语义是隐藏的同步调用。

处理原则：

1. 在对账和财务没有实际启用期间，不扩展该事件模型；
2. 解耦时把强一致更新改成明确的同步端口，保持现有事务回滚语义；
3. 不直接把监听器替换为 `@Async`、`@TransactionalEventListener(AFTER_COMMIT)` 或 `@ApplicationModuleListener`；
4. 如果未来业务确认可以最终一致，再单独设计可靠事件、幂等键、补偿和对账任务。

## 5.3 可靠事件约束

继续复用现有 Spring Modulith `event_publication` 表，不新增 Kafka、RabbitMQ 或独立 outbox 框架。

可靠事件必须满足：

1. 只在业务事务内发布；
2. 事件携带稳定 ID 和必要快照，不携带受管 JPA 实体；
3. 消费者以事件 ID 或业务幂等键去重；
4. 消费失败可观察、可重试；
5. 不完整发布积压必须有监控和运维处理步骤；
6. 同一副作用不能同时走旧直接调用和新事件，避免重复执行。

# 6. 分阶段实施任务

## 阶段 0：建立基线和发布护栏

目标：不改业务行为，先让每次解耦都有可比较基线。

任务：

1. 固化采购订单、采购入库、销售订单、销售出库和物流单的人工烟雾路径及关键数据检查项；
2. 记录当前顶层模块依赖边、12 组双向依赖和已接受的临时例外，建立可重复生成的只读依赖检查脚本；
3. 对新增的跨模块 `repository`、`domain.entity` 和 `web.dto` 导入设置 CI 阻断；该门禁使用 `scripts/` 下的非测试静态检查，不在 `src/test` 新增架构测试；
4. 建立独立非测试 Modulith runner；阶段 0 先输出报告，模块边界收口后逐个转为硬门禁；
5. 记录现有来源锁的表顺序、ID 排序和事务传播要求；
6. 先处理第 10 节的 P0 运维安全项，以及单账号历史所有权、附件存在性注册表和 `AllocationDummy` 风险，再发布任何新的架构版本；
7. 为每一条待解除依赖边建立独立变更单，不创建总量巨大的重构分支。

完成标准：依赖基线可重复生成，主流程烟雾清单已可执行，新增违规依赖会被 CI 拒绝。

## 阶段 1：建立最小模块边界

目标：建立可使用的公开 API，不移动大批现有类。

任务：

1. 为 `purchase`、`sales`、`logistics` 新增最小 `api` 包；
2. 启用 `explicitly-annotated` 检测，只将本阶段明确声明的模块纳入 Modulith；
3. 用 `@NamedInterface("api")` 暴露公开包，并用 `allowedDependencies` 声明最小依赖白名单；
4. 将 `SecurityConfig` 等高层装配职责移出 `common` 的目标纳入边界清单；
5. 用当前调用方驱动端口设计，没有调用方的接口不创建；
6. 将附件记录校验替换为各模块实现的 `RecordExistencePort`，未注册模块失败拒绝，不再通过 `EntityManager` 加载任意实体；
7. 将 `ItemAllocationNativeRepository` 改为普通只读 JDBC Repository，彻底移除 `AllocationDummy` 和继承得到的 CRUD API；
8. 保留现有包名和 Controller 接口，避免同时进行目录重排。

完成标准：新增模块 API 不暴露实体、Repository 或 Web DTO；现有外部 HTTP 合约不变化。

## 阶段 2：解耦已跑通主流程

目标：按一条依赖边一个批次解除主链模块环。

建议顺序：

1. `purchase -> sales.repository`：使用采购模块拥有的下游引用 Guard，由销售模块实现；
2. `sales -> logistics.repository`：使用销售模块拥有的下游引用 Guard，由物流模块实现；
3. `logistics -> sales.entity/repository/web.dto`：改为销售模块公开的来源查询端口和只读快照；
4. 收敛销售出库对销售订单状态的直接写入，通过销售模块内部应用协调器执行；
5. 收敛采购入库对采购订单状态和重量的直接写入，通过采购模块内部应用协调器执行。

每个批次必须：

1. 先增加端口和适配器；
2. 只迁移一个调用路径；
3. 保持 `@Transactional` 边界不变；
4. 保持 `SourceAllocationLockService` 的调用时机和锁顺序不变；
5. 通过主流程烟雾后再删除旧直接依赖；
6. 不在同一批次顺便改 DTO、状态机、数据库表或前端页面。

完成标准：`sales <-> logistics` 编译环消失，采购不再直接导入销售 Repository，主流程结果与整改前一致。

## 阶段 3：类型化核心领域不变量

目标：消除核心单据状态保护的静默失效风险，不重写全部 CRUD。

任务：

1. 仅为采购订单、采购入库、销售订单、销售出库和物流单建立显式状态契约；
2. 让状态读取和写入具备编译期检查，逐步移除这些聚合对 `CrudStatusGuard` 反射路径的依赖；
3. 将审核、反审核、完成和重新打开表达为业务命令，不允许调用方提交任意目标状态；
4. 保持数据库现有中文状态值，必要时通过枚举映射适配，不在本阶段迁移存量状态数据；
5. 将来源、所有权、重量、数量、状态和下游引用规则保留在领域服务或聚合方法中；
6. 不引入 Spring StateMachine，除非后续出现无法由当前显式命令维护的真实复杂度。

完成标准：五个核心单据的状态保护不再依赖反射；非法迁移和下游保护仍失败闭合。

## 阶段 4：收敛审计、缓存和模块事件

目标：统一事件语义并消除关键副作用的静默关闭。

任务：

1. 操作日志发布器改为必需依赖，缺失时应用启动失败，不允许静默跳过写审计；
2. 保留 `@ApplicationModuleListener` 处理操作日志和 JaVers 快照；
3. 使用账号变更事件解除 `auth -> system.dashboard` 的直接依赖；
4. 明确普通同步事件、事务后事件和可靠事件的使用规则；
5. 为 `event_publication` 未完成记录建立监控、告警和重试操作说明；
6. 当前 `completion-mode: update` 必须增加完成记录保留和清理策略；优先在确认成功投递不承担审计职责后改为 `delete`，否则只清理超过明确保留期且 `completion_date IS NOT NULL` 的记录；
7. 不把库存、来源额度、状态迁移等强一致规则事件化。

完成标准：关键写审计不能静默关闭；`auth <-> system` 循环缩小；可靠事件积压可观测；成功投递记录不会无限增长，未完成记录不会被误删。

## 阶段 5：隔离未使用的对账与财务

目标：只解除边界污染，不进行产品或领域重构。

任务：

1. 用最小 `statement-api` 替换财务模块对对账实体和 Service 内部类型的依赖；
2. 用同步端口替换对账模块对付款、收款 Repository 的反向读取；
3. 保留当前数据库表和 HTTP 接口，不重建账簿；
4. 不新增后台任务、补偿流程、复杂状态机或事件驱动结算；
5. 对仍未实际使用的代码只确保隔离、编译和启动，不做性能优化；
6. 若端口抽象的成本高于保留模块的价值，应另行评估整体下线，而不是继续深化设计。

完成标准：`statement <-> finance` 编译环消失；主流程不依赖对账或财务内部实现。

## 阶段 6：按维护热点改善分层

目标：停止继续扩大 Web DTO 和巨型 Service 的影响范围。

任务：

1. 新增 Repository 查询统一返回应用查询投影，不返回 `web.dto`；
2. 新增 Service 接口不接收 `HttpServletRequest`、`MultipartFile` 或 HTTP 媒体类型；
3. 优先拆分 `MaterialService` 的导入、导出和 CRUD 职责；
4. 按模块建立专用查询条件，逐步替换跨领域 `PageFilter` 万能参数；
5. 旧代码只在被业务修改时顺带迁移，不发起全仓 DTO 重写；
6. 报表和全局搜索继续使用只读跨表投影，不强制套用聚合 Repository。
7. 对单人部署剩余的少量运行时功能开关，评估以 `@ConfigurationProperties` 固化部署配置，避免继续维护 OpenFeature/Unleash、远程 Provider 和缓存链路；没有真实灰度需求时不保留该基础设施。

完成标准：新增代码不继续制造反向分层依赖；高变更服务的职责逐步收敛。

# 7. 验收标准

## 7.1 架构验收

1. CI 能生成顶层模块依赖图，拒绝新增循环和未登记的跨模块内部引用；
2. 非测试 Modulith runner 对已声明模块执行 `verify()`，并通过 DAG、内部包访问和依赖白名单检查；
3. 业务模块不再直接导入其他模块的 Repository；
4. 核心链路不跨模块传递 JPA 实体或 Web DTO；
5. `common` 不再依赖业务、认证或系统模块；
6. 每个公开端口都有当前调用方和明确一致性说明；
7. 同步端口和可靠事件的选择符合第 5 节规则；
8. 对账和财务只达到隔离标准，不以代码量或领域完整度作为验收指标。

阶段性迁移允许旧违规依赖继续存在，但基线数量必须单调下降，且不得新增未登记例外。

## 7.2 主流程验收

至少完成以下人工烟雾路径：

1. 新建并审核采购订单；
2. 从采购订单导入采购入库，审核并验证数量、批号、仓库、重量和状态回写；
3. 从采购来源创建销售订单，验证客户、项目、结算主体和来源快照；
4. 从销售订单创建并审核销售出库；
5. 从销售订单创建物流单，验证来源排他性、车号、重量和运费；
6. 对每类主单执行允许的编辑、审核、反审核、删除路径；
7. 构造已有下游引用的场景，确认上游变更仍被拒绝；
8. 并发冲突仍返回失败，不出现来源超分配或静默覆盖。

## 7.3 工程验收

每个阶段至少通过：

```bash
mvn -B -ntp -DskipTests compile
mvn -B -ntp -DskipTests checkstyle:check spotbugs:check
```

如果包含 Flyway 变更，还必须执行对应环境的 `flyway:migrate` 和 `flyway:validate`。本计划不得在活动源码目录新增测试文件，也不得恢复归档测试。

## 7.4 运维验收

1. 数据库有发布前备份和经过验证的恢复点；
2. Redis、数据库连接池和可靠事件积压有可见状态；
3. 实际需要的打印模板已由运维人工导入并完成打印烟雾；
4. 架构整改没有改变公开端口、反向代理路径或现有部署拓扑；
5. `event_publication` 的未完成记录可告警、可重投，完成记录有已验证的清理方式；
6. 发布记录包含本次解除的依赖边、保留的锁顺序和回滚版本。

# 8. 回滚原则

1. 一个发布批次只解除一条主要依赖边，确保可以按提交或版本回滚；
2. 不使用运行时功能开关维持新旧架构双轨，回滚依赖应用版本重新部署；
3. 纯解耦任务默认不修改数据库；确需修改时必须使用新的递增 Flyway，禁止修改已执行脚本；
4. 破坏性数据库变更采用扩展、迁移、清理三个发布阶段，应用回滚窗口内保留旧列或旧约束兼容性；
5. 同步端口迁移失败时回到原直接调用，不能临时改成异步以绕过失败；
6. 可靠事件迁移必须保证消费者幂等，回滚时不能重复执行已经完成的副作用；
7. 新旧实现不得同时写同一状态、审计记录或缓存失效结果；
8. 主流程烟雾失败时停止后续阶段并回滚应用，不继续发布未使用的对账或财务整改；
9. 回滚不得使用 `git reset --hard`、强制推送或直接修改生产数据；
10. 涉及数据修复时先新增 Flyway 修复脚本并完成备份，不允许临时手工持久化 DDL/DML。

# 9. 明确非目标

以下事项不属于本计划：

1. 拆分微服务、独立数据库或独立部署单元；
2. 引入 Kafka、RabbitMQ、分布式事务或新的 outbox 框架；
3. 建设 CQRS、事件溯源、完整 DDD 分层或通用工作流平台；
4. 大规模移动包、统一重命名或全仓 DTO 重写；
5. 重写已经跑通的采购、销售、入库、出库和物流规则；
6. 启用、补全或复杂重构当前未实际使用的对账和财务能力；
7. 建设会计总账、凭证、税务、多币种或复杂核销体系；
8. 自动执行打印 seed、启动时自动导入模板或把模板数据重新塞入主 Flyway 基线；
9. 恢复 RBAC、ABAC、API Key、部门权限或多账号授权；
10. 恢复活动测试目录或以架构整改为由增加生产测试文件；
11. 替换 Spring Data JPA、PostgreSQL、Redis 或现有部署拓扑；
12. 顺带处理与模块解耦无关的 UI、报表或性能优化。

# 10. P0/P1 运维安全风险

架构整改不能覆盖更高优先级的运维安全问题。P0 必须先处理或建立阻断措施，P1 必须在相关阶段上线前进入发布清单。

## 10.1 P0：仓库跟踪真实数据库转储

[`scripts/jsh_erp_steel_dump.sql`](../../scripts/jsh_erp_steel_dump.sql#L692) 当前由 Git 跟踪，并包含业务数据、个人信息、IP、历史密码哈希等敏感内容。

风险：

- 仓库、构建产物、备份或镜像被共享时会同步泄露生产数据；
- 只删除工作区文件不能消除 Git 历史中的内容；
- 历史密码哈希和相关凭据可能被离线攻击或交叉利用。

处理要求：

1. 立即停止把该文件复制到构建产物、镜像和发布包；
2. 按泄露事件处理相关数据库、账号和基础设施凭据轮换；
3. 使用脱敏结构样例替代真实转储；
4. Git 历史重写、强制推送和共享仓库协调属于独立高风险操作，必须单独审批；
5. 在该风险受控前，不得扩大仓库访问范围。

## 10.2 P0：离线初始化制品固化数据加密主密钥

[`release/db/postgresql-full-init.sql`](../../release/db/postgresql-full-init.sql#L10138) 包含一条具体的 `DATA_MASTER` 值。虽然正常 Flyway 迁移会在 [`V88__bootstrap_data_encryption_key.sql`](../../src/main/resources/db/migration/V88__bootstrap_data_encryption_key.sql#L15) 为每个环境随机生成密钥，但当前离线初始化制品会让所有由它创建的环境复用同一密钥。

风险：

- 源码或发布制品泄露即可取得数据加密主密钥；
- 多环境共用密钥会扩大单点泄露影响范围；
- 直接替换密钥会导致已有密文不可解密，不能把轮换当成普通配置修改。

处理要求：

1. 停止分发包含具体 `secret_value` 的离线初始化制品；
2. 新环境必须在部署时生成独立密钥，不得从仓库复制；
3. 已使用该制品的环境先盘点受影响密文，再制定可验证的解密、重加密和密钥轮换步骤；
4. 替换生成脚本后检查产物，确保不再导出 `sys_security_secret` 的实际值；
5. 不通过手工数据库更新或改写已执行 Flyway 记录处理该问题。

## 10.3 P0 发布门禁：数据库可恢复性

任何包含 Flyway 或数据修复的发布，如果没有可验证备份、恢复点和 `migrate/validate` 结果，都按 P0 发布阻断处理。

要求：

1. 发布前确认生产 `flyway_schema_history` 与代码一致；
2. 在生产数据副本演练新增迁移；
3. 建立可恢复备份并记录恢复命令和责任人；
4. 迁移失败立即停止应用发布；
5. 禁止绕过 Flyway 直接写入持久化 DDL/DML。

## 10.4 P0：单账号迁移遗留历史所有权

[`V103__enforce_single_account.sql`](../../src/main/resources/db/migration/V103__enforce_single_account.sql#L29) 会停用并软删除其他账号，但没有迁移业务表的 `created_by`。销售订单编辑和未绑定附件访问仍将创建者视为领域不变量，因此唯一活动账号可能无法处理旧账号创建的数据。

实施结论（2026-07-25）：

1. 单人模式继续保留“当前所有者”领域语义，不把它取消，也不迁入已移除的 RBAC/ABAC；
2. [`V107__add_operational_record_ownership.sql`](../../src/main/resources/db/migration/V107__add_operational_record_ownership.sql#L1) 为销售订单和附件新增 `owner_user_id`，并在 `sys_record_ownership_migration_audit` 保存实体类型、记录 ID、原 `created_by`、新所有者和迁移时间；
3. `created_by`、`created_name`、`created_at` 保持原值，业务校验改用 `owner_user_id`，不再混用创建审计和业务所有权；
4. 销售订单创建写入 owner，编辑、状态变更、完成和删除均校验 owner；自动领域同步直接更新既有聚合，不改变 owner；
5. 附件普通上传和直传凭证均绑定当前主体；未绑定附件仅 owner 可访问，恢复清单 v2 同时导出 `ownerUserId` 与原创建审计；
6. `V107` 是兼容发布，列暂时允许为空。新代码只对切换窗口中旧进程可能写出的空 owner 回退到 `created_by`；稳定后使用新的递增迁移补齐竞态记录并强制非空，再删除回退；
7. 真实生产进程当前连接的 schema 为 `V102`。只读盘点确认一个活动账号、零历史账号、十九条销售订单均由活动账号创建、附件为零；生产尚未执行 `V103` 至 `V108`；
8. 开发库已执行 `V107`，该脚本内容自此冻结。发现问题只能新增更高版本迁移修复，不得修改或删除 `V107`；
9. [`V108__enforce_single_active_attachment_binding.sql`](../../src/main/resources/db/migration/V108__enforce_single_active_attachment_binding.sql#L1) 提前锁定绑定表，对现有重复绑定失败关闭，并以部分唯一索引保证同一附件最多存在一个有效绑定；
10. 同一 `(moduleKey, recordId)` 的替换使用 PostgreSQL 事务级 advisory lock 串行化，并通过各模块 `RecordExistencePort` 对有效业务记录取得共享行锁；附件行继续按 ID 排序加锁，数据库唯一冲突被翻译为 409；
11. 业务记录软删除时冻结并保留原附件绑定，这是审计证据保留不变量；附件不得在删除提交后继续增删、不得因原记录软删除而重新绑定到其他记录，恢复清单继续导出该关系，不做自动清理；
12. 发布前仍需备份真实生产库，并对销售订单各类写操作、未绑定附件访问、同记录并发替换、附件并发跨记录复用拒绝和已绑定附件存在性执行烟雾验证。

## 10.5 P0：附件记录存在性校验失败开放

实施结论（2026-07-25）：

1. 模块展示清单与附件可绑定能力分离，模块名称不能充当存在性校验；
2. 每个支持附件的模块通过 [`RecordExistencePort.java`](../../src/main/java/com/leo/erp/attachment/api/RecordExistencePort.java#L3) 只暴露 `moduleKey()`、`existsActive(recordId)` 与 `lockActive(recordId)`；该端口由 `attachment::api` 命名接口公开；
3. [`RecordExistenceRegistry.java`](../../src/main/java/com/leo/erp/attachment/service/RecordExistenceRegistry.java#L13) 在启动时校验空注册和重复键，附件模块不再加载其他模块 JPA 实体；
4. 未注册、不支持附件、记录不存在或已删除均失败拒绝；
5. 旧全局实体目录、静态注册器和组合配置已经删除；
6. `ModuleCatalog` 统一负责模块码的大小写、前导路径和别名归一化；`material-categories` 映射为 `material-category`，并由商品类别 Repository 注册存在性端口；
7. `V108` 在上线时先锁定绑定表，再扫描重复有效绑定并建立数据库唯一保护，发现歧义数据时停止迁移，不自动删除；
8. 附件替换通过 `lockActive(recordId)` 对业务根记录取得 `PESSIMISTIC_READ`，在 PostgreSQL 上阻止并发软删除修改 `deleted_flag`，避免删除提交后附件集合继续变化。
9. 附件查询、记录校验和恢复清单导出分别通过 `AttachmentQuery`、`AttachmentRecordAccess` 和 `AttachmentManifestExporter` 暴露；运费对账使用本模块响应 DTO，外部模块不再导入附件 `service`、`mapper` 或 `web.dto`。
10. 本批只收口附件内部包访问，未把 `attachment` 声明为完整 Modulith 根模块。附件对操作日志和数据密钥仍存在 `attachment -> system` 依赖，必须在建立根模块硬门禁前继续拆分为中立契约。

## 10.6 P0：伪 JPA Repository 暴露无关表写能力

实施结论（2026-07-25）：

1. [`ItemAllocationNativeRepository.java`](../../src/main/java/com/leo/erp/allocation/repository/ItemAllocationNativeRepository.java#L16) 已改为使用 `NamedParameterJdbcTemplate` 的专用只读 Repository；
2. 接口只暴露当前四个汇总查询，不继承通用 CRUD 接口；
3. `AllocationDummy` 已删除，静态搜索未发现调用方依赖继承方法；
4. 该整改不调整查询 SQL、锁顺序、数量或重量口径。

## 10.7 P1：生产继承 `baseline-on-migrate`

[`application.yml`](../../src/main/resources/application.yml#L87) 全局启用 `baseline-on-migrate: true`，而生产配置没有覆盖。部署误连到“非空但没有 Flyway history”的数据库时，Flyway 可能把错误库登记为基线后继续迁移，而不是拒绝启动。

处理要求：

1. 生产 profile 明确配置 `baseline-on-migrate: false`；
2. 历史库首次接管必须走一次性人工审核流程，不能依赖应用启动自动判断；
3. 部署前同时校验数据库标识、schema 和 `flyway_schema_history`；
4. 该修复独立发布，不与模块解耦代码混在同一批次。

## 10.8 P1：JAR-only 回滚不保证 schema 兼容

[`rollback-production-release.sh`](../../scripts/deploy/rollback-production-release.sh#L149) 只切换应用制品并执行基础健康检查，不校验目标版本需要的 schema。当前迁移已经存在删列、删表操作，例如 [`V100__drop_department_management_schema.sql`](../../src/main/resources/db/migration/V100__drop_department_management_schema.sql#L1) 和 [`V106__drop_static_menu_metadata.sql`](../../src/main/resources/db/migration/V106__drop_static_menu_metadata.sql#L1)。旧 JAR 可能启动成功，但在实际业务请求中才因缺表或缺列失败。

处理要求：

1. 每个 release 记录兼容的 Flyway 版本区间；
2. 回滚前校验目标 JAR 与当前 schema 是否兼容；
3. 破坏性迁移进入清理阶段后明确采用前滚修复，不再承诺任意旧版本回滚；
4. 健康检查增加最小业务查询，不能只验证数据库和 Redis 可连接；
5. 继续采用扩展、迁移、清理三阶段，保留明确的应用回滚窗口。

## 10.9 P1：认证路径同时依赖 PostgreSQL 与 Redis

每个带 Access Token 的请求会从 PostgreSQL 读取 JWT 验签材料和凭据版本，并访问 Redis 中的令牌黑名单、主体缓存和会话活动信息：[`JwtAuthenticationFilter.java`](../../src/main/java/com/leo/erp/security/jwt/JwtAuthenticationFilter.java#L62)、[`JwtTokenService.java`](../../src/main/java/com/leo/erp/security/jwt/JwtTokenService.java#L50)、[`AuthenticatedUserCacheService.java`](../../src/main/java/com/leo/erp/security/jwt/AuthenticatedUserCacheService.java#L55)。HTTP 写幂等也依赖 Redis；不可用时会安全拒绝请求并返回 503：[`HttpIdempotencyFilter.java`](../../src/main/java/com/leo/erp/common/idempotent/HttpIdempotencyFilter.java#L75)。

该设计在安全上失败关闭，但数据库或 Redis 任一故障都会影响全部登录态请求，也没有发挥无状态 JWT 的低依赖优势。本计划不顺带重写认证；应作为独立决策评估“明确的服务端会话”或“带有界缓存的 JWT 验证”二选一，并在决策前保持现有安全语义。当前必须监控两项依赖的延迟和错误率，提供故障运行手册，不得为了可用性跳过黑名单、凭据版本或幂等校验。

## 10.10 P1：可靠事件积压可能导致审计延迟

操作日志和采购订单快照使用 Spring Modulith 可靠事件。主事务可以成功，而消费者失败记录会留在 `event_publication`。

要求：

1. 监控未完成发布数量和最老发布时间；
2. 告警中包含监听器 ID、事件类型和失败时间；
3. 重试前确认消费者幂等；
4. 不能通过删除积压记录伪造处理成功；
5. 关键写审计发布器必须改为必需依赖，禁止为空时静默跳过；
6. 对 `completion_date IS NOT NULL` 的成功记录采用明确清理策略，不能让 `completion-mode: update` 导致表无限增长。

## 10.11 P1：打印模板人工导入遗漏

打印模板人工导入是接受的方案，但遗漏会造成打印和导出不可用。

控制措施：

1. 将模板清单纳入环境交付检查表；
2. 记录模板来源、版本、校验值、结算主体和导入人；
3. 发布后对实际使用单据执行打印烟雾；
4. 模板缺失时阻止宣布打印功能就绪，但不阻止应用其他主流程启动；
5. 不以该风险为由重新建设自动 seed 或自动导入。

## 10.12 P1：架构解耦破坏原锁顺序

端口抽取最容易发生的回归是把查询移到锁之前、把同步调用移到事务之外，或改变多个来源表的加锁顺序。

控制措施：

1. 每个端口标注调用方必须已有事务还是允许独立事务；
2. 额度检查和排他占用检查必须在来源锁之后执行；
3. ID 继续去空、去重并升序；
4. 不在端口实现中使用 `REQUIRES_NEW` 切断主事务；
5. 主流程烟雾必须覆盖已有下游引用和并发冲突场景。

# 11. 实施优先级

| 优先级 | 内容 | 说明 |
| --- | --- | --- |
| P0-安全 | 敏感转储与固定数据密钥处置、数据库可恢复发布门禁 | 高于所有代码架构整改 |
| P0-代码 | 单账号历史所有权、附件失败开放、伪 JPA Repository | 先消除数据不可达、孤儿绑定和误写无关表风险 |
| P1-0 | 生产 Flyway 基线保护、schema 兼容回滚和认证依赖韧性 | 独立处理，不与业务解耦混发 |
| P1-1 | 建立依赖基线、显式 Modulith 模块和新增违规阻断 | 先停止继续恶化，再逐模块扩大硬门禁 |
| P1-2 | 解耦采购、销售、出入库、物流主链 | 架构代码第一优先级 |
| P1-3 | 类型化五个核心单据状态不变量 | 消除静默失效风险 |
| P1-4 | 强制审计依赖和可靠事件运维 | 保证写操作留痕 |
| P2 | 隔离未使用的对账和财务 | 只解除模块环，不深化功能 |
| P3 | DTO、万能过滤器、巨型 Service 与少量功能开关渐进收敛 | 仅在维护热点顺带实施，单人部署优先删去无灰度价值的 OpenFeature/Unleash |

本计划的成功标准不是目录更整齐或接口数量更多，而是在不改变现有业务结果的前提下，使依赖方向单向、领域不变量可编译检查、事务和事件语义可明确解释，并让后续修改能够限制在所属模块内。
