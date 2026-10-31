# 退役资产登记（legacy retired modules）

> **恢复历史备份前先读本文。**
>
> 本文登记 `leo` 仓库里「代码已退役、数据仍留库、或已从 Git 移除」的资产，以及它们各自
> 的处置口径。从历史数据库备份重建环境、或把某个退役模块重新拉起来之前，先读第 8 节
> 的风险清单——否则会把已经修掉的明细阻塞、幽灵金额、死外键一并复活。

- 更新：2026-10-31
- 依据：`src/main/resources/db/migration/` 的 `V66` / `V67` / `V71` / `V72` / `V205`，
  以及 2026-10-31 全量遗留审计（本地报告 `.local/len-audit/legacy-data.md`，不入库）
- 适用范围：`leo` 后端仓库与其 PostgreSQL schema

---

## 1. 仓库层面已移除的资产（2026-10-31）

| 资产 | 原路径 | 规模 | 处置 | 仓库外备份 |
| --- | --- | --- | --- | --- |
| 历史归档代码 | `leo/archive/` | **769 个 Git 文件 / 8.2 MB**（755 个 `.java`，其中 700 个 `*Test.java`；另有 5 json / 3 md / 2 txt / 2 sha256 / 1 yml / 1 properties） | `git rm -r archive`（索引与工作区均已删除，**已随 `777b8657` 提交**） | `/home/instance/Gemini/.local/archive-backup-20261031`（769 文件，`diff -r` 完全一致，sha256 769/769 一致；清单 `.local/archive-backup-20261031.sha256`） |
| 含 PII 的库 dump | `leo/scripts/jsh_erp_steel_dump.sql` | 717,093 字节 | `git rm`（**已随 `777b8657` 提交**） | `/home/instance/Gemini/.local/jsh_erp_steel_dump.sql`（`chmod 600`；sha256 `4033f220f2c311b78b1bfc12b8fcf642feeb727d78c9f4d6fa3e7f76377182a8`，与源文件一致） |

移除理由：

- `archive/` 不在 `pom.xml` 的任何 source root 里，Maven 不编译、CI 不执行；`scripts/test-affected.sh`
  与 `docs/incremental-testing.md` 都把 `archive/*` 列为「明确跳过」。留在仓库里只会被误当成现行代码。
- `scripts/jsh_erp_steel_dump.sql` 含业务数据、个人信息、IP、历史密码哈希，不应随仓库分发。

### 1.1 残留引用（本次**未改**，列在此处避免被误判为「还有人用」）

| 位置 | 内容 | 影响 / 建议 |
| --- | --- | --- |
| `docs/specs/2026-07-24-modular-monolith-decoupling-plan.md:523` | Markdown 链接指向 `../../scripts/jsh_erp_steel_dump.sql#L692` | 链接已失效；该文档是历史方案记录，保留原文即可 |
| `scripts/test-affected.sh:280` | `case` 分支里的 `archive/*` 跳过规则 | 变成死分支，无害；后续顺手删除即可 |
| `docs/incremental-testing.md:48` | 表格行「`archive/*` → 明确跳过」 | 已过时，后续顺手删除即可 |
| `.serena/project.yml:139,143` | `ignored_paths` 含 `archive/**` | 本地 LSP 配置（`.serena/` 不入库），索引重建后自然失效 |
| `docs/audit-2026-10-18-backend/05-code-quality-tests.md:201,483` | 提及 `archive/tests/` 的审计局限 | 历史审计原文，保留 |

> 备份目录 `.local/` 已在 `.gitignore` 中忽略（`.gitignore:29-30`），不会被 `git add -A` 带入索引。

---

## 2. 数据库退役归档表（16 张，COMMENT 标注「功能已退役」）

**判定标准**（三条同时成立）：

1. 表 `COMMENT` 明确写「…功能已退役，禁止新增业务数据」；
2. `src/main/java` 内**没有**实体 `@Table`、没有 Repository、没有 Service 写路径——只剩快照同步的
   表名常量（`ReferenceSnapshotSyncService` / `CompanySettlementNameSyncService` / 主数据 `*ReferenceGuard`）；
3. 库内 **0 行**。

