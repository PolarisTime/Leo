/**
 * Postgres 存储实现：协作文档持久化 + 物化写 SQL。
 *
 * <p><b>唯一写者</b>（设计文档 §6.1）：协作开启期间 `mk_supplier_price_item` /
 * `mk_supplier_price_list` 只由本文件的物化事务写；Java 的 `PUT /{id}/items` 由队友改造成
 * 返回 `409 COLLAB_ACTIVE`。边车**绝不**碰 `mk_quote_item_price`（比价单价是另一条业务线）。</p>
 *
 * <p>表结构由 V218 迁移创建（队友负责）：</p>
 * <pre>
 * mk_supplier_price_collab_doc(room PK, state BYTEA, updated_at)
 * mk_supplier_price_collab_update(id BIGSERIAL, room, update_blob BYTEA, created_at)
 * mk_supplier_price_cell_op(id, list_id, cell_key, price, price_status, remark, actor_id, list_revision, created_at)
 * </pre>
 *
 * <p>可选列（未建也能跑，启动日志会提示）：`mk_supplier_price_list.quoted_on`、
 * `mk_supplier_price_item.updated_at`、`mk_supplier_price_list.updated_by/updated_name`。
 * 这样队友的 V218 与本服务可以并行落地，不会互相阻塞。</p>
 */

import { createHash } from 'node:crypto'
import {
  type CellValue,
  makeCellKey,
  normalizeCellValue,
} from '../doc-model.js'
import type { Logger } from '../logger.js'
import { type PoolLike, type Queryable, withTransaction } from '../postgres.js'
import { parseRoom } from '../room.js'
import type { SnowflakeGenerator } from '../snowflake.js'
import type {
  CollabDocStore,
  InitialListSnapshot,
  MaterializeRequest,
  MaterializeResult,
  MaterializeStore,
  StoredListItem,
} from './types.js'

interface SchemaFeatures {
  /** `mk_supplier_price_list.quoted_on` 是否存在（任务要求的报价日期列）。 */
  listQuotedOn: boolean
  /** `mk_supplier_price_item.updated_at`（V217 已加，理论上一定存在）。 */
  itemUpdatedAt: boolean
  /** 表头审计列。 */
  listUpdatedBy: boolean
}

const REQUIRED_TABLES = [
  'mk_supplier_price_collab_doc',
  'mk_supplier_price_collab_update',
  'mk_supplier_price_cell_op',
  'mk_supplier_price_item',
  'mk_supplier_price_list',
] as const

/** 必需列：缺任何一个都无法工作，strict 模式启动即失败并列出缺什么。 */
const REQUIRED_COLUMNS: ReadonlyArray<{ table: string; columns: string[] }> = [
  { table: 'mk_supplier_price_collab_doc', columns: ['room', 'state'] },
  {
    table: 'mk_supplier_price_collab_update',
    columns: ['room', 'update_blob'],
  },
  {
    table: 'mk_supplier_price_cell_op',
    columns: [
      'id',
      'list_id',
      'cell_key',
      'price',
      'price_status',
      'remark',
      'actor_id',
      'list_revision',
    ],
  },
  {
    table: 'mk_supplier_price_item',
    columns: [
      'id',
      'list_id',
      'category',
      'material',
      'spec',
      'length',
      'price',
      'price_status',
      'remark',
      'sort_order',
    ],
  },
  {
    table: 'mk_supplier_price_list',
    columns: ['id', 'supplier_id', 'brand_name', 'effective_from', 'version'],
  },
]

export interface PostgresStoreOptions {
  pool: PoolLike
  snowflake: SnowflakeGenerator
  logger: Logger
  /** strict：缺表/缺列直接抛错；warn：只告警（本地无 V218 时联调用）。 */
  schemaCheck: 'strict' | 'warn'
}

interface ItemRowRecord {
  id: string
  category: string
  material: string
  spec: number
  length: string
  price: number | null
  price_status: string | null
  remark: string | null
  sort_order: number | null
}

export class PostgresStore implements CollabDocStore, MaterializeStore {
  private readonly pool: PoolLike
  private readonly snowflake: SnowflakeGenerator
  private readonly logger: Logger
  private readonly schemaCheck: 'strict' | 'warn'
  private features: SchemaFeatures = {
    listQuotedOn: false,
    itemUpdatedAt: false,
    listUpdatedBy: false,
  }
  /** 进程内去重：同一份 update 连续 append 两次只写一行（Yjs 本身幂等，但别让表白长）。 */
  private readonly lastUpdateHashByRoom = new Map<string, string>()

