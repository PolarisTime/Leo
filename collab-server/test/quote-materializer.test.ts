import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import * as Y from 'yjs'
import { QuoteMaterializer } from '../src/quote-materializer.js'
import { MemoryStore } from '../src/store/memory-store.js'
import { silentLogger } from './support.js'

const SHEET_ID = '380031723435458560'
const ROOM = `quote-sheet:${SHEET_ID}`
const ITEM_A = '380031723435458561'
const ITEM_B = '380031723435458562'

function seedStore(): MemoryStore {
  return new MemoryStore({
    quoteSheets: [{ sheetId: SHEET_ID, version: 0 }],
    quoteItems: [
      {
        itemId: ITEM_A,
        sheetId: SHEET_ID,
        lineNo: 1,
        ton: 10,
        remark: '原备注',
      },
      { itemId: ITEM_B, sheetId: SHEET_ID, lineNo: 2, ton: null, remark: null },
    ],
  })
}

function makeDoc(): Y.Doc {
  const doc = new Y.Doc()
  doc.getMap('meta').set('sheetId', SHEET_ID)
  doc.getMap('meta').set('version', 0)
  return doc
}

function itemMap(doc: Y.Doc, itemId: string): Y.Map<unknown> {
  const items = doc.getMap<Y.Map<unknown>>('items')
  let item = items.get(itemId)
  if (!item) {
    item = new Y.Map<unknown>()
    items.set(itemId, item)
  }
  return item
}

function createMaterializer(store: MemoryStore): QuoteMaterializer {
  return new QuoteMaterializer({
    store,
    logger: silentLogger(),
    debounceMs: 5,
    maxDebounceMs: 20,
    retryBaseMs: 10_000,
    retryMaxMs: 20_000,
  })
}

