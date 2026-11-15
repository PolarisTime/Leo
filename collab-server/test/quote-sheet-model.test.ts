import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import * as Y from 'yjs'
import {
  quoteItemRecordsEqual,
  readQuoteSheetSnapshot,
  toQuoteItemRecord,
  toQuoteItemValue,
  writeQuoteSheetSnapshot,
} from '../src/quote-sheet-model.js'

const SHEET_ID = '380031723435458560'
const ITEM_ID = '380031723435458561'

describe('报单比价文档模型（ton/remark）', () => {
  it('播种只写 meta.sheetId/version 与每行的 ton/remark，不写价格/规格', () => {
    const doc = new Y.Doc()
    writeQuoteSheetSnapshot(doc, {
      meta: { sheetId: SHEET_ID, version: 3 },
      items: new Map([
        [ITEM_ID, { ton: 12.5, remark: '急单' }],
        ['380031723435458562', { ton: 3 }],
      ]),
    })

    const items = doc.getMap<Y.Map<unknown>>('items')
    const first = items.get(ITEM_ID)
    assert.equal(first?.get('ton'), 12.5)
    assert.equal(first?.get('remark'), '急单')
    // 只有 ton/remark 两个键：价格列绝不进 CRDT
    assert.deepEqual([...first.keys()].sort(), ['remark', 'ton'])

    const snapshot = readQuoteSheetSnapshot(doc)
    assert.deepEqual(snapshot.meta, { sheetId: SHEET_ID, version: 3 })
    assert.deepEqual(snapshot.items.get(ITEM_ID), {
      ton: 12.5,
      remark: '急单',
    })
    assert.deepEqual(snapshot.items.get('380031723435458562'), { ton: 3 })
  })

  it('读快照：字段缺失 = 清空；非法 itemId（非雪花数字）被跳过', () => {
    const doc = new Y.Doc()
    const items = doc.getMap<Y.Map<unknown>>('items')

    const cleared = new Y.Map<unknown>()
    items.set(ITEM_ID, cleared)

    const legacyKey = new Y.Map<unknown>()
    legacyKey.set('ton', 1)
    items.set('row-1', legacyKey)

    const snapshot = readQuoteSheetSnapshot(doc)

    assert.deepEqual(snapshot.items.get(ITEM_ID), {})
    assert.equal(snapshot.items.has('row-1'), false)
  })

  it('元数据缺失时给 null（等待物化器从房间名前缀兜底）', () => {
    const snapshot = readQuoteSheetSnapshot(new Y.Doc())
    assert.deepEqual(snapshot.meta, { sheetId: null, version: null })
    assert.equal(snapshot.items.size, 0)
  })

  it('文档值 ↔ SQL 值互转：undefined 即 NULL，空字符串也当 NULL', () => {
    assert.deepEqual(toQuoteItemRecord(undefined), { ton: null, remark: null })
    assert.deepEqual(toQuoteItemRecord({ ton: 0 }), { ton: 0, remark: null })

    assert.deepEqual(toQuoteItemValue({ ton: 1, remark: null }), { ton: 1 })
    assert.deepEqual(toQuoteItemValue({ ton: null, remark: '' }), {})
    assert.deepEqual(toQuoteItemValue({ ton: null, remark: 'x' }), {
      remark: 'x',
    })
  })

  it('比较只看 ton/remark 两个字段', () => {
    const base = { ton: 1, remark: null }
    assert.equal(quoteItemRecordsEqual(base, { ...base }), true)
    assert.equal(quoteItemRecordsEqual(base, { ...base, ton: 2 }), false)
    assert.equal(quoteItemRecordsEqual(base, { ...base, remark: 'x' }), false)
  })

  it('整体覆盖写入会清掉旧行（结构重建语义）', () => {
    const doc = new Y.Doc()
    writeQuoteSheetSnapshot(doc, {
      meta: { sheetId: SHEET_ID, version: 1 },
      items: new Map([[ITEM_ID, { ton: 1 }]]),
    })
    writeQuoteSheetSnapshot(doc, {
      meta: { sheetId: SHEET_ID, version: 2 },
      items: new Map(),
    })
    assert.equal(readQuoteSheetSnapshot(doc).items.size, 0)
  })
})
