# 真实库（PostgreSQL）回归用例门禁与编写约定

## 为什么要有这道门禁

所有 `*PostgresTest` 用例都标注
`@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")`：
未设置该环境变量时 JUnit 整类跳过，Surefire 依然 `BUILD SUCCESS`。若只看"构建是否绿"，
真实库回归会以"默认跳过"的姿态长期脱离门禁（已有实际代价：某类真实库用例自 V180 起 4/5 必失败却长期潜伏）。

本项目只使用开发库与 CI 的 postgres service 跑真实库用例，**不新建独立 e2e 库**；
开发库中的历史 E2E 单据是共享数据，用例不得清理、也不得依赖。

## 门禁内容（`pom.xml`）

| 位置 | 配置 | 作用 |
| --- | --- | --- |
| `maven-surefire-plugin` 基线配置 | `failIfNoTests=true`、`failIfNoSpecifiedTests=true` | 没有任何用例被选中/执行，或 `-Dtest=` 未匹配到任何用例时，构建直接失败，杜绝门禁空跑，同时拦住写错类名的"定向执行" |
| `postgres-tests` profile（`-Dleo.test.postgres=true` 激活） | 只包含 `**/*PostgresTest.java`；profile 自行注入 `LEO_TEST_POSTGRES=true`；`failIfNoTests=true` | 真实库用例是否执行不再取决于调用方 shell 是否恰好导出变量；一个用例都没被选中即失败 |

不传 `-Dleo.test.postgres=true` 时保持原有全量测试行为（真实库用例按注解跳过）。
CI（`.github/workflows/ci.yml` 的 package job）通过环境变量 `LEO_TEST_POSTGRES: "true"` 开启真实库用例，
并在测试前执行 `flyway:migrate` 建好 schema，因此 CI 上这些用例会真正执行；删除该环境变量等同于关闭 CI 的这道门禁，
属于必须评审的门禁变更（`failIfNoTests` 只能拦住"一个用例都没被选中"，无法识别"被注解整体跳过"）。

## 命令

```bash
# 默认：真实库用例跳过（本地快速回归）
bash scripts/maven.sh -B -ntp test

# 全量真实库用例（只跑 *PostgresTest）；未选中任何用例即构建失败
# 提交涉及 db/migration、SQL、实体映射、仓储/口径的改动前必须执行
bash scripts/maven.sh -B -ntp test -Dleo.test.postgres=true

# 定向单个真实库用例（本仓常用门禁形式）
LEO_TEST_POSTGRES=true bash scripts/maven.sh -B -ntp test -Dtest=QuoteSheetFetchGraphExtremePostgresTest
# 等价的 profile 形式（由构建注入 LEO_TEST_POSTGRES，无需外部导出）
bash scripts/maven.sh -B -ntp test -Dleo.test.postgres=true -Dtest=QuoteSheetFetchGraphExtremePostgresTest
```

## 编写真实库用例的约定

1. **只统计自建数据**：共享库上禁止 `TRUNCATE`/`DELETE` 他人数据，禁止断言全表规模。凡是分页、计数、
   汇总类断言，必须按"本用例自建的归属标记"收窄：固定 id 区间或单据名前缀
   （示例：`QuoteSheetFetchGraphExtremePostgresTest` 的 `NAME_PREFIX = "pg-fetch-extreme-"`，
   并把 `store.page(...)` 的 `keyword` 传成该前缀），再用自建 id 集合做等值断言。
2. **自建自清**：`@AfterEach` 按记录下来的自建 id 清理，失败路径也必须执行（清理不能依赖断言先通过）。
3. **断言必须能变红**：新增/修改断言后，必须把过滤条件或实现临时改错跑一遍，确认变红再改回；
   只做"收窄后的查询"而不校验命中集合，会让 SQL 计数类断言在空结果时假绿。
4. **提交前执行**：涉及 SQL、迁移、实体映射、仓储与服务口径的改动，提交前跑定向真实库用例；
   `db/migration` 改动必须跑全量（`-Dleo.test.postgres=true`）。

## 迁移历史门禁（CI 与生产部署共用同一份实现）

`flyway:validate` 只回答“库中记录与本地脚本是否对齐”，它**不是**“这条迁移到底跑没跑”的探针：
判断某版本是否已执行必须看 `flyway_schema_history` 的 `success` / `checksum`。因此
`.github/workflows/ci.yml` 的 package job 与 `.github/workflows/deploy-production.yml` 的
backend job 在 `flyway:migrate`/`flyway:validate` 之后追加同源 SQL 断言，任一条不满足即失败：

| 门禁 | 位置 | 断言 / 变红条件 |
| --- | --- | --- |
| 迁移历史断言 | `scripts/deploy/assert-flyway-history.sh` | `success=false` 行数必须为 0；目标版本恰 1 行且 `success=true`、`checksum` 非空（漏跑/失败/checksum 为空/重复行都红）；`max(已成功版本)` 必须等于 `scripts/deploy/verify-flyway-target.sh` 输出；目标版本必须等于代码库最新迁移版本（把 target 调低也红） |
| 基线冻结 | `scripts/deploy/assert-flyway-baseline-frozen.sh` + `scripts/deploy/flyway-baseline.sha256` | `V1__baseline.sql` 的 sha256 与冻结清单不一致（改写）或清单记录缺失（删除条目）即红 |
| repair 守卫 | `scripts/deploy/assert-no-migration-repair.sh` | `.github/workflows` 与 `scripts` 中出现 Flyway 的 repair goal / 子命令即红 |

- 目标版本由 `verify-flyway-target.sh` 解析（当前 186），新增迁移后无需改 workflow；
  生产部署流程显式传入部署变量 `FLYWAY_TARGET`。
- `flyway info` 输出与断言证据作为 `flyway-migration-evidence` artifact 留档，故障复盘不必重连库。
- 门禁自带负例自检 `scripts/deploy/test/assert-flyway-history-selftest.sh`（1 正例 + 6 负例，
  用 `(VALUES …) AS h(…)` 合成历史，纯只读、不建表不写库）；CI 每次执行，防止门禁“假绿”。
- 本地复现（对开发库只读）：
  ```bash
  source scripts/env/dev.sh
  export PGHOST="$SPRING_DATASOURCE_HOST" PGPORT="$SPRING_DATASOURCE_PORT" \
         PGDATABASE="$SPRING_DATASOURCE_DB" PGUSER="$SPRING_DATASOURCE_USERNAME" \
         PGPASSWORD="$SPRING_DATASOURCE_PASSWORD"
  bash scripts/deploy/assert-flyway-history.sh --target "$(bash scripts/deploy/verify-flyway-target.sh .)"
  bash scripts/deploy/assert-flyway-baseline-frozen.sh .
  bash scripts/deploy/assert-no-migration-repair.sh
  ```
- 明确边界：**不执行 flyway repair**（它会改写 `flyway_schema_history`，一旦进入流水线就会把
  “迁移对不上”静默抹平）；**不自行计算 md5/CRC 去比 checksum**（checksum 算法属 Flyway 实现细节，
  一致性交给 `flyway:validate`，门禁只断言 checksum 非空）；本门禁也**不是 drift 检测**
  （drift 属 Flyway Enterprise 功能）。
