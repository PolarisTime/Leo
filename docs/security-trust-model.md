# 安全信任模型（Security Trust Model）

> 状态：当前有效。本文档描述系统实际运行的信任模型，并登记未来引入多角色时的收紧点。
> 任何新增"登录用户可修改全局影响面资源"的端点时，必须同步更新本文档的端点清单。

## 1. 账号模型：单账号、登录即管理员

系统当前为**单账号模型**：

- 系统仅存在一个由 `POST /api/v2/setup/account`（首次初始化向导）创建的用户账号，
  创建链路为 `V2InitialSetupController → InitialSetupCoordinator → InitialSetupService → InitialAccountProvisioningService`。
- 初始化完成后（`sys_bootstrap_state.completed = true` 或存在未删除账号），setup 端点永久禁用
  （`InitialSetupService.assertSetupRequired` 抛 403），无法再创建第二个账号。
- 因此**登录即管理员**：不存在角色、权限点或数据范围隔离；任何通过认证的会话都拥有全部业务能力。

## 2. 信任边界 = 登录边界

当前信任边界只有一条：**是否通过认证（持有有效会话/访问令牌）**。

- 通过认证的请求被视为完全可信，可执行所有管理操作。
- 未通过认证的请求只能访问显式标注 `@PublicAccess` 的端点：

| 公开端点 | 说明 |
| --- | --- |
| `POST /api/v2/auth/login` | 登录 |
| `POST /api/v2/auth/refresh` | 刷新令牌 |
| `POST /api/v2/auth/logout` | 登出 |
| `GET /api/v2/runtime-config` | 运行时配置与初始化状态（只读） |
| `POST /api/v2/setup/account` | 首次初始化账号（受 setup token 与一次性状态保护，初始化完成后 403） |
| `GET /api/v2/health` | 健康检查 |
| `GET /api/v2/version` | 版本信息 |

首次初始化端点的额外防线：请求须携带 `X-Setup-Token`（`InitialSetupTokenFilter` 校验），
且服务端以行锁 + 二次状态校验保证只允许创建一个账号。

## 3. "登录用户可修改全局影响面资源"端点清单

以下资源影响全局行为或全部业务单据的呈现，任何登录用户均可修改（单账号模型下的预期行为）：

### 打印模板（`/api/v2.0/print-templates`）

| 端点 | 全局影响 |
| --- | --- |
| `POST /print-templates` | 新增全局打印模板 |
| `PUT /print-templates/{id}` | 修改模板正文/布局，影响对应单据类型的所有后续打印 |
| `PUT /print-templates/{id}/content` | 替换模板文件内容（multipart 上传），同上 |
| `DELETE /print-templates/{id}` | 删除模板，影响所有后续打印 |

相关运行时端点：`GET /print-previews/items`（打印明细查询）、`POST /print-exports`（打印输出生成，读取上述模板）。

### 公司设置（`/api/v2/company-settings`）

| 端点 | 全局影响 |
| --- | --- |
| `POST /company-settings` | 新增结算主体 |
| `PUT /company-settings/current` | 修改当前生效结算主体信息，影响单据抬头、快照同步 |
| `PUT /company-settings/{id}` | 修改任意结算主体 |
| `DELETE /company-settings/{id}` | 删除结算主体（有引用校验） |

### 运行时配置（`/api/v2/runtime-config`）

当前该控制器**仅提供公开只读 `GET`**，无写端点；运行时配置的变更目前经由部署配置/数据库维护，
不暴露 HTTP 写接口。若未来新增写端点，应视为全局影响面资源并纳入本清单。

## 4. 未来引入多角色时的收紧点（@PreAuthorize 收敛清单）

> 端点级授权底座已落地：`PermissionCodes` 权限码目录、`@RequirePermission` 注解、可插拔
> `AuthorityProvider`（默认 `GrantAllAuthorityProvider` 为登录用户返回全部权限）与方法级安全。
> 详见 `docs/specs/2026-09-14-endpoint-permission-model-plan.md`。当前单账号仍全量可用，
> 收紧时只需替换 `AuthorityProvider` 并逐步给端点标注权限码，无需改动鉴权链路。

### 4.1 权限码规范与三层粒度

权限码统一为 `<资源复数kebab>:<动作>[:<字段>]`，资源与 REST 路径复数对齐
（如 `sales-returns`、`material-imports`、`customer-statements`），动作覆盖
`read | create | update | delete | audit | unaudit | complete | confirm | print | export | import | preview | backfill | rollback | rebuild`；
通配为 `*` 与 `资源:*`。

粒度分三层：

- **功能层**（当前生效）：`resource:action`，端点一一对应，由 `@RequirePermission` 校验；
- **字段层**（登记预留）：`resource:action:field`，如 `sales-orders:read:amount`、`inventory:read:cost`，
  本轮只作为规范占位登记，不强制校验；
- **数据层**（设计预留）：不进入权限码，由角色数据范围在查询侧收敛。

6 个试点控制器的 33 处注解已按细动作映射（`GET→read`、`POST→create`、`PUT/PATCH→update`、
`DELETE→delete`，以及 `audit / confirm / import / preview / rollback / backfill`），详见设计文档第 6 节。

### 4.2 RBAC0 落库路线

多角色落地采用标准 `sys_role` / `sys_permission` / `user_role` / `role_permission` /
`sys_role_data_scope` 表结构（新表，不复用 V94 已退役结构）：功能、字段、数据三类权限点统一登记在
`sys_permission`（含 `type`、`resource`、`action`、`field`），`code` 与 `PermissionCodes` 常量保持一致；
角色经 `role_permission` 绑定权限点，经 `sys_role_data_scope` 绑定数据范围，再由数据库版
`AuthorityProvider` 按 `principal.id()` 解析并注入 `GrantedAuthority`。

引入角色/权限模型时，按以下顺序收敛（先收窄破坏面最大的写操作）：

1. **打印模板管理**：`POST/PUT/DELETE /print-templates*` 收敛至模板管理员角色；
   打印执行（`GET /print-previews/items`、`POST /print-exports`）保持普通登录用户可用。
2. **公司设置**：`POST/PUT/DELETE /company-settings*` 收敛至系统管理员角色；
   `GET /current`、`GET /options` 保持全员可读。
3. **运行时配置**：如新增写端点，直接要求系统管理员角色。
4. **基础资料与业务单据**：按资源复数拆分 `read/create/update/delete` 与业务动作权限，
   不再使用笼统的 `:write`；字段层与数据层在前两层稳定后再启用。
5. 实现方式建议：在上述控制器方法上添加 `@RequirePermission(PermissionCodes.XXX_YYY)`
   （或等价的 `@PreAuthorize("hasAuthority(...)")`），并保留 `GlobalExceptionHandler`
   对 `AccessDeniedException` 的 403 映射；同时把 `@PublicAccess` 清单和本节收敛点纳入代码评审门禁。

## 5. 相关机制索引

- 认证过滤器与公开路径白名单：`com.leo.erp.system.setup.web.InitialSetupTokenFilter`、Spring Security 配置。
- 错误响应统一走 RFC 9457 ProblemDetail（`ApiProblemFactory` / `GlobalExceptionHandler`），
  未认证 401、无权限 403、限流 429（`ErrorCode.TOO_MANY_REQUESTS`，预留）语义已就位。
