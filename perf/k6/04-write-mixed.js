/**
 * 写接口压测：走完整「签发编码 -> 创建客户」链路。
 *
 * 默认自清理（创建后立即删除），只测写路径吞吐，不长期占用 dev 库数据；
 * 若设置 LEO_PERF_KEEP_DATA=1 则只创建不删除，用于观察数据持续增长下的写入表现，
 * 此时产生的数据 customerName 均以 PERF-LOAD-<runId> 开头，可用 perf/cleanup-perf-data.sh 清理。
 *
 * 用法：
 *   source tmp/perf/creds.env
 *   LEO_PERF_COMPANY_ID=<结算主体ID> k6 run leo/perf/k6/04-write-mixed.js
 */
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import {
  BASE_URL,
  DATA_MARKER,
  ensureToken,
  setupToken,
  SUMMARY_TREND_STATS,
  writeParams,
} from './lib/common.js';

const vus = Number(__ENV.LEO_PERF_WRITE_VUS || 25);
const iterations = Number(__ENV.LEO_PERF_WRITE_ITERATIONS || 250);
const keepData = __ENV.LEO_PERF_KEEP_DATA === '1';
const companyId = __ENV.LEO_PERF_COMPANY_ID || '';

const createTrend = new Trend('write_create_customer_duration', true);
const issueTrend = new Trend('write_issue_code_duration', true);
const createdCount = new Counter('write_customers_created');
const orphanCount = new Counter('write_customers_left_behind');

export const options = {
  scenarios: {
    write_mixed: {
      executor: 'shared-iterations',
      vus,
      iterations,
      maxDuration: __ENV.LEO_PERF_WRITE_MAX_DURATION || '8m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    write_create_customer_duration: ['p(95)<1500'],
  },
  summaryTrendStats: SUMMARY_TREND_STATS,
};

function createCustomer(token, code, seq) {
  const res = http.post(
    `${BASE_URL}/v2.0/customers`,
    JSON.stringify({
      customerCode: code,
      customerName: `${DATA_MARKER}-${__VU}-${seq}`,
      defaultSettlementCompanyId: companyId,
      status: '正常',
      remark: 'perf load test data, safe to delete',
    }),
    writeParams(token, 'POST /v2.0/customers')
  );
  createTrend.add(res.timings.duration);
  check(res, { '创建客户返回 201': (r) => r.status === 201 });
  return res.status === 201 ? res.json().id : '';
}

/** 串行登录一次并共享 token：避免各 VU 启动时的并发登录争用污染测量结果。 */
export function setup() {
  return setupToken();
}

export default function (data) {
  if (!companyId) {
    throw new Error('缺少 LEO_PERF_COMPANY_ID 环境变量（结算主体 ID）');
  }
  const token = ensureToken(data);
  if (!token) {
    return;
  }

  const issueRes = http.post(
    `${BASE_URL}/v2.0/master-data/code-issuances/customer`,
    null,
    writeParams(token, 'POST /v2.0/master-data/code-issuances/customer')
  );
  issueTrend.add(issueRes.timings.duration);
  check(issueRes, { '签发编码返回 201': (r) => r.status === 201 });
  if (issueRes.status !== 201) {
    return;
  }

  const createdId = createCustomer(token, issueRes.json().code, __ITER);
  if (createdId) {
    createdCount.add(1);
  }

  if (createdId && !keepData) {
    const deleteRes = http.del(
      `${BASE_URL}/v2.0/customers/${createdId}`,
      null,
      writeParams(token, 'DELETE /v2.0/customers/{id}')
    );
    check(deleteRes, { '删除自清理数据返回 204': (r) => r.status === 204 });
    if (deleteRes.status !== 204) {
      orphanCount.add(1);
    }
  } else if (createdId) {
    orphanCount.add(1);
  }
}
