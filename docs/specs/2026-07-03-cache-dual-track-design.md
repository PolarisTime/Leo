---
title: Leo 后端缓存双轨制演进设计
date: 2026-07-03
status: draft
owner: PolarisTime / 浮浮酱
scope: leo 后端 (Spring Boot)
---

# 1. 背景与目标

## 1.1 背景事实（探测得出）

leo 后端当前存在两套缓存机制并存：

1. **手写封装 `RedisJsonCacheSupport.getOrLoad(key, ttl, type, loader)`** — Spring `@Cacheable` 的语义等价物，全仓 **14 处**业务点在用（端到端：读 miss → loader 查库 → 写回 Redis），并附带：
   - `AfterCommitExecutor` **事务提交后才写缓存**（防脏数据进缓存）
   - `deleteAfterCommit()` **事务提交后才删缓存**（防"先删→事务回滚→缓存永久为空"）
   - 健康检查接口 `RedisCacheHealthCheck`（被 6 个业务 Service 实现并统一上报）
   - 空值兜底：`if (options.isEmpty()) { reload(); writeCache(); }` 的"空 list 视作未命中重查"
   - 动态 key（菜单按 `activeMenuCacheSignature()` 哈希后缀）
2. **直接 `StringRedisTemplate`** — 用于并发原语（CAS / Hash / Set 索引 / 计数器 / 令牌桶 / 幂等 / 会话快照），全仓 **10+ 处**：
   - `PermissionCache`（Hash + Set 索引 + scan 失效）
   - `AuthenticatedUserCacheService`（Hash + scan + 索引集）
   - `PreallocatedBusinessNoService`（`setIfAbsent` CAS + 用户归属）
   - `AccessTokenBlacklistService` / `IdempotentKeyService` / `HttpIdempotencyService`（CAS）
   - `TokenBucketService` / `RateLimitAspect`（令牌桶 / 限流计数）
   - `LoginAttemptService` / `CaptchaService`（计数 + TTL）
   - `SessionActivityService` / `ApiKeyUsageService`（心跳 / 计数）
   - `InitialSetupService`（TOTP 临时密钥 CAS）

## 1.2 问题

`CacheConfig` 配置了 `CACHE_STATIC`(7d 默认) 与 `CACHE_HOT`(10min) 两个 Redis 命名空间 + `Jackson2JsonRedisSerializer` + `withTtlJitter` 防雪崩 + `disableCachingNullValues`，但**业务代码完全没用 `@Cacheable`/`@CacheEvict`**——这套精巧的 CacheManager 配置实际无人消费。新人读代码时也难以一眼识别"这里是缓存"。

## 1.3 目标（业内双轨制）

> 把"读时缓存类"业务点迁 `@Cacheable`/`@CacheEvict`（声明式、一眼可识别）；"并发原语类"保留手写 `RedisJsonCacheSupport` / `StringRedisTemplate`（精确表达 CAS/Hash/计数语义）。两条线共存于同一 `RedisCacheManager` 体系。`RedisJsonCacheSupport` 不删除，继续作为并发原语的基础设施。

参考 Spring 官方缓存抽象与 Baeldung / Vlad Mihalcea 对缓存分层的表述：能用注解的用注解，超出注解表达能力的不要硬塞注解。

## 1.4 非目标

- 不重构并发原语类缓存（CAS/Hash/计数器/限流）——它们语义正确，硬迁会引竞态 bug
- 不引入 Caffeine 二级缓存（YAGNI，当前 Redis 已够用）
- 不把 `RedisJsonCacheSupport` 删掉
- 不动任何鉴权/限流/幂等逻辑

---

# 2. 命名空间设计

## 2.1 修订后命名空间

