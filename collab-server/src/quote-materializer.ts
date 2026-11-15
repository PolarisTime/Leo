/**
 * 报单比价（报价单）物化器：Y.Doc → `mk_quote_item.ton/remark` + `mk_quote_sheet.version`。
 *
 * <p>与供应商价格表物化器（`materializer.ts`）**语义一致、形状不同**：</p>
 * <ul>
 *   <li>差异写：只更新「与 SQL 当前值不同」的行；没变化时不写库、不加版本号；</li>
 *   <li>幂等：基于状态差异而不是操作日志，重放同一条 update / 重启重新物化都不产生重复写入；</li>
 *   <li>失败退避：事务失败时 pending 不清空（dirty 永不清），指数退避重试，期间改动合并；</li>
 *   <li>**只更新**：`mk_quote_item` 的行结构（增删行/规格/排序）归 Java REST，CRDT 不插入、不删除。</li>
 * </ul>
 *
 * <p>删除语义（见 `quote-sheet-model.ts`）：格内删字段 = 清空该列（写 NULL）；
 * 整个 `itemId` 从文档消失 = 忽略（保留 SQL 原值），不删明细行。</p>
 *
 * <p>本期**不写审计表**：`mk_supplier_price_cell_op` 的列（list_id/cell_key）不适用；
 * 报价单审计表列为 follow-up（README §12）。</p>
 */

import type * as Y from 'yjs'
import type { Logger } from './logger.js'
import { nextRetryDelayMs, SYSTEM_ACTOR_ID } from './materializer-support.js'
import {
  type QuoteSheetItemRecord,
  type QuoteSheetItemValue,
  quoteItemRecordsEqual,
  readQuoteSheetSnapshot,
  toQuoteItemRecord,
} from './quote-sheet-model.js'
import { parseRoom } from './room.js'
import type {
  QuoteMaterializeResult,
  QuoteMaterializeStore,
} from './store/types.js'

export interface QuoteMaterializedEvent {
  room: string
  sheetId: string
  version: string
  updated: number
  /** 从第一次改动到落库的延迟（毫秒）。 */
  lagMs: number
}

export interface QuoteMaterializerOptions {
  store: QuoteMaterializeStore
  logger: Logger
  /** debounce 窗口（默认 500ms，与供应商侧一致）。 */
  debounceMs: number
  /** debounce 上限：高频编辑时至少这么久物化一次。 */
  maxDebounceMs?: number
  retryBaseMs?: number
  retryMaxMs?: number
  /** 物化成功后的钩子（预留：Redis 广播/指标）。 */
  onMaterialized?: (event: QuoteMaterializedEvent) => void | Promise<void>
  /** 注入定时器（测试用）。 */
  timers?: Pick<typeof globalThis, 'setTimeout' | 'clearTimeout'>
  now?: () => number
}

interface PendingQuoteChange {
  value: QuoteSheetItemRecord
  actorId: string
}

interface QuoteRoomState {
  room: string
  sheetId: string | null
  /** SQL 已物化的值（diff 基线）。 */
  baseline: Map<string, QuoteSheetItemRecord>
  /** 上一次观察到的文档值。 */
  lastSeen: Map<string, QuoteSheetItemRecord>
  /** 上一次观察时文档里**真实存在**的字段（区分「用户清空」与「文档从未写过」）。 */
  seenFields: Map<string, { ton: boolean; remark: boolean }>
  pending: Map<string, PendingQuoteChange>
  timer: ReturnType<typeof setTimeout> | null
  retryDelayMs: number
  flushChain: Promise<void>
  baselineLoaded: boolean
  pendingSince: number | null
  lastError: string | null
  lastVersion: string | null
}

export interface QuoteMaterializerMetrics {
  rooms: number
  pendingRooms: number
  pendingItems: number
  retryingRooms: number
  /** 文档里有、SQL 里没有（或非 PRODUCT 行）而被忽略的行数累计。 */
  ignoredItems: number
  lastError: string | null
}

/** `load` 阶段：文档缺的字段沿用 SQL 基线值（"无意见"，不是清空）。 */
function mergeWithBaseline(
  baseline: QuoteSheetItemRecord | undefined,
  value: QuoteSheetItemValue | undefined,
): QuoteSheetItemRecord {
  const document = toQuoteItemRecord(value)
  if (!baseline) return document
  return {
    ton: value?.ton === undefined ? baseline.ton : document.ton,
    remark: value?.remark === undefined ? baseline.remark : document.remark,
  }
}

