/**
 * 物化器：Y.Doc → `mk_supplier_price_item` / `mk_supplier_price_list` / `mk_supplier_price_cell_op`。
 *
 * <p>三条硬性语义（设计文档 §6.2）：</p>
 * <ol>
 *   <li><b>差异写</b>：只写「与 SQL 当前状态不同」的格子，没变化时**不写库、不加版本号**；</li>
 *   <li><b>幂等</b>：基于状态差异而不是操作日志；重放同一条 update、或重启后重新物化，
 *       都不会产生重复审计或错误值；</li>
 *   <li><b>失败重试</b>：事务失败时保留 dirty（pending 不清空），指数退避重试（上限 60s），
 *       期间的后续改动合并进下一次。</li>
 * </ol>
 *
 * <p>按格归因：每次 `onChange` 立刻与「上一次观察到的文档」对比（100-300 格，成本可忽略），
 * 把变化的格记到**这次改动的用户**头上；debounce 窗口内同一格多次改动以最后一次为准。
 * 这样审计表里的 actor 是真正改这格的人，而不是「本次 flush 的最后一个人」。</p>
 */

import type * as Y from 'yjs'
import {
  type CellParts,
  type CellValue,
  cellValuesEqual,
  isMeaningfulCell,
  parseCellKey,
  readDocSnapshot,
} from './doc-model.js'
import type { Logger } from './logger.js'
import { parseRoom } from './room.js'
import type {
  CellChange,
  MaterializeResult,
  MaterializeStore,
} from './store/types.js'

/** 系统操作人（拿不到 JWT 用户时，例如服务端重建/测试）。 */
export const SYSTEM_ACTOR_ID = '0'

export interface MaterializedEvent {
  room: string
  listId: string
  version: string
  upserted: number
  deleted: number
  audited: number
  /** 从第一次改动到落库的延迟（毫秒），用于健康检查/指标。 */
  lagMs: number
}

export interface CollabMaterializerOptions {
  store: MaterializeStore
  logger: Logger
  /** debounce 窗口（默认 500ms，与设计文档一致）。 */
  debounceMs: number
  /** debounce 上限：高频编辑时至少这么久物化一次。 */
  maxDebounceMs?: number
  /** 失败重试基数 / 上限。 */
  retryBaseMs?: number
  retryMaxMs?: number
  /** 物化成功后的钩子（Redis 广播 `collab:list-changed`）。 */
  onMaterialized?: (event: MaterializedEvent) => void | Promise<void>
  /** 注入定时器（测试用）。 */
  timers?: Pick<typeof globalThis, 'setTimeout' | 'clearTimeout'>
  now?: () => number
}

interface PendingChange {
  parts: CellParts
  /** null = 该格被清空。 */
  next: CellValue | null
  actorId: string
  existedBefore: boolean
}

interface RoomState {
  room: string
  listId: string | null
  /** SQL 已物化的状态（diff 基线）。 */
  baseline: Map<string, CellValue>
  /** 上一次观察到的文档状态。 */
  lastSeen: Map<string, CellValue>
  lastQuotedOn: string | null
  pending: Map<string, PendingChange>
  pendingQuotedOn: string | null
  timer: ReturnType<typeof setTimeout> | null
  retryDelayMs: number
  flushChain: Promise<void>
  baselineLoaded: boolean
  pendingSince: number | null
  lastError: string | null
  lastVersion: string | null
  lastFlushAt: number | null
}

export interface MaterializerMetrics {
  rooms: number
  pendingRooms: number
  pendingCells: number
  retryingRooms: number
  lastError: string | null
}

export class CollabMaterializer {
  private readonly store: MaterializeStore
  private readonly logger: Logger
  private readonly debounceMs: number
  private readonly maxDebounceMs: number
  private readonly retryBaseMs: number
  private readonly retryMaxMs: number
  private readonly onMaterialized?: (
    event: MaterializedEvent,
  ) => void | Promise<void>
  private readonly timers: Pick<
    typeof globalThis,
    'setTimeout' | 'clearTimeout'
  >
  private readonly now: () => number
  private readonly rooms = new Map<string, RoomState>()
  private lastError: string | null = null

