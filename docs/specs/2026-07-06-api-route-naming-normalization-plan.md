# API Route Naming Normalization Plan

> **校验记录（2026-07-06）**：本计划已对照 leo/aries 代码交叉核对。后端 17 条本轮改名路径、前端常量与 Controller 位置已确认；权限路径解析并非“无影响”，已纳入本轮修正；阶段 3（smoke 脚本）原描述与现状不符，已修正；API Key `resource/menu` 命名分裂统一为 `resource-options` / `allowedResources`。

## 背景

当前 API 主资源路径整体遵循复数资源命名，例如 `/materials`、`/purchase-orders`、`/payments`。不一致主要集中在集合型子资源：

- 下拉选项接口同时存在 `/option`、`/options`、`/*-option`。
- 候选数据接口使用单数 `/candidate`、`/import-candidate`，但返回分页集合。
- `/materials/grade` 返回材质/牌号数组，语义上更接近 `/materials/grades`。
- 角色权限接口 `/role-settings/{id}/permission` 读写权限集合，但可理解为单个“权限配置域”，需要单独评估。

目标是把集合型接口统一到复数路径，降低前后端契约维护成本。按 2026-07-06 实施口径，后端直接替换旧路径，不保留旧路由兼容映射，因此前端、脚本和测试必须同批切换。

## 命名规则

1. 业务实体集合使用复数资源名，例如 `/customers`、`/supplier-statements`。
2. 集合型子资源使用复数名，例如 `/options`、`/candidates`、`/grades`。
3. 单个配置、状态视图、动作接口保持现有语义，不做机械复数化，例如 `/current`、`/summary`、`/search`、`/export`、`/refresh`。
4. 路径调整采用直接替换：后端只保留新路径，前端、脚本和测试同步更新；旧路径不再作为兼容入口保留。

## 范围

### 第一批：选项接口

| 旧路径 | 新路径 | 后端位置 | 前端位置 |
| --- | --- | --- | --- |
| `/departments/option` | `/departments/options` | `DepartmentController` | `ENDPOINTS.DEPARTMENTS_OPTIONS` |
| `/warehouses/option` | `/warehouses/options` | `WarehouseController` | `ENDPOINTS.WAREHOUSES_OPTIONS` |
| `/customers/option` | `/customers/options` | `CustomerController` | `ENDPOINTS.CUSTOMERS_OPTIONS` |
| `/suppliers/option` | `/suppliers/options` | `SupplierController` | `ENDPOINTS.SUPPLIERS_OPTIONS` |
| `/carriers/option` | `/carriers/options` | `CarrierController` | `ENDPOINTS.CARRIERS_OPTIONS` |
| `/material-categories/option` | `/material-categories/options` | `MaterialCategoryController` | `ENDPOINTS.MATERIAL_CATEGORIES` |
| `/auth/api-keys/user-option` | `/auth/api-keys/user-options` | `ApiKeyAdminController` | `ENDPOINTS.API_KEYS_USER_OPTIONS` |
| `/auth/api-keys/resource-option` | `/auth/api-keys/resource-options` | `ApiKeyAdminController` | `ENDPOINTS.API_KEYS_RESOURCE_OPTIONS` |
| `/auth/api-keys/action-option` | `/auth/api-keys/action-options` | `ApiKeyAdminController` | `ENDPOINTS.API_KEYS_ACTION_OPTIONS` |
| `/role-settings/permission-option` | `/role-settings/permission-options` | `RoleSettingController` | `ENDPOINTS.ROLE_PERMISSION_OPTIONS` |

`/company-settings/options` 已符合规则，保持不变。

> 校验补充（2026-07-06）：API Key 的 `resource-option` 接口曾存在 `resource/menu` 命名分裂——后端接口与 DTO 使用 `resource`，但 smoke 脚本调用 `menu-options` 并提交 `allowedMenus`。本轮最终统一为 `resource-options` 与 `allowedResources`。

### 第二批：候选集合接口

