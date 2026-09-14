---
title: 端点级授权模型与细粒度权限码设计
date: 2026-09-14
status: implemented
scope: Leo 后端、Spring Security、方法级安全、权限目录
---

# 端点级授权模型与细粒度权限码设计

## 1. 结论

在保留现有“单账号、登录即管理员”信任模型的前提下，引入**可插拔的端点级授权模型**，并把权限码从早期的“模块:read/write”细化为**资源复数 + 细动作**：

- 开启 Spring Security 方法级安全（`@EnableMethodSecurity`），为标准 `@PreAuthorize` 与自定义 `@RequirePermission` 提供支撑。
- 以 `PermissionCodes` 作为权限码单一来源，统一 `资源复数kebab:动作[:字段]` 命名，资源与 REST 路径一一对应。
- 权限来源通过 `AuthorityProvider` 接口插拔；默认实现 `GrantAllAuthorityProvider` 为任意登录用户返回全部权限码，保证现有功能零回归。
- 认证过滤器 `JwtAuthenticationFilter` 调用 `AuthorityProvider`，把权限码转成 `GrantedAuthority` 注入安全上下文。
- 在 6 个试点控制器共 33 处标注 `@RequirePermission`，失败时由既有 `GlobalExceptionHandler` 统一映射为 403。

本设计**不恢复** `V94__retire_rbac_permissions_and_mcp.sql` 已退役的 `sys_role` / `sys_menu_action` / `casbin_rule` 表结构，也不需要新增 Flyway 迁移；RBAC0 落库为后续路线，表名见第 7 节。

## 2. 权限码规范

```text
<资源复数kebab>:<动作>[:<字段>]
```

- **资源**：API 路径的复数 kebab-case，例如 `sales-orders`、`sales-returns`、`sales-outbounds`、`materials`、`material-imports`、`import-batches`、`inventory`、`customer-statements`、`customers`、`suppliers`、`warehouses`、`carriers`、`projects`、`quote-sheets`、`steel-quotes`、`purchase-orders`、`purchase-inbounds`、`freight-bills`、`finance`、`receipts`、`payments`、`ledger-adjustments`、`attachments`、`system`。
- **动作**：细粒度业务动作集，`read | create | update | delete | audit | unaudit | complete | confirm | print | export | import | preview | backfill | rollback | rebuild`。
- **字段**（可选）：`resource:action:field`，例如 `sales-orders:read:amount`、`sales-orders:update:unit-price`、`inventory:read:cost`。
- **通配**：`*`（全局，`WILDCARD`）与 `资源:*`（资源级，`PermissionCodes.ofResourceWildcard(resource)`）。

常量组织（`security/permission/PermissionCodes.java`）：

| 成员 | 说明 |
| --- | --- |
| `WILDCARD` | 全局通配 `*`，超级管理员语义 |
| `PermissionCodes.Resources` | 资源常量（复数 kebab，对齐路径） |
| `PermissionCodes.Actions` | 动作常量（上述动作集） |
| `PermissionCodes.Fields` | 字段常量（`amount` / `unit-price` / `cost`） |
| `XXX_READ/CREATE/UPDATE/DELETE` | 各资源 CRUD 权限码 |
| `SALES_RETURNS_AUDIT`、`INVENTORY_BACKFILL`、`MATERIAL_IMPORTS_IMPORT/PREVIEW`、`IMPORT_BATCHES_ROLLBACK`、`CUSTOMER_STATEMENTS_CONFIRM` 等 | 业务动作权限码 |
| `of(resource, action)` / `of(resource, action, field)` / `ofResourceWildcard(resource)` | 拼接助手 |
| `all()` | 目录全集，`PermissionCodesTest` 反射校验所有公开 String 常量均已登记 |

## 3. 三层粒度

| 层级 | 表达 | 本轮状态 | 落地机制 |
| --- | --- | --- | --- |
| 功能层 | `resource:action` | **生效**：与端点一一对应，由 `@RequirePermission` 校验 | 方法级安全拦截器 |
| 字段层 | `resource:action:field` | **登记占位**：本轮不强制校验 | 后续在 DTO/序列化层按字段裁剪或拒绝 |
| 数据层 | 不进入权限码 | **设计预留**：本轮不校验 | 角色-数据范围（`sys_role_data_scope`）在查询侧收敛 |

