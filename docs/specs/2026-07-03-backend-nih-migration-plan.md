---
title: Leo 后端手搓基础设施迁移计划（技术扩写版）
date: 2026-07-04
status: validated
owner: PolarisTime / 浮浮酱
scope: leo 后端 (Spring Boot 3.5)
revision: 3
---

# 1. 背景与结论

## 1.1 背景

本次扫描目标是识别 leo 后端中已经由项目自研实现、但存在成熟 Spring / Java / 云原生开源方案可替代的基础设施代码，减少 NIH（Not Invented Here）维护成本。

**项目基线**（截至 2026-07-04）：

| 维度 | 当前值 |
|---|---|
| Spring Boot | 3.5.x |
| Java | 21 |
| Redis 客户端 | Spring Data Redis (`StringRedisTemplate`) |
| JSON | Jackson `ObjectMapper` |
| 数据库 | PostgreSQL（自管 / 云） |
| S3 SDK | AWS SDK v2（`S3Client` / `S3Presigner`） |
| 安全 | Spring Security + jjwt + `dev.samstevens.totp` |

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

应迁移的不是所有自研代码，而是"通用基础设施且已有成熟生态方案"的部分。

优先级：

1. **立即清理**：S3 手写 SigV4 / HttpClient 历史残留
2. **优先迁移**：TraceId、读缓存、限流
3. **风险评估后迁移**：密钥加密、ID 生成
4. **长期外部化**：数据库备份
5. **保留**：业务审计、打印渲染、业务幂等语义、导入导出业务映射

## 1.3 当前执行状态（2026-07-04）

本迁移文档已完成后端 NIH 扫描、迁移分批、风险登记与测试基线记录。

- 已完成：S3 历史残留只读审计、不可删除类边界确认、后端覆盖率缺口补齐、PR2 TraceId 可观测性基础迁移。
- 未执行：S3 历史残留文件删除。该操作属于文件删除，需显式确认后再执行。
- 无数据库结构、约束、索引、种子数据或数据修复变更，本轮不需要新增 Flyway 脚本。
- 验证命令：`mvn test`。
- 验证结果：5584 个测试通过，Failures 0，Errors 0，Skipped 0。
- 覆盖率结果：JaCoCo `INSTRUCTION` / `BRANCH` / `LINE` / `COMPLEXITY` / `METHOD` / `CLASS` 的 `missed` 均为 0，后端覆盖率 100%。

# 2. 迁移原则

- **KISS**：每个 PR 只处理一类基础设施，不做横向大爆炸改造
- **YAGNI**：不引入平台级组件，除非当前代码已经承担了对应平台职责
- **DRY**：重复 Redis JSON 缓存、重复限流头、重复响应写出逻辑要收敛
- **SOLID**：业务服务不直接依赖第三方 SDK 细节，通过领域接口或基础设施 adapter 隔离
- **Boot 4 兼容**：避免引入 Spring Cloud Sleuth、Brave 专有 API、厂商 SDK 强绑定
- **可回滚**：迁移期间保留兼容层，任一 PR 可独立回滚
- **可观测性先行**：任何运行时行为变更（限流、缓存）前，必须先确保 TraceId + Metrics 链路可用
- **测试基线前置**：每个 PR 合入前跑全量回归（`mvn test`），记录覆盖率基线；后续 PR 覆盖率不得下降

# 3. 迁移清单

## 3.1 TraceId / 链路追踪

### 3.1.1 当前实现

