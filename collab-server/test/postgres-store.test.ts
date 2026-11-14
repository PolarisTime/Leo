import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import type { QueryResult, QueryResultRow } from 'pg'
import { createLogger } from '../src/logger.js'
import type { ClientLike, PoolLike } from '../src/postgres.js'
import { createSnowflakeGenerator } from '../src/snowflake.js'
import { PostgresStore } from '../src/store/postgres-store.js'

interface RecordedQuery {
  text: string
  values?: unknown[]
}

/**
 * 假连接池：记录每条 SQL 并按测试脚本返回行。
 *
 * <p>用假实现而不是真库，是为了让「SQL 形状 / 事务边界 / 审计参数」这些**契约**在没有
 * Postgres 的 CI 环境里也能被锁住；真实的 SQL 语义由集成环境验证。</p>
 */
class FakePool implements PoolLike {
  readonly queries: RecordedQuery[] = []
  private readonly rowsFor: (text: string, values?: unknown[]) => unknown[]

  constructor(
    rowsFor: (text: string, values?: unknown[]) => unknown[] = (text) =>
      // 默认给一份完整的表结构，只有显式传入脚本的用例才需要关心 information_schema。
      isSchemaQuery(text) ? schemaRows() : [],
  ) {
    this.rowsFor = rowsFor
  }

  async query<R extends QueryResultRow = QueryResultRow>(
    text: string,
    values?: unknown[],
  ): Promise<QueryResult<R>> {
    this.queries.push({ text, values })
    const rows = this.rowsFor(text, values)
    return {
      rows: rows as R[],
      rowCount: rows.length,
      command: 'SELECT',
      oid: 0,
      fields: [],
    } as QueryResult<R>
  }

  async connect(): Promise<ClientLike> {
    return {
      query: (text: string, values?: unknown[]) => this.query(text, values),
      release: () => {},
    }
  }

  async end(): Promise<void> {}

  texts(): string[] {
    return this.queries.map((query) => query.text.replace(/\s+/g, ' ').trim())
  }
}

const REQUIRED_TABLES = {
  mk_supplier_price_collab_doc: ['room', 'state', 'updated_at'],
  mk_supplier_price_collab_update: ['id', 'room', 'update_blob', 'created_at'],
  mk_supplier_price_cell_op: [
    'id',
    'list_id',
    'cell_key',
    'price',
    'price_status',
    'remark',
    'actor_id',
    'list_revision',
    'created_at',
  ],
  mk_supplier_price_item: [
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
    'updated_at',
  ],
  mk_supplier_price_list: [
    'id',
    'supplier_id',
    'brand_name',
    'effective_from',
    'version',
    'quoted_on',
    'updated_by',
    'updated_name',
    'updated_at',
    'deleted_flag',
    'status',
    'released_at',
    'warehouse',
  ],
}

function schemaRows(
  tables: Record<string, string[]> = REQUIRED_TABLES,
): Array<Record<string, string>> {
  const rows: Array<Record<string, string>> = []
  for (const [table, columns] of Object.entries(tables)) {
    for (const column of columns) {
      rows.push({ table_name: table, column_name: column })
    }
  }
  return rows
}

function createStore(
  pool: FakePool,
  schemaCheck: 'strict' | 'warn' = 'strict',
  logger = createLogger({ level: 'error', sink: () => {} }),
): PostgresStore {
  return new PostgresStore({
    pool,
    snowflake: createSnowflakeGenerator({ machineId: 1 }),
    logger,
    schemaCheck,
  })
}

const CELL = { category: '盘螺', material: 'HRB400', spec: 8, length: '9' }
const CELL_KEY = `盘螺\u0000HRB400\u00008\u00009`

function isSchemaQuery(text: string): boolean {
  return text.includes('information_schema.columns')
}

/** 自定义脚本包一层：schema 查询仍返回完整表结构，只有业务 SQL 走脚本。 */
function withSchema(
  rowsFor: (text: string, values?: unknown[]) => unknown[],
): (text: string, values?: unknown[]) => unknown[] {
  return (text, values) =>
    isSchemaQuery(text) ? schemaRows() : rowsFor(text, values)
}

