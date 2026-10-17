# 后端安全与数据一致性审计（2026-10-18）

## 基本信息

| 项目 | 内容 |
| --- | --- |
| 审计日期 | 2026-10-18 |
| 审计基线 | `0ba7f12c`（`chore(release): 13.2.1`），`main` 与 `origin/main` 同步 |
| 审计范围 | `leo/` 后端：`security` 模块 54 个 Java 文件、`auth`、`attachment`、`system/securitykey`、`common/concurrency`、`common/ratelimit`、全模块 controller / service 的授权与事务边界 |
| 技术栈 | Java 21、Spring Boot 3.5.16、Spring Security、jjwt 0.13.0、Spring Data JPA、PostgreSQL、Redis |
| 审计方式 | **只读静态审计**：源码阅读、调用链核对、Flyway 迁移核对、Git 历史追溯、公开标准对照。**未修改任何业务代码/配置，未对任何环境发起攻击性测试或写库** |
| 历史基线 | [`2026-07-10-backend-code-review.md`](../2026-07-10-backend-code-review.md)、[`security-trust-model.md`](../security-trust-model.md) |

本文所有结论均附 `文件:行号`。凡标注「(历史 XX 已修复 / 部分修复)」的条目，均已在当前基线上复核代码，不再重复报告已闭环的部分。

---

## 0. 审计口径

### 0.1 严重度定义

| 等级 | 定义 | 处置时限建议 |
| --- | --- | --- |
| **P0** | 可直接利用的认证/授权绕过，或可导致资金、库存、账簿等财务数据静默错误的缺陷 | 立即（阻断发布） |
| **P1** | 在明确输入、并发或部署条件下可造成越权、凭据失效、数据不一致，或多实例语义错误 | 最近一个修复批次 |
| **P2** | 需要特定组合条件才触发的一致性问题、可观测性缺口、纵深防御缺失 | 排期修复 |
| **P3** | 加固建议、规范一致性、可维护性 | 择机 |

### 0.2 判定边界（避免误报）

本审计显式遵守以下边界，避免把设计取舍当成缺陷：

1. **单账号/多账号模型**：`docs/security-trust-model.md:6-14` 声明系统当前为单账号模型，但 V103 的单活跃用户唯一索引已在 `V139__rbac0_user_role_permission.sql:17` 被 `DROP INDEX`，`V140` 进一步放开登录名唯一性为「未删除账号唯一」。**当前实际已是多用户 RBAC0 模型**，`security-trust-model.md` 的第 1 节与代码现状不一致——这一点本身登记为 P2 文档缺陷，而不是把「登录即管理员」当成当前事实来推导越权结论。
2. **数据层（行级）权限已整体移除**：`569258b9 refactor(security)!: 移除通用数据范围权限模型` 删除了 `DataScopeContext` / `DataScopeStrategy` / `DepartmentScopeResolver` / `CreatedByScopeStrategy`，`V84__remove_data_scope_columns.sql:1-5` 删除 `sys_role.data_scope`、`sys_user.data_scope`。因此**不能**把「列表页未按 created_by 过滤」一律报成缺陷；只有那些**曾经存在记录级校验、或仍保留了所有权字段与文案承诺、却在某条路径上缺失校验**的地方才算缺陷（见 §2.4）。
3. **不报已闭环项**：`event_publication` 可靠事件（`V79__add_reliable_business_operation_events.sql:22-31`，`application.yml:117-121` 的 `completion-mode: delete` + `republish-outstanding-events-on-restart: true`，消费者 `INSERT ... ON CONFLICT (event_id) DO NOTHING`）已正确落地，本文只引用不报缺陷。
4. **不把「默认关闭」当「不存在」**：限流默认关闭（`application.yml:626`）属于运维开关，本文按「开启后是否安全」判定，而不是按「默认不开」直接定级。

### 0.3 已确认的正面基线

以下项经逐行核对确认**当前实现正确**，列出以避免后续审计重复排查：

| 项 | 证据 | 说明 |
| --- | --- | --- |
| 雪花 ID 全局序列化为字符串 | `common/config/JacksonConfig.java:44-45` 对 `Long.class` 与 `Long.TYPE` 注册 `ToStringSerializer.instance` | 另有 `LongToStringModelConverter` 保持 OpenAPI 侧一致；`common/json/` 下 `NumericLongSerializer`（仅用于 `fileSize` 这类非 ID 数值）、`SnowflakeIdStringDeserializer`、`ScaledBigDecimalSerializer` |
| 实体未滥用 Lombok `@Data` | 全仓库 `@Entity` + `@Data` 组合 **0 处**（实体统一 `@Getter/@Setter`） | 避免 `toString()` 泄露凭据、`equals/hashCode` 破坏 JPA 语义 |
| 无 `printStackTrace` | 全仓库 **0 处** | 异常统一走 `GlobalExceptionHandler` + 结构化日志 |
| 无 TODO/FIXME/HACK 遗留 | 全仓库 **0 处** | — |
| 密码哈希强度 | `common/config/SecurityConfig.java:180` `new BCryptPasswordEncoder(12)` | strength=12，符合 OWASP 建议的 ≥10；全仓库无 MD5/SHA1 口令摘要 |
| Refresh Token 存储为摘要 | `auth/service/SessionManagementService.java:80`、`:129`、`:206-213` | 仅存 SHA-256 `tokenHash`，DB 泄露不可直接重放；随机源 `SecureRandom` 64 字节（`:196-200`） |
| 刷新令牌 Cookie 属性 | `auth/web/support/AuthTokenCookieSupport.java:24-33`；`application.yml:561-569` | `HttpOnly=true`、`Secure`（prod 默认 true）、`SameSite=Strict`（prod）、`path=/api/v2.0/auth` |
| Refresh Token 双来源一致性 | `AuthTokenCookieSupport.java:46-55` | Cookie 与 body 同时提供且不一致时抛 403，防 Cookie 注入/混淆 |
| 时间比较常量 | `common/support/PrecisionConstants.java:13-22` | 重量 8 位、金额 2 位、`HALF_UP` 集中定义，非魔法值 |
| JWT 密钥非硬编码 | `application.yml:606` `secret: ${LEO_JWT_SECRET:}`；`system/securitykey/service/SecurityKeyService.java:88-114` | 配置文件**无默认密钥字面量**；库内无可用密钥且环境变量缺失时**启动直接失败**（`IllegalStateException`），fail-closed |
| JWT 密钥最小长度校验 | `SecurityKeyService.java:103-109` | 兜底密钥长度 <32 直接拒绝 |
| SQL 注入面极小 | `common/moduleexport/service/ModuleExportService.java:152-154`（`?` 占位符）、`finance/overview/repository/FinanceOverviewQueryRepository.java:425-429`（排序白名单 `SORT_COLUMNS.getOrDefault`）、`common/support/MasterDataReferenceGuard.java:128-131`（`validateIdentifier`） | 拼接的 SQL 片段仅来自代码内白名单常量或经过标识符校验的表名，无用户输入直拼 |

---

## 1. 风险热力图

> 行 = 领域，列 = 严重度，单元格内为编号（详见后文各节）。`—` 表示无该级别发现。

| 领域 | P0 | P1 | P2 | P3 |
| --- | --- | --- | --- | --- |
| 认证与会话（JWT / credential_version / 黑名单） | — | C-1 | C-2、C-3 | C-4 |
| 授权与越权（@RequirePermission / IDOR） | **S-1** | S-2、S-3 | S-4、S-5、S-6、S-8 | S-7 |
| 附件与加密（上传 / S3 / tink） | — | A-1 | A-2、A-3 | A-4 |
| 限流与资源耗尽 | — | R-1 | R-2、R-3 | R-4 |
| 事务边界与远程 IO | — | T-1 | — | T-4、T-5 |
| 并发与锁（来源锁 / 乐观锁） | — | — | T-2、T-3 | — |
| 金额与精度 | — | — | M-1 | M-2、M-3 |
| 审计日志与可观测性 | — | — | L-1、L-2 | L-3 |
| 配置与部署基线 | — | — | D-1、D-2 | D-3 |

**统计**：P0 = 1，P1 = 6，P2 = 17，P3 = 10，合计 **34 条**（P0 1 + P1 6 + P2 17 + P3 10）。

---

## 2. 认证与授权

### 2.1 认证链路总览（现状核对）

过滤器顺序（`common/config/SecurityConfig.java:106-111`）：

```
CorsFilter → InitialSetupTokenFilter → JwtAuthenticationFilter → HttpIdempotencyFilter → RateLimitFilter → AuthorizationFilter
```

- 会话策略 `STATELESS`（`:74`），CSRF 已禁用（`:57`）——Bearer Token + `SameSite=Strict` Cookie 的组合下可接受，但需注意 §2.2 的 C-3。
- 路径规则（`:90-105`）：`PublicAccessRequestMatcher`（按 `@PublicAccess` 注解自动构建，`PublicAccessRequestMatcher.java:30-40`）→ `/error` → 健康页 → OpenAPI → `OPTIONS /**` → `anyRequest().authenticated()`。**不存在 `/** permitAll` 的过度放行**（历史 Critical-01 的修复已闭环）。
- 唯一 `@PreAuthorize` 1 处；授权主通道是自定义 `@RequirePermission`（248 处）+ `PermissionMethodSecurityConfig.java:106-116` 注册的 `AuthorizationManagerBeforeMethodInterceptor`。

**正面结论**：259 个端点中 144 个写端点全部有 `@RequirePermission`；未加注解的仅 13 个端点，全部属于 `@PublicAccess` 白名单或 `/account`（当前登录用户自身资源）。这一覆盖度经独立核算，本文不重复报告。

### 2.2 P1

#### C-1（P1）管理面接口在「登录即可用」与「权限码已判定」之间存在判据不一致的越权面：`RoleBasedAuthorityProvider` 对系统主体直接返回全部权限码，且该主体可由控制器在无认证主体时构造

**证据**

- `security/rbac/RoleBasedAuthorityProvider.java:51-54`：
  ```java
  Long userId = principal.id();
  if (userId == null || userId <= 0L) {
      return PermissionCodes.all();      // 系统主体 → 无条件全部权限
  }
  ```
- `security/support/SecurityPrincipal.java:15-17`：`system()` 固定返回 `id = 0L`。
- `market/quotation/web/V2QuoteSheetEditLockController.java:49`：
  ```java
  SecurityPrincipal current = principal == null ? SecurityPrincipal.system() : principal;
  return service.acquire(id, current.id(), current.username(), force);
  ```
  该控制器方法同时标注 `@RequirePermission(PermissionCodes.QUOTE_SHEETS_UPDATE)`（`:43`）。
- 同类「无主体时回落」写法：`V2QuoteSheetController.java:231`（`principal == null ? 0L : principal.id()`）、`V2QuoteSheetEditLockController.java:58、:67`、`purchase/order/service/PurchaseOrderForceCloseService.java:155-157`（`new OperatorDisplay(0L, "system")`）。

**攻击/失效场景**

1. 当前 `@RequirePermission` 在 `PermissionAuthorizationManager.java:37-40` 已拒绝未认证主体（`!current.isAuthenticated()` → deny），因此**现状不是可直接利用的匿名越权**。
2. 真正的风险在于判据不一致：`RoleBasedAuthorityProvider` 把 `id <= 0` 视为「内部调用，全部权限」，而控制器又在 principal 缺失时**主动构造**该主体。一旦未来出现以下任一变化，就会立刻变成可利用漏洞：
   - 引入 `@PublicAccess` 或 `permitAll` 到上述端点（例如为「编辑锁探活」开白名单）；
   - 新增一个不带 `@RequirePermission` 的写端点复用同一 `system()` 回落模式（`V2QuoteSheetEditLockController` 已经示范了这种写法）；
   - 服务层 `PermissionChecker.hasCurrent` 在匿名时返回 `false`（`PermissionChecker.java:39-46`），但 `StatusTransitionPermissionGuard.java:106-111` 在**无已认证主体时直接跳过校验**（`if (!hasAuthenticatedPrincipal()) return;`）——两者语义相反。
3. 叠加 `StatusTransitionPermissionGuard` 的跳过逻辑后，「系统主体」路径既绕过功能级权限、又绕过状态迁移动作权限（：audit/:unaudit/:confirm），只剩业务不变量。

**修复建议**

1. `RoleBasedAuthorityProvider` 的系统主体白名单改为**显式开关 + 调用来源断言**：仅在 `SecurityContext` 为空且当前线程标记为内部任务（如定时任务上下文）时返回全权限；HTTP 请求线程一律不返回 `PermissionCodes.all()`。
2. 删除控制器里的 `principal == null ? SecurityPrincipal.system() : principal`，改为 `principal == null → throw BusinessException(UNAUTHORIZED)`，与 `V2UserAccountController.java:68-73` 的 `currentUserId` 写法对齐。
3. `StatusTransitionPermissionGuard` 的「无主体跳过」改为「无主体拒绝」，内部调用改由显式内部主体（带专用权限码）表达。
4. 增加架构测试：断言 `web` 包下不出现 `SecurityPrincipal.system()`（当前 1 处，收敛到 1 个内部适配器）。

**代价**：中。需区分「HTTP 请求」与「内部任务」两类主体，触及 `RoleBasedAuthorityProvider`、4 处控制器回落、1 处 guard；有现成测试可扩展，回归面集中在开盘报价锁与强制结单。

