# 迁移演练与对账校验清单（Flyway V133–V138）

## 文档状态

- 制定日期：2026-09-14
- 适用仓库：后端 `leo`（PostgreSQL / Flyway）
- 覆盖迁移：V133、V134、V135、V136、V137、V138
- 运行形态：生产**单人使用**，一次性迁移，此前**从未在真实 PostgreSQL 执行过** V133–V138
- 本文性质：演练与上线操作清单，**只做文档，不改代码、不改脚本**

> 重要前提：V138 `inv_balance`（库存余额快照表）由并行任务实现，本清单按其命名描述。
> 第 3.3 节对账 SQL 使用维度列 `material_id / warehouse_id / batch_no` 与度量列 `quantity / amount`；
> **正式演练前必须以 `V138__*.sql` 实际列名核对，列名不同则相应替换**。

SQL 约定：
- “预检/违规”类查询以**返回 0 行**为通过；“统计”类查询给出期望值。
- 所有查询默认在 `psql` 下执行；`\timing on` 便于评估锁时长。
- 校验只读，不写入；任何持久化变更都必须走 Flyway。

---

## 1. 演练前置

### 1.1 环境与版本确认

```sql
-- 当前迁移版本与最近应用记录（迁移前应看不到 V133–V138）
SELECT installed_rank, version, description, success, installed_on, execution_time
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 10;

-- 目标新表迁移前必须全部不存在（期望全部为空）
SELECT to_regclass('public.md_material_history') AS v133,
       to_regclass('public.so_sales_return')      AS v134_head,
       to_regclass('public.so_sales_return_item') AS v134_item,
       to_regclass('public.inv_transaction')      AS v136,
       to_regclass('public.inv_balance')          AS v138;

-- 版本一致性：生产 target 必须等于代码库最新脚本版本（V138 落地后为 138）
-- 命令：bash scripts/deploy/verify-flyway-target.sh /home/instance/Gemini/leo 138
```

### 1.2 全库备份 + WAL 归档

```bash
# ---- 连接约定，按实际环境导出 ----
export PGHOST=127.0.0.1 PGPORT=5432 PGUSER=leo PGDATABASE=prod
export STAMP=$(date -u +%Y%m%dT%H%M%SZ)
export BACKUP_DIR=/var/backups/leo
mkdir -p "$BACKUP_DIR"

# 1) 逻辑全库备份（custom 格式：可并行、可按表恢复、可校验）
pg_dump -Fc -Z6 --no-owner --no-acl -f "$BACKUP_DIR/prod-$STAMP.dump"
sha256sum "$BACKUP_DIR/prod-$STAMP.dump" | tee "$BACKUP_DIR/prod-$STAMP.dump.sha256"

# 2) 迁移前结构基线，便于迁移后 diff 新增对象
pg_dump --schema-only --no-owner --no-acl -f "$BACKUP_DIR/prod-$STAMP.schema.sql"

# 3) 记录恢复起点（PITR / 副本一致性核对）
psql -Atc "SELECT now(), pg_current_wal_lsn();" | tee "$BACKUP_DIR/prod-$STAMP.wal-start.txt"
psql -Atc "SELECT pg_control_checkpoint();"     | tee "$BACKUP_DIR/prod-$STAMP.checkpoint.txt"
```

```sql
-- 确认 WAL 归档已开启（否则物理 PITR 不可用，逻辑备份成为唯一回滚手段）
SHOW wal_level;            -- 期望 replica 或 logical
SHOW archive_mode;         -- 期望 on
SHOW archive_command;
SELECT * FROM pg_stat_archiver;   -- last_archived_time 应在推进、failed_count=0
```

> 若 `archive_mode=off`：在生产窗口内不具备物理 PITR，回滚只能依赖 1.2 的逻辑备份整库恢复；
> 需在演练报告中明确标注该限制，正式迁移前不要在未评估的情况下临时改 `archive_mode`（需重启）。

### 1.3 副本恢复

```bash
# 在副本实例（可独立 PG 或同一实例的不同库）恢复备份
dropdb --if-exists leo_rehearsal
createdb leo_rehearsal
pg_restore -d leo_rehearsal --no-owner --no-acl --clean --if-exists \
  "$BACKUP_DIR/prod-$STAMP.dump"

# 记录行数基线，迁移后逐表比对
psql -d leo_rehearsal -Atc "SELECT count(*) FROM flyway_schema_history;"
psql -d leo_rehearsal -Atc "SELECT count(*) FROM so_sales_order;"
psql -d leo_rehearsal -Atc "SELECT count(*) FROM so_sales_outbound;"
psql -d leo_rehearsal -Atc "SELECT count(*) FROM po_purchase_inbound;"
psql -d leo_rehearsal -Atc "SELECT count(*) FROM st_customer_statement;"
```

> 全部演练（V133–V138 执行 + 第 3 节对账）先在 `leo_rehearsal` 跑通、记录耗时与 0 错误后，才允许在生产执行。

### 1.4 flyway:info / validate

生产使用 Spring Boot 内置 Flyway（`ddl-auto: none`，迁移脚本目录 `classpath:db/migration`），
生产环境 `spring.flyway.target` 由 `SPRING_FLYWAY_TARGET` 显式指定，禁止自动 latest。

```bash
cd /home/instance/Gemini/leo
MVN_FLYWAY="mvn -q org.flywaydb:flyway-maven-plugin:11.20.3"

# 信息与校验（先对副本）
$MVN_FLYWAY:info \
  -Dflyway.url="jdbc:postgresql://127.0.0.1:5432/leo_rehearsal" \
  -Dflyway.user=leo -Dflyway.password="$PGPASSWORD" \
  -Dflyway.locations=filesystem:src/main/resources/db/migration

$MVN_FLYWAY:validate \
  -Dflyway.url="jdbc:postgresql://127.0.0.1:5432/leo_rehearsal" \
  -Dflyway.user=leo -Dflyway.password="$PGPASSWORD" \
  -Dflyway.locations=filesystem:src/main/resources/db/migration
```

> 若插件报 `Unsupported Database: PostgreSQL`，说明插件 classpath 缺 `flyway-database-postgresql`；
> 改用 Flyway CLI，或直接以副本库启动新版本应用由 Spring Boot 执行迁移（推荐演练方式，等价于生产真实路径）。

### 1.5 预检 SQL（迁移前必须全绿）

以下针对**既有表**，生产与副本均执行。

**(P1) 非法状态值**（返回 0 行）

