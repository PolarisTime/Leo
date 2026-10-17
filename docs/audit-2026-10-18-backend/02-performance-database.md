# 后端性能与数据库审计（leo）

- 审计日期：2026-10-18｜基线提交：`0ba7f12c`（`chore(release): 13.2.1`）
- 技术栈：Java 21 / Spring Boot 3.5.16 / Hibernate 6.6 / PostgreSQL 18.6 / Redis / HikariCP
- **只读审计**：未修改业务代码与配置，未对数据库执行写操作（仅 `SELECT` / `EXPLAIN` / `pg_catalog`）；本文件是唯一写入产物。

**证据分级**：`[静态]`=源码+Flyway `文件:行号`；`[EXPLAIN]`=本地 dev 库实跑 `EXPLAIN (ANALYZE,BUFFERS)`；`[实测]`=引用 `perf/reports/2026-09-29-dev-stress.md`。

**dev 库数据量实测**（理解全部 `[EXPLAIN]` 的前提）：`so_sales_order=108`、`mk_quote_sheet=130`、`lg_freight_bill=64`、`po_purchase_order=183`、`mk_steel_quote=60333`、`fm_receipt=19998`（其中 19995 行 `deleted_flag=true`）、`sys_operation_log=268929`。**除 3 张表外全部业务表 < 1000 行**，故所有 `Seq Scan` 只能证明"无索引可用/规划器未选索引"，不能量化生产延迟；凡推算均标注依据。

连库说明：`leo_readonly` 被 GRANT 拒绝（`permission denied for table so_sales_order`），实际用 `leo` 账号 + `options=-cdefault_transaction_read_only=on` 会话。`pg_stat_statements` **未安装**，无真实慢查询排行（见 §七 限制）。

---

## 一、摘要

### Top 5 热点表（按「写入成本 × 调用频率 × 增长斜率」）

| # | 表 | 实测规模 | 热点原因 | 关联 |
|---|---|---|---|---|
| 1 | `sys_operation_log`（121 分区）+ `_identity` | 268929 行；1320 个分区索引 | 每次业务写 INSERT 一行，BEFORE 触发器再写 identity 表（双写）；**121 分区致每次查询规划 52–54 ms** | P1-4 |
| 2 | `fm_receipt` | 19998 行（19995 行是压测残留） | 资金流水/财务总览/全局搜索全量聚合；垃圾行 99.98% | P2-3 |
| 3 | `so_sales_order` + `_item` + `so_sales_outbound_item` | 108 / 20 / 231 | 列表关联筛选用 `IN (子查询)`，实测 **8176 次子计划重算、82043 buffers** | **P0-1** |
| 4 | `lg_freight_bill_source_order` | 74 行 | `source_sales_order_id` 外键**无前导索引**；又是销售订单 `pendingOnly` 必经路径 | P1-5 |
| 5 | `mk_quote_sheet` / `_item` / `_item_price` | 130 / 134 / 134 | 列表 `.map(toResponse)` 每行触发 3 层懒加载；`resolveSupplierNames` 循环内查供应商 | P1-1、P1-2 |

### 严重度：P0 = 1、P1 = 5、P2 = 6、P3 = 4

| 编号 | 级别 | 一句话 | 位置 |
|---|---|---|---|
| P0-1 | **P0** | 关联筛选 `IN (子查询)` 按行重算，实测 8176 次子计划 | `SalesOrderRepository.java:151/176/198/222/246` |
| P1-1 | P1 | 报价单列表逐行触发 3 层懒加载（`items` 缺 `@BatchSize`） | `QuoteSheetStore.java:206`、`QuoteSheet.java:77-79` |
| P1-2 | P1 | `resolveSupplierNames` 循环内逐个查供应商 | `QuoteSheetStore.java:771-776` |
| P1-3 | P1 | 收付款核销明细逐行查对账单（事务内 N+1） | `ReceiptAllocationResponseAssembler.java:28/41`、`PaymentAllocationResponseAssembler.java:42/55` |
| P1-4 | P1 | 121 分区致规划 52–54 ms；分区清理只有统计无删除 | `V81__partition_operation_log.sql:52-75`、`scripts/maintenance.sh:116-145` |
| P1-5 | P1 | `lg_freight_bill_source_order.source_sales_order_id` 无索引 | `pg_indexes` 仅 `(freight_bill_id,...)` |
| P2-1 | P2 | `POSITION(...IN LOWER())`/`LOWER()+LIKE` 让 41 个 trgm 索引失效（EXPLAIN 已证） | 7 文件 16 处 |
| P2-2 | P2 | 附件清单导出在只读事务内做全表读+S3 上传 | `AttachmentManifestExportService.java:84-113` |
| P2-3 | P2 | 7 处 `LIMIT/OFFSET`，窗口函数在 LIMIT 前对全集求值 | `CashLedgerQueryRepository.java:333` 等 |
| P2-4 | P2 | 物料导出先 `findAll` 无上限再判 `max-export-rows` | `MaterialDocumentService.java:59-98` |
| P2-5 | P2 | `fm_receipt` 等统计信息陈旧（`n_live_tup=0` 而 `reltuples=19998`） | `pg_stat_user_tables` |
| P2-6 | P2 | `ExcelProperties.importBatchSize` 零引用（死配置） | `ExcelProperties.java:60` |
| P3-1 | P3 | 9 组完全重复索引 | `so_sales_order_item` 等 9 表 |
| P3-2 | P3 | 业务表 596 索引中 395 个 `idx_scan=0`（19 MB） | `pg_stat_user_indexes` |
| P3-3 | P3 | 全局搜索视图 15 分支 UNION ALL，无索引支撑 | `V1__baseline.sql:859-1245` |
| P3-4 | P3 | `readOnly` 覆盖 225/419（54%），漏标但无实质缺陷 | 全仓 |

### 结论

**无架构级必然故障，但存在 1 个 P0**：销售订单列表的「下游关联」筛选会把相关子查询退化为按行重算——在本机 108 行表上已跑出 8176 次子计划、82043 次 buffer 访问、121 ms。按 `N_order × N_outbound_item × N_order_item` 三层嵌套线性外推，订单过千后该接口即退化到秒级（推算，依据见 P0-1）。其余集中在：报价单/收付款列表的循环内查询、`sys_operation_log` 分区数失控、以及**关键字写法与索引不匹配导致 41 个 trigram 索引长期闲置**。

---

## 一bis、重点排查清单逐项结论（有/没有都给结论）

| # | 清单项 | 结论 | 详见 |
|---|---|---|---|
| 1 | `findAll()` / 无分页集合查询 | **零 `findAll()` 零参调用**；`findAll(` 家族 36 处，**3 处无分页无上限**（`AttachmentManifestExportService:88/90`、`PermissionCatalogService:82`），其余均有 `Pageable`/`MAX_OPTIONS` 边界 | §2.2、P2-2 |
| 2 | 循环内 repository 调用（N+1） | **有，4 组确认成立**：P0-1（5 处 `IN(子查询)`）、P1-1（报价单 3 层懒加载）、P1-2（循环查供应商）、P1-3（收付款逐行查对账单）；另 2 处写事务内循环 `findById`（§5.3 #6）。**7 处经核为批量/内存操作，非 N+1** | §三 全部 |
| 3 | Entity 懒加载在事务外被 DTO 映射触发 | **不会发生**：`open-in-view: false` 已关（`application.yml:80`），事务外访问集合必抛 `LazyInitializationException`；web 层 0 处引用实体/装配器。**风险已转移为事务内 N+1**（即 #2） | §2.1 |
| 4 | `@Transactional` 内做文件/打印/HTTP/OSS IO | **有，2 组**：P2-2（附件清单：全表读+S3 `putObject` 在 `readOnly` 事务内）、P2-2b（4 处 Excel/POI 导出在事务内渲染）。**上传/打印预览/行情抓取/SSH 均正确隔离在事务外** | §四 |
| 5 | 大文件热点（7 个指定文件） | 逐个给结论：`QuoteSheetStore` P1-1+P1-2（最重）；`SalesOrderService` 本身干净（唯一 `for` 在 `assertUnitPriceChangeAllowed:313/318`，只遍历内存 `HashMap`，零 SQL），问题在其调用的 repository P0-1；`ProjectService` 干净（`toResponse` 仅 MapStruct）；`FreightBillService` 详情 1+1 查询可接受；`CashLedgerQueryRepository` P2-3；`FinanceOverviewQueryRepository` P2-3（9 分支 UNION）；`CustomerStatementService` 干净（`toSummaryResponse` 不碰集合） | §三、P2-3 |
| 6 | Flyway 索引：高频字段覆盖 / OFFSET 深分页 / 部分唯一索引匹配 | 单号类 **41 个 trgm 索引已建但被写法废掉**（P2-1）；外键 **9 条无前导索引**，含关键的 `lg_freight_bill_source_order.source_sales_order_id`（P1-5）；`status+date` 组合覆盖良好（`idx_so_order_list` 等 INCLUDE 索引）；**部分唯一索引 6/6 与查询条件匹配**；**OFFSET 7 处**（P2-3） | §五 5.1 |
| 7 | application.yml：Hikari / JPA batch·fetch / open-in-view / Redis | Hikari 20 条**合理不调**；`open-in-view:false`、`batch_size:50`、`default_batch_fetch_size:50`、`reWriteBatchedInserts:true` **全部正确**；Redis(16379) TTL 抖动/活跃写节流/SCAN 有界**成熟**；遗留：`page` 无上界、`leak-detection=0`、`rate-limit.enabled=false`、`import-batch-size` 死配置、`lock_timeout` 未接线 | §五 5.2/5.4/5.5/5.6 |

