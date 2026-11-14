import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import * as Y from 'yjs'
import { makeCellKey, readDocSnapshot } from '../src/doc-model.js'
import { buildInitialDocState, CollabPersistence } from '../src/persistence.js'
import { MemoryStore } from '../src/store/memory-store.js'
import { silentLogger } from './support.js'

const ROOM = 'supplier-price-list:1001'

const SEED = {
  lists: [
    {
      listId: '1001',
      supplierId: '2001',
      brandName: '沙钢',
      quotedOn: '2026-11-13',
      warehouse: '东恒库',
      version: 2,
    },
  ],
  items: [
    {
      listId: '1001',
      id: '9001',
      category: '盘螺',
      material: 'HRB400',
      spec: 8,
      length: '9',
      price: 3280,
      status: 'NORMAL' as const,
      remark: '现货',
      sortOrder: 1,
    },
  ],
}

function decodeState(state: Uint8Array): Y.Doc {
  const doc = new Y.Doc()
  Y.applyUpdate(doc, state)
  return doc
}

describe('buildInitialDocState', () => {
  it('把 SQL 价格表转成 Y.Doc（meta + cells）', () => {
    const initial = {
      listId: '1001',
      supplierId: '2001',
      brandName: '沙钢',
      quotedOn: '2026-11-13',
      warehouse: '东恒库',
      version: 2,
      items: [
        {
          id: '9001',
          category: '盘螺',
          material: 'HRB400',
          spec: 8,
          length: '9',
          price: 3280,
          status: 'NORMAL' as const,
          remark: '',
          sortOrder: 1,
        },
      ],
    }

    const doc = decodeState(buildInitialDocState(initial))
    const snapshot = readDocSnapshot(doc)

    assert.equal(snapshot.meta.listId, '1001')
    assert.equal(snapshot.meta.quotedOn, '2026-11-13')
    assert.deepEqual(
      snapshot.cells.get(
        makeCellKey({
          category: '盘螺',
          material: 'HRB400',
          spec: 8,
          length: '9',
        }),
      ),
      { price: 3280, status: 'NORMAL', remark: '' },
    )
  })
})

describe('CollabPersistence', () => {
  it('无协作文档时从 SQL 生成并落库（首次连接不是空表）', async () => {
    const store = new MemoryStore(SEED)
    const persistence = new CollabPersistence({
      store,
      logger: silentLogger(),
      bootstrapFromSql: true,
      persistUpdates: true,
    })

    const state = await persistence.fetchState(ROOM)

    assert.ok(state)
    const doc = decodeState(state)
    assert.equal(readDocSnapshot(doc).cells.size, 1)
    // 生成后立即落库：第二次加载不再走 SQL 生成
    assert.ok(store.docs.has(ROOM))
    assert.equal(persistence.metrics().bootstrapCount, 1)

    const second = await persistence.fetchState(ROOM)
    assert.deepEqual(second, state)
    assert.equal(persistence.metrics().bootstrapCount, 1)
  })

  it('关闭 bootstrap 时返回 null（不静默造空表）', async () => {
    const store = new MemoryStore(SEED)
    const persistence = new CollabPersistence({
      store,
      logger: silentLogger(),
      bootstrapFromSql: false,
      persistUpdates: true,
    })
    assert.equal(await persistence.fetchState(ROOM), null)
  })

  it('快照写是 upsert：重复 store 同一 room 只保留一行', async () => {
    const store = new MemoryStore()
    const persistence = new CollabPersistence({
      store,
      logger: silentLogger(),
      bootstrapFromSql: false,
      persistUpdates: true,
    })
    const doc = new Y.Doc()
    const state = Y.encodeStateAsUpdate(doc)

    await persistence.storeState(ROOM, state)
    await persistence.storeState(ROOM, state)

    assert.equal(store.docs.size, 1)
    assert.equal(persistence.metrics().storedDocuments, 2)
  })

  it('增量按窗口批量落库，空 update 跳过', async () => {
    const store = new MemoryStore()
    const persistence = new CollabPersistence({
      store,
      logger: silentLogger(),
      bootstrapFromSql: false,
      persistUpdates: true,
      updateFlushDelayMs: 5,
    })

    persistence.recordUpdate(ROOM, new Uint8Array([1]))
    persistence.recordUpdate(ROOM, new Uint8Array([2]))
    persistence.recordUpdate(ROOM, new Uint8Array([]))
    await persistence.flushAll()

    assert.equal(store.updates.get(ROOM)?.length, 2)
    assert.equal(persistence.metrics().appendedUpdates, 2)
  })

  it('persistUpdates=false 时不写增量表（同步不受影响）', async () => {
    const store = new MemoryStore()
    const persistence = new CollabPersistence({
      store,
      logger: silentLogger(),
      bootstrapFromSql: false,
      persistUpdates: false,
    })

    persistence.recordUpdate(ROOM, new Uint8Array([1]))
    await persistence.flushAll()

    assert.equal(store.updates.size, 0)
  })

  it('落库失败时增量留在队列里，下次 flush 重试（不清空）', async () => {
    let failNext = true
    const store = new MemoryStore()
    const originalAppend = store.appendUpdates.bind(store)
    store.appendUpdates = async (room, updates) => {
      if (failNext) {
        failNext = false
        throw new Error('db down')
      }
      return originalAppend(room, updates)
    }
    const persistence = new CollabPersistence({
      store,
      logger: silentLogger(),
      bootstrapFromSql: false,
      persistUpdates: true,
    })

    persistence.recordUpdate(ROOM, new Uint8Array([7]))
    // 关闭流程不因单次落库失败而中断（allSettled），失败批次留在队列里。
    await persistence.flushAll()
    assert.equal(persistence.metrics().pendingUpdates, 1)

    await persistence.flushAll()
    assert.equal(store.updates.get(ROOM)?.length, 1)
    assert.equal(persistence.metrics().pendingUpdates, 0)
  })
})
