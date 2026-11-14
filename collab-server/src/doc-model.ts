/**
 * 文档模型（Y.Doc 结构）——**前端、边车、物化器共用同一份 schema 定义**。
 *
 * <pre>
 * doc.getMap('meta')                        // 表头：listId / supplierId / brandName / quotedOn / warehouse / version
 * doc.getMap&lt;Y.Map&gt;('cells')                 // key = cellKey
 *   └ Y.Map&lt;{ price, status, remark }&gt;      // 一格 = 该格当前的完整状态
 * </pre>
 *
 * <p>`cellKey = [category, material, spec, length].join('\u0000')`，与 SQL 唯一索引
 * `uk_supplier_price_item_key (list_id, category, material, spec, length)` 一一对应；
 * 用 `\u0000` 而不是 `|` 之类可见字符，避免字段值本身含分隔符造成串键。</p>
 */

import * as Y from 'yjs'

export const META_FIELD = 'meta'
export const CELLS_FIELD = 'cells'

/** 与 `ck_supplier_price_item_status` 完全一致；顺序即 UI 下拉顺序。 */
export const PRICE_STATUSES = [
  'NORMAL',
  'PENDING',
  'BUNDLED',
  'NEGOTIABLE',
  'OUT_OF_STOCK',
] as const

export type PriceStatus = (typeof PRICE_STATUSES)[number]

export const DEFAULT_PRICE_STATUS: PriceStatus = 'NORMAL'

/** 分隔符固定为 NUL：任何字段值都不可能是它。 */
export const CELL_KEY_SEPARATOR = '\u0000'

/** 与 `mk_supplier_price_item` 的列宽对齐，超长截断而不是让 INSERT 失败。 */
const MAX_TEXT_LENGTH = 16
const MAX_REMARK_LENGTH = 255

export interface CellParts {
  category: string
  material: string
  /** 规格 = 直径（mm），必须为正整数（表里是 integer + CHECK spec > 0）。 */
  spec: number
  /** 定尺（如 9 / 12），无定尺存空串。 */
  length: string
}

export interface CellValue {
  /** null = 不报价（与 0 元严格区分，见 V168 注释）。 */
  price: number | null
  status: PriceStatus
  remark: string
}

export interface CollabMeta {
  listId: string | null
  supplierId: string | null
  brandName: string | null
  /** 报价日期（YYYY-MM-DD）。 */
  quotedOn: string | null
  warehouse: string | null
  /** 价格表版本号（物化后回写，供前端乐观展示）。 */
  version: number | null
}

export interface CollabSnapshot {
  meta: CollabMeta
  cells: Map<string, CellValue>
}

export const EMPTY_META: CollabMeta = {
  listId: null,
  supplierId: null,
  brandName: null,
  quotedOn: null,
  warehouse: null,
  version: null,
}

export function makeCellKey(parts: CellParts): string {
  return [
    parts.category,
    parts.material,
    String(parts.spec),
    parts.length,
  ].join(CELL_KEY_SEPARATOR)
}

/** 解析失败（字段缺失、spec 非正整数）返回 null：物化时跳过并记日志。 */
export function parseCellKey(cellKey: string): CellParts | null {
  const segments = cellKey.split(CELL_KEY_SEPARATOR)
  if (segments.length !== 4) return null
  const [category, material, specRaw, length] = segments
  if (!category || !material) return null
  if (specRaw === undefined || !/^\d+$/.test(specRaw)) return null
  const spec = Number(specRaw)
  if (!Number.isInteger(spec) || spec <= 0) return null
  return {
    category,
    material,
    spec,
    length: length ?? '',
  }
}

function toFiniteNumber(value: unknown): number | null {
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : null
  }
  if (typeof value === 'string') {
    const trimmed = value.trim()
    if (trimmed.length === 0) return null
    const parsed = Number(trimmed)
    return Number.isFinite(parsed) ? parsed : null
  }
  return null
}

/**
 * 归一化价格：非法/负数一律当作「不报价」（null），不静默改成 0。
 *
 * <p>金额精度沿用 SQL 的 `numeric(12,2)`，这里按分四舍五入，避免 3280.005 这类值
 * 在不同客户端之间出现「显示一致、写库不一致」。</p>
 */
export function normalizePrice(value: unknown): number | null {
  const numeric = toFiniteNumber(value)
  if (numeric === null || numeric < 0) return null
  return Math.round(numeric * 100) / 100
}

export function normalizePriceStatus(value: unknown): PriceStatus {
  if (typeof value === 'string') {
    const upper = value.trim().toUpperCase()
    if ((PRICE_STATUSES as readonly string[]).includes(upper)) {
      return upper as PriceStatus
    }
  }
  return DEFAULT_PRICE_STATUS
}

function normalizeText(value: unknown, maxLength: number): string {
  if (typeof value !== 'string') return ''
  const trimmed = value.trim()
  return trimmed.length > maxLength ? trimmed.slice(0, maxLength) : trimmed
}