---

## 二、正面结论（优化时不得回退）

**2.1 `open-in-view: false` 已关闭**（`application.yml:80`）。这条把风险类型从「随机 500」变成「启动即见的确定性失败」——OSIV 开着时懒加载在 Controller 序列化阶段触发、`LazyInitializationException` 被掩盖、只表现为变慢；关掉后必然立刻抛异常。**因此本报告不把"懒加载在事务外被 DTO 映射触发"列为 P0/P1**：它不会以随机异常出现。代价转移到「事务内 N+1」——集合必须在事务内初始化，于是每个要明细的列表都在事务内逐行查（P1-1/P1-3 的根因）。

**2.2 零 `findAll()` 零参调用**：`grep -rn "findAll()" src/main/java | wc -l` → **0**。但 `findAll(` 家族共 **36 处**，核对后真正无分页无上限的仅 3 处：

| 位置 | 读什么 | 判定 |
|---|---|---|
| `AttachmentManifestExportService.java:88` | `findAllByOrderByIdAsc()` 全表附件 | P2-2 |
| `AttachmentManifestExportService.java:90` | 全表绑定 | P2-2 |
| `PermissionCatalogService.java:82` | `findAll(sort)` 权限目录（表仅 166 行，目录型） | 可接受 |

其余 33 处为 `findAll(Spec, Pageable)` 或 `PageRequest.of(0, MAX_OPTIONS, ...)`（如 `PurchaseOrderOptionService.java:60`，`MAX_OPTIONS=200`）。**分页边界统一收口在 `PageQuery`**（`PageQuery.java:13-46`）：`size` 有 `@Min(1) @Max(200)`、`toPageable` 强制追加 `id` 作唯一兜底排序（`:27-29`）——避免分页抖动，写法正确。**唯一遗漏：`page` 只有 `@Min(0)` 无上界**（P2-3），而 native 查询一律用 `page*size` 拼 `OFFSET`。

**2.3 Hibernate 批量参数已配置且方向正确**（`application.yml:86-96`）：`jdbc.batch_size:50`（:87）、`fetch_size:100`（:88）、`order_inserts:true`（:90）、`default_batch_fetch_size:50`（:96）；驱动侧 `reWriteBatchedInserts:true`（:65，PG 批量插入生效前提）、`prepareThreshold:3`、`in_clause_parameter_padding:true`。**`default_batch_fetch_size=50` 是抵消 N+1 最重要的全局开关**，把 LAZY 集合初始化从「每行 1 条」降为「每 50 父实体 1 条」。P1-1 实际是「3 层集合 × 每 50 行 1 条」而非严格 N+1。

**2.4 乐观锁覆盖 37/65（56.9%）是合理取舍**，不是缺失。未加 `@Version` 的是明细行/纯追加表（`QuoteSheetBrand`、`QuoteSheetItemPrice`、`DocumentChargeItem`、`OperationLog`、`MaterialHistory`），由父实体级联管理或本身不可变；给它们加版本号只会增加无谓的乐观锁失败面。8 个单据头（`SalesOrder`/`FreightBill`/`Receipt`/`Payment`/`QuoteSheet`/`PurchaseOrder`/`CustomerStatement`/`FreightStatement`）**全部有 `@Version`**，与 `If-Match`/412 契约一致。

**2.5 悲观锁用在必须串行的点上**：34 处 `PESSIMISTIC_*`。`QuoteSheetRepository.java:41-47` 的 `@Lock` 注释明确写了「不使用 NOWAIT（`lock.timeout=0` 会让正常并发签出直接 409）」「超时由 DB `lock_timeout` 兜底并映射 409」——是踩坑后沉淀的正确结论。**但配置有落差（P3）**：注释引用 `LEO_POSTGRES_LOCK_TIMEOUT=10s`，而 `application.yml`/`application-prod.yml` **均无**对应的 `options=-c lock_timeout`；全仓 `lock_timeout` 只命中 Flyway 脚本的 `SET lock_timeout = 0`（`V1__baseline.sql:14`）。即兜底**未在应用侧接线**。

**2.6 Flyway 索引纪律**：189 个脚本（V1–V189 连续），基线 5619 行含 **231 个 `CREATE INDEX`**。3 个补索引脚本质量高于平均：`V137` 用部分索引「避免索引已删除历史行」；`V152`/`V186` 补 5 个 `gin_trgm_ops`，且 **`V186:4-9` 的注释准确诊断了「`lower(col) LIKE` 与裸列索引表达式不匹配」这一根因**，还写明「刻意不建 `fm_payment.business_type`，高命中率谓词不会走 trigram」。**问题是：诊断对了、索引建了，但只修了 `Specs` 一条路径，其余 7 文件仍用 `POSITION(...IN LOWER())`，把索引白建（P2-1）。**

---

## 三、逐项发现：循环内查询与 N+1

> 方法：Python 脚本按大括号深度追踪 `for(`/`.forEach(`/`.stream()` 作用域，在作用域内匹配 `\w+(Repository|Query|Service|Store|Client|...).\w+(`，得 **138 处**候选后人工确认。只列确认成立的。

### P0-1（P0）：销售订单列表相关子查询按行重算 — 实测 8176 次

**证据 A｜代码**：`SalesOrderRepository.java` 的 `findByReferenceFilter`（`:101-257`）中，涉及销售出库的分支一律写成 `x IN (SELECT ... 相关到外层)`：

```java
// :149-158（pendingOnly 分支）
and not exists (
    select pendingOutboundItem.id from SalesOutboundItem pendingOutboundItem
    where pendingOutboundItem.sourceSalesOrderItemId in (     // ← IN (子查询)
        select pendingSalesItem.id from SalesOrderItem pendingSalesItem
        where pendingSalesItem.salesOrder = salesOrder        // ← 相关到外层
    )
    and pendingOutboundItem.salesOutbound.deletedFlag = false
)
```
同一形状在 **5 处**重复：`:151`（pendingOnly）、`:176`（referenced=true）、`:198`（referenced=false）、`:222`（referencedBy=none）、`:246`（referencedBy=sales-outbound）。

**证据 B｜真实计划** `[EXPLAIN]`（dev 库，`so_sales_order` 仅 108 行）：

```
Limit (cost=35.32..1691.91) (actual time=3.915..121.221 rows=13.00)
  Buffers: shared hit=82043                                  ← 82043 次 buffer 访问
  -> Nested Loop Anti Join (actual time=3.914..121.214 rows=13.00)
       Join Filter: (ANY (oi.source_sales_order_item_id = (SubPlan 1).col1))
       Rows Removed by Join Filter: 8153
       -> Merge Anti Join (actual time=0.127..0.219 rows=36.00)      ← 这半边正确
            -> Seq Scan on so_sales_order so (actual rows=99.00)
       -> Hash Join (actual time=0.003..0.088 rows=227.11 loops=36)
            -> Seq Scan on so_sales_outbound_item oi (actual rows=231.08 loops=36)
       SubPlan 1
         -> Seq Scan on so_sales_order_item si (actual time=0.012..0.014 rows=1.26 loops=8176)  ← 8176 次
              Filter: (order_id = so.id)   Rows Removed by Filter: 264
              Buffers: shared hit=81735                         ← 占总量 99.6%
```
**表只有 108 行/明细 20 行，却已 8176 次子计划执行、121 ms**——因为它不是"全表扫一遍"，而是「外层每行 × 内层全表扫」的笛卡尔式重算。

