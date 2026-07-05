# Flyway 双线迁移与打印模板存放方案

## 结论

打印模板不应继续作为大段正文塞进生产主线 Flyway 基线。后续采用两层治理：

- 生产主线 `db/migration`：管理 schema、约束、索引、视图和生产必须系统 seed。
- 可选 seed 线 `db/seed`：管理可重复初始化的标准业务配置元数据，例如内置打印模板元数据。
- 模板正文：放 classpath 文件或对象存储，数据库只保存运行索引、版本、校验和和当前缓存。

Flyway 可以配置独立前缀，但单个 Flyway 实例只有一个 versioned migration 前缀。若要同时使用 `V` 主线和 `S` seed 线，必须使用两个 Flyway 实例，或在 CI/运维脚本里执行两次 Flyway 命令，并使用不同 `locations` 与不同 history table。

## 背景

当前 `V1__baseline.sql` 已重建为新的生产主线基线。旧 `V1..V209` 中混杂了打印模板导入、字段修复、旧版副本、测试模板和多次标题修正。继续把模板正文写入主线迁移会带来几个问题：

- 初始化脚本审查困难，模板业务内容掩盖 schema 变化。
- 开发库的旧版、副本、测试模板容易污染生产初始化。
- 线上用户编辑模板后，后续 Flyway DML 可能覆盖业务配置。
- 模板正文更适合用文件 diff、对象版本或业务版本表管理，不适合作为 schema 生命周期的一部分。

## 当前状态

开发库 `sys_print_template` 导出位置：

```text
/instance/s3/steelx-print-templates-20260705021201
```

导出统计：

```text
total=18
active=16
deleted=2
coord=16
pdf_form=2
```

当前代码已经具备文件托管雏形：

- 表字段：`sync_mode`、`source_ref`、`source_checksum`。
- 后端同步器：`PrintTemplateFileSyncRunner`。
- 文件约束：`source_ref` 必须位于 `print-forms/` 且以 `.layout.json` 结尾。
- 现有 classpath 目录：`src/main/resources/print-forms/`。

因此长期方向应沿用 `FILE + source_ref + checksum + DB 缓存`，而不是恢复大段 `template_html` seed。

## Flyway 前缀规则

Flyway 支持配置不同类型迁移前缀：

- `sqlMigrationPrefix`：versioned migration，默认 `V`。
- `repeatableSqlMigrationPrefix`：repeatable migration，默认 `R`。
- `baselineMigrationPrefix`：baseline migration，默认 `B`。

但它们不是多条独立流水线：

- 单个 Flyway 实例不能同时把 `V1__*.sql` 和 `S1__*.sql` 都当 versioned migration。
- `R__*.sql` 会按 checksum 变化重复执行，适合视图、函数、可幂等重建对象，不适合用户可编辑业务模板。
- 独立生命周期的数据线应使用独立 `locations` 和独立 history table。

## 推荐目录

生产主线：

```text
src/main/resources/db/migration/
  V1__baseline.sql
  V2__next_schema_or_required_seed.sql
```

标准业务配置 seed 线：

```text
src/main/resources/db/seed/
  S1__seed_print_template_metadata.sql
```

打印模板正文：

```text
src/main/resources/print-forms/
  sales-order-yingjie-a4-remark.layout.json
  freight-bill-delivery.layout.json
```

开发或演示数据线：

```text
src/main/resources/db/dev-data/
  D1__seed_demo_customers.sql
  D2__seed_demo_orders.sql
```

`db/dev-data` 禁止在生产配置中加载。

## 推荐执行方式

### 生产主线

Spring Boot 默认 Flyway 只加载生产主线：

```yaml
spring:
  flyway:
    locations: classpath:db/migration
    table: flyway_schema_history
    placeholder-replacement: false
```

CI/CD 当前也应只验证 `src/main/resources/db/migration`，保证生产发布不依赖开发样例数据。

### seed 线

seed 线有两种落地方式。

方式一：CI/运维显式执行第二次 Flyway：

```bash
mvn -B -ntp flyway:migrate \
  -Dflyway.url="$SPRING_DATASOURCE_URL" \
  -Dflyway.user="$SPRING_DATASOURCE_USERNAME" \
  -Dflyway.password="$SPRING_DATASOURCE_PASSWORD" \
  -Dflyway.locations="filesystem:src/main/resources/db/seed" \
  -Dflyway.table="flyway_seed_history" \
  -Dflyway.sqlMigrationPrefix="S" \
  -Dflyway.baselineOnMigrate=true \
  -Dflyway.baselineVersion=0 \
  -Dflyway.cleanDisabled=true \
  -Dflyway.placeholderReplacement=false \
  -Dflyway.validateMigrationNaming=true
```

方式二：应用内新增第二个 Flyway bean，由环境变量控制是否执行：

```text
app.seed-flyway.enabled=true
app.seed-flyway.locations=classpath:db/seed
app.seed-flyway.table=flyway_seed_history
app.seed-flyway.sql-migration-prefix=S
```

短期建议优先使用方式一。它更显式，不会让应用启动时偷偷执行业务 seed；等发布流程稳定后，再考虑方式二。

## 打印模板筛选规则

从 `/instance/s3/steelx-print-templates-20260705021201` 转为标准模板时，先筛选：

- 排除 `deleted_flag=true`。
- 排除 `template_name` 或 `template_code` 明确为测试用途，例如 `test-template`。
- 排除名称包含 `旧版`、`copy`、`副本` 的模板，除非业务明确指定保留。
- 排除 `status=DISABLED` 且只是历史默认占位的模板，除非该单据类型没有任何可用模板。
- 保留 `status=ACTIVE`、字段命名已对齐当前打印运行时、业务仍需初始化的新库标准模板。

