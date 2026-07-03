---
title: Leo 后端手搓基础设施迁移计划
date: 2026-07-03
status: draft
owner: PolarisTime / 浮浮酱
scope: leo 后端 (Spring Boot)
---

# 1. 背景与结论

## 1.1 背景

本次扫描目标是识别 leo 后端中已经由项目自研实现、但存在成熟 Spring / Java / 云原生开源方案可替代的基础设施代码，减少 NIH（Not Invented Here）维护成本。

扫描范围：

- 链路追踪与日志关联
- 限流
- 缓存
- ID 生成
- 对象存储
- 密钥加密
- 幂等
- 数据库备份
- 分页、导入导出、打印渲染等横切能力

## 1.2 总体结论

应迁移的不是所有自研代码，而是“通用基础设施且已有成熟生态方案”的部分。

优先级：

1. **立即清理**：S3 手写 SigV4 / HttpClient 历史残留
2. **优先迁移**：TraceId、读缓存、限流
3. **风险评估后迁移**：密钥加密、ID 生成
4. **长期外部化**：数据库备份
5. **保留**：业务审计、打印渲染、业务幂等语义、导入导出业务映射

# 2. 迁移原则

- KISS：每个 PR 只处理一类基础设施，不做横向大爆炸改造
- YAGNI：不引入平台级组件，除非当前代码已经承担了对应平台职责
- DRY：重复 Redis JSON 缓存、重复限流头、重复响应写出逻辑要收敛
- SOLID：业务服务不直接依赖第三方 SDK 细节，通过领域接口或基础设施 adapter 隔离
- Boot 4 兼容：避免引入 Spring Cloud Sleuth、Brave 专有 API、厂商 SDK 强绑定
- 可回滚：迁移期间保留兼容层，任一 PR 可独立回滚

# 3. 迁移清单

## 3.1 TraceId / 链路追踪

| 项 | 当前实现 | 目标方案 |
|---|---|---|
| 文件 | `TraceIdFilter`、`ApiResponse`、`logback-spring.xml` | Spring Boot Actuator + Micrometer Tracing + OpenTelemetry |
| 问题 | 只能做 correlation id，没有 span、采样、跨服务传播、exporter | 使用 Boot 官方 observability 主线 |
| 计划 | 见 `2026-07-03-traceid-observability-upgrade-plan.md` | 单独实施 |

决策：

- 不使用 Spring Cloud Sleuth
- 不在业务代码直接调用 OpenTelemetry SDK
- 保留 `X-Trace-Id` 作为兼容响应头

## 3.2 限流

当前实现：

- `TokenBucketService`
- `GlobalRateLimitFilter`
- `RateLimitAspect`
- `RateLimit`
- `src/main/resources/db/token_bucket.lua`

问题：

- 自研 Redis Lua token bucket 需要自己维护原子性、TTL、异常策略、兼容性
- `pom.xml` 已引入 Resilience4j，但生产代码未使用，属于“依赖存在但能力未落地”
- 限流语义散在 filter 与 aspect 两处，响应头写出重复

目标方案：

| 方案 | 适用性 | 结论 |
|---|---|---|
| Bucket4j + Redis | 支持 token bucket 与分布式 bucket，贴合当前算法 | 推荐 |
| 网关限流 | 适合全局 IP / 路由级限流 | 生产长期推荐 |
| Resilience4j RateLimiter | 更偏本地实例限流，不天然覆盖 Redis 分布式 token bucket | 不作为主替代 |

迁移目标：

- 用 Bucket4j 替代自研 `token_bucket.lua`
- 保持 `@RateLimit` 对业务控制器的使用方式不变
- 保持 `X-RateLimit-*` 与 `Retry-After` 响应头行为不变
- 全局限流可先接 Bucket4j，长期可迁到网关

## 3.3 缓存

当前实现：

- `RedisJsonCacheSupport`
- `CacheConfig`
- 业务服务中大量 `getOrLoad(...)`

问题：

