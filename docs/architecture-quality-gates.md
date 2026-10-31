# 架构与静态质量门禁

## Java 顶层模块依赖基线

默认检查是只读操作：

```bash
bash scripts/check-java-module-boundaries.sh
```

检查由 ArchUnit（`scripts/ModuleBoundaryVerifier.java`，编译到临时目录、不进入生产 JAR）在 `target/classes` 字节码上执行，按 `com.leo.erp` 后的第一段包名建立顶层模块图，并执行两类增量阻断：

1. 拒绝新增跨模块 `repository`、`domain.entity` 和 `web.dto` 依赖；
2. 拒绝新增任何参与模块环的依赖边；
3. 拒绝新增白名单外的实体状态写入：实体 `setStatus` 只允许出现在 `*ApplyService`、`*CompletionSyncService` 和 `CrudStatusGuard` 中，防止绕过 `beforeStatusUpdate` 状态守卫的旁路直写（背景见 2026-07-26 业务状态机审查）。

已有违规通过 ArchUnit `FreezingArchRule` 冻结在 `config/archunit-boundary-store/`。本地运行时已修复的违规会自动从冻结存储中移除（同一变更内提交缩减后的存储）；`CI=true` 时存储只读，流水线不能自动接受新增违规。

依赖整改完成并经过架构评审后，可在本地显式重建基线：

```bash
bash scripts/check-java-module-boundaries.sh --write-baseline
```

`--write-baseline` 在 `CI=true` 时拒绝执行。修改冻结存储必须与对应的依赖整改或例外说明一起评审。

基于字节码分析后，反射之外的全限定类名引用、注解、泛型签名等不再是盲区；早期基于 import 文本的 Python 扫描器已退役（历史实现见 Git 历史）。已显式纳管的模块由下面的 Spring Modulith 验证补充覆盖。

### 2026-10-31 当前基线（权威）

| 指标 | 数值 |
| --- | ---: |
| 冻结受限依赖 | 44 |
| 冻结循环模块边 | 25（平台 SCC 20 + 业务 SCC 5） |
| 冻结未授权状态写 | 23 |
| 已纳管 Modulith 模块 | 8 |

当前 25 条循环边的分布：平台强连通分量
`{attachment, auth, common, master, security, system}` 20 条；业务强连通分量
`{inventory, purchase, sales}` 5 条（`inventory -> purchase`、`inventory -> sales`、
`purchase -> inventory`、`sales -> inventory`、`sales -> purchase`，均走 `api` 端口）。

当前 44 条受限依赖分布：

| 来源 -> 目标 | 数量 |
| --- | ---: |
| finance -> system | 21 |
| inventory -> master | 8 |
| security -> auth | 4 |
| auth -> security | 3 |
| sales -> system | 2 |
| purchase -> system | 2 |
| master -> system | 2 |
| logistics -> system | 2 |

`config/archunit-boundary-store/` 是唯一权威来源，上表随基线收敛会变化。2026-10-31 前的
一次 refreeze（`36673b52`）曾把 172 条新增违规一次性纳入基线，因此基线的更新方式已收紧为
"只读 + 显式写基线 + CI 禁止净扩张"，见本节末尾"基线更新政策"。

### 历史基线快照（2026-07-26，仅作趋势参照）

2026-07-26 最终源码与阶段中间基线的对比如下（历史口径，数字与当前不一致）：

| 指标 | 阶段中间基线 | 当前基线 | 变化 |
| --- | ---: | ---: | ---: |
| Java 文件 | 607 | 683 | +76 |
| 顶层模块依赖边 | 55 | 50 | -5 |
| 跨模块内部类型 import | 104 | 24 | -80 |
| 参与循环的模块边 | 31 | 19 | -12 |

Java 文件增加来自公开端口、快照和适配器；同时顶层依赖边下降 9.1%，内部类型 import 下降 76.9%，循环边下降 38.7%。刷新基线前的差异检查只发现已移除项，没有新增受限 import 或新增循环边。

当时的 19 条循环边全部位于平台强连通分量
`{attachment, auth, common, master, security, system}`。其中 `attachment`、`master` 已纳管，
`auth`、`common`、`security`、`system` 尚未纳管；静态基线继续按完整顶层包图阻止该分量增加新边。
当前基线已包含业务分量 `{inventory, purchase, sales}`（见上节），本文档不再声称业务模块之间是 DAG。

### 业务 SCC 单向化路线（`{inventory, purchase, sales}`）

本次交付只校正文档与门禁口径，**不做代码解环**：解环会改变库存/销售/采购的运行时依赖与事务边界，属独立结构工程。路线如下，每阶段以「环边数下降」为唯一验收判据，禁止用 refreeze 消化新增边：