  constructor(options: CollabMaterializerOptions) {
    this.store = options.store
    this.logger = options.logger
    this.debounceMs = options.debounceMs
    this.maxDebounceMs = options.maxDebounceMs ?? options.debounceMs * 4
    this.retryBaseMs = options.retryBaseMs ?? 500
    this.retryMaxMs = options.retryMaxMs ?? 60_000
    this.onMaterialized = options.onMaterialized
    this.timers = options.timers ?? globalThis
    this.now = options.now ?? (() => Date.now())
  }

  /**
   * 文档加载完成时调用：加载 SQL 基线，并把「SQL 与文档的差异」立即物化。
   *
   * <p>这是崩溃恢复点：边车可能在「文档已落库、物化未提交」之间挂掉，
   * 重启后第一次加载该房间必须补齐差异，否则 SQL 永远缺那几格。</p>
   */
  async syncOnLoad(room: string, doc: Y.Doc): Promise<void> {
    const snapshot = readDocSnapshot(doc)
    const state = this.ensureRoom(room, snapshot.meta.listId)
    // 基线 = SQL 当前状态；lastSeen 也停在基线，随后的 diff 得到「SQL 与文档的差异」。
    // 这正是崩溃恢复点：边车可能在「快照已写、物化未提交」之间挂掉。
    await this.ensureBaseline(state, snapshot.meta.listId)
    // 报价日期不做加载期对账：SQL 表头没有可靠的回读值（quoted_on 为可选列），
    // 每次加载都写一次会让版本号无谓自增；等下一次真实编辑再落库。
    state.lastQuotedOn = snapshot.meta.quotedOn
    this.applyDocumentDiff(state, snapshot, SYSTEM_ACTOR_ID)
    if (state.pending.size > 0 || state.pendingQuotedOn !== null) {
      this.scheduleFlush(state, 0)
    }
  }

  /**
   * 文档每次变更时调用（`onChange`）。
   *
   * @param actorId 触发本次变更的用户（雪花字符串）；服务端操作传 {@link SYSTEM_ACTOR_ID}。
   */
  async observe(room: string, doc: Y.Doc, actorId: string): Promise<void> {
    const snapshot = readDocSnapshot(doc)
    const state = this.ensureRoom(room, snapshot.meta.listId)
    // listId 变化（换成另一张价格表）时基线必须重载，否则会把新表整表当成变更。
    if (
      state.listId !== snapshot.meta.listId &&
      snapshot.meta.listId !== null
    ) {
      state.listId = snapshot.meta.listId
      state.baselineLoaded = false
      state.baseline = new Map()
    }
    await this.ensureBaseline(state, snapshot.meta.listId)
    this.applyDocumentDiff(state, snapshot, actorId)
    if (state.pending.size > 0 || state.pendingQuotedOn !== null) {
      this.scheduleFlush(state, this.debounceMs)
    }
  }

  /** 立即物化某房间（断连 flush / 关闭 flush）；无变更时返回 null。 */
  async flushRoom(room: string): Promise<MaterializeResult | null> {
    const state = this.rooms.get(room)
    if (!state) return null
    if (state.pending.size === 0 && state.pendingQuotedOn === null) return null
    if (state.timer) {
      this.timers.clearTimeout(state.timer)
      state.timer = null
    }
    const previous = state.flushChain
    const run = previous.then(() => this.runFlush(state))
    state.flushChain = run.then(
      () => undefined,
      () => undefined,
    )
    return run
  }

  /** 优雅退出：把所有待物化房间刷干净。 */
  async flushAll(): Promise<void> {
    const rooms = [...this.rooms.values()]
      .filter(
        (state) => state.pending.size > 0 || state.pendingQuotedOn !== null,
      )
      .map((state) => state.room)
    await Promise.allSettled(rooms.map((room) => this.flushRoom(room)))
  }

  metrics(): MaterializerMetrics {
    let pendingRooms = 0
    let pendingCells = 0
    let retryingRooms = 0
    for (const state of this.rooms.values()) {
      if (state.pending.size > 0 || state.pendingQuotedOn !== null)
        pendingRooms += 1
      pendingCells += state.pending.size
      if (state.lastError !== null) retryingRooms += 1
    }
    return {
      rooms: this.rooms.size,
      pendingRooms,
      pendingCells,
      retryingRooms,
      lastError: this.lastError,
    }
  }