字段层示例常量：`SALES_ORDERS_READ_AMOUNT`、`SALES_ORDERS_UPDATE_UNIT_PRICE`、`INVENTORY_READ_COST`。它们仅作为规范占位登记进 `all()`，不改变任何端点行为；启用时需要把“是否允许读取金额/成本”与查询投影绑定，不能只靠注解。

数据层不编码进权限码，原因是同一动作在不同数据范围（全部/本部门/本人）下语义不同；应由角色绑定数据范围策略，在 Repository/Query 侧注入过滤条件。

## 4. 组件与职责

| 组件 | 路径 | 职责 |
| --- | --- | --- |
| `PermissionCodes` | `security/permission/PermissionCodes.java` | 资源/动作/字段常量、权限码目录与拼接助手；`all()` 返回全集 |
| `@RequirePermission` | `security/permission/RequirePermission.java` | 端点/类级授权注解，声明所需权限码（OR 语义） |
| `PermissionAuthorizationManager` | `security/permission/PermissionAuthorizationManager.java` | 读取注解并依据当前认证主体的 `GrantedAuthority` 做决策 |
| `PermissionMethodSecurityConfig` | `security/permission/PermissionMethodSecurityConfig.java` | `@EnableMethodSecurity` + 注册 `@RequirePermission` 前置拦截器 |
| `AuthorityProvider` | `security/permission/AuthorityProvider.java` | 权限来源扩展点：`authoritiesFor(SecurityPrincipal)` |
| `GrantAllAuthorityProvider` | `security/permission/GrantAllAuthorityProvider.java` | 默认实现，返回 `PermissionCodes.all()` |
| `AuthorityProviderConfig` | `security/permission/AuthorityProviderConfig.java` | `@ConditionalOnMissingBean` 装配默认 Provider |
| `JwtAuthenticationFilter` | `security/jwt/JwtAuthenticationFilter.java` | 解析 JWT 后调用 Provider，注入 `GrantedAuthority` |

授权链路：

```text
请求 → JwtAuthenticationFilter（Provider 注入 authorities）
     → SecurityFilterChain（anyRequest().authenticated()）
     → 控制器方法拦截器（@RequirePermission / @PreAuthorize）
     → 通过则执行业务；拒绝抛 AccessDeniedException
     → GlobalExceptionHandler → 403 ProblemDetail
```

`PermissionAuthorizationManager` 规则（本轮不变）：

1. 未标注 `@RequirePermission` 的调用直接放行；
2. 未认证或认证未通过则拒绝；
3. 持有通配权限 `*`（`PermissionCodes.WILDCARD`）一律通过；
4. 否则拥有注解中任意一个权限码即通过（OR 语义）。

> `资源:*` 资源级通配已在目录层提供构造助手；当前决策器尚未做前缀匹配。若启用该通配，需要扩展决策器的匹配逻辑，并同步补充测试。

## 5. 默认 Provider 行为（临时实现）

`GrantAllAuthorityProvider` 对任意 `SecurityPrincipal` 返回 `PermissionCodes.all()`：

- 与当前单账号模型一致，登录用户全量可访问，不破坏既有功能；
- 不查询任何角色/权限表，仅为后续接入完整权限系统保留稳定契约；
- 一旦出现自定义 `AuthorityProvider` Bean，`@ConditionalOnMissingBean` 使默认实现自动退让。

## 6. 试点控制器与 33 处注解映射

