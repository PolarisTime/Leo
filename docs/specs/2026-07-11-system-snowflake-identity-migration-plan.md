# 全系统雪花 ID 稳定身份改造计划

## 1. 文档状态

| 项目 | 内容 |
| --- | --- |
| 状态 | In Progress；生产数据库切换及 Leo `v3.0.0`、Aries `v2.3.0` 部署已完成，旧 `Master_Prod` 与实时 dump 保留为回滚基线；销售交付核定 E2E 收尾提交及远端 CI 已通过，但最新可复核的完整 Playwright E2E 仍为 25 passed、57 failed、2 skipped，不能据此宣称全量业务门禁通过 |
| 计划版本 | 3.2（生产切换与发布后证据收口版） |
| 日期 | 2026-07-13 |
| 涉及仓库 | 后端 `leo`、前端 `aries` |
| 审计基线 | `leo@99703021`、`aries@1d247de4` |
| 当前数据库版本 | 当前生产应用连接 `master_prod_cutover_current_20260713_102500`：主线 V56/56、repair baseline + D3 + D4 为 3/3，失效索引 0、本次新增未验证约束 0；新库存量 14 个未验证约束均继承自旧库。旧 `Master_Prod` 保留 V11（11 success、0 failed）作为回滚库；下一主线数据库变更只能新增 V57+ |
| 核心决策 | 复用现有 `BIGINT` 雪花主键作为唯一内部身份，不新增 UUID 或平行身份体系 |
| 当前操作边界 | Leo `v3.0.0` 与 Aries `v2.3.0` 已提交、push 并由 self-hosted runner 完成生产部署，Flyway target 显式为 56；Aries 后续 `test(e2e)` 提交 `4e93949` 已 push，CI/React Doctor/release workflow 均成功且未生成新版本。原始两个工作区因历史分叉仍显示 dirty，但逐文件与远端一致；未执行 reset/clean |

### 1.1 当前实施进度（截至 2026-07-13）

生产迁移、应用实现和发布记录均已进入远端并完成生产部署。原始 `leo/main` 与 `aries/main` 因保留历史提交而继续呈现 ahead/behind 与 dirty，
但发布后逐文件复核分别为 Leo 13/13、Aries 40/40 与 `origin/main` 一致，不存在尚未 push 的文件内容。下表中的“完成”仍不等同于完整 E2E、业务守恒、fallback 周期和所有发布后门禁通过。

| 工作包 | 当前状态 | 已有证据 | 进入下一阶段前仍需完成 |
| --- | --- | --- | --- |
| G0 雪花根基 | 已部署生产 | 预分配失败关闭、本机生成、Long 字符串 JSON 与更新 ID 不变契约纳入后端全量 6184 项；生产 Leo `v3.0.0` 正常生成与读取现有 ID | 补齐机器号清单、时钟回拨告警和持续监控证据 |
| G1 客户/项目错写止血 | 已部署生产 | 客户/项目分别解析，ID 碰撞、跨客户、同名不同 ID 与快照冲突回归通过 | 历史错绑只读扫描、业务清洗确认和生产状态流观察 |
| E1/B1/C1 数据迁移 | V20–V56 脚本、契约与生产执行完成 | 实时 V11 dump 恢复的新生产库按 `V12–V29 → D3 → V30–V47 → D4 → V48–V56` 完成；主线 56/56、repair 3/3、两条 validate 成功，失效索引与新增未验证约束均为 0 | 补齐逐单/全局业务守恒、锁/WAL/空间实测归档和回滚恢复演练；后续变更只能从 V57+ 前滚 |
| A1 后端双写/读模型 | 实现已部署生产 | 物流稳定来源 91/91；供应商候选 126/126；客户/项目/物流商候选 128/128；空 V55 库后端全量 6184/6184；生产 Leo `v3.0.0` 健康与版本接口通过 | 继续生产 fallback 观察、机器号/时钟监控和真实权限/状态流核验 |
| A2 前端 API/交互链 | 实现已部署生产，定向 E2E 继续收口 | 三类 Statement 6 文件 109/109；Vitest 6181 passed、4 skipped；typecheck、Biome/ESLint、生产构建通过；`4e93949` 补齐销售订单从交付核定到完成销售的两条 E2E 链，远端 CI 全绿 | 在隔离测试库归档并重跑完整 E2E；验证真实权限、软删除、余额与库存守恒 |
| QA 本地与远端门禁 | 已完成非 E2E 门禁 | 当前 703 个测试源编译；D3/D4 release 契约 8/8；18 类联合 62/62；空 V55 库 `mvn test` 6184/6184，`verify`、Checkstyle、JaCoCo 均通过；Aries `4e93949` 的 Typecheck、Build、Lint、4 个测试分片和 React Doctor 均成功 | 现有 SpotBugs 51 个高等级告警另行治理，不冒充已通过；完整 E2E 仍须独立归档重跑 |
| 发布门禁 | 生产发布已执行，完整验收未完成 | 数据库切换、Leo/Aries 生产部署、应用健康、版本、未认证 403、runner 在线状态均已验证；最新可复核完整 E2E 仍为 25 passed、57 failed、2 skipped | fallback 完整周期、业务守恒、Dashboard/告警、回滚演练和完整 E2E 归档仍需完成；GitHub `PROD_FLYWAY_TARGET` 持久变量尚未配置 |

当前已完成或具备稳定定向证据的本地实现包括：

- G0：携带 `X-Preallocated-Id` 时，非正整数或预分配服务不可用均失败关闭；未携带该头时仍由本机雪花生成器分配。
- G1：修复销售订单把客户 ID 写入项目 ID 的缺陷；客户与项目现在分别解析，覆盖两类 ID 数值偶然相同的场景。
- V20 Expand：新增 `md_project.customer_id` 与 `so_sales_order.customer_id` 可空列，不回填、不加 FK、不收紧非空，保持旧应用兼容；该版本已有测试执行记录，后续发现问题只能用更高版本修复。
- 项目双写：项目请求/响应/实体贯穿 `customerId`；显式 ID 与客户编码冲突时拒绝，旧编码请求唯一解析后双写并记录 fallback。
- 销售订单双写：请求、实体、响应贯穿 `customerId`；`projectId` 按项目主数据解析，校验项目所属客户以及 ID/名称快照一致性。
- 兼容读取：旧销售订单请求缺少客户 ID时按客户编码解析；缺少项目 ID时仅允许在所选客户范围内按项目名称唯一匹配，多匹配失败关闭；两类命中均输出结构化 fallback 日志。
- 身份冻结：已审核改价与已审核转草稿路径均比较 `customerCode + customerId + customerName + projectId + projectName`，禁止旁路切换归属。
- 独立项目选项接口：新增 `GET /projects/options?customerId=...`；`id/value` 为项目雪花 ID，并返回 `customerId`、项目编码和名称；查询兼容尚未回填 `customer_id` 的历史项目。
- 仓库 option 返回 `id/value=id` 及编码、名称快照；供应商、物流商和车辆 option 使用真实主数据 ID，同名记录不合并。
- 采购、销售、物流、发票、对账、收付款、供应商退款到账及应收应付读模型已大范围贯穿 party、商品、仓库、结算主体和直接来源 ID。
- 采购退款保存后以 `sourcePurchaseOrderItemId` 集合触发入库完成状态同步；受影响入库单和父采购订单均按来源明细 ID 聚合，不再以采购订单号承担身份查询。
- 销售出库完成状态同步以销售订单明细 ID和父订单 ID查询、聚合；重新审核销售订单会按父订单 ID重算，草稿状态不触发同步，旧 `syncBySalesOrderReference/findActiveOutbounds` 调用已清零。
- 销售出库反审核复用销售订单下游保护，按来源销售订单明细 ID阻止已有开票或客户对账，保留已收款保护，并按销售出库明细 ID检查物流单占用。
- 物流来源按 `sourceSalesOutboundItemId` 批量定位父单、加锁、审核和计算占用；`sourceNo` 只校验快照。物流单继承真实客户、项目、商品和仓库 ID及快照，91/91 定向测试通过。
- 物流单反审核/删除下游保护以物流单 `id` 查询物流对账来源关系；即使业务单号快照为空或改变，已有下游事实仍能阻止变更。
- 物流对账前端保存白名单和父单导入保留 `sourceFreightBillId/sourceFreightBillItemId`；物流商车辆编辑保存保留稳定车辆 ID，不在客户端生成业务实体 ID。
- 后端候选链把 `customerId/projectId/supplierId/carrierId/currentRecordId` 下推到分页与占用查询；名称仅保留兼容检索，不承担归属判断。供应商链 126/126、客户/项目/物流商链 128/128、销售链 117/117 通过。
- 前端三类 Statement 草稿、头/明细、直接来源、候选 currentRecord、父单导入和批量生成均贯穿稳定 ID；按父单 ID去重/替换，拒绝同名不同 ID和部分缺 ID。
- V20–V46 已形成 Expand → Backfill → Constraint → Validate → Contract 本地版本线；当前 27 个文件已记录独立 SHA-256 manifest，从 2026-07-12 快照起提供未来防漂移证据，但不证明快照前从未改写。
- V47 已新增采购退款来源采购订单、供应商退款到账来源采购退款、角色冲突角色三个全量左前缀索引；未重复 V36 已覆盖的付款来源采购订单索引。
- V48–V49 增加并验证车辆/物流商组合归属 FK、组合索引与车辆 ID/车牌快照同空 CHECK；V50–V52 增加支撑索引并分阶段增加、验证恰好 8 条系统普通关系 FK，明确排除多态 `record_id`；V53–V55 分阶段验证并收紧采购入库直接来源明细为 `NOT NULL`。V47–V55 的 9 个文件由第二份独立 SHA-256 manifest 冻结。
- 生产 Flyway 阶段门禁统一使用显式正整数 target；运行时通过 `FlywayMigrationStrategy` 先校验再迁移，Maven、本地和 SSH部署入口校验同一 target，发布 manifest 同步记录 `flywayTarget`。

当前阶段性验证证据：

- 迁移契约：`SystemIdentityFinalContractMigrationsTest` 5/5、`SystemIdentityForwardContractMigrationsTest` 9/9、`SystemIdentityConstraintMigrationsTest` 8/8，两份 checksum 契约 2/2；V20–V46 manifest 27 项，V47–V55 manifest 9 项。
- 库存/出入库报表与仓库 option：26 项通过。
- 物流对账前端：43 项通过；物流商车辆前端：126 项定向和 84 项补充回归通过。
- 采购/供应商前端核心链与批量对账稳定 ID聚合均已 GREEN；预付款分配供应商对账候选按 `supplierId` 过滤 5/5 通过。
- 供应商/物流商/车辆后端 option：首轮 91 项和标签契约 69 项通过。
- 采购完成状态 ID 化：`PurchaseRefundServiceTest` 20 项、`PurchaseInboundCompletionSyncServiceTest` 18 项、`PurchaseInboundServiceTest` 44 项、`PurchaseInboundRepositoryTest` 9 项，共 91 项通过。
- 物流下游变更保护 ID 化：`FreightBillDownstreamMutationGuardTest` 6 项通过；目标差异 `git diff --check` 通过。
- 销售订单/出库 17 类定向测试 213 项通过，包含两项真实 PostgreSQL 仓储测试；验证按来源 ID跨父单聚合、加载完整父单明细、过滤软删除父单及出库审核状态。
- Flyway 阶段门禁、V47 精确三语句契约、V20–V46 checksum 和部署入口联合回归 25 项通过；非法运行时 target 会在 `flyway.migrate()` 前失败关闭。
- 当前工作树后端：`test-compile` 成功编译 703 个测试源；新增 D3/D4 release 契约 8/8；18 类联合 62/62；空 V55 库 `mvn test` 6184/6184，0 failure/error/skip；`verify` 通过 Checkstyle、打包和 JaCoCo 门禁。6184 项全量早于新增的 8 项 release 契约，二者分别记账，不合并伪造新的全量数字。
- 当前工作树前端：Vitest 461 个文件通过、1 个文件跳过；6181 项通过、4 项跳过；`pnpm typecheck`、Biome/ESLint、`pnpm build-only`、`pnpm audit --prod` 均通过。
- Ant Design 门禁：组件信息查询成功；全仓 lint 扫描 1008 个文件，0 deprecated、0 a11y、0 usage，仅 `OssSettingsView.tsx` 的静态 provider Select `virtual={false}` 有 1 条非阻断性能提示。
- 测试库 `leo_test` 已从 V47 迁移到 V55并成功 validate 55 项；临时 PostgreSQL 16.14 空库从 V1 顺序迁移到 V55并成功 validate 55 项，临时容器已销毁。
- 两仓 `git diff --check` 与两个部署触发脚本 `bash -n` 通过。
- 额外 SpotBugs 全库检查仍有 51 个既有高等级告警，集中在未改动的测试默认字符集和既有入库/出库空指针路径；本工作包未批量修改无关文件，该门禁不能标记为通过。

当前剩余项属于发布后验收和运行治理：生产雪花机器号与时钟配置；历史数据只读扫描、人工清洗和守恒归档；真实
`pg_index`、锁等待、WAL 与索引空间实测；生产 fallback 连续完整业务周期为 0；Dashboard、告警、备份恢复和回滚演练；修复现有 E2E selector/fixture 契约并全量重跑；
为后续自动 tag 部署配置并维护 `PROD_FLYWAY_TARGET`。代码提交、数据库切换和当前版本生产部署已经完成。现有 `test:e2e:mock` 只启动 Vite并跳过后端健康检查，仍把 `/api` 代理到
`127.0.0.1:11211`，没有覆盖稳定身份的共享 API mock，因此后端未启动时系统性 `ECONNREFUSED`；该项不得标记为通过。

因此当前可认定“稳定身份代码、生产数据迁移和当前版本部署完成”，但在完整 E2E、业务守恒、fallback 周期和回滚演练完成前，不能认定全系统验收完成。

### 1.2 本次续作收口点（截至 2026-07-13）

| 项目 | 当前状态 | 下一动作 |
| --- | --- | --- |
| 采购/销售完成状态同步 | 已部署生产；采购 91 项、销售订单/出库 213 项定向通过 | 观察真实业务状态流转并归档余额/库存守恒 |
| 物流稳定来源与下游保护 | 已部署生产；来源明细 ID、按 ID锁源、占用和快照继承 91/91；下游保护 6/6 | 完成物流/对账/财务跨域状态流与守恒核验 |
| 候选稳定 ID | 已部署生产；供应商 126/126、客户/项目/物流商 128/128、销售 117/117 | 验证真实分页 total、权限、软删除和占用行为 |
| 前端稳定身份链 | 实现与生产部署完成；Statement 109/109，Vitest 6181 passed + 4 skipped，typecheck/lint/build/audit 通过；`4e93949` 的两条销售交付核定 E2E 收尾及远端 CI 通过 | 在隔离环境归档并重跑完整 E2E，确认采购订单导入、附件 smoke、API Key 详情候选数据和业务守恒 |
| V20–V56 迁移证据 | 两份独立 SHA-256 manifest、迁移契约、空 V55 库、D1/D2 黄金副本、D3/D4 候选库及实时生产 V56 切换均通过；release repair SQL/契约已随 Leo `v3.0.0` 进入远端 | 继续生产 fallback/守恒观察，归档锁/WAL/空间数据并演练从保留的 V11 库或 dump 回滚 |
| 本地与远端质量门禁 | 后端空 V55 库 6184/6184 + verify、前端 6181 passed + 4 skipped、本地构建/审计、两仓 diff check 通过；Aries `4e93949` 的 Typecheck、Build、Lint、4 个测试分片、React Doctor 和 release workflow 全部成功 | 归档并重跑完整 E2E；不复用未归档运行时观察，不把定向链路通过扩写为全量通过 |

### 1.3 生产快照与隔离副本演练（2026-07-12）

本轮只读生产检查和副本演练的边界如下；所有持久化迁移均发生在 `127.0.0.1:55432` 的独立 PostgreSQL 18.4 实例，未对 `Master_Prod` 执行 DDL/DML。

- 生产库：`Master_Prod`；2026-07-12 22:52 CST 使用强制只读事务复核，主线 `flyway_schema_history` 为 11 success、0 failed、最高 V11；生产 Java PID `4025008`、端口 `57217`，`/api/health` 返回 200/UP；Leo/Aries runner PID `2553060`/`2553098` 均存活。
- 快照：使用 `pg_dump` 一致性事务快照（持有读取级锁以阻止并发 DDL，不踢连接、不停应用），文件为 `/home/instance/.local/state/steelx-migration-test-20260712/master_prod_v11.dump`，356,059 bytes，SHA-256 `9e42a19d47d72310b62a36f1a98c4c82573d1a6db50a823427515eaaace65b88`；目录权限 700，快照权限 600。
- 黄金副本：数据库 `master_prod_migration_gold_20260712`，从上述快照恢复；主线按 `V12–V29 → D1(target=1) → V30–V47 → D2(target=2) → V48–V55` 执行，主线 55/55、repair history 3/3（baseline + D1 + D2），两条 `flyway:validate` 均成功。
- 阻断证据：未执行 repair 时，V30 因销售客户/项目身份空值按预期整事务失败；D1 后 V30 可通过；V49 的车辆身份快照阻断由 D2 修复后继续通过。D1/D2 均锁定主线精确版本、数据库名、快照指纹和双向 `EXCEPT` 漂移校验。
- 数据完整性：黄金副本 68 张表中 57 张稳定基线表行数哈希前后一致；销售、合同、出库、发票、客户结算、物流和车辆未解析计数均为 0；失效索引 0；V48/V49 新约束 2/2、V51/V52 新外键 6/6；剩余 15 个未验证约束全部是 V1 基线既有状态 CHECK。
- 应用冒烟：以当前后端 jar 在隔离端口 `57218` 冷启动 39.449 秒，Flyway V55 up-to-date；health、认证 ping、版本、系统健康均 200，未认证客户 option 返回预期 403；冒烟前后 68 张表行数哈希一致，隔离 Redis db15 始终 0 key，PID 已停止。
- 空库质量门禁：新建空库 `leo_empty_v55_ci_20260712`，V1→V55 和前后 validate 均通过；隔离 Redis `127.0.0.1:16380/db15` 初始/结束均 0 key；`mvn -B -ntp test` 为 6184/6184、0 failure/error/skip，`mvn -B -ntp -DskipTests verify` 的 Checkstyle 0 violation、JaCoCo 达标。
- 数据副本测试边界：早先直接在带生产数据的迁移副本跑测试时，`UserAccountRepositoryTest` 仅因清理快照用户触发 V51 外键而出现 13 个 cleanup error；该结果不代表迁移失败，也不通过删除数据或放宽约束解决，最终门禁以空 V55 库结果为准。
- 演练身份：当前 D1/D2 使用 `REHEARSAL-20260712-*` 项目编码和预分配车辆 ID，仅用于副本；正式生产必须重新冻结快照并由业务批准正式项目/车辆 ID、编码及“仓库/仓库”归属，禁止直接复用演练值。
- 发布失败关闭：生产 `SPRING_FLYWAY_TARGET` 与 GitHub production 环境/仓库级 `PROD_FLYWAY_TARGET` 均未配置；当前 runner 自动发布会在 Flyway target 一致性校验处失败关闭，不会自动迁移生产库。
- 当前未决：正式 repair 映射、生产机器号/时钟、历史守恒、锁/WAL/空间评估、共享环境、现有 E2E selector/fixture 契约修复与全量重跑、生产 fallback 完整周期、正式发布和 runner 编排仍未授权。