**预估影响（推算，依据：当前 `N_so=108, N_oi=231, N_soi=20` → 121 ms；成本近似 `N_so×N_oi×N_soi`）**：订单 3000/出库明细 15000/订单明细 1200 时约 **40 分钟**（已不可用）；即使子计划有缓存，过千单也必然秒级。该接口即 `GET /v2.0/sales-orders`，压测主列表（`[实测]` 1 VU avg 9.04 ms）。

**触发条件**：前端「下游关联」分段筛选（`aries/src/config/business-pages/operations/sales-order-page.ts:206` 定义 `key:'referencedBy'`，选项 `freight-bill`/`sales-outbound`/`none`）被选中即走此路径；默认不带该筛选时走 `Specification` 分支（`SalesOrderQueryService.java:95-104`），不受影响。

**修复**（3 选 1，按代价递增）：
1. 改「先分页取 id，再批量判定引用」——仓库已有验证过的模式（`SalesOrderOutboundCandidateQueryRepository.pageIds:88-104`：先取 id 再批量装配）。**语义等价、代价最小，推荐先做**。约 1 人日。
2. 把 5 处 `IN(子查询)` 改写为 `NOT EXISTS` + 显式 join，让规划器拿到 anti-join 形态。约 0.5 人日，需验证计划变为 `Hash Anti Join`。
3. 物化 `referenced_by_outbound`/`referenced_by_freight` 冗余列。约 3 人日，需回填 + 三处写入点同步 + 删除时回滚标记。

### P1-1（P1）：报价单列表逐行触发 3 层懒加载

**证据**：`QuoteSheetStore.java:206` 的 `repository.findAll(specification, pageable).map(this::toResponse)`；`toResponse`→`toItemResponse`（`:1202-1233`）逐行访问 `getBrands()`（:1202）、`getItems()`（:1207）、然后每行再访问 `getPurchaseOrderLinks()`（:1222）与 `getPrices()`（:1227）。

**关键症状：实体批量配置「一半有一半没有」**——`QuoteSheet.java` 中 `brands` 有 `@BatchSize(50)`（`:73`），紧邻的 `items` 没有（`:77-79`）。而 `QuoteSheetRepository.java:30-36` 注释声称「集合已配置 `@BatchSize(50)`…无 N+1」——**该注释只对 `brands` 成立**。

**实际查询数（推算）**：`size=30`、每单 `i` 行、每行 `p` 价 → 主查询 1 + **count 1**（Spring Data 分页自动附带，常被忽略）+ `brands` 1 + `items` 1（被全局 batch 50 兜住）+ `prices` ⌈30i/50⌉ + `links` 同 ≈ **15 条 SQL**（i=10）。若某环境把 `SPRING_JPA_HIBERNATE_DEFAULT_BATCH_FETCH_SIZE` 覆盖为 0，`items` 退化为 +30 条，总数约 **45 条**。

**影响**：纯 DB 时间 5–45 ms，占连接时长为同量级接口 2–5 倍；池仅 20 条，显著压缩并发容量。

**修复**：① `items` 补 `@BatchSize(size=50)`（1 行，零风险）；② 改页级批量装配（照抄 `SalesOrderResponseAssembler.toDetailResponses:51-75` 的 `listByDocumentIds` 模式，或 `RoleService.page:60-80` 的 `findByRoleIdIn`）；③ **列表页不需要明细** → 新增 `toSummaryResponse` 只映射表头，details 留给 `GET /{id}`（收益最大，需确认前端契约）。**建议 ①+③。代价 0.5 人日。**

### P1-2（P1）：`resolveSupplierNames` 循环内逐个查供应商

**证据**：`QuoteSheetStore.java:755-778`，`:771` 的 `for (Long supplierId : supplierIds)` 内 `:772` 调 `supplierQuery.findActiveNormalById(supplierId)`；该实现是单行查询（`SupplierQueryService.java:29-31` → `findByIdAndDeletedFlagFalseAndStatus`），**无批量版本**。调用点：`create`（:118 前）、`addItem`（:230）、`updateItem`（:246）、整单替换。

**反证（同一文件内有正例）**：`:1180`/`:1191` 用的是 `listActiveByIds(orderIds)`/`listActiveItemsByIds(itemIds)` 批量查询，`:1136` 的方法注释还写着「批量解析行级采购订单关联(一次查询覆盖整单全部行, 避免逐行查询的 N+1)」。**说明团队知道怎么写，只是这里漏了。**

**影响**：整单替换有 20 个不同供应商时多 20 条 SELECT，约 +6 ms，且每条都占一次 PG 往返（批量化后 1 条约 0.5 ms）。
**修复**：`SupplierQuery` 加 `findActiveNormalByIds(Collection<Long>)`，改一次查询 + 内存组装，缺失 id 在内存报 422（保持错误语义）。**约 2 小时。**

### P1-3（P1）：收付款核销明细映射时逐行查对账单

这里**不是** `LazyInitializationException`（OSIV 已关、`items` 有 `@EntityGraph`），而是**集合批量取到了、集合内每行的关联单据仍逐个查**。

**证据 A（收款）** `ReceiptAllocationResponseAssembler.java:21-46`：
```java
List<ReceiptAllocationResponse> toResponses(Receipt entity) {
    return entity.getItems().stream()            // :22
            .map(this::toResponse)               // :23
            .toList();
}
private ReceiptAllocationResponse toResponse(ReceiptAllocation item) {
    CustomerStatementApi.Snapshot statement = findCustomerStatement(item.getSourceStatementId());  // :28 每行一次
}
private CustomerStatementApi.Snapshot findCustomerStatement(Long id) {
    return id == null ? null : customerStatementApi.findActiveById(id).orElse(null);              // :41
}
```
`findActiveById` → `repository.findByIdAndDeletedFlagFalse(id)`（`CustomerStatementQueryService.java:22-24`），无批量接口。调用点 `ReceiptService.java:377`。**核销几行就多发几条 SQL。**

**证据 B（付款，同构）** `PaymentAllocationResponseAssembler.java:21-56`：`:42` 每行调 `findFreightStatement` → `:55` → `FreightStatementQueryService.java:22-24` 单行查询。

**反证**：`SalesOrderResponseAssembler.toDetailResponses:51-75` 已用 `listByDocumentIds` 一次取全页——**批量能力存在，付款/收款装配器没用上**。

**影响**：一张收款单核销 20 行 → 详情 21+ 条 SQL。财务日终逐单核对是高频场景，估算详情从 ~5 ms 劣化到 ~25 ms，QPS 上限降约 5 倍。
**修复**：给 `CustomerStatementApi`/`FreightStatementApi` 加 `findActiveByIds(Collection)`，装配器改「收集本单全部 id → 一次批量查 → 内存 map」；**不建议**冗余快照列（违反 `AGENTS.md`「业务口径只在一处」，金额需实时）。**约 0.5 人日，本报告性价比最高的修复之一。**

### P1-4（P1）：`sys_operation_log` 121 分区 → 每次查询固定 52–54 ms 规划

**证据 A｜分区数** `[EXPLAIN]`：`SELECT count(*) FROM pg_inherits WHERE inhparent='public.sys_operation_log'::regclass` → **121**。`V81__partition_operation_log.sql:52-75` 用 `WHILE` 循环建到「当前月 +10 年」（`:61`：`+ interval '10 years'`）。**实际只有 4 个分区有数据**：m09=246438 / m08=7845 / m10=7379 / m07=7267。

**证据 B｜规划耗时 36 倍** `[EXPLAIN]`（连测 3 次稳定）：
```
EXPLAIN (ANALYZE) SELECT * FROM sys_operation_log ORDER BY operation_time DESC, id DESC LIMIT 30;
→ Planning 54.331 ms / Execution 2.061 ms ；52.776 / 1.935 ；52.388 / 2.249
对比普通表 SELECT * FROM so_sales_order ORDER BY id DESC LIMIT 30;
→ Planning 1.487 ms / Execution 0.090 ms ；1.563 / 0.089
```
计划文本证实原因：`Merge Append` 必须为**每个分区**准备子计划（即使 0 行），共 121 个 `Index Scan Backward using ..._pkey` 子节点；每个分区 12 个索引（`1320 = 121×12`，实测 `pg_indexes where tablename like 'sys_operation_log_y%'`）的元数据都要进规划器。

