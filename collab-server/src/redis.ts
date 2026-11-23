/**
 * Redis 集成（**可选**）。
 *
 * <p>作用有两个：</p>
 * <ol>
 *   <li>多实例扇出：`@hocuspocus/extension-redis` 把变更广播到其它边车实例；</li>
 *   <li>物化完成后 `PUBLISH collab:list-changed &lt;listId&gt;`（设计文档 §6.3，Java 侧可选用）。</li>
 * </ol>
 *
 * <p>未配置 `REDIS_URL`（或依赖没装）时整个模块退化为 no-op：单实例部署照样能跑。
 * 依赖用**动态 import**，避免把 ioredis 变成启动硬依赖。</p>
 */

import type { Extension } from '@hocuspocus/server'
import type { Logger } from './logger.js'

export const LIST_CHANGED_CHANNEL = 'collab:list-changed'
export const STRUCTURE_CHANNEL = 'collab:structure'
export const ACTIVE_KEY_PREFIX = 'collab:active:'

export interface CollabStructureEvent {
  room: string
  event: string
  entityId?: string
  at?: number
}

export interface CollabRedis {
  publishListChanged(
    listId: string,
    version: string,
    room: string,
  ): Promise<void>
  /** 标记房间存在活跃协作连接（供 Java 侧返回 409 COLLAB_ACTIVE）。 */
  setRoomActive(room: string, ttlSeconds: number): Promise<void>
  /** 清除房间活跃协作标记（该房间连接清零或停服时调用）。 */
  clearRoomActive(room: string): Promise<void>
  /** 订阅 Java REST 发布的房间结构变更通知（增删行 / 建删价格表）。 */
  subscribeStructure(
    handler: (event: CollabStructureEvent) => void,
  ): Promise<void>
  status(): string
  quit(): Promise<void>
}

export interface CollabRedisBundle {
  redis: CollabRedis | null
  /** 交给 hocuspocus 的扩展；无 Redis 时为 null。 */
  extension: Extension | null
}

const NO_REDIS: CollabRedisBundle = { redis: null, extension: null }

interface IoredisLike {
  publish(channel: string, message: string): Promise<number>
  set(key: string, value: string, mode: 'EX', seconds: number): Promise<unknown>
  del(key: string): Promise<number>
  subscribe(channel: string): Promise<unknown>
  on(
    event: 'message',
    listener: (channel: string, message: string) => void,
  ): void
  quit(): Promise<unknown>
  status: string
}

/** 解析 collab:structure JSON 消息；非法载荷返回 null，避免异常消息打崩订阅。 */
export function parseStructureEvent(raw: string): CollabStructureEvent | null {
  try {
    const parsed = JSON.parse(raw) as Partial<CollabStructureEvent>
    if (
      typeof parsed?.room !== 'string' ||
      parsed.room.trim().length === 0 ||
      typeof parsed.event !== 'string' ||
      parsed.event.trim().length === 0
    ) {
      return null
    }
    return {
      room: parsed.room.trim(),
      event: parsed.event.trim(),
      ...(typeof parsed.entityId === 'string'
        ? { entityId: parsed.entityId }
        : {}),
      ...(typeof parsed.at === 'number' ? { at: parsed.at } : {}),
    }
  } catch {
    return null
  }
}

export async function createCollabRedis(
  redisUrl: string | null,
  logger: Logger,
): Promise<CollabRedisBundle> {
  if (redisUrl === null) {
    logger.info('未配置 REDIS_URL：以单实例模式运行（无跨实例扇出）')
    return NO_REDIS
  }
  try {
    const [{ Redis: HocuspocusRedis }, ioredisModule] = await Promise.all([
      import('@hocuspocus/extension-redis'),
      import('ioredis'),
    ])
    // ioredis 是 CJS（`export = Redis`）：动态 import 后构造函数可能挂在 default 上，
    // 也可能就是命名空间本身，两种都接住。
    const moduleWithDefault = ioredisModule as unknown as {
      default?: unknown
    }
    const RedisConstructor = (moduleWithDefault.default ??
      ioredisModule) as unknown as new (
      url: string,
      options?: Record<string, unknown>,
    ) => IoredisLike
    const instanceId = `leo-collab-${process.pid}`
    const client = new RedisConstructor(redisUrl, {
      // 连接失败不要无限重连刷屏：边车靠 hocuspocus 自身重连与运维告警。
      maxRetriesPerRequest: 2,
      lazyConnect: false,
    }) as unknown as IoredisLike
    let subscriber: IoredisLike | null = null
    const extension = new HocuspocusRedis({
      redis: client as never,
      // 标识必须每个实例唯一，否则同实例的消息会被自己消费。
      identifier: instanceId,
      prefix: 'leo-collab',
      // 文档快照按 room upsert，本身幂等：不取锁，避免跨实例写被跳过。
      lockTimeout: 0,
    })
    logger.info('已启用 Redis 跨实例扇出')
    return {
      extension,
      redis: {
        status: () => client.status,
        publishListChanged: async (listId, version, room) => {
          await client.publish(
            LIST_CHANGED_CHANNEL,
            JSON.stringify({ listId, version, room, at: Date.now() }),
          )
        },
        setRoomActive: async (room, ttlSeconds) => {
          await client.set(
            `${ACTIVE_KEY_PREFIX}${room}`,
            instanceId,
            'EX',
            ttlSeconds,
          )
        },
        clearRoomActive: async (room) => {
          await client.del(`${ACTIVE_KEY_PREFIX}${room}`)
        },
        subscribeStructure: async (handler) => {
          // 订阅模式连接不能执行普通命令，单独建一条 subscriber 连接。
          subscriber ??= new RedisConstructor(redisUrl, {
            maxRetriesPerRequest: 2,
            lazyConnect: false,
          }) as unknown as IoredisLike
          subscriber.on('message', (channel, raw) => {
            if (channel !== STRUCTURE_CHANNEL) return
            const event = parseStructureEvent(raw)
            if (!event) {
              logger.warn('忽略非法 collab:structure 消息', { raw })
              return
            }
            handler(event)
          })
          await subscriber.subscribe(STRUCTURE_CHANNEL)
        },
        quit: async () => {
          await Promise.allSettled([
            client.quit(),
            ...(subscriber ? [subscriber.quit()] : []),
          ])
        },
      },
    }
  } catch (error) {
    logger.warn('Redis 集成不可用，降级为单实例模式', {
      error: error instanceof Error ? error.message : String(error),
    })
    return NO_REDIS
  }
}