### 1.4 首次真实 E2E 隔离执行结果（认证夹具缺失，2026-07-12）

- 执行窗口：2026-07-12 22:44:31–22:54:57 CST；使用迁移后的可写副本 `master_prod_migration_e2e_20260712`、Redis db14、Leo `11211`、Aries `3100`，未复用既有 storage state。
- 最终统计：84 tests / 19 files / 1 worker，8 passed、76 failed、0 skipped，耗时 10.4 分钟。8 项仅覆盖匿名重定向、认证前路由边界和 API path 静态契约，未覆盖认证后的稳定身份业务流。
- 阻断归因：本轮未配置 `E2E_API_KEY`，复制库 `auth_api_key` 为 0 行且 `sys_user` 不含 `test9`/`sakura` 测试账号；65 项密码会话和 8 项硬编码 `test9` 登录在认证阶段失败，首个错误为“账号或密码错误”，随后触发登录锁定/限流；另有 3 项独立前端路由断言失败。认证失败的 73 项不能写成迁移失败，3 项前端断言也未提供稳定身份业务流证据。
- 安全边界：本轮首次 E2E 未写入测试账号、未放宽认证、未连接生产取得会话；迁移快照仍包含快照中的复制用户凭据（至少 `password_hash`/加密 TOTP），但本轮未使用其登录。隔离 PID 已停止，`11211`/`3100` 已释放，E2E 副本与黄金库活跃连接均为 0，Redis db14 已清空；E2E 副本 68 张表仅 `sys_operation_log` 因失败登录增加 5 行。生产 `57217` 和两个 runner 未停止、未重启、未改动。
- 证据：长期摘要 `/home/instance/.local/state/steelx-migration-test-20260712/e2e-real-summary.txt`；完整日志 `/home/instance/.local/state/steelx-migration-test-20260712/e2e-real/playwright-real.log`；76 份原始失败上下文保存在 `/tmp/steelx-e2e-run-20260712/test-results`，后者仅作临时附件。

### 1.5 开发测试副本、测试夹具与认证后 E2E（2026-07-12 至 2026-07-13）

为避免影响 runner 自动发布、生产应用和生产 Redis，本轮从已迁移黄金副本创建独立开发测试库；所有测试持久化写入仅发生在隔离 PostgreSQL `127.0.0.1:55432`。

| 项目 | 结果 | 边界与限制 |
| --- | --- | --- |
| 数据来源 | 创建 `master_prod_devtest_20260712`，来源为 `master_prod_migration_gold_20260712`；其上游为生产 V11 一致性快照 `master_prod_v11.dump`，SHA-256 `9e42a19d47d72310b62a36f1a98c4c82573d1a6db50a823427515eaaace65b88` | 快照生成于 2026-07-12 21:28:13 CST，不是当前生产库的实时副本；需要当前生产数据基线时必须重新取得一致性快照 |
| Flyway 状态 | 主线 55/55 success；repair 线 baseline + D1 + D2，共 3/3 success；测试 fixture 线 baseline + S1，共 2/2 success；三条线均通过 `validate` | fixture 是开发测试专用迁移线，不属于生产主线或正式 repair 线，不得指向生产数据库 |
| 测试 fixture | S1 校验目标库名、主线精确 V55、repair 精确 D2；在 clone 中创建 `test/12345678`（NORMAL、部门 ID 2、ADMIN 角色 ID `700520000000000001`，关闭 TOTP 与首次强制设置）；复制来的 refresh token 全部撤销，当前 API Key 行数为 0 | 快照中的 `admin_prod` 仍保留 BCrypt `password_hash` 和加密 TOTP secret，不能宣称未复制生产密码哈希。凭据测试只使用 `test`；副本必须限制访问并配置独立 JWT/TOTP 密钥与 Redis 命名空间，不得用于共享环境或生产 |
| 凭据隔离核对 | clone 的 `sys_user` 有 `admin_prod` 与 `test` 两行；`sys_security_secret` 为 0 行；`auth_api_key` 为 0 行；`auth_refresh_token` 当前 active 为 0 | 该副本不是匿名化凭据副本；若要共享或重跑认证 E2E，必须新增 clone-only 脱敏 fixture，禁用复制用户并验证 API Key、refresh token 和 JWT 均不可用 |
| 认证冒烟 | `test/12345678` 的 login HTTP 200、refresh HTTP 200、logout HTTP 200，权限数量 41 | 证明隔离账号和认证链可用，不等价于全部业务 E2E 通过 |
| 真实 E2E | 2026-07-12 23:47:43 至 2026-07-13 00:25:49 CST，84 tests / 19 files / 1 worker；25 passed、57 failed、2 skipped，耗时 38.1 分钟 | 认证阻断已解除；失败主要来自过时导航定位、旧 `.workspace-overlay-panel` 表单结构、缺少 API Key、旧安全密钥文案/路由 marker，以及 refresh 并发竞争。后端日志未出现 Flyway、FK、CHECK 或数据库锁错误，但全量 E2E 门禁仍未通过 |
| 会话与缓存收尾 | 测试及最终管理操作共产生 27 条 `test` refresh token 记录，最终活动 0、已撤销 27；隔离 Redis db14 在服务运行时观测到 19 个带 TTL key，服务停止后最终 0 key | 只通过正常认证/管理接口处理测试会话，未直接 SQL 写 token；保留数据库记录作为审计证据 |
| 服务与生产边界 | 隔离 Leo PID `834114` 和 Aries PID `834656/834670` 已正常停止，`11211/3100` 已释放；生产 Java PID `4025008` 的 `57217` 健康检查仍为 200/UP，生产 Redis `16379` 与 Leo/Aries runner 未触碰 | 当前结果只证明指定快照副本上的迁移与部分应用兼容性，不能替代生产实时数据、共享环境或正式发布验证 |

因此可以认定：生产快照复制、V11→V55 分阶段迁移、测试账号认证和部分认证后页面/API 已在隔离环境工作；不能认定全量 E2E、共享环境或生产发布门禁完成。剩余阻断包括 E2E selector/fixture 契约修复、业务余额与库存守恒、锁/WAL/空间评估、正式 repair 映射批准、生产机器号/时钟、fallback 完整周期、备份恢复和正式发布演练。

### 1.6 D3/D4 候选库与最新隔离 E2E 收口（2026-07-13）

本节记录 2026-07-13 的追加演练。它覆盖最新生产一致性快照的隔离候选库和由该候选库复制出的开发测试库；不改变第 1.3–1.5 节的历史证据，也不表示生产已经迁移。第 1.5 节旧副本仍按“包含复制用户凭据、不得共享”处理；下表的 S2 结果只适用于新的 `master_prod_devtest_20260713`。

| 项目 | 结果 | 边界与限制 |
| --- | --- | --- |
| 生产快照 | 有效文件 `/home/instance/.local/state/steelx-production-cutover-20260713/master_prod_v11_pre_cutover_20260713_011202.dump`，352,969 bytes，SHA-256 `9d9de7db5f6880993758c77a631e3718da9e63ab9257fd212b5d0e04acfe114f` | 同目录 `...011149.dump` 为 0 bytes 失败残留，明确禁止恢复；快照来自生产 V11，但所有后续写入只发生在 `127.0.0.1:55432` 隔离 PostgreSQL |
| 候选库迁移 | `master_prod_cutover_candidate_20260713` 按 `V12–V29 → D3 → V30–V47 → D4 → V48–V55` 完成；主线 55/55 success、repair history baseline + D3 + D4 共 3/3 success，0 failed | D3/D4 使用独立 `flyway_identity_repair_history` 和显式 target；只证明当前快照/manifest 组合可迁移，不能直接替代正式审批、锁/WAL/空间评估或生产执行 |
| 身份与结构扫描 | D3 创建 4 个项目身份，D4 创建 2 个车辆身份；销售客户/项目未解析 0、物流车辆未解析 0、失效索引 0、迁移后缺失根 ID 0 | 行数差异包含 V12–V55 预期新增表和系统种子变化；业务余额、库存逐单与全局守恒仍未归档 |
| 开发测试库 | `master_prod_devtest_20260713` 为候选库完整复制；主线 V56/56、repair 3/3、fixture baseline + S1 + S2 共 3/3，均 success；`test` 为唯一 NORMAL 测试账号，S2 已禁用复制用户、覆盖其密码哈希并清除 TOTP，API Key 为 0 | V56 仅在该隔离库验证，不属于生产迁移；fixture 只允许指向该隔离库。无状态 JWT、独立 JWT/TOTP 密钥和 Redis 命名空间仍需单独验证后，副本才可扩大访问范围 |
| 最新可复核全量 E2E | 84 tests / 19 files / 1 worker；25 passed、57 failed、2 skipped，耗时 39.3 分钟 | 认证链已通过；失败集中在旧页面标题定位、canonical 表单字段、三条对账链旧“生成弹窗”流程、缺少“盘螺”/API Key 前置数据、旧 route/marker 文案及 refresh 并发。后续修复后的运行时观察未形成独立完整归档，不能替代该结果或发布门禁 |
| 已提交前的测试修复验证 | 已移除不存在的列表标题按钮断言，表单 helper 支持 canonical ID 与可见控件过滤，供应商/客户/项目/付款/收款 locator 改用 canonical key；密码认证会话按 Playwright request context 隔离，刷新 cookie 从当前 context 同步，并修正 `/permissions/catalog` | Biome、ESLint、TypeScript 和 `git diff --check` 通过；修复后的完整 Playwright 结果尚需独立归档；三条 Statement 已按“打开编辑器 → 选择 canonical 往来方 → 父单导入 → 保存并审核”验证 |
| 会话与服务收尾 | 隔离 Leo `11211`、Aries `3100`、PostgreSQL 代理和隔离 PostgreSQL 已停止；停止前只读核验 `auth_api_key=0`、refresh token 609 条（活动 3 条）、Redis db14 有 603 个带 TTL key；生产 `57217` 仍正常 | 未执行隔离 Redis `FLUSHDB`、未直接 SQL 清理或写入生产；活动会话与 TTL key 的最终清理需另行授权并保留审计证据 |
| 生产边界复核 | 生产 Java PID `4025008` 的 `57217/api/health` 为 200/UP；Leo/Aries runner PID `2553060`/`2553098` 存活 | 未停止或重启生产服务，未提交、push、部署、替换数据库，也未对 `Master_Prod` 执行 DDL/DML |

截至本节，能够认定的是“最新生产 V11 快照在隔离候选库完成 V12–V55 目标迁移，开发测试副本进一步完成 V56 验证，认证链可用”；最新可复核完整 E2E 仍为 25 passed、57 failed、2 skipped，后续修复后的运行时观察没有独立完整归档，不能据此宣称全量通过。生产 `Master_Prod` 仍为 V11，未迁移、未替换、未部署；下一步是独立归档并重跑完整 E2E、完成共享环境与发布级外部门禁后再申请正式发布审批。

### 1.7 实时生产切换与 V56 收口（2026-07-13）

- 维护窗口先停止生产 Leo，使用 PostgreSQL 18 客户端对当时的 `Master_Prod` 生成一致性 dump：`355,385 bytes`，SHA-256 `a9f81bf754db4a362369f94bca8f01815011c7aea7a80cb81767ff964e0201c8`，文件权限 600；原库未执行迁移、删除或重命名。
- 从该 dump 创建 `master_prod_cutover_current_20260713_102500`，按 `V12–V29 → D3 → V30–V47 → D4 → V48–V56` 顺序执行；主线 `56/56`、repair `baseline + D3 + D4 = 3/3`，两条 `validate` 成功。D3/D4 实时 manifest SHA-256 分别为 `98d0bc9f7690890673e7c17e083c3d051e230a67b4000c0dcc27079b5b7073de` 与 `3a7b0b3f40779dc0fd76e3a9d4f26c64253b651f769c3b1c17380ed4c646afc2`。
- 迁移后 64 张原有表仅出现预期行数差异：Flyway history、D3 新增 4 个项目、D4 新增 2 辆车辆及菜单/动作/编号规则/角色权限种子；失效索引 0、新增未验证约束 0。
- 生产配置 `SPRING_DATASOURCE_DB` 已切换到该副本，并显式设置 `SPRING_FLYWAY_TARGET=56`；旧 `Master_Prod` 仍可连接且保持 V11。Leo `v3.0.0` 已由 production-deploy run `29220370990` 部署到 `57217`，Flyway 校验 56 项且无需迁移；`/api/health`、认证 ping、版本接口为 200，未认证系统健康与客户 options 为 403。
- Aries `v2.3.0` 已由 frontend-production-deploy run `29220231919` 完成本地生产部署；Nginx 返回的生产 `index.html` 与 `/instance/steelx/frontend/current/index.html` SHA-256 一致。首次 Leo tag deploy 因缺少 `PROD_FLYWAY_TARGET` 在安装前失败关闭，随后使用显式 `flyway_target=56` 的既有 workflow_dispatch 成功完成构建、打包、安装和健康检查。
- 此次切换没有把失败 E2E 写成通过：最后一次可复核完整归档仍为 `25 passed / 57 failed / 2 skipped`；后续完整 E2E、生产 fallback 周期、业务守恒和发布后验收仍未完成。

### 1.8 发布后核验与工作区收口（2026-07-13）

- 生产应用只读复核：`/api/health`、`/api/auth/ping`、`/api/version` 均为 200；版本接口返回 Leo `3.0.0`、commit `731e054`。当前 release 为 `/instance/steelx/backend/releases/20260713105821-leo-production-release`，本次启动日志显示 Flyway 成功校验 56 项、schema 已在 V56且无需迁移，启动后未发现新的 `ERROR`、`FATAL` 或异常堆栈。
- 数据库只读复核：应用实际连接 `master_prod_cutover_current_20260713_102500`，主 Flyway 最高 V56、0 failed；repair history 为 baseline V0 + D3 + D4、0 failed；失效索引 0。旧 `Master_Prod` 精确名称仍存在，最高 V11、0 failed。
- 未验证约束口径：旧库有 15 个、新库有 14 个；`新库 - 旧库` identity 差集为 0，证明本次迁移没有新增未验证约束。`旧库 - 新库` 仅有 `public.so_sales_order.chk_so_status`，因此文中“新增未验证约束 0”不能误读为“新库存量 0”。
- 发布与 runner：Leo production-deploy run `29220370990`、Aries frontend-production-deploy run `29220231919` 均成功；GitHub API 显示 `PolarisTime-steelx-leo` 与 `PolarisTime-steelx-aries` 为 online/idle。各仓库另有一个旧名称 offline runner 注册，未影响当前执行器。
- Leo `731e054` 和文档提交 `bc2d3b7` 的 backend-ci 均只有 `Package & Smoke` 失败：prod profile 的 CI 容器未传 Flyway target，`FlywayStageGateConfiguration` 按设计以 `Missing production Flyway target` 失败关闭；静态分析、6 个测试分片、Flyway verify、package 和 image build 均成功。该 CI 配置缺项不改变显式传入 target=56 的生产部署成功结论，但未来自动 tag 部署仍需持久配置 `PROD_FLYWAY_TARGET`。
- Aries 追加提交 `4e93949 test(e2e): 补齐销售订单交付核定确认链路`，仅修改 3 个 E2E 文件：抽取共享 `confirmSalesOrderDelivery`，并在采购销售链和客户对账收款链验证销售订单从“交付核定”进入“完成销售”。本地 TypeScript、Biome、ESLint 通过，Playwright 正确收集 2 条目标测试；未对生产执行会写业务数据的真实 E2E。
- `4e93949` 的 frontend-ci run `29221754760`、React Doctor run `29221754744`、frontend-release run `29221754785` 全部成功；Typecheck、Build、Lint 和 4 个测试分片均成功。semantic-release 明确判定 `test:` 提交无相关版本变更，因此未生成新 tag、未再次部署，生产前端仍为 `v2.3.0`。
- 原始工作区未执行 reset/clean：`leo/main` 为 ahead 6 / behind 10，`aries/main` 为 ahead 14 / behind 18，仍显示 modified/untracked；这些是保留的 patch-equivalent 历史。逐文件 blob 复核为 Leo 13/13、Aries 40/40 与各自 `origin/main` 一致，差异和远端缺失均为 0；提交和 push 使用独立干净工作树，未把凭据、版本文件或构建产物重复提交。

## 2. 结论

当前架构已经使用雪花 ID 作为可独立寻址业务实体的内部主键；这不是本计划未来才引入的新机制。审计基线的缺口不是“没有 ID”，
而是部分业务表、API 和报表没有持续传递这些既有 ID，仍使用编码、名称、规格、仓库名称或业务单号完成关联和分组，
从而可能在主数据修改后把同一对象拆成新旧两组。当前工作树已在需求范围内完成稳定 ID双写、读模型、候选、来源链和前端契约收口；
历史数据是否已无偏移仍必须由生产只读扫描、守恒对账和 fallback 观察证明。