| 表 | 退役迁移 | 退役模块 |
| --- | --- | --- |
| `po_purchase_refund` | V66 | 采购退款 |
| `po_purchase_refund_item` | V66 | 采购退款 |
| `fm_cash_reversal` | V67 | 资金冲销 |
| `fm_invoice_receipt` | V67 | 收票 |
| `fm_invoice_receipt_item` | V67 | 收票 |
| `fm_invoice_receipt_source_order` | V67 | 收票 |
| `fm_invoice_issue` | V67 | 开票 |
| `fm_invoice_issue_item` | V67 | 开票 |
| `fm_invoice_issue_source_order` | V67 | 开票 |
| `ct_purchase_contract` | V71 | 采购合同 |
| `ct_purchase_contract_item` | V71 | 采购合同 |
| `ct_contract_purchase_order` | V71 | 采购合同（**见第 3 节：保留**） |
| `ct_sales_contract` | V71 | 销售合同 |
| `ct_sales_contract_item` | V71 | 销售合同 |
| `st_supplier_statement` | V72 | 供应商对账 |
| `st_supplier_statement_item` | V72 | 供应商对账 |

> 另有 **4 张退役但未带此 COMMENT** 的表，一并登记以免遗漏：`fm_supplier_refund_receipt`
> （V66 通过禁用菜单/权限退役，0 行，其死外键见第 7 节）、`mk_supplier_price_adjustment`、
> `mk_supplier_price_adjustment_item`（V170 供应商调价版本退役残留）、`sys_database_export_task`
> （V78 移除 database admin 后无 Java 引用）。后三张已在 V205 删除，见第 4 节。
> 即「退役归档 20 张 = 本表 16 张 + 上述 4 张」。

**结构风险（比 0 行本身更值得记住）**：与这 20 张表相关的外键去重后 23 条，其中 18 条由退役表发出
（15 条指向**活跃**表，含 4 条明细→明细），9 条以退役表为父。4 条明细→明细外键正是 V204 修掉的
同类阻塞关系：`po_purchase_refund_item → po_purchase_order_item`、`fm_invoice_issue_item →
so_sales_order_item`、`fm_invoice_receipt_item → po_purchase_order_item`、
`st_supplier_statement_item → po_purchase_inbound_item`。当前 0 行 → 无实际阻塞；**一旦从备份恢复
历史数据，阻塞立即复活**。

---

## 3. 例外保留：`ct_contract_purchase_order`

- 它同样是 V71 退役、0 行、无 Java 写路径的表，但**不删**。
- 原因：视图 `public.global_search_document`（`V1__baseline.sql:859`）的 UNION 分支里有
  `FROM public.ct_contract_purchase_order relation`——全局搜索仍会扫它，删表会让视图失效。
- 处置：保留为历史查询资产；`V205` 已在其注释中写明该例外。
- 删除前提：先改视图（移除该 UNION 分支）+ 改全局搜索的文档类型映射，再 `DROP TABLE`。

### 3.1 A4：`global_search_document` 视图还依赖另外 10 张退役归档表

`public.global_search_document`（`V1__baseline.sql:859` 起）的 CTE/UNION 分支共直接读取
**11 张退役归档表**（除本节例外表 `ct_contract_purchase_order` 外还有下列 10 张）：

| # | 视图引用的退役表 | 视图中的用途 |
| --- | --- | --- |
| 1 | `ct_contract_purchase_order` | 采购合同 ↔ 采购订单来源号聚合（`purchase_contract_sources` CTE） |
| 2 | `ct_purchase_contract` | 采购合同搜索分支 |
| 3 | `ct_sales_contract` | 销售合同搜索分支 |
| 4 | `fm_invoice_issue` | 开票搜索分支 |
| 5 | `fm_invoice_issue_item` | 开票来源号聚合（`invoice_issue_sources` CTE） |
| 6 | `fm_invoice_issue_source_order` | 开票来源订单聚合（`invoice_issue_sources` CTE） |
| 7 | `fm_invoice_receipt` | 收票搜索分支 |
| 8 | `fm_invoice_receipt_item` | 收票来源号聚合（`invoice_receipt_sources` CTE） |
| 9 | `fm_invoice_receipt_source_order` | 收票来源订单聚合（`invoice_receipt_sources` CTE） |
| 10 | `st_supplier_statement` | 供应商对账单搜索分支 |
| 11 | `st_supplier_statement_item` | 供应商对账来源号聚合（`supplier_statement_sources` CTE） |

