/**
 * 协作鉴权失败：区分「票据本身不合法（4401）」与「票据合法但无权访问该房间（4403）」。
 *
 * <p>Hocuspocus 在 `onAuthenticate` 抛出错误时，会把 `reason` 原样写入
 * `permission-denied` 消息发给客户端，并用 `code` 决定线路层的关闭语义。
 * 客户端据此区分「请重新登录取票」与「没有这个表的权限」。</p>
 */

export type CollabAuthFailureKind = 'unauthorized' | 'forbidden'

export class CollabAuthError extends Error {
  readonly kind: CollabAuthFailureKind
  /** WebSocket 关闭码：4401 未认证 / 4403 已认证但禁止。 */
  readonly code: number
  /** 发给客户端的简短原因（不要带票据或密钥）。 */
  readonly reason: string

  constructor(kind: CollabAuthFailureKind, reason: string) {
    super(reason)
    this.name = 'CollabAuthError'
    this.kind = kind
    this.code = kind === 'unauthorized' ? 4401 : 4403
    this.reason = reason
  }
}

export function isCollabAuthError(error: unknown): error is CollabAuthError {
  return error instanceof CollabAuthError
}
