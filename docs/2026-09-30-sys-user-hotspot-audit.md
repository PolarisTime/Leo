# sys_user 同行更新热点排查审计（2026-09-29 压测建议第 5 条）

- 审计时间：2026-09-30
- 审计对象：`leo` 后端 @ `10030a42`（只读审计，未改业务代码）
- 承接：`perf/reports/2026-09-29-dev-stress.md` 建议第 5 条「关注 `sys_user` 行热点：除登录外，
  任何高频更新同一 `sys_user` 行的逻辑都存在同类风险」；以及 `perf/reports/2026-09-30-gap-closure.md` §7.3。

## 一、实体与写入机制基线

- 实体：`com.leo.erp.auth.domain.entity.UserAccount` → `@Table(name="sys_user")`，
  `@Version private Long version`（`UserAccount.java:26-27`）。无 `@DynamicUpdate` →
  **任何 JPA save 都是整行 UPDATE**（含 `preferences_json`、`last_login_date`、`credential_version` 全部列）。
- 乐观锁冲突统一由 `GlobalExceptionHandler` 映射 409（`CONCURRENT_MODIFICATION`），**冲突后不重试**；
  悲观锁等待超时映射 409。`OptimisticLockRetryExecutor`（`common/transaction`）目前仅 `SalesOrderService` 接入，
  auth/account 路径均未接入。
- 连接池 `maximum-pool-size` 默认 20；登录审计节流默认 60s；access token 10 分钟。

## 二、修改 sys_user 行的全部路径（穷举：3 类 9 处）

### A. JPA 实体 save/update（除 1 处例外均先取悲观锁 `SELECT ... FOR UPDATE`）

| # | 路径 | 调用链 | 频率画像 | @Version 冲突 | 风险 |
|---|---|---|---|---|---|
| A1 | 个人资料更新 | `V2UserAccountController.update:44` → `UserAccountService.updateCurrent:50`（:51 悲观锁 → :55 save） | 用户手动，低频 | 不可能（锁内） | 低 |
| A2 | 修改密码 | `changePassword:61`（:62 锁 → bcrypt :63-70 → :75 saveAndFlush → :76 吊销会话） | 极低频 | 锁内不可能；**bcrypt 与吊销循环全程持锁** | 中低 |
| A3 | 自助状态变更 | `UserAccountService.updateStatus:81` | **主代码零调用方（疑似死路径）** | 锁内不可能 | 低（潜伏） |
| A4 | 管理端建号/编辑/启停/重置密码/软删 | `V2UserAdminController:67/76/85/94/105` → `UserAdminService:88/119/135/149/166` | 管理员手动，低频 | 锁内不可能 | 低 |
| A5 | 首启初始化建号 | `InitialAccountProvisioningService:85` | 一次性 | INSERT | 忽略 |
| A6 | **登录链路 `save(user)`** | `LoginService.login:76` → `TokenIssuanceService.issueTokens:137` | **每次登录** | 见发现 2 | **中（潜伏）** |

### B. 原生/JPQL 原子 UPDATE（绕开 version）

| # | 路径 | 调用链 | 频率画像 | 有无 version 冲突 | 风险 |
|---|---|---|---|---|---|
| B1 | 登录审计 `last_login_date`（`bc0041ed` 修复） | `LoginService:75` → `LoginAuditService:46` → `UserAccountRepository.updateLastLoginDate:39` | 每次登录触发判断，60s 节流落库 | **无**（SET/WHERE 均无 version） | **中**（行锁串行化，见发现 1） |
| B2 | 偏好设置 `preferences_json` | `savePreferences:63` → `UserAccountPreferenceService:37` → `updatePreferencesJson:59` | 前端列排序/显隐/列宽/重置每次 PUT（`aries useColumnSettingsSupport.ts:312/321/333/348`），中频、仅本用户行 | 无（原生 UPDATE；last-writer-wins 对列设置可接受） | 低-中低（见发现 3） |

### C. 触发器 / 定时任务 / 迁移

- **DB 触发器：不存在**（全 migration 中 `sys_user` 无 TRIGGER；唯一触发器在 `sys_operation_log`）。
- **定时任务批量写：不存在**（6 处 `@Scheduled` 均不触碰 `sys_user`）。
- 迁移期 SQL（V99/V103/V140）为 Flyway 一次性修复，非运行时路径。
- **每请求路径不写 `sys_user`**：`JwtAuthenticationFilter.touchSessionQuietly` 只写 Redis；
  `AuthenticatedUserCacheService` 只读 + Redis。

## 三、重点疑点核查结论

1. **偏好/资料接口**：存在。偏好 = B2 原子 UPDATE；资料 = A1 悲观锁 save。
2. **`credentialVersion` 高频递增**：**不存在**——仅改密码（`changePassword:71-74`）与重置密码（`resetPassword:158-159`）递增，均手动低频、锁内；**token 刷新不写 `sys_user` 行**。
3. **token 刷新**：不写行，但**每次刷新对 `sys_user` 取悲观写锁**（`TokenIssuanceService.refresh:61` → `findUserByIdForUpdate`），且**先加锁(:61)后校验会话(:64-88)**，无效刷新也白持锁；锁的语义是与改密吊销会话互斥，不能直接删。
4. **登录附带写**：登录 IP → `auth_refresh_token.login_ip`；失败计数/锁定 → Redis（`LoginAttemptService`）；`sys_user` 只有 `last_login_date` 一条。
5. **启停写 `sys_user`（A4 锁内）；分配角色不触碰 `sys_user` 行**（只写 `sys_user_role`）。
6. **定时/审计批量写**：不存在。
7. **「`bc0041ed` 原子 UPDATE 仍撞 version」反例：不成立**。行级证据：`UserAccountRepository:39-40` 的 SET/WHERE 无 `version`；JPQL 批量 UPDATE 不比对也不自增 version——既不会 409，也不会使他处版本判等失效。