**`TraceIdFilter`** (`common/config/TraceIdFilter.java`)：

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString()
                    .substring(0, PrecisionConstants.ID_PREFIX_LENGTH);
        }
        response.setHeader(TRACE_ID_HEADER, traceId);
        try (var ignored = MDC.putCloseable(MDC_KEY, traceId)) {
            filterChain.doFilter(request, response);
        }
    }
}
```

**`ApiResponse`** (`common/api/ApiResponse.java`) — 错误响应自动带入 traceId：

```java
public record ApiResponse<T>(int code, String message, T data,
        String timestamp, String traceId, RateLimitContext.Snapshot rateLimit) {

    public static ApiResponse<Void> failure(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.getCode(), message, null,
                DateTimeFormatSupport.now(), currentTraceId(), currentRateLimit());
    }

    private static String currentTraceId() {
        String traceId = MDC.get("traceId");
        return (traceId != null && !traceId.isBlank()) ? traceId : null;
    }
}
```

### 3.1.2 当前能力缺口

| 能力 | 现状 | 缺失 |
|---|---|---|
| Correlation ID | ✅ `MDC.putCloseable` + `X-Trace-Id` 响应头 | — |
| Span（跨方法追踪） | ❌ | 无法追踪一次请求内部的 DB 调用、Redis 调用耗时 |
| 跨服务传播 | ❌ | 无 W3C Trace Context / B3 Propagation |
| 采样策略 | ❌ | 无法按比例采样或按错误采样 |
| Exporter | ❌ | 无法导出到 Jaeger / Zipkin / OTel Collector |
| Baggage | ❌ | 无法跨服务透传业务字段（如 tenantId） |

### 3.1.3 目标方案

| 项 | 目标 |
|---|---|
| 核心依赖 | Spring Boot Actuator + Micrometer Tracing + OpenTelemetry (OTel) |
| Bridge | `micrometer-tracing-bridge-otel` |
| Exporter | OTLP (gRPC/HTTP) → OTel Collector |
| Span 传播 | W3C Trace Context (`traceparent` / `tracestate`) |
| 采样 | `management.tracing.sampling.probability`（默认 0.1，生产按需） |

**决策记录**：

- ❌ 不使用 Spring Cloud Sleuth（已废弃）
- ❌ 不在业务代码直接调用 OpenTelemetry SDK
- ✅ 保留 `X-Trace-Id` 作为兼容响应头（`ApiResponse.traceId` 字段从 Micrometer `currentTraceContext().context().traceId()` 读取）
- ✅ `TraceIdFilter` 收敛为仅做 `X-Trace-Id` 兼容头写入，核心链路 ID 由 Micrometer 的 `ObservationFilter` 或 Server HTTP Observation 生成

### 3.1.4 迁移步骤

1. `pom.xml` 引入 `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`（test scope 用 `zipkin` exporter 供本地开发）
2. `application.yml` 配置 `management.tracing.sampling.probability` 与 `management.otlp.tracing.endpoint`
3. 改造 `TraceIdFilter`：读 `ObservationRegistry` 或 `Tracer.currentSpan()` 写入 `ApiResponse.traceId`，不再自行生成 UUID
4. 删除 `TraceIdFilter` 中的 `MDC.putCloseable`（Micrometer 已通过 `ObservationHandler` 自动注入 MDC）
5. OTel Collector 不可用时不应阻塞应用启动（`spring.application.defaults` 配置 `management.health.defaults.enabled=false` 仅限 tracing）

### 3.1.5 验收

- 错误响应 `traceId` 与 `logback-spring.xml` 的 `%X{traceId:-}` pattern 一致
- 前端 `ApiResponse.error.traceId` 展示不回退
- Collector 不可用（connection refused）不阻塞应用启动
- W3C `traceparent` header 出现在 outgoing HTTP 请求中

### 3.1.6 执行记录（2026-07-04）

状态：已完成 PR2 阶段一/二基础迁移，保留兼容回滚面。

- `pom.xml` 已引入 `spring-boot-starter-actuator`、`micrometer-tracing-bridge-otel`、`opentelemetry-exporter-otlp`，版本由 Spring Boot BOM 管理。
- `application.yml` 已配置 `management.tracing.sampling.probability` 与 `management.otlp.tracing.*`；OTLP 导出默认关闭，避免本地/CI 依赖 Collector。
- `application-prod.yml` 将生产默认采样率降为 `0.1`，仍可通过 `LEO_TRACING_SAMPLING_PROBABILITY` 覆盖。
- `logback-spring.xml` 已从单独输出 `traceId` 调整为同时输出 `traceId/spanId`。
- `TraceIdFilter` 已后移到 Spring Boot `ServerHttpObservationFilter` 之后：优先使用 Micrometer/MDC 中已有 `traceId` 写出 `X-Trace-Id`；仅在无标准 traceId 时使用安全规范化后的兼容 header 或短 fallback。
- 客户端传入的 `X-Trace-Id` 增加 trim、长度上限和字符白名单，非法值不会写入 MDC 或响应头。
- `SecurityConfig` 已将 `X-Trace-Id` 加入 CORS exposed headers。
- `GlobalRateLimitFilter` 已后移到 TraceId 兼容 filter 之后，429 响应 body 可继续从 MDC 带出 traceId。
- 定向验证命令：`mvn test -Dtest="TraceIdFilterTest,SecurityConfigTest,GlobalRateLimitFilterTest,ApiResponseTest,HealthServiceTest,ObservabilityConfigurationTest"`。
- 定向验证结果：54 个测试通过，Failures 0，Errors 0，Skipped 0。

---

## 3.2 限流

### 3.2.1 当前实现栈

```
请求入口
  ├── GlobalRateLimitFilter  (OncePerRequestFilter, Ordered.HIGHEST_PRECEDENCE)
  │     └── TokenBucketService.tryConsume("global:ip:{ip}", 100, 150, 1)
  │           └── StringRedisTemplate.execute(DefaultRedisScript, ...)
  │                 └── db/token_bucket.lua (Hash-based Redis token bucket)
  │
  ├── RateLimitAspect        (@Around @RateLimit 注解的 Controller 方法)
  │     ├── 维度1: API Key    → key="apikey:{hash}"
  │     ├── 维度2: User       → key="user:{userId}:{method}"
  │     └── 维度3: IP (legacy) → key="ip:{ip}:{method}" / legacy fixed-window
  │
  └── @RateLimit 注解        (定义在 security/permission/RateLimit.java)
        rate=-1 / capacity=-1 / tokens=1 / maxRequests=10 / duration=1 / timeUnit=MINUTES
```

### 3.2.2 核心实现细节

**`TokenBucketService`** — 自研 Redis Lua token bucket：

```java
@Service
public class TokenBucketService {
    private static final String KEY_PREFIX = "rate-limit:bucket:";
    private static final double DEFAULT_RATE = 100.0;   // tokens/sec
    private static final int DEFAULT_CAPACITY = 150;     // burst

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> script;
    // Lua 脚本: db/token_bucket.lua → KEYS[1]=bucket_key, ARGV[1]=rate,ARGV[2]=capacity,...

    @SuppressWarnings("unchecked")
    public TokenBucketResult tryConsume(String dimensionKey,
            double rate, int capacity, int requested) {
        String bucketKey = KEY_PREFIX + dimensionKey;
        List<Long> result = redisTemplate.execute(
                script,
                List.of(bucketKey),
                String.valueOf(rate),    // ARGV[1]
                String.valueOf(capacity), // ARGV[2]
                String.valueOf(System.currentTimeMillis()), // ARGV[3]
                String.valueOf(requested), // ARGV[4]
                String.valueOf(redisTuningProperties.rateLimitBucketTtlFloorSeconds()) // ARGV[5]
        );
        if (result == null || result.size() < 3) {
            return TokenBucketResult.ALLOW_FALLBACK;  // ← fail-open
        }
        return new TokenBucketResult(
                result.get(0) == 1L,  // allowed
                result.get(1),         // remaining
                result.get(2));        // retry_after_ms
    }

    public record TokenBucketResult(boolean allowed, long remaining, long retryAfterMs) {
        static final TokenBucketResult ALLOW_FALLBACK =
                new TokenBucketResult(true, 1, 0);  // ← Redis 故障时放行
    }
}
```

**`token_bucket.lua`** — 49 行 Hash-based Redis token bucket：

```lua
-- KEYS[1]: bucket hash key (fields: tokens, ts)
-- ARGV[1]=rate, ARGV[2]=capacity, ARGV[3]=now_ms, ARGV[4]=requested, ARGV[5]=ttl_floor_sec
local bucket_key = KEYS[1]
local rate = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])
local ttl_floor = tonumber(ARGV[5])

local last_tokens = tonumber(redis.call("hget", bucket_key, "tokens"))
if last_tokens == nil then last_tokens = capacity end
local last_time = tonumber(redis.call("hget", bucket_key, "ts"))
if last_time == nil then last_time = now end

local delta = math.max(0, now - last_time)
local filled = math.min(capacity, last_tokens + (delta * rate / 1000))
-- ← 毫秒级精度填充

local allowed = filled >= requested
local new_tokens = filled
local retry_after = 0
if allowed then
    new_tokens = filled - requested
else
    retry_after = math.ceil((requested - filled) / rate * 1000)
end

