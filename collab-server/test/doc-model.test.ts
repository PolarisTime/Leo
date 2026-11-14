import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import * as Y from 'yjs'
import {
  CELL_KEY_SEPARATOR,
  cellValuesEqual,
  clampColumnText,
  isMeaningfulCell,
  makeCellKey,
  normalizeCellValue,
  normalizePrice,
  normalizePriceStatus,
  parseCellKey,
  readDocSnapshot,
  writeDocSnapshot,
} from '../src/doc-model.js'

describe('cellKey', () => {
  it('用 NUL 分隔四个字段，并与 SQL 唯一索引字段一一对应', () => {
    const key = makeCellKey({
      category: '螺纹钢',
      material: 'HRB400E',
      spec: 12,
      length: '9',
    })
    assert.equal(
      key,
      `螺纹钢${CELL_KEY_SEPARATOR}HRB400E${CELL_KEY_SEPARATOR}12${CELL_KEY_SEPARATOR}9`,
    )
  })

  it('可逆解析', () => {
    const parts = { category: '盘螺', material: 'HRB400', spec: 8, length: '' }
    assert.deepEqual(parseCellKey(makeCellKey(parts)), parts)
  })

  it('字段值本身含分隔符候选（| : ,）也不会串键', () => {
    const parts = { category: 'A|B', material: 'C:D', spec: 10, length: '9,12' }
    assert.deepEqual(parseCellKey(makeCellKey(parts)), parts)
  })

  it('非法键返回 null（spec 非正整数 / 段数不对）', () => {
    assert.equal(parseCellKey('a\u0000b\u00000\u0000'), null)
    assert.equal(parseCellKey('a\u0000b\u0000x\u0000'), null)
    assert.equal(parseCellKey('a\u0000b\u000012'), null)
  })

  it('文本字段按列宽截断', () => {
    assert.equal(clampColumnText('  螺纹钢  '), '螺纹钢')
    assert.equal(clampColumnText('12345678901234567890').length, 16)
  })
})

describe('格子值归一化', () => {
  it('price=null 与 0 严格区分', () => {
    assert.equal(normalizePrice(null), null)
    assert.equal(normalizePrice(''), null)
    assert.equal(normalizePrice(0), 0)
    assert.equal(normalizePrice('3280.555'), 3280.56)
    assert.equal(normalizePrice(-1), null)
    assert.equal(normalizePrice(Number.NaN), null)
  })

  it('状态只接受枚举值，其余退回 NORMAL', () => {
    assert.equal(normalizePriceStatus('out_of_stock'), 'OUT_OF_STOCK')
    assert.equal(normalizePriceStatus('UNKNOWN'), 'NORMAL')
    assert.equal(normalizePriceStatus(undefined), 'NORMAL')
  })

  it('备注截断到 255 字符（对齐 varchar(255)）', () => {
    const cell = normalizeCellValue({ remark: 'x'.repeat(300) })
    assert.equal(cell.remark.length, 255)
  })

  it('清空判定：价格空 + NORMAL + 无备注才算清空', () => {
    assert.equal(
      isMeaningfulCell({ price: null, status: 'NORMAL', remark: '' }),
      false,
    )
    assert.equal(
      isMeaningfulCell({ price: 0, status: 'NORMAL', remark: '' }),
      true,
    )
    assert.equal(
      isMeaningfulCell({ price: null, status: 'OUT_OF_STOCK', remark: '' }),
      true,
    )
    assert.equal(
      isMeaningfulCell({ price: null, status: 'NORMAL', remark: '待议' }),
      true,
    )
  })

  it('相等判定覆盖三个字段', () => {
    const base = { price: 1, status: 'NORMAL' as const, remark: '' }
    assert.equal(cellValuesEqual(base, { ...base }), true)
    assert.equal(cellValuesEqual(base, { ...base, remark: 'x' }), false)
  })
})

describe('Y.Doc 读写', () => {
  it('writeDocSnapshot → readDocSnapshot 往返一致', () => {
    const doc = new Y.Doc()
    const cells = new Map([
      [
        makeCellKey({
          category: '盘螺',
          material: 'HRB400',
          spec: 8,
          length: '9',
        }),
        { price: 3280, status: 'NORMAL' as const, remark: '现货' },
      ],
      [
        makeCellKey({
          category: '螺纹钢',
          material: 'HRB400E',
          spec: 12,
          length: '',
        }),
        { price: null, status: 'NEGOTIABLE' as const, remark: '' },
      ],
    ])
    writeDocSnapshot(doc, {
      meta: {
        listId: '333229108731322368',
        supplierId: '333229108731322369',
        brandName: '沙钢',
        quotedOn: '2026-11-14',
        warehouse: '东恒库',
        version: 3,
      },
      cells,
    })

    const snapshot = readDocSnapshot(doc)

    assert.equal(snapshot.meta.listId, '333229108731322368')
    assert.equal(snapshot.meta.brandName, '沙钢')
    assert.equal(snapshot.meta.version, 3)
    assert.equal(snapshot.cells.size, 2)
    for (const [key, value] of cells) {
      assert.deepEqual(snapshot.cells.get(key), value)
    }
  })

  it('整体替换会清掉旧格（结构重建语义）', () => {
    const doc = new Y.Doc()
    const key = makeCellKey({
      category: 'a',
      material: 'b',
      spec: 1,
      length: '',
    })
    writeDocSnapshot(doc, {
      meta: {
        listId: '1',
        supplierId: null,
        brandName: null,
        quotedOn: null,
        warehouse: null,
        version: null,
      },
      cells: new Map([[key, { price: 1, status: 'NORMAL', remark: '' }]]),
    })
    writeDocSnapshot(doc, {
      meta: {
        listId: '1',
        supplierId: null,
        brandName: null,
        quotedOn: null,
        warehouse: null,
        version: null,
      },
      cells: new Map(),
    })
    assert.equal(readDocSnapshot(doc).cells.size, 0)
  })

  it('忽略非法 cellKey', () => {
    const doc = new Y.Doc()
    const cells = doc.getMap<Y.Map<unknown>>('cells')
    const cell = new Y.Map<unknown>()
    cell.set('price', 100)
    cells.set('not-a-valid-key', cell)
    assert.equal(readDocSnapshot(doc).cells.size, 0)
  })
})
