---
title: Leo TraceId 与可观测性升级计划
date: 2026-07-04
status: validated-stage-1
owner: PolarisTime / 浮浮酱
scope: leo 后端 (Spring Boot) + aries 前端错误展示
---

# 1. 背景与结论

## 1.1 当前实现

leo 后端当前的 traceId 是项目手写的 correlation id 实现，不是完整分布式链路追踪框架。

当前链路：

- `TraceIdFilter` 基于 `OncePerRequestFilter` 读取请求头 `X-Trace-Id`
- 请求头缺失或空白时，使用 `UUID` 生成 8 位短 ID
- 将 traceId 写入响应头 `X-Trace-Id`
- 将 traceId 写入 SLF4J `MDC` 的 `traceId`
- `logback-spring.xml` 通过 `%X{traceId:-}` 输出日志关联 ID
- `ApiResponse.failure(...)` 从 MDC 读取 traceId，并只在失败响应 body 中返回
- aries 前端从错误响应 body/header 提取 traceId，挂到 `Error.traceId` 后用于错误页面和弹窗展示

## 1.2 结论

后续成熟化升级应采用 **Spring Boot Actuator + Micrometer Tracing + OpenTelemetry**。

不建议采用 Spring Cloud Sleuth。Sleuth 是旧路线，Spring Boot 3 后官方主线已经迁移到 Micrometer Tracing；考虑后续 Spring Boot 4.x 升级，应避免引入 Sleuth 形成迁移负担。

# 2. 目标与非目标

## 2.1 目标

- 使用 Spring Boot 官方可观测性体系生成标准 `traceId` / `spanId`
- 日志中输出标准 trace/span 关联信息
- 保留前端错误展示 traceId 的用户体验
- 支持后续接入 OTLP Collector、Grafana Tempo、Jaeger、Zipkin 或商业 APM
- 保持 Spring Boot 4.x 升级路径清晰
- 逐步替换手写 traceId 逻辑，避免一次性大改影响排障能力

## 2.2 非目标

- 本阶段不建设完整监控平台
- 本阶段不强制引入具体厂商 APM SDK
- 本阶段不重写全局异常结构
- 本阶段不改业务接口语义
- 本阶段不把前端作为 trace 根节点，除非后续明确需要浏览器端链路追踪

# 3. 框架选型

## 3.1 推荐方案

| 层级 | 方案 | 说明 |
|---|---|---|
| 应用观测入口 | `spring-boot-starter-actuator` | Spring Boot 官方 actuator/observability 入口 |
| Tracing 抽象 | Micrometer Tracing | Spring Boot 3/4 主线 |
| Tracer 实现 | OpenTelemetry bridge | 使用 OTel 生态，避免绑定单一平台 |
| Trace 导出 | OTLP exporter | 应用发 OTLP，后端采集平台可替换 |
| 采集网关 | OpenTelemetry Collector | 可转发到 Tempo/Jaeger/Zipkin/APM |

## 3.2 不选 Sleuth

原因：

- Spring Boot 3 后 Sleuth 不再是官方推荐主线
- Boot 4.x 继续沿用 Micrometer Tracing / OpenTelemetry 方向
- 引入 Sleuth 会增加未来升级与依赖冲突风险

## 3.3 保留手写 traceId 的边界

短期可以保留 `X-Trace-Id` 作为兼容入口，但它应降级为业务兼容层，而不是核心 tracing 实现。

最终目标是：

- 核心链路 ID 使用 Micrometer/OTel 标准 trace id
- 日志使用标准 `traceId` / `spanId`
- 错误响应继续暴露可排查的 traceId
- 如仍需兼容 `X-Trace-Id`，应作为 baggage 或单独 request id 处理，避免混淆标准 trace id

# 4. Spring Boot 4.x 兼容策略

Spring Boot 4.x 仍支持 tracing 与 OpenTelemetry 方向，并提供 OpenTelemetry SDK 自动配置能力。升级策略应遵循以下约束：

- 依赖只使用 Spring Boot BOM 管理的版本，不手写固定 tracing 版本
- 使用 `management.tracing.*` 配置，不写自定义 tracing 初始化代码
- 日志关联使用 `logging.pattern.correlation`，避免维护自定义 logback trace pattern
- 避免依赖 Sleuth、Brave 专有 API 或厂商 SDK API
- 应用侧导出统一走 OTLP，平台选择留在 Collector 层