目标模型统一为：

```text
雪花 ID       = 内部不可变身份、数据库关联、权限校验和报表分组键
业务编码       = 人工识别、搜索、导入和外部交换字段
名称/规格/单位 = 单据生成时的历史快照和展示字段
业务单号       = 可审计的单据编号，不作为唯一内部关联依据
```

本计划不替换现有雪花算法，也不把所有快照字段规范化删除。业务单据必须同时保存稳定 ID 和历史快照：
ID 保证归属不漂移，快照保证主数据改名后历史凭证仍按发生时内容展示。

“内部主键固定”只代表实体自身的 `id` 不随编辑变化。当前本地代码与 V20–V56 已让既有雪花 ID贯穿数据库外键、
后端 DTO/服务、前端字符串契约和报表分组；但只有目标环境的历史数据、运行观测和发布门禁也通过后，才能认定生产领域完成稳定身份改造。

## 3. 已确认的技术基础

### 3.1 雪花主键已经存在

- 通用创建流程通过 `BusinessCreateIdResolver` 和 `SnowflakeIdGenerator` 生成实体 ID。
- `AbstractCrudService` 仅在创建时调用 `assignId`，更新请求不重新生成或替换主键。
- 采购、销售、物流、财务、主数据和系统模块可独立寻址的核心实体主键均为 PostgreSQL `BIGINT` / Java `Long`，
  新增业务实体继续沿用同一雪花主键机制。
- 部分来源关系已经保存雪花 ID，例如采购入库明细的来源采购订单明细 ID、采购退款的来源采购订单 ID、
  供应商退款到账的采购退款 ID、付款的来源采购订单或对账单 ID。
- 聚合报表行不是持久化业务实体，不为其制造新的雪花主键；明细报表返回真实业务明细 ID，聚合报表另用明确的稳定维度键。

### 3.2 前端精度边界本地已收口

雪花 ID 通常大于 JavaScript 的 `Number.MAX_SAFE_INTEGER`。后端 `JacksonConfig` 已把 `Long`/`long` 全局序列化为
JSON 字符串，这是必须保留的系统契约。审计基线中的 `RawApiRecord`、`ModuleRecordInput` 和部分财务 DTO曾允许未经检查的
`string | number`，大整数 number 会在 Axios JSON 解析后不可逆丢失精度。当前工作树已在身份 decoder、领域 schema、保存白名单、
草稿、路由和候选链统一字符串 `EntityId`，unsafe number 在边界失败关闭，并由 6181 项前端全量测试及 TypeScript 门禁覆盖。

后续改造必须继续遵守：

```text
Java / PostgreSQL：Long / BIGINT
JSON：十进制字符串
TypeScript：EntityId = string
```

禁止在前端对雪花 ID 使用 `Number(...)`、`parseInt(...)`、算术运算或隐式数值排序。最终生产 API 契约只能返回字符串；
兼容期若仍接收 number，只允许 `Number.isSafeInteger` 的旧小整数并记录告警，任何不安全整数必须在边界直接拒绝，不能格式化后继续保存。

### 3.3 雪花生成器运行基线

关系 ID 改造依赖现有雪花主键长期不碰撞，因此实施前必须把生成器本身纳入发布门禁：

- 保持 `SnowflakeIdGenerator` 的 epoch、41 位时间、10 位机器号和 12 位序列布局不变，禁止重算或重写任何历史主键。
- 生产环境必须设置 `LEO_ID_STRICT_MACHINE_ID=true`；每个并发运行实例分配唯一的 `LEO_MACHINE_ID`（1–1023），
  机器号登记到部署清单，同一时间不得复用。
- 系统时钟回拨时生成器应继续失败关闭，并产生可告警日志/指标；部署节点必须启用可靠时钟同步，禁止通过回拨系统时间绕过。
- 携带 `X-Preallocated-Id` 的创建请求必须由预分配服务校验“模块 + 当前主体 + 保留状态”。预分配服务不可用时拒绝该请求，
  不再接受任意正 Long 的兼容分支；未携带该头时才由本机雪花生成器创建 ID。
- 根实体更新 DTO 不接收 `id`；子项只通过 `ManagedEntityItemSupport.syncById` 复用已有 ID或生成新 ID，拒绝重复、跨父单和伪造 ID。
- JPA 主键映射显式标记不可更新；更新事务保存前后断言根 ID与所有既有子项 ID不变。
- 数据迁移只回填新的关系 ID，绝不重新生成实体主键。迁移前后每条原记录的 `id` 必须逐值保持不变。

### 3.4 身份字段术语与所有权

为避免“字段是 Long 就可以互换”的错写，设计、代码评审、OpenAPI 和数据字典统一使用以下术语：

| 类型 | 示例 | 生成/写入方 | 可否修改 | 用途 |
| --- | --- | --- | --- | --- |
| 实体主键 | `so_sales_order.id` | 创建时由雪花生成器或受控预分配服务写入一次 | 否 | 唯一寻址、审计、更新 |
| 主数据关系 ID | `customer_id`、`material_id` | 服务端按请求 ID解析或从来源链继承 | 草稿且无下游事实时受控修改 | 归属、权限、分组、FK |
| 来源关系 ID | `source_*_id`、`source_*_item_id` | 从被导入/被结算的真实来源记录继承 | 形成下游事实后不可修改 | 容量、占用、追溯、去重 |
| 历史快照 | `customer_name`、`material_code`、规格、单位 | 服务端从当时主数据或来源单据复制 | 仅按更正流程修改 | 展示、打印、审计，不决定身份 |
| 聚合维度键 | 应收应付 `groupKey`、库存维度键 | 查询时由稳定 ID维度确定性构造 | 非持久化实体，不分配雪花 ID | 页面分组和明细下钻 |
| 业务/协议标识 | 订单号、菜单码、`traceId` | 各自领域生成 | 按领域规则 | 人工识别、协议或观测，不冒充实体 ID |

同名、同编码或数值偶然相同均不能证明两个字段指向同一实体。每个 `*_id` 必须在字段注释、Java/TypeScript 类型和
测试中声明唯一目标实体类型；例如 `customerId` 不能赋给 `projectId`，即使两个 Long 的当前数值恰好相等。

### 3.5 “底层 ID 固定”的完成层级

本计划把完成度分为四层，避免仅看到主键存在就误判全系统已经固定：

1. **L1 主键固定**：实体自身雪花 `id` 创建后不变。当前核心实体基本已满足。
2. **L2 关系固定**：所有跨实体和跨单据关系都保存目标雪花 ID，并由类型校验/FK保护。V38–V56 已在当前生产执行并 validate；历史业务守恒、删除语义和锁影响仍需归档。
3. **L3 读模型固定**：库存、应收应付、候选、容量和导出都按 ID计算，不再按名称、编码或单号猜测。核心读模型、候选和容量链已部署生产；真实数据守恒、权限和 fallback 观察尚未完成。
4. **L4 契约固定**：前端、API、迁移、观测和发布门禁全部保持字符串 ID，兼容回退清零并移除。代码、数据库和当前版本部署已完成，但生产 fallback 周期、完整 E2E、观测和回滚验收尚未完成。

只有 L1–L4 全部通过第 12 节验收，才能对外声明“整个系统固定”。L1 已存在不等于修改主数据后库存和财务不会偏移；
真正消除偏移需要 L2/L3 把原本基于快照文本的关联和分组改为既有雪花 ID。

## 4. 稳定身份覆盖矩阵

| 领域 | 当前内部身份 | 关联方式 | 当前状态 | 发布后剩余风险 |
| --- | --- | --- | --- | --- |
| 业务实体主键 | 雪花 `id` | 主表主键 | 已固定 | 无统一问题 |
| 采购/销售来源明细 | 来源头/明细雪花 ID + 单号快照 | 来源、容量、占用和完成状态按 ID；单号只展示/检索 | 已部署生产 | 真实状态流和余额/库存守恒尚未完整归档 |
| 结算主体 | 主数据雪花 ID + 名称快照 | 业务、财务和报表按 `settlement_company_id` 隔离 | 已部署生产 | 历史空值、删除保护和 fallback 观察需复核 |
| 供应商 | `supplier_id` + 编码/名称快照 | 采购、收票、对账、付款、退款和候选按 ID | 已部署生产 | 历史回填守恒、fallback 清零和权限验证 |
| 物流商 | `carrier_id` + 编码/名称快照 | 物流、车辆、对账、付款和候选按 ID | 已部署生产 | 车辆归属已由 V48/V49 验证；剩余真实状态流和 fallback 观察 |
| 客户 | `customer_id` + 编码/名称快照 | 销售、出库、物流明细、开票、对账、收款和候选按 ID | 已部署生产 | 历史错绑清洗与 fallback 清零 |
| 项目 | `project_id` + 名称快照 | 按真实项目 ID并校验客户归属；候选按 `customerId/projectId` | 已部署生产 | 历史项目客户归属和业务守恒验证 |
| 商品/SKU | `material_id` + 商品快照 | 单据明细、来源链、库存和报表按 ID | 已部署生产 | 商品身份属性业务决策与生产守恒报告 |
| 仓库 | `warehouse_id` + 名称快照 | 入出库明细、库存和报表按 ID | 已部署生产 | 仓库合并策略与生产守恒报告 |
| 批次 | 暂无独立实体，批次号是业务维度 | 批次号字符串 + 来源明细 | 已按当前模型部署 | 生效后改号规则和独立库存层触发条件仍需业务确认 |
| 发票/对账/收付款 | 单据与 typed party/source 雪花 ID | 直接来源、分配、占用和候选按 ID；快照仅展示 | 已部署生产 | 历史兼容命中、真实权限和 fallback 周期未验证 |
| 报表 | 聚合行使用稳定 ID维度键 | 库存、出入库、应收应付按实体/结算主体 ID聚合 | 已部署生产 | 生产数据守恒与 SQL 执行计划未归档 |
| 系统/认证/附件 | 实体 BIGINT ID，普通关系使用 FK | V50–V52 补索引并验证 8 条普通 FK；协议键和多态 `record_id` 明确排除 | 已生产执行 | 删除语义、历史守恒和锁影响报告未完成 |

### 4.1 已部署修复的 P0 身份错写

审计基线中的 `SalesOrderApplyService` 会在应用销售订单请求时把 `Customer.id` 写入 `SalesOrder.projectId`，而
`so_sales_order.project_id` 的外键目标是 `md_project(id)`。通常会直接触发外键失败；如果客户 ID 与项目 ID 偶然碰撞，
则会静默绑定错误项目。当前生产代码已经改为分别写入 `customerId` 与 `projectId`，并增加 ID碰撞、跨客户、同名项目和快照冲突回归测试；
新写入错写风险已关闭，但在历史异常扫描、清洗和守恒归档完成前，不能宣称历史风险清零。

审计基线中的 `md_project` 主要保存 `customer_code`，没有完整的 `customer_id` 外键关系；当前生产已通过 V20–V56增加、回填并约束
`customer_id`，服务校验固定客户与项目归属，编码和名称仅保留为快照或检索字段。

## 5. 架构决策记录

### ADR-001：使用现有雪花主键作为全系统内部稳定身份

**状态：** Accepted for implementation plan

**问题：** 系统已有雪花主键，但部分跨模块关联仍依赖可修改编码、名称和业务单号。

| 方案 | 优点 | 缺点 | 决策 |
| --- | --- | --- | --- |
| 继续以编码为稳定身份 | 改动最小 | 编码修改、复用和外部导入会导致漂移 | 不采用 |
| 新增 UUID 身份体系 | 跨系统常见 | 与现有雪花 ID 重复，增加迁移和运维复杂度 | 不采用 |
| 复用现有雪花 ID | 已覆盖所有实体、数据库和服务成熟、改造最小 | API 必须严格按字符串传输 | 采用 |

**接受的权衡：** API 中的 ID 可读性低于业务编码，但 ID 只用于内部关联，页面仍展示业务编码和名称。

### ADR-002：业务表同时保存主数据 ID 与历史快照

**决策：** 业务表保存 `*_id`，同时保留 `*_code`、`*_name`、规格和单位等发生时快照。

不采用“查询时全部 JOIN 当前主数据”的原因：

- 主数据改名后不能改写历史合同、订单、发票和打印内容。
- 已删除或停用主数据仍需要支持历史单据查询。
- 财务和库存归属使用 ID，历史展示使用快照，两者职责不同。

### ADR-003：采用 Expand → Backfill → Switch → Contract 的兼容迁移

**决策：** 至少跨三个应用发布阶段完成，不在单条迁移中同时新增字段、回填、设为非空并删除旧回退。

原因：

- 旧应用节点不会写入新增 ID 字段。
- 历史数据可能存在同名、多编码、已软删除主数据或无法唯一匹配。
- 大表回填、索引和约束验证可能锁表，必须可观测、可中止和可重试。

### ADR-004：默认不新增库存层身份体系

**决策：** P0 库存归属键先统一为 `material_id + warehouse_id + batch_no_normalized`。批次号必须在服务端规范化，
库存生效后不可原地修改；本阶段不默认新增 `inv_stock_lot` 或批次 UUID。

只有出现以下任一已确认需求，才另立 ADR 并为库存层创建现有雪花机制生成的 `BIGINT` 主键：

- 批次号需要在保留同一库存身份的前提下改号或复用。
- 一条出库明细需要分配到多个库存层。
- 库存层需要独立的冻结、转移、质检、成本或追踪生命周期。

**接受的权衡：** 当前批次维度仍是规范化业务值，无法表达尚不存在的独立库存层生命周期；该复杂度可在需求出现后增加，
不影响先用既有商品和仓库雪花 ID 解决当前库存裂组问题。

### ADR-005：多态往来方与有限来源采用不同建模方式

**决策：** 台账调整的客户、供应商、物流商属于开放的多态往来方，使用 `counterparty_type + counterparty_id`，
由成对检查约束、领域解析器和删除保护目录共同保证；不为此新增统一往来方超级表。

付款分配的来源只可能是供应商对账单或物流对账单，目标集合固定且财务完整性要求更高，因此使用两个显式可空 FK：
`source_supplier_statement_id`、`source_freight_statement_id`，再以 `num_nonnulls(...)` 约束恰好一个来源。
收款分配只指向客户对账单，直接使用语义明确的客户对账单 FK。

**权衡：** 多态往来方不能获得普通数据库 FK，接受服务层和删除保护的额外责任；有限财务来源会增加两个字段，
但换取数据库可验证的目标表语义。只有未来出现统一往来方生命周期时，才重新评估超级表。

### ADR-006：静态协议键不强制替换为关系 ID

**决策：** 运行时业务实体、主数据和可审计关系使用雪花 ID；菜单码、资源码、动作码、模块键等静态协议键继续作为
权限、路由、种子数据和外部契约的受控自然键。其数据库行仍保留 BIGINT 主键，但不为了形式统一把所有代码关系改成 ID。

附件绑定和操作日志的目标业务记录是跨模块多态关系，继续使用 `module_key + record_id`，由模块目录验证 record ID所属实体。
安全令牌的 `token_id`、请求 `traceId` 等协议/观测标识不属于业务实体主键，不套用 `EntityId` 规则。

**权衡：** 受控代码变更需要严格兼容流程，但可保持权限和路由契约稳定；若未来菜单/资源成为用户可重命名业务实体，再另立迁移方案。

## 6. 全系统身份不变量

实施完成后必须持续满足：

1. 所有可独立寻址、审计和更新的持久化业务实体使用现有雪花 `BIGINT` 主键；主键创建后不可修改，任何更新 DTO
   都不得把主键映射回实体，也不得因编码或名称变化重新生成 ID。
2. 所有跨主数据关联使用 `*_id`，不得以名称作为唯一关联条件。
3. 所有跨业务单据关联优先使用 `source_*_id` / `source_*_item_id`，业务单号只保存为快照。
4. 报表、库存、应收应付和权限校验按 ID 分组，编码和名称只用于筛选与展示。
5. 同名不同 ID 必须保持分离；同一 ID 改名或修改允许的展示属性后必须仍归入原组。
6. 服务端根据 ID 读取权威主数据并写入快照，不信任客户端提交的名称、规格或结算主体名称。
7. 已有生效业务引用时，禁止删除主数据；身份属性变更必须遵守领域规则。
8. 影响商品实际身份的属性变化必须新建 SKU，不能把已有库存的 SKU 原地改成另一种商品。
9. 无法唯一回填的历史数据失败闭合，禁止按名称排序后任取第一条。
10. 雪花 ID 在 JSON 和 TypeScript 中始终是字符串，往返后必须逐字符一致；前端不得接受不安全 number 后再转成字符串。
11. 可建立普通外键的来源 ID 和主数据 ID 必须由数据库 FK 兜底，默认使用 `RESTRICT/NO ACTION`，不得只有普通索引。
12. 批次号按统一算法规范化；形成有效库存事实后不可修改。相同批次号只在同一 `material_id + warehouse_id` 内表示同一批次维度。
13. 报表不得把 `ROW_NUMBER` 当成业务身份；明细行返回真实明细雪花 ID，聚合行明确标注为维度键而非实体 ID。
14. 每个 ID 字段只能指向文档指定的一张目标表；例如 `customer_id` 永远指向 `md_customer`，`project_id` 永远指向 `md_project`，
    禁止因字段同为 Long 而跨实体复用。
15. 硬删除引用由 FK 拒绝，软删除/停用由领域引用保护拒绝；历史身份 FK 不使用 `ON DELETE SET NULL` 抹除归属。
16. 生产实例雪花机器号必须唯一；预分配 ID、普通创建 ID和子项 ID均有明确且互斥的生成入口。

## 7. 目标领域模型

### 7.1 往来单位与项目

需要逐步把以下雪花 ID 传播到业务头、业务明细和财务来源：

- `customer_id` → `md_customer.id`
- `supplier_id` → `md_supplier.id`
- `carrier_id` → `md_carrier.id`
- `project_id` → `md_project.id`
- `settlement_company_id` → `sys_company_setting.id`

