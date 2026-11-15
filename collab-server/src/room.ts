/**
 * 房间名解析。
 *
 * <p>房间名由 Java 侧 `CollabSessionIssuer` 签发时决定（格式 `<domain>:<id>`），边车按前缀
 * 路由到不同的文档模型与物化器。当前支持：</p>
 * <ul>
 *   <li>`supplier-price:<supplierId>`：供应商价格表（设计文档 §4.1，一个供应商一个房间）</li>
 *   <li>`supplier-price-list:<listId>`：一张价格表一个房间（物化更直接）</li>
 *   <li>`<listId>`（纯数字）：等价于 `supplier-price-list:<listId>`，便于测试/直连</li>
 *   <li>`quote-sheet:<sheetId>`：报单比价（报价单），只协作吨位与备注</li>
 * </ul>
 *
 * <p>解析结果只是**兜底线索**：文档 `meta` 里存在权威 id 时以文档为准（见各物化器）。</p>
 *
 * <p>未知前缀一律 `unknown`：服务端会在鉴权阶段拒绝这类房间，避免边车为一堆拼错的房间名
 * 建空文档、或误用供应商价格表的物化路径。</p>
 */

export type RoomKind =
  | 'supplier-price'
  | 'supplier-price-list'
  | 'quote-sheet'
  | 'unknown'

export interface RoomTarget {
  room: string
  kind: RoomKind
  /** 供应商 id（雪花字符串）。 */
  supplierId: string | null
  /** 供应商价格表 id（雪花字符串）。 */
  listId: string | null
  /** 报价单 id（雪花字符串）。 */
  sheetId: string | null
}

const SUPPLIER_PRICE_PATTERN = /^supplier-price:(\d+)$/
const SUPPLIER_PRICE_LIST_PATTERN = /^supplier-price-list:(\d+)$/
const QUOTE_SHEET_PATTERN = /^quote-sheet:(\d+)$/
const PLAIN_ID_PATTERN = /^(\d+)$/

export function parseRoom(room: string): RoomTarget {
  const normalized = room.trim()

  const listMatch = SUPPLIER_PRICE_LIST_PATTERN.exec(normalized)
  if (listMatch?.[1]) {
    return {
      room: normalized,
      kind: 'supplier-price-list',
      supplierId: null,
      listId: listMatch[1],
      sheetId: null,
    }
  }

  const supplierMatch = SUPPLIER_PRICE_PATTERN.exec(normalized)
  if (supplierMatch?.[1]) {
    return {
      room: normalized,
      kind: 'supplier-price',
      supplierId: supplierMatch[1],
      listId: null,
      sheetId: null,
    }
  }

  const quoteSheetMatch = QUOTE_SHEET_PATTERN.exec(normalized)
  if (quoteSheetMatch?.[1]) {
    return {
      room: normalized,
      kind: 'quote-sheet',
      supplierId: null,
      listId: null,
      sheetId: quoteSheetMatch[1],
    }
  }

  // 纯数字：沿用历史行为，等价于一张供应商价格表。
  const plainMatch = PLAIN_ID_PATTERN.exec(normalized)
  if (plainMatch?.[1]) {
    return {
      room: normalized,
      kind: 'supplier-price-list',
      supplierId: null,
      listId: plainMatch[1],
      sheetId: null,
    }
  }

  return {
    room: normalized,
    kind: 'unknown',
    supplierId: null,
    listId: null,
    sheetId: null,
  }
}

/** 是否是可服务的房间（未知前缀在鉴权阶段直接拒绝）。 */
export function isSupportedRoom(room: string): boolean {
  return parseRoom(room).kind !== 'unknown'
}

/** 是否走供应商价格表（`mk_supplier_price_item`）的文档模型/物化路径。 */
export function isSupplierPriceRoom(kind: RoomKind): boolean {
  return kind === 'supplier-price' || kind === 'supplier-price-list'
}
