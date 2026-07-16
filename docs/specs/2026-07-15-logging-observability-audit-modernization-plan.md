---
title: Leo 日志、可观测性与业务审计现代化方案
date: 2026-07-15
status: in-progress
scope: leo 后端、aries 前端、PostgreSQL 与部署可观测性基础设施
---

# 1. 背景与结论

Leo 当前已经使用 SLF4J、Logback、Micrometer Tracing 和 OpenTelemetry，后端运行日志具备基础生产能力；但业务操作日志、归档和前端错误记录仍包含较多项目自研实现。

当前主要问题：

- `OperationLogRequestFilter`、`OperationLogInterceptor`、`OperationLogResponseBodyAdvice` 和 `OperationLogResultCollector` 依赖 HTTP 请求、响应体和反射推断业务语义；
- 业务操作成功与否由 HTTP 状态和通用响应结构推断，无法稳定表达审核、反审核、导入、删除和状态流转等领域事实；
- 前端 logger 只在浏览器内存中保留最近 50 条警告或错误，刷新页面后丢失，也没有远程聚合；
- 技术日志以本地纯文本文件为主，没有形成统一的日志、指标和链路检索入口；
- 操作日志归档由应用手写 JDBC 游标分页、CSV、GZIP 和文件移动，恢复、保留和完整性责任仍由应用承担。

目标方案不是寻找一个框架替换全部日志代码，而是按职责拆分：

```text
业务服务
  -> 发布明确的领域事件
  -> Spring Modulith 可靠投递
  -> 审计事件处理器写入操作日志
  -> JaVers 补充字段变化明细

后端技术日志
  -> SLF4J / Logback JSON
  -> Grafana Alloy / OpenTelemetry Collector
  -> Loki + Tempo + Grafana

前端异常
  -> Sentry React SDK
  -> Release、Source Map、Trace ID 关联

操作日志数据
  -> PostgreSQL 时间分区
  -> 分区保留、归档或脱机导出
```

# 2. 目标与非目标

## 2.1 目标

- 用明确的领域事件记录业务事实，不再通过响应体反射推断业务操作；
- 保证已提交业务事件可以可靠投递、幂等消费和追踪失败；
- 将字段修改历史与业务操作语义分离；
- 建立后端日志、指标和链路的统一检索入口；
- 建立前端异常的远程采集、版本定位和 Source Map 还原能力；
- 用 PostgreSQL 分区和生命周期策略替代应用内手写归档主路径；
- 分阶段迁移，任一阶段均可独立停止或回滚，不影响核心 ERP 交易。

## 2.2 非目标

- 不把采购、销售、库存、财务等领域规则迁入日志框架；
- 不用 JaVers 或 Envers 替代审核、反审核、完成采购等业务事件；
- 不在首阶段删除现有 `sys_operation_log` 查询页面和权限模型；
- 不允许日志、链路或 Sentry 不可用时阻断核心业务；
- 不在应用内重新实现 Loki、Tempo、Sentry 或消息队列已有能力；
- 不绕过 Flyway 直接修改任何环境的数据库结构或数据。

# 3. 职责边界

| 能力 | 负责内容 | 不负责内容 |
| --- | --- | --- |
| SLF4J / Logback | 应用运行日志、异常栈、结构化字段 | 业务状态事实、字段版本历史 |
| OpenTelemetry / Tempo | Trace、Span、跨服务调用关系 | 长期业务审计 |
| Loki | 技术日志集中收集、检索、告警 | ERP 操作日志的权威数据源 |
| Sentry | 前端异常、版本、Source Map、用户操作上下文 | 服务端业务操作审计 |
| Spring Modulith | 模块边界、领域事件发布与可靠消费 | 自动推断业务语义 |
| JaVers | 聚合或实体字段变化快照与差异 | 审核、反审核等业务动作定义 |
| `sys_operation_log` | 操作人、业务动作、单号、结果和 Trace ID | 完整对象快照和技术异常栈 |
| PostgreSQL 分区 | 审计数据保留、归档和分区删除 | 日志可视化与告警 |