| 控制器 | 端点 | 权限码 |
| --- | --- | --- |
| `V2SalesReturnController` | `GET /sales-returns`、`/candidates`、`/{id}` | `sales-returns:read` |
| | `POST /sales-returns` | `sales-returns:create` |
| | `PUT /sales-returns/{id}` | `sales-returns:update` |
| | `PATCH /sales-returns/{id}/status` | `sales-returns:update` |
| | `POST /sales-returns/{id}/audits` | `sales-returns:audit` |
| | `DELETE /sales-returns/{id}` | `sales-returns:delete` |
| `V2InventoryController` | `GET /inventory/balances`、`/transactions` | `inventory:read` |
| | `POST /inventory/backfills` | `inventory:backfill` |
| `V2MaterialController` | `GET /materials*`、`/{id}`、`/{id}/histories` | `materials:read` |
| | `POST /materials` | `materials:create` |
| | `PUT /materials/{id}` | `materials:update` |
| | `DELETE /materials/{id}` | `materials:delete` |
| `V2MaterialImportController` | `POST /material-imports` | `material-imports:import` |
| | `POST /material-imports/previews` | `material-imports:preview` |
| `V2ImportBatchController` | `POST /import-batches/{importBatchNo}/rollbacks` | `import-batches:rollback` |
| `V2CustomerStatementController` | `GET /customer-statements`、`/summary`、`/candidates`、`/{id}` | `customer-statements:read` |
| | `POST /customer-statements` | `customer-statements:create` |
| | `PUT /customer-statements/{id}` | `customer-statements:update` |
| | `PATCH /customer-statements/{id}/status` | `customer-statements:update` |
| | `POST /customer-statements/{id}/confirmations` | `customer-statements:confirm` |
| | `DELETE /customer-statements/{id}` | `customer-statements:delete` |

## 7. 扩展到全仓库的路线

1. **补齐权限码**：为新模块在 `PermissionCodes` 增加资源常量、动作权限码，并加入 `all()`；`PermissionCodesTest` 会反射校验漏登记。
2. **按资源标注**：只读控制器加 `资源:read`；写方法按 HTTP 语义标注 `:create` / `:update` / `:delete`；业务动作（审核、确认、打印、导出、导入、回滚、回填、重建）一律建模为 `资源:<动作>`。
3. **收敛类级默认**：批量迁移可先在类级声明只读权限，再在写方法覆盖；禁止用 `:write` 这类粗粒度动作新增常量。
4. **替换权限来源**：实现数据库版 `AuthorityProvider`，按 `principal.id()` 查询用户角色与角色权限码；通过 `@Primary` 或替换默认 Bean 生效。
5. **门禁**：把 `@PublicAccess` 白名单、端点-权限映射纳入代码评审；对无权限声明的新写接口默认拒绝接入。
6. **缓存与失效**：角色/权限变更后，结合既有 `AuthenticatedUserCacheService` 的失效机制清理快照，避免过期权限。

### RBAC0 落库路线

引入多角色时使用 RBAC0 标准表结构（**新表，不复用 V94 已退役结构**）：

| 表 | 职责 | 关键字段 |
| --- | --- | --- |
| `sys_role` | 角色 | `id`、`code`、`name`、`status` |
| `sys_permission` | 权限点（本目录权限码） | `id`、`code`（如 `sales-returns:audit`）、`type`（功能/字段/数据）、`resource`、`action`、`field` |
| `user_role` | 用户-角色 | `user_id`、`role_id` |
| `role_permission` | 角色-权限 | `role_id`、`permission_id` |
| `sys_role_data_scope` | 角色-数据范围 | `role_id`、`scope`（全部/本部门/本人/自定义）、`scope_value` |

数据范围通过 `sys_role_data_scope` 在查询侧生效，不进入权限码；权限点表以 `type` 区分功能层、字段层与数据层登记。`code` 与 `PermissionCodes` 常量保持一致，作为权限目录落库的单一来源。

## 8. 风险与缓解

| 风险 | 缓解 |
| --- | --- |
| 开启方法级安全改变既有行为 | 默认 Provider 返回全部权限；仅标注 `@RequirePermission` 的方法被拦截（自定义切点只匹配该注解） |
| 权限码拼写漂移 | 统一引用 `PermissionCodes` 常量，禁止散落字符串；目录完整性测试兜底 |
| 细动作与端点漂移 | 权限码资源与路径复数对齐；映射表与控制器同步维护 |
| 字段/数据层提前启用导致误拒 | 本轮仅登记占位，不接入校验；启用前必须有投影/查询侧配套与灰度 |
| 未认证命中方法级安全时抛 `AuthenticationCredentialsNotFoundException` | 生产链路中 `SecurityFilterChain` 先以 401 拦截未认证请求，方法级安全不会成为未认证入口 |
| 自定义 Provider 引入后权限缺失导致误拒 | Provider 上线前用 `:read` 全量标注做灰度，配套 403 监控与告警 |

## 9. 验证

- `mvn -q -DskipTests compile`
- `mvn -q -Dtest='*Permission*Test,*Security*Test,*SalesReturn*Test,*Inventory*Test,*Material*Test,*CustomerStatement*Test' test`