**对应标准**：[OWASP API Security Top 10 API5:2023 Broken Function Level Authorization](https://api-security.owasp.org/editions/2023/en/0xa5-broken-function-level-authorization/)、[CWE-863 Incorrect Authorization](https://cwe.mitre.org/data/definitions/863.html)、[CWE-306 Missing Authentication for Critical Function](https://cwe.mitre.org/data/definitions/306.html)。

### 2.3 P2 / P3（认证侧）

#### C-2（P2）`AccessTokenBlacklistService` 双次 Redis 往返导致吊销判定存在 TOCTOU 窗口，且用户级黑名单语义在并发登录下会误杀新令牌

**证据**

- `security/jwt/JwtAuthenticationFilter.java:153-164`：
  ```java
  if (!blacklistService.isBlacklisted(userId)) return false;
  Date issuedAt = claims.getIssuedAt();
  long blacklistTime = blacklistService.getBlacklistTime(userId);   // 第二次 Redis 往返
  return issuedAt != null && issuedAt.getTime() < blacklistTime;
  ```
- `security/jwt/AccessTokenBlacklistService.java:34-46`：`isBlacklisted` 用 `hasKey`，`getBlacklistTime` 用 `get`，**两次独立读**；`blacklistUser`（`:25-29`）写入的是 `System.currentTimeMillis()`，TTL = access token 有效期。

**失效场景**

1. **TOCTOU**：请求 A 在 `hasKey` 返回 `false` 之后、业务处理完成之前，另一个事务调用 `blacklistUser` 完成吊销。A 仍会通过认证——这本身可接受（吊销不回溯）。但反过来：`hasKey` 返回 `true` 后 `get` 拿到 `null`（key 恰好在两次调用之间过期），`getBlacklistTime` 返回 `0`，于是 `issuedAt < 0` 为 false → **已吊销令牌被放行**。窗口极窄，但属于确定性代码路径缺陷，非理论推测。
2. **误杀新令牌**：`blacklistUser` 记录的是「拉黑时刻」，判定用的是 `issuedAt < blacklistTime`。`SessionManagementService.java:191` 在**账号被停用**时拉黑用户；若管理员随后立即重新启用并让用户重新登录，新令牌的 `issuedAt` 可能仍早于该黑名单时间戳（同一毫秒级或时钟偏差），导致新令牌被误判为已吊销，表现为「刚登录就 401」。

**修复建议**：改为单次 `GET` 原子读取（返回 `null` 即未拉黑，避免二次读）；或改用 Lua 脚本一次返回 `{blacklisted, timestamp}`。用户级黑名单的判定基准从「拉黑时刻」改为「`credential_version` + 会话级 sid 黑名单」，用户级黑名单只保留 TTL 兜底。

**代价**：低。仅改 `AccessTokenBlacklistService` 两个方法，`JwtAuthenticationFilter` 调用点合并；有 `JwtAuthenticationFilter` 回归测试可扩展。

**对应标准**：[CWE-367 TOCTOU](https://cwe.mitre.org/data/definitions/367.html)、[OWASP API Security API2:2023 Broken Authentication](https://api-security.owasp.org/editions/2023/en/0xa2-broken-authentication/)。

#### C-3（P2）CSRF 关闭 + Refresh Cookie 随请求自动携带：`/auth/refresh` 与 `/auth/logout` 构成跨站触发面（当前由 `SameSite=Strict` 单点兜底）

**证据**

- `common/config/SecurityConfig.java:57` `.csrf(AbstractHttpConfigurer::disable)`。
- `auth/web/V2AuthController.java:25` 整个控制器 `@PublicAccess`，`:45-53` `/refresh`、`:57-62` `/logout` 均无 `@RequirePermission`。
- `auth/web/support/AuthTokenCookieSupport.java:57-68`：`resolveCookieRefreshToken` 直接读 Cookie，**不校验 `Origin`/`Referer`**。
- `application.yml:567-569`：`secure` 默认 `true`，`same-site` 默认 `Strict`；但 `.env.example:38-39` 把 `LEO_AUTH_REFRESH_COOKIE_SAME_SITE` 示例值写成 `Lax`——`Lax` 下**顶级跨站 GET/POST 导航会带上 Cookie**，若某环境照抄示例，`/auth/logout` 可被跨站表单触发。

**攻击/失效场景**：在 `SameSite=Lax`（或 `None`）配置下，攻击者页面自动提交 `POST /api/v2.0/auth/logout`，携带受害者 Refresh Cookie，造成强制下线（可用性攻击）。`/auth/refresh` 跨站触发会触发refresh token 轮换，配合 `previous_token_valid_until` 宽限期（`SessionManagementService.java:100-102`）可能造成客户端持旧令牌失效。注意：由于 **refresh 结果写在响应中而攻击者读不到**（CORS 白名单，`WebSecurityProperties.java:22-25`），不构成令牌窃取，故定级 P2 而非 P0。

**修复建议**：①`.env.example:39` 改为 `Strict`，与 prod 保持一致并加注释说明；②`/auth/refresh`、`/auth/logout` 增加 `Origin`/`Sec-Fetch-Site` 校验（仅允许同源或白名单 Origin），在 `AuthWebFlow.refresh/logout` 入口做；③为 Cookie 名加 `__Host-` 前缀（需 `Path=/`，或保留现路径并接受前缀不可用）。

**代价**：低-中。代码改动集中在 `AuthWebFlow` 与一处配置示例；风险是从反向代理来的 `Origin` 缺失场景需白名单放行（`X-Requested-With` 已在 `WebSecurityProperties.java:32` 允许）。

**对应标准**：[OWASP CSRF Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)、[Spring Security CSRF 文档](https://docs.spring.io/spring-security/reference/features/exploits/csrf.html)、[CWE-352](https://cwe.mitre.org/data/definitions/352.html)。

#### C-4（P3）`JwtProperties.secret` 无 `@NotBlank`，`accessExpirationMs` 无上限，配置错误只能在启动后由 `SecurityKeyStartupValidator` 间接触发

**证据**

- `security/jwt/JwtProperties.java:12-18`：`issuer` 有 `@NotBlank`，`secret` 无校验注解；`accessExpirationMs` 只有 `@Positive`，无上限。
- `security/config/SecurityKeyStartupValidator.java:17-21`：仅在 `ApplicationRunner` 阶段读取一次，异常使启动失败（fail-closed，属正面），但错误信息不指向具体配置项。

**失效场景**：把 `access-expiration-ms` 误配为 `604800000`（7 天）不会报错，直接让「凭据撤销链路」的兜底窗口从 10 分钟变成 7 天（黑名单 TTL、`validateKeyWindow` 允许窗口、降级快照语义都按此值计算——见 `AccessTokenBlacklistService.java:27`、`JwtTokenService.java:127`、`SecurityKeyService.java:83-85`）。

**修复建议**：`accessExpirationMs` 加 `@Max(3600_000)`（1 小时上限），`refreshExpirationMs` 加合理上限；`secret` 补 `@Size(min = 32)` 并在 `@Validated` 生效路径上（`JwtProperties` 已有 `@Validated`，见 `:8`）。

**代价**：极低。三行注解 + 配置测试。

---

### 2.4 越权（IDOR）抽查

**抽查方法**：逐个核对「详情 / 更新 / 删除」路径是否只按 `id` 直取而不校验数据范围，且**是否存在被删除的校验**或**代码内自相矛盾的所有权承诺**。抽样覆盖 14 个模块 26 个 `@GetMapping("/{id}")` 端点 + 8 个写路径。

**基线事实**：行级数据范围已整体移除（`569258b9`、`V84`），因此「按 id 直取、不做 created_by 过滤」在当前模型下**是设计选择而非缺陷**。真正的缺陷只出现在两类地方：①代码里**保留**了所有权字段与「只能操作本人」的文案，却在某条路径上不校验；②同一资源的不同入口校验强度不一致。

#### S-1（P0）销售订单：所有权校验只覆盖「编辑/删除/状态」，打印与表格导出路径完全绕过；`SalesOrderPrintExportService` 无任何 owner 断言，任一持 `print-exports:print` 的主体可打印任意他人订单

**证据**

- 所有权校验存在且明确：`sales/order/service/SalesOrderService.java:381-388`
  ```java
  private void assertOwnedByCurrentUser(SalesOrder order) {
      Long currentUserId = requireCurrentUserId();
      Long ownerUserId = order.getOwnerUserId() == null ? order.getCreatedBy() : order.getOwnerUserId();
      if (!Objects.equals(ownerUserId, currentUserId)) {
          throw new BusinessException(ErrorCode.FORBIDDEN, "只能编辑本人负责的销售订单");
      }
  }
  ```
  调用点：`SalesOrderService.java:243`（更新）、`:373`（validateUpdate）、`:517`（beforeDelete）、`:527`（beforeStatusUpdate）。`newEntity()` 在 `:533` 写入 `ownerUserId`。
- **打印路径完全没有该断言**：
  - `system/printtemplate/web/V2PrintExportController.java:65` `create(...)` 仅校验 `@RequirePermission(PermissionCodes.PRINT_EXPORTS_PRINT)`（`:64`），随后直接调用 `printOutputService.generateFromRecord(payload.templateId(), payload.moduleKey(), payload.recordId(), ...)`（`:71-78`）。
  - `system/printtemplate/service/PrintOutputService.java:26-32` 透传给 `printScriptService.generateFromRecord`，无授权代码。
  - `system/printtemplate/service/PrintScriptService.java:78-90` 只做「模板存在 / 模板 ACTIVE / moduleKey 与 billType 匹配 / 记录存在」四件事，`dataProvider.loadRecord(moduleKey, recordId)` 注释明确写着「一次查询完成存在性校验与数据加载」（`:89-90`）。
  - `system/printtemplate/service/PrintRecordDataProvider.java:35-42` 的 SQL 是 `WHERE id = ? AND deleted_flag = FALSE`（`:56-60`），**没有 created_by / owner_user_id 条件**。
- **打印预览同样绕过**：`system/printtemplate/web/V2PrintPreviewController.java:42-52` 的 `GET /print-previews/items` 接收 `recordIds` 列表（`:49`），仅校验 `PRINT_PREVIEWS_READ`，直接 `printScriptService.pagePrintItems(moduleKey, recordIds, query)`；`PrintScriptService.java:325-331` 全量取回后在**内存**分页（`:326-330`），无范围过滤。
- **历史对照 —— 这是一条完整的「能力被移除 → 重建时未恢复」证据链，而非单纯漏实现**：
  1. `docs/2026-07-10-backend-code-review.md:22` 记录「High-01 | **已修复** | 销售订单打印增加记录级数据范围校验」；`:466-471` 明确写出修复方式：调用 `ResourceRecordAccessGuard.assertCurrentUserCanAccess("sales-order", "print", order)`；`:472` 的验收标准要求「`SELF` 只能打印本人订单……越权固定返回 403」。
  2. `git log --all -- '*ResourceRecordAccessGuard.java'` 显示：该文件在 `49c71e59 refactor(security)!: 移除 RBAC 与 MCP 授权体系`（**2026-07-18 09:15:15**）中被删除，同批还删除了 `ModulePermissionGuard`、`MachineSubjectRbacGuard`、`WorkflowTransitionGuard`；**此后再无任何恢复提交**。
  3. 系统随后确实**重建了 RBAC**：`deb03350 feat(security): 可扩展端点权限模型（资源:动作细化）`（2026-09-14）、`73369334 feat(security): 落地 RBAC0 用户-角色-权限落库`（2026-09-15），对应迁移 `V139__rbac0_user_role_permission.sql` 与 `V140__multi_user_account_management.sql`。
  4. **但重建的是「功能级」端点权限模型，记录级/数据范围守卫没有随之恢复**：全仓库 `grep -rn "ResourceRecordAccessGuard|assertCurrentUserCanAccess" src/main/java` = **0 处**；`security/permission/` 目录 11 个文件中不存在任何记录级或数据范围守卫；`grep -rn "dataScope|DataScope" src/main/java` = **0 处**（`V84` 已删列、`569258b9` 已删类）。
  5. 因此 `:22` 的「已修复」与 `:466-471` 的修复说明**在当前基线上已不成立**，而旧文档仍标为已修复（该文档失真单独登记为 S-8）。
  
  这比「从未实现」更严重：后续评审按历史审计表的「已修复」状态跳过该项核查，形成**文档与实现双重失真**，使回归长期不可见。

**攻击/失效场景**

1. 主体 A（业务员）持有 `print-exports:print`（打印是普通业务动作，`docs/security-trust-model.md:100` 明确「打印执行保持普通登录用户可用」）。
2. A 从列表页或全局搜索拿到 B 负责的销售订单 ID（雪花 ID 虽为 19 位，但列表/搜索/单据流接口对其他登录用户均可见，`search/web/V2DocumentFlowController`、`search/web/V2GlobalSearchController.java:29-31` 都只校验 `global-search:read`）。
3. A 调用 `POST /api/v2.0/print-exports`，body 携带 `templateId`（模板列表全员可读，`V2PrintTemplateController.java:49-50` 只要 `print-templates:read`）+ `moduleKey=sales-order` + `recordId=<B 的订单>`。
4. 服务端返回完整 PDF/XLSX，含客户名称、项目、单价、金额、结算主体、银行账号等**全部商务敏感字段**。
5. 同样可用 `GET /print-previews/items?moduleKey=sales-order&recordIds=<批量 ID>` 一次性拉取**任意数量**他人订单明细（上限 200 个/次，`V2PrintPreviewController.java:49`）。

这是教科书式的 **BOLA（Broken Object Level Authorization）**：功能级权限（能否打印）通过，对象级权限（能否打印这条）缺失。

**修复建议**

1. **首选：把记录级校验下沉到数据加载的唯一出口**。在 `PrintRecordDataProvider.loadRecord` / `listPrintItems` 的 SQL 里追加数据范围谓词（`AND (owner_user_id = :currentUserId OR :hasAllScope)`），与「单一业务口径只在一处实现」的仓库规范一致（`AGENTS.md`《业务口径只在一处实现》）。这样 `print-exports` 与 `print-previews` 两个入口自动同时受保护，不需要两处各写一遍。
2. **兜底：恢复记录级 guard**，在 `PrintOutputService.generateFromRecord` 与 `PrintScriptService.pagePrintItems` 入口调用，按 `moduleKey` 解析出对应聚合的 owner 字段（`so_sales_order.owner_user_id`，`V107__add_operational_record_ownership.sql:4-5` 已建列）。
3. **打印预览必须下推分页**：`PrintScriptService.pagePrintItems` 当前把 `recordIds` 全量读进内存再 `subList`（`:325-331`），既是越权面也是内存放大面（配合 §R-1）。
4. **补回归测试**：按历史 High-01 的验收标准重建（`docs/2026-07-10-backend-code-review.md:466-472`）——两个入口、多主体、越权固定 403，并断言模板加载与文件生成未执行。

**代价**：中。数据范围下沉需要新增 `DataScope` 抽象（或最小化：一个 `RecordOwnershipResolver` 按 moduleKey 返回 owner 列与当前用户）；触及 `PrintRecordDataProvider`（1 个类）、`PrintScriptService`、两个 controller 的测试。若仅恢复 guard，代价低（2 个调用点 + 1 个 resolver）。

**对应标准**：[OWASP API Security API1:2023 Broken Object Level Authorization](https://api-security.owasp.org/editions/2023/en/0xa1-broken-object-level-authorization/)、[OWASP Insecure Direct Object Reference Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Insecure_Direct_Object_Reference_Prevention_Cheat_Sheet.html)、[CWE-639 Authorization Bypass Through User-Controlled Key](https://cwe.mitre.org/data/definitions/639.html)、[OWASP Top 10 A01:2021 Broken Access Control](https://owasp.org/Top10/A01_2021-Broken_Access_Control/)。

#### S-2（P1）附件下载的「记录存在性」校验只验存在、不验访问者是否有权访问该业务记录，跨模块 ID 枚举可读取他人附件

**证据**

- `attachment/service/AttachmentRecordAccessService.java:44-64`：
  ```java
  public void assertAttachmentAccessible(SecurityPrincipal principal, String moduleKey, Long attachmentId) {
      ...
      List<AttachmentBinding> bindings = attachmentBindingRepository.findByAttachmentIdAnd...;
      if (bindings.isEmpty()) { assertUnboundAttachmentOwner(principal, normalizedAttachmentId); return; }
      boolean boundToExistingRecord = bindings.stream()
              .filter(binding -> normalizedModuleKey.equals(...))
              .map(AttachmentBinding::getRecordId)
              .anyMatch(port::existsActive);          // ← 只判「记录存在」
      if (!boundToExistingRecord) throw NOT_FOUND;
  }
  ```
  一旦附件已绑定到任意活动业务记录，**只校验该记录存在**，不校验当前主体是否有权访问该记录。
- `attachment/api/RecordExistencePort.java:7` 的契约只有 `boolean existsActive(Long recordId)`，**签名里没有 principal**——从接口设计上就无法表达数据范围。
- 对照：未绑定附件反而**有**所有权校验（`AttachmentRecordAccessService.java:78-88`，`assertUnboundAttachmentOwner` 比对 `ownerUserId/createdBy`）。即「未绑定附件比已绑定附件更严格」，语义倒置。
- 调用点：`attachment/web/V2AttachmentController.java:71`（`access-url`）、`:112`（`content`）。两者只需 `ATTACHMENTS_READ`。

**攻击/失效场景**：A 知道任一活动业务记录 ID（例如自己的某张销售订单，或公开列表里的 ID）与目标附件 ID。构造 `GET /api/v2.0/attachments/{附件ID}/content?moduleKey=<A 能访问的模块>&accessKey=<accessKey>`——`moduleKey` 只要指向**任何一个**该附件被绑定且存在的记录即可（`filter` 按 `normalizedModuleKey` 匹配 binding，`anyMatch(port::existsActive)`）。由于 `accessKey` 由 `AttachmentResponseAssembler.java:46` 通过附件列表响应下发给「能看到该业务记录的人」，攻击者若曾是协作者（而后被移出该记录范围），其手中的 `accessKey` 仍然永久有效（`AttachmentMetadataService.java:40-47` 生成后**不轮换**），可继续读取附件内容。

**修复建议**：①在 `RecordExistencePort` 增加 `boolean isAccessibleBy(Long recordId, SecurityPrincipal principal)`（或新增 `RecordAccessPort`），已绑定路径改为调用它；②统一未绑定/已绑定两条路径的语义——都以「附件所有者 + 业务记录访问权」双条件判定；③附件 `accessKey` 支持轮换（或在附件被解绑时轮换），避免长期 bearer 化的 URL 泄漏。

**代价**：中。需要各模块提供「记录是否对当前用户可见」的端口实现（14 个模块）；短期可先对**销售订单**这一已具备 owner 字段的模块落地，其余模块返回「按功能权限放行」并登记 TODO。

**对应标准**：[OWASP API Security API1:2023](https://api-security.owasp.org/editions/2023/en/0xa1-broken-object-level-authorization/)、[CWE-639](https://cwe.mitre.org/data/definitions/639.html)、[OWASP File Upload Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html)。

#### S-3（P1）全局搜索 / 单据流 / 打印预览等「跨模块横向检索」端点只有功能权限，缺对象级过滤，构成批量枚举面

**证据**

- `search/web/V2GlobalSearchController.java:29-31`：`@RequirePermission(GLOBAL_SEARCH_READ)`，`keyword` + `limit` 直接进 service。
- `search/web/V2DocumentFlowController.java`：同为横向检索入口（`@RequirePermission` 覆盖）。
- `system/printtemplate/web/V2PrintPreviewController.java:42-52`：`recordIds` 最多 200 个/次，直接返回明细行。
- `common/moduleexport/service/ModuleExportService.java:80-97`：`@RequirePermission(MODULE_EXPORTS_EXPORT)`（controller 侧 `V2ModuleExportController.java:74-75`）+ `permissionChecker.require(definition.readPermission())`（`:82`），但导出**整模块**时 `recordIds == null` → `where` 为空 → `SELECT ... WHERE deleted_flag = FALSE` 全表导出（`:87-95`），上限 `maxExportRows`（`:92`）。

**失效场景**：任何持 `global-search:read`、`print-previews:read`、`module-exports:export` 的主体（在 `SUPER_ADMIN` 之外，RBAC0 允许多角色）可批量遍历全库业务数据。在「单账号」旧模型下这是等价权限，无需关注；但在 **V139/V140 已放开多用户 RBAC0** 之后（见 §0.2-1），这变成了实际的横向越权面。定级 P1 而非 P0，是因为需要管理员显式授予这些权限码，且不涉及资金写入。

**修复建议**：①为这三类横向检索引入「模块可见性集合」（`GlobalSearchModuleAccess` 曾存在，在 `569258b9` 被删除；可参考其设计恢复为**查询侧过滤**而非权限码）；②`ModuleExportService` 整模块导出加 `owner` 谓词或改为强制 `recordIds`；③在 `security-trust-model.md` 里把「横向检索类权限」列为高风险权限码，默认不授予普通角色。

**代价**：中-高。需要重新设计数据范围抽象（正是 `569258b9` 移除的那一层），但可以只做「查询侧 owner 过滤」这一最小子集，不恢复完整 DataScope 上下文。

**对应标准**：[OWASP API Security API1:2023](https://api-security.owasp.org/editions/2023/en/0xa1-broken-object-level-authorization/)、[OWASP API Security API3:2023 Broken Object Property Level Authorization](https://api-security.owasp.org/editions/2023/en/0xa3-broken-object-property-level-authorization/)。

### 2.5 P2 / P3（授权侧）

#### S-4（P2）权限码目录存在「动作名未纳入规范动作表」与「粗粒度 `:write` 与细粒度混用」，`PermissionCodeRules` 会接受它们但语义不可推导

**证据**

- `security/permission/PermissionCodes.java:394-398`：`USER_ACCOUNTS_READ/UPDATE/WRITE` 三者并存；`USER_ACCOUNTS_WRITE` 被 `auth/web/V2UserAdminController.java:70、78、87、97、108` 用于创建/编辑/状态/重置密码/删除五个动作。
- `docs/security-trust-model.md:74-77` 规定动作为 `read | create | update | delete | audit | ...`，**不含 `write`**；而 `PermissionCodes` 实际存在 `:write`（`USER_ACCOUNTS_WRITE`、`ROLES_WRITE`、以及 `PermissionCodes.java:463` 的目录登记）。
- `security/rbac/service/PermissionCodeRules.java:24-33` 的 `isKnown` 只校验「在目录内 / 全局通配 / 已知资源通配」，**不校验动作是否符合规范**。
- `security/rbac/web/V2RoleController.java:75-80`：`PUT /roles/{id}/permissions` 只需 `ROLES_WRITE`（自身就是粗粒度码），即可给自己所在角色授予 `*`。

**攻击/失效场景**：任何持 `roles:write` 的主体可以 `PUT /roles/{id}/permissions` 把**任意**角色（含自己的角色，因为 `V2UserRoleController.java:46-49` 的自提升防护只拦「改自己的角色分配」，**不拦「改自己所属角色的权限集合」**）升级为 `*`。这是一个**权限自提升链**：

1. 用户 U 持 `roles:write`（管理员助手角色常见）但**不**持 `user-accounts:update`。
2. U 调 `PUT /api/v2.0/roles/{自己角色ID}/permissions`，body `{"permissions":["*"]}`。
3. `RoleService.replacePermissions`（`security/rbac/service/RoleService.java:163-201`）通过 `normalizePermissionCodes`（`:203-217`，`*` 合法）→ 写库 → `permissionCacheService.invalidateUsersByRole(List.of(id))`（`:199`）→ **下一个请求即生效**。
4. U 现在拥有全部权限，包括 `user-accounts:update`。

定级 P2 而非 P0：需要 `roles:write` 这一本身就极高危的权限；但「持有 `roles:write` 却能绕过 `builtin` 保护语义直达 `*`」是设计缺陷（`RoleService.java:166-168` 只保护内置角色必须保留 `*`，没有限制非内置角色能否被授予 `*`）。

**修复建议**：①`replacePermissions` 增加**授权上限校验**：当前主体只能授予「自己已拥有」的权限码（子集约束），禁止把 `*` 授予任何非内置角色；②`V2UserRoleController` 的自提升防护扩展到角色权限变更（禁止修改自己所属角色的权限集合）；③`PermissionCodeRules` 增加动作白名单校验，把 `:write` 逐步迁移为 `:create/:update`。

**代价**：低（子集约束约 20 行 + 测试）。

**对应标准**：[OWASP Top 10 A01:2021 Broken Access Control](https://owasp.org/Top10/A01_2021-Broken_Access_Control/)、[CWE-269 Improper Privilege Management](https://cwe.mitre.org/data/definitions/269.html)、[OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html)。

#### S-5（P2）`docs/security-trust-model.md` 与代码现状不符：文档声明单账号且 setup 端点永久 403，实际已是多用户 RBAC0

**证据**

- `docs/security-trust-model.md:8-14`：「系统仅存在一个……初始化完成后 setup 端点永久禁用，无法再创建第二个账号」「不存在角色、权限点或数据范围隔离；任何通过认证的会话都拥有全部业务能力」。
- 代码事实：`V139__rbac0_user_role_permission.sql:17` `DROP INDEX IF EXISTS public.uk_sys_user_single_active`；`V140__multi_user_account_management.sql:1` 标题即「多用户账号管理：放开单人模式遗留的账号约束，支撑管理员建号/停用/软删」；`auth/web/V2UserAdminController.java:41` 提供完整 `POST /users` 创建账号能力（需 `user-accounts:write`）。
- `security-trust-model.md:14` 的「登录即管理员」与实际 `RoleBasedAuthorityProvider`（`RoleBasedAuthorityProvider.java:46-65` 按角色查权限）矛盾。

**失效场景**：这不是代码漏洞，而是**安全文档失真**——评审、渗透测试、上线检查都会依据 `security-trust-model.md` 判断「不存在越权面」，从而放过 S-1/S-2/S-3。`:4` 还写着「任何新增『登录用户可修改全局影响面资源』的端点时，必须同步更新本文档的端点清单」，说明该文档本应随代码演进维护。

**修复建议**：①重写 `security-trust-model.md` 第 1 节为「多用户 RBAC0 + 行级数据范围已移除（`V84`）」；②明确登记「当前**没有**行级数据范围，因此 `@RequirePermission` 是唯一的对象访问控制手段，凡需对象级隔离的资源必须自带 owner 断言」；③把 §2.4 的 S-1/S-3 列入「收紧点」清单。

**代价**：低（纯文档），但优先级不低——它是其他所有结论的前提。

#### S-6（P2）`@RequirePermission` 覆盖虽全，但 13 个未注解端点中有 5 个属于「当前用户自身资源」，缺少与其它端点一致的自证校验

**证据**：未加 `@RequirePermission` 的 13 个端点分布：
`auth/web/V2AuthController.java`（3，全部 `@PublicAccess`）、`common/web/HealthController.java`（1，`@PublicAccess`）、`common/web/VersionController.java`（1，`@PublicAccess`）、`system/web/HealthPageController.java`（1）、`system/setup/web/V2InitialSetupController.java`（1，`@PublicAccess`）、`system/runtimeconfig/web/V2RuntimeConfigController.java`（1，`@PublicAccess`）、`auth/web/V2UserAccountController.java`（5）。

其中 `V2UserAccountController` 的 5 个端点是唯一「无注解但**非** `@PublicAccess`」的写/读入口（`GET /account`、`PUT /account`、`PUT /account/password`、`GET/PUT /account/preferences`）。它们靠 `currentUserId(principal)`（`V2UserAccountController.java:68-73`）从 principal 取 id，本身**是安全的**（不接受路径 id）。

**失效场景**：风险不在当前实现，而在**规范缺口**——`AGENTS.md:151` 要求「新增写接口必须评估认证、授权、租户/数据范围」。这 5 个端点既无权限码也无显式豁免标记，未来若有人复制其结构创建 `GET /users/{id}` 风格端点，会同时丢掉权限码与 owner 校验。另外 `PUT /account/password`（`:50-56`）无频率限制（登录失败保护只覆盖 `/auth/login`，`LoginAttemptService` 仅被 `LoginService.java:55、:73` 调用），持被盗 token 者可无限次暴力验证 `currentPassword`。

**修复建议**：①为 `/account/*` 增加显式豁免注解（如 `@SelfScoped`）并在架构测试中断言「无 `@RequirePermission` 的端点必须带 `@PublicAccess` 或 `@SelfScoped`」；②`PUT /account/password` 接入 `LoginAttemptService`（或独立的自助改密限流），失败计数按 userId 维度。

**代价**：低。豁免注解 + 1 个架构测试 + 改密限流复用现有 `LoginAttemptService`。

#### S-7（P3）`PermissionAuthorizationManager` 的 `OR` 语义与 `资源:*` 通配粒度在字段级权限上会静默放大授权

**证据**：`security/permission/PermissionAuthorizationManager.java:50-52` 使用 `anyMatch`（`@RequirePermission` 多值取 OR）；`PermissionCodes.grants`（`PermissionCodes.java:505-517`）允许 `资源:*` 覆盖该资源任意动作，**包括** `资源:read:amount` 这类字段级码（注释 `:57-60` 明确承认此行为）。

**失效场景**：授予 `sales-orders:*` 给「销售主管」时，同时授予了 `sales-orders:read:amount` 字段级权限，即**金额可见性**。这与 `docs/security-trust-model.md:82-83`「字段层登记预留」的意图不符——字段层本应是独立的收紧维度。

**修复建议**：`grants` 对 `资源:字段` 形式的三段码要求显式精确匹配（`资源:*` 只覆盖两段码），并在 `docs/security-trust-model.md:82` 记录该语义。

**代价**：低（一处判断 + `PermissionCodes` 测试），但有行为变更风险，需先核对现有角色是否依赖通配覆盖字段码。

**对应标准**：[OWASP API Security API3:2023](https://api-security.owasp.org/editions/2023/en/0xa3-broken-object-property-level-authorization/)、[OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html)。

#### S-8（P2）历史审计文档 `docs/2026-07-10-backend-code-review.md` 的修复进度表与当前实现不一致，会把已回退的安全能力标成「已修复」

**证据**

- `docs/2026-07-10-backend-code-review.md:22` 的进度表仍写：「High-01 | **已修复** | 销售订单打印增加记录级数据范围校验」，`:466-472` 还保留完整修复说明与验收标准（调用 `ResourceRecordAccessGuard.assertCurrentUserCanAccess`、越权固定 403）。
- 但该 guard 已在 `49c71e59`（2026-07-18）被删除且**再无恢复提交**（详见 S-1 证据链第 2-4 条）；当前 `grep -rn "ResourceRecordAccessGuard\|assertCurrentUserCanAccess" src/main/java` = **0 处**。
- 同类失真至少还有两处：
  - `:23` 记「High-02 | 部分修复 | ……多实例 Redis 共享限流仍待实施」——该项**已在当前基线闭环**（`RedisRateLimitStore.java:79-111` 单 Lua 原子脚本、`SecurityConfig.java:161-166` 关闭重复注册），文档状态偏保守，属「修复了但没更新」。
  - `:31` 记「其余发现 | 待处理 | ……动态限流规则……未纳入本批次」——`sys_rate_limit_rule` 至今是死表（`V1__baseline.sql:1753-1756` 建表且有种子 `:2397-2400`；全仓库无 Java 读取），判断正确但缺时间戳，无法与 High-01 的失真相区分。
- `docs/security-trust-model.md:8-14` 同样失真（已单独登记为 S-5），两份文档共同构成「安全基线文档与实现脱节」。

**失效场景**：这不是运行时漏洞，而是**审计可追溯性缺陷**。本次审计即为直接案例：若仅按历史审计表核对，会判定「High-01 已修复」从而跳过 `SalesOrderPrintExportService`，P0 就此漏报。同类风险适用于任何依据该表做上线检查、渗透范围界定或合规取证的流程。`:4` 虽声明只覆盖原始审查提交，但读者通常只看进度表而不看表头。

**修复建议**

1. 在 `docs/2026-07-10-backend-code-review.md:22` 的 High-01 行直接加更正标注：「**该修复已随 `49c71e59`（2026-07-18 移除 RBAC）回退，当前基线不存在记录级守卫，见 `04-security-consistency.md` S-1**」。
2. 进度表增加「最后复核提交 + 复核日期」列，规定每次大版本发布前只复核已标记修复的条目。
3. 建立「安全能力清单」替代「发现清单」：以能力（记录级守卫、密钥版本化、限流覆盖、审计留痕）为单位登记是否生效 + 验证方式（测试名/命令），取代一次性静态表格。
4. CI 增加文档一致性检查：断言本文档引用的关键符号（如 `ResourceRecordAccessGuard`）在 `src/main/java` 存在，不存在则失败。

**代价**：低（①③④为文档与脚本工作），但收益高——它决定下次审计能否信任历史结论。

**对应标准**：`AGENTS.md`《业务口径只在一处实现》的同类问题（同一事实两处维护导致分叉）；[A04:2021 Insecure Design](https://owasp.org/Top10/A04_2021-Insecure_Design/)（安全基线文档属设计约束的一部分）、[CWE-1059 Insufficient Documentation](https://cwe.mitre.org/data/definitions/1059.html)。

---

## 3. 附件、上传与加密密钥

### 3.1 现状核对（正面）

- **路径穿越防护到位**：`attachment/service/storage/LocalAttachmentStorage.java:51-55`（`root.resolve(objectKey).normalize()` + `startsWith(root)`）、`:110-127`（`resolvePath` 对带前缀与 legacy 路径都做 `startsWith(root)` 校验）。文件名侧 `AttachmentFilenameResolver.java:102-117` 把 `\ / : * ? " < > |` 全部替换为 `_` 并去除首尾 `_.`，`:119-126` 把扩展名限制为 `[a-z0-9]` 且截断 16 字符。**未发现路径穿越**。
- **上传大小限制双处同源**：`application.yml:152-156`（Servlet multipart 20MB/25MB）与 `:230`（`leo.attachment.max-file-size`）使用同一环境变量 `LEO_ATTACHMENT_MAX_FILE_SIZE`；服务端二次校验在 `AttachmentUploadService.java:179-192`。
- **扩展名黑名单**：`AttachmentUploadService.java:30-32` 阻断 `jsp/jspx/php/phtml/asp/aspx/exe/bat/cmd/sh/cgi/war`。
- **S3 直传校验完整**：`S3CompatibleAttachmentStorage.java:168-191` 预签名带 `contentLength` + `checksumSHA256`；`:195-215` 回传后用 `headObject` 校验桶一致、大小、SHA-256（checksum 缺失时回退为整对象读取计算）。
- **直传令牌防篡改**：`AttachmentDirectUploadTokenService.java:42-57` 绑定 `attachmentId` + `moduleKey` + `ownerUserId` + 过期时间，HMAC 签名 + `MessageDigest.isEqual` 常量时间比较（`:63-66`）；`:80+` 使用 `Mac`（HMAC）。
- **服务端代理为默认**：`application.yml:265` `server-proxy-only: ${LEO_ATTACHMENT_S3_SERVER_PROXY_ONLY:true}`，默认禁止浏览器直连对象存储，预签名 URL 不对外暴露。
- **预签名 TTL 短**：`:270-273` upload 10m / preview 5m。
- **加密引擎选型正确**：`system/securitykey/service/TinkAeadSecretEncryptionEngine.java:50-69` 使用 Tink `AesGcmParameters`（32 字节密钥、12 字节 IV、16 字节 tag）；`attachment/service/storage/AttachmentContentCryptor.java:35-50` 使用 AES-GCM + `SecureRandom` IV + `LEOENC1` magic 前缀。
- **密钥非硬编码**：`V88__bootstrap_data_encryption_key.sql:1-40` 用 `gen_random_uuid()` 生成 `DATA_MASTER`（不写死字面量），`SecurityKeyService.java:88-114` 要求 ≥32 字符否则启动失败。

### 3.2 P1

#### A-1（P1）附件内容加密使用「单一活动主密钥」，附件记录不保存密钥版本，轮转 `DATA_MASTER` 会使**全部历史附件永久不可读**（历史 High-08 标注「待处理」，当前仍为未闭环）

**证据**

- `attachment/service/storage/AttachmentContentCryptor.java:89-101`：
  ```java
  private SecretKeySpec key() {
      String material = securityKeyService.getActiveDataMaterial().secretValue();   // ← 永远取 ACTIVE
      ...
  }
  ```
  加密与解密**都**调用 `key()`，即都取「当前 ACTIVE 密钥」。
- `attachment/domain/entity/AttachmentFile.java:17-46` 实体**没有任何 key version 字段**（字段仅 `id / ownerUserId / fileName / originalFileName / fileExtension / contentType / fileSize / storagePath / accessKey / sourceType`）。
- 对照 JWT 侧做了正确的版本化：`security/jwt/JwtTokenService.java:33-37`（签发时把 `activeMaterial.version()` 写入 JWT header `kid`）、`:50-72`（验签遍历 `getJwtVerificationMaterials()` 多版本）、`:101-131`（按 `retiredAt` 限定窗口）；`SecurityKeyService.java:47-60` 会加载 ACTIVE + RETIRED 两类密钥。
- 附件侧**没有**对应的多版本加载入口：`SecurityKeyService.java:43-45` 的 `getActiveDataMaterial()` 是唯一数据密钥读取方法。
- 历史状态：`docs/2026-07-10-backend-code-review.md:31` 把「附件密钥版本化」列在「其余发现 | 待处理」；`:279` High-08 标题即「轮转 TOTP 主密钥可能使加密附件永久不可读」。

**失效场景**

1. 运维在 `sys_security_secret` 中把当前 `DATA_MASTER`（version=1）置为 `RETIRED` 并插入 version=2 `ACTIVE`（这是正常的密钥轮转操作）。
2. 下一个下载请求：`AttachmentContentCryptor.decrypt` → `key()` → `getActiveDataMaterial()` 返回 **version 2**。
3. AES-GCM 用 v2 解 v1 加密的密文 → `AEADBadTagException` → 被 `:62-64` 捕获后包装为 `BusinessException(INTERNAL_ERROR, "附件解密失败")`。
4. **所有历史加密附件同时不可读，且无任何恢复路径**（密文与密钥都还在库里，但代码里没有「按版本找密钥」的查找逻辑）。
5. 更隐蔽的变体：若某环境同时开了 `local.encrypted-storage=true` 与 `s3.encrypted-storage=true`，两类存储的密文共用同一主密钥，轮转影响面翻倍。

注意：由于 `encrypted-storage` 默认 `false`（`application.yml:239、:262`），**默认配置下不触发**；这是一条「一旦开启加密存储就必然踩中」的定时炸弹，因此定级 P1 而非 P2。

**修复建议**

1. `sys_attachment` 增加 `encryption_key_version integer` + `encryption_algorithm varchar(32)`（新 Flyway `V190__add_attachment_key_version.sql`），写入密文时记录版本。
2. `SecurityKeyService` 增加 `getDataMaterial(int version)`（按 `key_version` 查 ACTIVE/RETIRED，键控精确匹配），`AttachmentContentCryptor` 改为 `key(int version)`；解密时按记录里的版本取密钥，**不再读 ACTIVE**。
3. 密文头部（现 `MAGIC = "LEOENC1"`）扩展为 `LEOENC1` + `version` 1 字节，对存量数据在解密时按 magic 兜底为 version 1，平滑迁移。
4. 轮转前增加预检：统计所有 `encryption_key_version` 分布，确认旧版本仍在 `sys_security_secret` 中且未 `deleted_flag=true`。
5. 回归：加密→轮转→解密→明文一致；旧版本密钥删除后明确报错「密钥版本缺失」而非笼统「解密失败」。

**代价**：中。1 个新迁移 + 2 个类改造 + 存量兜底；不做的话，一次轮转即造成不可逆的数据可用性事故。

**对应标准**：[OWASP Top 10 A02:2021 Cryptographic Failures](https://owasp.org/Top10/A02_2021-Cryptographic_Failures/)、[OWASP Cryptographic Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html)、[CWE-320 Key Management Errors](https://cwe.mitre.org/data/definitions/320.html)、[CWE-798 Use of Hard-coded Credentials](https://cwe.mitre.org/data/definitions/798.html)（作为密钥管理的对照反例）。

### 3.3 P2 / P3（附件侧）

#### A-2（P2）上传类型校验是「扩展名黑名单」而非「内容嗅探 + 白名单」，且 `contentType` 完全由客户端声明并原样落库与回显

**证据**

- `AttachmentUploadService.java:186-191`：只对**原始文件名最后一个 `.` 之后的扩展名**做黑名单比对；无 magic bytes 校验、无白名单。
- `AttachmentUploadService.java:81`：`file.getContentType()`（客户端 `Content-Type`）直接写入元数据。
- `AttachmentResponseAssembler.java:81-88`：`resolveResponseContentType` 对 `pdf`/`image` 按**扩展名**推断；其它类型统一下发 `application/octet-stream`。
- 下载响应：`AttachmentContentService.java:65-71` 用 `MediaType.parseMediaType(payload.contentType())`，`inline` 时用 `ContentDisposition.inline()`。

**失效场景**

1. 攻击者上传 `evil.svg`（SVG 不在黑名单）→ `svn`/`svg` 未阻断 → `detectPreviewType` 对非 pdf/图片扩展名返回 `none`（`AttachmentResponseAssembler.java:70-79`），`inline` 会抛「不支持预览」（`AttachmentContentService.java:79-81`），**这条路径当前被堵住**。
2. 真正的缺口是 `contentType` 客户端可控：上传时声明 `contentType=text/html` 但文件名 `a.pdf`。`storedFileName` 由 `buildStoredFileName` 生成为 `<id>.pdf`（`AttachmentFilenameResolver.java:24-29`），落库 `contentType=text/html`。下载时 `loadForPreview` 因扩展名 `pdf` 判定 `previewType=pdf` → `resolveResponseContentType` 返回 `application/pdf`（**覆盖了客户端声明**）→ 安全。但 `loadForDownload`（`attachment=false`）走 `responseAssembler.toPresentation(entity, null)` → `resolveResponseContentType(entity, previewType)`，`previewType` 仍按扩展名算，同样返回 `application/pdf`。
3. 因此**当前实现下 HTML 嗅探被扩展名派生覆盖**，实际无法构造 XSS。定级 P2 的理由是**纵深防御缺失**：一旦未来某处改为直接信任 `entity.getContentType()`（`AttachmentContentService.java:82` 的 `responseContentType` 分支已有一处直接用 `entity.getContentType()`），立刻变成存储型 XSS。

**修复建议**：①上传时用 Apache Tika 或 magic bytes 校验真实类型，并强制 `contentType` 从真实类型派生，**忽略客户端声明**；②改为白名单（`pdf/png/jpg/jpeg/gif/webp/bmp/txt/doc/docx/xls/xlsx`，与 `AttachmentMediaTypes.java:33-46` 已有的映射表对齐）；③`AttachmentContentService.java:82` 的 else 分支改为 `OCTET_STREAM`；④下载响应加 `X-Content-Type-Options: nosniff`（全局 header 已在 `SecurityConfig.java:63` 配置，需确认对附件路径生效）。

**代价**：低-中。Tika 引入一个新依赖；若只用 magic bytes 手写校验，约 60 行。

**对应标准**：[OWASP File Upload Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html)、[CWE-434 Unrestricted Upload of File with Dangerous Type](https://cwe.mitre.org/data/definitions/434.html)、[OWASP Top 10 A05:2021 Security Misconfiguration](https://owasp.org/Top10/A05_2021-Security_Misconfiguration/)。

#### A-3（P2）附件恢复清单导出把 `storagePath`、`accessKeyHash`、`ownerUserId` 等内部结构写入可下载文件，且一次性 `findAll` 全表加载

**证据**

- `attachment/service/AttachmentManifestExportService.java:146-154`：
  ```java
  node.put("storagePath", attachment.getStoragePath());
  ...
  node.put("accessKeyHash", sha256Hex(attachment.getAccessKey()));
  node.put("ownerUserId", stringValue(attachment.getOwnerUserId()));
  ```
- `:84-88`：`@Transactional(readOnly = true)` 内 `attachmentFileRepository.findAllByOrderByIdAsc()` 全表加载；`:90-91` 再全表加载 bindings。
- `:95`：`storageResolver.storeBytes(...)` 在**事务内**执行对象存储写入（见 T-1）。
- 端点：`attachment/web/V2AttachmentManifestExportController.java:44-47`，`POST /attachment-manifest-exports`，仅需 `ATTACHMENT_MANIFEST_EXPORTS_EXPORT`。

**失效场景**：清单文件（gzip JSONL）可被下载后离线分析，暴露：①`storagePath` 泄露本地绝对路径或 S3 bucket/key 结构，为后续路径穿越或桶枚举提供情报；②`accessKeyHash` 是 `accessKey` 的 **SHA-256 哈希**——`accessKey` 由两个 UUID 拼接（`AttachmentMetadataService.java:46-47`，64 字符高熵），哈希不可逆，风险低，但暴露「哪些附件共享同一 key」；③`ownerUserId` 暴露人员-附件映射关系。综合为「过度暴露内部实现」而非直接可利用漏洞，故 P2。

**修复建议**：①清单只输出恢复所必需的字段（`id / fileName / fileSize / sha256 / binding(moduleKey, recordId, sortOrder)`），**移除 `storagePath`、`accessKeyHash`、`ownerUserId`**，`storagePath` 改为不透明引用；②改为 keyset 分页流式写盘（`Stream` + `ScrollableResults`），避免全表进堆；③把对象存储写入移出事务（见 T-1）。

**代价**：低-中。

**对应标准**：[OWASP API Security API3:2023](https://api-security.owasp.org/editions/2023/en/0xa3-broken-object-property-level-authorization/)、[CWE-200 Exposure of Sensitive Information](https://cwe.mitre.org/data/definitions/200.html)。

#### A-4（P3）本地附件存储目录与加密开关默认值组合存在「默认明文落盘」

**证据**：`application.yml:236-239`：`path: /tmp/leo/uploads`（`/tmp` 通常全局可读）、`encrypted-storage: false`。

**失效场景**：同机其他用户（或同机其他容器共享 `/tmp` 卷）可读取附件明文；`/tmp` 还常被 systemd `PrivateTmp` 隔离，误配置会让附件在重启后「消失」。

**修复建议**：默认路径改到 `/var/lib/leo/attachments`（`0700`），并在部署文档与 `deploy/systemd/leo-backend.service` 中显式声明；生产建议默认 `encrypted-storage: true`（与 A-1 的密钥版本化配套）。

**代价**：极低（配置 + 部署脚本），但需与 A-1 一起做，否则开启加密会引入轮转风险。

---

## 4. 限流与资源耗尽

#### R-1（P1）读路径限流默认关闭，且 `limit-write-methods` 默认 false——写接口（含导出、打印、导入）完全没有限流；历史 High-02/High-04 的「多实例共享限流」已修复，但覆盖范围问题仍在

**证据**

- `application.yml:626` `enabled: ${LEO_RATE_LIMIT_ENABLED:false}`；`:628` `limit-write-methods: ${LEO_RATE_LIMIT_LIMIT_WRITE_METHODS:false}`。`.env.example:154-155` 同样是 `false/false`。
- `common/ratelimit/RateLimitFilter.java:120-123`：`if (!properties.isEnabled()) { chain.doFilter(); return; }` —— 关闭时**零限流**。
- `:124-127` + `:81`：`WRITE_METHODS` 只在 `limitWriteMethods=true` 时纳入。
- **正面（历史 High-02 部分修复已闭环）**：多实例共享已实现——`RedisRateLimitStore.java:79-111` 用单个 Lua 脚本在 Redis 内原子完成「裁剪 → 计数 → 准入 → 记账 → PEXPIRE」，`global` 维度为集群全局语义（`:32-35` 注释明确）；`RateLimitFilter` 通过 `SecurityConfig.java:161-166` 关闭了 Servlet 容器的重复注册。
- **正面**：`ClientIpResolver.java:34-72` 已按「从右向左剥离可信代理」实现（`:68-70`），有长度（1024）与跳数（8）上限，非法地址回退 `remoteAddr`，不做 DNS 解析；`deploy/systemd/leo-backend.service:14` 已注入 `LEO_TRUSTED_PROXIES=127.0.0.1`。

**失效场景**（限流开启后仍存在的缺口）

1. **写方法不限流**：`POST /print-exports`（同步生成 PDF，`V2PrintExportController.java:64-79`）、`POST /module-exports`（整模块 XLSX）、`POST /material-imports`（文件解析）都属 CPU/内存密集写操作。它们受 `ExportConcurrencyGuard` 保护（`V2PrintExportController.java:68-69`、`application.yml:642-648` `max-concurrent: 4`），这是**并发闸门而非速率限制**——同一主体可以串行无限次调用，持续占用 4 个槽位中的 1 个，形成慢速资源耗尽。
2. **限流维度缺「成本感知」**：`RateLimitCheck` 每请求固定消费 1 个 token（`RedisRateLimitStore.java:91` `ZADD key now ARGV[6]`），不区分「查 1 条」与「导出 10 万行」。`ModuleExportService.java:92` 的 `maxExportRows`（默认 10000，`application.yml:617`）是唯一上限。
3. **登录失败保护不覆盖自助改密**：`LoginAttemptService` 仅被 `LoginService.java:55、:73` 使用；`PUT /account/password`（`V2UserAccountController.java:50-56`）无失败计数（见 S-6）。
4. **`sys_rate_limit_rule` 表是死表**：`V1__baseline.sql:1753-1756` 建表且有种子数据（`:2397-2400`），但全仓库**无任何 Java 代码读取**（`grep -rn "sys_rate_limit_rule\|RateLimitRule" src/main/java` 返回空）。历史 High-04「动态限流规则管理接口修改后不影响运行时限流」**未闭环**，只是把管理面移除了——运维仍无法在不重启的情况下调整额度（只能改环境变量重启）。
5. **过度豁免**：`RateLimitFilter.java:87-94` 内置豁免含 `/v2.0/auth/login`。理由是「登录已有失败次数保护」（`:51-52`），但**登录失败的速率**仍不受限（只有失败**次数**计数触发锁定，`AuthProperties.java:132-135` `maxFailures=5`/`lockDurationSeconds`）。攻击者可用大量不同 `loginName` 触发 bcrypt（strength 12，约 250ms/次）形成 CPU 耗尽——每次尝试都是新的 loginName，不触发任何单账号锁定。

**攻击/失效场景（第 5 点，最具体）**：攻击者脚本并发 POST `/api/v2.0/auth/login`，`loginName` 每次随机。`LoginService.java:63-67` 先 `findByLoginNameAndDeletedFlagFalse` → 未命中直接抛 `invalidCredentials`，**不执行 bcrypt**——所以随机 loginName 的路径是廉价的。仅当命中真实 loginName 时才跑 bcrypt，而此时会触发该账号的锁定计数（5 次/900s）。因此**该攻击成本低但收益也低**（无法绕 lock），真正的风险是 `AuthProperties` 的 `loginProtection.enabled` 可被环境变量关闭（`application.yml:536` 默认 true）。

**修复建议**：①`limit-write-methods` 默认改为 `true`（写方法限流是防成本攻击的核心）；②为导出/打印/导入类端点引入**权重化限流**（`RateLimitCheck` 增加 `cost` 字段，导出消费 N 个 token），或在 `ExportConcurrencyGuard` 之外增加「每主体每小时导出次数」配额；③接通或删除 `sys_rate_limit_rule`（历史 High-04 建议仍有效：`docs/2026-07-10-backend-code-review.md:732`）；④`PUT /account/password` 接入失败计数；⑤`/auth/login` 的豁免改为「仅豁免全局维度、保留匿名 IP 维度」（当前 `RateLimitFilter.java:129-132` 是整条豁免）。

**代价**：中。①③④各约 20-50 行；②需要改 `RateLimitCheck` record + Lua 脚本 + 调用点。

**对应标准**：[OWASP API Security API4:2023 Unrestricted Resource Consumption](https://api-security.owasp.org/editions/2023/en/0xa4-unrestricted-resource-consumption/)、[OWASP API Security API6:2023 Unrestricted Access to Sensitive Business Flows](https://api-security.owasp.org/editions/2023/en/0xa6-unrestricted-access-to-sensitive-business-flows/)、[CWE-770 Allocation of Resources Without Limits](https://cwe.mitre.org/data/definitions/770.html)、[CWE-400 Uncontrolled Resource Consumption](https://cwe.mitre.org/data/definitions/400.html)。

#### R-2（P2）打印预览的内存分页 + 无限 `recordIds` 组合放大

**证据**：`PrintScriptService.java:325-331` 把 `recordIds` 对应明细**全量**读入 `List<PrintRecordItem> all`，再 `sorted.subList(from, to)` 内存分页。

**失效场景**：`V2PrintPreviewController.java:49` 允许 `recordIds` 最多 200 个。若每个订单有 500 行明细，单请求加载 10 万行对象进堆；`ExportConcurrencyGuard` **不覆盖**这个 GET 端点（只有 `print-exports` 和 `module-exports` 包了闸门）。多个并发请求即可 OOM。

**修复建议**：下推分页到 SQL（`PrintRecordDataProvider.listPrintItems` 已有 `IN (?,...)` 结构，`PrintRecordDataProvider.java:44-54`，改为带 `LIMIT/OFFSET` 与 `ORDER BY` 的查询）；同时把该端点纳入 `ExportConcurrencyGuard`。

**代价**：低-中。需按 moduleKey 的 `source.printColumns()` 动态拼排序白名单（`PageSortFieldCatalog` 已有模式可复用）。

#### R-3（P2）`HttpIdempotencyFilter` 对超大 multipart 静默跳过幂等，且已完成响应在 Redis 中缓存 24h 无体积上限

**证据**

- `common/idempotent/HttpIdempotencyFilter.java:110-119`：`isMultipart && !isWithinMultipartLimit` → `return false`（跳过），`MULTIPART_MAX_IDEMPOTENT_BYTES = 5MB`（`:66`）。
- `:145-153`：成功响应经 `ContentCachingResponseWrapper` 缓存后 `markCompleted(..., DEFAULT_TTL=24h, cachedResponse)`（`:65` `Duration.ofHours(24)`）。

**失效场景**：①>5MB 的上传请求（附件上限 20MB，`application.yml:154`）**不享受幂等保护**，重复提交会重复落库（虽然附件本身有 `accessKey` 唯一性与 `V108__enforce_single_active_attachment_binding.sql` 兜底，但 `POST /material-imports` 这类非附件上传同样 >5MB 时无保护）；②把打印导出的 PDF 响应（可能是数 MB）整体缓存进 Redis 24 小时，配合 `POST /print-exports` 可造成 Redis 内存放大；`cachedResponse` 无 `maxBodyBytes` 截断。

**修复建议**：①超大 multipart 返回明确 413/422 而不是静默跳过（或按 `sha256(前 5MB)` 做部分指纹）；②`cachedResponse` 增加体积阈值，超过阈值只缓存「状态 + Location」不缓存 body；③幂等缓存 TTL 与响应体积挂钩。

**代价**：低。

**对应标准**：[OWASP API Security API4:2023](https://api-security.owasp.org/editions/2023/en/0xa4-unrestricted-resource-consumption/)、[CWE-770](https://cwe.mitre.org/data/definitions/770.html)。

#### R-4（P3）`RateLimitStore` 在 Redis 不可用时统一 fail-open

**证据**：`RateLimitFilter.java:148-163`（异常与 `UNAVAILABLE` 均放行 + WARN）；`RedisRateLimitStore.java:129、:146-148` 同样 fail-open。

**失效场景**：Redis 故障期间限流失效。这是**有意取舍**（`:57-60` 注释明确「限流器绝不能成为 500 来源」），与认证链路的 fail-closed（`JwtAuthenticationFilter.java:88-94`）形成有意对比。登记为 P3 仅提示：应把「限流 fail-open 持续时长」做成指标告警，否则故障期间无感知。

**修复建议**：`RateLimitFilter` fail-open 时递增 Micrometer counter 并配置告警阈值；连续 fail-open 超过 N 分钟降级为「仅全局维度本地内存桶」（有界）。

**代价**：低。

---

## 5. 事务与并发一致性专项

> 背景数据：全仓库 `@Transactional` **419 处**；`@Entity` **76 个**，其中带 `@Version` **32 个**（无版本列 **44 个**）。本节只报告会导致「资金/库存口径错误」或「静默丢更新」的路径。

### 5.1 现状核对（正面）

- **AFTER_COMMIT 抽象正确**：`common/support/AfterCommitExecutor.java:10-24` 在无活动事务时直接执行（`:14-17`），有事务时注册 `TransactionSynchronization.afterCommit`。使用点全部为「缓存失效 / 黑名单写入」这类幂等旁路写：`SessionManagementService.java:191、:222`、`RedisJsonCacheSupport.java:111、:133`、`PermissionCacheService.java:259`、`MasterDataCodeIssuanceService.java:90`、`MaterialDictionaryCache.java:41`。
- **乐观锁冲突统一 409**：`GlobalExceptionHandler.java:278-296` 捕 `ObjectOptimisticLockingFailureException` / `OptimisticLockException` → `ErrorCode.CONCURRENT_MODIFICATION`（4090）→ `ApiProblemFactory` 映射 409（`:541`）；Pessimistic 超时同样处理（`:302-314`）。
- **按职责区分的并发编码**：`ErrorCode.java:15` `CONCURRENT_MODIFICATION(4090)` 与 `IDEMPOTENCY_CONFLICT(4092)` 分开（`:20` 注释明确「4090 文案引导刷新重试，重复提交应引导等待」），`HttpIdempotencyFilter.java:177-190` 使用 4092。
- **来源锁顺序设计正确且集中**：`common/concurrency/SourceAllocationLockService.java:33-44` 用静态 rank 固化「表名字典序」，`:131-136` 所有步骤按 rank 排序后串行执行，`:47-78` 每张表单独一条 `WHERE id IN (...) ORDER BY id FOR UPDATE`（`:21-28` 注释解释了为何不用 `FOR UPDATE OF a, b`）；`:178-185` 对 ID 去空去重升序；`:86、:103、:122` 全部 `Propagation.MANDATORY`（强制调用方已有事务）。
- **库存维度锁协议统一**：`inventory/service/InventoryTransactionLockService.java:21-22` 用 `pg_advisory_xact_lock`，`:49-60` 按 `(materialId, warehouseId)` 全局升序加锁，`:70-72` 空值排最后；`:62-68` 期初回填用全局锁 `inv:backfill`，`InventoryBackfillService.java:81`、`InventoryBalanceMaintenanceService.java:33` 都先取该锁 —— **回填与常规记账不会交叉死锁**。
- **软删与快照守恒**：`InventoryTransactionService.java:79-106` 软删时先锁维度（`:97-103`）再反向回补快照；`:255-272` 对「恰好清零」的销售出库做残值修正（`:264-268`），保证 `Σ账本 == 快照`。
- **登录链路刻意不写实体**：`TokenIssuanceService.java:133-140` 用原子 UPDATE 落 `last_login_date` 而非 `save(user)`，避免整行覆盖与 409 风暴；`LoginService.java:70-88` 的注释记录了实测 20 并发登录 92% 409 的历史。

### 5.2 P1

#### T-1（P1）事务内执行对象存储 / 文件 I/O：附件清单导出在 `@Transactional(readOnly=true)` 内写 S3，把数据库连接与事务 held 时长绑定到外部 I/O 延迟

**证据**

- `attachment/service/AttachmentManifestExportService.java:84-95`：
  ```java
  @Transactional(readOnly = true)
  public AttachmentManifestArchive exportDailyArchive() {
      List<AttachmentFile> attachments = attachmentFileRepository.findAllByOrderByIdAsc();   // 全表
      List<AttachmentBinding> bindings = attachmentBindingRepository.findAllBy...;           // 全表
      byte[] payload = gzip(toJsonLines(...));
      String storagePath = storageResolver.storeBytes(objectKey, payload, CONTENT_TYPE_GZIP); // ← 事务内远程 IO
  ```
- `:72-79` 的 `exportDaily()` 又在外层套了一层 `@Transactional(readOnly = true)`（自调用代理问题之外，同样在事务内）。
- 对照**正确的实现**：`AttachmentUploadService.java:72-73` 注释「Store file outside the DB transaction to avoid holding connections during I/O」，先 `storageResolver.store(...)` 再 `metadataService.saveUploadedFileMetadata(...)`，失败时 `cleanupStoredFileQuietly`（`:86-89`）补偿 —— 说明团队已经识别并解决了同类问题，清单导出是遗漏点。
- 同类：`market/service/SteelxQuoteSyncService.java:126` `fetcher.fetch(...)` 是 JDK `HttpClient` 同步调用（`steelx/SteelxFetcher.java:96-113`），其调用方 `SteelQuoteStore.java:43、:114` 标了 `@Transactional`；`fetchViaSsh`（`:122-160`）还会 `ProcessBuilder` 阻塞等 `ssh` 子进程（`waitFor(timeoutSeconds + grace, SECONDS)`）。

**失效场景**

1. 对象存储抖动（S3 超时 `read-timeout: 30s`，`application.yml:268`）时，清单导出事务持续持有连接池连接 30s+；`spring.datasource.hikari` 默认 10 连接，3-4 个并发清单导出即可打满连接池，导致**全站所有需要数据库的请求排队超时**。
2. 事务内 I/O 失败后数据库事务回滚，但**已写入对象存储的 gzip 文件不会回滚**（`storeBytes` 无事务性），产生孤儿对象。
3. `SteelxFetcher` 走 SSH 分支时，单次 `fetch` 最长阻塞 `requestTimeout/1000 + SSH_WAIT_GRACE_SECONDS` 秒；`syncAllRegions`（`SteelxQuoteSyncService.java:83-95`）串行遍历所有地区，最坏情况是「单请求 + 单事务」持锁数分钟。该链路若由 `POST /steel-quote-syncs`（`V2SteelQuoteSyncController.java:69-77`，`@ResponseStatus(ACCEPTED)`）触发，则**名为 202 实为同步长事务**。

**修复建议**：①清单导出改为两阶段：事务内只读取并序列化到内存/临时文件（或 keyset 流式），**事务提交后**再 `storeBytes`；失败只影响存储、不影响 DB 语义。②`SteelxQuoteSyncService` 的远程抓取移出事务边界（`syncUrl` 的 `fetcher.fetch` 在事务外，落库另开事务），`POST /steel-quote-syncs` 改为真正的异步任务（返回任务资源 + `Location`，符合 `AGENTS.md` 资源建模要求）。③加架构测试：断言 `@Transactional` 方法体内不出现 `storageResolver.*`、`HttpClient.send`、`ProcessBuilder.start`。

**代价**：中。①低（1 个类拆分）；②需要异步任务表与 worker；③用 ArchUnit 可静态约束，成本低收益高。

**对应标准**：[CWE-400](https://cwe.mitre.org/data/definitions/400.html)、[OWASP API Security API4:2023](https://api-security.owasp.org/editions/2023/en/0xa4-unrestricted-resource-consumption/)、[Spring Framework 事务与 I/O 指南（Transaction Management）](https://docs.spring.io/spring-framework/reference/data-access/transaction.html)。

### 5.3 P2 / P3（事务与并发）

#### T-2（P2）44 个无 `@Version` 的实体中，若干是「可被并发整体替换」的聚合子表，存在静默丢更新；`PaymentAllocation` / `ReceiptAllocation` 属于资金分配表

**证据**

无 `@Version` 的实体清单（`grep -L "@Version"`）中包含：
- 资金分配表：`finance/payment/domain/entity/PaymentAllocation.java`、`finance/receipt/domain/entity/ReceiptAllocation.java`
- 单据子表：`purchase/order/domain/entity/PurchaseOrderItem.java`、`purchase/inbound/domain/entity/PurchaseInboundItem.java`、`sales/order/domain/entity/SalesOrderItem.java`、`sales/outbound/domain/entity/SalesOutboundItem.java`、`sales/returns/domain/entity/SalesReturnItem.java`、`logistics/bill/domain/entity/FreightBillItem.java`
- RBAC：`security/rbac/domain/entity/SysRole.java`、`SysUserRole.java`、`SysRolePermission.java`
- 附件：`attachment/domain/entity/AttachmentBinding.java`、`AttachmentFile.java`

已带 `@Version` 的 32 个包含 6 个核心聚合根（`V10__add_core_aggregate_versions.sql` 覆盖采购订单、采购入库、销售订单、销售出库、开票、收票）与主数据 + `QuoteSheet` + `CustomerStatement` / `FreightStatement`（`V129__add_master_data_aggregate_versions.sql`）。

**失效场景**：子表本身通过父聚合根的 `@Version` 间接保护（`V10` 的策略），因此 `PurchaseOrderItem` 等**已被父级覆盖**，不构成缺陷。真正的缺口在**没有父聚合根版本覆盖的独立表**：
1. `PaymentAllocation` / `ReceiptAllocation`：`Payment.java` / `Receipt.java` 有 `@Version`（`Receipt.java:33-34` 确认），分配写入路径 `PaymentAllocationService.java:101、:138、:169` 通过 `TradeItemCalculator.safeBigDecimal` 做额度校验后写分配行。若两个并发请求分别「新增分配」与「删除分配」，父聚合根版本会拦下其中一个（因此**不丢更新**），但**分配总额校验与父版本检查的先后顺序**决定了是否会返回 409 而非超分配——当前依赖 `sourceAllocationLockService.lockStatementSources`（`PaymentMutationGuardService.java:115`、`ReceiptService.java:416`）串行化，**该锁是正确性的实际保证**，`@Version` 只是兜底。
2. `SysUserRole` / `SysRolePermission`：`UserRoleService.replaceRoles`（`security/rbac/service/UserRoleService.java:83`）先 `deleteByUserId` 再 `saveAll`；两个并发「整体替换」会交错成「A 删 → B 删 → A 插 → B 插」，最终**两边写入的并集**而非任一方的意图。`RoleService.java:170-182` 的注释记录了这个坑的**另一种解法**（改成 diff 增量），但 `UserRoleService` **没有同步改造**。

**修复建议**：①`UserRoleService.replaceRoles` 改为与 `RoleService.replacePermissions` 一致的 diff 增量写法，或对 `userId` 加 `SELECT ... FOR UPDATE`（`UserAccountRepository` 已有 `findByIdAndDeletedFlagFalseForUpdate`，`V2UserAdminController`/`UserAdminService.java:150` 在用）；②为「独立于聚合根、且会被并发整体替换」的表补 `@Version`（优先 `SysUserRole`）；③补并发契约测试（两线程同时 replaceRoles，断言结果等于其中一个请求的完整意图）。

**代价**：低-中。

#### T-3（P2）`LOCK_ITEM_PARENT_SQL` 的子查询在 READ COMMITTED 下不加锁，父表锁定与子表锁定是两条独立语句，存在「锁住父表 X 后子表行被并发迁移到父表 Y」的窗口

**证据**

- rank 顺序**已核对一致，不是缺陷**（避免误报）：`SourceAllocationLockService.java:27-28` 注释称「跨方法的全局顺序取『表名字典序』，父表恰好先于对应子表」，实际 rank（`:34-44`）为 `FREIGHT_BILL=10` < `PURCHASE_INBOUND=20` < `PURCHASE_INBOUND_ITEM=21` < `PURCHASE_ORDER=30` < `PURCHASE_ORDER_ITEM=31` < `SALES_ORDER=40` < `SALES_ORDER_ITEM=41` < `SALES_OUTBOUND=50` < `SALES_OUTBOUND_ITEM=51` < `CUSTOMER_STATEMENT=60` < `FREIGHT_STATEMENT=61`，与表名 `lg_*` < `po_*` < `so_*` < `st_*` 的字典序完全吻合。唯一提示：rank 是手工维护常量，新增表时必须人工插入正确位置，**无编译期或测试期保护**（建议加一条测试断言 rank 唯一且与表名字典序单调一致）——这只是加固建议，不构成本节缺陷。
- 真正的结构性缺口：`LOCK_ITEM_PARENT_SQL`（`:68-78`）是
  ```sql
  SELECT source_parent.id FROM %s source_parent
  WHERE source_parent.id IN (SELECT source_item.%s FROM %s source_item WHERE source_item.id IN (:sourceIds))
  ORDER BY source_parent.id FOR UPDATE OF source_parent
  ```
  子查询**不加锁**（`:66` 注释也承认「子查询只用于取父表主键集合，不参与加锁」）。随后 `LOCK_ITEM_SQL`（`:56-62`）才锁子表。两者是**两条独立语句**。

**失效场景**：事务 T1 走 `lockTradeItemSources(poItemIds=[A], ...)`：先锁父 `po_purchase_order`（rank 30），再锁子 `po_purchase_order_item`（rank 31）。T2 在 T1 的两条语句之间修改了 `po_purchase_order_item.id=A` 的 `order_id`（把明细从订单 X 移到订单 Y）。T1 锁父表时读到的是 X（子查询在 T1 语句执行瞬间求值），锁定 X；随后锁子表拿到 A —— 此时 A 的 `order_id` 已变成 Y，**T1 持有的父锁是 X 而非 Y**。若 T2 同时持有 Y 的锁，T1 后续对 Y 的额度校验就缺少 Y 的行锁保护。在 PostgreSQL READ COMMITTED 下这是可发生的（子查询不加 `FOR UPDATE`，不阻止并发更新）。

实际触发需要「明细跨订单迁移」这一少见操作（`V153__release_soft_deleted_purchase_inbound_item_references.sql` 暗示存在此类迁移），且父锁 X 仍能拦住对 X 的并发写，故定级 P2。

**修复建议**：①把父表主键集合的获取与锁定合并为单条语句（`FOR UPDATE` 子查询中的 `source_item`），或先锁子表（`RANK_*_ITEM`）再锁父表 —— 但后者会破坏「父先子后」的全局序；②更简单：在父表锁定语句里对 **子表当前映射**加 `FOR SHARE`（`FOR UPDATE OF source_parent, source_item` 不适用于子查询，需改为 JOIN 形式并显式 `ORDER BY`，即回到被 `:21-25` 否定的方案）；③折中：`normalizeIds` 之后先对子表做一次无锁校验读取 `order_id` 集合，随后锁父表，再锁子表，最后**重新校验** `order_id` 未变，变了就抛 409 让上层重试。③的代价最低且不动全局序。

**代价**：低（③约 30 行 + 测试）；①/② 需重新评估死锁矩阵，代价高。

**对应标准**：[CWE-362 Race Condition](https://cwe.mitre.org/data/definitions/362.html)、[CWE-367 TOCTOU](https://cwe.mitre.org/data/definitions/367.html)、[PostgreSQL 显式锁定文档](https://www.postgresql.org/docs/current/explicit-locking.html)。

#### T-4（P3）`AfterCommitExecutor` 的 `afterCommit` 异常会向调用方传播，与「旁路写失败不应影响主流程」的注释意图不符

**证据**：`common/support/AfterCommitExecutor.java:18-23` 注册的 `afterCommit()` 内 `action.run()` **无 try/catch**。Spring 在 `afterCommit` 中抛出的异常会传播给 `commit()` 调用者，即事务已提交但方法抛异常。

**现状评估**：调用点自身大多包了 try/catch 或底层方法吞异常——`RedisJsonCacheSupport.delete`（`:90-100`）内部 `catch (RuntimeException)`；`PermissionCacheService.java:259` → `rotateEpoch`（内部有 `errorCounter` 降级）。**因此当前不构成实际故障**，仅为潜在隐患。

**修复建议**：在 `AfterCommitExecutor` 内 try/catch 并记录 `log.warn`，与 `touchSessionQuietly`（`JwtAuthenticationFilter.java:181-188`）的「尽力而为」模式一致。

**代价**：极低。

**对应标准**：[CWE-390 Detection of Error Condition Without Action](https://cwe.mitre.org/data/definitions/390.html)。

#### T-5（P3）`@Transactional` 与自调用：`SalesOrderService.completeSalesOrder` 的注释承认未纳入乐观锁重试，且删除路径亦然

**证据**：`sales/order/service/SalesOrderService.java:236-243` 注释「完成销售（专用子资源端点）。暂未纳入乐观锁重试」；`:246-258` 注释「软删除单据。未纳入乐观锁重试：删除端点不在压测暴露的冲突面内」。`completeSalesOrder` 内用 `findForUpdateByIdAndDeletedFlagFalse`（`:239-240`）以悲观锁替代，**实现上是安全的**。

**失效场景**：无实际风险，属技术债记录：`updateStatus`（`:218-224`）走 `OptimisticLockRetryExecutor` 重试、`completeSalesOrder` 走悲观锁，两条路径的并发语义不同，客户端无法统一预期（一个可能返回 409，一个会阻塞）。

**修复建议**：统一为悲观锁 + 有界等待超时，或统一为乐观重试；在 OpenAPI 中明确各端点的并发契约。

**代价**：低。

---

## 6. 金额精度与数值一致性

### 6.1 现状核对（正面）

| 检查项 | 结论 | 证据 |
| --- | --- | --- |
| `new BigDecimal(double)` 误用 | **0 处**。全部 18 处 `new BigDecimal(...)` 中，17 处入参为 `String`（`raw.trim()` / `value.trim()` / `matcher.group(2)`），1 处为 `BigInteger`（`ExcelExportService.java:169`） | 无 `double`/`float` 构造。这是最容易被忽视的精度杀手，当前**干净** |
| `doubleValue()` 使用点 | 仅 6 处，全部在**导出/渲染**边界（`ExcelExportService.java:171、:192、:225`、`SalesOrderPrintExportService.java:391`、`PrintRecordEnricher.java:149`） | 见 M-1（唯一有问题的一处） |
| `setScale` 舍入模式 | 全部显式传 `RoundingMode`，无裸 `setScale(n)`（会抛 `ArithmeticException`） | `PrecisionConstants.java:22` 统一 `HALF_UP`；**唯一例外** `SalesOrderPrintExportService.java:147` `targetSetup.setScale(sourceSetup.getScale())` 是 `BigDecimal.setScale(int)` 用于**复制 scale**，非数值舍入，语义正确 |
| `BigDecimal.equals` 误用 | **0 处**。金额/重量比较统一 `compareTo`（全仓库 59 处 `compareTo`） | 专用辅助 `SourceLineDiffSupport.java:74-80` `sameNumber` 用 `compareTo(left, right) == 0`，正确处理 `null` |
| 金额/重量 scale 一致性 | 内部 8 位（吨）、2 位（元），DB `numeric(18,8)` / `numeric(14,2)` | `PrecisionConstants.java:13-19`；`V1__baseline.sql:129-130、:160-164` |
| 数据库精度匹配 | `unit_cost numeric(12,2)`、`amount numeric(14,2)` 与服务层 `COST_SCALE = 2` 一致 | `V136__add_inventory_transaction.sql:12-13`；`InventoryTransactionService.java:40` |
| 移动加权成本残值处理 | 「恰好清零」时金额取余额相反数，保证 `Σ账本 == 快照` | `InventoryTransactionService.java:255-272`（含 `:264-268` 的特判） |
| 中文大写金额 | 精确到分，`HALF_UP`，`RoundingMode.DOWN` 取整数元 | `ChineseAmountFormatter.java:26-31` |
| 雪花 ID 跨端 | 全局 `Long → String` | `JacksonConfig.java:44-45`（见 §0.3） |

### 6.2 P2

#### M-1（P2）`PrintRecordEnricher.toAmount` 把任意 `Number` 经 `doubleValue()` 转 `BigDecimal`，`BigDecimal` 实例本身直接返回不做 scale 规范化，导致打印金额可能带 `double` 尾差或超长小数

**证据**

- `system/printtemplate/service/PrintRecordEnricher.java:140-156`：
  ```java
  private BigDecimal toAmount(Object value) {
      if (value == null) return BigDecimal.ZERO;
      if (value instanceof BigDecimal decimal) return decimal;              // ← 原样返回，不 setScale
      if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());  // ← double 中转
      try { return new BigDecimal(String.valueOf(value).trim()); } catch (NumberFormatException ignored) { return BigDecimal.ZERO; }
  }
  ```
- `:149` 的 `BigDecimal.valueOf(number.doubleValue())`：`BigDecimal.valueOf(double)` 用 `Double.toString` 转换，对 `long`/`Integer` 输入是精确的；但若 `value` 是 `Double`/`Float`（例如 JDBC 驱动、JSON 反序列化产生），会引入二进制浮点表示误差。
- 与正确实现对照：`PrintScriptService.java:252-259` 的 `plainAmount` 会 `setScale(AMOUNT_SCALE, HALF_UP).toPlainString()`；`PrintRecordFieldFormatter.java:68` 走 `new BigDecimal(String)`。

**失效场景**：打印模板的表达式规则（`arguments(data, rule)`，`:158+`）对字段做算术时，若字段来源是 `Double`（例如外部行情 JSON、`Map<String,Object>` 反序列化路径），`toAmount` 会得到 `0.1 + 0.2 = 0.30000000000000004` 这类值。若该值参与金额汇总后未在最终渲染处 `setScale`，打印件上会出现 17 位小数金额，或「合计 ≠ 各行之和」（差 1 分）。

**现状缓解**：最终渲染路径大多经 `plainAmount`（`PrintScriptService.java:252-259`）或 `ChineseAmountFormatter`（`:26`）做了 2 位 `HALF_UP`，**故实际输出通常正确**。定级 P2 因为它是**唯一未经 scale 规范化的金额入口**，且 `catch (NumberFormatException ignored) return BigDecimal.ZERO`（`:153-155`）会把「无法解析的金额」静默变成 0——在打印件上表现为金额凭空归零，比抛异常更危险。

**修复建议**：①`toAmount` 对 `Number` 分支改用 `new BigDecimal(String.valueOf(number))`（避免 double 中转）；②`BigDecimal` 分支补 `setScale(AMOUNT_SCALE, DEFAULT_ROUNDING)`，或在返回值统一规范化；③解析失败改为抛 `BusinessException`（打印件不得出现静默归零的金额），至少记录 WARN 并携带字段名。

**代价**：低（约 15 行 + 打印回归测试）。

**对应标准**：[CWE-681 Incorrect Conversion between Numeric Types](https://cwe.mitre.org/data/definitions/681.html)、[CWE-704 Incorrect Type Conversion or Cast](https://cwe.mitre.org/data/definitions/704.html)、[CWE-1339 Insufficient Precision or Accuracy of a Real Number](https://cwe.mitre.org/data/definitions/1339.html)。

### 6.3 P3

#### M-2（P3）`InventoryTransactionService` 的 `COST_SCALE = 2` 是本地常量，未复用 `PrecisionConstants`

**证据**：`inventory/service/InventoryTransactionService.java:40` `private static final int COST_SCALE = 2;`，而 `PrecisionConstants.java:19` 已有 `AMOUNT_SCALE = 2`；同一文件 `:272`、`:290-291` 直接写 `RoundingMode.HALF_UP` 而非 `PrecisionConstants.DEFAULT_ROUNDING`。

**失效场景**：违反仓库「同一业务口径只在一处实现」（`AGENTS.md`）与 P3C「禁止魔法值」；若未来 `AMOUNT_SCALE` 调整为 3，库存成本仍是 2 位，出现口径分叉。

**修复建议**：替换为 `PrecisionConstants.AMOUNT_SCALE` / `DEFAULT_ROUNDING`。

**代价**：极低。

#### M-3（P3）`ExcelExportService.writeDecimal` 的「可精确表示则写数值、否则写文本」策略会让同一列出现混合单元格类型

**证据**：`common/excel/service/ExcelExportService.java:190-197`：
```java
if (BigDecimal.valueOf(value.doubleValue()).compareTo(value) == 0) { cell.setCellValue(value.doubleValue()); return; }
cell.setCellValue(value.toPlainString());
```

**失效场景**：导出列中大部分金额写为数值单元格、个别写为文本单元格，Excel 中「求和」会漏掉文本单元格，用户看到「合计对不上」而不知道原因。这是**有意取舍**（`:195` 注释「宁可保留精度也不隐式舍入」），正确的做法是整列统一。

**修复建议**：按列决策而非按值决策——先扫描该列所有值能否精确表示为 double，全部可以则整列写数值，否则整列写文本。

**代价**：低-中（需两遍扫描或先收集再写）。

---

## 7. 审计日志与敏感信息泄露

### 7.1 现状核对（正面）

| 检查项 | 结论 | 证据 |
| --- | --- | --- |
| 日志打印 token | **未发现**。`JwtAuthenticationFilter.java:124-130` 只打印 `ex.getClass().getSimpleName()` 与 `ex.getMessage()`，**不打印 token 本体**；`JwtTokenService` 全文无日志 | — |
| 日志打印密码 | **未发现**。`LoginService`、`UserAdminService`、`UserAccountService` 均无打印口令或哈希的语句 | — |
| 实体 `toString()` 泄露 | 无风险。`@Entity` + `@Data` **0 处**（统一 `@Getter/@Setter`），无自动 `toString` 输出全字段 | §0.3 |
| 日志脱敏配置 | `application.yml:672-675` `configprops.show-values: never`（Spring Boot 3.4+ 默认，显式固定），避免 actuator 暴露连接串与密钥 | — |
| 操作日志自动覆盖写操作 | `OperationLogMetadataResolver.java:19-36`：无显式注解时按 HTTP 方法自动生成「新增/编辑/删除」记录（`:38-46`），写操作**默认留痕** | 见 L-1 的覆盖度 |
| 批量写入审计字段自动填充 | `common/persistence/JpaAuditConfig.java:19` 从 `SecurityContext` 取当前主体写入 `created_by/updated_by` | — |

### 7.2 P2

#### L-1（P2）操作日志在「无显式 `@OperationLoggable` / `@DomainEventAudited`」时只记录方法名级信息，且**失败原因被固定文案抹平**，无法用于安全事件溯源

**证据**

- `system/operationlog/support/OperationLogMetadataResolver.java:28-36`：自动模式的 `ActionLog` 只有 `moduleName / actionType / businessNoFields=[] / recordIdField="" / moduleKeyField=""` —— **不记录业务单号、记录 ID、模块键**。
- `OperationLogResultCollector.java:140-142`：
  ```java
  public String resolveRemark(Exception ex) {
      return ex == null ? null : "请求处理失败";     // ← 所有异常都被抹成同一句
  }
  ```
- 覆盖度：`@OperationLoggable` **18 处**、`@DomainEventAudited` **37 处**，合计 55 处；而写端点 **144 个**。即约 **89 个写端点**走自动模式，只留「模块名 + 动作 + HTTP 方法 + path + IP + 结果状态」。

**失效场景**

1. 安全事件溯源时需要回答「谁在何时改了哪条单据」，自动模式**没有 `recordId`**（`recordIdField` 为空字符串），只能靠 `requestPath` 里的路径 ID 回填——而 `OperationLogResultCollector.java:56-64` 只在 `businessNoFields` 未命中时才从 URI 模板取 `id`，自动模式下确实会取到 `id`，**但仅当路径形如 `/{id}`**；`PATCH /{id}/status`、`POST /{id}/force-closures` 等子资源路径的 `id` 能取到，而 `POST /sales-orders`（创建）则完全没有记录 ID。
2. 更严重的是**失败原因不可区分**：权限拒绝（403）、乐观锁冲突（409）、校验失败（422）、服务端异常（500）在日志里全是「请求处理失败」。入侵检测无法区分「攻击者尝试越权 100 次」与「用户连续输错表单 100 次」——这正是 [OWASP A09:2021](https://owasp.org/Top10/A09_2021-Security_Logging_and_Monitoring_Failures/) 描述的失效模式。

**修复建议**：①`resolveRemark` 改为携带 `ErrorCode` + 异常类名（不含堆栈、不含入参）：`"请求处理失败: " + code + "/" + ex.getClass().getSimpleName()`；②自动模式的 `recordIdField` 默认填 `"id"`，并在 `@OperationLoggable` 覆盖时优先；③为剩余 89 个写端点补齐 `@OperationLoggable`（其中创建类端点必须补 `businessNoFields`）。

**代价**：①极低（1 个方法）；②低；③中等（89 个端点逐一标注，可分批）。

**对应标准**：[OWASP Top 10 A09:2021 Security Logging and Monitoring Failures](https://owasp.org/Top10/A09_2021-Security_Logging_and_Monitoring_Failures/)、[CWE-778 Insufficient Logging](https://cwe.mitre.org/data/definitions/778.html)、[CWE-223 Omission of Security-relevant Information](https://cwe.mitre.org/data/definitions/223.html)。

#### L-2（P2）操作日志仅在 `afterCompletion` 写库，且写入失败静默降级为 WARN——高危操作可能「执行成功但无审计」

**证据**

- `system/operationlog/support/OperationLogInterceptor.java:38-46`：`afterCompletion` 中调 `commandRecorder.record(...)`。
- `OperationLogCommandRecorder.java:50-52`：
  ```java
  } catch (Exception logEx) {
      log.warn("操作日志写入失败: {} {}", request.getMethod(), request.getRequestURI(), logEx);
  }
  ```
  审计写入失败**只留一行 WARN，业务照常成功**。
- `OperationLogService.java:96-98`：`@Transactional(propagation = REQUIRES_NEW)`，与业务事务解耦（这是正确的——审计失败不应回滚业务）。

**失效场景**：数据库连接池耗尽或 `sys_operation_log` 分区表写满时，所有审计写入失败但业务全部成功。由于失败只进应用日志，**运维不会收到告警**，审计表出现静默空洞。对于 P0 级的资金/库存写操作（审核、反审核、强制结单、删除），这意味着「操作发生了但无法证明」。

**修复建议**：①审计写入失败递增 Micrometer counter 并接入告警（仓库已有 `system/operationlog/metrics/ReliableEventMetrics.java` 的 `event_publication` 积压指标可参照）；②`@DomainEventAudited` 路径走的 `BusinessOperationAuditListener` 是 `@ApplicationModuleListener`（`event_publication` 表 + 重投递，`V79__add_reliable_business_operation_events.sql:22-31`），**这条路径是可靠的**——建议把高危写操作从拦截器路径迁到事件路径，拦截器路径只用于普通 CRUD。

**代价**：中。①低；②需把 37 处 `@DomainEventAudited` 扩展到资金/库存关键端点（这些端点多数已有领域事件，成本可控）。

**对应标准**：[OWASP Top 10 A09:2021](https://owasp.org/Top10/A09_2021-Security_Logging_and_Monitoring_Failures/)、[CWE-778](https://cwe.mitre.org/data/definitions/778.html)。

### 7.3 P3

#### L-3（P3）`OperationLogRequestFilter` 用 `ContentCachingRequestWrapper` 全量缓存**所有**请求体，包含 20MB 附件上传，与会话撤销/日志目的无关

**证据**：`system/operationlog/support/OperationLogRequestFilter.java:14-28`：`@Order(Ordered.HIGHEST_PRECEDENCE)`，对每个请求无条件 `new ContentCachingRequestWrapper(request)`。

**现状评估**：`ContentCachingRequestWrapper` 只在 `getInputStream` 被读取时缓存，且 `OperationLogResultCollector.parseRequestBody` 仅在需要 `businessNoFields` 时才解析（`:46`）。multipart 请求不被解析（`parseRequestBody` 有类型检查）。**因此当前不造成 20MB 驻留**，但 wrapper 本身对所有请求生效，属于无必要的全局开销。

**修复建议**：`shouldNotFilter` 排除 `multipart/*` 与 `application/octet-stream`。

**代价**：极低。

---

## 8. 配置、部署与纵深防御

### 8.1 P2

#### D-1（P2）Actuator 在 prod 暴露 `loggers` + `configprops`，且管理端口虽绑 `127.0.0.1` 但**无认证**

**证据**

- `application-prod.yml:50-56`：
  ```yaml
  management:
    server:
      address: ${LEO_MANAGEMENT_SERVER_ADDRESS:127.0.0.1}
      port: ${LEO_MANAGEMENT_SERVER_PORT:57218}
    endpoints:
      web:
        exposure:
          include: ${LEO_MANAGEMENT_ENDPOINTS:health,prometheus,loggers,configprops}
  ```
- `application.yml:670-680` 默认同样暴露 `health,loggers,configprops`。
- **无 `spring.security.user` / 无 actuator 专属 SecurityFilterChain**：`SecurityConfig.java:46-114` 只定义了一条 `SecurityFilterChain`，其 `authorizeHttpRequests`（`:90-105`）对 `/actuator/**` 未做任何特殊处理 —— 但因为 actuator 走**独立端口**，这条链不作用于管理端口。管理端口因此**没有任何认证**。
- `configprops.show-values: never`（`application.yml:673-675`）是唯一的缓解。

**失效场景**

1. `POST /actuator/loggers/com.leo.erp` body `{"configuredLevel":"TRACE"}` 可运行时把任意包调到 TRACE，配合 §L-1 的日志内容，可在日志中放大敏感信息（虽然当前日志不含 token/密码，但 TRACE 会输出 SQL 参数与实体字段）。
2. `/actuator/configprops` 虽 `show-values: never`，但仍暴露所有配置**属性名**与结构，为攻击者提供内部拓扑（数据源类型、Redis 配置项、S3 endpoint 属性名、限流参数名）。若某次排障按注释 `:673-674` 把 `LEO_CONFIGPROPS_SHOW_VALUES` 设为 `always` 后忘记改回，则连接串与密钥**全部明文可读**。
3. 前提是攻击者能在宿主机访问 `127.0.0.1:57218`。若存在 SSRF（当前未发现明显 SSRF，见下方备注）或同机其他进程/容器共享网络命名空间，则可触达。

**备注（SSRF 排查结论）**：`market/steelx/SteelxProperties.java:177-190` 的 URL 由 `regionMap().get(region)` 的 slug 拼装，slug 受 `QuoteRegionRequest.java:23` `@Pattern("^[a-z0-9-]+$")` 与 `V187__add_quote_region.sql:27` 的 CHECK 约束双重限制，**用户不能注入任意 host/path**；`MysteelProperties.listUrl` 是配置项不是请求参数。因此**未发现可利用 SSRF**，这降低了 D-1 的可达性，故定级 P2。

**修复建议**：①actuator 端口增加 `NetworkPolicy`/防火墙限制到仅监控进程可访问，并在部署文档中固化；②`LEO_MANAGEMENT_ENDPOINTS` 的 prod 默认值移除 `loggers`、`configprops`（排障时临时开启）；③为管理端口增加一个独立的、带 Basic Auth 的 `SecurityFilterChain`（`@Order` 高于主链，`securityMatcher` 限定 management port）。

**代价**：①低（部署层）；②低（配置）；③中（需引入 management 专属链 + 凭据管理）。

**对应标准**：[OWASP Top 10 A05:2021 Security Misconfiguration](https://owasp.org/Top10/A05_2021-Security_Misconfiguration/)、[OWASP API Security API8:2023 Security Misconfiguration](https://api-security.owasp.org/editions/2023/en/0xa8-security-misconfiguration/)、[CWE-306 Missing Authentication for Critical Function](https://cwe.mitre.org/data/definitions/306.html)、[CWE-16 Configuration](https://cwe.mitre.org/data/definitions/16.html)。

#### D-2（P2）「状态写入唯一出口」的守卫在代码中**不存在**，但实体与服务层注释仍宣称存在——一致性约束只靠约定，无强制

**证据**

- `market/region/domain/entity/MdQuoteRegion.java:19-21` 与 `market/lengthpremium/domain/entity/MdLengthPremium.java:19-21` 注释：
  > 「状态字段是单据状态门禁（`ModuleBoundaryVerifier`）管辖的写入口：实现 `StatusAwareEntity` 后由 `CrudStatusGuard#writeStatus` 唯一写入，服务层不得直接 `setStatus`，避免绕过守卫的旁路写。」
- `market/region/service/QuoteRegionService.java:42-46`、`market/lengthpremium/service/LengthPremiumService.java:40-44` 同样宣称：「服务层直接 `setStatus` 会被 `ModuleBoundaryVerifier` 判为旁路写」。
- **`ModuleBoundaryVerifier` 在全仓库不存在**：`grep -rn "ModuleBoundaryVerifier" --include=*.java src/` 仅返回上述 4 处**注释**，无任何类定义或测试；`find src -name "*ModuleBoundary*" -o -name "*BoundaryVerifier*"` 返回空；`src/test` 下无 ArchUnit/架构测试目录。
- 实体确实**显式**实现了 `setStatus`（`MdQuoteRegion.java:85-87`、`MdLengthPremium.java:84-86`），并且 `StatusAwareEntity.java:7` 的接口方法本身可被任意调用者直接调用。
- 服务层当前**正确**使用了守卫（`QuoteRegionService.java:90、:113` 与 `LengthPremiumService.java:83、:103` 都是 `STATUS_GUARD.writeStatus(...)`）。

**失效场景**：当前无运行时缺陷（实现是对的），但**防护是零成本的**——任何人新增一个 service 方法直接 `entity.setStatus("停用")`，编译通过、测试通过、CI 通过，没有任何机制会拦住。而注释会让评审者误以为「有架构测试保护」从而跳过人工核对。这是一个**虚假的安全感**问题，与 §L-1 的审计覆盖不足叠加后，旁路写状态将既无守卫也无审计。

**修复建议**：①真正落地 `ModuleBoundaryVerifier`（ArchUnit `noClasses().that().resideInAPackage("..service..").should().callMethod(...setStatus)`），或②在 `StatusAwareEntity.setStatus` 上加 `@Deprecated` 并让 `CrudStatusGuard.writeStatus` 走一个内部专用的 setter，实体对外只暴露 `getStatus()`；③若两者都不做，**删除这 4 处注释中的虚假承诺**，改为「约定，无强制」。

**代价**：①低（一个 ArchUnit 测试，需引入 archunit 依赖，`pom.xml` 已有 `archunit-boundary-store` 配置目录可复用）；②低；③极低。

**对应标准**：[CWE-636 Not Failing Securely](https://cwe.mitre.org/data/definitions/636.html)、仓库规范 `AGENTS.md`《业务口径只在一处实现》。

### 8.2 P3

#### D-3（P3）`SecurityKeyStartupValidator` 仅校验密钥可读，不校验「已退役密钥是否仍有历史数据依赖」

**证据**：`security/config/SecurityKeyStartupValidator.java:17-21` 只调 `getActiveJwtMaterial()` 与 `getActiveDataMaterial()`。

**失效场景**：与 A-1 联动——轮转时若把旧 `DATA_MASTER` 的 `deleted_flag` 置 true，启动不报错，但历史加密附件已经解不开了。启动期是发现这个问题的最佳时机（可以 fail-fast 阻止发布）。

**修复建议**：启动校验增加「若存在 `encryption_key_version` 引用但对应密钥不可用，则启动失败并列出受影响的附件数量」。

**代价**：低（依赖 A-1 的版本列先落地）。

---

## 9. 修复路线图

### 9.1 第 0 批：阻断发布（P0，1 项）

| 编号 | 内容 | 关键改动 | 验收 |
| --- | --- | --- | --- |
| **S-1** | 恢复打印/打印预览的记录级数据范围校验 | 优先在 `PrintRecordDataProvider.loadRecord`/`listPrintItems` 的 SQL 追加 owner 谓词；兜底在 `PrintOutputService.generateFromRecord` 与 `PrintScriptService.pagePrintItems` 入口加 guard | 按 `docs/2026-07-10-backend-code-review.md:466-472` 重建：多主体 × 两个入口，越权固定 403，断言模板加载与文件生成未执行 |

### 9.2 第 1 批：最近修复批次（P1，6 项）

| 编号 | 内容 | 依赖 |
| --- | --- | --- |
| **C-1** | 消除 `SecurityPrincipal.system()` 在 HTTP 线程的回落；`StatusTransitionPermissionGuard` 无主体时改拒绝 | 无 |
| **S-2** | `RecordExistencePort` 增加按主体的可访问性判定；统一已绑定/未绑定附件语义 | 需各模块提供可见性端口（可先做 sales-order） |
| **S-3** | 横向检索（global-search / document-flow / print-previews / module-exports）引入查询侧数据范围 | 与 S-1 共用同一套 owner 解析 |
| **A-1** | 附件加密密钥版本化（新迁移 + `getDataMaterial(version)` + magic 扩展） | 需在 `encrypted-storage` 打开前完成 |
| **R-1** | `limit-write-methods` 默认开启；导出/打印/导入权重化限流；`/account/password` 接入失败计数 | 无（权重化需改 `RateLimitCheck` + Lua） |
| **T-1** | 清单导出与行情同步的远程 I/O 移出事务 | 无（行情异步化可延后） |

**建议实施顺序**：S-1 → A-1 → C-1 → R-1 → T-1 → S-2 → S-3（S-1 与 S-2/S-3 共享 owner 解析抽象，先做 S-1 可复用到后两者；R-1 中的「写方法默认开启」是独立的一行配置，可随时先合）。

### 9.3 第 2 批：P2（17 项）

| 主题 | 编号 | 一句话 |
| --- | --- | --- |
| 认证 | C-2、C-3 | 黑名单单次原子读；Cookie 示例值与 Origin 校验 |
| 授权 | S-4、S-5、S-6、S-8 | 角色权限子集约束；重写 `security-trust-model.md`；`@SelfScoped` 豁免注解 + 改密限流；历史审计表加回退标注与最后复核列 |
| 附件 | A-2、A-3 | 真实类型嗅探；清单脱敏 + 流式 |
| 限流 | R-2、R-3 | 预览下推分页 + 纳入并发闸门；幂等缓存体积上限与超大 multipart 语义 |
| 事务并发 | T-2、T-3 | `replaceRoles` diff 化；父锁后校验子表映射 |
| 精度 | M-1 | `toAmount` 去 double 中转 + 解析失败不归零 |
| 可观测 | L-1、L-2 | 审计 remark 带错误码；审计写失败告警 + 关键端点迁事件路径 |
| 配置 | D-1、D-2 | actuator 收敛 + 认证；落地或删除 `ModuleBoundaryVerifier` 承诺 |

### 9.4 第 3 批：P3（10 项）

| 编号 | 一句话 |
| --- | --- |
| C-4 | `JwtProperties` 补 `@Size`/`@Max` |
| S-7 | `资源:*` 不覆盖字段级三段码 |
| A-4 | 附件本地目录改 `/var/lib` + 生产默认加密 |
| R-4 | 限流 fail-open 加指标告警 |
| T-4 | `AfterCommitExecutor` 内部 try/catch |
| T-5 | 统一 `completeSalesOrder` 与 `updateStatus` 的并发契约 |
| M-2 | `COST_SCALE` 复用 `PrecisionConstants` |
| M-3 | Excel 导出按列统一单元格类型 |
| L-3 | `OperationLogRequestFilter` 排除 multipart |
| D-3 | 启动校验密钥版本完整性 |

### 9.5 门禁建议（防止回归）

1. **架构测试（ArchUnit）**
   - `web` 包不得引用 `SecurityPrincipal.system()`（对应 C-1）。
   - `@Transactional` 方法体内不得调用 `storageResolver.*` / `HttpClient.send` / `ProcessBuilder.start`（对应 T-1）。
   - `service` 包不得直接调用 `StatusAwareEntity.setStatus`（对应 D-2）。
   - 无 `@RequirePermission` 的端点必须带 `@PublicAccess` 或 `@SelfScoped`（对应 S-6）。
2. **契约测试**
   - 打印/预览两入口的越权 403 矩阵（S-1，长期保留）。
   - 附件加密「加密 → 轮转 → 解密」往返（A-1）。
   - 并发 `replaceRoles` 两线程结果等于其中一方完整意图（T-2）。
3. **CI 静态检查**
   - `new BigDecimal(` 后紧跟 `double`/`float` 变量 → 编译期/Checkstyle 违规（当前 0 处，锁死这个状态）。
   - `catch (Exception` 且 catch 块内无 `throw` 且无指标上报 → 告警（当前 18 处 `catch (Exception`，其中 9 处紧邻 `log.error/warn`，需逐个确认非吞异常）。

---

## 10. 结论

1. **唯一 P0 是历史 High-01 的回归，且属「能力被移除 → RBAC 重建时未恢复」而非单纯漏实现**：`ResourceRecordAccessGuard` 在 `49c71e59`（2026-07-18 移除 RBAC）被删除，此后 `deb03350`（2026-09-14）与 `73369334`（2026-09-15）重建了 RBAC0，但**只重建了功能级端点权限，记录级守卫没有随之恢复**（全仓库 `ResourceRecordAccessGuard`/`assertCurrentUserCanAccess`/`DataScope` 均 0 命中）。结果是打印与打印预览两个入口对任意他人订单无对象级授权。这是本次审计最需要立即处置的项，且修复应「从 `49c71e59^` 取回原 guard 并适配新语义」，而非二次设计。
2. **认证与密钥管理的「基础件」质量明显高于「外围一致性」**：JWT 密钥无硬编码、fail-closed 启动、credential_version 链路、Refresh Token 摘要存储、Cookie 属性、S3 直传校验、路径穿越防护、来源锁顺序、库存守恒、乐观锁 409 映射——这些都经得起逐行核对，且历史 High-02/03/05/06/07 的修复确实落地。
3. **主要风险集中在「文档与代码漂移」与「纵深防御缺失」两类**：`security-trust-model.md` 仍描述单账号模型（实际已多用户 RBAC0，S-5）、历史审计表的 High-01 仍标「已修复」（S-8）、`ModuleBoundaryVerifier` 只存在于注释（D-2）、`sys_rate_limit_rule` 是死表（R-1）、限流覆盖写方法默认关闭（R-1）。这类问题的共同特征是**看起来有防护，实际没有强制力**，比明确的漏洞更难在评审中发现；S-1 的 P0 正是经由这条路径被历史审计表掩盖了三个月。
4. **数据一致性的核心机制（来源锁 + 库存维度锁 + AFTER_COMMIT + 可靠事件）设计正确**；剩余风险是边界细节：`LOCK_ITEM_PARENT_SQL` 的子查询不加锁（T-3）、`replaceRoles` 未 diff 化（T-2）、44 个无版本实体中仅少数是真实缺口（T-2 已区分）。金额精度方面 `new BigDecimal(double)` 为 0 处、`BigDecimal.equals` 误用为 0 处，这是本次审计的显著正面结论；唯一需修的是 `PrintRecordEnricher.toAmount` 的 double 中转与静默归零（M-1）。

---

## 附录 A：引用来源

**OWASP 标准**

- [OWASP Top 10（2021 与 2025 版入口）](https://owasp.org/Top10/)
- [A01:2021 Broken Access Control](https://owasp.org/Top10/A01_2021-Broken_Access_Control/)
- [A02:2021 Cryptographic Failures](https://owasp.org/Top10/A02_2021-Cryptographic_Failures/)
- [A04:2021 Insecure Design](https://owasp.org/Top10/A04_2021-Insecure_Design/)
- [A05:2021 Security Misconfiguration](https://owasp.org/Top10/A05_2021-Security_Misconfiguration/)
- [A09:2021 Security Logging and Monitoring Failures](https://owasp.org/Top10/A09_2021-Security_Logging_and_Monitoring_Failures/)
- [OWASP API Security Top 10 2023 — 总览](https://api-security.owasp.org/editions/2023/en/0x11-t10/)
- [API1:2023 Broken Object Level Authorization](https://api-security.owasp.org/editions/2023/en/0xa1-broken-object-level-authorization/)
- [API2:2023 Broken Authentication](https://api-security.owasp.org/editions/2023/en/0xa2-broken-authentication/)
- [API3:2023 Broken Object Property Level Authorization](https://api-security.owasp.org/editions/2023/en/0xa3-broken-object-property-level-authorization/)
- [API4:2023 Unrestricted Resource Consumption](https://api-security.owasp.org/editions/2023/en/0xa4-unrestricted-resource-consumption/)
- [API5:2023 Broken Function Level Authorization](https://api-security.owasp.org/editions/2023/en/0xa5-broken-function-level-authorization/)
- [API6:2023 Unrestricted Access to Sensitive Business Flows](https://api-security.owasp.org/editions/2023/en/0xa6-unrestricted-access-to-sensitive-business-flows/)
- [API8:2023 Security Misconfiguration](https://api-security.owasp.org/editions/2023/en/0xa8-security-misconfiguration/)
- [OWASP ASVS（v5.0.0）](https://owasp.org/projects/asvs)

**OWASP Cheat Sheets**

- [Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html)
- [Insecure Direct Object Reference Prevention](https://cheatsheetseries.owasp.org/cheatsheets/Insecure_Direct_Object_Reference_Prevention_Cheat_Sheet.html)
- [Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- [Cryptographic Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html)
- [File Upload Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html)
- [Transaction Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Transaction_Authorization_Cheat_Sheet.html)
- [Cross-Site Request Forgery Prevention](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)

**CWE**

- [CWE-16 Configuration](https://cwe.mitre.org/data/definitions/16.html)
- [CWE-22 Path Traversal](https://cwe.mitre.org/data/definitions/22.html)
- [CWE-200 Exposure of Sensitive Information](https://cwe.mitre.org/data/definitions/200.html)
- [CWE-223 Omission of Security-relevant Information](https://cwe.mitre.org/data/definitions/223.html)
- [CWE-269 Improper Privilege Management](https://cwe.mitre.org/data/definitions/269.html)
- [CWE-306 Missing Authentication for Critical Function](https://cwe.mitre.org/data/definitions/306.html)
- [CWE-320 Key Management Errors](https://cwe.mitre.org/data/definitions/320.html)
- [CWE-352 Cross-Site Request Forgery](https://cwe.mitre.org/data/definitions/352.html)
- [CWE-362 Race Condition](https://cwe.mitre.org/data/definitions/362.html)
- [CWE-367 TOCTOU](https://cwe.mitre.org/data/definitions/367.html)
- [CWE-390 Detection of Error Condition Without Action](https://cwe.mitre.org/data/definitions/390.html)
- [CWE-400 Uncontrolled Resource Consumption](https://cwe.mitre.org/data/definitions/400.html)
- [CWE-434 Unrestricted Upload of Dangerous File Type](https://cwe.mitre.org/data/definitions/434.html)
- [CWE-639 Authorization Bypass Through User-Controlled Key](https://cwe.mitre.org/data/definitions/639.html)
- [CWE-681 Incorrect Conversion between Numeric Types](https://cwe.mitre.org/data/definitions/681.html)
- [CWE-704 Incorrect Type Conversion or Cast](https://cwe.mitre.org/data/definitions/704.html)
- [CWE-770 Allocation of Resources Without Limits](https://cwe.mitre.org/data/definitions/770.html)
- [CWE-778 Insufficient Logging](https://cwe.mitre.org/data/definitions/778.html)
- [CWE-798 Use of Hard-coded Credentials](https://cwe.mitre.org/data/definitions/798.html)
- [CWE-863 Incorrect Authorization](https://cwe.mitre.org/data/definitions/863.html)
- [CWE-1339 Insufficient Precision or Accuracy of a Real Number](https://cwe.mitre.org/data/definitions/1339.html)

**框架与数据库官方文档**

- [Spring Security — Authorize HTTP Requests](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html)
- [Spring Security — CSRF](https://docs.spring.io/spring-security/reference/features/exploits/csrf.html)
- [Spring Security — Password Storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)
- [Spring Security — OAuth2 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Spring Framework — Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/transaction.html)
- [PostgreSQL — Explicit Locking](https://www.postgresql.org/docs/current/explicit-locking.html)

**仓库内部文档**

- [`docs/security-trust-model.md`](../security-trust-model.md)
- [`docs/2026-07-10-backend-code-review.md`](../2026-07-10-backend-code-review.md)
- [`AGENTS.md`](../../../AGENTS.md)《强制 RESTful API 规范》《业务口径只在一处实现》

---

## 附录 B：审计方法与可复现命令

本次审计全程只读，未修改任何业务代码/配置、未连接任何数据库、未对任何环境发起请求。关键统计命令：

```bash
# 安全模块文件数
find src/main/java -path "*security*" -name "*.java" | wc -l          # 54

# 权限注解覆盖
grep -rn "@RequirePermission" --include=*.java src/main/java | wc -l  # 248
grep -rn "@PreAuthorize" --include=*.java src/main/java | wc -l       # 1
grep -rn "@PublicAccess" --include=*.java src/main/java              # 5 个类

# 事务与版本
grep -rn "@Transactional" --include=*.java src/main/java | wc -l      # 419
grep -rln "@Version" --include=*.java src/main/java | wc -l           # 32（@Entity 共 76）

# 精度反模式（期望 0）
grep -rn "new BigDecimal(" --include=*.java src/main/java | grep -i "double\|float"   # 空
grep -rn "printStackTrace" --include=*.java src/main/java | wc -l     # 0
grep -rn "TODO\|FIXME\|HACK" --include=*.java src/main/java | wc -l   # 0

# 审计注解覆盖
grep -rn "@OperationLoggable" --include=*.java src/main/java | wc -l  # 18
grep -rn "@DomainEventAudited" --include=*.java src/main/java | wc -l # 37

# 死表确认
grep -rn "sys_rate_limit_rule\|RateLimitRule" --include=*.java src/main/java   # 空

# 不存在的守卫确认
grep -rn "ModuleBoundaryVerifier" --include=*.java src/             # 仅 4 处注释
```

### 审计局限（需在后续轮次补齐）

1. **未执行任何动态测试**：本文所有结论均来自静态阅读，未验证运行时行为；S-1 的越权路径建议由安全测试同事在隔离环境构造只读复现（不要对共享环境执行）。
2. **未逐条核对 419 处 `@Transactional`**：按「是否含远程/文件 IO」与「是否跨多个聚合写」抽样，重点覆盖了附件、行情同步、库存、来源锁四处；其余以架构测试建议（§9.5）代替人工穷举。
3. **`catch (Exception` 18 处未逐个定性**：已确认其中 9 处紧邻 `log.error/warn`，需人工确认是否存在「记录后继续」的吞异常（本审计抽样未发现会掩盖资金错误的情形，但不排除）。
4. **代码行号对应基线 `0ba7f12c`**：后续提交会使行号漂移，建议以「文件 + 符号名」为主要定位依据。
