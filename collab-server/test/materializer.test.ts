import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import * as Y from 'yjs'
import { type CellValue, makeCellKey } from '../src/doc-model.js'
import { CollabMaterializer } from '../src/materializer.js'
import { MemoryStore } from '../src/store/memory-store.js'
import { silentLogger } from './support.js'

const ROOM = 'supplier-price-list:1001'
const LIST_ID = '1001'

const CELL = { category: '盘螺', material: 'HRB400', spec: 8, length: '9' }
const CELL_KEY = makeCellKey(CELL)
const OTHER_CELL = {
  category: '螺纹钢',
  material: 'HRB400E',
  spec: 12,
  length: '',
}

function seedStore(): MemoryStore {
  return new MemoryStore({
    lists: [
      {
        listId: LIST_ID,
        supplierId: '2001',
        brandName: '沙钢',
        quotedOn: '2026-11-13',
        warehouse: '东恒库',
        version: 0,
      },
    ],
    items: [],
  })
}

function makeDoc(): Y.Doc {
  const doc = new Y.Doc()
  doc.getMap('meta').set('listId', LIST_ID)
  return doc
}

function setCell(
  doc: Y.Doc,
  parts: { category: string; material: string; spec: number; length: string },
  value: CellValue,
): void {
  doc.transact(() => {
    const cells = doc.getMap<Y.Map<unknown>>('cells')
    const cell = new Y.Map<unknown>()
    cell.set('price', value.price)
    cell.set('status', value.status)
    cell.set('remark', value.remark)
    cells.set(makeCellKey(parts), cell)
  })
}

function createMaterializer(store: MemoryStore): CollabMaterializer {
  return new CollabMaterializer({
    store,
    logger: silentLogger(),
    debounceMs: 5,
    maxDebounceMs: 20,
    retryBaseMs: 10_000,
    retryMaxMs: 20_000,
  })
}