  /** 房间当前价格表 id（健康检查/测试用）。 */
  listIdOf(room: string): string | null {
    return this.rooms.get(room)?.listId ?? null
  }

  dispose(): void {
    for (const state of this.rooms.values()) {
      if (state.timer) {
        this.timers.clearTimeout(state.timer)
        state.timer = null
      }
    }
  }

  private ensureRoom(room: string, listIdFromDoc: string | null): RoomState {
    const existing = this.rooms.get(room)
    if (existing) {
      if (existing.listId === null && listIdFromDoc !== null) {
        existing.listId = listIdFromDoc
      }
      return existing
    }
    const state: RoomState = {
      room,
      // 文档 meta 优先；没有时退回房间名解析（supplier-price-list:<id> / 纯数字）。
      listId: listIdFromDoc ?? parseRoom(room).listId,
      baseline: new Map(),
      lastSeen: new Map(),
      lastQuotedOn: null,
      pending: new Map(),
      pendingQuotedOn: null,
      timer: null,
      retryDelayMs: 0,
      flushChain: Promise.resolve(),
      baselineLoaded: false,
      pendingSince: null,
      lastError: null,
      lastVersion: null,
      lastFlushAt: null,
    }
    this.rooms.set(room, state)
    return state
  }

  private async ensureBaseline(
    state: RoomState,
    listIdFromDoc: string | null,
  ): Promise<void> {
    if (state.baselineLoaded) return
    const listId = listIdFromDoc ?? state.listId
    if (listId === null) {
      this.logger.warn('无法确定价格表 id，暂不物化（等待文档 meta.listId）', {
        room: state.room,
      })
      return
    }
    state.listId = listId
    state.baseline = await this.store.loadMaterialized(state.room, listId)
    state.lastSeen = new Map(state.baseline)
    state.baselineLoaded = true
  }

  /** 对比 lastSeen 与新快照，把变化记进 pending（同名格以最后一次改动为准）。 */
  private applyDocumentDiff(
    state: RoomState,
    snapshot: ReturnType<typeof readDocSnapshot>,
    actorId: string,
  ): void {
    for (const [cellKey, nextValue] of snapshot.cells) {
      const previous = state.lastSeen.get(cellKey)
      if (previous && cellValuesEqual(previous, nextValue)) continue
      const parts = parseCellKey(cellKey)
      if (parts === null) {
        this.logger.warn('忽略非法 cellKey（无法解析列键）', {
          room: state.room,
        })
        continue
      }
      this.recordPending(state, cellKey, parts, nextValue, actorId)
    }
    for (const cellKey of state.lastSeen.keys()) {
      if (snapshot.cells.has(cellKey)) continue
      const parts = parseCellKey(cellKey)
      if (parts === null) continue
      this.recordPending(state, cellKey, parts, null, actorId)
    }
    state.lastSeen = new Map(snapshot.cells)

    if (snapshot.meta.quotedOn !== state.lastQuotedOn) {
      state.pendingQuotedOn = snapshot.meta.quotedOn
      state.lastQuotedOn = snapshot.meta.quotedOn
    }
  }

  private recordPending(
    state: RoomState,
    cellKey: string,
    parts: CellParts,
    value: CellValue | null,
    actorId: string,
  ): void {
    // 「清空」= 价格为空、状态 NORMAL、无备注：SQL 里不该有这一行。
    const next = value !== null && isMeaningfulCell(value) ? value : null
    state.pending.set(cellKey, {
      parts,
      next,
      actorId,
      existedBefore: state.baseline.has(cellKey),
    })
    if (state.pendingSince === null) state.pendingSince = this.now()
  }

  private scheduleFlush(state: RoomState, delayMs: number): void {
    const pending = state.pending.size > 0 || state.pendingQuotedOn !== null
    if (!pending) return
    if (state.timer) {
      this.timers.clearTimeout(state.timer)
      state.timer = null
    }
    // 高频编辑时不能用纯 debounce 无限推迟：距第一次改动超过上限就立刻物化。
    const elapsed =
      state.pendingSince === null ? 0 : this.now() - state.pendingSince
    const remaining = Math.max(0, this.maxDebounceMs - elapsed)
    const effectiveDelay = Math.min(delayMs, remaining)
    const timer = this.timers.setTimeout(() => {
      state.timer = null
      void this.flushRoom(state.room).catch((error: unknown) => {
        this.logger.error('物化失败，进入退避重试', {
          room: state.room,
          error: error instanceof Error ? error.message : String(error),
        })
      })
    }, effectiveDelay)
    timer.unref?.()
    state.timer = timer
  }

