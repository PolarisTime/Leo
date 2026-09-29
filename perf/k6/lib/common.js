/**
 * Leo ERP 压测公共库（k6）。
 *
 * 凭据与地址一律从环境变量读取，禁止写死在脚本里：
 *   LEO_PERF_BASE_URL    默认 http://127.0.0.1:11211/api
 *   LEO_PERF_LOGIN_NAME  登录名（必填）
 *   LEO_PERF_PASSWORD    密码（必填）
 *   LEO_PERF_RUN_ID      本次压测批次标识，用于标记与清理压测数据
 */
import http from 'k6/http';

export const BASE_URL = (__ENV.LEO_PERF_BASE_URL || 'http://127.0.0.1:11211/api').replace(/\/+$/, '');
export const LOGIN_NAME = __ENV.LEO_PERF_LOGIN_NAME || '';
export const PASSWORD = __ENV.LEO_PERF_PASSWORD || '';
export const RUN_ID = __ENV.LEO_PERF_RUN_ID || `run${Date.now()}`;

/** 写接口数据标记；压测产生的数据均可凭此前缀检索与清理。 */
export const DATA_MARKER = `PERF-LOAD-${RUN_ID}`;

/**
 * 生成唯一幂等键：所有写请求（含登录）都必须携带 X-Idempotency-Key。
 * `__VU`/`__ITER` 只在 VU 执行期存在，setup() 阶段引用会抛 ReferenceError，故做兼容。
 */
export function idempotencyKey(scope) {
  const vu = typeof __VU === 'undefined' ? 'setup' : __VU;
  const iter = typeof __ITER === 'undefined' ? 0 : __ITER;
  return `${RUN_ID}-${scope}-${vu}-${iter}-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

export function jsonHeaders(extra) {
  return Object.assign({ 'Content-Type': 'application/json' }, extra || {});
}

export function authHeaders(token, extra) {
  return Object.assign({ Authorization: `Bearer ${token}` }, extra || {});
}

/** access token 有效期 600s，提前 120s 主动续期，避免长压测中途 401。 */
export const TOKEN_TTL_MS = 480 * 1000;

let cachedToken = '';
let cachedAt = 0;

/**
 * 执行一次登录。
 *
 * 注意：`LoginService` 每次登录都会写 `sys_user.last_login_date`，该实体带 `@Version`
 * 乐观锁且失败不重试，因此同一账号并发登录必定大量返回 409（code 4090）。
 * 所以读/写压测一律在 setup() 阶段串行登录一次并共享 token，避免把登录争用
 * 混进读接口的测量结果；登录并发本身由 06-login.js 专项测量。
 */
export function loginNow(scope) {
  if (!LOGIN_NAME || !PASSWORD) {
    throw new Error('缺少 LEO_PERF_LOGIN_NAME / LEO_PERF_PASSWORD 环境变量');
  }
  const res = http.post(
    `${BASE_URL}/v2.0/auth/login`,
    JSON.stringify({ loginName: LOGIN_NAME, password: PASSWORD }),
    {
      headers: jsonHeaders({ 'X-Idempotency-Key': idempotencyKey(scope || 'login') }),
      tags: { name: 'POST /v2.0/auth/login' },
    }
  );
  if (res.status !== 200) {
    return '';
  }
  const body = res.json();
  return body && body.accessToken ? body.accessToken : '';
}

/** 在 setup() 中串行登录一次，返回 {token, issuedAt} 供各 VU 共享。 */
export function setupToken() {
  const token = loginNow('setup');
  return { token, issuedAt: Date.now() };
}

/**
 * 取可用 token：优先用 setup() 共享的 token；超过 TTL 才由本 VU 兜底重登。
 * 兜底重登可能撞上并发登录争用，因此单次压测时长应控制在 TTL 之内。
 */
export function ensureToken(shared) {
  const now = Date.now();
  if (shared && shared.token && now - shared.issuedAt < TOKEN_TTL_MS) {
    return shared.token;
  }
  if (cachedToken && now - cachedAt < TOKEN_TTL_MS) {
    return cachedToken;
  }
  cachedToken = loginNow('relogin');
  cachedAt = Date.now();
  return cachedToken;
}

/**
 * 构造读请求参数。
 * 注意：k6 只识别 params.headers，认证头必须放在 headers 下，直接平铺到 params 上不会生效。
 */
export function readParams(token, name, anonymous) {
  const params = { tags: { name, kind: 'read' } };
  if (!anonymous && token) {
    params.headers = { Authorization: `Bearer ${token}` };
  }
  return params;
}

/** 构造写请求参数（自动带认证头、幂等键与 JSON Content-Type）。 */
export function writeParams(token, name, extraHeaders) {
  const headers = Object.assign(
    {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      'X-Idempotency-Key': idempotencyKey('write'),
    },
    extraHeaders || {}
  );
  return { headers, tags: { name, kind: 'write' } };
}

/** 读接口清单：按真实前端调用特征取样，weight 用于混合压测的流量配比。 */
export const READ_ENDPOINTS = [
  { name: 'GET /v2.0/sales-orders', path: '/v2.0/sales-orders?page=0&size=30', weight: 22 },
  { name: 'GET /v2.0/sales-orders?size=5', path: '/v2.0/sales-orders?page=0&size=5', weight: 8 },
  { name: 'GET /v2.0/customers', path: '/v2.0/customers?page=0&size=30', weight: 14 },
  { name: 'GET /v2.0/materials', path: '/v2.0/materials?page=0&size=30', weight: 14 },
  { name: 'GET /v2.0/suppliers', path: '/v2.0/suppliers?page=0&size=30', weight: 8 },
  { name: 'GET /v2.0/global-search', path: `/v2.0/global-search?keyword=${encodeURIComponent('浙江景华建设有限公司')}&limit=20`, weight: 12 },
  { name: 'GET /v2.0/company-settings', path: '/v2.0/company-settings?page=0&size=5', weight: 6 },
  { name: 'GET /v2.0/account', path: '/v2.0/account', weight: 6 },
  { name: 'GET /v2.0/users', path: '/v2.0/users?page=0&size=30', weight: 5 },
  { name: 'GET /v2.0/health', path: '/v2.0/health', weight: 5, anonymous: true },
];

const TOTAL_WEIGHT = READ_ENDPOINTS.reduce((sum, item) => sum + item.weight, 0);

/** 按权重随机挑一个读接口。 */
export function pickReadEndpoint() {
  let roll = Math.random() * TOTAL_WEIGHT;
  for (const endpoint of READ_ENDPOINTS) {
    roll -= endpoint.weight;
    if (roll <= 0) {
      return endpoint;
    }
  }
  return READ_ENDPOINTS[0];
}

/** 统一的 k6 结果统计口径。 */
export const SUMMARY_TREND_STATS = ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'];

/** k6 自定义指标名只允许字母/数字/下划线，把接口名规范化为安全标识。 */
export function safeMetricName(prefix, name) {
  const safe = name.replace(/[^A-Za-z0-9]+/g, '_').replace(/^_+|_+$/g, '');
  return `${prefix}_${safe}`;
}

/** 读压测默认阈值：错误率与延迟上限，用于自动判定是否达标。 */
export function readThresholds() {
  return {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{kind:read}': ['p(95)<800', 'p(99)<2000'],
  };
}