# 4. 组件选型

## 4.1 Spring Modulith 领域事件

采用 Spring Modulith 的事件发布注册表和 `@ApplicationModuleListener`，由业务服务在事务内发布不可变领域事件。

事件必须表达已发生的业务事实，例如：

- `PurchaseOrderAudited`；
- `PurchaseInboundReverseAudited`；
- `SalesOrderDeliveryVerified`；
- `SalesOutboundAudited`；
- `FreightBillDeleted`。

禁止发布 `UpdateSucceeded`、`RequestCompleted` 等缺乏领域语义的通用事件。

推荐事件公共字段：

```java
public record BusinessOperationEvent(
        UUID eventId,
        Instant occurredAt,
        String moduleKey,
        String actionType,
        Long aggregateId,
        String businessNo,
        Long operatorId,
        String operatorName,
        String loginName,
        String authType,
        String traceId,
        Map<String, String> attributes
) {}
```

约束：

- 事件在业务事务完成前发布，由 Modulith 在提交后可靠交付；
- 审计消费者以 `eventId` 幂等，数据库建立唯一约束；
- 事件只携带审计所需快照，不把 JPA Entity 作为事件载荷；
- 事件版本必须显式演进，禁止消费者依赖任意 JSON 结构；
- 事件消费者失败不得回滚已经完成的业务事务，应保留待重试记录并产生技术告警；
- 业务失败不发布“成功事实”，失败请求继续由技术日志和异常平台记录；确需失败审计时，由应用命令边界单独发布失败事件。

## 4.2 JaVers 字段变化审计

JaVers 只负责回答“哪些字段从什么值变成了什么值”，不负责回答“为什么发生变化”。

接入原则：

- 只审计采购、销售、库存、财务等明确要求追踪变更的聚合根；
- 不对缓存对象、查询 DTO、附件二进制内容和敏感密钥建立快照；
- 密码、Token、TOTP 密钥、JWT 密钥、API Key 和数据库凭据必须忽略或脱敏；
- 业务操作日志通过 `eventId`、聚合 ID 或 Trace ID 关联 JaVers Commit；
- 首期不在所有 JPA Repository 上全局自动代理，避免数据量和性能失控。

## 4.3 Sentry 前端异常

aries 使用 Sentry React SDK 替换当前内存 logger 的远程采集职责。

必须具备：

- React Error Boundary 异常上报；
- 未处理 Promise rejection 和全局脚本异常采集；
- Release 与 Git Commit 关联；
- 生产构建上传 Source Map，上传后避免公开暴露；
- 从后端错误响应提取 `traceId` 并写入 Sentry tag；
- 环境、路由、用户 ID 和业务模块作为受控上下文；
- 用户姓名、手机号、Token、请求正文和业务敏感字段默认不采集；
- DSN 缺失或 Sentry 不可用时静默降级，不阻塞页面。

本地 `logger.warn/error` 可以保留为薄适配器，但不得继续承担持久化职责，其实现改为控制台输出加 Sentry 上报。

## 4.4 Loki、Tempo 与采集层

推荐部署组合：

| 组件 | 职责 |
| --- | --- |
| Grafana Alloy | 收集主机或容器日志、接收 OTLP、统一标签和转发 |
| Loki | 保存和检索结构化应用日志 |
| Tempo | 保存和检索 OpenTelemetry Trace |
| Prometheus | 保存 Actuator/Micrometer 指标 |
| Grafana | 日志、链路、指标和告警统一入口 |

应用侧要求：

- 使用 Spring Boot 3.5 内建 `StructuredLogEncoder` 输出 ECS JSON，避免为相同能力新增编码器依赖；
- 固定输出时间、级别、logger、线程、服务名、环境、traceId、spanId 和消息；
- 业务标识只作为受控字段写入，禁止动态生成高基数 Loki Label；
- Trace ID 保持为日志字段，Grafana 中配置日志到 Tempo 的跳转；
- 生产采集使用标准输出或单一文件来源，避免 `backend.log` 与 Logback 文件重复采集；
- 应用只面向 OTLP/标准输出，不直接依赖 Loki 或 Tempo 客户端 API。

