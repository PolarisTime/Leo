# 后端 API 契约审计（03）

- 仓库 `leo/`（Java 21 + Spring Boot 3.5.16，66 个业务 `@RestController` + 1 个 `@RestControllerAdvice`）｜基线 `0ba7f12c`（2026-10-17）｜审计日期 2026-10-18
- 基准：`AGENTS.md`《强制 RESTful API 规范》《雪花 ID 精度与跨端契约》｜性质：**只读审计**，未改任何业务代码/配置/迁移/测试
- 严重度：**P0** 数据错乱/越权｜**P1** 调用方功能故障｜**P2** 规范偏离｜**P3** 风格

> 口径：端点数字由源码注解**结构化解析**（括号配对 + 常量求值）得到，非 grep 行数。
> 交叉校验：`grep` 映射注解 322 行 − 类级 `@RequestMapping` 63 行 = **259** 方法级端点。

---

## 一、端点全景

```bash
grep -rn "@RequestMapping\|@GetMapping\|@PostMapping\|@PutMapping\|@PatchMapping\|@DeleteMapping" \
  src/main/java --include=*.java | wc -l    # 322（含类级）
grep -rn "@RequestMapping" src/main/java --include=*.java | wc -l   # 63 → 端点 = 322 - 63 = 259
```

**方法分布与版本前缀**

| 方法 | 端点 | 占比 | 版本前缀项 | 值 |
| --- | ---: | ---: | --- | --- |
| `GET` | 115 | 44.4% | 含 `ApiVersion.V2_PREFIX` | **259 / 259 = 100%** |
| `POST` | 58 | 22.4% | 未版本化端点 | 0 |
| `PUT` | 41 | 15.8% | 查询参数版本号 | 0 |
| `DELETE` | 31 | 12.0% | 同路径隐式版本分支 | 0 |
| `PATCH` | 14 | 5.4% | 类级前缀常量拼接 / 字面量硬编码 | 63 / 0 |
| **合计** | **259** | 100% | — | — |

读 115 / 写 144（`POST+PUT+PATCH+DELETE`），读写比 0.80:1。写端点占 55.6%，API 面以命令型业务为主——这解释了为何状态码与幂等契约权重高于普通 CRUD 服务。

```bash
grep -rn "@RequestMapping(ApiVersion.V2_PREFIX" src/main/java --include=*.java | wc -l  # 63
grep -rn '@RequestMapping("/v2' src/main/java --include=*.java | wc -l                  # 0（无字面量硬编码）
```

**对外真实路径是 `/api/v2.0/...`（三处独立证据）**——`V2_PREFIX` 只是应用内前缀，`context-path` 才是对外前缀：

| 证据 | 位置 | 内容 |
| --- | --- | --- |
| 服务端 context-path | `src/main/resources/application.yml:7` | `context-path: /api` |
| 前端基址 | `aries/src/utils/env.ts:22-24` | `apiBase \|\| '/api'` → `` `${apiBase}/v2.0` `` |
| 反向代理 | `deploy/nginx/leo.conf:26-27` | `location /api/ { proxy_pass http://127.0.0.1:11211/api/; }`（不剥前缀） |

⇒ 对外路径 = `/api` + `/v2.0` + `/资源` = `/api/v2.0/sales-orders`。AGENTS.md 的 `/api/v2` 与实现的 `/api/v2.0` 前缀语义一致（次版本号形态），**版本化规范达标**。

**模块分布**（41 个模块）：`master/material` 20（读9/写11）、`auth` 15（5/10）、`market/quotation` 15（6/9）、`purchase/order` 14（7/7）、`sales/order` 12（5/7）、`security/rbac` 10（4/6）；`attachment`/`statement/customer`/`statement/freight`/`system/printtemplate` 各 9，`logistics/bill`/`market`/`master/project`/`sales/returns`/`system/company` 各 8，`purchase/inbound`/`sales/contract`/`sales/outbound` 各 7，`finance/{receipt,payment,ledgeradjustment}`、`master/{carrier,customer,supplier,warehouse}` 各 6，其余 18 个 ≤5。端点最多：`V2PurchaseOrderController`/`V2SalesOrderController` 各 12。

**D-1（P3）注释中 `/api/v2` 与 `/api/v2.0` 混用**：

```bash
grep -rn '/api/v2' src/main/java --include=*.java | grep -v OpenApiConfig
# V2ValueAliasController.java:32                     /api/v2/value-aliases            ← 缺 .0
# V2ProjectPricingQuoteResolutionController.java:27  /api/v2/material-price-matches   ← 缺 .0
# AttachmentResponseAssembler.java:45                "/api/v2.0/attachments/" + id    ← 正确
# HealthPageRenderer.java:38                         /api/v2.0/system/health          ← 正确
```

运行时无影响（注释不参与路由），但照注释写调用方会生成 404 URL。建议统一为 `/api/v2.0`。

---

## 二、路径违规

**判定口径**（避免把名词词根误判为动词）：
**A. 整段即动词**（`create/save/audit/print/export/download/upload/list/sync/submit/cancel/confirm/reverse/adjust/close/reopen/void`）= **违规**；
**B. 动词作名词词根**（`print-exports`、`attachment-upload-sessions`、`import-batches`）= 否；
**C. 子资源动作模式**（`POST /{id}/audits`、`PATCH /{id}/status`）= 否（**推荐**）。kebab-case 全覆盖，0 个驼峰/下划线/大写路径段。

### A 类违规：仅 2 条，均为存量

