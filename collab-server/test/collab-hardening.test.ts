import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import * as Y from 'yjs'
import { createLogger } from '../src/logger.js'
import { CollabPersistence } from '../src/persistence.js'
import { QuoteMaterializer } from '../src/quote-materializer.js'
import { writeQuoteSheetSnapshot } from '../src/quote-sheet-model.js'
import { parseStructureEvent } from '../src/redis.js'
import { MemoryStore } from '../src/store/memory-store.js'

const silentLogger = createLogger({ level: 'error' })

describe('协作生产化加固：快照压缩 / 结构通知 / 未知行自愈', () => {
  it('单房间增量达到阈值时自动压缩为全量快照并清空旧增量', async () => {
    const store = new MemoryStore()
    const persistence = new CollabPersistence({
      store,
      logger: silentLogger,
      bootstrapFromSql: false,
      persistUpdates: true,
      updateFlushDelayMs: 5,
      compactUpdateThreshold: 3,
    })

    const doc = new Y.Doc()
    const updates: Uint8Array[] = []
    doc.on('update', (u: Uint8Array) => updates.push(u))

    const map = doc.getMap('cells')
    map.set('a', 100)
    map.set('b', 200)
    map.set('c', 300)

    for (const u of updates) {
      persistence.recordUpdate('supplier-price-list:10', u)
    }
    await persistence.flushRoom('supplier-price-list:10')

    // 已触发自动压缩：增量表清空，快照里包含全部 3 个键
    assert.equal(store.updates.get('supplier-price-list:10'), undefined)
    const compacted = await store.loadDocState('supplier-price-list:10')
    assert.ok(compacted && compacted.byteLength > 0)

    const restored = new Y.Doc()
    Y.applyUpdate(restored, compacted)
    assert.equal(restored.getMap('cells').get('a'), 100)
    assert.equal(restored.getMap('cells').get('b'), 200)
    assert.equal(restored.getMap('cells').get('c'), 300)
    assert.equal(persistence.metrics().compactedRooms, 1)
    assert.equal(persistence.metrics().compactedUpdates, 3)

    doc.destroy()
    restored.destroy()
  })

  it('compactAll 扫描所有有增量的房间并合并压缩', async () => {
    const store = new MemoryStore()
    const persistence = new CollabPersistence({
      store,
      logger: silentLogger,
      bootstrapFromSql: false,
      persistUpdates: true,
      compactUpdateThreshold: 500,
    })

    const doc = new Y.Doc()
    doc.getMap('cells').set('x', 3300)
    const update = Y.encodeStateAsUpdate(doc)
    await store.appendUpdates('supplier-price-list:20', [update])

    const total = await persistence.compactAll(1)
    assert.equal(total, 1)
    assert.equal(store.updates.get('supplier-price-list:20'), undefined)
    assert.ok(store.docs.get('supplier-price-list:20'))
    doc.destroy()
  })

  it('报单比价 REST 新增行后：reloadBaseline 与未知行自愈都能让后续 ton/remark 正常落库', async () => {
    const store = new MemoryStore({
      quoteSheets: [{ sheetId: '900', version: 1 }],
      quoteItems: [
        { itemId: '1001', sheetId: '900', lineNo: 1, ton: 10, remark: null },
      ],
    })
    let now = 1_000
    const materializer = new QuoteMaterializer({
      store,
      logger: silentLogger,
      debounceMs: 10,
      now: () => now,
      unknownItemReloadCooldownMs: 100,
    })

    const doc = new Y.Doc()
    writeQuoteSheetSnapshot(doc, {
      meta: { sheetId: '900', version: 1 },
      items: new Map([['1001', { ton: 10 }]]),
    })
    await materializer.syncOnLoad('quote-sheet:900', doc)

    // 模拟 Java REST POST /quote-sheets/900/items 新增了行 1002
    store.quoteItems.set('1002', {
      itemId: '1002',
      sheetId: '900',
      lineNo: 2,
      ton: null,
      remark: null,
    })

    // 场景 A：无 Redis 通知，推进时间超过冷却窗口，直接编辑行 1002 触发自愈重载
    now += 200
    const item1002 = new Y.Map<unknown>()
    item1002.set('ton', 25.5)
    item1002.set('remark', '加急')
    doc.getMap<Y.Map<unknown>>('items').set('1002', item1002)

    await materializer.observe('quote-sheet:900', doc, '77')
    await materializer.flushRoom('quote-sheet:900')

    assert.equal(store.getQuoteItem('1002')?.ton, 25.5)
    assert.equal(store.getQuoteItem('1002')?.remark, '加急')

    // 场景 B：通过 collab:structure 触发显式 reloadBaseline（新增行 1003）
    store.quoteItems.set('1003', {
      itemId: '1003',
      sheetId: '900',
      lineNo: 3,
      ton: null,
      remark: null,
    })
    await materializer.reloadBaseline('quote-sheet:900')
    const item1003 = new Y.Map<unknown>()
    item1003.set('ton', 18)
    doc.getMap<Y.Map<unknown>>('items').set('1003', item1003)
    await materializer.observe('quote-sheet:900', doc, '77')
    await materializer.flushRoom('quote-sheet:900')
    assert.equal(store.getQuoteItem('1003')?.ton, 18)

    materializer.dispose()
    doc.destroy()
  })

  it('parseStructureEvent 正确解析合法消息并忽略脏消息', () => {
    assert.deepEqual(
      parseStructureEvent(
        JSON.stringify({
          room: 'quote-sheet:900',
          event: 'item-added',
          entityId: '900',
          at: 123,
        }),
      ),
      {
        room: 'quote-sheet:900',
        event: 'item-added',
        entityId: '900',
        at: 123,
      },
    )
    assert.equal(parseStructureEvent('not json'), null)
    assert.equal(parseStructureEvent(JSON.stringify({ room: '' })), null)
  })
})
