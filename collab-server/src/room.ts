/**
 * 房间名解析。
 *
 * <p>房间名由 Java 侧签发票据时决定，边车必须能同时接受设计文档与任务约定里的两种写法，
 * 免得两边对不齐就直接连不上：</p>
 * <ul>
 *   <li>`supplier-price:<supplierId>`（设计文档 §4.1：一个房间 = 一个供应商）</li>
 *   <li>`supplier-price-list:<listId>`（一个房间 = 一张价格表，物化更直接）</li>
 *   <li>`<listId>`（纯数字，便于测试/直连）</li>
 * </ul>
 *
 * <p>解析结果只是**兜底线索**：文档 `meta.listId` 存在时以文档为准（见物化器）。</p>
 */

export interface RoomTarget {
  room: string
  /** 供应商 id（雪花字符串）。 */
  supplierId: string | null
  /** 价格表 id（雪花字符串）。 */
  listId: string | null
}

const SUPPLIER_PRICE_PATTERN = /^supplier-price:(\d+)$/
const SUPPLIER_PRICE_LIST_PATTERN = /^supplier-price-list:(\d+)$/
const PLAIN_ID_PATTERN = /^(\d+)$/

export function parseRoom(room: string): RoomTarget {
  const normalized = room.trim()
  const listMatch = SUPPLIER_PRICE_LIST_PATTERN.exec(normalized)
  if (listMatch?.[1]) {
    return { room: normalized, supplierId: null, listId: listMatch[1] }
  }
  const supplierMatch = SUPPLIER_PRICE_PATTERN.exec(normalized)
  if (supplierMatch?.[1]) {
    return { room: normalized, supplierId: supplierMatch[1], listId: null }
  }
  const plainMatch = PLAIN_ID_PATTERN.exec(normalized)
  if (plainMatch?.[1]) {
    return { room: normalized, supplierId: null, listId: plainMatch[1] }
  }
  return { room: normalized, supplierId: null, listId: null }
}