```sql
SELECT 'so_sales_order' AS tbl, status AS bad_value, count(*) AS rows
FROM so_sales_order
WHERE deleted_flag = false
  AND status NOT IN ('草稿','已审核','交付核定','完成销售')
GROUP BY status
UNION ALL
SELECT 'so_sales_outbound', status, count(*) FROM so_sales_outbound
WHERE deleted_flag = false AND status NOT IN ('草稿','已审核') GROUP BY status
UNION ALL
SELECT 'po_purchase_inbound', status, count(*) FROM po_purchase_inbound
WHERE deleted_flag = false AND status NOT IN ('草稿','已审核','完成入库') GROUP BY status
UNION ALL
SELECT 'lg_freight_bill', status, count(*) FROM lg_freight_bill
WHERE deleted_flag = false AND status NOT IN ('草稿','已审核') GROUP BY status
UNION ALL
SELECT 'st_customer_statement', status, count(*) FROM st_customer_statement
WHERE deleted_flag = false AND status NOT IN ('待确认','已确认') GROUP BY status;
```

**(P2) 孤儿外键**（返回 0 行）

```sql
SELECT 'so_sales_outbound_item.source_sales_order_item_id' AS ref, count(*) AS orphans
FROM so_sales_outbound_item i
LEFT JOIN so_sales_order_item oi ON oi.id = i.source_sales_order_item_id
WHERE i.source_sales_order_item_id IS NOT NULL AND oi.id IS NULL
UNION ALL
SELECT 'po_purchase_inbound_item.material_id', count(*)
FROM po_purchase_inbound_item i
WHERE i.material_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM md_material m WHERE m.id = i.material_id)
UNION ALL
SELECT 'po_purchase_inbound_item.warehouse_id', count(*)
FROM po_purchase_inbound_item i
WHERE i.warehouse_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM md_warehouse w WHERE w.id = i.warehouse_id)
UNION ALL
SELECT 'so_sales_outbound_item.material_id', count(*)
FROM so_sales_outbound_item i
WHERE i.material_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM md_material m WHERE m.id = i.material_id)
UNION ALL
SELECT 'so_sales_outbound_item.warehouse_id', count(*)
FROM so_sales_outbound_item i
WHERE i.warehouse_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM md_warehouse w WHERE w.id = i.warehouse_id)
UNION ALL
SELECT 'st_customer_statement_item.source_sales_order_item_id', count(*)
FROM st_customer_statement_item i
WHERE i.source_sales_order_item_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM so_sales_order_item oi WHERE oi.id = i.source_sales_order_item_id);
```

**(P3) 重复单号 / 空必填单号**（返回 0 行）

```sql
SELECT 'so_sales_order.order_no' AS key_name, order_no AS value, count(*) AS cnt
FROM so_sales_order GROUP BY order_no HAVING count(*) > 1
UNION ALL
SELECT 'so_sales_outbound.outbound_no', outbound_no, count(*)
FROM so_sales_outbound GROUP BY outbound_no HAVING count(*) > 1
UNION ALL
SELECT 'po_purchase_inbound.inbound_no', inbound_no, count(*)
FROM po_purchase_inbound GROUP BY inbound_no HAVING count(*) > 1
UNION ALL
SELECT 'lg_freight_bill.bill_no', bill_no, count(*)
FROM lg_freight_bill GROUP BY bill_no HAVING count(*) > 1
UNION ALL
SELECT 'st_customer_statement.statement_no', statement_no, count(*)
FROM st_customer_statement GROUP BY statement_no HAVING count(*) > 1;

SELECT 'so_sales_order.order_no' AS key_name, count(*) AS blank
FROM so_sales_order WHERE order_no IS NULL OR btrim(order_no) = ''
UNION ALL
SELECT 'so_sales_outbound.outbound_no', count(*)
FROM so_sales_outbound WHERE outbound_no IS NULL OR btrim(outbound_no) = ''
UNION ALL
SELECT 'po_purchase_inbound.inbound_no', count(*)
FROM po_purchase_inbound WHERE inbound_no IS NULL OR btrim(inbound_no) = ''
UNION ALL
SELECT 'st_customer_statement.statement_no', count(*)
FROM st_customer_statement WHERE statement_no IS NULL OR btrim(statement_no) = '';
```

**(P4) 数量异常 / 库存维度空值**（返回 0 行）

```sql
SELECT 'so_sales_outbound_item.quantity<=0' AS check_name, count(*)
FROM so_sales_outbound_item WHERE quantity <= 0
UNION ALL
SELECT 'po_purchase_inbound_item.quantity<=0', count(*)
FROM po_purchase_inbound_item WHERE quantity <= 0
UNION ALL
SELECT 'so_sales_outbound_item.warehouse_id IS NULL', count(*)
FROM so_sales_outbound_item WHERE warehouse_id IS NULL
UNION ALL
SELECT 'po_purchase_inbound_item.warehouse_id IS NULL', count(*)
FROM po_purchase_inbound_item WHERE warehouse_id IS NULL;
```

**(P5) 历史超发 / 超退近似检测**

退货表迁移前尚不存在，真正的“累计退货 ≤ 已出库”在迁移后（第 3.1 节）校验。
迁移前先检测历史是否存在已审核出库累计超过订单量的“超发”，它会直接破坏后续派生数量与库存守恒：

```sql
SELECT oi.id AS order_item_id, oi.quantity AS ordered_qty,
       COALESCE(x.out_qty, 0) AS audited_outbound_qty,
       COALESCE(x.out_qty, 0) - oi.quantity AS exceeded_qty
FROM so_sales_order_item oi
LEFT JOIN (
    SELECT bi.source_sales_order_item_id AS oi_id, SUM(bi.quantity) AS out_qty
    FROM so_sales_outbound_item bi
    JOIN so_sales_outbound b ON b.id = bi.outbound_id
    WHERE b.deleted_flag = false AND b.status = '已审核'
    GROUP BY bi.source_sales_order_item_id
) x ON x.oi_id = oi.id
WHERE COALESCE(x.out_qty, 0) > oi.quantity;
```

期望 0 行；若非 0，必须在演练报告中列出并评估是否属历史合法 granfathered 数据。

**(P6) V135 金额约束预检**（红字负数是否会被既有 CHECK 拒绝）

```sql
SELECT conname, pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE conrelid = 'public.st_customer_statement'::regclass
  AND contype = 'c'
ORDER BY conname;
```