- 已配置 `RedisCacheManager`，但多数读缓存未使用 Spring Cache 注解
- 手写 JSON 序列化、读 miss、写入、失效、scan 删除等逻辑
- 新人难以从业务方法直接识别缓存语义

目标方案：

- 读缓存迁移到 Spring Cache：`@Cacheable` / `@CacheEvict`
- 并发原语类 Redis 用法保留手写：CAS、Hash、Set、计数器、幂等、会话快照

计划：

- 参考 `2026-07-03-cache-dual-track-design.md`
- 本文档只纳入总迁移路线，不重复展开缓存迁移细节

## 3.4 对象存储 S3

当前实现：

- 主路径：`S3CompatibleAttachmentStorage` 已使用 AWS SDK v2 `S3Client` / `S3Presigner`
- 历史残留：`S3Signer` 手写 SigV4，`DefaultS3RequestExecutor` 手写 `HttpClient`

问题：

- `S3Signer` / `S3RequestExecutor` / `DefaultS3RequestExecutor` 只被测试引用，生产代码无引用
- 手写 SigV4 容易在 canonical header、query、path escaping、payload hash 上出兼容问题
- 与 AWS SDK v2 重复

目标方案：

- 删除 `S3Signer`
- 删除 `S3RequestExecutor`
- 删除 `DefaultS3RequestExecutor`
- 删除对应测试
- 主路径继续统一使用 AWS SDK v2

## 3.5 密钥加密

当前实现：

- `TotpSecretCryptor`
- `OssSecretCryptor`
- `AttachmentContentCryptor`
- `SecurityKeyService`

问题：

- AES/GCM 自行封装，密钥派生存在自定义截断/补零逻辑
- TOTP、OSS Secret、附件内容复用同一安全材料衍生不同用途密钥
- 缺少成熟 envelope encryption、key id、版本化密钥、轮换协议

目标方案候选：

| 方案 | 适用性 | 结论 |
|---|---|---|
| 云 KMS / Vault Transit | 生产最佳，支持密钥托管与轮换 | 长期推荐 |
| Google Tink | 应用内 envelope encryption，封装更成熟 | 中期可选 |
| Spring Security Crypto | 简单本地加密封装 | 可作为过渡 |

迁移目标：

- 先新增 `SecretEncryptionService` 抽象，不让业务直接依赖具体加密实现
- 新写入数据使用新 envelope 格式，包含版本与 key id
- 旧数据读取保留 legacy decrypt
- 后台任务逐步重加密旧数据

## 3.6 ID 生成

当前实现：

- `SnowflakeIdGenerator`
- 大量业务实体直接使用 `nextId()`
- 代码仍使用 `SnowflakeIdGenerator.getInstance()` 静态入口，实测仅 4 处（见下），迁移面可控

`getInstance()` 静态调用现存清单（截至 2026-07-04 扫描）：

| 文件 | 行 | 上下文 |
|---|---|---|
| `common/support/TradeItemMaterialSupport.java` | 91 | `piece.setId(SnowflakeIdGenerator.getInstance().nextId())` |
| `common/service/AbstractCrudService.java` | 56 | `idGenerator != null ? idGenerator : SnowflakeIdGenerator.getInstance()`（带回退） |
| `common/service/BusinessCreateIdResolver.java` | 168 | `idGenerator != null ? idGenerator : SnowflakeIdGenerator.getInstance()`（带回退） |
| `purchase/order/service/PurchaseOrderItemPieceWeightService.java` | 210 | `piece.setId(SnowflakeIdGenerator.getInstance().nextId())` |

注：`SnowflakeIdGenerator` 本身已是 `@Component` + 构造器注入 `@Value("${leo.id.machine-id:0}")`，DI 化仅剩上述 4 处调用方改造，PR6 工作量收敛在 4 个文件。

问题：

- 默认 `leo.id.machine-id=0`，多实例部署时有 ID 冲突风险
- `synchronized` 单实例生成，吞吐和时钟回拨策略都由项目维护
- 静态 `getInstance()` 破坏 DI，可测试性差