local fill_time = math.ceil(capacity / rate)
local ttl = math.max(fill_time * 2, ttl_floor)
redis.call("hset", bucket_key, "tokens", new_tokens, "ts", now)
redis.call("expire", bucket_key, ttl)
return {allowed and 1 or 0, math.floor(new_tokens), retry_after}
```

**`GlobalRateLimitFilter`** — 全局 IP 限流（所有请求入口的第一道防线）：

```java
@Component
public class GlobalRateLimitFilter extends OncePerRequestFilter implements Ordered {
    private static final double GLOBAL_RATE = 100;
    private static final int GLOBAL_CAPACITY = 150;
    private static final Set<String> EXCLUDED_PATHS = Set.of(
            "/api/health", "/api/auth/ping", "/api/auth/captcha",
            "/api/setup/status", "/api/meta");

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }

    @Override
    protected void doFilterInternal(...) {
        TokenBucketResult result = tokenBucketService.tryConsume(
                "global:ip:" + ip, GLOBAL_RATE, GLOBAL_CAPACITY, 1);
        if (!result.allowed()) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
            response.setHeader("X-RateLimit-Limit", String.valueOf(GLOBAL_CAPACITY));
            response.setHeader("X-RateLimit-Remaining", "0");
            // ← 响应头写出（重复：与 RateLimitAspect.reject() 逻辑相同但各自实现）
        }
    }
}
```

**`@RateLimit` 使用点**（截至扫描）：

| Controller | 方法 | 注解参数 |
|---|---|---|
| `AttachmentController` | 文件上传相关方法 | `@RateLimit`（默认值） |
| `AuthController` | 登录相关方法 | `@RateLimit`（默认值） |

### 3.2.3 现存问题

1. **自研 Lua 原子性**：`hget` → 计算 → `hset` + `expire` 非原子 pipeline，Redis `MULTI/EXEC` 或 Lua 脚本本身虽是原子的，但 `ALLOW_FALLBACK` 策略是硬编码的 fail-open，无法按场景定制
2. **Resilience4j 僵尸依赖**：`pom.xml` 引入 `resilience4j-spring-boot3` + `resilience4j-ratelimiter`，但 `rg resilience4j src/main` = 0 命中，属于纯占位
3. **响应头写出重复**：`GlobalRateLimitFilter` 和 `RateLimitAspect.reject()` 各有一套 `X-RateLimit-*`/`Retry-After` 写出逻辑，内容相同但代码重复
4. **限流维度混合**：Aspect 中 API Key / User / IP 三维度 + legacy fixed-window fallback 混在一个方法里，单一 `tryConsume` 超过 80 行
5. **异常吞掉无可见性**：`TokenBucketService` catch 后仅 `log.error` + fail-open，无 metrics 计数

### 3.2.4 目标方案

**Bucket4j + Redis（`bucket4j-redis` 模块，Lettuce/Jedis backend）**：

Bucket4j 原生提供：

- `Bucket4j.dialect(redis)` → `ProxyManager` → `bucket.tryConsume(n)`
- 与当前 Redis Lua 实现**算法等价**（token bucket + DistributedBucket），但**维护责任在 Bucket4j 社区**
- 支持 `BlockingBucket`（等几个 token）和 `SchedulingBucket`（按速率调度），但本项目不需要
- Lettuce backend 与当前项目假设的 Lettuce 连接器一致（需确认 `pom.xml` 中 `spring-boot-starter-data-redis` 的 connector 选择）

**迁移收敛点**：

```
新 TokenBucketService (内部改用 Bucket4j)
    ↑ 对外 API 不变: tryConsume(dimensionKey, rate, capacity, requested) → TokenBucketResult
    ↑ 响应头写出收敛到单一 RateLimitHeaderWriter
    ↑ GlobalRateLimitFilter / RateLimitAspect 不感知底层实现变化
```

**`pom.xml` 变更**：

```xml
<!-- 删除 -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-ratelimiter</artifactId>
</dependency>

<!-- 新增 -->
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-redis</artifactId>
    <version>8.10.1</version>  <!-- 需与当前 Lettuce 版本对齐 -->
</dependency>
```

### 3.2.5 迁移步骤

1. 建立《现状 Redis 故障行为基线》（注入异常/超时，记录 fail-open/fail-closed 行为、Retry-After 存在性）
2. 新增 `RateLimitHeaderWriter` 收敛 `X-RateLimit-*`/`Retry-After` 写出
3. 新增 Bucket4j 依赖，`TokenBucketService` 内部实现替换为 Bucket4j ProxyManager
4. 回放基线场景，确认一致
5. 删除 `db/token_bucket.lua` + Resilience4j 依赖
6. 压测确认 QPS 误差 < 5%

### 3.2.6 验证

- 全局 IP 限流（`/api/**` → 429）
- API Key 限流（`AttachmentController` 上传方法）
- 用户维度限流（`AuthController` 登录方法）
- `Retry-After` / `X-RateLimit-Limit` / `X-RateLimit-Remaining` / `X-RateLimit-Reset` 响应头
- Redis 不可用时 fail-open 与现状一致（ALLOW_FALLBACK）
- Bucket4j vs 现状 QPS 误差 < 5%（JMeter / wrk2）

---

## 3.3 缓存

### 3.3.1 当前实现

**双轨并存**：

| 轨道 | 实现 | 使用范围 |
|---|---|---|
| 手写 | `RedisJsonCacheSupport` — Jackson 序列化 + `StringRedisTemplate` (`opsForValue().get/set`) | 10+ 业务服务通过 `getOrLoad(key, ttl, type, loader)` |
| Spring Cache | `CacheConfig` — `RedisCacheManager` with static/hot 双 cache 配置 | **已配置但全项目 0 处 `@Cacheable` 使用** |

**`RedisJsonCacheSupport` 核心 API**：

```java
public <T> T getOrLoad(String key, Duration ttl, TypeReference<T> typeRef, Supplier<T> loader) {
    Optional<T> cached = read(key, typeRef);   // Redis GET → Jackson deserialize
    if (cached.isPresent()) return cached.get();
    T loaded = loader.get();                    // ← 无锁！并发 miss 时 loader 被多次调用
    write(key, loaded, ttl);                    // Redis SET with TTL jitter
    return loaded;
}

public void deleteByPattern(String pattern) {
    // RedisConnection.scan() + 批量 delete，有 maxScanKeys 上限保护
}

public void deleteAfterCommit(String key) {
    afterCommitExecutor.run(() -> delete(key));  // 事务提交后删除
}
```

**`CacheConfig`**（已配置未使用）：

```java
@Configuration
@EnableCaching
public class CacheConfig {
    public static final String CACHE_STATIC = "static";  // 静态数据（部门、仓库等低频变更）
    public static final String CACHE_HOT = "hot";        // 热数据（用户会话、计数等）

    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper, RedisTuningProperties props) {
        Jackson2JsonRedisSerializer<Object> serializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);

        // static cache: TTL from props.cache.static-ttl, disable null caching
        RedisCacheConfiguration staticConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(props.withTtlJitter(props.getCache().getStaticTtl()))
                .disableCachingNullValues()
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair.fromSerializer(serializer));

        RedisCacheConfiguration hotConfig = /* 同上, TTL 来自 props.cache.hot-ttl */;
        return RedisCacheManager.builder(connectionFactory)
                .withCacheConfiguration(CACHE_STATIC, staticConfig)
                .withCacheConfiguration(CACHE_HOT, hotConfig)
                .build();
    }
}
```

**`getOrLoad` 调用点**（10+ 服务）：

| 服务 | 使用场景 | 缓存类型建议 |
|---|---|---|
| `DepartmentService` | 部门树 | `@Cacheable(CACHE_STATIC)` |
| `SupplierService` | 供应商下拉 | `@Cacheable(CACHE_STATIC)` |
| `CustomerService` | 客户下拉 | `@Cacheable(CACHE_STATIC)` |
| `CompanySettingService` | 公司配置 | `@Cacheable(CACHE_STATIC)` |
| `GeneralSettingQueryService` | 系统开关 | `@Cacheable(CACHE_STATIC)` |
| `DashboardSummaryService` | 仪表板汇总 | `@Cacheable(CACHE_HOT)` |
| `SystemSwitchService` | 功能开关 | `@Cacheable(CACHE_STATIC)` |
| `WarehouseSelectionSupport` | 仓库选项 | `@Cacheable(CACHE_STATIC)` |
| `TradeItemMaterialSupport` | 物料选项 | `@Cacheable(CACHE_STATIC)` |

### 3.3.2 现存问题

1. **getOrLoad 无锁**：并发 cache miss 时 `loader.get()` 被多次调用（thundering herd），Redis `SETNX` + 短暂过期锁可缓解但当前未实现
2. **CacheConfig 已配未用**：`RedisCacheManager` 静态/hot 双 cache + Jackson 序列化 + TTL jitter 全套就绪，全项目 0 处 `@Cacheable`
3. **新人识别成本高**：`getOrLoad(...)` 不如 `@Cacheable("static")` 直观，缓存语义藏在方法体内
4. **空值处理不一致**：`CacheConfig` 已 `disableCachingNullValues()`，但 `RedisJsonCacheSupport` 的 `read()` 对空 JSON 反序列化失败时直接 `delete(key)`，不区分"值为空"和"序列化异常"

### 3.3.3 目标方案

**读缓存迁移到 Spring Cache `@Cacheable` / `@CacheEvict`**：

```java
// 迁移前
public List<DepartmentDTO> getDepartmentTree() {
    return redisJsonCacheSupport.getOrLoad(
            "dept:tree", Duration.ofHours(2), new TypeReference<>() {}, this::loadDeptTree);
}