# 5. 迁移方案

## 5.1 阶段一：引入框架但不改变接口行为

后端新增依赖：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>

<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

基础配置：

```yaml
management:
  tracing:
    sampling:
      probability: 1.0

logging:
  pattern:
    correlation: "[%X{traceId:-},%X{spanId:-}] "
  include-application-name: false
```

要求：

- 保留当前错误响应 body 中的 `traceId`
- 前端错误展示不变
- 日志同时能看到 traceId 与 spanId
- 不在业务代码中直接调用 OpenTelemetry SDK

## 5.2 阶段二：统一错误响应 traceId 来源

将 `ApiResponse.failure(...)` 的 traceId 来源调整为标准 tracing MDC：

- 优先读取 MDC `traceId`
- 无 traceId 时兼容读取当前手写 filter 的 traceId
- 保持字段名 `traceId` 不变，避免影响前端

验收点：

- 业务异常、认证异常、权限异常、500 异常均返回 `traceId`
- 同一次请求的错误 body traceId 与日志 traceId 一致

## 5.3 阶段三：收敛或替换 `TraceIdFilter`

当前 `TraceIdFilter` 的职责拆分：

| 当前职责 | 迁移后归属 |
|---|---|
| 生成 traceId | Micrometer Tracing / OTel |
| MDC 写入 | Micrometer Tracing 自动处理 |
| 响应头 `X-Trace-Id` | 可保留为兼容输出 |
| 接收客户端 `X-Trace-Id` | 需要安全规范化后决定是否作为 baggage/request id |

建议改造方向：

- 不再由 `TraceIdFilter` 生成核心 trace id
- 保留一个轻量 `TraceResponseHeaderFilter`，只负责把当前 MDC `traceId` 写入响应头 `X-Trace-Id`
- 如果继续接受客户端 `X-Trace-Id`，必须做 trim、长度上限、字符白名单校验
- 非法客户端 trace id 不透传进日志，改用框架生成的新 trace id

## 5.4 阶段四：接入 OTLP Collector

部署侧增加 Collector：

- 应用只配置 OTLP endpoint
- Collector 负责转发到 Tempo/Jaeger/Zipkin/APM
- 不让应用代码绑定具体观测平台

生产建议：

- 默认采样率低于 100%，按流量与成本配置
- 错误请求可在 Collector 或后端平台侧提高保留优先级
- 本地和测试环境可以使用 100% 采样便于验证

## 5.5 执行记录（2026-07-04）

状态：阶段一/二已完成，阶段四 Collector 实机接入留待部署环境验证。

- 已引入 `spring-boot-starter-actuator`、`micrometer-tracing-bridge-otel`、`opentelemetry-exporter-otlp`，不使用 Sleuth，也不在业务代码直接依赖 OpenTelemetry SDK。
- 已配置 `management.tracing.sampling.probability`、`management.otlp.tracing.endpoint`、`management.otlp.tracing.export.enabled`、OTLP connect/read timeout。
- OTLP 导出默认关闭，避免本地/CI 在没有 Collector 时启动失败；生产可通过环境变量开启。
- 已将日志 pattern 调整为同时输出 MDC `traceId` 与 `spanId`。
- 已将 `TraceIdFilter` 收敛为兼容层：优先输出当前 MDC `traceId` 到 `X-Trace-Id` 响应头；客户端 `X-Trace-Id` 仅在无标准 traceId 时作为安全规范化 fallback。
- 已将 `GlobalRateLimitFilter` 顺序后移，避免 429 响应绕过 tracing scope。
- 已将 `X-Trace-Id` 加入 CORS exposed headers，保持前端读取 header 的兼容性。
- 定向验证命令：`mvn test -Dtest="TraceIdFilterTest,SecurityConfigTest,GlobalRateLimitFilterTest,ApiResponseTest,HealthServiceTest,ObservabilityConfigurationTest"`。
- 定向验证结果：54 个测试通过，Failures 0，Errors 0，Skipped 0。
- 全量验证命令：`mvn test`。
- 全量验证结果：5584 个测试通过，Failures 0，Errors 0，Skipped 0；JaCoCo `INSTRUCTION` / `BRANCH` / `LINE` / `COMPLEXITY` / `METHOD` / `CLASS` 的 `missed` 均为 0。

