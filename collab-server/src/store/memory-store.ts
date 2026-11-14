/**
 * 内存存储：单测与本地无库调试用（`COLLAB_STORE=memory`）。
 *
 * <p>语义必须与 Postgres 实现一致，否则测试通过、上线翻车：版本号 +1、条目 upsert、
 * 清空格删除、每格一条审计、**空变更不产生任何写入**（「重复 store 不重复插入」的保证）。</p>
 */

import {
  type CellValue,
  DEFAULT_PRICE_STATUS,
  isMeaningfulCell,
  makeCellKey,
  normalizeCellValue,
  type PriceStatus,
} from '../doc-model.js'
import type {
  CollabDocStore,
  InitialListSnapshot,
  MaterializeRequest,
  MaterializeResult,
  MaterializeStore,
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

export interface MemoryStoreSeed {
  lists?: MemoryListRow[]
  items?: MemoryListItemRow[]
}

function itemKey(listId: string, cellKey: string): string {
  return `${listId}\u0001${cellKey}`
}

export class MemoryStore implements CollabDocStore, MaterializeStore {
  readonly docs = new Map<string, Uint8Array>()
  readonly updates = new Map<string, Uint8Array[]>()
  readonly lists = new Map<string, MemoryListRow>()
  readonly items = new Map<string, MemoryListItemRow>()
  readonly audits: MemoryAuditRow[] = []
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
        cellKey: change.cellKey,
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
