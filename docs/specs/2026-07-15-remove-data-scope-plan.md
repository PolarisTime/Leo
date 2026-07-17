---
title: 单企业单部门模式移除通用 DataScope 实施计划
date: 2026-07-15
status: implemented
scope: leo 后端、aries 前端、PostgreSQL 权限元数据
---

# 1. 决策摘要

> 实施状态（2026-07-16）：应用代码、接口契约、前端清理和 `V84` 数据库迁移均已完成；Flyway validate、迁移后备份、隔离恢复演练和只读运行时烟雾检查已通过。迁移前备份因本地 DevTools 自动执行迁移而未能生成，需认证的写入型烟雾检查因缺少相应凭据未执行，详见第 10 节。

Leo 当前部署模型为单一企业、单一业务部门。业务已确认不再需要通用的“全部数据、本部门、本人”行级数据隔离；用户能否访问业务模块及执行动作，继续由资源与动作权限控制。

本计划决定：

- 移除通用 DataScope 权限维度；
- 权限模型收敛为 `主体/角色 + resource + action`；
- 不引入 PostgreSQL RLS；
- 保留 `created_by`、`updated_by` 等审计字段；
- 少量“仅创建人可操作”的要求改为显式领域规则，不再借助全局 DataScope；
- 先停止执行行级过滤并观察，再删除运行时代码和接口字段，最后通过新的 Flyway 迁移清理数据库字段。

移除后的安全语义必须明确：拥有某资源 `read` 权限的主体可以读取该资源的全部记录；拥有 `update`、`delete`、`audit` 等动作权限的主体原则上可以对该资源的全部记录执行相应动作，除非领域服务存在额外业务约束。

# 2. 前提与边界

## 2.1 必须持续成立的前提

- 系统只服务一个企业，不存在租户隔离；
- 只有一个实际参与业务隔离的部门；
- 销售、采购、仓储和财务人员不要求在同一资源内按创建人隔离记录；
- 财务等敏感能力可以通过资源和动作权限完整隔离；
- 不存在按客户、项目、仓库或区域划分的通用行级授权要求；
- 后台任务和系统调用不需要限制到部分业务记录。

任一前提失效时，应重新设计明确的行级授权模型，而不是恢复当前基于 `created_by` 和用户集合的通用实现。

## 2.2 本计划不包含

- Keycloak 登录迁移；
- jCasbin 接入；
- Spring Method Security 改造；
- MCP 的认证协议迁移和权限模型设计；
- 多租户能力；
- PostgreSQL RLS；
- 新增、恢复或维护测试文件。

MCP 生产代码中现有的 DataScope 上下文绑定必须随编译依赖机械移除，但不把 MCP 行为作为本计划的验收范围。

# 3. 当前实现盘点

## 3.1 后端授权入口

当前 `PermissionAspect` 在完成 `resource + action` 判定后，还会：

1. 调用 `PermissionService.getUserDataScope`；
2. 调用 `DepartmentScopeResolver`计算允许的创建人集合；
3. 写入 `DataScopeContext`；
4. 在方法完成后恢复或清理线程上下文。

`ResourceRecordAccessGuard`、`ErpMcpPermissionExecutor`也会独立建立相同上下文。

## 3.2 查询和实体访问

DataScope 当前影响以下类型的访问路径：

- `AbstractCrudService`的通用分页、列表、详情、修改和删除；
- 物料、销售、物流、客户对账和物流对账服务中的显式 `Specification`；
- 现金流水、库存报表、进销存报表、销售来源候选等原生 SQL；
- 全局搜索模块的创建人集合过滤；
- 附件及来源单据的记录访问校验；
- 创建业务 ID 时基于当前资源上下文进行的约束。

删除时必须逐个调用点分类，禁止用全局文本替换机械删除所有访问守卫。附件父资源授权、来源单据合法性和单据状态保护仍然属于业务安全边界。

## 3.3 权限快照和接口契约

当前 DataScope 还存在于：

- `UserPermissionSnapshot.dataScopeByPermission`；
- `PermissionResolver`与`PermissionScopeKeyParser`；
- `AuthUserResponse.dataScopes`；
- JWT签发结果中的数据范围；
- 用户账户与角色设置请求、响应 DTO；
- 用户有效数据范围聚合和角色授予边界校验；
- 排序字段白名单和权限缓存。

## 3.4 前端

Aries 当前在以下位置消费 DataScope：

- 角色编辑器的数据范围字段；
- 用户账户列表、详情和编辑器；
- 权限管理页面；
- `permissionStore.dataScopes`；
- Zod Schema、API类型和中英文文案。

