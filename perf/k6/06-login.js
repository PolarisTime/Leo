/**
 * 登录专项压测：量化 POST /v2.0/auth/login 在并发下的表现。
 *
 * 背景：LoginService 每次登录都会写 sys_user.last_login_date，该实体带 @Version 乐观锁
 * 且失败不重试，因此**同一账号**并发登录会大量返回 409（code 4090）。本脚本把这个
 * 行为显式测量出来，并支持传入多账号池以区分「单账号争用」与「多用户真实登录吞吐」。
 *
 * 用法：
 *   # 单账号（测同一账号并发争用）
 *   source tmp/perf/creds.env && k6 run leo/perf/k6/06-login.js
 *
 *   # 多账号池（测真实多用户登录吞吐；格式 账号:密码,账号:密码）
 *   LEO_PERF_LOGIN_POOL='u1:p1,u2:p2,u3:p3' k6 run leo/perf/k6/06-login.js
 *
 * 环境变量：
 *   LEO_PERF_LOGIN_VUS        并发上限，默认 20
 *   LEO_PERF_LOGIN_ITERATIONS 每 VU 轮次，默认 10
 */
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import {
  BASE_URL,
  LOGIN_NAME,
  PASSWORD,
  idempotencyKey,
  jsonHeaders,
  SUMMARY_TREND_STATS,
} from './lib/common.js';

const vus = Number(__ENV.LEO_PERF_LOGIN_VUS || 20);
const iterations = Number(__ENV.LEO_PERF_LOGIN_ITERATIONS || 10);

/**
 * 账号池：优先用 LEO_PERF_LOGIN_POOL，否则退化为单账号。
 * 在 setup() 中调用，避免 init 阶段就因缺少凭据而加载失败。
 */
function parseAccounts() {
  const pool = __ENV.LEO_PERF_LOGIN_POOL || '';
  if (pool.trim()) {
    return pool.split(',').map((pair) => {
      const idx = pair.indexOf(':');
      return { loginName: pair.slice(0, idx).trim(), password: pair.slice(idx + 1).trim() };
    }).filter((a) => a.loginName && a.password);
  }
  if (!LOGIN_NAME || !PASSWORD) {
    throw new Error('缺少 LEO_PERF_LOGIN_NAME / LEO_PERF_PASSWORD，或设置 LEO_PERF_LOGIN_POOL');
  }
  return [{ loginName: LOGIN_NAME, password: PASSWORD }];
}

const loginSuccess = new Rate('login_success');
const loginConflict = new Rate('login_conflict_409');
const loginOtherFail = new Rate('login_other_failure');
const loginDuration = new Trend('login_duration', true);
const conflictCounter = new Counter('login_409_count');

export const options = {
  scenarios: {
    login: {
      executor: 'per-vu-iterations',
      vus,
      iterations,
      maxDuration: __ENV.LEO_PERF_LOGIN_MAX_DURATION || '5m',
    },
  },
  summaryTrendStats: SUMMARY_TREND_STATS,
};

export function setup() {
  const accounts = parseAccounts();
  const unique = new Set(accounts.map((a) => a.loginName)).size;
  console.log(`登录压测账号数=${unique}，并发=${vus}，每 VU 轮次=${iterations}`);
  return { accounts };
}

export default function (data) {
  const accounts = data.accounts;
  // 每个 VU 固定用一个账号：账号数 < VU 数时即形成同一账号并发争用
  const account = accounts[(__VU - 1) % accounts.length];
  const res = http.post(
    `${BASE_URL}/v2.0/auth/login`,
    JSON.stringify({ loginName: account.loginName, password: account.password }),
    {
      headers: jsonHeaders({ 'X-Idempotency-Key': idempotencyKey('login-load') }),
      tags: { name: 'POST /v2.0/auth/login', kind: 'auth' },
    }
  );
  loginDuration.add(res.timings.duration);

  const isSuccess = res.status === 200;
  loginSuccess.add(isSuccess);
  loginConflict.add(res.status === 409);
  loginOtherFail.add(!isSuccess && res.status !== 409);
  if (res.status === 409) {
    conflictCounter.add(1);
  }

  check(res, {
    '登录返回 200 或 409(乐观锁冲突)': (r) => r.status === 200 || r.status === 409,
  });
}