| CacheName 常量 | TTL（带 jitter） | 用途 | 对应业务 |
|---|---|---|---|
| `CACHE_OPTIONS` (**新增**) | 30min | 高频读、低频写的业务下拉选项 | Customer/Supplier/Carrier/Department/Warehouse 选项 |
| `CACHE_HOT`(已存在) | 10min | 中频读 | TradeMaterial、UserAccountValidation、CompanySetting |
| `CACHE_DASHBOARD` (**新增**) | 2min | 用户级仪表盘汇总 | DashboardSummary |
| `CACHE_SWITCH` (**新增**) | 5min | 系统开关 / 通用设置 | SystemSwitch、GeneralSettingQuery |
| `CACHE_MENU` (**新增**) | 30min | 菜单可见性 | (留手写，不迁)仅供 schema 对齐 |

## 2.2 决策记录

- **CACHE_STATIC 默认 TTL 由 7d 改为 30min**（贴合业务选项真实 TTL，避免"7 天不变化、evict 不及时读到过期数据"风险）
- 为业务语义清晰，**新增 `CACHE_OPTIONS` 而非复用 `CACHE_STATIC`**（30min 与原 7d 含义截然不同）
- TTL jitter 沿用现有 `RedisTuningProperties.withTtlJitter`，默认 10%，防雪崩

---

# 3. 迁移清单

## 3.1 迁 `@Cacheable` / `@CacheEvict`（12 个读点）

> **`unless` 取用规则（实施必读）**：下表第三列的 `@Cacheable 配置` 一律**完整写出 `unless=...` 表达式**，实施时**逐字复制对应行的写法**，禁止凭印象省略或自行推断。三类写法的语义边界见 §4.1 顶部表格：
> - 选项类（返回 `List`，6 处，行 #6-#11）：`unless="#result == null || #result.isEmpty()"`
> - `Long` 哨兵类（`loadLoginNameOwnerId`，1 处，行 #12）：`unless="#result == null"`（保留 `-1L` 入缓存，见 §4.1.2）
> - 对象类（`DashboardSummary`，1 处，行 #13）：`unless="#result == null"`
> - 单 key 纯读类（CompanySetting/SystemSwitch/GeneralSettingQuery，5 处，行 #1-#5）：无 unless（返回值不会为 null/空，无需兜底）
>
> 表内所有 `|` 已转义为 `\|\|`（Markdown 表格需要），复制到代码时还原为 `||`。

| # | 模块.方法 | @Cacheable 配置（逐字复制） | 写失效点（@CacheEvict） |
|---|---|---|---|
| 1 | `CompanySettingService.current()` | `value=CACHE_HOT, key="'leo:company:current'"` | `saveCurrent()` 单方法 evict |
| 2 | `CompanySettingService.resolveCurrentTaxRate()` | `value=CACHE_HOT, key="'leo:company:tax-rate'"` | 同上 |
| 3 | `SystemSwitchService.loadKnownSwitches()` | `value=CACHE_SWITCH, key="'leo:system:switches'"` | 写开关处（已有 `evict*` 方法） |
| 4 | `GeneralSettingQueryService.publicDisplaySwitches()` | `value=CACHE_SWITCH, key="'leo:system:public-display-switches'"` | `evictPublicDisplaySwitchesCache()` |
| 5 | `GeneralSettingQueryService.publicClientSettings()` | `value=CACHE_SWITCH, key="'leo:system:public-client-settings'"` | `evictPublicClientSettingsCache()` |
| 6 | `CustomerService.listActiveOptions()` | `value=CACHE_OPTIONS, key="'leo:customer:all'", unless="#result == null \|\| #result.isEmpty()"` | `saveEntity()` 已调 `evictCache()` → 改为注解 |
| 7 | `SupplierService.listActiveOptions()` | `value=CACHE_OPTIONS, key="'leo:supplier:all'", unless="#result == null \|\| #result.isEmpty()"` | `evictCache()` 改为注解 |
| 8 | `CarrierService.loadCachedResponses()` | `value=CACHE_OPTIONS, key="'leo:carrier:all'", unless="#result == null \|\| #result.isEmpty()"` | `evictCache()` 改为注解 |
| 9 | `DepartmentService.options()` | `value=CACHE_OPTIONS, key="'leo:department:options'", unless="#result == null \|\| #result.isEmpty()"` | `evictOptionsCache()` 改为注解 |
| 10 | `WarehouseSelectionSupport.loadActiveWarehouseNames()` | `value=CACHE_OPTIONS, key="'leo:warehouse:all'", unless="#result == null \|\| #result.isEmpty()"` | `evictCache()` 改为注解 |
| 11 | `TradeItemMaterialSupport.loadActiveMaterialsByCode()` | `value=CACHE_HOT, key="'leo:material:all'", unless="#result == null \|\| #result.isEmpty()"` | `evictCache()` 改为注解 |
| 12 | `UserAccountValidationService.loadLoginNameOwnerId(loginName)` | `value=CACHE_HOT, key="'auth:user:login-name:owner:' + #loginName", unless="#result == null"`（**保留 -1L 哨兵入缓存**，见 §4.1.2） | `UserAccountCacheService.evictLoginNameCache()` 联动改注解 |
| 13 | `DashboardSummaryService.getSummary(userId)` | `value=CACHE_DASHBOARD, key="'leo:dashboard:' + #userId", unless="#result == null"` | 保留手写 evict（事件总线驱动，见 §4.4） |

