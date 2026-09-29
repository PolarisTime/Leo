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

const docStatus = new Counter('race_doc_status');
const docOk = new Counter('race_doc_ok_200');
const docConflict = new Counter('race_doc_conflict_409');
const docDuration = new Trend('race_doc_duration', true);

// 非预期状态码计数器（401/403/5xx 等）：本脚本的 409/422 是**被测现象**，
// 不能当失败；但 401（会话失效）或 5xx（服务端故障）会直接污染并发结论，
// 必须让整个脚本明确失败。没有这个闸门时，一次会话吊销级联造成的 86.77% 401
// 会被误读成「并发冲突率极高」。
const unexpectedStatus = new Counter('race_unexpected_status');
const EXPECTED = [200, 201, 409, 422];
const recordUnexpected = (res) => {
  if (!EXPECTED.includes(res.status)) {
    unexpectedStatus.add(1, { http_status: String(res.status) });
  }
};

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
    // D) 同一**单据**并发整体替换：销售订单带 @Version 乐观锁，且单据有明细行，
    //    是「同一行并发写」之外的另一类风险——若并发写产生部分写入，
    //    单据可能出现重复/缺失明细行，而不只是版本冲突。
    document_write: {
      executor: 'constant-vus',
      vus: raceVus,
      duration: __ENV.LEO_PERF_RACE_DOC_DURATION || '20s',
      exec: 'documentWrite',
      startTime: '26s',
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
  thresholds: {
    // 409/422 是本脚本要测量的竞态现象，不计入失败；但任何一个非预期状态
    // （401 会话失效、403 权限、5xx 服务端故障）都说明这次运行不可用于结论。
    race_unexpected_status: ['count==0'],
  },
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

  // ---- 场景 D：同一**单据**并发整体替换的靶子 ----
  // 为什么要有这一条：A 场景写的是「角色权限」这类单行记录，而单据（销售订单）带 @Version
  // 乐观锁**且有明细行**——并发整体替换若产生部分写入，风险不只是版本冲突，而是
  // 明细行重复或丢失。dev 库里可写的草稿单据很少（其余草稿已被 E2E 软删，PUT 会 404），
  // 因此这里先找、再预检，找不到就直接失败，避免把「单据不可写」误读成「并发冲突」。
  let draftOrderId = '';
  let draftPutBody = '';
  let draftPutBodyTemplate = '';
  let draftOriginalRemark = '';
  const listRes = http.get(
    `${BASE_URL}/v2.0/sales-orders?page=0&size=100`,
    readParams(token, 'setup:GET /v2.0/sales-orders (draft hunt)', false)
  );
  if (listRes.status === 200) {
    const listBody = listRes.json();
    const rows = listBody.content || listBody || [];
    for (const row of rows) {
      if (row.status === '草稿') {
        draftOrderId = String(row.id);
        break;
      }
    }
  }
  if (!draftOrderId) {
    throw new Error('未找到状态为「草稿」的销售订单，无法执行同一单据并发写专项');
  }
  const detailRes = http.get(
    `${BASE_URL}/v2.0/sales-orders/${draftOrderId}`,
    readParams(token, 'setup:GET /v2.0/sales-orders/{id}', false)
  );
  if (detailRes.status !== 200) {
    throw new Error(`读取目标单据失败（status=${detailRes.status}）`);
  }
  const doc = detailRes.json();
  draftOriginalRemark = doc.remark || '';
  const idStr = (v) => (v === null || v === undefined ? null : String(v));
  // PUT 是整体替换：必须原样回填全部业务字段，雪花 ID 一律用十进制字符串
  // （发数字会被 Jackson 拒绝为 400），只改 remark 作为本次运行的标记。
  draftPutBody = JSON.stringify({
    orderNo: doc.orderNo,
    purchaseInboundNo: doc.purchaseInboundNo || null,
    purchaseOrderNo: doc.purchaseOrderNo || null,
    customerId: idStr(doc.customerId),
    customerCode: doc.customerCode,
    customerName: doc.customerName,
    projectId: idStr(doc.projectId),
    projectName: doc.projectName,
    settlementCompanyId: idStr(doc.settlementCompanyId),
    settlementCompanyName: doc.settlementCompanyName,
    deliveryDate: doc.deliveryDate,
    salesName: doc.salesName,
    status: doc.status,
    // remark 由每个请求替换为**互不相同**的值：见 documentWrite 里的说明。
    remark: '__REMARK__',
    priceRuleId: idStr(doc.priceRuleId),
    items: (doc.items || []).map((i) => ({
      id: idStr(i.id),
      materialId: idStr(i.materialId),
      materialCode: i.materialCode,
      brand: i.brand,
      category: i.category,
      material: i.material,
      spec: i.spec,
      length: i.length,
      unit: i.unit,
      sourceInboundItemId: idStr(i.sourceInboundItemId),
      sourcePurchaseOrderItemId: idStr(i.sourcePurchaseOrderItemId),
      warehouseId: idStr(i.warehouseId),
      warehouseName: i.warehouseName,
      batchNo: i.batchNo || null,
      quantity: i.quantity,
      quantityUnit: i.quantityUnit,
      pieceWeightTon: Number(i.pieceWeightTon || 0),
      piecesPerBundle: i.piecesPerBundle || 0,
      weightTon: Number(i.weightTon || 0),
      unitPrice: Number(i.unitPrice || 0),
    })),
    chargeItems: [],
    audit: false,
  });
  // 预检：先串行写一次，确认该单据当前可写。失败即中止——否则本场景测到的是业务规则。
  const probeDoc = http.put(
    `${BASE_URL}/v2.0/sales-orders/${draftOrderId}`,
    draftPutBody.replace('__REMARK__', `${DATA_MARKER}-DOC-PROBE`),
    writeParams(token, 'setup:PUT /v2.0/sales-orders/{id}')
  );
  // 并发写必须**内容互不相同**：实测发现完全相同的 PUT 会被服务端当作无变化而短路，
  // 596 次并发 PUT 全部返回 200 但版本号一次都没有增长——那不是并发冲突，
  // 而是「空写被优化掉」。用唯一 remark 才能让每次请求都构成真实写入。
  draftPutBodyTemplate = draftPutBody;
  if (probeDoc.status !== 200) {
    throw new Error(`目标单据不可写（status=${probeDoc.status}），同一单据并发写专项无法进行`);
  }
  console.log(
    `同一单据并发写目标: id=${draftOrderId} 原 remark="${draftOriginalRemark}" 预检=200`
  );

  // 必须带上 expiresAt（直接展开 base）：漏传会让 ensureToken 认为共享 token 恒失效，
  // 每个 VU 各自重登 → 触发服务端会话上限（3）的吊销级联 → 大面积 401 污染并发结论。
  return Object.assign({}, base, {
    token,
    roleId,
    permissions,
    replayCode,
    raceCode,
    draftOrderId,
    draftPutBodyTemplate,
    draftOriginalRemark,
  });
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
  recordUnexpected(res);
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
  recordUnexpected(res);
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
  recordUnexpected(res);
  codeStatus.add(1, { http_status: String(res.status) });
}

/** 幂等键工具在本脚本中通过 writeParams 间接使用，显式引用避免误报未使用 */

/** D) 同一单据（销售订单）并发整体替换 */
export function documentWrite(data) {
  const token = ensureToken(data);
  if (!token || !data.draftOrderId) {
    return;
  }
  const body = data.draftPutBodyTemplate.replace('__REMARK__', `race-doc-${__VU}-${__ITER}`);
  const res = http.put(
    `${BASE_URL}/v2.0/sales-orders/${data.draftOrderId}`,
    body,
    writeParams(token, 'PUT /v2.0/sales-orders/{id}')
  );
  recordUnexpected(res);
  docStatus.add(1, { http_status: String(res.status) });
  docDuration.add(res.timings.duration);
  if (res.status === 200) {
    docOk.add(1);
  }
  if (res.status === 409) {
    docConflict.add(1);
  }
  check(res, { '单据替换返回 200 或 409': (r) => r.status === 200 || r.status === 409 });
}