| # | 文件:行号 | 方法 | 实际路径 | 违规段 |
| --- | --- | --- | --- | --- |
| V-1 | `src/main/java/com/leo/erp/attachment/web/V2AttachmentController.java:55` | `POST` | `/api/v2.0/attachments/upload` | `upload` |
| V-2 | `src/main/java/com/leo/erp/finance/cashledger/web/V2CashLedgerController.java:46` | `GET` | `/api/v2.0/cash-ledger/export` | `export` |

```bash
git blame -L 55,55 --date=short -- src/main/java/com/leo/erp/attachment/web/V2AttachmentController.java
# ^9f992397  2026-04-27  @PostMapping(value = "/upload", consumes = MULTIPART_FORM_DATA_VALUE)
git blame -L 46,46 --date=short -- src/main/java/com/leo/erp/finance/cashledger/web/V2CashLedgerController.java
# 283476584  2026-07-31  @GetMapping("/export")
git log -1 --format='%cs %s' 9f992397   # 2026-04-27 feat: Leo ERP backend - steel trade business platform
git log -1 --format='%cs %s' 28347658   # 2026-07-31 feat(api)!: 移除 V1 并统一 V2 契约
```

**判定：2 条均为「存量历史接口」，2026-07 之后无新增违规。** V-2 是「统一 V2 契约」重构时原样搬运的旧路径（只改类名，路径未动）。

### V-2 是 P1，不是风格问题

```java
@GetMapping("/export")                                    // V2CashLedgerController.java:46
@RequirePermission(PermissionCodes.CASH_LEDGER_EXPORT)
public ResponseEntity<byte[]> export(...) { return toDownloadResponse(cashLedgerService.exportExcel(...)); }
```

无写库副作用，**不违反「GET 不得写数据」**；但违反「GET 必须安全且幂等」的**可缓存性假设**：① 带独立 `EXPORT` 权限的动作被建模成 GET，代理/CDN 可缓存导出结果，构成权限绕过式泄露面；② 无法用「GET 免幂等键」的安全假设区分「只读查询」与「高成本导出」。

### C 类：46 个子资源动作端点（正确做法）

| 模式 | 数量 | 示例 |
| --- | ---: | --- |
| `POST /{id}/audits` | 6 | `V2SalesReturnController.java:113` |
| `PATCH /{id}/status` | 11 | `V2PurchaseOrderController.java:153` |
| `POST /{id}/completions` | 2 | `V2SalesOrderController.java:205` |
| `POST/DELETE /{id}/edit-locks` | 2 | `V2QuoteSheetEditLockController.java:42/62` |
| `confirmations`/`force-closures`/`xlsx-exports`/`delivery-verifications`/`password-resets`/`{importBatchNo}/rollbacks` | 各 1 | `V2CustomerStatementController.java:122`、`V2PurchaseOrderController.java:170`、`V2SalesOrderController.java:143/181`、`V2UserAdminController.java:94`、`V2ImportBatchController.java:38` |

**风格并存程度（量化）**：C 类 46（占写端点 144 的 31.9%）、A 类 2（1.4%）、常规 CRUD 96（66.7%）。
**正确 : 错误 = 46 : 2 = 23 倍。** 规范在新增代码上**确实生效**，问题集中在两处未清理的历史端点。

**最有力证据**：`POST /api/v2.0/attachments/upload`（V-1 违规）与 `POST /api/v2.0/attachment-upload-sessions`（合规资源化替代）**同时存在**——同一功能域已完成资源化改造，旧端点未下线。

**P3**：`V2PurchaseOrderController.java:113` 用 `GET /warehouse-recommendations`（名词，合规），`:122` 却用 `GET /pickup-list-preview`（`-preview` 后缀）。语义相近却一名词一后缀，建议统一为 `GET /purchase-orders/pickup-list-previews`。

---

## 三、状态码契约

| 契约项 | 分子 | 分母 | 覆盖率 | 判定 |
| --- | ---: | ---: | ---: | --- |
| `POST` 创建返回 201 | 48 | 58 | **82.8%** | 达标（10 例外非创建语义） |
| `POST` 创建带 `Location` | 43 实际生效 | 48 | **89.6%** | **P1** 见下 |
| `DELETE` 返回 204 | 31 | 31 | **100%** | 达标 |
| 异步返回 202 | 2 | 2 | **100%** | 达标 |
| 错误统一 ProblemDetail | 25 handler | 25 | **100%** | 达标 |

**201 覆盖：两套写法并存（P3）**——`@V2Created` 28 处 + `created()` 45 处，去重后 48 个 `POST` 声明 201，其中 **27 个同时用两者**。冲突风险：若加了 `@V2Created` 却 `return ResponseEntity.ok(...)`，Spring 以 `@ResponseStatus` 为准，产生「状态 201 但 body 是 200 语义」的不一致。建议收敛为只用 `created()`（它同时给出 `Location`）。

### `V2ResponseSupport.created()` 反射缺口（P1）

`src/main/java/com/leo/erp/common/api/V2ResponseSupport.java:29-60`：

```java
private static String resolveId(Object body) {
    Object id = invokeIdAccessor(body, "id");               // 先试 id()
    if (id == null) id = invokeIdAccessor(body, "getId");   // 再试 getId()
    return id == null ? null : id.toString();
}
private static Object invokeIdAccessor(Object body, String methodName) {
    try { return body.getClass().getMethod(methodName).invoke(body); }
    catch (NoSuchMethodException ex) { return null; }
    catch (IllegalAccessException | InvocationTargetException ex) {
        throw new IllegalStateException("无法读取新建资源标识", ex);
    }
}
```

**三个后果**：