**证据 C｜清理只统计不删除**：`scripts/maintenance.sh:116-145` 的 `do_operation_log_partitions()` **只执行 SELECT**（`expired_active_rows`/`attached_partitions` 都是查询）。全仓 `grep -rn "DROP TABLE\|DETACH PARTITION\|dropPartition" src/main/java scripts` → **零命中**。`application.yml:281-284` 的 `retention-months:12` 只被 `OperationLogPartitionProperties` 读取用于**上报 Gauge**（`OperationLogPartitionMetrics.java:36`），无任何代码据此删分区。

**关联 P2-5（统计陈旧）**：`pg_stat_user_tables` 中 m07/m08/m09 的 `n_live_tup=0` 但 `reltuples=7267/7673/225437`，且 `last_autoanalyze` 全为 null。

**影响**：操作日志列表（`GET /v2.0/operation-logs`，`V2OperationLogController.java:33`）**每次固定多付 50 ms PG 端 CPU**；`OperationLogService.record` 的 `existsByEventId` 同样受影响。121 个空分区还让 `OperationLogPartitionMetrics` 每 5 分钟用 `generate_series`+`to_regclass` 探 121 个关系名（`:43-79`）。
**修复**：① 新增迁移 `DETACH`+`DROP` 所有 `reltuples=0` 且不在 lookahead 窗口的分区，**预期 121→约 14，规划 52 ms → 约 6 ms（线性推算 52×14/121）**；② 补真正的清理调度，**注意 `DROP TABLE` 不触发 `FOR EACH ROW` 触发器，必须同时清理 `sys_operation_log_identity` 对应时间范围的行**，否则 identity 表无限增长（当前设计最易忽略的一致性坑）。**①约 2 小时应立即做；②约 0.5 人日。**

### P1-5（P1）：`lg_freight_bill_source_order.source_sales_order_id` 外键无索引

**证据 A｜索引清单**：仅 `_pkey(id)`、`uk_freight_source_order_pair(freight_bill_id, source_sales_order_id)`、`idx_freight_source_order_bill(freight_bill_id) WHERE active_flag=true`。**无以 `source_sales_order_id` 为前导列的索引**；复合索引前导列不匹配则无法支撑反查。

**证据 B｜全库系统确认**：用 `pg_constraint`×`pg_index` 反查「外键列是否被某索引前导列覆盖」，命中 9 条，业务关键的是 `lg_freight_bill_source_order | source_sales_order_id | fk_freight_source_order_sales`。

**证据 C｜真实计划** `[EXPLAIN]`：
```
SELECT 1 FROM lg_freight_bill_source_order r WHERE r.source_sales_order_id=123 AND r.active_flag AND NOT r.deleted_flag;
→ Seq Scan on lg_freight_bill_source_order r (actual rows=0)  Rows Removed by Filter: 74
```
74 行所以 Seq Scan 是对的，**但说明规划器连索引都无从选**。

**为何重要**：它是 **P0-1 的同一张表**——`SalesOrderRepository.java:142-148` 的 `not exists (select ... where pendingRelation.sourceSalesOrderId = salesOrder.id ...)` 正是反查此无索引列。**P0-1 的 EXPLAIN 里该分支表现良好（`Merge Anti Join` 0.219 ms）只因 74 行能整表进 hash；一旦该表到 10 万行，每行订单一次全表扫会直接打死接口。两个缺陷叠加才是完整风险图景。**

**其余 8 条无索引外键**（多为表小，仅记录）：`bd_document_charge_item.material_id`、`fm_cash_reversal.counterparty_id`（已被 `(settlement_company_id,counterparty_id,reversal_date,id)` 覆盖，**实际不缺**）、`fm_supplier_ledger_lock.supplier_id`、**`mk_steel_quote.article_id`（6 万行，值得关注）**、`so_sales_return.warehouse_id`、`so_sales_return_item.material_id`/`source_freight_bill_id`/`warehouse_id`。

**修复**：新增迁移建 3 条索引（表都小，秒级，无需 CONCURRENTLY）：
```sql
CREATE INDEX idx_freight_source_order_sales_order ON public.lg_freight_bill_source_order
    (source_sales_order_id) WHERE active_flag = true AND deleted_flag = false;
CREATE INDEX idx_steel_quote_article ON public.mk_steel_quote (article_id);
CREATE INDEX idx_so_sales_return_item_freight_bill ON public.so_sales_return_item
    (source_freight_bill_id) WHERE source_freight_bill_id IS NOT NULL;
```
**必须与 P0-1 同批上线**，否则 P0-1 修好后仍被此索引缺失拖住。

### P2-3（P2）：7 处 `LIMIT/OFFSET`，窗口函数在 LIMIT 前对全集求值

| 文件:行号 | 特点 |
|---|---|
| `CashLedgerQueryRepository.java:333` | 窗口函数算 `running_balance` |
| `FinanceOverviewQueryRepository.java:302` | 9 分支 UNION ALL + 窗口汇总 |
| `FreightBillSalesOrderCandidateQueryRepository.java:105`、`PurchaseOrderInboundCandidateQueryRepository.java:93`、`SalesOrderOutboundCandidateQueryRepository.java:99`、`SalesOrderSourceCandidateQueryRepository.java:61` | CTE + 单独 COUNT |
| `InventoryBalanceQueryService.java:76` | 注释承认「保留 OFFSET 兼容契约」 |

**深层问题不是 OFFSET，而是「窗口函数 + OFFSET」**。`CashLedgerQueryRepository:271-275` 注释说明这是**有意为之**（把 3 次 CTE 重算压成 1 次，优化方向正确），但代价是 `COUNT(1) OVER ()` 与 `SUM(...) OVER (ORDER BY ...)` 必须对**整个过滤结果集**求值后才能 LIMIT。`[EXPLAIN]` 实测（2 万行 `fm_receipt`，去掉 settlement_company 过滤让全量进窗口）：
```
WindowAgg (actual time=23.569..25.380 rows=19995.00)   Storage: Memory 1350kB
  -> WindowAgg (actual time=11.484..20.304 rows=19995.00)
       -> Sort (actual time=11.468..12.222)  quicksort  Memory: 1550kB
            -> Seq Scan on fm_receipt (actual time=0.012..5.412 rows=19995.00)
→ Execution Time: 29.591 ms
```
**取 30 行付了 2 万行排序 + 两次窗口求值（29.6 ms）。** 单主体年 5 万条流水时单次翻页约 75 ms（线性推算），且**翻到第几页都一样慢**——OFFSET 的经典"越翻越慢"变成了"每页都慢"。

**OFFSET 自身风险**：`PageQuery.page` 无上界（`PageQuery.java:14` 仅 `@Min(0)`），理论上可传 `page=100000&size=200` → `OFFSET 20000000`，**服务端无任何防护**。

**修复**：① 给 `page` 加 `@Max(1000)`（5 分钟）；② 资金流水/财务总览把 `COUNT(1) OVER ()` 拆为独立 COUNT，仅首次加载取 total、翻页省略 → 翻页 29.6 ms 降至约 5 ms（约 1 人日）；③ 4 个候选查询改 keyset 分页（`WHERE (sort,id) < (:lastSort,:lastId)`，参考 11/12）——结构已具备（`pageIds` 先取 id 再批量装配），但属契约变更需 `/api/v2` 兼容（约 3 人日）。

### P2-1（P2）：`POSITION(...IN LOWER())` / `LOWER()+LIKE` 让 41 个 trgm 索引长期失效

**证据 A｜索引是裸列 + partial**（`pg_indexes where indexdef like '%gin_trgm_ops%'` → **41**）：
```sql
CREATE INDEX idx_fm_receipt_counterparty_name_trgm ON public.fm_receipt
    USING gin (counterparty_name public.gin_trgm_ops) WHERE (deleted_flag = false);
```
注意建在**裸列** `counterparty_name`，不是 `lower(...)`。

**证据 B｜代码用表达式形式**：`Specs.java:86-95` 的注释把根因写得完全正确（「`lower(col)` 是另一个表达式，无法匹配裸列索引表达式，关键字一非空就退化为顺序扫描，这些 trgm 索引等于白建」），`V186__keyword_trigram_indexes.sql:4-9` 同样。**但修复只覆盖 `Specs` 一条路径**，全仓仍剩 **7 文件 16 处** `POSITION(...IN LOWER(...))` + **23 处** `LOWER(COALESCE(...))`：

