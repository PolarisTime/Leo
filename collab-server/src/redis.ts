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

export interface CollabRedis {
  publishListChanged(
    listId: string,
    version: string,
    room: string,
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
  quit(): Promise<unknown>
  status: string
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
    const client = new RedisConstructor(redisUrl, {
      // 连接失败不要无限重连刷屏：边车靠 hocuspocus 自身重连与运维告警。
      maxRetriesPerRequest: 2,
      lazyConnect: false,
    }) as unknown as IoredisLike
    const extension = new HocuspocusRedis({
      redis: client as never,
      // 标识必须每个实例唯一，否则同实例的消息会被自己消费。
      identifier: `leo-collab-${process.pid}`,
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
        quit: async () => {
          await client.quit()
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