候选模板应生成一份人工审核清单，至少包含：

- `bill_type`
- `template_code`
- `template_name`
- `template_type`
- `engine`
- `status`
- `is_default`
- `source_ref`
- `source_checksum`

## 打印模板存放模型

### 内置标准模板

内置标准模板由代码仓库作为源：

- 模板正文放 `src/main/resources/print-forms/*.layout.json`。
- `sys_print_template` 保存元数据、`sync_mode='FILE'`、`source_ref`、`source_checksum`。
- 应用启动时由 `PrintTemplateFileSyncRunner` 读取 classpath 文件，校验并同步到 `template_html`。
- `template_html` 是运行缓存，不是源数据。

适用场景：

- 系统自带模板。
- 需要随版本发布审查的模板。
- 需要代码 review、diff 和可重复构建的模板。

### 业务在线编辑模板

用户在线编辑模板继续存数据库：

- `sync_mode='MANUAL'`。
- `source_ref`、`source_checksum` 可为空。
- 后续应新增模板版本表或审计日志，支持回滚和责任追踪。

适用场景：

- 客户定制模板。
- 业务人员在页面上调整格式。
- 不应被版本发布自动覆盖的模板。

### 大文件和附件资产

PDF、xlsx、图片、字体等大文件应放对象存储：

- DB 保存 object key、version id、checksum、content type、大小和业务状态。
- 对象存储开启 versioning。
- 删除使用软删除或 delete marker，避免误删无法恢复。

当前项目已经引入 AWS SDK S3，后续可复用现有对象存储能力。

## `S1__seed_print_template_metadata.sql` 原则

`S1` 只写元数据，不写大段模板正文。推荐字段策略：

- `template_html` 写空 JSON 或最小占位内容，满足 NOT NULL；启动后由文件同步器覆盖。
- `sync_mode='FILE'`。
- `source_ref='print-forms/<name>.layout.json'`。
- `source_checksum` 写资源文件 SHA-256。
- `created_by`、`created_name` 使用 `system`。
- `updated_by`、`updated_name` 使用 `flyway`。
- SQL 使用 `INSERT ... ON CONFLICT ... DO UPDATE`，避免 seed 重跑失败。

如果当前唯一索引仍以 `(bill_type, settlement_company_id, template_code)` 表达式为准，`ON CONFLICT` 需要匹配实际唯一索引；不易匹配表达式索引时，可使用 `DO $$ BEGIN ... END $$` 先查后插，保持幂等。

`S1` 禁止：

- 写入开发客户、供应商、订单、合同、发票等业务数据。
- 写入用户账号、密码、token、API key。
- 写入 `SYS_OOBE_COMPLETED`。
- 恢复 `deleted_flag=true` 模板。
- 恢复明显测试、旧版、副本模板。

## 与 `V1__baseline.sql` 的关系

长期目标是让 `V1__baseline.sql` 不再包含打印模板正文。当前 `V1` 已经生成并验证，后续处理分两步：

1. 短期：不再修改已经确认的 `V1`，通过 `S1` 或 `V2` 修正模板元数据和文件同步模式。
2. 中期：如果在生产前允许再次重建基线，可重新生成 `V1`，将打印模板正文从基线中移除，只保留表结构和生产必须系统 seed。

如果已经有任一环境执行了当前 `V1`，不要再改 `V1`，只能新增更高版本迁移或 seed 脚本修正。

## CI/CD 要求

生产发布必须：

- 执行并校验主线 `db/migration`。
- 默认不执行 `db/dev-data`。
- 是否执行 `db/seed` 必须显式配置，不能隐式混入主线。
- 执行 seed 线时使用独立 `flyway_seed_history`。

CI 建议增加一个空库验证任务：

1. 执行 `db/migration`。
2. 可选执行 `db/seed`。
3. 启动应用，确认 `PrintTemplateFileSyncRunner` 可加载所有 `sync_mode='FILE'` 模板。
4. 查询 `sys_print_template`，确认没有 `deleted_flag=true`、测试模板、旧版副本模板被初始化为标准模板。

## 后续落地清单

1. 从导出目录生成模板审核清单。
2. 选择需要保留为标准模板的记录。
3. 将保留模板正文转入 `src/main/resources/print-forms/`。
4. 计算每个文件 SHA-256。
5. 新增 `src/main/resources/db/seed/S1__seed_print_template_metadata.sql`。
6. 用临时空库验证：
   - 主线 `db/migration` 执行成功。
   - seed 线 `db/seed` 执行成功。
   - 应用启动后文件同步成功。
7. 更新 CI/CD，显式增加 seed 线验证，但生产执行由环境开关控制。

## 参考资料

- Flyway Baseline Migrations: https://documentation.red-gate.com/fd/baseline-migrations-273973336.html
- Flyway Repeatable Migrations: https://documentation.red-gate.com/fd/repeatable-migrations-273973335.html
- Flyway Multiple Instances FAQ: https://github.com/flyway/flyway/blob/main/documentation/Reference/Usage/Frequently%20Asked%20Questions.md
- Amazon S3 Versioning: https://docs.aws.amazon.com/AmazonS3/latest/userguide/Versioning.html
- Amazon S3 Object Metadata: https://docs.aws.amazon.com/AmazonS3/latest/userguide/UsingMetadata.html
- Spring Static Resources: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/static-resources.html
