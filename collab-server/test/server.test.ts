import assert from 'node:assert/strict'
import http from 'node:http'
import { after, before, describe, it } from 'node:test'
import { loadConfig } from '../src/config.js'
import { CollabAuthError } from '../src/errors.js'
import {
  type CollabServerHandle,
  ConnectionRegistry,
  createCollabServer,
} from '../src/server.js'
import { MemoryStore } from '../src/store/memory-store.js'
import { silentLogger } from './support.js'

function testConfig() {
  const config = loadConfig({
    NODE_ENV: 'test',
    COLLAB_JWT_SECRET: 'test-secret-at-least-16-chars-long',
    COLLAB_STORE: 'memory',
    COLLAB_BOOTSTRAP_FROM_SQL: '1',
  })
  // 端口 0：由内核分配空闲端口，测试之间不会互相抢端口。
  return { ...config, port: 0, host: '127.0.0.1' }
}

interface UpgradeProbe {
  statusCode: number | undefined
  upgraded: boolean
}

/** 发一个 WebSocket 升级请求（不实现协议，只验证升级阶段的准入）。 */
function probeUpgrade(port: number, path: string): Promise<UpgradeProbe> {
  return new Promise((resolve, reject) => {
    const request = http.request({
      host: '127.0.0.1',
      port,
      path,
      headers: {
        Connection: 'Upgrade',
        Upgrade: 'websocket',
        'Sec-WebSocket-Key': Buffer.from('0123456789abcdef').toString('base64'),
        'Sec-WebSocket-Version': '13',
      },
    })
    const settle = (probe: UpgradeProbe) => {
      request.destroy()
      resolve(probe)
    }
    request.on('upgrade', (response, socket) => {
      socket.destroy()
      settle({ statusCode: response.statusCode, upgraded: true })
    })
    request.on('response', (response) => {
      response.resume()
      settle({ statusCode: response.statusCode, upgraded: false })
    })
    request.on('error', (error) => {
      // 路径被拒时服务端会写完 404 再断开，部分内核下客户端会先收到 ECONNRESET。
      const code = (error as NodeJS.ErrnoException).code
      if (code === 'ECONNRESET' || code === 'EPIPE') {
        resolve({ statusCode: undefined, upgraded: false })
        return
      }
      reject(error)
    })
    request.end()
  })
}

describe('协作边车 HTTP 面', () => {
  let handle: CollabServerHandle
  let store: MemoryStore
  let port: number

  before(async () => {
    store = new MemoryStore()
    handle = createCollabServer({
      config: testConfig(),
      logger: silentLogger(),
      docStore: store,
      materializeStore: store,
      quoteMaterializeStore: store,
      version: 'test-version',
    })
    await handle.start()
    port = handle.server.address.port
  })

  after(async () => {
    await handle.stop()
  })

  it('/healthz 返回连接数、房间数、物化队列长度', async () => {
    const response = await fetch(`http://127.0.0.1:${port}/healthz`)
    assert.equal(response.status, 200)
    assert.match(
      response.headers.get('content-type') ?? '',
      /application\/json/,
    )
    const payload = (await response.json()) as Record<string, unknown>

    assert.equal(payload.status, 'ok')
    assert.equal(payload.version, 'test-version')
    assert.equal(payload.connections, 0)
    assert.equal(payload.documents, 0)
    assert.deepEqual(payload.persistence, {
      pendingUpdateRooms: 0,
      pendingUpdates: 0,
      storedDocuments: 0,
      appendedUpdates: 0,
      bootstrapCount: 0,
      compactedRooms: 0,
      compactedUpdates: 0,
    })
    assert.equal(
      (payload.materializer as Record<string, unknown>).pendingCells,
      0,
    )
    assert.equal(payload.redis, 'disabled')
  })

  it('其它 HTTP 路径一律 404（本服务只提供 WS + 健康检查）', async () => {
    const root = await fetch(`http://127.0.0.1:${port}/`)
    assert.equal(root.status, 404)
    const random = await fetch(`http://127.0.0.1:${port}/api/v2/anything`)
    assert.equal(random.status, 404)
  })

  it('WebSocket 只允许 /collab 路径', async () => {
    const allowed = await probeUpgrade(port, '/collab')
    assert.equal(allowed.upgraded, true)

    const rejected = await probeUpgrade(port, '/socket')
    assert.equal(rejected.upgraded, false)
    assert.ok(
      rejected.statusCode === 404 || rejected.statusCode === undefined,
      `期望 404，实际 ${String(rejected.statusCode)}`,
    )
  })
})

describe('单用户连接数限制', () => {
  it('超过上限抛 4403，同 socket 重复计数不算新连接', () => {
    const registry = new ConnectionRegistry(2)
    registry.add('user-1', 'socket-a')
    registry.add('user-1', 'socket-a')
    registry.add('user-1', 'socket-b')

    assert.throws(
      () => registry.add('user-1', 'socket-c'),
      (error: unknown) =>
        error instanceof CollabAuthError &&
        error.kind === 'forbidden' &&
        error.code === 4403,
    )

    registry.remove('user-1', 'socket-a')
    registry.add('user-1', 'socket-c')
    assert.equal(registry.size(), 1)
  })
})
