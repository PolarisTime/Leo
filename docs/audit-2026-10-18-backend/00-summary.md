# Leo 后端代码质量设计审计 · 总报告

- 审计日期：2026-10-18
- 基线提交：`0ba7f12c`（`chore(release): 13.2.1`，2026-10-17）
- 审计对象：后端仓库 `leo/`（**不含前端 `aries/`**，前端由另一条审计线负责）
- 技术栈：Java 21 / Spring Boot 3.5.16 / Spring Modulith 1.4.13 / Spring Data JPA / PostgreSQL / Redis / Flyway
- 规模：15 个顶层包、1060 个 Java 文件、91158 行主代码；342 个测试类、2949 个 `@Test`
- 审计性质：**只读**。未修改任何业务代码、配置、Flyway 脚本；`git status` 仅新增 `docs/audit-2026-10-18-backend/`
- 分项报告：`01` 架构耦合 ｜ `02` 性能数据库 ｜ `03` API 契约 ｜ `04` 安全一致性 ｜ `05` 代码质量门禁

---

## 一、总体结论

**这是一套基础远高于同类平均水平、但"门禁形式齐全而阈值缺位"的代码库。** 真实的缺陷不是"缺规范""缺门禁""缺 CI"，而是：

1. **门禁的保护半径显著小于表观半径** —— 架构门禁只认 8/15 个顶层包，静态分析只拦 error/High 级，覆盖率没有阈值，因此可以长期全绿而不逼迫质量上升。
2. **两条真实的生产级缺陷**（各 1 个 P0）—— 一条性能、一条安全，都可精确定位到文件行号并已独立复核。
3. **绝大多数"有问题的表象"经核查是正面的** —— 版本化 100%、DELETE 204 100%、错误契约 100%、Flyway 189 连续、零 `findAll()`、零 `@Data` on Entity、零 `TODO`、零 `printStackTrace`、零 `@SpringBootTest`、提交规范 100%。

所以后续工作**不应以"大整改"开场**，而应按「2 个 P0 → 门禁补阈值 → 基线收敛 → 结构性重构」四步走，前三步合计约 8 人日、风险接近零，却能拦住未来绝大多数回归。

### 严重度汇总（各分项独立定级）

| 分项 | P0 | P1 | P2 | P3 | 合计 |
|---|---:|---:|---:|---:|---:|
| 01 架构与耦合 | 0 | 5 | 4 | 4 | 13 |
| 02 性能与数据库 | **1** | 5 | 6 | 4 | 16 |
| 03 API 契约 | 0 | 6 | 7 | 3 | 16 |
| 04 安全与一致性 | **1** | 6 | 17 | 10 | 34 |
| 05 代码质量与门禁 | 0 | 4 | 6 | 5 | 15 |
| **合计** | **2** | **26** | **40** | **26** | **94** |

> 口径说明：各分项按自身领域独立定级，同一处代码可能被两个分项分别计入（例如打印导出既出现在 04 的授权问题，也关联 03 的状态码问题）。合计值用于排序，不代表 93 个互不重复的缺陷。

---

## 二、两个 P0（须立即处置）

### P0-A｜销售订单列表"下游关联"筛选：相关子查询被按行重算（性能）

- **位置**：`src/main/java/com/leo/erp/sales/order/repository/SalesOrderRepository.java:151 / 176 / 198 / 222 / 246`（共 5 处）
- **证据**：Lead 已独立复核该文件，5 处 `in (select ...)` 位于 `not exists` 内并引用外层 `salesOrder`，属于**关联子查询**而非可优化的半连接。审计员用只读会话取得真实 `EXPLAIN (ANALYZE, BUFFERS)`：在**仅 108 行订单 / 20 行明细**的开发库上即出现 `SubPlan 1 ... loops=8176`、`Buffers: shared hit=81735`（占总量 99.6%）、耗时 121 ms。
- **影响**：按 `N_order × N_outbound_item × N_order_item` 三层嵌套外推，订单过千后该接口退化到秒级。这是全站第一个会被打穿的接口。
- **修复**：改写为 `LEFT JOIN ... WHERE ... IS NULL` 或 `NOT EXISTS` 相关子查询（PostgreSQL 对 `NOT EXISTS` 可做 anti-join），并补 `lg_freight_bill_source_order.source_sales_order_id` 索引。
- **注意**：P0-A 与 P1-5（`lg_freight_bill_source_order.source_sales_order_id` 无前导索引）**必须同批修**——当前该分支只花 0.219 ms 纯粹因为表仅 74 行能整表进 hash，表一大就暴露。