| 文件 | 行号 | 对应裸列索引 |
|---|---|---|
| `SalesOrderRepository.java` | `:116-119`、`:128-131` | ✓ `idx_so_sales_order_order_no_trgm` 等 4 个 |
| `PurchaseOrderRepository.java` | `:91-92`、`:131-132` | ✓ |
| `FreightBillSalesOrderCandidateQueryRepository.java` | `:67-69` | ✓（被表达式废掉） |
| `PurchaseOrderInboundCandidateQueryRepository.java` | `:55-56` | ✓ |
| `SalesOrderOutboundCandidateQueryRepository.java` | `:60-63` | ✓ |
| `SalesOrderSourceCandidateQueryRepository.java` | `:245` | ✓ |
| `CashLedgerQueryRepository.java` | `:399-404` | ✗ CTE 输出列 |
| `FinanceOverviewQueryRepository.java` | `:411-412` | ✗ CTE 输出列 |

**证据 C｜三组 EXPLAIN 对照（决定性）**——`lg_freight_bill_item` 上 `ANALYZE` 后 `SET enable_seqscan = off`，强制只能用索引：
```sql
-- ① 裸列 ILIKE（Specs 现在的写法）
SELECT id FROM lg_freight_bill_item WHERE source_no ILIKE '%SO2026%';
→ Bitmap Index Scan on idx_doc_flow_freight_item_source_no    ← 命中
-- ② POSITION + LOWER（7 文件的写法）
SELECT id FROM lg_freight_bill_item WHERE POSITION('so2026' IN LOWER(source_no)) > 0;
→ Seq Scan  Disabled: true                                    ← 禁用 seqscan 也不走索引
-- ③ LOWER() LIKE（另 23 处）
SELECT id FROM lg_freight_bill_item WHERE lower(source_no) LIKE '%so2026%';
→ Seq Scan  Disabled: true                                    ← 同样不走
```
**同一列、同一索引：`ILIKE` 命中，另两种连"禁用 seqscan 强制用索引"都做不到**——规划器根本没把该表达式视为可索引。

**量化（推算）**：trgm 在 `LIKE '%kw%'` 上 10 万行文本列通常把 20–50 ms Seq Scan 降到 1–3 ms（约 10–30 倍）。**注意 dev 反例**：2 万行 `fm_receipt` 上实测 `ILIKE` 20.06 ms / `LOWER LIKE` 14.21 ms / `POSITION+LOWER` 14.60 ms —— `ILIKE` 反而略慢，因表太小规划器选 Seq Scan（`idx_fm_receipt_counterparty_name_trgm` 的 `idx_scan=0` 即证）。**这正是「不能拿小表 EXPLAIN 外推生产」的例证**；表过 10 万行且有选择性关键字时才会是 1 ms vs 50 ms。

**影响面**：覆盖订单/入库/出库/采购全链路关键字检索，是用户最常用入口；关键字非空即全表扫。
**修复**：① 16 处统一为 `ILIKE`（复用 `Specs` 已有实现，与 `Specs.java:80-96` 方向一致）——约 1 人日，须覆盖 `%`/`_`/`\` 转义与大小写语义回归；② `CashLedgerQueryRepository`/`FinanceOverviewQueryRepository` 的关键字**下推到基表**（而非在 CTE 输出列上过滤），既复用裸列索引又减少窗口函数要处理的行数。**不要**为 CTE 列建 `lower(col)` 表达式索引（与既有裸列索引重复占空间）。

### P2-2（P2）：附件恢复清单导出在只读事务内做「全表读 + S3 上传」

**证据** `AttachmentManifestExportService.java:84-113`：
```java
@Transactional(readOnly = true)                                      // :84 ← 事务起点
public AttachmentManifestArchive exportDailyArchive() {
    List<AttachmentFile> attachments = attachmentFileRepository.findAllByOrderByIdAsc();          // :88 全表读
    List<AttachmentBinding> bindings = attachmentBindingRepository.findAllByOrderByAttachmentIdAsc...(); // :90
    byte[] payload = gzip(toJsonLines(attachments, bindingsByAttachmentId, exportedAt));          // :92 全量JSON+gzip
    String storagePath = storageResolver.storeBytes(objectKey, payload, CONTENT_TYPE_GZIP);      // :95 ← 事务内网络 IO
}
```
`storeBytes` → `S3CompatibleAttachmentStorage.storeBytes:97-107` → `s3Client.putObject(...)`（`:107`），即**事务内发起 HTTPS 上传**（`connect-timeout:10s`/`read-timeout:30s`）。

**三重问题**：① 连接占用 = 全表读 + 全量 JSON + gzip + S3 上传（单独可达数秒）；② `findAll` 无上限，附件表只增不减，10 万份时全量入堆；③ `byte[] payload` 全程驻留，峰值约为 gzip 后 5–10 倍。

**为何是 P2**：定时任务每天 02:05 一次（`application.yml:293`），且有 `attachmentManifestExportRunning.compareAndSet` 闸门（`MaintenanceScheduledTasks.java:54-57`）、异常被 catch（`:60-61`）——不会打挂线上，最坏是凌晨占一条连接数秒 + 任务失败告警。但附件到百万级时 OOM 风险真实。

**修复**：① 拆掉 `@Transactional`，改「按 id keyset 分批读 5000 行 → 序列化追加临时文件 → 最后上传」，事务边界收缩到每批；② 最低限度把 `storeBytes` 移到事务外。**仓库内有现成正例**：`AttachmentUploadService.java:73` 注释即标准答案——`// Store file outside the DB transaction to avoid holding connections during I/O`，且失败时 `cleanupStoredFileQuietly` 补偿。**①约 1 人日，②约 2 小时。**

### P2-2b（并入 P2-2）：4 处 Excel/POI 导出在只读事务内渲染

| 位置 | 事务 | 事务内做的事 |
|---|---|---|
| `CashLedgerService.java:63-88` | `readOnly` :63 | 全量流水 + `ExcelExportService.export`（SXSSF 1 万行） |
| `ModuleExportService.java:75-105` | `readOnly` :75 | `jdbc.queryForList` 全量 + `exportTable` |
| `MaterialDocumentService.java:59-98` | `readOnly` :59/:91 | `findAll` 全表 + CSV/XLSX 生成 |
| `SalesOrderPrintExportService.java:85-120` | `readOnly` :85 | 读单 + `XSSFWorkbook` 填表 + `workbook.write`（:111） |

**为何只到 P2**：`application.yml:647-649` 的 `leo.export.concurrency.max-concurrent:4` + `acquire-timeout-millis:500`（超时即 429）是**正确的背压**，注释（`:404-409`）也准确描述了风险（"防止 POI 的 CPU 密集负载把线程与连接池一起吃满"）。**残留问题：闸门限的是并发数，不是事务持有时间**——1 万行导出占 1 条连接 2–5 秒，4 并发即 4 条（池 20 的 20%+），月末对账高峰会与其他请求叠加。
**修复**：Service 拆 `loadRows()`（`@Transactional`）+ `render(rows)`（无事务），或用一个不带 `@Transactional` 的编排方法包住。POI 渲染不需要 DB 连接。**约 0.5 人日/处，优先资金流水与模块导出。**
**同时记录 P2-4**：`MaterialDocumentService.java:59-98` 的 `materialRepository.findAll(MaterialSearchPolicy.search(keyword), DEFAULT_SORT)` **无上限**，超 1 万行时先白读全表再被 `ExcelExportService.java:52-55` 拒绝——应把校验提前或改流式写。

### 其余循环内调用：已确认**不是** N+1（避免误伤）

| 位置 | 为什么不是 |
|---|---|
| `PurchaseInboundApplyService.java:58` | `loadPurchaseWeighCategoryRules(request)` 在循环**之前**（:48 是上一行），脚本把作用域算宽了 |
| `MaterialResolver.resolve`（`PurchaseInboundApplyService.java:87`、`PurchaseOrderApplyService.java:96`） | `prepareResolver()` 已一次性载入商品目录到内存 map（`TradeItemMaterialSupport.java:73-86`），循环内是 `HashMap.get`，**零 SQL** |
| `PurchaseInboundSourceStatusGuard.java:70`、`PurchaseInboundResponseAssembler.java:46` | 均为 `...ByIdIn(ids)` / `summarize...Ids` 的**批量 IN 聚合**后才 `.stream()` |
| `SalesOrderResponseAssembler.toDetailResponses:51-75` | 页级批量装配正例，三聚合各一次 |
| `RoleService.java:71/75`、`SupplierService.java:359-370` | `findByRoleIdIn` / `findBySupplierIdIn` 批量 |
| `SalesOrderDerivedQuantityService` 全部 | 全是 `IN (:ids)` + `GROUP BY` 批量聚合 |
| `PrintPdfTableRenderer.java:101/272/372` | `valueResolver.*` 是内存模板求值，非 DB |