当前工作树已为 `md_project` 增加并回填 `customer_id → md_customer.id`，项目所属客户不再只靠 `customer_code` 维持。
销售订单中的 `customer_id` 与 `project_id` 必须分别解析、分别校验：若选择项目，服务端校验该项目确实属于所选客户，
禁止把一种实体的 ID 写入另一种实体字段。

覆盖范围至少包括：

- 采购合同、采购订单、采购入库、采购退款、收票、供应商对账、采购预付款、供应商退款到账。
- 销售合同、销售订单、销售出库、开票、客户对账、收款。
- 物流单头的物流商 ID，以及物流单明细的客户 ID、项目 ID。
- 物流对账和物流付款。
- 收付款、退款到账、预付款分配、应收应付和对账台账必须保存明确的往来方类型与 ID，不能只用名称或一个无类型的数字猜测实体表。
- 台账调整的往来方 ID。台账调整是多态往来方，可保留 `counterparty_type + counterparty_id`，
  由服务层按类型验证对应主数据；数据库用成对非空检查约束，不能建立指向三张表的普通外键。

普通单态字段必须建立真实 FK；只有 `counterparty_type + counterparty_id` 这类多态引用因 PostgreSQL 普通 FK 无法跨多张目标表，
才使用类型/ID 成对检查、服务端类型解析器和删除保护目录共同兜底。所有 `settlement_company_id` 需要补齐 FK 或明确记录无法建立的理由，
并把 V15–V18 新表加入结算主体删除引用清单。

供应商和物流商现有稳定编码保护不能立即删除。只有在所有有效历史业务行都具备主数据 ID、生产名称回退计数长期为 0 后，
才能另行决定是否允许修改业务编码。

### 7.2 商品与仓库

所有商品业务明细必须传播 `material_id`；只有实际库存事实、已有仓库快照或确需仓库追溯的明细才传播 `warehouse_id`：

- `material_id` → `md_material.id`
- `warehouse_id` → `md_warehouse.id`

具体范围：

- 采购/销售合同明细增加商品 ID，不凭空增加其当前不存在的仓库语义。
- 采购订单、采购入库、销售订单、销售出库明细增加商品和适用仓库 ID；实际入出库最终均必填。
- 采购退款明细继承商品 ID；仓库只作可空来源快照，不把退款误建模为库存移动。
- 物流单明细中引用的商品和仓库。
- 发票、对账单中需要追溯商品来源的明细；优先从来源业务明细继承，而不是重新按编码查找。
- 逐件重量通过父明细取得商品 ID，不重复存储；来源分配、打印和导出模型透传真实 ID用于审计。

商品编码、品牌、类别、材质、规格、长度、数量单位、重量单位和仓库名称继续保留为快照，但不得再参与库存归属主键。

商品身份属性分两类：

| 类型 | 示例 | 被库存引用后规则 |
| --- | --- | --- |
| 展示属性 | 备注、可选营销名称 | 可以修改，历史单据保留原快照 |
| 身份/计量属性 | 商品编码、品牌、材质、规格、长度、基础单位、数量单位 | 禁止原地改变语义；应新建新的雪花 SKU ID |

仓库名称、地址和联系人可以修改，因为库存按 `warehouse_id` 归属；仓库合并或停用必须通过显式库存转移流程，
不能把旧仓库 ID 替换成新仓库 ID。

### 7.3 库存批次维度

本阶段库存稳定键为：

```text
material_id + warehouse_id + batch_no_normalized
```

- `batch_no_normalized` 优先定义为 STORED generated column，表达式为 `NULLIF(BTRIM(batch_no), '')`；原 `batch_no` 继续保存发生时展示值。
  默认保留大小写语义，除非业务另行确认批次号大小写不敏感。应用不得直接写该生成列，数据库查询和索引统一使用它。
- 商品需要批次管理时，规范化后为空必须失败；不管理批次时服务端把 `batch_no` 统一保存为 `NULL`，从而令生成列为 `NULL`。
  生成表达式同时把历史空串和空格串折叠为同一无批次语义。
- 采购入库形成有效库存事实后，批次号不可原地修改。纠错应通过冲销/更正流程保留审计链。
- 销售订单和销售出库从来源明细继承商品 ID、仓库 ID 和原批次号快照，由数据库生成相同 `batch_no_normalized`，
  不允许应用直接写生成列或重新按页面文本匹配。
- 编码、名称、规格、仓库名和单位只作为发生时快照展示，不再决定入库与出库能否相互抵扣。

库存报表的汇总层按 `material_id`，仓库层按 `material_id + warehouse_id`，批次层按上述三元键聚合。
同一商品 ID 出现多个基础计量单位属于数据异常，必须阻断或进入清洗清单，不能继续把单位加入分组键来掩盖问题。

如果 ADR-004 的触发条件出现，再新增 `inv_stock_lot.id BIGINT`，该 ID 同样由现有雪花生成器产生；在此之前，
不得为了“看起来所有东西都有 ID”而把报表维度或批次字符串伪装成独立实体。

### 7.4 业务来源关系

以下核心来源列除索引外还应补齐 FK，并在历史数据清理后按业务语义收紧非空：

- 采购入库明细 → 采购订单明细。
- 销售订单明细 → 采购入库明细或采购订单明细；同时增加“最多一个来源”的检查约束。
- 销售出库明细 → 销售订单明细。
- 合同、发票、对账、收付款和退款中可定位到唯一来源头或明细的关系。

来源删除默认由 FK 拒绝；软删除和反审核继续由服务层按业务状态、下游容量和结算事实校验。`source_no` 只作为审计快照和检索字段，
不得替代 `source_id/source_item_id` 完成真实关联。

### 7.5 列、外键与索引约定

- 新关系列统一使用 PostgreSQL `BIGINT`、Java `Long`、JSON 十进制字符串和 TypeScript `EntityId`。
- Expand 阶段新增列全部可空；只有经过双写、回填、约束验证且业务语义必填的列，才在 Contract 阶段设为非空。
- 主数据和来源 FK 默认 `NO ACTION`；仅父表物理删除必然连带删除、且不存在独立历史意义的聚合子项可以使用 `CASCADE`。
- 每个 FK 列建立覆盖全部行的 B-tree。现有 `WHERE deleted_flag = false` 部分索引只服务业务查询，不能替代 FK 删除检查所需的全量索引。
- 业务列表可另建 `(party_id, business_date DESC) WHERE deleted_flag = false`；库存事实建立
  `(material_id, warehouse_id, batch_no_normalized)`，并为不处于左前缀位置的 `warehouse_id` 单独建索引。
- 所有 `*_code`、`*_name`、规格、单位、仓库名、批次号和业务单号列永久保留为历史快照，不给名称增加身份唯一约束。
- 数据库列注释必须写明目标表，例如“客户内部标识，引用 md_customer(id)”，避免 Long 字段语义再次漂移。
- 不为可从父明细稳定取得的值重复存 ID，例如逐件重量从采购订单明细取得 `material_id`；只有查询、历史或混合主体语义确有需要时才冗余。

### 7.6 主数据与合同逐表目标

| 表 | 新增/收紧字段 | 权威回填来源 | 最终约束与说明 |
| --- | --- | --- | --- |
| `md_project` | 新增 `customer_id` | 全历史 `customer_code` 唯一匹配；编码复用进入人工清洗 | 活动项目必填，FK → `md_customer`；索引 `customer_id`，并提供 `(id, customer_id)` 组合唯一键 |
| `md_material` | P2 新增 `material_category_id` | `category` 名称与类别快照唯一匹配 | FK → `md_material_category`；`category` 继续作快照，避免过磅规则随类别改名漂移 |
| `md_customer`、`md_carrier` | 收紧 `default_settlement_company_id` | 仅验证已有 ID 与名称，不用当前默认值改写历史 | 可空 FK → `sys_company_setting`；`md_vehicle.carrier_id` 作为已完成范例保持不变 |
| `ct_purchase_contract` | 新增 `supplier_id`；必要时补 `supplier_code` 快照 | 供应商编码优先；仅名称唯一时自动回填，否则人工 | 有效合同必填 FK → `md_supplier`；不凭空增加仓库 ID |
| `ct_purchase_contract_item` | 新增 `material_id` | 商品编码 + 完整规格快照唯一匹配 | 有效明细必填 FK → `md_material`；合同当前无仓库字段，不新增 `warehouse_id` |
| `ct_sales_contract` | 新增 `customer_id`、`project_id` 及编码快照 | 客户编码；项目必须同时匹配客户归属与名称 | 两个 FK 均 `NO ACTION`，组合关系阻止跨客户项目 |
| `ct_sales_contract_item` | 新增 `material_id` | 商品编码 + 完整规格快照唯一匹配 | 有效明细必填 FK → `md_material`；不新增无业务语义的仓库 ID |
| `ct_contract_purchase_order` | 不新增身份列 | 已有合同 ID、采购订单 ID | 两端非空 FK、组合唯一保持不变 |

`md_customer.project_name*` 在兼容期只作为旧默认展示/快照，不再代表项目关系。项目选项、保存和校验统一以 `md_project.id`
为权威，Contract 后评估是否停止维护客户表中的重复项目字段，但本计划不直接删除这些列。

### 7.7 采购、销售与库存逐表目标

| 表 | 新增/收紧字段 | 权威回填来源 | 最终约束与说明 |
| --- | --- | --- | --- |
| `po_purchase_order` | 新增 `supplier_id`；收紧结算主体 FK | `supplier_code` 全历史唯一匹配 | 有效订单 `supplier_id` 必填；结算主体按业务保持可空 |
| `po_purchase_order_item` | 新增 `material_id`、`warehouse_id`、生成列 `batch_no_normalized` | 商品编码/完整快照；仓库名唯一匹配 | `material_id` 必填；仓库当前允许未选时保持可空，ID 与名称成对 |
| `po_purchase_inbound` | 新增 `supplier_id`、`warehouse_id` | 来源订单全部明细的同一供应商；头部仓库名唯一匹配 | 有效入库均必填；`purchase_order_no` 可含多个编号，不新增错误的单值来源头 ID |
| `po_purchase_inbound_item` | 新增 `material_id`、`warehouse_id`、生成批次键；收紧 `source_purchase_order_item_id` | 优先从来源采购订单明细继承 | 有效库存事实商品/仓库必填；来源存在时 FK → 采购订单明细 |
| `po_purchase_refund` | 新增 `supplier_id`；收紧结算主体 FK | 直接从 `source_purchase_order_id` 继承 | 来源订单 FK 已存在，保持 `NO ACTION` |
| `po_purchase_refund_item` | 新增 `material_id`、可空 `warehouse_id`、生成批次键 | 直接从来源采购订单明细继承 | 来源明细 FK 已存在；退款不是库存移动，不强制仓库非空 |
| `so_sales_order` | 新增 `customer_id`；修正并收紧 `project_id` | 客户编码；项目 ID 仅在目标存在、属于客户且名称一致时可信 | 修复客户 ID 错写后回填；项目 FK 从 `SET NULL` 改 `NO ACTION` |
| `so_sales_order_item` | 新增 `material_id`、`warehouse_id`、生成批次键；收紧两个来源 ID | 入库来源优先，其次采购订单来源；无来源时按主数据 ID | 两来源 FK；`num_nonnulls(source_inbound_item_id, source_purchase_order_item_id) <= 1` |
| `so_sales_outbound` | 新增 `customer_id`、`project_id`、`warehouse_id` | 来源销售订单及实际出库仓库 | 有效出库均必填，客户/项目组合一致 |
| `so_sales_outbound_item` | 新增 `material_id`、`warehouse_id`、生成批次键；收紧来源销售订单明细 ID | 直接从来源销售订单明细继承 | 有效出库商品/仓库与来源 ID 必填，来源 FK `NO ACTION` |
| `po_purchase_order_item_piece_weight` | 不重复增加商品/仓库 ID | 经 `purchase_order_item_id` 取得 | 保持聚合子项模型；现有来源 FK 另行评审 `SET NULL` 是否符合审计要求 |

### 7.8 物流、发票、对账与资金逐表目标

| 表 | 新增/收紧字段 | 权威回填来源 | 最终约束与说明 |
| --- | --- | --- | --- |
| `lg_freight_bill` | 新增 `carrier_id`；可选 `vehicle_id`；收紧结算主体 FK | `carrier_code`；车辆牌照仅在历史唯一时匹配 | `carrier_id` 有效单必填。混合客户物流单头不增加单值客户/项目 ID |
| `lg_freight_bill_item` | 新增 `customer_id`、`project_id`、`material_id`、`warehouse_id`；收紧来源出库明细 ID | 由来源出库明细 → 销售订单链继承 | 每行保留自己的客户/项目；来源 FK 取代 `source_no` 占用判断 |
| `st_freight_statement` | 新增 `carrier_id`；收紧结算主体 FK | `carrier_code` 唯一匹配 | 有效对账单物流商必填 |
| `st_freight_statement_item` | 新增 `source_freight_bill_id`、`source_freight_bill_item_id` 及客户/项目/商品/仓库 ID | 直接物流单明细；销售出库来源 ID仅作更远端追踪 | 以 `(source_freight_bill_item_id, source_freight_bill_id)` 组合 FK保证同一父项，删除文本猜测；V35–V37 已随当前生产 V56 执行，剩余来源继承与余额守恒发布后核验 |
| `fm_invoice_issue` | 新增 `customer_id`、`project_id`；收紧结算主体 FK | 来源销售订单，或客户/项目稳定 ID | 客户/项目组合一致；发票来源头桥接表保持已有 FK |
| `fm_invoice_issue_item` | 新增 `material_id`、`warehouse_id`、生成批次键；收紧来源销售订单明细 ID | 直接来源明细 | 来源 FK `NO ACTION`，业务单号只作快照 |
| `fm_invoice_receipt` | 新增 `supplier_id`；收紧结算主体 FK | `supplier_code` 或来源采购订单 | 有效收票供应商必填；来源头桥接表保持已有 FK |
| `fm_invoice_receipt_item` | 新增 `material_id`、`warehouse_id`、生成批次键；收紧来源采购订单明细 ID | 直接来源明细 | 来源 FK `NO ACTION` |
| `st_customer_statement` | 新增 `customer_id`；收紧项目/结算主体 FK | 来源销售订单必须属于同一客户/项目 | 项目 FK 从 `SET NULL` 改 `NO ACTION` |
| `st_customer_statement_item` | 新增 `customer_id`、`material_id`、`warehouse_id`；收紧项目和来源明细 ID | 直接来源销售订单明细 | 来源与项目 FK，现有占用唯一规则改用来源 ID |
| `st_supplier_statement` | 新增 `supplier_id`；收紧结算主体 FK | 所有来源入库必须属于同一供应商 | 有效对账单供应商必填 |
| `st_supplier_statement_item` | 新增 `material_id`、`warehouse_id`；收紧来源入库明细 ID | 直接来源入库明细 | 来源 FK，现有占用唯一规则改用来源 ID |
| `fm_receipt` | 新增 `customer_id`；收紧项目、来源客户对账单和结算主体 FK | 来源客户对账单 | 项目 FK 从 `SET NULL` 改 `NO ACTION` |
| `fm_receipt_allocation` | 明确 `source_customer_statement_id`，或兼容期收紧现列语义 | 现有客户对账分配 | 非空 FK → `st_customer_statement`，禁止泛化为其他类型 |
| `fm_payment` | 新增 `counterparty_type`、`counterparty_id`；拆分对账来源 ID | 供应商/物流对账单；预付款来源采购订单 | 预付款两个 statement FK 均空；对账付款恰好一个 statement FK 非空 |
| `fm_payment_allocation` | 新增 `source_supplier_statement_id`、`source_freight_statement_id` | 由付款业务类型和现有来源 ID确定 | 两个真实 FK，`num_nonnulls(...) = 1`；停止使用无类型 generic ID |
| `fm_supplier_refund_receipt` | 新增 `supplier_id`；收紧结算主体 FK | 直接从采购退款单继承 | 来源退款 FK 已存在 |
| `fm_ledger_adjustment` | 新增 `counterparty_id`；收紧 `project_id` 和结算主体 FK | 类型 + 稳定编码全历史唯一匹配 | type/ID 成对检查；非客户往来方项目必须为空；客户项目归属由服务校验 |
| 所有含 `settlement_company_id` 的业务表与 `sys_print_template` | 收紧现有 ID | 已有非空 ID 只验证；空值优先从直接来源继承 | FK → `sys_company_setting`；禁止用当前主数据默认值静默回填历史 |

应收应付没有持久表，Switch 阶段直接改造 `ReceivablePayableQueryRepository`：每个 UNION 分支必须产生
`counterparty_type + counterparty_id + settlement_company_id + source_document_id`，按 ID 聚合并用来源 ID 判断已对账，
删除 `DISTINCT ON(name)`、`name:MD5(name)` 和按 `source_no` 关联的身份回退。

#### 7.8.1 应收应付稳定身份查询契约

应收应付聚合行不是业务实体，不生成新的雪花主键。其稳定下钻键固定为五段：

```text
direction:counterpartyType:reconciliationStatus:settlementCompanyId:counterpartyId
```

示例：

```text
应付:供应商:未对账:8740000000000000100:8740000000000000200
```

最后两段必须是现有结算主体和往来方的真实雪花 ID。禁止使用 `none`、业务编码、名称、`MD5(name)` 或查询排序序号补位；
兼容期出现任一必需 ID为空时应记录数据质量错误并阻断该行进入稳定聚合，而不是制造一个看似有效的组键。

`ledger_source` 的每个 UNION 分支必须以相同类型和顺序输出：

```text
direction, counterparty_type, counterparty_id, counterparty_code, counterparty_name,
settlement_company_id, settlement_company_name, reconciliation_status, entry_role,
source_type, source_document_id, source_line_id, source_no, accounting_date,
confirmed_amount, settled_amount
```

其中 `source_no` 永久只作展示与检索快照；身份、对账匹配和下钻使用以下真实 ID：