  private async runFlush(state: RoomState): Promise<MaterializeResult | null> {
    const listId = state.listId
    if (listId === null) {
      // 没确定价格表时保持 pending，等 meta 到位（不丢改动）。
      this.logger.warn('缺少 listId，物化暂缓', { room: state.room })
      this.scheduleRetry(state)
      return null
    }
    if (state.pending.size === 0 && state.pendingQuotedOn === null) return null

    const batch = new Map(state.pending)
    const quotedOn = state.pendingQuotedOn
    const lagMs =
      state.pendingSince === null
        ? 0
        : Math.max(0, this.now() - state.pendingSince)
    const changes: CellChange[] = [...batch.entries()].map(
      ([cellKey, change]) => ({
        cellKey,
        parts: change.parts,
        next: change.next,
        actorId: change.actorId,
        existedBefore: change.existedBefore,
      }),
    )
    const dominantActor =
      changes.length > 0 ? changes[changes.length - 1].actorId : SYSTEM_ACTOR_ID

    try {
      const result = await this.store.applyChanges({
        room: state.room,
        listId,
        changes,
        quotedOn,
        actorId: dominantActor,
      })
      // 提交成功后才推进基线：事务失败时基线必须停留在旧值。
      for (const [cellKey, change] of batch) {
        if (change.next === null) {
          state.baseline.delete(cellKey)
        } else {
          state.baseline.set(cellKey, change.next)
        }
        // await 期间同一格又变了：保留新的 pending，下一轮再物化。
        if (state.pending.get(cellKey) === change) {
          state.pending.delete(cellKey)
        }
      }
      if (state.pendingQuotedOn === quotedOn) state.pendingQuotedOn = null
      state.retryDelayMs = 0
      state.pendingSince = null
      state.lastError = null
      state.lastVersion = result.version
      state.lastFlushAt = this.now()

      const event: MaterializedEvent = {
        room: state.room,
        listId,
        version: result.version,
        upserted: result.upserted,
        deleted: result.deleted,
        audited: result.audited,
        lagMs,
      }
      if (this.onMaterialized) {
        try {
          await this.onMaterialized(event)
        } catch (error) {
          // 广播失败不影响物化结果（SQL 已经是权威值）。
          this.logger.warn('物化后广播失败（不影响落库）', {
            room: state.room,
            error: error instanceof Error ? error.message : String(error),
          })
        }
      }
      this.logger.info('物化完成', {
        room: state.room,
        listId,
        version: result.version,
        upserted: result.upserted,
        deleted: result.deleted,
        audited: result.audited,
      })
      if (state.pending.size > 0 || state.pendingQuotedOn !== null) {
        this.scheduleFlush(state, this.debounceMs)
      }
      return result
    } catch (error) {
      state.lastError = error instanceof Error ? error.message : String(error)
      this.lastError = state.lastError
      // pending 保持不动（dirty 永不清空），指数退避重试。
      this.logger.error('物化失败，保留 dirty 并重试', {
        room: state.room,
        listId,
        pending: state.pending.size,
        error: state.lastError,
      })
      this.scheduleRetry(state)
      throw error
    }
  }

  private scheduleRetry(state: RoomState): void {
    if (state.timer) {
      this.timers.clearTimeout(state.timer)
      state.timer = null
    }
    state.retryDelayMs =
      state.retryDelayMs === 0
        ? this.retryBaseMs
        : Math.min(state.retryDelayMs * 2, this.retryMaxMs)
    const timer = this.timers.setTimeout(() => {
      state.timer = null
      void this.flushRoom(state.room).catch(() => {
        // 下一次失败会继续 scheduleRetry；这里只是避免未处理的 rejection。
      })
    }, state.retryDelayMs)
    timer.unref?.()
    state.timer = timer
  }
}
