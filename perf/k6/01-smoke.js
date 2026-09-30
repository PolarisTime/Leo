/**
 * 压测前置冒烟：1 VU、1 轮，验证登录、读接口、写接口全链路可用。
 * 写链路自带清理（创建后立即删除），不留残余数据。
 *
 * 用法：
 *   source tmp/perf/creds.env && k6 run leo/perf/k6/01-smoke.js
 */
import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';
import {
  BASE_URL,
  COMPANY_ID,
  DATA_MARKER,
  endpointPath,
  ensureToken,
  setupToken,
  READ_ENDPOINTS,
  readParams,
  SUMMARY_TREND_STATS,
  writeParams,
} from './lib/common.js';

const writeChain = new Trend('write_chain_duration', true);

export const options = {
  vus: 1,
  iterations: 1,
  summaryTrendStats: SUMMARY_TREND_STATS,
};

/**
 * 解析结算主体 ID：优先环境变量 LEO_PERF_COMPANY_ID（run.sh 已解析），
 * 缺失时经 company-settings 取首个——smoke 是前置验证脚本，必须能脱离
 * run.sh 独立跑（README 的直接 k6 用法）。
 *
 * 教训：财务/对账三个读接口带必填 settlementCompanyId，早前本脚本直接拼
 * endpoint.path，把字面量 {companyId} 发了出去，导致这三个检查必然 400，
 * 看起来像「财务接口故障」，其实是冒烟脚本自己的占位符没替换。
 */
function resolveCompanyId(token) {
  if (COMPANY_ID) {
    return COMPANY_ID;
  }
  const res = http.get(
    `${BASE_URL}/v2.0/company-settings?page=0&size=5`,
    readParams(token, 'GET /v2.0/company-settings')
  );
  if (res.status !== 200) {
    return '';
  }
  const body = res.json();
  const first = (Array.isArray(body) ? body : (body.content || []))[0];
  return first && first.id ? String(first.id) : '';
}

/** 串行登录一次并共享 token：避免各 VU 启动时的并发登录争用污染测量结果。 */
export function setup() {
  const shared = setupToken();
  shared.companyId = resolveCompanyId(shared.token);
  return shared;
}

export default function (data) {
  const token = ensureToken(data);
  check(token, { '登录取到 accessToken': (value) => typeof value === 'string' && value.length > 0 });
  if (!token) {
    return;
  }

  for (const endpoint of READ_ENDPOINTS) {
    const res = http.get(
      `${BASE_URL}${endpointPath(endpoint, data && data.companyId)}`,
      readParams(token, endpoint.name, endpoint.anonymous)
    );
    check(res, { [`${endpoint.name} 返回 200`]: (r) => r.status === 200 });
  }

  // 写链路：签发客户编码 -> 创建客户 -> 删除清理
  const started = Date.now();
  const issued = http.post(
    `${BASE_URL}/v2.0/master-data/code-issuances/customer`,
    null,
    writeParams(token, 'POST /v2.0/master-data/code-issuances/customer')
  );
  check(issued, { '签发客户编码返回 201': (r) => r.status === 201 });

  const code = issued.status === 201 ? issued.json().code : '';
  if (!code) {
    return;
  }

  const createRes = http.post(
    `${BASE_URL}/v2.0/customers`,
    JSON.stringify({
      customerCode: code,
      customerName: `${DATA_MARKER}-SMOKE`,
      defaultSettlementCompanyId: __ENV.LEO_PERF_COMPANY_ID,
      status: '正常',
      remark: 'perf smoke, safe to delete',
    }),
    writeParams(token, 'POST /v2.0/customers')
  );
  check(createRes, { '创建客户返回 201': (r) => r.status === 201 });

  const createdId = createRes.status === 201 ? createRes.json().id : '';
  if (createdId) {
    const deleteRes = http.del(
      `${BASE_URL}/v2.0/customers/${createdId}`,
      null,
      writeParams(token, 'DELETE /v2.0/customers/{id}')
    );
    check(deleteRes, { '删除客户返回 204': (r) => r.status === 204 });
  }
  writeChain.add(Date.now() - started);
}