1. **静默降级为「201 但无 `Location`」**——`id == null` 时直接 `ResponseEntity.status(CREATED).body(body)`，不发 Location、不打日志。
2. **4 个端点必然降级**（响应 DTO 经逐一 `grep` 确认无 `id` 组件）：

| 调用点 | 路径参数 | 响应 DTO | 缺 id |
| --- | --- | --- | --- |
| `V2MaterialImportController.java:55` | `/material-imports` | `MaterialImportResultResponse` | ✅ 只有统计计数 |
| `V2MaterialImportController.java:71` | `/material-imports/previews` | `MaterialImportPreviewResponse` | ✅ 无 `id` |
| `V2ImportBatchController.java:43` | `/import-batches/{no}/rollbacks` | `MaterialBatchRollbackResponse` | ✅ 只有 String `importBatchNo` |
| `V2InventoryController.java:87` | `/inventory/backfills` | `InventoryBackfillResponse` | ✅ 无 `id` |

   45 个调用点中 36 个响应体带 `id`（record 自动生成 `id()`，可命中）；扣除 4 个确定降级点，**`Location` 实际有效率 43/48 = 89.6%**（另 3 个未走 `created()` 的 201 端点自行构造了 Location：`V2AttachmentUploadSessionController.java:57`、`V2MasterDataCodeIssuanceController.java:40`；`V2AttachmentManifestExportController.java:44` 返回文件流、本就不该有 Location）。
3. **反射异常变 500 → 重复创建**：`IllegalStateException` 被 `GlobalExceptionHandler.java:472` 的 `@ExceptionHandler(Exception.class)` 兜底成 **500**。此时资源**已创建并提交**，调用方收到 500 后重试 ⇒ **重复创建**。这是本项核心风险。

**修复**：加 `created(path, body, Function<T,Object> idExtractor)` 重载（编译期强类型、无反射）；4 个无 id 端点显式声明「不返回 Location」；`IllegalStateException` 改降级 + `log.warn`。

### 10 个非 201 的 POST：逐个判定

| 端点 | 文件:行号 | 实际 | 判定 |
| --- | --- | --- | --- |
| `POST /auth/login`｜`/refresh`｜`/logout` | `V2AuthController.java:38/45/57` | 200/200/204 | **合规**（会话/令牌，非资源创建） |
| `POST /users/{id}/password-resets`｜`/quote-sheets/{id}/edit-locks` | `V2UserAdminController.java:94`、`V2QuoteSheetEditLockController.java:42` | 204/200 | **合规**（一次性动作 / 幂等 upsert） |
| `POST /module-exports`｜`/material-exports`｜`/sales-orders/{id}/xlsx-exports` | `V2ModuleExportController.java:74`、`V2MaterialExportController.java:51`、`V2SalesOrderController.java:143` | 200 | **P2** 同步返回裸文件流 |
| `POST /print-exports` | `V2PrintExportController.java:55` | **200 或 201** | **P1** 见下 |

**`print-exports` 双状态码（P1）**——`V2PrintExportController.java:68-79`：

```java
public ResponseEntity<?> create(@Valid @RequestBody @NotNull PrintRecordRequest payload) {
    PrintOutput output = exportConcurrencyGuard.execute(...);
    if (output.kind() == PrintOutput.Kind.PDF) {
        return pdfFileResponse(output);                    // 200 + application/pdf
    }
    return V2ResponseSupport.created("/print-exports", PrintRecordOutputResponse.from(output));  // 201 + Location
}
```

**同一 URL、同一请求，返回两种状态码 + 两种 Content-Type + 两种 body 类型**，签名还是通配符 `ResponseEntity<?>`。调用方无法静态建模，OpenAPI 只能生成 `anyOf`。建议拆分：`print-exports` 返回资源表示（201），文件走 `GET /print-exports/{id}/content`——符合 AGENTS.md「文件内容是资源的表示」。

### 204 / 202 / 错误契约：全部达标

**204（DELETE）31/31 = 100%**：`@V2NoContent` 23 处 + `noContent()` 31 处，典型实现（`V2CustomerController.java:72-77`）三件套齐全（`@V2NoContent` + `noContent()` + `ResponseEntity<Void>`）。
**202（异步）2/2 = 100%**：`V2SteelQuoteSyncController.java:69`、`V2SteelQuoteBackfillController.java:57`，均带 `@ApiResponse(responseCode="202")`。

**错误契约 25/25 = 100%**。`ApiProblemFactory.java:41-70` 生成标准 RFC 9457：

```java
ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, resolvedDetail);
problem.setType(URI.create("urn:leo:problem:" + problemType(errorCode)));
problem.setTitle(errorCode.getMessage());
problem.setInstance(URI.create(request.getRequestURI()));
problem.setProperty("code", errorCode.getCode());
problem.setProperty("timestamp", ...);
problem.setProperty("traceId", MDC.get("traceId"));
problem.setProperty("errors", fieldErrors);
```

`GlobalExceptionHandler.java` 25 个 `@ExceptionHandler`，状态码精度完整：400（91/155/169/182/195）、401（326）、403（414）、404（419）、405（208）、406（221）、409（304）、412（286）、413（234）、415（247）、422（104/120/133）、503（342/388）、500（472）。

**错误输出是单一路径**：`ApiErrorResponseWriter.java:29-37` 内部委托 `problemFactory.create(...)`；拦截器（`IdempotencyRequiredInterceptor.java:52`）与过滤器（`HttpIdempotencyFilter.java:192/221`）都走它——**不存在第二套错误 JSON 生成器**，无格式漂移风险。

---

## 四、响应体契约

**Entity / Map / Object 暴露：0 处**

