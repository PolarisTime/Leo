import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import {
  createSnowflakeGenerator,
  SNOWFLAKE_EPOCH,
  snowflakeTimestamp,
} from '../src/snowflake.js'

describe('雪花 ID', () => {
  it('同一毫秒内序列号递增，ID 严格递增（字符串比较需等长，这里转 BigInt 比较）', () => {
    const generator = createSnowflakeGenerator({
      machineId: 7,
      now: () => 1_704_038_400_500,
    })

    const first = BigInt(generator.nextId())
    const second = BigInt(generator.nextId())

    assert.equal(second - first, 1n)
  })

  it('布局与 Java 一致：时间戳 / 机器号 / 序列号', () => {
    const generator = createSnowflakeGenerator({
      machineId: 7,
      now: () => Number(SNOWFLAKE_EPOCH) + 1234,
    })
    const id = generator.nextId()

    assert.equal(snowflakeTimestamp(id), Number(SNOWFLAKE_EPOCH) + 1234)
    // 机器号占 bit12-21
    assert.equal((BigInt(id) >> 12n) & 1023n, 7n)
    // 同一毫秒第一条：序列号为 0
    assert.equal(BigInt(id) & 4095n, 0n)
  })

  it('不同机器号不会产生相同 ID', () => {
    const fixedNow = () => 1_704_038_400_500
    const a = createSnowflakeGenerator({ machineId: 1, now: fixedNow })
    const b = createSnowflakeGenerator({ machineId: 2, now: fixedNow })
    assert.notEqual(a.nextId(), b.nextId())
  })

  it('序列号用尽时自旋到下一毫秒，不产生重复', () => {
    let clock = 1_704_038_400_500
    const generator = createSnowflakeGenerator({
      machineId: 0,
      // 每被调用一次时间前进 1ms 太快，用固定时间 + 显式推进验证自旋逻辑：
      now: () => clock,
    })
    const ids = new Set<string>()
    for (let i = 0; i < 4096; i += 1) {
      ids.add(generator.nextId())
    }
    assert.equal(ids.size, 4096)
    // 第 4097 条会等待下一毫秒：把时钟推进后即可继续。
    clock += 1
    const next = generator.nextId()
    assert.ok(!ids.has(next))
  })

  it('时钟回拨直接报错，绝不生成可能撞主键的 ID', () => {
    let clock = 1_704_038_400_500
    const generator = createSnowflakeGenerator({
      machineId: 0,
      now: () => clock,
    })
    generator.nextId()
    clock -= 1000
    assert.throws(() => generator.nextId(), /时钟回拨/)
  })

  it('机器号越界拒绝构造', () => {
    assert.throws(() => createSnowflakeGenerator({ machineId: 1024 }), /0-1023/)
    assert.throws(() => createSnowflakeGenerator({ machineId: -1 }), /0-1023/)
  })
})