## 3.5 数据库

当前基线包含：

- `sys_role.data_scope NOT NULL`；
- `sys_user.data_scope`。

当前最高 versioned migration 为 `V83`。不得修改 `V1__baseline.sql` 或任何已存在迁移；最终字段清理使用新的 `V84__remove_data_scope_columns.sql`。如实施期间远端新增更高版本迁移，文件编号必须在开发前重新确认并顺延。

# 4. 目标架构

## 4.1 权限模型

目标判定只包含：

```text
subject -> role -> resource + action -> allow/deny
```

权限服务返回权限集合或布尔结果，不再返回数据范围：

```java
boolean can(Long principalId, String resource, String action);
```

菜单可见性继续由资源权限推导，与 DataScope 无关。

## 4.2 数据访问

JPA、jOOQ和原生 SQL 不再自动追加创建人集合条件。业务查询只保留：

- 业务筛选条件；
- 软删除条件；
- 状态和来源关系条件；
- 明确存在的领域归属约束。

`created_by`继续用于审计、问题追踪和显式领域规则，不能随 DataScope 字段一起删除。

## 4.3 局部所有者规则

如果存在“草稿只能由创建人修改”等要求，应在具体聚合或应用服务中显式表达：

```java
if (order.isDraft() && !Objects.equals(order.getCreatedBy(), principal.id())) {
    throw new BusinessException(ErrorCode.FORBIDDEN, "只能修改本人创建的草稿");
}
```

局部规则必须说明适用资源、动作和状态，不抽象回全局 DataScope。

# 5. 分阶段实施

## 5.1 阶段一：全量可见兼容版本

目标是验证“有资源权限即可访问全部记录”的业务影响，同时保留快速代码回滚能力和原有数据库数据。

后端调整：

- 权限判定仍使用现有 `PermissionService.can`；
- `PermissionAspect`停止为业务资源建立 `DataScopeContext`；
- `ResourceRecordAccessGuard`只保留资源动作判定和仍有效的领域访问检查；
- 查询层暂时保留 `DataScopeContext.apply`、`allowedOwnerUserIds`等调用，但在没有上下文时自然不追加过滤；
- 不修改`sys_role.data_scope`和`sys_user.data_scope`；
- 不删除DTO字段，兼容版本统一返回`全部数据`或空的`dataScopes`，具体契约在实施前固定；
- 增加发布说明，明确数据可见范围扩大。

Aries 调整：

- 隐藏角色和用户编辑器的数据范围选择；
- 列表与详情不再展示数据范围；
- 仍兼容后端旧字段，确保前后端可以独立回滚。

观察重点：

- 普通角色能否看到此前被“本人”隐藏的历史单据；
- 是否存在不应跨人员操作的草稿、待审核或作废单据；
- 报表、全局搜索、附件下载和来源候选的可见结果是否符合业务确认；
- 操作日志中是否出现角色权限允许但业务期望拒绝的操作。

阶段一不得删除数据库字段，回滚只需要恢复旧版本应用。

## 5.2 阶段二：移除后端运行时依赖

阶段一观察通过后，删除 DataScope 运行时模型。

授权模块：

- 删除 `DataScopeContext`、`DataScopeStrategy`、`CreatedByScopeStrategy`和`DepartmentScopeResolver`；
- 删除 `PermissionScopeKeyParser`中仅为数据范围服务的逻辑；
- 从 `PermissionService`删除数据范围查询、用户集合解析和部门缓存失效接口；
- 从 `PermissionResolver`、`PermissionCache`和`UserPermissionSnapshot`删除数据范围快照；
- `PermissionAspect`只负责认证、API调用约束以及`resource + action`判定；
- 简化 `ResourceRecordAccessGuard`，保留资源动作授权和明确的父资源/领域约束。

查询模块：

- `AbstractCrudService`直接使用调用方传入的`Specification`；
- 删除物料、物流、对账和候选服务中的`DataScopeContext.apply/assertCanAccess`；
- 删除现金流水、库存报表、进销存报表和销售候选SQL中的`created_by IN (:dataScopeOwnerUserIds)`；
- 全局搜索只按模块权限决定是否搜索某资源；
- 复核附件下载、打印、导出和来源单据接口，保证仍进行资源动作权限判定；
- `BusinessCreateIdResolver`不再依赖 DataScope 上下文推断资源，改用已有显式模块参数；禁止为此引入新的线程上下文。

身份与接口：