期望仅有 `chk_customer_stmt_status`（金额列无非负 CHECK）。若出现金额非负约束，需先安排放开，否则红字（负金额）写入会失败。

---

## 2. 逐迁移核对

> 通用结论：PostgreSQL 的 DDL 是事务性的，Flyway 每个版本脚本在单事务内执行；
> **脚本中途失败会整体回滚且不写入 `flyway_schema_history`**，修正后重跑会从该版本重新执行，因此单脚本层面“可重入”。
> 但脚本本身未使用 `IF NOT EXISTS`，**不接受手工对同一对象重复执行**。

### V133 `add_material_history`（商品主数据历史表）

- **目的**：新增商品主数据不可变版本历史，支持审计与按导入批次回滚；不改动 `md_material` 结构与历史数据。
- **新增对象**：表 `md_material_history`；主键；外键 `fk_md_material_history_material`；CHECK `chk_md_material_history_source`（`MANUAL/IMPORT/ROLLBACK`）、`chk_md_material_history_type`（`CREATED/UPDATED/DELETED/ROLLBACK`）；索引 `idx_md_material_history_material_id`、`idx_md_material_history_batch_no`、`idx_md_material_history_created_at`。
- **预检**：`SELECT to_regclass('public.md_material_history');` 期望为空；`md_material(id)` 主键存在。
- **迁移后校验**：

```sql
SELECT to_regclass('public.md_material_history') IS NOT NULL AS table_exists;
SELECT count(*) AS index_count
FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'md_material_history';   -- 期望 4
SELECT conname, contype, convalidated
FROM pg_constraint WHERE conrelid = 'public.md_material_history'::regclass ORDER BY conname;

-- 可选：约束行为冒烟（事务回滚，不落库）
BEGIN;
INSERT INTO md_material_history(id, material_id, change_source, change_type)
VALUES (1, -1, 'MANUAL', 'CREATED');   -- 期望外键报错
ROLLBACK;
```

- **失败回滚方式**：脚本失败自动回滚（事务性 DDL），无需人工补偿；已成功后又需撤销时 `DROP TABLE md_material_history;`。
- **是否阻塞写**：否。仅创建新表与索引，不锁既有表。
- **幂等/可重入**：Flyway 层面幂等（成功即跳过）；脚本本身非幂等。

### V134 `add_sales_return`（销售退货单头/明细）

- **目的**：新增反向出库单据 `so_sales_return` / `so_sales_return_item`，明细锚定原销售出库明细，不改写出库/订单/物流单。
- **新增对象**：两张新表；头表唯一 `uk_so_sales_return_no`、状态 CHECK（`草稿/已审核`）、外键到客户/项目/仓库；明细表外键到退货头、出库明细、订单明细、物流单、商品、仓库，CHECK `quantity >= 0`，生成列 `batch_no_normalized`；明细索引若干。
- **预检**：`to_regclass` 两表为空；`so_sales_outbound_item(id)`、`so_sales_order_item(id)`、`lg_freight_bill(id)`、`md_customer/md_project/md_warehouse(id)` 主键存在；P2/P4 已全绿。
- **迁移后校验**：

```sql
SELECT to_regclass('public.so_sales_return') IS NOT NULL      AS head_ok,
       to_regclass('public.so_sales_return_item') IS NOT NULL AS item_ok;

SELECT count(*) AS index_count
FROM pg_indexes
WHERE schemaname = 'public' AND tablename IN ('so_sales_return','so_sales_return_item');  -- 期望 11

SELECT conrelid::regclass AS tbl, conname
FROM pg_constraint
WHERE conrelid IN ('public.so_sales_return'::regclass, 'public.so_sales_return_item'::regclass)
  AND NOT convalidated;   -- 期望 0 行

SELECT column_name, is_generated
FROM information_schema.columns
WHERE table_name = 'so_sales_return_item' AND column_name = 'batch_no_normalized';   -- is_generated = ALWAYS
```

- **失败回滚方式**：自动回滚；已成功后撤销 `DROP TABLE so_sales_return_item, so_sales_return;`（先删有外键的明细）。
- **是否阻塞写**：否，新表为空，索引创建瞬时。
- **幂等/可重入**：Flyway 幂等；脚本非幂等。

### V135 `add_customer_statement_direction`（对账方向 + 来源退货单）

- **目的**：`st_customer_statement` 增加 `direction`（默认蓝字）、`source_sales_return_id`、`source_sales_return_no`；支持红字冲销。
- **新增对象**：3 个新列；CHECK `chk_customer_stmt_direction`；索引 `idx_st_customer_statement_source_return`；部分唯一索引 `uk_st_customer_statement_source_return_active`（同一退货单仅一条有效红字）。
- **预检**：P6 确认金额无非负 CHECK；`st_customer_statement` 无同名列。
- **迁移后校验**：

```sql
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_name = 'st_customer_statement'
  AND column_name IN ('direction','source_sales_return_id','source_sales_return_no')
ORDER BY column_name;
-- direction: NOT NULL, default '蓝字'::character varying
-- source_sales_return_id / source_sales_return_no: nullable

SELECT count(*) FILTER (WHERE direction IS NULL) AS null_direction,
       count(*) FILTER (WHERE direction = '蓝字') AS blue_rows,
       count(*) FILTER (WHERE direction = '红字') AS red_rows,
       count(*) AS total
FROM st_customer_statement;
-- 期望：null_direction = 0，blue_rows = total（历史全部回填蓝字），red_rows = 0

SELECT indexname FROM pg_indexes
WHERE tablename = 'st_customer_statement'
  AND indexname IN ('idx_st_customer_statement_source_return',
                    'uk_st_customer_statement_source_return_active');
```

- **失败回滚方式**（**仅在尚无红字对账单时可逆**）：

```sql
DROP INDEX IF EXISTS uk_st_customer_statement_source_return_active;
DROP INDEX IF EXISTS idx_st_customer_statement_source_return;
ALTER TABLE st_customer_statement DROP CONSTRAINT IF EXISTS chk_customer_stmt_direction;
ALTER TABLE st_customer_statement DROP COLUMN IF EXISTS source_sales_return_no;
ALTER TABLE st_customer_statement DROP COLUMN IF EXISTS source_sales_return_id;
ALTER TABLE st_customer_statement DROP COLUMN IF EXISTS direction;
```

> 若已产生红字对账单/收款核销，撤销三列将丢失数据，**只能整库恢复**。