// 迁移后
@Cacheable(value = CacheConfig.CACHE_STATIC, key = "'dept:tree'")
public List<DepartmentDTO> getDepartmentTree() {
    return loadDeptTree();
}
```

**并发原语类 Redis 用法保留手写**：
- CAS / 分布式锁 / Hash / Set / Sorted Set 计数器
- 幂等状态机（`ACQUIRED` / `DUPLICATE_PENDING` / `DUPLICATE_COMPLETED`）
- 会话快照 / 临时令牌

### 3.3.4 迁移步骤

详见子文档 `2026-07-03-cache-dual-track-design.md`。

**关键约束**：

- **复用现有 `CacheConfig.RedisCacheManager`**（含 static/hot 双 cache），不新增 CacheManager Bean
- 空 list 不缓存（`unless = "#result?.size() == 0"`）
- 事务回滚后同步 evict（`@CacheEvict` 在 `@Transactional` 方法上 + `afterCommitExecutor`）
- TTL 命名空间使用 `spring.cache.redis.time-to-live` 配置项，不复用 `RedisTuningProperties` 手写 TTL 参数

### 3.3.5 验证

- 空 list 不缓存
- 事务回滚不误删缓存（`@Transactional` + `@CacheEvict` 在 commit 前/后行为）
- TTL 命名空间正确（static 2h / hot 5min）
- `scanBatchSize` / `deleteBatchSize` / `maxScanKeys` 参数在 pattern 删除中有效
- 缓存命中率（迁移后 7 天）不低于迁移前

---

## 3.4 对象存储 S3

### 3.4.1 当前实现

**生产路径**：`S3CompatibleAttachmentStorage` 已使用 AWS SDK v2：

```java
// 主路径已使用 SDK v2
S3Client s3Client;        // 标准 SDK 客户端
S3Presigner s3Presigner;  // 预签名 URL 生成
```

**历史残留**（审计后确认 4 个文件，仅测试或待删类引用）：

| 文件 | 内容 | 生产引用 |
|---|---|---|
| `S3Signer.java` | 手写 AWS SigV4 签名（canonical request → StringToSign → HMAC-SHA256） | 0（仅自身） |
| `S3RequestExecutor.java` | 手写 HTTP 请求执行接口 | 0（仅自身） |
| `DefaultS3RequestExecutor.java` | `HttpURLConnection` 的实现 | 0（仅自身） |
| `S3ChecksumUtil.java` | 手写 SHA-256 / HMAC / Hex 编码辅助 | 仅 `S3Signer`，随 `S3Signer` 删除后无生产引用 |

仍需保留：

- `S3ClientProvider.java`：仍被 `S3CompatibleAttachmentStorage` 和 `OssSettingService` 生产路径使用。
- `S3PathParser.java`：仍被 `S3CompatibleAttachmentStorage` 生产路径使用。

### 3.4.2 问题

- 手写 SigV4 容易在 canonical header 大小写、query parameter escaping、payload hash、chunked transfer encoding 上出兼容性 bug
- 这三个文件的维护成本（安全修复、JDK 升级适配）由项目承担，但收益为零
- AWS SDK v2 已覆盖签名、重试、连接池、DNS 故障转移，手写版本是纯冗余

### 3.4.3 目标方案

**纯删除**：

- `S3Signer.java`
- `S3RequestExecutor.java`
- `DefaultS3RequestExecutor.java`
- `S3ChecksumUtil.java`
- `S3SignerTest.java`
- `S3RequestExecutorTest.java`
- `DefaultS3RequestExecutorTest.java`
- `S3ChecksumUtilTest.java`

主路径继续统一使用 AWS SDK v2。

### 3.4.4 验证

- `rg S3Signer\|S3RequestExecutor\|DefaultS3RequestExecutor src/main` = 0
- `S3CompatibleAttachmentStorageTest`（附件上传/下载/预签名直传）通过
- `AttachmentStorageResolverTest` 通过

### 3.4.5 执行记录（2026-07-04）

状态：审计完成，删除待确认。

- 只读审计确认 `S3Signer`、`S3RequestExecutor`、`DefaultS3RequestExecutor`、`S3ChecksumUtil` 可删除。
- 只读审计确认 `S3ClientProvider`、`S3PathParser` 不可删除，仍在生产路径中使用。
- 建议删除后运行：`mvn test -Dtest="S3CompatibleAttachmentStorageTest,S3CompatibleAttachmentStorageExtendedTest,AttachmentStorageResolverTest,OssSettingServiceTest"`。
- 建议编译确认：`mvn test -DskipTests`。

---

## 3.5 密钥加密

### 3.5.1 当前实现

**加密体系**（4 个组件）：

```
SecurityKeyService          ← 密钥生命周期管理（来源、版本、轮换）
    ├── TotpSecretCryptor   ← AES-256/GCM/NoPadding, 12-byte IV, 128-bit tag
    │       └── deriveKey() ← UTF-8 bytes → 截断/补零到 32 bytes (!!)
    │
    ├── OssSecretCryptor    ← 复用 TotpSecretCryptor, key = SecurityKeyService.getActiveTotpMaterial().secretValue()
    │
    └── AttachmentContentCryptor ← 独立 AES-256/GCM, MAGIC="LEOENC1", key = SHA-256("attachment-content:" + totpMaterial)
