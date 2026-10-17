# 后端代码质量与测试门禁审计（05）

- 仓库 `leo/`（Java 21 + Spring Boot 3.5.16 + Maven 3.6.3，1060 个源文件 / 91158 行）｜基线 `0ba7f12c`（`chore(release): 13.2.1`）｜审计日期 2026-10-18
- **只读审计**：未改任何业务代码、配置、CI 或迁移脚本；本文件是唯一写入产物。
- 复现：除注明外命令均在 `leo/` 下执行；脚本在 `/home/instance/Gemini/tmp/quality-audit/`（未进仓库）。凡启发式统计均标注局限。

---

## 摘要

**这是一套"工具齐全、门禁有形、阈值缺位"的质量体系。** CI 覆盖编译/架构/静态分析/Flyway/全量测试/镜像冒烟，高于同类项目平均；真缺口不是"有没有门禁"，而是**门禁只拦 error 级、只拦 High 级、没有覆盖率阈值，因此可以长期全绿而质量不上升**。

**严重度：P0 = 0、P1 = 4、P2 = 6、P3 = 5。**

1. **覆盖率门禁已被删除，只剩报表。** `pom.xml:390-408` 的 JaCoCo 只有 `prepare-agent` + `report`，**无 `check`、无阈值**——曾有的 `LINE COVEREDRATIO ≥ 0.10` check 在 `657beee7`（"清理退役配置并收紧质量门禁"）中被删除且未恢复。三个 workflow 从不读取 `target/site/jacoco`（`grep -rn "jacoco" .github/workflows/*.yml` → 0）⇒ **新增 500 行零测试代码可通过全部 CI**。
2. **静态分析实际阻断面远小于表观面。** Checkstyle 18 条规则中仅 7 条 `error`，`violationSeverity=error` 使其余 11 条（含 `LineLength`/`MagicNumber`）永不阻断——实测 **615 告警 / 0 阻断**。SpotBugs `threshold=High` 对 1347 个类返回 **0 bug**，是阈值结果而非代码无瑕。
3. **测试规模大但判据弱。** 2949 个 `@Test`、测试/主代码行比 0.70；但仅 **45.9% 测试类含负路径断言**（`auth`/`system` 仅 25%），`@ParameterizedTest` 仅 3 个文件。

**同样明确的正面项**（不被上述问题掩盖）：`TODO/FIXME/HACK/XXX` **全仓 0 处**；`@Data` 在 `@Entity` 上 **0 处**（`@EqualsAndHashCode`/`@Builder` 亦 0）；`printStackTrace` **0 处**；`@SpringBootTest` **0 个**（无慢测试）；Flyway **V1–V189 连续、无跳号无重复、命名 100% 合规**；近 200 条提交 Conventional Commits **100% 合规**。本地离线编译、Checkstyle、SpotBugs、ArchUnit 边界、`flyway:validate` 全部实测通过。

### 质量指标仪表盘

| 维度 | 指标 | 值 | 判定 | 复现命令（`leo/` 下） |
| --- | --- | ---: | :--: | --- |
| 规模 | 主代码 文件 / 行 | 1060 / 91158（均值 86.0） | — | `find src/main/java -name '*.java' -exec cat {} + \| wc -l` |
| 规模 | 测试 文件 / 行 / 类 | 342 / 63460 / 340 | — | 同式换 `src/test/java` |
| 规模 | 测试 ÷ 主代码行 | 0.70 | ✅ | 63460/91158 |
| 坏味道 | >500 / >300 / <100 行 文件 | **7** / 45 / 765(72%) | ⚠️ | `find … -exec wc -l {} + \| awk '$1>500 && $2!="total"'` |
| 坏味道 | 方法 ≥40 / ≥60 / ≥80 / ≥100 行 | 57 / 15 / 5 / 3 | ⚠️ | `python3 tmp/quality-audit/methodlen2.py` |
| 坏味道 | Spring Bean 构造器 ≥8 依赖 | 11 类（最多 FreightBillService 11） | ⚠️ | 内联脚本（仅 bean，排除 DTO record） |
| 坏味道 | 重复率（8 行窗口） | **15.4%**；剔 DTO/实体 10.2%；仅 service 7.6% | ⚠️ | 8 行归一化窗口 + MD5 |
| 异常 | `catch (` / `catch (Exception` / `catch (RuntimeException` | 230 / **18** / 39 | ⚠️ | `grep -rn "catch (Exception" src/main/java \| wc -l` |
| 异常 | 吞异常（62 只 log + 5 空） | **67** | ⚠️ | `python3 tmp/quality-audit/swallow.py` |
| 异常 | `printStackTrace` / `catch (Throwable` | **0 / 0** | ✅ | `grep -rn printStackTrace src/main/java \| wc -l` |
| 异常 | 宽泛 catch 重抛未传 cause | 6（加密 3 处最关键） | ⚠️ | 内联脚本 |
| Lombok | `@Data` 在 `@Entity` / `@EqualsAndHashCode` / `@Builder`；`@Getter`/`@Setter`/`@Slf4j`/`@Value` | **0/0/0**；62/63/33/16 | ✅ | `grep -rn "@Data" src/main/java \| wc -l` |
| 实体 | `@Entity` / 带 `@Version` | 65 / 24（36.9%） | ⚠️ | `grep -rn "^@Entity" src/main/java \| wc -l` |
| 债务 | `TODO/FIXME/HACK/XXX`；中文债务词 | **0 / 0** | ✅ | `grep -rniE "\b(todo\|fixme\|hack\|xxx)\b" src/main/java \| wc -l` |
| 死代码 | 跨文件零引用顶层类型 | 8（**全为 Spring 配置类，误报**） | ✅ | 内联脚本 |
| 死代码 | 主+测试均零引用 public 方法 | **12** | ⚠️ | 见 §4.2 |
| 测试 | `@Test` / 断言调用 | 2949 / 6067（2.06/方法） | ✅ | `grep -rhoE 'assert[A-Z][A-Za-z]*\(' src/test/java \| wc -l` |
| 测试 | `@SpringBootTest`/`@Disabled`；`@DataJpaTest`；纯单元测试类 | **0/0**；29；287（84.4%） | ✅ | `grep -rn "@SpringBootTest" src/test/java \| wc -l` |
| 测试 | 含异常路径断言的测试类；无断言方法；`@ParameterizedTest` 文件 | **156/340=45.9%**；60（含误判）；3 | ⚠️ | `python3 tmp/quality-audit/noassert.py` |
| 门禁 | Checkstyle 告警/阻断；SpotBugs（threshold=High）；JaCoCo 阈值 | **615/0**；**0**；**无** | ⚠️⚠️❌ | `checkstyle:check` / `spotbugs:check` / `grep -n "COVEREDRATIO" pom.xml` |
| 门禁 | 离线编译 | ✅ 22.1s | ✅ | `mvn -o -q -DskipTests compile` |
| Flyway | 脚本/连续性/命名合规；`flyway:validate`；`db/seed` S1–S15 | 189 / V1–V189 连续 / 100%；✅ 退出码 0；15 脚本**从未被执行** | ✅✅⚠️ | `ls …/db/migration \| wc -l`；§7；`grep -rn "db/seed" src/main scripts .github` → 0 |
| 提交 | 近 200 条 Conventional Commits 合规；近 100 条 PGP 签名 | **200/200=100%**；86 G / 14 N（全是 `chore(release)`） | ✅⚠️ | `git log --format='%s' -200 \| grep -cE '^(feat\|fix\|…)'`；`git log --format='%G?' -100` |