# 6. 前端策略

aries 当前只消费后端 traceId，不生成请求 trace id。

短期保持：

- `auth-interceptor` 继续从错误响应 body/header 提取 traceId
- `AppResult`、`AppErrorBoundary`、`ErrorView` 继续展示可复制 Trace ID

后续可选增强：

- 如果需要浏览器到后端完整链路，可使用 W3C Trace Context 的 `traceparent`
- 不建议前端继续自定义生成 `X-Trace-Id` 作为核心 trace id
- 如果要读取成功响应 header 中的 `X-Trace-Id`，后端 CORS 需要暴露该 header，前端 API wrapper 也要保留响应元信息

# 7. 风险与控制

| ID | 风险 | 控制方式 |
|---|---|---|
| R1 | 手写 traceId 与 OTel traceId 并存导致排查混乱 | 阶段二明确错误响应 traceId 只取标准 MDC `traceId` |
| R2 | 客户端传入超长或恶意 `X-Trace-Id` 污染日志 | 对兼容 header 做长度与字符白名单 |
| R3 | CORS 下前端读不到 `X-Trace-Id` 响应头 | 如需要成功响应 traceId，显式配置 exposed headers |
| R4 | 采样率过高带来存储成本 | 生产按流量配置采样率，本地/测试 100% |
| R5 | 直接依赖 OTel SDK 导致 Boot 4 升级困难 | 业务代码只依赖 Micrometer/Spring 配置，不直接调 SDK |
| R6 | 异步线程 MDC 丢失 | 对 `@Async`、线程池、调度任务单独验证上下文传播 |
| R7 | 接入 exporter 后 CI 或本地启动依赖外部 Collector | exporter endpoint 必须可配置，默认不阻塞应用启动 |

# 8. 测试计划

后端测试：

- `TraceIdFilterTest` 或替代 filter 测试：响应头输出当前 MDC traceId
- `ApiResponseTest`：失败响应携带 MDC traceId
- `GlobalExceptionHandlerTest`：主要异常路径返回 traceId
- `SecurityConfigTest`：如暴露 `X-Trace-Id`，验证 CORS allowed/exposed headers
- 集成测试：同一请求日志 MDC traceId 与错误响应 traceId 一致

前端测试：

- `auth-interceptor.spec.ts`：继续覆盖 body/header traceId 提取
- `client.spec.ts`：API 失败响应 traceId 挂到 Error
- 错误展示组件测试：Trace ID 可见且可复制

人工验证：

- 本地请求任一 500 接口，确认响应 body、响应头、日志三处 traceId 一致
- 启动 OTLP Collector 后确认 trace 可在后端平台查询
- Collector 不可用时应用仍可启动，业务请求不被阻断

# 9. 实施顺序

1. [x] 新增 Actuator、Micrometer Tracing、OTel exporter 依赖与基础配置
2. [x] 调整日志 pattern，验证标准 `traceId/spanId` 输出
3. [x] 统一 `ApiResponse.failure(...)` traceId 来源为 MDC `traceId`
4. [x] 将当前 `TraceIdFilter` 收敛为响应头兼容 filter
5. [x] 补充后端回归测试
6. [ ] 本地接入 OTLP Collector 验证 trace export
7. 评估生产采样率与 Collector 部署参数

# 10. 验收标准

- [x] 不再依赖手写 filter 生成核心 trace id
- [x] 日志包含标准 `traceId` 与 `spanId`
- [x] 错误响应 body 中的 `traceId` 与日志 traceId 一致
- [x] 响应头 `X-Trace-Id` 兼容输出仍可用
- [x] 前端错误展示 Trace ID 的行为不回退
- [x] 客户端传入非法 `X-Trace-Id` 不会进入日志
- [ ] 本地可通过 OTLP Collector 查询请求 trace
- [x] Collector 不可用时应用可正常启动
- [x] 方案不引入 Sleuth，保留 Spring Boot 4.x 升级路径
