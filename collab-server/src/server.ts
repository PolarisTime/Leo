/**
 * Hocuspocus 服务装配：鉴权、路径限制、健康检查、持久化、物化、优雅退出。
 *
 * <p>这里只做「接线」，业务规则都在各自模块里：</p>
 * <ul>
 *   <li>票据校验 → `auth.ts`（唯一校验点，浏览器不可信）</li>
 *   <li>快照/增量 → `persistence.ts`</li>
 *   <li>Y.Doc → SQL → `materializer.ts`</li>
 * </ul>
 */

import { Database } from '@hocuspocus/extension-database'
import { Throttle } from '@hocuspocus/extension-throttle'
import { type Extension, Server } from '@hocuspocus/server'
import type * as Y from 'yjs'
import {
  type CollabClaims,
  resolveRequestedRoom,
  verifyCollabToken,
} from './auth.js'
import type { CollabConfig } from './config.js'
import { CollabAuthError, isCollabAuthError } from './errors.js'
import { buildHealthPayload, type HealthPayload } from './health.js'
import type { Logger } from './logger.js'
import { CollabMaterializer, SYSTEM_ACTOR_ID } from './materializer.js'
import { CollabPersistence } from './persistence.js'
import type { CollabRedis } from './redis.js'
import type { CollabDocStore, MaterializeStore } from './store/types.js'

export interface CollabServerDependencies {
  config: CollabConfig
  logger: Logger
  docStore: CollabDocStore
  materializeStore: MaterializeStore
  redis?: CollabRedis | null
  redisExtension?: Extension | null
  /** 版本号（健康检查展示）。 */
  version?: string
}

export interface CollabServerHandle {
  server: Server<CollabClaims>
  persistence: CollabPersistence
  materializer: CollabMaterializer
  start(): Promise<void>
  stop(): Promise<void>
  health(): HealthPayload
}

/**
 * 单用户连接数限制器（设计文档 §7.2：每用户 ≤ 5 连接）。
 *
 * <p>按 socketId 去重：一个 WebSocket 可能开多个文档（多标签页/多表），
 * 那是同一条连接，不该重复计数。</p>
 */
export class ConnectionRegistry {
  private readonly byUser = new Map<string, Set<string>>()

  constructor(private readonly limit: number) {}

  add(userId: string, socketId: string): void {
    const sockets = this.byUser.get(userId) ?? new Set<string>()
    if (sockets.has(socketId)) return
    if (sockets.size >= this.limit) {
      throw new CollabAuthError(
        'forbidden',
        `同一用户连接数超过上限（${this.limit}）`,
      )
    }
    sockets.add(socketId)
    this.byUser.set(userId, sockets)
  }

  remove(userId: string, socketId: string): void {
    const sockets = this.byUser.get(userId)
    if (!sockets) return
    sockets.delete(socketId)
    if (sockets.size === 0) this.byUser.delete(userId)
  }

  size(): number {
    return this.byUser.size
  }
}

function actorIdOf(context: CollabClaims | undefined): string {
  if (!context || typeof context.userId !== 'string') return SYSTEM_ACTOR_ID
  // actor_id 是 BIGINT：只接受十进制数字，防止把 'system' 之类塞进库。
  return /^\d+$/.test(context.userId) ? context.userId : SYSTEM_ACTOR_ID
}

