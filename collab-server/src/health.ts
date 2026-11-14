/**
 * 健康检查载荷（`GET /healthz`）。
 *
 * <p>设计文档 §12 要求健康检查能看出「连接数 / 房间数 / 物化队列长度」，
 * 因此这里除了 `status` 还带上持久化与物化两组计数，运维不用再去翻日志。</p>
 */

import type { MaterializerMetrics } from './materializer.js'
import type { PersistenceMetrics } from './persistence.js'

export interface HealthPayload {
  status: 'ok' | 'degraded'
  version: string
  uptimeSeconds: number
  connections: number
  documents: number
  persistence: PersistenceMetrics
  materializer: MaterializerMetrics
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
  redisStatus: () => string
  now?: () => number
}

export function buildHealthPayload(deps: HealthDependencies): HealthPayload {
  const now = deps.now ?? (() => Date.now())
  const materializer = deps.materializer()
  return {
    // 物化在退避重试时仍能同步（文档没丢），但 SQL 已落后，标 degraded 让监控能报警。
    status: materializer.retryingRooms > 0 ? 'degraded' : 'ok',
    version: deps.version,
    uptimeSeconds: Math.floor((now() - deps.startedAt) / 1000),
    connections: deps.connections(),
    documents: deps.documents(),
    persistence: deps.persistence(),
    materializer,
    redis: deps.redisStatus(),
    timestamp: new Date(now()).toISOString(),
  }
}