目标方案候选：

| 方案 | 适用性 | 结论 |
|---|---|---|
| PostgreSQL sequence / identity | 数据库强一致，简单可靠 | 长期推荐，但迁移影响面大 |
| UUIDv7 / ULID / TSID | 应用生成、趋势递增、无机器号 | 可评估 |
| 继续 Snowflake 但用成熟库 | 降低算法维护成本 | 过渡可选 |

短期目标：

- 禁止新增 `SnowflakeIdGenerator.getInstance()` 调用
- 所有使用方改为构造器注入
- 启动时校验生产环境必须显式配置唯一 `leo.id.machine-id`

长期目标：

- 新模块优先使用 DB sequence 或 UUIDv7/TSID
- 老实体迁移需单独评估数据库主键和前端 ID 兼容性

## 3.7 幂等

当前实现：

- `HttpIdempotencyFilter`
- `HttpIdempotencyService`
- `IdempotentAspect`
- `IdempotentKeyService`

判断：

- 这是业务协议的一部分，不是纯技术轮子
- Redis Lua 状态机表达了 `ACQUIRED`、`DUPLICATE_PENDING`、`DUPLICATE_COMPLETED`、`PARAMETER_MISMATCH`
- 可替代方案存在，但未必能直接覆盖当前响应语义

目标方案：

- 短期保留
- 收敛两套幂等：HTTP Header 幂等和方法注解幂等要明确使用边界
- 中期可评估 Spring Integration Idempotent Receiver 或 Redisson 原语

## 3.8 数据库备份

当前实现：

- `DatabaseBackupService` 在应用内调用 `pg_dump` / `psql`
- `ScheduledDatabaseBackupService`
- `DatabaseExportTaskService`

问题：

- 业务应用承担数据库运维职责
- 进程权限、命令可用性、密码环境变量、超时、备份一致性都由应用维护
- 生产环境更适合专业备份工具或云数据库备份

目标方案：

| 场景 | 方案 |
|---|---|
| 自管 PostgreSQL | pgBackRest / Barman / WAL-G |
| 云数据库 | 云厂商自动备份 + PITR |
| 应用内导出 | 保留为开发/小规模部署辅助能力，不作为生产主备份 |

短期目标：

- 明确标注应用内备份不是生产级备份
- 生产配置默认禁用导入能力
- 导出任务继续可用，但不替代数据库备份体系

# 4. 不迁移清单

| 模块 | 判断 |
|---|---|
| JWT | 使用 `jjwt`，不是手写 JWT |
| TOTP 算法 | 使用 `dev.samstevens.totp`，不是手写 TOTP |
| 密码哈希 | 使用 Spring Security `PasswordEncoder` |
| Excel 导入导出 | 使用 Apache POI / Commons CSV，业务映射自定义合理 |
| PDF 水印和打印表单 | 使用 iText，版式渲染是业务能力 |
| 操作日志 | 审计语义强，Spring Interceptor/Advice 只是承载方式 |
| 分页参数 | 虽可用 Spring `Pageable`，但当前有 sort 白名单与统一错误语义，先保留 |

# 5. 实施顺序

## 5.0 PR 依赖关系

```
PR1 (S3 删除, 独立)
   │
   ├── 可与 PR2 / PR3 任意并行

PR2 (TraceId 迁移)  ──┐
                      ├── 基础设施先就位，为 PR4 提供可观测性
PR3 (缓存迁移)    ───┘
   │
   └── PR3 完成后 Redis 配置稳定，PR4 才合入

PR4 (限流迁移) ←── 依赖 PR2 可观测性 + PR3 Redis 配置稳定
   │
   ├── PR6 (ID 治理, 与 PR4 可并行)
   │
PR5 (密钥迁移, 独立, 见 5.1 拆分)
   │
PR7 (DB 备份外部化, 文档级, 独立)
```

依赖推断：