| 旧路径 | 新路径 | 后端位置 | 前端位置 |
| --- | --- | --- | --- |
| `/supplier-statements/candidate` | `/supplier-statements/candidates` | `SupplierStatementController` | `api/statements.ts` |
| `/customer-statements/candidate` | `/customer-statements/candidates` | `CustomerStatementController` | `api/statements.ts` |
| `/freight-statements/candidate` | `/freight-statements/candidates` | `FreightStatementController` | `api/statements.ts` |
| `/purchase-orders/import-candidate` | `/purchase-orders/import-candidates` | `PurchaseOrderController` | `api/purchase-order-candidates.ts` |
| `/freight-bills/import-candidate` | `/freight-bills/import-candidates` | `FreightBillController` | `api/freight-bill-candidates.ts` |
| `/sales-orders/outbound-import-candidate` | `/sales-orders/outbound-import-candidates` | `SalesOrderController` | `api/sales-order-candidates.ts` |

> 校验补充（2026-07-06）：前端三个对账单候选（supplier/customer/freight）共用 `api/statements.ts:40` 的一处动态拼接 `${endpointConfig.path}/candidate`，只需改这一处即可同时覆盖三个后端接口；`purchase-orders`、`freight-bills`、`sales-orders` 三个导入候选则各自硬编码在对应 `api/*-candidates.ts`。相关单测 `api/statements.spec.ts`、`api/purchase-order-candidates.spec.ts`、`api/freight-bill-candidates.spec.ts`、`api/sales-order-candidates.spec.ts` 均硬编码了旧路径，需同步更新。

### 第三批：材质/牌号集合

| 旧路径 | 新路径 | 后端位置 | 前端位置 |
| --- | --- | --- | --- |
| `/materials/grade` | `/materials/grades` | `MaterialController` | `ENDPOINTS.MATERIAL_GRADES`、`api/material-grades.ts` |

### 单独评估：角色权限集合

| 当前路径 | 可选新路径 | 处理建议 |
| --- | --- | --- |
| `GET /role-settings/{id}/permission` | `GET /role-settings/{id}/permissions` | 可选，低优先级 |
| `PUT /role-settings/{id}/permission` | `PUT /role-settings/{id}/permissions` | 可选，低优先级 |

这两个接口虽然读写集合，但也可以理解为角色的一个权限配置资源。为降低权限模块影响，建议不纳入第一轮。

## 不纳入范围

以下接口命名符合当前语义，不建议调整：

- 查询动作：`/search`
- 导入导出动作：`/import`、`/export`
- 模板下载：`/template`
- 状态视图：`/status`、`/summary`、`/monitoring`
- 登录会话动作：`/login`、`/refresh`、`/logout`
- 安全动作：`/revoke`、`/rotate`、`/enable`、`/disable`
- 单个配置：`/current`、`/preference`、`/upload-rule`、`/statement-generator-rule`
- MCP 查询层：`ErpMcpQueryFacade` 使用内部 reader key（如 `"material-grade"`，非 HTTP 路由），不受本次路由改名影响，无需调整。

## 实施步骤

### 阶段 1：后端直接替换为新路径

1. 把第一批到第三批的 `@GetMapping` 直接改为新路径字符串，不保留旧路径。

示例：

```java
@GetMapping("/options")
public ApiResponse<List<SupplierOptionResponse>> options() {
    return ApiResponse.success(supplierService.listActiveOptions());
}
```

2. 候选集合接口同理：

```java
@GetMapping("/candidates")
public ApiResponse<PageResponse<SupplierStatementCandidateResponse>> candidates(...) {
    ...
}
```

3. `/materials/grade` 直接替换为 `/materials/grades`。
4. 不改业务返回结构，不改权限注解，不改服务层逻辑。
5. 同步修正 `ResourcePermissionCatalog` 的真实复数路由前缀，确保 API Key 过滤器能把新路径解析回资源编码。

### 阶段 2：前端切换到新路径

1. 更新 `aries/src/constants/endpoints.ts`：
   - 所有 `*_OPTIONS` 常量使用 `/options` 或 `/*-options`。
   - `MATERIAL_GRADES` 使用 `/materials/grades`。
2. 更新硬编码候选路径：
   - `api/statements.ts`
   - `api/purchase-order-candidates.ts`
   - `api/freight-bill-candidates.ts`
   - `api/sales-order-candidates.ts`