```

**`TotpSecretCryptor.deriveKey()` 的问题**：

```java
private SecretKey deriveKey(String encryptionKey) {
    byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
    byte[] padded = new byte[32];
    System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
    return new SecretKeySpec(padded, "AES");
}
// 问题：
// 1. 输入 key < 32 bytes 时：尾部补 0x00（弱密钥）
// 2. 输入 key > 32 bytes 时：截断（丢失熵）
// 3. 应使用 PBKDF2 / HKDF 而非截断补零
```

**`AttachmentContentCryptor.key()` 的键派生**：

```java
private SecretKeySpec key() {
    String material = securityKeyService.getActiveTotpMaterial().secretValue();
    byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(("attachment-content:" + material).getBytes(StandardCharsets.UTF_8));
    return new SecretKeySpec(digest, "AES");
}
// 优点：使用了 domain-separated SHA-256 派生，比 TotpSecretCryptor 好
// 问题：仍缺标准 KDF（如 HKDF-SHA256），且未包含 IV/nonce 防复用
```

**`SecurityKeyService` 密钥生命周期**：

```java
// 密钥来源
SOURCE_CONFIG    = "CONFIG_FILE"    // 启动兜底（环境变量 LEO_JWT_SECRET / TOTP_ENCRYPTION_KEY）
SOURCE_DATABASE  = "DATABASE"       // 数据库托管（security_secret 表）

// 密钥状态
STATUS_ACTIVE    // 当前主密钥
STATUS_RETIRED   // 已轮转，仍在验证窗口期内（JWT 验证旧 token 签名）

// 轮转流程（rotateTotpMasterKey）：
// 1. 生成新 48-byte Base64 随机 secret
// 2. 用旧 key 解密所有 UserAccount.totpSecret
// 3. 用新 key 重新加密
// 4. 用旧 key 解密所有 OssSetting.encryptedSecretKey
// 5. 用新 key 重新加密
// 6. retire 旧 key, persist 新 key
```

### 3.5.2 现存问题

| 问题 | 严重程度 | 详情 |
|---|---|---|
| 自研 deriveKey 截断/补零 | **高** | 非标准 KDF，熵损失或弱密钥风险 |
| 同一安全材料派生多个用途密钥 | **中** | TOTP / OSS / Attachment 三个 cryptor 共享 `totpMaterial`，虽 `AttachmentContentCryptor` 做了 domain separation，但 `OssSecretCryptor` 直接用原始 `secretValue()` |
| 缺 envelope encryption | **中** | 无 key id / version 标记在密文上，无法判断用哪个 key 解密 |
| 缺 key rotation 自动重加密 | **高** | `rotateTotpMasterKey` 是同步全量扫描 `UserAccount` + `OssSetting`，如在轮转中新增账号/设置会遗漏 |
| IV 用 `SecureRandom` 而非 `SecureRandom.getInstanceStrong()` | **低** | `AttachmentContentCryptor` 用了 `new SecureRandom()`（普通强度），`TotpSecretCryptor` 用了 `getInstanceStrong()`（高强度）——不一致 |

### 3.5.3 目标方案选型

| 方案 | 适用性 | 结论 |
|---|---|---|
| **Google Tink** | 应用内 AEAD + envelope encryption + key versioning，原生 Java 支持 | **中期推荐**：比 Spring Security Crypto 强，比 KMS 轻 |
| 云 KMS / Vault Transit | 生产最佳，支持密钥托管与轮换 | **长期推荐**：外部化密钥管理 |
| Spring Security Crypto | `Encryptors` 仅 PBKDF2 + CBC/GCM，无 key id/轮换 | ❌ 过渡价值低，建议跳过直接上 Tink |
| 保持现有但规范化 KDF | 最小改动 | ❌ 不改"自己维护加密库"的本质 |

**推荐路径**：

```
新写入 → Tink AEAD (AES-256-GCM, key id + version in ciphertext prefix)
旧读取 → legacy adapter (保留现有 TotpSecretCryptor / OssSecretCryptor 解密能力)
旧重加密 → 后台批量 → 全部完成后删除 legacy adapter
```

**Tink envelope 密文格式**（5-byte prefix）：

```
[0x01] [key_id: 4 bytes big-endian] [IV: 12 bytes] [ciphertext + tag]
```

迁移后无需在密文额外维护 key version 字段，Tink 原生支持 `KeysetHandle` 轮换。

### 3.5.4 迁移步骤（PR5 拆分）

详见 PR5a / PR5b / PR5c 三阶段。

**迁移前必须**：

- 审计所有 call site：哪些方法读/写 `encryptedSecret` → 列清单
- 确认 `SecurityKeyService.rotateTotpMasterKey()` 的同步全量扫描边界（UserAccount 数量、OssSetting 数量）
- 建立当前密文总条数基线

### 3.5.5 验证

- TOTP Secret 旧数据（Base64[IV|ciphertext]）可读
- OSS Secret 旧数据可读
- 新数据可加解密（Tink AEAD）
- 错误密钥无法解密 → `GeneralSecurityException`
- `KeysetHandle.rotate()` 后新旧 key 均可解密
- key rotation 单测（新旧 key 交叉解密）

---

## 3.6 ID 生成

### 3.6.1 当前实现

**`SnowflakeIdGenerator`** (`common/support/SnowflakeIdGenerator.java`)：

```java
@Component
public class SnowflakeIdGenerator {
    private static volatile SnowflakeIdGenerator instance;

