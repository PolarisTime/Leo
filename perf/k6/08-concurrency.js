/**
 * 并发正确性专项（第 1 项缺口）：同一行/同一单据的并发写与幂等重放。
 *
 * 背景：登录接口已实测出「同一行并发写」缺陷——LoginService 每次登录都写
 * sys_user.last_login_date，该实体带 @Version 乐观锁且冲突后不重试，
 * 同一账号 20 并发登录有 92% 返回 409。本项目全局使用乐观锁，因此需要
 * 系统性确认其它写路径是否同样脆弱。本脚本测量三类竞态：
 *
 *   A) 同一角色权限并发替换：N 个请求同时 PUT 同一角色的权限集合
 *   B) 幂等键并发重放：N 个请求同时用同一个 X-Idempotency-Key 创建资源，
 *      正确语义应为「仅一次生效」，其余返回重放结果或明确的进行中冲突
 *   C) 同一客户编码并发创建：唯一性约束下的竞态
 *
 * 本脚本只测量并输出状态码分布，不自动断言业务正确性——业务层
 * 「最终只创建了一条」需由 harness 侧查询核对（见 perf/README.md 说明）。
 *
 * 用法：
 *   source tmp/perf/creds.env && LEO_PERF_COMPANY_ID=<id> k6 run leo/perf/k6/08-concurrency.js
 */
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import {
  BASE_URL,
  COMPANY_ID,
  DATA_MARKER,
  ensureToken,
  readParams,
  setupToken,
  SUMMARY_TREND_STATS,
  writeParams,
} from './lib/common.js';

const raceVus = Number(__ENV.LEO_PERF_RACE_VUS || 10);

const roleStatus = new Counter('race_role_status');
const roleConflict = new Counter('race_role_conflict_409');
const roleOk = new Counter('race_role_ok_200');
const roleDuration = new Trend('race_role_duration', true);

const replayStatus = new Counter('race_replay_status');
const replayCreated = new Counter('race_replay_created_201');
const replayOther = new Counter('race_replay_other');
const replayDuration = new Trend('race_replay_duration', true);

const codeStatus = new Counter('race_code_status');

export const options = {
  scenarios: {
    // A) 同一角色权限并发替换：持续并发写同一行，观察乐观锁冲突比例
    same_role_write: {
      executor: 'constant-vus',
      vus: raceVus,
      duration: __ENV.LEO_PERF_RACE_DURATION || '20s',
      exec: 'sameRoleWrite',
    },
    // B) 幂等键并发重放：raceVus 个 VU 同时、仅一次，共用同一个幂等键
    idempotency_replay: {
      executor: 'per-vu-iterations',
      vus: raceVus,
      iterations: 1,
      exec: 'idempotencyReplay',
      startTime: '1s',
    },
    // C) 同一客户编码并发创建：唯一性约束竞态
    same_code_create: {
      executor: 'per-vu-iterations',
      vus: raceVus,
      iterations: 1,
      exec: 'sameCodeCreate',
      startTime: '25s',
    },
  },
  summaryTrendStats: SUMMARY_TREND_STATS,
};