1. **抽共享只读契约**：把三模块共同消费的「库存可用量 / 出库明细 / 订货占用」读模型抽到 `common::api` 或新建 `fulfillment::api`，sales/purchase 只依赖不可变快照，不落实体或仓储。
2. **反转写入方向**：销售出库对库存的扣减改为 inventory 侧消费 sales 领域事件（`@ApplicationModuleListener` + outbox），sales 不再同步调用 inventory。
3. **同样处理采购入库**：入库完成对销售占用的释放改为事件驱动，移除 purchase → inventory 的同步写依赖。
4. **验收**：`bash scripts/check-java-module-boundaries.sh` 环边数从当前 25 逐条下降（目标 ≤20 后再逐条），基线只许净收缩；每阶段补真实库并发/一致性用例。

当前 5 条业务环边仍在冻结基线内，属已登记技术债。

历史 import 口径的 24 条分布已退役，不再作为门禁输入；当前以冻结存储的 32 条受限依赖为准（见上节）。

### 基线更新政策（2026-10-31 收紧）

- `ModuleBoundaryVerifier` 默认只读：本地运行也不再自动写基线，只有显式 `--write-baseline`
  （脚本内部传 `--refreeze`）才允许更新，且 `CI=true` 时拒绝 refreeze。
- CI 增加 `scripts/deploy/assert-arch-baseline-not-expanded.sh`：相对父提交，基线新增行数必须
  不超过删除行数（只许净收敛）；确需净扩张时显式设置 `ALLOW_ARCH_BASELINE_EXPANSION=true`
  并在架构评审中说明。
- 历史教训：`36673b52` 曾一次 refreeze 172 条新增违规，其中 7 条状态写至今仍在基线；
  这条"本地跑一次门禁就自动扩容"的通道已经关闭。

## Spring Modulith 验证

`application.yml` 使用 `explicitly-annotated` 检测策略。只有顶层 `package-info.java` 明确声明的模块参与硬门禁，模块收口后再逐个加入，避免历史依赖一次性阻断全部交付。

当前已纳管 8 个模块，允许边均指向目标模块的 `api` 命名接口：

| 模块 | `allowedDependencies` |
| --- | --- |
| allocation | 无 |
| attachment | 无 |
| master | `attachment::api` |
| purchase | `allocation::api`、`attachment::api`、`master::api` |
| sales | `attachment::api`、`master::api`、`purchase::api` |
| logistics | `attachment::api`、`master::api`、`sales::api` |
| statement | `attachment::api`、`logistics::api`、`master::api`、`sales::api` |
| market | `master::api`、`purchase::api` |

这 8 个模块各自的 `allowedDependencies` 有向且受 `ApplicationModules.verify()` 校验，但**不能据此认为全局依赖图是 DAG**：`finance`、`system`、`inventory` 等模块尚未纳管，仍由上面的完整包图冻结基线约束；业务分量 `{inventory, purchase, sales}` 就是未纳管循环的实例。
`master::api` 按客户、供应商、项目、物流商、车辆、物料分类规则和主数据统计拆分同步查询端口，只返回不可变快照。生产源码中 `master` 外部对其 Repository 和 JPA 实体的 import 已清零；尚未纳管的 `finance`、`system` 也统一改为消费该公开 API，后续纳管无需再迁移主数据依赖。
`auth.api` 通过账号展示快照、有效认证快照、会话统计、首次账号初始化命令和公开事件契约收口外部调用。生产源码中 `auth` 外部对其 Repository、JPA 实体、领域枚举和内部 Service 的 import 已清零。`auth` 根模块仍反向依赖 `security` 的 JWT/缓存能力和 `system` 的操作日志能力，且三者仍处于同一强连通分量，因此本阶段不添加 `@ApplicationModule`，避免把尚未消除的循环误报为已治理完成。

执行命令：

```bash
bash scripts/verify-modulith-architecture.sh
```

脚本使用 Maven `architecture-verification` profile 解析 `spring-modulith-core`，将独立 runner 编译到临时目录，再调用 `ApplicationModules.verify()`。runner 不是 Spring Bean、不位于应用源码目录，也不会进入生产 JAR 或在应用启动时执行。若没有发现任何显式模块，检查会失败，防止门禁空跑。

## Checkstyle 严重度

Checkstyle 全局默认仍为 `warning`，存量格式和魔法数字问题继续显示但不阻断。Maven 插件显式设置 `violationSeverity=error` 和 `failOnViolation=true`，因此配置中标记为 `error` 的未使用 import、冗余 import、空 catch、Tab、标准输出及不安全 API 规则会阻断构建。

新增跨模块内部类型引用不依赖 Checkstyle 的 warning 阈值，而由独立架构基线门禁硬阻断。