    private static final long EPOCH = 1704038400000L;   // 2024-01-01 00:00:00 UTC
    private static final long MAX_MACHINE_ID = 1023L;    // 10-bit
    private static final long SEQUENCE_MASK = 4095L;     // 12-bit

    private final long machineId;                        // 构造器注入 @Value("${leo.id.machine-id:0}")

    @PostConstruct
    void registerInstance() { instance = this; }

    public static SnowflakeIdGenerator getInstance() { return instance; }

    public synchronized long nextId() {                  // ← synchronized
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("系统时钟回拨，无法生成雪花ID");
            // ← 仅抛异常，无等待/重试/预留 sequence
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;   // 4096 IDs/ms 上限
            if (sequence == 0L) {
                timestamp = waitUntilNextMillis(timestamp); // ← 自旋等待
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << 22) | (machineId << 12) | sequence;
    }
}
```

**bit 分配**：

```
[41-bit timestamp delta] [10-bit machine-id] [12-bit sequence]
```

**`getInstance()` 静态调用现存清单**（4 处）：

| 文件 | 上下文 |
|---|---|
| `TradeItemMaterialSupport.java:91` | `piece.setId(SnowflakeIdGenerator.getInstance().nextId())` |
| `AbstractCrudService.java:56` | `idGenerator != null ? idGenerator : SnowflakeIdGenerator.getInstance()` |
| `BusinessCreateIdResolver.java:168` | 同上带回退 |
| `PurchaseOrderItemPieceWeightService.java:210` | `piece.setId(SnowflakeIdGenerator.getInstance().nextId())` |

**`application.yml` 默认值**：`leo.id.machine-id: ${LEO_MACHINE_ID:0}` → 多实例部署时默认 ID 冲突。

### 3.6.2 现存问题

| 问题 | 详情 |
|---|---|
| `synchronized` 瓶颈 | 单 JVM 最多 ~4M IDs/s（受 `System.currentTimeMillis()` 精度和 synchronized 开销限制），当前业务量够用但架构上不自洽 |
| 时钟回拨无容忍 | 仅抛 `IllegalStateException`，NTP 微调也应导致 crash |
| 静态 `getInstance()` | 4 处绕过 DI，可测试性差 |
| 默认 `machine-id=0` | 生产多实例零配置即冲突 |

### 3.6.3 目标方案选型

| 方案 | 适用性 | 结论 |
|---|---|---|
| **TSID** (`com.github.f4b6a3:tsid-creator`) | 应用生成、趋势递增、无机器号、128-bit（比 Snowflake 64-bit 碰撞概率更低） | **中期推荐**：零配置，无机器号冲突，迁移面等同于 UUID |
| PostgreSQL `BIGINT GENERATED ALWAYS AS IDENTITY` | 数据库强一致，简单 | **长期推荐**：新模块优先使用，老实体 FK 兼容需评估 |
| UUIDv7 | 应用生成、时间排序、128-bit | **可选**：空间开销比 TSID 大（36-char string vs 13-char TSID） |
| 继续 Snowflake 用成熟库 | 降低算法维护 | 过渡：`com.github.f4b6a3:tsid-creator` 本身也支持 Snowflake 兼容模式 |

### 3.6.4 迁移步骤

**短期（PR6）**：

1. 新增 `leo.id.strict-machine-id` 开关，生产环境 `machine-id=0` 时启动失败
2. 禁止新增 `getInstance()` 调用（lint 规则 + code review）
3. 4 处存量调用改为构造器注入
4. 编写 ADR：TSID vs DB sequence vs UUIDv7 选型

**长期**：

- 新模块（`finance.*`、`mcp.*`）优先使用 `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)` 或 TSID
- 老实体迁移需单独评估 FK 兼容性（所有以 Snowflake ID 为 FK 的表需同步迁移或添加映射表）

---

## 3.7 幂等

### 3.7.1 当前实现

**两套幂等机制**：

| 机制 | 触发方式 | 实现 |
|---|---|---|
| HTTP Header 幂等 | `Idempotency-Key` 请求头 | `HttpIdempotencyFilter` → `HttpIdempotencyService` |
| 方法注解幂等 | `@Idempotent` 注解 | `IdempotentAspect` → `IdempotentKeyService` |

**状态机**：

```
ACQUIRED            ← 首次请求，执行业务逻辑
DUPLICATE_PENDING   ← 重复请求，前一个请求还在执行
DUPLICATE_COMPLETED ← 重复请求，前一个请求已执行完成（直接返回缓存结果）
PARAMETER_MISMATCH  ← 相同 idempotency key 但请求体不同
```

**`IdempotentKeyService` key 生成**：基于请求体 SHA-256 + 用户 ID + URI 的复合 key。

### 3.7.2 判断

这是**业务协议**的一部分，不是纯技术轮子：

- Redis Lua 状态机表达了复杂的业务语义（`PARAMETER_MISMATCH` 异常）
- 可替代方案（Spring Integration Idempotent Receiver、Redisson）存在，但不能直接覆盖当前四态语义

### 3.7.3 目标方案

- **短期保留**
- 收敛两套幂等的使用边界：HTTP Header 幂等用于外部 API / webhook，方法注解幂等用于内部 service 防重
- 收敛两套幂等的 Redis key 前缀规范
- 中期评估 Redisson `RLock` / `RSemaphore` 是否可简化状态机

---

## 3.8 数据库备份

### 3.8.1 当前实现

**`DatabaseBackupService`** (`system/database/service/DatabaseBackupService.java`)：

- 应用进程内通过 `ProcessBuilder` 调 `pg_dump` / `psql`
- 密码通过环境变量 `PGPASSWORD` 注入（进程可见性风险）
- 备份超时、一致性（`--lock-wait-timeout`、`--no-owner`）、压缩（`-Z`）由应用配置
- `DatabaseBackupProperties` 下管理连接参数

**`ScheduledDatabaseBackupService`**：定时任务触发备份。

**`DatabaseExportTaskService`**：用户触发的数据导出（Excel/CSV），复用同一套连接配置。

### 3.8.2 问题

- **职责错位**：业务应用不应承担数据库运维
- **进程权限**：应用容器需安装 `pg_dump` 二进制 + 访问数据库端口 + 读取密码
- **备份一致性**：`pg_dump` 默认非 `--lock-wait-timeout` 且不保证 point-in-time
- **生产级工具缺失**：无 WAL 归档、PITR、增量备份、备份校验

### 3.8.3 目标方案

| 场景 | 方案 |
|---|---|
| 自管 PostgreSQL | pgBackRest（增量 + PITR + 并行） / WAL-G（云存储） |
| 云数据库 | 云厂商自动备份（RDS Automated Backup / Cloud SQL Export） + PITR |
| 应用内导出 | 保留为**开发/小规模部署辅助**，不作为生产主备份 |

### 3.8.4 验证

- 生产配置默认禁用 `leo.database.import.enabled=false`
- 开发环境 `DatabaseExportTaskService` 导出仍可用
- 运维文档 `docs/ops/database-backup.md` 交付（pgBackRest/WAL-G 配置示例 + 恢复流程）

---

# 4. 不迁移清单

| 模块 | 判断 | 理由 |
|---|---|---|
| JWT | ✅ 保留 | 使用 `jjwt` (io.jsonwebtoken)，不是手写 JWT |
| TOTP 算法 | ✅ 保留 | 使用 `dev.samstevens.totp`，不是手写 TOTP |
| 密码哈希 | ✅ 保留 | `Spring Security PasswordEncoder` (BCrypt/Argon2) |
| Excel 导入导出 | ✅ 保留 | Apache POI / Commons CSV，业务字段映射自定义合理 |
| PDF 水印和打印表单 | ✅ 保留 | 使用 iText，版式渲染是业务能力 |
| 操作日志 | ✅ 保留 | 审计语义强，Spring Interceptor/AOP 只是承载方式 |
| 分页参数 | ✅ 保留 | 有 sort 白名单 + 统一错误语义（`@PageableDefault` + `SortHandlerMethodArgumentResolver` 自定义），大于 Spring `Pageable` 默认能力 |

---

# 5. 实施顺序

## 5.0 PR 依赖 DAG

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
PR5a (密钥抽象, 只读不改, 可与第一阶段并行)
   │
PR5b (新格式双写灰度, 严格灰度节奏)
   │
PR5c (后台重加密)

PR7 (DB 备份外部化, 文档级, 独立)
```