- PR1 纯删代码，无任何前置依赖，应立即执行
- PR2、PR3 互不依赖，可与 PR1 并行，但 PR3 改的 Redis 连接配置需先于 PR4 稳定
- PR4 依赖 PR2（限流误拦需要 TraceId + 指标观测）和 PR3（Redis 配置不可在限流迁移期同时变动）
- PR5、PR6、PR7 相互独立，可分别排期

## 5.1 PR 列表

范围：

- 删除 `S3Signer`
- 删除 `S3RequestExecutor`
- 删除 `DefaultS3RequestExecutor`
- 删除对应测试

验证：

- `S3CompatibleAttachmentStorageTest`
- `AttachmentStorageResolverTest`
- 附件上传、下载、预签名直传相关测试

风险：

- 低。生产代码无引用。

回滚：

- 还原被删除文件即可。

## PR2：TraceId 迁移到 Micrometer Tracing

范围：

- 按 `2026-07-03-traceid-observability-upgrade-plan.md` 实施
- 引入 Actuator、Micrometer Tracing、OTel exporter
- 收敛 `TraceIdFilter`

验证：

- 错误响应 traceId 与日志 traceId 一致
- 前端错误展示不回退
- Collector 不可用不阻塞应用启动

风险：

- 中。影响日志排查链路。

回滚：

- 恢复旧 `TraceIdFilter` 与 logback pattern。

## PR3：缓存双轨制迁移

范围：

- 按 `2026-07-03-cache-dual-track-design.md` 实施
- 读缓存改 Spring Cache
- Redis 并发原语保留手写
- **复用现有 `CacheConfig` 的 `RedisCacheManager` Bean**（已含 static / hot 双 cache 配置），不新增 CacheManager Bean，避免双 manager 冲突

验证：

- 空 list 不缓存
- 事务回滚不误删缓存
- TTL 命名空间正确
- Redis 健康检查正常

风险：

- 中。影响多个下拉选项和系统配置读路径。

回滚：

- 恢复对应业务服务 `RedisJsonCacheSupport.getOrLoad(...)`。

## PR4：限流迁移 Bucket4j

范围：

- 新增 Bucket4j Redis 依赖
- 新增 `RateLimiterGateway` 或 `DistributedRateLimiter`
- 保持 `@RateLimit` 注解不变
- 替换 `TokenBucketService` 内部实现
- 删除 `token_bucket.lua`
- 移除未使用 Resilience4j 依赖，或明确保留用于后续熔断/重试

前置基线（合入前必须先建立）：

- 建立《现状 Redis 故障行为基线》：注入 Redis 连接异常 / 高延迟，记录当前 `TokenBucketService` 是 fail-open（放行）还是 fail-closed（拒绝）、`Retry-After` 头是否丢失、异常是否吞掉
- PR4 合入后回放同一基线场景，逐项比对，确认 Bucket4j 行为"与现状一致"再删除 `token_bucket.lua`

验证：

- 全局 IP 限流
- API Key 限流
- 用户维度限流
- `Retry-After` 与 `X-RateLimit-*` 响应头
- Redis 不可用时 fail-open / fail-closed 策略与现状一致

风险：

- 中高。影响所有请求入口。

回滚：

- 保留旧 `TokenBucketService` 分支或 feature flag，一键切回 Redis Lua。

## PR5：密钥加密抽象与新格式（拆分 5a / 5b / 5c）

PR5 安全敏感，影响登录、2FA、OSS，一次性改完回滚成本过高，故拆为三步独立合入，每步可独立回滚。

### PR5a：新增抽象与 legacy 适配器（只读不改）

范围：

- 新增 `SecretEncryptionService` 抽象
- 新增 legacy 适配器：包装现有 `TotpSecretCryptor` / `OssSecretCryptor` / `AttachmentContentCryptor` 旧格式读取能力
- 本步**不修改任何写入路径**，只新增并行读通道，业务行为完全不变

验证：

- 现有密钥通过 legacy 适配器可正确解密
- 抽象层不破坏现有单测
- 写入路径仍是旧格式（黑盒不变）

风险：**低**。纯新增，不改写入。

