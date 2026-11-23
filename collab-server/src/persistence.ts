/**
 * 文档持久化：快照 + 增量。
 *
 * <p>写路径分两条（设计文档 §5）：</p>
 * <ol>
 *   <li>**增量**：每次 Yjs update 追加一行到 `mk_supplier_price_collab_update`（append-only，
 *       崩溃后可按「快照 + id &gt; last_update_id 的增量」重建）；</li>
 *   <li>**快照**：hocuspocus 在 debounce（默认 500ms）与断连/关闭时调用 `onStoreDocument`，
 *       把整份文档 upsert 到 `mk_supplier_price_collab_doc`。</li>
 * </ol>
 *
 * <p>两条写都是**幂等**的：快照按 room upsert（永远一行），增量按内容哈希 + 逐条去重
 * （Yjs apply 本身幂等，去重只是别让表无谓增长）。写入失败时增量留在内存队列里等下次 flush，
 * 不清空 —— 与物化器「永不清 dirty」同一条原则。</p>
 */

import * as Y from 'yjs'
import { type CellValue, makeCellKey, writeDocSnapshot } from './doc-model.js'
import type { Logger } from './logger.js'
import {
  type QuoteSheetSnapshot,
  writeQuoteSheetSnapshot,
} from './quote-sheet-model.js'
import { parseRoom } from './room.js'
import type {
  CollabDocStore,
  InitialListSnapshot,
  InitialQuoteSheetSnapshot,
} from './store/types.js'

export interface CollabPersistenceOptions {
  store: CollabDocStore
  logger: Logger
  /** 无协作文档时是否从 SQL 价格表生成初始文档。 */
  bootstrapFromSql: boolean
  /** 是否追加增量表（关闭只影响持久化，不影响实时同步）。 */
  persistUpdates: boolean
  /** 增量批量写窗口（毫秒）：把一次连打里的多条 update 合并成一条 INSERT。 */
  updateFlushDelayMs?: number
  /** 单房间累计追加多少条增量后自动触发快照压缩（默认 500）。 */
  compactUpdateThreshold?: number
}

export interface PersistenceMetrics {
  pendingUpdateRooms: number
  pendingUpdates: number
  storedDocuments: number
  appendedUpdates: number
  bootstrapCount: number
  compactedRooms: number
  compactedUpdates: number
}

/** 从 SQL 快照构造 Y.Doc 的初始状态（§11 阶段 0：从 SQL 生成 Y.Doc）。 */
export function buildInitialDocState(initial: InitialListSnapshot): Uint8Array {
  const doc = new Y.Doc()
  const cells = new Map<string, CellValue>()
  for (const item of initial.items) {
    cells.set(
      makeCellKey({
        category: item.category,
        material: item.material,
        spec: item.spec,
        length: item.length,
      }),
      {
        price: item.price,
        status: item.status,
        remark: item.remark,
      },
    )
  }
  writeDocSnapshot(doc, {
    meta: {
      listId: initial.listId,
      supplierId: initial.supplierId,
      brandName: initial.brandName,
      quotedOn: initial.quotedOn,
      warehouse: initial.warehouse,
      version: initial.version,
    },
    cells,
  })
  const state = Y.encodeStateAsUpdate(doc)
  doc.destroy()
  return state
}

/**
 * 从 SQL 报价单快照构造 Y.Doc 的初始状态（报单比价房间）。
 *
 * <p>只写 `meta.sheetId/version` 与每行的 `ton/remark`：规格/价格/行结构全部归 REST 与
 * 实时派生，不进 CRDT（见 `quote-sheet-model.ts` 的说明）。</p>
 */
export function buildInitialQuoteDocState(
  initial: InitialQuoteSheetSnapshot,
): Uint8Array {
  const doc = new Y.Doc()
  const snapshot: QuoteSheetSnapshot = {
    meta: { sheetId: initial.sheetId, version: initial.version },
    items: new Map(
      initial.items.map((item) => [
        item.itemId,
        {
          ...(item.ton === null ? {} : { ton: item.ton }),
          ...(item.remark === null || item.remark === ''
            ? {}
            : { remark: item.remark }),
        },
      ]),
    ),
  }
  writeQuoteSheetSnapshot(doc, snapshot)
  const state = Y.encodeStateAsUpdate(doc)
  doc.destroy()
  return state
}