## 5.1 PR 列表

### PR1：删除 S3 历史残留

**范围**：删除 6 个文件（3 生产 + 3 测试）

**验证**：`S3CompatibleAttachmentStorageTest` / `AttachmentStorageResolverTest` / 附件上传/下载/预签名直传

**风险**：低。`rg S3Signer\|S3RequestExecutor src/main` 确认生产代码无引用。

**回滚**：还原被删除文件。

---

### PR2：TraceId 迁移到 Micrometer Tracing

**范围**：按子文档 `2026-07-03-traceid-observability-upgrade-plan.md` 实施。

**pom.xml 新增**：
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

**application.yml 新增**：
```yaml
management:
  tracing:
    sampling:
      probability: ${LEO_TRACING_SAMPLING_PROBABILITY:1.0}
  otlp:
    tracing:
      export:
        enabled: ${LEO_OTLP_TRACING_EXPORT_ENABLED:false}
      endpoint: ${LEO_OTLP_TRACING_ENDPOINT:http://localhost:4318/v1/traces}
```

**TraceIdFilter 改造**：收敛为仅兼容 `X-Trace-Id` 写入（不再自行生成 UUID），核心链路 ID 由 Micrometer Tracer 提供。

**验证**：
- 错误响应 `ApiResponse.traceId` 与 `logback-spring.xml` `%X{traceId:-}` 一致
- Collector 不可用不阻塞启动
- W3C `traceparent` 出现在 outgoing HTTP 请求头中

**风险**：中。影响日志排查链路。

**回滚**：恢复旧 `TraceIdFilter` + logback pattern。

---

### PR3：缓存双轨制迁移

**范围**：按子文档 `2026-07-03-cache-dual-track-design.md` 实施。

**关键约束**：复用现有 `CacheConfig.RedisCacheManager`（static/hot 双 cache），不新增 CacheManager Bean。

**验证**：
- 空 list 不缓存（`unless = "#result?.size() == 0"`）
- 事务回滚不误删缓存
- TTL 命名空间正确（static 2h / hot 5min）
- 缓存命中率（迁移后 7 天）不低于迁移前

**风险**：中。影响 10+ 业务服务读路径。

**回滚**：恢复对应业务服务 `RedisJsonCacheSupport.getOrLoad(...)`。

---

### PR4：限流迁移 Bucket4j

**范围**：
- 新增 `bucket4j-redis` 依赖
- `TokenBucketService` 内部实现替换为 Bucket4j ProxyManager
- 新增 `RateLimitHeaderWriter` 收敛 `X-RateLimit-*`/`Retry-After` 写出
- 删除 `db/token_bucket.lua` + Resilience4j 依赖

**前置基线**（合入前必须建立）：
- 注入 Redis 连接异常/高延迟，记录当前 `TokenBucketService` 的 fail-open 行为
- PR4 合入后回放同一基线，确认一致

**验证**：
- 全局 IP 限流 / API Key 限流 / 用户维度限流
- `Retry-After` / `X-RateLimit-*` 响应头与现状一致
- Redis 不可用时 fail-open 行为与现状一致
- Bucket4j vs 现状 QPS 误差 < 5%

**风险**：中高。影响所有请求入口。

**回滚**：feature flag 一键切回旧 `TokenBucketService`。

---

### PR5a：密钥抽象 + legacy 适配器（只读不改）

**范围**：
- 新增 `SecretEncryptionService` 接口
- 新增 `LegacyTotpSecretEncryptionAdapter`（包装 `TotpSecretCryptor`）
- 新增 `LegacyOssSecretEncryptionAdapter`（包装 `OssSecretCryptor`）
- 新增 `LegacyAttachmentContentEncryptionAdapter`（包装 `AttachmentContentCryptor`）
- **不修改任何写入路径**

**验证**：现有密钥通过 legacy 适配器可正确解密；写入路径行为不变。

**风险**：低。纯新增，不改写入。

---

### PR5b：新写入切 Tink AEAD 格式（含双写灰度）

**范围**：
- 引入 `com.google.crypto.tink:tink` 依赖
- 新写入使用 Tink AEAD（AES-256-GCM, KeysetHandle）
- 读链路先尝试 Tink → 失败回退 legacy
- 灰度：新功能写入先用新格式 → 全部写入用新格式 → 稳定 7 天

**灰度阈值**：

| 阶段 | 比例 | 监控指标 | 回滚触发 |
|---|---|---|---|
| 灰度 1 | 新功能写入用新格式 | 解密失败率 = 0% | > 0.01% 回滚 |
| 灰度 2 | 全部写入用新格式 | 回退 legacy 命中率 < 5% | > 5% 暂停 |
| 灰度 3 | 稳定 7 天 | 解密失败率 = 0% | — |

