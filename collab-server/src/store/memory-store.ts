/**
 * 内存存储：单测与本地无库调试用（`COLLAB_STORE=memory`）。
 *
 * <p>语义必须与 Postgres 实现一致，否则测试通过、上线翻车：版本号 +1、条目 upsert、
 * 清空格删除、每格一条审计、**空变更不产生任何写入**（「重复 store 不重复插入」的保证）。</p>
 */

import * as Y from 'yjs'
import {
  type CellValue,
  DEFAULT_PRICE_STATUS,
  isMeaningfulCell,
  makeCellKey,
  normalizeCellValue,
  type PriceStatus,
  toDbCellKey,
} from '../doc-model.js'
import type { QuoteSheetItemRecord } from '../quote-sheet-model.js'
import type {
  CollabDocStore,
  InitialListSnapshot,
  InitialQuoteSheetSnapshot,
  MaterializeRequest,
  MaterializeResult,
  MaterializeStore,
  QuoteMaterializeRequest,
  QuoteMaterializeResult,
  QuoteMaterializeStore,
  StoredListItem,
} from './types.js'

export interface MemoryListItemRow extends StoredListItem {
  listId: string
}

export interface MemoryListRow {
  listId: string
  supplierId: string
  brandName: string | null
  quotedOn: string | null
  warehouse: string | null
  version: number
}

export interface MemoryAuditRow {
  id: string
  listId: string
  cellKey: string
  price: number | null
  priceStatus: PriceStatus | null
  remark: string | null
  actorId: string
  listRevision: string
}

export interface MemoryQuoteSheetRow {
  sheetId: string
  version: number
}

/** 报价单行（只含可协作的 ton/remark，与 `mk_quote_item` 对齐）。 */
export interface MemoryQuoteItemRow {
  itemId: string
  sheetId: string
  lineNo: number
  ton: number | null
  remark: string | null
}

export interface MemoryStoreSeed {
  lists?: MemoryListRow[]
  items?: MemoryListItemRow[]
  quoteSheets?: MemoryQuoteSheetRow[]
  quoteItems?: MemoryQuoteItemRow[]
}

function itemKey(listId: string, cellKey: string): string {
  return `${listId}\u0001${cellKey}`
}

