# 库存运维 API（无前端入口）

**定位：运维 API，不属于业务功能，没有前端入口。** `aries` 对本文三个端点零接入、无对应 UI 页面；
调用方只应是发布脚本、迁移演练清单与人工运维窗口（携带运维账号权限）。不要在业务页面里接入这些端点，
也不要为它们在 `module-contracts` / 页面注册表里新增入口。

三个端点都挂在 `V2InventoryController`（`@RestController` 类级 `@IdempotencyRequired`，
基础路径 `ApiVersion.V2_PREFIX + "/inventory"` 即 `/api/v2.0/inventory`）：

| 端点 | 方法 / 成功状态码 | 权限 | 幂等键 | 用途 |
| --- | --- | --- | --- | --- |
| `/inventory/backfills` | `POST` / **200** | `inventory:backfill` | **必须** `X-Idempotency-Key` | 期初回填：为已审核/完成来源单据补记库存事务 |
| `/inventory/balance-rebuilds` | `POST` / **200** | `inventory:backfill` | **必须** `X-Idempotency-Key` | 余额重建：从账本重算 `inv_balance` 并清理过期维度 |
| `/inventory/balance-mismatches` | `GET` / **200** | `inventory:read` | 不需要（只读） | 守恒校验：分页列出账本与快照不一致的维度 |

> 状态码口径：两个 `POST` 都是**同步命令**，不是「创建可回读资源」，因此成功返回 `200` + 本次结果体、
> **不发 `Location`**，不是 REST 的 `201 Created`。缺少 `X-Idempotency-Key` 时由
> `IdempotencyRequiredInterceptor` 返回 `422`。

---

## 1. `POST /api/v2.0/inventory/backfills` —— 期初回填

**用途**：对「已审核/已完成、但尚无有效库存事务」的来源单据（采购入库 / 销售出库 / 销售退货）
补记库存事务，用于基线导入后的库存初始化与迁移演练。

- **权限**：`inventory:backfill`
- **幂等**：天然幂等。已有有效事务或非法明细的来源行只会计入 `skipped`，不会重复记账；
  重复调用只改变 `skipped` 计数。
- **并发**：持有 `inv:backfill` **会话级排他锁**（与余额重建、以及所有库存记账事务的事务级共享锁互斥）。
  已有回填在跑时立即失败（报 `已有库存期初回填正在执行，请稍后重试`），**不排队**。
- **响应体** `InventoryBackfillResponse`：

  | 字段 | 含义 |
  | --- | --- |
  | `purchaseInCreated` | 本次新记的采购入库事务数 |
  | `salesOutCreated` | 本次新记的销售出库事务数 |
  | `salesReturnCreated` | 本次新记的销售退货入库事务数 |
  | `skipped` | 已有有效事务或非法明细而跳过的来源明细数 |

```bash
BASE_URL=http://127.0.0.1:11211
curl -sS -X POST "$BASE_URL/api/v2.0/inventory/backfills" \
  -H "X-Idempotency-Key: $(cat /proc/sys/kernel/random/uuid)" \
  -H "Accept: application/json"
# 200 OK → {"purchaseInCreated":N,"salesOutCreated":N,"salesReturnCreated":N,"skipped":N}
```

---

## 2. `POST /api/v2.0/inventory/balance-rebuilds` —— 余额重建

**用途**：`inv_balance` 是账本 `inv_transaction` 的增量快照。怀疑快照漂移（例如历史数据修复、
迁移演练、异常中断）时，按 `(material_id, warehouse_id)` 从账本聚合**覆盖写回**快照，并删除
账本已无对应维度的过期行。

- **权限**：`inventory:backfill`（与期初回填同一运维写权限）
- **幂等**：结果幂等。重复执行得到同一账本聚合值；只有账本变化才会改变 `rebuiltRows`。
- **并发与窗口（重要）**：重建持有 `inv:backfill` **事务级排他锁**，与所有库存记账事务互斥——
  它会等待在途记账提交，并在执行期间**阻塞新的库存写**。因此不是可以随时执行的轻量操作，
  **应在业务低峰触发**，且不要与期初回填并发执行。
- **响应体** `InventoryBalanceRebuildResponse`：

  | 字段 | 含义 |
  | --- | --- |
  | `rebuiltRows` | 本次覆盖写入的快照维度行数 |