  constructor(options: PostgresStoreOptions) {
    this.pool = options.pool
    this.snowflake = options.snowflake
    this.logger = options.logger
    this.schemaCheck = options.schemaCheck
  }

  /** 启动自检：缺表/缺列给出可执行的错误信息（谁该建什么表一眼可见）。 */
  async init(): Promise<SchemaFeatures> {
    const result = await this.pool.query<{
      table_name: string
      column_name: string
    }>(
      `SELECT table_name, column_name
         FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = ANY($1::text[])`,
      [REQUIRED_TABLES],
    )
    const existing = new Map<string, Set<string>>()
    for (const row of result.rows) {
      const columns = existing.get(row.table_name) ?? new Set<string>()
      columns.add(row.column_name)
      existing.set(row.table_name, columns)
    }

    const missing: string[] = []
    for (const table of REQUIRED_TABLES) {
      if (!existing.has(table)) {
        missing.push(`表 ${table}`)
      }
    }
    for (const requirement of REQUIRED_COLUMNS) {
      const columns = existing.get(requirement.table)
      if (!columns) continue
      for (const column of requirement.columns) {
        if (!columns.has(column)) {
          missing.push(`列 ${requirement.table}.${column}`)
        }
      }
    }

    if (missing.length > 0) {
      const message = `协作表结构缺失：${missing.join('、')}（应由 V218 迁移创建）`
      if (this.schemaCheck === 'strict') {
        throw new Error(message)
      }
      this.logger.warn('协作表结构不完整，物化写入会失败', { missing })
    }

    const listColumns = existing.get('mk_supplier_price_list') ?? new Set()
    const itemColumns = existing.get('mk_supplier_price_item') ?? new Set()
    this.features = {
      listQuotedOn: listColumns.has('quoted_on'),
      itemUpdatedAt: itemColumns.has('updated_at'),
      listUpdatedBy:
        listColumns.has('updated_by') && listColumns.has('updated_name'),
    }
    if (!this.features.listQuotedOn) {
      this.logger.warn(
        'mk_supplier_price_list 缺少 quoted_on 列：报价日期只在 Y.Doc 内生效，不回写 SQL',
      )
    }
    return this.features
  }

  async loadDocState(room: string): Promise<Uint8Array | null> {
    const result = await this.pool.query<{ state: Buffer | null }>(
      'SELECT state FROM mk_supplier_price_collab_doc WHERE room = $1',
      [room],
    )
    const state = result.rows[0]?.state
    if (!state) return null
    return new Uint8Array(state)
  }

  async saveDocState(room: string, state: Uint8Array): Promise<void> {
    await this.pool.query(
      `INSERT INTO mk_supplier_price_collab_doc (room, state, updated_at)
       VALUES ($1, $2, now())
       ON CONFLICT (room)
       DO UPDATE SET state = EXCLUDED.state, updated_at = now()`,
      [room, Buffer.from(state)],
    )
  }

  async appendUpdates(
    room: string,
    updates: readonly Uint8Array[],
  ): Promise<number> {
    const fresh: Buffer[] = []
    for (const update of updates) {
      // 空 update 不写：hocuspocus 首次同步时可能产生 0 字节更新。
      if (update.byteLength === 0) continue
      const hash = sha256Hex(update)
      if (this.lastUpdateHashByRoom.get(room) === hash) continue
      this.lastUpdateHashByRoom.set(room, hash)
      fresh.push(Buffer.from(update))
    }
    if (fresh.length === 0) return 0
    // 一次 INSERT 多行：连续输入可能产生几十条 update，逐条 round-trip 太贵。
    const placeholders = fresh
      .map((_, index) => `($1, $${index + 2})`)
      .join(', ')
    await this.pool.query(
      `INSERT INTO mk_supplier_price_collab_update (room, update_blob)
       VALUES ${placeholders}`,
      [room, ...fresh],
    )
    return fresh.length
  }