export function setup() {
  const base = setupToken();
  const token = base.token;

  // 关键安全考虑：绝不拿 SUPER_ADMIN 做并发写靶子。
  // 全库仅有一个内置角色，若并发写在它身上产生部分写入，管理员将失去 `*` 通配权限，
  // 导致连修复接口都调不动（被 403 拦死）。因此先创建一个专用测试角色，只打它。
  const roleCode = `PERF-RACE-${__ENV.LEO_PERF_RUN_ID || Date.now()}`;
  let roleId = '';
  const created = http.post(
    `${BASE_URL}/v2.0/roles`,
    JSON.stringify({ code: roleCode, name: '压测并发专项角色', description: 'perf race probe', status: '正常' }),
    writeParams(token, 'setup:POST /v2.0/roles')
  );
  if (created.status === 201 || created.status === 200) {
    roleId = String(created.json().id);
  }
  if (!roleId) {
    throw new Error(`setup 未能创建测试角色（status=${created.status}），无法执行并发写专项`);
  }
  // 先给该角色一个已知的最小权限集，后续并发写的都是这个集合
  const permissions = ['roles:read'];
  const seeded = http.put(
    `${BASE_URL}/v2.0/roles/${roleId}/permissions`,
    JSON.stringify({ permissions }),
    writeParams(token, 'setup:PUT /v2.0/roles/{id}/permissions')
  );
  if (seeded.status !== 200) {
    throw new Error(`setup 未能初始化测试角色权限（status=${seeded.status}）`);
  }
  console.log(`并发写靶子角色: id=${roleId} code=${roleCode}`);

  // 为幂等重放准备一个已签发的客户编码（同一编码被并发使用即为竞态）
  // 签发两个互不相同的编码：编码是「一次性消耗」的（首次创建成功后即失效），
  // 若两个场景共用同一个编码，后跑的场景只会得到 422「编码未由系统签发或已失效」，
  // 唯一性竞态根本不会被触发。
  const issue = () => {
    const res = http.post(
      `${BASE_URL}/v2.0/master-data/code-issuances/customer`,
      null,
      writeParams(token, 'setup:POST /v2.0/master-data/code-issuances/customer')
    );
    return res.status === 201 ? res.json().code : '';
  };
  const replayCode = issue();
  const raceCode = issue();
  if (!replayCode || !raceCode) {
    throw new Error(`setup 未能签发足够的客户编码（replay=${replayCode}, race=${raceCode}）`);
  }

  return {
    token,
    issuedAt: base.issuedAt,
    roleId,
    permissions,
    replayCode,
    raceCode,
  };
}

/** A) 同一角色权限并发替换 */
export function sameRoleWrite(data) {
  const token = ensureToken(data);
  if (!token) {
    return;
  }
  const res = http.put(
    `${BASE_URL}/v2.0/roles/${data.roleId}/permissions`,
    JSON.stringify({ permissions: data.permissions }),
    writeParams(token, 'PUT /v2.0/roles/{id}/permissions')
  );
  roleStatus.add(1, { http_status: String(res.status) });
  roleDuration.add(res.timings.duration);
  if (res.status === 200) {
    roleOk.add(1);
  }
  if (res.status === 409) {
    roleConflict.add(1);
  }
  check(res, { '权限替换返回 200 或 409': (r) => r.status === 200 || r.status === 409 });
}

/** B) 幂等键并发重放：所有 VU 共用同一个幂等键 */
export function idempotencyReplay(data) {
  const token = ensureToken(data);
  if (!token) {
    return;
  }
  if (!data.replayCode) {
    return;
  }
  // 所有 VU 使用同一个键 => 构成并发重放
  const sharedKey = `${__ENV.LEO_PERF_RUN_ID || 'race'}-shared-replay-key`;
  const res = http.post(
    `${BASE_URL}/v2.0/customers`,
    JSON.stringify({
      customerCode: data.replayCode,
      customerName: `${DATA_MARKER}-REPLAY`,
      defaultSettlementCompanyId: COMPANY_ID,
      status: '正常',
      remark: 'perf idempotency replay probe, safe to delete',
    }),
    {
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
        'X-Idempotency-Key': sharedKey,
      },
      tags: { name: 'POST /v2.0/customers (replay)', kind: 'write' },
    }
  );
  replayStatus.add(1, { http_status: String(res.status) });
  replayDuration.add(res.timings.duration);
  if (res.status === 201) {
    replayCreated.add(1);
  } else {
    replayOther.add(1);
  }
}

/** C) 同一客户编码并发创建 */
export function sameCodeCreate(data) {
  const token = ensureToken(data);
  if (!token || !data.raceCode) {
    return;
  }
  const res = http.post(
    `${BASE_URL}/v2.0/customers`,
    JSON.stringify({
      customerCode: data.raceCode,
      customerName: `${DATA_MARKER}-SAMECODE`,
      defaultSettlementCompanyId: COMPANY_ID,
      status: '正常',
      remark: 'perf same-code race probe, safe to delete',
    }),
    writeParams(token, 'POST /v2.0/customers (same code)')
  );
  codeStatus.add(1, { http_status: String(res.status) });
}

/** 幂等键工具在本脚本中通过 writeParams 间接使用，显式引用避免误报未使用 */