**约束（A4）**：将来删除上述任何一张表之前，**必须先按当前定义重建 `global_search_document` 视图**
（移除对应 CTE/UNION 分支），再改全局搜索的文档类型映射，最后才能 `DROP TABLE`；
否则视图失效会让全局搜索整体报错（不只是少一个分支）。
本节清单来自 `V1__baseline.sql` 视图定义逐表比对；改视图时请同步更新本表。

---

## 4. V205 删除的 3 张孤儿表

| 表 | 退役来源 | 判定 |
| --- | --- | --- |
| `mk_supplier_price_adjustment_item` | V170 供应商调价版本退役残留 | 0 行、0 代码引用（LSP + grep 双证） |
| `mk_supplier_price_adjustment` | 同上 | 0 行、0 代码引用 |
| `sys_database_export_task` | V78「移除 database admin 功能」后无 Java 引用 | 0 行、0 代码引用；其 CHECK `chk_export_task_status` 随表一起消失 |

另有 **`sys_operation_log_unpartitioned`** 也在 V205 中 `DROP`：它是 V81 分区化时改名的旧表，
数据已全量复制到分区表，仅 V81 自身引用，保留只会造成 **2,949 行**重复与备份混淆。
它是「日志副本」不是业务孤儿表，因此**不计入上述 3 张**。

同时清理了操作日志的超窗口空分区：保留「当前月 ±12 个月」，其余**空**分区删除；非空分区一律不动
（插入若超出保留窗口会落到 `sys_operation_log_default`，不影响可用性）。

---

## 5. 6 张幽灵入库单处置（V205 金额/重量归零）

**成因**：`PurchaseInboundDeleteService.releaseUnreferencedItems` 在软删采购入库单时，会把
「未被销售订单引用」的明细行**物理释放**，但头的 `total_amount` / `total_weight` 没有回写。
结果是「已删除、只剩金额、没有明细」的空壳单据，任何按已删单据做对账/追溯的报表都会看到幽灵金额。

**规模**：6 张，全部 `po_purchase_inbound`、`deleted_flag=true`、状态「草稿」，
合计 **339,142.47 元 / 46.577 吨**：

| 单据 ID | 头金额（元） | 头重量（吨） | 明细行 |
| --- | ---: | ---: | ---: |
| `337912848921403392` | 23,152.79 | 7.485 | 0 |
| `343431490148769792` | 219,224.06 | 8.316 | 0 |
| `358995810429444096` | — | — | 0 |
| `362925284783038464` | — | — | 0 |
| `363295647828287488` | — | — | 0 |
| `363362870093684736` | — | — | 0 |

**处置**：`V205` 第 2 段（`V205__legacy_data_cleanup.sql:26-31`）把「已软删 + 无明细 + 金额/重量非 0」
的头的 `total_amount`、`total_weight` 归零（幂等，带条件）。

**后续约束**：新增/修改任何「软删时释放明细」的逻辑，必须同时把头的金额/重量归零——
这是**唯一口径**，不要在报表侧再算一次。其余 6 类单据（采购订单/销售订单/销售出库/物流单/
两张对账单）的已删空壳均为 0，说明该现象目前只出现在采购入库。

---

## 6. 10 个 NOT VALID CHECK 已校验

2026-10-31 审计实测：495 个 CHECK 中 10 个 `convalidated=false`，**存量违规 0**，因此可以零风险收口。