export function createCollabServer(
  deps: CollabServerDependencies,
): CollabServerHandle {
  const { config, logger, docStore, materializeStore } = deps
  const startedAt = Date.now()
  const version = deps.version ?? '0.1.0'
  const connections = new ConnectionRegistry(config.maxConnectionsPerUser)

  const persistence = new CollabPersistence({
    store: docStore,
    logger,
    bootstrapFromSql: config.bootstrapFromSql,
    persistUpdates: config.persistUpdates,
  })

  const materializer = new CollabMaterializer({
    store: materializeStore,
    logger,
    debounceMs: config.debounceMs,
    maxDebounceMs: config.maxDebounceMs,
    retryBaseMs: config.materializeRetryBaseMs,
    retryMaxMs: config.materializeRetryMaxMs,
    onMaterialized: async (event) => {
      logger.info('清单已物化，广播变更', {
        listId: event.listId,
        version: event.version,
        lagMs: event.lagMs,
      })
      await deps.redis?.publishListChanged(
        event.listId,
        event.version,
        event.room,
      )
    },
  })

  /** 读文档状态：没有协作文档时从 SQL 生成（见 persistence.fetchState）。 */
  const database = new Database({
    fetch: async ({ documentName }) => persistence.fetchState(documentName),
    store: async ({ documentName, state }) =>
      persistence.storeState(documentName, state),
  })

  const extensions: Extension[] = [
    database,
    new Throttle({
      // 单 IP 每分钟 60 次连接尝试，超限封 5 分钟：够正常重连，也挡得住脚本刷。
      throttle: 60,
      consideredSeconds: 60,
      banTime: 5,
    }),
  ]
  if (deps.redisExtension) extensions.push(deps.redisExtension)

  const server = new Server<CollabClaims>({
    port: config.port,
    address: config.host,
    // 自己接管 SIGTERM/SIGINT：先关连接、再 flush 物化，最后关连接池。
    stopOnSignals: false,
    quiet: true,
    extensions,
    debounce: config.debounceMs,
    maxDebounce: config.maxDebounceMs,
    // 断连即落库（不把「文档已卸载 / 快照未写」留到下一个 debounce 窗口）。
    unloadImmediately: true,

    /** 票据校验：唯一强制校验点。 */
    onAuthenticate: async ({
      documentName,
      token,
      requestParameters,
      connectionConfig,
      socketId,
    }) => {
      const room = resolveRequestedRoom(documentName, requestParameters)
      if (room === null) {
        throw new CollabAuthError('forbidden', '缺少房间标识')
      }
      try {
        const claims = await verifyCollabToken({
          secret: config.jwtSecret,
          token,
          room,
        })
        connections.add(claims.userId, socketId)
        // 只读用户：hocuspocus 会拒绝其 syncStep2 的写入（内部按 readOnly 校验）。
        connectionConfig.readOnly = !claims.writable
        logger.info('协作连接已认证', {
          room,
          userId: claims.userId,
          writable: claims.writable,
          socketId,
        })
        return claims
      } catch (error) {
        logger.warn('协作连接被拒绝', {
          room,
          socketId,
          reason: error instanceof Error ? error.message : String(error),
          unauthorized: isCollabAuthError(error) ? error.code : 4401,
        })
        throw error
      }
    },

    /** 只允许 /collab 升级为 WebSocket；其它路径直接 404。 */
    onUpgrade: async ({ request, socket }) => {
      const pathname = (request.url ?? '').split('?')[0] ?? ''
      if (pathname === config.wsPath || pathname === `${config.wsPath}/`) {
        return
      }
      socket.write('HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n')
      socket.destroy()
      // 用「空错误」阻止 hocuspocus 继续执行升级（见 Server.setupHttpUpgrade 的 catch）。
      return Promise.reject(undefined)
    },

    /** 健康检查 + 其它 HTTP 一律 404（本服务只提供 WS）。 */
    onRequest: async ({ request, response }) => {
      const pathname = (request.url ?? '').split('?')[0] ?? ''
      if (pathname === config.healthPath) {
        const payload = handle.health()
        response.writeHead(200, {
          'Content-Type': 'application/json; charset=utf-8',
          'Cache-Control': 'no-store',
        })
        response.end(JSON.stringify(payload))
        return Promise.reject(undefined)
      }
      response.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' })
      response.end('Not Found')
      return Promise.reject(undefined)
    },

    /** 文档加载完成后对齐基线：崩溃恢复时把 SQL 与文档的差异补齐。 */
    afterLoadDocument: async ({ documentName, document, context }) => {
      try {
        await materializer.syncOnLoad(documentName, document as Y.Doc)
      } catch (error) {
        // 基线加载失败不能阻断连接：实时同步优先，物化会在下一次变更时重试。
        logger.error('加载物化基线失败，稍后重试', {
          room: documentName,
          userId: actorIdOf(context),
          error: error instanceof Error ? error.message : String(error),
        })
      }
    },

    /** 每次变更：追加增量 update（持久化）+ 记入物化 pending（按用户归因）。 */
    onChange: async ({ documentName, document, update, context }) => {
      persistence.recordUpdate(documentName, update)
      try {
        await materializer.observe(
          documentName,
          document as Y.Doc,
          actorIdOf(context),
        )
      } catch (error) {
        logger.error('登记物化变更失败', {
          room: documentName,
          error: error instanceof Error ? error.message : String(error),
        })
      }
    },

    /** 单条 update 大小上限（§7.2：> 256KB 拒绝，防恶意/异常载荷）。 */
    beforeHandleMessage: async ({ update, documentName, context }) => {
      if (update.byteLength > config.maxUpdateBytes) {
        logger.warn('拒绝超大更新', {
          room: documentName,
          userId: actorIdOf(context),
          bytes: update.byteLength,
          limit: config.maxUpdateBytes,
        })
        throw new Error('更新体积超过上限')
      }
    },

    /** 断连即 flush：文档快照 + 物化 pending 都刷干净。 */
    onDisconnect: async ({ documentName, context, socketId }) => {
      if (context?.userId) connections.remove(context.userId, socketId)
      await Promise.allSettled([
        persistence.flushRoom(documentName),
        materializer.flushRoom(documentName),
      ])
    },

    onDestroy: async () => {
      materializer.dispose()
    },
  })

  // 健康检查要读连接数/文档数与两组队列指标，统一在这里装配。
  const handle: CollabServerHandle = {
    server,
    persistence,
    materializer,
    async start(): Promise<void> {
      await server.listen()
      const address = server.address
      logger.info('协作边车已启动', {
        host: address?.address,
        port: address?.port,
        wsPath: config.wsPath,
        healthPath: config.healthPath,
        users: connections.size(),
      })
    },
    async stop(): Promise<void> {
      logger.info('开始优雅退出', {
        shutdownTimeoutMs: config.shutdownTimeoutMs,
      })
      // 1) 关闭连接并让 hocuspocus 立刻执行挂起的 onStoreDocument（v4 保证）。
      await withTimeout(
        server.destroy(),
        config.shutdownTimeoutMs,
        'server.destroy',
      )
      // 2) 刷掉增量与物化（幂等，失败也只记日志，不阻塞退出）。
      const flushes = await Promise.allSettled([
        persistence.flushAll(),
        materializer.flushAll(),
      ])
      for (const result of flushes) {
        if (result.status === 'rejected') {
          logger.error(
            '退出时 flush 失败（数据可在下次启动后按快照+增量重建）',
            {
              error:
                result.reason instanceof Error
                  ? result.reason.message
                  : String(result.reason),
            },
          )
        }
      }
      // 3) 关闭 Redis 与数据库连接（同一实例只关一次，见 index.ts 的装配）。
      await deps.redis?.quit().catch((error: unknown) => {
        logger.warn('关闭 Redis 失败', {
          error: error instanceof Error ? error.message : String(error),
        })
      })
      logger.info('已退出')
    },
    health(): HealthPayload {
      return buildHealthPayload({
        version,
        startedAt,
        connections: () => server.hocuspocus.getConnectionsCount(),
        documents: () => server.hocuspocus.getDocumentsCount(),
        persistence: () => persistence.metrics(),
        materializer: () => materializer.metrics(),
        redisStatus: () => deps.redis?.status() ?? 'disabled',
      })
    },
  }

  return handle
}

/** 给退出流程加超时，避免某个挂起连接让 systemd 等到 SIGKILL。 */
async function withTimeout<T>(
  promise: Promise<T>,
  timeoutMs: number,
  label: string,
): Promise<void> {
  let timer: NodeJS.Timeout | undefined
  const timeout = new Promise<never>((_resolve, reject) => {
    timer = setTimeout(() => {
      reject(new Error(`${label} 超时（${timeoutMs}ms）`))
    }, timeoutMs)
    timer.unref?.()
  })
  try {
    await Promise.race([promise, timeout])
  } finally {
    if (timer) clearTimeout(timer)
  }
}
