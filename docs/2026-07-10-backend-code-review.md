# Leo 后端代码审查报告

## 基本信息

| 项目 | 内容 |
| --- | --- |
| 审查日期 | 2026-07-10 |
| 审查提交 | `992286063134e6d671dd120cfcb0c616e45d3647` |
| 审查分支 | `main`，与 `origin/main` 同步 |
| 审查范围 | `leo` 后端生产代码、测试、配置、Flyway、部署与 CI 工作流 |
| 技术栈 | Java 21、Spring Boot 3.5、Spring Security、Spring Data JPA、PostgreSQL、Redis |
| 审查方式 | 只读静态审查、调用链核对、现有测试与质量门禁执行；后续按本报告实施修复 |

本报告以表中的审查提交为原始基线。原始审查时仓库工作区干净且未修改业务代码、配置或数据库；
下方“修复进度”记录同日后续实施结果。详细发现中的文件行号均对应原始审查提交，修复后位置可能变化。

## 修复进度（2026-07-10）

| 发现 | 状态 | 已实施内容 | 验证 |
| --- | --- | --- | --- |
| Critical-01 | 已修复 | bootstrap token、写接口过滤、Nginx 管理面隔离、数据库单例状态与跨实例初始化锁 | token、过滤器、协调器、迁移与并发初始化测试 |
| High-01 | 已修复 | 销售订单打印增加记录级数据范围校验 | 服务与切面权限回归 |
| High-02 | 部分修复 | 边缘覆盖 XFF、可信代理链从右向左解析、本地 Bucket 增加容量与过期边界 | 代理链、IPv4/IPv6、超限与 Bucket 回归；多实例 Redis 共享限流仍待实施 |
| High-03 | 已修复 | `credential_version` 写入用户、会话与 JWT，修改密码撤销活动会话并绑定 2FA challenge | 认证、刷新、改密与 JWT 回归 |
| High-05 | 已修复 | 建立统一来源锁服务，额度和排他来源的创建、更新、状态、删除路径均锁定旧新来源并集 | 单元时序测试及 PostgreSQL 双事务额度、反向 ID、排他来源 3 项回归 |
| High-06 | 已修复 | 固化库存状态矩阵，区分 `onHand/reserved/available`，统一库存与出入库流水口径 | 98 项定向测试及 PostgreSQL 状态矩阵 |
| High-07 | 部分修复 | `V10` 为六个核心聚合增加版本列，实体增加 `@Version`，乐观锁异常统一返回 409 | 40 项并发契约与迁移测试；客户端 `expectedVersion/ETag` 及 update-vs-audit 并发协议仍待实施 |
| Medium-01 | 已修复 | 待收票聚合、筛选、稳定排序、分页和 count 全部下推 PostgreSQL | 5 项服务/仓储测试及 2 项 PostgreSQL 大分页回归 |
| Medium-02 | 部分修复 | `V11` 将六类主数据编码改为活动记录部分唯一，业务单号、登录名和发票号继续永久唯一 | Flyway validate、catalog 校验及项目编码软删复用 PostgreSQL 回归；永久唯一标识的数据库冲突尚未全面映射为明确的 409/业务错误 |
| Medium-06 | 已修复 | 供应商对账统一使用 `INBOUND_COMPLETED` | 状态矩阵回归 |
| 其余发现 | 待处理 | 动态限流规则、会话撤销 outbox、附件密钥版本化、流式导出、候选查询、完整 HTTP 契约和多实例维护租约未纳入本批次 | 维持原建议与优先级 |

### 本批次技术落地

1. `SourceAllocationLockService` 要求调用方已有事务，按“采购订单明细 -> 采购入库明细 -> 销售订单明细”固定表顺序，并对 ID 去空、去重、升序后执行 `FOR UPDATE`；头单和对账单来源也使用固定表顺序。
2. 更新操作锁定旧来源与新来源并集；状态迁移和软删除锁定持久化旧来源。锁位于额度汇总、占用判断和实体修改之前，缺失来源行会中止事务。
3. `V10__add_core_aggregate_versions.sql` 覆盖采购订单、采购入库、销售订单、销售出库、开票和收票六个聚合根；并发写冲突使用独立业务码 `CONCURRENT_MODIFICATION(4090)` 返回 HTTP 409。
4. `V11__use_active_master_data_code_uniqueness.sql` 仅调整承运商、客户、物料、项目、供应商和仓库编码；审计型业务标识不放宽。
5. 当前 Flyway 主线已成功执行并校验 `V1` 至 `V11`。后续迁移必须从 `V12` 开始，不得修改已执行的 `V1` 至 `V11`。
6. 采购和销售单据同步明细时，先在临时集合完成映射、来源查询、父子关联与必填字段初始化，再通过 `clear/addAll` 发布到 Hibernate 受管集合；查询触发 auto-flush 时不会持久化半初始化明细，同时保留 orphan removal 语义。

## 结论摘要

以下为原始审查提交上的结论。当前修复状态以上方进度表为准：

1. 未初始化实例可被匿名访问者抢先创建管理员，属于完整接管风险。
2. 销售订单打印绕过数据范围，具备打印权限的受限用户可跨用户或部门导出订单。
3. Nginx 与客户端 IP 解析组合允许伪造 `X-Forwarded-For`，同时限流桶永久驻留内存。
4. 数量、金额和排他来源关联普遍使用“先查询、后校验、再写入”，并发时可超分配或重复关联。
5. 库存与出入库报表未限定生效状态，会把草稿或预出库单计入实际库存。
6. 多数核心业务聚合没有乐观锁，并发编辑与状态迁移可能静默覆盖。

在上述问题解决前，不建议把全新未初始化实例直接暴露到公网，也不应把当前限流管理页面视为有效的安全控制面。

## 严重级定义

| 等级 | 定义 |
| --- | --- |
| Critical | 可直接造成系统接管、关键数据大规模泄露或不可恢复破坏，应立即修复 |
| High | 可造成越权、认证失效、财务/库存错误、并发数据破坏或服务不可用，应进入最近修复批次 |
| Medium | 在明确输入、数据规模或状态组合下产生错误结果、错误协议或运维风险 |
| Conditional | 取决于部署拓扑或功能开关，需要先确认实际生产条件 |

## 详细发现

### Critical-01：匿名访问者可抢先创建系统管理员

**证据**