## 4.5 PostgreSQL 分区

`sys_operation_log` 按 `operation_time` 使用月度范围分区，建议通过 `pg_partman` 或受控运维任务提前创建未来分区。

建议策略：

- 当前热数据保留期确定为 12 个月，仅在完整月分区全部到期后处理，因此实际窗口最多不足 13 个月；
- 过期数据通过 detach/drop 分区处理，不执行全表大批量 `DELETE`；
- 需要长期保存时，先导出并校验分区，再删除原分区；
- 导出作业若仍由应用承担，使用 Spring Batch 管理作业状态、重试和断点；
- 分区键必须进入主键或唯一键设计，迁移前评估现有主键、外键和查询索引；
- 分区改造采用兼容建表、数据回填、读写切换、旧表清理四个阶段。

# 5. 目标事件流

## 5.1 成功业务操作

```text
Controller
  -> Application Service
      -> 校验状态与下游引用
      -> 修改聚合
      -> 发布领域事件
      -> 提交业务事务
          -> Spring Modulith 标记待投递事件
              -> Audit Event Handler
                  -> 幂等写入 sys_operation_log
                  -> 关联 JaVers Commit
                  -> 标记事件完成
```

## 5.2 失败业务操作

```text
Application Service 抛出异常
  -> 业务事务回滚
  -> GlobalExceptionHandler 生成标准错误响应
  -> Logback 输出 traceId 与异常上下文
  -> Loki 保存后端错误日志
  -> 前端 Sentry 记录错误及相同 traceId
```

失败操作只有在审计法规或安全策略明确要求时才写入 `sys_operation_log`，并必须使用独立事务，避免把未发生的领域事实记录为成功。

# 6. 数据模型演进

实施时通过新的 post-baseline Flyway 迁移逐步增加以下能力，禁止改写 `V1__baseline.sql` 或已经执行的迁移：

- `sys_operation_log.event_id`：领域事件唯一标识；
- `sys_operation_log.trace_id`：关联技术日志和链路；
- `sys_operation_log.aggregate_type`：聚合类型；
- `sys_operation_log.aggregate_id`：聚合标识，可复用或逐步替代现有 `record_id`；
- `sys_operation_log.event_version`：事件载荷版本；
- 必要的唯一约束和查询索引；
- Spring Modulith 事件发布注册表所需表；
- JaVers Repository 所需表或独立 schema；
- 新的分区表与迁移进度表。

迁移顺序：

1. 兼容新增列和事件发布表；
2. 新旧操作日志双写并核对；
3. 回填必要的历史关联字段；
4. 切换读取与查询；
5. 将旧表迁移到分区表；
6. 观察稳定后移除响应反射链和旧归档任务。

# 7. 分阶段实施

## 阶段一：前端异常平台

- 接入 Sentry React SDK；
- 配置环境变量、Release 和 Source Map 上传；
- 将 `AppErrorBoundary` 与 logger 接到 Sentry；
- 建立敏感数据过滤规则；
- 保持现有错误页面和 Trace ID 展示不变。

退出条件：生产异常可按 Release、路由和 Trace ID 查询，Sentry 故障不影响前端使用。

## 阶段二：集中日志与链路

- 后端日志切换为结构化 JSON；
- 部署 Grafana Alloy、Loki、Tempo、Prometheus 和 Grafana；
- 开启生产 OTLP 导出并配置采样率；
- 建立错误率、500、认证失败和事件消费积压告警；
- 移除重复日志落盘或重复采集路径。

退出条件：可从错误日志跳转到同 Trace，且能够按服务、环境、级别和 Trace ID 检索。

## 阶段三：领域事件试点