---

## 第 1 节 坏味道清单

分类沿用 Fowler《重构》Bloaters / Dispensables / Couplers（[Refactoring.Guru](https://refactoring.guru/refactoring/smells)）。

### 1.1 超长类

`find src/main/java -name '*.java' -exec wc -l {} + | sort -nr | head -16`

| 行 | 文件 | 结构 | 判定 |
| ---: | --- | --- | --- |
| **1233** | `market/quotation/service/QuoteSheetStore.java` | 67 方法 / 55 字段 | **真坏味道**：读、写、锁、版本、快照全在一类（Divergent Change） |
| **633** | `sales/order/service/SalesOrderService.java` | 10 依赖 | **真坏味道** |
| 601 / 594 | `DocumentFlowService` / `ProjectService` | 1 / 8 依赖 | 前者行数来自数据常量、复杂度低；后者**真坏味道** |
| 569 | `GlobalExceptionHandler` | 28 handler | 集中映射合理，接近可维护上限 |
| 555 | `logistics/bill/service/FreightBillService.java` | **11 依赖（全仓最多）** | **真坏味道** |
| 524 | `security/permission/PermissionCodes.java` | 235 常量 | **不算坏味道**（常量目录） |
| 499–440 | CustomerStatementService 499、PrintPdfDrawingSupport 486、ReceiptService 485、CashLedgerQueryRepository 476、PrintScriptService 472、FinanceOverviewQueryRepository 447、PurchaseOrderService 445、SalesOrderPrintExportService 440 | — | 前 4 需关注；SQL 文本块与打印原语不算膨胀 |

**修复（P1）**：`QuoteSheetStore` 四刀拆 QueryStore / CommandStore / VersionGuard / SnapshotSync，保留 Store 作 Facade 不动调用方（2–3 人日；111 个 `@Test` 的 `QuoteSheetStoreTest` 作安全网）。`PermissionCodes`、`CashLedgerQueryRepository` 不修。

### 1.2 超长方法

剥离注释/字符串/文本块后按括号平衡定位方法体（`tmp/quality-audit/methodlen2.py`）：**≥40 行 57、≥60 行 15、≥80 行 5、≥100 行 3**。

| 行 | 位置 | 方法 | 判定 |
| ---: | --- | --- | --- |
| 150 | `common/moduleexport/service/ModuleExportCatalog.java:56` | `buildDefinitions` | 纯 `put(...)` 堆叠，拆私有方法即可，零风险 |
| 104 / 87 | `OpenApiConfig:32` / `SalesOrderSourceCandidateQueryRepository:168` | `leoOpenApi` / `normalSourceSql` | 配置装配、SQL 文本块，非逻辑膨胀 |
| 103 / 86 | `PurchaseInboundApplyService:45` / `SupplierPrepaymentBalanceService:30` | `applyItems` / `validateSupplierReceipt` | **真坏味道**：审核+应用+校验混叠 |
| 77 / 77 | `SalesOrderDocumentFlowService:87` / `ExcelImportService:153` | `documentFlow` / `parseAndValidate` | 组装、解析与校验耦合 |
| 74 / 74 | `SalesReturnCoverageValidator:44` / `SalesReturnApplyService:37` | `assertCoverage` / `applyItems` | **真坏味道**，与采购侧同构 |

**修复（P2，1.5 人日）**：三个审核/校验长方法 + `buildDefinitions` 机械拆分。

### 1.3 构造器依赖过多

仅统计 Spring bean（`PurchaseInboundItemResponse` 26 个构造参数是数据字段，**不构成 Long Parameter List**）：

| 依赖 | 类 |
| ---: | --- |
| **11** | `FreightBillService` |
| **10** | `SalesOrderService` |
| 9 | `CustomerStatementService`、`ReceiptService` |
| 8 | `PurchaseOrderService`、`PurchaseOrderForceCloseService`、`ProjectService`、`MaterialService`、`CustomerService`、`CarrierService`、`FreightStatementService` |

**修复（P2，2 人日/类）**：命令对象 + 领域服务委派。`PurchaseOrderForceCloseService`（8 依赖）尤其可疑——单一"强制关闭"动作不应需 8 个协作者。

### 1.4 重复代码

口径：归一化（去注释、字符串→`S`、压空白、丢纯括号行）后取**连续 8 行相同窗口**，被 ≥2 文件命中的行计为重复。

全部 main：65276 归一化行 / 10075 重复行 = **15.4%**；剔除 `web/dto`+`domain/entity`：56108/5731 = **10.2%**；仅 service 层：35947/2714 = **7.6%**。

**局限**：`@Column`/`@Schema`/getter 样板都算重复行，实体与 DTO 层天然偏高；service 层 7.6% 才反映真实可维护性；与 SonarQube `duplicated_lines` 口径不可直接对标。

Top 重复对与抽查相似度（`difflib.SequenceMatcher`）：

| 块 | 文件对 | 相似度 | 判定 |
| ---: | --- | ---: | --- |
| 60 | `MysteelFetcher` ↔ `SteelxFetcher` | **91.7%** | **确认重复**：仅 URL/Referer/风控标记不同，同一传输层写两份 |
| 55 / 22 | `FreightBillSalesOrderCandidateQueryRepository`↔`SalesOrderOutboundCandidateQueryRepository`；`SalesOrderPageCriteria`↔`PurchaseOrderPageCriteria` | 63.5% / 69.3% | 候选 SQL 与分页条件复制 |
| 39/30/29 | 实体 `FreightBillItem`↔`FreightStatementItem`、`SalesOutboundItem`↔`SalesReturnItem`、`PaymentRequest`↔`PaymentResponse` | — | **不修**：ORM/DTO 固有形态 |

**重要反证（纠正任务书假设）**：怀疑的"跨模块雷同状态迁移/同步明细"经实测**不成立**——`PurchaseInboundCompletionSyncService`↔`SalesOrderCompletionSyncService` **17.5%**、`ExcelImportService`↔`MaterialSpreadsheetImportService` **4.6%**、`CustomerStatementService`↔`FreightStatementService` **22.2%**、`CustomerStatementSourceService`↔`FreightStatementSourceService` **18.2%**、`PurchaseInboundApplyService`↔`SalesReturnApplyService` **11.7%**。同名对称类已差异化实现，**不是复制粘贴**。

**修复**：P1 抽 `HtmlFetcher` 模板方法（0.5 人日，91.7%→<20%）；P2 抽 `CandidateSqlSupport`（1 人日）；P3 抽 `DocumentPageCriteria` 基类（0.5 人日）。

---

## 第 2 节 异常处理反模式

### 2.1 总量与吞异常判定

```
catch ( 230        catch (Exception 18        catch (RuntimeException 39
printStackTrace 0 ✅        catch (Throwable 0 ✅
```

吞异常精确统计（剥注释后按括号平衡取 catch 体，"体内既无 throw 也无 return"→SWALLOW；空体→EMPTY）：`python3 tmp/quality-audit/swallow.py` → **`Counter({'SWALLOW': 62, 'EMPTY': 5}) total: 67`**；按模块 common 19、master 14、security 12、market 11、system 9、attachment 2。**关键判定：67 处绝大多数是"有意的降级"，不可一刀切清理。**

| 类别 | 典型位置 | 判定 |
| --- | --- | :--: |
| 缓存降级（Redis 不可用不影响主流程） | `RedisJsonCacheSupport:56,73,87,98,120,173,179`；`PermissionCacheService:247,307,324`；`AuthenticatedUserCacheService:96,165,170,206` | ✅ 合理（`log.warn` 带 key/原因，`PermissionCacheService` 还 `increment(errorCounter)`）。**唯一问题：降级无统一指标上报，长期发生无人知** |
| 定时任务不中断 | `MaintenanceScheduledTasks:40,58`；`SteelQuoteScheduledTasks:48`；`SteelxQuoteScheduledTasks:47` | ✅ `@Scheduled` 抛异常会终止本次调度 |
| 行级导入失败收集 | `MaterialCsvImportService:97,100,103,127,130,133`；`MaterialSpreadsheetImportService:79,82,85,135,138,141` | ✅ 转 `Row.failed(...)` 返回调用方，业务语义 |
| 外部行情跳过 | `SteelxQuoteSyncService:89,103`；`SteelQuoteSyncService:142,146` | ✅ 记入 `noQuoteRegions`/`skippedDates` |
| 重试循环存 lastError 后外层重抛 | `QuoteSheetService:124`；`JwtTokenService:64`；`RetryExecutor:79` | ✅ 合理 |
| 中断标志 | `PermissionCacheService:183` `Thread.currentThread().interrupt()` | ✅ 标准写法 |
| 纯空 catch | `S3ClientProvider:100`、`JacksonConfig:127,132,159`、`MaterialCsvImportService:148` | ⚠️ 4 处**带解释性注释**，符合 Google Java Style §6.2；**仅 `S3ClientProvider.java:152` 空且无注释 → 真缺陷（P3）** |

**修复（P3，0.2 人日）**：为 `S3ClientProvider:152` 补注释；为缓存降级补统一指标。

### 2.2 异常转译丢失 cause（P1）

宽泛 catch 后 `throw new XxxException(...)` **未传 cause**：**6 处**。

| 位置 | 影响 | 严重度 |
| --- | --- | :--: |
| `attachment/service/storage/AttachmentContentCryptor.java:47`（加密）、`:62`（解密）、`:98`（密钥加载） | 丢失 `GeneralSecurityException` 根因，无法区分密钥问题 vs JCE 配置 | **P2** |
| `AttachmentDirectUploadTokenService:83`、`AttachmentManifestExportService:229`、`SteelQuoteSyncService:201` | 排障线索丢失 | P3 |

**修复（P1，0.2 人日）**：`throw new BusinessException(ErrorCode.INTERNAL_ERROR, "附件加密失败", ex)`。加密 3 处是排障唯一线索。

**附带**：`catch (Exception` 18 处中 9 处紧邻 `log.`；`catch (RuntimeException` 39 处集中在 `security`(15)/`common`(12)，与"基础设施降级"判定一致。**`GlobalExceptionHandler` 一致性（正面）**：569 行 / 28 handler 统一返回 RFC 9457 `ProblemDetail`（生产引用 7 处、`ApiProblemFactory` 存在），未见控制器手写错误 JSON——**问题只是"太大"，不是"不一致"**。

---

## 第 3 节 Lombok 与实体契约

- `@Data` 用在 `@Entity` 上 **0 处** —— 规避 Lombok 最经典的坑（生成 equals/hashCode 含全部字段，与 Hibernate 代理、懒加载集合、可变主键冲突）。
- `@EqualsAndHashCode` / `@Builder` 各 **0 处** —— 实体不定义 equals，按 `id` 在业务层比较；`grep` 确认实体未被放入 `HashSet`/`HashMap` 作 key。
- `@Getter` 62 / `@Setter` 63 / `@Slf4j` 33 / `@Value` 16 —— 用法克制、语义清晰。
- `lombok.config` 含 `stopBubbling = true` 与 **`addLombokGeneratedAnnotation = true`** —— 后者让 JaCoCo/SpotBugs 正确忽略生成代码，是成熟配置。

**这是显著优于行业平均的正面结论，本仓零命中。**

**实体 `@Version` 覆盖率（真实缺口）**：`grep -rn "^@Entity"` → **65 个**；带 `@Version` **24（36.9%）**，缺 41。

> **口径更正**：任务书的"`@Entity` 76 个"来自 `grep -l "@Entity"`，会命中 `@EntityGraph`/`@EntityListeners`/注释。精确口径 `grep -rn "^@Entity"` → 65，与 `grep -rn "import jakarta.persistence.Entity;"` 数量一致。

**判定（P2）**：36.9% 对财务/单据系统偏低。`V10` 已为核心聚合加版本列；其余 41 个多为明细/日志/配置表（随聚合事务写入可不单列），但**需人工确认是否有被独立并发更新的**。**修复（P2，1 人日）**：逐项标注或补 `@Version`，结论落文档——审计性工作，成本低收益高。

**访问器一致性（P3）**：63/65 实体用 `@Getter`；`MdValueAlias`、`MdQuoteRegion`、`MdLengthPremium` 手写 6 组 getter/setter，风格不一致，无功能风险。

---

## 第 4 节 债务标记、死代码与 Flyway

### 4.1 TODO/FIXME（全仓 0 处，需正确解读）

```
grep -rniE "\b(todo|fixme|hack|xxx)\b" src/main/java  → 0
grep -rniE "\b(todo|fixme|hack)\b"     src/test/java  → 0
grep -rnE "待办|临时方案|后续处理|先这样|暂时这样" src/main → 0
```

**正面但不是"没有技术债"**：本仓债务记录方式是**外部化**的——写进 `docs/2026-07-10-backend-code-review.md`（764 行，20+ 条按 Critical/High/Medium 分级并标注"已修复/部分修复/待处理"）、`docs/specs/`、`docs/audit-*` 与 `AGENTS.md` 强制条款。**风险（P3）**：外部化清单与代码位置脱钩——重构移动方法后旧文档的 `文件:行号` 全部失效，建议关键债务处补**带链接的 TODO**（[Google Java Style §4.8.6.2](https://google.github.io/styleguide/javaguide.html) 要求 `TODO: <link> - <说明>`）。

### 4.2 死代码（启发式，附局限）

**类级跨文件零引用：8 个，全部误报。** `ClockConfig`、`OpenApiConfig`、`PageQueryProperties`、`StringTrimmingAdvice`、`ExternalProcessRunner`、`ValidationConfig`、`OperationLogRequestFilter`、`MaintenanceSchedulingConfig` —— 全部带 `@Configuration`/`@Component`/`@ControllerAdvice`，由容器反射实例化，源码零引用是**预期形态**。**该口径对 Spring 项目不可作死代码判据。**

**方法级：12 个候选**（口径：main 下方法名在 main+test 全文本仅出现 1 次；排除 getter/setter、`hashCode/equals/toString/main/builder`、带 `@Bean/@Override/@EventListener/@Scheduled/@ExceptionHandler/@PostConstruct/@PreDestroy/@Around` 者、`config/` 与 `*Properties.java`；已用 `grep -rn "\b名\b" src/main src/test` 二次确认零调用）：

路径前缀均为 `src/main/java/com/leo/erp/`：

`attachment/service/AttachmentBindingService.java:118` `listAttachmentIds`｜`attachment/service/AttachmentFilenameResolver.java:31` `buildFileName`｜`attachment/service/storage/S3PathParser.java:37` `buildUri`｜`auth/service/SessionManagementService.java:280` `findRecentlyEvictedSession`｜`common/support/RedisJsonCacheSupport.java:41` `setAfterCommitExecutor`｜`common/support/WarehouseSelectionSupport.java:23` `normalizeWarehouseName`｜`market/service/SteelxQuoteSyncService.java:175` `latestArticleDate`｜`master/service/ReferenceSnapshotSyncService.java:52` `syncMaterialName`｜`sales/order/service/SalesOrderPriceRuleService.java:120` `lastUsedRuleId`｜`security/jwt/SessionActivityService.java:69` `resolveLastActiveAt`｜`statement/service/StatementCandidateSupport.java:16,30` `parseRelationNos`、`excludeFieldValues`

> 有测试引用故**排除**：`ModuleExportService.xlsxMediaType`(6)、`DocumentStatus.labelOf`(5)、`ProjectProcurementScopeQuery.checksBrands`(3)、`loadMaterialMap`(2)、`validateWarehouseNames`(2)、`checksMaterials`(2)、`reconcile`(1)、`isStatementSourceValid`(1)。

**局限**：①看不到 Spring 反射（`@Cacheable` SpEL、AOP 切点、JSON setter），第 5 项需人工确认；②看不到 `scripts/` 下 Java 校验器与 `archive/tests/` 的引用；③控制器方法由 `@GetMapping` 驱动，已通过"仅保留零引用"规避大部分误报。**修复（P3，0.5 人日）**：逐项 `git log -S"名"` 确认后删除或加 `@Deprecated(forRemoval=true)`——删死公开方法还能减少攻击面。

### 4.3 Flyway（全部正面）

```
ls src/main/resources/db/migration | wc -l            # 189
bash scripts/deploy/verify-flyway-target.sh .         # 189
ls … | sed -E 's/^V([0-9]+)__.*/\1/' | sort -n | awk …  # first 1 / last 189 / 无 GAP
```

| 检查项 | 结果 | 判定 |
| --- | --- | :--: |
| 脚本总数 / 版本连续 / 跳号 / 重复 / 非 `V` 前缀混入 / 命名合规 | 189（V1–V189）/ 连续 / **0** / **0** / **0** / **100%** | ✅ |
| `flyway:validate`（开发库只读）/ 库内历史 | 退出码 0 / count=189、min=1、max=189、全 `success=t` | ✅ |
| 基线冻结 / repair 守卫 / post-baseline 脚本被改写 | `…_FREEZE_OK` / `…_GUARD_OK` / V2–V189 逐文件 `git log` **全部=1** | ✅ |
| 旧 V1–V209 回流 | **0 个**；仅 `V1__baseline.sql:2` 一行说明性注释 | ✅ |

**口径更正（重要）**：`ls` 默认输出是 **shell 字典序**，会出现 `V100` 排在 `V10` 前、`V59` 出现在 `V18` 前等"看起来乱序"——**这是列表排序假象，不是迁移缺陷**。必须用 `sort -V`。

**结论：Flyway 无缺陷，是全仓最扎实的一块。**

### 4.4 seed 线孤立（任务书外发现，P2）

```
ls src/main/resources/db/seed | wc -l                                            # 15（S1–S15 连续）
grep -rn "db/seed" src/main/java src/main/resources/*.yml scripts/ .github/      # 0 命中
grep -rn "flyway_seed_history" src/main scripts/ .github/                        # 0 命中
```

| 事实 | 证据 |
| --- | --- |
| 15 脚本被打进 jar，但**无执行路径** | `ls target/classes/db/` 含 `seed`；主 Flyway 只扫 `db/migration`（`application.yml:98-110` 无 `locations` 覆盖；CI 显式指定）；`grep -rn "Flyway" src/main/java` → 仅 `FlywayStageGateConfiguration`（阶段闸门），**无第二实例** |
| 开发库无 seed 历史表 | `select to_regclass('flyway_seed_history')` → 空；但 `sys_print_template` 有 **19** 条（来自 V3 后其它迁移 + `PrintTemplateFileSyncRunner` 启动同步） |

**判定（P2）**：`AGENTS.md`《Database Migration Policy》要求"seed 线用独立 `flyway_seed_history`、`baselineOnMigrate=true`、`baselineVersion=0`"——**文档已规定，代码未实现**。当前 15 个脚本是**死资产**：新环境不执行、CI 不校验、改错无门禁会红。`docs/specs/2026-07-14-print-workflow-logic-audit.md:116` 已记录该风险但未闭环。

**修复（P2，1 人日）**：A（推荐）实现第二 `Flyway` Bean + `ApplicationRunner` 按开关执行 + CI 校验；B 若确认废弃，删除脚本并改 `AGENTS.md`。**文档与代码必须一致。**

---

## 第 5 节 门禁现状与盲区

### 5.1 表观强度 vs 实际阻断面

| 门禁 | 位置 | 实测 | 判定 |
| --- | --- | --- | :--: |
| 编译 | `mvn -o -q -DskipTests compile` | ✅ 退出码 0，**22.1s** | ✅ |
| ArchUnit 边界 / Spring Modulith | `check-java-module-boundaries.sh` / `verify-modulith-architecture.sh` | ✅ `1346 classes, 160 baselined restricted, 25 cyclic, 24 status writes`；Modulith ✅（CI:36-39） | ✅ |
| Checkstyle | `config/checkstyle/checkstyle.xml` + `pom.xml:307-334` | ⚠️ **615 告警 / 0 阻断** | ⚠️ |
| SpotBugs | `pom.xml:336-344` | ⚠️ **1347 类 / 0 bug** | ⚠️ |
| JaCoCo | `pom.xml:390-408` | ❌ **无 check、无阈值** | ❌ |
| Surefire 执行数 | `pom.xml:297-304` | ✅ `failIfNoTests` + `failIfNoSpecifiedTests` | ✅ |
| Flyway 对齐+历史断言 / 真实库用例 / 镜像冒烟 | CI `package` job | ✅ `validate` 退出码 0；29 个 `*PostgresTest` 真跑；`curl -fsS /api/v2.0/health`（`timeout 240`） | ✅ |

### 5.2 缺口一：JaCoCo 阈值已被删除（P1，杠杆最大）

```
grep -n -A20 "jacoco-maven-plugin" pom.xml   # 只有 prepare-agent + report
grep -n "COVEREDRATIO" pom.xml               # 空
```

历史溯源：`git show 657beee7 -- pom.xml | grep -A20 COVEREDRATIO` 显示被删的正是 `<execution><id>check</id><phase>verify</phase>` + `<counter>LINE</counter><value>COVEREDRATIO</value><minimum>0.10</minimum>`——**曾有 10% 行覆盖率 check，在"清理退役配置"提交中一并删除且未恢复**。

**后果链（可验证）**：无 workflow 读取 `target/site/jacoco`（`grep -rn "jacoco" .github/workflows/*.yml` → 0）；报表仅本地生成、不入库 ⇒"新增 500 行零测试"可通过 `ci.yml` 两个 job。本次定向运行产生的 `jacoco.exec` 汇总 3.0% 行 / 1.7% 分支**只代表跑了两个模块**，恰说明无全量覆盖率数据可查。

**修复（P1，0.5 人日，三步走）**：① `verify` 阶段归档 `target/site/jacoco` 为 artifact，先拿基线；② 加 `check` 规则 `minimum = 实测值 − 1%`，锁住"不许下降"；③ 每季度 +2%，直到对标 [Sonar way 新代码覆盖率 ≥ 80%](https://docs.sonarsource.com/sonarqube-server/2025.4/quality-standards-administration/managing-quality-gates/introduction-to-quality-gates/)（实测可访问，原文"New code test coverage is greater than or equal to 80.0%… Duplication in the new code is less than or equal to 3.0%"）。语法见 [JaCoCo check-mojo](https://www.jacoco.org/jacoco/trunk/doc/check-mojo.html)（HTTP 200）。**禁止一刀切**：直接设 55% 会让 CI 立刻红。

### 5.3 缺口二：Checkstyle 的 error 面太窄（P2）

根 `<property name="severity" value="warning"/>`（`checkstyle.xml:7`）+ 仅 7 条覆盖 `error`；`pom.xml:320` 设 `violationSeverity=error` ⇒ **只有 error 级才 `failOnViolation`**。

- **error（7 条，真阻断）**：`FileTabCharacter`、`RedundantImport`、`UnusedImports`、`EmptyBlock`、3× `RegexpSinglelineJava`（禁 `System.out.print`、`Executors.new*`、`new SimpleDateFormat`）。
- **warning（11 条，永不阻断）**：`LineLength`、`MagicNumber`、`ConstantName`、`WhitespaceAround`、`MethodParamPad`、`NoWhitespaceBefore`、`LeftCurly`、`RightCurly` 等。

实测 `mvn -o -B -ntp -DskipTests checkstyle:check` → `[WARN]` 行 **615**，末尾 `You have 0 Checkstyle violations.`

按规则：`LineLength` 328、`MagicNumber` 247、`ConstantName` 30、`WhitespaceAround` 10。按模块：system 207、common 124、sales 53、attachment 47、finance 43、purchase 31、market 28、master 23、statement 22、auth 15、security 13、logistics 6、search 2、inventory 1。

**判定**：不是配置错误（warning 治理存量是常见手段），而是**治理停滞**——615 条长期存在、无收敛趋势、无 issue 追踪。`ConstantName` 30 条（`log` 应为 `LOG`）说明规则与本仓 `@Slf4j` 惯例冲突。

**修复（P2，1 人日）**：`ConstantName` 加 `ignorePattern="log\|logger"`（消 30 条）；`LineLength` 升 `error` 并一次性 `google-java-format`（328 处，风险低）；`MagicNumber` 保持 warning。**另两个覆盖面缺口**：`pom.xml:319` `includeTestSourceDirectory=false` ⇒ 63460 行测试代码零静态检查（这解释了 6 处 `Thread.sleep` 无人拦，P2）；`scripts/*.java`（含 `ModuleBoundaryVerifier`）不在 `src/main/java`，Checkstyle 与 SpotBugs 均不覆盖（P3）。

### 5.4 缺口三：SpotBugs `threshold=High` 的静默（P2）

`pom.xml:336-344`：`effort=Max`（好）+ `threshold=High`，实测 `BugInstance size is 0` / `BUILD SUCCESS`。对 91158 行、1347 个类返回 0 bug，更可能是**阈值过滤掉全部 Medium/Low**（含 `NP_NULL_ON_SOME_PATH`、`RV_RETURN_VALUE_IGNORED`）而非代码无瑕。**修复（P2，0.5 人日）**：降 `Medium` 跑基线，存量写 `config/spotbugs/exclude.xml` 冻结（仿 ArchUnit `FreezingArchRule`），此后新代码 Medium 阻断。

### 5.5 缺口四：门禁可绕过性（P2）

| 绕过路径 | 可行 | 影响 |
| --- | :--: | --- |
| `mvn package -DskipTests` | ✅ | 但 CI `ci.yml:204` 用 `bash scripts/maven.sh -B -ntp package`（不带 skip），**CI 不会漏跑测试** |
| `git commit --no-verify` | ✅ | 仓库无 `.husky`/`.githooks`（`git config core.hooksPath` 为空），本地零钩子 |
| 直接 push 绕过 PR 检查 | ⚠️ | 取决于分支保护（不在仓库内，**无法审计**）；`ci.yml` 对 `push: branches: ["**"]` 也触发，但**不阻断合并** |
| 删改 `checkstyle.xml` / `assert-flyway-*` | ✅ | **无 sha256 冻结**——Flyway 基线有 `flyway-baseline.sha256`，质量门禁配置没有 |

**修复（P3，0.5 人日）**：仿 `flyway-baseline.sha256` 建 `scripts/deploy/quality-gate.sha256`（覆盖 `checkstyle.xml`、`pom.xml`、4 个门禁脚本），CI `static-analysis` 增校验步骤——该模式在本仓已验证有效（脚本注释明确"冻结清单自身也在 Git 里，改动清单必须走代码评审"）。

### 5.6 CI 覆盖面本身完整（正面项，纠正"CI 缺失"误判）

`.github/workflows/ci.yml`（243 行、2 job）：

- **`static-analysis`**：Architecture gates（`check-java-module-boundaries.sh` + `verify-modulith-architecture.sh`）→ 迁移门禁（基线冻结 + repair 守卫）→ **压测套件离线自检**（token-guard / leak-detector / collector-login / login-invariant selftest + `perf/check-undefined-funcs.py`）→ `checkstyle:check spotbugs:check`。
- **`package`**（needs）：Flyway `migrate` + `validate`（postgres:16-alpine）→ **历史断言门禁**（`assert-flyway-history.sh --target 189`）→ **门禁负例自检** → `flyway info` 证据 → `mvn package`（**含全量测试**，`LEO_TEST_POSTGRES:"true"`）→ `docker build` → **镜像冒烟** → 上传 jar。

**覆盖度高于多数同类项目**，三个亮点：**门禁自身有负例自检**（1 正例 + 6 负例，防"门禁假绿"）、**迁移不只靠 `validate` 而直接查 `flyway_schema_history` 断言 success/checksum/max(version)**、**压测脚本有离线自检**（防"工具骗人"）。**因此不应报"CI 缺失"**，真实问题就是 §5.2–5.5 四条。

**一处不一致（P3）**：`deploy-production.yml` 的 `package` job 自己执行 `mvn package`（:189-190）**但不跑静态分析**（`grep -nE "checkstyle|spotbugs|check-java-module" deploy-production.yml` → 0；`release.yml` 则有）。要么删重复 package 改用 CI artifact，要么补同源静态分析——避免"两条构建路径、只有一条有门禁"。

### 5.7 提交规范与签名

| 指标 | 值 | 判定 |
| --- | ---: | :--: |
| 近 200 条 Conventional Commits 合规 | **200/200 = 100%** | ✅ |
| 全历史 980 条合规 | 931/980 = 95.0% | ✅ |
| 类型分布（近 300） | fix 86、feat 76、chore 61、test 23、refactor 19、perf 16、docs 14、ci 3、revert 2 | ✅ |
| `.releaserc.json` | commit-analyzer → release-notes → changelog → exec(versions:set) → git → github | ✅ |
| 近 100 条 PGP 签名 | 86 G / 14 N | ⚠️ |

**签名成因（非流程违规）**：14 条 `N` 全部是 `chore(release): x.y.z`，由 `semantic-release` 在 CI 以 bot 身份创建、CI 无 GPG 私钥。`AGENTS.md` 要求"所有提交必须签名"，**该规则与语义化发布自动提交在技术上冲突**。

**修复（P3，0.5 人日）**：A 在 CI secret 配 GPG 私钥 + `git config user.signingkey`；B 在 `AGENTS.md` 显式豁免 `chore(release):` 提交并把 `git log --format='%G?'` 断言范围限定为非 release 提交。**文档与工具必须一致**，否则每次发布都产生"已知违规"，久而久之所有签名检查被忽略。

---

## 第 6 节 测试覆盖分布与质量

### 6.1 按模块分布

```bash
for d in src/main/java/com/leo/erp/*/; do m=$(basename "$d"); \
  mf=$(find "$d" -name '*.java'|wc -l); tf=$(find "src/test/java/com/leo/erp/$m" -name '*.java' 2>/dev/null|wc -l); \
  tl=$(find "src/test/java/com/leo/erp/$m" -name '*.java' -exec cat {} + 2>/dev/null|wc -l); \
  printf "%-12s %4s %4s %6s\n" "$m" "$mf" "$tf" "$tl"; done
```

| 模块 | 主文件 | 测试类 | 主行 | 测试行 | **行比** | 0 测试 |
| --- | ---: | ---: | ---: | ---: | ---: | :--: |
| market | 91 | 47 | 8803 | 11648 | **1.32** | — |
| logistics | 31 | 13 | 2458 | 2803 | **1.14** | — |
| sales | 127 | 57 | 13099 | 12148 | 0.93 | — |
| statement | 62 | 21 | 5239 | 4031 | 0.77 | — |
| inventory | 20 | 10 | 1919 | 1414 | 0.74 | — |
| purchase | 80 | 31 | 8861 | 5766 | 0.65 | — |
| security | 47 | 14 | 3766 | 2419 | 0.64 | — |
| master | 137 | 33 | 9998 | 5818 | 0.58 | — |
| auth | 53 | 12 | 2923 | 1680 | 0.57 | — |
| common | 135 | 39 | 10642 | 6065 | 0.57 | — |
| finance | 67 | 17 | 7243 | 3500 | 0.48 | — |
| system | 138 | 36 | 10941 | 4495 | 0.41 | — |
| attachment | 55 | 11 | 4122 | 1490 | 0.36 | — |
| search | 10 | 1 | 948 | 183 | 0.19 | — |
| **allocation** | **6** | **0** | **182** | **0** | **0.00** | ❌ **是** |

**判定**：
1. **`allocation` 是全仓唯一 0 测试模块**（6 文件 / 182 行），仅经 `api/` 端口间接覆盖（`PurchaseOrderAvailabilityServiceTest` mock `ItemAllocationQuery` 并断言 `ItemAllocationSummary`）——**`ItemAllocationNativeRepository`（114 行原生 SQL）零测试**，是真实缺口（P2）。
2. `search`（10 文件 / 948 行）仅 1 个测试类覆盖 `DocumentFlowService`，`GlobalSearchService`/`GlobalSearchDocumentRepository` 零测试。
3. `system` 行比 0.41 但它是最大模块之一；`printtemplate`（20+ 文件）有 20 个测试类、质量不差，比值被 `operationlog`/`runtimeconfig`/`schedule`/`setup` 拉低。`market`、`logistics` 测试行超主代码，是明确投入重点。

### 6.2 测试类型构成（零 `@SpringBootTest` 是优点）

| 类型 | 数量 | 占比 | 判定 |
| --- | ---: | ---: | :--: |
| 纯单元测试（无 Spring 上下文）/ `@DataJpaTest`（真实库 `Replace.NONE`） | **287**/340；29 | **84.4%**；8.5% | ✅ 快 |
| `@SpringBootTest`/`@WebMvcTest`（均 0）；`MockMvc` 类 18；`@Disabled` **0** | 0 / 0；18；0 | 5.3% | ✅ 无慢测试；控制器用 `standaloneSetup` |

**最值得肯定**：零 `@SpringBootTest` 意味着没有"改一行等 40 秒上下文启动"——定向测试实测很快（`QuoteRegionServiceTest` 14 项 2.878s；inventory 10 类 55 项全绿）。**代价**：缺端到端验证，`standaloneSetup` 手工装配**不覆盖真实 Security 过滤器链、`@Valid` 真实解析、`JacksonConfig` 全局序列化**——雪花 ID 序列化或 `SecurityConfig` 路径匹配改动时测不出来。

### 6.3 断言与负路径

断言调用共 **6067** 次、均值 **2.06/方法**；AssertJ 静态导入 499 个文件、JUnit `Assertions` 仅 2 个（风格统一 ✅）；`assertThatThrownBy` 覆盖 153 个文件但 `assertThrows` 仅 **9** 处（几乎全用 AssertJ ⚠️）。

**无断言的测试方法：60 处**（`tmp/quality-audit/noassert.py`；Top：`SalesOrderCompletionSyncServiceTest` 7、`V2SalesContractApiContractTest` 7、`GlobalExceptionHandlerConnectionFailureTest` 6、`V2ModuleExportControllerTest` 6）。

**必须正确解读，避免误报**：抽查确认多数是有效用例，如 `syncBySourceIds_shouldSkipNull() { service.sync(...null); verifyNoInteractions(repo, query); }` —— `verifyNoInteractions` 已计入判定。剩余真需人工复核的是**既无断言也无 `andExpect`** 的用例——`V2SalesContractApiContractTest` 的多个 `post_returns201...` 依赖 `MockMvc.andExpect(...)`，而该正则未覆盖。**结论：60 处不能一律当缺陷**（P3 二次过滤）。

**负路径覆盖（更可靠判据，按测试类是否含 `assertThatThrownBy/assertThrows/assertThatExceptionOfType/catchThrowable`）**：

`market` 29/47 (61.7%)｜`purchase` 19/31 (61.3%)｜`finance` 10/17 (58.8%)｜`statement` 12/21 (57.1%)｜`sales` 32/57 (56.1%)｜`master` 13/33 (39.4%)｜`attachment` 4/11 (36.4%)｜`security` 5/14 (35.7%)｜`common` 12/37 (32.4%)｜`logistics` 4/13 (30.8%)｜`inventory` 3/10 (30.0%)｜**`system` 9/36 (25.0%)**｜**`auth` 3/12 (25.0%)**

**合计 156/340 = 45.9%**

**判定（P2）**：超过一半测试类只测 happy path。`system`（最大模块）与 `auth`（安全相关）各 25%——**认证模块不测失败路径风险最高**（登录失败计数、锁定、token 过期、权限拒绝都是负路径）。`AGENTS.md`《Test File Policy》已明确要求覆盖"空值/边界/MAX/MIN/0/负/非法输入/并发/幂等/权限/雪花 ID 精度/金额精度/状态码契约"，**规则存在，执行力不足**。

**参数化测试不足（P3）**：2949 个方法中仅 **3 个文件**用 `@ParameterizedTest`，`@DisplayName` 5 处、`@Nested` 4 处。[JUnit 5 官方文档](https://docs.junit.org/current/writing-tests/parameterized-classes-and-tests.html)明确参数化测试能让每个入参组合独立报告（"each invocation will be reported separately"）；对边界密集的金额/吨位校验，可把 N 个复制粘贴方法压成 1 个。

### 6.4 真实库门禁（`docs/real-db-test-gate.md` 已落地，但有结构缺陷）

| 门禁 | 实现 | 实测 |
| --- | --- | :--: |
| 未设变量时整类跳过 + 0 漏标 | `@EnabledIfEnvironmentVariable(named="LEO_TEST_POSTGRES", matches="true")` | ✅ 29/29 |
| profile 注入变量 + `failIfNoTests` 防空跑 | `pom.xml:410-441` + `:301-302`/`:436` | ✅ |
| CI 显式开启 + 先 `flyway:migrate` 建 schema + 自建自清 | `ci.yml:113`；文档规定 `@AfterEach` 按自建 id 清理 | ✅ 抽查相符 |

**结构缺陷（P2）：`@DataJpaTest` + `@AutoConfigureTestDatabase(replace = Replace.NONE)` 直连共享开发库。** 后果：①结果依赖本机库状态（文档已用"自建自清 + 只统计自建数据"缓解）；②无隔离致并发/顺序敏感（`Thread.sleep` 6 处、测试 `@Transactional` 48 处、无 `@TestMethodOrder`）；③本地无法一条命令跑全绿。**修复（P2，3 人日）**：引入 [Testcontainers PostgreSQL](https://java.testcontainers.org/test_framework_integration/junit_5/)（`@Container` + `@DynamicPropertySource`），按官方 [singleton containers 模式](https://java.testcontainers.org/test_framework_integration/manual_lifecycle_control/) 避免 29 类各起容器。收益：自包含、CI 免 postgres service、并发安全。

**测试数据构造（P3，判定"不修"）**：340 个测试类仅 **2 个**用 fixture 抽象，其余靠 98 个 `@BeforeEach` + 类内私有工厂方法。属"重复但可接受"——显式构造更易读、更抗重构；`Refactoring` 明确把测试重复列为可容忍，仅当同一构造在 50+ 处重复时才值得抽。

---

## 第 7 节 门禁可执行性实测

```bash
cd leo
mvn -o -q -DskipTests compile                                # EXIT 0，REAL=22.10s
mvn -o -B -ntp test -Dtest=QuoteRegionServiceTest            # Tests run: 14, Failures: 0 (2.878s)
mvn -o -B -ntp test -Dtest='com.leo.erp.inventory.**.*Test'  # Tests run: 55, Failures: 0，BUILD SUCCESS
# Checkstyle / SpotBugs / ArchUnit / Flyway 脚本结果见 §5.1
```

| 项 | 结果 |
| --- | --- |
| 离线编译 | ✅ `-o` 成功（依赖本地完整），增量 **22.1s**；Maven 3.6.3 / OpenJDK 21.0.12.1 / Spring Boot 3.5.16 parent |
| 定向测试 | 14 + 55 项全绿 |
| **未跑全量** | 全量约 6020 项（`docs/2026-07-11-finance-audit.md:248`），按任务约束未跑 ⇒ **"全量测试是否全绿"无结论** |

**Flyway validate（对开发库只读）**：

```bash
source scripts/env/dev.sh && mvn -o -q -B -ntp flyway:validate \
  -Dflyway.url="jdbc:postgresql://$SPRING_DATASOURCE_HOST:$SPRING_DATASOURCE_PORT/$SPRING_DATASOURCE_DB" \
  -Dflyway.user="$SPRING_DATASOURCE_USERNAME" -Dflyway.password="$SPRING_DATASOURCE_PASSWORD" \
  -Dflyway.locations="filesystem:src/main/resources/db/migration" \
  -Dflyway.cleanDisabled=true -Dflyway.placeholderReplacement=false -Dflyway.validateMigrationNaming=true   # EXIT=0
psql -tAc "select count(*), min(version::int), max(version::int) from flyway_schema_history where success;"  # 189|1|189
```

本次**只执行 `validate`（只读比对），未执行 `migrate`**，未对开发库做任何写入。

---

## 第 8 节 优先级修复清单（按性价比排序）

性价比 = 收益（风险消除/长期降本）÷（人日 + 回归风险）。P1 内按代价升序。

### P1（4 项，必须处理）

| # | 问题（位置） | 建议 | 代价 | 收益 |
| ---: | --- | --- | ---: | --- |
| **P1-1** | **JaCoCo 无覆盖率阈值**，覆盖率可无限下降而 CI 全绿（`pom.xml:390-408`） | 三步：①归档 jacoco 报告为 artifact 拿基线；②加 check `minimum=实测−1%`；③每季度 +2% 至 Sonar way 新代码 80% | 0.5 人日 | 让"新增零测试代码"从可发布变不可合并——**全仓质量杠杆最大的一件事** |
| **P1-2** | **6 处异常转译丢失 cause**，加密失败无法排障（`AttachmentContentCryptor:47,62,98` 最关键，另 `AttachmentDirectUploadTokenService:83`、`AttachmentManifestExportService:229`、`SteelQuoteSyncService:201`） | 补 `throw new BusinessException(INTERNAL_ERROR, "...", ex)` | 0.2 人日 | 故障定位从"不可诊断"到"看堆栈即知"，纯收益 |
| **P1-3** | **`QuoteSheetStore` 1233 行 / 55 字段 / 67 方法**，Divergent Change（`market/quotation/service/QuoteSheetStore.java`） | 四刀拆 QueryStore / CommandStore / VersionGuard / SnapshotSync，保留 Store 作 Facade | 2–3 人日 | 变更半径大幅收窄；111 个 `@Test` 作安全网 |
| **P1-4** | **`MysteelFetcher`(177) ↔ `SteelxFetcher`(168) 91.7% 重复** | 抽 `HtmlFetcher` 模板方法：`buildRequest`/`validateContent`/`isRiskControlPage` 由子类实现，重试/退避/风控上提 | 0.5 人日 | 消除双份重试与风控；新增第三个行情源从"复制 170 行"降为"实现 3 个方法" |

### P2（6 项，下一两个迭代）

| # | 问题 → 建议 | 代价 |
| ---: | --- | ---: |
| P2-1 | Checkstyle 615 告警 0 阻断、error 面仅 7 条 → `ConstantName` 加 `ignorePattern="log\|logger"`；`LineLength` 升 error 并一次性格式化（328 处）；`MagicNumber` 保持 warning | 1 人日 |
| P2-2 | SpotBugs `threshold=High` 返回 0 bug → 降 `Medium` 跑基线，存量写 `config/spotbugs/exclude.xml` 冻结，新代码 Medium 阻断 | 0.5 人日 |
| P2-3 | Checkstyle 不覆盖测试代码（`pom.xml:319`）→ 打开 `includeTestSourceDirectory`，先只对 error 级生效 | 0.5 人日 |
| P2-4 | `db/seed` S1–S15 从未执行 → A 实现第二 `Flyway` Bean（`table=flyway_seed_history`、`baselineOnMigrate=true`、`baselineVersion=0`）+ CI 校验；B 删除并改文档 | 1 人日 |
| P2-5 | 真实库测试直连共享开发库（`Replace.NONE`）→ 引入 Testcontainers PostgreSQL + singleton container | 3 人日 |
| P2-6 | 45.9% 测试类含负路径断言、`auth`/`system` 仅 25% → 优先补 auth（登录失败计数、锁定、token 过期、权限拒绝）与 security 负路径 | 2 人日 |

### P3（5 项，择机）

| # | 问题 | 建议 | 代价 |
| ---: | --- | --- | ---: |
| P3-1 | 质量门禁配置无冻结保护（V1 有 sha256，checkstyle/pom 无） | 仿 `flyway-baseline.sha256` 建 `quality-gate.sha256` + CI 校验 | 0.5 人日 |
| P3-2 | `deploy-production.yml` 重跑 package 但不跑静态分析 | 删重复 package 改用 CI artifact，或补同源静态分析 | 0.2 人日 |
| P3-3 | 12 个零引用 public 方法（疑似死代码） | 逐项 `git log -S` 确认后删除或 `@Deprecated(forRemoval=true)` | 0.5 人日 |
| P3-4 | 41/65 实体无 `@Version`；3 实体手写 getter | 逐项审计标注"随聚合写入无需版本"；3 实体统一 `@Getter` | 1 人日 |
| P3-5 | 14 条 `chore(release)` 无 PGP 签名 | CI 配 GPG 私钥；或 `AGENTS.md` 显式豁免 release 提交并限定断言范围 | 0.5 人日 |

### 明确"不修"（避免过度设计）

| 项 | 理由 |
| --- | --- |
| Request/Response DTO 字段镜像（29 处 8 行块） | DTO 固有形态，MapStruct 契约测试已保障一致性 |
| 实体字段重复（`FreightBillItem`↔`FreightStatementItem` 等） | ORM 映射必然重复；抽 `@MappedSuperclass` 得不偿失 |
| `PermissionCodes` 524 行 / 235 常量 | 常量目录语义单一，拆散降低可发现性 |
| `CashLedgerQueryRepository` 476 行（含 57 行 SQL 块） | 长 SQL 是业务复杂度而非代码坏味道 |
| 测试数据构造重复（仅 2 fixture 抽象、98 `@BeforeEach`） | 显式构造更抗重构，`Refactoring` 将其列为可容忍 |
| `CustomerStatement*` ↔ `FreightStatement*` 对称类 | 实测相似度 18.2%–22.2%，已差异化实现，**不是复制粘贴** |
| 空 catch 4 处（带注释） | 符合 Google Java Style §6.2；仅 `S3ClientProvider:152` 需补注释 |

---

## 第 9 节 口径边界（必须随结论引用）

1. **未跑全量测试**。只跑 2 个定向集合（14 + 55 项全绿）⇒ 只有"定向集合 + 编译/静态检查/Flyway validate 全绿"，**没有"全量测试全绿"结论**。
2. **覆盖率无全量数字**。因无门禁、本地只有定向运行的 `jacoco.exec`，3.0% 仅代表"这两个模块的相对分母"，**不可作为仓库覆盖率**——这正是 P1-1 要解决的问题。
3. **死代码与重复率均为启发式**。文本引用计数看不到 Spring 反射、`@Cacheable` SpEL、AOP 切点、`archive/tests/`；12 个候选需人工确认。重复率为 8 行窗口归一化口径，与 SonarQube `duplicated_lines` 不可直接对标，实体/DTO 层天然偏高。
4. **无 `main` 分支保护信息**。保护规则、必需检查、审批人数在 GitHub 仓库设置里、不在仓库内，**"门禁是否可被绕过合并"无法审计**，只能确认"CI 会跑、本地无钩子"。
5. **范围外**：未审计前端 `aries/`；未审计 `perf/` 压测脚本实际有效性（仅确认自检脚本存在且被 CI 调用）；**未执行 `flyway:migrate`**，仅 `validate`（只读）。

---

## 附录 A 复现脚本（`tmp/quality-audit/`，未进仓库）
`methodlen2.py` 方法体长度（§1.2）｜`swallow.py` 吞异常（§2.1）｜`sim.py` 归一化相似度（§1.4）｜`noassert.py` 无断言测试（§6.3）｜内联：8 行窗口重复率、类级/方法级死代码、构造器依赖（§1.4/§4.2/§1.3）。

## 附录 B 公开参考（全部实测 HTTP 200）
- **Conventional Commits 1.0.0** https://www.conventionalcommits.org/en/v1.0.0/ —— §5.7 提交规范与 SemVer 映射
- **Google Java Style Guide** https://google.github.io/styleguide/javaguide.html —— §2.1 空 catch 须注释（§6.2）；§4.4 列宽；§4.8.6.2 `TODO:` 须带链接
- **SonarQube 质量门禁（Sonar way）** https://docs.sonarsource.com/sonarqube-server/2025.4/quality-standards-administration/managing-quality-gates/introduction-to-quality-gates/ —— §5.2 对标：新代码覆盖率 ≥80%、重复率 ≤3%
- **JaCoCo `check` goal** https://www.jacoco.org/jacoco/trunk/doc/check-mojo.html —— §5.2 `rules/limits/COVEREDRATIO` 语法
- **Refactoring.Guru 坏味道目录** https://refactoring.guru/refactoring/smells —— §1 分类框架（Bloaters / Dispensables / Couplers）
- **Testcontainers** [JUnit 5 集成](https://java.testcontainers.org/test_framework_integration/junit_5/)｜[单例容器模式](https://java.testcontainers.org/test_framework_integration/manual_lifecycle_control/) —— §6.4 替代共享开发库、避免 29 类各起容器
- **JUnit 5 参数化测试** https://docs.junit.org/current/writing-tests/parameterized-classes-and-tests.html —— §6.3 仅 3 文件用 `@ParameterizedTest`
- **Checkstyle** https://github.com/checkstyle/checkstyle ｜ **SpotBugs** https://spotbugs.readthedocs.io/en/latest/introduction.html —— §5.3 严重级别、§5.4 `threshold` 语义