```bash
grep -rn "public Map<\|public Object \|ResponseEntity<Map<\|ResponseEntity<Object>" \
  src/main/java --include=*.java | grep -i controller    # → 0 行
```

259 个端点返回类型仅 5 类：`ResponseEntity<Void>` 34、`ResponseEntity<byte[]>` 7、`*Response` record ~190、`PageResponse<T>` 40、`List<*Response>` 19。`grep` 出的 `Map<Long,...>` 全在 `service/`、`repository/` 层，属内部方法，不构成 HTTP 契约。**唯一例外（P3）**：`V2PrintExportController.java:68` 的 `ResponseEntity<?>`。

**`code/message/data` 私有包装：0 处**

```bash
grep -rn "code.*message.*data\|ApiResponse<\|Result<\|R<" src/main/java --include=*.java \
  | grep -v "ErrorCode\|ApiProblem\|responseCode"   # → 0 行
```

成功响应统一「资源 DTO 裸返回」或 `PageResponse<T>`；错误统一 `ProblemDetail`。两层职责分离清晰，**与 AGENTS.md 完全一致，是本项最佳实践**。

**分页统一 PageQuery / PageResponse**：41 个端点用 `PageQuery`，40 个返回 `PageResponse<T>`（97.6%）。

```java
public record PageQuery(@Min(0) int page, @Min(1) @Max(200) int size, String sortBy, String direction) {}
public record PageResponse<T>(List<T> content, long totalElements, int totalPages,
                              int currentPage, int pageSize, boolean hasMore) {}
```

边界校验到位，且有**稳定 tiebreaker**（`PageQuery.java:32-36`）：

```java
Sort sort = Sort.by(sortDirection, property);
if (!UNIQUE_TIEBREAKER.equals(property)) { sort = sort.and(Sort.by(sortDirection, "id")); }
```

**排序白名单：强制生效，0 注入风险**——32 个列表、1~14 个允许字段（`PageSortFieldCatalog.java`），**与使用方完全对齐**（交叉校验：`used keys: 32 / catalog keys: 32`，无孤儿键、无死条目）。执行链路 `PageQueryArgumentResolver.java:55-62` → `normalizeSortBy`：

```java
if (!normalized.matches("[A-Za-z][A-Za-z0-9_]*")) throw ...("sortBy 格式不合法");            // 格式闸门
if (allowedSortFields != null && !allowedSortFields.contains(normalized)) throw ...("sortBy 不支持当前列表");
```

**5 处拼接排序串全部有白名单或全常量，0 处裸拼接**：

| 位置 | 白名单方式 |
| --- | --- |
| `FinanceOverviewQueryRepository.java:423` | `SORT_COLUMNS.getOrDefault(sortBy, "outstanding_amount")` |
| `InventoryBalanceQueryService.java:147` | `SORT_EXPRESSIONS.get(sortBy)`，null 回落 `DEFAULT_ORDER` |
| `FreightBillSalesOrderCandidateQueryRepository.java:128` / `PurchaseOrderInboundCandidateQueryRepository.java:119` / `SalesOrderOutboundCandidateQueryRepository.java:122` | 均 `SORT_COLUMNS.getOrDefault(sortBy, "id")` |
| `CashLedgerQueryRepository.java:435` | 方向仅 asc/desc 归一化，列名硬编码常量 |
| `PrintScriptService.java:334` | `PRINT_ITEM_COMPARATORS.get(sortBy)`，未命中抛 422 |
| `ModuleExportService.java:94` | 全常量 `ORDER BY id DESC` |

**这项远超规范要求**——AGENTS.md 只要求「禁止拼接 SQL 排序字段」，实现做到了「拼接但值域受控」。

**分页排序唯一缺口（P2）**：`V2PrintPreviewController.java:42` 的 `GET /print-previews/items` 用 `PageQuery` 但**无 `sortFieldKey`**（41 个里唯一一个），导致 `allowedSortFields = null` → **跳过白名单校验**。但仍安全，因 `PrintScriptService.java:334-347` 自带兜底（`PRINT_ITEM_COMPARATORS.get(sortBy)` 未命中抛 422）。**P2 理由**：不是注入漏洞（双重兜底生效），而是**白名单分散两处**，未来改动易退化。该端点还是「伪分页」——全量读入内存再 `subList`（`PrintScriptService.java:326-331`）；`recordIds` 上限 200 可控，建议 OpenAPI 注明。

**OpenAPI 注解覆盖不足（P2）**：

| 注解 | 覆盖 | 分母 | 覆盖率 |
| --- | ---: | ---: | ---: |
| `@Operation` | 152 | 259 | **58.7%** |
| `@Tag`（类级） | 38 | 66 | 57.6% |
| `@Parameter` | 15 | ~135 参数 | 11.1% |
| `produces=` / `consumes=` | **1** / 5 | 259 | 0.4% / 1.9% |

**107 个端点无 `@Operation`**，集中在 `V2FreightBillController`（8 全缺）、`V2LengthPremiumController`（5）、`V2QuoteRegionController`（5）、`V2UserAccountController`（5）。`produces=` 仅 1 处（`HealthPageController.java:22` 的 `text/html`）；文件下载端点在 OpenAPI 里没有正确的媒体类型声明。AGENTS.md 要求「每个接口必须声明正确的 `Content-Type`、`Accept` 和 `@Operation` 语义」——**这项不达标**。

---

## 五、雪花 ID 专项

**全局 Long→String：生效，三重保障**——`JacksonConfig.java:38-40` 用 `serializerByType(Long.class, ToStringSerializer.instance)` 与 `serializerByType(Long.TYPE, ToStringSerializer.instance)` 双注册（包装类型 + 基本类型），是 `ObjectMapper` 级全局规则，对所有 `Long`/`long` 生效，无需逐字段标注。**全局生效确认成立**：