| 约束 | 表 | 处置 |
| --- | --- | --- |
| `chk_po_status` | `po_purchase_order` | V205 `VALIDATE CONSTRAINT` |
| `chk_inbound_status` | `po_purchase_inbound` | V205 `VALIDATE CONSTRAINT` |
| `chk_customer_stmt_status` | `st_customer_statement` | V205 `VALIDATE CONSTRAINT` |
| `chk_sign_status` | `st_freight_statement` | V205 `VALIDATE CONSTRAINT` |
| `chk_purchase_contract_status` | `ct_purchase_contract`（退役） | V205 `VALIDATE CONSTRAINT` |
| `chk_sales_contract_status` | `ct_sales_contract`（退役） | V205 `VALIDATE CONSTRAINT` |
| `chk_invoice_issue_status` | `fm_invoice_issue`（退役） | V205 `VALIDATE CONSTRAINT` |
| `chk_invoice_receipt_status` | `fm_invoice_receipt`（退役） | V205 `VALIDATE CONSTRAINT` |
| `chk_supplier_stmt_status` | `st_supplier_statement`（退役） | V205 `VALIDATE CONSTRAINT` |
| `chk_export_task_status` | `sys_database_export_task`（孤儿表） | 随表 `DROP`，无需校验 |

`V205` 中实际是 9 条 `ALTER TABLE ... VALIDATE CONSTRAINT`（`V205__legacy_data_cleanup.sql:15-23`），
第 10 条随第 4 节的孤儿表一起删除。校验后库内 `convalidated=false` 的 CHECK 应为 0。

---

## 7. 2 条退役表死外键已删

| 约束 | 子表.列 | 父表 | 定义来源 | 处置 |
| --- | --- | --- | --- | --- |
| `fk_fm_payment_allocation_supplier_statement` | `fm_payment_allocation.source_supplier_statement_id`（**活跃表**） | `st_supplier_statement.id`（V72 退役） | `V38:307` | V205 `DROP CONSTRAINT IF EXISTS` |
| `fk_fm_supplier_refund_receipt_refund` | `fm_supplier_refund_receipt.purchase_refund_id`（V66 退役） | `po_purchase_refund.id`（V66 退役） | `V16:116` | V205 `DROP CONSTRAINT IF EXISTS` |

口径：**只删约束、保留列**。列继续作为历史快照保留（`NULL`/历史值 = 来源单据已退役），
不得为了「保持引用完整」把它们重建回外键——那会让活跃表的写入必然失败，也会在恢复备份后
把上一轮修掉的明细阻塞带回来。

---

## 8. 恢复历史备份前必读（检查清单）

1. **先读本文**，再决定恢复哪一份备份、恢复后要补跑哪些脚本。
2. 恢复后**立即执行 V205**（或手工执行等价 SQL）：9 个 `VALIDATE`、6 张幽灵入库单归零、
   3 张孤儿表 `DROP`、2 条死外键 `DROP`、超窗口空日志分区清理。V205 是幂等的，重复执行安全。
3. **不要重建第 7 节的 2 条死外键**。历史备份里若 `fm_payment_allocation` 有非空
   `source_supplier_statement_id`，重建 FK 会让该表写入必然失败。
4. 恢复历史数据后，第 2 节末尾那 4 条**明细→明细**外键可能重新阻塞上游改单（V204 修掉的同类缺陷）。
   必须按 V204 口径（下游软删时由所属模块把明细来源引用置空 + 迁移期统一置空）重新解绑，
   否则用户会看到「数据被其他单据引用」的 409 却找不到是哪张已删单据。
5. **退役表保持 0 行**：不要把历史业务数据回灌到第 2 节的 16 张表；它们只是归档，
   没有任何 Java 写路径，回灌后无法通过业务界面维护。
6. 恢复完成后跑 `flyway validate`：库内 `flyway_schema_history` 与仓库版本必须一一对应
   （当前历史 204 条 + 待执行的 V205；旧 `V1`-`V209` 链路已被单基线 `V1__baseline.sql` 取代，
   不要从历史备份里恢复旧迁移表）。
7. 第 1 节被移出 Git 的 `archive/` 与 PII dump **不在数据库备份范围内**；确需查阅时从
   `.local/` 的仓库外备份取，不要重新提交进仓库。

---

## 9. 未来新增 / 恢复模块时的解绑要求

**恢复某个退役模块**（采购退款 / 供应商退款到账 / 开票 / 收票 / 资金冲销 / 采购合同 / 销售合同 /
供应商对账）时，必须一次性补齐下面各项，缺一项都会留下半退役状态：