## 3.2 不迁（保留手写）

| 模块 | 理由 |
|---|---|
| `MenuVisibilityService.loadActiveMenus()` | 动态 key 含 `activeMenuCacheSignature()` 哈希后缀 + 失效用 `cache.evictMetadata(MENU_CACHE_KEY_PREFIX)` **按 pattern scan**（非精确 key），`@CacheEvict` 无法表达 |
| `PermissionCache` / `AuthenticatedUserCacheService` / `PreallocatedBusinessNoService` / `AccessTokenBlacklistService` / `IdempotentKeyService` / `TokenBucketService` / `RateLimitAspect` / `LoginAttemptService` / `CaptchaService` / `SessionActivityService` / `ApiKeyUsageService` / `InitialSetupService` | CAS / Hash / Set / 计数器 / 令牌桶 / 幂等 / 会话原语，超出 `@Cacheable` 表达力 |
| `RedisJsonCacheSupport` 自身 | 保留作为上述手写场景的封装基础设施 |

## 3.3 文件变更范围预估

- `CacheConfig.java` — 新增 4 个命名空间 + TTL 配置 + `unless` 防 null 工具
- `RedisTuningProperties` — 新增 OPTIONS/DASHBOARD/SWITCH TTL 属性
- 12 个业务 Service — 加注解、删手写 `getOrLoad`/`writeXxxCache` 兜底
- `UserAccountCacheService.evictLoginNameCache()` — 改用注解失效
- 6 个 Service 的 `RedisCacheHealthCheck` 实现 — 适配注解式缓存探测
- 测试 — 每处加回归单测（空值/回滚/evict 时序）

---

# 4. 关键补偿方案

## 4.1 空值兜底（决策：`unless` 为主 + 方法体删除 reload）

**决策**：空集合（List 为空）与 `null` 都不进缓存；`-1L` 哨兵值照常进缓存。三者在 unless 表达式中的写法见下表，不靠记忆、统一从 §3 表内逐行复制。

| 返回类型 | unless 表达式 | 进缓存 | 不进缓存 | 查库行为 |
|---|---|---|---|---|
| `List<T>`（选项类 6 处） | `unless="#result == null \|\| #result.isEmpty()"` | 非空 List | `null` 或空 List | miss → loader 查库（每次空都重查，合法） |
| `Long`（`loadLoginNameOwnerId`） | `unless="#result == null"` | `0`、`-1L` 及任意有效 ownerId | 仅 `null` | `-1L` 命中后**不再查库**（见下方 -1L 决策） |
| 对象（`DashboardSummary`） | `unless="#result == null"` | 非空对象 | `null`（仪表盘无数据） | miss → 重新汇总 |

### 4.1.1 list 空值兜底（决策：unless 覆盖 + 删方法体 reload）