| 层 | 机制 | 位置 |
| --- | --- | --- |
| 序列化 | `Long`/`long` → 十进制字符串 | `JacksonConfig.java:38-39` |
| 反序列化 | 字符串直通；数值仅接受 `≤ 2^53-1`，超出 400 | `JacksonConfig.java:58-92` |
| OpenAPI | 所有 `Long` schema 改 `type: string` | `LongToStringModelConverter` + `OpenApiConfig.java:66-68` |

契约测试锁定（`src/test/java/com/leo/erp/common/config/JacksonIdPrecisionContractTest.java`，8 用例）：

```java
payload.id = 9223372036854775807L;
assertThat(json).contains("\"id\":\"9223372036854775807\"");
assertThat(json).doesNotContain("\"id\":9223372036854775807");
```

**DTO 局部覆盖成数值：0 处 ID 违规**——`@JsonFormat` 0 处；`@JsonSerialize` 2 处但都是非 ID 的 `fileSize`（`AttachmentResponse.java:18`、`AttachmentUploadResponse.java:15`）。`fileSize` 是字节数（远小于 2^53），有注释与测试双重约束：`AttachmentResponse.java:12-16` 说明「全局 Long→字符串只用于 ID 精度保护，fileSize 必须保持 JSON number，否则前端 Zod 按 number 声明的契约整条失败」；`AttachmentJsonSerializationTest.java:76-82` 断言 `"fileSize":8550` 且 `doesNotContain("\"fileSize\":\"8550\"")`，同时断言 `"id":"9223372036854775807"` 仍是字符串；`NumericLongSerializer.java:15-16` 明确前提「取值远小于 2^53-1」。

**抽样 10 个响应 DTO 的 `Long *Id`**（122 个候选，`random.seed(7)` 固定种子可复现）：

| # | DTO | Long ID 字段 | 字段级注解 | 输出 |
| --- | --- | --- | --- | --- |
| 1 | `MaterialHistoryResponse` | `id, materialId` | 无（靠全局） | 字符串 ✅ |
| 2 | `InventoryTransactionResponse` | `id, materialId, warehouseId, sourceDocumentId, sourceItemId` | 有（显式重申） | 字符串 ✅ |
| 3 | `LedgerAdjustmentRequest` | `counterpartyId, settlementCompanyId, projectId` | 无（**请求** DTO） | 见下 |
| 4 | `ProjectRequest` | `customerId, settlementCompanyId` | 无（**请求** DTO） | 见下 |
| 5 | `RoleResponse`｜`CurrentAccountResponse`｜`FileDownloadResponse`｜`CompanySettlementAccountResponse` | 各含 `id` 或 `recordId` | 无 | 字符串 ✅ |
| 9 | `SalesOrderItemResponse` | 6 个 ID 字段 | 有（6 字段全显式 ToStringSerializer） | 字符串 ✅ |
| 10 | `PaymentAllocationRequest` | `id, sourceStatementId, sourceFreightStatementId` | 无（**请求** DTO） | 见下 |

**响应 DTO 抽检 6/6 全部输出字符串，0 违规。** 注意 #1/#5~#8 这类「不显式标注」的反而更安全——完全依赖全局规则，无局部覆盖失配可能；#2/#9 的显式标注属**冗余但无害**的防御性重复。

**入参 ID 校验：主要短板**

| 入口 | 现状 | 覆盖率 | 严重度 |
| --- | --- | ---: | --- |
| JSON 请求体严格拒绝数值 ID | 34 个含 `Long *Id` 的请求 DTO 中仅 3 个用 `SnowflakeIdStringDeserializer`；另 3 处 `@JsonDeserialize(contentUsing=...)` 处理数组元素，合计 6 处引用 | **3/34 = 8.8%** | **P2** |
| 路径参数 `@PathVariable` 正整数校验 | 132 个端点 / 135 个参数，仅 6 个加 `@Positive` | **6/132 = 4.5%** | **P1** |
| 查询参数 ID 校验 | 28 处 `@Positive Long`，但同 controller 内不一致 | 部分 | **P2** |

- **请求体**：严格版只覆盖 `PrintRecordRequest.recordId`、`PrintTemplateRequest.settlementCompanyId`、`ProjectPrintPreferenceRequest.projectId/templateId`；其余 91.2% 依赖「≤2^53-1 数值也放行」的兼容规则（`JacksonConfig.java:62-64` 注释明示为历史兼容）。不是漏洞（超范围数值被拒），但与「新增 API 不得接受 JSON number」有偏差。
- **路径参数（P1）**：加了的 6 个是 `V2MaterialCategoryController.java:51/64/70`、`V2PrintTemplateController.java:63/70/81`；未加的典型为 `V2CustomerController.java:55`、`V2PurchaseOrderController.java:129`、`V2SalesOrderController.java:129`、`V2FreightBillController.java:78`、`V2UserAdminController.java:60`。**精确结论**：「`≤ Long.MAX_VALUE`」由 Java 类型系统天然保证——超范围输入在类型转换阶段抛 `MethodArgumentTypeMismatchException`，被 `GlobalExceptionHandler.java:155` 转成 400，**这部分达标**。缺失的**只有「正整数」这一半**：`-1`、`0` 能被 `parseLong` 接受并传入业务层，部分路径会落到 500 而非 400，污染错误率指标。修复成本极低：`@PathVariable @Positive Long id`，或加统一 resolver。
- **查询参数**：`V2PurchaseOrderController.java:113` 有 `@Positive` ✅，但 `V2CashLedgerController.java:38/39` 的 `settlementCompanyId`、`counterpartyId` 都没有 ❌。