- **是否阻塞写**：短时 `ACCESS EXCLUSIVE`。
  - 加 `NOT NULL DEFAULT` 列在 PostgreSQL 11+ 为元数据操作，不重写表；
  - `ADD CONSTRAINT ... CHECK`（未用 `NOT VALID`）会扫描全表校验；
  - 两个 `CREATE INDEX` 为非并发创建，持 `SHARE` 锁阻塞该表写。
  单人环境建议低峰执行，`\timing` 记录耗时。
- **幂等/可重入**：Flyway 幂等；脚本非幂等（列/约束已存在会失败）。

### V136 `add_inventory_transaction`（库存事务账本）

- **目的**：新增不可变库存事务账本，由采购入库/销售出库/销售退货审核与反审核驱动，只增/软删不改数量；余额查询按聚合。
- **新增对象**：表 `inv_transaction`；唯一 `uk_inv_transaction_no`；CHECK `direction IN (1,-1)`、`quantity > 0`、`transaction_type` 六值、`source_document_type` 三值；部分唯一索引 `uk_inv_transaction_source_active`（`source_document_type, source_item_id, transaction_type WHERE deleted_flag=false`）；索引 `idx_inv_transaction_material_warehouse`、`idx_inv_transaction_source_item`、`idx_inv_transaction_occurred_at`。
- **预检**：`to_regclass('public.inv_transaction')` 为空；来源明细表主键存在。
- **迁移后校验**：

```sql
SELECT to_regclass('public.inv_transaction') IS NOT NULL AS table_exists;
SELECT conname, contype, convalidated
FROM pg_constraint WHERE conrelid = 'public.inv_transaction'::regclass ORDER BY conname;
SELECT indexname, indexdef
FROM pg_indexes WHERE tablename = 'inv_transaction' ORDER BY indexname;
-- 重点确认 uk_inv_transaction_source_active 为部分唯一索引（含 WHERE deleted_flag = false）
```

- **失败回滚方式**：自动回滚；已成功后 `DROP TABLE inv_transaction;`。
- **是否阻塞写**：否，新表为空。
- **幂等/可重入**：Flyway 幂等；脚本非幂等。

### V137 `add_inventory_and_statement_indexes`（性能补索引）

- **目的**：库存事务按来源单据维度、对账明细按来源订单明细的查询索引。
- **新增对象**：部分索引 `idx_inv_transaction_source_document (source_document_type, source_document_id) WHERE deleted_flag=false`；部分索引 `idx_st_customer_statement_item_source_order_item (source_sales_order_item_id) WHERE source_sales_order_item_id IS NOT NULL`。
- **预检**：`inv_transaction`、`st_customer_statement_item` 存在；无同名索引。
- **迁移后校验**：

```sql
SELECT indexname, indexdef
FROM pg_indexes
WHERE indexname IN ('idx_inv_transaction_source_document',
                    'idx_st_customer_statement_item_source_order_item');
-- 期望 2 行，且 indexdef 含 WHERE 条件
```

- **失败回滚方式**：自动回滚；已成功后 `DROP INDEX idx_inv_transaction_source_document;`、`DROP INDEX idx_st_customer_statement_item_source_order_item;`。
- **是否阻塞写**：`CREATE INDEX`（非并发）在 `st_customer_statement_item` 上持 `SHARE` 锁，扫描期间阻塞写入；单人低峰执行。
- **幂等/可重入**：Flyway 幂等；脚本非幂等（无 `IF NOT EXISTS`）。

### V138 `add_inventory_balance`（库存余额快照表 `inv_balance`）

- **目的**（按并行任务命名）：新增库存余额快照表，缓存按 `(material_id, warehouse_id, batch_no)` 聚合的库存数量与金额，作为 `inv_transaction` 的物化视图/快照，供余额查询与库存守恒对账。
- **新增对象（以实际 `V138__*.sql` 为准）**：表 `inv_balance`；维度列 `material_id / warehouse_id / batch_no`；度量列 `quantity / amount`；审计列；对维度列的唯一约束或唯一索引；可能的 `CHECK (quantity >= 0)`。
- **预检**：`to_regclass('public.inv_balance')` 为空；`inv_transaction` 已存在。
- **迁移后校验**：

```sql
SELECT to_regclass('public.inv_balance') IS NOT NULL AS table_exists;
SELECT conname, contype, convalidated
FROM pg_constraint WHERE conrelid = 'public.inv_balance'::regclass ORDER BY conname;
SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'inv_balance';
-- 确认维度列上的唯一索引存在，且不存在未校验（convalidated=false）的约束

-- 迁移当时应为空表（余额由回填/记账产生）
SELECT count(*) AS balance_rows FROM inv_balance;
```

- **失败回滚方式**：自动回滚；已成功后 `DROP TABLE inv_balance;`。
- **是否阻塞写**：否，新表为空。
- **幂等/可重入**：Flyway 幂等；脚本非幂等。

---

## 3. 迁移后对账校验（可直接执行 SQL）

在**迁移完成、期初回填尚未执行时**先跑一遍（此时退货/库存事务为空，主要验证结构与空集守恒）；
在**期初回填执行后再跑一遍**（这是核心验收）。

### 3.1 数量守恒

**(a) 总量视图：订单量 = 已出库 + 未出库**

```sql
WITH ordered AS (
    SELECT oi.id AS order_item_id, oi.quantity::numeric AS ordered_qty
    FROM so_sales_order_item oi
    JOIN so_sales_order o ON o.id = oi.order_id AND o.deleted_flag = false
),
outbound AS (
    SELECT bi.source_sales_order_item_id AS order_item_id, SUM(bi.quantity)::numeric AS out_qty
    FROM so_sales_outbound_item bi
    JOIN so_sales_outbound b ON b.id = bi.outbound_id
    WHERE b.deleted_flag = false AND b.status = '已审核'
    GROUP BY bi.source_sales_order_item_id
)
SELECT COALESCE(SUM(a.ordered_qty), 0)                                  AS total_ordered,
       COALESCE(SUM(b.out_qty), 0)                                      AS total_outbound,
       COALESCE(SUM(a.ordered_qty), 0) - COALESCE(SUM(b.out_qty), 0)    AS total_pending
FROM ordered a
LEFT JOIN outbound b ON b.order_item_id = a.order_item_id;
```

**违规：已审核出库累计超过订单量**（返回 0 行）

