# leo 增量测试（本地）

本地按 Git 变更集只跑相关 JUnit 测试，缩短反馈循环；**CI 仍执行全量测试**，增量结果不是合并判据。

脚本：`scripts/test-affected.sh`（自测：`scripts/test-affected.selftest.sh`）

## 用法

```bash
# 默认：变更集 = merge-base HEAD origin/main → 工作区（含已提交、暂存、未跟踪）
bash scripts/test-affected.sh

# 只看决策，不执行 Maven
bash scripts/test-affected.sh --list

# 指定基线（例如从一个已知绿的提交开始算）
bash scripts/test-affected.sh --base 8e5c6a09

# 强制全量 / 调整定向阈值
bash scripts/test-affected.sh --full
bash scripts/test-affected.sh --max-classes 60     # 默认 40，也可用 LEO_AFFECTED_MAX_CLASSES
```

退出码：

| 退出码 | 含义 |
| --- | --- |
| 0 | 测试通过；或明确“跳过”（无变更 / 无相关测试） |
| 1 | 测试失败（透传 Maven 退出码） |
| 2 | 参数或环境错误 |
| 3 | 仅命中真实库用例（`*PostgresTest`），默认不跑，**本次未验证任何测试** |

“跳过”会打印 `跳过不等于“测试通过”`，不要把它当成绿灯。

## 映射与回退规则

宁可多选、不可漏测：任何无法可靠判断影响面的变更都回退全量。

| 变更 | 决策 |
| --- | --- |
| `src/test/java/**/FooTest.java` | 直接定向执行该类 |
| `src/main/java/**/Foo.java` | 定向执行 `Foo*Test.java` + 测试源码中出现 `Foo` 的 `*Test`（`grep -rlwF`，保守多选） |
| 主代码有变更但映射不到任何测试 | 回退全量 |
| 测试 support / 基类（非 `*Test` 的 `.java`）变更或被引用 | 回退全量 |
| `pom.xml`、`.mvn/*`、`mvnw`、`lombok.config`、`config/*` | 回退全量 |
| `src/main/resources/**`、`src/test/resources/**`（含 Flyway 迁移） | 回退全量 |
| 删除测试文件 | 回退全量 |
| 文档 / `scripts/*` / `.github/*` / `archive/*` / `*.md` | 明确跳过（不跑测试） |
| 未分类路径（含 `perf/*` 等） | 回退全量 |
| 选中测试类 > 阈值（默认 40） | 回退全量 |
| `*PostgresTest` | 默认过滤不跑；仅命中真实库用例时退出码 3 |

真实库用例与 `docs/real-db-test-gate.md` 一致：涉及数据库 / SQL / 迁移 / 仓储口径的改动必须显式执行：

```bash
LEO_TEST_POSTGRES=true bash scripts/maven.sh -B -ntp -Dtest=SomePostgresTest test
```

## 实测数据（2026-10-20，8 核 / Maven 3.6.3 / surefire 3.5.6）

| 场景 | 结果 |
| --- | --- |
| 自测 `scripts/test-affected.selftest.sh` | 92 项全部通过（临时仓库 + mvn 桩，不触真实 Maven） |
| 工作区仅两个新脚本 | 变更 2 个非代码文件 → 跳过，exit 0 |
| `--base HEAD~6` | 10 个变更文件（含 `pom.xml`）→ 映射 10 个测试类，但按规则回退全量 |
| 定向实跑（临时改 `Specs.java` 一行注释） | 映射 6 个测试类、执行 46 个用例全绿；**墙钟 109s**，其中 `maven-compiler` 重编译 1065 个主源文件 + 353 个测试源文件约 100s，测试执行本身约 8s |
| 全量实测（同一工作区） | `BUILD SUCCESS`；墙钟 235s，但当时与前端全量测试并行、CPU 竞争，**不能当作基线** |
| 全量历史基线（干净机器、已编译） | 2471 用例 / 约 54s |

关键结论：增量的收益在“测试执行阶段”，**编译是下限**。刚改过主代码 / 首次构建后，Maven 可能全量重编译（实测 109s 中有约 100s 是编译），此时增量不一定比“直接全量”划算；连续迭代（编译已热）时收益才明显。若只是改一个测试类，通常只重编译测试源文件，收益更稳定。

## 局限

- **文本近似**：`grep -rlwF` 只匹配测试源码中出现的类名。反射、字符串拼接、注解值、SPI、资源文件中的类名可能漏；测试经父类 / 工具类间接依赖主代码时也可能漏。
- **不分析主代码之间的传递依赖**：A 被 B 使用、测试只引用 B 时，改 A 可能不会选中该测试。改 `common/**`、`security/**`、公共实体 / 工具类时，建议直接 `--full`。
- **编译仍需要全量**：Maven 编译下限在 20–30s，增量主要省的是测试执行时间。
- **默认不跑真实库用例**：增量绿灯不代表 DB 相关口径已验证。
- 基线默认是 `merge-base HEAD origin/main`；本地已提交但未推送的改动会被计入，远端未拉取时变更集可能偏大。

## 维护

- 新增构建配置、资源目录或测试基础设施路径时，同步更新 `scripts/test-affected.sh` 的 `classify_path` 与对应自测用例。
- 修改脚本后先跑 `bash scripts/test-affected.selftest.sh`（秒级、无副作用）。
- CI 不使用本脚本：`.github/workflows/ci.yml` 仍执行全量 `mvn package`。