3. 同步更新前端单测里的 mock endpoint，避免测试继续固化旧路径。具体：
   - **硬阻塞**：`aries/src/constants/endpoints.spec.ts` 直接断言常量字面量（如 `expect(ENDPOINTS.CUSTOMERS_OPTIONS).toBe('/customers/option')`，第 26–30 行；`MATERIAL_GRADES` 第 38 行）。改常量后必须同步改断言，否则前端测试失败。
   - **已抢跑复数的 spec**：`api/supplier-options.spec.ts`、`api/warehouse-options.spec.ts`、`api/user-accounts.spec.ts` 的 `vi.mock` 已把 endpoint 写成复数（如 `/suppliers/options`、`/departments/options`），当前靠 mock 覆盖保持绿。切换生产常量后这些无需再改，但需确认 mock 值与新常量一致。
   - 候选路径相关 spec 详见「第二批」说明。

### 阶段 3：脚本和后端测试同步

1. `leo/scripts/smoke_admin_api.py` 统一使用复数新路径。
2. API Key 脚本契约统一为 `/auth/api-keys/resource-options` 与请求体字段 `allowedResources`，不再使用不存在的 `/auth/api-keys/menu-options` 和 `allowedMenus`。
3. 同步修正脚本中已知系统路径漂移：`/system/menu/tree`、`/system/databases/status`。
4. 补充或调整 Controller/权限解析测试，覆盖新路径且确认旧路径未继续注册。

## 验证清单

后端：

```bash
cd /home/instance/Gemini/leo
./mvnw test
```

前端：

```bash
cd /home/instance/Gemini/aries
npm test -- --run
```

静态检查：

```bash
# 注意：原 pattern 中 `/option` 匹配不到 `user-option`/`resource-option`/`action-option`（前缀是 `-` 而非 `/`），会漏检 API Key 接口；下方 pattern 已修正为同时覆盖 `-`/`/` 前缀与新旧单复数。
rg -n "[-/]options?|[-/]candidates?|/materials/grades?" "/home/instance/Gemini"
```

预期结果：

- 后端 Controller 只注册新路径，不再注册旧路径。
- 前端生产常量和 API 调用全部使用新路径。
- smoke 脚本不再调用旧 API Key option 路径，也不再提交 `allowedMenus`。
- `ResourcePermissionCatalog.resolveResourceByPath` 能解析新复数路径，API Key 过滤器不因路径解析失败误拒。

## 风险和回滚

风险：

- 前端和后端不同步会导致 404；本轮采用直接替换，必须同批发布。
- smoke 脚本或外部调用方如果仍依赖旧路径，会在发布后失败。
- 权限路径解析（`ResourcePermissionCatalog.resolveResourceByPath`）是实际风险点：匹配规则是 `path.equals(prefix) || path.startsWith(prefix + "/")`，因此必须显式登记真实复数根路径，例如 `/suppliers`、`/departments`、`/auth/api-keys`、`/role-settings`。
- `resolveResourceByPath` 中角色权限正则需要支持 `/role-settings/{id}/permission`；本轮只修解析，不改 `GET/PUT /role-settings/{id}/permission` 的 API 语义。

回滚：

- 由于后端不保留旧路径，回滚必须前后端和 smoke 脚本同步回滚。
- 不涉及数据库变更，不需要 Flyway。
- 不涉及响应结构变更，业务数据无迁移风险。

## 建议优先级

1. 第一批 `option -> options`。
2. 第二批 `candidate -> candidates`。
3. 第三批 `grade -> grades`。
4. 角色权限 `/permission -> /permissions` 暂缓，单独评审。

## 附录 A：完整接口清单

> 完整路径 = Controller 类级 `@RequestMapping` 前缀 + 方法级 `@GetMapping`。下表已于 2026-07-06 对照代码核对，行号为改动位置，全部为 `GET`。

### 第一批 · 选项接口（`option → options`）

