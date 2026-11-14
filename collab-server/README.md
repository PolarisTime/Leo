# price-collab（供应商价格表实时协作边车）

供应商价格表的 Yjs CRDT 同步边车：**实时同步** + **文档持久化** + **物化回 SQL**。
设计依据：`aries/docs/supplier-price-realtime-collab.md`（§3 架构、§4 文档模型、§5 持久化、§6 物化、§7 鉴权）。

```
浏览器 A/B/C ──WS /collab──▶ price-collab (:11212) ──▶ Postgres（leo 库）
                              ├ 文档快照 + 增量
                              ├ 物化 mk_supplier_price_item / _list
                              └ 审计 mk_supplier_price_cell_op
```

- 端口 **11212**（与 leo 后端 11211、前端 3100 并列），WebSocket 路径 **`/collab`**，健康检查 **`/healthz`**。
- 只写这 5 张表：`mk_supplier_price_collab_doc`、`mk_supplier_price_collab_update`、
  `mk_supplier_price_cell_op`、`mk_supplier_price_item`、`mk_supplier_price_list`。
  **绝不写 `mk_quote_item_price`**（比价单价是另一条业务线）。
- 协作开启期间 `mk_supplier_price_item` 只由本服务物化写（唯一写者，见设计文档 §6.1）。

---

## 1. 快速开始

```bash
cd leo/collab-server
npm ci --include=dev            # 注意：环境若已设 NODE_ENV=production，npm 会默认省略 devDependencies
cp .env.example .env            # 改成自己的 DATABASE_URL / COLLAB_JWT_SECRET
npm run build && npm start
curl -s http://127.0.0.1:11212/healthz | jq
```

没有数据库也能跑（数据不落库，用于联调前端）：

```bash
COLLAB_STORE=memory COLLAB_JWT_SECRET=dev-secret-at-least-16 PORT=11212 npm run dev
```

> 若 `~/.npm` 是只读的（容器/受限沙箱常见），给 npm 指定可写缓存：
> `npm ci --include=dev --cache /tmp/npm-cache`。

## 2. npm 脚本

| 脚本 | 作用 |
| --- | --- |
| `npm start` | 运行编译产物 `dist/index.js`（生产） |
| `npm run dev` | `tsx watch` 直跑 TS（本地开发） |
| `npm run build` | `tsc -p tsconfig.build.json` → `dist/` |
| `npm run typecheck` | `tsc --noEmit`（源码 + 测试） |
| `npm test` | `node --test`（tsx 加载 TS），**不依赖数据库** |
| `npm run lint` | `biome check .`（格式 + lint + import 排序） |

## 3. 环境变量

完整清单见 [`.env.example`](./.env.example)。要点：

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `PORT` / `COLLAB_HOST` | `11212` / `0.0.0.0` | 裸机建议 `COLLAB_HOST=127.0.0.1` 交给 nginx |
| `COLLAB_WS_PATH` / `COLLAB_HEALTH_PATH` | `/collab` / `/healthz` | 其它路径：WS 升级返回 404、HTTP 返回 404 |
| `DATABASE_URL` | 本地 dev `postgres://postgres:postgres@127.0.0.1:5432/leo` | **生产必须显式配置** |
| `COLLAB_JWT_SECRET` | 无（生产缺失即启动失败） | 与 Java 签发端共享的 HS256 密钥，≥16 字符 |
| `REDIS_URL` | 空 | 配了才启用跨实例扇出 + `collab:list-changed` 发布 |
| `COLLAB_DEBOUNCE_MS` | `500` | 快照写回 debounce |
| `COLLAB_MAX_UPDATE_BYTES` | `262144` | 单条 update 上限（§7.2） |
| `COLLAB_MAX_CONNECTIONS_PER_USER` | `5` | 每用户并发连接上限 |
| `COLLAB_SNOWFLAKE_WORKER_ID` | `7` | **必须与 Java `leo.id.machine-id` 错开** |
| `COLLAB_SCHEMA_CHECK` | `strict` | 启动自检表结构，缺表即失败；本地无库可设 `warn` |
| `COLLAB_STORE` | `postgres` | `memory` 仅本地调试 |

## 4. 文档模型

