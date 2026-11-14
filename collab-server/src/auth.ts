/**
 * 协作票据校验（HS256）。
 *
 * <p>票据由 Java 侧 `POST /api/v2/supplier-price-lists/{listId}/collab-session` 签发，
 * 5 分钟有效、只授予一个房间。边车是**唯一的强制校验点**（浏览器不可信）：
 * 签名、过期、房间越权、只读判定都必须在这里完成。</p>
 *
 * <p>兼容两种 claims 形态，避免 Java 侧实现细节变化把边车卡死：</p>
 * <ul>
 *   <li>设计文档 §7.1：`{ sub, name, perms: [...], rooms: [...], exp }`</li>
 *   <li>任务约定：`{ sub, name, room, writable, color, exp }`</li>
 * </ul>
 * <p>`writable` 缺省时按 `perms` 是否含写权限推导，二者都没有则视为只读。</p>
 */

import { type JWTPayload, jwtVerify } from 'jose'
import { CollabAuthError } from './errors.js'

/** 写权限码：与 Java 侧 `PermissionCodes` 里的 supplier-price-lists:update 对齐。 */
export const COLLAB_WRITE_PERMISSION = 'supplier-price-lists:update'

export interface CollabClaims {
  /** 用户 id（雪花字符串，保持字符串避免精度丢失）。 */
  userId: string
  /** 展示名。 */
  name: string
  /** 票据允许访问的房间（通常只有一个）。 */
  rooms: string[]
  /** false 表示只读：边车会把该连接标记为 readOnly，写更新被拒绝。 */
  writable: boolean
  /** 光标颜色（Java 按用户分配，跨端一致）。 */
  color: string | null
  /** 过期时间（秒级时间戳）。 */
  expiresAt: number
  /** 原始权限码列表，仅用于日志/诊断。 */
  permissions: string[]
}

export interface VerifyCollabTokenInput {
  secret: string
  token: string
  /** 本次要进入的房间；票据必须显式包含它。 */
  room: string
  /** 注入时钟（测试用）。 */
  now?: () => number
}

const COLOR_PATTERN = /^#[0-9a-f]{6}$/i

function normalizeStringList(value: unknown): string[] {
  if (typeof value === 'string') {
    return value.trim().length > 0 ? [value.trim()] : []
  }
  if (!Array.isArray(value)) return []
  return value
    .map((item) => (typeof item === 'string' ? item.trim() : ''))
    .filter((item) => item.length > 0)
}

function readWritable(payload: JWTPayload, permissions: string[]): boolean {
  if (typeof payload.writable === 'boolean') return payload.writable
  // 没显式声明时按权限推导：没有写权限就是只读（看板/大屏场景）。
  return permissions.includes(COLLAB_WRITE_PERMISSION)
}

function readColor(payload: JWTPayload): string | null {
  const raw = payload.color
  if (typeof raw !== 'string') return null
  return COLOR_PATTERN.test(raw) ? raw : null
}

/**
 * 校验票据并返回归一化后的身份。
 *
 * @throws {CollabAuthError} 签名/过期/缺字段 → 4401；房间越权 → 4403。
 */
export async function verifyCollabToken(
  input: VerifyCollabTokenInput,
): Promise<CollabClaims> {
  const token = input.token.trim()
  if (token.length === 0) {
    throw new CollabAuthError('unauthorized', '缺少协作票据')
  }
  if (input.secret.trim().length === 0) {
    // 配置层已拦截，这里再兜一次：绝不能用空密钥验签。
    throw new CollabAuthError('unauthorized', '协作票据密钥未配置')
  }

  const key = new TextEncoder().encode(input.secret)
  let payload: JWTPayload
  try {
    const verified = await jwtVerify(token, key, {
      algorithms: ['HS256'],
      // 允许 5 秒时钟漂移：浏览器/边车/Java 三台机器的时钟不可能完全同步。
      clockTolerance: 5,
      ...(input.now ? { currentDate: new Date(input.now()) } : {}),
    })
    payload = verified.payload
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    // 过期与签名错误都归为 4401：客户端处理都是「重新取票」。
    throw new CollabAuthError('unauthorized', `协作票据无效：${message}`)
  }

  const userId = typeof payload.sub === 'string' ? payload.sub.trim() : ''
  if (userId.length === 0) {
    throw new CollabAuthError('unauthorized', '协作票据缺少用户标识（sub）')
  }
  if (typeof payload.exp !== 'number' || !Number.isFinite(payload.exp)) {
    // 短票据必须带过期时间；没有 exp 的令牌等于永久凭证。
    throw new CollabAuthError('unauthorized', '协作票据缺少过期时间（exp）')
  }

  const permissions = normalizeStringList(payload.perms).concat(
    normalizeStringList(payload.permissions),
  )
  const rooms = normalizeStringList(payload.rooms).concat(
    normalizeStringList(payload.room),
  )
  if (!rooms.includes(input.room)) {
    throw new CollabAuthError('forbidden', `票据无权访问房间 ${input.room}`)
  }

  const name =
    typeof payload.name === 'string' && payload.name.trim().length > 0
      ? payload.name.trim()
      : userId

  return {
    userId,
    name,
    rooms,
    writable: readWritable(payload, permissions),
    color: readColor(payload),
    expiresAt: payload.exp,
    permissions,
  }
}

/**
 * 从连接参数里解析房间名。
 *
 * <p>Hocuspocus 的 `documentName` 就是房间名（客户端 `new HocuspocusProvider({ name })`），
 * 也接受 URL 上的 `?room=`，两者的优先级是 documentName &gt; query。</p>
 */
export function resolveRequestedRoom(
  documentName: string | undefined | null,
  requestParameters: URLSearchParams | undefined,
): string | null {
  const fromDocument = documentName?.trim() ?? ''
  if (fromDocument.length > 0) return fromDocument
  const fromQuery = requestParameters?.get('room')?.trim() ?? ''
  return fromQuery.length > 0 ? fromQuery : null
}