export class CollabPersistence {
  private readonly store: CollabDocStore
  private readonly logger: Logger
  private readonly bootstrapFromSql: boolean
  private readonly persistUpdates: boolean
  private readonly updateFlushDelayMs: number
  private readonly compactUpdateThreshold: number

  private readonly pendingUpdates = new Map<string, Uint8Array[]>()
  private readonly flushTimers = new Map<string, NodeJS.Timeout>()
  private readonly flushChains = new Map<string, Promise<void>>()
  private readonly updatesSinceCompact = new Map<string, number>()
  private readonly counters = {
    storedDocuments: 0,
    appendedUpdates: 0,
    bootstrapCount: 0,
    compactedRooms: 0,
    compactedUpdates: 0,
  }

  constructor(options: CollabPersistenceOptions) {
    this.store = options.store
    this.logger = options.logger
    this.bootstrapFromSql = options.bootstrapFromSql
    this.persistUpdates = options.persistUpdates
    this.updateFlushDelayMs = options.updateFlushDelayMs ?? 100
    this.compactUpdateThreshold = options.compactUpdateThreshold ?? 500
  }

  /**
   * 读取文档状态（`Database.fetch` 的入口）。
   *
   * <p>没有协作文档时按 §11 阶段 0 的口径**从 SQL 生成**：首次连接的客户端立刻看到整张价格表，
   * 而不是空表；生成后立即落库，后续连接直接读快照。</p>
   */
  async fetchState(room: string): Promise<Uint8Array | null> {
    const stored = await this.store.loadDocState(room)
    if (stored !== null) return stored
    if (!this.bootstrapFromSql) return null

    const kind = parseRoom(room).kind
    if (kind === 'quote-sheet') {
      const initial = await this.store.loadInitialQuoteSheet(room)
      if (initial === null) {
        this.logger.warn('报价单房间无协作文档且无对应报价单，按空文档处理', {
          room,
        })
        return null
      }
      const state = buildInitialQuoteDocState(initial)
      await this.store.saveDocState(room, state)
      this.counters.bootstrapCount += 1
      this.logger.info('已从 SQL 生成初始报单比价文档', {
        room,
        sheetId: initial.sheetId,
        items: initial.items.length,
        bytes: state.byteLength,
      })
      return state
    }

    const initial = await this.store.loadInitialList(room)
    if (initial === null) {
      this.logger.warn('房间无协作文档且无对应价格表，按空文档处理', { room })
      return null
    }
    const state = buildInitialDocState(initial)
    await this.store.saveDocState(room, state)
    this.counters.bootstrapCount += 1
    this.logger.info('已从 SQL 生成初始协作文档', {
      room,
      listId: initial.listId,
      items: initial.items.length,
      bytes: state.byteLength,
    })
    return state
  }

  /** 持久化整份快照（`Database.store` 的入口，由 hocuspocus debounce 调用）。 */
  async storeState(room: string, state: Uint8Array): Promise<void> {
    await this.store.saveDocState(room, state)
    this.counters.storedDocuments += 1
    // 快照落库后顺手把挂起的增量刷掉：顺序无关（Yjs 幂等），但别让增量表落后太久。
    await this.flushRoom(room)
  }

  /** 记录一条增量（`onChange` 入口）；不阻塞广播，按窗口批量落库。 */
  recordUpdate(room: string, update: Uint8Array): void {
    if (!this.persistUpdates || update.byteLength === 0) return
    const queue = this.pendingUpdates.get(room)
    if (queue) {
      queue.push(update)
    } else {
      this.pendingUpdates.set(room, [update])
    }
    if (this.flushTimers.has(room)) return
    const timer = setTimeout(() => {
      this.flushTimers.delete(room)
      void this.flushRoom(room).catch((error: unknown) => {
        this.logger.error('增量落库失败，等待下一次 flush 重试', {
          room,
          error: error instanceof Error ? error.message : String(error),
        })
      })
    }, this.updateFlushDelayMs)
    // 不让待写增量拖住进程退出：优雅退出会显式 flushAll。
    timer.unref?.()
    this.flushTimers.set(room, timer)
  }

