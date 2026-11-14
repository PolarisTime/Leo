/**
 * 进程入口：读环境变量 → 装配存储/Redis → 启动 hocuspocus → 装信号处理器。
 *
 * <p>退出顺序有意如此：先关 WebSocket（hocuspocus 会把挂起的快照写掉），
 * 再 flush 增量与物化，最后关连接池。任何一步失败都只记日志，**不让数据丢失变成进程崩溃**。</p>
 */

import { createRequire } from 'node:module'
import { loadConfig } from './config.js'
import { createLogger } from './logger.js'
import { createPool } from './postgres.js'
import { createCollabRedis } from './redis.js'
import { type CollabServerHandle, createCollabServer } from './server.js'
import { createSnowflakeGenerator } from './snowflake.js'
import { MemoryStore } from './store/memory-store.js'
import { PostgresStore } from './store/postgres-store.js'
import type { CollabDocStore, MaterializeStore } from './store/types.js'

const require = createRequire(import.meta.url)
const packageJson = require('../package.json') as { version?: string }

async function main(): Promise<void> {
  const config = loadConfig()
  const logger = createLogger({
    level: config.logLevel,
    base: { service: 'price-collab' },
  })

  if (config.jwtSecretInsecure) {
    logger.warn(
      '未配置 COLLAB_JWT_SECRET：正在使用开发态兜底密钥，生产环境必须显式配置',
    )
  }
  if (config.storeKind === 'memory') {
    logger.warn('COLLAB_STORE=memory：数据不落库，仅用于本地调试/测试')
  }

  const snowflake = createSnowflakeGenerator({
    machineId: config.snowflakeWorkerId,
  })

  const { docStore, materializeStore, closeStores } = await createStores(
    config,
    snowflake,
    logger,
  )

  const { redis, extension } = await createCollabRedis(config.redisUrl, logger)

  const handle = createCollabServer({
    config,
    logger,
    docStore,
    materializeStore,
    redis,
    redisExtension: extension,
    version: packageJson.version ?? '0.1.0',
  })

  await handle.start()
  logger.info('配置摘要', {
    port: config.port,
    store: config.storeKind,
    wsPath: config.wsPath,
    debounceMs: config.debounceMs,
    bootstrapFromSql: config.bootstrapFromSql,
    persistUpdates: config.persistUpdates,
    redis: redis ? 'enabled' : 'disabled',
    snowflakeWorkerId: config.snowflakeWorkerId,
  })

  installSignalHandlers(handle, logger, closeStores)
}

interface StoreBundle {
  docStore: CollabDocStore
  materializeStore: MaterializeStore
  closeStores: () => Promise<void>
}

async function createStores(
  config: ReturnType<typeof loadConfig>,
  snowflake: ReturnType<typeof createSnowflakeGenerator>,
  logger: ReturnType<typeof createLogger>,
): Promise<StoreBundle> {
  if (config.storeKind === 'memory') {
    const store = new MemoryStore()
    return {
      docStore: store,
      materializeStore: store,
      closeStores: async () => store.close(),
    }
  }

  const pool = createPool(config.databaseUrl, logger)
  const store = new PostgresStore({
    pool,
    snowflake,
    logger,
    schemaCheck: config.schemaCheck,
  })
  try {
    await store.init()
  } catch (error) {
    // 表结构缺失是部署问题：给出明确信息后退出，不要带着半残状态对外服务。
    logger.error('数据库表结构自检失败，服务不启动', {
      error: error instanceof Error ? error.message : String(error),
    })
    await pool.end().catch(() => undefined)
    throw error
  }
  return {
    docStore: store,
    materializeStore: store,
    // PostgresStore 同时实现两个接口，close 只执行一次（同一连接池）。
    closeStores: async () => store.close(),
  }
}

function installSignalHandlers(
  handle: CollabServerHandle,
  logger: ReturnType<typeof createLogger>,
  closeStores: () => Promise<void>,
): void {
  let shuttingDown = false
  const shutdown = async (reason: string): Promise<void> => {
    if (shuttingDown) return
    shuttingDown = true
    logger.info('收到退出信号', { reason })
    try {
      await handle.stop()
    } catch (error) {
      logger.error('优雅退出失败', {
        error: error instanceof Error ? error.message : String(error),
      })
    }
    try {
      await closeStores()
    } catch (error) {
      logger.warn('关闭存储失败', {
        error: error instanceof Error ? error.message : String(error),
      })
    }
    process.exit(0)
  }

  process.on('SIGTERM', () => void shutdown('SIGTERM'))
  process.on('SIGINT', () => void shutdown('SIGINT'))
  process.on('uncaughtException', (error) => {
    logger.error('未捕获异常', { error: error.message, stack: error.stack })
    void shutdown('uncaughtException')
  })
  process.on('unhandledRejection', (reason) => {
    logger.error('未处理的 Promise 拒绝', {
      error: reason instanceof Error ? reason.message : String(reason),
    })
    void shutdown('unhandledRejection')
  })
}

main().catch((error: unknown) => {
  // 启动阶段失败：直接写 stderr 并退出，交给 systemd 重启策略与运维告警。
  console.error(
    JSON.stringify({
      ts: new Date().toISOString(),
      level: 'error',
      msg: '协作边车启动失败',
      error: error instanceof Error ? error.message : String(error),
    }),
  )
  process.exit(1)
})