  async loadInitialList(room: string): Promise<InitialListSnapshot | null> {
    const target = parseRoom(room)
    let listId = target.listId
    if (listId === null && target.supplierId !== null) {
      const resolved = await this.pool.query<{ id: string }>(
        `SELECT id
           FROM mk_supplier_price_list
          WHERE supplier_id = $1
            AND deleted_flag = false
            AND status = 'ACTIVE'
          ORDER BY released_at DESC
          LIMIT 1`,
        [target.supplierId],
      )
      listId = resolved.rows[0]?.id ?? null
    }
    if (listId === null) {
      this.logger.warn('无法从房间名解析价格表，跳过初始文档生成', { room })
      return null
    }

    const quotedOnExpression = this.features.listQuotedOn
      ? 'quoted_on'
      : 'effective_from::text'
    const listResult = await this.pool.query<{
      id: string
      supplier_id: string
      brand_name: string | null
      quoted_on: string | null
      warehouse: string | null
      version: string | number
    }>(
      `SELECT id,
              supplier_id,
              brand_name,
              ${quotedOnExpression} AS quoted_on,
              warehouse,
              version
         FROM mk_supplier_price_list
        WHERE id = $1
          AND deleted_flag = false`,
      [listId],
    )
    const list = listResult.rows[0]
    if (!list) {
      this.logger.warn('价格表不存在或已删除，跳过初始文档生成', {
        room,
        listId,
      })
      return null
    }

    const itemsResult = await this.pool.query<ItemRowRecord>(
      `SELECT id, category, material, spec, length, price, price_status, remark, sort_order
         FROM mk_supplier_price_item
        WHERE list_id = $1
        ORDER BY sort_order ASC, id ASC`,
      [list.id],
    )

    return {
      listId: String(list.id),
      supplierId: String(list.supplier_id),
      brandName: list.brand_name,
      quotedOn: list.quoted_on,
      warehouse: list.warehouse,
      version: Number(list.version),
      items: itemsResult.rows.map((row) => toStoredItem(row)),
    }
  }

  async loadMaterialized(
    _room: string,
    listId: string,
  ): Promise<Map<string, CellValue>> {
    const result = await this.pool.query<ItemRowRecord>(
      `SELECT id, category, material, spec, length, price, price_status, remark, sort_order
         FROM mk_supplier_price_item
        WHERE list_id = $1`,
      [listId],
    )
    const cells = new Map<string, CellValue>()
    for (const row of result.rows) {
      const item = toStoredItem(row)
      cells.set(
        makeCellKey({
          category: item.category,
          material: item.material,
          spec: item.spec,
          length: item.length,
        }),
        normalizeCellValue({
          price: item.price,
          status: item.status,
          remark: item.remark,
        }),
      )
    }
    return cells
  }

  async applyChanges(request: MaterializeRequest): Promise<MaterializeResult> {
    return withTransaction(this.pool, async (client) => {
      const version = await this.bumpListVersion(client, request)
      let upserted = 0
      let deleted = 0
      let audited = 0

      for (const change of request.changes) {
        const meaningful = change.next !== null
        if (meaningful) {
          const sortOrder = change.existedBefore
            ? null
            : await this.nextSortOrder(client, request.listId)
          await this.upsertItem(client, request.listId, change, sortOrder)
          upserted += 1
        } else if (change.existedBefore) {
          await this.deleteItem(client, request.listId, change)
          deleted += 1
        } else {
          // 从未落库的格子被清空：净变化为零，不留审计。
          continue
        }
        await this.insertAudit(client, request, change, version)
        audited += 1
      }

      return { version, upserted, deleted, audited }
    })
  }

  async close(): Promise<void> {
    await this.pool.end()
  }

  private async bumpListVersion(
    client: Queryable,
    request: MaterializeRequest,
  ): Promise<string> {
    const assignments = ['version = version + 1', 'updated_at = now()']
    const params: unknown[] = [request.listId]
    if (this.features.listQuotedOn) {
      // 报价日期只在真的变化时传值；undefined 语义用「不追加赋值」表达。
      if (request.quotedOn !== null) {
        params.push(request.quotedOn)
        assignments.push(`quoted_on = $${params.length}`)
      }
    }
    if (this.features.listUpdatedBy) {
      params.push(request.actorId)
      assignments.push(`updated_by = $${params.length}::bigint`)
    }
    const result = await client.query<{ version: string | number }>(
      `UPDATE mk_supplier_price_list
          SET ${assignments.join(', ')}
        WHERE id = $1
        RETURNING version`,
      params,
    )
    const row = result.rows[0]
    if (!row) {
      throw new Error(
        `价格表 ${request.listId} 不存在，物化中止（不写任何条目）`,
      )
    }
    return String(row.version)
  }