## 四、关键发现（行级证据）

### 发现 1（中风险）：登录原子 UPDATE 的行锁窗口覆盖整个登录尾段，且 60s 节流在并发突发下失效
- 顺序：`:75 recordSuccessfulLogin`（发原子 UPDATE，**取得行锁**）→ `:76 issueTokens`（创建会话、写 `auth_refresh_token`、权限查询）→ `:77` 操作日志 → 提交。PostgreSQL 行锁持到提交，**并发同账号登录在行锁上排队并各占一条连接**。
- 节流判断读**加载时的旧快照**（`LoginAuditService:51`），20 并发在首个事务提交前全部通过 → **20 条 UPDATE 全部发出并串行**。
- `bc0041ed` 提交信息自认：「20 并发仍受同账号会话管理串行化限制并会打满连接池……容量特征」。即 409 已消除、**行锁串行化残留**，与「20 并发打满 20 连接」同型。
- 修法：`recordSuccessfulLogin` 移到 login() 最末（锁窗口缩到「仅审计+提交」）；节流条件并入 UPDATE WHERE（PG 的 EvalPlanQual 会让后到者在新版本上重评条件、直接跳过写入），并发突发下实际只写 1 条。

### 发现 2（中风险·潜伏地雷）：`TokenIssuanceService:137 save(user)` 是每次登录必经的整行版本写入口
- `bc0041ed` 删除 `setLastLoginDate` 后实体不再变脏，save 目前 0 SQL；但**未来任何人在登录链路给 user 加一个 setter**，save 立即变成整行 `version=?` 写——轻则覆盖刚写好的审计值、重则复活 20 并发 409 风暴（`UserAccountRepository:32-36` 注释明文记载该陷阱）。
- 修法（一行）：删除 `:137` 的 `save(user)`，或加「登录不改实体」注释 + 守护测试断言登录事务对 `sys_user` 最多发出 B1 一条 UPDATE。

### 发现 3（中低）：`updatePreferencesJson` 携带被自家 javadoc 明令警告的 `clearAutomatically=true`
`UserAccountRepository:59` 与 `:32-36` 的警告组合当前「靠调用点巧合安全」（该事务内没有后续 `sys_user` 读）。修法：去标志，或补护栏注释 + 「事务内不得再读 sys_user」单测。

### 发现 4（中低）：改密/重置密码在悲观锁内执行 bcrypt（各约 100ms×2），锁期间阻塞同账号登录审计与刷新锁
修法：`matches/encode` 移到加锁前，锁内复核 hash 未变后写入。

### 发现 5（中低）：刷新路径先加锁后校验
`TokenIssuanceService.refresh:61` 加锁 → `:64-88` 才校验；无效刷新也持锁。修法：锁挪到定位 active 会话之后并在锁内复核，或改用 `auth_refresh_token` 条件 UPDATE（`WHERE revoked_at IS NULL`）做互斥。

## 五、结论表

| 路径 | 频率画像 | @Version 冲突 | 现状 | 风险 | 建议 |
|---|---|---|---|---|---|
| 登录审计 `last_login_date` | 每次登录（60s 节流，突发下失效） | 无 | `bc0041ed` 原子 UPDATE | **中**（行锁串行） | P2：末尾执行 + 节流进 WHERE |
| 登录 `save(user)` | 每次登录（当前 0 SQL） | 潜伏整行写 | 无处置 | **中（潜伏）** | **P1：删除/注释+守护测试** |
| token 刷新行锁 | 每会话 ≤10min | 无（只读） | 悲观锁互斥 | 中低 | P3：加锁时机后移 |
| 偏好设置 | 交互中频，本用户行 | 无 | 原子 UPDATE | 低-中低 | P5：去 `clearAutomatically` 或加护栏 |
| 个人资料 | 手动低频 | 无（锁内） | 悲观锁 | 低 | 无需处理 |
| 改密/重置密码 | 极低频 | 无（锁内） | bcrypt 持锁 | 中低 | P4：bcrypt 移出锁 |
| 自助状态 `updateStatus` | **死路径** | 无 | 悲观锁 | 低（潜伏） | P6：确认删除或文档化 |
| 管理端五操作 | 管理员低频 | 无（锁内） | 悲观锁 | 低 | 无需处理 |
| 分配角色 / 登录 IP / 失败计数 / 定时任务 / 触发器 | — | **不写 `sys_user`** | — | 无 | 确认无需处理 |

## 六、修复优先级（本次审计不改代码）

1. **P1** 删除 `TokenIssuanceService:137 save(user)`（+ 守护测试）
2. **P2** 缩小登录行锁窗口：`recordSuccessfulLogin` 移至 login() 末尾 + 节流条件进 UPDATE WHERE
3. **P3** refresh 持锁时机优化
4. **P4** 改密/重置密码 bcrypt 移出悲观锁
5. **P5** `updatePreferencesJson` 的 `clearAutomatically` 复核
6. **P6** `UserAccountService.updateStatus` 死路径处置