优先选择边界清晰且状态机已经稳定的采购订单与采购入库：

- 定义审核、反审核、完成采购和退回草稿事件；
- 接入 Spring Modulith 事件发布注册表；
- 新增幂等审计消费者；
- 与旧 `@OperationLoggable` 双写核对；
- 不在首个试点同时修改业务状态机规则。

退出条件：领域事件与旧操作日志的业务单号、操作人、动作和结果一致，事件失败可恢复。

## 阶段四：JaVers 试点

- 只对采购订单聚合启用字段差异；
- 明确忽略字段、敏感字段和集合比较规则；
- 将 JaVers Commit 与领域事件关联；
- 评估写放大、表增长和查询耗时。

退出条件：能够从一次操作日志查看对应字段差异，且不记录敏感数据。

## 阶段五：逐模块迁移业务审计

按采购、销售、物流、财务、系统设置的顺序迁移：

- 每个写操作发布明确事件；
- 删除对应 Controller 上的 `@OperationLoggable`；
- 去除业务服务对 HTTP Request Attribute 的写入；
- 当所有模块完成后，删除 `OperationLogResponseBodyAdvice`、请求体反射和通用结果推断；
- 保留少量安全访问审计和非领域管理操作的专用监听器。

退出条件：生产不再依赖响应体反射生成业务操作日志。

## 阶段六：操作日志分区

- 建立新分区表并准备未来分区；
- 在线回填历史数据；
- 短期双写或受控停写切换；
- 校验行数、主键、时间范围和查询结果；
- 切换查询后归档或删除旧表；
- 移除 `OperationLogArchiveService` 主路径。

退出条件：操作日志查询和写入使用分区表，过期清理不再执行大批量行删除。

# 8. 安全与隐私

- 技术日志、Sentry、领域事件和 JaVers 均采用字段白名单；
- 禁止记录密码、Cookie、Authorization、Refresh Token、API Key、TOTP/JWT 主密钥和数据库凭据；
- 请求正文默认不进入技术日志，确需记录时按接口显式授权和脱敏；
- Sentry 用户上下文仅保留内部用户 ID，不默认上报姓名、手机号和完整登录名；
- 审计日志的查看、导出和长期归档继续受资源权限控制；
- Loki、Tempo、Grafana、Sentry 和对象存储凭据只能通过部署密钥注入；
- 日志和审计数据的保留期限必须形成正式配置，不使用无限保留默认值。

# 9. 可靠性与降级

- Loki、Tempo、Grafana 或 Sentry 故障不得阻断业务；
- Spring Modulith 待投递事件必须可重试、可查询和可告警；
- 审计消费者以 `eventId` 保证幂等，重复投递不得产生重复日志；
- JaVers 写入失败的处理策略必须显式选择：降级并告警，或对受监管操作阻断提交；默认采用降级并告警；
- 分区创建失败必须在当前分区耗尽前告警，禁止等写入失败后再创建；
- 所有日志基础设施变更先在开发环境验证，再通过部署流程逐级启用。

# 10. 验收标准

## 10.1 领域事件与操作日志

- 审核、反审核、删除、导入和完成态流转均有明确事件类型；
- 每个成功业务事件最多生成一条主操作日志；
- 操作日志包含 `eventId`、聚合 ID、业务单号、操作人、时间和 Trace ID；
- 消费者重启或重复投递不会产生重复记录；
- 事件消费失败可查询、可重试并触发告警；
- 移除响应反射后，现有操作日志查询页面行为不回退。

## 10.2 可观测性

- 后端日志为结构化 JSON；
- Grafana 可按 Trace ID 关联 Loki 日志和 Tempo Trace；
- 生产 OTLP 采样率可通过环境变量调整；
- 同一错误的后端响应、后端日志和前端 Sentry 使用同一 Trace ID；
- 不存在控制台重定向与 Logback 文件导致的重复采集。

## 10.3 数据生命周期