- [`InitialSetupController.java:21`](../src/main/java/com/leo/erp/system/setup/web/InitialSetupController.java#L21) 在类级别标记 `@PublicAccess`，整个 `/setup` 控制器均匿名可访问。
- [`InitialSetupController.java:43`](../src/main/java/com/leo/erp/system/setup/web/InitialSetupController.java#L43) 允许匿名用户为自选 `loginName` 生成并取得 TOTP secret。
- [`InitialSetupService.java:139`](../src/main/java/com/leo/erp/system/setup/service/InitialSetupService.java#L139) 只检查 OOBE 是否完成，不校验部署引导凭证或请求来源。
- [`InitialSetupService.java:211`](../src/main/java/com/leo/erp/system/setup/service/InitialSetupService.java#L211) 使用请求者提供的账号、密码和缓存 TOTP secret 创建用户并绑定 `ADMIN`。
- [`steelx.conf:32`](../deploy/nginx/steelx.conf#L32) 将全部 `/api/` 请求公开代理到后端，没有限制 `/api/setup`。

**触发条件**

新实例已启动并可被网络访问，但尚未完成首次初始化。攻击者先于管理员访问 `/api/setup/admin/2fa/setup`，取得自己选择账号的 secret，计算验证码后调用 `/api/setup/admin`。

**影响**

攻击者获得完整管理员权限。即使合法管理员已创建账号、尚未完成公司主体步骤，匿名请求仍可能抢先写入公司主体配置。

**建议**

1. 部署时注入一次性高熵 bootstrap token，所有写入型 setup API 必须校验。
2. 默认只允许 loopback 或管理网访问 setup API，生产 Nginx 显式限制路径。
3. 初始化完成后在安全过滤层关闭整个写入面，而不只依赖服务层状态判断。
4. 使用数据库锁或唯一初始化记录保证多实例下只有一个首建事务成功；`synchronized` 只保护单 JVM。

### High-01：销售订单打印绕过数据范围

**证据**

- [`SalesOrderController.java:113`](../src/main/java/com/leo/erp/sales/order/web/SalesOrderController.java#L113) 只校验 `sales-order:print` 动作权限。
- [`PermissionAspect.java:65`](../src/main/java/com/leo/erp/security/permission/PermissionAspect.java#L65) 会为业务资源设置 `DataScopeContext`，但需要下游查询或实体校验实际消费该上下文。
- [`SalesOrderPrintExportService.java:67`](../src/main/java/com/leo/erp/sales/order/service/SalesOrderPrintExportService.java#L67) 直接通过 `findByIdAndDeletedFlagFalse` 加载任意订单，没有调用 `DataScopeContext.assertCanAccess` 或 `ResourceRecordAccessGuard`。

**触发条件**

用户拥有打印动作权限，但数据范围为 `SELF` 或 `DEPARTMENT`，并获得或推测了数据范围外的订单 ID。

**影响**

可跨用户或部门导出完整 Excel，泄露客户、项目、价格、重量和订单明细。

**建议**

加载订单后显式执行记录级访问校验，或把数据范围下推到 repository 查询。补充 `SELF`、`DEPARTMENT`、`ALL` 三类打印集成测试，不能只测试 Controller 是否调用 Service。

### High-02：客户端 IP 可伪造，限流可绕过并造成内存增长

**证据**

- [`steelx.conf:37`](../deploy/nginx/steelx.conf#L37) 使用 `$proxy_add_x_forwarded_for`，会保留客户端主动发送的 XFF。
- [`leo.conf:15`](../deploy/nginx/leo.conf#L15) 的另一份 Nginx 配置存在相同设置。
- [`ClientIpResolver.java:45`](../src/main/java/com/leo/erp/common/support/ClientIpResolver.java#L45) 在请求来自受信代理时直接取 XFF 第一项。
- [`GlobalRateLimitFilter.java:71`](../src/main/java/com/leo/erp/security/permission/GlobalRateLimitFilter.java#L71) 以该客户端 IP 构造全局限流键。
- [`TokenBucketService.java:22`](../src/main/java/com/leo/erp/security/permission/TokenBucketService.java#L22) 使用无 TTL、无容量上限的 `ConcurrentHashMap` 保存 Bucket。

**触发条件**

攻击者为每个请求设置不同的 `X-Forwarded-For` 第一项。Nginx 追加真实地址后，后端仍使用攻击者提供的第一项。

**影响**

- 绕过全局 IP 限流和基于 IP 的安全审计。
- 为每个伪造 IP 永久创建一个 Bucket，最终导致堆内存持续增长。
- 多实例部署时每个实例独立限流，实际允许速率会随实例数增加。

**建议**

边缘 Nginx 应覆盖客户端提供的 XFF，或使用正确配置的 `real_ip` 模块；后端应从右向左剥离已知可信代理。限流状态改为有界 TTL 缓存或 Redis，并验证多实例一致性。

### High-03：修改密码不会使已有会话失效

**证据**

- [`AccountSecurityService.java:43`](../src/main/java/com/leo/erp/auth/service/AccountSecurityService.java#L43) 修改密码后只保存新 hash 并清理用户缓存。
- [`TokenIssuanceService.java:53`](../src/main/java/com/leo/erp/auth/service/TokenIssuanceService.java#L53) 刷新 token 时只检查 session、到期时间和用户状态，不校验密码或凭据版本。

**触发条件**

攻击者已取得用户的 access token 或 refresh token，用户随后通过修改密码处置账号风险。

**影响**

已有 access token 可继续使用至到期，refresh token 仍可持续轮换到会话自然失效，修改密码无法完成预期的账号止损。

**建议**

密码修改事务提交后撤销该用户全部 refresh session，并拉黑现有 session/access token；或在用户表引入 `credential_version`，写入 JWT claim，并在认证与刷新时校验。

### High-04：限流规则管理接口修改后不影响运行时限流

**证据**

- [`RateLimitAdminService.java:27`](../src/main/java/com/leo/erp/system/admin/service/RateLimitAdminService.java#L27) 直接执行多条 `JdbcTemplate.update`，方法没有事务。
- [`application.yml:65`](../src/main/resources/application.yml#L65) 将 Hikari `auto-commit` 设为 `false`；非事务写入在连接归还时会回滚。
- [`GlobalRateLimitFilter.java:30`](../src/main/java/com/leo/erp/security/permission/GlobalRateLimitFilter.java#L30) 使用硬编码全局 rate/capacity。
- [`RateLimitAspect.java:24`](../src/main/java/com/leo/erp/security/permission/RateLimitAspect.java#L24) 使用注解值或硬编码默认值。
- 生产代码没有读取 `sys_rate_limit_rule` 并参与限流判定的路径。

**影响**

管理页面可以返回成功并展示新值，但数据库更新可能没有提交；即使成功提交，实际保护强度也不会变化，形成错误安全预期。

**建议**

在运行时规则接通前禁用或移除编辑入口。若保留动态规则，应使用类型化 DTO、完整参数校验、单事务更新、受控缓存失效，并增加“修改规则后请求阈值发生变化”的集成测试。

### High-05：采购、销售和开收票额度可并发超分配

**证据**

- [`application.yml:67`](../src/main/resources/application.yml#L67) 使用 PostgreSQL `READ_COMMITTED` 隔离级别。
- [`PurchaseInboundAllocationService.java:39`](../src/main/java/com/leo/erp/purchase/inbound/service/PurchaseInboundAllocationService.java#L39) 先汇总历史分配量，[`PurchaseInboundAllocationService.java:65`](../src/main/java/com/leo/erp/purchase/inbound/service/PurchaseInboundAllocationService.java#L65) 再以内存值判断剩余数量。
- [`PurchaseInboundItemRepository.java:46`](../src/main/java/com/leo/erp/purchase/inbound/repository/PurchaseInboundItemRepository.java#L46) 的汇总查询没有行锁。
- [`InvoiceIssueSourceService.java:156`](../src/main/java/com/leo/erp/finance/invoiceissue/service/InvoiceIssueSourceService.java#L156) 和 [`InvoiceIssueSourceService.java:228`](../src/main/java/com/leo/erp/finance/invoiceissue/service/InvoiceIssueSourceService.java#L228) 对开票重量/金额采用相同的查询后内存校验方式。
- 销售来源分配、收票额度和多种“一次性来源”占用也采用同类 check-then-write 模式。

**触发条件**

两个事务同时消费同一来源明细的最后可用数量、重量或金额。两者均读取旧汇总并通过校验，随后同时提交。

**影响**

累计入库、销售、开票或收票超过来源单据；同一来源可能被两张目标单重复关联，进一步污染库存、结算和报表。

**建议**

按来源明细 ID 排序后加 `PESSIMISTIC_WRITE/FOR UPDATE` 锁，再执行汇总和写入；或建立带数据库约束的 allocation ledger/counter。对高竞争路径可使用 `SERIALIZABLE` 加有限重试。必须补真实双事务并发测试。

### High-06：库存与出入库报表把未生效单据计入实际流水

**证据**

- [`InventoryReportQueryRepository.java:63`](../src/main/java/com/leo/erp/report/inventory/repository/InventoryReportQueryRepository.java#L63) 对采购入库只过滤 `deleted_flag=false`。
- [`InventoryReportQueryRepository.java:83`](../src/main/java/com/leo/erp/report/inventory/repository/InventoryReportQueryRepository.java#L83) 对销售出库同样只过滤删除状态。
- [`IoReportQueryRepository.java:46`](../src/main/java/com/leo/erp/report/io/repository/IoReportQueryRepository.java#L46) 和 [`IoReportQueryRepository.java:70`](../src/main/java/com/leo/erp/report/io/repository/IoReportQueryRepository.java#L70) 的出入库流水也没有生效状态条件。
- [`StatusConstants.java:18`](../src/main/java/com/leo/erp/common/support/StatusConstants.java#L18) 明确定义了草稿、预出库、已审核和完成状态。

**影响**

保存草稿入库即可增加库存，保存草稿或预出库即可减少库存；反审核也会直接改变历史报表口径。

**建议**

定义唯一的“库存生效状态”规则并复用到库存和出入库报表。通常入库只计 `已审核/完成入库`，实际出库只计 `已审核`；预出库如表示预留，应单列 `reserved` 和 `available`，不能混入现存量。

### High-07：核心业务聚合缺少并发版本控制

**证据**

- [`AbstractCrudService.java:88`](../src/main/java/com/leo/erp/common/service/AbstractCrudService.java#L88) 的编辑流程是 load-modify-save。
- [`AbstractCrudService.java:103`](../src/main/java/com/leo/erp/common/service/AbstractCrudService.java#L103) 的状态迁移也是独立的 load-modify-save。
- [`SalesOrder.java:15`](../src/main/java/com/leo/erp/sales/order/domain/entity/SalesOrder.java#L15)、[`PurchaseOrder.java:20`](../src/main/java/com/leo/erp/purchase/order/domain/entity/PurchaseOrder.java#L20) 等核心聚合没有 `@Version`。
- 对账单、收付款等少数实体已经使用 `@Version`，说明当前保护覆盖不一致。

**触发条件**

用户 A 编辑单据正文或明细，同时用户 B 审核、反审核或编辑同一单据。两个事务读到相同旧版本，后提交者覆盖先提交者。

**影响**

可能发生静默丢更新、审核状态被旧请求写回、明细或金额覆盖，以及派生同步结果与单据正文不一致。

**建议**

为全部可变聚合根新增版本列和 `@Version`。原始审查时迁移最大版本为 `V7`；本批次已使用 `V10` 落地首批六个聚合，后续扩展必须从当前下一版本 `V12` 开始。将 `ObjectOptimisticLockingFailureException` 映射为 HTTP 409，并补 update-vs-audit 并发测试。

### High-08：轮转 TOTP 主密钥可能使加密附件永久不可读

**证据**

- [`SecurityKeyService.java:149`](../src/main/java/com/leo/erp/system/securitykey/service/SecurityKeyService.java#L149) 轮转 TOTP 主密钥时只重加密用户 TOTP 和 OSS secret。
- [`AttachmentContentCryptor.java:88`](../src/main/java/com/leo/erp/attachment/service/storage/AttachmentContentCryptor.java#L88) 使用当前 active TOTP material 派生附件加密密钥。
- 附件密文格式没有记录 key version，解密也不会尝试历史密钥。
- [`LocalAttachmentStorage.java:85`](../src/main/java/com/leo/erp/attachment/service/storage/LocalAttachmentStorage.java#L85) 和 [`S3CompatibleAttachmentStorage.java:136`](../src/main/java/com/leo/erp/attachment/service/storage/S3CompatibleAttachmentStorage.java#L136) 根据当前全局 `encryptedStorage` 开关决定是否解密，而不是根据附件自身格式。
- [`SecuritySecret.java:31`](../src/main/java/com/leo/erp/system/securitykey/domain/entity/SecuritySecret.java#L31) 将 JWT/TOTP 主密钥原文保存在业务数据库字段中。

**触发条件**

附件加密功能已启用，系统中已有加密附件，管理员执行 TOTP 主密钥轮转。

**影响**

历史附件仍由旧密钥加密，但下载时只使用新密钥，AEAD 校验失败；切换加密开关还会让旧明文被当作密文或让旧密文被直接作为文件返回。只读数据库泄露也可进一步取得 JWT 签名密钥并伪造访问令牌。

**建议**

JWT、TOTP、OSS 和附件密钥应分域。附件密文保存 key ID/version，解密支持历史 key，后台重加密完成后再退役旧 key。主密钥优先放入 KMS/Vault/HSM，数据库只保存 key ID 或包封密文。

### High-09：会话撤销的数据库与 Redis 状态可能永久不一致

**证据**

- [`AfterCommitExecutor.java:18`](../src/main/java/com/leo/erp/common/support/AfterCommitExecutor.java#L18) 在事务提交后直接执行回调，没有隔离或持久化失败。
- 会话撤销先提交数据库状态，再通过 after-commit 回调写 access-token blacklist 和清理会话缓存。

**触发条件**

数据库撤销已提交，但 Redis 在 after-commit 阶段不可用或抛出异常。

**影响**

接口可能返回 500，但数据库实际上已经提交；access token 未进入黑名单。重试时又可能因记录已撤销而提前返回，无法补偿遗漏的 Redis 状态。

**建议**

对安全关键副作用使用事务型 outbox 或持久化重试。撤销操作必须幂等，即使数据库记录已撤销，也要允许补做 blacklist 和缓存清理。

### High-10：多个导出路径同步加载无界结果集

**证据**

- 库存、应收应付、物料及附件清单导出均存在一次性加载完整结果、组装 DTO/Workbook/CSV、再生成完整字节数组的路径。
- [`InventoryReportQueryRepository.java:240`](../src/main/java/com/leo/erp/report/inventory/repository/InventoryReportQueryRepository.java#L240) 的列表查询没有通过请求中的分页大小限制完整导出规模。

**影响**

数据库结果、对象列表、Excel/CSV 结构和最终字节数组可能同时驻留堆；数据增长后会造成长事务、GC 抖动或 OOM。定时附件清单任务也可与在线请求竞争内存。

**建议**

定义明确导出上限；使用游标/分批读取和流式写出。大导出改为有界异步任务，并把结果写入临时文件或对象存储，避免在 HTTP 请求内持有完整数据。

### Medium-01：待收票报表状态口径和分页结果不正确

**证据**

- [`PendingInvoiceReceiptReportService.java:97`](../src/main/java/com/leo/erp/report/pendinginvoicereceipt/service/PendingInvoiceReceiptReportService.java#L97) 加载采购单时没有限定可收票状态。
- [`InvoiceReceiptRepository.java:25`](../src/main/java/com/leo/erp/finance/invoicereceipt/repository/InvoiceReceiptRepository.java#L25) 汇总所有未删除收票单，没有限定 `已收票`。
- [`PendingInvoiceReceiptReportService.java:103`](../src/main/java/com/leo/erp/report/pendinginvoicereceipt/service/PendingInvoiceReceiptReportService.java#L103) 始终读取第 0 页，最多候选 1000 张采购单。
- 关键词过滤和最终分页在截断后的内存集合上执行。

**影响**

草稿收票会抬高“已收票”金额，草稿采购单会进入待收票；超过候选窗口或匹配项位于窗口外时会漏数据，`totalElements` 也不代表全量结果。

**建议**

为报表建立只汇总生效状态的专用查询，把待收聚合、关键词、排序、分页和 count 下推数据库。

### Medium-02：软删除语义与永久唯一约束冲突

**证据**

- [`AbstractCrudService.java:125`](../src/main/java/com/leo/erp/common/service/AbstractCrudService.java#L125) 删除只设置 `deleted_flag=true`。
- [`ProjectService.java:121`](../src/main/java/com/leo/erp/master/project/service/ProjectService.java#L121) 创建项目时只检查活动记录。
- [`V1__baseline.sql:3261`](../src/main/resources/db/migration/V1__baseline.sql#L3261) 对 `project_code` 使用无条件唯一约束。
- 业务单号和多种主数据编码存在相同模式。

**影响**

软删后用相同编码创建新记录会通过服务层校验，但在数据库提交时违反唯一约束，最终返回 500。

**建议**

先统一语义：若编码永久唯一，服务层应包含已删除记录并返回明确业务错误或提供恢复；若只要求活动记录唯一，使用新的后续 Flyway 改为 `WHERE deleted_flag=false` 的部分唯一索引，并移除实体上的误导性 `unique=true`。本批次已通过 `V11` 完成六类主数据编码迁移，数据库唯一性语义已经统一；永久唯一业务标识发生数据库级冲突时的统一 409/业务错误映射仍需补齐，因此当前为部分修复。

### Medium-03：客户端协议错误被映射为 500

**证据**

- [`PageQueryArgumentResolver.java:56`](../src/main/java/com/leo/erp/common/web/PageQueryArgumentResolver.java#L56) 直接调用 `Integer.valueOf`，`page=abc` 或整数溢出抛出 `NumberFormatException`。
- [`GlobalExceptionHandler.java:141`](../src/main/java/com/leo/erp/common/exception/GlobalExceptionHandler.java#L141) 最终将未显式处理异常统一映射为 500。
- 405、413、415 和部分 multipart 异常也缺少对应映射。
- Refresh token、API key、物料分类三个列表没有排序字段白名单，合法格式但不存在的 `sortBy` 会在 JPA 层抛异常并返回 500。

**影响**

客户端会把输入错误误判为可重试的服务故障，监控产生虚假 5xx，日志记录无意义堆栈。

**建议**

显式映射参数格式、HTTP 方法、Content-Type、上传大小和排序错误；分页解析应转换为 `VALIDATION_ERROR`。没有排序白名单时默认拒绝自定义排序。

### Medium-04：候选页与部分报表每次执行全量内存过滤

**证据**

- [`SalesOrderService.java:79`](../src/main/java/com/leo/erp/sales/order/service/SalesOrderService.java#L79) 循环读取全部审核销售订单后再过滤占用和手工分页。
- [`PurchaseOrderService.java:103`](../src/main/java/com/leo/erp/purchase/order/service/PurchaseOrderService.java#L103) 对采购候选执行相同全量扫描。
- 客户、供应商和物流对账候选还会加载全量活动对账单及明细，再构造不断增长的排除集合。

**影响**

单次小分页请求仍具有 O(全库) 数据库与堆开销；随着历史数据增长，查询次数、SQL 参数和内存占用线性增长。

**建议**

将占用判断改为数据库 `NOT EXISTS` 或 projection/subquery，并让数据库直接完成分页和 count。

### Medium-05：元数据接口路径重复包含 context path

**证据**

- [`application.yml:7`](../src/main/resources/application.yml#L7) 配置 `server.servlet.context-path: /api`。
- [`MetaController.java:16`](../src/main/java/com/leo/erp/common/web/MetaController.java#L16) 又声明 `@RequestMapping("/api/meta")`。

**影响**

实际外部路径变为 `/api/api/meta/code`，而设计文档和其他 Controller 的约定均是 Controller 映射不重复 context path。

**建议**

改为 `@RequestMapping("/meta")`，并补 MockMvc 路由测试。

### Medium-06：供应商对账使用了错误的采购入库完成状态

**证据**

- [`SupplierStatementSourceService.java:67`](../src/main/java/com/leo/erp/statement/supplier/service/SupplierStatementSourceService.java#L67) 使用 `PURCHASE_COMPLETED（完成采购）` 查询采购入库候选。
- [`SupplierStatementSourceService.java:173`](../src/main/java/com/leo/erp/statement/supplier/service/SupplierStatementSourceService.java#L173) 创建对账单时再次要求采购入库状态为 `PURCHASE_COMPLETED`。
- [`StatusConstants.java:50`](../src/main/java/com/leo/erp/common/support/StatusConstants.java#L50) 和 [`PurchaseInboundService.java:227`](../src/main/java/com/leo/erp/purchase/inbound/service/PurchaseInboundService.java#L227) 表明采购入库的完成状态实际为 `INBOUND_COMPLETED（完成入库）`；`PURCHASE_COMPLETED` 属于采购订单。
- [`SupplierStatementSourceServiceTest.java:499`](../src/test/java/com/leo/erp/statement/supplier/service/SupplierStatementSourceServiceTest.java#L499) 为采购入库测试对象设置了业务状态机不会产生的 `PURCHASE_COMPLETED`，因此掩盖了错误。

**影响**

正常完成的采购入库单不会出现在供应商对账候选中，直接按其明细创建对账单也会失败；只有数据库中存在非法状态数据时该路径才可能通过。

**建议**

候选查询和创建校验统一改用 `INBOUND_COMPLETED`，错误提示同步改为“未完成入库”。测试对象必须通过真实允许状态或状态迁移构造，并分别断言 `草稿/已审核` 被拒绝、`完成入库` 被接受。

## 建议实现细节

### 通用落地约束

1. 当前主迁移最大版本为 `V11`，下一个迁移必须使用 `V12`。每个数据库变更保持单一职责；不得修改 `V1__baseline.sql` 或任何已经执行的 `V2` 至 `V11`。并行分支合并前必须重新检查版本号，不能重复占用版本。
2. 新旧协议需要分阶段兼容时，先部署“双读/新写”或“新增字段但不强制使用”，完成数据迁移和客户端切换后再清理旧路径，避免数据库与应用无法独立回滚。
3. 建议新增通用 `CONCURRENT_MODIFICATION`、`SERVICE_UNAVAILABLE` 等业务码，由唯一映射器维护 HTTP 状态；不得复用 `REFRESH_TOKEN_REUSE_CONFLICT` 表达无关的 409 冲突。
4. 并发、锁、Flyway、Redis 和 MVC 协议测试应使用与生产一致的 PostgreSQL、Redis 和真实 Spring 调用链。单元测试中的 mock、同一事务内连续调用或 H2 不能证明并发与数据库约束正确。

### Critical-01：OOBE 引导凭证与跨实例互斥

**入口保护**

- 增加独立的 `SetupBootstrapTokenFilter`，只匹配 servlet path `/setup/**` 的写请求；不要在 Controller 参数、查询串或日志 MDC 中传递引导凭证。
- 部署通过 Secret/KMS 注入 32 个随机字节的 Base64URL `LEO_SETUP_BOOTSTRAP_TOKEN`，客户端放在 `X-Setup-Token`。服务端解码并校验固定长度后使用 `MessageDigest.isEqual` 比较；错误凭证统一返回 403，实例仍需初始化但配置缺失或非法时返回 503，并将 readiness 标记为失败。
- `GET /setup/status` 可以保持只读匿名访问，但应返回 `Cache-Control: no-store`，且不返回管理员账号、公司名称或其他可用于侦察的信息。全部 setup 写响应也应禁止缓存。
- 边缘 Nginx 对 `/api/setup/` 增加管理网段 allowlist；网络限制是第二道防线，不能替代引导 token。
- TOTP 临时密钥的 Redis key 加入 bootstrap token fingerprint 和登录名摘要，例如 `setup:admin:totp:v2:{tokenFingerprint}:{loginHash}`，保持短 TTL，并在管理员事务成功提交后删除。`InitialSetupAdminSubmitRequest.totpSecret` 当前不参与服务端校验，应先标记废弃再移除，避免客户端误以为该字段可信。

**事务与状态**

- 通过新的 Flyway 建立单例 `sys_bootstrap_state`，固定 `id=1`，至少保存 `state/completed_at/completed_by/version`。迁移时若现有 `SYS_OOBE_COMPLETED` 已启用，或同时存在活动 ADMIN 与公司主体，回填为 `COMPLETED`，避免升级后重新开放入口。
- token、DTO、TOTP 和密码强度等无数据库副作用的校验尽量在事务外完成。`initialize/configureAdmin/configureCompany` 进入独立事务 Bean 后，第一条状态 SQL 对单例行执行 `SELECT ... FOR UPDATE`，再重新读取管理员、公司和完成状态并写入；锁内不得做二维码、文件或远程网络操作。
- 锁内的二次状态检查决定唯一成功者，替换 `synchronized` 的正确性职责。过渡版本在同一事务同时更新新状态行和旧 `SYS_OOBE_COMPLETED` 开关，保证旧应用回滚后仍保持关闭；后续版本再移除双写。
- 完成标记必须与最后一步初始化数据在同一事务提交。已完成实例对全部 setup 写请求返回固定 403，bootstrap token 不再授予任何能力。

**验收标准**

- 缺失、错误、过短 token 均不能生成 TOTP secret 或写入任何初始化数据，日志不得出现 token。
- 两个 Spring Context 连接同一 PostgreSQL/Redis，同时使用正确 token 初始化时只能一个提交，另一个在取得数据库锁后得到“已完成”响应；最终只能存在一个活动管理员和一个默认公司主体。
- 另测 token 轮换、事务回滚、分步初始化后重启、已完成实例升级回填，以及旧 token 不能复用轮换前缓存的 TOTP secret。

### High-01：打印记录级权限

- 在 `SalesOrderPrintExportService` 加载订单后、读取明细和生成文件前，调用现有 `ResourceRecordAccessGuard.assertCurrentUserCanAccess("sales-order", "print", order)`。该 guard 会独立建立数据范围，比只调用在上下文缺失时默认放行的 `DataScopeContext.assertCanAccess` 更能保护其他调用入口。
- Controller 上的 `@RequiresPermission` 继续保留，用于动作权限和 `PermissionAspect` 上下文；服务层 guard 负责记录级防御，不应让 Controller 预查询订单后再把实体传入导出层。
- 使用真实 MockMvc 请求覆盖 GET、POST 两个打印入口：`SELF` 只能打印本人订单，`DEPARTMENT` 只能打印允许的 owner 集合，`ALL` 可以打印任意订单。越权固定返回 403，且断言模板加载和文件生成逻辑没有执行。

### High-02：可信代理链与限流存储

**代理协议**

- `steelx.conf` 与 `leo.conf` 作为公网边缘时都使用 `proxy_set_header X-Forwarded-For $remote_addr` 覆盖客户端输入。若前面还有负载均衡器，先用 `set_real_ip_from`、`real_ip_header` 和 `real_ip_recursive on` 只信任明确 CIDR，再把归一化后的 `$remote_addr` 传给应用；网络层禁止绕过代理直连后端端口。
- `ClientIpResolver` 将 `remoteAddr` 放在链尾，从右向左删除连续的可信代理，首个非可信地址才是客户端。只接受数字形式 IPv4/IPv6，限制 XFF 总长度和 hop 数，例如最多 8 跳；空值、主机名、非法地址或超限时回退 `remoteAddr`，不要触发 DNS 解析。
- `leo.trusted-proxies` 默认应为空。生产启动配置明确列出实际代理地址，不能笼统信任全部 RFC1918 网段，除非整个网段确实受控；当前本机 Nginx 部署必须同步在 systemd/container 注入 `LEO_TRUSTED_PROXIES=127.0.0.1`，否则应用只会识别到 loopback。设置 `server.forward-headers-strategy=none`，避免容器与自定义 resolver 对转发头重复解释。

**Bucket 生命周期**

- 多实例生产环境使用 Redis 中的原子 token-bucket/Lua 操作，bucket key 必须设置 TTL，TTL 至少覆盖从空桶恢复到满桶的时间并加空闲余量。使用 Redis 时间或单一时间源，避免实例时钟差异。
- 单实例降级实现应改为有 `maximumSize` 和 `expireAfterAccess` 的有界缓存；达到上限时记录指标。登录、初始化等安全入口在共享存储不可用时应 fail-closed 返回 503，普通业务是否降级必须由显式配置决定，不能统一静默 fail-open。
- 验收时同时模拟伪造首段 XFF、合法多级代理、IPv6、超过 hop 上限和两个实例共享 Redis bucket，确认解析结果、429 与 `Retry-After` 一致。

### High-03：密码变更后的凭据版本与会话撤销

- 在新的 Flyway 中为 `sys_user` 增加独立的 `credential_version bigint not null default 0`，并在 `auth_refresh_token` 记录签发时的同名版本；它不能复用 JPA 的 `version`，否则普通资料更新也会使 token 失效。
- access token 增加 `cv` claim，`AuthenticatedUserCacheService` 的快照和 `SecurityPrincipal` 同时携带当前凭据版本。`JwtAuthenticationFilter` 必须在认证前比较 token 与当前版本；旧 token 缺失 `cv` 时按 0 处理，从而保持上线兼容。
- 凭据版本的安全判定必须读取数据库权威 projection，至少在初次认证每个请求时查询一次，不能仅依赖“删除缓存成功”。只有在明确接受失效延迟并实现跨实例一致性后，才可增加短 TTL 缓存；缓存淘汰失败不得让旧 token 在无界时间内继续有效。
- 修改或重置密码时，在同一事务内锁定用户、递增 `credential_version`、撤销该用户全部活动 refresh session，并为每个 session 写入撤销 outbox。修改成功后当前会话也失效，响应应明确 `reloginRequired=true`，客户端收到成功响应后清理本地 token。
- refresh 与改密统一采用“用户行 -> session 行”的锁顺序。refresh 可先无锁读取 token 对应的 user/session ID，再锁用户和 session 并重新校验 hash、撤销状态及 `session.credential_version == user.credential_version`；不一致立即拒绝，避免两个流程交错后签发新 token。
- 建议为 `RevokeReason` 增加 `PASSWORD_CHANGED`，通过新迁移更新数据库 check constraint。不得把凭据变更记录成并发会话挤出。
- 验收应证明：事务回滚时旧 token 仍有效；提交后旧 access/refresh token 均得到 401；其他用户会话不受影响；并发“刷新 token 与修改密码”最终不能签发携带旧版本的新 access token。

### High-04：动态限流规则的运行时模型

**组件边界**

- `RateLimitRuleUpdateRequest` 替代 `Map<String,Object>`，至少校验 `rate > 0`、`capacity >= 1`、`1 <= tokensPerRequest <= capacity`，并限制可接受的最大值。
- `RateLimitRuleRepository` 负责持久化；`RateLimitPolicyProvider` 把完整启用规则加载为不可变快照；`TokenBucketStore` 只负责原子消费；`RateLimitEnforcer` 统一写 `RateLimitContext`、限流响应头和 429 响应。
- `GlobalRateLimitFilter` 执行 `GLOBAL/global_default` IP 配额；`RateLimitAspect` 按“`METHOD/<ControllerSimpleName>.<methodName>` 精确规则 -> `METHOD/<ControllerSimpleName>` 类级规则 -> `@RateLimit` 注解”的顺序解析，兼容现有 `DatabaseBackupController` 类级种子。API Key 请求再叠加 `API_KEY/api_key:<profile>`，不同维度不能互相覆盖。
- API Key bucket 使用持久化 key ID，不使用原始密钥的 `String.hashCode()`；如确需 profile，应通过独立迁移新增受约束字段。

**更新与缓存**

- 通过新的 Flyway 为 `sys_rate_limit_rule` 增加 `revision bigint not null default 0`，作为配置和 bucket 状态的代际标识；不要复用不具备单调并发语义的时间戳。
- `updateRule` 在单事务内锁定目标、一次性应用变更、递增规则 `revision` 并检查受影响行数；不存在返回 404。提交后更新 Redis version key 或发布失效通知，各实例丢弃本地完整快照；15 至 30 秒 TTL 作为通知丢失的最终一致性兜底。
- bucket identity 包含 `ruleId/revision/dimension`。规则修改后新请求使用全新 bucket，旧 revision 的 bucket 依靠短 TTL 回收；这明确选择“以新 capacity 重新获得一次 burst”，而不是复用旧 token 比例。若产品要求保留余额，则 Lua 必须在同一原子操作中识别 revision 并钳制 token，不能只更新配置缓存。
- 管理接口返回成功前，至少保证数据库提交成功。运行时 provider、管理列表和编辑接口必须使用同一字段语义及优先级排序。
- 集成测试先耗尽旧 bucket，再更新 `AuthController.login` 的 rate/capacity，断言无需重启即按新 revision 和约定的 burst 语义执行；同时覆盖类级 fallback、两实例缓存失效、规则不存在时的注解回退、非法更新 400 和目标不存在 404。

### High-05：额度分配的锁顺序与冲突语义

- 每个创建或编辑事务先提取去重后的来源明细 ID；编辑和删除必须锁定“旧来源与新来源的并集”，不能只锁请求中的新来源。随后按数值升序一次性锁定真实来源行，再汇总已有分配并写入目标明细。
- 跨聚合操作还需定义全局表顺序，例如采购订单及明细 -> 采购入库及明细 -> 销售订单及明细。只有明细 ID 时可先无锁解析 parent ID，按全局顺序取得全部锁后必须重新读取并校验归属、状态和软删除标记；禁止按 `HashSet` 迭代顺序逐行加锁。
- JPA 可使用 `@Lock(PESSIMISTIC_WRITE)`，原生 SQL 可使用 `FOR UPDATE`。锁定查询必须验证返回行数与请求 ID 数一致，并在持锁事务内完成校验和保存；只给汇总结果加锁或在 helper 的独立事务中加锁均无效。
- 对高频单值额度可改为原子条件更新：`allocated + delta <= source_total` 时更新成功，否则受影响行数为 0。编辑时必须先锁目标记录并按“新值减旧值”计算 delta，删除或回滚时反向释放，不能同时维护两套权威汇总。
- 真正的一次性来源关联再增加数据库唯一约束；可部分分配的来源不能用唯一约束替代额度校验。
- 额度校验与财务报表的状态口径必须分开：草稿目标单是否预占额度由业务规则决定，报表则只统计已开票/已收票等生效状态，不能复用同一条“活动记录”谓词。
- 单行请求本身超过来源绝对上限属于业务输入错误，保持 422；等待锁后发现剩余额度已变化、条件更新受影响行数为 0、锁超时、死锁重试耗尽和乐观锁失败映射为 `CONCURRENT_MODIFICATION`/409。只有已证明幂等的事务才能做少量带抖动重试。
- 并发测试使用两个独立连接和栅栏，让两个事务在提交前竞争同一最后额度；断言仅一个成功、最终汇总不超过来源值，且反向 ID 顺序输入不会死锁。

### High-06：库存状态矩阵与统一口径

建议先确认并固化以下状态矩阵，库存汇总与出入库流水共用同一规则：

| 单据 | 状态 | 实际库存 `onHand` | 预留 `reserved` | 出入库流水 |
| --- | --- | --- | --- | --- |
| 采购入库 | 草稿 | 不增加 | 不影响 | 不展示 |
| 采购入库 | 已审核、完成入库 | 增加 | 不影响 | 展示入库 |
| 销售出库 | 草稿 | 不减少 | 不增加 | 不展示 |
| 销售出库 | 预出库 | 不减少 | 增加 | 不作为实际出库展示 |
| 销售出库 | 已审核 | 减少 | 不再单列 | 展示出库 |

- `available = onHand - reserved`，不要把预出库直接从 `onHand` 扣除，也不要把负数静默截断为 0；负可用量应作为数据异常可见。
- `InventoryReportResponse` 同时存在数量和重量两个维度，应新增 `onHandQuantity/reservedQuantity/availableQuantity` 与 `onHandWeightTon/reservedWeightTon/availableWeightTon`。兼容期保留现有 `quantity/weightTon`，明确它们仍是 `onHand` 的别名并在 OpenAPI 标记废弃；前端切换完成后再移除，不能悄悄把旧字段改成 `available`。
- 把状态集合集中为库存领域策略，并以 SQL 参数传入 `InventoryReportQueryRepository` 与 `IoReportQueryRepository`。实际入库统一限定 `AUDITED/INBOUND_COMPLETED`，实际出库统一限定 `AUDITED`，预留使用单独 CTE 汇总 `PRE_OUTBOUND`。
- 状态切换、反审核和删除必须在同一矩阵测试中验证：每一步分别断言数量、重量两组 `onHand/reserved/available`、旧字段别名和流水行数，避免只测最终 DTO。

### High-07：乐观锁与客户端期望版本

- 第一批至少覆盖 `so_sales_order`、`po_purchase_order`、`po_purchase_inbound`、`so_sales_outbound`、`fm_invoice_issue` 和 `fm_invoice_receipt`，通过新的 Flyway 增加 `version bigint not null default 0`，聚合根增加 `@Version`。其他可变聚合按相同清单逐步补齐。
- 明细集合是 inverse `@OneToMany` 时，只有明细发生增删改不一定会自动更新根版本。所有 mutation、状态迁移和软删除路径应锁定聚合根，并在明细变化时使用 `OPTIMISTIC_FORCE_INCREMENT` 或等价的显式根版本推进；不能把 `@Version` 直接放进 `AbstractAuditableEntity`，否则必须一次迁移全部继承表。
- 仅增加 `@Version` 只能拦截事务重叠，不能识别用户拿着旧页面稍后提交。详情响应应返回 `version`，编辑和状态迁移请求必须携带 `expectedVersion`，或统一采用强 ETag/`If-Match`；缺失和不匹配的协议要明确。
- 捕获 `ObjectOptimisticLockingFailureException`/`OptimisticLockException` 和显式版本不匹配，统一返回 409，不回显 SQL。前端收到 409 后重新加载并提示冲突，服务端不要自动覆盖或盲目重试整笔业务操作。
- 测试至少覆盖 update-vs-update、update-vs-audit、audit-vs-reverse-audit 以及旧 `expectedVersion` 的串行提交；最终状态必须来自唯一成功的事务。

### High-08：附件密钥信封与轮转兼容

- 新增独立 `ATTACHMENT_KEK`，不再从 TOTP 材料派生。生产 KEK 由 KMS/Vault/Secret Manager provider 提供；对应 `sys_security_secret` 行只保存 provider key ID 或包封后的 KEK，不保存可直接使用的明文根密钥。JWT、TOTP 和 OSS 材料迁移到 provider 引用可作为后续独立步骤，不能借附件改造一次性混改全部密钥域。
- 每个附件生成独立 32-byte DEK，以 AES-256-GCM 加密正文。`sys_attachment` 通过新迁移增加 `encryption_format/encryption_key_version/wrapped_data_key`；DEK 用对应版本 KEK 包封，AAD 同时绑定 attachment ID、格式和 KEK 版本，防止把密钥信封移到另一附件。
- 新对象使用自描述格式 `LEOENC2 | algorithm(1 byte) | keyVersion(4 bytes) | IV(12 bytes) | ciphertext+GCM tag`，数据库元数据与头部版本必须一致。未知算法、未知 key version、信封/头部篡改和 GCM 认证失败都应失败关闭。
- V2 cryptor 使用 `InputStream -> OutputStream` 接口，上传时流式加密到临时对象；下载时先解密到受限临时文件并完成 GCM tag 校验，再向客户端输出，避免 `readAll()` 占满堆或在认证失败前发送未经完整验证的明文。
- 读取是否解密必须依据附件行 `encryption_format`，不能依据当前全局 `encryptedStorage` 开关。`NONE` 明确表示明文，`LEOENC1/LEOENC2` 表示对应密文，`NULL` 仅用于待分类 legacy；加密模式继续禁止客户端绕过后端直接上传未加密对象。
- KEK 轮转先在数据库锁保护下创建新 active 版本并切换新写入，再通过 `FOR UPDATE SKIP LOCKED` 分批解包和重包 `wrapped_data_key`。正文由 DEK 加密且保持不变，因此轮转不需要下载或重写大对象；旧 KEK 在引用计数归零前不可删除。
- 兼容发布顺序为：先部署元数据字段、`LEOENC1/LEOENC2` 双读且仍写旧格式；全部实例升级后切换 `LEOENC2` 新写；再将 legacy 写入不可变新对象，校验解密和摘要后短事务切换路径与元数据；最后关闭 legacy 写入并开放 KEK 轮转。旧应用不得与新格式写入阶段混跑。
- 对无法确定版本的 `LEOENC1`，迁移任务可以有界尝试保留的历史 TOTP key。迁移未完成前阻止 TOTP key 退役；在线下载路径不能为每个请求无界遍历历史密钥。
- 测试覆盖混合明文/V1/V2、错误附件 ID、未知版本、信封和正文篡改、轮转时并发上传、迁移崩溃恢复。重包 DEK 后正文对象 SHA-256 应保持不变，轮转前后附件均可读。

### High-09：会话撤销 Outbox

- 新增会话撤销 outbox 表，至少包含 `id/session_id/user_id/event_type/blacklist_until/status/attempts/next_attempt_at/locked_at/locked_by/last_error/created_at/completed_at`，并对 `(event_type, session_id)` 建唯一约束。`blacklist_until` 在撤销事务中按 access token 最长剩余寿命固化，worker 只写剩余 TTL。
- 普通注销、管理员单个撤销、批量撤销和密码变更统一走 `SessionRevocationService`：同一数据库事务内更新 `auth_refresh_token` 并执行幂等 `ensureEvent`。首次调用插入；`PENDING/PROCESSING` 保持原状态；若 token 尚未超过 `blacklist_until`，重复撤销会把 `COMPLETED/DEAD` 重置为 `PENDING`，从而可补偿 Redis 状态丢失。Redis 操作不得再放在事务同步回调中。
- worker 用 `FOR UPDATE SKIP LOCKED` 小批量领取，状态为 `PENDING -> PROCESSING -> COMPLETED`；失败增加 attempts 并指数退避，超过阈值进入 `DEAD` 并告警。`PROCESSING` 使用 lease，进程崩溃后可恢复。
- `blacklistSession` 和 `clearSession` 必须幂等。重复撤销已撤销记录仍要确保 outbox 存在，不能提前返回而遗漏补偿；普通注销无论传播是否延迟都应清理客户端 Cookie。
- 增加小批量 `SessionRevocationReconciler`，周期扫描仍处于 `blacklist_until` 窗口内的已撤销 session，批量检查 Redis blacklist key，仅对缺失项重新 `ensureEvent`，用于 Redis flush/故障恢复后的自愈。健康 Redis 下撤销传播目标建议不超过 5 秒，Redis 恢复后建议不超过 30 秒；对 oldest pending age、DEAD 数和处理失败率设告警。
- 验收覆盖数据库回滚、Redis 停机/flush 后恢复、重复事件、两个 worker 竞争、lease 超时、传播 SLO 和批量处理。Redis 故障时接口不得返回“事务已回滚”的假象，旧 access token 最终必须被拒绝。

### High-10：有界同步导出与可靠异步任务

- 单张业务单据等已知小文件可继续同步，但必须配置最大行数和最大输出字节数。报表全量、物料全量和附件清单改为通用异步导出任务，不再在 HTTP 请求内同时持有完整查询结果、Workbook 和最终 `byte[]`。
- 通用模块建议包含 `ExportTaskApplicationService`、`ExportTaskProcessor`、按类型注册的 `ExportHandler` 和流式 `ExportStorage`。任务状态使用 `QUEUED -> RUNNING -> SUCCEEDED | FAILED -> EXPIRED`，数据库保存请求人、过滤条件、进度、lease、结果元数据、错误码和版本。
- 创建接口返回 202 与 `Location`；状态查询只允许请求人或管理员访问，下载时再次校验权限。未完成、过期和越权分别返回 409、410、403。
- 查询使用 keyset 分页或处于有效事务中的 JDBC cursor/fetch size，每批 500 至 2000 行。CSV 直接写 `OutputStreamWriter`；普通大表 XLSX 使用 `SXSSFWorkbook` 并在 finally 中 `dispose`。依赖模板克隆的固定打印不能盲目替换为 SXSSF，应保持有界同步输出。
- 异步线程没有请求线程的 `DataScopeContext`。worker 开始时必须重新加载请求人权限并建立数据范围；权限已撤销则失败。结果先写临时对象，成功后原子发布，失败、超限、进程恢复和过期清理都要删除残留。
- 以 10 万级数据和受限 JVM 堆做验收，确认 SQL 始终有 keyset/LIMIT、峰值内存不随总行数线性增长，并覆盖双 worker 领取、进程重启、写盘/上传失败及越权下载。

### Medium-01：待收票报表的单 SQL 分页

- 建立专用查询仓储，以采购订单明细为主表。采购订单限定 `AUDITED/PURCHASE_COMPLETED`，收票聚合只计算 `INVOICE_RECEIVED` 且未删除的收票单；草稿收票不得占用额度。
- CTE 先按 `source_purchase_order_item_id` 聚合已收重量和金额，主查询使用 `GREATEST(source - COALESCE(received, 0), 0)` 计算待收值，并只保留待收重量或金额大于 0 的行。关键词、供应商、日期和 `created_by` 数据范围必须在数据库层生效。
- data query 和 count query 复用同一 predicate 构造，排序只接受白名单字段并追加 `purchase_order_id, item_id` 稳定排序；删除 `MAX_ORDER_CANDIDATES` 和内存二次分页。
- 根据真实 `EXPLAIN (ANALYZE, BUFFERS)` 决定索引，优先评估来源明细外键、收票状态/删除标记及采购单状态/日期/owner 组合，不能仅凭字段列表堆索引。
- 集成数据必须超过 1000 张采购单，并混合草稿/生效收票、跨部门数据和相同排序值，逐页断言无遗漏、无重复且 `totalElements` 精确。

### Medium-02：软删除唯一性的分类迁移

- 先按业务语义分类，不能批量把所有唯一约束改成部分索引。登录名、业务单号、发票号、安全 key 等审计标识通常永久唯一；项目、客户、供应商、仓库等主数据编码是否允许复用，需要产品规则明确。
- 若编码永久唯一，服务层查询必须包含已删除记录并返回可理解的冲突或恢复入口。若只要求活动记录唯一，则在新的 Flyway 中先检查活动重复，再删除永久 constraint，建立 `WHERE deleted_flag = false` 的部分唯一索引，并移除实体上误导性的 `unique=true`。
- 以项目编码为例，最终状态是移除 `md_project_project_code_key` 并保留命名稳定的 `uk_md_project_project_code_active`；不要修改基线。小表可在同一事务 drop constraint 后创建部分索引；大表先用独立非事务 Flyway `CREATE UNIQUE INDEX CONCURRENTLY`，成功后再用下一条短事务迁移删除旧 constraint，并单独验证中断恢复。
- 服务层预检查只用于友好提示，数据库唯一索引仍是并发正确性的最终保证。`DataIntegrityViolationException` 只能按已知 constraint 名翻译为 409，未知完整性错误继续作为 500 记录。

### Medium-03：统一 HTTP 错误契约

- 让 `GlobalExceptionHandler` 继承 `ResponseEntityExceptionHandler`，由 `ApiErrorResponseFactory` 统一构造现有 `ApiResponse`，由 `ErrorCodeHttpStatusMapper` 唯一维护业务码与 HTTP 状态，避免多个 handler 各自决定协议。

| HTTP | 场景 |
| --- | --- |
| 400 | JSON、参数绑定、类型、缺失参数、分页数字和排序校验错误 |
| 404 | 资源不存在 |
| 405 | HTTP 方法不支持，并保留 `Allow` 响应头 |
| 406 | 响应媒体类型不可接受 |
| 409 | 已知唯一约束、期望版本或乐观锁冲突 |
| 413 | multipart/请求体超过上限 |
| 415 | 请求 `Content-Type` 不支持 |
| 422 | 已通过协议校验但业务规则拒绝 |
| 503 | 明确配置为 fail-closed 的依赖不可用 |
| 500 | 未分类服务端异常，只返回通用消息 |

- `PageQueryArgumentResolver` 捕获数字格式和溢出错误并转换为 `VALIDATION_ERROR`；所有开放自定义排序的 Controller 都必须绑定 `PageSortFieldCatalog`，没有 catalog 时拒绝 `sortBy`。
- 字段校验详情不得回显 password、token、secret 等 rejected value。数据库异常只识别已登记的 constraint，不能把驱动错误文本直接返回客户端。
- 使用真实 MockMvc 分发覆盖表中状态，断言 HTTP、业务码、`traceId`、Content-Type 和必要响应头；直接调用 Advice 方法的单元测试不能替代协议测试。

### Medium-04 与 Medium-06：候选查询和供应商对账状态

- 先把 `SupplierStatementSourceService` 的两个 `PURCHASE_COMPLETED` 改为 `INBOUND_COMPLETED`，并用真实采购入库状态集建立回归测试；这是候选 SQL 重构前也应独立交付的功能修复。
- 排他候选改为相关 `NOT EXISTS`，优先关联现有 `source_*_id`；当前只有 `source_no` 的对账明细先使用规范化业务单号，本轮不为查询优化额外引入冗余 header ID。不得先加载全量目标单据、构造不断增长的排除集合再生成 `NOT IN`；编辑场景在子查询中排除当前目标 ID。
- 部分分配候选不能简单 `NOT EXISTS` 整个来源，应在数据库聚合已分配量并筛选 `source_total > allocated_total`。count query 必须使用相同占用和数据范围谓词。
- 为目标明细的 `source_*_id` 及必要的 parent ID 增加经过执行计划验证的索引。压测应覆盖百万级历史目标、深分页、空来源 ID 和被软删目标，确认 SQL 参数数量与结果页大小无关。

### Medium-05：元数据路由

- `MetaController` 改为 `@RequestMapping("/meta")`，由 `server.servlet.context-path=/api` 统一形成外部 `/api/meta/**`。Controller 层不得再次拼接 context path。
- MockMvc 请求显式设置 `.contextPath("/api")`，同时断言外部 `/api/meta/code` 成功、`/api/api/meta/code` 为 404，并核对 Nginx `/api/` 转发后路径不被二次改写。

### 条件性风险：多实例维护任务

- 多实例启用前，为每个维护任务增加基于数据库时间的租约，使用原子条件更新取得 `locked_by/locked_until`；本地 `AtomicBoolean` 只用于防止同实例重入。
- 文件归档采用“写临时文件并校验 -> 原子 rename/发布 -> 标记数据库批次可删除 -> 分批删除”的可恢复状态机。任何阶段重试都必须幂等，不能在归档文件未确认持久化前删除数据库记录。
- 验收需要两个实例同时触发、持锁实例崩溃、租约超时接管、文件写满和数据库删除失败场景。

### 质量门禁落地

1. 先为已人工确认的 SpotBugs 噪声建立精确到 bug pattern 与类的 exclude filter，并在注释中写明原因；禁止排除整个生产包或全部测试代码。
2. 静态分析命令改为 `mvn -B -ntp -DskipTests checkstyle:check spotbugs:check`，确保报告项会让 job 失败。
3. 增加 required 的 `verify-all` job 执行 `mvn -B -ntp verify`，覆盖根包 `LeoApplicationTest`、`com.leo.erp.mcp`、JaCoCo check 和未来新增顶级包。现有矩阵可保留用于快速反馈，但在自动发现和覆盖率合并可靠前不能替代全量 job。
4. 空 PostgreSQL 环境必须从 `V1` 迁移到最新并执行 Flyway validate；分支保护至少要求静态分析、全量验证、迁移验证和应用 smoke 全部成功。

## 质量门禁与测试发现

### 修复阶段最终验证结果

完整验证在隔离工作副本中执行，数据库写入仅指向 `leo_test`：

```bash
mvn -B -ntp -Dmaven.gitcommitid.skip=true verify
```

结果：

- `BUILD SUCCESS`，耗时 3 分 05 秒。
- Surefire 生成 627 份测试套件报告，共 5743 项测试；Failures 0、Errors 0、Skipped 0。
- Checkstyle 门禁通过，阻断级 violation 为 0；报告中仍有 10671 条非阻断 warning，当前配置不会因 warning 使构建失败。
- JaCoCo check 通过，分析 893 个类，全部现有覆盖率阈值满足。
- Flyway 显式校验通过，`V1` 至 `V11` 共 11 条迁移均有效。
- PostgreSQL 并发和唯一性回归 6/6 通过：末余额度竞争、反向 ID 锁顺序、排他来源、六类活动记录部分唯一索引、软删复用及永久业务标识约束均符合预期。
- PostgreSQL auto-flush 回归 3/3 通过，查询期间不会写入缺失父关联或必填字段的半初始化明细。

JaCoCo 报告级覆盖率：

| 指标 | 覆盖 | 覆盖率 |
| --- | ---: | ---: |
| Instruction | 110310 / 111319 | 99.09% |
| Branch | 8426 / 8652 | 97.39% |
| Line | 23256 / 23518 | 98.89% |
| Complexity | 9151 / 9418 | 97.17% |
| Method | 4979 / 5041 | 98.77% |
| Class | 890 / 893 | 99.66% |

### 原始审查本地验证结果

执行：

```bash
mvn verify
```

结果：

- 构建成功。
- 测试总数：5644。
- Failures：0。
- Errors：0。
- Skipped：0。
- JaCoCo 行覆盖率：99.07%。
- Checkstyle 报告 0 violation，但编译存在少量已弃用 API 警告。

单独执行：

```bash
mvn -B -ntp spotbugs:check
```

结果为失败，共 51 项 High 级报告。人工复核后，多数来自测试代码默认字符集和测试桩传 null；两个生产代码潜在空指针报告属于工具无法识别业务校验必然抛出的误报。即使如此，当前门禁仍存在三个确定问题：

1. [`.github/workflows/ci.yml:37`](../.github/workflows/ci.yml#L37) 执行的是 `spotbugs:spotbugs`，只生成报告，不会像 `spotbugs:check` 一样让构建失败。
2. CI 测试矩阵遗漏根包 `LeoApplicationTest` 和 `com.leo.erp.mcp`，共 26 个测试没有在正常 CI test job 中执行。
3. [`pom.xml:351`](../pom.xml#L351) 的 JaCoCo check 绑定在 `verify`，但矩阵只执行 `test`，打包任务执行 `-DskipTests package`，因此 CI 没有执行覆盖率阈值检查。

建议为 SpotBugs 增加精确 exclude filter，仅排除已确认的测试噪声和误报，然后让 `spotbugs:check` 成为强制门禁；CI 至少增加一个兜底全量测试 job，或通过自动发现方式保证新顶级包不会被矩阵静默遗漏。

## 条件性风险

### 多实例维护任务

部分维护任务只使用进程内互斥，多实例部署时可能重复执行；操作日志归档的文件移动和数据库删除也不具备跨资源原子性。当前部署如果始终为单实例，风险暂时受限；若计划扩容，应引入数据库/Redis 分布式租约，并为文件与数据库状态建立可恢复流程。

## 修复优先级

### 第一批：阻断直接安全风险

1. 为 OOBE 增加 bootstrap token、网络限制和数据库首建锁。
2. 修复销售订单打印记录级权限校验。
3. 修正 Nginx XFF、后端可信代理解析及无界本地限流桶。
4. 为密码修改补充全会话失效。

### 第二批：恢复数据一致性

1. 为来源分配、开收票额度和排他占用增加锁或数据库约束。
2. 修正库存、出入库和待收票报表状态口径。
3. 将供应商对账的采购入库状态改为 `INBOUND_COMPLETED`。
4. 为核心聚合新增 `V10` 版本列和 `@Version`。
5. 统一软删除唯一性语义，并通过新 Flyway 迁移落实。

### 第三批：可靠性与可扩展性

1. 接通或移除数据库限流规则管理面。
2. 为 Redis 提交后副作用引入 outbox/重试。
3. 修复附件密钥版本化与轮转流程。
4. 将全量候选与导出改为数据库分页、游标和流式输出。
5. 完善 HTTP 异常映射、排序白名单和端到端协议测试。
6. 让 SpotBugs、全量测试、JaCoCo 和 Flyway validate 成为 required CI 门禁。

## 必补测试

1. 缺失或错误 bootstrap token 的 setup 写入失败，两个实例并发初始化只能一个提交。
2. `SELF/DEPARTMENT/ALL` 用户通过真实切面访问 GET、POST 打印入口，越权请求返回 403。
3. 伪造 XFF、合法多级代理、IPv6 和超长代理链解析正确，两个实例共享同一限流 bucket。
4. 修改密码后旧 access/refresh token 均失效，并发刷新不能签发旧凭据版本 token。
5. 耗尽旧 bucket 后提交动态限流规则，无需重启即可按新 revision 生效，类级 fallback 与跨实例缓存失效符合约定。
6. 两个独立事务消费同一来源剩余额度时只能一个成功，锁定旧新来源并集且不会死锁。
7. 草稿、预出库、已审核和完成状态逐一验证数量、重量两组 `onHand/reserved/available`、旧字段别名与出入库流水。
8. 编辑、明细更新与审核并发时产生 409，聚合根版本递增且不得静默覆盖。
9. 明文、`LEOENC1/LEOENC2` 混合附件在 KEK 轮转和中断恢复前后均可解密；重包 DEK 后对象摘要不变，篡改信封或密文必须失败。
10. Redis 在数据库提交后失败或 flush 时，会话撤销事件可重试、可接管、可再对账，并在约定 SLO 内使旧 access token 失效。
11. 超过 1000 张采购单时待收票分页和总数仍准确，草稿收票不计入已收金额。
12. 供应商对账仅接受 `INBOUND_COMPLETED` 的采购入库，草稿和已审核状态被拒绝。
13. 软删除唯一索引覆盖删除后复用、并发创建及恢复冲突，已知约束返回 409。
14. `page=abc`、未知排序、405、406、413、415 和乐观锁冲突符合统一 HTTP 契约。
15. 百万级候选使用 `NOT EXISTS` 和数据库分页；10 万级导出验证分批、任务恢复与峰值内存。
16. `/api/meta/code` 可达且 `/api/api/meta/code` 为 404。

## 审查边界

- 未对真实生产数据库执行写入或迁移。
- 已在 `leo_test` 执行完整验证、Flyway `migrate/validate`、状态矩阵、大分页、唯一性、auto-flush 和双事务锁竞争回归；未执行生产压力测试或大数据量性能基准。
- 未执行动态渗透测试。
- 未验证生产环境是否存在仓库外的 WAF、bootstrap 网络隔离、KMS 或独立 seed 执行流程。
- 来源额度、反向 ID 锁顺序和排他来源已由真实 PostgreSQL 双事务测试固化；乐观锁的 update-vs-audit 客户端版本协议仍待补充。
