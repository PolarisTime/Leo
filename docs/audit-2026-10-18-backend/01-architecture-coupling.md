# 后端架构与耦合审计（leo）

- 审计日期：2026-10-18
- 基线提交：`0ba7f12c`（`chore(release): 13.2.1`）
- 技术栈：Java 21 / Spring Boot 3.5.16 / Spring Modulith 1.4.13 / Spring Data JPA / PostgreSQL / Redis
- 审计性质：**只读审计**，未修改任何业务代码、配置或 Flyway 脚本。本文件是唯一写入产物。
- 复现方式：除特别说明外所有命令在 `leo/` 下执行。

---

## 摘要

`leo` 是「单 Maven 模块 + 顶层包划分」的模块化单体：15 个顶层包、1060 个 Java 文件、约 91k 行（`find src/main/java/com/leo/erp -name '*.java' -exec cat {} + | wc -l` → `91158`）。

**web 分层纪律良好**：66 个 Controller 中 **0 个**引用 Repository、**0 个**引用 `domain.entity`。耦合缺陷集中在 **service 层横向穿透**与 **`common` 职责膨胀**。

**架构门禁已存在且选型正确**（需先纠正一个常见误判）：`scripts/check-java-module-boundaries.sh` 用 **ArchUnit + `FreezingArchRule`**（字节码级，非文本扫描），`scripts/verify-modulith-architecture.sh` 调官方 `ApplicationModules.of(...).verify()`。两者都在 CI `static-analysis` job 第一步执行（`.github/workflows/ci.yml:33-37`）。实测均通过：

```bash
cd leo && time bash scripts/check-java-module-boundaries.sh 2>&1 | tail -2
# INFO ModuleBoundaryVerifier -- Architecture boundary check passed: 1346 classes,
#   160 baselined restricted dependencies, 25 baselined cyclic edges, 24 baselined status writes
# real 0m13.7s
cd leo && time bash scripts/verify-modulith-architecture.sh 2>&1 | tail -2
# INFO ModulithArchitectureVerifier -- Spring Modulith verification passed for 8 explicitly annotated modules
# real 0m19.6s
```

**问题不在「有没有门禁」，而在门禁的实际保护半径远小于表观半径。** 三个结构性盲区（详见 P1-1 / P1-4）：

1. **Modulith 只认 8 个模块**（输出即 `for 8 explicitly annotated modules`）。`auth/common/finance/inventory/search/security/system` 这 7 个顶层包无 `@ApplicationModule`，配合 `detection-strategy: explicitly-annotated`（`application.yml:116`）被判为**非模块**，对它们的依赖完全不受约束——这解释了为何 8 个模块的 `allowedDependencies` **全部与实际不符却全部 verify 通过**。
2. **ArchUnit 用 baseline 冻结存量违规**：3 条规则共 **209 行**基线（160 restricted + 25 cyclic + 24 status write），语义是「不许新增」而非「必须为零」。
3. **基线无收敛机制且写入未去重**：`6100a3ca` 160 行只有 **40 条唯一**（单条最多重复 10 次），`525fb6ef` 24 行里 21 条唯一；`--refreeze` 只有追加/重写，无到期时间戳、无条目数单调递减断言。

**严重度统计：P0 = 0、P1 = 5、P2 = 4、P3 = 4**（P2-2 内含 a/b 两个子项、P2-1 为澄清项）。

### 模块规模

| 模块 | 文件 | 行数 | `@ApplicationModule` | `api/` |
|---|---:|---:|:--:|:--:|
| system | 138 | 10941 | ❌ | ❌ |
| master | 137 | 9998 | ✅ | ✅ 14 |
| common | 135 | 10642 | ❌ | ✅ 12 |
| sales | 127 | 13099 | ✅ | ✅ 12 |
| market | 91 | 8803 | ✅ | ❌ |
| purchase | 80 | 8861 | ✅ | ✅ 10 |
| finance | 67 | 7243 | ❌ | ❌ |
| statement | 62 | 5239 | ✅ | ✅ 6 |
| attachment | 55 | 4122 | ✅ | ✅ 8 |
| auth | 53 | 2923 | ❌ | ✅ 9 |
| security | 47 | 3766 | ❌ | ❌ |
| logistics | 31 | 2458 | ✅ | ✅ 4 |
| inventory | 20 | 1919 | ❌ | ✅ 4 |
| search | 10 | 948 | ❌ | ❌ |
| allocation | 6 | 182 | ✅ | ✅ 3 |
| **合计** | **1060** | **91158** | **8 / 15** | **10 / 15** |

```bash
cd leo; for d in src/main/java/com/leo/erp/*/; do m=$(basename $d); \
  printf "%-12s %4s %6s " $m $(find $d -name '*.java'|wc -l) $(find $d -name '*.java' -exec cat {} +|wc -l); \
  [ -f $d/package-info.java ] && printf "MODULE " || printf "none   "; [ -d $d/api ] && echo api:$(ls $d/api|wc -l) || echo api:-; done
# 合计：15 包 / 1060 文件 / 91158 行；8 个 MODULE、10 个有 api
```

### 模块依赖矩阵

行 = 引用方，列 = 被引用方，值为 `import com.leo.erp.<目标>.` 出现次数，`.` 表示 0。