```sql
SELECT oi.id AS order_item_id, oi.quantity AS ordered_qty, x.out_qty AS outbound_qty
FROM so_sales_order_item oi
JOIN (
    SELECT bi.source_sales_order_item_id AS oi_id, SUM(bi.quantity) AS out_qty
    FROM so_sales_outbound_item bi
    JOIN so_sales_outbound b ON b.id = bi.outbound_id
    WHERE b.deleted_flag = false AND b.status = '已审核'
    GROUP BY bi.source_sales_order_item_id
) x ON x.oi_id = oi.id
WHERE x.out_qty > oi.quantity;
```

**(b) 累计退货 ≤ 已出库**

```sql
WITH returned AS (
    SELECT i.source_sales_outbound_item_id AS outbound_item_id,
           SUM(i.quantity)::numeric AS return_qty
    FROM so_sales_return_item i
    JOIN so_sales_return h ON h.id = i.return_id
    WHERE h.deleted_flag = false AND h.status = '已审核'
    GROUP BY i.source_sales_outbound_item_id
)
SELECT r.outbound_item_id, r.return_qty, bi.quantity AS outbound_qty
FROM returned r
JOIN so_sales_outbound_item bi ON bi.id = r.outbound_item_id
WHERE r.return_qty > bi.quantity;   -- 期望 0 行
```

```sql
-- 汇总断言：累计退货量 <= 累计已审核出库量
SELECT
  (SELECT COALESCE(SUM(i.quantity), 0)
   FROM so_sales_return_item i
   JOIN so_sales_return h ON h.id = i.return_id
   WHERE h.deleted_flag = false AND h.status = '已审核')                 AS returned_qty,
  (SELECT COALESCE(SUM(bi.quantity), 0)
   FROM so_sales_outbound_item bi
   JOIN so_sales_outbound b ON b.id = bi.outbound_id
   WHERE b.deleted_flag = false AND b.status = '已审核')                 AS outbound_qty;
-- 断言：returned_qty <= outbound_qty
```

### 3.2 金额守恒

**(a) 蓝字 − 红字 = 应收净额**（红字金额为负，故净额 = `SUM(sales_amount)`）

```sql
SELECT
    COALESCE(SUM(CASE WHEN direction = '蓝字' THEN sales_amount ELSE 0 END), 0) AS blue_sales_amount,
    COALESCE(SUM(CASE WHEN direction = '红字' THEN sales_amount ELSE 0 END), 0)  AS red_sales_amount,
    COALESCE(SUM(sales_amount), 0)                                               AS net_receivable
FROM st_customer_statement
WHERE deleted_flag = false;
-- 断言：net_receivable = blue_sales_amount + red_sales_amount（red 为负）
-- 即 蓝字 − |红字| = 应收净额
```

**(b) 红字对账单自洽**（返回 0 行）

```sql
SELECT id, statement_no, direction, sales_amount, receipt_amount, closing_amount, source_sales_return_id
FROM st_customer_statement
WHERE deleted_flag = false AND direction = '红字'
  AND (sales_amount >= 0
       OR receipt_amount <> 0
       OR closing_amount <> sales_amount
       OR source_sales_return_id IS NULL);
```

**(c) 蓝字对账单自洽**（返回 0 行）

```sql
SELECT id, statement_no, sales_amount, receipt_amount
FROM st_customer_statement
WHERE deleted_flag = false AND direction = '蓝字'
  AND (sales_amount < 0 OR receipt_amount < 0 OR receipt_amount > sales_amount);
```

**(d) 头行金额一致**（返回 0 行）

```sql
SELECT h.id, h.statement_no, h.sales_amount, COALESCE(SUM(i.amount), 0) AS item_amount
FROM st_customer_statement h
LEFT JOIN st_customer_statement_item i ON i.statement_id = h.id
WHERE h.deleted_flag = false
GROUP BY h.id, h.statement_no, h.sales_amount
HAVING h.sales_amount <> COALESCE(SUM(i.amount), 0);
```

**(e) 红字 ↔ 退货单联动**（返回 0 行）

```sql
SELECT s.id, s.statement_no, s.source_sales_return_id
FROM st_customer_statement s
LEFT JOIN so_sales_return r ON r.id = s.source_sales_return_id
WHERE s.deleted_flag = false AND s.direction = '红字'
  AND (r.id IS NULL OR r.deleted_flag = true OR r.status <> '已审核');
```

**(f) 蓝/红数量对照（提示性，非强约束）**

```sql
SELECT
  (SELECT count(*) FROM so_sales_return WHERE deleted_flag = false AND status = '已审核')   AS audited_returns,
  (SELECT count(*) FROM st_customer_statement
   WHERE deleted_flag = false AND direction = '红字')                                        AS active_red_statements;
-- 红字只应在退货占用蓝字对账时生成，数量天然可能不等；用于人工核对
```

### 3.3 库存守恒

**核心断言：`inv_balance` 快照 == `inv_transaction`（未删除）聚合，跨全部维度零差异**（返回 0 行）

```sql
WITH ledger AS (
    SELECT material_id,
           warehouse_id,
           batch_no,
           SUM(quantity * direction)::numeric(20,0) AS qty,
           SUM(amount)::numeric(20,2)               AS amount
    FROM inv_transaction
    WHERE deleted_flag = false
    GROUP BY material_id, warehouse_id, batch_no
),
snapshot AS (
    SELECT material_id, warehouse_id, batch_no, quantity AS qty, amount
    FROM inv_balance
)
SELECT COALESCE(l.material_id,  s.material_id)  AS material_id,
       COALESCE(l.warehouse_id, s.warehouse_id) AS warehouse_id,
       COALESCE(l.batch_no,     s.batch_no)     AS batch_no,
       COALESCE(l.qty, 0)     AS ledger_qty,
       COALESCE(s.qty, 0)     AS snapshot_qty,
       COALESCE(l.amount, 0)  AS ledger_amount,
       COALESCE(s.amount, 0)  AS snapshot_amount
FROM ledger l
FULL OUTER JOIN snapshot s
  ON l.material_id = s.material_id
 AND l.warehouse_id IS NOT DISTINCT FROM s.warehouse_id
 AND l.batch_no     IS NOT DISTINCT FROM s.batch_no
WHERE COALESCE(l.qty, 0)    <> COALESCE(s.qty, 0)
   OR COALESCE(l.amount, 0) <> COALESCE(s.amount, 0);
```

