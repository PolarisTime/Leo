/**
 * 报单比价（报价单）的协作文档模型。
 *
 * <p>权威 schema 是前端 `aries/src/views/price-compare/collab/quote-sheet-doc.ts`，本文件
 * 是它在 Node 侧的镜像；两侧字段名/结构必须逐字一致，否则物化会把「改吨位」当成无变更：</p>
 *
 * ```text
 * doc.getMap('meta')              // sheetId / version
 * doc.getMap<Y.Map>('items')      // key = itemId（服务端 mk_quote_item.id，字符串）
 *   └ Y.Map { ton?, remark? }     // 键存在才有值；"清空" = 删键
 * ```
 *
 * <p><b>只允许 ton / remark</b>：价格（网价/现货价/差价）全部实时派生且只读，进文档会造出
 * 第二份互相打架的真相源。行结构（增删行/改规格/排序）仍走 Java REST，这里不提供结构操作。</p>
 *
 * <p><b>删除语义</b>（与任务约定一致）：</p>
 * <ul>
 *   <li>格内字段被删（`ton`/`remark` 键不存在）= 「输入框被清空」→ 物化把该列写 **NULL**；</li>
 *   <li>整个 `itemId` 在 `items` 里不存在 = **不删明细行**：明细行结构归 REST 管，物化只是
 *       没有这条行的可写值可更新，保持 SQL 原值（视为忽略）。</li>
 * </ul>
 */

import * as Y from 'yjs'

export const QUOTE_SHEET_META_FIELD = 'meta'
export const QUOTE_SHEET_ITEMS_FIELD = 'items'

/** `mk_quote_item.ton` 是 numeric(18,8)，这里只做「是否为有限数字」的判断。 */
export interface QuoteSheetItemValue {
  /** 报单吨位；`undefined` = 未填（SQL 里应写 NULL）。 */
  ton?: number
  /** 行备注；`undefined` = 空（SQL 里应写 NULL）。 */
  remark?: string
}

export interface QuoteSheetMeta {
  sheetId: string | null
  version: number | null
}

export interface QuoteSheetSnapshot {
  meta: QuoteSheetMeta
  /** key = itemId；value 只含存在的字段。 */
  items: Map<string, QuoteSheetItemValue>
}

/** SQL 侧的当前值（物化基线）。 */
export interface QuoteSheetItemRecord {
  ton: number | null
  remark: string | null
}

const MAX_REMARK_LENGTH = 255

function toFiniteNumber(value: unknown): number | undefined {
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined
}

function toNonEmptyText(value: unknown): string | undefined {
  return typeof value === 'string' && value !== '' ? value : undefined
}

/** 文档值 → SQL 值：缺字段即 NULL。 */
export function toQuoteItemRecord(
  value: QuoteSheetItemValue | undefined,
): QuoteSheetItemRecord {
  return {
    ton: value?.ton ?? null,
    remark: value?.remark ?? null,
  }
}

/** SQL 值 → 文档值：NULL 即不写该字段（前端读出来就是"清空"）。 */
export function toQuoteItemValue(
  record: QuoteSheetItemRecord,
): QuoteSheetItemValue {
  const value: QuoteSheetItemValue = {}
  if (record.ton !== null && Number.isFinite(record.ton)) {
    value.ton = record.ton
  }
  if (record.remark !== null && record.remark !== '') {
    value.remark =
      record.remark.length > MAX_REMARK_LENGTH
        ? record.remark.slice(0, MAX_REMARK_LENGTH)
        : record.remark
  }
  return value
}

export function quoteItemRecordsEqual(
  left: QuoteSheetItemRecord,
  right: QuoteSheetItemRecord,
): boolean {
  return left.ton === right.ton && left.remark === right.remark
}

/** 从 Y.Doc 读出纯数据快照（只读、无副作用）。 */
export function readQuoteSheetSnapshot(doc: Y.Doc): QuoteSheetSnapshot {
  const metaMap = doc.getMap<unknown>(QUOTE_SHEET_META_FIELD)
  const versionRaw = metaMap.get('version')
  const sheetIdRaw = metaMap.get('sheetId')
  const itemsMap = doc.getMap<Y.Map<unknown>>(QUOTE_SHEET_ITEMS_FIELD)
  const items = new Map<string, QuoteSheetItemValue>()
  for (const [itemId, item] of itemsMap.entries()) {
    // itemId 必须是服务端雪花 id 的十进制字符串：脏数据（空键/非数字）直接跳过，
    // 否则物化会拿它去 UPDATE 一个不存在的行。
    if (!/^\d+$/.test(itemId)) continue
    if (!(item instanceof Y.Map)) continue
    const value: QuoteSheetItemValue = {}
    const ton = toFiniteNumber(item.get('ton'))
    if (ton !== undefined) value.ton = ton
    const remark = toNonEmptyText(item.get('remark'))
    if (remark !== undefined) value.remark = remark
    items.set(itemId, value)
  }
  return {
    meta: {
      sheetId:
        typeof sheetIdRaw === 'string' && sheetIdRaw.length > 0
          ? sheetIdRaw
          : typeof sheetIdRaw === 'number' && Number.isFinite(sheetIdRaw)
            ? String(sheetIdRaw)
            : null,
      version:
        typeof versionRaw === 'number' && Number.isFinite(versionRaw)
          ? versionRaw
          : null,
    },
    items,
  }
}

/**
 * 用快照整体替换文档内容（仅用于「从 SQL 播种」与结构重建）。
 *
 * <p>播种**只写 ton/remark**：价格列不进文档。</p>
 */
export function writeQuoteSheetSnapshot(
  doc: Y.Doc,
  snapshot: QuoteSheetSnapshot,
): void {
  doc.transact(() => {
    const metaMap = doc.getMap<unknown>(QUOTE_SHEET_META_FIELD)
    if (snapshot.meta.sheetId === null) metaMap.delete('sheetId')
    else metaMap.set('sheetId', snapshot.meta.sheetId)
    if (snapshot.meta.version === null) metaMap.delete('version')
    else metaMap.set('version', snapshot.meta.version)

    const itemsMap = doc.getMap<Y.Map<unknown>>(QUOTE_SHEET_ITEMS_FIELD)
    for (const key of [...itemsMap.keys()]) {
      itemsMap.delete(key)
    }
    for (const [itemId, value] of snapshot.items) {
      if (!/^\d+$/.test(itemId)) continue
      const item = new Y.Map<unknown>()
      if (value.ton !== undefined) item.set('ton', value.ton)
      if (value.remark !== undefined) item.set('remark', value.remark)
      itemsMap.set(itemId, item)
    }
  })
}