| from \ to | alloc | attac | auth | common | finance | inven | logis | market | master | purch | sales | search | secur | state | system |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| **allocation** | – | 3 | . | . | . | . | . | . | . | . | . | . | . | . | . |
| **attachment** | . | – | . | 56 | . | . | . | . | . | . | . | . | 13 | . | 4 |
| **auth** | . | . | – | 43 | . | . | . | . | . | . | . | . | 16 | . | 6 |
| **common** | . | . | . | – | . | . | . | . | . | . | . | . | 10 | . | 1 |
| **finance** | . | 2 | . | 170 | – | . | 1 | . | 9 | 4 | 1 | . | 12 | 9 | 6 |
| **inventory** | . | . | . | 19 | . | – | . | . | 2 | 2 | 4 | . | 6 | . | . |
| **logistics** | . | 1 | . | 62 | . | . | – | . | 3 | . | 10 | . | 3 | . | 4 |
| **market** | . | . | . | 127 | . | . | . | – | 14 | 3 | . | . | 24 | . | . |
| **master** | . | 6 | . | 284 | . | . | . | . | – | . | . | . | 24 | . | 6 |
| **purchase** | 2 | 2 | 1 | 158 | . | 3 | . | . | 3 | – | . | . | 11 | . | 8 |
| **sales** | . | 4 | . | 249 | . | 6 | . | . | 9 | 8 | – | . | 20 | . | 19 |
| **search** | . | . | . | 7 | . | . | . | . | . | . | . | – | 4 | . | . |
| **security** | . | . | 3 | 42 | . | . | . | . | . | . | . | . | – | . | 2 |
| **statement** | . | 7 | . | 103 | . | . | 8 | . | 3 | . | 14 | . | 6 | – | 2 |
| **system** | . | 3 | 12 | 140 | . | . | . | . | 2 | . | . | . | 18 | . | – |

```bash
cd leo; mods=$(ls src/main/java/com/leo/erp | grep -v LeoApplication)
printf "%-12s" "from\\to"; for m in $mods; do printf "%7s" "${m:0:6}"; done; echo
for a in $mods; do printf "%-12s" "$a"; for b in $mods; do n=$(grep -rho "import com\.leo\.erp\.$b\." src/main/java/com/leo/erp/$a 2>/dev/null|wc -l); \
  [ "$n" -gt 0 ] && printf "%7s" "$n" || printf "%7s" "."; done; echo; done   # 输出即上表
```

### 读矩阵的三个结论

**(1) `common` 是绝对重心，但不是纯技术底座。** 每行最大值几乎都落在 `common` 列；而 `common` 自身反向依赖 `security`(10) 与 `system`(1)——`JwtAuthenticationFilter`、`PermissionChecker`、`PermissionCodes`×3、`RequirePermission`×2、`SecurityPrincipal`×3、`InitialSetupTokenFilter`（`grep -rh "import com\.leo\.erp\.\(security\|system\)\." src/main/java/com/leo/erp/common --include=*.java`）。

**(2) 业务模块间存在真实双向环，且是不对称结构**（一边走 api、一边直吃内部类型）：

| 边 | 次数 | 走向 | 证据 |
|---|---:|---|---|
| `sales -> inventory` | 6 | ✅ 全走 `inventory.api.*` | `InventoryTransactionCommand` 等 |
| `inventory -> sales` | 4 | ❌ `repository`+`domain.entity` | `sales.outbound.repository.SalesOutboundRepository` |
| `purchase -> inventory` | 3 | ✅ 全走 `inventory.api.*` | 同上 |
| `inventory -> purchase` | 2 | ❌ `repository`+`domain.entity` | `purchase.inbound.repository.PurchaseInboundRepository` |
| `statement -> sales` | 14 | ✅ 全走 `sales.api.*` | `SalesOrderStatementSourceQuery` 等 |
| `logistics -> sales` | 10 | ✅ 全走 `sales.api.*` | `SalesOrderSourceSnapshot` 等 |
| `finance -> statement` | 9 | ✅ 全走 `statement.api.*` | `CustomerStatementApi` 等 |
| `auth -> security` | 16 | ❌ 直吃 `security.rbac.repository/service` | `SysRolePermissionRepository` |
| `security -> auth` | 3 | ❌ 直吃 `auth.repository`+`domain.entity` | `UserAccountRepository` |

**(3) `system` 是被 8 模块依赖的隐性内核，却没有 `api` 包。** 引用集中于 `system.printtemplate.service.*`、`system.operationlog.*`、`system.company.*`，全是内部实现类。`system` 重构会编译期打断 8 个模块，且它没有 `@ApplicationModule`，Modulith 侧零约束。

### P0 计数为 0 的判定

web 层无泄漏（§3.1）；跨模块状态写入有 `CrudStatusGuard` + ArchUnit `statusWriteRule` 双保险；事件可靠性已正确落地（P2-1：`event_publication` 表 + `completion-mode: delete` + 重启重放 + 按 `eventId` 幂等消费）；未发现跨模块同步写同一聚合或事务边界错误。故最高级别为 P1——结构性问题会让**后续演进的缺陷长期不被拦截**，但当前不产生错误数据。

---

## 1. 发现清单

> P0=生产事故/数据错误；P1=显著阻碍演进或高频缺陷源；P2=局部可维护性；P3=风格建议。

### P1-1　Modulith 只覆盖 8 / 15 个顶层包，7 个包不是「模块」因而完全不受约束

本报告最重要的一条，也是「门禁看起来有效」的最大错觉来源。

**证据 1**——运行输出：`Spring Modulith verification passed for 8 explicitly annotated modules`（见摘要）。

**证据 2**——检测策略固定为只认注解，`scripts/ModulithArchitectureVerifier.java:17-19, 26-28`：
```java
private static final String DETECTION_STRATEGY_PROPERTY = "spring.modulith.detection-strategy";
private static final String EXPLICITLY_ANNOTATED = "explicitly-annotated";
System.setProperty(DETECTION_STRATEGY_PROPERTY, EXPLICITLY_ANNOTATED);
ApplicationModules modules = ApplicationModules.of(LeoApplication.class);
```
`application.yml:114-116` 亦固化该策略（注释写「渐进式纳管」）。

**证据 3**——15 个顶层包仅 8 个带 `package-info.java`：
```bash
cd leo && ls -d src/main/java/com/leo/erp/*/ | xargs -n1 basename | while read m; do \
  [ -f src/main/java/com/leo/erp/$m/package-info.java ] || echo "非模块: $m"; done
# 非模块: auth  common  finance  inventory  search  security  system
```