**验证**：旧数据回退可读；新数据 Tink 加解密；错误密钥解密失败；KeysetHandle 轮换。

**风险**：高。安全敏感且影响 2FA 登录、OSS 凭据。

**回滚**：写链路切回旧格式（legacy adapter 保留，Tink 格式数据仍可解）。

---

### PR5c：后台批量重加密 + 清理 legacy

**范围**：
- 后台任务分批重加密旧数据（每批 100 条，可暂停）
- 完成并稳定 N 天后，清理 legacy decrypt 路径

**验证**：重加密前后都可解；解密失败率持续 0；进度可监控/暂停/恢复。

**回滚**：停止后台任务，已处理数据保持 Tink，未处理数据保持 legacy。

---

### PR6：ID 生成治理

**范围**：
- 禁止新增 `getInstance()` 调用
- 4 处存量静态调用改构造器注入
- 新增 `leo.id.strict-machine-id` 开关
- 编写 ADR：TSID vs DB sequence vs UUIDv7 选型

**验证**：
- `strict-machine-id=true` 且 `machine-id=0` → 启动失败
- 4 处 DI 改造后单测通过
- 多线程并发 0 冲突
- `getInstance()` 残留 = 0 处

---

### PR7：数据库备份职责外部化

**范围**：文档标注 + 禁用进口 + 运维文档。

**验证**：`leo.database.import.enabled=false` 时接口不可用；开发环境导出仍可用。

---

# 6. 依赖调整计划

| 依赖 | 当前状态 | 计划 |
|---|---|---|
| `resilience4j-spring-boot3` (2.3.0) | 生产代码 0 使用 | PR4 删除 |
| `resilience4j-ratelimiter` | 同上 | PR4 删除 |
| AWS SDK v2 S3 | 已使用 | 保留 |
| `bucket4j-redis` | 未引入 | PR4 引入 (8.10.1) |
| `spring-boot-starter-actuator` | 已引入 | PR2 已完成 |
| `micrometer-tracing-bridge-otel` | 已引入 | PR2 已完成 |
| `opentelemetry-exporter-otlp` | 已引入 | PR2 已完成 |
| `com.google.crypto.tink:tink` | 未引入 | PR5b 引入 (1.15+) |

---

# 7. 验收标准

## 7.1 项级验收

- [ ] S3 手写 SigV4 / HttpClient 残留删除
- [ ] traceId 不再由手写 filter 生成核心链路 ID
- [ ] 读缓存业务点迁移到 Spring Cache，Redis 并发原语保留
- [ ] 限流不再依赖自研 Redis Lua token bucket
- [ ] 未使用的 Resilience4j 依赖被删除
- [ ] 密钥加密有 Tink AEAD envelope + legacy 兼容读取
- [ ] 生产环境不能使用默认 `machine-id=0` 静默启动
- [ ] 应用内 DB 备份不再定义为生产主备份
- [ ] 每个 PR 有单测/集成测试覆盖，可独立回滚

## 7.2 量化阈值

| PR | 指标 | 阈值 | 采集方式 |
|---|---|---|---|
| PR4 | 限流 QPS 误差（Bucket4j vs 现状） | < 5% | wrk2/JMeter 压测对比 |
| PR4 | Redis 故障 fail-open 行为 | 与现状 100% 一致 | 故障注入基线回放 |
| PR5b | Tink 写入解密失败率 | = 0% | 生产监控 + 单测 |
| PR5b | legacy 回退命中率（灰度初期） | < 5% | 读链路埋点 |
| PR5c | 重加密后历史数据解密失败率 | = 0% | 全量抽样校验 |
| PR3 | 缓存命中率（迁移后 7 天） | 不低于迁移前 | Redis INFO stats |
| PR3 | 脏读 / 缓存不失效 | = 0 起 | 事务回滚与 evict 单测 |
| PR2 | 错误 body / X-Trace-Id / log 三方一致 | 100% | 一致性集成测试 |
| PR6 | `getInstance()` 残留 | = 0 处 | 静态扫描 |
| PR6 | 多线程并发 ID 唯一性 | 0 冲突 | 并发单测 |

## 7.3 本轮测试与覆盖率基线（2026-07-04）

| 项 | 结果 |
|---|---|
| 回归命令 | `mvn test` |
| Maven 结果 | 5584 tests, 0 failures, 0 errors, 0 skipped |
| JaCoCo 指令覆盖 | 100% (`missed=0`, `covered=106601`) |
| JaCoCo 分支覆盖 | 100% (`missed=0`, `covered=8224`) |
| JaCoCo 行覆盖 | 100% (`missed=0`, `covered=22303`) |
| JaCoCo 复杂度覆盖 | 100% (`missed=0`, `covered=8949`) |
| JaCoCo 方法覆盖 | 100% (`missed=0`, `covered=4783`) |
| JaCoCo 类覆盖 | 100% (`missed=0`, `covered=863`) |

覆盖率 XML 总计 counter 解析结果：`INSTRUCTION` / `BRANCH` / `LINE` / `COMPLEXITY` / `METHOD` / `CLASS` 均为 `missed=0`。

---

# 8. 风险登记

| ID | 风险 | 等级 | 缓解 |
|---|---|---|---|
| R1 | 限流迁移导致误拦截 | 高 | feature flag + 压测 + 回滚旧 TokenBucket |
| R2 | 密钥迁移导致旧 secret 无法解密 | 中 | PR5a/5b/5c 三步独立回滚 + 双写灰度 + 监控触发回滚 |
| R3 | ID 策略调整影响历史主键 | 高 | 短期只治理 DI/配置，不换算法 |
| R4 | 缓存迁移导致脏读/缓存不失效 | 中 | 事务回滚与 evict 单测 |
| R5 | TraceId 迁移排障链路断裂 | 中 | 三方一致性测试 |
| R6 | S3 残留误删 | 低 | `rg` 确认生产代码无引用 + 附件测试 |

---

# 9. 推荐执行节奏

**第一阶段**（低风险降噪）：

1. PR1 S3 残留删除（1d）
2. PR2 TraceId 迁移（2d）
3. PR3 缓存迁移（3d）

**第二阶段**（运行时行为）：

4. PR4 限流迁移（3d）
5. PR6 ID 生成治理（2d）

**第三阶段**（安全与运维）：

6. PR5a 密钥抽象 + legacy（2d，可与第一阶段并行）
7. PR5b Tink 新格式双写灰度（3d + 7d 观察期）
8. PR5c 后台重加密（1d 代码 + 后台运行 1-2 周）
9. PR7 DB 备份外部化（1d 文档 + 配置）