describe('PostgresStore 表结构自检', () => {
  it('strict 模式缺表直接失败，并指出缺什么', async () => {
    const pool = new FakePool((text) => {
      if (isSchemaQuery(text)) {
        return schemaRows({ mk_supplier_price_item: ['id'] })
      }
      return []
    })
    const store = createStore(pool)

    await assert.rejects(store.init(), /mk_supplier_price_collab_doc/)
  })

  it('warn 模式缺表只告警，不阻塞启动', async () => {
    const pool = new FakePool((text) => (isSchemaQuery(text) ? [] : []))
    const store = createStore(pool, 'warn')
    await store.init()
    assert.ok(true)
  })

  it('识别可选列（quoted_on / updated_at）', async () => {
    const pool = new FakePool(
      withSchema((text) => {
        if (text.includes('UPDATE mk_supplier_price_list'))
          return [{ version: '1' }]
        return []
      }),
    )
    const store = createStore(pool)
    await store.init()

    await store.applyChanges({
      room: 'supplier-price-list:1001',
      listId: '1001',
      changes: [
        {
          cellKey: CELL_KEY,
          parts: CELL,
          next: { price: 3280, status: 'NORMAL', remark: '' },
          actorId: '9001',
          existedBefore: false,
        },
      ],
      quotedOn: '2026-11-14',
      actorId: '9001',
    })

    const texts = pool.texts()
    const listUpdate = texts.find((text) =>
      text.startsWith('UPDATE mk_supplier_price_list'),
    )
    assert.ok(listUpdate?.includes('quoted_on = $'))
    const itemInsert = texts.find((text) =>
      text.startsWith('INSERT INTO mk_supplier_price_item'),
    )
    assert.ok(itemInsert?.includes('updated_at'))
  })
})

describe('PostgresStore 文档持久化', () => {
  it('快照写是 upsert（同一 room 一行）', async () => {
    const pool = new FakePool()
    const store = createStore(pool)
    await store.init()

    await store.saveDocState('room-1', new Uint8Array([1, 2, 3]))

    const sql = pool
      .texts()
      .find((text) =>
        text.startsWith('INSERT INTO mk_supplier_price_collab_doc'),
      )
    assert.ok(sql?.includes('ON CONFLICT (room)'))
    assert.ok(sql?.includes('DO UPDATE SET state = EXCLUDED.state'))
  })

  it('增量批量插入，并按内容去重', async () => {
    const pool = new FakePool()
    const store = createStore(pool)
    await store.init()

    const updateA = new Uint8Array([1, 2, 3])
    const updateB = new Uint8Array([4, 5])
    assert.equal(await store.appendUpdates('room-1', [updateA, updateB]), 2)
    // 同一批次再写一次：内容哈希相同 → 跳过
    assert.equal(await store.appendUpdates('room-1', [updateB]), 0)

    const inserts = pool
      .texts()
      .filter((text) =>
        text.startsWith('INSERT INTO mk_supplier_price_collab_update'),
      )
    assert.equal(inserts.length, 1)
    assert.ok(inserts[0]?.includes('($1, $2), ($1, $3)'))
  })
})