**官方语义核对**（[Spring Modulith – Fundamentals](https://docs.spring.io/spring-modulith/reference/fundamentals.html)）：`explicitly-annotated` 下「only consider packages explicitly annotated」。这 7 个包**不是应用模块**，对它们的依赖既不受 cycle 规则约束，也不受 `allowedDependencies` 约束。

**最强佐证：8 个模块的 `allowedDependencies` 全部与实际不符，却全部 verify 通过。**

| 模块 | 声明的 allowedDependencies | 实测非 common 跨模块依赖（全部越界） |
|---|---|---|
| sales | `attachment::api, master::api, purchase::api` | security 20, system 19, master 9, purchase 8, attachment 4, inventory 6 |
| purchase | `allocation::api, attachment::api, master::api` | security 11, system 8, inventory 3, master 3, attachment 2, allocation 2 |
| statement | `attachment::api, logistics::api, master::api, sales::api` | sales 14, logistics 8, attachment 7, security 6, master 3, system 2 |
| logistics | `attachment::api, master::api, sales::api` | sales 10, system 4, security 3, master 3, attachment 1 |
| master | `attachment::api` | security 24, system 6, attachment 6 |
| market | `master::api, purchase::api` | security 24, master 14, purchase 3 |
| attachment | `{}`（空数组） | common 56, security 13, system 4 |
| allocation | `{}`（空数组） | attachment 3 |

`attachment` 最极端：声明空数组（`attachment/package-info.java:1`），实际 73 次跨包 import（`grep -rh "import com\.leo\.erp\.\(common\|security\|system\)\." src/main/java/com/leo/erp/attachment --include=*.java | wc -l` → `73`），verify 依然绿。官方原文：「If those are configured, dependencies to other application modules are rejected」——**只约束「其他应用模块」**；`common/security/system` 不是模块，73 次依赖全部合法。这不是团队写错声明，而是**模块身份缺失导致的系统性漏检**。

**两张图不同，这是关键**：ArchUnit 的 `cyclicEdgeRule` 以「15 个顶层包」为节点，Modulith 的 `verify()` 以「8 个模块」为节点。25 条边里 23 条至少一端是非模块包；剩余 2 条两端都是模块：

```bash
cd leo && M="sales master statement logistics purchase attachment market allocation"; \
awk -v m=" $M " -F'edge: ' '/cyclic module edge/{split($2,a," -> "); \
  if (index(m," "a[1]" ")>0 && index(m," "a[2]" ")>0) print "BOTH-MODULE: "a[1]" -> "a[2]}' \
  config/archunit-boundary-store/2c7ea9fa-4727-455c-9b32-c317a66eed2a
# BOTH-MODULE: master -> attachment
# BOTH-MODULE: sales -> purchase
```

这两条不构成 8 模块图上的环，且都在各自 `allowedDependencies` 中显式允许，所以 verify 通过是**正确**的。**准确表述**：Modulith 报告的「8 模块图无环」在数学上为真，但掩盖了 15 包图上真实存在的多个环——环经非模块包闭合，Modulith 看不到。例：`sales -> inventory` 走 `inventory.api`（合规）、`inventory -> sales` 直吃 `sales.outbound.repository`（违规）；因 `inventory` 不是模块，Modulith 看不见这条回边，环被判不存在。**verify 通过不能作为「系统无环」的证据。**

**影响**：`allowedDependencies` 产生虚假安全感（8/8 不符）；跨「模块 ↔ 非模块」依赖（`sales -> security` 20、`master -> security` 24、`market -> security` 24）完全无人看守。

**修复建议**：为 7 个包补 `@ApplicationModule` 与 `api/package-info.java`，`allowedDependencies` 如实声明。顺序：先 `search`/`inventory`/`auth`，再 `system`/`security`/`common`（需先做 P1-3 决策）。**代价**：前三个 1.5 人日；后三个 5–8 人日。**风险**：低（只加校验），但会立刻暴露大量违规，必须与 §4 同步，否则会被迫 `--refreeze` 重演腐化。

---

### P1-2　25 条循环边被冻结为合法状态：3 组共 6 条可立即消除，其余根因在 common

**证据 1**——基线共 25 行（`wc -l` 实测），涉及 `attachment`/`auth`/`common`/`inventory`/`master`/`purchase`/`sales`/`security`/`system` 9 个顶层包，构成两个强连通分量：`{system, auth, security, common, attachment, master}` 与 `{sales, purchase, inventory}`。
```bash
cd leo && cat config/archunit-boundary-store/2c7ea9fa-4727-455c-9b32-c317a66eed2a
```

**证据 2**——规则是官方 `FreezingArchRule`，`scripts/ModuleBoundaryVerifier.java:99-101`：
```java
FreezingArchRule.freeze(restrictedInternalsRule).check(classes);
FreezingArchRule.freeze(cyclicEdgeRule).check(classes);
FreezingArchRule.freeze(statusWriteRule).check(classes);
```

**逐组判定：**

| 组 | 边数 | 判定 | 依据 |
|---|---:|---|---|
| `inventory -> purchase` / `purchase -> inventory` | 2 | **真环，可立即消除** | 反向仅 2 个 import 直吃 `purchase.inbound.repository/domain.entity`；正向 3 个全走 `inventory.api.*` |
| `inventory -> sales` / `sales -> inventory` | 2 | **真环，可立即消除** | 反向 4 个直吃 `sales.outbound/returns` 的 repo+entity；正向 6 个全走 `inventory.api.*` |
| `security -> auth` / `auth -> security` | 2 | **真环，1–2 人日** | `security -> auth` 中 `auth.api.AuthenticationAccountQuery` 已合规，另 2 个直吃 `auth.repository.UserAccountRepository` + `auth.domain.entity.UserAccount` |
| `common -> security` / `common -> system` | 2 | **真环，根因** | `common` 引 `security.jwt.JwtAuthenticationFilter`、`security.permission.*`、`system.setup.web.InitialSetupTokenFilter`；这是 6 元强连通分量的根 |
| `{auth,attachment,master,system} -> system` 与 `system -> *` | 9 | **真环** | `system` 无 `api/` 包，被 8 模块依赖却无契约层（P2-2a） |
| `attachment -> common`、`attachment -> security`、`master -> security` 等 | 8 | **部分误判** | 横切基础设施依赖（`RequirePermission`/`PermissionCodes` 注解与常量），**环的责任在 `common`/`security` 的反向边**，把 `attachment` 当违规方去改是错的 |

**判定方法**：环里只要**一个方向全部走 `api` 包**，该方向契约合法，只需改另一方向即可断环。实测满足的方向：`sales -> inventory`(6/6)、`purchase -> inventory`(3/3)、`statement -> sales`(14/14)、`logistics -> sales`(10/10)、`finance -> statement`(9/9)。

**修复建议**：分 4 批递减（§4）。第一批（`inventory` 双向 4 条）仅涉及 `InventoryBackfillService` 与 `InventoryTransactionQueryService`，改完 25 → 21；再做 `security -> auth` → 19。**代价**：第一批 2–3 人日；降到个位数约 15–20 人日。**风险**：中（回填涉及历史数据，须补等价性断言，见 P2-2b）。

---

### P1-3　`common` 退化为「横切基础设施 + 业务口径」混合体，反向依赖 security/system

**证据 1**——135 文件 / 10642 行 / 23 个子包：
```bash
cd leo && ls src/main/java/com/leo/erp/common
# api charge concurrency config error excel exception export idempotent json mapper
# moduleexport persistence ratelimit retry service support transaction validation web
```

**证据 2**——反向依赖 11 次（见摘要结论 1）。

**证据 3**——`common` 混着**业务口径**而非纯技术能力：`common/support/` 下有 `TradeItemCalculator`（业务计算器）、`ModuleKeys`、`ModuleCatalog`、`StatusConstants`、`DocumentStatus`、`BusinessStatusValidator`、`StatusTransition`。`sales` 单模块就 import `StatusConstants` 27 次、`ModuleKeys` 16 次、`TradeItemCalculator` 11 次。

**影响**：`common` 同时是「最底层技术依赖」和「业务语义仓库」，任何模块都能无条件读到全部业务口径，**模块边界在 `common` 处失效**；`common -> security` 又使任何「纯工具类」复用都会拖入 `security`，进而拖入 `auth`。Modulith 侧因无 `@ApplicationModule` 而完全不检查。

**修复建议**：三分 `common`——(1) `common.tech`（零业务零 security：`json/excel/retry/ratelimit/persistence/transaction/validation/error/exception`）；(2) `common.security.spi`（把 `RequirePermission`/`PermissionCodes`/`SecurityPrincipal` 抽成不依赖实现层的注解+接口，由 `security` 实现，**同时解掉 `common -> security` 环**）；(3) 业务口径下沉到领域模块或建成声明 `allowedDependencies={}` 的 `shared-kernel`。**代价** 8–12 人日（3 个 PR）；**风险** 中高——三处高频引用需逐调用点确认归属，建议「先建 shared-kernel 再迁」两步法。

---

### P1-4　基线 160 行只有 40 条唯一，虚高 4 倍且无收敛机制

**证据 1**——重复度：
```bash
cd leo && wc -l < config/archunit-boundary-store/6100a3ca-7a4c-4f80-a896-acf74de82801   # 160
sort -u config/archunit-boundary-store/6100a3ca-7a4c-4f80-a896-acf74de82801 | wc -l  # 40
sort config/archunit-boundary-store/6100a3ca-7a4c-4f80-a896-acf74de82801 | uniq -c | sort -rn | head -4
# 10 ...InventoryBackfillService (inventory) -> SalesReturnItem (sales)
# 10 ...InventoryBackfillService (inventory) -> SalesReturn (sales)
# 10 ...InventoryBackfillService (inventory) -> SalesOutboundItem (sales)
# 10 ...InventoryBackfillService (inventory) -> SalesOutbound (sales)
```

**证据 2**——`525fb6ef` 同样重复：24 行 / 21 唯一（`UserAdminService -> UserAccount.setStatus` ×2、`RoleService -> SysRole.setStatus` ×3）。

**证据 3**——无到期/递减机制：
```bash
cd leo && grep -rn "expire\|deadline\|maxAge\|stale" scripts/ModuleBoundaryVerifier.java   # (无输出)
grep -n "CI" scripts/ModuleBoundaryVerifier.java
#  62-66: if (refreeze && ci) { LOGGER.error("Refusing to refreeze ... while CI=true"); System.exit(2); }
```
`--write-baseline` 是唯一写入口（`check-java-module-boundaries.sh:41`），只做追加/重写，无条目数单调递减断言、无到期时间戳、无重复行检测。

**证据 4**——基线被反复「更新以匹配」：
```bash
cd leo && git log --oneline -- config/archunit-boundary-store/ | head -5
# 3cf71840 refactor(sales-contract): 状态更名定稿并收紧作废与受保护状态口径
# 3b055831 feat(market): 报价单支持行级保存与编辑签出锁
# 8068e12c refactor(sales): 退货与对账跨模块解耦以通过 Modulith 边界校验
# 36673b52 chore(arch): 更新模块边界基线以纳入新增跨模块依赖   ← 危险模式
# 2526a2dc fix(ci): 更新 ArchUnit 冻结基线以匹配服务拆分后的类
```
`36673b52 chore(arch): 更新模块边界基线以纳入新增跨模块依赖` 是最危险模式：门禁报红时选「把新违规纳入基线」而非「修依赖」，门禁退化为日志。

**一个与直觉相反的事实**：重复行**不削弱**运行时防护（`FreezingArchRule` 按违规描述匹配，重复只是冗余），但**误导人**——「160 baselined restricted dependencies」让人以为有 160 个不同问题，实际 40 个，直接影响修复优先级与本类报告准确性。

**修复建议**：(1) 立即去重（160→40、24→21）；(2) 新增 `config/archunit-boundary-store/LIMITS`（`cyclic=25 restricted=160 status=24` + `deadline`），CI 断言实际行数 ≤ LIMITS；(3) 行数下降时强制同步下调 LIMITS。
**代价**：去重 0.5 人日（纯数据零风险）；LIMITS 1 人日。**风险**：低。**全报告性价比最高的一条。**

---

### P1-5　实体状态写入门禁依赖「类名后缀白名单」，被豁免的恰是状态机核心类

**证据**——规则只匹配方法名 `setStatus` 且目标在 `domain.entity`，`scripts/ModuleBoundaryVerifier.java:185-190`：
```java
for (JavaMethodCall call : clazz.getMethodCallsFromSelf()) {
    JavaTarget target = call.getTargetOwner();
    if (!"setStatus".equals(call.getName())
            || !"domain.entity".equals(restrictedKindOf(target))) { continue; }
```
白名单（`:200-208`）：
```java
return simpleName.endsWith("ApplyService")
        || simpleName.endsWith("CompletionSyncService")
        || simpleName.endsWith("ForceCloseService")
        || "com.leo.erp.common.service.CrudStatusGuard".equals(clazz.getName());
```

**盲区**：
1. **非 `setStatus` 命名的状态写入完全不检查**（`markCompleted()`、`transitionTo()`、Builder 赋值）。当前风险可控，但这是**依赖约定而非机制**：新增一个 `transitionTo` 就会静默绕过。
2. **实体可自实现 `setStatus` 绕过契约**：
```bash
cd leo && grep -rn "void setStatus\|public .* setStatus" src/main/java --include=*.java
# common/persistence/StatusAwareEntity.java:7                void setStatus(...)   ← 契约
# market/region/domain/entity/MdQuoteRegion.java:85          public void setStatus(...)   ← 实体自实现
# market/lengthpremium/domain/entity/MdLengthPremium.java:84 public void setStatus(...)   ← 实体自实现
# + 3 处 DTO 查询条件 setter（PurchaseOrderPageCriteria:57 等，非状态机）
```
`MdQuoteRegion:25` / `MdLengthPremium:25` 均 `implements StatusAwareEntity` 却又自写 `setStatus`，绕过基类 `beforeStatusUpdate` 守卫；门禁只检查**调用方**是否白名单，不检查**实体侧**是否履行守卫契约。
3. 白名单**只看类名后缀**，不检查是否真调用了 `beforeStatusUpdate`——任何以 `ApplyService` 结尾的类都可无条件写状态。
4. **`525fb6ef` 已冻结 21 条唯一违规**，恰是状态机核心类：`SalesOrderWorkflowService`、`PurchaseOrderWorkflowService`、`PurchaseInboundWorkflowService`、`SalesReturnWorkflowService`、`SalesOutboundWorkflowService`、`CustomerStatementReversalService`、`FreightBillService`、`UserAdminService`、`InitialAccountProvisioningService`、`RoleService`(×3)。

**补充核查**——实体是否都在受限包 `domain.entity` 下（若在别处，`restrictedKindOf` 返回 null，门禁完全看不见）：
```bash
cd leo && grep -rl "^@Entity" src/main/java --include=*.java | grep -vc "domain/entity"
# 1 —— 唯一命中 common/persistence/AbstractAuditableEntity.java，它是 @MappedSuperclass 而非实体
```
结论：65 个真实体全部位于 `domain.entity`，**这一点门禁覆盖完整**。

**影响**：状态机是本仓库最核心的业务不变式（销售/采购/物流/对账全依赖状态流转），门禁对它的保护是「类名白名单 + 基线豁免」，实际约束力弱于表观。

**修复建议**：状态写入收敛到实体自身方法（`entity.applyTransition(cmd)` 内部调私有 `setStatus`），ArchUnit 改为「除实体自身外禁止任何类调用 `StatusAwareEntity.setStatus`」，白名单下线。标准 Tell-Don't-Ask 改造。
**代价**：12–18 人日（21 个豁免类 + 所有实体）。**风险**：中高——须与 342 个测试文件同步，按单据类型分批。

---

### P2-1　（澄清）事件可靠性已正确落地，非缺陷

审计初期曾怀疑事件未持久化，实测**已证伪**，记录以澄清事实、避免重复排查。

**证据 1**——`event_publication` 表存在（`V79__add_reliable_business_operation_events.sql`）：
```sql
CREATE TABLE public.event_publication (
    id uuid PRIMARY KEY, publication_date timestamptz NOT NULL, listener_id varchar(512) NOT NULL,
    serialized_event text NOT NULL, event_type varchar(512) NOT NULL, completion_date timestamptz);
CREATE INDEX idx_event_publication_incomplete ON public.event_publication (publication_date, id)
    WHERE completion_date IS NULL;
```
同一脚本给 `sys_operation_log` 加了 `event_id uuid`（唯一索引）、`trace_id`、`aggregate_type`、`event_version`。

**证据 2**——`application.yml:117-121`：`completion-mode: delete`、`republish-outstanding-events-on-restart: true`。

**证据 3**——消费者按 `eventId` 幂等（`purchase/order/audit/PurchaseOrderSnapshotAuditListener.java:22-38`）：
```java
@ApplicationModuleListener(id = "purchase-order-javers-v1")
int claimed = jdbcTemplate.update(
    "INSERT INTO jv_business_event (event_id, ...) VALUES (?, ...) ON CONFLICT (event_id) DO NOTHING", ...);
if (claimed == 0) { return; }   // 幂等短路
javers.commit(...);
```

**证据 4**——真实 `@ApplicationModuleListener` 仅 2 处（「4 处」中 2 处是 javadoc 提及）：`PurchaseOrderSnapshotAuditListener.java:22`、`BusinessOperationAuditListener.java:20`。

**结论**：事件链路（AFTER_COMMIT + REQUIRES_NEW + 持久化 + 重启重放 + 幂等消费）符合[官方推荐](https://docs.spring.io/spring-modulith/reference/events.html)，**无缺陷**。唯一改进点：补「监听器确实被调用」的集成测试（342 个测试文件中未见此类断言）。

---

### P2-2　两处「无契约层的跨模块内部依赖」：`system` 被 8 模块依赖、`inventory` 单类占基线 45%

**(a) `system` 无 `api` 包却被 8 模块依赖。** `sales -> system` 19 次中 11 次是内部类型：
```bash
cd leo && grep -rh "import com\.leo\.erp\.system\." src/main/java/com/leo/erp/sales --include=*.java | sort | uniq -c
# 2 PrintItemOptions  1 PrintItemSplitter  1 PrintSpecLengthRule  1 PrintXlsxExportLayout
# 1 PrintXlsxExportLayoutProvider  1 PrintExportFilenameService  1 CompanySettingService
# 1 CompanySetting(entity)  5 BusinessOperationEventPublisher + 3 DomainEventAudited
# + 1 OperationLogResultCollector + 1 OperationLoggable
```
`system` 是事实核心域（打印引擎+审计+租户配置），被 8 模块依赖却无 `api/`：任何内部重构都会编译期打断 8 个模块；且无 `@ApplicationModule`，Modulith 侧零约束。**建议**建 `api/`（`OperationLogApi`、`PrintTemplateApi` 返回 DTO 而非 `PrintXlsxExportLayout` 等实现类型、`CompanySettingQuery`）。**代价** 3–4 人日；**风险** 低，但打印布局系列是核心结构，抽 DTO 需仔细设计。

**(b) `inventory` 的跨模块内部依赖集中在单类，占基线 45%。**
```bash
cd leo && grep -c "InventoryBackfillService" config/archunit-boundary-store/6100a3ca-7a4c-4f80-a896-acf74de82801  # 72
sort -u config/archunit-boundary-store/6100a3ca-7a4c-4f80-a896-acf74de82801 | grep -c "InventoryBackfillService"         # 8
```
72 / 160 = 45% 行数来自 `InventoryBackfillService` 一个类（去重后仅 8 条唯一，因 ×10/×4 虚增）。它是**数据回填运维代码**，直接读 `sales`/`purchase` 的 repository+entity 做历史修复——架构上可接受，但与业务代码同级导致：(a) 严重扭曲基线指标；(b) 使 `inventory -> sales/purchase` 两条环无法靠「改走 api」消除（回填需全表读）。**建议**移出业务包（如 `com.leo.erp.ops.backfill`，独立顶层包、单列规则组），或为 `*BackfillService` 设**带到期**的独立规则组。**代价** 1–2 人日；**风险** 低；**收益**基线 160→88（去重后 40→32）。

---

### P2-3　`architecture-verification` profile 引入的 `spring-modulith-core` 完全冗余

**证据 1**——core 已由 `starter-jpa` 传递引入且为 runtime scope：
```bash
cd leo && mvn -B -ntp dependency:tree -Dincludes='org.springframework.modulith:*'
# \- spring-modulith-starter-jpa:1.4.13:compile
#    +- starter-core +- spring-modulith-core:1.4.13:runtime   ← 已存在
#    +- spring-modulith-events-api / -core / -jackson / -jpa
```

**证据 2**——开/关 profile 的 classpath **逐字节相同**：
```bash
cd leo && mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp-noprofile.txt \
&& mvn -q -Parchitecture-verification dependency:build-classpath -Dmdep.outputFile=target/cp-profile.txt \
&& diff <(tr ':' '\n' < target/cp-noprofile.txt|sort) <(tr ':' '\n' < target/cp-profile.txt|sort)
# (无差异) → IDENTICAL CLASSPATH（profile 不增加任何 jar）
```

**官方用法判定**：按 [Spring Modulith – Fundamentals](https://docs.spring.io/spring-modulith/reference/fundamentals.html)，`spring-modulith-core` 作为 `implementation` 编译期依赖的官方场景**仅限于**使用运行时组件（`ApplicationModuleInitializer`、actuator、observability）。**纯验证场景应放 test scope 并用 `ApplicationModules.of(...).verify()` 写 JUnit**，而非 CI 里用 profile + 临时 javac。

**修复建议**：删除该 profile（`pom.xml:446-453`，零行为变化），并按 P2-4 迁到 `src/test` JUnit。**代价** 0.2 人日，**风险** 无。

---

### P2-4　架构门禁只经 shell 脚本运行，`mvn test` 与 IDE 无法触发

**证据**——
```bash
cd leo && find src/test -iname '*Arch*' -o -iname '*Modulith*' -o -iname '*Modular*'   # (无输出)
find src/test -name '*.java' | xargs grep -l "ApplicationModules\|ArchRule" 2>/dev/null  # (无输出)
```
实现方式是运行时 `javac` 编译 `scripts/*.java` 到临时目录后 `java -cp` 执行（`check-java-module-boundaries.sh:70-86`）。

**影响**：架构违规**不在 `mvn test` 阶段暴露**，只在 CI shell 步骤暴露，本地须手动跑脚本；无法用 ArchUnit 标准写法（`@AnalyzeClasses`/`@ArchTest`），也享受不到类缓存与 IDE 集成。

**修复建议**：新增 `src/test/java/com/leo/erp/ArchitectureTests.java`：
```java
@AnalyzeClasses(packages = "com.leo.erp")
class ArchitectureTests {
    @ArchTest static final ArchRule noModuleCycles =
        slices().matching("com.leo.erp.(*)..").should().beFreeOfCycles();
    @ArchTest static final ArchRule controllersMustNotUseRepositories =
        noClasses().that().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository");
    @ArchTest static void modulithVerify() { ApplicationModules.of(LeoApplication.class).verify(); }
}
```
规则来源：[ArchUnit User Guide · Cycle Checks](https://www.archunit.org/userguide/html/000_Index.html#_cycle_checks) 与 [Freezing Arch Rules](https://www.archunit.org/userguide/html/000_Index.html#_freezing_arch_rules)。
**代价**：1–2 人日。**风险**：低。注意**不要**保留两套等价规则——建议 JUnit 管「规则有效性」、shell 管 baseline 冻结，职责分离并写进注释。

---

### P3 级

- **P3-1 基线是裸条目**：209 行全无注释，无豁免理由/负责人/到期日；ArchUnit store 不支持行注释，可用同名 `.md` 台账。**建议** `config/archunit-boundary-store/EXEMPTIONS.md`；**代价** 0.5 人日（与 P1-4 去重一起做最划算）。
- **P3-2 `ModuleBoundaryVerifier.java` 未纳入 formatter**：`:210` 把方法签名与 `return` 挤在一行；checkstyle 只扫 `src/main/java`，`scripts/*.java` 无格式门禁。**建议**把 `scripts/*.java` 纳入 spotless/checkstyle；**代价** 0.5 人日。
- **P3-3 两个门禁脚本 90% 重复**：临时目录、classpath 构建、javac、错误处理完全一致，仅 `VERIFIER_CLASS`/`VERIFIER_SOURCE` 不同。合并为带参数的单一脚本；**代价** 0.5 人日。
- **P3-4 门禁能力边界未声明**：ArchUnit 基于字节码静态分析，不覆盖反射、Spring 字符串 bean 名注入、事件监听的**反向依赖**。实测反射为 0（`grep -rn "Class\.forName" src/main/java --include=*.java | wc -l` → `0`），风险低，但「门禁全绿 ≠ 无耦合」应在脚本输出中显式声明；**代价** 0.2 人日。

---

## 2. 可扩展性评估：新增一张单据需要改几处

以 `sales-order` 为样本：
```bash
cd leo && grep -rln "SALES_ORDER\|sales-order" src/main/java --include=*.java | wc -l   # 32 个文件
grep -rln "SALES_ORDER\|sales-order" src/main/java --include=*.java \
  | sed 's|src/main/java/com/leo/erp/||' | cut -d/ -f1-2 | sort | uniq -c | sort -rn | head -5
# 13 sales/order   3 security/permission   3 common/support   2 purchase/order   1 system/printtemplate
```

### 触点清单（7 类）

| 类别 | 文件:行号 | 形态 | 改动 |
|---|---|:--:|---|
| 模块键常量 | `common/support/ModuleKeys.java:25` | ✅ 常量类 | 加一个 `public static final String` |
| 模块显示名 | `common/support/ModuleCatalog.java:59` | ⚠️ 静态 `LinkedHashMap`（25 项） | `modules.put(KEY, "中文名")` |
| 状态集合 | `common/support/StatusConstants.java:73,155`（223 行 / 27 组） | ❌ 每单据一组 `ALLOWED_*` + `*_TRANSITIONS` | 新增 2 组集合 |
| 排序白名单 | `common/api/PageSortFieldCatalog.java:18`（21 项） | ⚠️ 静态 Map | 加一行 `Map.entry(...)` |
| 导出定义 | `common/moduleexport/service/ModuleExportCatalog.java:71`（10 项） | ✅ 注册表 | 加一个 `definitions.put(...)` |
| 权限码 | `security/permission/PermissionCodes.java:48` | ✅ 常量类 | 新增 `*_READ/_AUDIT/_COMPLETE` |
| 状态流转权限 | `security/permission/StatusTransitionPermissionGuard.java:34,48,62` | ❌ 三个静态 Map | 每个动作一行，**易漏** |
| 费用白名单 | `common/charge/service/DocumentChargeItemService.java:33` | ❌ `Set.of(PURCHASE_ORDER, SALES_ORDER, FREIGHT_BILL)` | 需附加费用则改源码 |
| 锁顺序 | `common/concurrency/SourceAllocationLockService.java`（25 个 `RANK_*`） | ❌ 硬编码 rank + 表名 | 参与库存分配则新增 rank |
| 打印模板 | `system/printtemplate/` + `db/seed/S*` | ⚠️ 元数据注册 | 新增 seed 脚本 |
| 搜索流 | `search/flow/` | ✅ 无硬编码（grep 未命中） | 不需改 |

### 判定

**做得好的部分**：`ModuleKeys`、`ModuleCatalog`、`ModuleExportCatalog`、`PermissionCodes` 都是**注册表/常量表**形态，符合开闭原则——加单据是「加一行」而非「改分支」。全仓**未发现**针对单据类型的 `switch/case` 硬编码分支（`grep -rn "case \"SALES\|switch (moduleKey)\|switch (billType)" src/main/java` 无输出），这比多数同规模 ERP 好。

**真正的扩展成本不在代码分支，而在必须记得改全部 7 类注册点。** 三处最易漏：`StatusTransitionPermissionGuard` 的三个静态 Map（audit/unaudit/complete）、`DocumentChargeItemService` 的 `Set.of`、`SourceAllocationLockService` 的 25 个 `RANK_*`；漏改后果分别是「动作越权/无法执行」「费用明细静默不落库」「并发死锁风险」，都是 P0 级业务后果且**无编译期保护**。

**建议**：(1) 收敛为**一个** `BillTypeDescriptor`（模块键 → 状态集合/排序字段/导出定义/权限码/是否支持附加费用/锁 rank），用 `List<BillTypeContributor>` 注入聚合，新增单据只需加一个 `@Component`；(2) 补「注册完整性」测试——遍历所有 `ModuleKeys` 常量，断言每个都有 `ModuleCatalog` 名称、`StatusConstants` 状态集合、`PageSortFieldCatalog` 排序白名单。**代价** 重构 6–8 人日、完整性测试 1 人日（**可先做，成本低收益高**）；**风险** 中（涉及 21 个页面/单据）。

---

## 3. 分层泄漏审计

### 3.1 web 层：无泄漏（正面结论）
```bash
cd leo
grep -rln "Repository" src/main/java/com/leo/erp --include=*Controller.java | wc -l       # 0
grep -rl "domain\.entity" src/main/java/com/leo/erp --include=*Controller.java | wc -l    # 0
find src/main/java -name '*Controller.java' | wc -l                                       # 66
```
66 个 Controller、sales 模块 13099 行的规模下，0 个引用 Repository、0 个引用 `domain.entity`，相当克制。

### 3.2 service 层：存在跨模块穿透明细
```bash
cd leo && for f in $(find src/main/java -name '*.java'); do src=$(echo $f|sed 's|src/main/java/com/leo/erp/||'|cut -d/ -f1); \
  grep -o "import com\.leo\.erp\.[a-z]*\.[a-z.]*\.service\.[A-Za-z0-9_]*" $f 2>/dev/null | sed 's/import com\.leo\.erp\.//' \
  | while read imp; do t=$(echo $imp|cut -d. -f1); [ "$t" != "$src" ] && [ "$t" != "common" ] && echo "$src -> $t"; done; done \
| sort | uniq -c | sort -rn
# 8 sales->system  3 master->system  2 security->system  2 auth->system  1 each: purchase/logistics/finance/attachment->system; 1 auth->security
```
`sales -> system` 8 次是最大穿透，主要是 `system.printtemplate.service.*`（P2-2a）。

### 3.3 `common` 垃圾桶迹象与 `api` 覆盖
`common` 135 文件 / 10642 行 / 23 子包，同时含纯技术（`json`/`excel`/`retry`）与业务（`charge`/`moduleexport`/`support`），详见 P1-3。

`api/` 包覆盖 **10 / 15**，无 `api` 的 5 个是 `finance`、`market`、`search`、`security`、`system`：
```bash
cd leo && ls -d src/main/java/com/leo/erp/*/ | while read d; do [ -d "$d/api" ] || echo "无 api 包: $(basename $d)"; done
# finance  market  search  security  system
```
其中 `system` 被 8 模块依赖、`security` 被 8 模块依赖（`master -> security` 24、`market -> security` 24），是最需要契约层却没有的两个。

---

## 4. 建议路线图

| 阶段 | 动作 | 代价 | 风险 | 性价比 | 验证方式 |
|---|---|---|---|---|---|
| **0. 让指标可信** | 基线去重（`6100a3ca` 160→40、`525fb6ef` 24→21）；新增 `LIMITS`（行数上限 + deadline）；删冗余 profile（P2-3） | 1.5 人日 | ≈0 | ★★★★★ | 门禁仍绿，输出变为 `40 baselined restricted dependencies`；classpath diff 为空已证 |
| **1. 消除可立即断的环** | `inventory -> sales/purchase` 改走 api 或事件（25→21）；`security -> auth` 改走 `auth.api`（21→19）；**补注册完整性测试**（§2 建议 2） | 3–4 人日 | 低 | ★★★★★ | 输出中 `baselined cyclic edges` 下降并同步下调 LIMITS；完整性测试覆盖所有 `ModuleKeys` |
| **2. 拆 common + 给 system 建契约** | `common.security.spi` 抽出（解 `common -> security` 环根因）；`common` 三分（tech / security.spi / shared-kernel）；`system` 建 `api/` | 12–18 人日 | 中高 | ★★★☆☆ | `baselined cyclic edges` 压到个位数；`system` 外部引用只剩 `api.*` |
| **3. 补模块身份** | 按 P1-1 给 7 个包补 `@ApplicationModule` + `api/package-info.java` | 8–12 人日 | 中 | ★★★☆☆ | 输出从 `8 explicitly annotated modules` 变为 15，且 `allowedDependencies` 与实测一致 |
| **4. 状态机收敛** | 按 P1-5 把状态写入收敛到实体方法，下线类名白名单；按单据类型分批（销售→采购→物流→对账） | 12–18 人日 | 高 | ★★☆☆☆ | `525fb6ef` 基线清空；实体外无 `setStatus` 调用 |

**阶段 3 必须在阶段 2 之后**：否则会瞬间产生数百条新违规，被迫 `--refreeze`，重演基线腐化。

---

## 5. 参考资料

官方文档（均已校验 HTTP 200 可达）：

- [Spring Modulith – Verifying Application Module Structure](https://docs.spring.io/spring-modulith/reference/verification.html)：`verify()` 三条规则——模块级无环、只能经 API 包访问内部、`allowedDependencies` 显式依赖。P1-1 核心判定依据。
- [Spring Modulith – Fundamentals](https://docs.spring.io/spring-modulith/reference/fundamentals.html)：`detection-strategy=explicitly-annotated` 的确切语义（「only consider packages explicitly annotated」）、`@NamedInterface`、`allowedDependencies` 只约束「其他应用模块」、以及 `spring-modulith-core` 作为编译期依赖的官方适用场景（仅运行时组件需要）。P1-1 与 P2-4 的判定依据。
- [Spring Modulith – Working with Application Events](https://docs.spring.io/spring-modulith/reference/events.html)：`@ApplicationModuleListener` 的 AFTER_COMMIT + REQUIRES_NEW 语义、事件持久化与重启重放。P2-1 对照依据。
- [ArchUnit User Guide](https://www.archunit.org/userguide/html/000_Index.html)：规则语法、[Cycle Checks](https://www.archunit.org/userguide/html/000_Index.html#_cycle_checks)、[Freezing Arch Rules](https://www.archunit.org/userguide/html/000_Index.html#_freezing_arch_rules)（`ModuleBoundaryVerifier` 的机制来源与收敛语义）。
- [Martin Fowler – MonolithFirst](https://martinfowler.com/bliki/MonolithFirst.html)：模块化单体的演进前提——边界必须真实存在，否则「单体内无环」只是形式。
- [Thoughtworks Technology Radar – Modular Monolith](https://www.thoughtworks.com/radar/techniques/modular-monolith)：模块化单体被列为需**强制约束**（用工具守边界）才有意义的实践，与「门禁存在但被基线削弱」直接对应。
- [Shopify Engineering – Shopify's Monolith](https://shopify.engineering/shopify-monolith)：大规模单体依赖组件边界与显式契约，是 `system`/`security` 缺 `api/` 的反例参照。

---

## 附录 A：口径与复现说明

1. **依赖矩阵口径**：`grep -rho "import com\.leo\.erp\.<模块>\." src/main/java/com/leo/erp/<引用模块>` 的出现次数，即「引用该模块的 import 语句数」。`grep` 会命中 `package-info.java` 与注释字符串，量级小，不影响结论。
2. **跨模块内部类型口径**：`repository` / `domain.entity` / `web.dto` 三个包段，与 `ModuleBoundaryVerifier.restrictedKindOf` 判定一致（`scripts/ModuleBoundaryVerifier.java:125-143`）。
3. **「走 api 包」判定**：只统计 `import com.leo.erp.<模块>.api.*`；混合依赖的模块对分别列出（摘要结论 2 表格）。
4. **门禁耗时**：两个脚本分别约 14s / 20s（含增量 `mvn compile`，本地已读 `target/classes` 缓存，冷启动更慢）。
5. **本次审计未修改**任何业务代码、配置或 Flyway 脚本；`git status --short` 仅显示 `?? docs/audit-2026-10-18-backend/`。