> 若 `inv_balance` 为按需/定时刷新而非记账时同步维护，比对前必须先触发其刷新入口（以 V138/应用实现为准），
> 否则该查询会把“未刷新”误报为“不平”。刷新机制须在演练报告中明确。

**总量对照**

```sql
SELECT
  (SELECT COALESCE(SUM(quantity * direction), 0) FROM inv_transaction WHERE deleted_flag = false) AS ledger_qty,
  (SELECT COALESCE(SUM(quantity), 0)            FROM inv_balance)                                  AS snapshot_qty,
  (SELECT COALESCE(SUM(amount), 0)               FROM inv_transaction WHERE deleted_flag = false) AS ledger_amount,
  (SELECT COALESCE(SUM(amount), 0)               FROM inv_balance)                                  AS snapshot_amount;
```

**按期初期末口径：期初 + 入 − 出 − 退 = 期末**（按事务类型）

```sql
SELECT transaction_type,
       SUM(CASE WHEN direction = 1 THEN quantity ELSE -quantity END) AS net_qty,
       SUM(amount)                                                   AS net_amount
FROM inv_transaction
WHERE deleted_flag = false
GROUP BY transaction_type
ORDER BY transaction_type;
-- PURCHASE_IN / SALES_RETURN_IN 应为正；SALES_OUT 应为负
```

**账本金额自洽与非法值**（返回 0 行）

```sql
SELECT id, transaction_no
FROM inv_transaction
WHERE deleted_flag = false
  AND (quantity <= 0
       OR unit_cost < 0
       OR abs(amount - direction * quantity * unit_cost) > 0.01);
```

**非负余额检查**（按需，返回 0 行；如业务允许负库存需在报告中说明）

```sql
SELECT material_id, warehouse_id, batch_no, SUM(quantity * direction) AS qty
FROM inv_transaction
WHERE deleted_flag = false
GROUP BY material_id, warehouse_id, batch_no
HAVING SUM(quantity * direction) < 0;
```

### 3.4 外键完整性（无孤儿 `source_*_id`）

```sql
SELECT 'so_sales_return_item.source_sales_outbound_item_id' AS ref, count(*) AS orphans
FROM so_sales_return_item i
WHERE NOT EXISTS (SELECT 1 FROM so_sales_outbound_item x WHERE x.id = i.source_sales_outbound_item_id)
UNION ALL
SELECT 'so_sales_return_item.source_sales_order_item_id', count(*)
FROM so_sales_return_item i
WHERE i.source_sales_order_item_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM so_sales_order_item x WHERE x.id = i.source_sales_order_item_id)
UNION ALL
SELECT 'so_sales_return_item.source_freight_bill_id', count(*)
FROM so_sales_return_item i
WHERE i.source_freight_bill_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM lg_freight_bill x WHERE x.id = i.source_freight_bill_id)
UNION ALL
SELECT 'so_sales_return_item.material_id', count(*)
FROM so_sales_return_item i
WHERE i.material_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM md_material x WHERE x.id = i.material_id)
UNION ALL
SELECT 'so_sales_return_item.warehouse_id', count(*)
FROM so_sales_return_item i
WHERE i.warehouse_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM md_warehouse x WHERE x.id = i.warehouse_id)
UNION ALL
SELECT 'st_customer_statement.source_sales_return_id', count(*)
FROM st_customer_statement s
WHERE s.source_sales_return_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM so_sales_return r WHERE r.id = s.source_sales_return_id)
UNION ALL
SELECT 'inv_transaction.source_item (typed)', count(*)
FROM inv_transaction t
WHERE t.deleted_flag = false AND (
       (t.source_document_type = 'PURCHASE_INBOUND'
        AND NOT EXISTS (SELECT 1 FROM po_purchase_inbound_item x WHERE x.id = t.source_item_id))
    OR (t.source_document_type = 'SALES_OUTBOUND'
        AND NOT EXISTS (SELECT 1 FROM so_sales_outbound_item x WHERE x.id = t.source_item_id))
    OR (t.source_document_type = 'SALES_RETURN'
        AND NOT EXISTS (SELECT 1 FROM so_sales_return_item x WHERE x.id = t.source_item_id))
);
-- 期望 0 行
```

**库存事务来源单据存在性**（返回 0 行）

```sql
SELECT t.source_document_type, t.source_document_id, count(*) AS orphans
FROM inv_transaction t
WHERE t.deleted_flag = false AND t.source_document_id IS NOT NULL AND (
       (t.source_document_type = 'PURCHASE_INBOUND'
        AND NOT EXISTS (SELECT 1 FROM po_purchase_inbound x WHERE x.id = t.source_document_id))
    OR (t.source_document_type = 'SALES_OUTBOUND'
        AND NOT EXISTS (SELECT 1 FROM so_sales_outbound x WHERE x.id = t.source_document_id))
    OR (t.source_document_type = 'SALES_RETURN'
        AND NOT EXISTS (SELECT 1 FROM so_sales_return x WHERE x.id = t.source_document_id))
)
GROUP BY t.source_document_type, t.source_document_id;
```

### 3.5 状态合法性

```sql
SELECT 'so_sales_return.status' AS check_name, count(*) AS invalid
FROM so_sales_return WHERE deleted_flag = false AND status NOT IN ('草稿','已审核')
UNION ALL
SELECT 'st_customer_statement.direction', count(*)
FROM st_customer_statement WHERE direction NOT IN ('蓝字','红字')
UNION ALL
SELECT 'inv_transaction.direction', count(*)
FROM inv_transaction WHERE direction NOT IN (1, -1)
UNION ALL
SELECT 'inv_transaction.transaction_type', count(*)
FROM inv_transaction
WHERE transaction_type NOT IN ('PURCHASE_IN','SALES_OUT','SALES_RETURN_IN',
                               'PURCHASE_RETURN_OUT','TRANSFER','COUNT_ADJUST')
UNION ALL
SELECT 'inv_transaction.source_document_type', count(*)
FROM inv_transaction
WHERE source_document_type NOT IN ('PURCHASE_INBOUND','SALES_OUTBOUND','SALES_RETURN')
UNION ALL
SELECT 'md_material_history.change_source', count(*)
FROM md_material_history WHERE change_source NOT IN ('MANUAL','IMPORT','ROLLBACK')
UNION ALL
SELECT 'md_material_history.change_type', count(*)
FROM md_material_history WHERE change_type NOT IN ('CREATED','UPDATED','DELETED','ROLLBACK');
-- 期望 0 行
```

**所有 CHECK 约束均已校验生效**（返回 0 行）

