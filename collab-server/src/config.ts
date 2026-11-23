/**
 * 运行时配置：全部来自环境变量，**不硬编码任何数据库凭据**。
 *
 * <p>部署（systemd / docker）只在 env 里给值；本地开发不配 `DATABASE_URL` 时回退到
 * 本机 `leo` 库的 dev 连接串，避免新同学 clone 后跑不起来。</p>
 */

/** npx 之外唯一被允许的默认值：本地 dev 库。生产必须显式配置。 */
export const DEFAULT_DATABASE_URL =
  'postgres://postgres:postgres@127.0.0.1:5432/leo'

/**
 * 开发态兜底密钥。**只在非生产启动且未配 `COLLAB_JWT_SECRET` 时使用**，
 * 启动日志会明确告警；生产环境缺失该变量会直接启动失败。
 */
export const DEV_INSECURE_JWT_SECRET = 'leo-collab-dev-insecure-secret'

export type SchemaCheckMode = 'strict' | 'warn'
export type CollabStoreKind = 'postgres' | 'memory'
export type LogLevel = 'debug' | 'info' | 'warn' | 'error'

export interface CollabConfig {
  /** 监听端口，默认 11212（与 leo 后端 11211、前端 3100 并列）。 */
  port: number
  /** 监听地址；容器内建议 0.0.0.0，裸机可用 127.0.0.1 交给 nginx。 */
  host: string
  /** WebSocket 路径，默认 `/collab`；其它路径在升级阶段返回 404。 */
  wsPath: string
  /** 健康检查路径，默认 `/healthz`。 */
  healthPath: string
  databaseUrl: string
  /** HS256 共享密钥（与 Java 侧 `COLLAB_JWT_SECRET` 同一值）。 */
  jwtSecret: string
  /** true 表示用的是开发态兜底密钥（启动时告警，生产禁止）。 */
  jwtSecretInsecure: boolean
  redisUrl: string | null
  /** 文档写回 SQL 的 debounce（毫秒），默认 500。 */
  debounceMs: number
  /** debounce 的上限，避免高频编辑把写回无限推迟。 */
  maxDebounceMs: number
  /** 单条 Yjs update 的字节上限，超出直接拒绝（默认 256KB）。 */
  maxUpdateBytes: number
  /** 每用户最大连接数（默认 5）。 */
  maxConnectionsPerUser: number
  /** 雪花 ID 的机器号（0-1023）。必须与 Java 实例错开，避免同一毫秒撞号。 */
  snowflakeWorkerId: number
  /** 物化失败重试的基数 / 上限（指数退避）。 */
  materializeRetryBaseMs: number
  materializeRetryMaxMs: number
  /** 表结构自检：strict 缺失即启动失败；warn 只告警（本地无库时用）。 */
  schemaCheck: SchemaCheckMode
  /** 首次加载房间时，若无协作文档，是否从 SQL 价格表生成初始 Y.Doc。 */
  bootstrapFromSql: boolean
  /** 是否把增量 update 追加到 `mk_supplier_price_collab_update`（关闭只影响持久化，不影响同步）。 */
  persistUpdates: boolean
  /** 存储实现：postgres（默认）/ memory（本地无库调试、测试）。 */
  storeKind: CollabStoreKind
  logLevel: LogLevel
  /** 优雅退出时等待未完成写回/物化的上限。 */
  shutdownTimeoutMs: number
  /** 单个房间累计追加多少条增量后触发快照压缩（默认 500）。 */
  compactUpdateThreshold: number
  /** 周期快照压缩间隔（毫秒，默认 24h；0 表示关闭周期压缩）。 */
  compactIntervalMs: number
  /** Redis 活跃协作房间标记 TTL（秒，默认 60）。 */
  activeRoomTtlSeconds: number
}

function readString(value: string | undefined): string | null {
  if (typeof value !== 'string') return null
  const trimmed = value.trim()
  return trimmed.length > 0 ? trimmed : null
}

function readInt(
  env: NodeJS.ProcessEnv,
  key: string,
  fallback: number,
  min: number,
  max: number,
): number {
  const raw = readString(env[key])
  if (raw === null) return fallback
  const parsed = Number(raw)
  if (!Number.isInteger(parsed) || parsed < min || parsed > max) {
    throw new Error(
      `环境变量 ${key} 必须是 ${min}-${max} 之间的整数，收到 ${raw}`,
    )
  }
  return parsed
}

function readBool(
  env: NodeJS.ProcessEnv,
  key: string,
  fallback: boolean,
): boolean {
  const raw = readString(env[key])
  if (raw === null) return fallback
  if (['1', 'true', 'yes', 'on'].includes(raw.toLowerCase())) return true
  if (['0', 'false', 'no', 'off'].includes(raw.toLowerCase())) return false
  throw new Error(
    `环境变量 ${key} 必须是布尔值（1/0、true/false），收到 ${raw}`,
  )
}