describe('报单比价物化器：ton/remark 差异写', () => {
  it('改吨位与备注 → 更新明细行 + 报价单版本 +1', async () => {
    const store = seedStore()
    const materializer = createMaterializer(store)
    const doc = makeDoc()
    await materializer.syncOnLoad(ROOM, doc)

    doc.transact(() => {
      itemMap(doc, ITEM_A).set('ton', 12.5)
      itemMap(doc, ITEM_A).set('remark', '急单')
    })
    await materializer.observe(ROOM, doc, '9001')
    const result = await materializer.flushRoom(ROOM)

    assert.deepEqual(result, { version: '1', updated: 1 })
    const row = store.getQuoteItem(ITEM_A)
    assert.equal(row?.ton, 12.5)
    assert.equal(row?.remark, '急单')
    assert.equal(store.quoteSheets.get(SHEET_ID)?.version, 1)
    // 未变化的行不写、也不加版本
    assert.equal(store.getQuoteItem(ITEM_B)?.ton, null)
    materializer.dispose()
  })

  it('重复物化幂等：无变更 → 不写库、不加版本', async () => {
    const store = seedStore()
    const materializer = createMaterializer(store)
    const doc = makeDoc()
    await materializer.syncOnLoad(ROOM, doc)
    doc.transact(() => itemMap(doc, ITEM_A).set('ton', 12.5))
    await materializer.observe(ROOM, doc, '9001')
    await materializer.flushRoom(ROOM)

    const applyCalls = store.quoteApplyCalls
    await materializer.observe(ROOM, doc, '9001')
    const second = await materializer.flushRoom(ROOM)

    assert.equal(second, null)
    assert.equal(store.quoteApplyCalls, applyCalls)
    assert.equal(store.quoteSheets.get(SHEET_ID)?.version, 1)
    materializer.dispose()
  })

  it('清空字段（删键）= 写 SQL NULL（不是"忽略"）', async () => {
    const store = seedStore()
    const materializer = createMaterializer(store)
    const doc = makeDoc()
    // 先播种：文档里已有 ITEM_A 的 ton=10 / remark=原备注
    doc.transact(() => {
      itemMap(doc, ITEM_A).set('ton', 10)
      itemMap(doc, ITEM_A).set('remark', '原备注')
    })
    await materializer.syncOnLoad(ROOM, doc)

    doc.transact(() => {
      itemMap(doc, ITEM_A).delete('ton')
      itemMap(doc, ITEM_A).delete('remark')
    })
    await materializer.observe(ROOM, doc, '9001')
    const result = await materializer.flushRoom(ROOM)

    assert.equal(result?.updated, 1)
    const row = store.getQuoteItem(ITEM_A)
    assert.equal(row?.ton, null)
    assert.equal(row?.remark, null)
    materializer.dispose()
  })

  it('文档里整行消失 → 不删明细行、保留 SQL 原值（结构归 REST）', async () => {
    const store = seedStore()
    const materializer = createMaterializer(store)
    const doc = makeDoc()
    doc.transact(() => itemMap(doc, ITEM_A).set('ton', 10))
    await materializer.syncOnLoad(ROOM, doc)

    doc.transact(() => {
      doc.getMap<Y.Map<unknown>>('items').delete(ITEM_A)
    })
    await materializer.observe(ROOM, doc, '9001')
    const result = await materializer.flushRoom(ROOM)

    assert.equal(result, null)
    assert.equal(store.getQuoteItem(ITEM_A)?.ton, 10)
    assert.equal(store.quoteSheets.get(SHEET_ID)?.version, 0)
    materializer.dispose()
  })

  it('SQL 里不存在的 itemId → 忽略（不插行、不顶版本）', async () => {
    const store = seedStore()
    const materializer = createMaterializer(store)
    const doc = makeDoc()
    await materializer.syncOnLoad(ROOM, doc)

    doc.transact(() => {
      itemMap(doc, '380031723435459999').set('ton', 99)
    })
    await materializer.observe(ROOM, doc, '9001')
    const result = await materializer.flushRoom(ROOM)

    assert.equal(result, null)
    assert.equal(store.getQuoteItem('380031723435459999'), undefined)
    assert.equal(store.quoteSheets.get(SHEET_ID)?.version, 0)
    assert.equal(materializer.metrics().ignoredItems, 1)
    materializer.dispose()
  })

  it('净变化为零（改回原值）不写库、不加版本', async () => {
    const store = seedStore()
    const materializer = createMaterializer(store)
    const doc = makeDoc()
    doc.transact(() => itemMap(doc, ITEM_A).set('ton', 10))
    await materializer.syncOnLoad(ROOM, doc)

    doc.transact(() => itemMap(doc, ITEM_A).set('ton', 20))
    await materializer.observe(ROOM, doc, '9001')
    doc.transact(() => itemMap(doc, ITEM_A).set('ton', 10))
    await materializer.observe(ROOM, doc, '9001')
    const result = await materializer.flushRoom(ROOM)

    assert.equal(result, null)
    assert.equal(store.quoteSheets.get(SHEET_ID)?.version, 0)
    materializer.dispose()
  })

  it('失败时保留 dirty，重试成功后数据不丢', async () => {
    const store = seedStore()
    const materializer = createMaterializer(store)
    const doc = makeDoc()
    await materializer.syncOnLoad(ROOM, doc)
    doc.transact(() => itemMap(doc, ITEM_A).set('ton', 12.5))
    await materializer.observe(ROOM, doc, '9001')

    store.failNextApply = new Error('db down')
    await assert.rejects(materializer.flushRoom(ROOM))
    assert.equal(materializer.metrics().pendingItems, 1)
    assert.equal(store.getQuoteItem(ITEM_A)?.ton, 10)

    const result = await materializer.flushRoom(ROOM)
    assert.equal(result?.updated, 1)
    assert.equal(store.getQuoteItem(ITEM_A)?.ton, 12.5)
    materializer.dispose()
  })

  it('加载期对账：SQL 与文档不一致 → 自动补齐（崩溃恢复）', async () => {
    const store = seedStore()
    const materializer = createMaterializer(store)
    const doc = makeDoc()
    doc.transact(() => itemMap(doc, ITEM_A).set('ton', 8))

    await materializer.syncOnLoad(ROOM, doc)
    const result = await materializer.flushRoom(ROOM)

    assert.equal(result?.updated, 1)
    assert.equal(store.getQuoteItem(ITEM_A)?.ton, 8)
    materializer.dispose()
  })

  it('缺少 meta.sheetId 时用房间前缀的 sheetId 兜底', async () => {
    const store = seedStore()
    const materializer = createMaterializer(store)
    const doc = new Y.Doc() // 不带 meta
    doc.transact(() => itemMap(doc, ITEM_A).set('ton', 7))

    await materializer.syncOnLoad(ROOM, doc)
    const result = await materializer.flushRoom(ROOM)

    assert.equal(materializer.sheetIdOf(ROOM), SHEET_ID)
    assert.equal(result?.updated, 1)
    assert.equal(store.getQuoteItem(ITEM_A)?.ton, 7)
    materializer.dispose()
  })
})
