/**
 * 雪花 ID 生成（与 Java `SnowflakeIdGenerator` 同一布局）。
 *
 * <p>为什么边车要自己生成 ID：`mk_supplier_price_cell_op.id` 是 `BIGINT PRIMARY KEY`
 * 且**没有默认值**，物化审计行必须有主键；`mk_supplier_price_item.id` 同理（插入新行时）。
 * 布局保持与 Java 一致，两种进程产生的 ID 语义相同、时间上可比：</p>
 *
 * <pre>
 *   41 bit 毫秒时间戳（相对 EPOCH） | 10 bit 机器号 | 12 bit 序列号
 * </pre>
 *
 * <p><b>机器号必须与 Java 实例错开</b>（Java 用 `leo.id.machine-id`，边车用
 * `COLLAB_SNOWFLAKE_WORKER_ID`）：同一毫秒 + 同一机器号 + 同一序列号会撞主键。
 * JavaScript 侧用 BigInt 计算，返回**字符串**，绝不经过 Number（19 位会丢精度）。</p>
 */

/** 与 Java 侧 EPOCH 保持一致：2024-01-01T00:00:00+08:00。 */
export const SNOWFLAKE_EPOCH = 1_704_038_400_000n
const MACHINE_ID_BITS = 10n
const SEQUENCE_BITS = 12n
const MAX_MACHINE_ID = 1023n
export const MAX_SEQUENCE = 4095n

export interface SnowflakeOptions {
  machineId: number
  /** 注入时钟（测试用）。 */
  now?: () => number
}

export interface SnowflakeGenerator {
  /** 下一个 ID（十进制字符串，可直接传给 pg 的 BIGINT 参数）。 */
  nextId(): string
  /** 当前机器号。 */
  readonly machineId: number
}

export function createSnowflakeGenerator(
  options: SnowflakeOptions,
): SnowflakeGenerator {
  const machineId = BigInt(options.machineId)
  if (machineId < 0n || machineId > MAX_MACHINE_ID) {
    throw new Error(`雪花机器号必须在 0-1023 之间，收到 ${options.machineId}`)
  }
  const now = options.now ?? (() => Date.now())

  let lastTimestamp = -1n
  let sequence = 0n

  return {
    machineId: options.machineId,
    nextId(): string {
      let timestamp = BigInt(Math.trunc(now()))
      if (timestamp < lastTimestamp) {
        // 时钟回拨会让 ID 重复：宁可失败也不生成可能撞主键的 ID。
        throw new Error('系统时钟回拨，拒绝生成雪花 ID')
      }
      if (timestamp === lastTimestamp) {
        sequence = (sequence + 1n) & MAX_SEQUENCE
        if (sequence === 0n) {
          // 当前毫秒序列用尽，自旋等下一毫秒（与 Java 实现一致）。
          do {
            timestamp = BigInt(Math.trunc(now()))
          } while (timestamp <= lastTimestamp)
        }
      } else {
        sequence = 0n
      }
      lastTimestamp = timestamp
      const id =
        ((timestamp - SNOWFLAKE_EPOCH) << (MACHINE_ID_BITS + SEQUENCE_BITS)) |
        (machineId << SEQUENCE_BITS) |
        sequence
      return id.toString()
    },
  }
}

/** 仅用于日志/诊断：把雪花 ID 还原成生成时刻（毫秒）。 */
export function snowflakeTimestamp(id: string): number {
  const value = BigInt(id)
  const timestamp =
    (value >> (MACHINE_ID_BITS + SEQUENCE_BITS)) + SNOWFLAKE_EPOCH
  return Number(timestamp)
}