describe('PostgresStore 物化事务', () => {
  it('单事务完成：版本 +1、upsert、审计；审计带版本号与 actor', async () => {
    const pool = new FakePool(
      withSchema((text) => {
        if (text.includes('UPDATE mk_supplier_price_list'))
          return [{ version: '7' }]
        if (text.includes('MAX(sort_order)')) return [{ max_order: 2 }]
        return []
      }),
    )
    const store = createStore(pool)
    await store.init()
    // 自检也走同一个假池：清掉记录，只断言物化事务里的 SQL 顺序。
    pool.queries.length = 0

    const result = await store.applyChanges({
      room: 'supplier-price-list:1001',
      listId: '1001',
      changes: [
        {
          cellKey: CELL_KEY,
          parts: CELL,
          next: { price: 3280, status: 'NORMAL', remark: '现货' },
          actorId: '9001',
          existedBefore: false,
        },
      ],
      quotedOn: null,
      actorId: '9001',
    })

    assert.deepEqual(result, {
      version: '7',
      upserted: 1,
      deleted: 0,
      audited: 1,
    })
    const texts = pool.texts()
    assert.ok(texts[0]?.startsWith('BEGIN'))
    assert.ok(texts.at(-1)?.startsWith('COMMIT'))

    const itemInsert = pool.queries.find((query) =>
      query.text.includes('INSERT INTO mk_supplier_price_item'),
    )
    assert.ok(
      itemInsert?.text.includes(
        'ON CONFLICT (list_id, category, material, spec, length)',
      ),
    )
    // $1 是新行雪花 id（字符串），$7 价格，$10 排序号（新行追加到末尾：max 2 + 1）
    assert.equal(itemInsert?.values?.[1], '1001')
    assert.equal(itemInsert?.values?.[6], 3280)
    assert.equal(itemInsert?.values?.[9], 3)
    assert.match(String(itemInsert?.values?.[0]), /^\d+$/)

    const auditInsert = pool.queries.find((query) =>
      query.text.includes('INSERT INTO mk_supplier_price_cell_op'),
    )
    assert.ok(auditInsert)
    // 参数顺序：id, list_id, cell_key, price, price_status, remark, actor_id, list_revision
    assert.equal(auditInsert?.values?.[1], '1001')
    assert.equal(auditInsert?.values?.[2], CELL_KEY)
    assert.equal(auditInsert?.values?.[3], 3280)
    assert.equal(auditInsert?.values?.[4], 'NORMAL')
    assert.equal(auditInsert?.values?.[5], '现货')
    assert.equal(auditInsert?.values?.[6], '9001')
    assert.equal(auditInsert?.values?.[7], '7')
  })

  it('清空格（existedBefore=true）走 DELETE 而不是 upsert', async () => {
    const pool = new FakePool(
      withSchema((text) => {
        if (text.includes('UPDATE mk_supplier_price_list'))
          return [{ version: '8' }]
        return []
      }),
    )
    const store = createStore(pool)
    await store.init()

    const result = await store.applyChanges({
      room: 'supplier-price-list:1001',
      listId: '1001',
      changes: [
        {
          cellKey: CELL_KEY,
          parts: CELL,
          next: null,
          actorId: '9001',
          existedBefore: true,
        },
      ],
      quotedOn: null,
      actorId: '9001',
    })

    assert.deepEqual(result, {
      version: '8',
      upserted: 0,
      deleted: 1,
      audited: 1,
    })
    const texts = pool.texts()
    assert.ok(
      texts.some((text) =>
        text.startsWith('DELETE FROM mk_supplier_price_item'),
      ),
    )
    assert.ok(
      !texts.some((text) =>
        text.startsWith('INSERT INTO mk_supplier_price_item'),
      ),
    )
  })

  it('事务中任一步失败 → ROLLBACK，且不提交', async () => {
    const pool = new FakePool(
      withSchema((text) => {
        if (text.includes('UPDATE mk_supplier_price_list'))
          return [{ version: '9' }]
        if (text.includes('INSERT INTO mk_supplier_price_cell_op')) {
          throw new Error('audit insert failed')
        }
        return []
      }),
    )
    const store = createStore(pool)
    await store.init()

    await assert.rejects(
      store.applyChanges({
        room: 'supplier-price-list:1001',
        listId: '1001',
        changes: [
          {
            cellKey: CELL_KEY,
            parts: CELL,
            next: { price: 3280, status: 'NORMAL', remark: '' },
            actorId: '9001',
            existedBefore: false,
          },
        ],
        quotedOn: null,
        actorId: '9001',
      }),
      /audit insert failed/,
    )

    const texts = pool.texts()
    assert.ok(texts.includes('ROLLBACK'))
    assert.ok(!texts.includes('COMMIT'))
  })

  it('价格表不存在时中止（不写任何条目）', async () => {
    const pool = new FakePool(withSchema(() => []))
    const store = createStore(pool)
    await store.init()

    await assert.rejects(
      store.applyChanges({
        room: 'supplier-price-list:404',
        listId: '404',
        changes: [
          {
            cellKey: CELL_KEY,
            parts: CELL,
            next: { price: 1, status: 'NORMAL', remark: '' },
            actorId: '9001',
            existedBefore: false,
          },
        ],
        quotedOn: null,
        actorId: '9001',
      }),
      /不存在/,
    )
    assert.ok(
      !pool
        .texts()
        .some((text) => text.startsWith('INSERT INTO mk_supplier_price_item')),
    )
  })
})

describe('PostgresStore 初始文档来源', () => {
  it('按 supplier 房间解析最新 ACTIVE 价格表，并把 numeric 字符串转 number', async () => {
    const pool = new FakePool(
      withSchema((text) => {
        if (text.includes('ORDER BY released_at DESC')) return [{ id: '1001' }]
        if (
          text.includes('FROM mk_supplier_price_list') &&
          text.includes('WHERE id = $1')
        ) {
          return [
            {
              id: '1001',
              supplier_id: '2001',
              brand_name: '沙钢',
              quoted_on: '2026-11-13',
              warehouse: '东恒库',
              version: '2',
            },
          ]
        }
        if (text.includes('FROM mk_supplier_price_item')) {
          return [
            {
              id: '9001',
              category: '盘螺',
              material: 'HRB400',
              spec: 8,
              length: '9',
              price: '3280.00',
              price_status: 'NORMAL',
              remark: null,
              sort_order: '1',
            },
          ]
        }
        return []
      }),
    )
    const store = createStore(pool)
    await store.init()

    const initial = await store.loadInitialList('supplier-price:2001')

    assert.ok(initial)
    assert.equal(initial.listId, '1001')
    assert.equal(initial.version, 2)
    assert.equal(initial.items[0]?.price, 3280)
    assert.equal(initial.items[0]?.remark, '')
    assert.equal(initial.items[0]?.sortOrder, 1)
  })

  it('无法解析房间时返回 null（不猜表）', async () => {
    const pool = new FakePool()
    const store = createStore(pool)
    await store.init()

    assert.equal(await store.loadInitialList('unknown-room'), null)
  })
})