```
room = documentName（Hocuspocus 的文档名）
doc.getMap('meta')                       // listId / supplierId / brandName / quotedOn / warehouse / version
doc.getMap<Y.Map>('cells')               // key = cellKey
  └ Y.Map<{ price, status, remark }>     // 一格 = 该格当前完整状态
cellKey = [category, material, spec, length].join('\u0000')
```

- `price === null` = **不报价**，与 `0` 严格区分（V168 的约定）。
- `status ∈ NORMAL / PENDING / BUNDLED / NEGOTIABLE / OUT_OF_STOCK`（与 SQL CHECK 一致）。
- 「清空」= `price === null && status === 'NORMAL' && remark === ''` → 物化时删除 SQL 行。
- 房间名兼容 `supplier-price:<supplierId>`、`supplier-price-list:<listId>`、纯数字 `<listId>`；
  文档 `meta.listId` 存在时**以文档为准**。

## 5. 持久化（快照 + 增量）

| 时机 | 写什么 |
| --- | --- |
| 首次加载房间、无协作文档 | 从 SQL 价格表生成初始 Y.Doc（阶段 0 影子同步），并立刻落快照 |
| 每次 `onChange` | 增量 update 追加到 `mk_supplier_price_collab_update`（100ms 窗口批量、按内容哈希去重） |
| debounce 500ms / 断连 / 关闭 | 整份文档 upsert 到 `mk_supplier_price_collab_doc`（`ON CONFLICT (room)`，永远一行） |

增量写失败不会丢：批次留在内存队列等下次 flush（优雅退出会显式 `flushAll`）。
崩溃后按「快照 + 增量」重建，Yjs apply 幂等。

## 6. 物化（Y.Doc → SQL）

- **差异写**：每次变更立刻与「上一轮观察到的文档」对比（100–300 格，成本可忽略），
  只把变化的格记进 pending；文档没变化时**不加版本号、不写任何行**。
- **按格归因**：pending 里每格带触发它的 `userId`，审计表 actor 是真正改这格的人。
- **失败重试**：事务失败 → pending 不清空（dirty 永不清），指数退避（500ms → 60s）。
- **单个事务**内完成：
  1. `mk_supplier_price_list.version = version + 1`（并回写 `quoted_on`，列存在时）；
  2. `mk_supplier_price_item` INSERT … ON CONFLICT DO UPDATE（按唯一键 `(list_id, category, material, spec, length)`）；
  3. 清空格 DELETE；
  4. 每格一条 `mk_supplier_price_cell_op` 审计（`list_revision` = 本次物化后的版本号）。
- `sort_order` 只在插入新行时追加（更新不动顺序，避免改个价整表行序跳动）。
- 崩溃恢复：加载文档时若 SQL 基线 ≠ 文档，**立即补物化**（`syncOnLoad`）。
- 物化成功后（可选）`PUBLISH collab:list-changed {listId, version, room, at}`。

## 7. 鉴权与只读

`onAuthenticate` 校验 Java 签发的 HS256 短票据（5 分钟），**两种 claims 形态都支持**：

```jsonc
// 任务约定
{ "sub": "<userId>", "name": "张三", "room": "supplier-price:333...", "writable": true, "color": "#1677ff", "exp": 1731000000 }
// 设计文档 §7.1
{ "sub": "<userId>", "name": "张三", "perms": ["supplier-price-lists:update"], "rooms": ["supplier-price:333..."], "exp": 1731000000 }
```

| 校验 | 行为 |
| --- | --- |
| 签名 / 过期 / 缺 `sub` / 缺 `exp` | 拒绝（关闭码 4401，`permission-denied` 原因回传客户端） |
| 票据不含请求房间 | 拒绝（4403） |
| `writable=false` 或无写权限 | 连接标记 readOnly，写入被拒绝（服务端强制，非 UI 限制） |
| 单条 update > `COLLAB_MAX_UPDATE_BYTES` | 丢弃并断开（`beforeHandleMessage` 拒绝） |
| 每用户连接数 > 上限 | 拒绝（4403） |
| 非 `/collab` 的 WS 升级 | HTTP 404 |

## 8. 健康检查 / 指标 / 日志

`GET /healthz` 返回连接数、房间数、持久化队列、物化队列与 Redis 状态；
物化处于退避重试时 `status=degraded`（文档没丢，但 SQL 落后，监控应告警）。