回滚：删除新生成的类即可，无数据迁移。

### PR5b：新写入切 envelope 格式（含双写灰度）

范围：

- 新写入数据使用 versioned envelope 格式（含 key id + version）
- 读链路先尝试 envelope，失败回退 legacy
- 双写灰度：先在「新格式写入」开启，**旧数据不主动重写**
- 灰度策略见下表

灰度阈值与监控：

| 阶段 | 比例 | 监控指标 | 回滚触发 |
|---|---|---|---|
| 灰度 1 | 新功能写入用新格式，存量写入不变 | 解密失败率（应 = 0） | 解密失败率 > 0.01% 立即回滚 |
| 灰度 2 | 全部写入用新格式 | 解密失败率、回退 legacy 命中率 | 回退命中率 > 5% 暂停观察 |
| 灰度 3 | 稳定 7 天后进入 PR5c 重加密 | 解密失败率 | — |

验证：

- TOTP Secret 旧数据可读（回退 legacy 成功）
- OSS Secret 旧数据可读
- 新数据可加解密
- 错误密钥无法解密
- key rotation 单测

风险：**高**。安全敏感且影响登录、2FA、OSS。

回滚：**写链路切回旧格式**（legacy 适配器保留，新生成的 envelope 数据后续可解，因 key 仍在）。预期单次回滚完成。

### PR5c：后台批量重加密 + 清理 legacy

范围：

- 后台任务分批重加密旧数据为 envelope 格式
- 分批批次小（如每批 100 条），每批可暂停
- 全部完成并稳定 N 天后，清理 legacy decrypt 路径（需单独 PR 评审）

验证：

- 重加密前后数据都可解
- 进度可监控、可暂停、可恢复
- 解密失败率持续为 0

风险：**中**。后台任务不影响写入主路径。

回滚：停止后台任务，已重加密数据保持 envelope，未处理数据保持 legacy，回退写链路至 PR5b 灰度 2 状态。

风险登记更新：R2 由「envelope 一次切」降级，因双写 + 灰度 + 三步可独立回滚，整体可控。

## PR6：ID 生成治理

范围：

- 删除新增代码中的 `SnowflakeIdGenerator.getInstance()` 调用（现存仅 4 处，见第 3.6 节清单）
- 4 处存量静态调用改为构造器注入（`TradeItemMaterialSupport`、`AbstractCrudService`、`BusinessCreateIdResolver`、`PurchaseOrderItemPieceWeightService`）
- 生产环境启动校验 `leo.id.machine-id`
- 编写新 ID 策略 ADR：DB sequence vs UUIDv7/TSID

生产环境判定依据：

- 引入独立开关 `leo.id.strict-machine-id`（默认 `false`，生产配置显式 `true`），开启后 `machine-id == 0` 即启动失败
- 不依赖 `spring.profiles.active` 判定生产环境，避免 profile 套娃与误判
- 测试 / 开发环境不开启此开关，避免本地启动无故失败

验证：

- 所有实体创建测试
- 多线程 ID 唯一性测试
- 配置缺失启动失败测试（`strict-machine-id=true` 且 `machine-id=0` 时启动抛错）
- 4 处 `getInstance()` 改造后 DI 单测通过

风险：

- 中。短期不换 ID 算法，只治理风险。

回滚：

- 放宽启动校验，保留旧生成器。

## PR7：数据库备份职责外部化

范围：

- 文档标注应用内备份定位
- 生产默认禁用导入能力
- 新增运维文档：pgBackRest / WAL-G / 云数据库备份选型

验证：

- 配置关闭导入时接口不可用
- 开发环境导出仍可用

风险：

- 中。影响运维流程，不应和业务发布混在一起。

回滚：

- 恢复旧配置默认值。

# 6. 依赖调整计划

