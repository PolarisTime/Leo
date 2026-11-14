import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { SignJWT } from 'jose'
import {
  COLLAB_WRITE_PERMISSION,
  resolveRequestedRoom,
  verifyCollabToken,
} from '../src/auth.js'
import { CollabAuthError } from '../src/errors.js'

const SECRET = 'test-secret-at-least-16-chars-long'
const ROOM = 'supplier-price:333229108731322368'

async function sign(
  payload: Record<string, unknown>,
  options: { secret?: string; expiresIn?: number } = {},
): Promise<string> {
  const secret = new TextEncoder().encode(options.secret ?? SECRET)
  const builder = new SignJWT(payload).setProtectedHeader({ alg: 'HS256' })
  if (options.expiresIn === undefined) {
    builder.setIssuedAt().setExpirationTime('5m')
  } else if (options.expiresIn !== 0) {
    builder.setIssuedAt().setExpirationTime(options.expiresIn)
  }
  return builder.sign(secret)
}

async function expectAuthError(
  promise: Promise<unknown>,
  kind: 'unauthorized' | 'forbidden',
): Promise<CollabAuthError> {
  try {
    await promise
  } catch (error) {
    assert.ok(
      error instanceof CollabAuthError,
      `应抛 CollabAuthError，实际 ${String(error)}`,
    )
    assert.equal(error.kind, kind)
    assert.equal(error.code, kind === 'unauthorized' ? 4401 : 4403)
    return error
  }
  assert.fail('期望鉴权失败，但校验通过了')
}

describe('verifyCollabToken', () => {
  it('接受任务约定的 claims（room + writable + color）', async () => {
    const token = await sign({
      sub: '333229108731322369',
      name: '张三',
      room: ROOM,
      writable: true,
      color: '#1677ff',
    })

    const claims = await verifyCollabToken({
      secret: SECRET,
      token,
      room: ROOM,
    })

    assert.equal(claims.userId, '333229108731322369')
    assert.equal(claims.name, '张三')
    assert.deepEqual(claims.rooms, [ROOM])
    assert.equal(claims.writable, true)
    assert.equal(claims.color, '#1677ff')
    assert.ok(claims.expiresAt > Math.floor(Date.now() / 1000))
  })

  it('接受设计文档 §7.1 的 claims（rooms 数组 + perms）', async () => {
    const token = await sign({
      sub: '42',
      name: '李四',
      perms: [COLLAB_WRITE_PERMISSION],
      rooms: [ROOM, 'supplier-price:999'],
    })

    const claims = await verifyCollabToken({
      secret: SECRET,
      token,
      room: ROOM,
    })

    assert.deepEqual(claims.rooms, [ROOM, 'supplier-price:999'])
    assert.equal(claims.writable, true)
    assert.equal(claims.color, null)
  })

  it('无写权限时 writable=false（只读看板）', async () => {
    const token = await sign({
      sub: '42',
      perms: ['supplier-price-lists:read'],
      rooms: [ROOM],
    })

    const claims = await verifyCollabToken({
      secret: SECRET,
      token,
      room: ROOM,
    })

    assert.equal(claims.writable, false)
  })

  it('显式 writable=false 优先于权限推导', async () => {
    const token = await sign({
      sub: '42',
      writable: false,
      perms: [COLLAB_WRITE_PERMISSION],
      room: ROOM,
    })

    const claims = await verifyCollabToken({
      secret: SECRET,
      token,
      room: ROOM,
    })

    assert.equal(claims.writable, false)
  })

  it('签名不合法 → 4401', async () => {
    const token = await sign(
      { sub: '1', room: ROOM },
      { secret: 'another-secret-16-chars' },
    )
    await expectAuthError(
      verifyCollabToken({ secret: SECRET, token, room: ROOM }),
      'unauthorized',
    )
  })

  it('已过期 → 4401', async () => {
    const token = await sign(
      { sub: '1', room: ROOM },
      // 负数表示已过期
      { expiresIn: -10 },
    )
    await expectAuthError(
      verifyCollabToken({ secret: SECRET, token, room: ROOM }),
      'unauthorized',
    )
  })

  it('缺少 exp → 4401（短票据必须有过期时间）', async () => {
    const secret = new TextEncoder().encode(SECRET)
    const token = await new SignJWT({ sub: '1', room: ROOM })
      .setProtectedHeader({ alg: 'HS256' })
      .sign(secret)

    await expectAuthError(
      verifyCollabToken({ secret: SECRET, token, room: ROOM }),
      'unauthorized',
    )
  })

  it('缺少 sub → 4401', async () => {
    const token = await sign({ name: '无主票据', room: ROOM })
    await expectAuthError(
      verifyCollabToken({ secret: SECRET, token, room: ROOM }),
      'unauthorized',
    )
  })

  it('票据不包含请求房间 → 4403（房间越权）', async () => {
    const token = await sign({ sub: '1', rooms: ['supplier-price:1'] })
    await expectAuthError(
      verifyCollabToken({ secret: SECRET, token, room: ROOM }),
      'forbidden',
    )
  })

  it('空票据 → 4401', async () => {
    await expectAuthError(
      verifyCollabToken({ secret: SECRET, token: '   ', room: ROOM }),
      'unauthorized',
    )
  })

  it('非法 color 归一为 null，不影响认证', async () => {
    const token = await sign({ sub: '1', room: ROOM, color: 'red' })
    const claims = await verifyCollabToken({
      secret: SECRET,
      token,
      room: ROOM,
    })
    assert.equal(claims.color, null)
  })
})

describe('resolveRequestedRoom', () => {
  it('优先使用 documentName', () => {
    assert.equal(
      resolveRequestedRoom(ROOM, new URLSearchParams('room=other')),
      ROOM,
    )
  })

  it('documentName 为空时回退 query.room', () => {
    assert.equal(
      resolveRequestedRoom('   ', new URLSearchParams(`room=${ROOM}`)),
      ROOM,
    )
  })

  it('两者都没有返回 null', () => {
    assert.equal(resolveRequestedRoom(undefined, new URLSearchParams()), null)
  })
})
