# 状态枚举中文 label → 稳定英文 code 迁移

## 1. 背景与目标

2026-11-14 API 审计要求对外枚举值稳定、可被客户端安全依赖。此前 `status` 类列直接存中文展示值
（`正常` / `草稿` / `已审核` / `完成采购` / `完成销售` …），中文文案一旦调整就会破坏 API 契约。
本迁移把数据库、后端常量、SQL 字面量、前端词表统一改为英文 code，中文只保留在展示层。

## 2. 映射表（唯一口径）

| 中文 label | code | 说明 |
| --- | --- | --- |
| 正常 | `NORMAL` | 主数据/角色/公司设置启用 |
| 禁用 | `DISABLED` | 主数据停用 |
| 草稿 | `DRAFT` | 单据草稿 |
| 已审核 | `AUDITED` | 通用单据审核态 |
| 审核 | `CONTRACT_REVIEWED` | 销售合同审核态（与通用「已审核」区分） |
| 签发 | `CONTRACT_ISSUED` | 销售合同签发态 |
| 已完成 | `COMPLETED` | 通用完成态 |
| 作废 | `VOIDED` | 销售合同作废 |
| 完成采购 | `PURCHASE_COMPLETED` | 采购订单完成 |
| 完成销售 | `SALES_COMPLETED` | 销售订单完成 |
| 完成入库 | `INBOUND_COMPLETED` | 采购入库完成 |
| 交付核定 | `DELIVERY_VERIFICATION` | 销售订单交付核定 |
| 已签署 | `SIGNED` | 合同签署 |
| 未签署 | `UNSIGNED` | 合同未签署 |
| 未审核 | `UNAUDITED` | 未审核 |
| 执行中 | `EXECUTING` | 合同执行中 |
| 归档 | `ARCHIVED` | 合同归档 |
| 待确认 | `PENDING_CONFIRM` | 对账单待确认 |
| 已确认 | `CONFIRMED` | 已确认 |
| 待审核 | `PENDING_AUDIT` | 待审核 |
| 报价 | `QUOTING` | 报价单状态（非 `DocumentStatus`，单独常量） |
| 成功 | `SUCCESS` | 操作日志结果 |
| 失败 | `FAILED` | 操作日志结果 |
| 已付款 | `PAID` | 历史财务状态（迁移前遗留数据） |
| 已收款 | `RECEIVED` | 历史财务状态（迁移前遗留数据） |
| 已归档 | `ARCHIVED` | 采购/销售合同（`ct_*`）的历史归档态，与「归档」同码 |
| 已开票 | `INVOICE_ISSUED` | `fm_invoice_issue` 终态（历史库/约束中的取值） |
| 已收票 | `INVOICE_RECEIVED` | `fm_invoice_receipt` 终态（历史库/约束中的取值） |
| 停用 | `DISABLED` | 结算账户明细 JSON 旧值（`settlement_accounts`，不入库迁移） |

映射的唯一定义在：

- 数据库迁移：`leo/src/main/resources/db/migration/V219__migrate_status_labels_to_codes.sql`
- 后端：`DocumentStatus` / `StatusConstants` / `OperationLogConstants` / `QuoteSheetStore`
- 前端：`aries/src/constants/status-constants.ts` 的 `STATUS_LABELS` / `statusLabel()`

## 3. 数据库迁移（V219）

迁移分四步（顺序不可调换）：

1. **状态列扩容**：`status` / `result_status` 由 `varchar(16)` 扩到 `varchar(32)`。
   最长 code 为 `DELIVERY_VERIFICATION`（20 字符），原 16 位装不下；Java 实体 `@Column(length)`
   与请求 DTO 的 `@Size(max)` 同步为 32（由 `FieldLengthContractValidationTest` 锁定）。
2. **删除仅允许中文 label 的 CHECK 约束**（29 个），否则第 3 步 UPDATE 会违反约束。
   已是英文 code 的约束（`mk_supplier_price_item` / `mk_supplier_price_list` /
   `sys_print_template` / `sys_security_secret` / `sys_user`）保持不变。
3. **修正列默认值并迁移数据**：`md_*`、`sys_role` 的 `DEFAULT '正常'` → `'NORMAL'`，
   `mk_quote_sheet` 的 `DEFAULT '报价'` → `'QUOTING'`；随后用临时映射表 `status_label_map`
   对 37 张表执行 `UPDATE ... FROM status_label_map WHERE col = label`，已是 code 的值原样保留。
4. **用 code 重建 CHECK 约束**，取值集合与迁移前一一对应（例如 `po_purchase_order`
   `DRAFT/AUDITED/PURCHASE_COMPLETED`）。

覆盖主数据、合同、资金、采购、销售、对账单、报价单、系统设置、操作日志（分区父表）。