---

## 四、逐项发现：事务边界内的 IO

原则只有一条：**`@Transactional` 内只能有 DB 操作**。HTTP/对象存储/文件/POI 放进去会把连接持有从毫秒拉到秒，而池只有 20 条。

**已识别的违规**：P2-2（附件清单 + S3）、P2-2b（4 处 POI 导出）。**均已在 §三 展开。**

**已确认正确的（正面）**：

| 位置 | 结论 |
|---|---|
| `AttachmentUploadService.upload`（`:57-91`） | 类上**无 `@Transactional`**，S3 上传在事务外，仅 `saveUploadedFileMetadata` 进事务，失败补偿删除 |
| `AttachmentWebService`（全类） | 无 `@Transactional`，纯编排 |
| `PrintScriptService`（全类） | **无 `@Transactional`**，`dataProvider.loadRecord` 走 `NamedParameterJdbcTemplate`，渲染在事务外 |
| `SteelQuoteSyncService`（`:37-45` 注释） | 明确「数据库操作委托 `SteelQuoteStore`（独立事务），本类不做事务」——**外部 HTTP/SSH 抓取在事务外**，最易踩坑处已正确处理 |
| `MysteelFetcher.java:137` / `SteelxFetcher.java:133` | `new ProcessBuilder(command).start()`（SSH 跳板机取证）不在任何事务内 |

即：事务内 IO 的问题**只集中在导出/清单这一族且都有并发闸门兜底**；上传、打印预览、行情抓取三条最易出事的链路都已正确隔离。缺陷是**逐方法遗漏**而非系统性错误。

**P3-4（记录）**：`@Transactional` 419 处、`readOnly` 225 处（53.7%）。抽查**未发现「纯读方法用了可写事务」**的实质缺陷（如 `QuoteSheetService.detail:43-45` 虽无注解，但委托给 `store.detail` 的 `readOnly`）；覆盖不足主要是**契约层面缺失**。**不建议批量补齐 194 处**（风险>>收益），只对**新增**读方法用 ArchUnit（仓库已有 `scripts/check-java-module-boundaries.sh`）做 CI 检查，防覆盖率继续下滑。

---

## 五、索引与配置问题清单

### 5.1 索引

| # | 表/范围 | 问题 | 证据 | 级别 | 动作 |
|---|---|---|---|---|---|
| 1 | `lg_freight_bill_source_order` | `source_sales_order_id` 无前导索引 | `pg_indexes` 仅 `(freight_bill_id,...)` | **P1** | 建部分索引（P1-5） |
| 2 | `mk_steel_quote` | `article_id` 无索引（6 万行） | `pg_constraint`×`pg_index` | P2 | 建 `(article_id)` |
| 3 | `so_sales_return_item` | `source_freight_bill_id`/`warehouse_id`/`material_id` 无索引 | 同上 | P2 | 择机补 3 列 |
| 4 | 9 张明细表 | `(head_id)` 完全重复索引各 2 个（`*_head` vs `*_<父表>_id`） | `pg_indexes` 自连接 | P3 | `DROP` 旧的 `*_head` |
| 5 | 业务表 | 596 索引中 395 个 `idx_scan=0`（19 MB） | `pg_stat_user_indexes` | P3 | **必须用生产 30 天数据判，勿按 dev** |
| 6 | 41 个 trgm 索引 | 仅 `idx_fm_receipt_project_name_trgm` 有扫描（39 次），其余 0 | 同上 | **P2** | 根因是写法（P2-1） |
| 7 | 部分唯一索引 | **6 个全部与查询条件匹配** ✅ | 见下 | 正面 | 无需改 |

**第 7 条的逐条核对（任务要求项）**：
- `uk_md_project_project_code_active`（`WHERE deleted_flag=false`）↔ `existsByProjectCodeAndDeletedFlagFalse`；
- `uk_md_carrier_carrier_name_active`（`lower(carrier_name) WHERE deleted_flag=false`）↔ `CarrierRepository.java:46/55` 的 `LOWER(carrier.carrierName) = LOWER(:name)`——**表达式逐字一致，全仓最正确的一处**；
- `uk_st_customer_statement_source_return_active`（`source_sales_return_id IS NOT NULL AND direction='红字' AND NOT deleted_flag`）↔ `CustomerStatementRepository.java:92-100` 的 `where sourceSalesReturnId=? and direction='红字' and deletedFlag=false`；
- `uk_quote_sheet_brand` / `uk_quote_item_line` / `uk_quote_item_price` ↔ 报价单协调逻辑（`replaceBrands`/`replaceItems` 注释明确为规避这些唯一键冲突而设计两阶段写）。

### 5.2 配置

| # | 项 | 现状 | 位置 | 级别 | 建议 |
|---|---|---|---|---|---|
| 1 | `hikari.maximum-pool-size` | 20 | `:46` | ✅ 合理 | **不调大**（8 核→公式 16–20；PG `max_connections=100`，4 实例即满），改为缩短持连接时长 |
| 2 | `open-in-view` | false | `:80` | ✅ 正确 | 保持 |
| 3 | `batch_size` / `default_batch_fetch_size` | 50 / 50 | `:87` / `:96` | ✅ 正确 | 保持 + 启动打印生效值 |
| 4 | `reWriteBatchedInserts` | true | `:65` | ✅ 正确 | 保持 |
| 5 | `PageQuery.page` | 仅 `@Min(0)` 无上界 | `PageQuery.java:14` | **P2** | `@Max(1000)` |
| 6 | `leak-detection-threshold` | 0（关闭） | `:278` | P3 | 预发常开 20s，让"持连接>20s"主动告警 |
| 7 | `rate-limit.enabled` | false（默认） | `:626` | **P3 发布检查项** | prod 显式 true |
| 8 | Redis `port` | **16379**（非 6379） | `:128` | ✅ 正确 | 6379 是无关实例（无密码），勿误判为配置错误 |
| 9 | Redis TTL 抖动/活跃写节流 | 10% / 30s | `:211` / `:226` | ✅ 正确 | 保持 |
| 10 | `excel.import-batch-size` | 500，**零引用** | `ExcelProperties.java:60` | P2（P2-6） | 删除或注释说明 |
| 11 | `lock_timeout` | 注释承诺但**应用侧未接线** | `QuoteSheetRepository.java:44` 提及，yml 无 | P3 | 数据源加 `options=-c lock_timeout=10s` |
| 12 | 操作日志 lookahead | 配置 12 月，实际 121 分区 | `:283` vs `pg_inherits` | **P1** | 见 P1-4 |

**关于 Hikari 池的定量判断**：既有压测证实其硬上限（`[实测]` `perf/reports/2026-09-29-dev-stress.md:138-149`：20 并发登录占满池 → `Connection is not available, request timed out after 3000ms (total=20, active=20, idle=0, waiting=23)`）。但根因是「登录事务持连接竞争同一行 `sys_user`」（同报告 `:126-136`），已由 `LoginAuditService` 节流 UPDATE 修复（`:71-92`、`UserAccountRepository.java:34-54` 注释「登录链路禁止写 UserAccount 实体（P1）」）。**结论：20 条读负载够用，不应再缩小，也不建议调大**——池变大只是把排队从应用层推到 PG 层。真正该做的是 P1-1/2/3 与 P2-2/2b：把事务内 N 条查询压成 1 条，**等效于把池放大 N 倍且无副作用**。

### 5.3 查询写法

