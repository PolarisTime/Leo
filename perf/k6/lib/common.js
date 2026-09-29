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
import encoding from 'k6/encoding';
import { sleep } from 'k6';

export const BASE_URL = (__ENV.LEO_PERF_BASE_URL || 'http://127.0.0.1:11211/api').replace(/\/+$/, '');
export const LOGIN_NAME = __ENV.LEO_PERF_LOGIN_NAME || '';
export const PASSWORD = __ENV.LEO_PERF_PASSWORD || '';
export const RUN_ID = __ENV.LEO_PERF_RUN_ID || `run${Date.now()}`;
/** 结算主体 ID：由 run.sh 统一解析后注入，用于财务/对账等需该参数的接口。 */
export const COMPANY_ID = __ENV.LEO_PERF_COMPANY_ID || '';

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

/**
 * token 提前失效的安全边界。真实有效期从 JWT 的 exp 声明读取，不再硬编码期限，
 * 避免「有效期假设」与压测时长不匹配。
 */
export const TOKEN_SAFETY_MARGIN_MS = 60 * 1000;

/** 解析 k6 时长字符串（如 2h / 90m / 30s）为毫秒。 */
export function parseDurationMs(value) {
  const m = String(value || '').trim().match(/^(\d+)(ms|s|m|h)$/);
  if (!m) {
    throw new Error(`无法解析时长: ${value}（支持 30s / 90m / 2h）`);
  }
  const n = Number(m[1]);
  const unit = m[2];
  const factor = unit === 'ms' ? 1 : unit === 's' ? 1000 : unit === 'm' ? 60000 : 3600000;
  return n * factor;
}

/** 解析 JWT 的 exp 声明（毫秒）。解析失败返回 0，调用方据此拒绝继续。 */
export function jwtExpiryMs(token) {
  try {
    const parts = String(token).split('.');
    if (parts.length < 2) {
      return 0;
    }
    const payload = JSON.parse(encoding.b64decode(parts[1], 'rawurl', 's'));
    return payload && payload.exp ? payload.exp * 1000 : 0;
  } catch (e) {
    return 0;
  }
}

/**
 * 校验 token 是否足以覆盖本次计划的运行时长。
 *
 * 这是补上一个真实事故的防护：access token 有效期为 600s 且不可配置，
 * 而 soak 计划跑 2 小时。原先按硬编码 480s 判断「是否该续期」，
 * 导致 8 分钟后全部 VU 转去重登，撞上会话数上限（3）后互相吊销，
 * 最终形成登录风暴（739 次连接池获取超时），而压测本身已不产生有效负载。
 * 有了这个校验，同类配置错误会在开跑前直接失败，而不是跑完 2 小时才发现。
 */
export function assertTokenCoversRun(token, plannedMs, label) {
  const exp = jwtExpiryMs(token);
  if (!exp) {
    throw new Error(`${label}: 无法解析 token 有效期，拒绝以不确定的凭据启动长时压测`);
  }
  const remaining = exp - Date.now();
  if (remaining < plannedMs + TOKEN_SAFETY_MARGIN_MS) {
    throw new Error(
      `${label}: token 剩余有效期仅 ${Math.round(remaining / 1000)}s，`
      + `不足以覆盖计划的 ${Math.round(plannedMs / 1000)}s（含 ${TOKEN_SAFETY_MARGIN_MS / 1000}s 安全边界）。`
      + '请缩短分段时长，或改用分段方式（见 perf/soak-runner.sh）。'
    );
  }
  return remaining;
}

let cachedToken = '';
let cachedAt = 0;
let cachedExpiresAt = 0;
let lastFailedLoginAt = 0;

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
  // 优先复用 run.sh 提供的共享 token：服务端对同一账号有会话数上限（默认 3），
  // 任何多余登录都会吊销既有会话并使其 401，因此会话必须全局唯一。
  const provided = __ENV.LEO_PERF_TOKEN;
  if (provided) {
    return { token: provided, issuedAt: Date.now(), expiresAt: jwtExpiryMs(provided) };
  }
  // 必须重试：同一账号并发登录会因 sys_user 乐观锁返回 409（已实测），
  // 而 setup 只跑一次——一旦这里拿不到 token，所有 VU 会退化成
  // 「每次迭代都重登」，直接把被测系统打成登录风暴并耗尽连接池。
  for (let attempt = 1; attempt <= 8; attempt++) {
    const token = loginNow(`setup-${attempt}`);
    if (token) {
      return { token, issuedAt: Date.now(), expiresAt: jwtExpiryMs(token) };
    }
    sleep(1);
  }
  throw new Error('setup 登录失败：同一账号可能存在并发登录竞争，请确认无其他并发登录后重试');
}