```sql
SELECT conrelid::regclass AS tbl, conname
FROM pg_constraint
WHERE contype = 'c' AND connamespace = 'public'::regnamespace AND NOT convalidated;
```

### 3.6 主数据历史链完整性

```sql
-- (a) 历史引用物料存在（返回 0 行）
SELECT count(*) AS orphan_history
FROM md_material_history h
WHERE h.deleted_flag = false
  AND NOT EXISTS (SELECT 1 FROM md_material m WHERE m.id = h.material_id);

-- (b) 快照与变更类型匹配（返回 0 行）
SELECT id, change_type, change_source
FROM md_material_history
WHERE deleted_flag = false
  AND ((change_type = 'CREATED' AND before_snapshot IS NOT NULL)
    OR (change_type IN ('UPDATED','DELETED','ROLLBACK') AND after_snapshot IS NULL));

-- (c) IMPORT 必须有导入批次号（返回 0 行）
SELECT count(*) AS import_without_batch
FROM md_material_history
WHERE deleted_flag = false AND change_source = 'IMPORT'
  AND (import_batch_no IS NULL OR btrim(import_batch_no) = '');

-- (d) 每个物料历史链时间单调（返回 0 行）
WITH chain AS (
    SELECT id, material_id, created_at,
           lag(created_at) OVER (PARTITION BY material_id ORDER BY created_at, id) AS prev_created_at
    FROM md_material_history
    WHERE deleted_flag = false
)
SELECT material_id, count(*) AS broken
FROM chain
WHERE prev_created_at IS NOT NULL AND created_at < prev_created_at
GROUP BY material_id;

-- (e) 已删除物料的最后一条历史应为 DELETED / ROLLBACK（提示性核对）
SELECT m.id AS material_id,
       (array_agg(h.change_type ORDER BY h.created_at DESC, h.id DESC))[1] AS last_change_type,
       count(h.id) AS history_rows
FROM md_material m
LEFT JOIN md_material_history h ON h.material_id = m.id AND h.deleted_flag = false
WHERE m.deleted_flag = true
GROUP BY m.id
HAVING (array_agg(h.change_type ORDER BY h.created_at DESC, h.id DESC))[1]
       IS DISTINCT FROM 'DELETED'
   AND (array_agg(h.change_type ORDER BY h.created_at DESC, h.id DESC))[1]
       IS DISTINCT FROM 'ROLLBACK';
```

> V133 迁移刚完成时 `md_material_history` 为空，以上均 trivially 通过；
> 其真实价值在导入/审核/回滚功能启用并产生数据后才体现，应纳入回填后验收。

---

## 4. 上线步骤与窗口

前端 `aries` 无需数据库迁移；本清单只覆盖后端 `leo` 与数据库。生产单人使用，选择无业务使用时段的低峰窗口。

| 步骤 | 动作 | 关键点 |
| --- | --- | --- |
| 0 | 副本预演 | 在 `leo_rehearsal` 完整跑 V133–V138 + 第 3 节对账，记录耗时/错误 |
| 1 | 窗口确认 | 通知使用人停止录入；确认无进行中订单/出库/入库审核 |
| 2 | 备份 | 执行 1.2 全库 `pg_dump` + 校验和 + schema 基线 + WAL 位点 |
| 3 | 停服 | `sudo systemctl stop leo-backend`，确认无 `leo-erp` 连接 |
| 4 | 迁移 | 以 `SPRING_FLYWAY_TARGET=138` 启动新版本（或独立 Flyway 执行）；见下 |
| 5 | 对账校验 | 执行第 3 节全部 SQL（回填前基线） |
| 6 | 启动应用 | `sudo systemctl start leo-backend`，健康检查通过 |
| 7 | 期初回填 | `POST /api/v2.0/inventory/backfills`（幂等） |
| 8 | 回填后复核对账 | 重跑 3.1–3.3，尤其 3.3 库存守恒 |
| 9 | 人工验收 | 按第 6 节勾选，异常打印留档并由第二人复核 |

**步骤 4 具体命令**

```bash
# 4.1 确认部署 target 与代码库最新脚本一致（V138 后应为 138）
bash scripts/deploy/verify-flyway-target.sh /home/instance/Gemini/leo 138

# 4.2 确认 systemd 服务读取的 env 文件已设置显式上限
grep -n 'SPRING_FLYWAY_TARGET' /opt/leo/shared/leo.env   # 期望 = 138

# 4.3 生产发布（由 CI 触发的推荐路径）
bash scripts/deploy/trigger-production-deploy.sh \
  --confirm-production --flyway-target 138 --leo-ref main --watch
```

> 生产 `application-prod.yml` 刻意把 `spring.flyway.target` 设为 `${SPRING_FLYWAY_TARGET:}`，
> 必须由 `/opt/leo/shared/leo.env` 显式提供 `SPRING_FLYWAY_TARGET=138`，禁止依赖隐式 latest。

**步骤 7 期初回填**

```bash
BASE_URL=http://127.0.0.1:57217        # 以部署脚本 --healthcheck-url 主机端口为准
IDEMPOTENCY_KEY=$(cat /proc/sys/kernel/random/uuid)

curl -sS -X POST "$BASE_URL/api/v2.0/inventory/backfills" \
  -H "X-Idempotency-Key: $IDEMPOTENCY_KEY" \
  -H "Accept: application/json"
# 返回 201 Created，体为 InventoryBackfillResponse：
#   purchaseInCreated / salesOutCreated / salesReturnCreated / skipped
# 回填天然幂等：重复调用只累加 skipped，不重复记账
```

**步骤 8 复核对账**

```sql
-- 回填后必须重跑：3.3 库存守恒（ledger vs snapshot 零差异）
-- 并确认回填计数与来源单据量匹配：
SELECT transaction_type, count(*) AS tx_rows
FROM inv_transaction WHERE deleted_flag = false
GROUP BY transaction_type ORDER BY transaction_type;
```

---

## 5. 回滚预案

### 5.1 总原则

- **迁移失败**：因为 PostgreSQL DDL 事务性 + Flyway 单脚本单事务，失败脚本自动回滚且不写入 `flyway_schema_history`；
  修正脚本后重跑即可。**若无法快速修正，恢复 1.2 的逻辑备份整库。**
- **迁移成功后需回退**：V133–V138 全部为**纯新增表 / 新增索引 / 新增列**，可直接整库恢复备份，最安全、最可预期。
- **单人环境无并发缓冲，副本演练不可省。**