describe('物化器：差异写与幂等', () => {
  it('新增格 → upsert 条目 + 版本 +1 + 一条审计（带回 actor）', async () => {
    const store = seedStore()
    const materializer = createMaterializer(store)
    const doc = makeDoc()
    await materializer.syncOnLoad(ROOM, doc)

    setCell(doc, CELL, { price: 3280, status: 'NORMAL', remark: '' })
    await materializer.observe(ROOM, doc, '9001')
    const result = await materializer.flushRoom(ROOM)

    assert.ok(result)
    assert.equal(result.version, '1')
    assert.equal(result.upserted, 1)
    assert.equal(result.audited, 1)
    const item = store.getItem(LIST_ID, CELL_KEY)
    assert.ok(item)
    assert.equal(item.price, 3280)
    assert.equal(item.spec, 8)
    assert.equal(store.audits.length, 1)
    assert.equal(store.audits[0]?.actorId, '9001')
    assert.equal(store.audits[0]?.listRevision, '1')
    materializer.dispose()
  })

  it('文档没变化时重复 store 不写库、不加版本（幂等）', async () => {
    const store = seedStore()
    const materializer = createMaterializer(store)
    const doc = makeDoc()
    await materializer.syncOnLoad(ROOM, doc)
    setCell(doc, CELL, { price: 3280, status: 'NORMAL', remark: '' })
    await materializer.observe(ROOM, doc, '9001')
    await materializer.flushRoom(ROOM)

    const applyCalls = store.applyCalls
    const version = store.lists.get(LIST_ID)?.version

    await materializer.observe(ROOM, doc, '9001')
    const second = await materializer.flushRoom(ROOM)

    assert.equal(second, null)
    assert.equal(store.applyCalls, applyCalls)
    assert.equal(store.lists.get(LIST_ID)?.version, version)
    assert.equal(store.audits.length, 1)
    materializer.dispose()
  })

  it('改价 → 同一条目更新（不新增行）+ 版本再 +1', async () => {
    const store = seedStore()
    const materializer = createMaterializer(store)
    const doc = makeDoc()
    await materializer.syncOnLoad(ROOM, doc)
    setCell(doc, CELL, { price: 3280, status: 'NORMAL', remark: '' })
    await materializer.observe(ROOM, doc, '9001')
    await materializer.flushRoom(ROOM)
    const originalId = store.getItem(LIST_ID, CELL_KEY)?.id

    setCell(doc, CELL, { price: 3300, status: 'NORMAL', remark: '调价' })
    await materializer.observe(ROOM, doc, '9002')
    const result = await materializer.flushRoom(ROOM)

    assert.equal(result?.version, '2')
    const item = store.getItem(LIST_ID, CELL_KEY)
    assert.equal(item?.id, originalId)
    assert.equal(item?.price, 3300)
    assert.equal(item?.remark, '调价')
    assert.equal(store.items.size, 1)
    materializer.dispose()
  })

  it('清空格（价格空 + NORMAL + 无备注）→ 删除条目并留审计', async () => {
    const store = seedStore()
    const materializer = createMaterializer(store)
    const doc = makeDoc()
    await materializer.syncOnLoad(ROOM, doc)
    setCell(doc, CELL, { price: 3280, status: 'NORMAL', remark: '' })
    await materializer.observe(ROOM, doc, '9001')
    await materializer.flushRoom(ROOM)
    assert.ok(store.getItem(LIST_ID, CELL_KEY))

    setCell(doc, CELL, { price: null, status: 'NORMAL', remark: '' })
    await materializer.observe(ROOM, doc, '9001')
    const result = await materializer.flushRoom(ROOM)

    assert.equal(result?.deleted, 1)
    assert.equal(store.getItem(LIST_ID, CELL_KEY), undefined)
    assert.equal(store.audits.length, 2)
    materializer.dispose()
  })

  it('有状态但无价格（如无货）仍然落库', async () => {
    const store = seedStore()
    const materializer = createMaterializer(store)
    const doc = makeDoc()
    await materializer.syncOnLoad(ROOM, doc)

    setCell(doc, CELL, { price: null, status: 'OUT_OF_STOCK', remark: '' })
    await materializer.observe(ROOM, doc, '9001')
    await materializer.flushRoom(ROOM)

    const item = store.getItem(LIST_ID, CELL_KEY)
    assert.equal(item?.price, null)
    assert.equal(item?.status, 'OUT_OF_STOCK')
    materializer.dispose()
  })

  it('失败时保留 dirty，重试成功后数据不丢', async () => {
    const store = seedStore()
    const materializer = createMaterializer(store)
    const doc = makeDoc()
    await materializer.syncOnLoad(ROOM, doc)
    setCell(doc, CELL, { price: 3280, status: 'NORMAL', remark: '' })
    await materializer.observe(ROOM, doc, '9001')

    store.failNextApply = new Error('db down')
    await assert.rejects(materializer.flushRoom(ROOM))
    assert.equal(materializer.metrics().pendingCells, 1)
    assert.equal(store.getItem(LIST_ID, CELL_KEY), undefined)

    const result = await materializer.flushRoom(ROOM)
    assert.equal(result?.upserted, 1)
    assert.equal(store.getItem(LIST_ID, CELL_KEY)?.price, 3280)
    assert.equal(materializer.metrics().pendingCells, 0)
    materializer.dispose()
  })

  it('按格归因：两个用户分别改两格，审计各记各的 actor', async () => {
    const store = seedStore()
    const materializer = createMaterializer(store)
    const doc = makeDoc()
    await materializer.syncOnLoad(ROOM, doc)

    setCell(doc, CELL, { price: 3280, status: 'NORMAL', remark: '' })
    await materializer.observe(ROOM, doc, '9001')
    setCell(doc, OTHER_CELL, { price: 3500, status: 'NORMAL', remark: '' })
    await materializer.observe(ROOM, doc, '9002')
    await materializer.flushRoom(ROOM)

    const actors = store.audits.map((row) => row.actorId).sort()
    assert.deepEqual(actors, ['9001', '9002'])
    materializer.dispose()
  })

  it('报价日期变化写入表头（quoted_on）', async () => {
    const store = seedStore()
    const materializer = createMaterializer(store)
    const doc = makeDoc()
    await materializer.syncOnLoad(ROOM, doc)

    doc.getMap('meta').set('quotedOn', '2026-11-14')
    await materializer.observe(ROOM, doc, '9001')
    const result = await materializer.flushRoom(ROOM)

    assert.equal(result?.version, '1')
    assert.equal(store.lists.get(LIST_ID)?.quotedOn, '2026-11-14')
    materializer.dispose()
  })

  it('加载时对账：SQL 与文档不一致 → 自动补齐（崩溃恢复）', async () => {
    const store = seedStore()
    // SQL 里已有 3280，但文档（例如崩溃前已同步到别的实例）是 3400。
    store.items.set(`1001\u0001${CELL_KEY}`, {
      listId: LIST_ID,
      id: '9001',
      category: CELL.category,
      material: CELL.material,
      spec: CELL.spec,
      length: CELL.length,
      price: 3280,
      status: 'NORMAL',
      remark: '',
      sortOrder: 1,
    })
    const materializer = createMaterializer(store)
    const doc = makeDoc()
    setCell(doc, CELL, { price: 3400, status: 'NORMAL', remark: '' })

    await materializer.syncOnLoad(ROOM, doc)
    const result = await materializer.flushRoom(ROOM)

    assert.equal(result?.upserted, 1)
    assert.equal(store.getItem(LIST_ID, CELL_KEY)?.price, 3400)
    materializer.dispose()
  })

  it('缺少 listId 时暂缓物化且不丢改动', async () => {
    const store = seedStore()
    const materializer = createMaterializer(store)
    const doc = new Y.Doc()
    await materializer.syncOnLoad(ROOM, doc)
    setCell(doc, CELL, { price: 3280, status: 'NORMAL', remark: '' })
    await materializer.observe(ROOM, doc, '9001')
    // ROOM 能解析出 listId=1001，改动留在 pending 里等 flush。
    assert.equal(materializer.metrics().pendingCells, 1)
    assert.equal(materializer.listIdOf(ROOM), LIST_ID)
    // 无法解析 listId 的房间不会凭空写库（flush 时暂缓并重试）。
    await materializer.observe('unknown-room', new Y.Doc(), '9001')
    assert.equal(materializer.listIdOf('unknown-room'), null)
    materializer.dispose()
  })
})