/**
 * 取可用 token：优先用 setup() 共享的 token；超过 TTL 才由本 VU 兜底重登。
 * 兜底重登可能撞上并发登录争用，因此单次压测时长应控制在 TTL 之内。
 */
export function ensureToken(shared) {
  const now = Date.now();
  // 以 JWT 真实 exp 判断有效性（留安全边界），而非硬编码期限。
  //
  // shared.expiresAt 缺失时必须回退为**直接解析 token 的 exp**，不能当成 0：
  // 07-heavy / 08-concurrency 的 setup() 曾只回传 {token, issuedAt} 而漏掉 expiresAt，
  // 于是 `now < (undefined||0) - 60000` 恒为假 → 每个 VU 都各自重登一次。
  // 服务端同账号会话上限硬编码为 3，多余登录会吊销并拉黑既有会话，
  // 实测因此产生 86.77% 的 401（346,686 请求里 339,955 失败），而 business 日志里
  // 几乎看不到异常，极易被误读成「接口本身故障」。这里做双保险，避免再次踩坑。
  const sharedExpiresAt = shared && shared.token
    ? (shared.expiresAt || jwtExpiryMs(shared.token))
    : 0;
  if (shared && shared.token && now < sharedExpiresAt - TOKEN_SAFETY_MARGIN_MS) {
    return shared.token;
  }
  if (cachedToken && now < cachedExpiresAt - TOKEN_SAFETY_MARGIN_MS) {
    return cachedToken;
  }
  // 重登失败时退避 2s，避免失败被放大成「每迭代一次登录」的风暴
  if (now - lastFailedLoginAt < 2000) {
    sleep(1);   // 必须 sleep：否则拿不到 token 时迭代不产生请求，k6 会以数万次/秒空转，
                // 「迭代数」将完全失真（实测空转到 2741 万次，压测实际已停摆）
    return '';
  }
  cachedToken = loginNow('relogin');
  cachedAt = Date.now();
  cachedExpiresAt = jwtExpiryMs(cachedToken);
  if (!cachedToken) {
    lastFailedLoginAt = Date.now();
  }
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

/**
 * 读接口清单：按真实前端调用特征取样，weight 用于混合压测的流量配比。
 *
 * 路径中的 {companyId} 占位由 endpointPath() 用 COMPANY_ID 替换。
 * 重负载接口（库存流水/财务概览/资金台账/对账）已纳入，用于覆盖此前的盲区。
 */

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
  // ---- 重负载业务接口（此前完全未覆盖）----
  { name: 'GET /v2.0/inventory/balances', path: '/v2.0/inventory/balances?page=0&size=30', weight: 8 },
  { name: 'GET /v2.0/inventory/transactions', path: '/v2.0/inventory/transactions?page=0&size=30', weight: 8 },
  { name: 'GET /v2.0/dashboard/summary', path: '/v2.0/dashboard/summary', weight: 5 },
  { name: 'GET /v2.0/sales-outbounds', path: '/v2.0/sales-outbounds?page=0&size=30', weight: 6 },
  { name: 'GET /v2.0/purchase-orders', path: '/v2.0/purchase-orders?page=0&size=30', weight: 6 },
  { name: 'GET /v2.0/receipts', path: '/v2.0/receipts?page=0&size=30', weight: 5 },
  { name: 'GET /v2.0/finance/overview', path: '/v2.0/finance/overview?settlementCompanyId={companyId}&page=0&size=30', weight: 8 },
  { name: 'GET /v2.0/cash-ledger', path: '/v2.0/cash-ledger?settlementCompanyId={companyId}&page=0&size=30', weight: 6 },
  { name: 'GET /v2.0/customer-statements', path: '/v2.0/customer-statements?settlementCompanyId={companyId}&page=0&size=30', weight: 5 },
  { name: 'GET /v2.0/materials/grades', path: '/v2.0/materials/grades', weight: 5 },
  { name: 'GET /v2.0/materials/brands', path: '/v2.0/materials/brands', weight: 5 },
];

const TOTAL_WEIGHT = READ_ENDPOINTS.reduce((sum, item) => sum + item.weight, 0);

/** 把路径中的 {companyId} 占位替换为真实结算主体 ID。 */
export function endpointPath(endpoint, companyId) {
  return endpoint.path.replace('{companyId}', companyId || COMPANY_ID);
}

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