```json
{"status":"ok","version":"0.1.0","uptimeSeconds":1,"connections":0,"documents":0,
 "persistence":{"pendingUpdateRooms":0,"pendingUpdates":0,"storedDocuments":0,"appendedUpdates":0,"bootstrapCount":0},
 "materializer":{"rooms":0,"pendingRooms":0,"pendingCells":0,"retryingRooms":0,"lastError":null},
 "redis":"disabled","timestamp":"..."}
```

日志是 JSON 行（`journalctl -o cat -u price-collab | jq`），**不记价格值、不记票据原文**。

## 9. 部署

`docs/price-collab.service` 提供 systemd unit（`User=leo`、`ProtectSystem=strict`、`Restart=on-failure`）。
nginx 需要把 WS 升级透传给边车：

```nginx
location /collab {
    proxy_pass http://127.0.0.1:11212;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_read_timeout 300s;
}
location /collab-healthz {
    proxy_pass http://127.0.0.1:11212/healthz;
}
```

## 10. 测试

`npm test`（65 个用例，全部不依赖真库）：

- JWT：签名错/过期/缺 exp/缺 sub/房间越权/只读推导；
- 持久化：首次从 SQL 生成、快照幂等、增量批量与去重、失败保留队列；
- 物化：差异写、重复 store 不写库、改价同 id 更新、清空删除、状态无价保留、
  失败重试不丢、按格归因、quotedOn、加载期对账（崩溃恢复）；
- Postgres 层：表结构自检、SQL 形状（`ON CONFLICT`、事务 BEGIN/COMMIT/ROLLBACK、审计参数）；
- 服务层：`/healthz`、其它 HTTP 404、`/collab` 才允许升级、单用户连接上限、SIGTERM 优雅退出（smoke 见提交说明）。

## 11. 数据库契约（与 V218 对齐）

V218 由队友创建，本服务**只读写**。启动 `COLLAB_SCHEMA_CHECK=strict` 时会校验下列结构，
缺表/缺列会直接失败并打印缺什么。

必需：

```sql
-- 文档快照（room 唯一）
mk_supplier_price_collab_doc(room PK, state BYTEA, updated_at)
-- 增量（append-only）
mk_supplier_price_collab_update(id BIGSERIAL PK, room, update_blob BYTEA, created_at)
-- 格级审计（id 由边车生成雪花，NOT NULL，无默认值）
mk_supplier_price_cell_op(id BIGINT PK, list_id, cell_key, price, price_status,
                          remark, actor_id, list_revision, created_at)
-- 已有表：边车按既有列读写
mk_supplier_price_item(id, list_id, category, material, spec, length, price,
                       price_status, remark, sort_order[, updated_at])
mk_supplier_price_list(id, supplier_id, brand_name, effective_from, version, ...)
```

可选列（有就用，没有降级并告警）：`mk_supplier_price_list.quoted_on`、
`mk_supplier_price_list.updated_by/updated_name`、`mk_supplier_price_item.updated_at`。

> `mk_supplier_price_cell_op.id` 与 `mk_supplier_price_item.id` 都由边车生成雪花
> （41 位毫秒 + 10 位机器 + 12 位序列，与 Java `SnowflakeIdGenerator` 同布局）。
> **`COLLAB_SNOWFLAKE_WORKER_ID` 不能与 Java 的 `leo.id.machine-id` 相同**，否则同一毫秒会撞主键。

## 12. 尚未实现 / 明确边界

- 结构变更（加列/删列）的 `doc-reset` 重建：等 Java 侧 `PUBLISH collab:structure` 落地后接
  （当前可按「删快照 → 下次加载从 SQL 重建」运维兜底）。
- `collab:active:<listId>` 活跃标记（Java 返回 `409 COLLAB_ACTIVE` 用）未实现：键名/TTL 需与 Java 对齐后再接。
- 快照压缩任务（24h / 增量 > 500 条）未内置：可按文档 §5.2 加定时任务，或用运维脚本。
- `mk_supplier_price_cell_op` 的归档策略（按年）未实现，审计永久保留。
- 物化不回写 `meta.version` 给客户端（前端可等 `onMaterialized` 的 Redis 广播或轮询 `/grid`）。