- **不迁移**：`jv_snapshot.state`（JSON 快照正文）、`mk_supplier_price_collab_doc.state`（二进制协作状态）、
  `sys_company_setting.settlement_accounts`（结算账户明细 JSON，应用侧双读：`StatusConstants.toCode`
  把旧值「正常/停用」归一化为 code）。
- 操作日志 `sys_operation_log` 为分区表，`UPDATE` 父表会路由到全部分区；分区较多时注意锁与执行时长。
- 脚本可重复执行：`DROP CONSTRAINT IF EXISTS` + 仅精确匹配中文 label 的行会被修改。

## 4. 应用契约变化

- 响应：所有 `status` / `resultStatus` / `signStatus` 字段输出英文 code；中文仅由前端 `statusLabel()` 展示。
- 请求：接受英文 code；迁移期兼容旧中文 label（`StatusConstants.toCode` / `CrudStatusGuard` /
  `StatusCodeDeserializer` 统一归一化为 code）。`SalesContractRequest` 的 `@Pattern` 同时接受 code 与旧 label。
- 查询参数：`status` 筛选同样接受 code 或旧 label，服务端经 `StatusConstants.normalize*` / `toCode`
  归一化为 code 后查询；操作日志 `resultStatus` 由 `OperationLogService` 双读归一化。
- 参数校验：各模块 `status` 列扩到 `varchar(32)`，DTO `@Size(max = 32)` 与实体 `@Column(length = 32)` 同步。
- 操作日志：结果值 `SUCCESS` / `FAILED`；报价单状态 `QUOTING`；主数据 `NORMAL` / `DISABLED`。
- 错误文案：`CrudStatusGuard` 用 `DocumentStatus.label()` 把 code 翻回中文展示，提示语保持中文，
  API/DB 仍只使用 code。
- 前端：`STATUS` / `DOCUMENT_STATUS` 常量值改为 code；展示层统一调用 `statusLabel()`。

## 5. 发布顺序（生产）

破坏性数据迁移必须按兼容发布推进，避免应用回滚时 DB 不兼容：

1. **兼容发布**：部署能同时读取 code 与中文 label 的应用版本（normalize 双读），DB 仍为中文。
2. **数据迁移**：执行 V219，把 DB 值改为 code；旧应用仍能通过双读运行。
3. **清理发布**：确认无回滚需求后，移除 label 双读与旧中文常量。

本仓库开发环境按上述三步落地；生产执行时不要跳过第 1 步。

## 6. 回滚

Flyway 不提供 down 脚本；如需回滚，用下面的反向映射手工执行（仅限未清理 label 双读的版本）：

```sql
CREATE TEMPORARY TABLE status_code_map (code VARCHAR(64) PRIMARY KEY, label VARCHAR(64) NOT NULL);
INSERT INTO status_code_map VALUES
 ('NORMAL','正常'),('DISABLED','禁用'),('DRAFT','草稿'),('AUDITED','已审核'),
 ('CONTRACT_REVIEWED','审核'),('CONTRACT_ISSUED','签发'),('COMPLETED','已完成'),
 ('VOIDED','作废'),('PURCHASE_COMPLETED','完成采购'),('SALES_COMPLETED','完成销售'),
 ('INBOUND_COMPLETED','完成入库'),('DELIVERY_VERIFICATION','交付核定'),
 ('SIGNED','已签署'),('UNSIGNED','未签署'),('UNAUDITED','未审核'),('EXECUTING','执行中'),
 ('ARCHIVED','归档'),('PENDING_CONFIRM','待确认'),('CONFIRMED','已确认'),
 ('PENDING_AUDIT','待审核'),('QUOTING','报价'),('SUCCESS','成功'),('FAILED','失败'),
 ('PAID','已付款'),('RECEIVED','已收款');
-- 对每张表执行：UPDATE <table> c SET status = m.label FROM status_code_map m WHERE c.status = m.code;
```

## 7. 风险

- 操作日志分区表更新可能产生较多行锁，建议低峰执行并关注执行时长；列扩容会短暂持有 ACCESS EXCLUSIVE 锁，
  业务低峰执行。
- 任何遗漏的中文状态字面量会导致比较/筛选失效；后端由「状态常量 + SQL 字面量契约测试」兜底
  （`SqlStatusLiteralContractTest`），前端由 `statusLabel` 单测覆盖。
- 结算账户明细 JSON（`settlement_accounts[].status`）未做数据迁移，靠应用侧 `toCode` 双读；
  若后续要做数据清洗，可追加迁移把 JSON 内旧值改为 code。
- 中文 label 仍保留在 `DocumentStatus.label()` / 错误文案 / 操作日志 `action_type` 中，属于展示层与历史值，
  不属于状态存储值。
- 历史审计/规格文档中的中文状态描述不改写，属于历史记录。
