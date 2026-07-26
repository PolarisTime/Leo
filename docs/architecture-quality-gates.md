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

### 当前基线快照

2026-07-26 最终源码与阶段中间基线的对比如下：

| 指标 | 阶段中间基线 | 当前基线 | 变化 |
| --- | ---: | ---: | ---: |
| Java 文件 | 607 | 683 | +76 |
| 顶层模块依赖边 | 55 | 50 | -5 |
| 跨模块内部类型 import | 104 | 24 | -80 |
| 参与循环的模块边 | 31 | 19 | -12 |

Java 文件增加来自公开端口、快照和适配器；同时顶层依赖边下降 9.1%，内部类型 import 下降 76.9%，循环边下降 38.7%。刷新基线前的差异检查只发现已移除项，没有新增受限 import 或新增循环边。

剩余 19 条循环边全部位于平台强连通分量
`{attachment, auth, common, master, security, system}`。其中 `attachment`、`master` 已纳管，`auth`、`common`、`security`、`system` 尚未纳管；静态基线继续按完整顶层包图阻止该分量增加新边。精确边如下：

```text
attachment -> common, security, system
auth       -> common, security, system
common     -> security, system
master     -> attachment, common, system
security   -> auth, common, system
system     -> attachment, auth, common, master, security
```

剩余 24 条跨模块内部类型 import 的分布如下。它们仍是待解耦债务，不因进入基线而视为合规：

| 来源 -> 目标 | 数量 | 类型 |
| --- | ---: | --- |
| finance -> common | 5 | 5 web.dto |
| finance -> system | 5 | 2 repository，3 domain.entity |
| sales -> common | 4 | 4 web.dto |
| master -> system | 2 | 2 domain.entity |
| purchase -> common | 2 | 2 web.dto |
| statement -> common | 2 | 2 web.dto |
| logistics -> common | 1 | 1 web.dto |
| logistics -> system | 1 | 1 domain.entity |
| purchase -> system | 1 | 1 domain.entity |
| sales -> system | 1 | 1 domain.entity |

### ArchUnit 字节码基线重校准

2026-07-26 边界检查引擎从 import 文本扫描切换为 ArchUnit 字节码分析后，同一份源码的度量口径发生变化：受限依赖按"类 → 类"的字节码依赖计数（含注解、泛型签名、方法体内引用），冻结存储共 65 条受限依赖；循环模块边保持 19 条，与旧口径一致，交叉验证了两代引擎的模块图等价。当前字节码口径的受限依赖分布：

| 来源 -> 目标 | 数量 |
| --- | ---: |
| finance -> system | 17 |
| sales -> common | 15 |
| finance -> common | 13 |
| statement -> common | 4 |
| purchase -> common | 4 |
| master -> system | 4 |
| sales -> system | 2 |
| purchase -> system | 2 |
| logistics -> system | 2 |
| logistics -> common | 2 |

上表 import 口径的历史分布保留作趋势参照；后续整改进度以冻结存储 `config/archunit-boundary-store/` 为准。

## Spring Modulith 验证

`application.yml` 使用 `explicitly-annotated` 检测策略。只有顶层 `package-info.java` 明确声明的模块参与硬门禁，模块收口后再逐个加入，避免历史依赖一次性阻断全部交付。

当前已纳管 7 个模块，允许边均指向目标模块的 `api` 命名接口：

| 模块 | `allowedDependencies` |
| --- | --- |
| allocation | 无 |
| attachment | 无 |
| master | `attachment::api` |
| purchase | `allocation::api`、`attachment::api`、`master::api` |
| sales | `attachment::api`、`master::api`、`purchase::api` |
| logistics | `attachment::api`、`master::api`、`sales::api` |
| statement | `attachment::api`、`logistics::api`、`master::api`、`sales::api` |

这些边构成 DAG。`ApplicationModules.verify()` 同时检查循环、内部包访问和白名单，禁止使用开放模块或内部包白名单绕过边界。
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
