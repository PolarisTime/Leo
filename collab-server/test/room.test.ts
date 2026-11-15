import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { isSupplierPriceRoom, isSupportedRoom, parseRoom } from '../src/room.js'

describe('房间名解析', () => {
  it('supplier-price-list：一张价格表一个房间', () => {
    const target = parseRoom('supplier-price-list:380031723435458560')

    assert.equal(target.kind, 'supplier-price-list')
    assert.equal(target.listId, '380031723435458560')
    assert.equal(target.supplierId, null)
    assert.equal(target.sheetId, null)
  })

  it('supplier-price：一个供应商一个房间（设计文档 §4.1）', () => {
    const target = parseRoom('supplier-price:333229108731322368')

    assert.equal(target.kind, 'supplier-price')
    assert.equal(target.supplierId, '333229108731322368')
    assert.equal(target.listId, null)
  })

  it('quote-sheet：报单比价房间', () => {
    const target = parseRoom('quote-sheet:380031723435458560')

    assert.equal(target.kind, 'quote-sheet')
    assert.equal(target.sheetId, '380031723435458560')
    // 报价单房间绝不解析成供应商价格表（否则会误走 item 物化路径）
    assert.equal(target.listId, null)
    assert.equal(target.supplierId, null)
  })

  it('纯数字沿用历史行为：等价于一张供应商价格表', () => {
    const target = parseRoom(' 123456 ')

    assert.equal(target.kind, 'supplier-price-list')
    assert.equal(target.listId, '123456')
  })

  it('未知前缀 → unknown（鉴权阶段会被拒绝）', () => {
    assert.equal(parseRoom('unknown:1').kind, 'unknown')
    assert.equal(parseRoom('supplier-price:abc').kind, 'unknown')
    assert.equal(parseRoom('quote-sheet:').kind, 'unknown')
    assert.equal(parseRoom('quote-sheet:1:2').kind, 'unknown')
    assert.equal(parseRoom('').kind, 'unknown')
  })

  it('前缀隔离：quote-sheet 与 supplier-price* 互不误判', () => {
    assert.equal(isSupportedRoom('quote-sheet:1'), true)
    assert.equal(isSupportedRoom('supplier-price:1'), true)
    assert.equal(isSupportedRoom('supplier-price-list:1'), true)
    assert.equal(isSupportedRoom('quote-sheet'), false)

    assert.equal(isSupplierPriceRoom(parseRoom('quote-sheet:1').kind), false)
    assert.equal(isSupplierPriceRoom(parseRoom('supplier-price:1').kind), true)
    assert.equal(
      isSupplierPriceRoom(parseRoom('supplier-price-list:1').kind),
      true,
    )
  })
})