- `sys_operation_log` 使用月度分区；
- 分区创建和保留策略可观测；
- 历史数据迁移前后行数、时间范围和关键索引一致；
- 过期清理通过分区生命周期完成；
- 长期归档产物具备数量、文件大小和校验和记录。

# 11. 风险与回滚

| 风险 | 控制措施 | 回滚方式 |
| --- | --- | --- |
| 领域事件遗漏 | 新旧双写、按模块核对 | 恢复旧注解链为主写 |
| 重复审计记录 | `eventId` 唯一约束、消费者幂等 | 暂停消费者并清理重复数据 |
| JaVers 写放大 | 限定聚合、忽略字段、监控表增长 | 关闭指定聚合审计 |
| Loki 标签基数失控 | 只允许低基数固定标签 | 调整 Alloy Pipeline，不改应用业务 |
| Sentry 泄露敏感字段 | 默认拒绝、发送前过滤、受控上下文 | 关闭 DSN 并删除问题事件 |
| 分区迁移锁表 | 分阶段建表和回填、低峰切换 | 保留旧表读写路径 |
| 观测平台不可用 | 应用异步或失败开放 | 关闭导出，继续本地标准输出 |

# 12. 决策

本方案采用以下原则：

1. 业务事实使用明确领域事件，不通过 HTTP 响应反射推断；
2. Spring Modulith 负责事件可靠交付，审计消费者负责持久化；
3. JaVers 只补充字段差异，不替代业务操作语义；
4. Sentry 接管前端异常的远程采集；
5. Loki、Tempo、Prometheus 和 Grafana 形成统一可观测平台；
6. PostgreSQL 分区接管操作日志生命周期，手写 CSV/GZIP 归档退出主路径；
7. 所有迁移按模块双写、核对、切换和清理，不进行一次性整体替换。

# 13. 实施记录

## 13.1 2026-07-15 第一阶段

已完成：

- aries 接入 `@sentry/react` 10.64.0，并将 logger、React Error Boundary 和应用启动异常接入统一上报；
- Sentry 默认过滤 Cookie、请求头、请求正文和用户敏感信息，仅保留用户 ID，并关联后端 Trace ID；
- 生产构建仅在 Sentry 发布凭据完整时生成并上传 hidden Source Map，上传完成后删除构建目录中的 Source Map；
- leo 使用 Spring Boot 3.5 内建 `StructuredLogEncoder` 在生产环境输出 ECS JSON，主日志统一写标准输出，错误日志保留独立 JSON 滚动文件；
- 引入 Spring Modulith 1.4.12 JPA 事件发布注册表，新增 V79 迁移和可靠业务操作事件审计消费者；
- `sys_operation_log` 增加事件 ID、Trace ID、聚合类型和事件版本，并通过事件 ID 唯一索引保证消费幂等；
- 采购订单“审核”和“反审核”作为领域事件试点，试点端点退出旧 HTTP 响应反射审计，其他模块继续使用原审计链。

已验证：

- 后端编译成功，Flyway 目标门禁识别 V79，开发库迁移到 V79 后健康检查通过；
- Spring Modulith 发布表与操作日志新增字段结构正确，当前没有未完成事件积压；
- 前端类型检查、Biome、ESLint 和生产构建通过；未配置 DSN 时 Sentry 静默降级。

后续工作：

- JaVers 延后到聚合白名单、敏感字段规则和写放大预算明确后再接入；
- Grafana Alloy、Loki、Tempo、Prometheus 和 Grafana 的基础设施部署、采集规则与告警仍待实施；
- PostgreSQL 分区改造仍待确定数据保留期限、兼容迁移方案和回滚窗口；
- 采购入库及其他模块的领域事件继续按模块迁移并核对，不在本阶段批量切换。

## 13.2 2026-07-15 第二阶段

已完成：