export class MemoryStore
  implements CollabDocStore, MaterializeStore, QuoteMaterializeStore
{
  readonly docs = new Map<string, Uint8Array>()
  readonly updates = new Map<string, Uint8Array[]>()
  readonly lists = new Map<string, MemoryListRow>()
  readonly items = new Map<string, MemoryListItemRow>()
  readonly audits: MemoryAuditRow[] = []
  readonly quoteSheets = new Map<string, MemoryQuoteSheetRow>()
  readonly quoteItems = new Map<string, MemoryQuoteItemRow>()
  /** quote 房间的 applyQuoteChanges 调用次数（断言空变更不写库）。 */
  quoteApplyCalls = 0
  /** 注入一次失败（测重试）：下一次 applyChanges 直接抛错。 */
  failNextApply: Error | null = null
  /** 注入持续失败：需要手动清空才能恢复（测退避重试）。 */
  failApplyTimes = 0
  /** 统计 applyChanges 调用次数，断言「空变更不产生写入」。 */
  applyCalls = 0

  constructor(seed: MemoryStoreSeed = {}) {
    for (const list of seed.lists ?? []) {
      this.lists.set(list.listId, { ...list })
    }
    for (const item of seed.items ?? []) {
      const cellKey = makeCellKey(item)
      this.items.set(itemKey(item.listId, cellKey), { ...item })
    }
    for (const sheet of seed.quoteSheets ?? []) {
      this.quoteSheets.set(sheet.sheetId, { ...sheet })
    }
    for (const item of seed.quoteItems ?? []) {
      this.quoteItems.set(item.itemId, { ...item })
    }
  }

  async loadDocState(room: string): Promise<Uint8Array | null> {
    return this.docs.get(room) ?? null
  }

  async saveDocState(room: string, state: Uint8Array): Promise<void> {
    // 必须拷贝：Buffer 可能被调用方复用，原样存会得到一份会变的「快照」。
    this.docs.set(room, new Uint8Array(state))
  }

  async appendUpdates(
    room: string,
    updates: readonly Uint8Array[],
  ): Promise<number> {
    let appended = 0
    for (const update of updates) {
      if (update.byteLength === 0) continue
      const queue = this.updates.get(room) ?? []
      queue.push(new Uint8Array(update))
      this.updates.set(room, queue)
      appended += 1
    }
    return appended
  }

  async compactRoom(room: string): Promise<number> {
    const queue = this.updates.get(room)
    if (!queue || queue.length === 0) return 0
    const doc = new Y.Doc()
    try {
      const existing = this.docs.get(room)
      if (existing && existing.byteLength > 0) {
        Y.applyUpdate(doc, existing)
      }
      for (const update of queue) {
        if (update.byteLength > 0) Y.applyUpdate(doc, update)
      }
      this.docs.set(room, Y.encodeStateAsUpdate(doc))
    } finally {
      doc.destroy()
    }
    const removed = queue.length
    this.updates.delete(room)
    return removed
  }

  async listRoomsWithUpdates(minUpdates: number): Promise<string[]> {
    const threshold = Math.max(1, Math.floor(minUpdates))
    const rooms: string[] = []
    for (const [room, queue] of this.updates.entries()) {
      if (queue.length >= threshold) rooms.push(room)
    }
    return rooms
  }

  async deleteDocState(room: string): Promise<void> {
    this.docs.delete(room)
    this.updates.delete(room)
  }

  async loadInitialList(room: string): Promise<InitialListSnapshot | null> {
    const listId = this.resolveListId(room)
    if (listId === null) return null
    const list = this.lists.get(listId)
    if (!list) return null
    const items = [...this.items.values()]
      .filter((row) => row.listId === listId)
      .sort((a, b) => a.sortOrder - b.sortOrder)
    return {
      listId: list.listId,
      supplierId: list.supplierId,
      brandName: list.brandName,
      quotedOn: list.quotedOn,
      warehouse: list.warehouse,
      version: list.version,
      items: items.map((row) => ({
        id: row.id,
        category: row.category,
        material: row.material,
        spec: row.spec,
        length: row.length,
        price: row.price,
        status: row.status,
        remark: row.remark,
        sortOrder: row.sortOrder,
      })),
    }
  }

  async loadMaterialized(
    _room: string,
    listId: string,
  ): Promise<Map<string, CellValue>> {
    const cells = new Map<string, CellValue>()
    for (const row of this.items.values()) {
      if (row.listId !== listId) continue
      cells.set(
        makeCellKey(row),
        normalizeCellValue({
          price: row.price,
          status: row.status,
          remark: row.remark,
        }),
      )
    }
    return cells
  }

  async applyChanges(request: MaterializeRequest): Promise<MaterializeResult> {
    this.applyCalls += 1
    if (this.failNextApply) {
      const error = this.failNextApply
      this.failNextApply = null
      throw error
    }
    if (this.failApplyTimes > 0) {
      this.failApplyTimes -= 1
      throw new Error('内存存储注入的物化失败')
    }
    const list = this.lists.get(request.listId)
    if (!list) {
      throw new Error(`价格表 ${request.listId} 不存在（内存存储）`)
    }

    // 空变更不写库、不加版本：这是「重复 store 不重复插入」的核心保证。
    if (request.changes.length === 0 && request.quotedOn === null) {
      return {
        version: String(list.version),
        upserted: 0,
        deleted: 0,
        audited: 0,
      }
    }

    list.version += 1
    if (request.quotedOn !== null) list.quotedOn = request.quotedOn

    let upserted = 0
    let deleted = 0
    let audited = 0
    for (const change of request.changes) {
      const existing = this.items.get(itemKey(request.listId, change.cellKey))
      const meaningful = change.next !== null
      if (meaningful) {
        const row: MemoryListItemRow = {
          listId: request.listId,
          id: existing?.id ?? `mem-item-${this.items.size + 1}`,
          category: change.parts.category,
          material: change.parts.material,
          spec: change.parts.spec,
          length: change.parts.length,
          price: change.next?.price ?? null,
          status: change.next?.status ?? DEFAULT_PRICE_STATUS,
          remark: change.next?.remark ?? '',
          sortOrder: existing?.sortOrder ?? this.nextSortOrder(request.listId),
        }
        this.items.set(itemKey(request.listId, change.cellKey), row)
        upserted += 1
      } else if (existing) {
        this.items.delete(itemKey(request.listId, change.cellKey))
        deleted += 1
      } else {
        // 从未落库的格子被清空：净变化为零，不留审计。
        continue
      }
      this.audits.push({
        id: `audit-${this.audits.length + 1}`,
        listId: request.listId,
        // 与 PostgresStore 的 DB 边界同口径：存转义后的形态（NUL → US），
        // 否则内存实现与真实库行为分叉，测试就锁不住「不能写 NUL」这条约束。
        cellKey: toDbCellKey(change.cellKey),
        price: change.next?.price ?? null,
        priceStatus: change.next?.status ?? null,
        remark: change.next?.remark ?? null,
        actorId: change.actorId,
        listRevision: String(list.version),
      })
      audited += 1
    }

    return {
      version: String(list.version),
      upserted,
      deleted,
      audited,
    }
  }

  /** 供测试直接断言某格的当前物化值。 */
  getItem(listId: string, cellKey: string): MemoryListItemRow | undefined {
    return this.items.get(itemKey(listId, cellKey))
  }

  async loadInitialQuoteSheet(
    room: string,
  ): Promise<InitialQuoteSheetSnapshot | null> {
    const sheetId = /^quote-sheet:(\d+)$/.exec(room.trim())?.[1]
    if (sheetId === undefined) return null
    const sheet = this.quoteSheets.get(sheetId)
    if (!sheet) return null
    const items = [...this.quoteItems.values()]
      .filter((row) => row.sheetId === sheetId)
      .sort((left, right) => left.lineNo - right.lineNo)
    return {
      sheetId: sheet.sheetId,
      version: sheet.version,
      items: items.map((row) => ({
        itemId: row.itemId,
        ton: row.ton,
        remark: row.remark,
      })),
    }
  }

  async loadQuoteMaterialized(
    _room: string,
    sheetId: string,
  ): Promise<Map<string, QuoteSheetItemRecord>> {
    const records = new Map<string, QuoteSheetItemRecord>()
    for (const row of this.quoteItems.values()) {
      if (row.sheetId !== sheetId) continue
      records.set(row.itemId, { ton: row.ton, remark: row.remark })
    }
    return records
  }

  async applyQuoteChanges(
    request: QuoteMaterializeRequest,
  ): Promise<QuoteMaterializeResult> {
    this.quoteApplyCalls += 1
    if (this.failNextApply) {
      const error = this.failNextApply
      this.failNextApply = null
      throw error
    }
    const sheet = this.quoteSheets.get(request.sheetId)
    if (!sheet) {
      throw new Error(`报价单 ${request.sheetId} 不存在（内存存储）`)
    }
    sheet.version += 1
    let updated = 0
    for (const change of request.changes) {
      const row = this.quoteItems.get(change.itemId)
      // 只更新已存在的行：行结构归 REST 管，物化不插入、不删除。
      if (!row || row.sheetId !== request.sheetId) continue
      row.ton = change.ton
      row.remark = change.remark
      updated += 1
    }
    return { version: String(sheet.version), updated }
  }

  /** 供测试直接断言某行的当前 SQL 值。 */
  getQuoteItem(itemId: string): MemoryQuoteItemRow | undefined {
    return this.quoteItems.get(itemId)
  }

  async close(): Promise<void> {
    // 内存存储无需释放资源。
  }

  private resolveListId(room: string): string | null {
    const trimmed = room.trim()
    const direct = /^(?:supplier-price-list:)?(\d+)$/.exec(trimmed)
    if (direct?.[1]) return direct[1]
    const supplierId = /^supplier-price:(\d+)$/.exec(trimmed)?.[1]
    if (supplierId === undefined) return null
    const candidates = [...this.lists.values()]
      .filter((row) => row.supplierId === supplierId)
      .sort((a, b) => b.version - a.version)
    return candidates[0]?.listId ?? null
  }

  private nextSortOrder(listId: string): number {
    let max = 0
    for (const row of this.items.values()) {
      if (row.listId === listId && row.sortOrder > max) max = row.sortOrder
    }
    return max + 1
  }
}

/** 便于测试构造「该格是否需要落库」。 */
export function isCellMeaningful(cell: CellValue | null): boolean {
  return cell !== null && isMeaningfulCell(cell)
}