### 5.2 按迁移可逆性

| 迁移 | 可逆性 | 补偿思路（仅在确认新对象无业务数据时手工执行） |
| --- | --- | --- |
| V133 | 可逆 | `DROP TABLE IF EXISTS md_material_history;` |
| V134 | 可逆 | `DROP TABLE IF EXISTS so_sales_return_item;` 再 `DROP TABLE IF EXISTS so_sales_return;` |
| V135 | 有条件可逆 | 无红字数据时按 2·V135 的 DROP COLUMN/INDEX/CONSTRAINT 回退；有红字数据则只能整库恢复 |
| V136 | 可逆 | `DROP TABLE IF EXISTS inv_transaction;`（会丢失已记库存流水） |
| V137 | 可逆 | `DROP INDEX IF EXISTS idx_inv_transaction_source_document;` 与 `idx_st_customer_statement_item_source_order_item` |
| V138 | 可逆 | `DROP TABLE IF EXISTS inv_balance;` |

> **手工 DROP 的额外要求**：对象被 DROP 后 `flyway_schema_history` 仍记录这些版本，
> Flyway 不会重跑，且新版校验会产生漂移。手工回退后必须同时清理对应 `flyway_schema_history` 行，
> 该操作等价于重写迁移历史，风险高。**首选整库恢复，不推荐手工 DROP + 改历史。**

### 5.3 应用 JAR 回退的特别风险（务必注意）

- 生产 `spring.flyway.validate-on-migrate: true`。当数据库已执行 V133–V138 后，
  若用**旧版本 JAR**（不含这些脚本）回退，Flyway 会因“本地缺少已应用迁移”而**校验失败、启动失败**。
- 因此 **JAR 回退不能单独使用**，可选方案：
  1. **整库恢复到迁移前备份**，再部署旧 JAR（最干净）；
  2. 保持新 schema，临时放宽缺失迁移校验（如设置 `SPRING_FLYWAY_IGNORE_MIGRATION_PATTERNS=*:missing` 或等价配置）后再回退 JAR，
     待问题修复后立即正向部署回 V138；**此方式需在演练中验证过再用于生产**。
- 部署脚本 `scripts/deploy/rollback-production-release.sh` 只切换软链并做健康检查，
  **不会回退数据库**；数据库回退必须单独执行。

### 5.4 数据回滚

```bash
# 整库恢复（生产停服后）
sudo systemctl stop leo-backend
dropdb --if-exists prod && createdb prod
pg_restore -d prod --no-owner --no-acl --clean --if-exists "$BACKUP_DIR/prod-$STAMP.dump"
psql -d prod -c "SELECT max(version) FROM flyway_schema_history;"   # 应为迁移前版本
sudo systemctl start leo-backend
```

> 若生产已启用 WAL 归档，可在逻辑恢复后配合 PITR 精确回到某时间点；
> 恢复过程与目标位点须在演练副本上验证后方可用于生产。

---

## 6. 验收清单（勾选式）

### 前置与备份
- [ ] 确认代码库最新迁移版本为 V138，生产 `SPRING_FLYWAY_TARGET=138` 且与 `verify-flyway-target.sh` 一致
- [ ] 生产全库 `pg_dump -Fc` 已完成，SHA-256 校验通过，schema 基线与 WAL 位点已留档
- [ ] `archive_mode` 状态已确认（开启则记录 PITR 可用，关闭则记录仅逻辑备份可回滚）
- [ ] 副本 `leo_rehearsal` 已从备份成功恢复，行数基线已记录
- [ ] 副本上完整演练 V133–V138 通过，耗时与 0 错误已记录
- [ ] 预检 P1–P6 在生产全部通过（0 违规行），P5 超发若有则已评估

### 迁移执行
- [ ] 迁移窗口低峰，使用人已停止录入
- [ ] 应用已停服，`pg_stat_activity` 无 `application_name='leo-erp'` 连接
- [ ] `flyway:info` 显示 V133–V138 待应用（副本），`flyway:validate` 通过
- [ ] 生产迁移成功，`flyway_schema_history` 中 V133–V138 全部 `success = true`
- [ ] V133 表/索引/约束齐全，V134 两表/索引/生成列齐全
- [ ] V135 三列、方向 CHECK、两个索引存在，历史行全部为蓝字、无 NULL direction
- [ ] V136 表/CHECK/部分唯一索引齐全，V137 两个部分索引存在
- [ ] V138 `inv_balance` 表/约束/维度唯一索引存在
- [ ] 所有 CHECK 约束 `convalidated = true`

### 对账校验
- [ ] 3.1(a) 订单量 = 已出库 + 未出库，且无超发（0 违规行）
- [ ] 3.1(b) 累计退货 ≤ 已出库（0 违规行）
- [ ] 3.2(a) 蓝字 − 红字 = 应收净额可计算且自洽
- [ ] 3.2(b)(c)(d)(e) 红/蓝对账单自洽、头行金额一致、红字 ↔ 退货联动正确
- [ ] 3.3 `inv_balance` == `inv_transaction` 未删除聚合，跨全部维度 0 差异
- [ ] 3.3 按期初期末口径入/出/退方向正确，账本金额自洽，无非负余额异常
- [ ] 3.4 无孤儿 `source_*_id`，库存事务来源单据存在
- [ ] 3.5 全部状态值在允许集合内
- [ ] 3.6 主数据历史链完整（外键、快照匹配、IMPORT 批次号、时间单调）

### 应用与回填
- [ ] 应用启动成功，健康检查通过
- [ ] `POST /api/v2.0/inventory/backfills` 返回 201，计数符合预期
- [ ] 重复调用回填接口只累加 `skipped`，无重复记账
- [ ] 回填后重跑 3.3 库存守恒，仍 0 差异
- [ ] 关键页面人工验收：库存余额、库存流水、销售退货、红字对账、单据流
- [ ] 异常与耗时打印留档，已由第二人复核（单人环境以书面留档替代）

---

## 附：本次演练产生的数据与前序版本对齐

- 若迁移过程中新增了更高版本脚本，`verify-flyway-target.sh` 要求 target 等于**代码库最新版本**；
  V138 之后如再有 V139，则本清单全部 138 处需同步更新为 139。
- 生产 `pom.xml` 未声明 `flyway-maven-plugin`，`flyway:info/validate` 属可选工具；
  正式迁移以 Spring Boot 启动时执行 Flyway 为准。