| 账簿事件 | 往来方 ID来源 | 对账/结算来源 ID |
| --- | --- | --- |
| 销售出库确认 | `so_sales_outbound.customer_id` | 客户对账明细的 `source_sales_order_item_id`，沿销售订单明细来源链核对 |
| 客户对账/收款结算 | `st_customer_statement.customer_id` / `fm_receipt.customer_id` | `fm_receipt_allocation.source_customer_statement_id` |
| 采购入库确认 | `po_purchase_inbound.supplier_id` | `st_supplier_statement_item.source_inbound_item_id` |
| 采购预付款 | `fm_payment.counterparty_type/counterparty_id` | 采购订单真实来源 ID；不得按订单号反查 |
| 供应商退款到账 | `fm_supplier_refund_receipt.supplier_id` | `source_purchase_refund_id`，并继承退款单来源采购订单 ID |
| 物流单确认 | `lg_freight_bill.carrier_id` | `st_freight_statement_item.source_freight_bill_id/source_freight_bill_item_id` |
| 供应商/物流付款结算 | `fm_payment.counterparty_type/counterparty_id` | `fm_payment_allocation.source_supplier_statement_id` 或 `source_freight_statement_id`，恰好一个非空 |
| 台账调整 | `fm_ledger_adjustment.counterparty_type/counterparty_id` | 调整单自身 `id`，不得按名称映射往来方 |

汇总、最新快照选择、账龄窗口、列表明细和导出统一按
`direction + counterparty_type + counterparty_id + settlement_company_id + reconciliation_status` 分区。
`DISTINCT ON` 仅允许在上述 ID维度内选择最新展示快照，禁止 `DISTINCT ON(name)`。仓储方法使用
`Long counterpartyId`、`Long settlementCompanyId` 作为下钻参数；汇总和详情响应至少显式返回 `counterpartyId`，前端仍按字符串接收。

### 7.9 系统、认证、附件与审计关系

| 表/关系 | 审计基线缺口 | 目标规则 |
| --- | --- | --- |
| `auth_api_key.user_id`、`auth_refresh_token.user_id` | 非空 Long 但无用户 FK | 增加全量索引和 FK → `sys_user`；硬删除策略由安全清理流程决定，日常仍使用软删除/撤销 |
| `sys_user_role.user_id/role_id` | 两列均无 FK | 分别 FK → 用户/角色，组合关系保持唯一；聚合成员关系可在批准的硬删除时 CASCADE |
| `sys_role_permission.role_id` | 无角色 FK | FK → `sys_role`；`resource_code/action_code` 保持受控协议键 |
| `sys_role.parent_id`、`sys_department.parent_id` | 自引用无 FK | 增加自引用 FK和防自引用/循环服务校验；删除有子节点时拒绝 |
| `sys_user.department_id` | 已 FK且 `SET NULL` | 作为当前组织归属而非历史凭证，可保留明确例外；操作日志继续保存发生时部门/人员快照 |
| `sys_attachment_binding.attachment_id` | 无附件 FK | FK → `sys_attachment`；`module_key + record_id` 按多态模块目录验证，不建立跨表普通 FK |
| `sys_operation_log.operator_id` | 无用户 FK，`module_key + record_id` 为多态 | 操作人 ID可加 `NO ACTION` FK；业务记录关系由模块目录验证并永久保留日志快照 |
| `sys_print_template.settlement_company_id` | 有列无 FK | 增加 FK → `sys_company_setting`，并进入结算主体删除保护 |
| `sys_menu/menu_action/role_permission` | 以 menu/resource/action code 关联 | 保持受控代码契约；代码不可随显示名称修改，名称仅展示 |

这些审计基线缺口已分别进入 V20–V46 主线和 V50–V52 系统关系补缺；普通 FK/CHECK 按 8.2 节拆分为增加、验证和收紧阶段。
系统模块同样通过 Long 字符串 JSON 契约；协议键、令牌 ID和观测 ID不使用实体 ID decoder。

当前实现已由 V50–V52 为已审计的系统普通关系补齐支撑索引、增加并验证恰好 8 条 FK；
`module_key + record_id` 等多态关系不建立错误的普通 FK。车辆/物流商组合归属由 V48–V49 增加并验证，
采购入库直接来源明细由 V53–V55 分阶段验证后收紧为 `NOT NULL`。这些结论已在 `leo_test` V55、临时空库
V1→V55 和当前生产 V56 验证；删除语义评审、逐单守恒和锁影响归档仍需独立完成。

## 8. 数据库迁移计划

### 8.1 迁移规则

- V1 至 V19 已进入受控历史，严禁修改。
- V20–V56 已完成跨领域契约串联并进入远端及生产执行记录；V20–V46 与 V47–V55 的独立 SHA-256 快照已建立，V56 已执行。上述版本全部冻结，后续修正只能从 V57 前滚。
- 共享环境执行前仍必须核对各环境 `flyway_schema_history`；无法证明某版本从未执行时，一律按已执行处理，禁止重排、重命名或改写 checksum。
- schema 扩展、数据回填、约束收紧和清理必须拆成不同迁移。
- 禁止绕过 Flyway 直接在开发或生产库执行持久化修复。
- 大表索引需要评估维护窗口；若使用 `CREATE INDEX CONCURRENTLY`，必须使用明确的非事务 Flyway 迁移配置。

### 8.2 版本序列与冻结状态

V20–V56 已形成完整版本线并随 Leo `v3.0.0` 进入远端和当前生产执行记录；为避免后续环境产生不同 checksum，
这些版本继续按冻结文件管理。`system-identity-v20-v46.sha256` 记录 V20–V46 的 27 项 SHA-256，
`system-identity-v47-v55.sha256` 独立记录 V47–V55 的 9 项 SHA-256；两份契约从 2026-07-12 快照起阻止未来漂移。
清单不能证明文件在快照建立前从未改写；以下内容记录实际文件、已执行边界与后续 V57+ 前滚规则。

| 范围 | 当前状态 | 执行许可 |
| --- | --- | --- |
| V20 | 文件存在且已有生产执行记录 | 不得改写；问题用 V57+ 前滚 |
| V21–V37 | Expand、Backfill、索引和物流对账补缺已执行 | 文件冻结；后续环境只能使用相同 checksum 顺序执行 |
| V38–V46 | FK、CHECK、Validate、NOT NULL 和前滚补缺已执行 | 文件冻结；发现数据或约束问题只能新增迁移修复 |
| V47 | 3 个既有 FK 的全量左前缀索引已执行 | 文件冻结；锁影响与运行耗时补充进入发布后报告 |
| V48–V55 | 车辆归属、8 条系统普通关系 FK和采购入库直接来源收紧已完成本地契约、空库和生产执行 | 文件冻结；不得因补文档或测试修改 checksum |
| V56 | `allow_delivery_verification_sales_order_status` 已提交并在开发测试库、候选库和当前生产执行 | 文件冻结；任何状态约束修正从 V57+ 前滚 |
| V57+ | 尚未创建 | 只用于 V56 之后的未来前滚修复或满足发布 C门禁后的兼容清理；按单一职责继续递增 |

**Expand：只扩结构，全部新普通列可空**

| 建议版本 | 单一职责 | 允许旧应用继续运行 |
| --- | --- | --- |
| V20 | `expand_customer_project_identity`：增加 `md_project.customer_id` 与 `so_sales_order.customer_id`，先闭合客户/项目 P0 | 是 |
| V21 | `expand_inventory_identity`：按 7.7/7.8 矩阵增加商品、适用仓库 ID及 STORED `batch_no_normalized` | 是 |
| V22 | `expand_purchase_party_identity`：采购合同、订单、入库、退款、收票、供应商对账、预付款/退款到账增加 `supplier_id` | 是 |
| V23 | `expand_remaining_sales_party_identity`：其余销售合同、出库、开票、客户对账、收款增加客户/项目 ID；物流客户 ID只加在明细 | 是 |
| V24 | `expand_logistics_identity`：物流单/物流对账增加 `carrier_id`、直接物流来源 ID；可选增加 `vehicle_id` | 是 |
| V25 | `expand_finance_typed_identity`：增加 typed party、付款两类对账来源、收款客户对账来源 | 是 |
| V26 | `add_identity_supporting_indexes`：只建立全量 FK 索引和已确认查询索引；并发索引使用独立非事务迁移 | 是 |

完成 V20–V26 后部署发布 A。新代码必须双写 ID + 快照并记录 fallback；旧节点未全部下线前不得开始非空收紧。

**Backfill：每个版本先失败闭合预检，再更新单一领域**

| 建议版本 | 单一职责 | 核对口径 |
| --- | --- | --- |
| V27 | `backfill_master_identity`：项目客户、可选商品类别 | 全历史唯一匹配，歧义立即中止 |
| V28 | `backfill_inventory_identity`：商品、仓库、生成批次键 | 逐单和全局数量、重量、金额守恒 |
| V29 | `backfill_purchase_party_identity`：采购、收票、供应商对账、预付款/退款 | 同一来源链供应商必须一致 |
| V30 | `backfill_sales_party_identity`：客户、项目及客户项目归属 | 错写/碰撞记录单独清单，禁止把存在的数字直接视为可信项目 ID |
| V31 | `backfill_logistics_identity`：物流商和混合客户明细 | 不生成虚假的单头客户/项目，逐明细继承 |
| V32 | `backfill_finance_typed_identity`：收付款、退款到账、台账 typed party | 按方向、往来方、结算主体、对账状态余额守恒 |
| V33 | `backfill_source_relationships`：只补来源头/明细 ID | 零匹配、多匹配、ID/单号冲突全部显式失败 |
| V34 | `backfill_settlement_identity`：验证/继承结算主体 ID | 禁止用当前主数据默认结算主体覆盖历史 |

**V35–V46 生产执行完成与发布后验收**

| 实际版本/发布 | 单一职责 | 进入条件/当前状态 |
| --- | --- | --- |
| V35 | `expand_freight_statement_item_party_identity`：前滚增加物流对账明细 `customer_id/project_id` 等已确认遗漏列 | 已生产执行并冻结；不修改 V20–V34 |
| V36 | `add_constraint_supporting_indexes`：补 V26 覆盖矩阵遗漏的全量 FK索引和组合索引 | 已生产执行；失效索引 0，冗余索引与锁影响报告仍需归档 |
| V37 | `backfill_freight_statement_item_party_identity`：从直接物流单明细继承客户/项目 ID | 已生产执行；未解析身份 0，剩余逐单/全局守恒核验 |
| 发布 B | 库存、应收应付、候选、容量和占用校验全部切换为 ID读取 | 代码与 V27–V37 已部署；真实状态流、权限和守恒继续观察 |
| V38 | `add_stable_identity_foreign_keys`：按领域增加主数据、来源、结算主体 FK | 已生产执行；主线 validate 成功，历史守恒仍需归档 |
| V39 | `add_stable_identity_checks`：增加 typed party、来源互斥和必需身份辅助 CHECK | 已生产执行；本次新增未验证约束 0 |
| V40 | `validate_stable_identity_foreign_keys`：验证 V38 FK | 已在生产执行并验证成功 |
| V41 | `validate_stable_identity_checks`：验证 V39 CHECK | 已在生产执行并验证成功 |
| V42 | `enforce_stable_identity_not_null`：收紧已验证的 party、商品、仓库、来源和结算字段 | 已生产执行；后续问题只能从 V57+ 前滚 |
| V43 | `add_missing_stable_identity_fk_indexes`：补充组合和直接来源 FK索引 | 已生产执行；V47 补齐剩余 3 个缺口，当前失效索引 0 |
| V44 | `add_missing_stable_identity_checks`：补直接来源及台账 typed party CHECK | 已生产执行；历史兼容命中继续观察 |
| V45 | `validate_missing_stable_identity_checks`：验证 V44 CHECK | 已在生产执行并验证成功 |
| V46 | `enforce_required_direct_source_not_null`：收紧客户/供应商对账及收/开票直接来源 | 已生产执行；采购入库直接来源由 V53–V55继续收紧 |
| 发布 C | 请求 ID 必填，停止旧字段身份回退 | 尚未完成：必须先证明 fallback 连续一个完整业务周期为 0 |
| 后续清理（最早 V57） | 只移除已确认无命中的名称/编码/单号身份回退和无用索引；永久保留快照 | V47–V56 补缺完成、发布 C 完成且具备回滚证据；版本号以届时最大版本 + 1 为准 |

**V47–V55 生产执行完成，发布后证据待收口**

| 建议版本 | 单一职责 | 前置检查与验收 |
| --- | --- | --- |
| V47 | 补 `po_purchase_refund(source_purchase_order_id)`、`fm_supplier_refund_receipt(purchase_refund_id)`、`sys_role_conflict(conflict_role_id)` 三个全量左前缀索引；不得重复创建 V36 已有的 `fm_payment(source_purchase_order_id)` 索引 | 已生产执行并冻结；当前失效索引 0，列序/谓词/重复索引与锁影响报告待归档 |
| V48 | 建立 `md_vehicle(id, carrier_id)` 唯一索引，并为 `lg_freight_bill(vehicle_id, carrier_id)` 建组合索引、`NOT VALID` 组合 FK及车辆 ID/车牌同空同非空 CHECK | 已生产执行；D4 修复 2 辆车辆，跨物流商与快照阻断通过 |
| V49 | 只验证 V48 车辆归属 FK/CHECK | 已在生产执行并验证成功 |
| V50 | 为认证、用户角色、角色权限、附件绑定、部门/角色树等普通关系补全量支撑索引 | 已生产执行；`module_key + record_id` 多态关系和审计 actor 仍明确排除普通实体 FK |
| V51 | 为 V50 对应普通关系增加恰好 8 条 `NOT VALID` FK | 已生产执行；新库没有新增未验证约束，删除动作逐表评审仍需完成 |
| V52 | 只验证 V51 系统关系 FK | 已在生产执行并验证成功 |
| V53 | 为 `po_purchase_inbound_item.source_purchase_order_item_id` 增加 `NOT VALID` 非空辅助 CHECK | 已生产执行；历史空来源、孤儿和来源头冲突阻断通过 |
| V54 | 只验证 V53 采购入库直接来源 CHECK | 已在生产执行并验证成功 |
| V55 | 将采购入库直接来源列设为 `NOT NULL` 并移除辅助 CHECK | 已生产执行；业务状态流与守恒继续发布后观察 |

数据库证据：`leo_test` 从 V47 顺序执行至 V55并成功 validate 55 项；临时 PostgreSQL 16.14 空库从 V1顺序执行至 V55；
两份生产快照隔离副本完成 D1/D2 与 D3/D4 分阶段演练；当前生产库完成主线 V56、repair D4 和双 validate。
真实锁等待/WAL/索引空间、逐单/全局业务守恒及回滚恢复仍需作为发布后证据归档。

实际拆分时若单个版本涉及过多表，应继续按领域拆成更小脚本，后续版本整体顺延，保持单一职责和可审查性。
任何版本替换删除动作时不得仅增加一条与旧 `SET NULL` 并存的 `NO ACTION` FK后就宣称完成；必须在受控停写/停删窗口验证新约束、
移除旧约束并复核最终 `pg_constraint` 定义。若 ADR-004 的库存层触发条件在实施前被正式确认，应另立设计并从届时最大版本
继续新增迁移，不插入或改写上述已执行版本。

### 8.3 回填优先级

每一行按以下优先级确定 ID：

1. 已有直接来源明细 ID：从来源业务实体继承主数据 ID。
2. 已有受保护的稳定编码：在包含软删除历史的主数据中唯一匹配，同时校验名称、规格和所属主体快照。
3. 已有业务单号：仅在全历史范围能定位唯一来源单据且其他快照一致时，才继承来源 ID；单号本身仍不是身份键。
4. 名称只能作为最后的人工辅助信息；仅当全历史范围唯一且其他快照一致时才允许自动匹配。
5. 任何多匹配或零匹配都写入预检报告并中止约束收紧，不允许 `MIN(id)`、`DISTINCT ON ... ORDER BY id` 猜测。

软删除主数据不能直接忽略：历史业务可能合法引用后来停用的主数据。回填应保留该历史 ID，但新建业务只能选择活动主数据。

### 8.4 必须人工清洗的数据

以下异常必须进入带表名、记录 ID、候选 ID、冲突原因和业务确认结果的清洗清单，禁止迁移脚本静默猜测：

- `BTRIM/LOWER` 规范化后重复的商品编码、仓库编码，以及空格/大小写变体。
- 软删除后编码被不同主数据雪花 ID 复用，导致一个历史编码匹配多个 ID。
- 商品编码不存在，或商品规格快照能匹配零个/多个商品。
- 仓库名称为空、不存在、重名，或头部仓库与明细仓库不一致。
- 来源头/明细 ID 为空、指向已不存在记录，或来源 ID 与业务单号快照指向不同记录。
- 入库、销售订单、销售出库与其来源行的商品、仓库、`batch_no_normalized` 不一致。
- 销售订单中的 `project_id` 实际保存了客户 ID，或项目不属于该客户。
- 客户、供应商、物流商、结算主体 ID 与编码/名称快照冲突；typed party 类型与目标主数据表不匹配。
- 同一商品 ID 出现多个基础数量/重量单位，或批次管理商品存在空批次号。
- 同一单据头部需要由明细推导仓库，但明细包含多个仓库；不得取第一条作为头部仓库。

清洗结果必须通过新的更高版本 Flyway 固化；不得直接在共享开发库或生产库执行持久化 SQL。

### 8.5 双写和切读顺序

**发布 A：Expand**

- 新增可空 ID 列、索引和响应字段。
- 新应用优先接收 ID，也兼容旧编码输入。
- 服务端用 ID 解析主数据并同时写 ID、编码和名称快照。
- 读取仍允许 ID 缺失时走旧逻辑，并记录结构化 fallback 指标。

**数据阶段：Backfill 与前滚补缺**

- 在生产副本执行只读预检。
- 执行 V27–V37 回填和物流对账前滚补缺；后续发现遗漏时只用当前最大版本 + 1 前滚，不改写旧迁移。
- 核对迁移前后金额、数量、重量及各往来方余额总量，并逐 ID维度核对归属不变。
- 人工处理歧义数据后重复演练，直到未解析计数为 0。

**发布 B：Switch**

