# 架构与静态质量门禁

## Java 顶层模块依赖基线

默认检查是只读操作：

```bash
bash scripts/check-java-module-boundaries.sh
```

扫描器读取 `src/main/java` 的 Java import，按 `com.leo.erp` 后的第一段包名建立顶层模块图，并执行两类增量阻断：

1. 拒绝新增跨模块 `repository`、`domain.entity` 和 `web.dto` import；
2. 拒绝新增任何参与模块环的依赖边。

已有违规以精确的源文件、目标类型和模块边记录在
`config/java-module-boundaries-baseline.json`。删除已有违规时必须在同一变更中缩减基线，避免已经移除的依赖以后借旧基线重新进入；不要求一次性清理全部存量。

依赖整改完成并经过架构评审后，可在本地显式刷新基线：

```bash
bash scripts/check-java-module-boundaries.sh --write-baseline
```

基线内容排序稳定且不含时间戳。扫描器在 `CI=true` 时拒绝刷新基线，流水线不能自动接受新增违规。修改基线必须与对应的依赖整改或例外说明一起评审。

扫描器自检使用临时目录，不会在仓库中生成测试文件：

```bash
bash scripts/check-java-module-boundaries.sh --self-test
```

该轻量扫描器不解析反射、配置字符串或没有 import 的全限定类名。已显式纳管的模块由下面的 Spring Modulith 字节码验证补充覆盖。

### 当前基线快照

2026-07-26 最终源码与阶段中间基线的对比如下：

| 指标 | 阶段中间基线 | 当前基线 | 变化 |
| --- | ---: | ---: | ---: |
| Java 文件 | 607 | 653 | +46 |
| 顶层模块依赖边 | 55 | 51 | -4 |
| 跨模块内部类型 import | 104 | 70 | -34 |
| 参与循环的模块边 | 31 | 19 | -12 |

Java 文件增加来自公开端口、快照和适配器；同时顶层依赖边下降 7.3%，内部类型 import 下降 32.7%，循环边下降 38.7%。刷新基线前的差异检查只发现已移除项，没有新增受限 import 或新增循环边。

剩余 19 条循环边全部位于平台强连通分量
`{attachment, auth, common, master, security, system}`。其中 `attachment` 已纳管，其余模块尚未纳管；静态基线继续按完整顶层包图阻止该分量增加新边。精确边如下：

```text
attachment -> common, security, system
auth       -> common, security, system
common     -> security, system
master     -> attachment, common, system
security   -> auth, common, system
system     -> attachment, auth, common, master, security
```

剩余 70 条跨模块内部类型 import 的分布如下。它们仍是待解耦债务，不因进入基线而视为合规：

| 来源 -> 目标 | 数量 | 类型 |
| --- | ---: | --- |
| finance -> master | 14 | 9 repository，5 domain.entity |
| system -> auth | 9 | 5 repository，4 domain.entity |
| finance -> common | 5 | 5 web.dto |
| finance -> system | 5 | 2 repository，3 domain.entity |
| logistics -> master | 5 | 3 repository，2 domain.entity |
| system -> master | 5 | 4 repository，1 domain.entity |
| sales -> common | 4 | 4 web.dto |
| sales -> master | 4 | 2 repository，2 domain.entity |
| purchase -> master | 3 | 2 repository，1 domain.entity |
| master -> system | 2 | 2 domain.entity |
| purchase -> allocation | 2 | 2 repository |
| purchase -> common | 2 | 2 web.dto |
| security -> auth | 2 | 1 repository，1 domain.entity |
| statement -> common | 2 | 2 web.dto |
| statement -> master | 2 | 2 repository |
| logistics -> common | 1 | 1 web.dto |
| logistics -> system | 1 | 1 domain.entity |
| purchase -> system | 1 | 1 domain.entity |
| sales -> system | 1 | 1 domain.entity |

## Spring Modulith 验证

`application.yml` 使用 `explicitly-annotated` 检测策略。只有顶层 `package-info.java` 明确声明的模块参与硬门禁，模块收口后再逐个加入，避免历史依赖一次性阻断全部交付。

当前已纳管 5 个模块，允许边均指向目标模块的 `api` 命名接口：

| 模块 | `allowedDependencies` |
| --- | --- |
| attachment | 无 |
| purchase | `attachment::api` |
| sales | `attachment::api`、`purchase::api` |
| logistics | `attachment::api`、`sales::api` |
| statement | `attachment::api`、`logistics::api`、`sales::api` |

这些边构成 DAG。`ApplicationModules.verify()` 同时检查循环、内部包访问和白名单，禁止使用开放模块或内部包白名单绕过边界。

执行命令：

```bash
bash scripts/verify-modulith-architecture.sh
```

脚本使用 Maven `architecture-verification` profile 解析 `spring-modulith-core`，将独立 runner 编译到临时目录，再调用 `ApplicationModules.verify()`。runner 不是 Spring Bean、不位于应用源码目录，也不会进入生产 JAR 或在应用启动时执行。若没有发现任何显式模块，检查会失败，防止门禁空跑。

## Checkstyle 严重度

Checkstyle 全局默认仍为 `warning`，存量格式和魔法数字问题继续显示但不阻断。Maven 插件显式设置 `violationSeverity=error` 和 `failOnViolation=true`，因此配置中标记为 `error` 的未使用 import、冗余 import、空 catch、Tab、标准输出及不安全 API 规则会阻断构建。

新增跨模块内部类型引用不依赖 Checkstyle 的 warning 阈值，而由独立架构基线门禁硬阻断。