  /** 把某房间挂起的增量落库（按房间串行化，避免并发写乱序）。 */
  async flushRoom(room: string): Promise<void> {
    const timer = this.flushTimers.get(room)
    if (timer) {
      clearTimeout(timer)
      this.flushTimers.delete(room)
    }
    const previous = this.flushChains.get(room) ?? Promise.resolve()
    const next = previous.then(async () => {
      const queue = this.pendingUpdates.get(room)
      if (!queue || queue.length === 0) return
      // 先摘出批次：成功后不再放回；失败时放回队列头等下次重试。
      this.pendingUpdates.delete(room)
      try {
        const appended = await this.store.appendUpdates(room, queue)
        this.counters.appendedUpdates += appended
        if (appended > 0 && this.store.compactRoom) {
          const accumulated =
            (this.updatesSinceCompact.get(room) ?? 0) + appended
          if (accumulated >= this.compactUpdateThreshold) {
            this.updatesSinceCompact.set(room, 0)
            await this.runCompactRoom(room)
          } else {
            this.updatesSinceCompact.set(room, accumulated)
          }
        }
      } catch (error) {
        const pending = this.pendingUpdates.get(room) ?? []
        this.pendingUpdates.set(room, [...queue, ...pending])
        throw error
      }
    })
    // 链上失败不能变成未处理的 rejection：调用方各自 catch 自己那次。
    const settled = next.catch(() => undefined)
    this.flushChains.set(room, settled)
    await next
  }

  /** 对单个房间执行快照压缩（串行排在同房间 flush 链上，避免与追加竞态）。 */
  async compactRoom(room: string): Promise<number> {
    if (!this.store.compactRoom) return 0
    await this.flushRoom(room)
    const previous = this.flushChains.get(room) ?? Promise.resolve()
    let removed = 0
    const next = previous.then(async () => {
      removed = await this.runCompactRoom(room)
    })
    this.flushChains.set(
      room,
      next.catch(() => undefined),
    )
    await next
    return removed
  }

  /** 扫描并压缩所有存在足够增量的房间（24h 周期压缩任务入口）。 */
  async compactAll(minUpdates = 1): Promise<number> {
    if (!this.store.compactRoom || !this.store.listRoomsWithUpdates) return 0
    const rooms = await this.store.listRoomsWithUpdates(minUpdates)
    let totalRemoved = 0
    for (const room of rooms) {
      try {
        totalRemoved += await this.compactRoom(room)
      } catch (error) {
        this.logger.warn('房间快照压缩失败，等待下次周期重试', {
          room,
          error: error instanceof Error ? error.message : String(error),
        })
      }
    }
    return totalRemoved
  }

  /** 资源被 REST 删除后清理持久化文档与增量队列。 */
  async deleteRoom(room: string): Promise<void> {
    const timer = this.flushTimers.get(room)
    if (timer) {
      clearTimeout(timer)
      this.flushTimers.delete(room)
    }
    this.pendingUpdates.delete(room)
    this.updatesSinceCompact.delete(room)
    if (this.store.deleteDocState) {
      await this.store.deleteDocState(room)
    }
  }

  private async runCompactRoom(room: string): Promise<number> {
    if (!this.store.compactRoom) return 0
    const removed = await this.store.compactRoom(room)
    if (removed > 0) {
      this.updatesSinceCompact.set(room, 0)
      this.counters.compactedRooms += 1
      this.counters.compactedUpdates += removed
      this.logger.info('房间协作增量已压缩为新快照', { room, removed })
    }
    return removed
  }

  /** 优雅退出：刷掉所有房间的增量，并等待进行中的写完成。 */
  async flushAll(): Promise<void> {
    const rooms = new Set<string>([
      ...this.pendingUpdates.keys(),
      ...this.flushTimers.keys(),
      ...this.flushChains.keys(),
    ])
    await Promise.allSettled([...rooms].map((room) => this.flushRoom(room)))
  }

  metrics(): PersistenceMetrics {
    let pendingUpdates = 0
    for (const queue of this.pendingUpdates.values()) {
      pendingUpdates += queue.length
    }
    return {
      pendingUpdateRooms: this.pendingUpdates.size,
      pendingUpdates,
      storedDocuments: this.counters.storedDocuments,
      appendedUpdates: this.counters.appendedUpdates,
      bootstrapCount: this.counters.bootstrapCount,
      compactedRooms: this.counters.compactedRooms,
      compactedUpdates: this.counters.compactedUpdates,
    }
  }
}