/** 从 Y.Map / 普通对象读出整格值；缺字段按「不报价 + NORMAL + 空备注」处理。 */
export function normalizeCellValue(value: unknown): CellValue {
  const source =
    value instanceof Y.Map
      ? {
          price: value.get('price'),
          status: value.get('status'),
          remark: value.get('remark'),
        }
      : value !== null && typeof value === 'object'
        ? (value as Record<string, unknown>)
        : {}
  return {
    price: normalizePrice(source.price),
    status: normalizePriceStatus(source.status),
    remark: normalizeText(source.remark, MAX_REMARK_LENGTH),
  }
}

/**
 * 该格是否需要存在于 SQL 中。
 *
 * <p>价格为空且状态正常、没有备注 = 「清空」：物化时删除对应 `mk_supplier_price_item` 行，
 * 与设计文档 §6.2 的 `deleteClearedItems` 一致。</p>
 */
export function isMeaningfulCell(cell: CellValue): boolean {
  return (
    cell.price !== null ||
    cell.status !== DEFAULT_PRICE_STATUS ||
    cell.remark.length > 0
  )
}

export function cellValuesEqual(left: CellValue, right: CellValue): boolean {
  return (
    left.price === right.price &&
    left.status === right.status &&
    left.remark === right.remark
  )
}

function readMetaString(meta: Y.Map<unknown>, key: string): string | null {
  const raw = meta.get(key)
  if (typeof raw === 'string') {
    const trimmed = raw.trim()
    return trimmed.length > 0 ? trimmed : null
  }
  // 雪花 ID 若被客户端写成 number，这里转字符串保住精度语义（数值本身已丢精度，至少不崩）。
  if (typeof raw === 'number' && Number.isFinite(raw)) return String(raw)
  return null
}

/**
 * 从 Y.Doc 读出纯数据快照（**只读**，无副作用、不开事务）。
 *
 * <p>物化器每个 debounce 周期都要读一次；文档规模 100-300 格，成本可忽略。</p>
 */
export function readDocSnapshot(doc: Y.Doc): CollabSnapshot {
  const metaMap = doc.getMap<unknown>(META_FIELD)
  const versionRaw = metaMap.get('version')
  const version =
    typeof versionRaw === 'number' && Number.isFinite(versionRaw)
      ? versionRaw
      : typeof versionRaw === 'string' && /^\d+$/.test(versionRaw.trim())
        ? Number(versionRaw.trim())
        : null
  const meta: CollabMeta = {
    listId: readMetaString(metaMap, 'listId'),
    supplierId: readMetaString(metaMap, 'supplierId'),
    brandName: readMetaString(metaMap, 'brandName'),
    quotedOn: readMetaString(metaMap, 'quotedOn'),
    warehouse: readMetaString(metaMap, 'warehouse'),
    version,
  }

  const cellsMap = doc.getMap<Y.Map<unknown>>(CELLS_FIELD)
  const cells = new Map<string, CellValue>()
  for (const [cellKey, value] of cellsMap.entries()) {
    if (cellKey.length === 0) continue
    if (parseCellKey(cellKey) === null) continue
    cells.set(cellKey, normalizeCellValue(value))
  }
  return { meta, cells }
}

function normalizeMetaValue(
  value: string | number | null,
): string | number | null {
  return value
}

/** 把文本字段按 SQL 列宽截断（单元格走 normalizeCellValue 已处理）。 */
function clampMetaText(value: string | null, maxLength: number): string | null {
  if (value === null) return null
  return value.length > maxLength ? value.slice(0, maxLength) : value
}

/**
 * 用快照**整体替换** Y.Doc 内容（初始化 / 结构重建用）。
 *
 * <p>放在单次事务里：其它客户端要么看到旧文档、要么看到新文档，不会看到半张表。</p>
 */
export function writeDocSnapshot(doc: Y.Doc, snapshot: CollabSnapshot): void {
  doc.transact(() => {
    const metaMap = doc.getMap<unknown>(META_FIELD)
    const metaEntries: Array<[string, string | number | null]> = [
      ['listId', clampMetaText(snapshot.meta.listId, 32)],
      ['supplierId', clampMetaText(snapshot.meta.supplierId, 32)],
      ['brandName', clampMetaText(snapshot.meta.brandName, 64)],
      [
        'quotedOn',
        normalizeMetaValue(clampMetaText(snapshot.meta.quotedOn, 32)),
      ],
      ['warehouse', clampMetaText(snapshot.meta.warehouse, 64)],
      ['version', snapshot.meta.version],
    ]
    for (const [key, value] of metaEntries) {
      if (value === null) {
        metaMap.delete(key)
      } else {
        metaMap.set(key, value)
      }
    }

    const cellsMap = doc.getMap<Y.Map<unknown>>(CELLS_FIELD)
    for (const key of [...cellsMap.keys()]) {
      cellsMap.delete(key)
    }
    for (const [cellKey, cell] of snapshot.cells) {
      if (parseCellKey(cellKey) === null) continue
      const yCell = new Y.Map<unknown>()
      yCell.set('price', cell.price)
      yCell.set('status', cell.status)
      yCell.set('remark', cell.remark)
      cellsMap.set(cellKey, yCell)
    }
  })
}

/** 表格文本字段（category/material/length）按列宽截断，供上层构造 cellKey 时复用。 */
export function clampColumnText(value: string): string {
  const trimmed = value.trim()
  return trimmed.length > MAX_TEXT_LENGTH
    ? trimmed.slice(0, MAX_TEXT_LENGTH)
    : trimmed
}