| # | 写法 | 位置 | 影响 | 级别 |
|---|---|---|---|---|
| 1 | `POSITION(:kw IN LOWER(col)) > 0` | 7 文件 16 处 | 41 个 trgm 索引失效（EXPLAIN 三组对照已证） | **P2** |
| 2 | `LOWER(COALESCE(col,'')) LIKE '%kw%'` | 23 处 | 同上 | **P2** |
| 3 | `x IN (SELECT ... 相关外层)` | `SalesOrderRepository.java:151/176/198/222/246` | 按行重算子计划，实测 8176 次 | **P0** |
| 4 | `findAll` 无分页 | `AttachmentManifestExportService.java:88/90` | 全表入内存 | P2 |
| 5 | `LIMIT/OFFSET` + `OVER()` | 7 处 | 窗口对全量求值（取 30 行付 2 万行代价，29.6 ms） | P2 |
| 6 | 循环内 `findById` | `QuoteSheetStore.java:772`、`ReceiptAllocationResponseAssembler.java:41`、`PaymentAllocationResponseAssembler.java:55`、**`MaterialBatchRollbackService.java:56`**、**`PurchaseOrderPrepaymentSourceAdapter.java:80`** | N 次往返 | P1/P2 |

**#6 后两处补充**（前文未展开，均在写事务内）：
```java
// PurchaseOrderPrepaymentSourceAdapter.java:77-84
for (Long purchaseOrderId : purchaseOrderIds) {
    sourceItemIds.addAll(purchaseOrderItemRepository.findActiveIdsByPurchaseOrderId(purchaseOrderId)); // :80
}
// MaterialBatchRollbackService.java:54-56
for (int index = rows.size() - 1; index >= 0; index--) {
    Optional<Material> target = materialRepository.findById(history.getMaterialId());  // :56 逐行 + :65/:76 save
}
```
后者更重：一次回滚 500 行历史 → 500 次 `findById` + 最多 500 次 `save`（`batch_size=50` 能合并写，但 `findById` 无法批量）。**修复**：改 `findAllById(ids)` 或按 `materialId` 去重后批量取。**约 2 小时，两处都改。**

### 5.4 Redis（实测 `INFO stats` @16379 db4）

```
total_commands_processed: 81,019,326   keyspace_hits: 45,614,502
keyspace_misses: 22,435,321（命中率 67.0%）  evicted_keys: 0（无内存压力）
db4: keys=495, expires=471, avg_ttl≈19.8h
```
命中率 67% 偏低但可解释：`auth-user` TTL 2 分钟（`:217`）、`permission-ttl` 5 分钟（`:222`）的正常过期，非缺陷。**TTL 设计成熟**：`static-ttl:7d`、`options-ttl:30m`、`ttl-jitter-percent:10`（防雪崩）、`activity-write-interval:30s`（限制会话活跃写）、`mget-batch-size:500` —— 这组参数是压测后针对性补的。**`SCAN` 全部有界**（`RedisJsonCacheSupport.java:140-160`、`AuthenticatedUserCacheService.java:137-160` 用 `scanBatchSize`/`deleteBatchSize`=256，`max-keys:10000` 兜底），不会阻塞 Redis。**唯一隐患（P3）**：`AuthenticatedUserCacheService.evictAll():113-136` 走 Set SSCAN，若索引 Set 异常膨胀到数万成员，一次全量失效会是数万次往返；建议加 `SCARD > 10000` 的 WARN 告警。

### 5.5 可观测性

| 项 | 配置 | 评估 |
|---|---|---|
| `tracing.sampling.probability` | 本地 1.0 / 生产 0.1（`application-prod.yml:57`） | 生产 10% 合理；**本地 1.0 会让压测偏高**（每请求生成 span） |
| `otlp.tracing.export.enabled` | 默认 false（`:491`） | ✅ 默认关闭正确，避免本地/CI 依赖 Collector |
| `OperationLogPartitionMetrics.refresh` | 每 5 分钟（`:43`） | **该 SQL 探 121 个关系 + 全表 COUNT**，是 P1-4 的雪上加霜（P3-6：可临时调 30 分钟或改用 `pg_class.reltuples` 估算） |
| `ReliableEventMetrics.refresh` | 每 30 秒（`ReliableEventMetrics.java:29`） | 未展开 |

### 5.6 限流与背压（压测后最正确的改进）

`application.yml:624-651`：`rate-limit.enabled:false`（默认）、`user-limit:60/s`、`global-limit:300/s`；`export.max-concurrent:4` + `acquire-timeout:500ms` 即 429。**正是针对压测缺陷 3**（`[实测] perf/reports/2026-09-29-dev-stress.md:151-153`：「读路径缺少准入控制……1500 VU 照单全收，p50 涨到近 2 s」）。注释说明 300/s ≈ 实测上限 850 req/s 的 1/3（饱和前削峰）、Redis 故障 fail-open（"限流器绝不产生 5xx"）——取舍正确。**唯一遗留**：默认关闭，prod 未覆盖，属易漏的发布检查项。

---

## 六、优化路线图（按性价比排序）

排序依据：**（收益 + 确定性）/（成本 + 回归风险）**，有 EXPLAIN 实证者优先。

### 第 1 批：本迭代内（约 1.5 人日，全部低风险）

| # | 动作 | 发现 | 预期收益 | 成本 | 风险 |
|---:|---|---|---|---|---|
| 1 | 新增迁移：3 条索引（`lg_freight_bill_source_order.source_sales_order_id` 部分索引 + `mk_steel_quote.article_id` + `so_sales_return_item.source_freight_bill_id`） | P1-5 | 解除 P0-1 修复后的下一道瓶颈 | 1 h | 极低（表小，秒级） |
| 2 | 新增迁移：`DETACH`+`DROP` 121 个分区中 `reltuples=0` 且不在 lookahead 窗口的 | P1-4 | **操作日志列表规划 52 ms → 约 6 ms**（推算 52×14/121） | 2 h | 中（须核对 identity 表一致性） |
| 3 | `PageQuery.page` 加 `@Max(1000)` | P2-3 | 阻断 `OFFSET 20000000` | 5 min | 极低 |
| 4 | `QuoteSheet.java:77` 的 `items` 补 `@BatchSize(50)` | P1-1 | 与 `brands` 对齐；环境覆盖时从 +30 条降为 +1 | 5 min | 极低 |
| 5 | 启动 INFO 打印 `open-in-view`/`batch_size`/`default_batch_fetch_size` 生效值 | §2.3 | 消除"被环境变量覆盖无人发现" | 1 h | 极低 |
| 6 | `application-prod.yml` 显式 `leo.rate-limit.enabled: true` | §5.6 | 补上背压最后开关 | 5 min | 低 |

### 第 2 批：下一迭代（约 3.5 人日）

| # | 动作 | 发现 | 预期收益 | 成本 | 风险 |
|---:|---|---|---|---|---|
| 7 | 销售订单 `findByReferenceFilter` 5 处 `IN(子查询)` → 「先分页取 id 再批量判定」（或改 `NOT EXISTS` 直连） | **P0-1** | 实测 121 ms / 8176 次子计划 → 预期 <5 ms；解除线性劣化 | 1 人日 | 中（5 分支 + 现有筛选测试） |
| 8 | 收付款装配器改批量（2 个 API 加 `findActiveByIds`） | P1-3 | 详情 21+ 条 SQL → 3 条，耗时约 −80% | 0.5 人日 | 低 |
| 9 | `resolveSupplierNames` 改批量（`SupplierQuery.findActiveNormalByIds`） | P1-2 | 少 N−1 次往返 | 2 h | 低 |
| 10 | 16 处 `POSITION(...IN LOWER())` 统一为 `ILIKE` | P2-1 | 41 个 trgm 索引生效；10 万行约 20–50 ms → 1–3 ms | 1 人日 | 中（转义/大小写回归） |
| 11 | 附件清单去 `@Transactional` 或 `storeBytes` 移出事务 | P2-2 | 消除事务内 S3 IO；避免附件增长后 OOM | 0.5 人日 | 低（`AttachmentUploadService:73` 有范式） |
| 12 | 4 处导出把 POI 渲染移出事务 | P2-2b | 单次导出持连接 2–5 s → <100 ms | 0.5 人日 | 低 |

### 第 3 批：季度内（需契约变更或数据迁移）