```bash
curl -sS -X POST "$BASE_URL/api/v2.0/inventory/balance-rebuilds" \
  -H "X-Idempotency-Key: $(cat /proc/sys/kernel/random/uuid)" \
  -H "Accept: application/json"
# 200 OK → {"rebuiltRows":N}
```

---

## 3. `GET /api/v2.0/inventory/balance-mismatches` —— 守恒校验（只读）

**用途**：校验 `Σ账本 == 快照`，返回不一致维度。空 `content` 表示守恒；重建前后各查一次即可确认修复效果。

- **权限**：`inventory:read`；金额字段（`ledgerAmount` / `snapshotAmount`）另受字段权限
  `inventory:read:cost` 控制，无该权限时由 `PermissionDecimalSerializer` 脱敏。
- **只读**：不修改任何数据，不需要幂等键。
- **分页/排序**：使用项目统一 `PageQuery`（`page` 从 0 起、`size` ≤ 200、`sortBy`、`direction`）；
  `sortBy` 白名单：`materialId`、`warehouseId`、`ledgerQuantity`、`snapshotQuantity`、`ledgerAmount`、`snapshotAmount`；
  默认稳定排序 `(materialId, warehouseId)` 升序。
- **响应体** `PageResponse<InventoryBalanceMismatchResponse>`：

  | 字段 | 含义 |
  | --- | --- |
  | `materialId` | 物料 ID（雪花 ID，JSON 中为十进制**字符串**） |
  | `warehouseId` | 仓库 ID（雪花 ID 字符串；`"0"` 表示「无仓库维度」哨兵，不对应任何仓库记录） |
  | `ledgerQuantity` | 账本侧数量（正负相抵后的净数量） |
  | `snapshotQuantity` | 快照侧数量 |
  | `ledgerAmount` | 账本侧金额（受 `inventory:read:cost` 控制） |
  | `snapshotAmount` | 快照侧金额（受 `inventory:read:cost` 控制） |

```bash
curl -sS "$BASE_URL/api/v2.0/inventory/balance-mismatches?page=0&size=50" \
  -H "Accept: application/json"
# 200 OK → {"content":[],"totalElements":0,...} 表示守恒
```

---

## 典型运维流程

1. **低峰窗口**确认无在途库存记账（无单据审核/出库/入库提交）。
2. `GET /inventory/balance-mismatches` 记录基线（迁移演练中即第 3.3 节守恒校验）。
3. 需要初始化库存：`POST /inventory/backfills`（幂等，重复调用只累加 `skipped`）。
4. 需要纠正快照漂移：`POST /inventory/balance-rebuilds`。
5. 再 `GET /inventory/balance-mismatches`，`totalElements = 0` 即守恒。
6. 把两次响应体与耗时留档；迁移演练按 `docs/plans/migration-rehearsal-checklist.md` 第 6/7 步勾选。

## 错误语义

错误统一走 `GlobalExceptionHandler` / `ApiProblemFactory` 的 RFC 9457 `ProblemDetail`（含 `code`、`traceId`）：

- `401` 未认证 / `403` 缺权限（`inventory:backfill` 或 `inventory:read`）；
- `422` 写接口缺 `X-Idempotency-Key`，或参数（`size`/`sortBy`/`direction`）越界；
- 回填/重建的并发冲突以业务错误返回（不会排队重试）。

## 禁止事项

- 不要用这些端点替代业务单据：余额变更必须由有明确业务来源的单据产生
  （台账调整写端点已随整改下线，见 `docs/legacy-retired-modules.md` §4.3）。
- 不要直接改库（`UPDATE inv_balance` / `DELETE FROM inv_transaction`）；确需数据修复时沉淀为 Flyway 前滚脚本。
- 不要在前端页面、定时任务或业务代码里调用这些端点——除既有 `moduleexport` 之外，它们只服务运维流程。

## 变更纪律

新增/修改库存运维端点时，必须同步更新：本文件、`docs/specs/2026-09-14-endpoint-permission-model-plan.md`
的端点权限表、`@Operation` 描述，以及真库门禁用例（`*PostgresTest`）。

## 相关文档

- 端点与权限映射：`docs/specs/2026-09-14-endpoint-permission-model-plan.md`
- 迁移演练清单（期初回填步骤）：`docs/plans/migration-rehearsal-checklist.md`
- 退役资产与已下线写端点：`docs/legacy-retired-modules.md`（§4.3 台账调整写端点下线）
- 真库回归门禁：`docs/real-db-test-gate.md`
