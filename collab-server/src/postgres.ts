/**
 * Postgres 连接与事务工具。
 *
 * <p>连接串只从配置来（`DATABASE_URL`），代码里没有任何账号密码字面量。
 * 事务用 `BEGIN/COMMIT/ROLLBACK` 显式包住物化写入：物化必须「要么全做、要么全不做」，
 * 否则会出现「版本号加了、条目没写」的中间态。</p>
 */

import { Pool, type QueryResult, type QueryResultRow } from 'pg'
import type { Logger } from './logger.js'

/** 最小查询能力：`Pool` 与 `PoolClient` 都满足，测试可注入假实现。 */
export interface Queryable {
  query<R extends QueryResultRow = QueryResultRow>(
    text: string,
    values?: unknown[],
  ): Promise<QueryResult<R>>
}

export interface ClientLike extends Queryable {
  release?: () => void
}

export interface PoolLike extends Queryable {
  connect(): Promise<ClientLike>
  end(): Promise<void>
}

export function createPool(
  databaseUrl: string,
  logger: Logger,
  maxConnections = 10,
): Pool {
  const pool = new Pool({
    connectionString: databaseUrl,
    max: maxConnections,
    // 边车不是高 QPS 服务：连接空闲 30s 回收足够，避免占着 leo 库的连接数。
    idleTimeoutMillis: 30_000,
    connectionTimeoutMillis: 5_000,
  })
  pool.on('error', (error) => {
    // 空闲连接被服务端断开时 Pool 会抛 error，不接住会让进程退出。
    logger.error('Postgres 空闲连接错误', { error: error.message })
  })
  return pool
}

/** 在显式事务里执行；回调抛错即回滚。 */
export async function withTransaction<T>(
  pool: PoolLike,
  run: (client: ClientLike) => Promise<T>,
): Promise<T> {
  const client = await pool.connect()
  try {
    await client.query('BEGIN')
    const result = await run(client)
    await client.query('COMMIT')
    return result
  } catch (error) {
    try {
      await client.query('ROLLBACK')
    } catch {
      // ROLLBACK 失败只是让连接进入未知状态，交给 release 回收；这里不再覆盖原始错误。
    }
    throw error
  } finally {
    client.release?.()
  }
}