### P0-B｜销售订单打印/导出缺失记录级授权（安全回归）

- **位置**：`src/main/java/com/leo/erp/sales/order/service/SalesOrderPrintExportService.java`（440 行，`exportSalesOrderPrint` 两个入口在 :81 / :86）
- **证据链**（Lead 独立核实，比"漏实现"更严重）：
  1. 历史审计 `docs/2026-07-10-backend-code-review.md:22` 记录 High-01「**已修复**：销售订单打印增加记录级数据范围校验」，`:466-471` 明确修复方式为调用 `ResourceRecordAccessGuard.assertCurrentUserCanAccess("sales-order", "print", order)`。
  2. 该守卫类在 `49c71e59 refactor(security)!: 移除 RBAC 与 MCP 授权体系`（**2026-07-18**）中被删除；`git log --all -- '*ResourceRecordAccessGuard.java'` 显示**此后无任何恢复提交**。
  3. 系统随后重新引入 RBAC：`V139__rbac0_user_role_permission.sql`、`73369334 feat(security): 落地 RBAC0 用户-角色-权限落库`（2026-09-15）、`deb03350 feat(security): 可扩展端点权限模型`（2026-09-14）——**但记录级守卫没有随 RBAC 一起恢复**。
  4. 当前核实：`grep -rn 'dataScope\|DataScope' src/main/java` = **0**；`grep -rn 'ResourceRecordAccessGuard\|assertCurrentUserCanAccess'` = **0**；`security/permission/` 下 12 个文件无记录级守卫。
- **影响**：任一持 `print-exports:print` 权限的主体可打印/导出**任意他人**的销售订单。在 V139/V140 放开多用户 RBAC0 之后，这是实际的横向越权面。
- **修复**：恢复记录级守卫（`SELF` / `DEPARTMENT` / `ALL` 三档），覆盖打印与打印预览两个入口，越权固定 403；并**更正历史审计文档**，注明该修复已随 `49c71e59` 回退——文档标记「已修复」而实现已失效，会持续误导后续排查。

---

## 三、五份报告的共同结论（跨分项交叉验证）

四份报告独立得出了同一个判断，这比任何单份报告都更可信：

> **"门禁存在但阈值缺位"是本次审计最系统性的问题。**

| 分项 | 独立表述 | 关键数字 |
|---|---|---|
| 01 架构 | 门禁保护半径 < 表观半径 | Modulith 只认 **8/15** 个顶层包；ArchUnit 冻结 **209 行**基线（160 行仅 **40 条唯一**，虚高 4×，无到期机制） |
| 05 质量 | "工具齐全、门禁有形、阈值缺位" | Checkstyle **615 告警 / 0 阻断**（18 条规则仅 7 条 error）；SpotBugs `threshold=High` 对 1347 类返回 **0 bug**；JaCoCo **无 check、无阈值**（曾有 10% 阈值，在 `657beee7` 被删且未恢复） |
| 03 API | 存量豁免掩盖真实缺口 | 版本化 **259/259 = 100%**，但 `created()` 反射缺口导致 **4 个端点必然丢 Location**；路径参数正整数校验仅 **6/132 = 4.5%** |
| 02 性能 | 索引存在但与查询写法错配 | **41 个 `gin_trgm_ops` 索引基本闲置**（仅 1 个有 39 次扫描）；`sys_operation_log` **121 个分区**（仅 4 个有数据）致规划 **52–54 ms vs 1.5 ms（36×）** |

---

## 四、必须纠正的四个"看似缺陷"（避免误伤与无效工作）

审计过程中，以下四点若不做核实就会被误报成缺陷。全部已实测澄清：