  private async nextSortOrder(
    client: Queryable,
    listId: string,
  ): Promise<number> {
    const result = await client.query<{ max_order: number | string | null }>(
      `SELECT COALESCE(MAX(sort_order), 0) AS max_order
         FROM mk_supplier_price_item
        WHERE list_id = $1`,
      [listId],
    )
    return Number(result.rows[0]?.max_order ?? 0) + 1
  }

  /**
   * upsert 条目。
   *
   * <p>`sort_order` 只在**插入**时写（新行追加到末尾）；冲突更新时保持原顺序，
   * 否则一次改价会让整张表的行序跳动。`session` 里没有显式排序语义，顺序只是展示便利。</p>
   */
  private async upsertItem(
    client: Queryable,
    listId: string,
    change: MaterializeRequest['changes'][number],
    sortOrder: number | null,
  ): Promise<void> {
    const next = change.next
    if (next === null) return
    const columns = [
      'id',
      'list_id',
      'category',
      'material',
      'spec',
      'length',
      'price',
      'price_status',
      'remark',
      'sort_order',
    ]
    const values = ['$1', '$2', '$3', '$4', '$5', '$6', '$7', '$8', '$9', '$10']
    if (this.features.itemUpdatedAt) {
      columns.push('updated_at')
      values.push('now()')
    }
    const assignments = [
      'price = EXCLUDED.price',
      'price_status = EXCLUDED.price_status',
      'remark = EXCLUDED.remark',
    ]
    if (this.features.itemUpdatedAt) {
      assignments.push('updated_at = now()')
    }
    await client.query(
      `INSERT INTO mk_supplier_price_item (${columns.join(', ')})
       VALUES (${values.join(', ')})
       ON CONFLICT (list_id, category, material, spec, length)
       DO UPDATE SET ${assignments.join(', ')}`,
      [
        this.snowflake.nextId(),
        listId,
        change.parts.category,
        change.parts.material,
        change.parts.spec,
        change.parts.length,
        next.price,
        next.status,
        next.remark.length > 0 ? next.remark : null,
        sortOrder ?? 0,
      ],
    )
  }

  private async deleteItem(
    client: Queryable,
    listId: string,
    change: MaterializeRequest['changes'][number],
  ): Promise<void> {
    await client.query(
      `DELETE FROM mk_supplier_price_item
        WHERE list_id = $1
          AND category = $2
          AND material = $3
          AND spec = $4
          AND length = $5`,
      [
        listId,
        change.parts.category,
        change.parts.material,
        change.parts.spec,
        change.parts.length,
      ],
    )
  }

  private async insertAudit(
    client: Queryable,
    request: MaterializeRequest,
    change: MaterializeRequest['changes'][number],
    version: string,
  ): Promise<void> {
    const next = change.next
    await client.query(
      `INSERT INTO mk_supplier_price_cell_op
         (id, list_id, cell_key, price, price_status, remark, actor_id, list_revision)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8)`,
      [
        this.snowflake.nextId(),
        request.listId,
        change.cellKey,
        next?.price ?? null,
        next?.status ?? null,
        next?.remark ?? null,
        request.actorId,
        version,
      ],
    )
  }
}

function toStoredItem(row: ItemRowRecord): StoredListItem {
  return {
    id: String(row.id),
    category: String(row.category),
    material: String(row.material),
    spec: Number(row.spec),
    length: row.length === null ? '' : String(row.length),
    // numeric(12,2) 由 pg 驱动返回字符串，必须在这里转成 number（否则会变成字符串拼接）。
    price: row.price === null ? null : Number(row.price),
    status: (row.price_status ?? 'NORMAL') as StoredListItem['status'],
    remark: row.remark ?? '',
    sortOrder: Number(row.sort_order ?? 0),
  }
}

function sha256Hex(data: Uint8Array): string {
  // 用 node:crypto 而不是自己实现，避免手写摘要出错。
  return createHash('sha256').update(data).digest('hex')
}