| # | 动作 | 发现 | 预期收益 | 成本 | 风险 |
|---:|---|---|---|---|---|
| 13 | 资金流水/财务总览：`COUNT(1) OVER()` 拆独立 COUNT，翻页不复算 total | P2-3 | 翻页 29.6 ms → 约 5 ms（2 万行实测基准） | 1 人日 | 中（summary 语义不变） |
| 14 | 4 个候选查询改 keyset（`cursor` 与 `page` 并存） | P2-3 | 消除深翻页 O(N) | 3 人日 | 中高（`/api/v2` 兼容） |
| 15 | 全局搜索视图物化为表 + 事件同步 + trgm 索引 | P3-3 | 从「全扫 15 表 + 30 ms 规划」变索引查询 | 5 人日 | 高（一致性+回填） |
| 16 | 销售订单下游引用物化为 `referenced_by_*` 列 | P0-1 方案 3 | 彻底消除关联筛选 join | 3 人日 | 高（并发一致性） |
| 17 | 清理 9 组重复索引 + 按生产 30 天使用率清未用索引 | P3-1/2 | 降低写放大，回收约 19 MB | 0.5 人日 | 低（须用生产统计） |

### 不建议做（优化陷阱）

| 想法 | 为什么不做 |
|---|---|
| 调大 `maximum-pool-size` | 20 已接近 Hikari 公式上限（8 核→16–20）；调大只把排队推到 PG（`max_connections=100`），4 实例即满 |
| 给明细行实体加 `@Version` | 由父实体级联管理，加版本号只增无谓乐观锁失败面；37/65 覆盖是合理取舍 |
| 批量补齐 194 处 `readOnly=true` | 抽查无「纯读用可写事务」实质缺陷；只对**新增**方法做 CI 检查 |
| 给 `CashLedger`/`FinanceOverview` 关键字加 `lower(col)` 表达式索引 | 与既有裸列索引重复占空间；正解是关键字下推基表 |
| 按 dev 库 `idx_scan=0` 删索引 | dev 表 <1000 行，规划器永不选索引，`idx_scan=0` 是伪信号 |

---

## 七、审计限制（必须与结论同读）

1. **dev 数据量极小**：除 `fm_receipt`/`mk_steel_quote`/`sys_operation_log` 外全部业务表 <1000 行。`Seq Scan` 只能证明"无索引可用/规划器未选"，**不能量化生产延迟**；推算均标注依据。
2. **`pg_stat_statements` 未安装**（`relation "pg_stat_statements" does not exist`），`shared_preload_libraries` 无权限（需 `pg_read_all_settings`）。**故无真实慢查询排行**——这是本次最大信息缺口。若生产已装，建议 `scripts/maintenance.sh queries`（`do_queries()` 在 `:92`）补 TOP 10。
3. **`leo_readonly` 无表权限**，改用 `leo` + 会话级 `default_transaction_read_only=on`，全程只 `SELECT`/`EXPLAIN`/`pg_catalog`，无写操作。**例外说明**：为让 EXPLAIN 可信，曾在 `fm_receipt` / `lg_freight_bill_item` 上执行过 `ANALYZE`（只更新统计信息、不改业务数据）。
4. **未覆盖**：JVM 堆/GC、Tomcat 线程池、k6 复跑；压测数据全部引自 `perf/reports/2026-09-29-dev-stress.md`（同机压测、宿主 load 39–41，该报告 §六 已声明会低估上限）。
5. **未逐行审计**：库存（`inv_transaction`/`InventoryBalanceQueryService`）、打印渲染（`PrintPdf*` 6 类约 1800 行）、Modulith `event_publication` 仅抽样。

---

## 八、参考来源

全部在 2026-10-18 用 `curl -o /dev/null -w '%{http_code}'` 验证（均 HTTP 200）。

1. PostgreSQL — Using EXPLAIN（`loops` 语义；官方「toy-sized table 结果不可外推」声明）https://www.postgresql.org/docs/current/using-explain.html
2. PostgreSQL — pg_trgm（`gin_trgm_ops` 支持 `LIKE`/`ILIKE`，不支持任意表达式 → P2-1 判据）https://www.postgresql.org/docs/current/pgtrgm.html
3. PostgreSQL — Multicolumn Indexes（前导列原则 → P1-5 判据）https://www.postgresql.org/docs/current/indexes-multicolumn.html
4. PostgreSQL — Partial Indexes（5.1 #7 的判定标准）https://www.postgresql.org/docs/current/indexes-partial.html
5. PostgreSQL — Indexes on Expressions（`lower(col)` 需表达式索引 → P2-1 另一半判据）https://www.postgresql.org/docs/current/indexes-expressional.html
6. PostgreSQL — Partitioning（`Merge Append` 121 子计划的机制背景 → P1-4）https://www.postgresql.org/docs/current/ddl-partitioning.html
7. Hibernate 6.6 — Batch fetching（`default_batch_fetch_size`/`@BatchSize` → §2.3、P1-1）https://docs.hibernate.org/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#fetching-batch
8. Hibernate 6.6 — Batching（`batch_size`+`order_inserts` 生效条件 → §2.3、P2-6）https://docs.hibernate.org/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#batch
9. Hibernate 6.6 — Entity graph 动态抓取（33 处 `@EntityGraph` 的理论依据）https://docs.hibernate.org/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#fetching-strategies-dynamic-fetching-entity-graph
10. Vlad Mihalcea — N+1 query problem（N+1 定义、`JOIN FETCH`/batch fetch 两解法 → P1-1/2/3 修复方向）https://vladmihalcea.com/n-plus-1-query-problem/
11. Vlad Mihalcea — SQL seek method（keyset 分页写法 → 路线图 #14）https://vladmihalcea.com/sql-seek-keyset-pagination/
12. Use The Index, Luke — No Offset（OFFSET 为何 O(N) → P2-3）https://use-the-index-luke.com/no-offset
13. Spring Data JPA — Query Methods（`Page` 会额外执行 count → P1-1「每次分页 2 条 SQL」依据）https://docs.spring.io/spring-data/jpa/reference/repositories/query-methods-details.html
14. Spring Framework — Declarative transaction management（`readOnly` 语义 → P3-4）https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html
15. pganalyze — EXPLAIN 文档（读 `Buffers`/`loops`/`Rows Removed` → 解读 82043 buffers 的方法论）https://pganalyze.com/docs/explain
16. HikariCP — About pool sizing（`core*2+spindles` → §5.2「不调大池」依据）https://github.com/brettwooldridge/HikariCP
17. PostgreSQL — pg_stat_statements（本报告缺失的慢查询能力与补救路径 → §七 限制 2）https://www.postgresql.org/docs/current/pgstatstatements.html
18. PostgreSQL — auto_explain（生产自动捕获慢计划，可作 P0-1 类问题的持续监控）https://www.postgresql.org/docs/current/auto-explain.html

---

## 九、复现命令

```bash
cd leo
# 静态统计
grep -rn "findAll()" src/main/java | wc -l                              # → 0
grep -rn "findAll(" src/main/java | wc -l                               # → 36
grep -rn "@Transactional" src/main/java --include=*.java | wc -l          # → 419
grep -rn "@Transactional(readOnly" src/main/java --include=*.java | wc -l # → 225
grep -rn "@EntityGraph" src/main/java --include=*.java | wc -l            # → 33
grep -rn "@BatchSize" src/main/java --include=*.java | wc -l              # → 4
grep -rn "position(\|POSITION(" src/main/java --include=*.java | wc -l    # → 16

# 只读连库（leo_readonly 无表权限，须 leo 账号 + 会话只读）
export PGPASSWORD='<见 .env.local>'
psql "host=127.0.0.1 port=5432 dbname=leo user=leo options=-cdefault_transaction_read_only=on"

# P1-4：分区数与规划耗时
SELECT count(*) FROM pg_inherits WHERE inhparent='public.sys_operation_log'::regclass;  -- 121
EXPLAIN (ANALYZE) SELECT * FROM sys_operation_log ORDER BY operation_time DESC, id DESC LIMIT 30;

# P2-1：验证 POSITION/LOWER 无法命中 trgm 索引
ANALYZE lg_freight_bill_item; SET enable_seqscan = off;
EXPLAIN (COSTS OFF) SELECT id FROM lg_freight_bill_item WHERE source_no ILIKE '%SO2026%';                 -- 命中
EXPLAIN (COSTS OFF) SELECT id FROM lg_freight_bill_item WHERE POSITION('so2026' IN LOWER(source_no)) > 0; -- Seq Scan
EXPLAIN (COSTS OFF) SELECT id FROM lg_freight_bill_item WHERE lower(source_no) LIKE '%so2026%';           -- Seq Scan

# 压测数据来源
cat perf/reports/2026-09-29-dev-stress.md
```

**报告结束。严重度：P0 = 1、P1 = 5、P2 = 6、P3 = 4（P2 含 P2-2b、P2-4 同源项；P3 含 P3-5/P3-6 配置建议）。**