1. **`ls` 看到的 Flyway "乱序"是假象**。`ls` 字典序会把 V59 排在 V18 前；`sort -V` 后 **V1–V189 连续、无跳号、无重复、命名 100% 合规**。CI 已有三重迁移门禁（`Verify Flyway migrations`、`Flyway 历史断言门禁`、`Flyway 门禁负例自检`，`.github/workflows/ci.yml:131-181`）。
2. **"CI 缺失"是误判**。2 个 job / 13 个步骤，含架构门禁、迁移冻结、压测套件离线自检、checkstyle/spotbugs、Flyway 负例自检、镜像冒烟。覆盖面对同类项目而言**高于平均**。真问题是阈值，不是覆盖面。
3. **`findAll()` 零调用是真，但 `findAll(` 有 36 处**。其中真正"无分页全表读"仅 3 处（`AttachmentManifestExportService.java:88`、`:90`、`PermissionCatalogService.java:82`）。写作"零 findAll"会掩盖这 3 处。
4. **实体/乐观锁口径**：`grep -l '@Entity'` 得 76 是把 import 与 `@EntityListeners` 计入的虚高值。行首 `@Entity` 真实实体 **65 个**，带 `@Version` **24 个（36.9%）**。审计员的 65/24 是正确口径，Lead 早期侦察的 76/32 应作废。

---

## 五、分项要点（各 3–5 条，详见对应报告）

### 01 架构与耦合（P0=0, P1=5）
- **Modulith 只覆盖 8/15 个顶层包**：`auth/common/finance/inventory/search/security/system` 无 `@ApplicationModule`，配合 `detection-strategy: explicitly-annotated`（`application.yml:116`）被判为非模块，其依赖完全不受 `verify()` 约束。这解释了为何 8 个模块的 `allowedDependencies` **全部与实际不符却全部 verify 通过**。
- **25 条循环边被冻结**，其中 `inventory ↔ sales`、`sales → purchase → inventory → sales` 是真环；`inventory` 直吃 `sales.outbound.repository`（非 api 包）。
- **`common` 已退化为混合体**：既含技术底座，又含业务口径，且**反向依赖 `security`/`system`**。
- **web 层干净是正面项**：66 个 Controller 中 0 个引用 Repository、0 个引用 `domain.entity`。
- **新增一张单据需改 7 类触点**，其中 3 类是硬编码白名单（非注册表）。

### 02 性能与数据库（P0=1, P1=5）
- P0-A 见上。
- **`sys_operation_log` 121 分区**：`Merge Append` 每次准备 121 个子计划，规划 52–54 ms（普通表 1.5 ms）；而 `scripts/maintenance.sh:116-145` 名为"分区保留"却**只做 SELECT**，全仓零 `DROP TABLE`/`DETACH PARTITION`。
- **41 个 trgm 索引闲置**：`POSITION(...IN LOWER())` / `lower() LIKE` 即使 `SET enable_seqscan=off` 也不走索引；`Specs.java:86-95` 与 V186 注释**早已写对根因**，但修复只覆盖 1 条路径，另有 7 文件 16 处未改。
- **列表页循环内查询**：报价单 `QuoteSheetStore.java:206` 逐行触发 3 层懒加载；`resolveSupplierNames`（:771-776）循环内逐个查供应商；收付款核销明细逐行查对账单。
- **正面项**：`open-in-view: false`、`batch_size`/`default_batch_fetch_size`=50、`reWriteBatchedInserts` 全部正确。

### 03 API 契约（P0=0, P1=6）
- **端点 259 个**（322 映射注解 − 63 类级 `@RequestMapping`），版本化 **259/259=100%**，DELETE→204 **31/31**，错误 ProblemDetail **25/25** 且无第二套错误生成器。
- **子资源动作 46 : 动词后缀 2 = 23 : 1**，2 条违规均 2026-07 前存量（blame 已确认），**2026-07 后零新增违规**——说明规范在新增代码上确实生效。
- **`created()` 反射读 id**：48 个调用点中 4 个响应 DTO 无 `id` 字段 → **必然静默丢 Location**；反射异常被 `GlobalExceptionHandler.java:472` 兜底成 **500 而资源已提交** → 重试导致重复创建。
- **路径参数正整数校验仅 6/132 = 4.5%**。
- **对外真实路径是 `/api/v2.0`**（`context-path: /api` + `V2_PREFIX: /v2.0`），三处证据：`application.yml:7`、`aries/src/utils/env.ts:20-22`、`deploy/nginx/leo.conf:26-27`。AGENTS.md 的 `/api/v2` 与实现语义一致，不算冲突；真实问题是仓库内注释 `/api/v2` 与 `/api/v2.0` 混用（各 2 处）。

