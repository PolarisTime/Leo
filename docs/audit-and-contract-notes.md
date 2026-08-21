# 操作日志与审计写入选型契约

leo 后端存在三条审计写入路径，各自适用场景如下。新增模块时按此契约选择，不要混用。

## 三条路径

| 路径 | 入口 | 适用场景 | 特性 |
|---|---|---|---|
| Web 层通用操作 | `@OperationLoggable` 注解（Controller 方法级） | 通用 HTTP 操作留痕（增删改、导入导出、打印等） | 同步拦截器采集，带 moduleName/actionType 元数据 |
| 单据状态事件 | `@DomainEventAudited` + `BusinessOperationEventPublisher` | 单据状态流转（审核、完成、撤销等业务事件） | 异步可靠事件（Modulith 事件表 + 重启重放），失败有 ReliableEventMetrics 监控 |
| 框架内部直调 | `OperationLogService.record(...)` | 登录等框架内部动作（无 Controller 注解入口） | 程序化调用，仅限 auth 登录场景使用 |

## 选型规则

1. Controller 层通用操作 → `@OperationLoggable`
2. 单据状态变更的业务事件 → `@DomainEventAudited`（需要失败重放保障时必须走此路径）
3. 无 HTTP 入口的框架内部动作 → 直调 `OperationLogService.record`（当前仅登录）

长期方向：路径三收敛为路径二的内部实现，新代码不得新增直调点。

---

# 已知命名遗留与技术债登记

## lg_freight_bill_item.material_name 列

- **现状**：上游快照（`SalesOrderSourceItemSnapshot`）无独立物料名称数据源，
  `FreightBillApplyService` 与前端导入映射均以品牌值填充该列，
  运费对账单明细读取同值——全链路语义一致，无数据错位。
- **风险**：字段名误导后续开发者以为存在独立物料名称。
- **处置计划**：若产品确认不需要独立物料名称，按 Flyway 兼容发布流程
  （停写 → 数据迁移 → 列删除）移除实体/DTO/响应中的 materialName 链路。

## 前端手写镜像的后端契约清单（后端变更时须人工同步）

| 前端位置 | 镜像的后端契约 |
|---|---|
| `aries/src/constants/error-codes.ts` | leo `ErrorCode` 枚举 |
| `aries/src/module-system/editor/module-editor-shared.ts`（generateBatchNo） | 雪花 ID epoch 与生成规则 |
| `aries/src/module-system/editor/module-editor-item-sort.ts` | `MaterialSearchPolicy.DEFAULT_SORT` 排序规则 |
| `aries/src/shared/schemas/module-record.ts` | 各模块单据状态机枚举 |
| `aries/src/constants/status-constants.ts` 及 shared-status 等 | 后端状态词汇表 |
| `aries/src/module-system/core/business-no-policy.ts` | 雪花单号适用模块范围 |
| `aries/src/hooks/useModuleQueryRefresh.ts` | purchase-inbound ↔ purchase-order 关联失效规则 |

同步提醒机制：修改上述后端契约时，在 PR 描述中勾对本清单逐项检查。

## 安全敏感仓库的疑似死方法（保守保留）

`SecuritySecretRepository` 与 `RefreshTokenSessionRepository` 中存在疑似零调用的
派生查询方法（密钥轮转、令牌会话清理相关）。因涉及密钥与令牌生命周期，
未纳入批量清理；后续调整密钥轮转/会话治理时一并确认处置。