- 选项类读点加 `unless="#result == null || #result.isEmpty()"` —— **空结果不进缓存**，下次读会重新查库
- 方法体内原有的 `if (options.isEmpty()) { reload(); writeCache(); }` 兜底逻辑**删除**（unless 已能等价覆盖）

### 4.1.2 -1L 哨兵决策（决策：照常入缓存，命中后不再查库 — 闭口，非 Pending）

**决策结论**：`UserAccountValidationService.loadLoginNameOwnerId` 返回的 `Long`，其 `LOGIN_NAME_NOT_FOUND = -1L` 哨兵值**照常进缓存**，区间 `unless="#result == null"`（仅防 `null`，不防 `-1L`）。

**决策理由（对齐现状，零行为变化）**：当前手写 `getOrLoad(...)` 同样会缓存 `-1L`，下次同名登录名命中直接返回 `-1L` 不再查库。迁移若改成"-1L 不缓存"，会导致每个不存在的登录名每次登录都打一次库，违背缓存初衷。故迁移后保持现状语义：**-1L 一旦查出即缓存，后续命中不再查库**，直到 evict 失效（用户被创建/关联后 `evictLoginNameCache()` 触发）。

**验证归属**：
- 此决策的回归验证由 **R6** 单独承担（命中 `-1L` 与现状一致直接返回、不再查库）
- **R1** 只负责"空 list/空集合"那条线（防 `null`/空 List），不管 `-1L` —— R1 与 R6 是**分工**关系，二者对象不同：R1 管 `List` 空集合，R6 管 `Long` 哨兵值

**与 §6 R1 的关系澄清**：R1 表述"`unless` 防 null/空 list"是 R1 自己的职责范围，并非"-1L 取舍未决"，-1L 取舍由 §4.1.2 闭口决策 + R6 验证共同闭合。如后续读到 R1 觉得"似乎漏了 -1L"，请回看本节。

## 4.2 写缓存时序（决策：A 方案，读方法无需_tabgun）

- 所有 12 个迁移目标**全部是 `@Transactional(readOnly = true)` 读方法**——只读事务不会回滚，`@Cacheable` 默认的 "方法返回后 put" 时序不会产生脏数据
- **不启用 `AfterCommitExecutor` 延迟写**——读方法无副作用事务，方法返回后立即写缓存安全
- 与手写 `RedisJsonCacheSupport`（走 afterCommit）共存：注解路径 put 立即生效，手写路径仍 afterCommit。两条路径语义自洽，互不影响

## 4.3 evict 时序与 AOP order（决策：默认 order + 回滚单测）

- 所有 `@CacheEvict` 默认 `beforeInvocation = false`（成功后 evict）
- 不自定义 CacheInterceptor / 不动 AfterCommitExecutor —— 依赖 Spring Cache AOP 与 Transaction AOP 的默认 order 协作
- **必须补回归单测**："写 Service 抛异常导致事务回滚时，缓存不应被删"——验证默认 order 协作正确性

## 4.4 Dashboard 事件总线 evict（决策：注解读 + 手写 evict）

- `DashboardSummaryService.getSummary(userId)` 读路径用 `@Cacheable(value=CACHE_DASHBOARD, key="'leo:dashboard:' + #userId")`
- evict 路径**保留手写**：原 `evictCache(userId)` / `evictAllCache()` 调用点不动；改为内部用 `redisTemplate.delete("leo:dashboard:" + userId)` / `redisTemplate.scan("leo:dashboard:*") + delete`
- `DashboardCacheEvictListener` 事件总线**保留**，跨模块事件驱动的 evict 不打断
- 否决方案"用 `Caching`/`@CacheEvict(allEntries=true)`"：allEntries 全清对按用户隔离的仪表盘过激，且事件总线场景下注解 evict 与事件来源脱节

---

# 5. 命名约定与 key 设计

| 业务类型 | key 模板 | 示例 |
|---|---|---|
| 单 key 全局 | `'leo:<domain>:<scope>'` | `'leo:company:current'` |
| 按参数 | `'leo:<domain>:' + #<argName>` | `'auth:user:login-name:owner:' + #loginName` |
| 列表 / 选项 | `'leo:<domain>:all'` | `'leo:customer:all'` |