- 报表、库存、候选查询和下游校验改为 ID 优先。
- API 请求将 ID 设为必填，编码/名称只作快照。
- 前端所有表单、父单导入、缓存键和路由统一使用字符串 ID。
- 继续保留旧回退，但对每次命中告警并计数。

**发布 C：Contract**

- 停止旧版本写入。
- 按 V38–V46 及后续 V47–V55 顺序增加、验证 FK/CHECK，再设置有效业务行 ID非空；不得把增加约束、全表验证和非空收紧压进同一迁移。
- 删除名称任取第一条等不安全回退。
- 是否允许修改业务编码另行评审；主键始终不可修改。

### 8.6 预检、回填与对账门禁

每张表的迁移证据必须输出以下固定指标，保存到发布记录，不只记录“脚本执行成功”：

| 指标 | 进入下一阶段的要求 |
| --- | --- |
| 总行数、有效业务行数、待回填数 | 与预检基线一致，变化必须能由双写期间新增数据解释 |
| 已解析数 | 等于待回填数减去业务确认可合法为空的记录数 |
| 零候选、多候选、快照冲突 | 在约束阶段前全部为 0 |
| 已有 ID 指向不存在目标的孤儿数 | 必须为 0 |
| ID 与编码/名称/所属客户不一致数 | 必须为 0；不能只以 FK 存在作为正确 |
| 主键变化数 | 必须严格为 0 |

回填和核对规则：

1. 所有编码匹配扫描包含软删除历史行；活动部分唯一索引不能证明全历史唯一。
2. 已有非空 ID 先验证，不因当前名称、编码或默认结算主体不同而自动改写。
3. 来源链回填优先级固定为“直接来源明细 ID → 来源头 ID → 稳定编码 + 完整快照唯一匹配”；名称只生成候选清单。
4. 按单据和全局两层核对采购/销售的数量、重量、金额；库存核对 `有效入库 - 有效出库`；财务核对
   `已确认金额、已结算金额、退款冲回、期末余额`。只比全表总额不能发现错归属，不予通过。
5. 客户项目、商品仓库、来源行、结算主体和 typed party 执行交叉一致性 anti-join，结果必须为 0。
6. 在匿名化生产副本记录每个版本的执行时间、锁等待、WAL 增量和索引空间。超出维护窗口时按表拆分更高版本迁移，
   不把同一个已执行版本改成批处理，也不绕过 Flyway 手工更新。
7. V38 前先执行 orphan/null/cross-scope anti-join；V40/V41 只验证或替换已审定约束。`flyway:migrate`、`flyway:validate`、空库 V1→最新和生产副本演练
   均通过后，才允许进入发布 B。

应收应付另设静态门禁：`ReceivablePayableQueryRepository` 中基于名称的 `DISTINCT ON`、`name:MD5` 和按
`source_no` 分组/匹配的身份路径必须全部移除，并由包含 `counterpartyId` 的契约测试证明新聚合键生效。

### 8.7 单次迁移演练运行手册

每次从一个阶段进入下一阶段都按固定顺序执行并归档，不允许只保留终端截图：

1. 记录应用版本、Git 提交、Flyway history、数据库只读副本时间点、表行数和数据校验摘要。
2. 执行只读预检 SQL，导出 `zero_candidate`、`multi_candidate`、`orphan`、`snapshot_mismatch`、`cross_scope` 清单；
   文件只保留内部 ID和脱敏业务提示，不导出账户、凭证或密钥。
3. 在同等规模副本运行 `flyway:migrate`，记录每个版本开始/结束时间、锁等待、WAL、临时文件和索引增长。
4. 运行逐单、逐往来方、逐结算主体、逐商品/仓库/批次四层守恒 SQL；任何一层差异非 0即失败。
5. 运行 `flyway:validate`、应用定向测试和关键查询 `EXPLAIN (ANALYZE, BUFFERS)`，保存 SQL、参数分布和执行计划。
6. 从备份恢复第二个副本并重复一次，证明流程可重复；正式窗口只执行已演练且 checksum 一致的脚本。
7. 正式环境执行后再次导出相同指标。若应用冒烟或观测指标异常，按第 13.3 节阶段边界回滚应用或前滚数据库，
   不手工改 `flyway_schema_history`，也不直接修数据。

每个迁移批次的证据包至少包含：`before.json`、`after.json`、异常 CSV、Flyway 输出、约束清单、查询计划、应用版本、
批准人和回滚/前滚决策。证据包保存位置由发布流程配置，不提交含生产数据的文件到 Git。

## 9. 后端改造计划

### 9.1 API 和 DTO

- 所有主数据 option 统一返回 `id`、`code`、`name/label`，ID 是字符串形式的雪花 ID。
- 创建/更新请求优先提交 `*_id`；快照字段由后端权威解析，不信任客户端名称。
- 响应同时返回 ID 和快照，保证历史展示不依赖当前主数据。
- 父单候选和导入响应必须携带来源头 ID、来源明细 ID 和全部主数据 ID。
- 禁止新增只接收名称的业务接口；批量导入必须先做编码到 ID 的唯一解析并返回逐行错误。
- 增加序列化契约测试，证明顶层 ID、嵌套明细 ID 和所有关联 Long 均输出十进制 JSON 字符串，防止全局 Jackson 配置被误删。
- 对账、预付款分配和父单候选改为服务端按 party ID、结算主体 ID、当前记录 ID 分页过滤；禁止前端全量加载后按名称筛选。

实体 option 统一契约示例：

```json
{
  "id": "202607110000000001",
  "code": "CUS-001",
  "name": "客户甲",
  "label": "CUS-001 / 客户甲"
}
```

项目 option 额外返回 `customerId`；仓库、商品、供应商和物流商分别使用自己的 code/name 字段，但 `id` 始终是目标主表 ID。
业务保存请求使用语义字段，例如 `customerId`、`projectId`、`materialId`，不能把 option 的通用 `id` 原样塞入含义不同的字段。

父单候选响应至少包含：候选头 `id`、来源头 ID、每条来源明细 `id/sourceItemId`、所有主数据 ID、业务单号快照、
剩余可用数量/重量/金额及服务端分页 `total`。ID 均为 JSON 字符串，`batchNoNormalized` 是只读派生值。

### 9.2 服务层

- 按领域建立小型解析器，例如 `MaterialIdentityResolver`、`WarehouseIdentityResolver`、`CustomerIdentityResolver`；
  不建立一个包含所有主数据类型的巨型通用解析器。
- 解析器按 ID 查询活动主数据，校验请求中可选编码是否与 ID 一致，再生成不可变快照值对象。
- 从父单生成子单时直接继承 ID 和快照，不重新按名称查询。
- 更新业务单据时锁定旧新来源 ID 并校验下游事实；已审核或已结算记录不能换身份。
- 扩展 `MasterDataReferenceGuard` 或按领域建立引用目录，覆盖 ID 引用、旧编码引用和软删除边界。
- 首先修复 `SalesOrderApplyService` 的客户 ID/项目 ID 错写，分别解析两个实体，并验证项目归属于客户。
- 采购订单等入口不得继续只验证商品编码存在后直接信任客户端品牌、规格、单位和仓库名称；必须按 ID 读取权威主数据，
  服务端生成 ID 与发生时快照。下游单据优先继承来源行的 ID 和快照。

### 9.3 仓储和报表

- 库存报表改按 `material_id`、`warehouse_id`、`batch_no_normalized` 聚合；单位冲突作为异常而非附加分组键。
- 出入库明细报表返回真实业务明细 ID、来源明细 ID、`materialId` 和 `warehouseId`，不再把 `ROW_NUMBER` 当稳定 ID；
  聚合结果另返回明确命名的维度键，避免与持久化实体 ID 混淆。
- 应收应付改按主数据 ID 和结算主体 ID 聚合；稳定编码保留为展示和兼容字段。
- 合同、发票、对账和付款候选以来源 ID 做排除、容量和分页，避免业务单号字符串关联。
- 查询结果的最新主数据名称与历史快照必须明确区分；财务凭证默认展示历史快照，主数据管理页面展示当前名称。
- 为 ID 过滤、状态和日期组合建立经过 `EXPLAIN (ANALYZE, BUFFERS)` 验证的索引，禁止仅凭字段列表堆索引。

### 9.4 后端实施落点

| 工作包 | 主要代码落点 | 完成条件 |
| --- | --- | --- |
| 雪花根基 | `SnowflakeIdGenerator`、`BusinessCreateIdResolver`、`AbstractCrudService`、`ManagedEntityItemSupport`、`JacksonConfig`、生产配置 | 机器号唯一、预分配失败闭合、更新 ID 不变、所有嵌套 Long 输出字符串 |
| 客户/项目 | `Project*` 实体/DTO/仓储/服务、`SalesOrder*`、`SalesOrderApplyService`、客户/项目解析器 | customer/project 分别解析，项目属于客户，错写历史有清洗报告 |
| 商品快照 | `TradeMaterialSnapshot`、`MaterialCatalog`、`MaterialRepository.listActiveMaterials`、`TradeItemMaterialSupport` | 按 `materialId` 加载权威快照，同编码不同 ID 不合并 |
| 仓库快照 | 新 `WarehouseSnapshot`、`WarehouseCatalog`、`WarehouseRepository`、`WarehouseSelectionSupport` | option 和校验均使用仓库 ID，不再只传名称 |
| 采购来源链 | `PurchaseOrderApplyService`、`InboundItemMapper`、`PurchaseInboundSourceValidator`、`PurchaseItemQueryAppService*` | 订单 → 入库/退款的 ID与快照继承完整 |
| 销售来源链 | `SalesOrderItemMapper`、`SalesOrderSourceAllocationService`、`SalesOutboundApplyService`、`SalesOutboundSourceService` | 入库/采购 → 销售订单 → 出库按来源 ID锁定容量 |
| 物流来源链 | `FreightBillService`、`FreightStatementSourceService`、物流对账 Command/View/DTO/Repository | 物流对账只按物流单明细 ID定位，删除文本猜测 |
| 财务身份 | `PaymentApplyService`、两类 Allocation/Validator、`ReceiptApplyService`、`LedgerAdjustmentService`、三类 StatementSourceService | typed party、显式 statement FK和结算主体贯穿 |
| 候选分页 | 采购订单、销售订单、采购退款候选仓储及 `StatementCandidateSupport` | 剩余容量和 `NOT EXISTS` 下推 PostgreSQL，total 是真实可导入总数 |
| 报表 | `IoReportQueryRepository`、`InventoryReportQueryRepository`、`ReceivablePayableQueryRepository` 及响应 DTO | 明细返回真实 ID，聚合按稳定 ID，名称/单号身份回退清零 |
| 删除保护 | 各主数据 Service、`MaterialReferenceGuard`、`MasterDataReferenceGuard`、`CompanySettingService` | FK 防硬删除、guard 防软删除，新增 ID列全部进入覆盖矩阵 |

`CompanySettingService` 的引用目录必须覆盖既有头表、业务明细、V12–V18 新表和 `sys_print_template`，至少包括
采购退款、收票、付款、退款到账、台账调整、采购/销售/物流明细中的结算主体 ID；不得只补当前已报错的单张表。

### 9.5 写入校验顺序与错误边界

每个创建/更新接口按以下顺序执行，并处于同一业务事务：

1. 解析十进制字符串 ID，拒绝空值、非正数和越界值。
2. 按 ID 加载目标主数据；新业务只允许活动主数据，历史读取允许已停用/软删除目标。
3. 校验实体类型和组合关系，例如项目属于客户、车辆属于物流商、结算主体是可用结算公司。
4. 有来源时锁定来源行，校验来源状态、剩余容量、未被占用以及 ID链一致性。
5. 无来源时按主数据 ID生成当前权威快照；有来源时继承来源 ID和发生时快照，不重新 JOIN 当前名称覆盖历史。
6. 对更新比较旧/新 ID。草稿仅在无下游事实时可换身份；已审核、已出入库、已开票、已对账或已结算记录拒绝换身份。
7. 原子保存关系 ID、快照、来源 ID和聚合版本；任何一步失败整单回滚。

错误必须区分“ID格式错误”“目标不存在/已停用”“ID与快照冲突”“跨客户项目”“来源已占用/容量不足”和“已形成下游事实不可改”。
兼容期 code/name fallback 每次命中均记录模块、字段、记录 ID和原因，但日志不得包含敏感凭证或完整请求体。

### 9.6 错误契约、锁与幂等边界

后端应使用现有统一异常响应承载稳定的领域错误码；文案可本地化，前端分支只判断错误码，不解析中文字符串：

| 错误码 | 触发条件 | 建议 HTTP 语义 | 是否可直接重试 |
| --- | --- | --- | --- |
| `IDENTITY_ID_INVALID` | 空、非十进制、非正数、越界 ID | 400 | 否，修正请求 |
| `IDENTITY_TARGET_NOT_FOUND` | ID不属于目标表或记录不存在 | 404 | 否 |
| `IDENTITY_TARGET_INACTIVE` | 新业务选择停用/软删除主数据 | 409 | 否，重新选择 |
| `IDENTITY_SNAPSHOT_MISMATCH` | ID与请求编码/名称快照冲突 | 409 | 否，刷新主数据 |
| `IDENTITY_SCOPE_MISMATCH` | 项目不属于客户、车辆不属于物流商等 | 409 | 否 |
| `SOURCE_IDENTITY_CONFLICT` | 来源头/明细、主体或结算公司链不一致 | 409 | 否 |
| `SOURCE_CAPACITY_EXCEEDED` | 来源已占用或剩余数量/重量/金额不足 | 409 | 刷新候选后人工重试 |
| `IDENTITY_IMMUTABLE` | 已形成下游事实后尝试换 ID | 409 | 否 |

同一事务涉及多条来源时按“表类型固定顺序 + ID升序”加锁，避免跨请求反向加锁；容量校验在锁后重新计算，不能只信任候选接口
返回的剩余值。数据库唯一/互斥约束是并发竞争的最终兜底，命中后映射为明确领域冲突。请求重试只能复用现有幂等键或业务创建
预分配 ID，不得因超时重新生成一个根实体 ID后造成重复单据。

## 10. 前端改造计划

### 10.1 类型边界

建立统一类型：

```ts
export type EntityId = string

export interface MasterOption {
  id: EntityId
  code: string
  name: string
  label: string
}
```

- 生产 API DTO 的实体 ID 和全部 `*Id` 引用字段直接声明为 `EntityId`，不在领域类型中保留 `number`。
- 兼容边界从 `unknown` 校验：十进制字符串正常接收；仅在过渡期接收安全正整数并告警；不安全 number 直接报错。
- 通用 normalizer 必须覆盖嵌套对象中的 `supplierId/customerId/carrierId/projectId/settlementCompanyId/source*Id`，
  不能只规范顶层记录 ID 和明细自身 ID。
- 表单值、路由参数、表格 row key、React Query key、选中项和本地草稿全部使用 `EntityId`。
- 禁止对 ID 使用数值比较；排序按后端结果或十进制字符串长度与字典序处理。
- 收紧现有 `RawApiRecord`、`ModuleRecordInput` 和财务分配 DTO 的 `string | number`；`asId` 与保存序列化在兼容期至少
  使用 `Number.isSafeInteger`，Contract 阶段删除 number 分支。
- 新增 `parseEntityId/parseOptionalEntityId`：只接受无空白的十进制正整数字符串；过渡期安全正整数 number 需告警后转换。
  unsafe number、0、负数、小数、科学计数法和空白一律抛出 API 契约错误，不返回空串或静默删除字段。
- ID normalizer 使用 DTO/schema 声明的 identity field registry 递归处理头、明细和 ID数组；不按 `endsWith('Id')` 盲猜，
  明确排除 `traceId`、`captchaId`、`requestId`、`tokenId` 和 DOM id 等非实体标识。

### 10.2 表单和父单导入

- 审计基线中的部分适配器曾把供应商 value 改成编码、把物流商 value 改成名称，并按客户名称去重而丢弃 ID；
  当前工作树已修正 option 契约，分别返回并保存语义明确的 `customerId`、`projectId`、`supplierId`、`carrierId`，
  不用一个含义不明的 `id` 在不同实体字段之间复用。
- Select 的真实 value 使用 ID，label 展示“编码 / 名称”。
- option 不得按名称去重；同名不同 ID 必须作为两条可区分记录显示，label 应包含编码或其他稳定业务提示。
- 选择主数据后可以回显快照，但保存时以后端按 ID 解析结果为准。
- 父单导入必须保留头 ID、明细 ID、商品 ID、仓库 ID、往来方 ID 和 `batchNoNormalized`，不能只复制页面可见文本。
- 切换父单、清空字段或编辑历史记录时，ID 与快照必须成组更新，禁止留下旧 ID + 新名称。
- 历史缺 ID 数据允许只读回显兼容提示；进入编辑前应由后端修复或明确阻止，不能让前端猜测匹配。

### 10.3 列表、筛选和导出

- 精确筛选提交 ID；关键词仍搜索编码和名称。
- 列表和详情可以同时展示业务编码、名称和必要的内部 ID，但内部 ID 默认不作为主要业务文案。
- 导出和打印保留发生时快照；审计导出可以附带内部 ID 便于追溯。
- 缓存键必须包含字符串 ID，主数据改名后只刷新展示缓存，不改变业务实体缓存归属。
- 服务端分页候选的 React Query key 必须包含 party ID、结算主体 ID、当前记录 ID、page 和 size，避免不同主体共享错误缓存。
- 历史记录补选项同时支持来源 ID → 来源单号显示，例如对账单、采购订单和退款来源；仅用于显示，不按单号反推 ID。

### 10.4 前端逐文件实施清单