**OpenAPI ID：type 正确，但缺约束（P2）**——`type: number` 误写 **0 处**：

```bash
grep -rn 'type\s*=\s*"\(number\|integer\)"\|int64' src/main/java --include=*.java
# 仅 1 处：V2ModuleExportController.java:62  @Schema(type="string", format="binary")  ← 文件下载
```

`LongToStringModelConverter` 在解析链末尾统一改写所有 `Long` → `type: string`，故不存在漏标可能（测试：`LongToStringModelConverterTest.java`）。**但 AGENTS.md 要求的「正整数正则 + 19 位示例」不达标**：`grep -rn '"[0-9]\{19\}"'` → 0 个示例；`pattern=` 约束 0 处。影响：生成的前端类型里 ID 只是 `string`，无法在类型层阻止 `"abc"`。

---

## 六、幂等与权限

### `Idempotency-Key`：122/144 = 84.7%

机制位于 `common/idempotent/`：`IdempotencyRequired`（标记）、`IdempotencyRequiredInterceptor`（缺失键返回 **422** 并阻断）、`HttpIdempotencyFilter`（请求指纹去重、重放）、`HttpIdempotencyService`（232 行，存储与状态机）；注册点 `common/config/WebMvcConfig.java:31`。

```java
boolean required = handlerMethod.hasMethodAnnotation(IdempotencyRequired.class)
        || handlerMethod.getBeanType().isAnnotationPresent(IdempotencyRequired.class);
if (!required || !WRITE_METHODS.contains(request.getMethod()) || isMultipart(request)) return true;
if (resolveKey(request) != null) return true;
errorResponseWriter.write(request, response, UNPROCESSABLE_ENTITY, BUSINESS_ERROR,
        "缺少幂等键：该写操作必须携带请求头 " + HttpIdempotencyFilter.HEADER);
```

支持双头（`HEADER` + `LEGACY_HEADER`），multipart 由 Filter 按文件指纹兜底。

**22 个未覆盖写端点中高危且必须补的（P1）**：`POST /ledger-adjustments` 族 4 个（**财务调整可重复入账**）、`POST /sales-contracts` 族 4 个（**合同重复创建**）、`POST/PUT/DELETE /quote-sheets*` 7 个（报价单重复创建）、`PUT/DELETE /purchase-order-items/{itemId}/issued-done` 2 个（状态翻转）。对比 `POST /payments`、`POST /receipts` 都有幂等键——**同类实体保护不一致**，财务与合同恰是最不能重复创建的实体却缺失，属实质性缺陷。

### 授权覆盖：实质 144/144

**`@PreAuthorize` 使用量：0 处**（唯 1 处出现在 `PermissionMethodSecurityConfig.java:17` 的 Javadoc 里）。项目用自研 `@RequirePermission` + `AuthorizationManagerBeforeMethodInterceptor`（`PermissionMethodSecurityConfig.java:31-40`），`PermissionAuthorizationManager` 支持 `资源:*` 通配与 `*` 超级权限。**这是合理设计选择，不判违规**——AGENTS.md 未强制 `@PreAuthorize`。

写端点有 `@RequirePermission` **137/144 = 95.1%**；读端点 **108/115 = 93.9%**；`@PublicAccess` 2 个（`GET /health`、`GET /version`）。

**7 个未覆盖写端点，全部有替代性安全控制**：

| 端点 | 位置 | 判定 |
| --- | --- | --- |
| `POST /auth/login`｜`/refresh`｜`/logout` | `V2AuthController.java:38/45/57` | **合规**：类级 `@PublicAccess` |
| `PUT /account`｜`/account/password`｜`/account/preferences` | `V2UserAccountController.java:44/50/63` | **合规**：主体自指，`currentUserId(principal)` 限定范围 |
| `POST /setup/account` | `V2InitialSetupController.java:35` | **合规**：`InitialSetupTokenFilter` + nginx `allow 127.0.0.1`（`deploy/nginx/leo.conf:10-15`）三层防护 |

**⇒ 授权实质覆盖率 144/144 = 100%。** 唯一实质缺口（P2）：`GET /runtime-config`（`V2RuntimeConfigController.java:28`）返回系统配置但无权限码，建议补 `RUNTIME_CONFIG_READ`。

**附带排除项（无风险）**：6 个未加 `@Validated` 的 controller（`V2MaterialPriceMatchController`、`V2SteelQuote*`、`V2ProjectPricingQuoteResolutionController`）**均未使用任何参数约束注解**——`@Positive/@Min/@Max/@Size` 命中数为 0，因此不存在「约束被静默忽略」的隐患。真正使用 `@Valid @RequestBody` 的两个（`V2SteelQuoteBackfillController`、`V2SteelQuoteSyncController`）校验发生在 DTO 内部，方法级 `@Validated` 本就不必要。

### 审计日志：55/144 = 38.2%（P1）

有 `@OperationLoggable` 或 `@DomainEventAudited` 的写端点仅 **55/144**，未覆盖 89。分工（`OperationLogMetadataResolver.java:14-17`）：`@DomainEventAudited` 37 处（带 `businessNo`/`recordId`）、`@OperationLoggable` 18 处（带 `moduleName`/`actionType`）；注册点 `OperationLogWebConfig.java:18` → `OperationLogInterceptor`（`preHandle` 存元数据、`afterCompletion` 落库，含失败路径）。

**89 个无审计的写端点中，必须补的高危族**：