| # | 完整旧路径 | 完整新路径 | 后端 `@GetMapping` | 前端引用 |
| --- | --- | --- | --- | --- |
| 1 | `/departments/option` | `/departments/options` | `DepartmentController.java:48` | `ENDPOINTS.DEPARTMENTS_OPTIONS` |
| 2 | `/warehouses/option` | `/warehouses/options` | `WarehouseController.java:37` | `ENDPOINTS.WAREHOUSES_OPTIONS` |
| 3 | `/customers/option` | `/customers/options` | `CustomerController.java:37` | `ENDPOINTS.CUSTOMERS_OPTIONS` |
| 4 | `/suppliers/option` | `/suppliers/options` | `SupplierController.java:35` | `ENDPOINTS.SUPPLIERS_OPTIONS` |
| 5 | `/carriers/option` | `/carriers/options` | `CarrierController.java:37` | `ENDPOINTS.CARRIERS_OPTIONS` |
| 6 | `/material-categories/option` | `/material-categories/options` | `MaterialCategoryController.java:73` | `ENDPOINTS.MATERIAL_CATEGORIES` |
| 7 | `/auth/api-keys/user-option` | `/auth/api-keys/user-options` | `ApiKeyAdminController.java:58` | `ENDPOINTS.API_KEYS_USER_OPTIONS` |
| 8 | `/auth/api-keys/resource-option` | `/auth/api-keys/resource-options` | `ApiKeyAdminController.java:64` | `ENDPOINTS.API_KEYS_RESOURCE_OPTIONS` |
| 9 | `/auth/api-keys/action-option` | `/auth/api-keys/action-options` | `ApiKeyAdminController.java:70` | `ENDPOINTS.API_KEYS_ACTION_OPTIONS` |
| 10 | `/role-settings/permission-option` | `/role-settings/permission-options` | `RoleSettingController.java:86` | `ENDPOINTS.ROLE_PERMISSION_OPTIONS` |

### 第二批 · 候选集合接口（`candidate → candidates`）

| # | 完整旧路径 | 完整新路径 | 后端 `@GetMapping` | 前端引用 |
| --- | --- | --- | --- | --- |
| 11 | `/supplier-statements/candidate` | `/supplier-statements/candidates` | `SupplierStatementController.java:73` | `api/statements.ts:40`（模板拼接） |
| 12 | `/customer-statements/candidate` | `/customer-statements/candidates` | `CustomerStatementController.java:73` | `api/statements.ts:40`（模板拼接） |
| 13 | `/freight-statements/candidate` | `/freight-statements/candidates` | `FreightStatementController.java:76` | `api/statements.ts:40`（模板拼接） |
| 14 | `/purchase-orders/import-candidate` | `/purchase-orders/import-candidates` | `PurchaseOrderController.java:56` | `api/purchase-order-candidates.ts:21` |
| 15 | `/freight-bills/import-candidate` | `/freight-bills/import-candidates` | `FreightBillController.java:66` | `api/freight-bill-candidates.ts:16` |
| 16 | `/sales-orders/outbound-import-candidate` | `/sales-orders/outbound-import-candidates` | `SalesOrderController.java:85` | `api/sales-order-candidates.ts:16` |

### 第三批 · 材质牌号（`grade → grades`）

| # | 完整旧路径 | 完整新路径 | 后端 `@GetMapping` | 前端引用 |
| --- | --- | --- | --- | --- |
| 17 | `/materials/grade` | `/materials/grades` | `MaterialController.java:89` | `ENDPOINTS.MATERIAL_GRADES`（`api/material-grades.ts:16` 引用常量，无需改） |

## 附录 B：精确改动对照

### B.1 后端（阶段 1）：三类改动模式

所有改动仅把 `@GetMapping` 路径直接改为新字符串，**方法签名、`@RequiresPermission`、`@BindPageQuery`/`@RequestParam` 参数绑定、服务层调用全部不动**。本轮不使用多路径数组映射。

**模式一 · 选项类（10 处）**

```java
// 通用（departments/warehouses/customers/suppliers/carriers/material-categories）
@GetMapping("/options")

// api-keys 三处为 -option 前缀（ApiKeyAdminController）
@GetMapping("/user-options")
@GetMapping("/resource-options")
@GetMapping("/action-options")

// role-settings（RoleSettingController:86）
@GetMapping("/permission-options")
```

**模式二 · 候选类（6 处，方法带分页与查询参数）**

```java
// 前（以 SupplierStatementController:73 为例）
@GetMapping("/candidate")
@RequiresPermission(resource = "supplier-statement", action = "read")
public ApiResponse<PageResponse<SupplierStatementCandidateResponse>> candidates(
        @BindPageQuery(sortFieldKey = "purchase-inbound") PageQuery query,
        @RequestParam(required = false) String keyword, ...) { ... }

// 后：仅注解路径改为复数，方法签名与参数原样保留
@GetMapping("/candidates")

// 导入候选（purchase-orders:56 / freight-bills:66）
@GetMapping("/import-candidates")
// 销售出库导入候选（sales-orders:85）
@GetMapping("/outbound-import-candidates")
```

**模式三 · 材质牌号（1 处，无参方法）**