export class QuoteMaterializer {
  private readonly store: QuoteMaterializeStore
  private readonly logger: Logger
  private readonly debounceMs: number
  private readonly maxDebounceMs: number
  private readonly retryBaseMs: number
  private readonly retryMaxMs: number
  private readonly onMaterialized?: (
    event: QuoteMaterializedEvent,
  ) => void | Promise<void>
  private readonly timers: Pick<
    typeof globalThis,
    'setTimeout' | 'clearTimeout'
  >
  private readonly now: () => number
  private readonly rooms = new Map<string, QuoteRoomState>()
  private lastError: string | null = null
  private ignoredItems = 0

  constructor(options: QuoteMaterializerOptions) {
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
   * 文档加载完成时调用：加载 SQL 基线，并做一次**加载期对账**。
   *
   * <p>方向是 **SQL → 文档**（补齐文档缺的 ton/remark），不是"把文档差异写回 SQL"。
   * 依据：打开单据/重连本身不该产生写库——否则每次进入协作房间都会白顶一次
   * `mk_quote_sheet.version`，而且断线期间的半截文档会被当成用户编辑写回。
   * 补齐后再把文档整体当作 `lastSeen`，之后的真实编辑才进入差异队列
   * （见测试「文档里整行消失」「净变化为零」钉住的两条语义）。</p>
   */
  async syncOnLoad(room: string, doc: Y.Doc): Promise<void> {
    const snapshot = readQuoteSheetSnapshot(doc)
    const state = this.ensureRoom(room, snapshot.meta.sheetId)
    await this.ensureBaseline(state, snapshot.meta.sheetId)
    this.applyDocumentDiff(state, snapshot, SYSTEM_ACTOR_ID, 'load')
    if (state.pending.size > 0) this.scheduleFlush(state, 0)
  }

  /** 文档每次变更时调用（`onChange`），还是按「这一行/这一格被谁改成什么」归因。 */
  async observe(room: string, doc: Y.Doc, actorId: string): Promise<void> {
    const snapshot = readQuoteSheetSnapshot(doc)
    const state = this.ensureRoom(room, snapshot.meta.sheetId)
    if (
      state.sheetId !== snapshot.meta.sheetId &&
      snapshot.meta.sheetId !== null
    ) {
      state.sheetId = snapshot.meta.sheetId
      state.baselineLoaded = false
      state.baseline = new Map()
      state.lastSeen = new Map()
    }
    await this.ensureBaseline(state, snapshot.meta.sheetId)
    this.applyDocumentDiff(state, snapshot, actorId)
    if (state.pending.size > 0) this.scheduleFlush(state, this.debounceMs)
  }

  /** 立即物化某房间（断连 flush / 关闭 flush）；无变更时返回 null。 */
  async flushRoom(room: string): Promise<QuoteMaterializeResult | null> {
    const state = this.rooms.get(room)
    if (!state || state.pending.size === 0) return null
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

  async flushAll(): Promise<void> {
    const rooms = [...this.rooms.values()]
      .filter((state) => state.pending.size > 0)
      .map((state) => state.room)
    await Promise.allSettled(rooms.map((room) => this.flushRoom(room)))
  }

  metrics(): QuoteMaterializerMetrics {
    let pendingRooms = 0
    let pendingItems = 0
    let retryingRooms = 0
    for (const state of this.rooms.values()) {
      if (state.pending.size > 0) pendingRooms += 1
      pendingItems += state.pending.size
      if (state.lastError !== null) retryingRooms += 1
    }
    return {
      rooms: this.rooms.size,
      pendingRooms,
      pendingItems,
      retryingRooms,
      ignoredItems: this.ignoredItems,
      lastError: this.lastError,
    }
  }

  sheetIdOf(room: string): string | null {
    return this.rooms.get(room)?.sheetId ?? null
  }

  dispose(): void {
    for (const state of this.rooms.values()) {
      if (state.timer) {
        this.timers.clearTimeout(state.timer)
        state.timer = null
      }
    }
  }

  private ensureRoom(
    room: string,
    sheetIdFromDoc: string | null,
  ): QuoteRoomState {
    const existing = this.rooms.get(room)
    if (existing) {
      if (existing.sheetId === null && sheetIdFromDoc !== null) {
        existing.sheetId = sheetIdFromDoc
      }
      return existing
    }
    const state: QuoteRoomState = {
      room,
      sheetId: sheetIdFromDoc ?? parseRoom(room).sheetId,
      baseline: new Map(),
      lastSeen: new Map(),
      seenFields: new Map(),
      pending: new Map(),
      timer: null,
      retryDelayMs: 0,
      flushChain: Promise.resolve(),
      baselineLoaded: false,
      pendingSince: null,
      lastError: null,
      lastVersion: null,
    }
    this.rooms.set(room, state)
    return state
  }

  private async ensureBaseline(
    state: QuoteRoomState,
    sheetIdFromDoc: string | null,
  ): Promise<void> {
    if (state.baselineLoaded) return
    const sheetId = sheetIdFromDoc ?? state.sheetId
    if (sheetId === null) {
      this.logger.warn('无法确定报价单 id，暂不物化（等待文档 meta.sheetId）', {
        room: state.room,
      })
      return
    }
    state.sheetId = sheetId
    state.baseline = await this.store.loadQuoteMaterialized(state.room, sheetId)
    state.lastSeen = new Map(state.baseline)
    state.baselineLoaded = true
  }

  /** 对比 lastSeen 与新快照，把「与 SQL 不同的行」记进 pending。 */
  private applyDocumentDiff(
    state: QuoteRoomState,
    snapshot: ReturnType<typeof readQuoteSheetSnapshot>,
    actorId: string,
    mode: 'load' | 'change' = 'change',
  ): void {
    const nextSeen = new Map<string, QuoteSheetItemRecord>()
    const nextFields = new Map<string, { ton: boolean; remark: boolean }>()
    for (const [itemId, value] of snapshot.items) {
      const baseline = state.baseline.get(itemId)
      const fields = {
        ton: value?.ton !== undefined,
        remark: value?.remark !== undefined,
      }
      nextFields.set(itemId, fields)
      /*
       * 缺字段的语义按阶段/历史区分：
       * - `load`（打开单据/重连）：缺字段只表示"这条文档没写过该字段"（旧版本、断线半截），
       *   取 SQL 基线值，否则每次进房间都会把备注/吨位写成 NULL；
       * - `change`（运行期）：**上一次真实存在过、这次没了** = 用户清空该字段 → NULL；
       *   从未出现过的字段仍沿用基线（否则"只改吨位"会顺手把备注清空）。
       */
      const previousFields = state.seenFields.get(itemId)
      const record =
        mode === 'load'
          ? mergeWithBaseline(baseline, value)
          : {
              ton: fields.ton
                ? toQuoteItemRecord(value).ton
                : previousFields?.ton
                  ? null
                  : (baseline?.ton ?? null),
              remark: fields.remark
                ? toQuoteItemRecord(value).remark
                : previousFields?.remark
                  ? null
                  : (baseline?.remark ?? null),
            }
      nextSeen.set(itemId, record)
      const previous = state.lastSeen.get(itemId)
      if (previous && quoteItemRecordsEqual(previous, record)) continue
      this.recordPending(state, itemId, record, actorId)
    }
    /*
     * 文档里整行消失：**不删明细行**（行结构归 REST，CRDT 没有删行操作），
     * 保留 SQL 原值、静默忽略。从 lastSeen 移除即可——该行以后若再出现会重新参与 diff。
     */
    for (const itemId of state.lastSeen.keys()) {
      if (nextSeen.has(itemId)) continue
      this.logger.debug('文档中的明细行被移除：忽略（不删 SQL 行）', {
        room: state.room,
        itemId,
      })
    }
    state.lastSeen = nextSeen
    state.seenFields = nextFields
  }

  private recordPending(
    state: QuoteRoomState,
    itemId: string,
    record: QuoteSheetItemRecord,
    actorId: string,
  ): void {
    const baseline = state.baseline.get(itemId)
    if (!baseline) {
      /*
       * 只更新语义：SQL 里没有这行（结构未落库、隔断行、或已被 REST 删除）。
       * 直接丢弃，不插行——否则协作端可以凭空"造"出没有规格的明细行。
       * 也绝不能把它当变更排进队列，否则会白顶一次 mk_quote_sheet.version。
       */
      this.ignoredItems += 1
      state.pending.delete(itemId)
      return
    }
    if (quoteItemRecordsEqual(baseline, record)) {
      // 净变化为零（改回原值/清空一个本来就空的字段）：不写库、不加版本。
      state.pending.delete(itemId)
      return
    }
    state.pending.set(itemId, { value: record, actorId })
    if (state.pendingSince === null) state.pendingSince = this.now()
  }

  private scheduleFlush(state: QuoteRoomState, delayMs: number): void {
    if (state.pending.size === 0) return
    if (state.timer) {
      this.timers.clearTimeout(state.timer)
      state.timer = null
    }
    const elapsed =
      state.pendingSince === null ? 0 : this.now() - state.pendingSince
    const remaining = Math.max(0, this.maxDebounceMs - elapsed)
    const effectiveDelay = Math.min(delayMs, remaining)
    const timer = this.timers.setTimeout(() => {
      state.timer = null
      void this.flushRoom(state.room).catch((error: unknown) => {
        this.logger.error('报价单物化失败，进入退避重试', {
          room: state.room,
          error: error instanceof Error ? error.message : String(error),
        })
      })
    }, effectiveDelay)
    timer.unref?.()
    state.timer = timer
  }

  private async runFlush(
    state: QuoteRoomState,
  ): Promise<QuoteMaterializeResult | null> {
    const sheetId = state.sheetId
    if (sheetId === null) {
      this.logger.warn('缺少 sheetId，报价单物化暂缓', { room: state.room })
      this.scheduleRetry(state)
      return null
    }
    if (state.pending.size === 0) return null

    const batch = new Map(state.pending)
    const lagMs =
      state.pendingSince === null
        ? 0
        : Math.max(0, this.now() - state.pendingSince)
    const changes = [...batch.entries()].map(([itemId, change]) => ({
      itemId,
      ton: change.value.ton,
      remark: change.value.remark,
      actorId: change.actorId,
    }))
    const dominantActor =
      changes.length > 0 ? changes[changes.length - 1].actorId : SYSTEM_ACTOR_ID

    try {
      const result = await this.store.applyQuoteChanges({
        room: state.room,
        sheetId,
        changes,
        actorId: dominantActor,
      })
      for (const [itemId, change] of batch) {
        state.baseline.set(itemId, change.value)
        // await 期间同一行又被改了：保留新的 pending，下一轮再物化。
        if (state.pending.get(itemId) === change) state.pending.delete(itemId)
      }
      state.retryDelayMs = 0
      state.pendingSince = null
      state.lastError = null
      state.lastVersion = result.version
      this.logger.info('报价单物化完成', {
        room: state.room,
        sheetId,
        version: result.version,
        updated: result.updated,
      })
      if (this.onMaterialized) {
        try {
          await this.onMaterialized({
            room: state.room,
            sheetId,
            version: result.version,
            updated: result.updated,
            lagMs,
          })
        } catch (error) {
          this.logger.warn('报价单物化后回调失败（不影响落库）', {
            room: state.room,
            error: error instanceof Error ? error.message : String(error),
          })
        }
      }
      if (state.pending.size > 0) {
        this.scheduleFlush(state, this.debounceMs)
      }
      return result
    } catch (error) {
      state.lastError = error instanceof Error ? error.message : String(error)
      this.lastError = state.lastError
      this.logger.error('报价单物化失败，保留 dirty 并重试', {
        room: state.room,
        sheetId,
        pending: state.pending.size,
        error: state.lastError,
      })
      this.scheduleRetry(state)
      throw error
    }
  }

  private scheduleRetry(state: QuoteRoomState): void {
    if (state.timer) {
      this.timers.clearTimeout(state.timer)
      state.timer = null
    }
    state.retryDelayMs = nextRetryDelayMs(
      state.retryDelayMs,
      this.retryBaseMs,
      this.retryMaxMs,
    )
    const timer = this.timers.setTimeout(() => {
      state.timer = null
      void this.flushRoom(state.room).catch(() => {
        // 下一次失败会继续 scheduleRetry；这里避免未处理的 rejection。
      })
    }, state.retryDelayMs)
    timer.unref?.()
    state.timer = timer
  }
}