function readEnum<T extends string>(
  env: NodeJS.ProcessEnv,
  key: string,
  allowed: readonly T[],
  fallback: T,
): T {
  const raw = readString(env[key])
  if (raw === null) return fallback
  if (!allowed.includes(raw as T)) {
    throw new Error(
      `环境变量 ${key} 必须是 ${allowed.join(' | ')}，收到 ${raw}`,
    )
  }
  return raw as T
}

/**
 * 读取并校验配置。**校验失败立即抛错**，不做静默降级：
 * 缺密钥、端口非法这类问题必须在启动时暴露，而不是等第一个用户改价才发现。
 */
export function loadConfig(env: NodeJS.ProcessEnv = process.env): CollabConfig {
  const nodeEnv = readString(env.NODE_ENV) ?? 'development'
  const isProduction = nodeEnv === 'production'

  const wsPath = readString(env.COLLAB_WS_PATH) ?? '/collab'
  if (!wsPath.startsWith('/')) {
    throw new Error(`COLLAB_WS_PATH 必须以 / 开头，收到 ${wsPath}`)
  }

  const envSecret = readString(env.COLLAB_JWT_SECRET)
  if (envSecret === null && isProduction) {
    throw new Error(
      '生产环境必须配置 COLLAB_JWT_SECRET（与 Java 后端签发协作票据所用密钥一致）',
    )
  }
  // HS256 的密钥长度直接影响抗暴力破解能力：低于 16 字节直接拒绝启动。
  if (envSecret !== null && envSecret.length < 16) {
    throw new Error('COLLAB_JWT_SECRET 至少 16 个字符')
  }

  return {
    port: readInt(env, 'PORT', 11212, 1, 65535),
    host: readString(env.COLLAB_HOST) ?? '0.0.0.0',
    wsPath,
    healthPath: readString(env.COLLAB_HEALTH_PATH) ?? '/healthz',
    databaseUrl: readString(env.DATABASE_URL) ?? DEFAULT_DATABASE_URL,
    jwtSecret: envSecret ?? DEV_INSECURE_JWT_SECRET,
    jwtSecretInsecure: envSecret === null,
    redisUrl: readString(env.REDIS_URL),
    debounceMs: readInt(env, 'COLLAB_DEBOUNCE_MS', 500, 50, 60_000),
    maxDebounceMs: readInt(env, 'COLLAB_MAX_DEBOUNCE_MS', 2_000, 100, 300_000),
    maxUpdateBytes: readInt(
      env,
      'COLLAB_MAX_UPDATE_BYTES',
      256 * 1024,
      1024,
      16 * 1024 * 1024,
    ),
    maxConnectionsPerUser: readInt(
      env,
      'COLLAB_MAX_CONNECTIONS_PER_USER',
      5,
      1,
      1000,
    ),
    snowflakeWorkerId: readInt(env, 'COLLAB_SNOWFLAKE_WORKER_ID', 7, 0, 1023),
    materializeRetryBaseMs: readInt(
      env,
      'COLLAB_MATERIALIZE_RETRY_BASE_MS',
      500,
      50,
      60_000,
    ),
    materializeRetryMaxMs: readInt(
      env,
      'COLLAB_MATERIALIZE_RETRY_MAX_MS',
      60_000,
      1000,
      600_000,
    ),
    schemaCheck: readEnum(
      env,
      'COLLAB_SCHEMA_CHECK',
      ['strict', 'warn'],
      'strict',
    ),
    bootstrapFromSql: readBool(env, 'COLLAB_BOOTSTRAP_FROM_SQL', true),
    persistUpdates: readBool(env, 'COLLAB_PERSIST_UPDATES', true),
    storeKind: readEnum(
      env,
      'COLLAB_STORE',
      ['postgres', 'memory'],
      'postgres',
    ),
    logLevel: readEnum(
      env,
      'COLLAB_LOG_LEVEL',
      ['debug', 'info', 'warn', 'error'],
      'info',
    ),
    shutdownTimeoutMs: readInt(
      env,
      'COLLAB_SHUTDOWN_TIMEOUT_MS',
      15_000,
      1_000,
      120_000,
    ),
    compactUpdateThreshold: readInt(
      env,
      'COLLAB_COMPACT_UPDATE_THRESHOLD',
      500,
      1,
      100_000,
    ),
    compactIntervalMs: readInt(
      env,
      'COLLAB_COMPACT_INTERVAL_MS',
      24 * 60 * 60 * 1000,
      0,
      7 * 24 * 60 * 60 * 1000,
    ),
    activeRoomTtlSeconds: readInt(
      env,
      'COLLAB_ACTIVE_ROOM_TTL_SECONDS',
      60,
      10,
      600,
    ),
  }
}