1. 实体 + Repository + Service + Controller（当前只剩快照同步的表名常量）；
2. 业务菜单与权限：V66/V67/V72 已把 `sys_menu` / `sys_menu_action` / `sys_role_permission` /
   `sys_no_rule` 里对应项置删/禁用（这三张表本身已在 V94/V95/V106 删除，需按现行菜单模型重建）；
3. 若该模块被 `global_search_document` 视图或全局搜索 DTO 引用，先确认视图分支与文档类型映射；
4. 恢复前先确认不会与 V203/V204 的「软删解绑」口径冲突——下游软删必须解绑上游明细来源，
   且**只在所属 DeleteService 里做一次**（唯一出口），不要在多个出口各写一份。

**新增模块**时：

1. 禁止在退役表上建外键，禁止把退役表写进新的业务路径；
2. 新增「表名常量」前先核对第 2 节清单，避免把退役表重新纳入快照同步/引用守卫；
3. 新增表若要被 `global_search_document` 收录，改视图即可，不要复用退役表的 UNION 分支；
4. 退役表若要真正 `DROP`：先清视图与搜索引用，再按「兼容发布 → 数据迁移 → 清理发布」三步走，
   不要一次性删表。

---

## 10. 复核命令

```bash
# 1) 退役表清单：库内 COMMENT 应返回 16 行
psql "$LEO_DB_URL" -c "
SELECT c.relname, obj_description(c.oid) AS comment
  FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
 WHERE n.nspname = 'public' AND obj_description(c.oid) LIKE '%功能已退役%'
 ORDER BY 1;"

# 2) 退役表应为 0 行（按第 2 节清单代入）
psql "$LEO_DB_URL" -c "
SELECT relname, n_live_tup FROM pg_stat_user_tables
 WHERE relname IN ('po_purchase_refund','po_purchase_refund_item','fm_cash_reversal',
   'fm_invoice_receipt','fm_invoice_receipt_item','fm_invoice_receipt_source_order',
   'fm_invoice_issue','fm_invoice_issue_item','fm_invoice_issue_source_order',
   'ct_purchase_contract','ct_purchase_contract_item','ct_contract_purchase_order',
   'ct_sales_contract','ct_sales_contract_item','st_supplier_statement','st_supplier_statement_item')
 ORDER BY 1;"

# 3) 不应再有 NOT VALID 的 CHECK
psql "$LEO_DB_URL" -c "
SELECT conname, conrelid::regclass AS tbl FROM pg_constraint
 WHERE contype = 'c' AND NOT convalidated ORDER BY 2,1;"

# 4) 死外键应不存在
psql "$LEO_DB_URL" -c "
SELECT conname FROM pg_constraint
 WHERE conname IN ('fk_fm_payment_allocation_supplier_statement',
                   'fk_fm_supplier_refund_receipt_refund');"

# 5) 幽灵入库单应为 0 张
psql "$LEO_DB_URL" -c "
SELECT count(*) FROM po_purchase_inbound h
 WHERE h.deleted_flag AND NOT EXISTS (
   SELECT 1 FROM po_purchase_inbound_item i WHERE i.inbound_id = h.id)
   AND (h.total_amount <> 0 OR h.total_weight <> 0);"

# 6) Git 侧：被移除的资产（提交后可用）
git log --diff-filter=D --name-only -- archive scripts/jsh_erp_steel_dump.sql
```

---

## 11. 变更记录

| 日期 | 变更 |
| --- | --- |
| 2026-10-31 | 建档：`archive/`（769 文件）与 `scripts/jsh_erp_steel_dump.sql` 移出 Git 跟踪（**已随 `777b8657` 提交**，仓库外备份见第 1 节）；登记 V205 的四类处置（3 张孤儿表 / 6 张幽灵入库单 / 10 个 NOT VALID CHECK / 2 条死外键）；登记 16 张退役归档表与 `ct_contract_purchase_order` 保留例外 |
| 2026-10-31（收尾复核） | 修正提交状态为已提交（`777b8657`）；新增 A4：`global_search_document` 视图依赖 11 张退役归档表，删除任何一张前必须先重建视图（见 §3.1） |