| 端点族 | 数量 | 为什么必须审计 |
| --- | ---: | --- |
| `/roles*`、`/users/{id}/roles`、`DELETE /users/{id}` | 6 | **权限变更，越权追溯硬性要求** |
| `/ledger-adjustments*` | 4 | 账务调整 |
| `/payments*`、`/receipts*` | 8 | 资金安全 |
| `/sales-contracts*` | 4 | 合同金额变更 |
| `/customer-statements*`、`/freight-statements*` | 10 | 结算依据 |

**最严重的是权限变更无审计**——无法回答「谁在何时给了谁什么权限」。反证：`/purchase-orders`、`/sales-orders`、`/purchase-inbounds`、`/freight-bills`、`/company-settings`、`/print-*` 都做对了，说明**规范存在但落地不完整**。

### 状态机与并发控制：达标

`StatusTransitionPermissionGuard.java` 强制受控动作权限（`资源:audit`/`:unaudit`/`:confirm`/`:complete`），防止仅持 `资源:update` 的主体绕过审核。`CrudStatusGuard.java:107-121` 用**显式转移表**校验：

```java
if (invalidStatus || !allowedTransitions.contains(StatusTransition.of(currentStatus, nextStatus))) {
    throw new BusinessException(BUSINESS_ERROR, "当前单据状态不能从「" + currentStatus + "」变更为「" + nextStatus + "」");
}
```

`assertRequestDidNotWriteFinalStatus`（`:96-105`）进一步阻止通过普通更新接口写入完成态。**无越权/跳状态风险。**并发控制：`@Version` 乐观锁 + 乐观锁冲突 → **412**（`GlobalExceptionHandler.java:286`）+ 悲观锁冲突 → **409**（`:304`）+ 服务端有界重试（`OptimisticLockRetryExecutor` / `SalesOrderRetryProperties`）。

### 数据范围：单租户模型（P2，需显式记录）

| 机制 | 存在 | 位置 |
| --- | --- | --- |
| 多租户隔离 / 部门组织数据范围 | ❌ | —（单公司 ERP） |
| 资源所有权校验 | ⚠️ 仅附件 | `AttachmentRecordAccessService.java:81-85` |
| 字段级权限 | ✅ | `@PermissionField` + `PermissionDecimalSerializer` |

附件是唯一做资源所有权的模块：

```java
Long ownerUserId = attachment.getOwnerUserId() == null ? attachment.getCreatedBy() : attachment.getOwnerUserId();
if (principal == null || !Objects.equals(principal.id(), ownerUserId)) { /* 拒绝 */ }
```

其余 65 个 controller **无资源级所有权校验**：`GET /sales-orders/{id}` 只校验 `SALES_ORDERS_READ`，不校验「这单是不是我的」。**P2 而非 P1**：这是单体单公司 ERP，权限模型是「角色=功能权限」而非「行级数据权限」，同公司销售员互相可见属业务预期。若未来要做分公司隔离需全局改造，建议把该决策显式写进已存在的 `leo/docs/security-trust-model.md`。

---

## 七、摘要与修复路线图

### 发现总表

| # | 严重度 | 问题 | 关键证据 | 涉及 |
| --- | --- | --- | --- | --- |
| 1 | **P1** | `created()` 反射读 id，4 端点必然无 `Location`；异常变 500 致重复创建 | `V2ResponseSupport.java:29-60`、`GlobalExceptionHandler.java:472` | 4 降级 + 45 调用点 |
| 2 | **P1** | `POST /print-exports` 同一 URL 返回 200 或 201，两种 Content-Type | `V2PrintExportController.java:68-79` | 1 |
| 3 | **P1** | 路径参数正整数校验仅 6/132 | 全仓 `@PathVariable Long id` | 126 |
| 4 | **P1** | 财务/合同/报价创建无幂等键（同族实体有） | `V2LedgerAdjustmentController.java:67`、`V2SalesContractController.java:72` | 15 |
| 5 | **P1** | 权限变更与资金类写操作无审计日志 | `V2RoleController.java:59-86`、`V2PaymentController.java:68` | 89（约 32 属高危） |
| 6 | **P1** | `GET /cash-ledger/export` 用 GET 建模高成本导出 | `V2CashLedgerController.java:46` | 1 |
| 7 | **P2** | 4 个导出端点用 200 返回裸文件流而非 201 资源 | `V2ModuleExportController.java:74` 等 | 4 |
| 8 | **P2** | OpenAPI `@Operation` 覆盖 58.7%、`produces=` 仅 1 处 | 107 端点无注解 | 107 |
| 9 | **P2** | OpenAPI 缺 19 位示例与正整数正则 | 全仓 0 处 | 全部 ID 字段 |
| 10 | **P2** | JSON 请求体严格拒绝数值 ID 仅 3/34 | `SnowflakeIdStringDeserializer` 6 处引用 | 31 DTO |
| 11 | **P2** | `print-previews/items` 无 `sortFieldKey`（白名单分散两处） | `V2PrintPreviewController.java:42` | 1 |
| 12 | **P2** | 查询参数 ID 校验同 controller 内不一致 | `V2CashLedgerController.java:38-39` | 多处 |
| 13 | **P2** | `GET /runtime-config` 无权限码 | `V2RuntimeConfigController.java:28` | 1 |
| 14 | **P3** | `@V2Created` 与 `created()` 双写 | 见第三节 | 27 |
| 15 | **P3** | `/pickup-list-preview` 命名不一致 | `V2PurchaseOrderController.java:122` | 1 |
| 16 | **P3** | 注释中 `/api/v2` 与 `/api/v2.0` 混用 | 2 处注释 | 2 |