```java
// MaterialController:89 —— materialGrades() 无入参，返回 List<String>
@GetMapping("/grades")
```

### B.2 前端（阶段 2）

**（a）常量注册表 `aries/src/constants/endpoints.ts`：改值即全局生效**

所有经 `ENDPOINTS.*` 引用的消费点（含 `api/material-grades.ts:16` 等）随常量值自动切换，无需逐个改：

| 常量 | 旧值 | 新值 |
| --- | --- | --- |
| `DEPARTMENTS_OPTIONS` | `/departments/option` | `/departments/options` |
| `WAREHOUSES_OPTIONS` | `/warehouses/option` | `/warehouses/options` |
| `CUSTOMERS_OPTIONS` | `/customers/option` | `/customers/options` |
| `SUPPLIERS_OPTIONS` | `/suppliers/option` | `/suppliers/options` |
| `CARRIERS_OPTIONS` | `/carriers/option` | `/carriers/options` |
| `MATERIAL_CATEGORIES` | `/material-categories/option` | `/material-categories/options` |
| `API_KEYS_USER_OPTIONS` | `/auth/api-keys/user-option` | `/auth/api-keys/user-options` |
| `API_KEYS_RESOURCE_OPTIONS` | `/auth/api-keys/resource-option` | `/auth/api-keys/resource-options` |
| `API_KEYS_ACTION_OPTIONS` | `/auth/api-keys/action-option` | `/auth/api-keys/action-options` |
| `ROLE_PERMISSION_OPTIONS` | `/role-settings/permission-option` | `/role-settings/permission-options` |
| `MATERIAL_GRADES` | `/materials/grade` | `/materials/grades` |

**（b）硬编码字符串：必须逐处手改**

| 文件:行 | 旧 | 新 |
| --- | --- | --- |
| `api/statements.ts:40` | 模板串结尾 `/candidate` | `/candidates` |
| `api/purchase-order-candidates.ts:21` | `'/purchase-orders/import-candidate'` | `'/purchase-orders/import-candidates'` |
| `api/freight-bill-candidates.ts:16` | `'/freight-bills/import-candidate'` | `'/freight-bills/import-candidates'` |
| `api/sales-order-candidates.ts:16` | `'/sales-orders/outbound-import-candidate'` | `'/sales-orders/outbound-import-candidates'` |

> `statements.ts:40` 的 `endpointConfig.path` 为模块复数根（`/supplier-statements` 等），改这一处即同时覆盖 supplier/customer/freight 三个对账单候选（表 #11–13）。

### B.3 测试（阶段 3）

| 文件 | 改动 |
| --- | --- |
| `aries/src/constants/endpoints.spec.ts` | 各 `*_OPTIONS` 断言（第 26–30 行）、`MATERIAL_GRADES` 断言（第 38 行）改为复数值——**硬阻塞** |
| `api/statements.spec.ts:68/112` | `/…/candidate` → `/…/candidates` |
| `api/purchase-order-candidates.spec.ts:45` | → `/purchase-orders/import-candidates` |
| `api/freight-bill-candidates.spec.ts:41` | → `/freight-bills/import-candidates` |
| `api/sales-order-candidates.spec.ts:41` | → `/sales-orders/outbound-import-candidates` |
| `api/supplier-options.spec.ts:26`、`api/warehouse-options.spec.ts:26`、`api/user-accounts.spec.ts:28/239` | 已是复数 mock，核对与新常量一致即可，无需改 |
| 后端 Controller 测试 | 新增注解级测试，断言 17 个 Controller 只注册新路径且不包含旧路径 |
| 后端权限解析测试 | 新增 `ResourcePermissionCatalog` 新路径解析用例，覆盖 options/candidates/grades/API Key/role 权限路径 |

## 实施记录（2026-07-06）

- 后端 17 个 `GET` 集合型接口已直接替换为复数路径，不做旧路径兼容。
- 前端 `ENDPOINTS` 与候选接口硬编码路径已切换到新路径。
- smoke 脚本已统一 API Key 资源命名：`resource-options` + `allowedResources`，并修正 `/system/menu/tree`、`/system/databases/status`。
- `ResourcePermissionCatalog` 已补充真实复数路由前缀，`/role-settings/{id}/permission` 解析正则已修正。
- 已新增 `ApiRouteNamingNormalizationTest` 和 `ResourcePermissionCatalogTest` 覆盖本轮契约。