| 依赖 | 当前状态 | 计划 |
|---|---|---|
| `resilience4j-spring-boot3` | 已引入，生产代码未使用 | PR4 决策：不用则删除 |
| `resilience4j-ratelimiter` | 已引入，生产代码未使用 | PR4 决策：不用则删除 |
| AWS SDK v2 S3 | 已使用 | 保留 |
| Bucket4j | 未引入 | PR4 引入 |
| Actuator / Micrometer Tracing / OTel exporter | 未引入 | PR2 引入 |
| Tink / KMS SDK / Vault SDK | 未引入 | PR5 评估后选择，不提前引入 |

# 7. 验收标准

## 7.1 项级验收（清单）

- [ ] S3 手写 SigV4 与手写 HttpClient 残留删除
- [ ] traceId 不再由手写 filter 生成核心链路 ID
- [ ] 读缓存业务点迁移到 Spring Cache，Redis 并发原语保留
- [ ] 限流不再依赖自研 Redis Lua token bucket
- [ ] 未使用的 Resilience4j 依赖被删除或有明确用途
- [ ] 密钥加密有版本化 envelope 与 legacy 兼容读取
- [ ] 生产环境不能使用默认 `leo.id.machine-id=0` 静默启动
- [ ] 应用内数据库备份不再被定义为生产级主备份方案
- [ ] 每个 PR 均有单测或集成测试覆盖，并能独立回滚

## 7.2 量化阈值（PR 合入判定）

| PR | 指标 | 阈值 | 采集方式 |
|---|---|---|---|
| PR4 | 限流 QPS 误差（Bucket4j vs 现状基线） | < 5% | 压测对比 |
| PR4 | Redis 故障 fail-open/fail-closed 行为 | 与现状 100% 一致 | 故障注入基线回放 |
| PR5b | envelope 写入解密失败率 | = 0% | 生产监控 + 单测 |
| PR5b | legacy 回退命中率（灰度初期） | < 5% | 读链路埋点 |
| PR5c | 重加密后历史数据解密失败率 | = 0% | 全量抽样校验 |
| PR3 | 缓存命中率（迁移后 7 天） | 不低于迁移前 | Redis 指标 |
| PR3 | 脏读 / 缓存不失效事件 | = 0 起 | 事务回滚与 evict 单测 |
| PR2 | 错误 body / X-Trace-Id / log 三方一致 | 100% | 一致性集成测试 |
| PR6 | `getInstance()` 残留调用 | = 0 处 | 静态扫描 |
| PR6 | 多线程并发 ID 唯一性 | 0 冲突 | 并发单测 |

# 8. 风险登记

| ID | 风险 | 等级 | 缓解 |
|---|---|---|---|
| R1 | 限流迁移导致误拦截正常请求 | 高 | feature flag + 压测 + 回滚旧 TokenBucket |
| R2 | 密钥迁移导致旧 secret 无法解密 | 中 | 拆分 5a/5b/5c 三步独立回滚 + 双写灰度 + 解密失败率监控触发回滚（详见 PR5） |
| R3 | ID 生成策略调整影响历史主键 | 高 | 短期只治理配置和 DI，不直接换算法 |
| R4 | 缓存迁移导致脏读或缓存不失效 | 中 | 事务回滚与 evict 单测 |
| R5 | TraceId 迁移后排障链路断裂 | 中 | 错误 body/header/log 三方一致性测试 |
| R6 | 删除 S3 残留误删隐藏路径 | 低 | `rg` 确认生产代码无引用，附件测试覆盖 |

# 9. 推荐执行节奏

第一阶段先做低风险降噪：

1. PR1 S3 残留删除
2. PR2 TraceId 迁移
3. PR3 缓存迁移

第二阶段处理运行时行为：

4. PR4 限流迁移
5. PR6 ID 生成治理

第三阶段处理安全与运维：

6. PR5a 密钥抽象 + legacy 适配器（只读）
7. PR5b 新格式双写灰度
8. PR5c 后台重加密 + 清理 legacy
9. PR7 数据库备份外部化

注：PR5a 应在安全可控前提下尽早合入（可与第一阶段并行），为后续留出双写窗口；PR5b/PR5c 严格按灰度节奏推进。