- 从认证响应和令牌载荷删除`dataScopes`；
- 从用户账户和角色设置DTO删除`dataScope`；
- 删除用户有效数据范围聚合和“不得授予更宽数据范围”校验；
- 从排序字段白名单、配置属性和初始化逻辑删除相关字段；
- 保留角色继承、角色冲突和资源动作授予边界检查。

MCP：

- 删除`ErpMcpPermissionExecutor`中的 DataScope 建立、恢复和创建人集合解析；
- 不改变其既有资源动作权限语义。

## 5.3 阶段三：移除 Aries 契约和状态

在后端兼容响应已部署、前端不再依赖字段后：

- 删除角色编辑器`dataScope`表单字段和联动逻辑；
- 删除用户列表、详情和编辑器的数据范围字段；
- 删除`permissionStore.dataScopes`；
- 删除Zod Schema、API类型、页面配置、i18n类型及中英文文案；
- 更新前后端集成文档和数据库设计文档；
- 保留部门管理页面，但删除“为数据范围控制提供基础归属”等失效说明。

推荐发布顺序：先部署能兼容新旧响应的 Aries，再部署删除字段的 Leo；回滚时旧Leo仍可向新Aries返回多余字段，不影响解析。

## 5.4 阶段四：数据库清理

代码不再读写字段后，新增：

```text
src/main/resources/db/migration/V84__remove_data_scope_columns.sql
```

迁移职责保持单一：

```sql
ALTER TABLE public.sys_role
    DROP COLUMN data_scope;

ALTER TABLE public.sys_user
    DROP COLUMN data_scope;
```

实施前必须重新检查字段约束、视图、函数、索引、导出脚本和报表依赖。如果仍存在依赖，先在同一迁移中按明确依赖顺序解除；禁止使用`CASCADE`隐藏未盘点对象。

这是不可通过应用版本自动恢复的兼容边界。数据库迁移发布后，旧版本应用将无法回滚运行，因此必须满足：

- 阶段二和阶段三已经稳定运行；
- 生产数据库已完成备份并验证恢复路径；
- 不再存在读取或写入`data_scope`的应用版本；
- Flyway migrate和validate均通过；
- 回滚策略改为前滚修复，而不是回退到依赖旧字段的应用。

# 6. 验收标准

## 6.1 授权行为

- 无`resource + action`权限的主体仍返回403；
- 有`read`权限的主体可以查看该资源全部记录；
- `create/update/delete/audit/print/export`等动作仍独立授权；
- 菜单可见性与资源权限一致；
- 管理员与普通角色不再存在数据范围配置；
- 附件、打印、导出和来源候选接口不能绕过资源动作权限；
- 明确保留的“仅创建人”领域规则行为正确。

## 6.2 静态和构建检查

遵循仓库测试文件政策，不新增、恢复或运行单元测试、集成测试和端到端测试。每个实施阶段至少执行：

Leo：

```bash
mvn -DskipTests verify
```

Aries：

```bash
npm run typecheck
npm run lint
npm run build
```

数据库阶段执行Flyway migrate与validate，并运行现有非测试烟雾脚本检查管理员、角色和核心业务API。

## 6.3 残余风险

由于仓库政策禁止恢复或新增生产测试，运行时授权回归主要依赖静态检查、构建、烟雾检查和发布观察。以下风险必须在发布记录中显式接受：

- 某些业务人员看到此前不可见的历史记录；
- 某些动作过去依赖DataScope间接拒绝，移除后需要补充领域守卫；
- 原生SQL删除创建人条件后，报表结果量和查询成本可能增加；
- 全局搜索、导出和打印的数据量可能增长。

# 7. 回滚策略

## 阶段一至阶段三

- 数据库字段仍在时，可以回滚到旧Leo版本；
- Aries保持对多余字段宽容，可以独立回滚；
- 若发现不应全量可见的资源，优先撤回兼容版本并明确该资源的领域规则，不新增临时通用scope类型。

## 阶段四之后

- 旧应用依赖已删除字段，不能直接回滚；
- 发生问题时使用更高版本Flyway恢复必要字段或执行前滚修复；
- 禁止修改或删除已经执行的`V84`迁移。

# 8. 实施检查清单

## 开发前

- [x] 重新检查`leo`和`aries`的分支、同步状态和工作区；
- [x] 确认生产仍为单企业、单业务部门；
- [ ] 取得业务负责人对“有资源权限即可访问全部记录”的书面确认；
- [ ] 枚举所有需要保留的创建人/负责人领域规则；
- [x] 重新确认当前最大Flyway版本。

## 阶段一

- [x] 停止DataScope产生行级过滤；
- [x] Aries隐藏数据范围配置和展示；
- [x] 保留数据库字段和接口兼容；
- [ ] 完成全量数据可见性观察。

