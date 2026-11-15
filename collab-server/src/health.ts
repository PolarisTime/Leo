/**
 * 健康检查载荷（`GET /healthz`）。
 *
 * <p>设计文档 §12 要求健康检查能看出「连接数 / 房间数 / 物化队列长度」，
 * 因此这里除了 `status` 还带上持久化与物化两组计数，运维不用再去翻日志。</p>
 */

import type { MaterializerMetrics } from './materializer.js'
import type { PersistenceMetrics } from './persistence.js'
import type { QuoteMaterializerMetrics } from './quote-materializer.js'

export interface HealthPayload {
  status: 'ok' | 'degraded'
  version: string
  uptimeSeconds: number
  connections: number
  documents: number
  persistence: PersistenceMetrics
  /** 供应商价格表物化队列（金额/吨位/状态）。 */
  materializer: MaterializerMetrics
  /** 报单比价物化队列（只要 ton/remark）。 */
  quoteMaterializer: QuoteMaterializerMetrics
  redis: string
  timestamp: string
}

export interface HealthDependencies {
  version: string
  startedAt: number
  connections: () => number
  documents: () => number
  persistence: () => PersistenceMetrics
  materializer: () => MaterializerMetrics
  quoteMaterializer: () => QuoteMaterializerMetrics
  redisStatus: () => string
  now?: () => number
}

export function buildHealthPayload(deps: HealthDependencies): HealthPayload {
  const now = deps.now ?? (() => Date.now())
  const materializer = deps.materializer()
  const quoteMaterializer = deps.quoteMaterializer()
  return {
    // 物化在退避重试时仍能同步（文档没丢），但 SQL 已落后，标 degraded 让监控能报警。
    // 两个物化器任一在重试都算降级：报单比价与供应商价格表互不影响，但都导致 SQL 落后。
    status:
      materializer.retryingRooms > 0 || quoteMaterializer.retryingRooms > 0
        ? 'degraded'
        : 'ok',
    version: deps.version,
    uptimeSeconds: Math.floor((now() - deps.startedAt) / 1000),
    connections: deps.connections(),
    documents: deps.documents(),
    persistence: deps.persistence(),
    materializer,
    quoteMaterializer,
    redis: deps.redisStatus(),
    timestamp: new Date(now()).toISOString(),
  }
}
