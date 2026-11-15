/**
 * 物化器的公共小工具：操作人占位与退避时长。
 *
 * <p>供应商价格表物化器（`materializer.ts`）与报单比价物化器（`quote-materializer.ts`）
 * 共用这两条口径：没有 JWT 用户时归到系统 actor；失败退避从 base 翻倍到 max。
 * 其余调度/差异逻辑各自实现（两者的「变更」形状不同：一格一个完整对象 vs 一行两个可选字段）。</p>
 */

/** 系统操作人：服务端重建/测试/对账时使用（DB 里 BIGINT 只接受十进制数字）。 */
export const SYSTEM_ACTOR_ID = '0'

/**
 * 指数退避：首次失败用 `base`，之后翻倍，封顶 `max`。
 *
 * @param currentDelay 当前已排的退避时长（0 = 还没退避过）
 */
export function nextRetryDelayMs(
  currentDelay: number,
  base: number,
  max: number,
): number {
  if (currentDelay <= 0) return Math.min(base, max)
  return Math.min(currentDelay * 2, max)
}