**约束**：
- key 字面量与原手写常量保持一致（如 `CUSTOMER_CACHE_KEY = "leo:customer:all"` → 注解 key `'leo:customer:all'`），保证迁移期 Redis 中已有键值兼容，无停机切换
- TTL 由 CacheManager 命名空间决定，注解中**不指定 TTL**（统一发到 `RedisTuningProperties`）
- 非 `String` key 一律走 SpEL 表达式（避免对象 toString 漂移）

---

# 6. 风险与回归测试

| ID | 风险 | 缓解 | 验证方式 |
|---|---|---|---|
| `R1` | 空值兜底丢失 | `unless` 防 null/空 list | 单测：`loadActiveOptions` 返回空时不进缓存，下次调用再查库 |
| `R2` | 写事务回滚却删缓存 | 默认 order 依赖 + 回滚单测 | 单测：写 Service 抛 RuntimeException，验证缓存仍存在 |
| `R3` | Jackson 序列化兼容 | CacheConfig 与手写均用相同的 `Jackson2JsonRedisSerializer` | 集成测试：迁移期 Redis 中已有手写键值，注解读回兼容 |
| `R4` | 健康检查断链 | `RedisCacheHealthCheck` 实现改为 `CacheManager.getCache(name)` 探测 | 单测：注解迁移后 healthCheck 仍能上报 |
| `R5` | 命名空间 TTL 配错 | 命名空间常量集中 + TTL 属性化 | 单测：每个 CacheName 的 TTL = 配置预期 |
| `R6` | 哨兵值 -1L 被错误防掉 | `unless` 仅防 `null`（`#result == null`），不防 `-1L` | 单测：命中 `-1L` 时与现状一致直接返回，不再查库 |
| `R7` | Dashboard 事件 evict 与注解共存 | evict 手写按精确 key，注解只读 | 集成测试：角色变更事件 → 对应用户仪表盘缓存被删 |

---

# 7. 实施顺序（4 个 PR）

| PR | 范围 | 文件数 | 风险 | 可独立回滚 |
|---|---|---|---|---|
| **PR1** 基础设施 | `CacheConfig` 新增 4 命名空间 + `RedisTuningProperties` 新增 TTL + `unless` 工具 | 2-3 | 低（不碰业务） | ✅ |
| **PR2** 低风险样板 | CompanySetting + SystemSwitch + GeneralSettingQuery（3 处，单 key、纯读、无空值兜底）| 4-5 | 低 | ✅ |
| **PR3** 选项系 | Customer/Supplier/Carrier/Department/Warehouse/TradeMaterial（6 处含 unless） | 6-7 | 中（含空值兜底补偿） | ✅ |
| **PR4** 复杂 key | MenuVisibility（保留手写，仅复用 TTL 类）+ UserAccountValidation + Dashboard（注解读+手写 evict） | 3-4 | 中（含 evict 联动改） | ✅ |

每个 PR 必须包含完整回归测试，PR1-4 顺序依赖。任一 PR 失败可独立回滚，不影响既有手写缓存正常工作。

---

# 8. 验收标准

- [ ] 全仓 `@Cacheable`/`@CacheEvict` 数量 = 13（12 读 + 1 MenuVisibility 留手写）
- [ ] `RedisJsonCacheSupport.getOrLoad()` 调用点数量从 14 降到 1（Dashboard，仍用 afterCommit 写场景保留）
- [ ] CacheConfig 注册命名空间覆盖所有迁移业务的 TTL
- [ ] 6 个 `RedisCacheHealthCheck` 实现迁移后仍正常上报
- [ ] 全部单元测试通过（含 R1-R7 回归测试）
- [ ] 并发原语（PermissionCache/Preallocated/Idempotent/RateLimit 等）**未受任何影响**，对应测试全绿
- [ ] 迁移期 Redis 既有 key 兼容（不停机切换）
