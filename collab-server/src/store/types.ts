/**
 * 存储抽象。
 *
 * <p>把「协作文档持久化」与「物化写 SQL」拆成两个接口，理由有两条：</p>
 * <ol>
 *   <li>物化器与持久化逻辑可以**脱离数据库**单测（内存实现足够覆盖幂等/重试/差异写）；</li>
 *   <li>写 SQL 的地方收敛在 `postgres-store.ts` 一个文件里，评审时不必满仓找 INSERT。</li>
 * </ol>
 */

import type { CellParts, CellValue, PriceStatus } from '../doc-model.js'

/** 从 SQL 读出的条目（用于生成初始 Y.Doc 与物化基线）。 */
export interface StoredListItem {
  id: string
  category: string
  material: string
  spec: number
  length: string
  price: number | null
  status: PriceStatus
  remark: string
  sortOrder: number
}

export interface InitialListSnapshot {
  listId: string
  supplierId: string
  brandName: string | null
  quotedOn: string | null
  warehouse: string | null
  version: number
  items: StoredListItem[]
}

/** 协作文档持久化（`mk_supplier_price_collab_doc` + `_update`）。 */
export interface CollabDocStore {
  /** 读快照状态；没有协作文档时返回 null（由上层决定是否从 SQL 生成）。 */
  loadDocState(room: string): Promise<Uint8Array | null>
  /** upsert 快照状态（同一 room 只会有一行）。 */
  saveDocState(room: string, state: Uint8Array): Promise<void>
  /**
   * 追加一批增量 update（append-only，一条 update 一行）。
   *
   * <p>批量是为了减少 round-trip：一次连续输入可能产生几十条 update，
   * 逐条 INSERT 会把连接打满。返回实际写入的行数（空 update 会被跳过）。</p>
   */
  appendUpdates(room: string, updates: readonly Uint8Array[]): Promise<number>
  /** 无协作文档时，按 room 线索从 SQL 价格表生成初始快照；找不到返回 null。 */
  loadInitialList(room: string): Promise<InitialListSnapshot | null>
  close(): Promise<void>
}

/**
 * 一格的变化。
 *
 * <p>`next === null` 表示「该格被清空」：价格为空、状态 NORMAL、无备注。
 * `existedBefore` 来自物化基线（SQL 里当前有没有这行），决定是删除还是什么都不做。</p>
 */
export interface CellChange {
  cellKey: string
  parts: CellParts
  next: CellValue | null
  /** 触发本次改动的用户（雪花字符串）；未知时 '0'（系统）。 */
  actorId: string
  existedBefore: boolean
}

export interface MaterializeRequest {
  room: string
  listId: string
  changes: CellChange[]
  /** 报价日期有新值时给出；null 表示不变。 */
  quotedOn: string | null
  /** 本次物化的兜底操作人（按格归因缺失时使用）。 */
  actorId: string
}

export interface MaterializeResult {
  /** 物化后的价格表版本号（字符串，BIGINT 语义）。 */
  version: string
  upserted: number
  deleted: number
  audited: number
}

/** 物化写 SQL 的唯一入口。 */
export interface MaterializeStore {
  /** 读 SQL 里当前的格子状态（重启后的 diff 基线，避免把整张表当变更重复审计）。 */
  loadMaterialized(
    room: string,
    listId: string,
  ): Promise<Map<string, CellValue>>
  /** **单事务**应用变更：版本 +1、upsert/delete 条目、写通知审计。 */
  applyChanges(request: MaterializeRequest): Promise<MaterializeResult>
  close(): Promise<void>
}