### 04 安全与一致性（P0=1, P1=6）
- P0-B 见上。
- **248 处 `@RequirePermission`**（自定义注解，`@PreAuthorize` 仅 1 处），写端点授权实质覆盖 **144/144**。
- **事件可靠性已正确落地，不是缺陷**：`event_publication` 表、`completion-mode: delete`、重启重放、`ON CONFLICT (event_id) DO NOTHING` 幂等消费齐备（`application.yml:117-121`）。
- **状态写入盲区**：`MdQuoteRegion` / `MdLengthPremium` 都 `implements StatusAwareEntity` 却**自写 `setStatus`**，绕过基类 `beforeStatusUpdate` 守卫；门禁只查调用方白名单，不查实体侧契约。
- **雪花 ID 契约达标**：`JacksonConfig.java:44-45` 对 `Long`/`long` 全局注册 `ToStringSerializer`，8 个契约测试锁定。
- **S-8 历史审计文档双向失真**（审计员自查新增）：除 `:22` High-01 属"假已修复"外，`:23` High-02 实际已闭环却仍标「部分修复」——`common/ratelimit/RedisRateLimitStore.java:79-111` 已是单条 Lua 原子脚本（Lead 已复核），`SecurityConfig.java:161-166` 关闭重复注册。两类相反方向的漂移同时存在，说明该文档的修复进度表已不可作为事实来源。

### 05 代码质量与门禁（P0=0, P1=4）
- **覆盖率门禁已被删除**：`pom.xml:390-408` 只有 `prepare-agent` + `report`，无 `check`、无阈值；三个 workflow 从不读取 `target/site/jacoco` ⇒ **新增 500 行零测试代码可通过全部 CI**。
- **Checkstyle 615 告警 / 0 阻断**（18 条规则仅 7 条 error）；**SpotBugs `threshold=High` 对 1347 类返回 0 bug**。
- **测试判据弱**：仅 **45.9%** 测试类含负路径断言（`auth`/`system` 仅 25%），`@ParameterizedTest` 仅 3 个文件。
- **正面项**：零 `@SpringBootTest`（无慢测试）、零 `TODO`、零 `printStackTrace`、零 `@Data` on Entity、提交规范 200/200 合规。
- **`db/seed` 线 15 个脚本从未被执行**（审计员自查新增，任务书外发现）：`src/main/resources/db/seed/` 下有 `S1`–`S15` 共 15 个脚本，但 `application.yml:98-110` 的 `flyway` 配置**没有 `locations`**，全仓也**没有第二套 Flyway 实例**（`grep -rln 'FlywaySeed\|seedDataSource\|flyway_seed_history' src/main/java` → 0），`grep -rn 'db/seed' src/main scripts .github` 只命中 S9 脚本自身的 SQL 内容。而 `AGENTS.md` 明确要求"schema 与 seed 用两个 Flyway 实例隔离"——**文档已规定、代码未实现**（Lead 已复核）。

---

## 六、建议路线图（按性价比排序）

| 批次 | 内容 | 工作量 | 风险 | 性价比 |
|---|---|---|---:|---:|
| **第 0 批** | 修 2 个 P0：①改写 `SalesOrderRepository` 5 处关联子查询 + 补索引；②恢复记录级授权守卫并更正历史审计文档 | 3–4 人日 | 低 | ★★★★★ |
| **第 1 批** | 补门禁阈值：JaCoCo `check` 加 LINE ≥ 0.10（先恢复到删除前水平）、Checkstyle 提 severity、SpotBugs 降 threshold；架构基线去重 + 加条目数单调递减断言 | 2–3 人日 | 极低 | ★★★★★ |
| **第 2 批** | 架构基线递减：inventory 双向 4 边 + `security → auth`（25→19）；`sys_operation_log` 分区砍到 ~14 个；修 7 文件 16 处 trgm 写法 | 4–6 人日 | 低 | ★★★★☆ |
| **第 3 批** | 给 7 个无名分顶层包补 `@ApplicationModule`。**必须晚于第 2 批**，否则会瞬间爆出大量违规被迫 `--refreeze` | 8–12 人日 | 中 | ★★★☆☆ |
| **第 4 批** | 拆 `common`（分离技术底座与业务口径）、给 `system` 建 `api` 契约层 | 12–18 人日 | 中高 | ★★★☆☆ |
| **第 5 批** | 状态机收敛、`created()` 反射改显式、幂等键覆盖补齐 | 15–20 人日 | 高 | ★★☆☆☆ |