- 引入 JaVers 7.9.0 SQL Repository，V80 建立官方兼容存储结构和业务事件幂等认领表；采购订单新增、编辑、审核、反审核、完成采购、退回已审核和删除均生成业务事件及白名单快照，逐件件重明细不进入 JaVers；
- 采购入库、销售订单、销售出库和物流单的新增、编辑、审核、反审核、删除及完成态流转改为显式业务事件，相关写端点退出 HTTP 响应反射审计；
- 采购入库自动完成或重开采购订单、销售出库自动核定或重开销售订单、删除销售出库导致销售订单退回草稿等间接状态变化均补充明确事件；
- 增加 Prometheus Registry 与可靠事件积压指标 `leo_business_events_incomplete`、`leo_business_events_oldest_age_seconds`，生产管理端口仅绑定 `127.0.0.1:57218`；
- 增加 Alloy、Loki、Tempo、Prometheus、Grafana 原生部署配置，Loki 仅将低基数日志级别设为 Label，Trace ID 保留为结构化字段并支持跳转 Tempo；
- V81 将 `sys_operation_log` 切换为按 `operation_time` 月度分区的同名表，以身份表和触发器保证 `id`、`log_no`、`event_id` 跨分区唯一；旧表保留为 `sys_operation_log_unpartitioned` 供迁移核对；
- 停止应用内 CSV/GZIP 操作日志归档调度及配置，并删除无调用方的旧归档服务；
- V82、V83 前滚退役已裁剪合同和供应商对账模块遗留的 FILE 打印模板，避免资源归档后启动同步器因文件缺失终止应用。
- `OperationLogResponseBodyAdvice` 已删除，HTTP 命令审计不再读取响应体或通过 DTO 方法、字段反射推断业务号、记录 ID、模块键和结果状态；
- 物流对账单新增、编辑、审核、反审核和删除已改为可靠业务事件，保存并审核同时记录保存事实与审核事实；
- 增加操作日志默认分区行数、未来连续可用月分区数指标及 Prometheus 告警，当前默认分区为 0 行、未来连续可用分区为 12 个月；
- 开发与生产测试启动脚本改为等待 HTTP 就绪：后端以 `/api/health` 成功响应为准，前端以首页可访问为准，并补充 Spring、Maven 和 Vite 启动失败关键字识别；脚本记录独立进程组 PID，停止或失败时清理完整进程组，避免 Maven、DevTools 或 Vite 残留进程重新占用端口。
- 操作日志热数据保留期正式配置为 12 个月，新增过期活动行指标、Prometheus 告警和只读分区诊断命令；到期 detach/drop 继续使用明确分区名的增量 Flyway，不在应用中执行动态 DDL。

已验证：

- 开发库 Flyway V1 至 V83 全部校验成功，V81 迁移前旧表、分区表与身份表均为 138 行；121 个分区已创建，现有数据全部位于 `sys_operation_log_y2026m07`，全局身份字段无重复；
- JaVers SQL Repository Bean 正常创建，`event_publication` 当前无未完成投递，开发健康接口 `/api/health` 持续返回 UP；
- 后端使用 `maven.test.skip=true` 编译成功，生产 Flyway 目标门禁识别 V83，未生成或运行测试；
- 开发启动脚本完成前后端重启验证，仅在后端健康接口返回成功、前端首页可访问后报告就绪；
- 前端 TypeScript、Biome、ESLint 和生产构建通过；
- Loki、Tempo、Prometheus 与 Grafana YAML 已通过结构化语法解析；当前主机未安装 Alloy、Loki、Tempo、Prometheus 和 Grafana 原生二进制，组件自身配置校验、安装和启动仍须通过生产部署审批执行。

待部署与受控清理：

- 由运维在生产主机安装并使用对应版本原生命令校验 Alloy、Loki、Tempo、Prometheus 和 Grafana 配置，再逐个启用服务；
- 分区到期 detach、校验、脱机归档和 drop 属于运维生命周期动作；保留期已确定为 12 个月，具体回滚窗口和每批明确分区仍须在对应 Flyway 执行前审批。
