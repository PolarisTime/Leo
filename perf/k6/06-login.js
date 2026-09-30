/**
 * 登录专项压测：量化 POST /v2.0/auth/login 在并发下的表现。
 *
 * 背景：LoginService 原先每次登录都写 sys_user.last_login_date，该实体带 @Version 乐观锁
 * 且失败不重试，因此**同一账号**并发登录会大量返回 409（code 4090）。
 *
 * 2026-09-30 实测基线（修复前，20 并发）：仅 **5% 成功**、92% 409、3% 因连接池耗尽 500；
 * 失控跑还出现过 7,902 次连接池获取超时、整体失败率 86.77%。
 * 修复（`last_login_date` 改原子 UPDATE + 按间隔节流）后，本脚本的阈值要求
 * **成功率 > 99%、409 占比 < 1%**——冲突从「预期现象」变成「回归信号」。
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
 *
 * 注意：同一账号的会话数默认上限为 3，并发登录会不断吊销旧会话（这是预期行为，
 * 不影响登录本身成功）。若要让服务账号不受上限影响，可配置
 * leo.auth.session.exempt-login-names（见 application.yml）。
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
  thresholds: {
    // 修复后同一账号并发登录不应再产生乐观锁冲突；一旦 409 回来，说明
    // 「登录写用户行」的老问题以别的形式回归了（例如又有人给 UserAccount 加了写入）。
    login_success: ['rate>0.99'],
    login_conflict_409: ['rate<0.01'],
    login_other_failure: ['rate<0.01'],
  },
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

  // 只接受 200：修复前 409 是「预期现象」所以被容忍，现在它必须让脚本失败，
  // 否则这个专项会在冲突回归时依然报「通过」。
  check(res, {
    '登录返回 200': (r) => r.status === 200,
  });
}