**P0 = 0，P1 = 6，P2 = 7，P3 = 3。** 路径违规 2 条（均存量）、状态码实质违规 5 条、响应体违规 0 条、雪花 ID 入参违规 3 类。

### 达标项（可作基线，无需整改）

| 维度 | 结果 | 维度 | 结果 |
| --- | --- | --- | --- |
| 版本化覆盖 | **259/259 = 100%** | Entity/Map/Object 暴露 | **0 处** |
| `code/message/data` 包装 | **0 处** | DELETE → 204 | **31/31 = 100%** |
| 错误契约 ProblemDetail | **25/25 = 100%**，单一路径 | SQL 排序注入 | **0 处裸拼接**，5/5 受控 |
| 雪花 ID 序列化（输出侧） | 全局生效 + 8 用例契约测试 | 状态机越权 | 显式转移表 + 动作权限守护 |
| 授权覆盖（实质） | **144/144 = 100%** | 子资源动作建模 | **46 处**（正确:错误 = 23:1） |

### 整改批次

**批次 A｜P1，1~2 天，低风险高收益（不改契约语义）**

1. `V2ResponseSupport` 加 `created(path, body, idExtractor)` 重载；4 个无 id 端点显式声明「无 Location」；`IllegalStateException` 降级为 `log.warn`。→ 消除「创建成功却 500 + 重复创建」。
2. 全局补 `@PathVariable @Positive Long id`（126 处机械替换）或加统一 argument resolver。→ 补上「正整数」语义。
3. `@IdempotencyRequired` 补到 `POST /ledger-adjustments`、`POST /sales-contracts`、`POST/PUT/DELETE /quote-sheets*`。→ 与同族实体对齐。
4. `@OperationLoggable`/`@DomainEventAudited` 先补**权限变更（6 个）**与**资金四族（付款/收款/调整/合同，20 个）**。→ 审计硬要求。
5. `POST /print-exports` 拆分：资源端点返回 201 + 资源表示，文件内容走 `GET /print-exports/{id}/content`。→ 消除双状态码。

**批次 B｜P2，3~5 天，需与前端联动**

6. OpenAPI 补齐：优先 107 个无 `@Operation` 端点；文件端点补 `produces`。
7. 所有 ID 字段加 `@Schema(type="string", pattern="^[1-9][0-9]{0,18}$", example="1800000000000000001")`。
8. 新 DTO 一律加 `SnowflakeIdStringDeserializer`；存量 31 个排期收紧（需前端确认已全部传字符串）。
9. 4 个导出端点改为「先创建导出资源（201）→ 下载内容」，或至少在 OpenAPI 声明 200 语义。
10. `print-previews/items` 补 `sortFieldKey = "print-item"` 并登记 Catalog；`GET /runtime-config` 补权限码。

**批次 C｜存量清理，需用户明确授权（破坏性）**

11. `POST /attachments/upload` → 统一切到已存在的 `POST /attachment-upload-sessions`，旧端点标记 `Deprecation` + `Sunset` 后下线。遵循 AGENTS.md「既有非 RESTful 接口不得在未获授权时直接破坏性改造」。
12. `GET /cash-ledger/export` → 改为 `POST /cash-ledger-export-requests`（202 + 任务资源）。
13. 统一注释 `/api/v2` → `/api/v2.0`，并在 AGENTS.md 注明以 `ApiVersion.V2_PREFIX` 为准。

### 一句话结论

> 这份后端的**契约骨架是高质量的**——版本化 100%、零 Entity/Map 暴露、错误统一 RFC 9457、SQL 排序零注入、雪花 ID 序列化有契约测试锁定；真正的风险不在"设计错"，而在**三个收口点没贯彻到底**：`created()` 用反射读 id 导致 4 个端点静默丢 `Location` 且异常会退化成 500 引发重复创建、路径参数正整数校验只做了 4.5%、财务管理类写操作既缺幂等键又缺审计日志。所有 P1 都是"补一致性"而非"重构架构"，批次 A 可在 1~2 天内以极低风险清除。

---

## 附录：外部规范依据（URL 均已实测 HTTP 200）与审计方法

| 规范 | URL | 用于论证 |
| --- | --- | --- |
| RFC 9457 Problem Details | https://www.rfc-editor.org/rfc/rfc9457.html | 错误契约：`type`/`title`/`detail`/`instance` + 扩展成员与 `application/problem+json` |
| Zalando RESTful API Guidelines | https://opensource.zalando.com/restful-api-guidelines/ | 第二节（MUST keep URLs verb-free / MUST avoid actions / MUST pluralize）；第三节（状态码）；`Location` 头；`Idempotency-Key` |
| Microsoft REST API Guidelines | https://github.com/microsoft/api-guidelines/blob/vNext/azure/Guidelines.md | 第三节（状态码与 `Location`）；第四节（响应体与分页） |
| Stripe Idempotent requests | https://docs.stripe.com/api/idempotent_requests | 6.1 幂等键语义与重放 |
| Google AIP-158 Pagination | https://google.aip.dev/158 | 4.3 分页须在最初提供、不可后加；不透明 page token |
| Google AIP-193 Errors | https://google.aip.dev/193 | 错误码精确性；403 检查先于 404 |

**审计方法**：解析脚本 `/home/instance/Gemini/tmp/api-audit/parse.py`（工作区临时产物，未进仓库）按括号配对抽取 `@XxxMapping` → 区分类级/方法级 → 求值 `ApiVersion.V2_PREFIX + "..."` → 拼完整路径。每条结论均附 `文件:行号` 或可粘贴执行的命令 + 输出摘要。未修改任何业务代码，`git status` 仅出现本报告文件。