| 工作包 | 主要文件 | 具体要求 |
| --- | --- | --- |
| ID 类型与解析 | 新增 `src/types/entity-id.ts`；收紧 `api-raw.ts`、`module-page.ts`、共享 Zod schema、财务分配 DTO | 真实 API normalizer 必须调用解析器，不能只在测试 schema 中声明 |
| 边界保存 | `type-narrowing.ts`、`module-save-payload.ts` | 必填无效 ID失败关闭；声明 `scalarIds/lineItemIds/idArrays`，不把错误 ID转成 `undefined` 继续提交 |
| 主数据 option | `customer-options.ts`、新项目 options、`supplier-options.ts`、`carrier-options.ts`、`warehouse-options.ts`、`materials.ts` | 实体 value 一律为 ID；code/name 只作 label和快照；同名/同码不同 ID不去重 |
| 编辑器行为 | `module-behavior-editor.ts`、`module-editor-item-column-handlers.ts`、`use-module-editor-item-columns.tsx` | 选择/清空时原子更新 ID + 快照；异步回查以 ID和请求序列防竞态 |
| 保存白名单 | `src/config/module-page-schema.ts`、各模块 page config、`module-save-payload.ts` | 补齐所有头/明细/来源 ID；消除两套 `saveFields` 漂移或增加一致性契约测试 |
| 历史回显 | `FormFieldRenderer.tsx` 与字段配置 | 使用显式 `entityKind + snapshotLabelKey`；活动 option 不含原 ID时注入 disabled 历史项，绝不按名称重绑 |
| 父单导入 | `module-page.ts`、`module-adapter-parent-import.ts`、`useModuleParentSelectorOverlay.tsx` | 增加 parent ID字段；`_parentRelationId` 用于身份，单号只显示；去重/替换/占用均按 ID |
| 候选缓存 | `query-keys.ts`、候选 API、三类对账与预付款 hooks | key 包含 queryType、usage、party type/ID、projectId、结算主体、当前记录、keyword、page、size；全部支持 AbortSignal |
| 草稿 | `module-editor-draft-storage.ts` | 升级 `DRAFT_VERSION`；读取走同一 ID decoder；含 unsafe number 或旧名称身份的草稿废弃并提示 |
| 筛选与导出 | 模块筛选配置、导出参数适配器 | 精确筛选传 ID；keyword 才传 code/name；业务导出默认快照，审计导出可附内部 ID |

保存白名单以 `src/config/module-page-schema.ts` 为当前真实优先来源，不能只修改页面配置。首轮至少覆盖：

- 采购：`supplierId`、`sourcePurchaseOrderId`、`materialId`、`warehouseId` 和明细来源 ID。
- 销售：`customerId`、`projectId`、`sourceSalesOrderId`、`materialId`、`warehouseId` 和两类采购来源 ID。
- 物流：头部 `carrierId`；每条明细的客户、项目、商品、仓库、来源销售出库明细 ID。
- 财务/合同/发票/对账：往来方、项目、结算主体、商品/仓库和全部来源 ID。

多客户物流导入不得把第一张销售出库单的客户/项目写到物流单头作为身份；每条明细保留自己的 `customerId/projectId`，
物流单头只固定 `carrierId` 和适用的结算主体。服务端返回的 `total` 必须是可导入总数，前端不得分页后再次过滤导致空页。

## 11. 测试计划

### 11.1 通用身份契约

每类主数据至少覆盖：

1. 同名不同 ID 不合并。
2. 同一 ID 修改允许的名称后仍归入原组。
3. 请求 ID 与编码不一致时失败。
4. 已审核业务不能更换主数据 ID。
5. 被引用主数据不能删除。
6. 雪花 ID 经过后端 JSON → 前端 → 请求 JSON 往返后逐字符不变。
7. 后端顶层与嵌套 Long 关联 ID 的 JSON 契约始终为字符串；前端遇到不安全 number 立即失败，不发送可能已失真的 ID。

### 11.2 进销存回归

- 商品改名后，历史入库和出库仍按同一 `material_id` 抵扣。
- 仓库改名后，库存仍按同一 `warehouse_id` 汇总。
- 同编码或同名称但不同 ID 的商品、仓库、批次绝不合并。
- 销售订单和销售出库沿来源链继承相同 `material_id`、`warehouse_id` 和 `batch_no_normalized`。
- 迁移前后按来源单据汇总的入库数量、出库数量、在手、预占、可用和重量完全一致。
- 修改身份/计量属性时，已有库存的商品必须拒绝更新并提示新建 SKU。
- 删除仍被引用的采购/销售来源明细时由数据库 FK 拒绝；来源互斥约束拒绝一行同时绑定两个销售来源。
- 出入库报表排序、筛选变化不改变真实明细 ID；聚合维度键不会被误当成可编辑实体 ID。

### 11.3 财务与业务回归

- 客户、供应商、物流商和项目改名不改变历史应收应付、对账、发票和付款归属。
- 相同往来方在不同结算主体下始终分离。
- 所有容量校验按来源明细 ID 计算，不受业务单号或名称修改影响。
- 名称 fallback 计数在 Contract 发布前必须为 0。
- 新建销售订单分别保存真实 `customer_id` 与 `project_id`；客户 ID 绝不会进入项目字段，且跨客户项目选择必须失败。
- typed party 的类型、ID 和快照不一致时失败；结算主体被任何新旧业务表引用时均不能删除。

### 11.4 迁移测试

- 空库从 V1 顺序迁移到最新版本并通过 `flyway:validate`。
- 使用匿名、同名多编码、软删除主数据、缺来源 ID、重复批次号等脏数据验证迁移失败闭合。
- 在生产数据副本记录回填耗时、锁等待、索引空间和约束验证时间。
- 对每条迁移保留文本契约测试和 PostgreSQL 集成测试，已执行迁移不得修改 checksum。

### 11.5 后端测试落点

| 测试域 | 重点测试类/新增契约 | 必须覆盖的场景 |
| --- | --- | --- |
| 雪花根基 | `SnowflakeIdGeneratorTest`、配置 YAML 测试、`BusinessCreateIdResolverTest`、`AbstractCrudServiceTest`、`ManagedEntityItemSupportTest`、`JacksonConfigTest` | 机器号边界/重复、时钟回拨、预分配服务缺失、更新 ID不变、嵌套 Long 字符串化 |
| 客户项目 | `SalesOrderApplyServiceTest`、`SalesOrderCustomerSnapshotTest`、PostgreSQL 测试、`ProjectServiceTest` | 客户 ID与项目 ID偶然同值、跨客户项目、同名不同 ID、历史错绑拒绝 |
| 商品仓库 | 采购/入库/销售/出库 Apply 与来源服务测试 | ID/快照不一致、父单继承、无来源权威解析、改名后归属不变、批次 trim 且保留大小写 |
| 来源完整性 | 核心来源服务与 FK PostgreSQL 测试 | 孤儿拒绝、合法空来源、两来源互斥、拆分容量、物流对账直接来源 ID |
| 财务身份 | 收付款、分配、三类对账、退款到账、台账测试 | typed party 错表、付款两类来源互斥、结算主体隔离、历史默认值不改写 |
| 报表 | 库存、出入库、应收应付仓储 PostgreSQL 测试 | 真实明细 ID、稳定 dimension key、无 ROW_NUMBER 身份、无 name/MD5/source_no 回退 |
| 删除保护 | `MasterDataReferenceGuardTest` 和新增引用覆盖矩阵测试 | 每个新增 `*_id` 都有 FK或多态 guard；硬删除与软删除均失败闭合 |

### 11.6 前端测试落点

- `type-narrowing.spec.ts`/新 EntityId 测试：19 位以上雪花字符串逐字符保留，unsafe number、科学计数法和空白拒绝。
- `normalizers.spec.ts`、模块记录 schema 测试：嵌套头/明细/ID数组全部规范化，非实体 `traceId` 等不误处理。
- `module-save-payload.spec.ts`：保存白名单包含所有 ID；必填无效 ID抛错；两套字段配置保持一致。
- 客户、项目、供应商、物流商、仓库、商品 option 测试：value=id，同名/同码不同 ID不合并，项目按 customerId 隔离。
- `FormFieldRenderer`、编辑器行为和商品/仓库列测试：历史停用项回显，ID+快照成组选择/清空，异步响应不覆盖新选择。
- 父单适配器与选择器测试：按 ID去重，query key 包含 usage/主体/页码，第二页可导入，AbortSignal 透传，跨页选择稳定。
- 对账/预付款候选测试：禁止全量加载后按名称或编码过滤；服务端 total 与页面行数契约一致。
- 草稿测试：版本升级、旧名称身份草稿和 unsafe ID草稿被明确废弃。

### 11.7 E2E、性能与 CI 门禁

- 大于 `Number.MAX_SAFE_INTEGER` 的 ID经过列表 → 路由 → 详情 → 编辑 → 保存后逐字符一致，网络请求中全部为 JSON 字符串。
- 同名不同 ID分别可选；主数据改名/停用后历史单仍绑定原 ID并显示原快照。
- 任意客户的销售出库单可合并为物流单，每条明细保持自己的客户/项目 ID；重复来源明细由服务端拒绝。
- 在第二页选择父单、切换客户项目、并发修改来源容量和重复提交均有端到端覆盖。
- 对关键候选和库存/财务 SQL 执行 `EXPLAIN (ANALYZE, BUFFERS)`，记录计划、耗时和 buffer；不得出现全表名称映射替代 ID索引。
- CI 运行后端全量测试、PostgreSQL 集成测试、前端 TypeScript、Biome/ESLint、全量 Vitest、关键 Playwright 和生产构建。
- 增加静态扫描：生产代码禁止对声明为身份字段的值使用 `Number`、`parseInt`、一元 `+` 或算术运算；兼容 number 仅允许集中在 decoder allowlist。

### 11.8 “修改后不偏移”判定矩阵

每个领域至少用一组大于 `Number.MAX_SAFE_INTEGER` 的雪花 ID执行以下变形测试；断言必须落在 ID归属和金额/数量结果，
不能只断言页面文案：

| 变形操作 | 必须保持不变 | 必须发生变化/拒绝 |
| --- | --- | --- |
| 同一主数据 ID修改允许的名称、地址或联系人 | 历史单据关系、库存数量、往来余额、来源链 | 新业务 option 展示新名称；历史快照仍显示旧值 |
| 创建同名但不同 ID的客户/供应商/商品/仓库 | 各自历史归属 | 列表、库存、应收应付必须分成不同 ID组 |
| 客户 ID与项目 ID在不同表中故意使用相同数值 | 各字段目标类型 | 跨类型赋值必须被实体解析/组合校验拒绝 |
| 修改业务单号或快照编码 | 已保存的 `source_*_id`、容量和占用结果 | 仅展示/检索值变化；不得产生新关联 |
| 停用或软删除被历史引用主数据 | 历史查询、打印、审计下钻 | 新建选择拒绝；硬删除由 FK拒绝 |
| 同一往来方切换结算主体 | 原账簿组和历史事实 | 新事实进入新的 `settlementCompanyId` 组，不与原组混合 |
| 前端收到 19 位 ID字符串 | 逐字符往返一致 | 收到同值 JSON number 时在边界失败，不允许 `String(number)`补救 |
| 反审核后尝试更换来源/主体 | 已形成的下游来源与结算事实 | 有下游事实时拒绝；无下游草稿按明确规则允许 |

迁移测试还必须比较迁移前后每张业务表的根 `id` 和既有明细 `id` 集合，新增关系 ID回填不允许导致任何旧主键新增、删除或变化；
主键差异计数必须严格为 0，不能用总行数相等替代逐值比较。

### 11.9 版本 3.2 本地、隔离副本与生产发布证据

以下结果均来自 2026-07-12 冻结后的当前工作树，不复用此前 6020 项历史结果：

- 后端编译：主源码 840 个、测试源码 703 个编译成功。
- 后端定向：物流稳定来源 91/91、供应商候选 126/126、客户/项目/物流商候选 128/128、销售候选 117/117；最终 PostgreSQL 夹具联合 18 类 62/62。
- 后端全量：在全新 V55 空库执行 `mvn -B -ntp test` 为 6184/6184，0 failure、0 error、0 skip；`mvn -B -ntp -DskipTests verify` 的 Checkstyle 0 violation、JaCoCo coverage check 达标并 BUILD SUCCESS。新增的 release D3/D4 契约测试另为 8/8；6184 项全量早于这 8 项，分别记账。带生产数据副本的 13 个 cleanup error 已单独归因于测试夹具与 V51 外键冲突，不计入迁移失败。
- 迁移契约：Final Contract 5/5、Forward Contract 9/9、Constraint Contract 8/8、两份 checksum 契约 2/2。
- 数据库：本地 `leo_test` 当前为 V55并成功 validate 55 项；临时 PostgreSQL 16.14 空库 V1→V55 migrate/validate 55 项成功；PostgreSQL 18.4 空 V55 库和历史黄金副本证据见第 1.3 节；D3/D4 候选库和开发测试库证据见第 1.6 节；实时生产切换库主线 V56、repair D4、0 failed，旧 V11 库保留，见第 1.7–1.8 节。
- 前端定向：三类 Statement 6 个文件 109/109；批量对账、父单导入、候选 current record和稳定 ID筛选均纳入全量回归。
- 前端全量：修复 `use-business-grid-page.spec.ts` 的异步附件计数测试隔离后，461 个 Vitest 文件通过、1 个文件跳过；6181 项通过、4 项跳过；`pnpm typecheck`、Biome/ESLint、`pnpm build-only` 与 `pnpm audit --prod` 通过。仍有 4 条既有 jsdom navigation 提示，但无 unhandled error、退出码为 0。
- Ant Design：全仓 lint 扫描 1008 个文件，0 deprecated、0 a11y、0 usage；保留 1 条非阻断性能提示。
- 静态检查：`leo`、`aries` 的 `git diff --check` 和两个部署触发脚本 `bash -n` 通过；最新 E2E canonical locator 与认证会话 helper 的目标 Biome、ESLint、TypeScript 检查通过。Aries `4e93949` 的 Typecheck、Build、Lint、4 个测试分片、React Doctor 和 release workflow 均成功，但修复后的完整 Playwright 结果尚无独立归档。

未通过或未执行的发布后门禁：生产数据库迁移、应用部署、健康/版本和 runner 状态已经通过；历史真实 Playwright E2E 依次为 8 passed、76 failed、0 skipped 和 25 passed、57 failed、2 skipped，后续定向修复不能替代全量门禁。锁/WAL/空间与耗时报告、业务余额/库存守恒、生产 fallback 周期、
Dashboard/告警、从保留 V11 基线恢复的回滚演练和完整 E2E 仍未完成。SpotBugs 仍有 51 个既有高等级告警，不能写成已通过。

## 12. 验收标准

全部满足后，才能对外宣称“全系统稳定身份固定完成”：

当前第 9 项本地/远端单元、静态与构建质量门禁已满足；第 10 项已完成实时生产 dump、新生产库迁移、完整性扫描、应用部署与冒烟，但尚缺锁/WAL/空间、业务余额/库存守恒和恢复演练；最新可复核完整 E2E 仍为 25 passed、57 failed、2 skipped，仍需在隔离环境重新执行并归档；
第 1–8、10–12 项的其余条件仍需业务批准、生产观察、fallback 或回滚证据，
因此总体状态保持 `In Progress`。

1. 所有有效业务行的必需主数据 ID 均非空，未解析计数为 0。
2. 所有核心跨单关联都具备来源头/明细 ID，不再仅靠名称或业务单号。
3. 库存报表不再用商品编码、规格或仓库名称作为归属键。
4. 应收应付不再用名称或稳定编码作为最终分组键，改为主数据 ID + 结算主体 ID。
5. 修改允许的主数据展示属性后，迁移前后库存和财务总量保持一致。
6. 同名不同 ID 在采购、销售、库存、物流和财务中始终分离。
7. 生产 fallback 指标连续一个完整业务周期为 0 后，才能移除兼容回退。
8. 前端 API DTO、表单、路由、缓存键和保存载荷中的身份字段均为 `EntityId`，不存在把雪花 ID 转为 JavaScript number 的生产路径。
9. 后端全量测试、前端全量测试、TypeScript、静态检查、生产构建和依赖审计全部通过。
10. 生产副本迁移演练、余额对账、库存守恒校验和应用冒烟全部通过。
11. 可建立普通 FK 的主数据与来源 ID 均已验证约束，数据库不存在孤儿来源；多态往来方通过类型/ID 成对约束和服务校验覆盖。
12. 客户/项目错写缺陷已修复，历史异常已清洗，客户与项目归属通过真实雪花 ID 校验。

## 13. 发布与回滚边界

### 13.1 发布前

- 冻结主数据编码和身份属性修改窗口。
- 导出所有未解析、同名、多编码、软删除引用和业务单号冲突清单，由业务逐条确认。
- 备份数据库并在生产副本完整演练。
- 确认当前线上应用与 Flyway 版本，禁止跳过中间双写发布。

生产数据修复不得混入主线 `V*.sql` 自动顺跑。release D3/D4 使用独立 Flyway 配置：`locations=db/identity-repair-release`、
`sqlMigrationPrefix=D`、`table=flyway_identity_repair_history`、`baselineOnMigrate=true`、`baselineVersion=0`，并显式固定
Flyway 11.20.3。执行顺序必须是主线精确停在 V29 后运行 `target=3`，主线精确停在 V47 后运行 `target=4`；禁止在 V29
直接运行 repair `latest`。正式执行前还必须满足：重新冻结生产快照、业务批准 4 个项目和 2 辆车辆的正式雪花 ID/编码、
生成并复核绑定目标库名、快照指纹和雪花生成窗口的 D3/D4 manifest、8 项 release 契约测试通过，并按阶段配置生产
`SPRING_FLYWAY_TARGET` 与 GitHub `PROD_FLYWAY_TARGET`。历史 D1/D2 仅为 2026-07-12 隔离演练，不得回放到生产；
2026-07-13 D3/D4 已按上述顺序在由实时 dump 恢复的新生产库执行，主线随后到 V56；该执行记录不可重放或修改。
当前生产 `SPRING_FLYWAY_TARGET=56`，但 GitHub 持久变量 `PROD_FLYWAY_TARGET` 仍未配置；未来自动 tag 部署必须先补齐该变量，否则继续失败关闭。

### 13.2 观测指标