**不建议做**（各报告均有专章说明，避免过度设计）：
- 不要按 dev 库 `idx_scan=0` 删那 395 个索引 —— dev 表太小，规划器永远不选索引，此指标无意义。
- 不要为"测试数量"而拆 `@SpringBootTest` —— 当前 0 个 `@SpringBootTest` 是优点而非缺陷。
- 不要引入 ArchUnit JUnit 入口替换现有 shell 门禁 —— 两者职责不同（JUnit 管规则有效性、shell 管基线冻结），应并存而非替换。

---

## 七、审计方法与局限（必须与结论同读）

**方法**：静态分析为主 + 可复现命令；性能分项使用**只读会话**（`options=-cdefault_transaction_read_only=on`）取得真实 `EXPLAIN`；安全分项用 git 历史追溯回归链；每份报告的引用 URL 均用 `curl`/`web_fetch` 实测 HTTP 200（`web_search` 本会话不可用，故全部改用直连验证，**未编造 URL**）。

**局限（务必随结论引用）**：
1. **开发库数据量极小**（除 3 张表外业务表 < 1000 行）。所有 `Seq Scan` 只能证明"无索引可用"，涉及生产量级的性能数字均为**推算**并已标注外推公式。
2. **`pg_stat_statements` 未安装**，因此**没有真实慢查询排行**，性能结论来自静态分析 + 单条 SQL 的 `EXPLAIN`。
3. 性能审计员为让 `EXPLAIN` 可信，在 `fm_receipt` 与 `lg_freight_bill_item` 上执行过 `ANALYZE`（只更新统计信息，不改业务数据）——严格按"零写"口径属例外，已声明。
4. **未执行动态/攻击性测试**；P0-B 的越权路径建议在隔离环境构造只读复现，不要对共享环境执行。
5. 未审计前端 `aries/`；未审计 `perf/` 压测脚本实际有效性；GitHub 分支保护规则不在仓库内，**"门禁是否可被绕过合并"无法审计**。
6. 行号对应基线 `0ba7f12c`，后续提交会漂移，定位请以「文件 + 符号名」为主。

---

## 八、审计过程记录（团队协作与纠错）

本轮由 6 个并行审计任务构成（架构 / 性能 / API / 安全 / 质量 5 个分项 + Lead 汇总）。过程中发生过 3 次 teammate 中断，其中 API 契约分项由替补审计员重跑完成。

**审计员对 Lead 初始判断的 5 处修正（均已由 Lead 独立复核并采纳）**：

| # | Lead 初期判断 | 审计员核实结果 | 采纳 |
|---|---|---|---|
| 1 | 25 条循环边全部因非模块而躲过 Modulith | 实为 **23 条**至少一端为非模块；`master → attachment`、`sales → purchase` 两端都是模块，且在 `allowedDependencies` 内，verify 通过是**正确**的 | ✅ |
| 2 | `architecture-verification` profile 应改 test scope | 该 profile 的 classpath 与默认**逐字节相同**（`spring-modulith-core` 已由 starter-jpa 传递引入），**完全冗余、零作用** | ✅ |
| 3 | `@ApplicationModuleListener` 有 4 处 | 实为 **2 处**，另 2 处是 javadoc 提及；事件可靠性已正确落地 | ✅ |
| 4 | AGENTS.md 的 `/api/v2` 与实现 `/v2.0` 冲突 | `context-path: /api` 使对外路径为 `/api/v2.0`，**语义一致不算冲突**，降为 P3 | ✅ |
| 5 | 实体 76 个 / 带 `@Version` 32 个 | 行首 `@Entity` 真实 **65 个** / `@Version` **24 个**（76 把 import 与 `@EntityListeners` 计入了） | ✅ |

这类纠错是本次审计质量的主要来源——单份报告的结论在跨报告交叉验证前不应直接采信。