## 阶段二和阶段三

- [x] 删除后端上下文、策略、解析和查询过滤；
- [x] 删除认证响应和权限快照中的scope；
- [x] 删除Aries Schema、Store、表单、表格和文案；
- [x] 复核附件、打印、导出、搜索、报表和来源候选；
- [x] 执行非测试质量检查；
- [x] 执行部署后的只读运行时烟雾检查；
- [ ] 执行需认证的写入型业务烟雾检查（缺少`LEO_SMOKE_PASSWORD`）。

## 阶段四

- [x] 确认没有`data_scope`活动代码依赖；
- [x] 创建新的递增Flyway迁移；
- [x] 生成迁移后备份并验证归档可读性和校验和；
- [x] 在隔离临时数据库中完成恢复演练；
- [x] 执行Flyway migrate和validate；
- [x] 更新数据库设计和接口文档。

# 9. 重新引入行级授权的触发条件

出现以下任一情况时，重新进行架构评估：

- 引入第二个企业、租户或独立核算主体；
- 出现多个需要相互隔离的业务部门；
- 需要按客户、项目、仓库、区域或负责人隔离数据；
- 合规要求数据库层强制行级隔离；
- 多个服务直接访问同一业务数据库，无法保证统一的数据访问入口。

重新设计时优先建立明确的归属字段和授权关系，再比较应用层查询约束与PostgreSQL RLS；不要直接恢复当前基于创建人集合的通用实现。

# 10. 本地实施记录

执行环境：2026-07-16，本地`dev`环境，PostgreSQL 18.4，数据库`leo`，应用数据库用户`leo`。

## 10.1 Flyway执行结果

- Maven构建将`V84__remove_data_scope_columns.sql`复制到运行目录后，Spring DevTools于2026-07-16 15:52:42自动重启应用；
- Flyway在迁移前成功校验84条迁移，随后执行`84 - remove data scope columns`；
- `flyway_schema_history`中`V84`的`installed_rank`为84且`success=true`；
- 后续多次DevTools重启均成功校验84条迁移；
- `scripts/deploy/verify-flyway-target.sh . 84`通过；
- `information_schema.columns`确认`sys_role.data_scope`和`sys_user.data_scope`均已不存在；
- 迁移后`sys_role`为5行，`sys_user`为2行，未发现迁移导致的行删除。

V84由已运行的开发服务自动执行，执行前没有生成专项数据库备份。该事实不可由迁移后的备份补正，生产或共享环境执行迁移时仍必须先完成备份和恢复演练。

## 10.2 迁移后备份

- 归档：`.local/backups/leo-post-v84-20260716T183251+0800.dump`；
- 格式：PostgreSQL 18.4自定义格式，`--no-owner --no-acl`；
- 文件大小：1,701,482字节；
- 文件权限：`0600`；
- `pg_restore --list`可读取3999个归档条目；
- SHA-256：`5e3862eede48da9db9608a69d56ee161e92a15036d85741c94dfc6e64a4ddacb`。

当前应用用户没有`CREATEDB`权限，环境也未配置`LEO_DB_ADMIN_PASSWORD`。恢复演练因此使用独立的临时PostgreSQL 18.4实例，仅监听`.local/restore-validation/`下的Unix Socket，不连接现有开发数据库。恢复后核对结果如下：

- `flyway_schema_history`共84条，`V84`成功；
- `sys_role.data_scope`和`sys_user.data_scope`均不存在；
- `sys_role`为5行，`sys_user`为2行；
- `public` schema包含202张基础表；
- 临时实例正常停止，临时数据目录已清理。

## 10.3 运行时验证

- `GET http://127.0.0.1:11211/api/health`返回HTTP 200和`UP`；
- `GET http://127.0.0.1:11211/api/version`返回HTTP 200；
- `GET http://127.0.0.1:3100/`返回HTTP 200；
- 未认证访问`/api/materials`、`/api/user-accounts`和`/api/inventory-report`均返回HTTP 403；
- 公共认证探针`/api/auth/ping`返回HTTP 200；
- 最近一次成功启动后的后端日志未发现新的应用启动失败、Flyway异常或SQL错误；
- 环境未配置`LEO_SMOKE_PASSWORD`，因此没有运行会创建和清理用户、角色、附件、API Key及业务单据的写入型烟雾脚本。

上线前剩余门禁是：使用受控测试账号验证已认证主体的403拒绝、资源动作授权和核心业务API。未完成该项前，不应把本地验证结果视为生产发布批准。