| 指标 | 维度 | 告警/退出规则 |
| --- | --- | --- |
| `identity_write_missing_total` | module、field | 新应用写入必需 ID缺失时立即告警，发布 A后必须为 0 |
| `identity_fallback_total` | module、field、reason | 发布 A允许下降；发布 B后任何新增命中告警；Contract 前连续完整周期为 0 |
| `identity_snapshot_mismatch_total` | entity_kind、module | ID与 code/name/project 归属不一致立即告警，必须为 0 |
| `identity_orphan_detected_total` | table、constraint | 任意大于 0停止迁移/发布 |
| `source_identity_conflict_total` | source_type、module | 重复占用、容量或来源头/明细不一致立即告警 |
| `unsafe_entity_id_total` | client_version、endpoint | 前端/网关发现不安全 number 立即拒绝；非测试环境必须为 0 |
| `snowflake_clock_rollback_total` | instance、machine_id | 任意命中为基础设施告警，实例停止生成新 ID |

日志只记录模块、字段、记录 ID、候选数量和错误类型；不记录 API Key、令牌、银行账户等敏感内容。Dashboard 同时展示
新字段非空率、fallback 趋势、回填剩余量和约束验证状态，避免仅凭应用无报错判断迁移完成。

### 13.3 回滚

- Expand 阶段只增加可空列，可安全回滚应用并保留新列。
- Backfill 失败依赖事务回滚或幂等批次重跑，不得手工标记迁移成功。
- Switch 阶段回滚时必须继续双写，不能恢复只写旧字段的版本。
- Contract 阶段设置非空后，旧应用不再兼容；出现问题优先前滚修复，不能直接恢复旧版本混合写入。
- 不删除编码、名称、规格等历史快照列，因此业务凭证和打印内容具备长期回溯能力。

| 故障阶段 | 允许动作 | 禁止动作 |
| --- | --- | --- |
| G0/G1 代码止血 | 回滚到仍理解旧 schema 的已验证版本 | 恢复客户 ID写入项目字段 |
| V20–V26 Expand | 回滚应用并保留可空新列/索引 | 删除已部署列或改写已执行迁移 |
| 发布 A 双写 | 回滚到同样支持新列的兼容版本，继续双写 | 恢复只写旧名称/编码的版本 |
| V27–V34 Backfill | 事务失败自动回滚；修复歧义后以更高版本重试 | 手工标记 Flyway 成功或直接改共享库 |
| 发布 B Switch | 切回旧读路径但继续双写 ID，保留回填数据 | 停止 ID写入或恢复 ROW_NUMBER/name-MD5 身份 |
| V38–V55 Contract | 约束未验证时停止；已收紧后优先更高版本前滚 | 直接部署不写 ID的旧应用或强制删除 FK |

## 14. 实施优先级与工作包

### 14.1 依赖顺序与交付物

| 顺序 | 工作包 | 主要角色 | 依赖 | 独立交付物 |
| --- | --- | --- | --- | --- |
| G0 | 雪花生成与 Long 字符串契约加固 | 后端/运维 | 无 | 机器号清单、预分配失败闭合、时钟告警、契约测试 |
| G1 | 客户/项目错写止血 | 后端/数据库 | G0 | 代码修复、历史异常只读报告、回归测试；可先于全量迁移发布 |
| E1 | V20–V26 Expand | 数据库 | G0/G1 设计确认 | 可空列、生成批次键、首批 FK支撑索引，不改变旧读写 |
| A1 | 后端 ID双写与权威快照 | 后端 | E1 | 实体/DTO/解析器/来源链双写、fallback 指标 |
| A2 | 前端 EntityId、option、保存和父单导入 | 前端 | A1 API契约 | 全链字符串 ID、value=id、按 ID导入与分页候选 |
| B1 | V27–V37 Backfill/前滚补缺 | 数据库/业务 | A1 已在线双写 | 每域回填、遗漏字段前滚修复、异常清单、库存/财务守恒证据 |
| S1 | 报表、应收应付、候选与容量 Switch | 后端/前端 | B1 清零 | ID读取、真实报表 ID、无名称/单号身份匹配 |
| C1 | V38–V55 Constraint/Contract | 数据库/发布 | S1 观察周期 fallback=0 | FK/CHECK验证、必填非空、客户项目/车辆归属/系统关系约束 |
| C2 | 届时最大版本 + 1 兼容清理 | 全栈/文档 | 发布 C | 删除零命中 fallback，更新 OpenAPI、数据字典和运行手册 |

G0/G1 可以独立小批次先行；采购/销售库存、往来方财务和前端改造可在 Expand 后分团队并行，但 Backfill、Switch 和 Contract
必须按表中顺序串行过门禁。每个工作包单独评审和验证，禁止把全系统改造压成一个无法回滚的大提交。

### 14.2 当前实施批次的可执行任务

代码、迁移、生产切换和当前版本部署已经完成；任务看板按“已生产执行”与“发布后仍待验收”分离：

| 任务 ID | 范围 | 当前状态 | 具体产物 | 完成门禁 |
| --- | --- | --- | --- | --- |
| DB-ID-01 | V20–V56 冻结迁移审查 | 已提交并生产执行 | 两份独立 checksum manifest；Final 5/5、Forward 9/9、Constraint 8/8；开发测试库、候选库和生产库 V56 | 保持 checksum 冻结；补齐真实锁/WAL/空间报告，后续问题从 V57+ 前滚 |
| DB-ID-02 | V35–V37 物流对账补缺 | 已生产执行 | 物流对账明细客户/项目前滚补列、索引、回填，并进入 V1→V55 空库链和生产 V56 | 完成来源继承、跨客户项目和余额守恒的发布后核验 |
| DB-ID-03 | 生产快照 repair 与切换 | 已完成 | 历史 D1/D2 黄金副本；实时 D3/D4 manifest；新生产库主线 56/56、repair 3/3；旧 V11 库和 dump 保留 | 演练从保留基线恢复，归档锁/WAL/空间和逐单/全局守恒 |
| BE-ID-01 | 主数据/进销存双写 | 已部署生产 | 商品、仓库、客户、项目、供应商、物流商 ID与快照贯穿 DTO/实体/服务 | 观察同名不同 ID、改名不偏移和历史数据行为 |
| BE-ID-02 | 来源链与财务 typed identity | 已部署生产 | 所有来源 ID、typed party、结算主体继承和不可变校验；物流稳定来源 91/91 | 验证真实来源冲突、跨主体、容量并发与状态流 |
| BE-ID-03 | 报表与候选 Switch | 已部署生产 | 库存/出入库/应收应付按 ID；供应商候选 126/126、客户/项目/物流商 128/128、销售 117/117 | 完成历史守恒、权限和真实分页 total 验证 |
| FE-ID-01 | API 边界 | 已部署生产 | `EntityId` decoder、领域 DTO、保存白名单、草稿、直接来源和审计 actor | 完整 E2E 中验证嵌套 ID逐字符往返 |
| FE-ID-02 | 交互链 | 已部署生产，定向收尾已 push | option、表单、父单导入、候选分页、批量生成、草稿和缓存键；Statement 109/109；`4e93949` 两条交付核定链 | 修复剩余导航、overlay、路由 marker 与测试数据契约；验证真实权限与软删除回显 |
| QA-ID-01 | 定向回归 | 静态、迁移和新增定向链已通过；完整真实 E2E 尚未通过 | 18 类 PostgreSQL 联合 62/62；release D3/D4 契约 8/8；`4e93949` 远端 CI 全绿；完整 E2E 可复核结果 25 passed、57 failed、2 skipped | 归档并重跑完整 E2E，核对采购订单导入、附件 smoke、live API Key 候选和业务守恒 |
| QA-ID-02 | 本地与远端质量收口 | 非 E2E 门禁完成 | 后端空 V55 库 6184/6184 + verify；前端 6181 passed + 4 skipped；Aries Typecheck/Build/Lint/4 分片/React Doctor 成功 | fallback 周期、回滚演练和完整 E2E 通过后才可整体验收 |

任何后续发现的 schema 缺口必须登记目标表并从 V57+ 前滚，不得改写 V20–V56，也不得在业务代码中用名称 fallback 掩盖。

### 14.3 发布后验收执行顺序

1. 保持 V20–V56、D3/D4、旧 `Master_Prod` 和实时 dump 不变，任何数据库修正只从 V57+ 前滚。
2. 补齐生产雪花机器号清单、时钟回拨、GitHub `PROD_FLYWAY_TARGET` 和失败关闭监控。
3. 对历史与新增数据执行只读扫描，归档错绑、空值、孤儿、冲突、逐单和全局守恒结果。
4. 归档本次切换的锁等待、WAL、索引空间和运行耗时；演练从保留的 V11 库或 dump 恢复并记录切换步骤。
5. 补齐采购订单导入、附件 smoke、live API Key 详情的测试候选数据，在隔离副本重新执行并归档完整 E2E。
6. 观察生产 fallback 连续一个完整业务周期为 0，验证真实权限、软删除、状态流、Dashboard 和告警。
7. 以上验收全部完成后再把本文状态改为 `Implemented`；当前生产部署成功不能替代剩余验收证据。

### P0：雪花根基与前端精度边界

- 固定生产机器号分配、预分配 ID校验、根/子项生成入口和更新不可变契约。
- 后端所有 Long 保持 JSON 字符串；前端建立真实运行时 EntityId decoder，unsafe number 失败闭合。
- 核心 option、表单、保存白名单、路由、缓存键和父单导入不再把 ID作为 number、名称或编码。

### P0：库存身份止偏

- `material_id`、`warehouse_id` 贯穿采购、销售、入库、出库。
- 批次号统一规范化并在库存生效后锁定；默认不新增库存层实体。
- 库存按商品 ID、仓库 ID、`batch_no_normalized` 聚合，出入库报表返回真实明细 ID。
- 锁定已有库存商品的身份和计量属性。

### P0：核心来源关系固定

- 补齐订单 → 入库 → 销售订单 → 出库 → 物流明细的来源 FK和 ID继承。
- 物流对账增加直接物流单/明细 ID，删除用商品、批次、仓库、数量猜来源的分支。
- 候选占用、容量和重复导入以来源 ID判断，业务单号只作展示快照。

### P0：客户/项目错写修复

- 修复 `SalesOrderApplyService` 把客户 ID 写入项目 ID 的缺陷。
- 分别按 `customer_id`、`project_id` 解析，并验证项目所属客户。
- 扫描并清洗已存在的碰撞错绑数据，增加 FK、服务和接口回归测试。

### P1：客户、项目和合同身份

- 客户编码引用后生命周期保护。
- 客户 ID、项目 ID 贯穿合同、销售、物流、开票、对账和收款。
- 删除客户/项目名称作为唯一关系的查询。

### P1：供应商和物流商从稳定编码升级为雪花 ID

- 保留现有编码保护，同时把主数据 ID 写入采购、物流和财务链路。
- 应收应付最终按 ID 分组。
- 清理普通付款的末级名称回退。

### P1：财务、结算主体和引用目录

- 收付款、对账、退款到账、台账和应收应付使用 typed party 与显式来源 ID。
- 为所有 `settlement_company_id` 增加 FK或多态校验，补齐 V12–V18 和业务明细删除保护。
- 建立按领域维护的主数据引用目录及删除、停用、项目归属和编码复用规则。

### P2：兼容清理和文档

- 删除已确认无命中的名称回退。
- 收敛前端 `string | number` 为 `EntityId`。
- 更新 OpenAPI、数据字典、迁移运行手册、审计文档和故障排查 SQL。
- 评估 `material_category_id`、车辆 ID及 ADR-004 库存层触发条件；未触发的能力不提前实施。

### P2：系统与认证关系完整性

- 为用户、角色、权限、API Key、刷新令牌、附件和打印模板的真实实体 ID补齐索引/FK。
- 为部门/角色树增加自引用完整性和循环校验；明确组织归属 `SET NULL` 是当前状态关系的受控例外。
- 保留菜单/资源/动作代码和 `module_key + record_id` 多态关系，增加不可变代码与模块目标类型契约测试。

## 15. 非目标

- 不替换或重新生成现有雪花主键。
- 不引入 UUID、微服务、事件溯源或独立库存中台。
- 不删除业务编码、名称、规格、单位和业务单号快照。
- 不在本计划中重写应收应付会计模型或引入总账凭证。
- 不允许直接修改开发或生产数据库完成持久化修复。
- 不承诺一次发布完成；稳定身份改造必须按兼容迁移阶段交付。

## 16. 发布后治理与剩余业务决策

1. 确认商品“身份属性”列表：编码、品牌、材质、规格、长度、单位中的哪些变化必须新建 SKU。
2. 确认仓库合并是否需要正式库存转移单；在此之前禁止直接替换仓库 ID。
3. 确认批次号是否需要改号/复用、出库是否跨多个库存层、库存层是否有独立生命周期；仅任一答案为“是”时另立库存层 ADR。
4. 确认外部导入是否有稳定外部 ID；没有时仍以编码查找，但导入落库前必须唯一解析为内部雪花 ID。
5. 补录本次生产数据规模、维护窗口、锁等待、WAL 和索引空间，并据此固化未来索引使用普通创建或非事务并发创建的决策规则。
6. 确认物流来源占用粒度是整张销售出库单还是单条出库明细，以及物流单软删除后是否允许重新导入；未确认前不增加错误的全局唯一约束。
7. 确认采购/销售合同是否具有结算主体和仓库语义；当前不存在的语义不为了字段统一而强行增加。
8. 车辆 ID及车辆/物流商组合归属已由 D4 与 V48–V49完成生产修复和验证；仅 `material_category_id` 是否从 V57+ 前滚增加仍需单独评审。

### 16.1 决策责任与未决时默认值

| 决策 | 责任角色 | 未决时采用的安全默认值 | 阻塞范围 |
| --- | --- | --- | --- |
| 商品身份/计量属性 | 产品 + 采购/库存业务 + 后端 | 编码、品牌、材质、规格、长度和基础单位改变均新建 SKU | 商品 Contract 与修改规则 |
| 仓库合并 | 库存业务 + 产品 | 必须通过显式库存转移，不替换历史 `warehouse_id` | 仓库合并功能，不阻塞按 ID汇总 |
| 批次生命周期 | 库存业务 + 架构 | 大小写敏感、trim 规范化、生效后不可改；不新增库存层实体 | 仅阻塞独立 lot 设计 |
| 外部导入身份 | 集成负责人 + 产品 | 外部无稳定 ID时以编码唯一解析；零/多候选整行失败 | 对应导入渠道 |
| 索引创建方式 | DBA/运维 + 后端 | 本次执行数据补录前不把普通建索引推广为未来默认；新大表迁移缺少规模/窗口数据时优先停止评审 | V57+ 索引迁移与运行手册 |
| 物流来源占用 | 物流业务 + 产品 + 后端 | 先按直接销售出库明细 ID校验重复，不增加未经确认的全局唯一约束 | 物流 Contract 约束 |
| 合同仓库/结算语义 | 产品 + 财务 | 不新增当前领域没有的字段；只传播已有明确关系 | 对应合同扩展 |
| 商品类别 ID P2 | 产品 + 架构 | 不纳入当前 V20–V56，单独评审后从 V57+ 前滚增加 | 不阻塞核心稳定身份；车辆 ID已由 V48–V49处理 |

所有决策结果必须以 ADR补充、需求记录或数据字典变更落库到 Git；口头确认不能作为迁移收紧依据。阻塞范围之外的工作继续按安全默认值推进，
避免一个 P2 选择拖延已经明确的 P0 身份止偏。

## 17. 实施完成定义

每个工作包必须同时交付代码、迁移/契约、自动化测试和运行证据，不能以“字段已增加”作为完成：

- [ ] 需求范围内所有实体 ID均有唯一目标表、字段注释和数据字典记录。
- [ ] 新请求/响应、OpenAPI 和前端类型均使用十进制字符串 ID，快照字段职责明确。
- [ ] 新增 ID写入路径有 ID/快照/来源一致性测试，更新路径有身份不可变测试。
- [ ] 每个新增普通 FK列都有全量索引；多态关系有成对检查、解析器和删除保护。
- [x] 本地确认 V1–V19 受控历史未被修改；V20–V46 的 27 项与 V47–V55 的 9 项分别由独立 manifest 防漂移；临时空库可从 V1顺序迁移至 V55并通过 `validate`。共享/生产执行不在此勾选范围内。
- [x] 两份受限生产一致性快照的隔离副本已分别按 V29/D1/V47/D2/V55 和 V29/D3/V47/D4/V55 顺序迁移；最新候选库主线 55/55、repair 3/3，身份/约束/索引扫描通过。
- [x] 实时 V11 生产 dump 已冻结并校验 SHA-256；从 dump 恢复的新生产库按 V29/D3/V47/D4/V56 完成主线与 repair 双 validate，旧 `Master_Prod` 保持 V11；Leo `v3.0.0`、Aries `v2.3.0` 部署及生产健康/版本核验通过。
- [ ] 回填指标、人工清洗清单、逐单守恒和全局守恒均归档，所有未解析/孤儿/冲突计数为 0。
- [ ] 库存、出入库和应收应付已用 ID聚合；`ROW_NUMBER`、名称 MD5、`DISTINCT ON(name)` 和 `source_no` 身份路径已移除。
- [ ] 前端 option、表单、草稿、路由、缓存、父单导入、筛选和保存载荷全程保持 `EntityId`。
- [ ] fallback 在生产连续一个完整业务周期为 0，且告警、Dashboard、回滚版本均已验证。
- [x] 空 V55 库后端 `test` 6184/6184 与 `verify`、前端 Vitest 6181 passed + 4 skipped、TypeScript、Biome/ESLint、关键 PostgreSQL、本地生产构建、生产依赖审计、`leo_test`/空库迁移和两仓 diff check 已通过。
- [ ] 稳定身份完整 E2E（可复核历史 8/76/0、25/57/2；`4e93949` 仅补齐两条定向链路，未形成新的完整归档）、锁/WAL/空间与业务守恒、fallback 完整周期、Dashboard/告警和从保留 V11 基线恢复的回滚演练全部通过。

只有以上项目全部完成，且 V38–V56（若版本继续前滚则到届时最新 Contract 版本）约束已验证，才允许将本文状态从 `In Progress` 改为 `Implemented`，并对外宣称
“全系统雪花 ID 稳定身份固定完成”。
