# Flyway 基线重建执行记录

## 结论

当前环境确认没有已部署生产库，仅有开发库和已清空的部署目标。因此本次采用破坏性迁移历史重置方案：

- 删除 Flyway 加载目录中的旧 `V1..V209`。
- 从当前源码旧迁移链在临时空库上的最终状态生成新的 `V1__baseline.sql`。
- 将旧迁移归档到非 Flyway 加载目录，保留追溯能力。
- 后续生产主线迁移从 `V2__*.sql` 开始。
- 开发业务样例数据如需保留，后续单独放入 dev/test 数据线，不进入生产主线。

## 背景

原迁移目录 `src/main/resources/db/migration` 中共有 205 个 SQL 文件，版本号到 `V209`。历史链包含早期建表、mock/demo 数据清理、废弃模块删除、字段回填、约束修正、索引优化和打印模板修复等多类历史变更。

直接继续维护旧链的问题：

- 新环境初始化需要执行大量历史修复脚本。
- 最终 schema 很难从迁移目录直接审查。
- 历史 mock/demo 清理逻辑和当前目标模型混杂。
- dev 库历史与当前源码迁移目录已出现漂移，不适合作为新基线来源。

## 基线来源

基线不是从 dev 库直接导出，而是按以下方式生成：

1. 使用本机 PostgreSQL 管理用户创建临时源库。
2. 对临时源库执行当前源码目录中的旧 `V1..V209` 全量迁移。
3. 从临时源库导出 schema 和必要系统 seed。
4. 排除 `flyway_schema_history`。
5. 静态检查并验证新 `V1__baseline.sql` 可单独初始化空库。

## 文件结果

新的生产主线迁移目录：

```text
src/main/resources/db/migration/
  V1__baseline.sql
```

旧迁移归档目录：

```text
release/db/legacy-migrations-20260705/
  V1__baseline.sql
  ...
  V209__restore_fixed_print_title.sql
```

归档目录不应配置到 Flyway `locations`，只用于审计和追溯。

## 新基线规则

`V1__baseline.sql` 只允许包含：

- 当前应用需要的表、索引、约束、视图。
- 必要 PostgreSQL 扩展，例如 `pg_trgm`。
- 生产必须的系统 seed：
  - 菜单和操作目录。
  - 权限资源。
  - 内置角色定义，但不绑定具体用户。
  - 系统开关、编号规则、上传规则。
  - 打印模板。
  - 默认物料分类。
  - OOBE 所需的默认部门和基础规则。

`V1__baseline.sql` 禁止包含：

- `flyway_schema_history` 数据。
- 开发库业务数据。
- 用户账号、用户角色绑定。
- API key、refresh token。
- 操作日志。
- 数据库导出任务记录。
- `SYS_OOBE_COMPLETED`。
- mock/demo 订单、合同、发票、客户、供应商等业务样例数据。

## 验证结果

旧迁移链源库验证：

- 旧 `V1..V209` 全量执行成功。
- Flyway 最新版本：`209 - restore fixed print title`。
- 失败迁移数：`0`。

新基线空库验证：

- 仅使用 `src/main/resources/db/migration/V1__baseline.sql` 执行成功。
- `flyway:validate` 成功。
- Flyway 历史：`1 | 1 | baseline | true`。

关键对象计数：

```text
tables=63
views=1
indexes=338
constraints=927
sys_user=0
sys_company_setting=0
sys_role=5
sys_menu=49
sys_menu_action=198
sys_role_permission=368
sys_no_rule=48
sys_print_template=17
md_material_category=3
SYS_OOBE_COMPLETED=0
```

说明：

- 新库不会跳过 OOBE。
- 新库没有管理员用户和公司主体，需要通过 `/setup` 首次初始化。
- `pg_stat_statements` 是可选诊断扩展，不强制写入基线；应用代码已支持不可用时降级。

## 后续迁移规则

- 后续生产 schema 或生产必须 seed 变更从 `V2__*.sql` 开始。
- 禁止再修改 `V1__baseline.sql`；发现问题时用新的 `V2+` 迁移修复。
- 生产 workflow 只加载 `classpath:db/migration` 或对应源码主线目录。
- dev/test/demo 数据不进入 `src/main/resources/db/migration`。

## dev/test 数据线建议

如需保留开发业务样例数据，新增独立目录：

```text
db/dev-data/
  V2026070501__seed_dev_customers.sql
  V2026070502__seed_dev_orders.sql
```

执行策略：

- 先执行生产主线迁移。
- 再按需执行 dev/test 数据线。
- dev/test 数据线使用独立 history table，例如 `flyway_dev_data_history`。
- 生产环境不配置 `db/dev-data`。

示例：

```bash
mvn -B -ntp flyway:migrate \
  -Dflyway.url="jdbc:postgresql://<host>:<port>/<dev_db>" \
  -Dflyway.user="<user>" \
  -Dflyway.password="<password>" \
  -Dflyway.locations="filesystem:db/dev-data" \
  -Dflyway.table="flyway_dev_data_history" \
  -Dflyway.cleanDisabled=true \
  -Dflyway.placeholderReplacement=false \
  -Dflyway.validateMigrationNaming=true
```

dev/test 数据线禁止写入真实密码、token、API key、`SYS_OOBE_COMPLETED` 或任何生产发布必须依赖的数据。

## seed 线与打印模板

打印模板后续不再建议把正文放入生产主线 Flyway。标准模板应采用“文件为源、数据库为元数据和运行缓存”的方式：

- 模板正文放入 `src/main/resources/print-forms/`。
- `sys_print_template` 只保存元数据、`sync_mode='FILE'`、`source_ref`、`source_checksum`。
- 标准模板元数据可放入独立 seed 线，例如 `src/main/resources/db/seed/S1__seed_print_template_metadata.sql`。
- seed 线必须使用独立 Flyway history table，例如 `flyway_seed_history`，不能混入生产主线 `flyway_schema_history`。

详细设计见 `docs/specs/2026-07-05-flyway-dual-track-and-print-template-storage.md`。

## 发布注意事项

- 本次迁移历史重置只适用于空库或已确认可清库环境。
- 如果未来出现已执行旧 `V1..V209` 的环境，不能直接使用当前主线无损升级；需要单独制定迁移历史修复或清库重建方案。
- 当前 `/instance/steelx` 已清库，适合使用新 `V1__baseline.sql` 初始化。
- 后端发布前仍需恢复 `/instance/steelx/shared/steelx.env`，否则部署 workflow 会在环境检查阶段失败。
